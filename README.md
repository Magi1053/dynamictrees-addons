# Dynamic Trees Addons

NeoForge 1.21.1 [Dynamic Trees](https://modrinth.com/mod/dynamictrees) compatibility mods, published individually on [Modrinth](https://modrinth.com/organization/viberanium) under **Viberanium**.

Gradle multi-project: each `mods/<name>` folder is a subproject (`:<name>`) and its own Modrinth download.

| Module | Mod ID | Description |
|--------|--------|-------------|
| [`mods/aether`](mods/aether) | `dtaether` | The Aether + Deep Aether trees |
| [`mods/vinery`](mods/vinery) | `dtvinery` | [Let's Do] Vinery trees |
| [`mods/meadow`](mods/meadow) | `dtmeadow` | [Let's Do] Meadow trees |
| [`mods/rubber`](mods/rubber) | `dtrubber` | Rubber tree + latex tapping |
| [`mods/spectrum`](mods/spectrum) | `dtspectrum` | Spectrum colored trees |

Player-facing details are in each module’s `README.md` (synced as the Modrinth long description).

## Requirements

- JDK 21
- NeoForge 1.21.1 (runtime deps vary by module; see each README)

## Build

```bash
./gradlew :aether:build
./gradlew :vinery:build
./gradlew :meadow:build
./gradlew :rubber:build
./gradlew :spectrum:build

./gradlew build   # all modules
```

Shared NeoForge / Minotaur config lives in the root `build.gradle`. Module scripts only declare dependencies (and aether’s run config).

Release jars are named `{mod_id}-neoforge-{mc}-{version}.jar` (example: `dtrubber-neoforge-1.21.1-1.0.45.jar`) and use committed `src/main/resources`. Optional `generateResources` (Node) is only needed when regenerating assets.

## Contributing

Prefer small, module-scoped changes under `mods/<name>/`. Mention the module name in issue titles when relevant.

## Releases

Modules version and publish independently.

1. Bump `mod_version` in `mods/<name>/gradle.properties`.
2. Add a top section in `mods/<name>/CHANGELOG.md`:

```markdown
## 1.0.46

- Fixed latex drip rate
```

3. Push tag `<module>/v<version>` (example: `rubber/v1.0.46`).
4. GitHub Actions publishes that module to Modrinth (`MODRINTH_TOKEN` secret).

| Modrinth surface | Content |
|------------------|---------|
| Version page | `## <version>` body only |
| Changelog tab | Stacked version notes (Modrinth UI) |
| Overview | Module `README.md` |

Publish fails if the matching changelog section is missing or empty.

## License

[MIT](LICENSE)
