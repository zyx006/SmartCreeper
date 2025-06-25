package cn.czyx007.smartcreeper;

import cn.czyx007.smartcreeper.handler.CreeperEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class SmartCreeper {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // 初始化配置
        Config.init(event.getSuggestedConfigurationFile());

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new CreeperEventHandler());

        LOGGER.info("SmartCreeper initialized successfully!");
    }
}