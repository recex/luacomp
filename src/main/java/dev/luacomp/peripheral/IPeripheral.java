package dev.luacomp.peripheral;

import java.util.Map;

/** Implementado por BlockEntities que podem ser controlados por um computador. */
public interface IPeripheral {
    String getPeripheralType();

    Map<String, PeripheralMethod> getPeripheralMethods();

    /** O computador passou a enxergar este periférico. */
    default void onAttach(PeripheralAccess access) {}

    /** O computador deixou de enxergar este periférico. */
    default void onDetach(PeripheralAccess access) {}
}
