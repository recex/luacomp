package dev.luacomp.lua;

import dev.luacomp.LuaCompConfig;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

/**
 * Usa o gancho de instruções do LuaJ para abortar scripts que ficam muito tempo sem
 * ceder a vez (loops infinitos). A biblioteca "debug" em si é escondida dos scripts.
 */
public class LimitedDebugLib extends DebugLib {
    private volatile long deadline = Long.MAX_VALUE;
    private int counter;

    public void resetDeadline() {
        deadline = System.nanoTime() + LuaCompConfig.cpuTimeoutNanos();
    }

    private static final Runtime RUNTIME = Runtime.getRuntime();

    @Override
    public void onInstruction(int pc, Varargs v, int top) {
        super.onInstruction(pc, v, top);
        int c = ++counter;
        if ((c & 0x3FFF) == 0) {
            // proteção do servidor: aborta o script se a memória da JVM estiver quase no fim
            long used = RUNTIME.totalMemory() - RUNTIME.freeMemory();
            if (used > RUNTIME.maxMemory() * 0.92) throw new LuaError("Out of memory");
        }
        if ((c & 0xFFF) == 0 && System.nanoTime() > deadline) {
            // dá uma folga para o código de tratamento de erro do shell rodar
            deadline = System.nanoTime() + 2_000_000_000L;
            throw new LuaError("Too long without yielding");
        }
    }
}
