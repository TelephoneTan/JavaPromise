package pub.telephone.javapromise.async;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import pub.telephone.javapromise.async.promise.ExecutorKt;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcast;

import java.time.Duration;

public class Async {
    static void delay(
            PromiseCancelledBroadcast scopeCancelledBroadcast,
            Duration d,
            AsyncCancellableRunnableThrowsThrowable then,
            Continuation<Unit> continuation
    ) {
        ExecutorKt.delay(
                d,
                () -> then.Run(scopeCancelledBroadcast == null ? new PromiseCancelledBroadcast() : scopeCancelledBroadcast),
                continuation
        );
    }

    public static void Delay(Duration d, AsyncRunnableThrowsThrowable then) {
        delay(null, d, cancelledBroadcast -> then.Run(), ExecutorKt.ignoreErrorContinuation());
    }

    public static void Delay(
            PromiseCancelledBroadcast scopeCancelledBroadcast,
            Duration d,
            AsyncCancellableRunnableThrowsThrowable then
    ) {
        delay(scopeCancelledBroadcast, d, then, ExecutorKt.ignoreErrorContinuation());
    }

    public static void Delay(Duration d, AsyncRunnableThrowsThrowable then, AsyncErrorListener onError) {
        delay(null, d, cancelledBroadcast -> then.Run(), ExecutorKt.normalContinuation(onError::OnError));
    }

    public static void Delay(
            PromiseCancelledBroadcast scopeCancelledBroadcast,
            Duration d,
            AsyncCancellableRunnableThrowsThrowable then,
            AsyncErrorListener onError
    ) {
        delay(scopeCancelledBroadcast, d, then, ExecutorKt.normalContinuation(onError::OnError));
    }

    public static Promise<Object> Delay(Duration d) {
        return Delay(null, d);
    }

    public static Promise<Object> Delay(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration d) {
        return new Promise<>(scopeCancelledBroadcast, (resolver, rejector) ->
                delay(
                        null,
                        d,
                        cancelledBroadcast -> resolver.Resolve(null),
                        ExecutorKt.ignoreErrorContinuation()
                )
        );
    }
}
