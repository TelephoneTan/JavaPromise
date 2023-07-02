package pub.telephone.javapromise.async.promise;

public interface PromiseCancellableCompoundFulfilledListener<R, O, S> {
    Object OnFulfilled(PromiseCompoundResult<R, O> value, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
