import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, "..", "..");
const outRoot = join(__dirname, "src", "main", "resources");
const dtLootBase = join(repoRoot, ".tmp", "dt", "data", "dynamictrees", "loot_table");
const bundledAssets = join(__dirname, "bundled_assets");
const dtbopJoBase = join(repoRoot, ".tmp", "dtbop", "trees", "dtbop", "jo_codes");
const shortJungleJoBundled = join(bundledAssets, "jo_codes", "short_jungle.txt");
const shortJungleJoSource = existsSync(join(dtbopJoBase, "short_jungle.txt"))
    ? join(dtbopJoBase, "short_jungle.txt")
    : shortJungleJoBundled;

const MOD_ID = "dtrubber";
const SPECIES = "rubber";
const SPAWN_SCALE = 1.25;

function out(...parts) {
    return join(outRoot, ...parts);
}

function writeJson(path, data) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function writeText(path, text) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, text.endsWith("\n") ? text : text + "\n", "utf8");
}

function writeTag(ns, kind, name, values) {
    const parts = name.split("/");
    const file = parts.pop();
    writeJson(out("data", ns, "tags", kind, ...parts, `${file}.json`), { values });
}

function writeRecipes(root, condition, entries) {
    for (const [relativePath, recipe] of entries) {
        writeJson(join(root, relativePath), { ...condition, ...recipe });
    }
}

function cloneLoot(srcRelative, destRelative, replacements) {
    let text = readFileSync(join(dtLootBase, srcRelative), "utf8");
    for (const [from, to] of replacements) {
        text = text.split(from).join(to);
    }
    writeText(out("data", MOD_ID, "loot_table", destRelative), text);
}

// BOP short_jungle: bushy jungle canopy with a short trunk. Vanilla jungle jo codes
// embed 7–10 J's per template even at low radius; cap trunk run for oak-scale height.
function writeRubberJoCodes(destPath, sourcePath, options = {}) {
    const minRadius = options.minRadius ?? 2;
    const maxRadius = options.maxRadius ?? 4;
    const maxTrunkJs = options.maxTrunkJs ?? 3;
    const filtered = readFileSync(sourcePath, "utf8")
        .split(/\r?\n/)
        .filter(Boolean)
        .filter((line) => {
            const colon = line.indexOf(":");
            if (colon < 0) {
                return false;
            }
            const radius = Number.parseInt(line.slice(0, colon), 10);
            return !Number.isNaN(radius) && radius >= minRadius && radius <= maxRadius;
        })
        .map((line) => {
            const colon = line.indexOf(":");
            const radius = line.slice(0, colon);
            const code = line.slice(colon + 1);
            const trunkRun = code.match(/^J+/)?.[0]?.length ?? 0;
            const rest = code.slice(trunkRun);
            const cappedTrunk = "J".repeat(Math.min(trunkRun, maxTrunkJs));
            return `${radius}:${cappedTrunk}${rest}`;
        });
    writeText(destPath, filtered.join("\n"));
}

// Do not set forestness unless overrides include it — forestness 1.0 pins spawns to
// perlin forest peaks, which often line up with biome borders (edge-only rubber).
function rubberSpeciesPool(rubberWeight = 3, poolWeight = 50 - rubberWeight) {
    return {
        method: "splice_before",
        random: {
            [`${MOD_ID}:${SPECIES}`]: rubberWeight,
            "...": poolWeight,
        },
    };
}

function scaledChance(value) {
    return Math.round(value * SPAWN_SCALE * 1000) / 1000;
}

function scaledDensity(value) {
    return [Math.round(value * SPAWN_SCALE * 1000) / 1000];
}

function rubberWorldGenApply(overrides = {}, rubberWeight = 3) {
    const apply = {
        species: rubberSpeciesPool(rubberWeight),
        density: [1.0],
        chance: 0.2,
        ...overrides,
    };

    if (Object.prototype.hasOwnProperty.call(overrides, "forestness")) {
        apply.forestness = overrides.forestness;
    }

    return apply;
}

// Jungle/rainforest biomes are the primary rubber habitat; marshes and swamps are secondary.
function rubberJungleApply(overrides = {}, rubberWeight = 3) {
    const baseChance = overrides.chance ?? 0.2;
    const baseDensity = (overrides.density ?? [1.0])[0];
    const { chance, density, ...rest } = overrides;
    return rubberWorldGenApply(
        {
            chance: scaledChance(baseChance),
            density: scaledDensity(baseDensity),
            ...rest,
        },
        rubberWeight,
    );
}

function rubberWetlandApply(overrides = {}) {
    const baseChance = overrides.chance ?? 0.12;
    const baseDensity = (overrides.density ?? [0.75])[0];
    const { chance, density, ...rest } = overrides;
    return rubberWorldGenApply(
        {
            chance: scaledChance(baseChance),
            density: scaledDensity(baseDensity),
            ...rest,
        },
        Math.round(1 * SPAWN_SCALE),
    );
}

// Splice rubber into an existing DT addon species pool without overriding chance/density.
function rubberSpliceOnly(rubberWeight = 3, poolWeight = 47) {
    return {
        species: rubberSpeciesPool(rubberWeight, poolWeight),
    };
}

function splice(rubberWeight = 2, poolWeight = 48) {
    return rubberSpliceOnly(rubberWeight, poolWeight);
}

function biomeEntry(mod, biome, apply, extraSelect = {}) {
    const entry = {
        select: { name: `${mod}:${biome}`, ...extraSelect },
        apply,
    };
    if (mod !== "minecraft") {
        entry.only_if_loaded = mod;
    }
    return entry;
}

