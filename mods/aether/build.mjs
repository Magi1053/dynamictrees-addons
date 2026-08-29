import { execSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { platform } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, "..", "..");
const outRoot = join(__dirname, "src", "main", "resources");
const structureTreesDatapack = join(__dirname, "datapack", "structure_trees");
const joCodeBase = join(repoRoot, ".tmp", "dtaether-ref", "trees", "dtaether", "jo_codes");
const aetherJar = join(repoRoot, ".tmp", "aether-neoforge.jar");
const deepAetherJar = join(repoRoot, ".tmp", "deep_aether-neoforge.jar");
const dtLootBase = join(repoRoot, ".tmp", "dt", "data", "dynamictrees", "loot_table");
const joCodeFallback = join(repoRoot, ".tmp", "dtaether-ref", "trees", "dtaether", "jo_codes", "skyroot.txt");

function resolveJarCli() {
    if (process.env.JAR) {
        return process.env.JAR;
    }
    if (process.env.JAVA_HOME) {
        const bin = platform() === "win32" ? "jar.exe" : "jar";
        return join(process.env.JAVA_HOME, "bin", bin);
    }
    return "jar";
}
const javaJar = resolveJarCli();

const TREE_NS = "dtaether";

function out(...parts) {
    return join(outRoot, ...parts);
}

/** Target ~50% faster branch chop (half hardness ≈ radius × √0.5). */
const AETHER_CHOP_HARDNESS_SCALE = 0.707106781;

function roundChop(value) {
    return Math.round(value * 1000) / 1000;
}

function chopTaper(value) {
    return roundChop(value * AETHER_CHOP_HARDNESS_SCALE);
}

function chopSignal(value) {
    return Math.max(1, Math.round(value * AETHER_CHOP_HARDNESS_SCALE));
}

function scaleChopSpecies(species) {
    if (!species) {
        return species;
    }
    const scaled = { ...species };
    if (typeof scaled.tapering === "number") {
        scaled.tapering = chopTaper(scaled.tapering);
    }
    if (typeof scaled.signal_energy === "number") {
        scaled.signal_energy = chopSignal(scaled.signal_energy);
    }
    if (typeof scaled.root_tapering === "number") {
        scaled.root_tapering = chopTaper(scaled.root_tapering);
    }
    if (typeof scaled.root_signal_energy === "number") {
        scaled.root_signal_energy = chopSignal(scaled.root_signal_energy);
    }
    return scaled;
}

/** Soil properties so DT accepts Aether ground blocks during worldgen. */
function dirtSoil(name, primitive, onlyIfLoaded) {
    const entry = {
        name,
        data: { primitive_soil: primitive, acceptable_soils: ["dirt_like"] },
    };
    if (onlyIfLoaded) {
        entry.only_if_loaded = onlyIfLoaded;
    }
    return entry;
}

const SOIL_PROPERTIES = [
    dirtSoil("aether_grass_block", "aether:aether_grass_block"),
    dirtSoil("enchanted_aether_grass_block", "aether:enchanted_aether_grass_block"),
    dirtSoil("aether_dirt", "aether:aether_dirt"),
    dirtSoil("aether_farmland", "aether:aether_farmland"),
    dirtSoil("golden_heights_grass_block", "deep_aether:golden_heights_grass_block", "deep_aether"),
    dirtSoil("aether_coarse_dirt", "deep_aether:aether_coarse_dirt", "deep_aether"),
    {
        name: "aether_mud",
        only_if_loaded: "deep_aether",
        data: {
            primitive_soil: "deep_aether:aether_mud",
            acceptable_soils: ["mud_like", "dirt_like"],
        },
    },
    {
        name: "yagroot_aerial_roots",
        only_if_loaded: "deep_aether",
        data: {
            type: "aerial_roots",
            primitive_soil: "deep_aether:yagroot_log",
            acceptable_soils: [],
        },
    },
];

const SKYROOT_SPECIES_CONFIG = {
    species: [
        { species: "dtaether:skyroot", weight: 99 },
        { species: "dtaether:golden_oak", weight: 1 },
    ],
};

function weightedCount(pairs) {
    return {
        type: "minecraft:count",
        count: {
            type: "minecraft:weighted_list",
            distribution: pairs.map(([data, weight]) => ({ data, weight })),
        },
    };
}

function improvedLayer(heightmap) {
    return {
        type: "aether:improved_layer_placement",
        count: { type: "minecraft:uniform", max_inclusive: 1, min_inclusive: 0 },
        heightmap,
        verticalBounds: 4,
    };
}

function skyrootPlacement(countStep) {
    return [
        countStep,
        { type: "minecraft:surface_water_depth_filter", max_water_depth: 0 },
        improvedLayer("OCEAN_FLOOR"),
        { type: "minecraft:biome" },
        { type: "aether:dungeon_blacklist_filter" },
    ];
}

