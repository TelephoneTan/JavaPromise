import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.task.shared.SharedTask;
import pub.telephone.javapromise.async.task.timed.TimedTask;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class TestSharedTask {
    @Test
    void test() {
        SharedTask<String> task = new SharedTask<>((resolver, rejector) -> Async.Delay(
                Duration.ofSeconds(1),
                () -> resolver.Resolve(
                        "hello " +
                                new SimpleDateFormat("yyyy 年 MM 月 dd 日 HH 时 mm 分 ss 秒 . SSS")
                                        .format(new Date())
                )
        ));
        new TimedTask(Duration.ofMillis(200), (resolver, rejector) -> {
            task.Do().Then(value -> {
                System.out.println(value);
                return null;
            });
            resolver.Resolve(true);
        }).Start().Await();
    }
}
