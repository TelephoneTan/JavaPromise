package pub.telephone.javapromise.async.promise;

public interface PromiseCompoundFulfilledListener<R, O, S> {
    Object OnFulfilled(PromiseCompoundResult<R, O> value) throws Throwable;
}
