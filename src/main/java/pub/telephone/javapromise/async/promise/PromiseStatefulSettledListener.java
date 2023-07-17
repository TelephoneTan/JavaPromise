package pub.telephone.javapromise.async.promise;

public interface PromiseStatefulSettledListener {
    Promise<?> OnSettled(PromiseState<?> state) throws Throwable;
}
