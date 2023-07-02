package pub.telephone.javapromise.async.promise;

interface baseRejectedListener<S> {
    Object OnRejected(Throwable reason, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
