package cn.czyx007.smartcreeper;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public class Config {
    private static Configuration config;

    // 苦力怕配置
    public static String[] TARGET_BLOCKS;
    public static String[] WILDCARD_MODS;
    public static boolean TARGET_ALL_CONTAINERS;
    public static boolean FEARLESS_OF_CATS;
    public static int NORMAL_SEARCH_RANGE;
    public static int CHARGED_SEARCH_RANGE;

    // 骷髅配置
    public static boolean SKELETON_TARGET_CONTAINERS;
    public static int SKELETON_SEARCH_RANGE;
    public static int SKELETON_SHOOT_COOLDOWN;

    // 末影人配置
    public static boolean ENDERMAN_CARRY_CONTAINERS;
    public static int ENDERMAN_SEARCH_RANGE;
    public static int ENDERMAN_PLACE_MIN_DISTANCE;

    // 僵尸配置
    public static boolean ZOMBIE_BREAK_CONTAINERS;
    public static int ZOMBIE_SEARCH_RANGE;
    public static int ZOMBIE_BREAK_TIME;

    // 容器击中配置
    public static int CONTAINER_REQUIRED_HITS;
    public static int CONTAINER_HIT_EXPIRY_TIME;

    public static void init(File configFile) {
        config = new Configuration(configFile);

        try {
            config.load();

            // 苦力怕配置
            String[] defaultTargetBlocks = {
                    "minecraft:chest",
                    "minecraft:trapped_chest",
                    "minecraft:furnace",
                    "minecraft:dispenser",
                    "minecraft:dropper",
                    "minecraft:hopper",
                    "minecraft:brewing_stand",
                    "minecraft:shulker_box"
            };

            TARGET_BLOCKS = config.getStringList("targetBlocks", "Creeper",
                    defaultTargetBlocks,
                    "List of specific blocks that creepers will prioritize exploding");

            String[] defaultWildcardMods = {
                    "thermalexpansion",
                    "enderio",
                    "mekanism",
                    "appliedenergistics2"
            };

            WILDCARD_MODS = config.getStringList("wildcardMods", "Creeper",
                    defaultWildcardMods,
                    "List of mod IDs whose ALL container blocks will be targeted");

            TARGET_ALL_CONTAINERS = config.getBoolean("targetAllContainers", "Creeper",
                    false,
                    "If true, creepers will target ALL container blocks from ANY mod");

            FEARLESS_OF_CATS = config.getBoolean("fearlessOfCats", "Creeper",
                    true,
                    "If true, creepers will not flee from cats and become charged when near them");

            NORMAL_SEARCH_RANGE = config.getInt("normalSearchRange", "Creeper",
                    16, 8, 32,
                    "Search range for normal creepers");

            CHARGED_SEARCH_RANGE = config.getInt("chargedSearchRange", "Creeper",
                    24, 8, 48,
                    "Search range for charged creepers");

            // 骷髅配置
            SKELETON_TARGET_CONTAINERS = config.getBoolean("skeletonTargetContainers", "Skeleton",
                    true,
                    "If true, skeletons will shoot arrows at container blocks");

            SKELETON_SEARCH_RANGE = config.getInt("skeletonSearchRange", "Skeleton",
                    16, 8, 32,
                    "Search range for skeletons to find container targets");

            SKELETON_SHOOT_COOLDOWN = config.getInt("skeletonShootCooldown", "Skeleton",
                    20, 10, 200,
                    "Cooldown between skeleton shots in ticks (20 ticks = 1 second)");

            // 末影人配置
            ENDERMAN_CARRY_CONTAINERS = config.getBoolean("endermanCarryContainers", "Enderman",
                    true,
                    "If true, endermen will pick up and relocate container blocks");

            ENDERMAN_SEARCH_RANGE = config.getInt("endermanSearchRange", "Enderman",
                    8, 4, 16,
                    "Search range for endermen to find container targets");

            ENDERMAN_PLACE_MIN_DISTANCE = config.getInt("endermanPlaceMinDistance", "Enderman",
                    16, 8, 32,
                    "Minimum distance from original position to place carried containers");

            // 僵尸配置
            ZOMBIE_BREAK_CONTAINERS = config.getBoolean("zombieBreakContainers", "Zombie",
                    true,
                    "If true, zombies will break container blocks like doors");

            ZOMBIE_SEARCH_RANGE = config.getInt("zombieSearchRange", "Zombie",
                    12, 6, 24,
                    "Search range for zombies to find container targets");

            ZOMBIE_BREAK_TIME = config.getInt("zombieBreakTime", "Zombie",
                    100, 40, 300,
                    "Time in ticks for zombies to break containers (20 ticks = 1 second)");

            // 容器击中配置
            CONTAINER_REQUIRED_HITS = config.getInt("containerRequiredHits", "Container Hits",
                    3, 1, 10,
                    "Number of arrow hits required to explode a container");

            CONTAINER_HIT_EXPIRY_TIME = config.getInt("containerHitExpiryTime", "Container Hits",
                    6000, 1200, 24000,
                    "Time in ticks for container hit records to expire (20 ticks = 1 second)");

        } catch (Exception e) {
            SmartCreeper.LOGGER.error("Problem loading config file!", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }
}