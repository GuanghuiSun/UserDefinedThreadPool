package myThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自定义线程池
 *
 * @author sgh
 */
@Slf4j
public class ThreadPool {

    /**
     * 最大核心线程数
     */
    private volatile int corePoolSize;

    /**
     * 线程池最大线程数
     */
    private volatile int maxPoolSize;

    /**
     * 阻塞时间
     */
    private volatile long keepAliveTime;

    /**
     * 阻塞时间单位
     */
    private volatile TimeUnit unit;

    /**
     * 阻塞队列
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * 阻塞队列长度
     */
    private final int capacity;

    /**
     * 线程工厂 设置线程属性
     */
    private volatile ThreadFactory threadFactory;

    /**
     * 拒绝策略
     */
    private volatile RejectPolicy<Runnable> rejectPolicy;

    /**
     * 锁
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 线程集合
     */
    private HashSet<Worker> workers = new HashSet<>();

    /**
     * 用来记录当前正在使用的线程数
     */
    private AtomicInteger workerCount = new AtomicInteger();

    /**
     * 当前线程池的状态
     */
    private final static int RUNNING = 0;
    private final static int STOPPED = 1;

    public ThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit,
                      int capacity) {
        if (corePoolSize < 0 ||
                maxPoolSize <= 0 ||
                maxPoolSize < corePoolSize ||
                keepAliveTime < 0 || capacity <= 0)
            throw new IllegalArgumentException();
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.unit = unit;
        this.workQueue = new BlockingQueue<>(capacity);
        this.capacity = capacity;
    }

    public ThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit,
                      int capacity, ThreadFactory threadFactory,
                      RejectPolicy<Runnable> rejectPolicy) {
        this(corePoolSize, maxPoolSize, keepAliveTime, unit, capacity);
        this.threadFactory = threadFactory;
        this.rejectPolicy = rejectPolicy;
    }


    /**
     * 任务类
     */
    private final class Worker extends Thread {
        private Runnable task;

        Worker(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            //执行任务
            if (task == null) {
                throw new NullPointerException("task can't be null");
            }
            //1. task不为空，直接执行        2. task执行完毕，从任务队列中获取任务执行
            while (task != null || (task = workQueue.poll(keepAliveTime,unit)) != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    task = null;
                }
            }
            synchronized (this) {
                log.debug("{} is removed", this);
                workers.remove(this);
            }
        }
    }

    public void execute(Runnable task) {
        lock.lock();
        try {
            //创建核心线程
            if (workerCount.get() < corePoolSize && addWorker(task, true)) {
                return;
            }
            //核心线程满了 加入阻塞队列
            if (workQueue.size() < capacity) {
                //加入阻塞队列
                workQueue.push(task);
                return;
            }
            if (workerCount.get() < maxPoolSize && addWorker(task, false)) {
                //创建救急线程
                return;
            }
            //执行拒绝策略
            workQueue.tryPut(task, rejectPolicy);
        } finally {
            lock.unlock();
        }
    }

    public boolean addWorker(Runnable task, boolean core) {
        while (true) {
            if (workerCount.get() >= (core ? corePoolSize : maxPoolSize)) {
                // 代表线程池不再允许创建新的worker
                return false;
            }
            if (!casIncreaseWorkerCount()) {
                continue;
            }
            break;
        }
        lock.lock();
        Worker worker;
        try {
            //核心线程/救急线程
            worker = new Worker(task);
            if (core) {
                log.debug("add worker {}", worker);
            } else {
                log.debug("add helping worker {}", worker);
            }
            workers.add(worker);
            worker.start();
        } finally {
            lock.unlock();
        }
        return true;
    }

    public boolean casIncreaseWorkerCount() {
        return workerCount.compareAndSet(workerCount.get(), workerCount.get() + 1);
    }

}