package dev.luacomp.network;

import dev.luacomp.client.ClientPacketHandler;
import dev.luacomp.terminal.Terminal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: cópia completa do terminal para a GUI aberta. */
public record TerminalPacket(int containerId, Terminal terminal) {

    public static TerminalPacket snapshotOf(int containerId, Terminal source) {
        Terminal copy = new Terminal();
        copy.copyFrom(source);
        return new TerminalPacket(containerId, copy);
    }

    public static void encode(TerminalPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.containerId);
        p.terminal.write(buf);
    }

    public static TerminalPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        Terminal t = new Terminal();
        t.read(buf);
        return new TerminalPacket(id, t);
    }

    public static void handle(TerminalPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleTerminal(p)));
        ctx.setPacketHandled(true);
    }
}
