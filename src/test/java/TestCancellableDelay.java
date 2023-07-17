import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcaster;

import java.time.Duration;

public class TestCancellableDelay {
    @Test
    void test() {
        test1();
        System.out.println("=================================");
        test2();
        System.out.println("=================================");
        test3();
        System.out.println("=================================");
        test4();
    }

    void test1() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        Async.Delay(broadcaster, Duration.ofSeconds(3), cancelledBroadcast -> {
            while (cancelledBroadcast.IsActive.get()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
        });
        Async.Delay(Duration.ofSeconds(2)).Then(value -> {
            broadcaster.Broadcast();
            return null;
        });
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test2() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        Async.Delay(broadcaster, Duration.ofSeconds(3), cancelledBroadcast -> {
            while (cancelledBroadcast.IsActive.get()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
        });
        Async.Delay(Duration.ofSeconds(4)).Then(value -> {
            broadcaster.Broadcast();
            return null;
        });
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test3() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        Async.Delay(broadcaster, Duration.ofSeconds(3)).Then((value, state) -> {
            while (state.CancelledBroadcast.IsActive.get()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
            return null;
        }).ForCancel(() -> System.out.println("外部检测到取消"));
        Async.Delay(Duration.ofSeconds(2)).Then(value -> {
            broadcaster.Broadcast();
            return null;
        });
        Async.Delay(Duration.ofSeconds(6)).Await();
    }

    void test4() {
        PromiseCancelledBroadcaster broadcaster = new PromiseCancelledBroadcaster();
        Async.Delay(broadcaster, Duration.ofSeconds(3)).Then((value, state) -> {
            while (state.CancelledBroadcast.IsActive.get()) {
                System.out.println("hello, world");
                Thread.sleep(200);
            }
            System.out.println("内部检测到取消");
            return null;
        }).ForCancel(() -> System.out.println("外部检测到取消"));
        Async.Delay(Duration.ofSeconds(4)).Then(value -> {
            broadcaster.Broadcast();
            return null;
        });
        Async.Delay(Duration.ofSeconds(6)).Await();
    }
}
