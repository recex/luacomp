package dev.luacomp.network;

import dev.luacomp.menu.ComputerMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: teclas, texto colado e comandos de energia. */
public record ComputerInputPacket(int containerId, Type type, int code, boolean flag, String text) {
    public enum Type { KEY, KEY_UP, CHAR, PASTE, TERMINATE, SHUTDOWN, REBOOT }

    public static final int MAX_TEXT = 16000;

    public static ComputerInputPacket key(int id, int code, boolean held) { return new ComputerInputPacket(id, Type.KEY, code, held, ""); }
    public static ComputerInputPacket keyUp(int id, int code) { return new ComputerInputPacket(id, Type.KEY_UP, code, false, ""); }
    public static ComputerInputPacket chr(int id, char c) { return new ComputerInputPacket(id, Type.CHAR, 0, false, String.valueOf(c)); }
    public static ComputerInputPacket paste(int id, String s) { return new ComputerInputPacket(id, Type.PASTE, 0, false, s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s); }
    public static ComputerInputPacket command(int id, Type t) { return new ComputerInputPacket(id, t, 0, false, ""); }

    public static void encode(ComputerInputPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.containerId);
        buf.writeEnum(p.type);
        buf.writeInt(p.code);
        buf.writeBoolean(p.flag);
        buf.writeUtf(p.text, MAX_TEXT * 4);
    }

    public static ComputerInputPacket decode(FriendlyByteBuf buf) {
        return new ComputerInputPacket(buf.readVarInt(), buf.readEnum(Type.class), buf.readInt(), buf.readBoolean(), buf.readUtf(MAX_TEXT * 4));
    }

    public static void handle(ComputerInputPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null && sender.containerMenu instanceof ComputerMenu menu && menu.containerId == p.containerId) {
                menu.handleInput(p);
            }
        });
        ctx.setPacketHandled(true);
    }
}
