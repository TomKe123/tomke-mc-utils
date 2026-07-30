package com.tomkestookit.landmark;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * 地标传送。集中处理"维度可能已被卸载/移除"的失败路径。
 */
public final class LandmarkTeleport {

    private LandmarkTeleport() {}

    public static boolean execute(ServerPlayerEntity player, Landmark lm) {
        Identifier id = Identifier.tryParse(lm.dimension);
        if (id == null) {
            player.sendMessage(Text.literal("地标维度标识非法：" + lm.dimension).formatted(Formatting.RED), true);
            return false;
        }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        ServerWorld world = player.getServer().getWorld(key);
        if (world == null) {
            player.sendMessage(Text.literal("地标所在维度不存在：" + lm.dimension).formatted(Formatting.RED), true);
            return false;
        }
        com.tomkestookit.PlayerTeleport.teleport(player, world, lm.x, lm.y, lm.z, lm.yaw, lm.pitch);
        com.tomkestookit.SpawnProtection.grant(player, "地标：" + lm.name);
        player.sendMessage(Text.literal("已传送到 " + lm.name).formatted(Formatting.GREEN), true);
        return true;
    }
}
