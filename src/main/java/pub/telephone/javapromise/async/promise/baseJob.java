package pub.telephone.javapromise.async.promise;

interface baseJob<T> {
    void Do(PromiseResolver<T> resolver, PromiseRejector rejector, PromiseCancelledBroadcast cancelledBroadcast, Promise<T> self) throws Throwable;
}
