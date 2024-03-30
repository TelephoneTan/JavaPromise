package pub.telephone.javapromise.async.promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PromiseCancelledBroadcaster extends PromiseCancelledBroadcast implements pub.telephone.javapromise.async.kpromise.PromiseCancelledBroadcaster {

    public synchronized void Broadcast() {
        IsActive.set(false);
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
