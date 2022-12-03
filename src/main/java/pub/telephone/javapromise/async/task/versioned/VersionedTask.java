package pub.telephone.javapromise.async.task.versioned;

import pub.telephone.javapromise.async.promise.*;

public class VersionedTask<T> {
    VersionedPromise<T> current;
    final PromiseJob<T> job;
    final PromiseSemaphore semaphore;
    boolean cancelled;

    public VersionedTask(PromiseJob<T> job, PromiseSemaphore semaphore) {
        current = null;
        cancelled = false;
        this.job = job;
        this.semaphore = semaphore;
        //
        synchronized (this) {
            ;
        }
    }

    public VersionedTask(PromiseJob<T> job) {
        this(job, null);
    }

    synchronized public void Cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        if (current != null) {
            current.Promise.Cancel();
        }
        current = new VersionedPromise<>(current == null ? 0 : current.Version + 1, Promise.Cancelled());
    }

    synchronized VersionedPromise<T> perform(Integer version) {
        if (current == null || (version != null && version == current.Version)) {
            int nextVersion = current == null ? 0 : current.Version + 1;
            return current = new VersionedPromise<>(
                    nextVersion,
                    cancelled ?
                            Promise.Cancelled() :
                            new Promise<>((resolver, rejector) -> resolver.Resolve(
                                    new Promise<>(job, semaphore)
                                            .Then(value -> new VersionedResult<>(nextVersion, value))
                            ))
            );
        } else {
            return current;
        }
    }

    public VersionedPromise<T> Perform() {
        return perform(null);
    }

    public VersionedPromise<T> Perform(int version) {
        return perform(version);
    }
}
