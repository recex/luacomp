package dev.luacomp.util;

import net.minecraftforge.energy.EnergyStorage;

/** Bateria interna do computador: aceita FE de fora, só o próprio computador consome. */
public class ModEnergy extends EnergyStorage {
    public ModEnergy(int capacity) {
        super(capacity, capacity, 0);
    }

    public int consume(int amount) {
        int taken = Math.min(energy, Math.max(0, amount));
        energy -= taken;
        return taken;
    }

    public void set(int value) {
        energy = Math.max(0, Math.min(capacity, value));
    }
}
