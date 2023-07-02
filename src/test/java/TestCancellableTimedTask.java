import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.time.Duration;

public class TestCancellableTimedTask {
    @Test
    void test() {
        test1();
        System.out.println("=================================");
        test2();
    }

    void test1() {
        TimedTask t = new TimedTask(Duration.ZERO, (resolver, rejector, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> System.out.println("内部收到取消广播"));
            for (int i = 0; i < 5; i++) {
                if (!cancelledBroadcast.IsActive.get()) {
                    System.out.println("内部检测到取消");
                    break;
                }
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            resolver.Resolve(true);
        });
        t.Start().ForCancel(() -> System.out.println("已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            t.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test2() {
        TimedTask t = new TimedTask(Duration.ZERO, (resolver, rejector, cancelledBroadcast) -> {
            cancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播"));
            resolver.Resolve(new Promise<>((resolver1, rejector1, cancelledBroadcast1) -> {
                cancelledBroadcast1.Listen(() -> System.out.println("内部 2 收到取消广播"));
                for (int i = 0; i < 5; i++) {
                    if (!cancelledBroadcast1.IsActive.get()) {
                        System.out.println("内部 2 检测到取消");
                        break;
                    }
                    System.out.println("hello, world");
                    Thread.sleep(200);
                }
                resolver1.Resolve(true);
            }));
        });
        t.Start().ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            t.Cancel();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }
}
