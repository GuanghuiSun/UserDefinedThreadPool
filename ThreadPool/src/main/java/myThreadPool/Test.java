package myThreadPool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.sleep;

/**
 * 测试入口
 *
 * @author sgh
 */
@Slf4j
public class Test {
    public static void main(String[] args) {
        ThreadPool threadPool = new ThreadPool(3, 5,
                1000, TimeUnit.MILLISECONDS, 6,
                new ThreadFactory() {
                    private AtomicInteger t = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "myThreadPool-t" + t.getAndIncrement());
                    }
                }, (queue, task) -> {
            log.debug("Execute a rejection policy for task-{}",task);
            //超时等待阻塞
            queue.push(task, 2, TimeUnit.SECONDS);
        });
        for (int i = 0; i < 10; i++) {
            int j = i;
            threadPool.execute(() -> {
                try {
                    sleep(100);
                    log.debug("task-{} running...", j);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
