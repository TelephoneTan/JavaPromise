package pub.telephone.javapromise.async;

import pub.telephone.javapromise.async.kpromise.RunThrow;

public interface AsyncRunnableThrowsThrowable extends RunThrow {
    void Run() throws Throwable;

    @Override
    default void run() throws Throwable {
        Run();
    }
}
