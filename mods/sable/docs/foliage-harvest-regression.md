# Managed foliage removal regression

## Contract and ownership

Native Dynamic Trees hydration, shade, and water updates may remove leaves without
calling block loot. Once DT Sable owns a leaf, those removals must use the same
harvest transaction as manual and collision harvesting. The fix is confined to
`DynamicLeavesCollisionMixin`; ordinary unmanaged leaves keep native behavior.
Spectrum's color tables are not missing and were not changed. The existing extra
seed quota is intentional and is separate from native leaf loot.

The integration scenarios live in the SKCraft pack's isolated Shenanigans runtime
test sources, under `compat/dynamictrees`. They use native DT and DT Sable APIs;
the whole-tree fixture creates forms with `dt settree`, bypassing pack growth
balance. The shared pack mod and runtime dependencies remain loaded, so these
runs do not establish startup compatibility with a minimal standalone mod list.

## Frozen inputs

Verified November 14, 2026 with Minecraft 1.21.1, NeoForge 21.1.262, Amazon
Corretto 21.0.13, Sable 2.0.5, DT 1.7.2, DT Spectrum 1.0.22, and Spectrum 1.12.5.

- Shipping DT Sable 1.1.10 jar SHA-256:
  `9ba5003203e0c32b8f9ff6fd43be3970973afe091d1f18070d86679198d7d6a0`.
- Candidate jar SHA-256:
  `1c2cd0862f52ed80cca657a8a70c7c425706e02cc70cae840f51bab711c985d6`.
- Candidate `DynamicLeavesCollisionMixin.class` SHA-256:
  `9331cb77ae1da6908d1771dde22364093dcef0c24b78f53d5a42aea61d48f79f`.
- Unchanged `TreeHarvest.class` SHA-256 in both jars:
  `bc43980dda5075be413630292dd0cb5eb461d046a9a4c2d606b711448821e89b`.

Full dependency hashes and immutable production/test-class/resource snapshot
hashes are recorded in the pack's `.tmp/agent-tree-balance/*-inputs.json` receipts.
The candidate was tested through a runtime override directory; shipping pack jars
were not replaced to prepare these runs. A release jar can have a different
archive hash from version metadata while retaining the verified class bytecode.

## Reproduction and results

`tree-spectrum-baseline-1` passed `spectrum-leaf-loot`: 2,048 native block and
foliage-table rolls per each of the 16 colors yielded pigment and colored seeds
through both entrypoints. This rejected the missing-table hypothesis.

`tree-spectrum-fallen-baseline-1` reproduced the loss on a real managed SubLevel:
native hydration removed an owned leaf and paid zero pigment/seeds, while manual,
collision-sink, and shaft removal each paid the diagnostic one of each. That
initial synchronous fixture also removed bodies without a teardown drain and
logged a shutdown error; it is reproduction evidence, not final acceptance.

`tree-spectrum-candidate-1` verified the hydration fix, but its shade fixture did
not actually select the native shade branch and its playerless full trees
unloaded. Those invalid controls were corrected before final acceptance.

`tree-spectrum-candidate-2` passed both scenarios and Gradle exited zero with
fresh `ALL GAME TEST CHECKS PASSED` results:

- `spectrum-fallen-leaf`: 160 trials, covering all 16 colors through actual server
  game-mode breaking, metadata recovery, the direct harvest helper, collision
  sink, hydration, water, shade, shaft removal, Create's harvest adapter, and an
  unmanaged-decay control. Each managed removal paid exactly one diagnostic
  pigment and seed; unmanaged decay paid neither. Diagnostic loot pools and the
  temporary shade light requirement were restored after each trial.
- `spectrum-tree-felling`: mature red, blue, and white trees were felled through
  native DT felling into Sable bodies, moved physically for 80 ordinary world
  ticks, then harvested through successive native player cuts and resulting
  splits. Original source-canopy accounting was conserved. Final guaranteed
  pigment payouts were exactly 599/599, 594/594, and 585/585; 127, 138, and 133
  paid automatically before manual cuts. Final seed counts were 600, 599, and
  588, retaining the existing extra seed quota. Player cuts numbered 32, 31, and
  31. Test-only admin SubLevel tickets prevented playerless unloading and were
  released; the fixture required physical pose movement and drained removals
  before shutdown.

The final reports are `spectrum-fallen-leaf.txt`, `spectrum-tree-felling.txt`,
`results.txt`, and `logs/latest.log` under the corresponding pack runtime's
`.tmp/agent-game-test/server/<runtime-id>/run/` directory. These checks exercise
server harvesting and real physics, not graphical client ray selection. The
synthetic collision and saw cases call the real harvest entrypoints rather than
constructing moving saw machinery or a physical impact for each color.

## Rerun

From the pack's `packages/shenanigans`, select `spectrum-leaf-loot`,
`spectrum-fallen-leaf`, or `spectrum-tree-felling` with `serverTest`, a fresh
`runtimeTestId`, `runtimeTestHeap=8G`, `neo_version=21.1.262`, and
`runtimeTestModsDir` pointing at a verified addon candidate directory. Keep game
JVMs sequential and require process success plus fresh scenario pass markers.
