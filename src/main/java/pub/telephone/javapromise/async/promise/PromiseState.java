package pub.telephone.javapromise.async.promise;

public class PromiseState<S> {
    final public PromiseCancelledBroadcast ScopeCancelledBroadcast;
    final public PromiseCancelledBroadcast CancelledBroadcast;
    final Promise<S> self;

    public PromiseState(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCancelledBroadcast cancelledBroadcast, Promise<S> self) {
        ScopeCancelledBroadcast = scopeCancelledBroadcast;
        CancelledBroadcast = cancelledBroadcast;
        this.self = self;
    }
}
