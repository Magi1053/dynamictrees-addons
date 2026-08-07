package com.skcraft.dtbetterend.systems.leaves;

import com.dtteam.dynamictrees.api.registry.TypedRegistry;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Dynamic leaves that occasionally place BetterEnd outer fur/leaves on adjacent air faces.
 */
public class FurOuterLeaveProperties extends LeavesProperties {
    public static final TypedRegistry.EntryType<LeavesProperties> TYPE =
            TypedRegistry.newType(FurOuterLeaveProperties::new);

    private Block outerBlock = Blocks.AIR;

    public FurOuterLeaveProperties(ResourceLocation registryName) {
        super(registryName);
    }

    public void setOuterBlock(Block outerBlock) {
        this.outerBlock = outerBlock == null ? Blocks.AIR : outerBlock;
    }

    public Block getOuterBlock() {
        return outerBlock;
    }

    @Override
    protected DynamicLeavesBlock createDynamicLeaves(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        return new FurOuterDynamicLeavesBlock(this, properties);
    }

    public static class FurOuterDynamicLeavesBlock extends DynamicLeavesBlock {
        public FurOuterDynamicLeavesBlock(LeavesProperties properties, net.minecraft.world.level.block.state.BlockBehaviour.Properties blockProperties) {
            super(properties, blockProperties);
        }

        @Override
        public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            super.randomTick(state, level, pos, random);
            if (!(properties instanceof FurOuterLeaveProperties fur) || fur.getOuterBlock() == Blocks.AIR) {
                return;
            }
            if (random.nextInt(4) != 0) {
                return;
            }
            Direction dir = Direction.getRandom(random);
            BlockPos target = pos.relative(dir);
            if (!level.isEmptyBlock(target)) {
                return;
            }
            BlockState place = fur.getOuterBlock().defaultBlockState();
            if (place.hasProperty(BlockStateProperties.FACING)) {
                place = place.setValue(BlockStateProperties.FACING, dir.getOpposite());
            }
            if (place.canSurvive(level, target)) {
                level.setBlock(target, place, Block.UPDATE_ALL);
            }
        }
    }
}
