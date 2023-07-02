package pub.telephone.javapromise.async.task.versioned;

import pub.telephone.javapromise.async.promise.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class VersionedTask<T> {
    final PromiseCancellableJob<T> job;
    final PromiseSemaphore semaphore;
    final PromiseCancelledBroadcaster cancelledBroadcaster = new PromiseCancelledBroadcaster();
    final AtomicReference<VersionedPromise<T>> current = new AtomicReference<>();
    final AtomicBoolean cancelled = new AtomicBoolean();

    public VersionedTask(PromiseCancellableJob<T> job, PromiseSemaphore semaphore) {
        this.job = job;
        this.semaphore = semaphore;
    }

    public VersionedTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this((resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), semaphore);
    }

    public VersionedTask(PromiseJob<T> job) {
        this(job, null);
    }

    public VersionedTask(PromiseCancellableJob<T> job) {
        this(job, null);
    }

    public VersionedTask() {
        this((PromiseCancellableJob<T>) null, null);
    }

    public void Cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        cancelledBroadcaster.Broadcast();
        current.updateAndGet(prev -> new VersionedPromise<>(prev == null ? 0 : prev.Version + 1, Promise.Cancelled()));
    }

    protected VersionedPromise<T> Perform(Integer version, PromiseCancellableJob<T> job) {
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
                                            }).Then(value -> new VersionedResult<>(nextVersion, value))
                                    );
                                })
                );
            } else {
                return prev;
            }
        });
    }

    public VersionedPromise<T> Perform(PromiseJob<T> job) {
        return Perform(null, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector));
    }

    public VersionedPromise<T> Perform(PromiseCancellableJob<T> job) {
        return Perform(null, job);
    }

    public VersionedPromise<T> Perform() {
        return Perform(null, job);
    }

    public VersionedPromise<T> Perform(int version, PromiseJob<T> job) {
        return Perform(version, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector));
    }

    public VersionedPromise<T> Perform(int version, PromiseCancellableJob<T> job) {
        return Perform((Integer) version, job);
    }

    public VersionedPromise<T> Perform(int version) {
        return Perform(version, job);
    }
}
