package pub.telephone.javapromise.async.promise;

public interface PromiseCancelledBroadcast extends pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcast {
    Object Listen(Runnable r);

    void UnListen(Object key);
}
