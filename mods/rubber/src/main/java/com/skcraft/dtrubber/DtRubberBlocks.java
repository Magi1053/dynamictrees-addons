package com.skcraft.dtrubber;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
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

    public static final DeferredBlock<Block> RUBBER_PLANKS = BLOCKS.register(
            "rubber_planks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .ignitedByLava())
    );

    public static final DeferredItem<BlockItem> STRIPPED_RUBBER_LOG_ITEM = ITEMS.register(
            "stripped_rubber_log",
            () -> new BlockItem(STRIPPED_RUBBER_LOG.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> RUBBER_LOG_ITEM = ITEMS.register(
            "rubber_log",
            () -> new BlockItem(RUBBER_LOG.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> STRIPPED_RUBBER_WOOD_ITEM = ITEMS.register(
            "stripped_rubber_wood",
            () -> new BlockItem(STRIPPED_RUBBER_WOOD.get(), new Item.Properties())
    );

    public static final DeferredItem<BlockItem> RUBBER_PLANKS_ITEM = ITEMS.register(
            "rubber_planks",
            () -> new BlockItem(RUBBER_PLANKS.get(), new Item.Properties())
    );

    private DtRubberBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private static BlockBehaviour.Properties woodProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }
}
