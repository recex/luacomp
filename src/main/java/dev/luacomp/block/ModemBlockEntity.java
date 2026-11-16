package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralAccess;
import dev.luacomp.peripheral.PeripheralMethod;
import dev.luacomp.peripheral.Payloads;
import dev.luacomp.peripheral.WirelessNetwork;
import dev.luacomp.util.PeripheralIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ModemBlockEntity extends BlockEntity implements IPeripheral {
    private static final int MAX_OPEN_CHANNELS = 128;

    /** Canais abertos por computador (só a thread do servidor mexe aqui). */
    private final Map<PeripheralAccess, Set<Integer>> channels = new HashMap<>();
    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();

    private int peripheralId = -1;
    private String peripheralIdType = "";

    public ModemBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MODEM_BE.get(), pos, state);

        methods.put("isWireless", (a, ctx) -> LuaValue.TRUE);
        methods.put("open", (a, ctx) -> {
            int ch = channel(a.checkint(1));
            Set<Integer> open = channels.computeIfAbsent(ctx, k -> new HashSet<>());
            if (!open.contains(ch) && open.size() >= MAX_OPEN_CHANNELS) throw new LuaError("Too many open channels");
            open.add(ch);
            return LuaValue.NONE;
        });
        methods.put("close", (a, ctx) -> {
            Set<Integer> open = channels.get(ctx);
            if (open != null) open.remove(channel(a.checkint(1)));
            return LuaValue.NONE;
        });
        methods.put("isOpen", (a, ctx) -> {
            Set<Integer> open = channels.get(ctx);
            return LuaValue.valueOf(open != null && open.contains(channel(a.checkint(1))));
        });
        methods.put("closeAll", (a, ctx) -> {
            Set<Integer> open = channels.get(ctx);
            if (open != null) open.clear();
            return LuaValue.NONE;
        });
        methods.put("transmit", (a, ctx) -> {
            int ch = channel(a.checkint(1));
            int reply = channel(a.checkint(2));
            Object payload = Payloads.toJava(a.arg(3));
            WirelessNetwork.transmit(this, ch, reply, payload);
            return LuaValue.NONE;
        });
    }

    private static int channel(int c) {
        if (c < 0 || c > 65535) throw new LuaError("Expected number in range 0-65535");
        return c;
    }

    public boolean isWired() {
        return getBlockState().getBlock() instanceof ModemBlock mb && mb.isWired();
    }

    public Direction getAttachedDirection() {
        return getBlockState().getValue(ModemBlock.FACING);
    }

    // ---------- registro sem fio ----------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && !isWired()) WirelessNetwork.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) WirelessNetwork.remove(this);
    }

    /** Entrega a mensagem aos computadores ligados a este modem que têm o canal aberto. */
    public void deliver(int channel, int reply, Object payload, double distance) {
        for (Map.Entry<PeripheralAccess, Set<Integer>> e : channels.entrySet()) {
            if (e.getValue().contains(channel)) {
                PeripheralAccess access = e.getKey();
                access.queueEvent("modem_message", access.name(), channel, reply, Payloads.toLua(payload), distance);
            }
        }
    }

    // ---------- IPeripheral (modem sem fio) ----------

    @Override
    public String getPeripheralType() { return "modem"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }

    @Override
    public void onAttach(PeripheralAccess access) {
        channels.putIfAbsent(access, new HashSet<>());
    }

    @Override
    public void onDetach(PeripheralAccess access) {
        channels.remove(access);
    }

    // ---------- nome do periférico preso a um modem com fio ----------

    public String peripheralName(IPeripheral p) {
        String type = p.getPeripheralType();
        if ((peripheralId < 0 || !type.equals(peripheralIdType)) && level instanceof ServerLevel sl) {
            peripheralId = PeripheralIds.get(sl.getServer()).allocate(type);
            peripheralIdType = type;
            setChanged();
        }
        return type + "_" + Math.max(0, peripheralId);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("PeripheralId", peripheralId);
        tag.putString("PeripheralIdType", peripheralIdType);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        peripheralId = tag.contains("PeripheralId") ? tag.getInt("PeripheralId") : -1;
        peripheralIdType = tag.getString("PeripheralIdType");
    }
}
