## 智慧苦力怕 / Smart Creeper

For Minecraft 1.21.1 NeoForge (generated from [NeoForgeMDKs/MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle))

### 功能 / Features

- 修改苦力怕的AI逻辑，现在会优先攻击容器类方块（如箱子、机器，可配置）

  Modified creeper AI logic. Creepers now prioritize attacking container blocks (such as chests, machines, configurable)


- 遇见猫时不再会逃跑，而是转变为高压苦力怕（可配置）

  When encountering cats, creepers no longer flee but instead transform into charged creepers (configurable)


- 允许配置搜索范围、精确指定攻击某个方块、通配指定攻击某模组的所有容器类方块

  Allows configuration of search range, precise specification of attacking specific blocks, and wildcard specification to attack all container blocks from a specific mod


- 默认会攻击原版的箱子、熔炉等方块，以及ae2、热力、eio、mek模组的容器类方块，可在配置文件中修改

  By default, attacks vanilla container blocks such as chests and furnaces, as well as container blocks from mods like AE2, Thermal Expansion, EnderIO, and Mekanism. This can be modified in the configuration file

