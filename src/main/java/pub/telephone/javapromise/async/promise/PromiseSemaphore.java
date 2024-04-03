package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.Nullable;
import pub.telephone.javapromise.async.AsyncRunnableThrowsThrowable;

public class PromiseSemaphore {
    public final pub.telephone.javapromise.async.kpromise.PromiseSemaphore kSemaphore;
    public PromiseSemaphore(long n) {
        this.kSemaphore = new pub.telephone.javapromise.async.kpromise.PromiseSemaphore(n);
    }

    public PromiseSemaphore(pub.telephone.javapromise.async.kpromise.PromiseSemaphore kSemaphore) {
        this.kSemaphore = kSemaphore;
    }

    public void Acquire(@Nullable AsyncRunnableThrowsThrowable then, Continuation<? super Unit> continuation) {
        kSemaphore.acquire(1, then, continuation);
    }

    public void Release() {
        kSemaphore.release(1);
    }

    public PromiseSemaphore Then(@Nullable PromiseSemaphore child) {
        kSemaphore.then(child == null ? null : child.kSemaphore);
        return this;
    }
}
