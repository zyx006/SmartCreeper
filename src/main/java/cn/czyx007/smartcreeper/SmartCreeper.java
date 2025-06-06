package cn.czyx007.smartcreeper;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SmartCreeper.MODID)
public class SmartCreeper {
    public static final String MODID = "smartcreeper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmartCreeper(ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // 注册事件处理器
        NeoForge.EVENT_BUS.register(new CreeperEventHandler());
        LOGGER.info("SmartCreeper initialized successfully!");
    }
}
