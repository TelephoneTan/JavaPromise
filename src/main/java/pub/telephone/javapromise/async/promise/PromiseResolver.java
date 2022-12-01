package pub.telephone.javapromise.async.promise;

public interface PromiseResolver<T> {
    void Resolve(T value);

    void Resolve(Promise<T> promise);

    default void ResolveValue(T value) {
        Resolve(value);
    }

    default void ResolvePromise(Promise<T> promise) {
        Resolve(promise);
    }
}
