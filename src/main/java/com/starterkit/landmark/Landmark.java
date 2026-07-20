package com.starterkit.landmark;

/**
 * 地标数据。字段用 public 直接暴露给 Gson，减少样板；
 * dimension 是 registry key（如 "minecraft:overworld"）。
 */
public final class Landmark {
    public String name;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public String creator; // 添加者名字，用于 lore 展示

    public Landmark() {} // Gson

    public Landmark(String name, String dimension,
                    double x, double y, double z,
                    float yaw, float pitch, String creator) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.creator = creator;
    }
}
