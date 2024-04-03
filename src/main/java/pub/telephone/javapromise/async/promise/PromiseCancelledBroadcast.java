package pub.telephone.javapromise.async.promise;

public interface PromiseCancelledBroadcast extends pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcast {
    default Object Listen(Runnable r) {
        return listen(r);
    }

    default void UnListen(Object key) {
        unListen(key);
    }
}
