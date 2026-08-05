package com.tomkestookit.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 物流终端一组：pairId 唯一，可以有 N 个 endpoint（不再限制 2 端）。
 *
 * 向后兼容：旧 JSON 里有 a/b 字段的会在 load 时迁移到 endpoints 列表。
 */
public final class LogisticsPair {
    public String pairId;
    /** 旧数据兼容字段：load 后会迁移到 endpoints，运行时不再使用。*/
    public Endpoint a;
    public Endpoint b;
    /** 多端列表。新数据和迁移后的数据都用这个。*/
    public List<Endpoint> endpoints = new ArrayList<>();

    public LogisticsPair() {}

    public LogisticsPair(String pairId) {
        this.pairId = pairId;
    }

    /** 迁移旧 a/b 字段到 endpoints 列表（load 后调用一次）。*/
    public void migrateIfNeeded() {
        if (a != null) {
            if (!containsPos(a)) endpoints.add(a);
            a = null;
        }
        if (b != null) {
            if (!containsPos(b)) endpoints.add(b);
            b = null;
        }
    }

    public boolean isComplete() {
        return endpoints.size() >= 2;
    }

    /** 获取除 self 之外的所有端。*/
    public List<Endpoint> others(Endpoint self) {
        List<Endpoint> out = new ArrayList<>();
        for (Endpoint e : endpoints) {
            if (!e.equalsTo(self)) out.add(e);
        }
        return out;
    }

    /** 按位置找端点。*/
    public Endpoint endpointAt(String dim, int x, int y, int z) {
        for (Endpoint e : endpoints) {
            if (e.dim != null && e.dim.equals(dim) && e.x == x && e.y == y && e.z == z) return e;
        }
        return null;
    }

    public boolean containsPos(Endpoint check) {
        for (Endpoint e : endpoints) {
            if (e.equalsTo(check)) return true;
        }
        return false;
    }

    /** 为了兼容旧代码中 other(self) 的调用：返回第一个不等于 self 的端点。*/
    public Endpoint other(Endpoint self) {
        for (Endpoint e : endpoints) {
            if (!e.equalsTo(self)) return e;
        }
        return null;
    }

    public static final class Endpoint {
        public String dim;
        public int x, y, z;
        public String markerUuid; // 可为 null

        public Endpoint() {}

        public Endpoint(String dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean equalsTo(Endpoint o) {
            return o != null && x == o.x && y == o.y && z == o.z
                && Objects.equals(dim, o.dim);
        }
    }
}
