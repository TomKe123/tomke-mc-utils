package com.tomkestookit;

import com.tomkestookit.landmark.LandmarkCommand;
import com.tomkestookit.landmark.LandmarkStore;
import com.tomkestookit.menu.MenuClock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomkesTookitMod implements ModInitializer {

    public static final String MOD_ID = "tomkestookit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 给 admin 面板做管理员 kit 定制用；须在 KitCommand.register 之前调，
        // 保证 config 首次 load 时能拿到默认 kit 兜底。
        TomkesTookitConfig.registerDefaultKit(KitCommand::buildDefaultKit);

        // 特性从"开"翻到"关"时的清理回调（server 可能为 null，处理方各自判空）
        TomkesTookitConfig.registerTearDown("world.afk", AfkDisplay::flushAll);
        TomkesTookitConfig.registerTearDown("ui.sidebar", Sidebar::uninstall);
        TomkesTookitConfig.registerTearDown("cmd.fly", FlyCommand::disableAll);
        TomkesTookitConfig.registerTearDown("world.bot_anchor", s -> BotAnchorTracker.clearAll());

        KitCommand.register();
        AntigravBootsHandler.register();
        CommandPermission.register();
        SleepPercentage.register();
        FlyCommand.register();
        EntityCaptureHandler.register();
        CarpetPermission.register();
        KillPermission.register();
        VeinMineCommand.register();
        VeinMineHandler.register();
        com.tomkestookit.regionclearer.RegionClearerHandler.register();
        com.tomkestookit.regionfiller.RegionFillerHandler.register();
        SignCommandHandler.register();
        BotInteractHandler.register();
        BotAnchorHandler.register();
        com.tomkestookit.menu.BotMenuTracker.register();
        com.tomkestookit.logistics.LogisticsHandler.register();
        AutoCrafterHandler.register();
        ShulkerBoxHandler.register();
        MenuClock.register();
        LandmarkCommand.register();
        BackCommand.register();
        PotionCauldronHandler.register();
        PotionCauldronLabel.register();
        Sidebar.register();
        TabHealth.register();
        SpawnProtection.register();
        SugarTntHandler.register();
        AfkDisplay.register();
        PortableCommand.register();
        PerPlayerSpawnCap.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TomkesTookitConfig.load(server);
            KitTracker.load(server);
            LandmarkStore.load(server);
            VeinMineTracker.load(server);
            VeinMineBlockConfig.load(server);
            DeathLocationTracker.load(server);
            EntityCaptureTracker.load(server);
            PotionCauldronTracker.load(server);
            AutoCrafterTracker.load(server);
            PotionCauldronLabel.cleanupLoadedOrphans(server);
            com.tomkestookit.logistics.LogisticsTracker.load(server);
            // 启动时清一次孤儿：只对已加载 chunk 生效；未加载 chunk 里的 marker
            // 会在 chunk 加载后由 tick sweep 兜底处理。
            int cleaned = com.tomkestookit.logistics.LogisticsHandler.sweepOrphans(server);
            int cleanedCrafters = AutoCrafterHandler.sweepOrphans(server);
            if (cleaned > 0) {
                LOGGER.info("[TomkesTookit] 启动时清理 {} 个孤儿物流终端盔甲架。", cleaned);
            }
            if (cleanedCrafters > 0) {
                LOGGER.info("[TomkesTookit] 启动时清理 {} 个孤儿自动合成器盔甲架。", cleanedCrafters);
            }
            LOGGER.info("TomkesTookit loaded.");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendKitMessage(handler.player));
    }

    public static void sendKitMessage(ServerPlayerEntity player) {
        if (!TomkesTookitConfig.enabled("misc.kit_on_join")) return;
        Text hover = Text.literal("点击领取 Kit").formatted(Formatting.YELLOW);
        Text message = Text.empty()
            .append(Text.literal("[Kit] ").formatted(Formatting.GOLD, Formatting.BOLD))
            .append(Text.literal("点击此处领取新手装备")
                .formatted(Formatting.GREEN)
                .styled(style -> style
                    .withUnderline(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tomkestookit give"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                )
            );
        player.sendMessage(message, false);
    }
}
