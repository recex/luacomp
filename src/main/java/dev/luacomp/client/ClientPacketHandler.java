package dev.luacomp.client;

import dev.luacomp.block.MonitorBlockEntity;
import dev.luacomp.menu.ComputerMenu;
import dev.luacomp.network.MonitorPacket;
import dev.luacomp.network.TerminalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Só é carregada no cliente (via DistExecutor). */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleTerminal(TerminalPacket pkt) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ComputerMenu menu && menu.containerId == pkt.containerId()) {
            menu.getTerminal().copyFrom(pkt.terminal());
        }
    }

    public static void handleMonitor(MonitorPacket pkt) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pkt.pos()) instanceof MonitorBlockEntity monitor) {
            monitor.applyClientData(pkt.data());
        }
    }
}
