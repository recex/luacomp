package dev.luacomp.peripheral;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cópia profunda de valores Lua para mensagens de rede (cada receptor ganha a sua própria cópia). */
public final class Payloads {
    private Payloads() {}

    private static final int MAX_DEPTH = 16;
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_STRING = 16_384;

    public static Object toJava(LuaValue v) {
        return toJava(v, 0, new int[]{0});
    }

    private static Object toJava(LuaValue v, int depth, int[] count) {
        if (depth > MAX_DEPTH) throw new LuaError("Message is too deeply nested");
        if (v.isnil()) return null;
        if (v.isboolean()) return v.toboolean();
        if (v.type() == LuaValue.TNUMBER) return v.todouble();
        if (v.isstring()) {
            String s = v.tojstring();
            if (s.length() > MAX_STRING) throw new LuaError("Message string is too long");
            return s;
        }
        if (v.istable()) {
            Map<Object, Object> m = new LinkedHashMap<>();
            LuaValue k = LuaValue.NIL;
            while (true) {
                Varargs n = v.next(k);
                k = n.arg1();
                if (k.isnil()) break;
                if (++count[0] > MAX_ENTRIES) throw new LuaError("Message is too large");
                Object key = toJava(k, depth + 1, count);
                if (key == null) continue;
                m.put(key, toJava(n.arg(2), depth + 1, count));
            }
            return m;
        }
        throw new LuaError("Cannot send " + v.typename() + " in a message");
    }

    @SuppressWarnings("unchecked")
    public static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof Boolean b) return LuaValue.valueOf(b.booleanValue());
        if (o instanceof Double d) return LuaValue.valueOf(d.doubleValue());
        if (o instanceof String s) return LuaValue.valueOf(s);
        if (o instanceof Map<?, ?> m) {
            LuaTable t = new LuaTable();
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) m).entrySet()) {
                t.set(toLua(e.getKey()), toLua(e.getValue()));
            }
            return t;
        }
        return LuaValue.NIL;
    }
}
