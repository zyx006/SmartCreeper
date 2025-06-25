package cn.czyx007.smartcreeper;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // 苦力怕配置
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TARGET_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WILDCARD_MODS;
    public static final ModConfigSpec.BooleanValue TARGET_ALL_CONTAINERS;
    public static final ModConfigSpec.BooleanValue FEARLESS_OF_CATS;
    public static final ModConfigSpec.IntValue NORMAL_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue CHARGED_SEARCH_RANGE;

    // 骷髅配置
    public static final ModConfigSpec.BooleanValue SKELETON_TARGET_CONTAINERS;
    public static final ModConfigSpec.IntValue SKELETON_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue SKELETON_SHOOT_COOLDOWN;

    // 末影人配置
    public static final ModConfigSpec.BooleanValue ENDERMAN_CARRY_CONTAINERS;
    public static final ModConfigSpec.IntValue ENDERMAN_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue ENDERMAN_PLACE_MIN_DISTANCE;

    // 僵尸配置
    public static final ModConfigSpec.BooleanValue ZOMBIE_BREAK_CONTAINERS;
    public static final ModConfigSpec.IntValue ZOMBIE_SEARCH_RANGE;
    public static final ModConfigSpec.IntValue ZOMBIE_BREAK_TIME;

    // 容器击中配置
    public static final ModConfigSpec.IntValue CONTAINER_REQUIRED_HITS;
    public static final ModConfigSpec.IntValue CONTAINER_HIT_EXPIRY_TIME;

    static {
        BUILDER.push("Creeper");

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
                                "ae2",
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

        BUILDER.push("Skeleton");

        SKELETON_TARGET_CONTAINERS = BUILDER
                .comment("If true, skeletons will shoot arrows at container blocks")
                .define("skeletonTargetContainers", true);

        SKELETON_SEARCH_RANGE = BUILDER
                .comment("Search range for skeletons to find container targets")
                .defineInRange("skeletonSearchRange", 16, 8, 32);

        SKELETON_SHOOT_COOLDOWN = BUILDER
                .comment("Cooldown between skeleton shots in ticks (20 ticks = 1 second)")
                .defineInRange("skeletonShootCooldown", 20, 10, 200);

        BUILDER.pop();

        BUILDER.push("Enderman");

        ENDERMAN_CARRY_CONTAINERS = BUILDER
                .comment("If true, endermen will pick up and relocate container blocks")
                .define("endermanCarryContainers", true);

        ENDERMAN_SEARCH_RANGE = BUILDER
                .comment("Search range for endermen to find container targets")
                .defineInRange("endermanSearchRange", 8, 4, 16);

        ENDERMAN_PLACE_MIN_DISTANCE = BUILDER
                .comment("Minimum distance from original position to place carried containers")
                .defineInRange("endermanPlaceMinDistance", 16, 8, 32);

        BUILDER.pop();

        BUILDER.push("Zombie");

        ZOMBIE_BREAK_CONTAINERS = BUILDER
                .comment("If true, zombies will break container blocks like doors")
                .define("zombieBreakContainers", true);

        ZOMBIE_SEARCH_RANGE = BUILDER
                .comment("Search range for zombies to find container targets")
                .defineInRange("zombieSearchRange", 12, 6, 24);

        ZOMBIE_BREAK_TIME = BUILDER
                .comment("Time in ticks for zombies to break containers (20 ticks = 1 second)")
                .defineInRange("zombieBreakTime", 100, 40, 300);

        BUILDER.pop();

        BUILDER.push("Container Hits");

        CONTAINER_REQUIRED_HITS = BUILDER
                .comment("Number of arrow hits required to explode a container")
                .defineInRange("containerRequiredHits", 3, 1, 10);

        CONTAINER_HIT_EXPIRY_TIME = BUILDER
                .comment("Time in ticks for container hit records to expire (20 ticks = 1 second)")
                .defineInRange("containerHitExpiryTime", 6000, 1200, 24000);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