/** Mirrors Aether skyroot placed-feature placement; inner feature is DT Java worldgen. */
const SKYROOT_DT_PLACEMENTS = [
    {
        id: "skyroot_forest_trees",
        biome: "aether:skyroot_forest",
        remove: "aether:skyroot_forest_trees",
        placement: skyrootPlacement(weightedCount([[6, 9], [7, 1]])),
    },
    {
        id: "skyroot_grove_trees",
        biome: "aether:skyroot_grove",
        remove: "aether:skyroot_grove_trees",
        placement: skyrootPlacement(weightedCount([[2, 9], [3, 1]])),
    },
    {
        id: "skyroot_meadow_trees",
        biome: "aether:skyroot_meadow",
        remove: "aether:skyroot_meadow_trees",
        placement: skyrootPlacement({ type: "minecraft:rarity_filter", chance: 1 }),
    },
    {
        id: "luminescent_skyroot_forest_trees",
        biome: "deep_aether:luminescent_forest",
        placement: [
            { type: "minecraft:count", count: 10 },
            improvedLayer("MOTION_BLOCKING"),
            { type: "minecraft:biome" },
            {
                type: "minecraft:block_predicate_filter",
                predicate: {
                    type: "minecraft:would_survive",
                    state: {
                        Name: "aether:skyroot_sapling",
                        Properties: { stage: "0" },
                    },
                },
            },
            { type: "aether:dungeon_blacklist_filter" },
        ],
    },
    {
        id: "skyroot_woodland_trees",
        biome: "aether:skyroot_woodland",
        remove: "aether:skyroot_woodland_trees",
        placement: skyrootPlacement(weightedCount([[5, 9], [6, 1]])),
    },
];

/** Aether tree / island placed features to strip after DT feature cancellers (NeoForge REMOVE phase). */
const AETHER_TREE_PLACED_FEATURES = [
    "aether:skyroot_forest_trees",
    "aether:skyroot_grove_trees",
    "aether:skyroot_meadow_trees",
    "aether:skyroot_woodland_trees",
    "aether:holiday_tree",
    "aether:crystal_island",
];

/**
 * Deep Aether tree placed features to strip when that mod is loaded.
 * Deep Aether biomes are already in #aether:is_aether, so do not re-add crystal islands there.
 */
const DEEP_AETHER_TREE_PLACED_FEATURES = [
    "deep_aether:aerglow_forest_trees_placement",
    "deep_aether:blue_aerglow_forest_trees_placement",
    "deep_aether:mystic_aerglow_forest_trees_placement",
    "deep_aether:golden_heights_trees",
    "deep_aether:golden_grove_trees",
    "deep_aether:yagroot_swamp_trees_placement",
    "deep_aether:aerlavender_field_trees",
    "deep_aether:aercloud_trees",
    "deep_aether:luminescent_skyroot_forest_trees",
    "deep_aether:overgrown_cloud_mushroom_trees",
];

const DEEP_AETHER_MOD_CONDITION = [
    {
        type: "neoforge:mod_loaded",
        modid: "deep_aether",
    },
];

/** Same placement as vanilla {@code aether:crystal_island}; feature body is DT Java. */
const CRYSTAL_ISLAND_PLACEMENT = [
    { type: "minecraft:rarity_filter", chance: 50 },
    { type: "minecraft:in_square" },
    {
        type: "minecraft:height_range",
        height: {
            type: "minecraft:uniform",
            max_inclusive: { absolute: 96 },
            min_inclusive: { absolute: 32 },
        },
    },
    { type: "minecraft:biome" },
    { type: "aether:dungeon_blacklist_filter" },
];

function tree(id, displayName, modId, extra = {}) {
    return {
        id,
        displayName,
        modId,
        log: `${id}_log`,
        logTop: `${id}_log_top`,
        strippedLog: `stripped_${id}_log`,
        strippedTop: `stripped_${id}_log_top`,
        leaves: `${id}_leaves`,
        sapling: `${id}_sapling`,
        joCode: id,
        maxBranchRadius: 24,
        ...extra,
    };
}

const TREES = [
    tree("skyroot", "Skyroot", "aether", {
        textureDir: "natural",
        species: { tapering: 0.3, signal_energy: 10, growth_rate: 0.8, up_probability: 2, lowest_branch_height: 4 },
    }),
    tree("golden_oak", "Golden Oak", "aether", {
        textureDir: "natural",
        logTop: "skyroot_log_top",
        strippedLog: "golden_oak_log",
        strippedTop: "skyroot_log_top",
        species: { tapering: 0.45, signal_energy: 14, growth_rate: 0.8, up_probability: 4, lowest_branch_height: 6 },
    }),
    tree("roseroot", "Roseroot", "deep_aether", {
        species: { tapering: 0.4, signal_energy: 10, growth_rate: 0.8, up_probability: 2, lowest_branch_height: 4 },
    }),
    tree("blue_roseroot", "Blue Roseroot", "deep_aether", {
        log: "roseroot_log",
        logTop: "roseroot_log_top",
        strippedLog: "stripped_roseroot_log",
        strippedTop: "stripped_roseroot_log_top",
        species: { tapering: 0.4, signal_energy: 10, growth_rate: 0.8, up_probability: 2, lowest_branch_height: 4 },
    }),
    tree("conberry", "Conberry", "deep_aether", {
        species: { tapering: 0.4, signal_energy: 16, growth_rate: 0.6, up_probability: 4, lowest_branch_height: 3 },
    }),
    tree("sunroot", "Sunroot", "deep_aether", {
        species: { tapering: 0.3, signal_energy: 16, growth_rate: 0.8, up_probability: 2, lowest_branch_height: 4 },
    }),
    tree("yagroot", "Yagroot", "deep_aether"),
    tree("cruderoot", "Cruderoot", "deep_aether", {
        species: { tapering: 0.35, signal_energy: 10, growth_rate: 0.6, up_probability: 3, lowest_branch_height: 3 },
    }),
];

function writeJson(path, data) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function writeVariant(rel, model) {
    writeJson(out("assets", TREE_NS, ...rel.split("/")), { variants: { "": { model } } });
}

