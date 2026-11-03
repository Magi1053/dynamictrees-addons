package com.skcraft.dtsable.compat.bluemap3d;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import dev.duzo.bluemap3d.api.BlockAppearance;
import dev.duzo.bluemap3d.api.BlockVolume;
import dev.duzo.bluemap3d.api.ModelAttachment;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Preserves the ship snapshot and adds neighbor-aware appearances for Dynamic Trees. */
public final class DynamicTreeVolume implements BlockVolume, BlockGetter {
    private final BlockVolume original;

    private DynamicTreeVolume(BlockVolume original) { this.original = original; }

    public static BlockVolume wrap(BlockVolume original) {
        return original instanceof DynamicTreeVolume ? original : new DynamicTreeVolume(original);
    }

    @Override public BlockPos min() { return original.min(); }
    @Override public BlockPos max() { return original.max(); }
    @Override public Vec3 pivot() { return original.pivot(); }
    @Override public BlockState stateAt(int x, int y, int z) { return original.stateAt(x, y, z); }
    @Override public void forEachBlock(BlockConsumer consumer) { original.forEachBlock(consumer); }
    @Override public int blockCount() { return original.blockCount(); }
    @Override public Collection<ModelAttachment> attachments() { return original.attachments(); }
    @Override public BlockState getBlockState(BlockPos pos) { return stateAt(pos.getX(), pos.getY(), pos.getZ()); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public int getMinBuildHeight() { return min().getY(); }
    @Override public int getHeight() { return max().getY() - min().getY() + 1; }

    @Override
    public BlockAppearance appearanceAt(int x, int y, int z) {
        BlockAppearance existing = original.appearanceAt(x, y, z);
        if (existing != null) return existing;
        BlockState state = stateAt(x, y, z);
        if (state.getBlock() instanceof BranchBlock branch) {
            int radius = branch.getRadius(state);
            BlockPos pos = new BlockPos(x, y, z);
            int[] connections = new int[6];
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                BlockState adjacent = getBlockState(neighbor);
                connections[direction.get3DDataValue()] = Math.max(0, Math.min(radius,
                        TreeHelper.getTreePart(adjacent).getRadiusForConnection(adjacent, this, neighbor,
                                branch, direction, radius)));
            }
            BlockState wood = branch.getPrimitiveLog().orElse(Blocks.OAK_LOG).defaultBlockState();
            var pieces = new ArrayList<BlockAppearance.Piece>();
            for (BranchBoxes.Box box : BranchBoxes.create(radius, connections)) {
                BlockState material = wood.hasProperty(BlockStateProperties.AXIS)
                        ? wood.setValue(BlockStateProperties.AXIS, Direction.Axis.values()[box.axis()]) : wood;
                pieces.add(new BlockAppearance.Piece(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ(), material));
            }
            return new BlockAppearance(pieces);
        }
        if (state.getBlock() instanceof DynamicLeavesBlock leaves) {
            BlockState primitive = leaves.getLeavesProperties().getPrimitiveLeaves();
            if (primitive != null && !primitive.isAir()) {
                return new BlockAppearance(java.util.List.of(
                        new BlockAppearance.Piece(0, 0, 0, 16, 16, 16, primitive)));
            }
        }
        return original.appearanceAt(x, y, z);
    }
}
