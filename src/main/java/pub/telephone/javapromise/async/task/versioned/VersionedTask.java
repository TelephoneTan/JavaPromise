package pub.telephone.javapromise.async.task.versioned;

import pub.telephone.javapromise.async.promise.*;

public class VersionedTask<T> {
    VersionedPromise<T> current;
    final PromiseJob<T> job;

    public VersionedTask(PromiseJob<T> job) {
        this.job = job;
        //
        current = null;
        //
        synchronized (this) {
            ;
        }
    }

    synchronized VersionedPromise<T> perform(Integer version) {
        if (current == null || (version != null && version == current.Version)) {
            int nextVersion = current == null ? 0 : current.Version + 1;
            return current = new VersionedPromise<>(
                    nextVersion,
                    new Promise<>((resolver, rejector) -> resolver.Resolve(
                            new Promise<>(job).Then(value -> new VersionedResult<>(nextVersion, value))
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
