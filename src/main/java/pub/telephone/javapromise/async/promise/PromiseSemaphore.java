package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.Nullable;
import pub.telephone.javapromise.async.AsyncRunnableThrowsThrowable;

public class PromiseSemaphore extends pub.telephone.javapromise.async.kpromise.PromiseSemaphore {
    public PromiseSemaphore(long n) {
        super(n);
    }

    public void Acquire(@Nullable AsyncRunnableThrowsThrowable then, Continuation<? super Unit> continuation) {
        acquire(1, then, continuation);
    }

    public void Release() {
        release(1);
    }

    public PromiseSemaphore Then(@Nullable PromiseSemaphore child) {
        then(child);
        return this;
    }
}
