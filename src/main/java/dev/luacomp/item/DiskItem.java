package dev.luacomp.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Disquete: guarda um ID (a pasta de arquivos fica no mundo) e um nome opcional. */
public class DiskItem extends Item {
    public DiskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getId(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("DiskId") ? t.getInt("DiskId") : -1;
    }

    public static void setId(ItemStack stack, int id) {
        stack.getOrCreateTag().putInt("DiskId", id);
    }

    @Nullable
    public static String getLabel(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("Label") ? t.getString("Label") : null;
    }

    public static void setLabel(ItemStack stack, @Nullable String label) {
        if (label == null || label.isBlank()) {
            if (stack.getTag() != null) stack.getTag().remove("Label");
        } else {
            stack.getOrCreateTag().putString("Label", label.length() > 32 ? label.substring(0, 32) : label);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        String label = getLabel(stack);
        return label != null ? Component.literal(label) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int id = getId(stack);
        if (id >= 0) tooltip.add(Component.literal("Disk #" + id).withStyle(ChatFormatting.GRAY));
    }
}
