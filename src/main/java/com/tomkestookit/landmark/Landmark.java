package com.tomkestookit.landmark;

/**
 * 地标数据。字段用 public 直接暴露给 Gson，减少样板；
 * dimension 是 registry key（如 "minecraft:overworld"）；
 * iconId 是物品 registry id（如 "minecraft:filled_map"），旧数据可为 null，走 fallback。
 */
public final class Landmark {
    public String name;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public String creator; // 添加者名字，用于 lore 展示
    public String iconId;  // 可空：null 走默认 filled_map

    public Landmark() {} // Gson

    public Landmark(String name, String dimension,
                    double x, double y, double z,
                    float yaw, float pitch, String creator) {
        this(name, dimension, x, y, z, yaw, pitch, creator, null);
    }

    public Landmark(String name, String dimension,
                    double x, double y, double z,
                    float yaw, float pitch, String creator, String iconId) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.creator = creator;
        this.iconId = iconId;
    }

    /** 图标 registry id；旧数据 null 时返回默认 filled_map。*/
    public String getIconId() {
        return (iconId == null || iconId.isEmpty()) ? "minecraft:filled_map" : iconId;
    }
}
