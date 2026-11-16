package dev.luacomp.lua;

import dev.luacomp.block.ComputerBlockEntity;
import dev.luacomp.LuaCompConfig;
import dev.luacomp.fs.ComputerFS;
import dev.luacomp.peripheral.PeripheralApi;
import dev.luacomp.terminal.Terminal;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uma máquina Lua = um computador. O programa principal (bios.lua) roda numa coroutine;
 * cada evento do jogo é entregue com coroutine.resume, e o programa devolve o controle
 * com coroutine.yield (os.pullEvent). Tudo roda fora da thread do servidor.
 */
public class LuaMachine {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "LuaComp-Computer");
        t.setDaemon(true);
        return t;
    });

    private static final Set<String> ROM_RESOURCES = Set.of("shell.lua");

    private volatile ComputerBlockEntity be;
    private final Terminal term;
    private final ComputerFS fs;

    private final Globals globals = new Globals();
    private final LimitedDebugLib limiter = new LimitedDebugLib();
    private final ConcurrentLinkedQueue<LuaValue[]> events = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Long> timers = new LinkedHashMap<>();
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    private final AtomicInteger httpSlots = new AtomicInteger();
    private final AtomicLong cpuNanos = new AtomicLong();

    private LuaValue mainCo, resumeFn, statusFn;
    private LuaValue filter = LuaValue.NIL;
    private int nextTimer = 1;

    private volatile boolean booted;
    private volatile boolean dead;
    private volatile boolean crashed;
    private volatile boolean shutdownRequested;
    private volatile boolean rebootRequested;

    public LuaMachine(ComputerBlockEntity be, Terminal term, ComputerFS fs) {
        this.be = be;
        this.term = term;
        this.fs = fs;
    }

    // ------------------------------------------------------------ estado

    /** Bloco que hospeda esta máquina agora (muda quando um turtle se move). */
    public ComputerBlockEntity be() { return be; }

    public void setOwner(ComputerBlockEntity newOwner) { this.be = newOwner; }

    public ComputerFS fs() { return fs; }

    public boolean isFinished() { return dead; }
    public boolean hasCrashed() { return crashed; }
    public boolean wantsReboot() { return rebootRequested; }

    public void start() {
        EXECUTOR.execute(this::boot);
    }

    public void kill() {
        dead = true;
        events.clear();
        fs.flushAll();
    }

    // ------------------------------------------------------------ utilidades para APIs

    public boolean tryAcquireHttp() {
        if (httpSlots.incrementAndGet() > 16) {
            httpSlots.decrementAndGet();
            return false;
        }
        return true;
    }

    public void releaseHttp() { httpSlots.decrementAndGet(); }

    /** Tempo de CPU gasto em Lua desde a última chamada (usado para cobrar energia). */
    public long drainCpuNanos() { return cpuNanos.getAndSet(0); }

    /** Executa uma tarefa na thread do servidor e espera o resultado (bloqueia só o Lua deste computador). */
    public <T> T callMain(Supplier<T> task) {
        return callMain(0, task);
    }

    /** Igual a callMain, mas a tarefa só roda depois de {@code delayTicks} ticks (ações lentas de turtle). */
    public <T> T callMain(int delayTicks, Supplier<T> task) {
        if (dead) throw new LuaError("Computer is shutting down");
        CompletableFuture<T> future = new CompletableFuture<>();
        long due = be.getGameTime() + Math.max(0, delayTicks);
        Runnable[] self = new Runnable[1];
        self[0] = () -> {
            if (be.getGameTime() < due) {
                be.enqueueMainTask(self[0]); // ainda não é a hora
                return;
            }
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        be.enqueueMainTask(self[0]);
        try {
            return future.get(5000L + Math.max(0, delayTicks) * 50L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new LuaError("Peripheral call timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LuaError("Interrupted");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof LuaError le) throw new LuaError(le.getMessage());
            throw new LuaError(c == null ? "Peripheral error" : String.valueOf(c.getMessage()));
        }
    }

    // ------------------------------------------------------------ eventos

    public void queueEvent(String name, Object... args) {
        LuaValue[] v = new LuaValue[args.length + 1];
        v[0] = LuaValue.valueOf(name);
        for (int i = 0; i < args.length; i++) v[i + 1] = toLua(args[i]);
        enqueue(v);
    }

    private void enqueue(LuaValue[] ev) {
        if (dead || events.size() > 512) return;
        events.add(ev);
        schedule();
    }

    private static LuaValue toLua(Object o) {
        if (o == null) return LuaValue.NIL;
        if (o instanceof LuaValue lv) return lv;
        if (o instanceof Integer i) return LuaValue.valueOf(i.intValue());
        if (o instanceof Double d) return LuaValue.valueOf(d.doubleValue());
        if (o instanceof Boolean b) return LuaValue.valueOf(b.booleanValue());
        return LuaValue.valueOf(o.toString());
    }

    /** Chamado todo tick pelo servidor: dispara timers vencidos. */
    public void tick(long now) {
        if (dead) return;
        List<Integer> due = null;
        synchronized (timers) {
            Iterator<Map.Entry<Integer, Long>> it = timers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> e = it.next();
                if (e.getValue() <= now) {
                    if (due == null) due = new ArrayList<>();
                    due.add(e.getKey());
                    it.remove();
                }
            }
        }
        if (due != null) for (int id : due) queueEvent("timer", id);
    }

    private int startTimer(double seconds) {
        synchronized (timers) {
            int id = nextTimer++;
            timers.put(id, be.getGameTime() + Math.max(1L, Math.round(seconds * 20.0)));
            return id;
        }
    }

    private void cancelTimer(int id) {
        synchronized (timers) { timers.remove(id); }
    }

    // ------------------------------------------------------------ execução

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) EXECUTOR.execute(this::step);
    }

    private synchronized void boot() {
        try {
            setupGlobals();
            LuaApis.installTerm(globals, term);
            LuaApis.installFs(globals, fs);
            LuaApis.installRedstone(globals, this);
            HttpApi.install(globals, this);
            PeripheralApi.install(globals, this);
            TurtleApi.install(globals, this);
            installOs();

            LuaValue main = globals.load(readResource("bios.lua"), "=bios");
            mainCo = globals.get("coroutine").get("create").call(main);
            resume(new LuaValue[0]);
        } catch (Throwable t) {
            fatal(t);
        } finally {
            booted = true;
            if (!events.isEmpty() && !dead) schedule();
        }
    }

    private synchronized void step() {
        try {
            if (!booted || dead) return;
            int guard = 0;
            LuaValue[] ev;
            while (!dead && guard++ < 256 && (ev = events.poll()) != null) {
                String name = ev[0].tojstring();
                if (filter.isnil() || filter.tojstring().equals(name) || name.equals("terminate")) {
                    try {
                        resume(ev);
                    } catch (Throwable t) {
                        fatal(t);
                    }
                }
            }
        } finally {
            scheduled.set(false);
            if (!events.isEmpty() && !dead) schedule();
        }
    }

    private void resume(LuaValue[] args) {
        long started = System.nanoTime();
        try {
            doResume(args);
        } finally {
            cpuNanos.addAndGet(System.nanoTime() - started);
        }
    }

    private void doResume(LuaValue[] args) {
        limiter.resetDeadline();
        LuaValue[] full = new LuaValue[args.length + 1];
        full[0] = mainCo;
        System.arraycopy(args, 0, full, 1, args.length);

        Varargs r = resumeFn.invoke(LuaValue.varargsOf(full));
        if (!r.arg1().toboolean()) {
            fail(r.arg(2).tojstring());
            return;
        }
        if (shutdownRequested) {
            dead = true;
            return;
        }
        if (statusFn.call(mainCo).tojstring().equals("dead")) {
            dead = true;
            return;
        }
        filter = r.arg(2);
    }

    private void fail(String message) {
        crashed = true;
        dead = true;
        term.setFg(14); // vermelho
        for (String line : ("Erro: " + message).split("\n")) term.println(line);
        term.setFg(0);
        term.println("Ctrl+R para reiniciar.");
    }

    private void fatal(Throwable t) {
        fail(t instanceof LuaError ? t.getMessage() : t.toString());
    }

    // ------------------------------------------------------------ ambiente Lua

    private void setupGlobals() {
        // Só bibliotecas seguras: sem io, sem luajava, sem package/require, sem os padrão.
        globals.load(new BaseLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        LoadState.install(globals);
        LuaC.install(globals);
        globals.load(limiter);

        for (String name : new String[]{"dofile", "loadfile", "loadstring", "collectgarbage", "debug", "require", "package", "luajava", "io"}) {
            globals.set(name, LuaValue.NIL);
        }

        LuaValue co = globals.get("coroutine");
        resumeFn = co.get("resume");
        statusFn = co.get("status");
    }

    private void installOs() {
        LuaTable os = new LuaTable();
        LuaApis.fn(os, "queueEvent", a -> {
            if (a.narg() < 1) throw new LuaError("Expected string");
            LuaValue[] v = new LuaValue[a.narg()];
            for (int i = 0; i < v.length; i++) v[i] = a.arg(i + 1);
            enqueue(v);
            return LuaValue.NONE;
        });
        LuaApis.fn(os, "startTimer", a -> LuaValue.valueOf(startTimer(a.checkdouble(1))));
        LuaApis.fn(os, "cancelTimer", a -> { cancelTimer(a.checkint(1)); return LuaValue.NONE; });
        LuaApis.fn(os, "getComputerID", a -> LuaValue.valueOf(be.getComputerId()));
        LuaApis.fn(os, "computerID", a -> LuaValue.valueOf(be.getComputerId()));
        LuaApis.fn(os, "getComputerLabel", a -> be.getLabel() == null ? LuaValue.NIL : LuaValue.valueOf(be.getLabel()));
        LuaApis.fn(os, "setComputerLabel", a -> { be.setLabel(a.isnil(1) ? null : a.checkjstring(1)); return LuaValue.NONE; });
        LuaApis.fn(os, "clock", a -> LuaValue.valueOf(be.getGameTime() * 0.05));
        LuaApis.fn(os, "time", a -> LuaValue.valueOf(((be.getDayTime() % 24000L) / 1000.0 + 6.0) % 24.0));
        LuaApis.fn(os, "day", a -> LuaValue.valueOf((int) (be.getDayTime() / 24000L) + 1));
        LuaApis.fn(os, "epoch", a -> LuaValue.valueOf((double) System.currentTimeMillis()));
        LuaApis.fn(os, "version", a -> LuaValue.valueOf("LuaComp 0.1"));
        LuaApis.fn(os, "getEnergy", a -> LuaValue.valueOf(be.getEnergyStored()));
        LuaApis.fn(os, "getEnergyCapacity", a -> LuaValue.valueOf(be.getEnergyCapacity()));
        LuaApis.fn(os, "isEnergyEnabled", a -> LuaValue.valueOf(LuaCompConfig.energyEnabled()));
        globals.set("os", os);

        // Interno: só a bios enxerga (ela guarda numa local e apaga o global).
        LuaTable sys = new LuaTable();
        LuaApis.fn(sys, "shutdown", a -> { shutdownRequested = true; return LuaValue.NONE; });
        LuaApis.fn(sys, "reboot", a -> { rebootRequested = true; shutdownRequested = true; return LuaValue.NONE; });
        LuaApis.fn(sys, "resource", a -> {
            String name = a.checkjstring(1);
            if (!ROM_RESOURCES.contains(name)) throw new LuaError("No such resource");
            return LuaValue.valueOf(readResource(name));
        });
        globals.set("__sys", sys);
    }

    private static String readResource(String name) {
        try (InputStream in = LuaMachine.class.getResourceAsStream("/assets/luacomp/lua/" + name)) {
            if (in == null) throw new LuaError("Missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LuaError("Cannot read resource " + name);
        }
    }
}
