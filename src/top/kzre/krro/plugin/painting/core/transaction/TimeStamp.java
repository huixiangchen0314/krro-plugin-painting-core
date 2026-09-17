package top.kzre.krro.plugin.painting.core.transaction;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局单调时间戳——op log 的排序依据。
 *
 * <p>性质：
 * <ul>
 *   <li>单调递增——永不回退</li>
 *   <li>跨 record 可比——同一 JVM 内全局唯一</li>
 *   <li>原子——多线程安全</li>
 * </ul>
 *
 * <p>为什么不用 {@code System.currentTimeMillis()}：
 * <ul>
 *   <li>墙钟可能被 NTP 校准回退</li>
 *   <li>同毫秒内多次调用返回相同值——无法区分顺序</li>
 * </ul>
 *
 * <p>起始值选择 1——让 0 可以作为"未初始化"或"无效"的哨兵。
 */
public final class TimeStamp {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    private TimeStamp() {}

    /**
     * 返回下一个单调时间戳。
     *
     * @return 严格递增的 long——第一次调用返回 1
     */
    public static long timeStamp() {
        return COUNTER.incrementAndGet();
    }

    /**
     * 当前值——不递增。
     *
     * <p>用于"取当前时间点"而不消耗一个序号——比如：
     * 创建 record 时记录 created-at，不需要消耗一个新序号。
     *
     * @return 最近一次分配的时间戳
     */
    public static long current() {
        return COUNTER.get();
    }
}