function writeBranchModel(file, textures) {
    writeJson(out("assets", TREE_NS, "models", "block", file), {
        loader: "dynamictrees:branch",
        textures,
    });
}

function treeTexturePath(tree, block) {
    if (tree.textureDir) {
        return `${tree.modId}:block/${tree.textureDir}/${block}`;
    }
    return `${tree.modId}:block/${block}`;
}

/** DT branch models need thick_branch_rings atlas entries for modded log tops. */
function writeCrystalSkyroot() {
    const id = "crystal_skyroot";
    const skyroot = TREES.find((tree) => tree.id === "skyroot");
    if (!skyroot) {
        return;
    }

    writeJson(out("trees", TREE_NS, "leaves_properties", `${id}.json`), {
        primitive_leaves: "aether:crystal_leaves",
    });
    writeJson(out("trees", TREE_NS, "leaves_properties", `${id}_fruit.json`), {
        primitive_leaves: "aether:crystal_fruit_leaves",
    });

    writeJson(out("trees", TREE_NS, "species", `${id}.json`), {
        family: `${TREE_NS}:skyroot`,
        tapering: chopTaper(0.3),
        signal_energy: chopSignal(10),
        growth_rate: 0.8,
        up_probability: 2,
        lowest_branch_height: 4,
        leaves_properties: `${TREE_NS}:${id}`,
        preferred_climate: "temperate",
        climate_tolerance: 0.5,
        perfect_biomes: { tag: "#aether:is_aether" },
        acceptable_soils: ["dirt_like", "mud_like"],
        world_gen_acceptable_soils: ["dirt_like", "mud_like"],
        primitive_sapling: "aether:skyroot_sapling",
        primitive_saplings: [],
        generate_seed: false,
        generate_sapling: false,
        drop_seeds: false,
        features: [
            {
                name: "alt_leaves",
                properties: {
                    alternative_leaves: `${TREE_NS}:${id}_fruit`,
                    place_chance: 0.2,
                },
            },
        ],
    });

    const joSrc = join(joCodeBase, `${id}.txt`);
    const joDest = out("trees", TREE_NS, "jo_codes", `${id}.txt`);
    if (existsSync(joSrc)) {
        cpSync(joSrc, joDest);
    } else if (existsSync(out("trees", TREE_NS, "jo_codes", "skyroot.txt"))) {
        cpSync(out("trees", TREE_NS, "jo_codes", "skyroot.txt"), joDest);
    }

    writeVariant(`blockstates/${id}_leaves.json`, "aether:block/crystal_leaves");
    writeVariant(`blockstates/${id}_fruit_leaves.json`, "aether:block/crystal_fruit_leaves");
    writeVariant(`blockstates/${id}_sapling.json`, `${TREE_NS}:block/saplings/${id}`);
    writeJson(out("assets", TREE_NS, "models", "block", "saplings", `${id}.json`), {
        parent: "dynamictrees:block/smartmodel/sapling",
        textures: {
            particle: "aether:block/natural/crystal_leaves",
            log: treeTexturePath(skyroot, skyroot.log),
            leaves: "aether:block/natural/crystal_leaves",
        },
    });
    writeJson(out("assets", TREE_NS, "models", "item", `${id}_seed.json`), {
        parent: "dynamictrees:item/standard_seed",
        textures: {
            layer0: `${TREE_NS}:item/${id}_seed`,
        },
    });

    writeTreeLoot({
        id: id,
        modId: "aether",
        log: skyroot.log,
        strippedLog: skyroot.strippedLog,
        leaves: "crystal_leaves",
        sapling: "skyroot_sapling",
    });

    writeJson(out("data", TREE_NS, "loot_table", "trees", "voluntary", `${id}.json`), {
        type: "minecraft:block",
        pools: [],
        random_sequence: `${TREE_NS}:trees/voluntary/${id}`,
    });
}

function writeThickBranchAtlas() {
    const rings = new Set();
    for (const tree of TREES) {
        rings.add(treeTexturePath(tree, tree.logTop));
        rings.add(treeTexturePath(tree, tree.strippedTop));
    }
    // DT merges thick ring sprites into the minecraft block atlas, not a mod-local atlas.
    writeJson(out("assets", "minecraft", "atlases", "blocks.json"), {
        sources: [...rings].sort().map((resource) => ({
            type: "dynamictrees:thick_branch_rings",
            resource: resource,
        })),
    });
}

