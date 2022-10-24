package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlinx.coroutines.channels.Channel;

public class PromiseSemaphore {
    PromiseSemaphore parent;
    final Channel<Unit> ticketChannel = ExecutorKt.newChannel(Channel.UNLIMITED);
    final Channel<Unit> darkChannel = ExecutorKt.newChannel(Channel.UNLIMITED);

    public PromiseSemaphore(int available) {
        Post(available);
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