function writeTreePack() {
    const treeRoot = out("trees", MOD_ID);

    writeJson(join(treeRoot, "families", `${SPECIES}.json`), {
        common_leaves: `${MOD_ID}:${SPECIES}`,
        common_species: `${MOD_ID}:${SPECIES}`,
        primitive_log: `${MOD_ID}:rubber_log`,
        primitive_stripped_log: `${MOD_ID}:stripped_rubber_log`,
        stick: "minecraft:stick",
        max_branch_radius: 7,
        generate_stripped_branch: true,
        min_radius_for_stripping: 1,
    });

    writeJson(join(treeRoot, "leaves_properties", `${SPECIES}.json`), {
        primitive_leaves: `${MOD_ID}:rubber_leaves_block`,
        light_requirement: 12,
    });

    writeJson(join(treeRoot, "species", `${SPECIES}.json`), {
        family: `${MOD_ID}:${SPECIES}`,
        tapering: 0.35,
        signal_energy: 9,
        up_probability: 2,
        lowest_branch_height: 3,
        growth_rate: 0.75,
        max_branch_radius: 7,
        leaves_properties: `${MOD_ID}:${SPECIES}`,
        preferred_climate: "tropical",
        climate_tolerance: 0.35,
        acceptable_soils: ["dirt_like"],
        world_gen_acceptable_soils: ["dirt_like"],
        generate_seed: true,
        generate_sapling: true,
        drop_seeds: true,
        features: [
            {
                name: "clear_volume",
                properties: {
                    height: 5,
                },
            },
        ],
        lang_overrides: {
            seed: "Rubber Seed",
            sapling: "Rubber Sapling",
        },
    });

    writeRubberJoCodes(join(treeRoot, "jo_codes", `${SPECIES}.txt`), shortJungleJoSource, {
        minRadius: 2,
        maxRadius: 4,
        maxTrunkJs: 3,
    });

    writeJson(join(treeRoot, "world_gen", "feature_cancellers.json"), []);

    const jungleTag = { tag: "#minecraft:is_jungle" };
    writeJson(join(treeRoot, "world_gen", "default.json"), [
        ["minecraft", "jungle", rubberJungleApply({ chance: 0.24, density: [1.1] })],
        ["minecraft", "sparse_jungle", rubberJungleApply({ chance: 0.2, density: [0.95] })],
        ["minecraft", "bamboo_jungle", rubberJungleApply({ chance: 0.2, density: [0.95] })],
        ["minecraft", "swamp", rubberWetlandApply()],
        ["regions_unexplored", "rainforest", rubberSpliceOnly()],
        ["regions_unexplored", "sparse_rainforest", rubberSpliceOnly()],
        ["regions_unexplored", "marsh", splice(1, 49)],
        ["regions_unexplored", "eucalyptus_forest", splice()],
        ["regions_unexplored", "bamboo_forest", splice()],
        ["regions_unexplored", "tropics", splice()],
        ["regions_unexplored", "bayou", splice(1, 49)],
        ["regions_unexplored", "old_growth_bayou", splice(1, 49)],
        ["natures_spirit", "tropical_woods", rubberSpliceOnly()],
        ["natures_spirit", "tropical_basin", rubberSpliceOnly()],
        ["natures_spirit", "sparse_tropical_woods", rubberSpliceOnly()],
        ["natures_spirit", "marsh", splice(1, 49)],
        ["natures_spirit", "bamboo_wetlands", splice()],
        ["natures_spirit", "tropical_shores", splice(1, 49)],
        ["biomesoplenty", ".*", splice(), jungleTag],
        ["biomesoplenty", "tropics", splice()],
        ["biomesoplenty", "marsh", splice(1, 49)],
        ["biomesoplenty", "wetland", splice(1, 49)],
        ["biomesoplenty", "bayou", splice(1, 49)],
        ["biomeswevegone", ".*", rubberSpliceOnly(), jungleTag],
        ["biomeswevegone", "bayou", splice(1, 49)],
        ["biomeswevegone", "cypress_swamplands", splice(1, 49)],
        ["biomeswevegone", "cypress_wetlands", splice(1, 49)],
        ["biomeswevegone", "white_mangrove_marshes", splice(1, 49)],
    ].map(([mod, biome, apply, extra]) => biomeEntry(mod, biome, apply, extra)));
}

function writeBranchTags() {
    const branchBlocks = [`${MOD_ID}:${SPECIES}_branch`, `${MOD_ID}:stripped_${SPECIES}_branch`];
    writeTag("dynamictrees", "block", "branches_that_burn", branchBlocks);
    writeJson(out("data", "dynamictrees", "tags", "item", "branches_that_burn.json"), {
        values: [
            `${MOD_ID}:${SPECIES}_branch`,
            {
                id: `${MOD_ID}:stripped_${SPECIES}_branch`,
                required: false,
            },
        ],
    });
    writeTag("dynamictrees", "block", "stripped_branches_that_burn", [`${MOD_ID}:stripped_${SPECIES}_branch`]);
}

const TREE_LOOT = [
    "trees/branches/rubber.json",
    "trees/branches/stripped_rubber.json",
    "trees/leaves/rubber.json",
    "trees/voluntary/rubber.json",
];

function existingTreeLoot() {
    const saved = new Map();
    for (const relativePath of TREE_LOOT) {
        const path = out("data", MOD_ID, "loot_table", relativePath);
        if (existsSync(path)) {
            saved.set(relativePath, readFileSync(path, "utf8"));
        }
    }
    return saved;
}

function writeLootTables(preservedTreeLoot) {
    if (!existsSync(dtLootBase)) {
        for (const [relativePath, text] of preservedTreeLoot) {
            writeText(out("data", MOD_ID, "loot_table", relativePath), text);
        }
        return;
    }

    const replacements = [
        ["dynamictrees:jungle", `${MOD_ID}:${SPECIES}`],
        ["dynamictrees:trees/", `${MOD_ID}:trees/`],
        ["minecraft:jungle_log", `${MOD_ID}:rubber_log`],
    ];

    cloneLoot("trees/branches/jungle.json", "trees/branches/rubber.json", replacements);
    cloneLoot("trees/branches/stripped_jungle.json", "trees/branches/stripped_rubber.json", [
        ["dynamictrees:jungle", `${MOD_ID}:${SPECIES}`],
        ["dynamictrees:trees/", `${MOD_ID}:trees/`],
        ["minecraft:stripped_jungle_log", `${MOD_ID}:stripped_rubber_log`],
    ]);
    cloneLoot("trees/leaves/jungle.json", "trees/leaves/rubber.json", replacements);
    cloneLoot("trees/voluntary/jungle.json", "trees/voluntary/rubber.json", replacements);
}

function writeLeafResources() {
    const leaves = "dtrubber:rubber_leaves_block";
    const harvest = {
        condition: "minecraft:any_of",
        terms: [
            { condition: "minecraft:match_tool", predicate: { items: "minecraft:shears" } },
            {
                condition: "minecraft:match_tool",
                predicate: {
                    predicates: {
                        "minecraft:enchantments": [{ enchantments: "minecraft:silk_touch", levels: { min: 1 } }],
                    },
                },
            },
        ],
    };
    const normalDrops = JSON.parse(readFileSync(out("data", MOD_ID, "loot_table", "trees/leaves/rubber.json"), "utf8"));
    // A placed vanilla leaf has no DT species context; use the rubber seed directly.
    for (const pool of normalDrops.pools) {
        pool.conditions = [{ condition: "minecraft:inverted", term: harvest }];
        for (const entry of pool.entries) {
            if (entry.type === "dynamictrees:seed_item") {
                entry.type = "minecraft:item";
                entry.name = "dtrubber:rubber_seed";
                entry.conditions = entry.conditions.filter((c) => c.condition !== "dynamictrees:seasonal_seed_drop_chance");
            }
        }
    }
    const harvestPool = {
        rolls: 1,
        entries: [{ type: "minecraft:item", name: leaves }],
        conditions: [harvest],
    };
    normalDrops.pools.unshift(harvestPool);
    delete normalDrops.random_sequence;
    writeJson(out("data", MOD_ID, "loot_table", "blocks/rubber_leaves_block.json"), normalDrops);
    const dynamicDrops = JSON.parse(readFileSync(out("data", MOD_ID, "loot_table", "trees/leaves/rubber.json"), "utf8"));
    for (const pool of dynamicDrops.pools) {
        pool.conditions = [{ condition: "minecraft:inverted", term: harvest }];
    }
    dynamicDrops.pools.unshift(harvestPool);
    delete dynamicDrops.random_sequence;
    writeJson(out("data", MOD_ID, "loot_table", "blocks/rubber_leaves.json"), dynamicDrops);
    writeJson(out("assets", MOD_ID, "blockstates/rubber_leaves_block.json"), {
        variants: { "": { model: "dtrubber:block/rubber_leaves" } },
    });
    writeJson(out("assets", MOD_ID, "models/item/rubber_leaves_block.json"), {
        parent: "dtrubber:block/rubber_leaves",
    });
    writeTag("minecraft", "block", "leaves", [leaves]);
    writeTag("minecraft", "item", "leaves", [leaves]);
}

