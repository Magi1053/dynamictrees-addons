import { execSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { platform } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, "..", "..");
const outRoot = join(__dirname, "src", "main", "resources");
const bundledAssets = join(__dirname, "bundled_assets");
const spectrumJar = join(repoRoot, ".tmp", "spectrum-neoforge.jar");
const dtLootBase = join(repoRoot, ".tmp", "dt", "data", "dynamictrees", "loot_table");
// Oak jo code — birch jo codes yield thicker branch radii at the same visual size (~2–3× chop time).
const oakJoCode = join(repoRoot, ".tmp", "dt", "trees", "dynamictrees", "jo_codes", "oak.txt");
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

const TREE_NS = "dtspectrum";

const COLORS = [
    "black",
    "blue",
    "brown",
    "cyan",
    "gray",
    "green",
    "light_blue",
    "light_gray",
    "lime",
    "magenta",
    "orange",
    "pink",
    "purple",
    "red",
    "white",
    "yellow",
];

/** Matches Spectrum colored_tree_patch weighted feature list (CMY set). */
const WORLDGEN_WEIGHTS = {
    blue: 1,
    cyan: 3,
    green: 1,
    light_blue: 1,
    lime: 1,
    magenta: 3,
    orange: 1,
    pink: 1,
    purple: 1,
    red: 1,
    yellow: 3,
};

function writeJson(path, data) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function colorTitle(color) {
    return color
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
}

const WHITE_GROUP = new Set(["white", "light_gray", "gray"]);
const BLACK_GROUP = new Set(["black", "brown"]);

function branchStateCloaks(color, stripped) {
    const block = stripped ? `${TREE_NS}:stripped_${color}_branch` : `${TREE_NS}:${color}_branch`;
    // Preserve branch radius — vanilla oak_log is always a full 1x1 block.
    const cloakBlock = stripped ? "dynamictrees:stripped_oak_branch" : "dynamictrees:oak_branch";
    const entries = {};
    for (let radius = 1; radius <= 8; radius++) {
        for (const waterlogged of [false, true]) {
            entries[`${block}[radius=${radius},waterlogged=${waterlogged}]`] =
                `${cloakBlock}[radius=${radius},waterlogged=${waterlogged}]`;
        }
    }
    return entries;
}

function leavesStateCloaks(color) {
    const entries = {};
    for (let distance = 1; distance <= 7; distance++) {
        for (const persistent of [false, true]) {
            for (const waterlogged of [false, true]) {
                entries[`${TREE_NS}:${color}_leaves[distance=${distance},persistent=${persistent},waterlogged=${waterlogged}]`] =
                    `dynamictrees:oak_leaves[distance=${distance},persistent=${persistent},waterlogged=${waterlogged}]`;
            }
        }
    }
    return entries;
}

function saplingStateCloaks(color) {
    // DynamicTrees sapling blocks have no growth stage property.
    return {
        [`${TREE_NS}:${color}_sapling`]: "dynamictrees:oak_sapling",
    };
}

function treeBlockStateCloaks(color) {
    return {
        ...branchStateCloaks(color, false),
        ...branchStateCloaks(color, true),
        ...leavesStateCloaks(color),
        ...saplingStateCloaks(color),
    };
}

