package pub.telephone.javapromise.async.task.timed;

import kotlin.Unit;
import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.Async;
import pub.telephone.javapromise.async.promise.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TimedTask {
    static final Throwable archivedError = new Throwable("定时任务已归档");
    final AtomicReference<Duration> interval;
    final PromiseCancellableJob<Boolean> job;
    final PromiseSemaphore semaphore;
    final PromiseCancelledBroadcast scopeCancelledBroadcast;
    final Object scopeUnListenKey;
    final boolean lifeLimited;
    final Channel<Integer> lifeTimes;
    final Promise<Integer> promise;
    final PromiseCancelledBroadcaster cancelledBroadcaster = new PromiseCancelledBroadcaster();
    final Channel<token> token = ExecutorKt.newChannel(1);
    final Channel<token> block = ExecutorKt.newChannel(1);
    final Channel<Unit> archived = ExecutorKt.newChannel(Channel.RENDEZVOUS);
    final Channel<Unit> modifying = ExecutorKt.newChannel(1);
    final Channel<Integer> succeeded = ExecutorKt.newChannel(1);
    final Channel<Throwable> failed = ExecutorKt.newChannel(1);
    final Channel<Unit> cancelled = ExecutorKt.newChannel(1);
    final AtomicInteger succeededTimes = new AtomicInteger(0);
    final Channel<Unit> started = ExecutorKt.newChannel(1);

    protected TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseCancellableJob<Boolean> job, boolean lifeLimited, int lifeTimes, PromiseSemaphore semaphore) {
        this.interval = new AtomicReference<>(interval);
        this.job = job;
        this.lifeLimited = lifeLimited;
        this.lifeTimes = ExecutorKt.newChannel(1);
        ExecutorKt.trySend(this.lifeTimes, lifeLimited ? lifeTimes : 0);
        this.semaphore = semaphore;
        this.scopeCancelledBroadcast = scopeCancelledBroadcast;
        this.scopeUnListenKey = this.scopeCancelledBroadcast != null ?
                this.scopeCancelledBroadcast.Listen(this::Cancel) :
                null;
        Promise<Integer> p = new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(
                succeeded,
                resolver::Resolve,
                failed,
                rejector::Reject,
                cancelled,
                v -> {
                },
                ExecutorKt.noErrorContinuation()));
        promise = p.Finally(() -> {
            leaveScope();
            return null;
        }).Then(value -> p);
    }

    public TimedTask(Duration interval, PromiseJob<Boolean> job) {
        this(null, interval, job);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseJob<Boolean> job) {
        this(scopeCancelledBroadcast, interval, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), false, 0, null);
    }

    public TimedTask(Duration interval, PromiseCancellableJob<Boolean> job) {
        this(null, interval, job);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseCancellableJob<Boolean> job) {
        this(scopeCancelledBroadcast, interval, job, false, 0, null);
    }

    public TimedTask(Duration interval, PromiseJob<Boolean> job, PromiseSemaphore semaphore) {
        this(null, interval, job, semaphore);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseJob<Boolean> job, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, interval, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), false, 0, semaphore);
    }

    public TimedTask(Duration interval, PromiseCancellableJob<Boolean> job, PromiseSemaphore semaphore) {
        this(null, interval, job, semaphore);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseCancellableJob<Boolean> job, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, interval, job, false, 0, semaphore);
    }

    public TimedTask(Duration interval, PromiseJob<Boolean> job, int times) {
        this(null, interval, job, times);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseJob<Boolean> job, int times) {
        this(scopeCancelledBroadcast, interval, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), true, times, null);
    }

    public TimedTask(Duration interval, PromiseCancellableJob<Boolean> job, int times) {
        this(null, interval, job, times);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseCancellableJob<Boolean> job, int times) {
        this(scopeCancelledBroadcast, interval, job, true, times, null);
    }

    public TimedTask(Duration interval, PromiseJob<Boolean> job, int times, PromiseSemaphore semaphore) {
        this(null, interval, job, times, semaphore);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseJob<Boolean> job, int times, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, interval, (resolver, rejector, cancelledBroadcast) -> job.Do(resolver, rejector), true, times, semaphore);
    }

    public TimedTask(Duration interval, PromiseCancellableJob<Boolean> job, int times, PromiseSemaphore semaphore) {
        this(null, interval, job, times, semaphore);
    }

    public TimedTask(PromiseCancelledBroadcast scopeCancelledBroadcast, Duration interval, PromiseCancellableJob<Boolean> job, int times, PromiseSemaphore semaphore) {
        this(scopeCancelledBroadcast, interval, job, true, times, semaphore);
    }

    void leaveScope() {
        if (this.scopeCancelledBroadcast != null) {
            this.scopeCancelledBroadcast.UnListen(this.scopeUnListenKey);
        }
    }

    public boolean IsArchived() {
        return archived.isClosedForSend();
    }

    <E> Promise<E> modify(PromiseJob<E> op) {
        Promise<E> res = new Promise<>((resolver, rejector) -> ExecutorKt.onSend(modifying, () -> {
            if (IsArchived()) {
                rejector.Reject(archivedError);
            } else {
                op.Do(resolver, rejector);
            }
        }, ExecutorKt.normalContinuation(rejector::Reject)));
        res.Finally(() -> {
            ExecutorKt.tryReceive(modifying);
            return null;
        });
        return res;
    }

    void end() {
        modify((resolver, rejector) -> {
            ExecutorKt.trySend(succeeded, succeededTimes.get());
            archived.close(null);
            resolver.Resolve(null);
        });
    }

    void error(Throwable e) {
        modify((resolver, rejector) -> {
            ExecutorKt.trySend(failed, e);
            archived.close(null);
            resolver.Resolve(null);
        });
    }

    public Promise<Object> Cancel() {
        return modify((resolver, rejector) -> {
            promise.Cancel();
            ExecutorKt.trySend(cancelled);
            cancelledBroadcaster.Broadcast();
            archived.close(null);
            resolver.Resolve(null);
        });
    }

    public Promise<Integer> Start(Duration... delay) {
        if (ExecutorKt.trySend(started)) {
            new Promise<>((resolver, rejector) -> {
                TimedTask.this.run();
                ExecutorKt.trySend(block, new token());
                resolver.Resolve(Resume(delay));
            }).Catch(reason -> {
                error(reason);
                return null;
            });
        }
        return promise;
    }

    public Promise<Object> Pause() {
        return modify((resolver, rejector) -> ExecutorKt.tryReceive(token, v -> {
            ExecutorKt.trySend(block, v);
            resolver.Resolve(null);
        }, () -> {
            throw new Exception("定时任务当前处于不可暂停状态，请稍后重试");
        }));
    }

    public Promise<Object> Resume(Duration... delay) {
        return modify((resolver, rejector) -> ExecutorKt.tryReceive(block, v -> {
            v.setDelay(delay == null || delay.length == 0 ? null : delay[0]);
            ExecutorKt.trySend(token, v);
            resolver.Resolve(null);
        }, () -> {
            throw new Exception("定时任务非暂停状态，不能恢复");
        }));
    }

    public Promise<Object> SetInterval(Duration interval) {
        return modify((resolver, rejector) -> {
            this.interval.set(interval);
            resolver.Resolve(null);
        });
    }

    public Promise<Integer> AddTimesBy(int delta) {
        if (!lifeLimited) {
            return Promise.Reject(new Exception("该定时任务无固定运行次数"));
        } else if (delta < 0) {
            return Promise.Reject(new Exception("增加的次数不能小于 0"));
        } else if (delta == 0) {
            return Promise.Resolve(0);
        } else {
            return modify((resolver, rejector) -> ExecutorKt.onReceive(lifeTimes, v -> {
                ExecutorKt.trySend(lifeTimes, v + delta);
                resolver.Resolve(delta);
            }, ExecutorKt.normalContinuation(rejector::Reject)));
        }
    }

    public Promise<Integer> ReduceTimesBy(int delta) {
        if (!lifeLimited) {
            return Promise.Reject(new Exception("该定时任务无固定运行次数"));
        } else if (delta < 0) {
            return Promise.Reject(new Exception("减少的次数不能小于 0"));
        } else if (delta == 0) {
            return Promise.Resolve(0);
        } else {
            return modify((resolver, rejector) -> ExecutorKt.onReceive(lifeTimes, v -> {
                int rd = delta;
                if (v <= 0) {
                    rd = 0;
                } else if (rd > v) {
                    rd = v;
                }
                ExecutorKt.trySend(lifeTimes, v - rd);
                resolver.Resolve(rd);
            }, ExecutorKt.normalContinuation(rejector::Reject)));
        }
    }

    Promise<Object> checkAlive(boolean consumeLife) {
        return new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(lifeTimes, v -> {
            boolean alive = !lifeLimited || v > 0;
            if (alive && consumeLife) {
                v--;
            }
            if (!alive) {
                end();
            }
            ExecutorKt.trySend(lifeTimes, v);
            if (alive) {
                resolver.Resolve(null);
            } else {
                rejector.Reject(null);
            }
        }, ExecutorKt.normalContinuation(rejector::Reject)));
    }

    Promise<Object> prepare() {
        return
                new Promise<token>((resolver, rejector) -> ExecutorKt.onReceive(token, v -> {
                    ExecutorKt.trySend(token, new token());
                    resolver.Resolve(v);
                }, archived, () -> {
                    throw archivedError;
                }, ExecutorKt.normalContinuation(rejector::Reject)))
                        .Then((PromiseFulfilledListener<token, token>) token ->
                                checkAlive(false)
                                        .Then((PromiseFulfilledListener<Object, token>) value -> token))
                        .Then(value -> value.delay == null ? null : Async.Delay(value.delay))
                        .Then(value -> checkAlive(true));
    }

    void run() {
        prepare()
                .Then((PromiseFulfilledListener<Object, Boolean>) value -> {
                    Promise<Boolean> p = new Promise<>(job, semaphore);
                    cancelledBroadcaster.Listen(p::Cancel);
                    return p;
                })
                .Then((PromiseFulfilledListener<Boolean, Boolean>) value -> {
                    succeededTimes.incrementAndGet();
                    return value;
                })
                .Then(value -> {
                    if (value != null && !value) {
                        end();
                        throw new Throwable("提前结束了");
                    }
                    return null;
                })
                .Then(value -> checkAlive(false))
                .Then(value1 -> {
                    Duration d = interval.get();
                    return d == null ? null : Async.Delay(d);
                })
                .Then(value12 -> checkAlive(false))
                .Finally(() -> {
                    cancelledBroadcaster.Clear();
                    return null;
                })
                .Then(value13 -> {
                    TimedTask.this.run();
                    return null;
                })
                .Catch(reason -> {
                    error(reason);
                    return null;
                })
        ;
    }
}
