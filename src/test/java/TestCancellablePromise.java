import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.time.Duration;

public class TestCancellablePromise {
    @Test
    void test() {
        test1();
        System.out.println("=================================");
        test2();
        System.out.println("=================================");
        test3();
        System.out.println("=================================");
        test4();
        System.out.println("=================================");
        test5();
    }

    void test1() {
        Promise<Long> p = new Promise<>((resolver, rejector, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> System.out.println("内部收到取消广播"));
            while (cancelledBroadcast.IsActive.get()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
        });
        p.ForCancel(() -> System.out.println("已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            p.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(3));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test2() {
        Promise<Long> p = new Promise<>((resolver, rejector, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> cancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播")));
            resolver.Resolve(new Promise<>((resolver1, rejector1, cancelledBroadcast1) -> {
                        cancelledBroadcast1.Listen(() -> System.out.println("内部 2 收到取消广播"));
                        while (cancelledBroadcast1.IsActive.get()) {
                            System.out.println("hello, world");
                            Thread.sleep(200);
                        }
                        System.out.println("内部 2 检测到取消");
                    })
//                    .ForCancel(() -> System.out.println("2 已取消"))
            );
        });
        p.ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            p.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(3));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test3() {
        Promise<Long> p = new Promise<>((resolver, rejector) ->
                resolver.Resolve(null)
        ).Then((value, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> cancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播")));
            return new Promise<>((resolver1, rejector1, cancelledBroadcast1) -> {
                cancelledBroadcast1.Listen(() -> System.out.println("内部 2 收到取消广播"));
                while (cancelledBroadcast1.IsActive.get()) {
                    System.out.println("hello, world");
                    Thread.sleep(200);
                }
                System.out.println("内部 2 检测到取消");
            })
//                    .ForCancel(() -> System.out.println("2 已取消"))
                    ;
        });
        p.ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            p.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(3));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test4() {
        Promise<Long> p = new Promise<>((resolver, rejector) -> {
            throw new NullPointerException();
        }).Catch((reason, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> cancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播")));
            return new Promise<>((resolver1, rejector1, cancelledBroadcast1) -> {
                cancelledBroadcast1.Listen(() -> System.out.println("内部 2 收到取消广播"));
                while (cancelledBroadcast1.IsActive.get()) {
                    System.out.println("hello, world");
                    Thread.sleep(200);
                }
                System.out.println("内部 2 检测到取消");
            })
//                    .ForCancel(() -> System.out.println("2 已取消"))
                    ;
        });
        p.ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            p.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(3));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test5() {
        Promise<Long> p = new Promise<>((resolver, rejector) -> {
            throw new NullPointerException();
        }).Finally(cancelledBroadcast -> {
            cancelledBroadcast.Listen(() -> cancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播")));
            return new Promise<>((resolver1, rejector1, cancelledBroadcast1) -> {
                cancelledBroadcast1.Listen(() -> System.out.println("内部 2 收到取消广播"));
                while (cancelledBroadcast1.IsActive.get()) {
                    System.out.println("hello, world");
                    Thread.sleep(200);
                }
                System.out.println("内部 2 检测到取消");
            })
//                    .ForCancel(() -> System.out.println("2 已取消"))
                    ;
        });
        p.ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            p.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofSeconds(3));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }
}
