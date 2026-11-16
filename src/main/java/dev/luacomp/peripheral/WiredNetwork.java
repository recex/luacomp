package dev.luacomp.peripheral;

import dev.luacomp.block.CableBlock;
import dev.luacomp.block.ModemBlock;
import dev.luacomp.block.ModemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rede de cabos: modems com fio ligados por cabos formam uma rede que expõe os periféricos de cada modem. */
public final class WiredNetwork {
    private WiredNetwork() {}

    private static final int MAX_NODES = 1024;

    public static void collect(Level level, BlockPos start, Map<String, IPeripheral> out) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();

            if (level.getBlockEntity(pos) instanceof ModemBlockEntity m && m.isWired()) {
                BlockPos target = pos.relative(m.getAttachedDirection());
                BlockEntity tb = level.getBlockEntity(target);
                if (tb instanceof IPeripheral p && !(tb instanceof ModemBlockEntity) && !tb.isRemoved()) {
                    out.putIfAbsent(m.peripheralName(p), p);
                }
            }

            for (Direction d : Direction.values()) {
                BlockPos np = pos.relative(d);
                if (seen.contains(np) || seen.size() >= MAX_NODES) continue;
                BlockState st = level.getBlockState(np);
                if (st.getBlock() instanceof CableBlock || (st.getBlock() instanceof ModemBlock mb && mb.isWired())) {
                    seen.add(np);
                    queue.add(np);
                }
            }
        }
    }
}
