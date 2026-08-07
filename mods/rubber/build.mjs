import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
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

function writeJson(path, data) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function writeText(path, text) {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, text.endsWith("\n") ? text : text + "\n", "utf8");
}

function cloneLoot(srcRelative, destRelative, replacements) {
    let text = readFileSync(join(dtLootBase, srcRelative), "utf8");
    for (const [from, to] of replacements) {
        text = text.split(from).join(to);
    }
    writeText(join(outRoot, "data", MOD_ID, "loot_table", destRelative), text);
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

function rubberLightSpliceOnly(rubberWeight = 2, poolWeight = 48) {
    return rubberSpliceOnly(rubberWeight, poolWeight);
}

// Peripheral modded tropical biomes without a dedicated DT addon entry.
function rubberModdedJungleApply(overrides = {}, rubberWeight = 2) {
    const baseChance = overrides.chance ?? 0.14;
    const baseDensity = (overrides.density ?? [0.75])[0];
    const { chance, density, ...rest } = overrides;
    return rubberJungleApply(
        {
            chance: baseChance,
            density: [baseDensity],
            ...rest,
        },
        rubberWeight,
    );
}

function writeTreePack() {
    const treeRoot = join(outRoot, "trees", MOD_ID);

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
        primitive_leaves: "minecraft:jungle_leaves",
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

    writeJson(join(treeRoot, "world_gen", "default.json"), [
        {
            select: {
                name: "minecraft:jungle",
            },
            apply: rubberJungleApply({
                chance: 0.24,
                density: [1.1],
            }),
        },
        {
            select: {
                name: "minecraft:sparse_jungle",
            },
            apply: rubberJungleApply({
                chance: 0.2,
                density: [0.95],
            }),
        },
        {
            select: {
                name: "minecraft:bamboo_jungle",
            },
            apply: rubberJungleApply({
                chance: 0.2,
                density: [0.95],
            }),
        },
        {
            select: {
                name: "minecraft:swamp",
            },
            apply: rubberWetlandApply(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:rainforest",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:sparse_rainforest",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:marsh",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:eucalyptus_forest",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:bamboo_forest",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:tropics",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:bayou",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "regions_unexplored",
            select: {
                name: "regions_unexplored:old_growth_bayou",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:tropical_woods",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:tropical_basin",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:sparse_tropical_woods",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:marsh",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:bamboo_wetlands",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "natures_spirit",
            select: {
                name: "natures_spirit:tropical_shores",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomesoplenty",
            select: {
                name: "biomesoplenty:.*",
                tag: "#minecraft:is_jungle",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "biomesoplenty",
            select: {
                name: "biomesoplenty:tropics",
            },
            apply: rubberLightSpliceOnly(),
        },
        {
            only_if_loaded: "biomesoplenty",
            select: {
                name: "biomesoplenty:marsh",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomesoplenty",
            select: {
                name: "biomesoplenty:wetland",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomesoplenty",
            select: {
                name: "biomesoplenty:bayou",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomeswevegone",
            select: {
                name: "biomeswevegone:.*",
                tag: "#minecraft:is_jungle",
            },
            apply: rubberSpliceOnly(),
        },
        {
            only_if_loaded: "biomeswevegone",
            select: {
                name: "biomeswevegone:bayou",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomeswevegone",
            select: {
                name: "biomeswevegone:cypress_swamplands",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomeswevegone",
            select: {
                name: "biomeswevegone:cypress_wetlands",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
        {
            only_if_loaded: "biomeswevegone",
            select: {
                name: "biomeswevegone:white_mangrove_marshes",
            },
            apply: rubberLightSpliceOnly(1, 49),
        },
    ]);
}

function writeBranchTags() {
    const branchBlocks = [`${MOD_ID}:${SPECIES}_branch`, `${MOD_ID}:stripped_${SPECIES}_branch`];
    const tagRoot = join(outRoot, "data", "dynamictrees", "tags");

    for (const [subdir, fileName] of [
        ["block", "branches_that_burn.json"],
        ["item", "branches_that_burn.json"],
    ]) {
        writeJson(join(tagRoot, subdir, fileName), { values: branchBlocks });
    }

    writeJson(join(tagRoot, "block", "stripped_branches_that_burn.json"), {
        values: [`${MOD_ID}:stripped_${SPECIES}_branch`],
    });
}

function writeLootTables() {
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

function generateDerivedTextures() {
    const script = join(__dirname, "scripts", "generate_wood_textures.py");
    if (!existsSync(script)) {
        return;
    }

    const result = spawnSync("python", [script], {
        cwd: __dirname,
        encoding: "utf8",
    });

    if (result.status !== 0) {
        throw new Error(result.stderr || result.stdout || "generate_wood_textures.py failed");
    }
}

function generateLatexItemTextures() {
    const script = join(__dirname, "scripts", "generate_latex_item_textures.py");
    if (!existsSync(script)) {
        return;
    }

    const result = spawnSync("python", [script], {
        cwd: __dirname,
        encoding: "utf8",
    });

    if (result.status !== 0) {
        throw new Error(result.stderr || result.stdout || "generate_latex_item_textures.py failed");
    }
}

function writeTextures() {
    generateDerivedTextures();
    generateLatexItemTextures();
    const texturesRoot = join(outRoot, "assets", MOD_ID, "textures");
    cpSync(join(bundledAssets, "textures"), texturesRoot, { recursive: true });

    for (const srcName of ["_src_birch_planks.png", "_src_stripped_jungle_log.png"]) {
        const path = join(texturesRoot, "block", srcName);
        if (existsSync(path)) {
            rmSync(path, { force: true });
        }
    }
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
    const assetsRoot = join(outRoot, "assets", MOD_ID);

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
        variants: {
            "": {
                model: `${MOD_ID}:block/rubber_planks`,
            },
        },
    });

    writeLogModels(assetsRoot, "rubber_log", "rubber_log");
    writeLogModels(assetsRoot, "stripped_rubber_log", "rubber_stripped_log");
    writeWoodBlockModel(assetsRoot, "stripped_rubber_wood", "rubber_stripped_log");

    writeJson(join(assetsRoot, "models", "block", "rubber_planks.json"), {
        parent: "minecraft:block/cube_all",
        textures: {
            all: `${MOD_ID}:block/rubber_planks`,
        },
    });

    for (const itemName of ["rubber_log", "stripped_rubber_log", "stripped_rubber_wood", "rubber_planks"]) {
        writeJson(join(assetsRoot, "models", "item", `${itemName}.json`), {
            parent: `${MOD_ID}:block/${itemName}`,
        });
    }
}

function writeWoodLootTables() {
    for (const blockName of ["rubber_log", "stripped_rubber_log", "stripped_rubber_wood", "rubber_planks"]) {
        writeJson(join(outRoot, "data", MOD_ID, "loot_table", "blocks", `${blockName}.json`), {
            type: "minecraft:block",
            pools: [
                {
                    bonus_rolls: 0.0,
                    conditions: [
                        {
                            condition: "minecraft:survives_explosion",
                        },
                    ],
                    entries: [
                        {
                            type: "minecraft:item",
                            name: `${MOD_ID}:${blockName}`,
                        },
                    ],
                    rolls: 1.0,
                },
            ],
            random_sequence: `${MOD_ID}:blocks/${blockName}`,
        });
    }
}

function writeWoodTags() {
    const logItems = [`${MOD_ID}:rubber_log`, `${MOD_ID}:stripped_rubber_log`];
    const logBlocks = logItems.concat([`${MOD_ID}:stripped_rubber_wood`]);
    const planks = [`${MOD_ID}:rubber_planks`];

    writeJson(join(outRoot, "data", MOD_ID, "tags", "item", "rubber_logs.json"), {
        values: logItems,
    });

    writeJson(join(outRoot, "data", MOD_ID, "tags", "block", "rubber_logs.json"), {
        values: logBlocks,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "block", "logs.json"), {
        values: logBlocks,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "block", "logs_that_burn.json"), {
        values: logBlocks,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "block", "mineable", "axe.json"), {
        values: logBlocks.concat(planks),
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "block", "planks.json"), {
        values: planks,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "item", "logs.json"), {
        values: logItems,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "item", "logs_that_burn.json"), {
        values: logItems,
    });

    writeJson(join(outRoot, "data", "minecraft", "tags", "item", "planks.json"), {
        values: planks,
    });

    writeJson(join(outRoot, "data", "c", "tags", "block", "logs.json"), {
        values: [`${MOD_ID}:rubber_log`],
    });

    writeJson(join(outRoot, "data", "c", "tags", "item", "logs.json"), {
        values: [`${MOD_ID}:rubber_log`],
    });

    writeJson(join(outRoot, "data", "c", "tags", "block", "stripped_logs.json"), {
        values: [`${MOD_ID}:stripped_rubber_log`],
    });

    writeJson(join(outRoot, "data", "c", "tags", "item", "stripped_logs.json"), {
        values: [`${MOD_ID}:stripped_rubber_log`],
    });

    writeJson(join(outRoot, "data", "c", "tags", "block", "planks.json"), {
        values: planks,
    });

    writeJson(join(outRoot, "data", "c", "tags", "item", "planks.json"), {
        values: planks,
    });
}

function writeWoodRecipes() {
    writeJson(join(outRoot, "data", MOD_ID, "recipe", "rubber_planks.json"), {
        type: "minecraft:crafting_shapeless",
        category: "building",
        group: "planks",
        ingredients: [{ tag: `${MOD_ID}:rubber_logs` }],
        result: {
            count: 4,
            id: `${MOD_ID}:rubber_planks`,
        },
    });

    writeJson(join(outRoot, "data", MOD_ID, "recipe", "stripped_rubber_wood.json"), {
        type: "minecraft:crafting_shaped",
        category: "building",
        group: "bark",
        key: {
            "#": { item: `${MOD_ID}:stripped_rubber_log` },
        },
        pattern: ["##", "##"],
        result: {
            count: 3,
            id: `${MOD_ID}:stripped_rubber_wood`,
        },
    });
}

function writeAssets() {
    const assetsRoot = join(outRoot, "assets", MOD_ID);

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
    const assetsRoot = join(outRoot, "assets", MOD_ID);
    const itemModels = join(assetsRoot, "models", "item");

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
    writeJson(join(outRoot, "data", "c", "tags", "item", "rubber.json"), {
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

    const recipeRoot = join(outRoot, "data", MOD_ID, "recipe");
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

    const electroRoot = join(outRoot, "data", "electroenergetics", "recipe", "crafting");
    const electroOverrides = [
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
    ];

    for (const [relativePath, recipe] of electroOverrides) {
        writeJson(join(electroRoot, relativePath), {
            ...electroLoaded,
            ...recipe,
        });
    }

    const offroadRoot = join(outRoot, "data", "offroad", "recipe");
    writeJson(join(offroadRoot, "monstrous_tire.json"), {
        ...offroadLoaded,
        type: "minecraft:crafting_shaped",
        category: "misc",
        key: {
            K: rubberNine,
            S: { item: "create:shaft" },
        },
        pattern: [" K ", "KSK", " K "],
        result: { count: 1, id: "offroad:monstrous_tire" },
    });
    writeJson(join(offroadRoot, "small_tire.json"), {
        ...offroadLoaded,
        type: "minecraft:crafting_shapeless",
        category: "misc",
        ingredients: [{ item: "create:shaft" }, rubber],
        result: { count: 1, id: "offroad:small_tire" },
    });
    writeJson(join(offroadRoot, "tire.json"), {
        ...offroadLoaded,
        type: "minecraft:crafting_shaped",
        category: "misc",
        key: {
            K: rubber,
            S: { item: "create:shaft" },
        },
        pattern: [" K ", "KSK", " K "],
        result: { count: 1, id: "offroad:tire" },
    });

    const bigtiresRoot = join(outRoot, "data", "bigtires", "recipe");
    const bigtiresOverrides = [
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
    ];

    for (const [relativePath, recipe] of bigtiresOverrides) {
        writeJson(join(bigtiresRoot, relativePath), {
            ...bigtiresLoaded,
            ...recipe,
        });
    }

    writePetrolspartsOverrides();
}

function writePetrolspartsOverrides() {
    const rubber = { tag: "c:rubber" };

    writeJson(join(outRoot, "data", "petrolsparts", "recipe", "crafting", "pneumatic_tube.json"), {
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
    const createRecipeRoot = join(outRoot, "data", "create", "recipe", "crafting");
    const createLoaded = { "neoforge:conditions": [modLoadedCondition("create")] };
    const rubber = { tag: "c:rubber" };
    const rubberNine = { tag: "c:rubber", count: 9 };

    const overrides = [
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
    ];

    for (const [relativePath, recipe] of overrides) {
        writeJson(join(createRecipeRoot, relativePath), {
            ...createLoaded,
            ...recipe,
        });
    }
}

function writeLang() {
    writeJson(join(outRoot, "assets", MOD_ID, "lang", "en_us.json"), {
        [`block.${MOD_ID}.${SPECIES}_branch`]: "Rubber Branch",
        [`block.${MOD_ID}.stripped_${SPECIES}_branch`]: "Stripped Rubber Branch",
        [`block.${MOD_ID}.rubber_log`]: "Rubber Log",
        [`block.${MOD_ID}.stripped_rubber_log`]: "Stripped Rubber Log",
        [`block.${MOD_ID}.stripped_rubber_wood`]: "Stripped Rubber Wood",
        [`block.${MOD_ID}.rubber_planks`]: "Rubber Planks",
        [`block.${MOD_ID}.${SPECIES}_leaves`]: "Rubber Leaves",
        [`block.${MOD_ID}.${SPECIES}_sapling`]: "Rubber Sapling",
        [`item.${MOD_ID}.${SPECIES}_seed`]: "Rubber Seed",
        [`item.${MOD_ID}.raw_latex`]: "Raw Latex",
        [`item.${MOD_ID}.coagulated_latex`]: "Coagulated Latex",
        [`item.${MOD_ID}.rubber_ball`]: "Rubber Ball",
        [`item.${MOD_ID}.rubber_sheet`]: "Rubber Sheet",
        [`species.${MOD_ID}.${SPECIES}`]: "Rubber Tree",
    });
}

function writePackMeta() {
    writeJson(join(outRoot, "pack.mcmeta"), {
        pack: {
            description: "SKCraft Dynamic Trees rubber species",
            pack_format: 34,
        },
    });
}

rmSync(outRoot, { recursive: true, force: true });
writeTreePack();
writeTextures();
writeBranchTags();
writeLootTables();
writeWoodAssets();
writeWoodLootTables();
writeWoodTags();
writeWoodRecipes();
writeRubberItemAssets();
writeRubberTagsAndRecipes();
writeAssets();
writeLang();
writePackMeta();

console.log("Generated dtrubber resources.");