/** Copy log-top PNGs as *_thick so the block atlas can resolve thick branch faces. */
function extractThickRingTextures() {
    const tempExtract = join(__dirname, ".thick-texture-tmp");
    rmSync(tempExtract, { recursive: true, force: true });
    mkdirSync(tempExtract, { recursive: true });

    const copied = new Set();
    for (const tree of TREES) {
        for (const block of [tree.logTop, tree.strippedTop]) {
            const jar = tree.modId === "aether" ? aetherJar : deepAetherJar;
            if (!existsSync(jar)) {
                continue;
            }
            const basePaths = tree.textureDir
                ? [`assets/${tree.modId}/textures/block/${tree.textureDir}/${block}.png`]
                : [`assets/${tree.modId}/textures/block/${block}.png`];
            for (const baseEntry of basePaths) {
                const thickEntry = baseEntry.replace(`/${block}.png`, `/${block}_thick.png`);
                const key = thickEntry;
                if (copied.has(key)) {
                    continue;
                }
                try {
                    execSync(`"${javaJar}" xf "${jar}" ${baseEntry}`, { cwd: tempExtract, stdio: "pipe" });
                    const extracted = join(tempExtract, baseEntry);
                    if (!existsSync(extracted)) {
                        continue;
                    }
                    const dest = out( thickEntry);
                    mkdirSync(dirname(dest), { recursive: true });
                    cpSync(extracted, dest);
                    copied.add(key);
                    break;
                } catch {
                    // try next path
                }
            }
        }
    }

    // crystal_skyroot uses skyroot log tops (already covered by skyroot tree entry)
    const crystalSeedPaths = [
        "assets/aether/textures/block/natural/crystal_leaves.png",
        "assets/aether/textures/item/crystal_sapling.png",
    ];
    for (const entry of crystalSeedPaths) {
        const dest = out("assets", TREE_NS, "textures", "item", "crystal_skyroot_seed.png");
        if (existsSync(dest)) {
            continue;
        }
        try {
            execSync(`"${javaJar}" xf "${aetherJar}" ${entry}`, { cwd: tempExtract, stdio: "pipe" });
            const extracted = join(tempExtract, entry);
            if (existsSync(extracted)) {
                mkdirSync(dirname(dest), { recursive: true });
                cpSync(extracted, dest);
            }
        } catch {
            // optional seed icon
        }
    }

    rmSync(tempExtract, { recursive: true, force: true });
}

const LOOT_EXTRACT_DIR = join(__dirname, ".loot-extract");

const SILK_TOUCH_TOOL_PREDICATE = {
    predicates: {
        "minecraft:enchantments": [
            {
                enchantments: "minecraft:silk_touch",
                levels: { min: 1 },
            },
        ],
    },
};

/** Golden oak DT branches: match aether:blocks/golden_oak_log loot (1.21 item-tag + set_count amber). */
const GOLDEN_OAK_BRANCH_LOOT = {
    type: "minecraft:block",
    pools: [
        {
            bonus_rolls: 0.0,
            entries: [
                {
                    type: "minecraft:item",
                    conditions: [
                        {
                            condition: "minecraft:match_tool",
                            predicate: SILK_TOUCH_TOOL_PREDICATE,
                        },
                    ],
                    functions: [
                        { function: "dynamictrees:multiply_logs_count" },
                        { function: "minecraft:explosion_decay" },
                    ],
                    name: "aether:golden_oak_log",
                },
            ],
            rolls: 1.0,
        },
        {
            bonus_rolls: 0.0,
            entries: [
                {
                    type: "minecraft:item",
                    conditions: [
                        {
                            condition: "minecraft:inverted",
                            term: {
                                condition: "minecraft:match_tool",
                                predicate: SILK_TOUCH_TOOL_PREDICATE,
                            },
                        },
                    ],
                    functions: [
                        { function: "dynamictrees:multiply_logs_count" },
                        { function: "minecraft:explosion_decay" },
                    ],
                    name: "aether:golden_oak_log",
                },
            ],
            rolls: 1.0,
        },
        {
            bonus_rolls: 0.0,
            entries: [
                {
                    type: "minecraft:item",
                    conditions: [
                        {
                            condition: "minecraft:match_tool",
                            predicate: {
                                items: "#aether:golden_amber_harvesters",
                            },
                        },
                        {
                            condition: "minecraft:inverted",
                            term: {
                                condition: "minecraft:match_tool",
                                predicate: SILK_TOUCH_TOOL_PREDICATE,
                            },
                        },
                    ],
                    functions: [
                        {
                            function: "minecraft:set_count",
                            count: { type: "minecraft:uniform", min: 1.0, max: 2.0 },
                            add: false,
                        },
                        {
                            enchantment: "minecraft:fortune",
                            formula: "minecraft:ore_drops",
                            function: "minecraft:apply_bonus",
                        },
                        { function: "minecraft:explosion_decay" },
                    ],
                    name: "aether:golden_amber",
                },
            ],
            rolls: 1.0,
        },
        {
            bonus_rolls: 0.0,
            entries: [
                {
                    type: "minecraft:item",
                    functions: [
                        { function: "dynamictrees:multiply_sticks_count" },
                        { function: "minecraft:explosion_decay" },
                    ],
                    name: "aether:skyroot_stick",
                },
            ],
            rolls: 1.0,
        },
    ],
    random_sequence: `${TREE_NS}:trees/branches/golden_oak`,
};

function replaceLootTemplate(text, tree) {
    const id = tree.id;
    // Birch-specific paths must be rewritten before the generic dynamictrees:trees/ → dtaether:trees/ pass.
    return text
        .replaceAll("dynamictrees:trees/branches/stripped_birch", `${TREE_NS}:trees/branches/stripped_${id}`)
        .replaceAll("dynamictrees:trees/branches/birch", `${TREE_NS}:trees/branches/${id}`)
        .replaceAll("dynamictrees:trees/leaves/birch", `${TREE_NS}:trees/leaves/${id}`)
        .replaceAll("dynamictrees:trees/voluntary/birch", `${TREE_NS}:trees/voluntary/${id}`)
        .replaceAll("dynamictrees:blocks/birch_leaves", `${TREE_NS}:blocks/${id}_leaves`)
        .replaceAll("dynamictrees:trees/", `${TREE_NS}:trees/`)
        .replaceAll("dynamictrees:blocks/", `${TREE_NS}:blocks/`)
        .replaceAll("minecraft:birch_log", `${tree.modId}:${tree.log}`)
        .replaceAll("minecraft:stripped_birch_log", `${tree.modId}:${tree.strippedLog}`)
        .replaceAll("minecraft:birch_leaves", `${tree.modId}:${tree.leaves}`)
        .replaceAll("minecraft:stick", "aether:skyroot_stick")
        .replaceAll("dynamictrees:birch_seed", `${TREE_NS}:${id}_seed`);
}

