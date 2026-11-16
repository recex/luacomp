package dev.luacomp.peripheral;

import dev.luacomp.block.ComputerBlockEntity;

/** Ligação entre um computador e um nome de periférico (ex.: "left" ou "monitor_3"). */
public final class PeripheralAccess {
    private final ComputerBlockEntity computer;
    private final String name;

    public PeripheralAccess(ComputerBlockEntity computer, String name) {
        this.computer = computer;
        this.name = name;
    }

    public ComputerBlockEntity computer() { return computer; }

    public String name() { return name; }

    public void queueEvent(String event, Object... args) {
        computer.queueMachineEvent(event, args);
    }
}
