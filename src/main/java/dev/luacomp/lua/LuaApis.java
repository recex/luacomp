package dev.luacomp.lua;

import dev.luacomp.block.ComputerBlockEntity;
import dev.luacomp.fs.ComputerFS;
import dev.luacomp.fs.FSException;
import dev.luacomp.terminal.Terminal;
import dev.luacomp.util.Sides;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** APIs Lua que dependem só do terminal, do sistema de arquivos e da redstone. */
public final class LuaApis {
    private LuaApis() {}

    @FunctionalInterface
    public interface Fn {
        Varargs call(Varargs args);
    }

    public static void fn(LuaTable table, String name, Fn f) {
        table.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return f.call(args);
                } catch (FSException e) {
                    throw new LuaError(e.getMessage());
                }
            }
        });
    }

    /** Registra a mesma função sob vários nomes (aliases como getAnalogInput/getAnalogueInput). */
    public static void fn(LuaTable table, Fn f, String... names) {
        for (String n : names) fn(table, n, f);
    }

    // ------------------------------------------------------------------ term

    private static int colourIndex(int c) {
        if (c <= 0 || c > 0x8000 || Integer.bitCount(c) != 1) throw new LuaError("Invalid color (got " + c + ")");
        return Integer.numberOfTrailingZeros(c);
    }

    private static int unit(double v) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, v)) * 255.0);
    }

    /** Métodos de terminal compartilhados entre o computador (global term) e o periférico monitor. */
    public static Map<String, Fn> termFns(Terminal term) {
        Map<String, Fn> m = new LinkedHashMap<>();
        m.put("write", a -> { term.write(a.arg(1).tojstring()); return LuaValue.NONE; });
        m.put("blit", a -> {
            String s = a.checkjstring(1), f = a.checkjstring(2), b = a.checkjstring(3);
            if (s.length() != f.length() || s.length() != b.length()) throw new LuaError("Arguments must be the same length");
            term.blit(s, f, b);
            return LuaValue.NONE;
        });
        m.put("clear", a -> { term.clear(); return LuaValue.NONE; });
        m.put("clearLine", a -> { term.clearLine(); return LuaValue.NONE; });
        m.put("scroll", a -> { term.scroll(a.checkint(1)); return LuaValue.NONE; });
        m.put("setCursorPos", a -> { term.setCursor(a.checkint(1) - 1, a.checkint(2) - 1); return LuaValue.NONE; });
        m.put("getCursorPos", a -> LuaValue.varargsOf(LuaValue.valueOf(term.cursorX() + 1), LuaValue.valueOf(term.cursorY() + 1)));
        m.put("getSize", a -> LuaValue.varargsOf(LuaValue.valueOf(term.width()), LuaValue.valueOf(term.height())));
        m.put("setCursorBlink", a -> { term.setBlink(a.arg(1).toboolean()); return LuaValue.NONE; });
        m.put("getCursorBlink", a -> LuaValue.valueOf(term.blink()));
        Fn setFg = a -> { term.setFg(colourIndex(a.checkint(1))); return LuaValue.NONE; };
        Fn setBg = a -> { term.setBg(colourIndex(a.checkint(1))); return LuaValue.NONE; };
        Fn getFg = a -> LuaValue.valueOf(1 << term.fg());
        Fn getBg = a -> LuaValue.valueOf(1 << term.bg());
        Fn isColor = a -> LuaValue.TRUE;
        m.put("setTextColor", setFg); m.put("setTextColour", setFg);
        m.put("setBackgroundColor", setBg); m.put("setBackgroundColour", setBg);
        m.put("getTextColor", getFg); m.put("getTextColour", getFg);
        m.put("getBackgroundColor", getBg); m.put("getBackgroundColour", getBg);
        m.put("isColor", isColor); m.put("isColour", isColor);

        Fn setPalette = a -> {
            int idx = colourIndex(a.checkint(1));
            int rgb;
            if (a.narg() >= 4) {
                rgb = (unit(a.checkdouble(2)) << 16) | (unit(a.checkdouble(3)) << 8) | unit(a.checkdouble(4));
            } else {
                rgb = a.checkint(2);
            }
            term.setPalette(idx, rgb);
            return LuaValue.NONE;
        };
        Fn getPalette = a -> {
            int c = term.palette(colourIndex(a.checkint(1)));
            return LuaValue.varargsOf(
                    LuaValue.valueOf(((c >> 16) & 0xFF) / 255.0),
                    LuaValue.valueOf(((c >> 8) & 0xFF) / 255.0),
                    LuaValue.valueOf((c & 0xFF) / 255.0));
        };
        m.put("setPaletteColor", setPalette); m.put("setPaletteColour", setPalette);
        m.put("getPaletteColor", getPalette); m.put("getPaletteColour", getPalette);
        return m;
    }

    public static void putAll(LuaTable t, Map<String, Fn> fns) {
        for (Map.Entry<String, Fn> e : fns.entrySet()) fn(t, e.getKey(), e.getValue());
    }

    public static void installTerm(Globals g, Terminal term) {
        LuaTable t = new LuaTable();
        putAll(t, termFns(term));
        g.set("term", t);
    }

    // -------------------------------------------------------------- redstone

    private static int side(Varargs a) {
        int i = Sides.indexOf(a.checkjstring(1));
        if (i < 0) throw new LuaError("Invalid side.");
        return i;
    }

    public static void installRedstone(Globals g, LuaMachine machine) {
        LuaTable r = new LuaTable();
        fn(r, "getSides", a -> {
            LuaValue[] v = new LuaValue[Sides.NAMES.length];
            for (int i = 0; i < v.length; i++) v[i] = LuaValue.valueOf(Sides.NAMES[i]);
            return LuaValue.listOf(v);
        });
        fn(r, "getInput", a -> LuaValue.valueOf(machine.be().getInput(side(a)) > 0));
        fn(r, "getOutput", a -> LuaValue.valueOf(machine.be().getOutput(side(a)) > 0));
        fn(r, "setOutput", a -> { machine.be().setOutput(side(a), a.arg(2).toboolean() ? 15 : 0); return LuaValue.NONE; });
        fn(r, a -> LuaValue.valueOf(machine.be().getInput(side(a))), "getAnalogInput", "getAnalogueInput");
        fn(r, a -> LuaValue.valueOf(machine.be().getOutput(side(a))), "getAnalogOutput", "getAnalogueOutput");
        fn(r, a -> { machine.be().setOutput(side(a), a.checkint(2)); return LuaValue.NONE; }, "setAnalogOutput", "setAnalogueOutput");
        g.set("redstone", r);
        g.set("rs", r);
    }

    // -------------------------------------------------------------------- fs

    public static void installFs(Globals g, ComputerFS fs) {
        LuaTable t = new LuaTable();
        fn(t, "list", a -> {
            List<String> l = fs.list(a.checkjstring(1));
            LuaValue[] v = new LuaValue[l.size()];
            for (int i = 0; i < v.length; i++) v[i] = LuaValue.valueOf(l.get(i));
            return LuaValue.listOf(v);
        });
        fn(t, "exists", a -> LuaValue.valueOf(fs.exists(a.checkjstring(1))));
        fn(t, "isDir", a -> LuaValue.valueOf(fs.isDir(a.checkjstring(1))));
        fn(t, "isReadOnly", a -> LuaValue.FALSE);
        fn(t, "getSize", a -> LuaValue.valueOf((double) fs.size(a.checkjstring(1))));
        fn(t, "getFreeSpace", a -> LuaValue.valueOf((double) fs.freeSpace(a.optjstring(1, ""))));
        fn(t, "makeDir", a -> { fs.makeDir(a.checkjstring(1)); return LuaValue.NONE; });
        fn(t, "delete", a -> { fs.delete(a.checkjstring(1)); return LuaValue.NONE; });
        fn(t, "move", a -> { fs.move(a.checkjstring(1), a.checkjstring(2)); return LuaValue.NONE; });
        fn(t, "copy", a -> { fs.copy(a.checkjstring(1), a.checkjstring(2)); return LuaValue.NONE; });
        fn(t, "getName", a -> {
            String c = ComputerFS.clean(a.checkjstring(1), false);
            return LuaValue.valueOf(c.isEmpty() ? "root" : c.substring(c.lastIndexOf('/') + 1));
        });
        fn(t, "getDir", a -> {
            String c = ComputerFS.clean(a.checkjstring(1), false);
            if (c.isEmpty()) return LuaValue.valueOf("..");
            int i = c.lastIndexOf('/');
            return LuaValue.valueOf(i < 0 ? "" : c.substring(0, i));
        });
        fn(t, "combine", a -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= a.narg(); i++) sb.append(a.checkjstring(i)).append('/');
            return LuaValue.valueOf(ComputerFS.clean(sb.toString(), false));
        });
        fn(t, "open", a -> {
            String path = a.checkjstring(1);
            String mode = a.optjstring(2, "r");
            try {
                if (mode.startsWith("r")) return readHandle(fs.read(path));
                if (mode.startsWith("w") || mode.startsWith("a")) return writeHandle(fs, path, mode.startsWith("a"));
                throw new LuaError("Unsupported mode");
            } catch (FSException e) {
                return LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(e.getMessage()));
            }
        });
        g.set("fs", t);
    }

    public static LuaTable readHandle(String content) {
        int[] pos = {0};
        LuaTable h = new LuaTable();
        fn(h, "readAll", a -> {
            String r = content.substring(pos[0]);
            pos[0] = content.length();
            return LuaValue.valueOf(r);
        });
        fn(h, "readLine", a -> {
            if (pos[0] >= content.length()) return LuaValue.NIL;
            boolean keep = a.arg(1).toboolean();
            int nl = content.indexOf('\n', pos[0]);
            String line;
            if (nl < 0) {
                line = content.substring(pos[0]);
                pos[0] = content.length();
            } else {
                line = content.substring(pos[0], keep ? nl + 1 : nl);
                pos[0] = nl + 1;
            }
            if (!keep && line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            return LuaValue.valueOf(line);
        });
        fn(h, "read", a -> {
            if (pos[0] >= content.length()) return LuaValue.NIL;
            int n = Math.max(1, a.optint(1, 1));
            int end = Math.min(content.length(), pos[0] + n);
            String r = content.substring(pos[0], end);
            pos[0] = end;
            return LuaValue.valueOf(r);
        });
        fn(h, "close", a -> LuaValue.NONE);
        return h;
    }

    private static LuaTable writeHandle(ComputerFS fs, String path, boolean append) {
        StringBuilder sb = new StringBuilder(append && fs.exists(path) ? fs.read(path) : "");
        fs.write(path, sb.toString()); // cria/zera o arquivo já, para falhar cedo se não der
        Runnable flusher = () -> fs.write(path, sb.toString());
        fs.registerWriter(flusher);

        LuaTable h = new LuaTable();
        fn(h, "write", a -> { sb.append(a.arg(1).tojstring()); return LuaValue.NONE; });
        fn(h, "writeLine", a -> { sb.append(a.arg(1).tojstring()).append('\n'); return LuaValue.NONE; });
        fn(h, "flush", a -> { flusher.run(); return LuaValue.NONE; });
        fn(h, "close", a -> { flusher.run(); fs.unregisterWriter(flusher); return LuaValue.NONE; });
        return h;
    }
}