function writeRevelationCloaks() {
    const cmyTreeBlocks = {};
    const cmySeedItems = {};
    const whiteTreeBlocks = {};
    const whiteSeedItems = {};
    const blackTreeBlocks = {};
    const blackSeedItems = {};

    for (const color of COLORS) {
        const treeBlocks = treeBlockStateCloaks(color);

        if (WHITE_GROUP.has(color)) {
            Object.assign(whiteTreeBlocks, treeBlocks);
            whiteSeedItems[`${TREE_NS}:${color}_seed`] = "minecraft:oak_sapling";
        } else if (BLACK_GROUP.has(color)) {
            Object.assign(blackTreeBlocks, treeBlocks);
            blackSeedItems[`${TREE_NS}:${color}_seed`] = "minecraft:oak_sapling";
        } else {
            Object.assign(cmyTreeBlocks, treeBlocks);
            cmySeedItems[`${TREE_NS}:${color}_seed`] = "minecraft:oak_sapling";
        }
    }

    const revelationDir = join(outRoot, "data", TREE_NS, "revelations");

    writeJson(join(revelationDir, "cmy_tree_blocks.json"), {
        advancement: "spectrum:milestones/reveal_colored_trees_cmy",
        block_states: cmyTreeBlocks,
    });

    // CMY seeds use the sapling milestone (matches Spectrum ColoredTree.TreePart.SAPLING).
    writeJson(join(revelationDir, "cmy_seed_items.json"), {
        advancement: "spectrum:milestones/reveal_colored_saplings_cmy",
        items: cmySeedItems,
    });

    writeJson(join(revelationDir, "grayscale_tree_blocks_w.json"), {
        advancement: "spectrum:milestones/reveal_colored_trees_w",
        block_states: whiteTreeBlocks,
        items: whiteSeedItems,
    });

    writeJson(join(revelationDir, "grayscale_tree_blocks_k.json"), {
        advancement: "spectrum:milestones/reveal_colored_trees_k",
        block_states: blackTreeBlocks,
        items: blackSeedItems,
    });
}

function replaceLootTemplate(text, color) {
    // Birch-specific paths must be rewritten before the generic dynamictrees:trees/ → dtspectrum:trees/ pass.
    return text
        .replaceAll("dynamictrees:trees/branches/stripped_birch", `${TREE_NS}:trees/branches/stripped_${color}`)
        .replaceAll("dynamictrees:trees/branches/birch", `${TREE_NS}:trees/branches/${color}`)
        .replaceAll("dynamictrees:trees/leaves/birch", `${TREE_NS}:trees/leaves/${color}`)
        .replaceAll("dynamictrees:trees/voluntary/birch", `${TREE_NS}:trees/voluntary/${color}`)
        .replaceAll("dynamictrees:blocks/birch_leaves", `${TREE_NS}:blocks/${color}_leaves`)
        .replaceAll("dynamictrees:trees/", `${TREE_NS}:trees/`)
        .replaceAll("dynamictrees:blocks/", `${TREE_NS}:blocks/`)
        .replaceAll("minecraft:birch_log", `spectrum:${color}_log`)
        .replaceAll("minecraft:stripped_birch_log", `spectrum:stripped_${color}_log`)
        .replaceAll("minecraft:birch_leaves", `spectrum:${color}_leaves`)
        .replaceAll("dynamictrees:birch_seed", `${TREE_NS}:${color}_seed`);
}

/** DT only datagens vanilla families into #dynamictrees:branches_that_burn; addon packs must append explicitly. */
function writeDynamictreesBranchTags() {
    const branchBlocks = COLORS.map((color) => `${TREE_NS}:${color}_branch`);
    const strippedBlocks = COLORS.map((color) => `${TREE_NS}:stripped_${color}_branch`);
    const branchItems = [...branchBlocks];

    const tagRoot = join(outRoot, "data", "dynamictrees", "tags");
    writeJson(join(tagRoot, "block", "branches_that_burn.json"), { values: branchBlocks });
    writeJson(join(tagRoot, "block", "stripped_branches_that_burn.json"), { values: strippedBlocks });
    writeJson(join(tagRoot, "item", "branches_that_burn.json"), { values: branchItems });
}

function copyLootFromBirch(color) {
    const lootFiles = [
        ["trees/branches/birch.json", `trees/branches/${color}.json`],
        ["trees/branches/stripped_birch.json", `trees/branches/stripped_${color}.json`],
        ["trees/leaves/birch.json", `trees/leaves/${color}.json`],
        ["trees/voluntary/birch.json", `trees/voluntary/${color}.json`],
        ["blocks/birch_leaves.json", `blocks/${color}_leaves.json`],
    ];
    for (const [fromRel, toRel] of lootFiles) {
        const src = join(dtLootBase, fromRel);
        const dest = join(outRoot, "data", TREE_NS, "loot_table", toRel);
        mkdirSync(dirname(dest), { recursive: true });
        writeFileSync(dest, replaceLootTemplate(readFileSync(src, "utf8"), color), "utf8");
    }
}

