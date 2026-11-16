package dev.luacomp.menu;

import dev.luacomp.ModRegistry;
import dev.luacomp.block.ComputerBlockEntity;
import dev.luacomp.network.ComputerInputPacket;
import dev.luacomp.network.ModNetwork;
import dev.luacomp.network.TerminalPacket;
import dev.luacomp.terminal.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

public class ComputerMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ComputerBlockEntity be;   // null no cliente
    private final ServerPlayer serverPlayer; // null no cliente
    private final Terminal terminal;
    private int lastVersion = -1;

    /** Cliente: o terminal começa vazio e é preenchido pelos pacotes do servidor. */
    public static ComputerMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new ComputerMenu(id, inv, null, buf.readBlockPos());
    }

    public ComputerMenu(int id, Inventory inv, ComputerBlockEntity be, BlockPos pos) {
        super(ModRegistry.COMPUTER_MENU.get(), id);
        this.be = be;
        this.pos = pos;
        this.terminal = be != null ? be.getTerminal() : new Terminal();
        this.serverPlayer = inv.player instanceof ServerPlayer sp ? sp : null;
    }

    public Terminal getTerminal() { return terminal; }
    public BlockPos getPos() { return pos; }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverPlayer != null) {
            int v = terminal.version();
            if (v != lastVersion) {
                lastVersion = v;
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                        TerminalPacket.snapshotOf(containerId, terminal));
            }
        }
    }

    public void handleInput(ComputerInputPacket p) {
        if (be != null) be.handleInput(p);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be == null) return true;
        return !be.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
