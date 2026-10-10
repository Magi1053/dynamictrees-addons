# Dynamic Trees Sable — Specification

This is the contract for `dtsable`. Implement against these rules. A change that
fixes one behavior by violating another is a failed change.

Collision snapping and harvest cascade share one classifier: `WoodThickness`.
Mining speed is a separate concern and must not change that classifier.

## 1. Product

Chop a living Dynamic Trees (DT) tree. Instead of spawning DT's
`FallingTreeEntity`, assemble the felled volume into a Sable SubLevel: a
physics rigid body made of the same DT branch, trunk-shell, and leaves blocks.

After it falls, the player mines individual cores and limbs off that body.
Cores stay rigid on impact. Thin branches and leaves snap when they hit the
ground or another tree.

When the addon is disabled, or a tree exceeds `max_blocks`, DT's original
falling entity, hardness, and loot must be unchanged.

## 2. Vocabulary

| Term | Meaning |
|---|---|
| **Standing tree** | Rooted DT wood in the world. Not inside a Sable SubLevel. |
| **Fallen body** | A Sable SubLevel assembled from a felled DT volume. |
| **Radius** | DT wood thickness. Thick trunks: `RADIUS_DOUBLE` via `WoodThickness.radius`. Integer 1–24. Do not use `ThickBranchBlock.getRadius` (returns 0 when `isSameTree` fails). |
| **Core** | A wood node that does not auto-break. Absolute rule: `radius >= 4`. |
| **Limb** | A twig: `radius < 4`. |
| **Trunk shell** | DT `TrunkShellBlock` occupying the 8 neighbors around a thick core (`radius > 8`). Visual filler owned by that core. |
| **Primary thickness** | DT family value, normally 8. One full vanilla log's worth of wood. |

Radius 8 is a full log. Radius 4 is already a solid trunk section. Mega spruce
cores are often 16–24. Twigs are radius 1–3.

## 3. Two worlds

Every code path must know which world it is in. Mixing them is the usual
regression.

| | Standing tree | Fallen body |
|---|---|---|
| Location | Overworld / dimension | Sable plot (`Sable.HELPER.getContaining != null`) |
| DT network destroy | Allowed (`sloppyBreak`, `dropTree`) | Forbidden |
| Mining speed | Primitive-log progress × `standing_wood_mining_speed` × 8 / radius | Primitive-log progress × `fallen_wood_mining_speed`, scaled by radius |
| Harvest | DT whole-tree / branch cut | `TreeHarvest.breakAt` only |
| Collision callback | Must do nothing | Leaves and limbs snap; cores never snap |
| Renderer | Native DT | Native DT. No global baked-model rewrite |

If a mixin or event cannot prove the block is in a SubLevel, it must leave DT
alone.

## 4. Inviolable rules

These are testable. If a change breaks one, revert it.

### R1. Standing trees remain mineable
A rooted DT branch, thick branch, or trunk shell must always be mineable with
primitive-log tool-tier, enchantment, and status-effect scaling. The default
`standing_wood_mining_speed` is 0.85 at radius 8, and other radii scale linearly
from that. This override applies only to rooted DT
wood outside a Sable SubLevel; unmanaged Sable bodies retain native behavior.

### R2. Fallen cores are actually removed, and wood pays its grown volume
When a player finishes mining a core on a fallen body, that block is gone,
its shells are gone, and the body pays loot (survival, non-creative).
Loot comes from the body's grown-volume budget (section 7), never from a
thickness dice roll. Volume is normalised to the tree's own base radius, so
the thickest trunk block of any grown species is one log and thin species pay
by height. Over the life of a body it pays exactly `floor(volume / 4096)` logs
per branch type of that normalised volume. Sticks and other non-log drops are
not part of that count.
A thick core pays several logs from one block (radius 16 is four, radius 24 is
nine). Trunk shells add no volume.
A finished mining animation with the block still there is a bug.

### R3. Fallen bodies never re-fell as DT entities
Chopping, colliding, or assembling inside a SubLevel must not call
`FallingTreeEntity.dropTree` or `BranchBlock.sloppyBreak` network destruction.
`destroyMode` is `IGNORE` for the duration of assemble and harvest removal.

