package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.lua.LuaApis;
import dev.luacomp.network.ModNetwork;
import dev.luacomp.network.MonitorPacket;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralAccess;
import dev.luacomp.peripheral.PeripheralMethod;
import dev.luacomp.terminal.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Monitor. Monitores vizinhos (mesma face, formando um retângulo até 8x6) viram uma tela só:
 * o bloco do canto superior esquerdo ("origem") guarda o terminal e desenha tudo.
 */
public class MonitorBlockEntity extends BlockEntity implements IPeripheral {
    public static final int COLS_PER_BLOCK = 7;
    public static final int ROWS_PER_BLOCK = 5;
    public static final int MAX_W = 8;
    public static final int MAX_H = 6;

    private static final Set<String> TERM_METHODS = LuaApis.termFns(new Terminal(1, 1)).keySet();

    private final Terminal terminal = new Terminal(COLS_PER_BLOCK, ROWS_PER_BLOCK);
    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();
    private final Set<PeripheralAccess> attached = new HashSet<>(); // só no servidor

    private BlockPos originPos;
    private boolean origin = true;
    private int gw = 1, gh = 1;
    private double scale = 1.0;

    private int lastSent = -1;
    private boolean dirty = true;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MONITOR_BE.get(), pos, state);
        originPos = pos;

        for (String name : TERM_METHODS) {
            methods.put(name, (a, ctx) -> LuaApis.termFns(origin().terminal).get(name).call(a));
        }
        methods.put("setTextScale", (a, ctx) -> {
            double s = a.checkdouble(1);
            if (s < 0.5 || s > 5.0) throw new LuaError("Expected number in range 0.5-5");
            origin().setScale(s);
            return LuaValue.NONE;
        });
        methods.put("getTextScale", (a, ctx) -> LuaValue.valueOf(origin().scale));
    }

    // ---------- geometria ----------

    /** Canto superior esquerdo da face da frente, em coordenadas do mundo (a origem do bloco é {@code pos}). */
    public static Vec3 faceTopLeft(BlockPos pos, Direction facing) {
        Direction r = facing.getCounterClockWise();
        double x, z;
        if (facing.getAxis() == Direction.Axis.X) x = facing.getStepX() > 0 ? 1 : 0;
        else x = r.getStepX() > 0 ? 0 : 1;
        if (facing.getAxis() == Direction.Axis.Z) z = facing.getStepZ() > 0 ? 1 : 0;
        else z = r.getStepZ() > 0 ? 0 : 1;
        return new Vec3(pos.getX() + x, pos.getY() + 1, pos.getZ() + z);
    }

    /** Recalcula o retângulo de monitores a partir de {@code start}. {@code self} cobre o BE ainda não registrado no onLoad. */
    public static void regroup(Level level, BlockPos start, @Nullable MonitorBlockEntity self) {
        BlockState st = level.getBlockState(start);
        if (!(st.getBlock() instanceof MonitorBlock)) return;
        Direction f = st.getValue(MonitorBlock.FACING);
        Direction r = f.getCounterClockWise();
        Direction[] steps = {r, r.getOpposite(), Direction.UP, Direction.DOWN};

        Set<BlockPos> comp = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        comp.add(start);
        queue.add(start);
        while (!queue.isEmpty() && comp.size() <= 256) {
            BlockPos p = queue.poll();
            for (Direction d : steps) {
                BlockPos np = p.relative(d);
                if (comp.contains(np)) continue;
                BlockState ns = level.getBlockState(np);
                if (ns.getBlock() instanceof MonitorBlock && ns.getValue(MonitorBlock.FACING) == f) {
                    comp.add(np);
                    queue.add(np);
                }
            }
        }

        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (BlockPos p : comp) {
            int u = (p.getX() - start.getX()) * r.getStepX() + (p.getZ() - start.getZ()) * r.getStepZ();
            int v = p.getY() - start.getY();
            minU = Math.min(minU, u); maxU = Math.max(maxU, u);
            minV = Math.min(minV, v); maxV = Math.max(maxV, v);
        }
        int w = maxU - minU + 1, h = maxV - minV + 1;
        boolean rect = comp.size() == w * h && w <= MAX_W && h <= MAX_H;
        BlockPos topLeft = start.relative(r, minU).relative(Direction.UP, maxV);

        for (BlockPos p : comp) {
            MonitorBlockEntity be = (self != null && p.equals(self.worldPosition)) ? self
                    : (level.getBlockEntity(p) instanceof MonitorBlockEntity m ? m : null);
            if (be == null) continue;
            if (rect) be.setGroup(topLeft, w, h);
            else be.setGroup(p, 1, 1);
        }
    }

    private void setGroup(BlockPos origin, int w, int h) {
        this.originPos = origin;
        this.gw = w;
        this.gh = h;
        this.origin = origin.equals(worldPosition);
        if (this.origin) refreshSize();
        dirty = true;
    }

    private void refreshSize() {
        int cols = Math.max(1, Math.min(120, (int) Math.floor(gw * COLS_PER_BLOCK / scale)));
        int rows = Math.max(1, Math.min(80, (int) Math.floor(gh * ROWS_PER_BLOCK / scale)));
        terminal.resize(cols, rows);
    }

    private void setScale(double s) {
        scale = Math.round(Math.max(0.5, Math.min(5.0, s)) * 2.0) / 2.0;
        refreshSize();
        terminal.clear();
        dirty = true;
        setChanged();
    }

    private MonitorBlockEntity origin() {
        if (origin || level == null || originPos == null) return this;
        return level.getBlockEntity(originPos) instanceof MonitorBlockEntity m ? m : this;
    }

    // ---------- toque ----------

    /** Clique direito na frente: vira o evento monitor_touch(nome, x, y) nos computadores ligados à tela. */
    public void touch(Vec3 hit, Direction facing) {
        MonitorBlockEntity o = origin();
        if (o.level == null) return;
        Direction r = facing.getCounterClockWise();
        Vec3 tl = faceTopLeft(o.worldPosition, facing);
        Vec3 d = hit.subtract(tl);
        double u = d.x * r.getStepX() + d.z * r.getStepZ();
        double v = -d.y;
        int cols = o.terminal.width(), rows = o.terminal.height();
        int x = (int) Math.floor(u / o.gw * cols) + 1;
        int y = (int) Math.floor(v / o.gh * rows) + 1;
        x = Math.max(1, Math.min(cols, x));
        y = Math.max(1, Math.min(rows, y));

        for (int i = 0; i < o.gw; i++) {
            for (int j = 0; j < o.gh; j++) {
                BlockPos p = o.worldPosition.relative(r, i).relative(Direction.DOWN, j);
                if (o.level.getBlockEntity(p) instanceof MonitorBlockEntity m) {
                    for (PeripheralAccess access : m.attached) access.queueEvent("monitor_touch", access.name(), x, y);
                }
            }
        }
    }

    // ---------- tick e sincronização ----------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) regroup(level, worldPosition, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MonitorBlockEntity be) {
        boolean termChanged = be.origin && be.terminal.version() != be.lastSent;
        if (!be.dirty && !(termChanged && level.getGameTime() % 3 == 0)) return;
        be.dirty = false;
        be.lastSent = be.terminal.version();
        ModNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64.0, level.dimension())),
                new MonitorPacket(pos, be.buildTag(be.origin)));
    }

    private CompoundTag buildTag(boolean withTerminal) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Origin", origin);
        tag.putInt("GW", gw);
        tag.putInt("GH", gh);
        if (withTerminal) tag.put("Term", terminal.toTag());
        return tag;
    }

    public void applyClientData(CompoundTag tag) {
        origin = tag.getBoolean("Origin");
        gw = Math.max(1, tag.getInt("GW"));
        gh = Math.max(1, tag.getInt("GH"));
        if (tag.contains("Term")) terminal.fromTag(tag.getCompound("Term"));
    }

    @Override
    public CompoundTag getUpdateTag() {
        return buildTag(origin);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        applyClientData(tag);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(10);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble("Scale", scale);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Scale")) scale = Math.max(0.5, Math.min(5.0, tag.getDouble("Scale")));
    }

    // ---------- cliente ----------

    public boolean isOriginClient() { return origin; }
    public int getGroupWidth() { return gw; }
    public int getGroupHeight() { return gh; }
    public Terminal getTerminal() { return terminal; }

    // ---------- IPeripheral ----------

    @Override
    public String getPeripheralType() { return "monitor"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }

    @Override
    public void onAttach(PeripheralAccess access) { attached.add(access); }

    @Override
    public void onDetach(PeripheralAccess access) { attached.remove(access); }
}
