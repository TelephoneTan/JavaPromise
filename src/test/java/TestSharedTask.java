import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.task.shared.SharedTask;
import pub.telephone.javapromise.async.task.timed.TimedTask;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Random;

public class TestSharedTask {
    @Test
    void test() {
        SharedTask<String> task = new SharedTask<>();
        Async.Delay(Duration.ofSeconds(5)).Then(value -> {
            for (int i = 0; i < 100000; i++) {
                task.Cancel();
            }
            return null;
        });
        Random random = new Random(System.currentTimeMillis());
        String[] prefix = new String[]{"A", "B", "C", "D", "E"};
        new TimedTask(Duration.ofMillis(200), (resolver, rejector) -> {
            String p = prefix[random.nextInt(prefix.length)];
            task.Do((rs, re) -> Async.Delay(
                    Duration.ofSeconds(1),
                    () -> rs.Resolve(
                            p + " " +
                                    new SimpleDateFormat("yyyy 年 MM 月 dd 日 HH 时 mm 分 ss 秒 . SSS")
                                            .format(new Date())
                    )
            )).Then(value -> {
                System.out.println(value);
                return null;
            }).ForCancel(() -> System.out.println("不妙，被取消了"));
            resolver.Resolve(true);
        }).Start().Await();
    }
}
