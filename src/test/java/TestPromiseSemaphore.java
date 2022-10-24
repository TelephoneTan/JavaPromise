import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TestPromiseSemaphore {
    @Test
    void test() {
        testN(1);
        testN(5);
    }

    void testN(int num) {
        List<Promise<Object>> allWork = new LinkedList<>();
        PromiseSemaphore semaphore = new PromiseSemaphore(num);
        AtomicInteger counter = new AtomicInteger(0);
        //
        for (int i = 0; i < 100; i++) {
            int ii = i;
            allWork.add(new Promise<>((resolver, rejector) -> {
                if (ii == 55 || ii == 66 || ii == 77) {
                    throw new Exception(String.format("#%d 不干了", ii));
                }
                System.out.printf("#%d 开始值 %d\n", ii, counter.get());
                for (int j = 0; j < 1000000; j++) {
                    counter.incrementAndGet();
                }
                System.out.printf("#%d 结束值 %d\n", ii, counter.get());
                resolver.Resolve(null);
            }, semaphore));
        }
        Promise.ThenAll(value -> {
            System.out.println("全部成功");
            return null;
        }, allWork).Catch(reason -> {
            System.out.println("出错了：" + reason.getMessage());
            return null;
        }).Await();
        System.out.printf("总体结束值 %d\n", counter.get());
    }
}
