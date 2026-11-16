package dev.luacomp.block;

import dev.luacomp.ModRegistry;
import dev.luacomp.peripheral.IPeripheral;
import dev.luacomp.peripheral.PeripheralMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraftforge.registries.ForgeRegistries;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Alto-falante: playNote(instrumento, volume, nota) e playSound(nome, volume, tom). */
public class SpeakerBlockEntity extends BlockEntity implements IPeripheral {
    private static final int MAX_SOUNDS_PER_TICK = 8;

    private final Map<String, PeripheralMethod> methods = new LinkedHashMap<>();
    private long lastTick = -1;
    private int soundsThisTick;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SPEAKER_BE.get(), pos, state);

        methods.put("playNote", (a, ctx) -> {
            String name = a.checkjstring(1).toUpperCase(Locale.ROOT);
            float volume = clampVolume((float) a.optdouble(2, 1.0));
            int note = Math.max(0, Math.min(24, a.optint(3, 12)));
            NoteBlockInstrument instrument;
            try {
                instrument = NoteBlockInstrument.valueOf(name);
            } catch (IllegalArgumentException e) {
                throw new LuaError("Invalid instrument, expected one of harp, bass, basedrum, snare, hat, guitar, flute, bell, chime, xylophone, iron_xylophone, cow_bell, didgeridoo, bit, banjo, pling");
            }
            if (!allowSound()) return LuaValue.FALSE;
            float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
            level.playSound(null, worldPosition, instrument.getSoundEvent().value(), SoundSource.RECORDS, volume, pitch);
            return LuaValue.TRUE;
        });

        methods.put("playSound", (a, ctx) -> {
            ResourceLocation id = ResourceLocation.tryParse(a.checkjstring(1));
            if (id == null) throw new LuaError("Malformed sound name");
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
            if (sound == null) return LuaValue.FALSE;
            float volume = clampVolume((float) a.optdouble(2, 1.0));
            float pitch = (float) Math.max(0.5, Math.min(2.0, a.optdouble(3, 1.0)));
            if (!allowSound()) return LuaValue.FALSE;
            level.playSound(null, worldPosition, sound, SoundSource.RECORDS, volume, pitch);
            return LuaValue.TRUE;
        });
    }

    private static float clampVolume(float v) {
        return Math.max(0f, Math.min(3f, v));
    }

    private boolean allowSound() {
        if (level == null) return false;
        long now = level.getGameTime();
        if (now != lastTick) {
            lastTick = now;
            soundsThisTick = 0;
        }
        return ++soundsThisTick <= MAX_SOUNDS_PER_TICK;
    }

    @Override
    public String getPeripheralType() { return "speaker"; }

    @Override
    public Map<String, PeripheralMethod> getPeripheralMethods() { return methods; }
}
