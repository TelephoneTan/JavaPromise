package pub.telephone.javapromise.async.task.once;

import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.promise.*;

public class OnceTask<T> {
    final PromiseCancellableJob<T> job;
    final Channel<Promise<T>> promise;
    final PromiseSemaphore semaphore;

    public OnceTask(PromiseCancellableJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
        this.promise = ExecutorKt.newChannel(1);
        ExecutorKt.trySend(promise, null);
    }

    public OnceTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this((resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), semaphore);
    }

    public OnceTask(PromiseJob<T> job) {
        this(job, null);
    }

    public OnceTask(PromiseCancellableJob<T> job) {
        this(job, null);
    }

    public OnceTask() {
        this((PromiseCancellableJob<T>) null, null);
    }

    public Promise<T> Do(PromiseCancellableJob<T> job) {
        return new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(promise, v -> {
            if (v == null) {
                v = new Promise<>(job, semaphore);
            }
            ExecutorKt.trySend(promise, v);
            resolver.Resolve(v);
        }, ExecutorKt.noErrorContinuation()));
    }

    public Promise<T> Do(PromiseJob<T> job) {
        return Do((resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector));
    }

    public Promise<T> Do() {
        return Do(job);
    }

    public void Cancel() {
        ExecutorKt.onReceive(promise, v -> {
            if (v != null) {
                v.Cancel();
            } else {
                v = Promise.Cancelled();
            }
            ExecutorKt.trySend(promise, v);
        }, ExecutorKt.noErrorContinuation());
    }
}
