package pub.telephone.javapromise.async.promise;

interface baseCompoundFulfilledListener<R, O, S> {
    Object OnFulfilled(PromiseCompoundResult<R, O> value, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
