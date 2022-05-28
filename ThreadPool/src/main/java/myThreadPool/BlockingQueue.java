package myThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 阻塞队列
 *
 * @param <T> 元素类型
 * @author sgh
 */
@Slf4j
public class BlockingQueue<T> {
    /**
     * 延迟队列
     */
    private final Deque<T> queue = new LinkedList<>();
    /**
     * 容量
     */
    private final int capacity;

    /**
     * 锁
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 生产者条件变量
     */
    private final Condition fullWaitSet = lock.newCondition();

    /**
     * 消费者条件变量
     */
    private final Condition emptyWaitSet = lock.newCondition();


    public BlockingQueue(int capacity) {
        this.capacity = capacity;
    }

    /**
     * 超时阻塞获取任务
     *
     * @param time 超时时间
     * @param unit 时间单位
     * @return 任务
     */
    public T poll(long time, TimeUnit unit) {
        lock.lock();
        try {
            //将超时时间统一成纳秒
            long nanos = unit.toNanos(time);
            while (queue.isEmpty()) {
                try {
                    if (nanos <= 0) {
                        return null;
                    }
                    nanos = emptyWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("get task from blockQueue ");
            T t = queue.poll();
            fullWaitSet.signal();
            return t;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 阻塞获取
     *
     * @return 任务
     */
    public T poll() {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                try {
                    emptyWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            T t = queue.poll();
            fullWaitSet.signal();
            return t;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 阻塞添加任务到队列
     *
     * @param t 任务
     */
    public void push(T t) {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                try {
                    log.debug("queue is full and waiting consumed");
                    fullWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("add {} to block queue", t);
            queue.addLast(t);
            emptyWaitSet.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 超时阻塞添加任务
     *
     * @param t    任务
     * @param time 超时时间
     * @param unit 时间单位
     * @return 是否添加成功
     */
    public boolean push(T t, long time, TimeUnit unit) {
        lock.lock();
        //统一转为纳秒
        long nanos = unit.toNanos(time);
        try {
            while (queue.size() == capacity) {
                try {
                    if (nanos <= 0) {
                        log.debug("Block add queue failed because of overtime...");
                        return false;
                    }
                    nanos = fullWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("Block add success...");
            queue.addLast(t);
            emptyWaitSet.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试向队列中添加 失败执行拒绝策略
     * @param t 任务
     * @param rejectPolicy 拒绝策略
     */
    public void tryPut(T t, RejectPolicy<T> rejectPolicy) {
        lock.lock();
        try {
            //判断队列是否已满
            if (queue.size() == capacity) {
                if (rejectPolicy == null) {
                    throw new RejectedExecutionException("BlockQueue is already full...");
                } else {
                    rejectPolicy.reject(this, t);
                }
            } else {
                log.debug("add queue...");
                queue.addLast(t);
                emptyWaitSet.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 加锁获取队列大小
     *
     * @return 队列长度
     */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}
