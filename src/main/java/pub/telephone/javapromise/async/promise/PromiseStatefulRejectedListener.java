package pub.telephone.javapromise.async.promise;

public interface PromiseStatefulRejectedListener<S> {
    Object OnRejected(Throwable reason, PromiseState<S> state) throws Throwable;
}