function resolveJavaCli() {
    if (process.env.JAVA) {
        return process.env.JAVA;
    }
    if (process.env.JAVA_HOME) {
        return join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java");
    }
    return "java";
}

function generateTextures() {
    const script = join(__dirname, "scripts", "GenerateTextures.java");
    if (!existsSync(script)) {
        return;
    }
    const result = spawnSync(resolveJavaCli(), [script, __dirname], {
        cwd: __dirname,
        encoding: "utf8",
    });
    if (result.status !== 0) {
        throw new Error(result.stderr || result.stdout || "GenerateTextures.java failed");
    }
}

function writeTextures() {
    generateTextures();
    const texturesRoot = out("assets", MOD_ID, "textures");
    cpSync(join(bundledAssets, "textures"), texturesRoot, { recursive: true });

    const cleanupRoots = [
        join(texturesRoot, "block"),
        join(texturesRoot, "item"),
        join(texturesRoot, "entity"),
    ];
    for (const root of cleanupRoots) {
        cleanupSrcTextures(root);
    }
}

function cleanupSrcTextures(dir) {
    if (!existsSync(dir)) {
        return;
    }
    for (const name of readdirSync(dir)) {
        const path = join(dir, name);
        if (statSync(path).isDirectory()) {
            cleanupSrcTextures(path);
            continue;
        }
        if (name.startsWith("_src_")) {
            rmSync(path, { force: true });
        }
    }
}

function findMinecraftResourcesJar() {
    const artifacts = join(__dirname, "build", "moddev", "artifacts");
    if (!existsSync(artifacts)) {
        return null;
    }
    const jars = readdirSync(artifacts)
        .filter((name) => name.includes("client-extra") && name.endsWith(".jar"))
        .map((name) => join(artifacts, name))
        .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
    return jars[0] || null;
}

function extractJarEntries(jar, entries) {
    const scratch = join(repoRoot, ".tmp", "agent-rubber-leaves");
    mkdirSync(scratch, { recursive: true });
    const temp = mkdtempSync(join(scratch, "vanilla-assets-"));
    const result = spawnSync("jar", ["xf", jar, ...entries], { cwd: temp, encoding: "utf8" });
    if (result.status !== 0) {
        rmSync(temp, { recursive: true, force: true });
        throw new Error(`Failed extracting vanilla assets: ${result.stderr || result.stdout}`);
    }
    return temp;
}

function remapBirchWoodText(text) {
    return text
        .replaceAll("minecraft:block/birch_", `${MOD_ID}:block/rubber_`)
        .replaceAll("minecraft:item/birch_", `${MOD_ID}:item/rubber_`)
        .replaceAll("minecraft:block/birch", `${MOD_ID}:block/rubber`)
        .replaceAll("\"birch_", `"rubber_`);
}

function logBlockstateVariants(modelBase) {
    return {
        "axis=x": {
            model: `${MOD_ID}:block/${modelBase}_horizontal`,
            x: 90,
            y: 90,
        },
        "axis=y": {
            model: `${MOD_ID}:block/${modelBase}`,
        },
        "axis=z": {
            model: `${MOD_ID}:block/${modelBase}_horizontal`,
            x: 90,
        },
    };
}

function writeLogModels(assetsRoot, modelBase, sideTexture) {
    const modelDir = join(assetsRoot, "models", "block");
    const column = {
        parent: "minecraft:block/cube_column",
        textures: {
            end: `${MOD_ID}:block/rubber_log_top`,
            side: `${MOD_ID}:block/${sideTexture}`,
        },
    };

    writeJson(join(modelDir, `${modelBase}.json`), column);
    writeJson(join(modelDir, `${modelBase}_horizontal.json`), column);
}

function woodBlockstateVariants(modelBase) {
    return {
        "axis=x": {
            model: `${MOD_ID}:block/${modelBase}`,
            x: 90,
            y: 90,
        },
        "axis=y": {
            model: `${MOD_ID}:block/${modelBase}`,
        },
        "axis=z": {
            model: `${MOD_ID}:block/${modelBase}`,
            x: 90,
        },
    };
}

function writeWoodBlockModel(assetsRoot, modelBase, sideTexture) {
    writeJson(join(assetsRoot, "models", "block", `${modelBase}.json`), {
        parent: "minecraft:block/cube_column",
        textures: {
            end: `${MOD_ID}:block/${sideTexture}`,
            side: `${MOD_ID}:block/${sideTexture}`,
        },
    });
}