/** DT only datagens vanilla families into #dynamictrees:branches_that_burn; addon packs must append explicitly. */
function writeDynamictreesBranchTags() {
    const branchBlocks = TREES.map((tree) => `${TREE_NS}:${tree.id}_branch`);
    const strippedBlocks = TREES.map((tree) => `${TREE_NS}:stripped_${tree.id}_branch`);
    const branchItems = [...branchBlocks];

    const tagRoot = out("data", "dynamictrees", "tags");
    writeJson(join(tagRoot, "block", "branches_that_burn.json"), { values: branchBlocks });
    writeJson(join(tagRoot, "block", "stripped_branches_that_burn.json"), { values: strippedBlocks });
    writeJson(join(tagRoot, "item", "branches_that_burn.json"), { values: branchItems });
}

function readModLootTable(modId, lootPath) {
    const jar = modId === "aether" ? aetherJar : deepAetherJar;
    if (!existsSync(jar)) {
        return null;
    }
    const outFile = join(LOOT_EXTRACT_DIR, lootPath);
    mkdirSync(dirname(outFile), { recursive: true });
    try {
        execSync(`"${javaJar}" xf "${jar}" ${lootPath}`, { cwd: LOOT_EXTRACT_DIR, stdio: "pipe" });
    } catch {
        return null;
    }
    if (!existsSync(outFile)) {
        return null;
    }
    return JSON.parse(readFileSync(outFile, "utf8"));
}

function isSaplingLootEntry(child) {
    return child?.type === "minecraft:item" && typeof child.name === "string" && child.name.includes("sapling");
}

function toDtSeedLootEntry(vanillaSaplingChild) {
    const fortune = vanillaSaplingChild?.conditions?.find(
        (condition) => condition.condition === "minecraft:table_bonus" && condition.enchantment === "minecraft:fortune",
    );
    return {
        type: "dynamictrees:seed_item",
        conditions: [
            { condition: "minecraft:survives_explosion" },
            {
                chances: fortune?.chances ?? [0.05, 0.0625, 0.083333336, 0.1],
                condition: "minecraft:table_bonus",
                enchantment: "minecraft:fortune",
            },
            { condition: "dynamictrees:seasonal_seed_drop_chance" },
        ],
    };
}

function transformVanillaLeavesLoot(loot) {
    const copy = structuredClone(loot);
    for (const pool of copy.pools ?? []) {
        for (const entry of pool.entries ?? []) {
            if (entry.type !== "minecraft:alternatives" || !Array.isArray(entry.children)) {
                continue;
            }
            entry.children = entry.children.map((child) =>
                isSaplingLootEntry(child) ? toDtSeedLootEntry(child) : child,
            );
        }
    }
    return copy;
}

function writeDtLeavesLoot(tree, loot) {
    writeJson(out("data", TREE_NS, "loot_table", "trees", "leaves", `${tree.id}.json`), {
        ...loot,
        random_sequence: `${TREE_NS}:trees/leaves/${tree.id}`,
    });
    writeJson(out("data", TREE_NS, "loot_table", "blocks", `${tree.id}_leaves.json`), {
        ...loot,
        random_sequence: `${TREE_NS}:blocks/${tree.id}_leaves`,
    });
}

function writeStandardAetherBranchLoot(tree) {
    writeJson(out("data", TREE_NS, "loot_table", "trees", "branches", `${tree.id}.json`), {
        type: "minecraft:block",
        pools: [
            {
                bonus_rolls: 0.0,
                entries: [
                    {
                        type: "minecraft:item",
                        functions: [
                            { function: "dynamictrees:multiply_logs_count" },
                            { function: "minecraft:explosion_decay" },
                        ],
                        name: `${tree.modId}:${tree.log}`,
                    },
                ],
                rolls: 1.0,
            },
            {
                bonus_rolls: 0.0,
                entries: [
                    {
                        type: "minecraft:item",
                        functions: [
                            { function: "dynamictrees:multiply_sticks_count" },
                            { function: "minecraft:explosion_decay" },
                        ],
                        name: "aether:skyroot_stick",
                    },
                ],
                rolls: 1.0,
            },
        ],
        random_sequence: `${TREE_NS}:trees/branches/${tree.id}`,
    });
}

function copyBirchLootTemplate(fromRel, toRel, tree) {
    const src = join(dtLootBase, fromRel);
    const dest = out("data", TREE_NS, "loot_table", toRel);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, replaceLootTemplate(readFileSync(src, "utf8"), tree), "utf8");
}

function writeTreeLoot(tree) {
    if (tree.id === "golden_oak") {
        writeJson(out("data", TREE_NS, "loot_table", "trees", "branches", "golden_oak.json"), GOLDEN_OAK_BRANCH_LOOT);
    } else {
        writeStandardAetherBranchLoot(tree);
    }

    copyBirchLootTemplate("trees/branches/stripped_birch.json", `trees/branches/stripped_${tree.id}.json`, tree);
    copyBirchLootTemplate("trees/voluntary/birch.json", `trees/voluntary/${tree.id}.json`, tree);

    const vanillaLeaves = readModLootTable(tree.modId, `data/${tree.modId}/loot_table/blocks/${tree.leaves}.json`);
    if (vanillaLeaves) {
        writeDtLeavesLoot(tree, transformVanillaLeavesLoot(vanillaLeaves));
    } else {
        copyBirchLootTemplate("trees/leaves/birch.json", `trees/leaves/${tree.id}.json`, tree);
        copyBirchLootTemplate("blocks/birch_leaves.json", `blocks/${tree.id}_leaves.json`, tree);
    }
}

