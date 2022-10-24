package pub.telephone.javapromise.async.promise;

public interface PromiseResolver<T> {
    void Resolve(T value);

    void Resolve(Promise<T> promise);
}