function writeWoodAssets() {
    const assetsRoot = out("assets", MOD_ID);
    const jar = findMinecraftResourcesJar();
    if (!jar) {
        throw new Error("Missing Minecraft client-extra jar under build/moddev/artifacts; run a rubber Gradle build once first.");
    }

    const birchAssetEntries = [
        "assets/minecraft/blockstates/birch_wood.json",
        "assets/minecraft/blockstates/birch_stairs.json",
        "assets/minecraft/blockstates/birch_fence.json",
        "assets/minecraft/blockstates/birch_fence_gate.json",
        "assets/minecraft/blockstates/birch_door.json",
        "assets/minecraft/blockstates/birch_trapdoor.json",
        "assets/minecraft/blockstates/birch_button.json",
        "assets/minecraft/blockstates/birch_pressure_plate.json",
        "assets/minecraft/blockstates/birch_sign.json",
        "assets/minecraft/blockstates/birch_wall_sign.json",
        "assets/minecraft/blockstates/birch_hanging_sign.json",
        "assets/minecraft/blockstates/birch_wall_hanging_sign.json",
        "assets/minecraft/models/block/birch_wood.json",
        "assets/minecraft/models/block/birch_stairs.json",
        "assets/minecraft/models/block/birch_stairs_inner.json",
        "assets/minecraft/models/block/birch_stairs_outer.json",
        "assets/minecraft/models/block/birch_fence_post.json",
        "assets/minecraft/models/block/birch_fence_side.json",
        "assets/minecraft/models/block/birch_fence_inventory.json",
        "assets/minecraft/models/block/birch_fence_gate.json",
        "assets/minecraft/models/block/birch_fence_gate_open.json",
        "assets/minecraft/models/block/birch_fence_gate_wall.json",
        "assets/minecraft/models/block/birch_fence_gate_wall_open.json",
        "assets/minecraft/models/block/birch_door_bottom_left.json",
        "assets/minecraft/models/block/birch_door_bottom_left_open.json",
        "assets/minecraft/models/block/birch_door_bottom_right.json",
        "assets/minecraft/models/block/birch_door_bottom_right_open.json",
        "assets/minecraft/models/block/birch_door_top_left.json",
        "assets/minecraft/models/block/birch_door_top_left_open.json",
        "assets/minecraft/models/block/birch_door_top_right.json",
        "assets/minecraft/models/block/birch_door_top_right_open.json",
        "assets/minecraft/models/block/birch_trapdoor_bottom.json",
        "assets/minecraft/models/block/birch_trapdoor_top.json",
        "assets/minecraft/models/block/birch_trapdoor_open.json",
        "assets/minecraft/models/block/birch_button.json",
        "assets/minecraft/models/block/birch_button_pressed.json",
        "assets/minecraft/models/block/birch_button_inventory.json",
        "assets/minecraft/models/block/birch_pressure_plate.json",
        "assets/minecraft/models/block/birch_pressure_plate_down.json",
        "assets/minecraft/models/block/birch_sign.json",
        "assets/minecraft/models/block/birch_hanging_sign.json",
        "assets/minecraft/models/item/birch_wood.json",
        "assets/minecraft/models/item/birch_stairs.json",
        "assets/minecraft/models/item/birch_fence.json",
        "assets/minecraft/models/item/birch_fence_gate.json",
        "assets/minecraft/models/item/birch_door.json",
        "assets/minecraft/models/item/birch_trapdoor.json",
        "assets/minecraft/models/item/birch_button.json",
        "assets/minecraft/models/item/birch_pressure_plate.json",
        "assets/minecraft/models/item/birch_sign.json",
        "assets/minecraft/models/item/birch_hanging_sign.json",
        "assets/minecraft/models/item/birch_boat.json",
        "assets/minecraft/models/item/birch_chest_boat.json",
    ];

    const temp = extractJarEntries(jar, birchAssetEntries);
    try {
        for (const entry of birchAssetEntries) {
            const src = join(temp, ...entry.split("/"));
            const relative = entry
                .replace(/^assets\/minecraft\//, "")
                .replaceAll("birch_", "rubber_")
                .replaceAll("/birch", "/rubber");
            const dest = join(assetsRoot, relative);
            writeText(dest, remapBirchWoodText(readFileSync(src, "utf8")));
        }
    } finally {
        rmSync(temp, { recursive: true, force: true });
    }

    writeJson(join(assetsRoot, "blockstates", "rubber_log.json"), {
        variants: logBlockstateVariants("rubber_log"),
    });
    writeJson(join(assetsRoot, "blockstates", "stripped_rubber_log.json"), {
        variants: logBlockstateVariants("stripped_rubber_log"),
    });
    writeJson(join(assetsRoot, "blockstates", "stripped_rubber_wood.json"), {
        variants: woodBlockstateVariants("stripped_rubber_wood"),
    });
    writeJson(join(assetsRoot, "blockstates", "rubber_planks.json"), {
        variants: { "": { model: `${MOD_ID}:block/rubber_planks` } },
    });
    writeJson(join(assetsRoot, "blockstates", "rubber_slab.json"), {
        variants: {
            "type=bottom": { model: `${MOD_ID}:block/rubber_slab` },
            "type=double": { model: `${MOD_ID}:block/rubber_planks` },
            "type=top": { model: `${MOD_ID}:block/rubber_slab_top` },
        },
    });

    writeLogModels(assetsRoot, "rubber_log", "rubber_log");
    writeLogModels(assetsRoot, "stripped_rubber_log", "rubber_stripped_log");
    writeWoodBlockModel(assetsRoot, "stripped_rubber_wood", "rubber_stripped_log");
    writeWoodBlockModel(assetsRoot, "rubber_wood", "rubber_log");

    writeJson(join(assetsRoot, "models", "block", "rubber_planks.json"), {
        parent: "minecraft:block/cube_all",
        textures: { all: `${MOD_ID}:block/rubber_planks` },
    });

    const slabTextures = {
        bottom: `${MOD_ID}:block/rubber_planks`,
        side: `${MOD_ID}:block/rubber_planks`,
        top: `${MOD_ID}:block/rubber_planks`,
    };
    writeJson(join(assetsRoot, "models", "block", "rubber_slab.json"), {
        parent: "minecraft:block/slab",
        textures: slabTextures,
    });
    writeJson(join(assetsRoot, "models", "block", "rubber_slab_top.json"), {
        parent: "minecraft:block/slab_top",
        textures: slabTextures,
    });

    for (const itemName of [
        "rubber_log",
        "stripped_rubber_log",
        "rubber_wood",
        "stripped_rubber_wood",
        "rubber_planks",
        "rubber_slab",
    ]) {
        writeJson(join(assetsRoot, "models", "item", `${itemName}.json`), {
            parent: `${MOD_ID}:block/${itemName}`,
        });
    }
}

function writeSimpleBlockLoot(blockName) {
    writeJson(out("data", MOD_ID, "loot_table", "blocks", `${blockName}.json`), {
        type: "minecraft:block",
        pools: [
            {
                bonus_rolls: 0.0,
                conditions: [{ condition: "minecraft:survives_explosion" }],
                entries: [{ type: "minecraft:item", name: `${MOD_ID}:${blockName}` }],
                rolls: 1.0,
            },
        ],
        random_sequence: `${MOD_ID}:blocks/${blockName}`,
    });
}

function writeWoodLootTables() {
    const jar = findMinecraftResourcesJar();
    const specialLoot = [
        ["birch_slab", "rubber_slab"],
        ["birch_door", "rubber_door"],
        ["birch_sign", "rubber_sign"],
        ["birch_hanging_sign", "rubber_hanging_sign"],
    ];

    for (const blockName of [
        "rubber_log",
        "stripped_rubber_log",
        "rubber_wood",
        "stripped_rubber_wood",
        "rubber_planks",
        "rubber_stairs",
        "rubber_fence",
        "rubber_fence_gate",
        "rubber_trapdoor",
        "rubber_button",
        "rubber_pressure_plate",
    ]) {
        writeSimpleBlockLoot(blockName);
    }

    if (!jar) {
        throw new Error("Missing Minecraft client-extra jar for wood loot tables.");
    }

    const entries = specialLoot.map(([from]) => `data/minecraft/loot_table/blocks/${from}.json`);
    const temp = extractJarEntries(jar, entries);
    try {
        for (const [from, to] of specialLoot) {
            const src = join(temp, "data", "minecraft", "loot_table", "blocks", `${from}.json`);
            const text = readFileSync(src, "utf8")
                .replaceAll(`minecraft:${from}`, `${MOD_ID}:${to}`)
                .replaceAll(`"minecraft:blocks/${from}"`, `"${MOD_ID}:blocks/${to}"`)
                .replaceAll("birch_", "rubber_");
            writeText(out("data", MOD_ID, "loot_table", "blocks", `${to}.json`), text);
        }
    } finally {
        rmSync(temp, { recursive: true, force: true });
    }
}

function writeWoodTags() {
    const rubberWood = `${MOD_ID}:rubber_wood`;
    const strippedWood = `${MOD_ID}:stripped_rubber_wood`;
    const logItems = [
        `${MOD_ID}:rubber_log`,
        `${MOD_ID}:stripped_rubber_log`,
        rubberWood,
        strippedWood,
    ];
    const planks = [`${MOD_ID}:rubber_planks`];
    const slab = [`${MOD_ID}:rubber_slab`];
    const stairs = [`${MOD_ID}:rubber_stairs`];
    const fence = [`${MOD_ID}:rubber_fence`];
    const fenceGate = [`${MOD_ID}:rubber_fence_gate`];
    const door = [`${MOD_ID}:rubber_door`];
    const trapdoor = [`${MOD_ID}:rubber_trapdoor`];
    const button = [`${MOD_ID}:rubber_button`];
    const pressurePlate = [`${MOD_ID}:rubber_pressure_plate`];
    const sign = [`${MOD_ID}:rubber_sign`];
    const hangingSign = [`${MOD_ID}:rubber_hanging_sign`];
    const boat = [`${MOD_ID}:rubber_boat`];
    const chestBoat = [`${MOD_ID}:rubber_chest_boat`];
    const axeMineable = logItems.concat(
        planks,
        slab,
        stairs,
        fence,
        fenceGate,
        door,
        trapdoor,
        button,
        pressurePlate,
        sign,
        hangingSign,
        [`${MOD_ID}:rubber_wall_sign`, `${MOD_ID}:rubber_wall_hanging_sign`],
    );

    writeTag(MOD_ID, "item", "rubber_logs", logItems);
    writeTag(MOD_ID, "block", "rubber_logs", logItems);
    writeTag("minecraft", "block", "logs", logItems);
    writeTag("minecraft", "block", "logs_that_burn", logItems);
    writeTag("minecraft", "block", "mineable/axe", axeMineable);
    writeTag("minecraft", "block", "planks", planks);
    writeTag("minecraft", "block", "slabs", slab);
    writeTag("minecraft", "block", "wooden_slabs", slab);
    writeTag("minecraft", "block", "stairs", stairs);
    writeTag("minecraft", "block", "wooden_stairs", stairs);
    writeTag("minecraft", "block", "fences", fence);
    writeTag("minecraft", "block", "wooden_fences", fence);
    writeTag("minecraft", "block", "fence_gates", fenceGate);
    writeTag("minecraft", "block", "doors", door);
    writeTag("minecraft", "block", "wooden_doors", door);
    writeTag("minecraft", "block", "trapdoors", trapdoor);
    writeTag("minecraft", "block", "wooden_trapdoors", trapdoor);
    writeTag("minecraft", "block", "buttons", button);
    writeTag("minecraft", "block", "wooden_buttons", button);
    writeTag("minecraft", "block", "pressure_plates", pressurePlate);
    writeTag("minecraft", "block", "wooden_pressure_plates", pressurePlate);
    writeTag("minecraft", "block", "standing_signs", sign);
    writeTag("minecraft", "block", "wall_signs", [`${MOD_ID}:rubber_wall_sign`]);
    writeTag("minecraft", "block", "ceiling_hanging_signs", hangingSign);
    writeTag("minecraft", "block", "wall_hanging_signs", [`${MOD_ID}:rubber_wall_hanging_sign`]);
    writeTag("minecraft", "item", "logs", logItems);
    writeTag("minecraft", "item", "logs_that_burn", logItems);
    writeTag("minecraft", "item", "planks", planks);
    writeTag("minecraft", "item", "slabs", slab);
    writeTag("minecraft", "item", "wooden_slabs", slab);
    writeTag("minecraft", "item", "stairs", stairs);
    writeTag("minecraft", "item", "wooden_stairs", stairs);
    writeTag("minecraft", "item", "fences", fence);
    writeTag("minecraft", "item", "wooden_fences", fence);
    writeTag("minecraft", "item", "fence_gates", fenceGate);
    writeTag("minecraft", "item", "doors", door);
    writeTag("minecraft", "item", "wooden_doors", door);
    writeTag("minecraft", "item", "trapdoors", trapdoor);
    writeTag("minecraft", "item", "wooden_trapdoors", trapdoor);
    writeTag("minecraft", "item", "buttons", button);
    writeTag("minecraft", "item", "wooden_buttons", button);
    writeTag("minecraft", "item", "wooden_pressure_plates", pressurePlate);
    writeTag("minecraft", "item", "signs", sign);
    writeTag("minecraft", "item", "hanging_signs", hangingSign);
    writeTag("minecraft", "item", "boats", boat);
    writeTag("minecraft", "item", "chest_boats", chestBoat);
    writeTag("c", "block", "logs", [`${MOD_ID}:rubber_log`]);
    writeTag("c", "item", "logs", [`${MOD_ID}:rubber_log`]);
    writeTag("c", "block", "stripped_logs", [`${MOD_ID}:stripped_rubber_log`]);
    writeTag("c", "item", "stripped_logs", [`${MOD_ID}:stripped_rubber_log`]);
    writeTag("c", "block", "stripped_woods", [strippedWood]);
    writeTag("c", "item", "stripped_woods", [strippedWood]);
    writeTag("c", "block", "planks", planks);
    writeTag("c", "item", "planks", planks);
}

function writeWoodRecipes() {
    const jar = findMinecraftResourcesJar();
    if (!jar) {
        throw new Error("Missing Minecraft client-extra jar for wood recipes.");
    }

    const recipeMap = [
        ["birch_wood", "rubber_wood"],
        ["stripped_birch_wood", "stripped_rubber_wood"],
        ["birch_stairs", "rubber_stairs"],
        ["birch_slab", "rubber_slab"],
        ["birch_fence", "rubber_fence"],
        ["birch_fence_gate", "rubber_fence_gate"],
        ["birch_door", "rubber_door"],
        ["birch_trapdoor", "rubber_trapdoor"],
        ["birch_button", "rubber_button"],
        ["birch_pressure_plate", "rubber_pressure_plate"],
        ["birch_sign", "rubber_sign"],
        ["birch_hanging_sign", "rubber_hanging_sign"],
        ["birch_boat", "rubber_boat"],
        ["birch_chest_boat", "rubber_chest_boat"],
    ];

    const entries = recipeMap.map(([from]) => `data/minecraft/recipe/${from}.json`);
    const temp = extractJarEntries(jar, entries);
    try {
        for (const [from, to] of recipeMap) {
            const src = join(temp, "data", "minecraft", "recipe", `${from}.json`);
            let text = readFileSync(src, "utf8")
                .replaceAll("minecraft:birch_planks", `${MOD_ID}:rubber_planks`)
                .replaceAll("minecraft:birch_log", `${MOD_ID}:rubber_log`)
                .replaceAll("minecraft:stripped_birch_log", `${MOD_ID}:stripped_rubber_log`)
                .replaceAll("minecraft:birch_boat", `${MOD_ID}:rubber_boat`)
                .replaceAll(`minecraft:${from}`, `${MOD_ID}:${to}`)
                .replaceAll('"birch_logs"', `"${MOD_ID}:rubber_logs"`)
                .replaceAll("minecraft:birch_logs", `${MOD_ID}:rubber_logs`);
            writeText(out("data", MOD_ID, "recipe", `${to}.json`), text);
        }
    } finally {
        rmSync(temp, { recursive: true, force: true });
    }

    writeJson(out("data", MOD_ID, "recipe", "rubber_planks.json"), {
        type: "minecraft:crafting_shapeless",
        category: "building",
        group: "planks",
        ingredients: [{ tag: `${MOD_ID}:rubber_logs` }],
        result: {
            count: 4,
            id: `${MOD_ID}:rubber_planks`,
        },
    });
}

function writeAssets() {
    const assetsRoot = out("assets", MOD_ID);

    writeJson(join(assetsRoot, "blockstates", `${SPECIES}_branch.json`), {
        variants: {
            "": {
                model: `${MOD_ID}:block/${SPECIES}_branch`,
            },
        },
    });

    writeJson(join(assetsRoot, "blockstates", `stripped_${SPECIES}_branch.json`), {
        variants: {
            "": {
                model: `${MOD_ID}:block/stripped_${SPECIES}_branch`,
            },
        },
    });

    writeJson(join(assetsRoot, "blockstates", `${SPECIES}_leaves.json`), {
        variants: {
            "": {
                model: `${MOD_ID}:block/${SPECIES}_leaves`,
            },
        },
    });

    writeJson(join(assetsRoot, "blockstates", `${SPECIES}_sapling.json`), {
        variants: {
            "": {
                model: `${MOD_ID}:block/saplings/${SPECIES}`,
            },
        },
    });

    writeJson(join(assetsRoot, "models", "block", `${SPECIES}_leaves.json`), {
        parent: "minecraft:block/cube_all",
        render_type: "minecraft:cutout_mipped",
        textures: {
            all: `${MOD_ID}:block/rubber_leaves`,
        },
    });

    writeJson(join(assetsRoot, "models", "block", `${SPECIES}_branch.json`), {
        loader: "dynamictrees:branch",
        textures: {
            bark: `${MOD_ID}:block/rubber_log`,
            rings: `${MOD_ID}:block/rubber_log_top`,
            rings_thick: `${MOD_ID}:block/rubber_log_top_thick`,
        },
    });

    writeJson(join(assetsRoot, "models", "block", `stripped_${SPECIES}_branch.json`), {
        loader: "dynamictrees:branch",
        textures: {
            bark: `${MOD_ID}:block/rubber_stripped_log`,
            rings: `${MOD_ID}:block/rubber_log_top`,
            rings_thick: `${MOD_ID}:block/rubber_log_top_thick`,
        },
    });

    writeJson(join(assetsRoot, "models", "block", "saplings", `${SPECIES}.json`), {
        parent: "dynamictrees:block/smartmodel/sapling",
        render_type: "minecraft:cutout_mipped",
        textures: {
            leaves: `${MOD_ID}:block/rubber_leaves`,
            log: `${MOD_ID}:block/rubber_log`,
        },
    });

    writeJson(join(assetsRoot, "models", "item", `${SPECIES}_branch.json`), {
        parent: `${MOD_ID}:block/${SPECIES}_branch`,
    });

    writeJson(join(assetsRoot, "models", "item", `${SPECIES}_seed.json`), {
        parent: "dynamictrees:item/standard_seed",
        textures: {
            layer0: `${MOD_ID}:item/rubber_seed`,
        },
    });

    writeJson(join(assetsRoot, "models", "item", `${SPECIES}_sapling.json`), {
        parent: `${MOD_ID}:block/saplings/${SPECIES}`,
    });
}

function modLoadedCondition(modid) {
    return { type: "neoforge:mod_loaded", modid: modid };
}

function modNotLoadedCondition(modid) {
    return { type: "neoforge:not", value: modLoadedCondition(modid) };
}

function writeRubberItemAssets() {
    const itemModels = out("assets", MOD_ID, "models", "item");

    for (const itemName of ["raw_latex", "coagulated_latex", "rubber_ball", "rubber_sheet"]) {
        writeJson(join(itemModels, `${itemName}.json`), {
            parent: "minecraft:item/generated",
            textures: {
                layer0: `${MOD_ID}:item/${itemName}`,
            },
        });
    }
}

function writeRubberTagsAndRecipes() {
    writeJson(out("data", "c", "tags", "item", "rubber.json"), {
        values: [
            {
                id: `${MOD_ID}:rubber_sheet`,
                required: false,
            },
            {
                id: "tfmg:rubber_sheet",
                required: false,
            },
        ],
    });

    const recipeRoot = out("data", MOD_ID, "recipe");
    const createLoaded = [modLoadedCondition("create")];
    const tfmgLoaded = [modLoadedCondition("tfmg")];
    const noTfmg = [modNotLoadedCondition("tfmg")];

    writeJson(join(recipeRoot, "coagulated_latex_from_charcoal.json"), {
        type: "create:mixing",
        "neoforge:conditions": createLoaded,
        ingredients: [
            { item: `${MOD_ID}:raw_latex` },
            { item: `${MOD_ID}:raw_latex` },
            { item: `${MOD_ID}:raw_latex` },
            { item: "minecraft:charcoal" },
            { item: "minecraft:slime_ball" },
        ],
        results: [{ id: `${MOD_ID}:coagulated_latex` }],
    });

    writeJson(join(recipeRoot, "coagulated_latex_from_sulfur.json"), {
        type: "create:mixing",
        "neoforge:conditions": createLoaded.concat(tfmgLoaded),
        heat_requirement: "heated",
        ingredients: [{ item: `${MOD_ID}:raw_latex` }, { item: "tfmg:sulfur_dust" }],
        results: [{ id: `${MOD_ID}:coagulated_latex` }],
    });

    writeJson(join(recipeRoot, "rubber_ball_smelting.json"), {
        type: "minecraft:smelting",
        category: "misc",
        cookingtime: 200,
        experience: 0.35,
        ingredient: { item: `${MOD_ID}:coagulated_latex` },
        result: { count: 1, id: `${MOD_ID}:rubber_ball` },
    });

    writeJson(join(recipeRoot, "rubber_ball_blasting.json"), {
        type: "minecraft:blasting",
        category: "misc",
        cookingtime: 100,
        experience: 0.35,
        ingredient: { item: `${MOD_ID}:coagulated_latex` },
        result: { count: 1, id: `${MOD_ID}:rubber_ball` },
    });

    writeJson(join(recipeRoot, "rubber_sheet_pressing.json"), {
        type: "create:pressing",
        "neoforge:conditions": createLoaded.concat(noTfmg),
        ingredients: [{ item: `${MOD_ID}:rubber_ball` }],
        results: [{ id: `${MOD_ID}:rubber_sheet` }],
    });

    writeJson(join(recipeRoot, "rubber_sheet_pressing_tfmg.json"), {
        type: "create:pressing",
        "neoforge:conditions": createLoaded.concat(tfmgLoaded),
        ingredients: [{ item: `${MOD_ID}:rubber_ball` }],
        results: [{ id: "tfmg:rubber_sheet" }],
    });

    writeCreateRubberOverrides();
    writeModRubberOverrides();
}

function writeModRubberOverrides() {
    const rubber = { tag: "c:rubber" };
    const rubberNine = { tag: "c:rubber", count: 9 };

    const electroLoaded = { "neoforge:conditions": [modLoadedCondition("electroenergetics")] };
    const offroadLoaded = { "neoforge:conditions": [modLoadedCondition("offroad")] };
    const bigtiresLoaded = { "neoforge:conditions": [modLoadedCondition("bigtires")] };

    const electroRoot = out("data", "electroenergetics", "recipe", "crafting");
    writeRecipes(electroRoot, electroLoaded, [
        [
            "momentary_switch.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    B: { tag: "minecraft:buttons" },
                    C: { item: "electroenergetics:connector" },
                    K: rubber,
                },
                pattern: [" K ", " B ", "CAC"],
                result: { count: 1, id: "electroenergetics:momentary_switch" },
            },
        ],
        [
            "rail_contact_shoe.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    C: { item: "electroenergetics:connector" },
                    K: rubber,
                    S: { item: "create:shaft" },
                },
                pattern: ["CKA", "ASA"],
                result: { count: 1, id: "electroenergetics:rail_contact_shoe" },
            },
        ],
        [
            "insulated_wire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    k: rubber,
                    w: { tag: "c:wires/copper" },
                },
                pattern: ["www", "wkw", "www"],
                result: { count: 8, id: "electroenergetics:insulated_wire" },
            },
        ],
        [
            "pantograph.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    C: { item: "electroenergetics:connector" },
                    K: rubber,
                    S: { item: "create:shaft" },
                },
                pattern: [" KK", "CAS", "CC "],
                result: { count: 1, id: "electroenergetics:pantograph" },
            },
        ],
        [
            "heavily_insulated_wire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    k: rubber,
                    p: { item: "minecraft:paper" },
                    w: { item: "electroenergetics:insulated_wire" },
                },
                pattern: ["pwp", "wkw", "pwp"],
                result: { count: 4, id: "electroenergetics:heavily_insulated_wire" },
            },
        ],
        [
            "cut_off_switch.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    C: { item: "electroenergetics:connector" },
                    S: { tag: "c:plates/copper" },
                    k: rubber,
                },
                pattern: [" k ", " S ", "CAC"],
                result: { count: 1, id: "electroenergetics:cut_off_switch" },
            },
        ],
    ]);

    writeRecipes(out("data", "offroad", "recipe"), offroadLoaded, [
        [
            "monstrous_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    K: rubberNine,
                    S: { item: "create:shaft" },
                },
                pattern: [" K ", "KSK", " K "],
                result: { count: 1, id: "offroad:monstrous_tire" },
            },
        ],
        [
            "small_tire.json",
            {
                type: "minecraft:crafting_shapeless",
                category: "misc",
                ingredients: [{ item: "create:shaft" }, rubber],
                result: { count: 1, id: "offroad:small_tire" },
            },
        ],
        [
            "tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    K: rubber,
                    S: { item: "create:shaft" },
                },
                pattern: [" K ", "KSK", " K "],
                result: { count: 1, id: "offroad:tire" },
            },
        ],
    ]);

    writeRecipes(out("data", "bigtires", "recipe"), bigtiresLoaded, [
        [
            "big_tractor_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                pattern: ["CDC", "DSD", "CDC"],
                key: {
                    C: { item: "minecraft:sugar_cane" },
                    D: rubberNine,
                    S: { item: "create:shaft" },
                },
                result: { id: "bigtires:big_tractor_tire", count: 2 },
            },
        ],
        [
            "drift_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    I: { item: "create:iron_sheet" },
                    D: rubber,
                    S: { item: "create:shaft" },
                },
                pattern: [" DDD ", "DIIID", "DISID", "DIIID", " DDD "],
                result: { count: 4, id: "bigtires:drift_tire" },
            },
        ],
        [
            "huge_rowing_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    E: { tag: "aeronautics:envelope" },
                    D: rubber,
                    K: rubberNine,
                    S: { item: "create:shaft" },
                },
                pattern: [" DDD ", "DEKED", "DKSKD", "DEKED", " DDD "],
                result: { count: 2, id: "bigtires:huge_rowing_tire" },
            },
        ],
        [
            "huge_rowing_wide_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    E: { tag: "aeronautics:envelope" },
                    D: rubber,
                    K: rubberNine,
                    S: { item: "create:shaft" },
                },
                pattern: ["  DDD  ", " DEEED ", "DEEKEED", "DEKSKED", "DEEKEED", " DEEED ", "  DDD  "],
                result: { count: 2, id: "bigtires:huge_rowing_wide_tire" },
            },
        ],
        [
            "huge_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    I: { item: "create:iron_sheet" },
                    D: rubber,
                    K: rubberNine,
                    S: { item: "create:shaft" },
                },
                pattern: [" DDD ", "DIKID", "DKSKD", "DIKID", " DDD "],
                result: { count: 2, id: "bigtires:huge_tire" },
            },
        ],
        [
            "huge_wide_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    I: { item: "create:iron_sheet" },
                    D: rubber,
                    K: rubberNine,
                    S: { item: "create:shaft" },
                },
                pattern: ["  DDD  ", " DIIID ", "DIKKKID", "DIKSKID", "DIKKKID", " DIIID ", "  DDD  "],
                result: { count: 2, id: "bigtires:huge_wide_tire" },
            },
        ],
        [
            "monster_jam_tire.json",
            {
                type: "create:mechanical_crafting",
                accept_mirrored: true,
                category: "misc",
                key: {
                    I: { item: "create:industrial_iron_block" },
                    K: rubberNine,
                    S: { item: "create:shaft" },
                    D: rubber,
                    G: { item: "create:metal_girder" },
                },
                pattern: [" DKD ", "DGIGD", "KISIK", "DGIGD", " DKD "],
                result: { count: 2, id: "bigtires:monster_jam_tire" },
            },
        ],
        [
            "narrow_truck_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                pattern: ["DDD", "BSB", "DDD"],
                key: {
                    D: rubber,
                    B: rubberNine,
                    S: { item: "create:shaft" },
                },
                result: { id: "bigtires:narrow_truck_tire", count: 2 },
            },
        ],
        [
            "small_truck_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                pattern: ["B", "S", "B"],
                key: {
                    B: rubberNine,
                    S: { item: "create:shaft" },
                },
                result: { id: "bigtires:small_truck_tire", count: 2 },
            },
        ],
        [
            "tractor_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                pattern: ["CDC", "DSD", "CDC"],
                key: {
                    C: { item: "minecraft:sugar_cane" },
                    D: rubber,
                    S: { item: "create:shaft" },
                },
                result: { id: "bigtires:tractor_tire", count: 2 },
            },
        ],
        [
            "truck_tire.json",
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                pattern: ["DBD", "BSB", "DBD"],
                key: {
                    D: rubber,
                    B: rubberNine,
                    S: { item: "create:shaft" },
                },
                result: { id: "bigtires:truck_tire", count: 2 },
            },
        ],
        [
            "vintage_tire.json",
            {
                type: "minecraft:crafting_shaped",
                accept_mirrored: true,
                category: "misc",
                key: {
                    I: { item: "create:iron_sheet" },
                    D: rubber,
                    S: { item: "create:shaft" },
                },
                pattern: ["DID", "ISI", "DID"],
                result: { count: 2, id: "bigtires:vintage_tire" },
            },
        ],
    ]);

    writePetrolspartsOverrides();
}

