package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.AsyncRunnableThrowsThrowable;

public class PromiseSemaphore {
    volatile PromiseSemaphore parent;
    final Channel<Unit> ticketChannel = ExecutorKt.newChannel(Channel.UNLIMITED);
    final Channel<Unit> darkChannel = ExecutorKt.newChannel(Channel.UNLIMITED);

    public PromiseSemaphore(int available) {
        Post(available);
    }

    public void Acquire(AsyncRunnableThrowsThrowable then, Continuation<? super Unit> continuation) {
        ExecutorKt.acquirePromiseSemaphore(this, 1, 0, then::Run, continuation);
    }

    public void Release() {
        PromiseSemaphore s = this;
        while (s != null) {
            s.Post(1);
            s = s.parent;
        }
    }

    public void Get(int n, AsyncRunnableThrowsThrowable then, Continuation<? super Unit> continuation) {
        ExecutorKt.acquirePromiseSemaphore(this, n, 1, then::Run, continuation);
    }

    public void Post(int n) {
        for (int i = 0; i < n; i++) {
            ExecutorKt.readFromOrSendTo(darkChannel, ticketChannel);
        }
    }

    public void Reduce(int n) {
        for (int i = 0; i < n; i++) {
            ExecutorKt.trySend(darkChannel);
        }
    }

    public PromiseSemaphore Then(PromiseSemaphore child) {
        if (child != null) {
            child.parent = this;
        }
        return this;
    }
}
