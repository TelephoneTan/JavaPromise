package pub.telephone.javapromise.async.task.shared;

import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.promise.ExecutorKt;
import pub.telephone.javapromise.async.promise.Promise;
import pub.telephone.javapromise.async.promise.PromiseJob;
import pub.telephone.javapromise.async.promise.PromiseSemaphore;

public class SharedTask<T> {
    final PromiseJob<T> job;
    final Channel<Promise<T>> promise;
    final PromiseSemaphore semaphore;

    public SharedTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
        this.promise = ExecutorKt.newChannel(1);
        ExecutorKt.trySend(promise, null);
    }

    public SharedTask(PromiseJob<T> job) {
        this(job, null);
    }

    public Promise<T> Do() {
        return new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(promise, v -> {
            if (v == null || v.TryAwait()) {
                v = new Promise<>(job, semaphore);
            }
            ExecutorKt.trySend(promise, v);
            resolver.Resolve(v);
        }, ExecutorKt.noErrorContinuation()));
    }
}
