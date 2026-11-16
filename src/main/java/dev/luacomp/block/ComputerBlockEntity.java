package dev.luacomp.block;

import dev.luacomp.LuaCompConfig;
import dev.luacomp.ModRegistry;
import dev.luacomp.fs.ComputerFS;
import dev.luacomp.lua.LuaMachine;
import dev.luacomp.menu.ComputerMenu;
import dev.luacomp.network.ComputerInputPacket;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralAccess;
import dev.luacomp.peripheral.WiredNetwork;
import dev.luacomp.terminal.Terminal;
import dev.luacomp.util.ComputerIds;
import dev.luacomp.util.ModEnergy;
import dev.luacomp.util.Sides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class ComputerBlockEntity extends BlockEntity {
    private int computerId = -1;
    private boolean powered;
    private String label;

    private Terminal terminal = new Terminal();
    private LuaMachine machine;

    // Índices seguem Sides.NAMES. Lidos/escritos pela thread do Lua, por isso atômicos.
    private final AtomicIntegerArray inputs = new AtomicIntegerArray(6);
    private final AtomicIntegerArray outputs = new AtomicIntegerArray(6);
    private volatile boolean outputsDirty;
    private volatile long gameTime;
    private volatile long dayTime;

    // tarefas que o Lua pede para rodar na thread do servidor (periféricos)
    private final ConcurrentLinkedQueue<Runnable> mainTasks = new ConcurrentLinkedQueue<>();

    // periféricos visíveis agora
    private final Map<String, PeripheralAccess> accesses = new HashMap<>();
    private Map<String, IPeripheral> attached = new LinkedHashMap<>();

    // disquetes montados: nome do periférico -> nome da montagem (disk, disk2...)
    private final Map<String, String> mountNames = new HashMap<>();

    // energia
    private final ModEnergy energy = new ModEnergy(LuaCompConfig.energyCapacity());
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public ComputerBlockEntity(BlockPos pos, BlockState state) {
        this(ModRegistry.COMPUTER_BE.get(), pos, state);
    }

    protected ComputerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------- persistência ----------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ComputerId", computerId);
        tag.putBoolean("Powered", powered);
        tag.putInt("Energy", energy.getEnergyStored());
        if (label != null) tag.putString("Label", label);
        int[] o = new int[6];
        for (int i = 0; i < 6; i++) o[i] = outputs.get(i);
        tag.putIntArray("Outputs", o);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        computerId = tag.contains("ComputerId") ? tag.getInt("ComputerId") : -1;
        powered = tag.getBoolean("Powered");
        energy.set(tag.getInt("Energy"));
        label = tag.contains("Label") ? tag.getString("Label") : null;
        int[] o = tag.getIntArray("Outputs");
        for (int i = 0; i < 6 && i < o.length; i++) outputs.set(i, o[i]);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (machine != null) {
            machine.kill();
            machine = null;
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY && LuaCompConfig.energyEnabled()) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    // ---------- tick ----------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity be) {
        if (level instanceof ServerLevel sl) be.tick(sl);
    }

    private void tick(ServerLevel level) {
        gameTime = level.getGameTime();
        dayTime = level.getDayTime();

        // tarefas pedidas pelo Lua (chamadas de periférico)
        int pending = Math.min(mainTasks.size(), 64);
        for (int i = 0; i < pending; i++) {
            Runnable task = mainTasks.poll();
            if (task == null) break;
            task.run();
            if (isRemoved()) return; // o turtle se moveu durante a tarefa
        }

        if (powered && machine == null) startMachine(level); // retoma depois de recarregar o chunk

        if (machine != null) {
            machine.tick(gameTime);

            if (LuaCompConfig.energyEnabled() && !machine.isFinished()) {
                int cost = LuaCompConfig.energyPerTick() + (int) (machine.drainCpuNanos() / LuaCompConfig.energyNanosPerFE());
                if (energy.getEnergyStored() < cost) {
                    stopMachine(level);
                    terminal.println("Sem energia");
                    machine = null;
                } else {
                    energy.consume(cost);
                }
            } else {
                machine.drainCpuNanos();
            }
        }

        if (machine != null) {
            if (gameTime % 10 == 0) updatePeripherals(true);
            if (machine.isFinished() && !machine.hasCrashed()) {
                boolean reboot = machine.wantsReboot();
                stopMachine(level);
                if (reboot) startMachine(level);
            }
        }

        if (outputsDirty) {
            outputsDirty = false;
            setChanged();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    // ---------- ciclo de vida da máquina ----------

    public void startMachine(ServerLevel level) {
        if (machine != null) machine.kill();
        if (computerId < 0) computerId = ComputerIds.get(level.getServer()).allocate();

        if (LuaCompConfig.energyEnabled() && energy.getEnergyStored() < LuaCompConfig.energyPerTick() * 20) {
            powered = false;
            terminal.reset();
            terminal.setPowered(false);
            terminal.println("Sem energia");
            setChanged();
            return;
        }

        Path root = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("luacomp").resolve("computer").resolve(Integer.toString(computerId));

        terminal.reset();
        terminal.setPowered(true);
        refreshInputs(false);

        machine = new LuaMachine(this, terminal, new ComputerFS(root));
        powered = true;
        setChanged();
        updateOnState(level, true);
        updatePeripherals(false);
        machine.start();
    }

    public void stopMachine(ServerLevel level) {
        if (machine != null) {
            machine.kill();
            machine = null;
        }
        detachAll();
        mainTasks.clear();
        powered = false;
        for (int i = 0; i < 6; i++) outputs.set(i, 0);
        outputsDirty = true;
        terminal.reset();
        terminal.setPowered(false);
        setChanged();
        updateOnState(level, false);
    }

    private void updateOnState(ServerLevel level, boolean on) {
        BlockState s = getBlockState();
        if (s.hasProperty(ComputerBlock.ON) && s.getValue(ComputerBlock.ON) != on) {
            level.setBlock(worldPosition, s.setValue(ComputerBlock.ON, on), 3);
        }
    }

    // ---------- periféricos ----------

    /** Thread do servidor: periféricos que o computador enxerga agora (por lado ou por rede de cabos). */
    public Map<String, IPeripheral> scanPeripherals() {
        Map<String, IPeripheral> out = new LinkedHashMap<>();
        if (level == null) return out;
        Direction facing = getBlockState().getValue(ComputerBlock.FACING);
        for (int i = 0; i < 6; i++) {
            Direction d = Sides.world(facing, i);
            BlockPos np = worldPosition.relative(d);
            BlockEntity nb = level.getBlockEntity(np);
            if (nb instanceof ModemBlockEntity m && m.isWired()) {
                if (m.getAttachedDirection() == d.getOpposite()) WiredNetwork.collect(level, np, out);
            } else if (nb instanceof IPeripheral p && !nb.isRemoved()) {
                out.put(Sides.NAMES[i], p);
            }
        }
        return out;
    }

    public PeripheralAccess accessFor(String name) {
        return accesses.computeIfAbsent(name, n -> new PeripheralAccess(this, n));
    }

    /** Compara com o último scan: chama onAttach/onDetach e dispara os eventos peripheral / peripheral_detach. */
    public void updatePeripherals(boolean fireEvents) {
        Map<String, IPeripheral> now = scanPeripherals();

        for (Map.Entry<String, IPeripheral> e : attached.entrySet()) {
            if (now.get(e.getKey()) != e.getValue()) {
                e.getValue().onDetach(accessFor(e.getKey()));
                if (fireEvents) queueMachineEvent("peripheral_detach", e.getKey());
            }
        }
        for (Map.Entry<String, IPeripheral> e : now.entrySet()) {
            if (attached.get(e.getKey()) != e.getValue()) {
                e.getValue().onAttach(accessFor(e.getKey()));
                if (fireEvents) queueMachineEvent("peripheral", e.getKey());
            }
        }
        attached = now;
    }

    private void detachAll() {
        for (Map.Entry<String, IPeripheral> e : attached.entrySet()) e.getValue().onDetach(accessFor(e.getKey()));
        attached = new LinkedHashMap<>();
        accesses.clear();
        mountNames.clear();
    }

    public void enqueueMainTask(Runnable task) {
        mainTasks.add(task);
    }

    public void queueMachineEvent(String event, Object... args) {
        LuaMachine m = machine;
        if (m != null) m.queueEvent(event, args);
    }

    // ---------- GUI e entrada ----------

    public void openMenu(ServerPlayer player) {
        if (computerId < 0) startMachine(player.serverLevel()); // primeiro uso: já liga
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inv, p) -> new ComputerMenu(id, inv, this, worldPosition),
                        Component.translatable("block.luacomp.computer")),
                worldPosition);
    }

    public void handleInput(ComputerInputPacket p) {
        if (!(level instanceof ServerLevel sl)) return;
        switch (p.type()) {
            case TERMINATE -> { if (machine != null) machine.queueEvent("terminate"); }
            case SHUTDOWN -> stopMachine(sl);
            case REBOOT -> { stopMachine(sl); startMachine(sl); }
            case KEY -> { if (machine != null) machine.queueEvent("key", p.code(), p.flag()); }
            case KEY_UP -> { if (machine != null) machine.queueEvent("key_up", p.code()); }
            case CHAR -> { if (machine != null) machine.queueEvent("char", p.text()); }
            case PASTE -> { if (machine != null) machine.queueEvent("paste", p.text()); }
        }
    }

    // ---------- redstone ----------

    public void onNeighborChanged() {
        refreshInputs(true);
    }

    protected void refreshInputs(boolean fireEvent) {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(ComputerBlock.FACING);
        boolean changed = false;
        for (int i = 0; i < 6; i++) {
            Direction d = Sides.world(facing, i);
            int power = level.getSignal(worldPosition.relative(d), d);
            if (inputs.getAndSet(i, power) != power) changed = true;
        }
        if (changed && fireEvent && machine != null) machine.queueEvent("redstone");
    }

    public int getOutputFor(Direction face, Direction facing) {
        return outputs.get(Sides.index(facing, face));
    }

    // chamados pela thread do Lua
    public int getInput(int side) { return inputs.get(side); }
    public int getOutput(int side) { return outputs.get(side); }

    public void setOutput(int side, int power) {
        int v = Math.max(0, Math.min(15, power));
        if (outputs.getAndSet(side, v) != v) outputsDirty = true;
    }

    // ---------- disquetes ----------

    /** Monta a pasta do disquete no sistema de arquivos deste computador e devolve o nome ("disk", "disk2"...). */
    public String mountDisk(String peripheralName, int diskId) {
        LuaMachine m = machine;
        if (m == null || !(level instanceof ServerLevel sl)) return null;
        String mount = mountNames.computeIfAbsent(peripheralName, k -> freeMountName());
        Path root = sl.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("luacomp").resolve("disk").resolve(Integer.toString(diskId));
        m.fs().mount(mount, root, 125_000L);
        return mount;
    }

    public void unmountDisk(String peripheralName) {
        String mount = mountNames.remove(peripheralName);
        LuaMachine m = machine;
        if (mount != null && m != null) m.fs().unmount(mount);
    }

    public String getMountName(String peripheralName) {
        return mountNames.get(peripheralName);
    }

    private String freeMountName() {
        for (int i = 1; ; i++) {
            String name = i == 1 ? "disk" : "disk" + i;
            if (!mountNames.containsValue(name)) return name;
        }
    }

    // ---------- movimento de turtles ----------

    /** Assume a máquina Lua, o terminal e o estado de outro bloco (usado quando um turtle se move). */
    protected void adoptFrom(ComputerBlockEntity old) {
        old.detachAll(); // desconecta periféricos do bloco antigo; o novo reconecta no próximo scan
        this.computerId = old.computerId;
        this.powered = old.powered;
        this.label = old.label;
        this.terminal = old.terminal;
        this.machine = old.machine;
        for (int i = 0; i < 6; i++) {
            this.outputs.set(i, old.outputs.get(i));
            this.inputs.set(i, old.inputs.get(i));
        }
        this.energy.set(old.energy.getEnergyStored());
        this.mainTasks.addAll(old.mainTasks);
        old.mainTasks.clear();
        old.machine = null;
        old.powered = false;
        if (this.machine != null) this.machine.setOwner(this);
        this.outputsDirty = true;
        setChanged();
    }

    // ---------- acessores ----------

    public Terminal getTerminal() { return terminal; }
    public int getComputerId() { return computerId; }
    public String getLabel() { return label; }
    public void setLabel(String l) { label = l; }
    public long getGameTime() { return gameTime; }
    public long getDayTime() { return dayTime; }
    public int getEnergyStored() { return energy.getEnergyStored(); }
    public int getEnergyCapacity() { return energy.getMaxEnergyStored(); }
}
