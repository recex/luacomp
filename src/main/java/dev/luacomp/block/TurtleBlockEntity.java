package dev.luacomp.block;

import dev.luacomp.LuaCompConfig;
import dev.luacomp.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.List;
import java.util.Map;

/**
 * Turtle: um computador que se move. Para "andar", o bloco coloca uma cópia de si no destino,
 * transfere a máquina Lua, o terminal e o inventário, e remove o bloco antigo.
 */
public class TurtleBlockEntity extends ComputerBlockEntity {
    public static final int SLOTS = 16;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> inventory);

    private int selected = 0;
    private int fuel = LuaCompConfig.turtleStartFuel();

    public TurtleBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TURTLE_BE.get(), pos, state);
    }

    // ---------- persistência e capabilities ----------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", inventory.serializeNBT());
        tag.putInt("Fuel", fuel);
        tag.putInt("Selected", selected);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) inventory.deserializeNBT(tag.getCompound("Items"));
        if (tag.contains("Fuel")) fuel = tag.getInt("Fuel");
        selected = Math.max(0, Math.min(SLOTS - 1, tag.getInt("Selected")));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    public ItemStackHandler getInventory() { return inventory; }

    /** Solta tudo no mundo (turtle quebrado). */
    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ---------- utilidades ----------

    private ServerLevel sl() { return (ServerLevel) level; }

    public Direction front() { return getBlockState().getValue(ComputerBlock.FACING); }

    private static Varargs fail(String message) {
        return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(message));
    }

    private IItemHandler handlerAt(BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be == this) return null;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    private void spawn(ItemStack stack, Direction dir) {
        Vec3 p = Vec3.atCenterOf(worldPosition.relative(dir));
        ItemEntity e = new ItemEntity(sl(), p.x, p.y, p.z, stack);
        e.setDeltaMovement(dir.getStepX() * 0.15, dir.getStepY() * 0.15 + 0.05, dir.getStepZ() * 0.15);
        e.setDefaultPickUpDelay();
        sl().addFreshEntity(e);
    }

    /** Guarda no inventário (começando pelo slot selecionado); o que sobrar cai no mundo. */
    private void store(ItemStack stack, Direction dropDir) {
        ItemStack rest = stack;
        for (int k = 0; k < SLOTS && !rest.isEmpty(); k++) {
            rest = inventory.insertItem((selected + k) % SLOTS, rest, false);
        }
        if (!rest.isEmpty()) spawn(rest, dropDir);
    }

    private void copyTurtleState(TurtleBlockEntity old) {
        for (int i = 0; i < SLOTS; i++) {
            inventory.setStackInSlot(i, old.inventory.getStackInSlot(i));
            old.inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        selected = old.selected;
        fuel = old.fuel;
    }

    // ---------- movimento ----------

    public Varargs move(Direction dir) {
        ServerLevel sl = sl();
        boolean fuelOn = LuaCompConfig.turtleFuelEnabled();
        if (fuelOn && fuel <= 0) return fail("Out of fuel");

        BlockPos target = worldPosition.relative(dir);
        if (!sl.isInWorldBounds(target)) return fail("Movement obstructed");
        BlockState there = sl.getBlockState(target);
        if (!there.canBeReplaced() || !sl.getEntitiesOfClass(LivingEntity.class, new AABB(target)).isEmpty()) {
            return fail("Movement obstructed");
        }

        BlockState mine = getBlockState();
        sl.setBlock(target, mine, 3);
        if (!(sl.getBlockEntity(target) instanceof TurtleBlockEntity moved)) return fail("Movement failed");

        moved.adoptFrom(this);
        moved.copyTurtleState(this);
        if (fuelOn) moved.fuel = Math.max(0, moved.fuel - 1);
        sl.removeBlock(worldPosition, false);
        moved.refreshInputs(false);
        return LuaValue.TRUE;
    }

    public Varargs turn(boolean right) {
        Direction f = front();
        Direction nf = right ? f.getClockWise() : f.getCounterClockWise();
        level.setBlock(worldPosition, getBlockState().setValue(ComputerBlock.FACING, nf), 3);
        return LuaValue.TRUE;
    }

    // ---------- blocos ----------

    public Varargs dig(Direction dir) {
        if (!LuaCompConfig.turtleCanDig()) return fail("Digging is disabled on this server");
        ServerLevel sl = sl();
        BlockPos tp = worldPosition.relative(dir);
        BlockState st = sl.getBlockState(tp);
        if (st.isAir() || st.getBlock() instanceof LiquidBlock) return fail("Nothing to dig here");
        if (st.getDestroySpeed(sl, tp) < 0) return fail("Unbreakable block detected");

        List<ItemStack> drops = Block.getDrops(st, sl, tp, sl.getBlockEntity(tp));
        sl.destroyBlock(tp, false);
        for (ItemStack d : drops) store(d, dir);
        return LuaValue.TRUE;
    }

    public Varargs place(Direction dir) {
        ServerLevel sl = sl();
        ItemStack stack = inventory.getStackInSlot(selected);
        if (stack.isEmpty()) return fail("No items to place");
        if (!(stack.getItem() instanceof BlockItem bi)) return fail("Cannot place item here");

        BlockPos tp = worldPosition.relative(dir);
        if (!sl.getBlockState(tp).canBeReplaced() || !sl.getEntitiesOfClass(LivingEntity.class, new AABB(tp)).isEmpty()) {
            return fail("Cannot place block here");
        }

        BlockState ns = bi.getBlock().defaultBlockState();
        if (ns.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            ns = ns.setValue(BlockStateProperties.HORIZONTAL_FACING, front().getOpposite());
        }
        if (!ns.canSurvive(sl, tp)) return fail("Cannot place block here");

        ItemStack one = stack.copy();
        one.setCount(1);
        sl.setBlock(tp, ns, 3);
        BlockItem.updateCustomBlockEntityTag(sl, null, tp, one);
        sl.playSound(null, tp, ns.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        inventory.extractItem(selected, 1, false);
        return LuaValue.TRUE;
    }

    public Varargs attack(Direction dir) {
        ServerLevel sl = sl();
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition.relative(dir)));
        if (targets.isEmpty()) return fail("Nothing to attack here");
        targets.get(0).hurt(sl.damageSources().generic(), 4.0F);
        return LuaValue.TRUE;
    }

    public boolean detect(Direction dir) {
        BlockState st = level.getBlockState(worldPosition.relative(dir));
        return !st.isAir() && !st.canBeReplaced();
    }

    public Varargs inspect(Direction dir) {
        BlockState st = level.getBlockState(worldPosition.relative(dir));
        if (st.isAir()) return fail("No block to inspect");
        LuaTable t = new LuaTable();
        var key = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        t.set("name", LuaValue.valueOf(key == null ? "unknown" : key.toString()));
        LuaTable props = new LuaTable();
        for (Map.Entry<Property<?>, Comparable<?>> e : st.getValues().entrySet()) {
            props.set(e.getKey().getName(), LuaValue.valueOf(String.valueOf(e.getValue())));
        }
        t.set("state", props);
        return LuaValue.varargsOf(LuaValue.TRUE, t);
    }

    // ---------- itens ----------

    public Varargs drop(Direction dir, int count) {
        ItemStack stack = inventory.getStackInSlot(selected);
        if (stack.isEmpty()) return fail("No items to drop");
        int n = count <= 0 ? stack.getCount() : Math.min(count, stack.getCount());
        ItemStack out = inventory.extractItem(selected, n, false);

        IItemHandler handler = handlerAt(worldPosition.relative(dir), dir.getOpposite());
        if (handler != null) {
            ItemStack rest = ItemHandlerHelper.insertItem(handler, out, false);
            if (!rest.isEmpty()) {
                ItemStack back = inventory.insertItem(selected, rest, false);
                if (!back.isEmpty()) spawn(back, dir);
            }
            return rest.getCount() < out.getCount() ? LuaValue.TRUE : fail("No space for items");
        }
        spawn(out, dir);
        return LuaValue.TRUE;
    }

    public Varargs suck(Direction dir, int count) {
        BlockPos tp = worldPosition.relative(dir);
        IItemHandler handler = handlerAt(tp, dir.getOpposite());
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                int limit = count <= 0 ? 64 : count;
                ItemStack probe = handler.extractItem(slot, limit, true);
                if (probe.isEmpty()) continue;
                store(handler.extractItem(slot, probe.getCount(), false), dir);
                return LuaValue.TRUE;
            }
            return fail("No items to take");
        }

        List<ItemEntity> items = sl().getEntitiesOfClass(ItemEntity.class, new AABB(tp));
        if (items.isEmpty()) return fail("No items to take");
        ItemEntity e = items.get(0);
        ItemStack s = e.getItem();
        int n = count <= 0 ? s.getCount() : Math.min(count, s.getCount());
        ItemStack taken = s.split(n);
        if (s.isEmpty()) e.discard(); else e.setItem(s);
        store(taken, dir);
        return LuaValue.TRUE;
    }

    public Varargs select(int slot) {
        if (slot < 1 || slot > SLOTS) return fail("Slot number " + slot + " out of range");
        selected = slot - 1;
        return LuaValue.TRUE;
    }

    public int selectedSlot() { return selected + 1; }

    private static int slotIndex(int oneBased, int current) {
        return oneBased <= 0 ? current : Math.max(0, Math.min(SLOTS - 1, oneBased - 1));
    }

    public int itemCount(int slot) { return inventory.getStackInSlot(slotIndex(slot, selected)).getCount(); }

    public int itemSpace(int slot) {
        ItemStack s = inventory.getStackInSlot(slotIndex(slot, selected));
        return s.isEmpty() ? 64 : Math.max(0, s.getMaxStackSize() - s.getCount());
    }

    public Varargs itemDetail(int slot) {
        ItemStack s = inventory.getStackInSlot(slotIndex(slot, selected));
        if (s.isEmpty()) return LuaValue.NIL;
        LuaTable t = new LuaTable();
        var key = ForgeRegistries.ITEMS.getKey(s.getItem());
        t.set("name", LuaValue.valueOf(key == null ? "unknown" : key.toString()));
        t.set("count", LuaValue.valueOf(s.getCount()));
        return t;
    }

    // ---------- combustível ----------

    public Varargs fuelLevel() {
        return LuaCompConfig.turtleFuelEnabled() ? LuaValue.valueOf(fuel) : LuaValue.valueOf("unlimited");
    }

    public Varargs fuelLimit() {
        return LuaCompConfig.turtleFuelEnabled() ? LuaValue.valueOf(LuaCompConfig.turtleFuelLimit()) : LuaValue.valueOf("unlimited");
    }

    public Varargs refuel(int count) {
        ItemStack stack = inventory.getStackInSlot(selected);
        if (stack.isEmpty()) return fail("No items to combust");
        int per = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) / 20;
        if (per <= 0) return fail("Items not combustible");

        int limit = LuaCompConfig.turtleFuelLimit();
        int want = count <= 0 ? stack.getCount() : Math.min(count, stack.getCount());
        int used = 0;
        while (used < want && fuel + per <= limit) {
            ItemStack remainder = stack.getCraftingRemainingItem();
            inventory.extractItem(selected, 1, false);
            if (!remainder.isEmpty()) store(remainder, front());
            fuel += per;
            used++;
        }
        return used > 0 ? LuaValue.TRUE : fail("Fuel limit reached");
    }
}
