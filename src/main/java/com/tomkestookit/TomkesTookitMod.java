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
        KitCommand.register();
        AntigravBootsHandler.register();
        TpPermission.register();
        SleepPercentage.register();
        FlyCommand.register();
        EntityCaptureHandler.register();
        CarpetPermission.register();
        KillPermission.register();
        VeinMineCommand.register();
        VeinMineHandler.register();
        MenuClock.register();
        LandmarkCommand.register();
        BackCommand.register();
        Sidebar.register();
        TabHealth.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            KitTracker.load(server);
            LandmarkStore.load(server);
            VeinMineTracker.load(server);
            DeathLocationTracker.load(server);
            EntityCaptureTracker.load(server);
            LOGGER.info("TomkesTookit loaded.");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendKitMessage(handler.player);
        });
    }

    public static void sendKitMessage(ServerPlayerEntity player) {
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
