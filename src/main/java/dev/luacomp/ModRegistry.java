package dev.luacomp;

import dev.luacomp.block.CableBlock;
import dev.luacomp.block.CameraBlock;
import dev.luacomp.block.CameraBlockEntity;
import dev.luacomp.block.ComputerBlock;
import dev.luacomp.block.ComputerBlockEntity;
import dev.luacomp.block.DiskDriveBlock;
import dev.luacomp.block.DiskDriveBlockEntity;
import dev.luacomp.block.ModemBlock;
import dev.luacomp.block.ModemBlockEntity;
import dev.luacomp.block.MonitorBlock;
import dev.luacomp.block.MonitorBlockEntity;
import dev.luacomp.block.PeripheralBlock;
import dev.luacomp.block.PrinterBlock;
import dev.luacomp.block.PrinterBlockEntity;
import dev.luacomp.block.SensorBlockEntity;
import dev.luacomp.block.SpeakerBlockEntity;
import dev.luacomp.block.TurtleBlock;
import dev.luacomp.block.TurtleBlockEntity;
import dev.luacomp.item.DiskItem;
import dev.luacomp.item.PrintoutItem;
import dev.luacomp.menu.ComputerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, LuaComp.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LuaComp.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LuaComp.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, LuaComp.MODID);

    /** Itens que entram na aba Redstone do criativo. */
    public static final List<RegistryObject<Item>> CREATIVE_ITEMS = new ArrayList<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL);
    }

    private static RegistryObject<Block> block(String name, java.util.function.Supplier<Block> factory) {
        RegistryObject<Block> b = BLOCKS.register(name, factory);
        RegistryObject<Item> item = ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        CREATIVE_ITEMS.add(item);
        return b;
    }

    // ---------- blocos ----------

    public static final RegistryObject<Block> COMPUTER = block("computer", () -> new ComputerBlock(metal()));
    public static final RegistryObject<Block> MONITOR = block("monitor", () -> new MonitorBlock(metal()));
    public static final RegistryObject<Block> WIRELESS_MODEM = block("wireless_modem", () -> new ModemBlock(metal().noOcclusion(), false));
    public static final RegistryObject<Block> WIRED_MODEM = block("wired_modem", () -> new ModemBlock(metal().noOcclusion(), true));
    public static final RegistryObject<Block> CABLE = block("cable", () -> new CableBlock(metal().strength(1.0F).noOcclusion()));
    public static final RegistryObject<Block> SPEAKER = block("speaker",
            () -> new PeripheralBlock(metal(), SpeakerBlockEntity::new));
    public static final RegistryObject<Block> SENSOR = block("sensor",
            () -> new PeripheralBlock(metal(), SensorBlockEntity::new));
    public static final RegistryObject<Block> CAMERA = block("camera", () -> new CameraBlock(metal()));
    public static final RegistryObject<Block> DISK_DRIVE = block("disk_drive", () -> new DiskDriveBlock(metal()));
    public static final RegistryObject<Block> TURTLE = block("turtle", () -> new TurtleBlock(metal()));
    public static final RegistryObject<Block> PRINTER = block("printer", () -> new PrinterBlock(metal()));

    // ---------- itens ----------

    public static final RegistryObject<Item> DISK = ITEMS.register("disk", () -> new DiskItem(new Item.Properties()));
    public static final RegistryObject<Item> PRINTOUT = ITEMS.register("printout", () -> new PrintoutItem(new Item.Properties()));
    static {
        CREATIVE_ITEMS.add(DISK);
    }

    // ---------- block entities ----------

    public static final RegistryObject<BlockEntityType<ComputerBlockEntity>> COMPUTER_BE = BLOCK_ENTITIES.register("computer",
            () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, COMPUTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<MonitorBlockEntity>> MONITOR_BE = BLOCK_ENTITIES.register("monitor",
            () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, MONITOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ModemBlockEntity>> MODEM_BE = BLOCK_ENTITIES.register("modem",
            () -> BlockEntityType.Builder.of(ModemBlockEntity::new, WIRELESS_MODEM.get(), WIRED_MODEM.get()).build(null));

    public static final RegistryObject<BlockEntityType<SpeakerBlockEntity>> SPEAKER_BE = BLOCK_ENTITIES.register("speaker",
            () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, SPEAKER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SensorBlockEntity>> SENSOR_BE = BLOCK_ENTITIES.register("sensor",
            () -> BlockEntityType.Builder.of(SensorBlockEntity::new, SENSOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<CameraBlockEntity>> CAMERA_BE = BLOCK_ENTITIES.register("camera",
            () -> BlockEntityType.Builder.of(CameraBlockEntity::new, CAMERA.get()).build(null));

    public static final RegistryObject<BlockEntityType<DiskDriveBlockEntity>> DISK_DRIVE_BE = BLOCK_ENTITIES.register("disk_drive",
            () -> BlockEntityType.Builder.of(DiskDriveBlockEntity::new, DISK_DRIVE.get()).build(null));

    public static final RegistryObject<BlockEntityType<TurtleBlockEntity>> TURTLE_BE = BLOCK_ENTITIES.register("turtle",
            () -> BlockEntityType.Builder.of(TurtleBlockEntity::new, TURTLE.get()).build(null));

    public static final RegistryObject<BlockEntityType<PrinterBlockEntity>> PRINTER_BE = BLOCK_ENTITIES.register("printer",
            () -> BlockEntityType.Builder.of(PrinterBlockEntity::new, PRINTER.get()).build(null));

    // ---------- menus ----------

    public static final RegistryObject<MenuType<ComputerMenu>> COMPUTER_MENU = MENUS.register("computer",
            () -> IForgeMenuType.create(ComputerMenu::fromNetwork));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
    }
}
