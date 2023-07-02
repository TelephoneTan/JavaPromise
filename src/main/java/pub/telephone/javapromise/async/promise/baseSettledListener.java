package pub.telephone.javapromise.async.promise;

interface baseSettledListener {
    Promise<?> OnSettled(PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
