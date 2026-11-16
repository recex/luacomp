package dev.luacomp.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Numeração estável dos periféricos em rede (monitor_0, monitor_1, speaker_0...). */
public class PeripheralIds extends SavedData {
    private final CompoundTag counters = new CompoundTag();

    public static PeripheralIds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PeripheralIds::load, PeripheralIds::new, "luacomp_peripheral_ids");
    }

    public static PeripheralIds load(CompoundTag tag) {
        PeripheralIds ids = new PeripheralIds();
        for (String key : tag.getAllKeys()) ids.counters.putInt(key, tag.getInt(key));
        return ids;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (String key : counters.getAllKeys()) tag.putInt(key, counters.getInt(key));
        return tag;
    }

    public int allocate(String type) {
        int id = counters.getInt(type);
        counters.putInt(type, id + 1);
        setDirty();
        return id;
    }
}
