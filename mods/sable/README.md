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
