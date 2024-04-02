import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcaster;
import pub.telephone.javapromise.async.task.once.OnceTask;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.time.Duration;

public class TestCancellableOnceTask {
    @Test
    void test() {
        test1();
        System.out.println("=================================");
        test2();
    }

    void test1() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        OnceTask<Object> t = new OnceTask<>(broadcaster, (resolver, rejector, state) -> {
            state.CancelledBroadcast.Listen(() -> System.out.println("内部收到取消广播"));
            while (state.CancelledBroadcast.isActive()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
        });
        t.Do().ForCancel(() -> System.out.println("已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            broadcaster.Broadcast();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test2() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        OnceTask<Object> p = new OnceTask<>(broadcaster, (resolver, rejector, state) -> {
            state.CancelledBroadcast.Listen(() -> state.CancelledBroadcast.Listen(() -> System.out.println("内部 1 收到取消广播")));
            resolver.Resolve(new Promise<>(state.CancelledBroadcast, (resolver1, rejector1, state1) -> {
                        state1.CancelledBroadcast.Listen(() -> System.out.println("内部 2 收到取消广播"));
                while (state1.CancelledBroadcast.isActive()) {
                            System.out.println("hello, world");
                            Thread.sleep(200);
                        }
                        System.out.println("内部 2 检测到取消");
                    })
                            .ForCancel(() -> System.out.println("2 已取消"))
            );
        });
        p.Do().ForCancel(() -> System.out.println("1 已取消"));
        new TimedTask(Duration.ZERO, (resolver, rejector) -> {
            broadcaster.Broadcast();
            resolver.Resolve(false);
        }).Start(Duration.ofMillis(2500));
        Async.Delay(Duration.ofSeconds(6)).Await();
    }
}
