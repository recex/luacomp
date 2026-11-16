package dev.luacomp.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Página impressa: guarda título e linhas de texto. Clique direito mostra o texto no chat. */
public class PrintoutItem extends Item {
    public PrintoutItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    public static void write(ItemStack stack, String title, List<String> lines) {
        CompoundTag tag = stack.getOrCreateTag();
        if (title != null && !title.isBlank()) tag.putString("Title", title);
        ListTag list = new ListTag();
        for (String l : lines) list.add(StringTag.valueOf(l));
        tag.put("Lines", list);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("Title")) return Component.literal(t.getString("Title"));
        return super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            CompoundTag t = stack.getTag();
            if (t != null && t.contains("Lines")) {
                ListTag lines = t.getList("Lines", 8);
                player.sendSystemMessage(Component.literal("--- " + (t.contains("Title") ? t.getString("Title") : "Printout") + " ---"));
                for (int i = 0; i < lines.size(); i++) {
                    String l = lines.getString(i);
                    if (!l.isBlank()) player.sendSystemMessage(Component.literal(l));
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
