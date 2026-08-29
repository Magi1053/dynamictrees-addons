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
    "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
    "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow",
];
const PIGMENTS = ["cyan", "magenta", "yellow", "white", "black"];
const WHITE_GROUP = new Set(["white", "light_gray", "gray"]);
const BLACK_GROUP = new Set(["black", "brown"]);

/** Spectrum pedestal pigment amounts (zeros filled) + tier per colored sapling. */
const PEDESTAL = {
    black: { tier: "advanced", black: 6 },
    blue: { tier: "basic", cyan: 3, magenta: 2, yellow: 1 },
    brown: { tier: "advanced", magenta: 1, yellow: 2, black: 3 },
    cyan: { tier: "basic", cyan: 6 },
    gray: { tier: "complex", white: 2, black: 4 },
    green: { tier: "basic", cyan: 2, magenta: 1, yellow: 3 },
    light_blue: { tier: "basic", cyan: 4, magenta: 2 },
    light_gray: { tier: "complex", white: 4, black: 2 },
    lime: { tier: "basic", cyan: 2, yellow: 4 },
    magenta: { tier: "basic", magenta: 6 },
    orange: { tier: "basic", magenta: 2, yellow: 4 },
    pink: { tier: "basic", magenta: 4, yellow: 2 },
    purple: { tier: "basic", cyan: 2, magenta: 3, yellow: 1 },
    red: { tier: "basic", magenta: 3, yellow: 3 },
    white: { tier: "complex", white: 6 },
    yellow: { tier: "basic", yellow: 6 },
};

function out(...parts) {
    return join(outRoot, ...parts);
}

function writeJson(path, data) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function writeTag(ns, kind, name, values, extra = {}) {
    writeJson(out("data", ns, "tags", kind, `${name}.json`), { ...extra, values });
}

function colorTitle(color) {
    return color.split("_").map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(" ");
}

function seed(color) {
    return `${TREE_NS}:${color}_seed`;
}

function saplingBlock(color) {
    return `${TREE_NS}:${color}_sapling`;
}

function pedestalColors(spec) {
    return Object.fromEntries(PIGMENTS.map((p) => [`spectrum:${p}`, spec[p] ?? 0]));
}

function survives() {
    return { condition: "minecraft:survives_explosion" };
}

