import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.task.once.OnceTask;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class TestOnceTask {
    @Test
    void test() {
        OnceTask<String> task = new OnceTask<>((resolver, rejector) -> {
            Duration delay = Duration.ofSeconds(2);
            System.out.printf("请等我 %d 秒\n", delay.toMillis() / 1000);
            Async.Delay(delay).Then(v -> {
                resolver.Resolve(
                        "hello " +
                                new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS")
                                        .format(new Date())
                );
                return null;
            });
        });
        Async.Delay(Duration.ofSeconds(5)).Then(v -> {
            for (int i = 0; i < 100000; i++) {
                task.Cancel();
            }
            return null;
        });
        Duration delay = Duration.ofSeconds(0);
        System.out.printf("%d 秒后开始\n", delay.toMillis() / 1000);
        new TimedTask(Duration.ofMillis(200), (resolver, rejector) -> {
            task.Do().Then(v -> {
                System.out.println(v);
                return null;
            }).ForCancel(() -> System.out.println("不妙，被取消了"));
            resolver.Resolve(true);
        }).Start(delay).Await();
    }
}
