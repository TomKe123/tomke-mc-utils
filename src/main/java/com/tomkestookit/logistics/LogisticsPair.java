package com.tomkestookit.logistics;

/**
 * 物流终端一对：pairId 唯一，两端 endpoint 各含 dim/x/y/z。
 * marker UUID 是每端方块顶上盔甲架的 UUID（可为 null，未生成或已丢失）。
 *
 * 字段用 public + 无参构造暴露给 Gson，参考 landmark 里 Landmark 的做法。
 */
public final class LogisticsPair {
    public String pairId;
    public Endpoint a; // 第一次放下的
    public Endpoint b; // 第二次放下的；未放则为 null

    public LogisticsPair() {}

    public LogisticsPair(String pairId) {
        this.pairId = pairId;
    }

    public boolean isComplete() {
        return a != null && b != null;
    }

    public Endpoint other(Endpoint self) {
        if (self == null) return null;
        if (self == a) return b;
        if (self == b) return a;
        // 值比较（反序列化后不同实例）
        if (a != null && a.equalsTo(self)) return b;
        if (b != null && b.equalsTo(self)) return a;
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
                && java.util.Objects.equals(dim, o.dim);
        }
    }
}
