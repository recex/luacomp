package dev.luacomp.peripheral;

import org.luaj.vm2.Varargs;

/** Método de periférico. Sempre executa na thread do servidor (dentro do tick). */
@FunctionalInterface
public interface PeripheralMethod {
    Varargs call(Varargs args, PeripheralAccess ctx);
}
