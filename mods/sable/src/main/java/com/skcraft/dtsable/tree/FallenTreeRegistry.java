package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.tree.species.Species;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collection;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime authority for bodies created by this addon. Unmanaged Sable bodies are deliberately ignored. */
public final class FallenTreeRegistry {
    private static final String MANAGED_TAG = "dtsable_managed_tree";
    private static final String SPECIES_TAG = "dtsable_species";
    private static final Map<UUID, FallenTreeBody> BODIES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingTopple> PENDING_TOPPLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SETTLING_BODIES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, java.util.Set<BlockPos>> PENDING_SNAPS = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, java.util.Set<BlockPos>> PENDING_STANDING_LEAVES = new ConcurrentHashMap<>();
    private static final Map<Level, ArrayDeque<PendingSplit>> PENDING_SPLITS = new IdentityHashMap<>();
    private static final java.util.Set<SubLevelContainer> OBSERVED =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        SubLevelHeatMapManager.addSplitListener(FallenTreeRegistry::captureSableSplit);
    }

    private FallenTreeRegistry() {}

    public static void register(FallenTreeBody body) { BODIES.put(body.id(), body); }
    public static void register(ServerLevel level, FallenTreeBody body) {
        installObserver(level);
        register(body);
        ServerSubLevel subLevel = subLevel(level, body.id());
        if (subLevel != null) {
            CompoundTag tag = userData(subLevel);
            tag.putBoolean(MANAGED_TAG, true);
            tag.putString(SPECIES_TAG, body.species().getRegistryName().toString());
        }
    }
    public static void forget(UUID id) {
        BODIES.remove(id);
        PENDING_TOPPLES.remove(id);
        SETTLING_BODIES.remove(id);
    }
    public static Optional<FallenTreeBody> byId(UUID id) { return Optional.ofNullable(BODIES.get(id)); }
    public static Collection<FallenTreeBody> all() { return java.util.List.copyOf(BODIES.values()); }

    public static Optional<FallenTreeBody> find(Level level, BlockPos plotPos) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        SubLevel containing = Sable.HELPER.getContaining(server, plotPos);
        if (!(containing instanceof ServerSubLevel subLevel) || subLevel.isRemoved()) return Optional.empty();
        FallenTreeBody body = BODIES.get(subLevel.getUniqueId());
        if (body == null) body = recover(server, subLevel);
        return body != null && body.dimension().equals(server.dimension()) && body.owns(plotPos)
                ? Optional.of(body) : Optional.empty();
    }

    private static FallenTreeBody recover(ServerLevel level, ServerSubLevel subLevel) {
        Set<BlockPos> wood = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        Set<BlockPos> shellPositions = new HashSet<>();
        Species species = null;
        CompoundTag tag = userData(subLevel);
        ResourceLocation storedSpecies = ResourceLocation.tryParse(tag.getString(SPECIES_TAG));
        if (storedSpecies != null) {
            Species value = Species.findSpecies(storedSpecies);
            if (value != Species.NULL_SPECIES) species = value;
        }
        var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds.volume() <= 0) return null;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof BranchBlock branch) {
                wood.add(pos.immutable());
                if (species == null) species = branch.getFamily().getCommonSpecies();
            } else if (state.getBlock() instanceof TrunkShellBlock) {
                shellPositions.add(pos.immutable());
            } else if (state.is(BlockTags.LEAVES)) {
                leaves.add(pos.immutable());
            }
        }
        // The persisted marker distinguishes current bodies. The wood check recovers bodies
        // made by earlier development builds before the marker existed.
        if (wood.isEmpty() || species == null) return null;
        Map<BlockPos, BlockPos> shells = new HashMap<>();
        for (BlockPos shellPos : shellPositions) {
            var state = level.getBlockState(shellPos);
            TrunkShellBlock shell = (TrunkShellBlock) state.getBlock();
            var muse = shell.getMuse(level, state, shellPos);
            if (muse != null && wood.contains(muse.pos())) shells.put(shellPos, muse.pos().immutable());
        }
        FallenTreeBody recovered = new FallenTreeBody(subLevel.getUniqueId(), level.dimension(), species,
                wood, leaves, shells);
        FallenTreeBody existing = BODIES.putIfAbsent(recovered.id(), recovered);
        tag.putBoolean(MANAGED_TAG, true);
        tag.putString(SPECIES_TAG, recovered.species().getRegistryName().toString());
        installObserver(level);
        return existing == null ? recovered : existing;
    }

    private static CompoundTag userData(ServerSubLevel subLevel) {
        CompoundTag tag = subLevel.getUserDataTag();
        if (tag == null) {
            tag = new CompoundTag();
            subLevel.setUserDataTag(tag);
        }
        return tag;
    }

    public static boolean isManagedBody(Level level, BlockPos pos) { return find(level, pos).isPresent(); }

    /** Client has no authoritative registry; containment is sufficient only for visual mining progress. */
    public static boolean hasFallenMiningContext(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!level.isClientSide) return isManagedBody(level, pos);
        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        return containing != null && !containing.isRemoved();
    }

    public static ServerSubLevel subLevel(ServerLevel level, UUID id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel value = container.getSubLevel(id);
        return value instanceof ServerSubLevel server && !server.isRemoved() ? server : null;
    }

    /** Collision callbacks run inside Rapier's step and must never mutate plots directly. */
    public static void scheduleCollisionSnap(ServerLevel level, BlockPos pos) {
        PENDING_SNAPS.computeIfAbsent(level, unused -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    public static void scheduleStandingLeafBreak(ServerLevel level, BlockPos pos) {
        PENDING_STANDING_LEAVES.computeIfAbsent(level, unused -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
    }

    /** Collision callbacks only record state; damping is applied after the Rapier step. */
    public static void noteCollision(ServerLevel level, BlockPos pos) {
        find(level, pos).ifPresent(body -> SETTLING_BODIES.put(body.id(), 30));
    }

    /** Capture direction now so later player movement/sneaking cannot change the fell. */
    public static void scheduleTopple(FallenTreeBody body, BlockPos cut,
                                      net.minecraft.world.entity.player.Player player) {
        double dx = player == null ? 0.0 : cut.getX() + 0.5 - player.getX();
        double dz = player == null ? 1.0 : cut.getZ() + 0.5 - player.getZ();
        if (player != null && player.isShiftKeyDown()) { dx = -dx; dz = -dz; }
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) { dx = 0.0; dz = 1.0; }
        else { dx /= length; dz /= length; }
        PENDING_TOPPLES.put(body.id(), new PendingTopple(dx, dz, 2, 8));
    }

    private static void installObserver(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !OBSERVED.add(container)) return;
        container.addObserver(new SubLevelObserver() {
            @Override public void onSubLevelAdded(SubLevel added) {
                if (!(added instanceof ServerSubLevel server)) return;
                ArrayDeque<PendingSplit> queue = PENDING_SPLITS.get(level);
                PendingSplit split = queue == null ? null : queue.pollFirst();
                if (split == null) return;
                if (queue.isEmpty()) PENDING_SPLITS.remove(level);
                BlockPos destination = server.getPlot().getCenterBlock();
                FallenTreeBody child = new FallenTreeBody(server.getUniqueId(), level.dimension(), split.species(),
                        translate(split.wood(), split.anchor(), destination),
                        translate(split.leaves(), split.anchor(), destination),
                        translateOwners(split.shells(), split.anchor(), destination));
                register(child);
                FallenTreeBody source = BODIES.get(split.sourceId());
                if (source != null) {
                    java.util.Set<BlockPos> wood = difference(source.wood(), split.wood());
                    java.util.Set<BlockPos> leaves = difference(source.leaves(), split.leaves());
                    Map<BlockPos, BlockPos> shells = new HashMap<>(source.shellOwners());
                    split.shells().keySet().forEach(shells::remove);
                    register(new FallenTreeBody(source.id(), source.dimension(), source.species(), wood, leaves, shells));
                }
            }
        });
    }

    private static void captureSableSplit(Level level, dev.ryanhcode.sable.companion.math.BoundingBox3ic ignored,
                                          Collection<BlockPos> blocks) {
        if (!(level instanceof ServerLevel server) || blocks.isEmpty()) return;
        FallenTreeBody source = BODIES.values().stream()
                .filter(body -> body.dimension().equals(server.dimension()))
                .filter(body -> blocks.stream().anyMatch(body::owns)).findFirst().orElse(null);
        if (source == null) return;
        java.util.Set<BlockPos> blockSet = new HashSet<>(blocks);
        java.util.Set<BlockPos> wood = intersection(source.wood(), blockSet);
        java.util.Set<BlockPos> leaves = intersection(source.leaves(), blockSet);
        Map<BlockPos, BlockPos> shells = new HashMap<>();
        source.shellOwners().forEach((shell, core) -> { if (blockSet.contains(shell)) shells.put(shell, core); });
        PENDING_SPLITS.computeIfAbsent(level, unused -> new ArrayDeque<>()).addLast(
                new PendingSplit(source.id(), source.species(), blocks.iterator().next(), wood, leaves, shells));
        installObserver(server);
    }

    private static java.util.Set<BlockPos> difference(java.util.Set<BlockPos> source, java.util.Set<BlockPos> removed) {
        java.util.Set<BlockPos> result = new HashSet<>(source);
        result.removeAll(removed);
        return result;
    }

    private static java.util.Set<BlockPos> intersection(java.util.Set<BlockPos> source, java.util.Set<BlockPos> selected) {
        java.util.Set<BlockPos> result = new HashSet<>(source);
        result.retainAll(selected);
        return result;
    }

    private static java.util.Set<BlockPos> translate(java.util.Set<BlockPos> input, BlockPos from, BlockPos to) {
        java.util.Set<BlockPos> result = new HashSet<>();
        input.forEach(pos -> result.add(to.offset(pos.getX() - from.getX(), pos.getY() - from.getY(), pos.getZ() - from.getZ())));
        return result;
    }

    private static Map<BlockPos, BlockPos> translateOwners(Map<BlockPos, BlockPos> input, BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> result = new HashMap<>();
        input.forEach((shell, core) -> result.put(
                to.offset(shell.getX() - from.getX(), shell.getY() - from.getY(), shell.getZ() - from.getZ()),
                to.offset(core.getX() - from.getX(), core.getY() - from.getY(), core.getZ() - from.getZ())));
        return result;
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        java.util.Set<BlockPos> standingLeaves = PENDING_STANDING_LEAVES.remove(level);
        if (standingLeaves != null) {
            for (BlockPos pos : standingLeaves) TreeHarvest.breakStandingLeaf(level, pos);
        }
        java.util.Set<BlockPos> snaps = PENDING_SNAPS.remove(level);
        if (snaps != null) {
            for (BlockPos pos : snaps) TreeHarvest.snapFrom(level, pos);
        }
        for (FallenTreeBody body : java.util.List.copyOf(BODIES.values())) {
            if (!body.dimension().equals(level.dimension())) continue;
            ServerSubLevel subLevel = subLevel(level, body.id());
            if (subLevel == null) {
                forget(body.id());
                continue;
            }
            PendingTopple topple = PENDING_TOPPLES.get(body.id());
            if (topple != null && topple.delayTicks() > 0) {
                PENDING_TOPPLES.put(body.id(), topple.afterDelay());
            } else if (topple != null && (TreePhysics.applyToppleVelocity(subLevel, level, topple.dx(), topple.dz())
                    || topple.attemptsLeft() <= 1)) {
                PENDING_TOPPLES.remove(body.id());
            } else if (topple != null) {
                PENDING_TOPPLES.put(body.id(), topple.afterAttempt());
            }
            Integer settlingTicks = SETTLING_BODIES.get(body.id());
            if (settlingTicks != null) {
                TreePhysics.dampAfterContact(subLevel, level);
                if (settlingTicks <= 1) SETTLING_BODIES.remove(body.id());
                else SETTLING_BODIES.put(body.id(), settlingTicks - 1);
            }
        }
    }
    private record PendingTopple(double dx, double dz, int delayTicks, int attemptsLeft) {
        PendingTopple afterDelay() { return new PendingTopple(dx, dz, delayTicks - 1, attemptsLeft); }
        PendingTopple afterAttempt() { return new PendingTopple(dx, dz, 0, attemptsLeft - 1); }
    }
    private record PendingSplit(UUID sourceId, com.dtteam.dynamictrees.tree.species.Species species,
                                BlockPos anchor, java.util.Set<BlockPos> wood,
                                java.util.Set<BlockPos> leaves, Map<BlockPos, BlockPos> shells) {}
}