function writeSaplingLootOverride(tree) {
    writeJson(out("data", tree.modId, "loot_table", "blocks", `${tree.sapling}.json`), {
        type: "minecraft:block",
        pools: [
            {
                rolls: 1.0,
                bonus_rolls: 0.0,
                entries: [
                    {
                        type: "minecraft:item",
                        name: `${TREE_NS}:${tree.id}_seed`,
                    },
                ],
                conditions: [
                    {
                        condition: "minecraft:survives_explosion",
                    },
                ],
            },
        ],
    });
}

function copyJoCode(tree) {
    const src = join(joCodeBase, `${tree.joCode}.txt`);
    const dest = out("trees", TREE_NS, "jo_codes", `${tree.id}.txt`);
    if (existsSync(src)) {
        cpSync(src, dest);
    } else if (existsSync(joCodeFallback)) {
        cpSync(joCodeFallback, dest);
    }
}

function writeFamily(tree) {
    const id = tree.id;
    const mod = tree.modId;
    const radius = tree.maxBranchRadius ?? 8;
    const path = out("trees", TREE_NS, "families", `${id}.json`);

    if (id === "yagroot") {
        writeJson(path, {
            type: "underground_roots",
            common_leaves: `${TREE_NS}:${id}`,
            common_species: `${TREE_NS}:${id}`,
            primitive_log: `${mod}:${tree.log}`,
            primitive_stripped_log: `${mod}:${tree.strippedLog}`,
            stick: "aether:skyroot_stick",
            max_branch_radius: radius,
            default_soil: `${TREE_NS}:yagroot_aerial_roots`,
            primitive_root: "deep_aether:yagroot_roots",
            primitive_filled_root: "deep_aether:muddy_yagroot_roots",
            primitive_covered_root: "deep_aether:aether_mud",
            root_system_acceptable_soils: ["dirt_like", "mud_like"],
        });
        return;
    }

    const family = {
        common_leaves: `${TREE_NS}:${id}`,
        common_species: `${TREE_NS}:${id}`,
        primitive_log: `${mod}:${tree.log}`,
        primitive_stripped_log: `${mod}:${tree.strippedLog}`,
        stick: "aether:skyroot_stick",
        max_branch_radius: radius,
    };
    if (id === "golden_oak") {
        family.strip_loot_location = "aether:stripping/strip_golden_oak";
    }
    writeJson(path, family);
}

function writeSoilProperties() {
    for (const entry of SOIL_PROPERTIES) {
        const payload = { ...entry.data };
        if (entry.only_if_loaded) {
            payload.only_if_loaded = entry.only_if_loaded;
        }
        writeJson(out("trees", TREE_NS, "soil_properties", `${entry.name}.json`), payload);
    }
}

/** DT rooty soil blocks need multipart blockstates (soil + fertility overlay) or the base shows missing texture. */
function copyRootyBlockstates() {
    const refDir = join(repoRoot, ".tmp", "dtaether-ref", "assets", "dtaether", "blockstates");
    const destDir = out("assets", TREE_NS, "blockstates");
    mkdirSync(destDir, { recursive: true });

    for (const entry of SOIL_PROPERTIES) {
        const fileName = `rooty_${entry.name}.json`;
        const refPath = join(refDir, fileName);
        if (existsSync(refPath)) {
            cpSync(refPath, join(destDir, fileName));
        }
    }

    const yagrootRoots = join(refDir, "rooty_yagroot_aerial_roots.json");
    if (existsSync(yagrootRoots)) {
        cpSync(yagrootRoots, join(destDir, "rooty_yagroot_aerial_roots.json"));
        const refModels = join(repoRoot, ".tmp", "dtaether-ref", "assets", "dtaether", "models", "block");
        const destModels = out("assets", TREE_NS, "models", "block");
        mkdirSync(destModels, { recursive: true });
        for (const name of [
            "rooty_yagroot_aerial_roots_radius1.json",
            "rooty_yagroot_aerial_roots_radius2.json",
            "rooty_yagroot_aerial_roots_radius3.json",
            "rooty_yagroot_aerial_roots_radius4.json",
            "rooty_yagroot_aerial_roots_radius5.json",
            "rooty_yagroot_aerial_roots_radius6.json",
            "rooty_yagroot_aerial_roots_radius7.json",
            "rooty_yagroot_aerial_roots_radius8.json",
        ]) {
            const refModel = join(refModels, name);
            if (existsSync(refModel)) {
                cpSync(refModel, join(destModels, name));
            }
        }
    }
}

