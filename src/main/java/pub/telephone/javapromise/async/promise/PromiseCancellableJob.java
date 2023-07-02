package pub.telephone.javapromise.async.promise;

public interface PromiseCancellableJob<T> {
    void Do(PromiseResolver<T> resolver, PromiseRejector rejector, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