function writePetrolspartsOverrides() {
    const rubber = { tag: "c:rubber" };

    writeJson(out("data", "petrolsparts", "recipe", "crafting", "pneumatic_tube.json"), {
        "neoforge:conditions": [
            modLoadedCondition("petrolsparts"),
            modLoadedCondition("create"),
            {
                type: "petrolpark:config_bool",
                key: "petrolsparts:common.recipes.pneumaticTube",
            },
        ],
        type: "minecraft:crafting_shaped",
        pattern: ["KMK", "KPK", "ICI"],
        key: {
            K: rubber,
            M: { item: "create:precision_mechanism" },
            P: { item: "create:propeller" },
            C: { item: "create:cogwheel" },
            I: { tag: "c:plates/iron" },
        },
        result: {
            id: "petrolsparts:pneumatic_tube",
            count: 8,
        },
    });
}

function writeCreateRubberOverrides() {
    writeJson(out("assets", "create", "lang", "en_us.json"), {
        "advancement.create.belt": "Rubberᵗᵐ Drive",
    });

    const createLoaded = { "neoforge:conditions": [modLoadedCondition("create")] };
    const rubber = { tag: "c:rubber" };
    const rubberNine = { tag: "c:rubber", count: 9 };

    writeRecipes(out("data", "create", "recipe", "crafting"), createLoaded, [
        [
            join("kinetics", "belt_connector.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: { D: rubber },
                pattern: ["DDD", "DDD"],
                result: { count: 1, id: "create:belt_connector" },
            },
        ],
        [
            join("kinetics", "spout.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    P: rubber,
                    T: { item: "create:copper_casing" },
                },
                pattern: ["T", "P"],
                result: { count: 1, id: "create:spout" },
            },
        ],
        [
            join("kinetics", "elevator_pulley.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    B: { item: "create:brass_casing" },
                    C: rubberNine,
                    I: { tag: "c:plates/iron" },
                },
                pattern: ["B", "C", "I"],
                result: { count: 1, id: "create:elevator_pulley" },
            },
        ],
        [
            join("kinetics", "hose_pulley.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    B: { item: "create:copper_casing" },
                    C: rubberNine,
                    I: { tag: "c:plates/copper" },
                },
                pattern: ["B", "C", "I"],
                result: { count: 1, id: "create:hose_pulley" },
            },
        ],
        [
            join("logistics", "andesite_funnel.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    K: rubber,
                },
                pattern: ["A", "K"],
                result: { count: 2, id: "create:andesite_funnel" },
            },
        ],
        [
            join("logistics", "brass_funnel.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { tag: "c:ingots/brass" },
                    E: { item: "create:electron_tube" },
                    K: rubber,
                },
                pattern: ["E", "A", "K"],
                result: { count: 2, id: "create:brass_funnel" },
            },
        ],
        [
            join("logistics", "andesite_tunnel.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { item: "create:andesite_alloy" },
                    K: rubber,
                },
                pattern: ["AA", "KK"],
                result: { count: 2, id: "create:andesite_tunnel" },
            },
        ],
        [
            join("logistics", "brass_tunnel.json"),
            {
                type: "minecraft:crafting_shaped",
                category: "misc",
                key: {
                    A: { tag: "c:ingots/brass" },
                    E: { item: "create:electron_tube" },
                    K: rubber,
                },
                pattern: ["E ", "AA", "KK"],
                result: { count: 2, id: "create:brass_tunnel" },
            },
        ],
    ]);
}

