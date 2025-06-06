package cn.czyx007.smartcreeper;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TARGET_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WILDCARD_MODS;
    public static final ModConfigSpec.BooleanValue TARGET_ALL_CONTAINERS;
    public static final ModConfigSpec.BooleanValue FEARLESS_OF_CATS;
    public static final ModConfigSpec.IntValue NORMAL_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue CHARGED_SEARCH_RANGE;

    static {
        BUILDER.push("General");

        TARGET_BLOCKS = BUILDER
                .comment("List of specific blocks that creepers will prioritize exploding")
                .defineList("targetBlocks",
                        List.of(
                                "minecraft:chest",
                                "minecraft:trapped_chest",
                                "minecraft:furnace",
                                "minecraft:blast_furnace",
                                "minecraft:smoker",
                                "minecraft:dispenser",
                                "minecraft:dropper",
                                "minecraft:hopper",
                                "minecraft:brewing_stand",
                                "minecraft:barrel",
                                "minecraft:shulker_box"
                        ),
                        obj -> obj instanceof String);

        WILDCARD_MODS = BUILDER
                .comment("List of mod IDs whose ALL container blocks will be targeted")
                .defineList("wildcardMods",
                        List.of(
                                "appliedenergistics2",
                                "thermal",
                                "enderio",
                                "mekanism"
                        ),
                        obj -> obj instanceof String);

        TARGET_ALL_CONTAINERS = BUILDER
                .comment("If true, creepers will target ALL container blocks from ANY mod")
                .define("targetAllContainers", false);

        FEARLESS_OF_CATS = BUILDER
                .comment("If true, creepers will not flee from cats and become charged when near them")
                .define("fearlessOfCats", true);

        NORMAL_SEARCH_RANGE = BUILDER
                .comment("Search range for normal creepers")
                .defineInRange("normalSearchRange", 16, 8, 32);

        CHARGED_SEARCH_RANGE = BUILDER
                .comment("Search range for charged creepers")
                .defineInRange("chargedSearchRange", 24, 8, 48);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
