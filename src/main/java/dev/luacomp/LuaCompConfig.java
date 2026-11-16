package dev.luacomp;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Configuração do servidor (config/luacomp-common.toml). */
public final class LuaCompConfig {
    private LuaCompConfig() {}

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue HTTP_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> HTTP_ALLOW;
    private static final ForgeConfigSpec.IntValue HTTP_TIMEOUT_MS;
    private static final ForgeConfigSpec.IntValue HTTP_MAX_BYTES;
    private static final ForgeConfigSpec.ConfigValue<String> PASTEBIN_API_KEY;
    private static final ForgeConfigSpec.ConfigValue<String> STORE_INDEX_URL;

    private static final ForgeConfigSpec.IntValue CPU_TIMEOUT_MS;

    private static final ForgeConfigSpec.BooleanValue ENERGY_ENABLED;
    private static final ForgeConfigSpec.IntValue ENERGY_CAPACITY;
    private static final ForgeConfigSpec.IntValue ENERGY_PER_TICK;
    private static final ForgeConfigSpec.IntValue ENERGY_NS_PER_FE;

    private static final ForgeConfigSpec.IntValue MODEM_RANGE;
    private static final ForgeConfigSpec.BooleanValue MODEM_CROSS_DIMENSION;
    private static final ForgeConfigSpec.IntValue SENSOR_RANGE;

    private static final ForgeConfigSpec.BooleanValue TURTLE_FUEL_ENABLED;
    private static final ForgeConfigSpec.IntValue TURTLE_START_FUEL;
    private static final ForgeConfigSpec.IntValue TURTLE_FUEL_LIMIT;
    private static final ForgeConfigSpec.IntValue TURTLE_DELAY_TICKS;
    private static final ForgeConfigSpec.BooleanValue TURTLE_CAN_DIG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("http");
        HTTP_ENABLED = b.comment("Permite http.get/http.post/pastebin/wget nos computadores.")
                .define("enabled", true);
        HTTP_ALLOW = b.comment("Domínios permitidos (inclui subdomínios). Use \"*\" para liberar todos.",
                        "Endereços privados/locais (localhost, 192.168.x.x, 10.x.x.x...) são sempre bloqueados.")
                .defineListAllowEmpty("allow",
                        List.of("pastebin.com", "raw.githubusercontent.com", "gist.githubusercontent.com", "github.com"),
                        o -> o instanceof String);
        HTTP_TIMEOUT_MS = b.comment("Tempo máximo de cada requisição (ms).")
                .defineInRange("timeoutMs", 10_000, 1_000, 60_000);
        HTTP_MAX_BYTES = b.comment("Tamanho máximo da resposta (bytes).")
                .defineInRange("maxResponseBytes", 1_000_000, 1_024, 16_000_000);
        PASTEBIN_API_KEY = b.comment("Chave de desenvolvedor do Pastebin (necessária só para 'pastebin put'). Fica só no servidor.")
                .define("pastebinApiKey", "");
        STORE_INDEX_URL = b.comment("URL do índice da loja de programas ('store'). Vazio = loja desativada.",
                        "O índice é um arquivo Lua que retorna uma tabela: return { {name=\"x\", url=\"...\", desc=\"...\"}, ... }")
                .define("storeIndexUrl", "");
        b.pop();

        b.push("computer");
        CPU_TIMEOUT_MS = b.comment("Tempo máximo (ms) que um programa pode ficar sem ceder a vez antes de ser abortado.")
                .defineInRange("cpuTimeoutMs", 7_000, 500, 60_000);
        b.pop();

        b.push("energy");
        ENERGY_ENABLED = b.comment("Computadores consomem FE. Desligado por padrão.")
                .define("enabled", false);
        ENERGY_CAPACITY = b.defineInRange("capacity", 20_000, 100, 10_000_000);
        ENERGY_PER_TICK = b.comment("FE por tick enquanto ligado.")
                .defineInRange("perTick", 1, 0, 10_000);
        ENERGY_NS_PER_FE = b.comment("Cada X nanossegundos de CPU gastos pelo Lua custam 1 FE extra.")
                .defineInRange("nanosPerFE", 500_000, 1_000, 1_000_000_000);
        b.pop();

        b.push("peripherals");
        MODEM_RANGE = b.comment("Alcance do modem sem fio (blocos).")
                .defineInRange("modemRange", 64, 1, 100_000);
        MODEM_CROSS_DIMENSION = b.comment("Modems sem fio alcançam outras dimensões (sem limite de distância).")
                .define("modemCrossDimension", false);
        SENSOR_RANGE = b.comment("Raio máximo do sensor de entidades (blocos).")
                .defineInRange("sensorRange", 16, 1, 128);
        b.pop();

        b.push("turtle");
        TURTLE_FUEL_ENABLED = b.comment("Turtles precisam de combustível para se mover.")
                .define("fuelEnabled", true);
        TURTLE_START_FUEL = b.comment("Combustível de um turtle recém-colocado.")
                .defineInRange("startFuel", 20, 0, 1_000_000);
        TURTLE_FUEL_LIMIT = b.defineInRange("fuelLimit", 20_000, 1, 1_000_000);
        TURTLE_DELAY_TICKS = b.comment("Ticks que cada ação (andar, cavar, colocar, atacar) demora.")
                .defineInRange("actionDelayTicks", 4, 0, 100);
        TURTLE_CAN_DIG = b.comment("Turtles podem quebrar blocos.")
                .define("canDig", true);
        b.pop();

        SPEC = b.build();
    }

    // Os getters protegem contra leitura antes da config carregar.

    private static <T> T safe(java.util.function.Supplier<T> s, T fallback) {
        try { return s.get(); } catch (IllegalStateException e) { return fallback; }
    }

    public static boolean httpEnabled() { return safe(HTTP_ENABLED::get, true); }
    public static List<? extends String> httpAllow() { return safe(HTTP_ALLOW::get, List.of("pastebin.com")); }
    public static int httpTimeoutMs() { return safe(HTTP_TIMEOUT_MS::get, 10_000); }
    public static int httpMaxBytes() { return safe(HTTP_MAX_BYTES::get, 1_000_000); }
    public static String pastebinKey() { return safe(PASTEBIN_API_KEY::get, ""); }
    public static String storeIndexUrl() { return safe(STORE_INDEX_URL::get, ""); }
    public static long cpuTimeoutNanos() { return safe(CPU_TIMEOUT_MS::get, 7_000) * 1_000_000L; }
    public static boolean energyEnabled() { return safe(ENERGY_ENABLED::get, false); }
    public static int energyCapacity() { return safe(ENERGY_CAPACITY::get, 20_000); }
    public static int energyPerTick() { return safe(ENERGY_PER_TICK::get, 1); }
    public static int energyNanosPerFE() { return safe(ENERGY_NS_PER_FE::get, 500_000); }
    public static int modemRange() { return safe(MODEM_RANGE::get, 64); }
    public static boolean modemCrossDimension() { return safe(MODEM_CROSS_DIMENSION::get, false); }
    public static int sensorRange() { return safe(SENSOR_RANGE::get, 16); }

    public static boolean turtleFuelEnabled() { return safe(TURTLE_FUEL_ENABLED::get, true); }
    public static int turtleStartFuel() { return safe(TURTLE_START_FUEL::get, 20); }
    public static int turtleFuelLimit() { return safe(TURTLE_FUEL_LIMIT::get, 20_000); }
    public static int turtleDelayTicks() { return safe(TURTLE_DELAY_TICKS::get, 4); }
    public static boolean turtleCanDig() { return safe(TURTLE_CAN_DIG::get, true); }
}
