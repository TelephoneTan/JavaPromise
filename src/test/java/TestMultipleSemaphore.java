import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class TestMultipleSemaphore {
    PromiseSemaphore total;
    PromiseSemaphore host;
    PromiseSemaphore hostA;
    PromiseSemaphore hostB;
    PromiseSemaphore user;
    PromiseSemaphore userA;
    PromiseSemaphore userB;

    {
        total = new PromiseSemaphore(10)
                .Then(host = new PromiseSemaphore(5)
                        .Then(hostA = new PromiseSemaphore(3))
                        .Then(hostB = new PromiseSemaphore(3))
                )
                .Then(user = new PromiseSemaphore(5)
                        .Then(userA = new PromiseSemaphore(3))
                        .Then(userB = new PromiseSemaphore(3))
                )
        ;
    }

    @Test
    void test() {
        long interval = 5000;
        List<Promise<String>> all = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            int ii = i;
            all.add(new Promise<String>((resolver, rejector) -> {
                String time = new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS").format(new Date());
                Thread.sleep(interval);
                resolver.Resolve("hostA " + ii + " -> " + time);
            }, hostA).Then(value -> {
                System.out.println(value);
                return null;
            }));
        }
        for (int i = 0; i < 10; i++) {
            int ii = i;
            all.add(new Promise<String>((resolver, rejector) -> {
                String time = new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS").format(new Date());
                Thread.sleep(interval);
                resolver.Resolve("hostB " + ii + " -> " + time);
            }, hostB).Then(value -> {
                System.out.println(value);
                return null;
            }));
        }
        for (int i = 0; i < 10; i++) {
            int ii = i;
            all.add(new Promise<String>((resolver, rejector) -> {
                String time = new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS").format(new Date());
                Thread.sleep(interval);
                resolver.Resolve("userA " + ii + " -> " + time);
            }, userA).Then(value -> {
                System.out.println(value);
                return null;
            }));
        }
        for (int i = 0; i < 10; i++) {
            int ii = i;
            all.add(new Promise<String>((resolver, rejector) -> {
                String time = new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS").format(new Date());
                Thread.sleep(interval);
                resolver.Resolve("userB " + ii + " -> " + time);
            }, userB).Then(value -> {
                System.out.println(value);
                return null;
            }));
        }
        Promise.FinallyAll(() -> {
            System.out.println("结束了");
            return null;
        }, all).Await();
    }
}
