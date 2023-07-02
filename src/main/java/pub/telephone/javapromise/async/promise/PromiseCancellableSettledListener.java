package pub.telephone.javapromise.async.promise;

public interface PromiseCancellableSettledListener {
    Promise<?> OnSettled(PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