function writeLang() {
    writeJson(out("assets", MOD_ID, "lang", "en_us.json"), {
        [`block.${MOD_ID}.${SPECIES}_branch`]: "Rubber Branch",
        [`block.${MOD_ID}.stripped_${SPECIES}_branch`]: "Stripped Rubber Branch",
        [`block.${MOD_ID}.rubber_log`]: "Rubber Log",
        [`block.${MOD_ID}.stripped_rubber_log`]: "Stripped Rubber Log",
        [`block.${MOD_ID}.rubber_wood`]: "Rubber Wood",
        [`block.${MOD_ID}.stripped_rubber_wood`]: "Stripped Rubber Wood",
        [`block.${MOD_ID}.rubber_planks`]: "Rubber Planks",
        [`block.${MOD_ID}.rubber_slab`]: "Rubber Slab",
        [`block.${MOD_ID}.rubber_stairs`]: "Rubber Stairs",
        [`block.${MOD_ID}.rubber_fence`]: "Rubber Fence",
        [`block.${MOD_ID}.rubber_fence_gate`]: "Rubber Fence Gate",
        [`block.${MOD_ID}.rubber_door`]: "Rubber Door",
        [`block.${MOD_ID}.rubber_trapdoor`]: "Rubber Trapdoor",
        [`block.${MOD_ID}.rubber_button`]: "Rubber Button",
        [`block.${MOD_ID}.rubber_pressure_plate`]: "Rubber Pressure Plate",
        [`block.${MOD_ID}.rubber_sign`]: "Rubber Sign",
        [`block.${MOD_ID}.rubber_wall_sign`]: "Rubber Wall Sign",
        [`block.${MOD_ID}.rubber_hanging_sign`]: "Rubber Hanging Sign",
        [`block.${MOD_ID}.rubber_wall_hanging_sign`]: "Rubber Wall Hanging Sign",
        [`block.${MOD_ID}.${SPECIES}_leaves`]: "Rubber Leaves",
        [`block.${MOD_ID}.rubber_leaves_block`]: "Rubber Leaves",
        [`block.${MOD_ID}.${SPECIES}_sapling`]: "Rubber Sapling",
        [`item.${MOD_ID}.${SPECIES}_seed`]: "Rubber Seed",
        [`item.${MOD_ID}.rubber_boat`]: "Rubber Boat",
        [`item.${MOD_ID}.rubber_chest_boat`]: "Rubber Boat with Chest",
        [`item.${MOD_ID}.raw_latex`]: "Raw Latex",
        [`item.${MOD_ID}.coagulated_latex`]: "Coagulated Latex",
        [`item.${MOD_ID}.rubber_ball`]: "Rubber Ball",
        [`item.${MOD_ID}.rubber_sheet`]: "Rubber Sheet",
        [`species.${MOD_ID}.${SPECIES}`]: "Rubber Tree",
        [`emi.${MOD_ID}.raw_latex`]: "Strip rubber logs or thick rubber tree trunks with an axe to collect Raw Latex.",
    });
}