function writeTreeAssets(tree) {
    const id = tree.id;
    const mod = tree.modId;

    writeFamily(tree);

    writeJson(out("trees", TREE_NS, "leaves_properties", `${id}.json`), {
        primitive_leaves: `${mod}:${tree.leaves}`,
    });

    const species = {
        family: `${TREE_NS}:${id}`,
        tapering: chopTaper(0.2),
        signal_energy: chopSignal(12),
        up_probability: 2,
        lowest_branch_height: 4,
        growth_rate: 1.0,
        leaves_properties: `${TREE_NS}:${id}`,
        preferred_climate: "temperate",
        climate_tolerance: 0.5,
        perfect_biomes: { tag: "#aether:is_aether" },
        acceptable_soils: ["dirt_like", "mud_like"],
        world_gen_acceptable_soils: ["dirt_like", "mud_like"],
        primitive_sapling: `${mod}:${tree.sapling}`,
        primitive_saplings: [`${mod}:${tree.sapling}`],
        generate_seed: true,
        generate_sapling: true,
        drop_seeds: true,
        ...(tree.species ? scaleChopSpecies(tree.species) : {}),
    };
    writeJson(out("trees", TREE_NS, "species", `${id}.json`), species);

    copyJoCode(tree);

    const branchTextures = {
        bark: treeTexturePath(tree, tree.log),
        rings: treeTexturePath(tree, tree.logTop),
    };
    const strippedTextures = {
        bark: treeTexturePath(tree, tree.strippedLog),
        rings: treeTexturePath(tree, tree.strippedTop),
    };

    writeVariant(`blockstates/${id}_branch.json`, `${TREE_NS}:block/${id}_branch`);
    writeVariant(`blockstates/stripped_${id}_branch.json`, `${TREE_NS}:block/stripped_${id}_branch`);
    writeVariant(`blockstates/${id}_leaves.json`, `${mod}:block/${tree.leaves}`);
    writeVariant(`blockstates/${id}_sapling.json`, `${TREE_NS}:block/saplings/${id}`);
    writeBranchModel(`${id}_branch.json`, branchTextures);
    writeBranchModel(`stripped_${id}_branch.json`, strippedTextures);
    writeJson(out("assets", TREE_NS, "models", "block", "saplings", `${id}.json`), {
        parent: "dynamictrees:block/smartmodel/sapling",
        textures: {
            particle: treeTexturePath(tree, tree.leaves),
            log: treeTexturePath(tree, tree.log),
            leaves: treeTexturePath(tree, tree.leaves),
        },
    });
    writeJson(out("assets", TREE_NS, "models", "item", `${id}_seed.json`), {
        parent: "dynamictrees:item/standard_seed",
        textures: {
            layer0: `${TREE_NS}:item/${id}_seed`,
        },
    });
    writeJson(out("assets", TREE_NS, "models", "item", `${id}_branch.json`), {
        parent: `${TREE_NS}:block/${id}_branch`,
    });
}

function extractSeedTextures() {
    if (!existsSync(javaJar)) {
        return;
    }
    const tempExtract = join(__dirname, ".extract-tmp");
    rmSync(tempExtract, { recursive: true, force: true });
    mkdirSync(tempExtract, { recursive: true });

    for (const tree of TREES) {
        const dest = out("assets", TREE_NS, "textures", "item", `${tree.id}_seed.png`);
        if (existsSync(dest)) {
            continue;
        }
        const jar = tree.modId === "aether" ? aetherJar : deepAetherJar;
        if (!existsSync(jar)) {
            continue;
        }
        const paths = [
            `assets/${tree.modId}/textures/block/natural/${tree.sapling}.png`,
            `assets/${tree.modId}/textures/block/${tree.sapling}.png`,
        ];
        for (const entry of paths) {
            try {
                execSync(`"${javaJar}" xf "${jar}" ${entry}`, { cwd: tempExtract, stdio: "pipe" });
                const extracted = join(tempExtract, entry);
                if (existsSync(extracted)) {
                    mkdirSync(dirname(dest), { recursive: true });
                    cpSync(extracted, dest);
                    break;
                }
            } catch {
                // try next path
            }
        }
    }
    rmSync(tempExtract, { recursive: true, force: true });
}

