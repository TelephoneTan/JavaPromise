package pub.telephone.javapromise.async.promise;

public interface PromiseCompoundFulfilledListener<S> {
    Object OnFulfilled(PromiseCompoundResult value) throws Throwable;
}
