package dev.luacomp.block;

import dev.luacomp.LuaCompConfig;
import dev.luacomp.ModRegistry;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sensor de entidades: getEntities([raio]) e getPlayers([raio]), com posições relativas ao sensor. */
public class SensorBlockEntity extends BlockEntity implements IPeripheral {
    private static final int MAX_RESULTS = 64;

    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();

    public SensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SENSOR_BE.get(), pos, state);
        methods.put("getEntities", (a, ctx) -> scan(a, false));
        methods.put("getPlayers", (a, ctx) -> scan(a, true));
        methods.put("getRange", (a, ctx) -> LuaValue.valueOf(LuaCompConfig.sensorRange()));
    }

    private Varargs scan(Varargs a, boolean playersOnly) {
        if (level == null) return LuaValue.NIL;
        double max = LuaCompConfig.sensorRange();
        double radius = Math.max(1.0, Math.min(max, a.optdouble(1, max)));
        Vec3 center = Vec3.atCenterOf(worldPosition);

        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(worldPosition).inflate(radius),
                e -> e.distanceToSqr(center) <= radius * radius && (!playersOnly || e instanceof ServerPlayer));
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));

        LuaTable result = new LuaTable();
        int n = 0;
        for (LivingEntity e : found) {
            if (n >= MAX_RESULTS) break;
            LuaTable t = new LuaTable();
            ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            t.set("name", LuaValue.valueOf(e.getName().getString()));
            t.set("type", LuaValue.valueOf(type == null ? "unknown" : type.toString()));
            t.set("x", LuaValue.valueOf(e.getX() - center.x));
            t.set("y", LuaValue.valueOf(e.getY() - center.y));
            t.set("z", LuaValue.valueOf(e.getZ() - center.z));
            t.set("health", LuaValue.valueOf((double) e.getHealth()));
            t.set("maxHealth", LuaValue.valueOf((double) e.getMaxHealth()));
            result.set(++n, t);
        }
        return result;
    }

    @Override
    public String getPeripheralType() { return "sensor"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }
}
