import org.junit.jupiter.api.Test;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.PromiseFulfilledListener;
import pub.telephone.javapromise.async.promise.PromiseJob;
import pub.telephone.javapromise.async.task.timed.TimedTask;
import pub.telephone.javapromise.async.task.versioned.VersionedPromise;
import pub.telephone.javapromise.async.task.versioned.VersionedResult;
import pub.telephone.javapromise.async.task.versioned.VersionedTask;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Random;

public class TestVersionedTask {
    static final long sts = System.currentTimeMillis();
    static final Random random = new Random(System.currentTimeMillis());

    static class status {
        final int version;
        final int versionPlusOne;
        final long offset;
        final String description;

        public status(int version) {
            this.version = version;
            this.versionPlusOne = version + 1;
            this.offset = System.currentTimeMillis() - sts;
            this.description = String.format("v%d : %d", versionPlusOne, offset);
        }
    }

    <T> PromiseJob<T> buildTask(T v) {
        return (rs, re) -> Async.Delay(Duration.ofSeconds(1)).Then(value -> {
            rs.Resolve(v);
            return null;
        });
    }

    @Test
    void test() {
        System.out.println("start " +
                new SimpleDateFormat("yyyy / MM / dd | HH : mm : ss . SSS")
                        .format(new Date(sts))
        );
        VersionedTask<String> clock = new VersionedTask<>(buildTask("hello"));
        final Throwable retry = new Throwable("失效啦，请重试");
        final PromiseFulfilledListener<VersionedResult<String>, String> format = value -> {
            status stat = new status(value.Version);
            if (stat.offset > stat.versionPlusOne * 1100L) {
                System.out.println(stat.description + "(miss)");
                throw retry;
            }
            if (random.nextInt(5) == 0) {
                throw new Throwable(stat.description + "(failed)");
            }
            return stat.description + "(confirmed)" + value.Result;
        };
        final PromiseFulfilledListener<String, Object> print = value -> {
            System.out.println(value);
            return null;
        };
        String[] words = new String[]{"A", "B", "C", "D", "E"};
        new TimedTask(Duration.ofMillis(200), (resolver, rejector) -> {
            VersionedPromise<String> result = clock.Perform(); // 首次尝试
            status stat = new status(result.Version);
            System.out.println(stat.description + "(try)");
            result.Promise
                    .Then(format) // 这里可能会抛出重试异常
                    .Then(print) // 如果没有任何异常就继续打印
                    .Catch(reason -> {
                        if (reason == retry) { // 捕获重试异常
                            VersionedPromise<String> newResult = clock.Perform(
                                    result.Version,
                                    buildTask(words[random.nextInt(words.length)])
                            );
                            status newStat = new status(newResult.Version);
                            System.out.println(newStat.description + "(retry)");
                            return newResult.Promise // 重试
                                    .Then(format) // 这里依然可能抛出重试异常
                                    .Then(print) // 如果重试后没有异常就正常打印
                                    .Catch(reason1 -> {
                                        throw reason1 == retry ?
                                                // 将第二次抛出的重试异常转化为未知错误
                                                new Throwable(newStat.description + "(unknown error)") :
                                                // 非重试异常，透传
                                                reason1;
                                    })
                                    .ForCancel(() -> System.out.println(newStat.description + "（不妙，重试被取消了）"))
                                    ;
                        } else {
                            throw reason; // 非重试异常，透传
                        }
                    })
                    .Catch(reason -> { // 打印错误
                        System.out.println("Error: " + reason.getMessage());
                        reason.printStackTrace();
                        return null;
                    })
                    .ForCancel(() -> System.out.println(stat.description + "（不妙，被取消了）"));
            if (random.nextInt(50) == 0) {
                clock.Cancel();
            }
            resolver.Resolve(true);
        }).Start().Await();
        Async.Delay(Duration.ofHours(5)).Await();
    }
}
