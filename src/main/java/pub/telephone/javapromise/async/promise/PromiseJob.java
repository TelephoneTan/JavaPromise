package pub.telephone.javapromise.async.promise;


public interface PromiseJob<T> {
    void Do(PromiseResolver<T> resolver, PromiseRejector<T> rejector) throws Throwable;
}
