package io.aioa.common.id;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花 ID 生成器（简化版，单机够用）
 * 时间戳(41) + 机器位(5) + 序列(18) ≈ 64 bit
 */
public final class Snowflake {

    private static final long EPOCH = 1704067200000L; // 2024-01-01
    private static final long MACHINE_ID = (long) (Math.random() * 31);
    private static final AtomicLong SEQ = new AtomicLong(0);

    public static synchronized long nextId() {
        long ts = System.currentTimeMillis() - EPOCH;
        long seq = SEQ.getAndIncrement() & ((1 << 18) - 1);
        return (ts << 23) | (MACHINE_ID << 18) | seq;
    }

    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    private Snowflake() {
    }
}
