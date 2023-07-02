package pub.telephone.javapromise.async.promise;

public class PromiseCancelledBroadcaster extends PromiseCancelledBroadcast {

    @Override
    public synchronized void Broadcast() {
        super.Broadcast();
    }

    public synchronized void Clear() {
        Listeners.clear();
    }
}
