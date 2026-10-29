# Dynamic Trees Sable

Replace Dynamic Trees' falling-tree entity with a harvestable [Sable](https://modrinth.com/mod/sable) physics SubLevel. Chop a living branch at vanilla log speed; the felled wood becomes a rigid body that collides with the world. Then mine each log off the fallen tree.

Behavior contract: [SPEC.md](SPEC.md). Implement against those rules; a fix that violates another rule is a failed change.

## Features

- Intercepts Dynamic Trees felling and assembles the cut volume into a Sable SubLevel
- Standing branches break at primitive-log speed (not whole-tree radius hardness)
- Log count on the fallen body matches Dynamic Trees volume yield; leftover sticks drop at the cut
- Leaves break on impact and decay after the body settles
- Sneak while chopping to reverse topple direction

## Requirements

| Dependency | Required |
|---|---|
| Minecraft 1.21.1 | Yes |
| NeoForge | Yes |
| [Dynamic Trees](https://modrinth.com/mod/dynamictrees) | Yes |
| [Sable](https://modrinth.com/mod/sable) | Yes |

## Create integration

[Create](https://modrinth.com/mod/create) is optional. Integration covers fixed machines, moving Create contraptions, and machines mounted on Sable sublevels, including Create Aeronautics.

- **Mechanical saws** harvest standing Dynamic Trees through Create's tree-cutting queue without converting them into Sable bodies. Sable adjusts breaking speed using the species' normal log hardness and trunk cross-sectional area, and calculates wood drops using its grown-volume rules. Recorded branch and leaf states supply break sounds and particles, limited to eight branch/leaf effects per cut to keep large canopies from producing particle bursts. Additional items produced during harvesting, including fruit, pods, and addon decorations, follow the same collection path and retain native loot and ripeness rules. Falling Dynamic Trees fruit and pods are collected using their block loot.
- **Mechanical drills** fell standing Dynamic Trees into Sable bodies and collect the cut-block drops. The remaining fallen wood stays available to harvest.
- **Fallen trees** can be harvested by saws and drills without creating another falling body.
- Drops use Create's normal collection and overflow handling: contraptions insert into mounted storage and eject items that do not fit. Fixed machines eject into the world; sublevel machines use their native ejection or Simulated's connected storage when available.
- Moving saws discard stale cutting targets when they leave the target block, preventing a regrown tree from being cut remotely. Ordinary blocks retain Create's normal behavior.

Rotated aircraft and Simulated connected storage have not been verified in game.

## Notes

- Does not wrap Dragged's Tree Chopper or Sable Physics Tree. Those mods do not understand Dynamic Trees branches.
- Very large trees (over the configured block cap) still use Dynamic Trees' original falling entity.
- Built for NeoForge 1.21.1.

## Links

- Source: [Magi1053/dynamictrees-addons](https://github.com/Magi1053/dynamictrees-addons/tree/main/mods/sable)
- Issues: [GitHub Issues](https://github.com/Magi1053/dynamictrees-addons/issues)
- Organization: [Viberanium on Modrinth](https://modrinth.com/organization/viberanium)

## License

MIT
