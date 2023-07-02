package pub.telephone.javapromise.async.promise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PromiseCancelledBroadcast {
    final public AtomicBoolean IsActive = new AtomicBoolean(true);
    protected final List<Runnable> Listeners = new ArrayList<>();

    public synchronized void Listen(Runnable r) {
        if (!IsActive.get()) {
            r.run();
            return;
        }
        Listeners.add(r);
    }

    protected synchronized void Broadcast() {
        IsActive.set(false);
        for (Runnable r : Listeners) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