function writePackMeta() {
    writeJson(out("pack.mcmeta"), {
        pack: {
            description: "SKCraft Dynamic Trees rubber species",
            pack_format: 34,
        },
    });
}

function writeEnumExtensions() {
    writeJson(out("META-INF", "enumextensions.json"), {
        entries: [
            {
                enum: "net/minecraft/world/entity/vehicle/Boat$Type",
                name: "DTRUBBER_RUBBER",
                constructor:
                    "(Ljava/util/function/Supplier;Ljava/lang/String;Ljava/util/function/Supplier;Ljava/util/function/Supplier;Ljava/util/function/Supplier;Z)V",
                parameters: {
                    class: "com/skcraft/dtrubber/DtRubber",
                    method: "getBoatTypeParameter",
                },
            },
        ],
    });
}

const preservedTreeLoot = existsSync(dtLootBase) ? new Map() : existingTreeLoot();

rmSync(outRoot, { recursive: true, force: true });
writeTreePack();
writeTextures();
writeBranchTags();
writeJson(out("data", "dynamictrees", "tags", "item", "seeds.json"), {
    values: [`${MOD_ID}:${SPECIES}_seed`],
});
writeJson(out("data", "dynamictrees", "tags", "block", "saplings.json"), {
    values: [`${MOD_ID}:${SPECIES}_sapling`],
});
writeJson(out("data", "dynamictrees", "tags", "block", "leaves.json"), {
    values: [`${MOD_ID}:${SPECIES}_leaves`],
});
writeLootTables(preservedTreeLoot);
writeLeafResources();
writeWoodAssets();
writeWoodLootTables();
writeWoodTags();
writeWoodRecipes();
writeRubberItemAssets();
writeRubberTagsAndRecipes();
writeAssets();
writeLang();
writePackMeta();
writeEnumExtensions();

console.log("Generated dtrubber resources.");
