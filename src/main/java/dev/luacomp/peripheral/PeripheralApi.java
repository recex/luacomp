package dev.luacomp.peripheral;

import dev.luacomp.block.ComputerBlockEntity;
import dev.luacomp.lua.LuaApis;
import dev.luacomp.lua.LuaMachine;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** API "peripheral" do Lua. As chamadas rodam na thread do servidor e bloqueiam só o computador. */
public final class PeripheralApi {
    private PeripheralApi() {}

    public static void install(Globals g, LuaMachine machine) {
        LuaTable p = new LuaTable();

        LuaApis.fn(p, "getNames", a -> {
            List<String> names = machine.callMain(() -> new ArrayList<>(machine.be().scanPeripherals().keySet()));
            LuaValue[] v = new LuaValue[names.size()];
            for (int i = 0; i < v.length; i++) v[i] = LuaValue.valueOf(names.get(i));
            return LuaValue.listOf(v);
        });

        LuaApis.fn(p, "isPresent", a -> {
            String name = a.checkjstring(1);
            boolean present = machine.callMain(() -> machine.be().scanPeripherals().containsKey(name));
            return LuaValue.valueOf(present);
        });

        LuaApis.fn(p, "getType", a -> {
            String name = a.checkjstring(1);
            String type = machine.callMain(() -> {
                IPeripheral per = machine.be().scanPeripherals().get(name);
                return per == null ? null : per.getPeripheralType();
            });
            return type == null ? LuaValue.NIL : LuaValue.valueOf(type);
        });

        LuaApis.fn(p, "getMethods", a -> {
            String name = a.checkjstring(1);
            List<String> methods = machine.callMain(() -> {
                IPeripheral per = machine.be().scanPeripherals().get(name);
                return per == null ? null : new ArrayList<>(per.getPeripheralMethods().keySet());
            });
            if (methods == null) return LuaValue.NIL;
            LuaValue[] v = new LuaValue[methods.size()];
            for (int i = 0; i < v.length; i++) v[i] = LuaValue.valueOf(methods.get(i));
            return LuaValue.listOf(v);
        });

        LuaApis.fn(p, "call", a -> {
            String name = a.checkjstring(1);
            String method = a.checkjstring(2);
            Varargs rest = a.subargs(3);
            return machine.callMain(() -> {
                machine.be().updatePeripherals(true);
                IPeripheral per = machine.be().scanPeripherals().get(name);
                if (per == null) throw new LuaError("No peripheral attached");
                PeripheralMethod m = per.getPeripheralMethods().get(method);
                if (m == null) throw new LuaError("No such method " + method);
                return m.call(rest, machine.be().accessFor(name));
            });
        });

        g.set("peripheral", p);
    }
}
