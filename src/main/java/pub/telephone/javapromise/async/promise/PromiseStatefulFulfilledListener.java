package pub.telephone.javapromise.async.promise;

public interface PromiseStatefulFulfilledListener<T, S> {
    Object OnFulfilled(T value, PromiseState<S> state) throws Throwable;
}
