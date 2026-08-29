package com.skcraft.dtsable.dt;

import com.dtteam.dynamictrees.DynamicTrees;
import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.ThickBranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.mixin.ThickBranchRadiusAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The only class allowed to know Dynamic Trees implementation details. */
public final class DtIntegration {
    private static final ThreadLocal<Integer> MUTATION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DtIntegration() {}

    public static boolean isMutatingBody() { return MUTATION_DEPTH.get() > 0; }

    public static <T> T mutateWithoutNetworkDestroy(Supplier<T> work) {
        DynamicTrees.DestroyMode previous = BranchBlock.destroyMode;
        MUTATION_DEPTH.set(MUTATION_DEPTH.get() + 1);
        BranchBlock.destroyMode = DynamicTrees.DestroyMode.IGNORE;
        try {
            return work.get();
        } finally {
            BranchBlock.destroyMode = previous;
            int depth = MUTATION_DEPTH.get() - 1;
            if (depth == 0) MUTATION_DEPTH.remove(); else MUTATION_DEPTH.set(depth);
        }
    }

    public static void mutateWithoutNetworkDestroy(Runnable work) {
        mutateWithoutNetworkDestroy(() -> { work.run(); return null; });
    }

    public static boolean supported(FallingTreeEntity.DestroyType type) {
        return type == FallingTreeEntity.DestroyType.HARVEST;
    }

    public static int radius(BlockState state) {
        if (state.getBlock() instanceof TrunkShellBlock) return 4;
        if (state.getBlock() instanceof ThickBranchBlock) {
            IntegerProperty property = ThickBranchRadiusAccessor.dtsable$radiusDouble();
            if (state.hasProperty(property)) return Mth.clamp(state.getValue(property), 1, 24);
        }
        return state.getBlock() instanceof BranchBlock branch ? Math.max(0, branch.getRadius(state)) : 0;
    }

    public static Species species(BranchDestructionData data) { return data.species; }
    public static Family family(BranchDestructionData data) { return data.species.getFamily(); }
    public static Block primitiveLog(BranchDestructionData data) { return family(data).getPrimitiveLog().orElse(Blocks.OAK_LOG); }
    public static Block primitiveLog(BranchBlock branch) { return branch.getFamily().getPrimitiveLog().orElse(Blocks.OAK_LOG); }
    public static BlockPos cutPos(BranchDestructionData data) { return data.cutPos; }
    public static Direction cutDirection(BranchDestructionData data) { return data.cutDir; }
    public static int branchCount(BranchDestructionData data) { return data.getNumBranches(); }
    public static BlockPos branchPos(BranchDestructionData data, int index) { return data.cutPos.offset(data.getBranchRelPos(index)); }
    public static int branchRadius(BranchDestructionData data, int index) { return data.getBranchRadius(index); }
    public static BlockState branchState(BranchDestructionData data, int index) { return data.getBranchBlockState(index); }
    public static int leafCount(BranchDestructionData data) { return data.getNumLeaves(); }
    public static BlockPos leafPos(BranchDestructionData data, int index) { return data.cutPos.offset(data.getLeavesRelPos(index)); }
    public static BlockState leafState(BranchDestructionData data, int index) { return data.getLeavesBlockState(index); }
    public static NetVolumeNode.Volume volume(BranchDestructionData data) { return data.woodVolume; }
    public static List<ItemStack> branchDrops(Level level, BranchDestructionData data) {
        return new ArrayList<>(data.species.getBranchesDrops(level, data.woodVolume));
    }
    public static List<ItemStack> leavesDrops(BranchDestructionData data) {
        List<ItemStack> result = new ArrayList<>();
        data.leavesDrops.forEach(drop -> result.add(drop.stack.copy()));
        return result;
    }
}
