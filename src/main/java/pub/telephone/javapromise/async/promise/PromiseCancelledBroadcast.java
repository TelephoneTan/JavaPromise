package pub.telephone.javapromise.async.promise;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PromiseCancelledBroadcast implements pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcast {
    final public AtomicBoolean IsActive = new AtomicBoolean(true);
    protected final HashMap<Object, Runnable> Listeners = new HashMap<>();

    public synchronized Object Listen(Runnable r) {
        if (!IsActive.get()) {
            r.run();
            return null;
        }
        Object key = new Object();
        Listeners.put(key, r);
        return key;
    }

    public synchronized void UnListen(Object key) {
        Listeners.remove(key);
    }

    @Override
    public boolean isActive() {
        return IsActive.get();
    }

    @NotNull
    @Override
    public Object listen(@NotNull Runnable r) {
        return Listen(r);
    }

    @Override
    public void unListen(@NotNull Object key) {
        UnListen(key);
    }
}
