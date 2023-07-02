package pub.telephone.javapromise.async.promise;

public interface PromiseCancellableFulfilledListener<T, S> {
    Object OnFulfilled(T value, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