### R4. Cores never auto-break
Any wood with `WoodThickness.radius >= 4` (and trunk shells) is rigid. It
must not snap on collision and must not be removed because a neighbor was
mined or snapped. Only a player mining that core removes it.

### R5. Thin wood and leaves auto-break through one transaction
`radius < 4` branches snap when impact speed exceeds their thickness-scaled
`branch_collision_break_velocity`. Fallen leaves use the separate
`leaf_collision_break_velocity` threshold (default 0.25 m/s).
A fluid contact does not snap leaves or twigs and does not start ground settling.
The same thin-wood flood runs when a player mines a connected piece.
Cores stop the flood. Standing wood is not fragile. Standing leaves must
not form a physics pin (they cannot hold a trunk). Collision must not
call `destroyBlock` / DT `sloppyBreak`.

A fallen body's wood, shells, and leaves are ignored while the player tests
whether they can stand. The trunk still blocks movement, but it cannot force
the crawling or swimming pose.

### R6. One thickness threshold for collision and harvest cascade
Use the same thickness threshold (`WoodThickness.THRESHOLD`, 4) in both
paths. Do not use a relative `neighborRadius * 2` test. Equal-thickness
cores stay and split into a new SubLevel. Read thick-trunk radius from
`RADIUS_DOUBLE` (mixin on `ThickBranchBlock.getRadius`), never the DT
`isSameTree` path that returns 0 in a SubLevel.

### R7. Center of mass is wood-thickness-weighted
`COM = sum(position * radius²) / sum(radius²)` over every `BranchBlock` in
the plot (`radius > 0`), including thin wood. Leaves do not contribute.
Read blocks from the plot (`LevelAccelerator(subLevel.getLevel())` + plot
AABB), not overworld `getBlockState` at those coords if that would miss
plot chunks. Do not use the geometric bounding-box center. Do not apply a
flat “shift 30% toward the base” hack. Recalculate after assemble and
after every split. A tip chop with only twigs still needs a wood COM;
cores-only (`radius >= 4`) leaves `totalWeight == 0` and the canopy COM
makes the trunk levitate.

### R8. Topple, do not flip
Impulse is horizontal only. No upward linear impulse. The spin is
`impulse_torque` (default 0.40 rad/s) about a horizontal axis. Linear velocity
is the horizontal speed that cancels that spin at the lowest wood, so the
stump stays put and the crown falls away from the player. Sneak reverses
direction. Tracked bodies must not gain upward COM velocity (a snagged canopy
is not a pivot that levitates the stump).

### R9. Client/server hit positions may disagree
On a moving SubLevel, re-raycast on the server from the player's look vector
and break that block. Do not trust the client-sent `BlockPos` alone.

### R10. Do not rewrite DT's branch renderer globally
Do not mixin `ThickBranchBlockBakedModel` / `BasicBranchBlockBakedModel` to
force ring textures on every UP/DOWN face. That change made bottoms vanish
and stopped cores from mining. Thick cores get trunk shells via
`DtApi.addTrunkShells`. End-grain appearance is DT's native connection
logic, not a global texture override.

### R11. No recursive assemble
`DtApi.isAssembling()` is true for the whole assemble/split. Nested
`dropTree` returns null. Nested `sloppyBreak` is cancelled. Splitting must
not call `SubLevelAssemblyHelper.assembleBlocks` on a plot whose SubLevel
is already removed.

### R12. Config off is a full revert
`enabled = false` restores DT falling entities, DT hardness, DT volume loot,
and DT axe damage. No leftover mixin behavior.

### R13. Disconnected wood means independent bodies
After omitting the chopped block, partition the remaining wood by six-directional
connectivity before Sable assembly. Each wood component becomes its own SubLevel.
Leaves and shells never reconnect separate wood components: each shell belongs
only to its muse core and each connected leaf cluster is assigned to one wood
component. If any component fails, remove every body created by that conversion
and restore the complete standing snapshot.

