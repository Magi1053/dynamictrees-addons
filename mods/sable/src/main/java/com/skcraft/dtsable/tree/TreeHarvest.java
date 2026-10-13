package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ItemAbilities;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** The sole removal path for DT blocks owned by a managed fallen body. */
public final class TreeHarvest {
    private static final int FLAGS = Block.UPDATE_ALL;
    private TreeHarvest() {}

    public static boolean breakManually(ServerLevel level, BlockPos pos, Player player) {
        return breakManually(level, pos, player, (at, stack) -> Block.popResource(level, at, stack));
    }

    public static boolean breakManually(ServerLevel level, BlockPos pos, Player player,
                                        BiConsumer<BlockPos, ItemStack> drops) {
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null) return false;
        if (body.leaves().contains(pos) && DtIntegration.isLeaves(level.getBlockState(pos))) {
            return snapFrom(level, pos, player, drops);
        }
        return breakAt(level, pos, player, false, drops);
    }

    /**
     * Everything one break would do, computed without touching the world or the budget.
     * {@code breakAt} applies exactly this, so a tooltip built from it cannot drift from the chop.
     */
    public record Preview(FallenTreeBody body, BlockPos pos, BlockState selected,
                          Set<BlockPos> removedWood, Set<BlockPos> removedLeaves,
                          List<Set<BlockPos>> components, Map<Integer, Integer> paidVolume,
                          Map<Integer, Integer> unpaidVolume, List<Map<Integer, Integer>> splitBudgets) {}

    /**
     * Guaranteed items the hint shows for a managed fallen body: the logs of its current
     * segment. Sticks, saplings, and other species rolls happen when a block is destroyed or
     * collides, so they are not previewed. Empty in creative.
     *
     * Position-based cascade belongs only to standing Dynamic Trees ({@code StandingTreeVolume}).
     */
    public static List<ItemStack> previewDrops(ServerLevel level, BlockPos pos, Player player) {
        if (player != null && player.isCreative()) return List.of();
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null) return List.of();
        BlockPos core = body.shellOwners().getOrDefault(pos, pos);
        if (!body.wood().contains(core) && !body.leaves().contains(pos)) return List.of();
        Map<Integer, Integer> paid = WoodBudget.pay(body.unpaidVolume(), Map.of(), new HashMap<>(),
                WoodYield.stickCost(0));
        return merge(woodDrops(level, body, paid, ItemStack.EMPTY));
    }

    public static boolean breakAt(ServerLevel level, BlockPos requested, Player player, boolean collision) {
        return breakAt(level, requested, player, collision, (at, stack) -> Block.popResource(level, at, stack));
    }

    private static boolean breakAt(ServerLevel level, BlockPos requested, Player player, boolean collision,
                                   BiConsumer<BlockPos, ItemStack> drops) {
        Preview preview = preview(level, requested, collision);
        if (preview == null) return false;
        FallenTreeBody body = preview.body();
        BlockPos pos = preview.pos();
        return DtIntegration.mutateWithoutNetworkDestroy(() -> {
            int pending = body.pendingSeeds() - dropLeaves(level, preview.removedLeaves(), body, player, drops);
            pending = splitSecondaryComponents(level, body, preview.components(), preview.removedLeaves(),
                    preview.splitBudgets(), pending);
            dropHarvest(level, body, pos, preview.paidVolume(), player, drops);
            dropBranchChances(level, body, preview.removedWood(), player, drops);
            playBreakSound(level, pos, preview.selected());
            removeOwned(level, body, preview.removedWood(), preview.removedLeaves());
            updateSourceBody(level, body, preview.components(), preview.removedLeaves(), preview.unpaidVolume(), pending);
            return true;
        });
    }

    private static Preview preview(ServerLevel level, BlockPos requested, boolean collision) {
        FallenTreeBody body = FallenTreeRegistry.find(level, requested).orElse(null);
        if (body == null) return null;
        BlockPos pos = body.shellOwners().getOrDefault(requested, requested);
        BlockState selected = level.getBlockState(pos);
        if (!body.wood().contains(pos)) return null;
        boolean rigid = WoodThickness.isRigid(selected);
        boolean thin = WoodThickness.isThinWood(selected);
        if (!rigid && !thin) return null;

        Set<BlockPos> thinWood = new HashSet<>();
        for (BlockPos wood : body.wood()) if (WoodThickness.isThinWood(level.getBlockState(wood))) thinWood.add(wood);
        Set<BlockPos> removedWood = new HashSet<>();
        if (collision) {
            // Collision callbacks identify the exact collider that was hit. Do not turn that
            // into a harvest-style flood through every connected thin branch.
            removedWood.add(pos);
        } else if (rigid) {
            removedWood.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                removedWood.addAll(TreeTopology.flood(neighbor, thinWood, TreeHarvest::neighbors));
            }
        } else {
            removedWood.addAll(TreeTopology.flood(pos, thinWood, TreeHarvest::neighbors));
        }
        if (removedWood.isEmpty()) return null;

        Set<BlockPos> remainingWood = new HashSet<>(body.wood());
        remainingWood.removeAll(removedWood);
        if (collision) {
            Set<BlockPos> unsupportedThin = unsupportedThinWood(level, remainingWood);
            removedWood.addAll(unsupportedThin);
            remainingWood.removeAll(unsupportedThin);
        }
        Set<BlockPos> removedLeaves = unsupportedLeaves(body, remainingWood);
        List<Set<BlockPos>> components = TreeTopology.connectedComponents(remainingWood, TreeHarvest::neighbors);

        // Pay from the grown budget: wood that left the body is credit, and only whole logs come
        // out of it. Sticks roll per branch when that block breaks, so they never consume a log.
        double lootScale = DtIntegration.lootVolumeScale(body.species());
        Map<Integer, Integer> remainingVolume = WoodBudget.measure(level, remainingWood, body.baseRadius(), lootScale);
        Map<Integer, Integer> unpaid = new HashMap<>();
        Map<Integer, Integer> paid = WoodBudget.pay(body.unpaidVolume(), remainingVolume, unpaid,
                WoodYield.stickCost(0));
        List<Map<Integer, Integer>> splitBudgets = new ArrayList<>();
        for (int i = 1; i < components.size(); i++) {
            Map<Integer, Integer> moved = WoodBudget.measure(level, components.get(i), body.baseRadius(), lootScale);
            splitBudgets.add(WoodBudget.takeSplitShare(unpaid, moved, remainingVolume));
        }
        return new Preview(body, pos, selected, removedWood, removedLeaves, components, paid, unpaid, splitBudgets);
    }

    /**
     * A stripped fallen block keeps its debt under the stripped branch type, so the next break
     * pays stripped logs for it. Also drops shell ownership DT removed while shrinking the radius.
     */
    public static void onStripped(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null || !body.wood().contains(pos)) return;
        int from = DtIntegration.branchIndex(before);
        int to = DtIntegration.branchIndex(after);
        if (from < 0 || to < 0 || from == to) return;
        double lootScale = DtIntegration.lootVolumeScale(body.species());
        int volume = WoodBudget.volumeOf(before, WoodThickness.radius(before), body.baseRadius(), lootScale);
        Map<Integer, Integer> unpaid = new HashMap<>(body.unpaidVolume());
        WoodBudget.transfer(unpaid, from, to, volume);
        boolean unstrippedCore = false;
        for (BlockPos wood : body.wood()) {
            BlockState standing = wood.equals(pos) ? after : level.getBlockState(wood);
            if (DtIntegration.yieldRadius(standing, WoodThickness.radius(standing)) >= WoodThickness.THRESHOLD
                    && !DtIntegration.isStrippedBranch(standing)) {
                unstrippedCore = true;
                break;
            }
        }
        if (!unstrippedCore) {
            WoodBudget.foldUnstrippedIfAllCoresStripped(unpaid, DtIntegration.strippedBranchIndex(body.species()));
        }
        Map<BlockPos, BlockPos> shells = new HashMap<>(body.shellOwners());
        shells.keySet().removeIf(shell -> !DtIntegration.isTrunkShell(level.getBlockState(shell)));
        FallenTreeRegistry.register(level, body.withBlocks(body.wood(), body.leaves(), shells, unpaid));
        playStripSound(level, body);
    }

    /**
     * Plot-local coords are tens of millions of blocks away. Play at the plot's overworld
     * position and notify nearby players so Sable's level wrapper cannot swallow the packet.
     */
    private static void playStripSound(ServerLevel level, FallenTreeBody body) {
        ServerSubLevel sub = FallenTreeRegistry.subLevel(level, body.id());
        BlockPos at = sub != null && !sub.isRemoved() ? sub.getPlot().getCenterBlock() : null;
        double x = at != null ? at.getX() + 0.5 : 0;
        double y = at != null ? at.getY() + 0.5 : 0;
        double z = at != null ? at.getZ() + 0.5 : 0;
        if (at != null) {
            level.playSound(null, at, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        for (ServerPlayer player : level.players()) {
            if (at != null && player.distanceToSqr(x, y, z) > 32 * 32) continue;
            player.playNotifySound(SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    public static boolean snapFrom(ServerLevel level, BlockPos pos) {
        return snapFrom(level, pos, null, (at, stack) -> Block.popResource(level, at, stack));
    }

    private static boolean snapFrom(ServerLevel level, BlockPos pos, Player player,
                                    BiConsumer<BlockPos, ItemStack> drops) {
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null) return false;
        BlockState state = level.getBlockState(pos);
        if (!WoodThickness.isAutoBreakable(state)) return false;
        if (DtIntegration.isLeaves(state)) {
            int dropped = DtIntegration.mutateWithoutNetworkDestroy(() -> {
                int paid = dropLeaves(level, Set.of(pos), body, player, drops);
                playBreakSound(level, pos, state);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                return paid;
            });
            Set<BlockPos> leaves = new HashSet<>(body.leaves());
            leaves.remove(pos);
            FallenTreeRegistry.register(level, body.withLeaves(leaves).withPendingSeeds(body.pendingSeeds() - dropped));
            return true;
        }
        return breakAt(level, pos, null, true, drops);
    }

    public static boolean breakStandingLeaf(ServerLevel level, BlockPos pos) {
        return breakStandingLeaf(level, pos, (at, stack) -> Block.popResource(level, at, stack));
    }

    public static boolean breakStandingLeaf(ServerLevel level, BlockPos pos, BiConsumer<BlockPos, ItemStack> drops) {
        BlockState state = level.getBlockState(pos);
        if (!DtIntegration.isLeaves(state) || FallenTreeRegistry.find(level, pos).isPresent()) return false;
        for (ItemStack drop : Block.getDrops(state, level, pos, null, null, ItemStack.EMPTY)) {
            drops.accept(pos, drop);
        }
        playBreakSound(level, pos, state);
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static void playBreakSound(ServerLevel level, BlockPos pos, BlockState state) {
        var sound = state.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private static int splitSecondaryComponents(ServerLevel level, FallenTreeBody source,
                                                List<Set<BlockPos>> components, Set<BlockPos> removedLeaves,
                                                List<Map<Integer, Integer>> splitBudgets, int pendingSeeds) {
        if (components.size() <= 1) return pendingSeeds;
        Set<BlockPos> availableLeaves = new HashSet<>(source.leaves());
        availableLeaves.removeAll(removedLeaves);
        int poolLeaves = availableLeaves.size();
        int poolSeeds = pendingSeeds;
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> wood = components.get(i);
            Map<Integer, Integer> budget = splitBudgets.get(i - 1);
            Set<BlockPos> leaves = attachedLeaves(level, availableLeaves, wood);
            availableLeaves.removeAll(leaves);
            Map<BlockPos, BlockPos> shells = new HashMap<>();
            source.shellOwners().forEach((shell, core) -> { if (wood.contains(core)) shells.put(shell, core); });
            Set<BlockPos> moving = new HashSet<>(wood);
            moving.addAll(leaves);
            moving.addAll(shells.keySet());
            if (moving.isEmpty()) continue;
            BlockPos anchor = wood.iterator().next();
            BoundingBox3i bounds = BoundingBox3i.from(moving);
            if (bounds == null) continue;
            int movedSeeds = SeedYield.take(poolSeeds, poolLeaves, leaves.size(), () -> level.getRandom().nextFloat());
            poolSeeds -= movedSeeds;
            poolLeaves = Math.max(0, poolLeaves - leaves.size());
            ServerSubLevel split = SubLevelAssemblyHelper.assembleBlocks(level, anchor, moving, bounds.expand(1, 1, 1));
            if (split == null || split.isRemoved()) throw new IllegalStateException("Sable split failed");
            BlockPos destination = split.getPlot().getCenterBlock();
            FallenTreeBody splitBody = source.child(split.getUniqueId(),
                    translate(wood, anchor, destination), translate(leaves, anchor, destination),
                    translateOwners(shells, anchor, destination), budget, movedSeeds);
            FallenTreeRegistry.register(level, splitBody);
        }
        return poolSeeds;
    }

    private static void removeOwned(ServerLevel level, FallenTreeBody body, Set<BlockPos> wood, Set<BlockPos> leaves) {
        Set<BlockPos> positions = new HashSet<>(wood);
        positions.addAll(leaves);
        body.shellOwners().forEach((shell, core) -> { if (wood.contains(core)) positions.add(shell); });
        for (BlockPos pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static void updateSourceBody(ServerLevel level, FallenTreeBody old, List<Set<BlockPos>> components,
                                         Set<BlockPos> removedLeaves, Map<Integer, Integer> unpaidVolume,
                                         int pendingSeeds) {
        if (components.isEmpty()) {
            ServerSubLevel source = FallenTreeRegistry.subLevel(level, old.id());
            if (source != null) {
                SubLevelContainer.getContainer(level).removeSubLevel(source, SubLevelRemovalReason.REMOVED);
            }
            FallenTreeRegistry.forget(old.id());
            return;
        }
        Set<BlockPos> largest = components.getFirst();
        Set<BlockPos> leaves = attachedLeaves(level, difference(old.leaves(), removedLeaves), largest);
        Map<BlockPos, BlockPos> shells = new HashMap<>();
        old.shellOwners().forEach((shell, core) -> { if (largest.contains(core)) shells.put(shell, core); });
        FallenTreeRegistry.register(level, old.withBlocks(largest, leaves, shells, unpaidVolume)
                .withPendingSeeds(pendingSeeds));
    }

    private static Set<BlockPos> unsupportedThinWood(ServerLevel level, Set<BlockPos> remainingWood) {
        Set<BlockPos> rigid = new HashSet<>();
        Set<BlockPos> thin = new HashSet<>();
        for (BlockPos pos : remainingWood) {
            if (WoodThickness.isRigid(level.getBlockState(pos))) rigid.add(pos);
            else if (WoodThickness.isThinWood(level.getBlockState(pos))) thin.add(pos);
        }
        Set<BlockPos> unsupported = new HashSet<>();
        for (Set<BlockPos> component : TreeTopology.connectedComponents(thin, TreeHarvest::neighbors)) {
            if (component.stream().noneMatch(pos -> touches(pos, rigid))) unsupported.addAll(component);
        }
        return unsupported;
    }

    private static Set<BlockPos> unsupportedLeaves(FallenTreeBody body, Set<BlockPos> remainingWood) {
        Set<BlockPos> candidates = new HashSet<>(body.leaves());
        Set<BlockPos> unsupported = new HashSet<>();
        for (Set<BlockPos> leafGroup : TreeTopology.connectedComponents(candidates, TreeHarvest::neighbors)) {
            boolean supported = leafGroup.stream().anyMatch(leaf -> touches(leaf, remainingWood));
            if (!supported) unsupported.addAll(leafGroup);
        }
        return unsupported;
    }

    private static Set<BlockPos> attachedLeaves(ServerLevel level, Set<BlockPos> available, Set<BlockPos> wood) {
        Set<BlockPos> result = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos leaf : available) if (touches(leaf, wood)) { result.add(leaf); queue.add(leaf); }
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (available.contains(next) && result.add(next)) queue.add(next);
            }
        }
        return result;
    }

    private static boolean touches(BlockPos pos, Set<BlockPos> target) {
        for (Direction direction : Direction.values()) if (target.contains(pos.relative(direction))) return true;
        return false;
    }

    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        return java.util.Arrays.stream(Direction.values()).map(pos::relative).toList();
    }

    /** Runs the species branch loot table on the whole-log volume owed by this break. */
    private static void dropHarvest(ServerLevel level, FallenTreeBody body, BlockPos at,
                                    Map<Integer, Integer> paidVolume, Player player, BiConsumer<BlockPos, ItemStack> drops) {
        if (player != null && player.isCreative()) return;
        for (ItemStack drop : woodDrops(level, body, paidVolume, ItemStack.EMPTY)) drops.accept(at, drop);
    }

    private static List<ItemStack> woodDrops(ServerLevel level, FallenTreeBody body, Map<Integer, Integer> paidVolume,
                                             ItemStack tool) {
        List<ItemStack> drops = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : paidVolume.entrySet()) {
            drops.addAll(DtIntegration.branchDrops(level, body.species(), entry.getKey(), entry.getValue(), tool));
        }
        return drops;
    }

    /**
     * Each removed branch rolls sticks the way a vanilla leaf does, and any other non-log pool
     * on that species' branch loot table (golden amber, and similar). The stick item is whatever
     * the species table names, so skyroot sticks stay skyroot sticks.
     */
    private static void dropBranchChances(ServerLevel level, FallenTreeBody body, Set<BlockPos> wood,
                                          Player player, BiConsumer<BlockPos, ItemStack> drops) {
        if (player != null && player.isCreative()) return;
        ItemStack tool = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        int fortune = fortuneLevel(level, tool);
        int rate = DtSableConfig.fallenSticksPerLog();
        for (BlockPos pos : wood) {
            BlockState state = level.getBlockState(pos);
            int index = DtIntegration.branchIndex(state);
            if (index < 0) continue;
            if (WoodYield.branchDropsStick(level.getRandom().nextFloat(), fortune, rate)) {
                Item stick = stickItem(level, body, index);
                if (stick != null) {
                    drops.accept(pos, new ItemStack(stick, 1 + level.getRandom().nextInt(2)));
                }
            }
            for (ItemStack extra : DtIntegration.branchDrops(level, body.species(), index, 0, tool)) {
                if (!extra.isEmpty()) drops.accept(pos, extra);
            }
        }
    }

    /** The item the species branch table calls a stick. One probe per branch type per break. */
    private static Item stickItem(ServerLevel level, FallenTreeBody body, int index) {
        for (ItemStack stack : DtIntegration.branchDrops(level, body.species(), index, WoodYield.STICK_VOLUME, ItemStack.EMPTY)) {
            if (!stack.isEmpty()) return stack.getItem();
        }
        return null;
    }

    private static int fortuneLevel(ServerLevel level, ItemStack tool) {
        if (tool.isEmpty()) return 0;
        Holder<Enchantment> fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        return EnchantmentHelper.getTagEnchantmentLevel(fortune, tool);
    }

    /** @return how many owed seeds this break paid */
    private static int dropLeaves(ServerLevel level, Set<BlockPos> leaves, FallenTreeBody body,
                                  Player player, BiConsumer<BlockPos, ItemStack> drops) {
        if (player != null && player.isCreative()) return 0;
        Species species = body.species();
        ItemStack tool = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        List<BlockPos> breaking = new ArrayList<>();
        for (BlockPos pos : leaves) {
            if (!DtIntegration.isLeaves(level.getBlockState(pos))) continue;
            breaking.add(pos);
            for (ItemStack drop : leafDrops(level, Set.of(pos), species, player, tool)) {
                drops.accept(pos, drop);
            }
        }
        if (breaking.isEmpty() || shears(tool)) return 0;
        int owed = SeedYield.take(body.pendingSeeds(), body.leaves().size(), breaking.size(),
                () -> level.getRandom().nextFloat());
        ItemStack seed = species.getSeedStack(1);
        if (seed.isEmpty() || owed <= 0) return 0;
        for (int i = 0; i < owed; i++) {
            drops.accept(breaking.get(level.getRandom().nextInt(breaking.size())), seed.copy());
        }
        return owed;
    }

    private static boolean shears(ItemStack tool) {
        return !tool.isEmpty() && (tool.is(Items.SHEARS) || tool.canPerformAction(ItemAbilities.SHEARS_HARVEST));
    }

    private static List<ItemStack> leafDrops(ServerLevel level, Set<BlockPos> leaves, Species species, Player player,
                                             ItemStack tool) {
        List<ItemStack> drops = new ArrayList<>();
        for (BlockPos pos : leaves) {
            BlockState state = level.getBlockState(pos);
            if (!DtIntegration.isLeaves(state)) continue;
            drops.addAll(state.getBlock() instanceof DynamicLeavesBlock dynamicLeaves
                    ? dynamicLeaves.getLeavesProperties().getDrops(level, pos, tool, species)
                    : Block.getDrops(state, level, pos, null, player, tool));
        }
        return drops;
    }

    /** Collapses same-item stacks so a tooltip shows one count per item. */
    private static List<ItemStack> merge(List<ItemStack> drops) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack existing = null;
            for (ItemStack candidate : merged) {
                if (ItemStack.isSameItemSameComponents(candidate, drop)) { existing = candidate; break; }
            }
            if (existing == null) merged.add(drop.copy()); else existing.grow(drop.getCount());
        }
        return merged;
    }

    private static Set<BlockPos> difference(Set<BlockPos> all, Set<BlockPos> removed) {
        Set<BlockPos> result = new HashSet<>(all);
        result.removeAll(removed);
        return result;
    }

    private static Set<BlockPos> translate(Set<BlockPos> input, BlockPos from, BlockPos to) {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : input) result.add(translate(pos, from, to));
        return result;
    }

    private static Map<BlockPos, BlockPos> translateOwners(Map<BlockPos, BlockPos> input, BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> result = new HashMap<>();
        input.forEach((shell, core) -> result.put(translate(shell, from, to), translate(core, from, to)));
        return result;
    }

    private static BlockPos translate(BlockPos pos, BlockPos from, BlockPos to) {
        return to.offset(pos.getX() - from.getX(), pos.getY() - from.getY(), pos.getZ() - from.getZ());
    }
}
