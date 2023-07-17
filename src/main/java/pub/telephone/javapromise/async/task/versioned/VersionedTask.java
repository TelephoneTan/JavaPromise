package pub.telephone.javapromise.async.task.versioned;

import pub.telephone.javapromise.async.promise.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class VersionedTask<T> {
    final PromiseStatefulJob<T> job;
    final PromiseSemaphore semaphore;
    final PromiseCancelledBroadcast scopeCancelledBroadcast;
    final Object scopeUnListenKey;
    final PromiseCancelledBroadcaster cancelledBroadcaster = new PromiseCancelledBroadcaster();
    final AtomicReference<VersionedPromise<T>> current = new AtomicReference<>();
    final AtomicBoolean cancelled = new AtomicBoolean();

    public VersionedTask(PromiseStatefulJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public VersionedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
        this.scopeCancelledBroadcast = scopeCancelledBroadcast;
        this.scopeUnListenKey = this.scopeCancelledBroadcast != null ?
                this.scopeCancelledBroadcast.Listen(this::Cancel) :
                null;
    }

    public VersionedTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public VersionedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, (resolver, rejector, state) -> job.Do(resolver, rejector), semaphore);
    }

    public VersionedTask(PromiseJob<T> job) {
        this(null, job);
    }

    public VersionedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job) {
        this(scopeCancelledBroadcast, job, null);
    }

    public VersionedTask(PromiseStatefulJob<T> job) {
        this(null, job);
    }

    public VersionedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulJob<T> job) {
        this(scopeCancelledBroadcast, job, null);
    }

    public VersionedTask() {
        this((PromiseCancelledBroadcast) null);
    }

    public VersionedTask(PromiseCancelledBroadcast scopeCancelledBroadcast) {
        this(scopeCancelledBroadcast, (PromiseStatefulJob<T>) null, null);
    }

    void leaveScope() {
        if (this.scopeCancelledBroadcast != null) {
            this.scopeCancelledBroadcast.UnListen(this.scopeUnListenKey);
        }
    }

    public void Cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        cancelledBroadcaster.Broadcast();
        leaveScope();
        current.updateAndGet(prev -> new VersionedPromise<>(prev == null ? 0 : prev.Version + 1, Promise.Cancelled()));
    }

    protected VersionedPromise<T> Perform(Integer version, PromiseStatefulJob<T> job) {
        return current.updateAndGet(prev -> {
            if (prev == null || (version != null && version == prev.Version)) {
                int nextVersion = prev == null ? 0 : prev.Version + 1;
                return new VersionedPromise<>(
                        nextVersion,
                        cancelled.get() ?
                                Promise.Cancelled() :
                                new Promise<>((resolver, rejector) -> {
                                    Promise<T> p = new Promise<>(job, semaphore);
                                    cancelledBroadcaster.Listen(p::Cancel);
                                    resolver.Resolve(
                                            p.Finally(() -> {
                                                cancelledBroadcaster.Clear();
                                                return null;
                                            }).Then(value -> p.Then(value1 -> new VersionedResult<>(nextVersion, value1)))
                                    );
                                })
                );
            } else {
                return prev;
            }
        });
    }

    public VersionedPromise<T> Perform(PromiseJob<T> job) {
        return Perform(null, (resolver, rejector, state) -> job.Do(resolver, rejector));
    }

    public VersionedPromise<T> Perform(PromiseStatefulJob<T> job) {
        return Perform(null, job);
    }

    public VersionedPromise<T> Perform() {
        return Perform(null, job);
    }

    public VersionedPromise<T> Perform(int version, PromiseJob<T> job) {
        return Perform(version, (resolver, rejector, state) -> job.Do(resolver, rejector));
    }

    public VersionedPromise<T> Perform(int version, PromiseStatefulJob<T> job) {
        return Perform((Integer) version, job);
    }

    public VersionedPromise<T> Perform(int version) {
        return Perform(version, job);
    }
}
