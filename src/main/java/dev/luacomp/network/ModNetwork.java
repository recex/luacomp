package dev.luacomp.network;

import dev.luacomp.LuaComp;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private ModNetwork() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LuaComp.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, TerminalPacket.class, TerminalPacket::encode, TerminalPacket::decode, TerminalPacket::handle);
        CHANNEL.registerMessage(id++, MonitorPacket.class, MonitorPacket::encode, MonitorPacket::decode, MonitorPacket::handle);
        CHANNEL.registerMessage(id++, ComputerInputPacket.class, ComputerInputPacket::encode, ComputerInputPacket::decode, ComputerInputPacket::handle);
    }
}