### R14. Fallen ownership survives reload and split
Persist the managed marker, exact species, original base radius, established-
tree classification, and the unpaid volume budget in Sable user data. On reload, lazily reconstruct plot-local
wood, leaves, and shell ownership before allowing interaction. Every split inherits
the original standing-tree metadata. A missing runtime registry entry must never
fall through to DT network destruction for a persisted managed body.

## 5. Felling (standing → fallen)

1. Player chops a standing branch. DT computes `BranchDestructionData`.
2. If disabled, over `max_blocks`, or already assembling: DT handles it.
3. Otherwise intercept `FallingTreeEntity.dropTree`. Before mutation, snapshot
   the original base radius and topology. A tree is established when its base
   radius is at least 4, its original network/height contains at least 5 wood
   nodes, and it has at least 2 endpoints. Clear leftover DT wood
   and leaves at the cut and every destruction-data position (DT's
   `onDestroyedByPlayer` returns false, so the chopped block is still there).
   Do **not** place the chopped standing block into the SubLevel — it was
   harvested; drop one primitive log at the cut.
4. Place DT branch states, not vanilla logs. After each thick core
   (`radius > 8`), call `setRadius` so trunk shells exist and are included
   in the plot.
5. Partition remaining branches by six-directional wood connectivity and
   assemble each component independently (R13). Assign foliage only after wood
   partitioning so a touching canopy cannot bridge severed branches.
6. Do not pay DT's supplied stick, seed, sapling, or foliage drops at felling.
   Those resources are deferred until their physical branch or leaf breaks.
7. Apply topple impulse (R7, R8) to every body. Persist and track every UUID.
8. One axe durability on the fell, not extra DT volume `damageAxe`.

## 6. Mining

### Standing
`destroyProgress = primitiveLogProgress * standing_wood_mining_speed * 8 / radius`.

The default multiplier is 0.85, so a radius-8 trunk is a little slower than its
primitive log and not an exact match. A radius-4 trunk is twice as fast as that;
a radius-16 trunk is half as fast. Primitive-log progress preserves axe material,
tool tier, Efficiency, Haste, Mining Fatigue, and correct-tool behavior. Standing
thick-trunk shells resolve their muse core and use the core's radius.

A mechanical saw or drill uses the same ratio as its destroy speed, so the break
takes about as long as an axe chop on that thickness.

### Fallen, per block
`destroyProgress = primitiveLogProgress * fallen_wood_mining_speed / max(1, radius / 8)`.

The default fallen multiplier is 2.0.

| Radius | Relative time vs vanilla log at the default multiplier |
|---|---|
| 1–8 | 0.5× |
| 16 | 1× |
| 24 | 1.5× |

Look up radius from the `BlockState`, never from `BlockGetter.getBlockState`
inside a moving plot. Trunk shells delegate to their muse core's progress.

Axe: one durability per harvested fallen block. Skip DT `damageAxe` volume hit.

## 7. Harvest on a fallen body

`TreeHarvest.breakAt` / `snapFrom` is the only removal path for SubLevel DT wood.
A horizontal Create mechanical saw uses that path on a fallen body. On a
standing trunk or shell it mills the cut and everything supported through it:
those blocks pop and drop their wood and leaves, and the stump below the cut
stays. A mechanical drill uses that same fallen-body chop, and on a standing
trunk or shell it calls DT `futureBreak`, so the crown converts to a SubLevel
and the cut pays one log.

### The grown-volume budget

Radius 4 is the physics line (cores never snap). It is not a loot line.
Species differ in how thick they grow: birch tapers at 0.1 and tops out near
radius 6, mega spruce cores reach 24. DT's own `radius² × 64` rule pays a
seven-tall birch trunk two logs. Loot here normalises to the tree instead.

- **Block volume** (`WoodYield.volumeForRadius(radius, baseRadius)`):
  `unit = clamp(baseRadius, 4, 8)`; volume is `4096 × max(radius / unit,
  (radius / 8)²)`. The thickest block of a grown tree is one log. Below the
  unit a block pays linearly with radius, so height drives thin species. Above
  radius 8 a block pays by cross-section, so a mega core is several logs. A
  radius-1 oak twig is a small fraction of a log. Base radius is the standing tree's
  base as snapshotted at the fell, so a tip chop still normalises to the full
  tree; saplings clamp to 4 and pay less per block.
