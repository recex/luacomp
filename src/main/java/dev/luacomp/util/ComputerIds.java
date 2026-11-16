package dev.luacomp.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Contador global de IDs de computadores (um por mundo). */
public class ComputerIds extends SavedData {
    private int next = 0;

    public static ComputerIds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ComputerIds::load, ComputerIds::new, "luacomp_ids");
    }

    public static ComputerIds load(CompoundTag tag) {
        ComputerIds ids = new ComputerIds();
        ids.next = tag.getInt("Next");
        return ids;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Next", next);
        return tag;
    }

    public int allocate() {
        int id = next++;
        setDirty();
        return id;
    }
}
