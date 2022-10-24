package pub.telephone.javapromise.async;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import pub.telephone.javapromise.async.promise.ExecutorKt;

import java.time.Duration;

public class Async {
    static void delay(Duration d, AsyncRunnableThrowsThrowable then, Continuation<Unit> continuation) {
        ExecutorKt.delay(d.toNanos(), then::Run, continuation);
    }

    public static void Delay(Duration d, AsyncRunnableThrowsThrowable then) {
        delay(d, then, ExecutorKt.ignoreErrorContinuation());
    }

    public static void Delay(Duration d, AsyncRunnableThrowsThrowable then, AsyncErrorListener onError) {
        delay(d, then, ExecutorKt.normalContinuation(onError::OnError));
    }
}
