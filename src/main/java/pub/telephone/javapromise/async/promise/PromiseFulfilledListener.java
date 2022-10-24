package pub.telephone.javapromise.async.promise;

public interface PromiseFulfilledListener<T, S> {
    Object OnFulfilled(T value) throws Throwable;
}
