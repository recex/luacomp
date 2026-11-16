package dev.luacomp.lua;

import dev.luacomp.LuaCompConfig;
import dev.luacomp.block.TurtleBlockEntity;
import net.minecraft.core.Direction;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** API "turtle" (só existe quando o computador é um turtle). Cada chamada roda na thread do servidor. */
public final class TurtleApi {
    private TurtleApi() {}

    @FunctionalInterface
    private interface TFn {
        Varargs call(TurtleBlockEntity turtle, Varargs args);
    }

    private static void reg(LuaTable table, LuaMachine m, String name, boolean slow, TFn f) {
        LuaApis.fn(table, name, a -> m.callMain(slow ? LuaCompConfig.turtleDelayTicks() : 0, () -> {
            if (!(m.be() instanceof TurtleBlockEntity t)) throw new LuaError("Not a turtle");
            return f.call(t, a);
        }));
    }

    private static LuaValue bool(boolean b) { return LuaValue.valueOf(b); }

    public static void install(Globals g, LuaMachine m) {
        if (!(m.be() instanceof TurtleBlockEntity)) return;
        LuaTable t = new LuaTable();

        // ----- movimento -----
        reg(t, m, "forward", true, (tb, a) -> tb.move(tb.front()));
        reg(t, m, "back", true, (tb, a) -> tb.move(tb.front().getOpposite()));
        reg(t, m, "up", true, (tb, a) -> tb.move(Direction.UP));
        reg(t, m, "down", true, (tb, a) -> tb.move(Direction.DOWN));
        reg(t, m, "turnLeft", true, (tb, a) -> tb.turn(false));
        reg(t, m, "turnRight", true, (tb, a) -> tb.turn(true));

        // ----- blocos -----
        reg(t, m, "dig", true, (tb, a) -> tb.dig(tb.front()));
        reg(t, m, "digUp", true, (tb, a) -> tb.dig(Direction.UP));
        reg(t, m, "digDown", true, (tb, a) -> tb.dig(Direction.DOWN));
        reg(t, m, "place", true, (tb, a) -> tb.place(tb.front()));
        reg(t, m, "placeUp", true, (tb, a) -> tb.place(Direction.UP));
        reg(t, m, "placeDown", true, (tb, a) -> tb.place(Direction.DOWN));
        reg(t, m, "attack", true, (tb, a) -> tb.attack(tb.front()));
        reg(t, m, "attackUp", true, (tb, a) -> tb.attack(Direction.UP));
        reg(t, m, "attackDown", true, (tb, a) -> tb.attack(Direction.DOWN));

        reg(t, m, "detect", false, (tb, a) -> bool(tb.detect(tb.front())));
        reg(t, m, "detectUp", false, (tb, a) -> bool(tb.detect(Direction.UP)));
        reg(t, m, "detectDown", false, (tb, a) -> bool(tb.detect(Direction.DOWN)));
        reg(t, m, "inspect", false, (tb, a) -> tb.inspect(tb.front()));
        reg(t, m, "inspectUp", false, (tb, a) -> tb.inspect(Direction.UP));
        reg(t, m, "inspectDown", false, (tb, a) -> tb.inspect(Direction.DOWN));

        // ----- itens -----
        reg(t, m, "drop", false, (tb, a) -> tb.drop(tb.front(), a.optint(1, 0)));
        reg(t, m, "dropUp", false, (tb, a) -> tb.drop(Direction.UP, a.optint(1, 0)));
        reg(t, m, "dropDown", false, (tb, a) -> tb.drop(Direction.DOWN, a.optint(1, 0)));
        reg(t, m, "suck", false, (tb, a) -> tb.suck(tb.front(), a.optint(1, 0)));
        reg(t, m, "suckUp", false, (tb, a) -> tb.suck(Direction.UP, a.optint(1, 0)));
        reg(t, m, "suckDown", false, (tb, a) -> tb.suck(Direction.DOWN, a.optint(1, 0)));

        reg(t, m, "select", false, (tb, a) -> tb.select(a.checkint(1)));
        reg(t, m, "getSelectedSlot", false, (tb, a) -> LuaValue.valueOf(tb.selectedSlot()));
        reg(t, m, "getItemCount", false, (tb, a) -> LuaValue.valueOf(tb.itemCount(a.optint(1, 0))));
        reg(t, m, "getItemSpace", false, (tb, a) -> LuaValue.valueOf(tb.itemSpace(a.optint(1, 0))));
        reg(t, m, "getItemDetail", false, (tb, a) -> tb.itemDetail(a.optint(1, 0)));

        // ----- combustível -----
        reg(t, m, "getFuelLevel", false, (tb, a) -> tb.fuelLevel());
        reg(t, m, "getFuelLimit", false, (tb, a) -> tb.fuelLimit());
        reg(t, m, "refuel", false, (tb, a) -> tb.refuel(a.optint(1, 0)));

        g.set("turtle", t);
    }
}
