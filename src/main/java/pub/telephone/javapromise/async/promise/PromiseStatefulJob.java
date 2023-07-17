package pub.telephone.javapromise.async.promise;

public interface PromiseStatefulJob<T> {
    void Do(PromiseResolver<T> resolver, PromiseRejector rejector, PromiseState<T> state) throws Throwable;
}
