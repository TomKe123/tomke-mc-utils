package com.starterkit;

import com.starterkit.landmark.LandmarkCommand;
import com.starterkit.landmark.LandmarkStore;
import com.starterkit.menu.MenuClock;
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

public class StarterKitMod implements ModInitializer {

    public static final String MOD_ID = "starterkit";
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
        MenuClock.register();
        LandmarkCommand.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            KitTracker.load(server);
            LandmarkStore.load(server);
            LOGGER.info("StarterKit loaded.");
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
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/starterkit give"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                )
            );
        player.sendMessage(message, false);
    }
}
