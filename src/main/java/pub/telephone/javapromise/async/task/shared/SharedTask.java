package pub.telephone.javapromise.async.task.shared;

import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.promise.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SharedTask<T> {
    final PromiseCancellableJob<T> job;
    final Channel<Promise<T>> promise;
    final PromiseSemaphore semaphore;
    final CountDownLatch cancelled = new CountDownLatch(1);

    public SharedTask(PromiseCancellableJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
        this.promise = ExecutorKt.newChannel(1);
        ExecutorKt.trySend(promise, null);
    }

    public SharedTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this((resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), semaphore);
    }

    public SharedTask(PromiseJob<T> job) {
        this(job, null);
    }

    public SharedTask(PromiseCancellableJob<T> job) {
        this(job, null);
    }

    public SharedTask() {
        this((PromiseCancellableJob<T>) null, null);
    }

    public Promise<T> Do(PromiseCancellableJob<T> job) {
        return new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(promise, v -> {
            Promise<T> waitFor;
            if (v == null || v.TryAwait()) {
                if (SharedTask.this.cancelled.await(0, TimeUnit.SECONDS)) {
                    waitFor = Promise.Cancelled();
                    if (v != null) {
                        v = null;
                    }
                } else {
                    v = new Promise<>(job, semaphore);
                    waitFor = v;
                }
            } else {
                waitFor = v;
            }
            ExecutorKt.trySend(promise, v);
            resolver.Resolve(waitFor);
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
            }
            cancelled.countDown();
            ExecutorKt.trySend(promise, v);
        }, ExecutorKt.noErrorContinuation());
    }
}
