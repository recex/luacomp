package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.item.PrintoutItem;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Impressora. Slot 0 = tinta (corantes), 1-6 = papel, 7-12 = páginas prontas.
 * Cada corante rende 8 páginas; cada página gasta 1 papel.
 */
public class PrinterBlockEntity extends BlockEntity implements IPeripheral {
    public static final int PAGE_W = 25;
    public static final int PAGE_H = 21;
    public static final int INK_SLOT = 0;
    public static final int PAPER_FIRST = 1, PAPER_LAST = 6;
    public static final int OUT_FIRST = 7, OUT_LAST = 12;
    private static final int PAGES_PER_DYE = 8;

    private final ItemStackHandler inventory = new ItemStackHandler(13) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == INK_SLOT) return stack.is(Tags.Items.DYES);
            if (slot >= PAPER_FIRST && slot <= PAPER_LAST) return stack.is(Items.PAPER);
            return false; // saídas só pela própria impressora
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> inventory);
    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();

    private int inkUnits;
    private boolean open;
    private char[][] page = new char[PAGE_H][PAGE_W];
    private int cx = 1, cy = 1;
    private String title = "";

    public PrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRINTER_BE.get(), pos, state);

        methods.put("newPage", (a, ctx) -> {
            if (open) return LuaValue.FALSE;
            if (!consumeSupplies()) return LuaValue.FALSE;
            open = true;
            for (char[] row : page) Arrays.fill(row, ' ');
            cx = 1;
            cy = 1;
            title = "";
            return LuaValue.TRUE;
        });

        methods.put("endPage", (a, ctx) -> {
            requireOpen();
            for (int slot = OUT_FIRST; slot <= OUT_LAST; slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    ItemStack out = new ItemStack(ModRegistry.PRINTOUT.get());
                    PrintoutItem.write(out, title, lines());
                    inventory.setStackInSlot(slot, out);
                    open = false;
                    return LuaValue.TRUE;
                }
            }
            return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf("Output tray is full"));
        });

        methods.put("write", (a, ctx) -> {
            requireOpen();
            String text = a.arg(1).tojstring();
            if (cy >= 1 && cy <= PAGE_H) {
                for (int i = 0; i < text.length(); i++) {
                    int x = cx + i;
                    if (x >= 1 && x <= PAGE_W) page[cy - 1][x - 1] = text.charAt(i) < 32 ? '?' : text.charAt(i);
                }
            }
            cx += text.length();
            return LuaValue.NONE;
        });

        methods.put("setCursorPos", (a, ctx) -> {
            requireOpen();
            cx = a.checkint(1);
            cy = a.checkint(2);
            return LuaValue.NONE;
        });
        methods.put("getCursorPos", (a, ctx) -> {
            requireOpen();
            return LuaValue.varargsOf(LuaValue.valueOf(cx), LuaValue.valueOf(cy));
        });
        methods.put("getPageSize", (a, ctx) -> {
            requireOpen();
            return LuaValue.varargsOf(LuaValue.valueOf(PAGE_W), LuaValue.valueOf(PAGE_H));
        });
        methods.put("setPageTitle", (a, ctx) -> {
            requireOpen();
            String t = a.isnil(1) ? "" : a.checkjstring(1);
            title = t.length() > 32 ? t.substring(0, 32) : t;
            return LuaValue.NONE;
        });
        methods.put("getPaperLevel", (a, ctx) -> LuaValue.valueOf(paperCount()));
        methods.put("getInkLevel", (a, ctx) -> LuaValue.valueOf(inkUnits + inventory.getStackInSlot(INK_SLOT).getCount() * PAGES_PER_DYE));
    }

    private void requireOpen() {
        if (!open) throw new LuaError("Page not started");
    }

    private int paperCount() {
        int n = 0;
        for (int s = PAPER_FIRST; s <= PAPER_LAST; s++) n += inventory.getStackInSlot(s).getCount();
        return n;
    }

    private boolean consumeSupplies() {
        int paperSlot = -1;
        for (int s = PAPER_FIRST; s <= PAPER_LAST; s++) {
            if (!inventory.getStackInSlot(s).isEmpty()) { paperSlot = s; break; }
        }
        if (paperSlot < 0) return false;
        if (inkUnits <= 0) {
            ItemStack dye = inventory.getStackInSlot(INK_SLOT);
            if (dye.isEmpty()) return false;
            inventory.extractItem(INK_SLOT, 1, false);
            inkUnits = PAGES_PER_DYE;
        }
        inkUnits--;
        inventory.extractItem(paperSlot, 1, false);
        return true;
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        for (char[] row : page) {
            String s = new String(row);
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == ' ') end--;
            out.add(s.substring(0, end));
        }
        return out;
    }

    public ItemStackHandler getInventory() { return inventory; }

    public int getPaperCount() { return paperCount(); }

    public int getInkTotal() {
        return inkUnits + inventory.getStackInSlot(INK_SLOT).getCount() * PAGES_PER_DYE;
    }

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, s);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", inventory.serializeNBT());
        tag.putInt("Ink", inkUnits);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) inventory.deserializeNBT(tag.getCompound("Items"));
        inkUnits = tag.getInt("Ink");
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

    @Override
    public String getPeripheralType() { return "printer"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }
}