function silkOrShears() {
    return {
        condition: "minecraft:any_of",
        terms: [
            { condition: "minecraft:match_tool", predicate: { items: "#c:tools/shear" } },
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
}

function itemDrop(name, conditions = [survives()]) {
    return {
        rolls: 1.0,
        bonus_rolls: 0.0,
        entries: [{ type: "minecraft:item", name }],
        conditions,
    };
}

function branchCloaks(color, stripped) {
    const from = stripped ? `${TREE_NS}:stripped_${color}_branch` : `${TREE_NS}:${color}_branch`;
    const to = stripped ? "dynamictrees:stripped_oak_branch" : "dynamictrees:oak_branch";
    const entries = {};
    for (let radius = 1; radius <= 8; radius++) {
        for (const waterlogged of [false, true]) {
            const props = `radius=${radius},waterlogged=${waterlogged}`;
            entries[`${from}[${props}]`] = `${to}[${props}]`;
        }
    }
    return entries;
}

function leavesCloaks(color) {
    const entries = {};
    for (let distance = 1; distance <= 7; distance++) {
        for (const persistent of [false, true]) {
            for (const waterlogged of [false, true]) {
                const props = `distance=${distance},persistent=${persistent},waterlogged=${waterlogged}`;
                entries[`${TREE_NS}:${color}_leaves[${props}]`] = `dynamictrees:oak_leaves[${props}]`;
            }
        }
    }
    return entries;
}

function treeBlockStateCloaks(color) {
    return {
        ...branchCloaks(color, false),
        ...branchCloaks(color, true),
        ...leavesCloaks(color),
        // DynamicTrees sapling blocks have no growth stage property.
        [`${TREE_NS}:${color}_sapling`]: "dynamictrees:oak_sapling",
    };
}

function writeRevelationCloaks() {
    const groups = {
        cmy: { blocks: {}, items: {}, blockAdv: "spectrum:milestones/reveal_colored_trees_cmy", itemAdv: "spectrum:milestones/reveal_colored_saplings_cmy" },
        white: { blocks: {}, items: {}, blockAdv: "spectrum:milestones/reveal_colored_trees_w" },
        black: { blocks: {}, items: {}, blockAdv: "spectrum:milestones/reveal_colored_trees_k" },
    };
    for (const color of COLORS) {
        const group = WHITE_GROUP.has(color) ? "white" : BLACK_GROUP.has(color) ? "black" : "cmy";
        Object.assign(groups[group].blocks, treeBlockStateCloaks(color));
        groups[group].items[seed(color)] = "minecraft:oak_sapling";
    }
    const dir = out("data", TREE_NS, "revelations");
    writeJson(join(dir, "cmy_tree_blocks.json"), { advancement: groups.cmy.blockAdv, block_states: groups.cmy.blocks });
    // CMY seeds use the sapling milestone (matches Spectrum ColoredTree.TreePart.SAPLING).
    writeJson(join(dir, "cmy_seed_items.json"), { advancement: groups.cmy.itemAdv, items: groups.cmy.items });
    writeJson(join(dir, "grayscale_tree_blocks_w.json"), {
        advancement: groups.white.blockAdv,
        block_states: groups.white.blocks,
        items: groups.white.items,
    });
    writeJson(join(dir, "grayscale_tree_blocks_k.json"), {
        advancement: groups.black.blockAdv,
        block_states: groups.black.blocks,
        items: groups.black.items,
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
        .replaceAll("dynamictrees:birch_seed", seed(color));
}

/** DT only datagens vanilla families into #dynamictrees:branches_that_burn; addon packs must append explicitly. */
function writeDynamictreesBranchTags() {
    const branches = COLORS.map((c) => `${TREE_NS}:${c}_branch`);
    const stripped = COLORS.map((c) => `${TREE_NS}:stripped_${c}_branch`);
    const tagRoot = ["data", "dynamictrees", "tags"];
    writeJson(out(...tagRoot, "block", "branches_that_burn.json"), { values: branches });
    writeJson(out(...tagRoot, "block", "stripped_branches_that_burn.json"), { values: stripped });
    writeJson(out(...tagRoot, "item", "branches_that_burn.json"), { values: branches });
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
        const dest = out("data", TREE_NS, "loot_table", toRel);
        mkdirSync(dirname(dest), { recursive: true });
        writeFileSync(dest, replaceLootTemplate(readFileSync(join(dtLootBase, fromRel), "utf8"), color), "utf8");
    }
}

function writeSaplingLoot(color) {
    const loot = out("data", "spectrum", "loot_table", "blocks");
    writeJson(join(loot, `${color}_sapling.json`), { type: "minecraft:block", pools: [itemDrop(seed(color))] });
    writeJson(join(loot, `potted_${color}_sapling.json`), {
        type: "minecraft:block",
        pools: [itemDrop("minecraft:flower_pot"), itemDrop(seed(color))],
    });
}

function writeColoredLeavesLoot(color) {
    writeJson(out("data", "spectrum", "loot_table", "blocks", `${color}_leaves.json`), {
        type: "minecraft:block",
        pools: [
            {
                rolls: 1,
                bonus_rolls: 0,
                entries: [
                    {
                        type: "minecraft:alternatives",
                        children: [
                            { type: "minecraft:item", name: `spectrum:${color}_leaves`, conditions: [silkOrShears()] },
                            {
                                type: "minecraft:item",
                                name: seed(color),
                                conditions: [
                                    {
                                        condition: "minecraft:any_of",
                                        terms: [
                                            { condition: "minecraft:table_bonus", enchantment: "minecraft:fortune", chances: [0.0025, 0.005, 0.0075, 0.01] },
                                            { condition: "minecraft:table_bonus", enchantment: "spectrum:resonance", chances: [0, 0.15] },
                                        ],
                                    },
                                    survives(),
                                ],
                            },
                        ],
                    },
                ],
            },
            {
                rolls: 1,
                bonus_rolls: 0,
                entries: [
                    {
                        type: "minecraft:item",
                        name: `spectrum:${color}_pigment`,
                        conditions: [
                            survives(),
                            { condition: "minecraft:table_bonus", enchantment: "minecraft:fortune", chances: [0.2, 0.25, 0.3, 0.35, 0.4] },
                        ],
                    },
                ],
                conditions: [{ condition: "minecraft:inverted", term: silkOrShears() }],
            },
        ],
    });
}

function writeColorRecipes(color) {
    const spec = PEDESTAL[color];
    writeJson(out("data", "spectrum", "recipe", "pedestal", "tier1", "saplings", `${color}.json`), {
        type: "spectrum:pedestal",
        group: "colored_saplings",
        time: 160,
        tier: spec.tier,
        colors: pedestalColors(spec),
        experience: 1.0,
        pattern: ["DDD", "VSV", "DDD"],
        key: { S: "#minecraft:saplings", V: "spectrum:vegetal", D: `minecraft:${color}_dye` },
        result: { id: seed(color), count: 1 },
        required_advancement: `spectrum:unlocks/colored_saplings/${color}_sapling`,
    });
    writeJson(out("data", "spectrum", "recipe", "mod_integration", "create", "crushing", "leaves", `${color}.json`), {
        type: "create:crushing",
        ingredients: [{ item: `spectrum:${color}_leaves` }],
        results: [
            { id: `spectrum:${color}_pigment`, count: 1, chance: 1.0 },
            { id: seed(color), count: 1, chance: 0.02 },
        ],
        processing_time: 450,
        "neoforge:conditions": [{ type: "neoforge:mod_loaded", modid: "create" }],
    });
    writeJson(out("data", "spectrum", "recipe", "mod_integration", "neepmeat", "advanced_crushing", "leaves", `${color}.json`), {
        type: "neepmeat:advanced_crushing",
        input: { resource: `spectrum:${color}_leaves`, amount: 1 },
        output: { resource: `spectrum:${color}_pigment`, amount: 1 },
        extra: { resource: seed(color), amount: 1, chance: 0.03 },
        experience: 0.7,
        processtime: 45,
        "neoforge:conditions": [{ type: "neoforge:mod_loaded", modid: "neepmeat" }],
    });
}

function writeColoredTreesGuidebook() {
    const book = "book.spectrum.guidebook.colored_trees";
    const pigmentAdv = { type: "modonomicon:advancement", advancement_id: "spectrum:collect_pigment" };
    writeJson(out("data", "spectrum", "modonomicon", "books", "guidebook", "entries", "general", "colored_trees.json"), {
        name: `${book}.name`,
        icon: { item: seed("red") },
        condition: { type: "modonomicon:advancement", advancement_id: "spectrum:collect_vegetal" },
        turnin: "spectrum:collect_pigment",
        category: "spectrum:general",
        hide_while_locked: true,
        parents: [
            { entry: "spectrum:general/color_mixing_cmy", line_reversed: true },
            { entry: "spectrum:general/gemstone_powder" },
            { entry: "spectrum:general/vegetal" },
        ],
        background_u_index: 0,
        background_v_index: 0,
        x: 2,
        y: -1,
        pages: [
            { type: "modonomicon:text", title: `${book}.name`, text: `${book}.page0.text` },
            { type: "modonomicon:text", title: `${book}.crafting_colored_saplings.title`, text: `${book}.page1.text` },
            ...COLORS.map((color) => ({
                type: "spectrum:pedestal_crafting",
                title: `item.${TREE_NS}.${color}_seed`,
                recipe_id: `spectrum:pedestal/tier1/saplings/${color}`,
            })),
            {
                type: "modonomicon:image",
                use_legacy_rendering: true,
                title: `${book}.natural_generation.title`,
                condition: { type: "modonomicon:advancement", advancement_id: "spectrum:craft_colored_sapling" },
                images: ["spectrum:textures/gui/guidebook/colored_trees.png"],
                text: `${book}.natural_generation.text`,
            },
            {
                type: "modonomicon:crafting_recipe",
                condition: pigmentAdv,
                anchor: "colored_wood",
                title: `${book}.colored_wood.title`,
                recipe_id_1: "spectrum:crafting_table/colored_wood/light_blue_planks",
                text: `${book}.colored_wood.text`,
            },
            {
                type: "spectrum:anvil_crushing",
                condition: pigmentAdv,
                title: `${book}.leaf_crushing.title`,
                recipe_id: "spectrum:anvil_crushing/colored_leaves/light_blue",
                text: `${book}.leaf_crushing.text`,
            },
        ],
    });
}

/** Pedestal / crushing / leaf loot / tags: Spectrum saplings become DT seeds 1:1. */
function writePrimitiveSaplingReplacement() {
    const seeds = COLORS.map(seed);
    const saplings = COLORS.map(saplingBlock);
    const append = { replace: false };
    writeTag("spectrum", "item", "colored_saplings", seeds, append);
    writeTag("spectrum", "block", "colored_saplings", saplings, append);
    writeTag("spectrum", "block", "saplings", saplings, append);
    writeTag("minecraft", "item", "saplings", seeds, append);
    writeTag("minecraft", "block", "saplings", saplings, append);
    writeTag("moonlight", "item", "non_recolorable", seeds, append);
    writeTag("moonlight", "block", "non_recolorable", saplings, append);
    writeTag("supplementaries", "item", "non_cleanable", seeds, append);
    writeTag("supplementaries", "block", "non_cleanable", saplings, append);
    writeJson(out("data", "neoforge", "data_maps", "item", "compostables.json"), {
        values: Object.fromEntries(COLORS.map((c) => [seed(c), { chance: 0.3 }])),
    });
    writeJson(out("data", "spectrum", "ink_color_mapping", "item", "dtspectrum.json"),
        Object.fromEntries(COLORS.map((c) => [`spectrum:${c}`, [seed(c)]])));
    for (const color of COLORS) {
        writeColorRecipes(color);
        writeColoredLeavesLoot(color);
    }
    writeColoredTreesGuidebook();
}

function writeVariant(rel, model) {
    writeJson(out("assets", TREE_NS, ...rel.split("/")), { variants: { "": { model } } });
}

function writeBranchModel(file, bark, rings) {
    writeJson(out("assets", TREE_NS, "models", "block", file), {
        loader: "dynamictrees:branch",
        textures: { bark, rings },
    });
}

function writeTreeAssets(color) {
    writeJson(out("trees", TREE_NS, "families", `${color}.json`), {
        common_leaves: `${TREE_NS}:${color}`,
        common_species: `${TREE_NS}:${color}`,
        primitive_log: `spectrum:${color}_log`,
        primitive_stripped_log: `spectrum:stripped_${color}_log`,
        max_branch_radius: 8,
    });
    writeJson(out("trees", TREE_NS, "leaves_properties", `${color}.json`), {
        primitive_leaves: `spectrum:${color}_leaves`,
    });
    writeJson(out("trees", TREE_NS, "species", `${color}.json`), {
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
    cpSync(oakJoCode, out("trees", TREE_NS, "jo_codes", `${color}.txt`));

    writeVariant(`blockstates/${color}_branch.json`, `${TREE_NS}:block/${color}_branch`);
    writeVariant(`blockstates/stripped_${color}_branch.json`, `${TREE_NS}:block/stripped_${color}_branch`);
    writeVariant(`blockstates/${color}_leaves.json`, `spectrum:block/${color}_leaves`);
    writeVariant(`blockstates/${color}_sapling.json`, `${TREE_NS}:block/saplings/${color}`);
    writeBranchModel(`${color}_branch.json`, `spectrum:block/${color}_log`, `spectrum:block/${color}_log_top`);
    writeBranchModel(`stripped_${color}_branch.json`, `spectrum:block/stripped_${color}_log`, `spectrum:block/stripped_${color}_log_top`);
    writeJson(out("assets", TREE_NS, "models", "block", "saplings", `${color}.json`), {
        parent: "dynamictrees:block/smartmodel/sapling",
        textures: {
            particle: `spectrum:block/${color}_leaves`,
            log: `spectrum:block/${color}_log`,
            leaves: `spectrum:block/${color}_leaves`,
        },
    });
    writeJson(out("assets", TREE_NS, "models", "item", `${color}_seed.json`), {
        parent: "dynamictrees:item/standard_seed",
        textures: { layer0: `${TREE_NS}:item/${color}_seed` },
    });
    writeJson(out("assets", TREE_NS, "models", "item", `${color}_branch.json`), {
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
        const dest = out("assets", TREE_NS, "textures", "item", `${color}_seed.png`);
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

function writeWorldgen() {
    // DT species in Spectrum biomes are spawned only by ColoredTreePatchFeature (see Java worldgen),
    // not via splice_before on the global dynamic_tree populator.
    writeJson(out("trees", TREE_NS, "world_gen", "default.json"), []);
    writeJson(out("data", TREE_NS, "worldgen", "configured_feature", "colored_tree_patch.json"), {
        type: `${TREE_NS}:colored_tree_patch`,
        config: {},
    });
    writeJson(out("data", TREE_NS, "worldgen", "placed_feature", "colored_tree_patch.json"), {
        feature: `${TREE_NS}:colored_tree_patch`,
        placement: [
            { type: "minecraft:rarity_filter", chance: 75 },
            { type: "minecraft:in_square" },
            { type: "minecraft:heightmap", heightmap: "WORLD_SURFACE_WG" },
            { type: "minecraft:biome" },
        ],
    });
    writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", "colored_tree_patch.json"), {
        type: "neoforge:add_features",
        biomes: "#spectrum:colored_trees_generating_in",
        features: `${TREE_NS}:colored_tree_patch`,
        step: "vegetal_decoration",
    });
    writeJson(out("data", TREE_NS, "neoforge", "biome_modifier", "remove_vanilla_colored_trees.json"), {
        type: "neoforge:remove_features",
        biomes: "#spectrum:colored_trees_generating_in",
        features: "spectrum:colored_tree_patch",
        steps: ["vegetal_decoration"],
    });
    writeJson(out("trees", TREE_NS, "world_gen", "feature_cancellers.json"), [
        { select: { tag: "#spectrum:colored_trees_generating_in" }, cancellers: { type: "tree", namespaces: ["spectrum"] } },
    ]);
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
writeJson(out("assets", TREE_NS, "lang", "en_us.json"), lang);

for (const color of COLORS) {
    writeTreeAssets(color);
    copyLootFromBirch(color);
    writeSaplingLoot(color);
}

writeJson(out("pack.mcmeta"), {
    pack: { description: "Dynamic Trees tree pack for Spectrum colored trees", pack_format: 34 },
});

writeWorldgen();
writeRevelationCloaks();
writeDynamictreesBranchTags();
writePrimitiveSaplingReplacement();

if (existsSync(bundledAssets)) {
    cpSync(bundledAssets, outRoot, { recursive: true });
}

extractSeedTextures();

console.log(`Generated resources in ${outRoot}`);
