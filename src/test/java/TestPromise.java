import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestPromise {
    @Test
    void test() {
        int num = 20;
        AtomicInteger res = new AtomicInteger(0);
        List<Promise<String>> allWork = new LinkedList<>();
        long interval = 500;
        PromiseSemaphore semaphore = new PromiseSemaphore(1);
        for (int i = 0; i < num; i++) {
            int ii = i;
            Promise<String> promise = new Promise<>((resolver, rejector) -> {
                Thread.sleep(interval);
                resolver.Resolve("x");
            }, semaphore);
            promise
                    .SetTimeOut(Duration.ofMillis(interval * 5), d ->
                            System.out.printf("任务 %d 已超时（%ds）\n", ii, d.toMillis() / 1000)
                    )
                    .SetTimeOut(Duration.ofMillis(interval * 10), d ->
                            System.out.printf("任务 %d 已超时（%ds）\n", ii, d.toMillis() / 1000)
                    )
            ;
            promise = promise.Then(value -> {
                System.out.println("" + ii + " -> x = " + res.incrementAndGet());
                return null;
            });
            allWork.add(promise);
            for (int j = 0; j < 10; j++) {
                promise = promise.Finally(() -> null);
                allWork.add(promise);
            }
        }
        Promise.ThenAll(null, allWork, allWork).Await();
        System.out.printf("执行 %d 个任务，最后的结果是 %d\n", num, res.get());
        try {
            Thread.sleep(60000_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
