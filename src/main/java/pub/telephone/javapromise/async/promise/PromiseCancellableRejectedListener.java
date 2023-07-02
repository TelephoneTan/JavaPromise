package pub.telephone.javapromise.async.promise;

public interface PromiseCancellableRejectedListener<S> {
    Object OnRejected(Throwable reason, PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
