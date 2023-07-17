package pub.telephone.javapromise.async.promise;

public interface PromiseStatefulCompoundFulfilledListener<R, O, S> {
    Object OnFulfilled(PromiseCompoundResult<R, O> value, PromiseState<S> state) throws Throwable;
}