function writeSaplingLootOverride(color) {
    writeJson(join(outRoot, "data", "spectrum", "loot_table", "blocks", `${color}_sapling.json`), {
        type: "minecraft:block",
        pools: [
            {
                rolls: 1.0,
                bonus_rolls: 0.0,
                entries: [
                    {
                        type: "minecraft:item",
                        name: `${TREE_NS}:${color}_seed`,
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

function writeTreeAssets(color) {
    writeJson(join(outRoot, "trees", TREE_NS, "families", `${color}.json`), {
        common_leaves: `${TREE_NS}:${color}`,
        common_species: `${TREE_NS}:${color}`,
        primitive_log: `spectrum:${color}_log`,
        primitive_stripped_log: `spectrum:stripped_${color}_log`,
        max_branch_radius: 8,
    });

    writeJson(join(outRoot, "trees", TREE_NS, "leaves_properties", `${color}.json`), {
        primitive_leaves: `spectrum:${color}_leaves`,
    });

    writeJson(join(outRoot, "trees", TREE_NS, "species", `${color}.json`), {
        family: `${TREE_NS}:${color}`,
        tapering: 0.3,
        signal_energy: 12,
        up_probability: 2,
        lowest_branch_height: 4,
        growth_rate: 0.8,
        leaves_properties: `${TREE_NS}:${color}`,
        preferred_climate: "temperate",
        climate_tolerance: 0.5,
        perfect_biomes: { tag: "#spectrum:colored_trees_generating_in" },
        acceptable_soils: ["dirt_like"],
        world_gen_acceptable_soils: ["dirt_like"],
        primitive_sapling: `spectrum:${color}_sapling`,
        primitive_saplings: [`spectrum:${color}_sapling`],
        generate_seed: true,
        generate_sapling: true,
        drop_seeds: true,
        features: ["bee_nest"],
    });

    cpSync(oakJoCode, join(outRoot, "trees", TREE_NS, "jo_codes", `${color}.txt`));

    writeJson(join(outRoot, "assets", TREE_NS, "blockstates", `${color}_branch.json`), {
        variants: {
            "": { model: `${TREE_NS}:block/${color}_branch` },
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "blockstates", `stripped_${color}_branch.json`), {
        variants: {
            "": { model: `${TREE_NS}:block/stripped_${color}_branch` },
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "blockstates", `${color}_leaves.json`), {
        variants: {
            "": { model: `spectrum:block/${color}_leaves` },
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "blockstates", `${color}_sapling.json`), {
        variants: {
            "": { model: `${TREE_NS}:block/saplings/${color}` },
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "models", "block", `${color}_branch.json`), {
        loader: "dynamictrees:branch",
        textures: {
            bark: `spectrum:block/${color}_log`,
            rings: `spectrum:block/${color}_log_top`,
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "models", "block", `stripped_${color}_branch.json`), {
        loader: "dynamictrees:branch",
        textures: {
            bark: `spectrum:block/stripped_${color}_log`,
            rings: `spectrum:block/stripped_${color}_log_top`,
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "models", "block", "saplings", `${color}.json`), {
        parent: "dynamictrees:block/smartmodel/sapling",
        textures: {
            particle: `spectrum:block/${color}_leaves`,
            log: `spectrum:block/${color}_log`,
            leaves: `spectrum:block/${color}_leaves`,
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "models", "item", `${color}_seed.json`), {
        parent: "dynamictrees:item/standard_seed",
        textures: {
            layer0: `${TREE_NS}:item/${color}_seed`,
        },
    });

    writeJson(join(outRoot, "assets", TREE_NS, "models", "item", `${color}_branch.json`), {
        parent: `${TREE_NS}:block/${color}_branch`,
    });
}

function extractSeedTextures() {
    if (!existsSync(spectrumJar) || !existsSync(javaJar)) {
        return;
    }
    const tempExtract = join(__dirname, ".extract-tmp");
    rmSync(tempExtract, { recursive: true, force: true });
    mkdirSync(tempExtract, { recursive: true });
    for (const color of COLORS) {
        const dest = join(outRoot, "assets", TREE_NS, "textures", "item", `${color}_seed.png`);
        if (existsSync(dest)) {
            continue;
        }
        try {
            execSync(`"${javaJar}" xf "${spectrumJar}" assets/spectrum/textures/block/${color}_sapling.png`, {
                cwd: tempExtract,
                stdio: "pipe",
            });
            const extracted = join(tempExtract, "assets", "spectrum", "textures", "block", `${color}_sapling.png`);
            if (existsSync(extracted)) {
                mkdirSync(dirname(dest), { recursive: true });
                cpSync(extracted, dest);
            }
        } catch {
            // ignore per-color failures
        }
    }
    rmSync(tempExtract, { recursive: true, force: true });
}

rmSync(outRoot, { recursive: true, force: true });
mkdirSync(outRoot, { recursive: true });

const lang = {};
for (const color of COLORS) {
    const title = colorTitle(color);
    lang[`block.${TREE_NS}.${color}_branch`] = `${title} Tree`;
    lang[`block.${TREE_NS}.${color}_sapling`] = `${title} Sapling`;
    lang[`block.${TREE_NS}.${color}_leaves`] = `${title} Leaves`;
    lang[`block.${TREE_NS}.stripped_${color}_branch`] = `Stripped ${title} Tree`;
    lang[`item.${TREE_NS}.${color}_seed`] = `${title} Tree Seed`;
    lang[`species.${TREE_NS}.${color}`] = title;
}
writeJson(join(outRoot, "assets", TREE_NS, "lang", "en_us.json"), lang);

for (const color of COLORS) {
    writeTreeAssets(color);
    copyLootFromBirch(color);
    writeSaplingLootOverride(color);
}

// DT species in Spectrum biomes are spawned only by ColoredTreePatchFeature (see Java worldgen),
// not via splice_before on the global dynamic_tree populator.
writeJson(join(outRoot, "trees", TREE_NS, "world_gen", "default.json"), []);

writeJson(join(outRoot, "data", TREE_NS, "worldgen", "configured_feature", "colored_tree_patch.json"), {
    type: `${TREE_NS}:colored_tree_patch`,
    config: {},
});

writeJson(join(outRoot, "data", TREE_NS, "worldgen", "placed_feature", "colored_tree_patch.json"), {
    feature: `${TREE_NS}:colored_tree_patch`,
    placement: [
        { type: "minecraft:rarity_filter", chance: 75 },
        { type: "minecraft:in_square" },
        { type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
        { type: "minecraft:biome" },
    ],
});

writeJson(join(outRoot, "data", TREE_NS, "neoforge", "biome_modifier", "colored_tree_patch.json"), {
    type: "neoforge:add_features",
    biomes: "#spectrum:colored_trees_generating_in",
    features: `${TREE_NS}:colored_tree_patch`,
    step: "vegetal_decoration",
});

writeJson(join(outRoot, "data", TREE_NS, "neoforge", "biome_modifier", "remove_vanilla_colored_trees.json"), {
    type: "neoforge:remove_features",
    biomes: "#spectrum:colored_trees_generating_in",
    features: "spectrum:colored_tree_patch",
    steps: ["vegetal_decoration"],
});

writeJson(join(outRoot, "trees", TREE_NS, "world_gen", "feature_cancellers.json"), [
    {
        select: { tag: "#spectrum:colored_trees_generating_in" },
        cancellers: {
            type: "tree",
            namespaces: ["spectrum"],
        },
    },
]);

writeJson(join(outRoot, "pack.mcmeta"), {
    pack: {
        description: "Dynamic Trees tree pack for Spectrum colored trees",
        pack_format: 34,
    },
});

writeRevelationCloaks();
writeDynamictreesBranchTags();

if (existsSync(bundledAssets)) {
    cpSync(bundledAssets, outRoot, { recursive: true });
}

extractSeedTextures();

console.log(`Generated resources in ${outRoot}`);
