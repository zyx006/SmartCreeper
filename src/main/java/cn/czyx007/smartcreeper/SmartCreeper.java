package cn.czyx007.smartcreeper;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class SmartCreeper {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
    public static List<String> targetBlocks = new ArrayList<>();
    public static Set<String> wildcardMods = new HashSet<>(); // 通配符mod列表
    public static boolean targetAllContainers = false; // 是否针对所有容器类方块
    public static boolean fearlessOfCats = true;

    // 搜索范围配置
    public static int normalSearchRange = 16;  // 普通苦力怕搜索范围
    public static int chargedSearchRange = 24; // 高压苦力怕搜索范围

    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Tags.MOD_NAME);
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();

        // 默认目标方块列表
        String[] defaultTargets = {
                "minecraft:chest",
                "minecraft:trapped_chest",
                "minecraft:furnace",
                "minecraft:lit_furnace",
                "minecraft:dispenser",
                "minecraft:dropper",
                "minecraft:hopper",
                "minecraft:brewing_stand"
        };

        // 从配置读取目标方块
        String[] targets = config.getStringList("targetBlocks", Configuration.CATEGORY_GENERAL,
                defaultTargets, "List of specific blocks that creepers will prioritize exploding");

        for (String target : targets) {
            targetBlocks.add(target);
            LOGGER.info("Added target block: " + target);
        }

        // 读取通配符mod列表
        String[] defaultWildcardMods = {
                "appliedenergistics2",
                "industrialcraft2",
                "thermalexpansion",
                "enderio"
        };

        String[] wildcardModsArray = config.getStringList("wildcardMods", Configuration.CATEGORY_GENERAL,
                defaultWildcardMods, "List of mod IDs whose ALL container blocks will be targeted (e.g., 'appliedenergistics2' for all AE2 containers)");

        for (String modId : wildcardModsArray) {
            wildcardMods.add(modId.toLowerCase());
            LOGGER.info("Added wildcard mod: " + modId);
        }

        // 读取是否针对所有容器类方块
        targetAllContainers = config.getBoolean("targetAllContainers", Configuration.CATEGORY_GENERAL,
                false, "If true, creepers will target ALL container blocks from ANY mod");

        // 读取是否无视猫的配置
        fearlessOfCats = config.getBoolean("fearlessOfCats", Configuration.CATEGORY_GENERAL,
                true, "If true, creepers will not flee from cats and become charged when near them");

        // 读取搜索范围配置
        normalSearchRange = config.getInt("normalSearchRange", Configuration.CATEGORY_GENERAL,
                16, 8, 32, "Search range for normal creepers");

        chargedSearchRange = config.getInt("chargedSearchRange", Configuration.CATEGORY_GENERAL,
                24, 8, 48, "Search range for charged creepers");

        if (config.hasChanged()) {
            config.save();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 注册AI任务
        CreeperAIModifier.register();
    }
}