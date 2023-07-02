package pub.telephone.javapromise.async;

import pub.telephone.javapromise.async.promise.PromiseCancelledBroadcast;

public interface AsyncCancellableRunnableThrowsThrowable {
    void Run(PromiseCancelledBroadcast cancelledBroadcast) throws Throwable;
}