function writeWorldgen() {
    writeJson(out("trees", TREE_NS, "world_gen", "default.json"), [
        {
            select: { name: "aether:skyroot.*" },
            apply: {
                species: {
                    random: {
                        "dtaether:skyroot": 24,
                        "dtaether:golden_oak": 1,
                    },
                },
            },
        },
        {
            select: { name: "aether:skyroot_forest" },
            apply: {
                density: [0.1],
                chance: 0.9,
                forestness: 1,
            },
        },
        {
            select: { name: "aether:skyroot_woodland" },
            apply: {
                density: [1],
                chance: 1,
                forestness: 1,
            },
        },
        {
            select: { name: "aether:skyroot_meadow" },
            apply: {
                density: [0.1],
                chance: 0.2,
            },
        },
        {
            select: { name: "aether:skyroot_grove" },
            apply: {
                density: [0.6],
                chance: 0.4,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:aerglow_forest" },
            apply: {
                species: {
                    random: {
                        "dtaether:roseroot": 8,
                        "dtaether:blue_roseroot": 1,
                    },
                },
                density: [1.5],
                chance: 0.6,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:blue_aerglow_forest" },
            apply: {
                species: "dtaether:blue_roseroot",
                density: [1.5],
                chance: 0.6,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:mystic_aerglow_forest" },
            apply: {
                species: "dtaether:roseroot",
                density: [1.5],
                chance: 0.6,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:golden_heights" },
            apply: {
                species: {
                    random: {
                        "dtaether:conberry": 4,
                        "dtaether:sunroot": 1,
                    },
                },
                density: [0.4],
                chance: 0.4,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:golden_grove" },
            apply: {
                species: {
                    random: {
                        "dtaether:conberry": 4,
                        "dtaether:sunroot": 1,
                    },
                },
                density: [0.6],
                chance: 1,
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:yagroot_swamp" },
            apply: {
                species: {
                    random: {
                        "dtaether:yagroot": 8,
                        "dtaether:cruderoot": 1,
                    },
                },
                density: [0.8],
                chance: 1,
                forestness: 1,
            },
        },
    ]);

    writeJson(out("trees", TREE_NS, "world_gen", "feature_cancellers.json"), [
        {
            select: { name: "aether:.*" },
            cancellers: {
                types: ["tree"],
                namespaces: ["aether", "minecraft"],
            },
        },
        {
            only_if_loaded: "deep_aether",
            select: { name: "deep_aether:.*" },
            cancellers: {
                types: ["tree"],
                namespaces: ["aether", "deep_aether", "minecraft"],
            },
        },
    ]);

    const AETHER_TREE_REMOVAL_STEPS = ["vegetal_decoration", "top_layer_modification"];

    writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", "remove_vanilla_trees.json"), {
        type: "neoforge:remove_features",
        biomes: "#aether:is_aether",
        features: AETHER_TREE_PLACED_FEATURES,
        steps: AETHER_TREE_REMOVAL_STEPS,
    });

    // Deep Aether biomes are tagged #aether:is_aether; only strip DA-specific tree features here.
    writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", "remove_deep_aether_trees.json"), {
        "neoforge:conditions": DEEP_AETHER_MOD_CONDITION,
        type: "neoforge:remove_features",
        biomes: "#aether:is_aether",
        features: DEEP_AETHER_TREE_PLACED_FEATURES,
        steps: ["vegetal_decoration"],
    });

    writeJson(out("data", TREE_NS, "worldgen", "configured_feature", "skyroot_trees.json"), {
        type: `${TREE_NS}:aether_dt_tree`,
        config: SKYROOT_SPECIES_CONFIG,
    });

    for (const entry of SKYROOT_DT_PLACEMENTS) {
        writeJson(out("data", TREE_NS, "worldgen", "placed_feature", `${entry.id}.json`), {
            feature: `${TREE_NS}:skyroot_trees`,
            placement: entry.placement,
        });
        const addModifier = {
            type: "neoforge:add_features",
            biomes: entry.biome,
            features: `${TREE_NS}:${entry.id}`,
            step: "vegetal_decoration",
        };
        if (String(entry.biome).startsWith("deep_aether:")) {
            addModifier["neoforge:conditions"] = DEEP_AETHER_MOD_CONDITION;
        }
        writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", `add_${entry.id}.json`), addModifier);
    }

    writeJson(out("data", TREE_NS, "worldgen", "configured_feature", "crystal_island.json"), {
        type: `${TREE_NS}:crystal_island`,
        config: {},
    });
    writeJson(out("data", TREE_NS, "worldgen", "placed_feature", "crystal_island.json"), {
        feature: `${TREE_NS}:crystal_island`,
        placement: CRYSTAL_ISLAND_PLACEMENT,
    });
    // Covers Aether + Deep Aether (DA biomes are in #aether:is_aether). Do not add a second modifier
    // with the same placed feature — that creates a feature-order cycle.
    writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", "add_crystal_island.json"), {
        type: "neoforge:add_features",
        biomes: "#aether:is_aether",
        features: `${TREE_NS}:crystal_island`,
        step: "top_layer_modification",
    });
}

rmSync(outRoot, { recursive: true, force: true });
mkdirSync(outRoot, { recursive: true });

const lang = {};
for (const tree of TREES) {
    lang[`block.${TREE_NS}.${tree.id}_branch`] = `${tree.displayName} Tree`;
    lang[`block.${TREE_NS}.${tree.id}_sapling`] = `${tree.displayName} Sapling`;
    lang[`block.${TREE_NS}.${tree.id}_leaves`] = `${tree.displayName} Leaves`;
    lang[`block.${TREE_NS}.stripped_${tree.id}_branch`] = `Stripped ${tree.displayName} Tree`;
    lang[`item.${TREE_NS}.${tree.id}_seed`] = `${tree.displayName} Tree Seed`;
    lang[`species.${TREE_NS}.${tree.id}`] = tree.displayName;
    if (tree.id === "golden_oak") {
        lang[`item.${TREE_NS}.${tree.id}_seed`] = "Golden Oak Acorn";
    }
}
Object.assign(lang, {
    [`block.${TREE_NS}.crystal_skyroot_leaves`]: "Crystal Leaves",
    [`block.${TREE_NS}.crystal_skyroot_fruit_leaves`]: "Crystal Fruit Leaves",
    [`block.${TREE_NS}.crystal_skyroot_sapling`]: "Crystal Skyroot Sapling",
    [`item.${TREE_NS}.crystal_skyroot_seed`]: "Crystal Skyroot Seed",
    [`species.${TREE_NS}.crystal_skyroot`]: "Crystal Skyroot",
});
writeJson(out("assets", TREE_NS, "lang", "en_us.json"), lang);

writeSoilProperties();
copyRootyBlockstates();
writeThickBranchAtlas();
extractThickRingTextures();

for (const tree of TREES) {
    writeTreeAssets(tree);
    writeTreeLoot(tree);
    writeSaplingLootOverride(tree);
}
writeCrystalSkyroot();
writeDynamictreesBranchTags();
writeWorldgen();

if (existsSync(structureTreesDatapack)) {
    cpSync(structureTreesDatapack, outRoot, { recursive: true });
}

writeJson(out("pack.mcmeta"), {
    pack: {
        description: "Dynamic Trees tree pack for The Aether and Deep Aether",
        pack_format: 34,
    },
});

extractSeedTextures();

console.log(`Generated resources in ${outRoot}`);
