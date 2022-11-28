package pub.telephone.javapromise.async.task.versioned;

import pub.telephone.javapromise.async.promise.Promise;

public class VersionedPromise<T> {
    public final int Version;
    public final Promise<VersionedResult<T>> Promise;

    public VersionedPromise(int version, Promise<VersionedResult<T>> promise) {
        Version = version;
        Promise = promise;
    }
}
