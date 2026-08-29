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
| Mining speed | Native DT | Primitive-log speed scaled by this block's radius |
| Harvest | DT whole-tree / branch cut | `TreeHarvest.breakAt` only |
| Collision callback | Must do nothing | Leaves and limbs snap; cores never snap |
| Renderer | Native DT | Native DT. No global baked-model rewrite |

If a mixin or event cannot prove the block is in a SubLevel, it must leave DT
alone.

## 4. Inviolable rules

These are testable. If a change breaks one, revert it.

### R1. Standing trees remain mineable
A rooted DT branch, thick branch, or trunk shell must always be mineable with
DT's own `getDestroyProgress` / `getHardness`. Addon mining mixins apply only
when the block is inside a SubLevel.

### R2. Fallen cores are actually removed
When a player finishes mining a core on a fallen body, that block is gone,
its shells are gone, and a primitive log item drops (survival, non-creative).
A finished mining animation with the block still there is a bug.

### R3. Fallen bodies never re-fell as DT entities
Chopping, colliding, or assembling inside a SubLevel must not call
`FallingTreeEntity.dropTree` or `BranchBlock.sloppyBreak` network destruction.
`destroyMode` is `IGNORE` for the duration of assemble and harvest removal.

### R4. Cores never auto-break
Any wood with `WoodThickness.radius >= 4` (and trunk shells) is rigid. It
must not snap on collision and must not be removed because a neighbor was
mined or snapped. Only a player mining that core removes it.

### R5. Thin wood and leaves auto-break together
`radius < 4` branches and leaves snap when impact speed exceeds
`branch_collision_break_velocity`. Fallen leaves snap on any contact.
The same thin-wood flood runs when a player mines a connected piece.
Cores stop the flood. Standing wood is not fragile. Standing leaves must
not form a physics pin (they cannot hold a trunk). Collision must not
call `destroyBlock` / DT `sloppyBreak`.

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
Impulse is horizontal only. No upward linear impulse. Defaults:
`impulse_force = 0.6 m/s`, `impulse_torque = 0.35 rad/s`. The tree falls away
from the player. Sneak reverses direction. Tracked bodies must not gain
upward COM velocity (a snagged canopy is not a pivot that levitates the
stump).

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

## 5. Felling (standing → fallen)

1. Player chops a standing branch. DT computes `BranchDestructionData`.
2. If disabled, over `max_blocks`, or already assembling: DT handles it.
3. Otherwise intercept `FallingTreeEntity.dropTree`. Clear leftover DT wood
   and leaves at the cut and every destruction-data position (DT's
   `onDestroyedByPlayer` returns false, so the chopped block is still there).
   Do **not** place the chopped standing block into the SubLevel — it was
   harvested; drop one primitive log at the cut. Place the remaining felled
   blocks in-world, wrap them with `SubLevelAssemblyHelper.assembleBlocks`.
4. Place DT branch states, not vanilla logs. After each thick core
   (`radius > 8`), call `setRadius` so trunk shells exist and are included
   in the plot.
5. Log-item budget from DT volume loot determines how many nodes are cores
   vs leftover limbs. Extra sticks/non-log drops spawn at the cut.
6. Apply topple impulse (R7, R8). Track the SubLevel UUID.
7. One axe durability on the fell, not extra DT volume `damageAxe`.

## 6. Mining

### Standing
Native DT. Thick trunks are slow because DT hardness is `primitive * r² / 8`.
That is intended for living trees.

### Fallen, per block
`destroyProgress = primitiveLogProgress / max(1, radius / 8)`.

| Radius | Relative time vs vanilla log |
|---|---|
| 1–8 | 1× |
| 16 | 2× |
| 24 | 3× |

Look up radius from the `BlockState`, never from `BlockGetter.getBlockState`
inside a moving plot. Trunk shells delegate to their muse core's progress.

Axe: one durability per harvested fallen block. Skip DT `damageAxe` volume hit.

## 7. Harvest on a fallen body

`TreeHarvest.breakAt` / `snapFrom` is the only removal path for SubLevel DT wood.

### Mining a core (`WoodThickness.isRigid`)
1. Remove that one core and its trunk shells.
2. Drop one primitive log.
3. Flood-fill connected `isThinWood` from the core's neighbors. Cores stop the flood.
4. Drop DT `species.getBranchesDrops` for that thin-wood volume.
5. Clear leaves that no longer touch remaining wood.
6. Split remaining connected components into new SubLevels. Largest stays.
   Recalculate COM on each new body (R7). Forget the old tracker UUID.

### Mining a limb (`isThinWood`)
1. Flood-fill connected thin wood, including the mined block.
2. Remove it, drop volume loot, clear unsupported leaves, split.

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
if leaves: snap at any speed
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

- Linear impulse: away from player (or toward, if sneaking), horizontal.
- Angular velocity: horizontal axis perpendicular to fall direction.
- Mass for impulse scaling: SubLevel mass tracker.
- COM: R7. Every branch (`radius > 0`) contributes `radius²`. Trunk shells
  count as primary-thickness mass. Leaves do not.
- Kill upward COM velocity on tracked bodies (R8).
- After collision or harvest splits a body, each piece gets its own COM.

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
- [ ] Mega spruce trunk mines; it is slow
- [ ] Chopping the base felling-converts to a SubLevel, no `falling_tree`
- [ ] Crack overlay shows on the top AND the bark sides of a thick core
- [ ] Canopy snag in another tree: foliage gives; the trunk falls, it does not levitate
- [ ] Mid-branch / tip cut leaves the stump planted; the trunk does not levitate
- [ ] Addon disabled: DT entity, DT hardness, DT loot

Fallen appearance / motion
- [ ] Body tips over; it does not flip upside down
- [ ] COM sits in the wood (radius²-weighted), not the leafy canopy. A tip chop must not lift the stump.
- [ ] Thick cores have shells; no hollow 1-block trunk
- [ ] Sneak reverses fall direction

Fallen mining
- [ ] Mining a core removes that core and drops a log
- [ ] Connected thin limbs of that core break and drop volume loot
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
