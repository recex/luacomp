package dev.luacomp.peripheral;

import dev.luacomp.LuaCompConfig;
import dev.luacomp.block.ModemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registro dos modems sem fio carregados e entrega de mensagens por alcance. */
public final class WirelessNetwork {
    private WirelessNetwork() {}

    private static final Map<ResourceKey<Level>, Set<ModemBlockEntity>> MODEMS = new ConcurrentHashMap<>();

    public static void add(ModemBlockEntity m) {
        Level level = m.getLevel();
        if (level != null) MODEMS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(m);
    }

    public static void remove(ModemBlockEntity m) {
        Level level = m.getLevel();
        if (level == null) return;
        Set<ModemBlockEntity> set = MODEMS.get(level.dimension());
        if (set != null) set.remove(m);
    }

    public static void transmit(ModemBlockEntity sender, int channel, int reply, Object payload) {
        Level level = sender.getLevel();
        if (level == null) return;
        double range = LuaCompConfig.modemRange();
        boolean cross = LuaCompConfig.modemCrossDimension();
        BlockPos from = sender.getBlockPos();

        for (Map.Entry<ResourceKey<Level>, Set<ModemBlockEntity>> e : MODEMS.entrySet()) {
            boolean sameDim = e.getKey().equals(level.dimension());
            if (!sameDim && !cross) continue;
            for (ModemBlockEntity m : e.getValue()) {
                if (m == sender || m.isRemoved()) continue;
                double dist = sameDim ? Math.sqrt(m.getBlockPos().distSqr(from)) : 0.0;
                if (!cross && dist > range) continue;
                m.deliver(channel, reply, payload, dist);
            }
        }
    }
}
