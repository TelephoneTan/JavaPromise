import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcaster;
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
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        TimedTask t = new TimedTask(broadcaster, Duration.ZERO, (resolver, rejector, state) -> {
            state.CancelledBroadcast.Listen(() -> System.out.println("内部收到取消广播"));
            for (int i = 0; i < 5; i++) {
                if (!state.CancelledBroadcast.IsActive.get()) {
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
            broadcaster.Broadcast();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test2() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        TimedTask t = new TimedTask(broadcaster, Duration.ZERO, (resolver, rejector, state) -> {
            state.CancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播"));
            resolver.Resolve(new Promise<>(state.CancelledBroadcast, (resolver1, rejector1, state1) -> {
                state1.CancelledBroadcast.Listen(() -> System.out.println("内部 2 收到取消广播"));
                for (int i = 0; i < 5; i++) {
                    if (!state1.CancelledBroadcast.IsActive.get()) {
                        System.out.println("内部 2 检测到取消");
                        break;
                    }
                    System.out.println("hello, world");
                    Thread.sleep(200);
                }
                resolver1.Resolve(true);
            }).ForCancel(() -> System.out.println("2 已取消")));
        });
        t.Start().ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            broadcaster.Broadcast();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }
}
