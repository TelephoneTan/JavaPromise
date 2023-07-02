package pub.telephone.javapromise.async.promise;

interface baseFulfilledListener<T, S> {
    Object OnFulfilled(T value, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
