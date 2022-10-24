import pub.telephone.javapromise.async.task.once.OnceTask;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TestOnceTask {
    @Test
    void test() {
        OnceTask<String> task = new OnceTask<>((resolver, rejector) -> resolver.Resolve(
                "hello " +
                        new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS")
                                .format(new Date())
        ));
        for (int i = 0; i < 100000; i++) {
            task.Do().Then(value -> {
                System.out.println(value);
                return null;
            });
        }
        try {
            Thread.sleep(600000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
