package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralMethod;
import dev.luacomp.terminal.Terminal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Câmera: capture(largura, altura) devolve uma matriz de cores (valores de colors.*) vistas
 * na direção em que a câmera aponta. Cada pixel é um raio andando em passos de meio bloco.
 */
public class CameraBlockEntity extends BlockEntity implements IPeripheral {
    private static final int MAX_W = 32, MAX_H = 24;
    private static final double RANGE = 48.0, STEP = 0.5;
    private static final int COOLDOWN_TICKS = 10;
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(35));

    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();
    private long lastCapture = -1000;

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CAMERA_BE.get(), pos, state);

        methods.put("getMaxSize", (a, ctx) -> LuaValue.varargsOf(LuaValue.valueOf(MAX_W), LuaValue.valueOf(MAX_H)));
        methods.put("capture", (a, ctx) -> {
            int w = Math.max(1, Math.min(MAX_W, a.optint(1, 16)));
            int h = Math.max(1, Math.min(MAX_H, a.optint(2, 12)));
            long now = level.getGameTime();
            if (now - lastCapture < COOLDOWN_TICKS) throw new LuaError("Camera is recharging");
            lastCapture = now;
            return capture(w, h);
        });
    }

    private LuaTable capture(int w, int h) {
        Direction f = getBlockState().getValue(CameraBlock.FACING);
        Direction right = f.getClockWise(); // para quem olha na direção f
        Vec3 fv = new Vec3(f.getStepX(), 0, f.getStepZ());
        Vec3 rv = new Vec3(right.getStepX(), 0, right.getStepZ());
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(fv.scale(0.6));
        double aspect = (double) h / w;

        LuaTable rows = new LuaTable();
        for (int py = 0; py < h; py++) {
            LuaTable row = new LuaTable();
            for (int px = 0; px < w; px++) {
                double nx = ((px + 0.5) / w - 0.5) * 2.0 * TAN_HALF_FOV;
                double ny = (0.5 - (py + 0.5) / h) * 2.0 * TAN_HALF_FOV * aspect;
                Vec3 dir = fv.add(rv.scale(nx)).add(0, ny, 0).normalize();
                row.set(px + 1, LuaValue.valueOf(1 << march(origin, dir)));
            }
            rows.set(py + 1, row);
        }
        return rows;
    }

    /** Índice (0-15) da cor do primeiro bloco atingido; céu = azul-claro. */
    private int march(Vec3 origin, Vec3 dir) {
        for (double t = 0; t < RANGE; t += STEP) {
            Vec3 p = origin.add(dir.scale(t));
            BlockPos bp = BlockPos.containing(p);
            if (!level.isLoaded(bp)) return 15;
            BlockState st = level.getBlockState(bp);
            if (!st.isAir()) {
                return nearestColour(st.getMapColor(level, bp).col);
            }
        }
        return 3; // lightBlue
    }

    private static int nearestColour(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            int c = Terminal.PALETTE[i];
            long dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public String getPeripheralType() { return "camera"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }
}
