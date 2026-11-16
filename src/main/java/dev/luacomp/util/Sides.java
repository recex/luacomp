package dev.luacomp.util;

import net.minecraft.core.Direction;

/** Lados relativos ao bloco, como no ComputerCraft: a "frente" é a face com a tela. */
public final class Sides {
    private Sides() {}

    public static final String[] NAMES = {"bottom", "top", "back", "front", "right", "left"};

    public static int indexOf(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }

    public static int index(Direction facing, Direction world) {
        if (world == Direction.DOWN) return 0;
        if (world == Direction.UP) return 1;
        if (world == facing) return 3;
        if (world == facing.getOpposite()) return 2;
        if (world == facing.getCounterClockWise()) return 4;
        return 5;
    }

    public static Direction world(Direction facing, int index) {
        return switch (index) {
            case 0 -> Direction.DOWN;
            case 1 -> Direction.UP;
            case 2 -> facing.getOpposite();
            case 3 -> facing;
            case 4 -> facing.getCounterClockWise();
            default -> facing.getClockWise();
        };
    }
}
