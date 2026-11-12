package com.skcraft.dtrubber;

import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DtRubberBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DtRubber.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DtRubber.MOD_ID);

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_RUBBER_LOG = BLOCKS.register(
            "stripped_rubber_log",
            () -> new RotatedPillarBlock(woodProperties(MapColor.WOOD))
    );
    public static final DeferredBlock<RotatedPillarBlock> RUBBER_LOG = BLOCKS.register(
            "rubber_log",
            () -> new StrippableRubberLogBlock(STRIPPED_RUBBER_LOG, woodProperties(MapColor.PODZOL))
    );
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_RUBBER_WOOD = BLOCKS.register(
            "stripped_rubber_wood",
            () -> new RotatedPillarBlock(woodProperties(MapColor.WOOD))
    );
    public static final DeferredBlock<RotatedPillarBlock> RUBBER_WOOD = BLOCKS.register(
            "rubber_wood",
            () -> new StrippableRubberLogBlock(STRIPPED_RUBBER_WOOD, woodProperties(MapColor.PODZOL))
    );
    // Keep rubber_leaves reserved for the existing Dynamic Trees canopy block.
    public static final DeferredBlock<LeavesBlock> RUBBER_LEAVES = BLOCKS.register(
            "rubber_leaves_block",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUNGLE_LEAVES))
    );
    public static final DeferredBlock<Block> RUBBER_PLANKS = BLOCKS.register("rubber_planks", () -> new Block(plankProperties()));
    public static final DeferredBlock<SlabBlock> RUBBER_SLAB = BLOCKS.register("rubber_slab", () -> new SlabBlock(plankProperties()));
    public static final DeferredBlock<StairBlock> RUBBER_STAIRS = BLOCKS.register(
            "rubber_stairs",
            () -> new StairBlock(RUBBER_PLANKS.get().defaultBlockState(), plankProperties())
    );
    public static final DeferredBlock<FenceBlock> RUBBER_FENCE = BLOCKS.register("rubber_fence", () -> new FenceBlock(plankProperties()));
    public static final DeferredBlock<FenceGateBlock> RUBBER_FENCE_GATE = BLOCKS.register(
            "rubber_fence_gate",
            () -> new FenceGateBlock(DtRubber.RUBBER_WOOD_TYPE, plankProperties())
    );
    public static final DeferredBlock<DoorBlock> RUBBER_DOOR = BLOCKS.register(
            "rubber_door",
            () -> new DoorBlock(
                    DtRubber.RUBBER_SET,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(3.0F)
                            .noOcclusion()
                            .ignitedByLava()
                            .pushReaction(PushReaction.DESTROY)
            )
    );
    public static final DeferredBlock<TrapDoorBlock> RUBBER_TRAPDOOR = BLOCKS.register(
            "rubber_trapdoor",
            () -> new TrapDoorBlock(
                    DtRubber.RUBBER_SET,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .instrument(NoteBlockInstrument.BASS)
                            .strength(3.0F)
                            .noOcclusion()
                            .isValidSpawn((state, level, pos, type) -> false)
                            .ignitedByLava()
            )
    );
    public static final DeferredBlock<ButtonBlock> RUBBER_BUTTON = BLOCKS.register(
            "rubber_button",
            () -> new ButtonBlock(
                    DtRubber.RUBBER_SET,
                    30,
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .strength(0.5F)
                            .pushReaction(PushReaction.DESTROY)
            )
    );
    public static final DeferredBlock<PressurePlateBlock> RUBBER_PRESSURE_PLATE = BLOCKS.register(
            "rubber_pressure_plate",
            () -> new PressurePlateBlock(
                    DtRubber.RUBBER_SET,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .forceSolidOn()
                            .instrument(NoteBlockInstrument.BASS)
                            .noCollission()
                            .strength(0.5F)
                            .ignitedByLava()
                            .pushReaction(PushReaction.DESTROY)
            )
    );
    public static final DeferredBlock<StandingSignBlock> RUBBER_SIGN = BLOCKS.register(
            "rubber_sign",
            () -> new StandingSignBlock(DtRubber.RUBBER_WOOD_TYPE, signProperties(false))
    );
    public static final DeferredBlock<WallSignBlock> RUBBER_WALL_SIGN = BLOCKS.register(
            "rubber_wall_sign",
            () -> new WallSignBlock(DtRubber.RUBBER_WOOD_TYPE, signProperties(false).lootFrom(RUBBER_SIGN))
    );
    public static final DeferredBlock<CeilingHangingSignBlock> RUBBER_HANGING_SIGN = BLOCKS.register(
            "rubber_hanging_sign",
            () -> new CeilingHangingSignBlock(DtRubber.RUBBER_WOOD_TYPE, signProperties(true))
    );
    public static final DeferredBlock<WallHangingSignBlock> RUBBER_WALL_HANGING_SIGN = BLOCKS.register(
            "rubber_wall_hanging_sign",
            () -> new WallHangingSignBlock(DtRubber.RUBBER_WOOD_TYPE, signProperties(true).lootFrom(RUBBER_HANGING_SIGN))
    );

    public static final DeferredItem<BlockItem> STRIPPED_RUBBER_LOG_ITEM = ITEMS.registerSimpleBlockItem(STRIPPED_RUBBER_LOG);
    public static final DeferredItem<BlockItem> RUBBER_LOG_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_RUBBER_WOOD_ITEM = ITEMS.registerSimpleBlockItem(STRIPPED_RUBBER_WOOD);
    public static final DeferredItem<BlockItem> RUBBER_WOOD_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_WOOD);
    public static final DeferredItem<BlockItem> RUBBER_LEAVES_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_LEAVES);
    public static final DeferredItem<BlockItem> RUBBER_PLANKS_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_PLANKS);
    public static final DeferredItem<BlockItem> RUBBER_SLAB_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_SLAB);
    public static final DeferredItem<BlockItem> RUBBER_STAIRS_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_STAIRS);
    public static final DeferredItem<BlockItem> RUBBER_FENCE_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_FENCE);
    public static final DeferredItem<BlockItem> RUBBER_FENCE_GATE_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_FENCE_GATE);
    public static final DeferredItem<BlockItem> RUBBER_DOOR_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_DOOR);
    public static final DeferredItem<BlockItem> RUBBER_TRAPDOOR_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_TRAPDOOR);
    public static final DeferredItem<BlockItem> RUBBER_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_BUTTON);
    public static final DeferredItem<BlockItem> RUBBER_PRESSURE_PLATE_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_PRESSURE_PLATE);

    public static final DeferredItem<SignItem> RUBBER_SIGN_ITEM = ITEMS.register(
            "rubber_sign",
            () -> new SignItem(new Item.Properties().stacksTo(16), RUBBER_SIGN.get(), RUBBER_WALL_SIGN.get())
    );
    public static final DeferredItem<HangingSignItem> RUBBER_HANGING_SIGN_ITEM = ITEMS.register(
            "rubber_hanging_sign",
            () -> new HangingSignItem(RUBBER_HANGING_SIGN.get(), RUBBER_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16))
    );
    public static final DeferredItem<BoatItem> RUBBER_BOAT_ITEM = ITEMS.register(
            "rubber_boat",
            () -> new BoatItem(false, rubberBoatType(), new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<BoatItem> RUBBER_CHEST_BOAT_ITEM = ITEMS.register(
            "rubber_chest_boat",
            () -> new BoatItem(true, rubberBoatType(), new Item.Properties().stacksTo(1))
    );

    private DtRubberBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    public static void registerDispenserBehaviors() {
        Boat.Type type = rubberBoatType();
        DispenserBlock.registerBehavior(RUBBER_BOAT_ITEM.get(), new BoatDispenseItemBehavior(type));
        DispenserBlock.registerBehavior(RUBBER_CHEST_BOAT_ITEM.get(), new BoatDispenseItemBehavior(type, true));
    }

    public static Boat.Type rubberBoatType() {
        return Boat.Type.byName(DtRubber.WOOD_NAME);
    }

    private static BlockBehaviour.Properties woodProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties plankProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties signProperties(boolean hanging) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASS)
                .noCollission()
                .strength(1.0F)
                .ignitedByLava();
        return hanging ? properties.sound(SoundType.HANGING_SIGN) : properties;
    }
}
