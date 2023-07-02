package pub.telephone.javapromise.async.task.once;

import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.promise.*;

public class OnceTask<T> {
    final PromiseCancellableJob<T> job;
    final Channel<Promise<T>> promise;
    final PromiseSemaphore semaphore;
    final PromiseCancelledBroadcast scopeCancelledBroadcast;
    final Object scopeUnListenKey;
    final PromiseCancelledBroadcaster cancelledBroadcaster = new PromiseCancelledBroadcaster();

    public OnceTask(PromiseCancellableJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public OnceTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCancellableJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
        this.promise = ExecutorKt.newChannel(1);
        this.scopeCancelledBroadcast = scopeCancelledBroadcast;
        this.scopeUnListenKey = this.scopeCancelledBroadcast != null ?
                this.scopeCancelledBroadcast.Listen(this::Cancel) :
                null;
        ExecutorKt.trySend(promise, null);
    }

    public OnceTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public OnceTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), semaphore);
    }

    public OnceTask(PromiseJob<T> job) {
        this(null, job);
    }

    public OnceTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job) {
        this(scopeCancelledBroadcast, job, null);
    }
 
    public OnceTask(PromiseCancellableJob<T> job) {
        this(null, job);
    }

    public OnceTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCancellableJob<T> job) {
        this(scopeCancelledBroadcast, job, null);
    }

    public OnceTask() {
        this((PromiseCancelledBroadcast) null);
    }

    public OnceTask(PromiseCancelledBroadcast scopeCancelledBroadcast) {
        this(scopeCancelledBroadcast, (PromiseCancellableJob<T>) null, null);
    }

    void leaveScope() {
        if (this.scopeCancelledBroadcast != null) {
            this.scopeCancelledBroadcast.UnListen(this.scopeUnListenKey);
        }
    }

    public Promise<T> Do(PromiseCancellableJob<T> job) {
        return new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(promise, v -> {
            if (v == null) {
                Promise<T> p = new Promise<>(cancelledBroadcaster, job, semaphore);
                v = p.Finally(() -> {
                    leaveScope();
                    return null;
                }).Then(value -> p);
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
                leaveScope();
                v = Promise.Cancelled();
            }
            cancelledBroadcaster.Broadcast();
            ExecutorKt.trySend(promise, v);
        }, ExecutorKt.noErrorContinuation());
    }
}
