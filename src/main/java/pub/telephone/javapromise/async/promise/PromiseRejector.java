package pub.telephone.javapromise.async.promise;

public abstract class PromiseRejector<T> {
    abstract void cancel();

    public abstract void Reject(Throwable reason);

    public abstract void Reject(Promise<T> promise);
}