- At conversion, each body snapshots that volume per branch-block type over the
  wood placed in it, scaled once by DT's harvest multiplier and the family
  loot-volume multiplier (`DtIntegration.lootVolumeScale`). That is the body's
  **unpaid volume**. Trunk shells add nothing; the core's radius already
  measures the whole cross-section.
- On every wood break (mine or snap), `credit = unpaid − volume still standing`.
  Pay `credit / 4096` whole logs. The sub-log remainder stays owed, so a later
  break can still complete a log. Sticks are not taken out of this budget.
- Logs go through the species branch loot table (`DtIntegration.branchDrops`)
  on that whole-log volume, so stripped and normal branches pay their own
  primitive log.
- Stripping a fallen block (axe right-click, DT's `stripBranch`) moves that
  block's debt from the normal to the stripped branch type
  (`WoodBudget.transfer`, hooked by `FallenBranchStripMixin`), so it later
  pays stripped logs. DT shaves one radius while stripping; a stripped block
  is measured at its pre-strip radius (`DtIntegration.yieldRadius`), so
  stripping never turns a log into sticks. Shell ownership DT dropped while
  shrinking a thick core is forgotten on the body.
- A split divides unpaid volume in proportion to the wood volume that moves.
  Sable-initiated splits do the same through `FallenTreeRegistry`.
- Reload restores the budget from user data; bodies saved before the budget
  existed owe exactly the wood still standing in the plot.

Outcomes: a young birch whose placed wood is under 4096 pays no logs. A
grown birch with base 6 and trunk 6,5,5,4,4,3,3 pays five birch logs (DT's
area rule: two). An oak radius-8 block is one log and its trunk 8,7,6,5 pays
about 3.25 logs. A radius-16 redwood core is four logs from that one block,
and mining one of its shells breaks the core and pays that same four, never
eight extra.

### Mining a core (`WoodThickness.isRigid`)
1. Remove that one core and its trunk shells.
2. Flood-fill connected `isThinWood` from the core's neighbors. Cores stop the flood.
3. Pay the break from the budget above.
4. Clear leaves that no longer touch remaining wood.
5. Split remaining connected components into new SubLevels. Largest stays.
   Recalculate COM on each new body (R7). Forget the old tracker UUID.

### Mining a limb (`isThinWood`)
1. Flood-fill connected thin wood, including the mined block.
2. Remove it, pay the break from the budget, clear unsupported leaves, and split.

### Chance drops when a block breaks

Logs are the only guaranteed drop. Everything else rolls when that block is
destroyed or the body collides hard enough to snap it, the same way vanilla
leaves roll saplings and sticks.

- Each removed branch block rolls the vanilla leaf stick chance (2% with no
  Fortune, rising with Fortune, 1 or 2 sticks). `fallen_sticks_per_log` scales
  that chance: 4 is vanilla, 0 is never, 8 is twice as often. The stick item
  is the one named by that species' branch loot table, so an aether branch
  drops skyroot sticks.
- The same break runs the rest of the branch loot table with zero volume, so
  pools that are not log or stick counts still roll. Golden oak amber is the
  example: it needs its harvesting tool and Fortune, and it rolls once per
  broken branch rather than once per log pile.
- Do not spawn leaf sticks, seeds, saplings, or other foliage when the standing
  tree converts.
- When a fallen or unsupported leaf actually breaks, run its DT
  `LeavesProperties` loot table with the body's persisted exact species. That
  table is the whole drop: saplings or seeds, leaf sticks, and species extras
  such as Spectrum pigments.
- Preserve DT's native percentage chance, seasonal seed factor, and Fortune
  behavior. Collision uses an empty tool; manual breaking uses the player's tool.
- Creative removal produces no branch or leaf loot.

On a fallen body, every wood or leaf block shows the guaranteed logs of that
entire current SubLevel segment, including stripped logs, with their counts.
Dynamic Trees may still draw the species seed and a stick icon on a branch.
Those icons have no count, because sticks, saplings, and other extras are
rolled when the block breaks. The stick icon stays on every standing block of
a tree that has wood, including a base whose log volume is exact. Log icons
are sorted ahead of those other drops, and the icons share one line. A split is its own segment and
its own budget, so both hints recalculate dynamically after a cut. Creative mode shows
nothing. Only a standing Dynamic Trees trunk uses position-based cascade: the
block on the root shows that whole network, and each block farther from the
root shows less. A break that splits
the crown recalculates that piece alone.

Do not invoke `sloppyBreak`. Return `false` from `onDestroyedByPlayer` after
custom removal so Minecraft does not delete the block a second time.

## 8. Collision on a fallen body

Same `WoodThickness` rules as harvest cascade (R4, R5, R6).

```
if standing leaves (not in a SubLevel): removeCollision  // foliage cannot pin
if not in a live SubLevel: NONE
if world contact is standing leaves: removeCollision; snap if this block is a limb/leaf
if isRigid: NONE
if not isAutoBreakable: NONE
if leaves and impact >= leaf_collision_break_velocity: snap
if impact too slow: NONE
TreeHarvest.snapFrom(pos)   // never destroyBlock
```

## 9. Rendering

- Keep DT branch models (`loader: dynamictrees:branch`, bark + rings).
- Thick cores must include trunk shells so the 3×3 trunk looks solid
  (`radius > 8` only). Shells are `RenderShape.INVISIBLE`; crack overlay
  is drawn on the muse core model shifted to the core block.
- `ThickBranchBlock.getRadius` must read `RADIUS_DOUBLE` so plot cores
  keep their thickness in the model.
- The chopped standing block must be cleared and must not be re-placed
  into the SubLevel. Leftover source wood sits in the world, z-fights
  the SubLevel, and poisons connection-based faces.

## 10. Physics

- Linear velocity: horizontal, and large enough to cancel the spin at the
  lowest wood so the stump does not swing toward the player.
- Angular velocity: horizontal axis perpendicular to fall direction.
- Mass for impulse scaling: SubLevel mass tracker.
- COM: R7. Every branch (`radius > 0`) contributes `radius²`. Trunk shells
  count as primary-thickness mass. Leaves do not.
- Kill upward COM velocity on tracked bodies (R8).
- After collision or harvest splits a body, each piece gets its own COM.
- Fallen wood floats at green-log density: `mass / volume = 0.8`. A full cell
  (radius ≥ 8, or a trunk shell) is mass `0.8 * fallen_wood_mass_multiplier`
  and volume `1 * fallen_wood_mass_multiplier`. Thinner wood scales both by
  `(radius / 8)²`, and radius above 8 stays one cell because the extra
  thickness is shells. Equilibrium leaves about one fifth of a full cell
  above the water. A fluid contact does not start ground settling and does
  not snap leaves or twigs. While that contact is fresh, each game tick
  removes 8% of linear velocity and 12% of angular velocity on every axis.
  Solid hits still kill upward speed (R8). `enabled = false` leaves Sable's
  original mass, volume, and friction.

## 11. Forbidden shortcuts

These already shipped and were reverted. Do not revive them.

| Shortcut | What it broke |
|---|---|
| Global `ThickBranchEndGrainMixin` on DT baked models | Bottoms missing; cores not mining |
| `getDestroyProgress` override on all DT wood | Standing trees unmineable or instant |
| Hardness via `state.getDestroySpeed(level, pos)` in a plot | Wrong block; mega cores one-shot |
| `FragileBlockCallback.destroyBlock` on DT wood | Cores snapped; standing DT unmineable |
| `ThickBranchBlock.getRadius` for collision | Returns 0; mega cores treated as twigs |
| `destroyMode` left at `SLOPPY` during assemble | StackOverflow / crash |
| Splitting after the source SubLevel is removed | `assembly attempted inside plot of already removed sub-level` |
| Allowing `sloppyBreak` inside a SubLevel | Fallen body becomes a DT falling tree |
| Trusting client `BlockPos` on a moving body | Mining finishes, nothing happens |
| Flat 30% COM shift toward AABB minY | COM still wrong on thick trunks |
| Assemble all destruction-data blocks as one body | Severed crown branches stay welded together |
| Let touching leaves connect wood components | Canopy re-welds branches after their junction is cut |
| Pay DT's supplied non-log drops at conversion | Sticks and saplings duplicate before foliage breaks |
| Rediscover species from roots inside a fallen plot | `NULL_SPECIES`; broken leaves never drop DT seeds |
| Per-branch thickness log chance (10/30/60%) and a one-log core minimum | Birch pays twigs for a grown trunk; redwood cores cap at one log |
| DT's raw `radius² × 64` with no species normalisation | A seven-tall grown birch pays two logs |
| Leave a stripped block's debt under the normal branch type | It pays a normal log early and the stripped block pays nothing |
| Pay sticks eagerly from a body's whole remaining volume | A stick paid early removes a log the grown tree earned |

## 12. Change process

1. Name the concern: felling, mining-standing, mining-fallen, harvest,
   collision, physics, or rendering. Touch only that concern.
2. Quote the rule numbers this change must preserve.
3. After the change, run the regression list below before calling it done.
4. Collision and harvest cascade must both call `WoodThickness`. Mining
   speed must not.

## 13. Regression checklist

Standing
- [ ] Oak/spruce branch mines with an axe (not instant, not impossible)
- [ ] Iron/diamond/netherite axes retain proportional tool-tier scaling
- [ ] Mega spruce trunk mines at configured primitive-log speed
- [ ] Chopping the base felling-converts to a SubLevel, no `falling_tree`
- [ ] Chopping a core junction creates one body per disconnected crown branch,
      even when their leaves touch
- [ ] Crack overlay shows on the top AND the bark sides of a thick core
- [ ] Canopy snag in another tree: foliage gives; the trunk falls, it does not levitate
- [ ] Mid-branch / tip cut leaves the stump planted; the trunk does not levitate
- [ ] Addon disabled: DT entity, DT hardness, DT loot

Fallen appearance / motion
- [ ] Body tips over; it does not flip upside down
- [ ] A felled trunk in water rises, sits low, and stops bouncing. Leaves stay on through the splash
- [ ] COM sits in the wood (radius²-weighted), not the leafy canopy. A tip chop must not lift the stump.
- [ ] Thick cores have shells; no hollow 1-block trunk
- [ ] Sneak reverses fall direction

Fallen mining
- [ ] Mining a core removes that core and drops radius-scaled rigid-wood loot
- [ ] A grown birch body pays the same log count however it is mined, and about one log per trunk block
- [ ] Stripping a fallen radius-8 oak block then mining it drops one stripped oak log, not oak or sticks
- [ ] A young birch under 4096 volume pays no logs; broken branches may roll sticks
- [ ] A radius-16 core pays four logs; mining one of its shells pays that same four
- [ ] Mining a twig off a big body pays no log; the log arrives once enough wood has left
- [ ] Jade on a fallen body lists logs only, and the count matches on every block of that segment
- [ ] Breaking or colliding a leaf can drop that species' sapling, seed, or extra (Spectrum pigment)
- [ ] Reload keeps the unpaid budget, so a half-mined body finishes with the same total
- [ ] Leaves drop exact-species DT seeds/saplings only when physically broken
- [ ] Remaining equal-thickness trunk becomes its own SubLevel
- [ ] Mining a thin branch removes that limb only
- [ ] Trunk shells mine at the core's speed and break with the core

Collision
- [ ] Mega spruce cores survive ground impact
- [ ] Leaves and twigs snap on ground / neighbor trees
- [ ] Snapping limbs does not delete nearby cores
- [ ] Body does not snag forever in a neighbor canopy

Stability
- [ ] No crash on first fell
- [ ] No crash when breaking a core that still has limbs
- [ ] No second DT falling tree when mining a fallen piece
- [ ] Reloading the world preserves ownership/species and fallen mining behavior
- [ ] Failed multi-component assembly restores the source and leaves no tracked body
