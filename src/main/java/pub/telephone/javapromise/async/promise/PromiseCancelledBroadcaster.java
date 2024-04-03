package pub.telephone.javapromise.async.promise;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class PromiseCancelledBroadcaster implements PromiseCancelledBroadcast, pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcaster {
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private final HashMap<Object, Runnable> Listeners = new HashMap<>();

    @Override
    public boolean isActive() {
        return isActive.get();
    }

    @NotNull
    @Override
    public synchronized Object listen(@NotNull Runnable r) {
        if (!isActive.get()) {
            r.run();
            return new Object();
        }
        Object key = new Object();
        Listeners.put(key, r);
        return key;
    }

    @Override
    public synchronized void unListen(@NotNull Object key) {
        Listeners.remove(key);
    }
    public synchronized void Broadcast() {
        isActive.set(false);
        List<Runnable> todos = new ArrayList<>();
        for (Map.Entry<Object, Runnable> e : Listeners.entrySet()) {
            todos.add(e.getValue());
        }
        Listeners.clear();
        for (Runnable r : todos) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public synchronized void Clear() {
        Listeners.clear();
    }

    @Override
    public void broadcast() {
        this.Broadcast();
    }
}
