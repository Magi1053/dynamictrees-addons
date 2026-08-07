# Dynamic Trees Addons (SKCraft)

Monorepo of NeoForge 1.21.1 Dynamic Trees addons published individually to Modrinth.

| Module | Mod ID | Description |
|--------|--------|-------------|
| `mods/aether` | `dtaether` | The Aether + Deep Aether trees |
| `mods/vinery` | `dtvinery` | [Let's Do] Vinery trees |
| `mods/meadow` | `dtmeadow` | [Let's Do] Meadow trees |
| `mods/rubber` | `dtrubber` | Rubber tree + latex tapping |
| `mods/spectrum` | `dtspectrum` | Spectrum colored trees |

## Build

Each module is a standalone Gradle project. From the repo root:

```bash
./gradlew -p mods/aether build
./gradlew -p mods/vinery build
./gradlew -p mods/meadow build
./gradlew -p mods/rubber build
./gradlew -p mods/spectrum build
```

Release builds use committed `src/main/resources` and do **not** require Node or regenerate scripts.
Optional regenerate: `./gradlew -p mods/<name> generateResources` (module-specific inputs; see each README).

## Release / Modrinth

1. Bump `mod_version` and update `CHANGELOG.md` in the module.
2. Set `modrinth_project_id` once the Modrinth project exists.
3. Push and tag: `<module>/v<version>` (example: `rubber/v1.0.46`).
4. GitHub Actions builds that module and runs Minotaur (`MODRINTH_TOKEN` secret).

One-time setup:

1. Create five Modrinth projects (one per module); paste each project ID into that module’s `modrinth_project_id`.
2. Set the repo secret: `gh secret set MODRINTH_TOKEN` (token needs `CREATE_VERSION`).
3. Optional: transfer this repo to the `SKCraft` GitHub org when create permissions allow.

Modules version and publish independently — tagging one mod does not release the others.

## License

MIT


> GitHub repository: https://github.com/Magi1053/dynamictrees-addons (transfer to SKCraft org when org create permissions are available).
