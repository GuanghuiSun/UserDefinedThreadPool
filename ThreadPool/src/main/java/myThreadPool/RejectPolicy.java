package myThreadPool;

/**
 * 拒绝策略
 *
 * @param <T> 任务
 * @author sgh
 */
@FunctionalInterface
public interface RejectPolicy<T> {
    void reject(BlockingQueue<T> queue, T t);
}
