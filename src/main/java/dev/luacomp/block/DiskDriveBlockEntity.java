package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.item.DiskItem;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralAccess;
import dev.luacomp.peripheral.PeripheralMethod;
import dev.luacomp.util.PeripheralIds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaValue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Leitor de disquetes. O disquete vira a pasta "disk" (ou "disk2"...) no sistema de arquivos do computador. */
public class DiskDriveBlockEntity extends BlockEntity implements IPeripheral {
    private ItemStack disk = ItemStack.EMPTY;
    private final Set<PeripheralAccess> attached = new HashSet<>();
    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DISK_DRIVE_BE.get(), pos, state);

        methods.put("isDiskPresent", (a, ctx) -> LuaValue.valueOf(hasDisk()));
        methods.put("hasData", (a, ctx) -> LuaValue.valueOf(hasDisk()));
        methods.put("getDiskLabel", (a, ctx) -> {
            String label = hasDisk() ? DiskItem.getLabel(disk) : null;
            return label == null ? LuaValue.NIL : LuaValue.valueOf(label);
        });
        methods.put("setDiskLabel", (a, ctx) -> {
            if (hasDisk()) {
                DiskItem.setLabel(disk, a.isnil(1) ? null : a.checkjstring(1));
                setChanged();
            }
            return LuaValue.NONE;
        });
        methods.put("getDiskID", (a, ctx) -> hasDisk() ? LuaValue.valueOf(DiskItem.getId(disk)) : LuaValue.NIL);
        methods.put("getMountPath", (a, ctx) -> {
            String mount = hasDisk() ? ctx.computer().getMountName(ctx.name()) : null;
            return mount == null ? LuaValue.NIL : LuaValue.valueOf(mount);
        });
        methods.put("hasAudio", (a, ctx) -> LuaValue.FALSE);
        methods.put("getAudioTitle", (a, ctx) -> LuaValue.NIL);
        methods.put("ejectDisk", (a, ctx) -> {
            ItemStack out = eject();
            if (!out.isEmpty() && level != null) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, out);
            }
            return LuaValue.NONE;
        });
    }

    public boolean hasDisk() { return !disk.isEmpty(); }

    public ItemStack getDisk() { return disk; }

    public boolean insert(ItemStack stack) {
        if (hasDisk() || !(stack.getItem() instanceof DiskItem) || !(level instanceof ServerLevel sl)) return false;
        if (DiskItem.getId(stack) < 0) DiskItem.setId(stack, PeripheralIds.get(sl.getServer()).allocate("disk"));
        disk = stack;
        setChanged();
        for (PeripheralAccess access : attached) {
            access.computer().mountDisk(access.name(), DiskItem.getId(disk));
            access.queueEvent("disk", access.name());
        }
        return true;
    }

    public ItemStack eject() {
        if (!hasDisk()) return ItemStack.EMPTY;
        ItemStack out = disk;
        disk = ItemStack.EMPTY;
        setChanged();
        for (PeripheralAccess access : attached) {
            access.computer().unmountDisk(access.name());
            access.queueEvent("disk_eject", access.name());
        }
        return out;
    }

    @Override
    public void onAttach(PeripheralAccess access) {
        attached.add(access);
        if (hasDisk()) access.computer().mountDisk(access.name(), DiskItem.getId(disk));
    }

    @Override
    public void onDetach(PeripheralAccess access) {
        attached.remove(access);
        access.computer().unmountDisk(access.name());
    }

    @Override
    public String getPeripheralType() { return "drive"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (hasDisk()) tag.put("Disk", disk.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        disk = tag.contains("Disk") ? ItemStack.of(tag.getCompound("Disk")) : ItemStack.EMPTY;
    }
}
