package pub.telephone.javapromise.async.promise;

public abstract class PromiseRejector {
    abstract void cancel();

    public abstract void Reject(Throwable reason);
}
