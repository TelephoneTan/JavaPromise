import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.task.timed.TimedTask;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

public class TestTimedTask {
    @Test
    void test() {
        TimedTask task = new TimedTask(Duration.ofMillis(50), (resolver, rejector) -> {
            System.out.println("hello " + new SimpleDateFormat("yyyy 年 MM 月 dd 日 HH 时 mm 分 ss 秒 . SSS").format(new Date()));
            resolver.Resolve(true);
        });
        task.AddTimesBy(1).Then(value -> {
            System.out.printf("成功增加了 %d 次运行\n", value);
            return null;
        }).Catch(reason -> {
            System.out.printf("增加运行失败：%s\n", reason.getMessage());
            return null;
        }).Await();
        task.ReduceTimesBy(4).Then(value -> {
            System.out.printf("成功减少了 %d 次运行\n", value);
            return null;
        }).Catch(reason -> {
            System.out.printf("减少运行失败：%s\n", reason.getMessage());
            return null;
        }).Await();
        Promise<Integer> taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        taskPromise = task.Start();
        Promise<Object> completed = taskPromise.Then(value -> {
            System.out.printf("一共运行了 %d 次\n", value);
            return null;
        }).ForCancel(() -> System.out.println("不妙，被取消掉了"));
        new Promise<>((resolver, rejector) -> {
            Thread.sleep(3000 * 3);
            resolver.Resolve(null);
        }).Then(value -> {
            task.Pause();
            System.out.println("已暂停");
            return null;
        }).Then(value -> {
            Thread.sleep(3000 * 2);
            task.SetDuration(Duration.ofSeconds(1));
            System.out.println("已更改间隔");
            return null;
        }).Then(value -> {
            task.Resume(Duration.ofSeconds(5));
            System.out.println("已恢复");
            return null;
        }).Then(value -> {
            Thread.sleep(10000);
            task.Cancel();
            System.out.println("已取消");
            return null;
        });
        completed.Await();
    }
}
