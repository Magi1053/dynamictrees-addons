package com.skcraft.dtsable.dt;

import com.dtteam.dynamictrees.DynamicTrees;
import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.ThickBranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.config.DTConfigs;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.dtteam.dynamictrees.loot.DTLootContextParams;
import com.dtteam.dynamictrees.loot.DTLootParameterSets;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.family.Family;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.mixin.ThickBranchRadiusAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

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

    public static boolean isTrunkShell(BlockState state) { return state.getBlock() instanceof TrunkShellBlock; }

    /**
     * Leaves by class, not only by tag. DT tags its own leaves {@code #minecraft:leaves}; addon
     * species (rubber, aether, ...) usually do not, and every fallen-body leaf path must still see them.
     */
    public static boolean isLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof DynamicLeavesBlock;
    }

    /**
     * Radius a block pays loot for. DT shaves one radius off a branch when it strips it, so a
     * stripped block pays for the radius it had before stripping; stripping never costs a log.
     */
    public static int yieldRadius(BlockState state, int radius) {
        if (state.getBlock() instanceof BranchBlock branch && branch.isStrippedBranch()
                && branch.getFamily().reduceRadiusWhenStripping()) {
            return Math.min(radius + 1, 24);
        }
        return radius;
    }

    /** Index into the family's branch-block list, or -1 for anything that is not DT wood. */
    public static int branchIndex(BlockState state) {
        return state.getBlock() instanceof BranchBlock branch ? branch.getFamily().getBranchBlockIndex(branch) : -1;
    }

    /** The scale DT applies to felled volume once: server harvest multiplier times the family loot multiplier. */
    public static double lootVolumeScale(Species species) {
        double harvest = 1.0;
        try {
            harvest = DTConfigs.SERVER.treeHarvestMultiplier.get();
        } catch (IllegalStateException | NullPointerException notLoaded) {
            // Server config is absent until a level exists; DT's own felling has the same window.
        }
        return harvest * species.getFamily().getLootVolumeMultiplier();
    }

    /** Runs the species branch loot table for one branch-block type on already-scaled volume. */
    public static List<ItemStack> branchDrops(ServerLevel level, Species species, int branchIndex, int volume,
                                              ItemStack tool) {
        Family family = species.getFamily();
        if (volume <= 0 || branchIndex < 0 || branchIndex >= family.getNumberOfValidBranchBlocks()) return List.of();
        BranchBlock branch = family.getValidBranchBlock(branchIndex);
        LootTable table = branch.getLootTable(level.getServer().reloadableRegistries(), species);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(DTLootContextParams.SPECIES, species)
                .withParameter(DTLootContextParams.VOLUME, volume)
                .create(DTLootParameterSets.BRANCHES);
        return table.getRandomItems(params);
    }

    public static Species species(BranchDestructionData data) { return data.species; }
    public static Family family(BranchDestructionData data) { return data.species.getFamily(); }
    public static boolean isStrippedBranch(BlockState state) {
        return state.getBlock() instanceof BranchBlock branch && branch.isStrippedBranch();
    }

    /** Family index of the stripped branch block, or -1 if the family has none. */
    public static int strippedBranchIndex(Species species) {
        Family family = species.getFamily();
        return family.getStrippedBranch().map(family::getBranchBlockIndex).orElse(-1);
    }

    public static Block primitiveLog(BranchDestructionData data) { return family(data).getPrimitiveLog().orElse(Blocks.OAK_LOG); }
    public static Block primitiveLog(BranchBlock branch) { return branch.getFamily().getPrimitiveLog().orElse(Blocks.OAK_LOG); }

    /** The primitive log a cut block should drop: stripped if that branch was stripped. */
    public static Block primitiveLog(BlockState branchState, Species species) {
        Family family = species.getFamily();
        if (isStrippedBranch(branchState)) {
            return family.getPrimitiveStrippedLog().orElse(family.getPrimitiveLog().orElse(Blocks.OAK_LOG));
        }
        return family.getPrimitiveLog().orElse(Blocks.OAK_LOG);
    }
    public static BlockPos cutPos(BranchDestructionData data) { return data.cutPos; }
    public static BlockPos basePos(BranchDestructionData data) { return data.basePos; }
    public static Direction cutDirection(BranchDestructionData data) { return data.cutDir; }
    public static int branchCount(BranchDestructionData data) { return data.getNumBranches(); }
    public static int endpointCount(BranchDestructionData data) { return data.getNumEndpoints(); }
    public static int trunkHeight(BranchDestructionData data) { return data.trunkHeight; }
    public static BlockPos branchPos(BranchDestructionData data, int index) { return data.cutPos.offset(data.getBranchRelPos(index)); }
    public static int branchRadius(BranchDestructionData data, int index) { return data.getBranchRadius(index); }
    public static BlockState branchState(BranchDestructionData data, int index) { return data.getBranchBlockState(index); }
    public static int leafCount(BranchDestructionData data) { return data.getNumLeaves(); }
    public static BlockPos leafPos(BranchDestructionData data, int index) { return data.cutPos.offset(data.getLeavesRelPos(index)); }
    public static BlockState leafState(BranchDestructionData data, int index) { return data.getLeavesBlockState(index); }
    public static NetVolumeNode.Volume volume(BranchDestructionData data) { return data.woodVolume; }
}
