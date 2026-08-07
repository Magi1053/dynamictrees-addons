package com.skcraft.dtrubber;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class StrippableRubberLogBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> stripped;

    public StrippableRubberLogBlock(Supplier<? extends Block> stripped, Properties properties) {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    public @Nullable BlockState getToolModifiedState(
            BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        if (itemAbility != ItemAbilities.AXE_STRIP) {
            return null;
        }

        return stripped.get()
                .defaultBlockState()
                .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
    }
}
