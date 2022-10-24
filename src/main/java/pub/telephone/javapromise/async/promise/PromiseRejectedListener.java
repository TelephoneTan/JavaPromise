package pub.telephone.javapromise.async.promise;

public interface PromiseRejectedListener<S> {
    Object OnRejected(Throwable reason) throws Throwable;
}
