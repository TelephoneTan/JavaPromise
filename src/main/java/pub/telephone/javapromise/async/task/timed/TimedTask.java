package pub.telephone.javapromise.async.task.timed;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.channels.Channel;
import pub.telephone.javapromise.async.promise.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TimedTask {
    final AtomicReference<Duration> duration;
    final PromiseJob<Boolean> job;
    final PromiseSemaphore semaphore;
    final boolean lifeLimited;
    final Channel<Integer> lifeTimes;
    final Promise<Integer> promise;
    final Channel<token> token = ExecutorKt.newChannel(1);
    final Channel<token> block = ExecutorKt.newChannel(1);
    final Channel<Unit> archived = ExecutorKt.newChannel(Channel.RENDEZVOUS);
    final Channel<Unit> modifying = ExecutorKt.newChannel(1);
    final Channel<Integer> succeeded = ExecutorKt.newChannel(1);
    final Channel<Throwable> failed = ExecutorKt.newChannel(1);
    final Channel<Unit> cancelled = ExecutorKt.newChannel(1);
    final AtomicInteger succeededTimes = new AtomicInteger(0);
    final Channel<Unit> started = ExecutorKt.newChannel(1);

    protected TimedTask(Duration interval, PromiseJob<Boolean> job, boolean lifeLimited, int lifeTimes, PromiseSemaphore semaphore) {
        this.duration = new AtomicReference<>(interval);
        this.job = job;
        this.lifeLimited = lifeLimited;
        this.lifeTimes = ExecutorKt.newChannel(1);
        ExecutorKt.trySend(this.lifeTimes, lifeLimited ? lifeTimes : 0);
        this.semaphore = semaphore;
        promise = new Promise<>((resolver, rejector) -> ExecutorKt.onReceive(
                succeeded,
                resolver::Resolve,
                failed,
                rejector::Reject,
                cancelled,
                v -> {
                },
                ExecutorKt.noErrorContinuation()));
    }

    public TimedTask(Duration duration, PromiseJob<Boolean> job) {
        this(duration, job, false, 0, null);
    }

    public TimedTask(Duration duration, PromiseJob<Boolean> job, PromiseSemaphore semaphore) {
        this(duration, job, false, 0, semaphore);
    }

    public TimedTask(Duration duration, PromiseJob<Boolean> job, int times) {
        this(duration, job, true, times, null);
    }

    public TimedTask(Duration duration, PromiseJob<Boolean> job, int times, PromiseSemaphore semaphore) {
        this(duration, job, true, times, semaphore);
    }

    boolean isArchived() {
        return archived.isClosedForSend();
    }

    <E> Promise<E> modify(PromiseJob<E> op) {
        Promise<E> res = new Promise<>((resolver, rejector) -> ExecutorKt.onSend(modifying, () -> {
            if (isArchived()) {
                rejector.Reject(new Exception("定时任务已归档"));
                return;
            }
            try {
                op.Do(resolver, rejector);
            } catch (Throwable e) {
                rejector.Reject(e);
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
            archived.close(null);
            resolver.Resolve(null);
        });
    }

    public Promise<Integer> Start(Duration... delay) {
        if (ExecutorKt.trySend(started)) {
            try {
                checkStopPoint(() -> {
                    TimedTask.this.run();
                    ExecutorKt.trySend(block, new token());
                    Resume(delay);
                }, true, null, ExecutorKt.normalContinuation(TimedTask.this::error));
            } catch (Throwable e) {
                error(e);
            }
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

    public Promise<Object> Resume(Duration... duration) {
        return modify((resolver, rejector) -> ExecutorKt.tryReceive(block, v -> {
            v.setDelay(duration == null || duration.length == 0 ? null : duration[0]);
            ExecutorKt.trySend(token, v);
            resolver.Resolve(null);
        }, () -> {
            throw new Exception("定时任务非暂停状态，不能恢复");
        }));
    }

    public Promise<Object> SetDuration(Duration duration) {
        return modify((resolver, rejector) -> {
            this.duration.set(duration);
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

    void checkStopPoint(RunThrowsThrowable then, boolean consumeLife, RunThrowsThrowable quitThen, Continuation<Unit> continuation) throws Throwable {
        if (isArchived()) {
            if (quitThen != null) {
                quitThen.run();
            }
            return;
        }
        ExecutorKt.onReceive(lifeTimes, v -> {
            try {
                if (lifeLimited && v <= 0) {
                    end();
                    if (quitThen != null) {
                        quitThen.run();
                    }
                    return;
                }
                if (consumeLife) {
                    v--;
                }
            } finally {
                ExecutorKt.trySend(lifeTimes, v);
            }
            then.run();
        }, continuation);
    }

    void prepare(RunThrowsThrowable then, RunThrowsThrowable quitThen, Continuation<Unit> continuation) {
        ExecutorKt.onReceive(token, v -> {
            ExecutorKt.trySend(token, new token());
            if (isArchived()) {
                if (quitThen != null) {
                    quitThen.run();
                }
                return;
            }
            RunThrowsThrowable doJob = () -> {
                if (isArchived()) {
                    if (quitThen != null) {
                        quitThen.run();
                    }
                    return;
                }
                then.run();
            };
            if (v.delay != null) {
                ExecutorKt.delay(v.delay.toNanos(), doJob, continuation);
            } else {
                doJob.run();
            }
        }, archived, quitThen == null ? () -> {
        } : quitThen, continuation);
    }

    void run() {
        new Promise<Boolean>((resolver, rejector) -> {
            Continuation<Unit> rejectThis = ExecutorKt.normalContinuation(rejector::Reject);
            RunThrowsThrowable quitThen = () -> resolver.Resolve(false);
            //
            prepare(() -> resolver.Resolve(new Promise<>(job, semaphore)), quitThen, rejectThis);
        }).Then(value -> new Promise<>((resolver, rejector) -> {
            succeededTimes.incrementAndGet();
            //
            Continuation<Unit> rejectThis = ExecutorKt.normalContinuation(rejector::Reject);
            RunThrowsThrowable quitThen = () -> resolver.Resolve(null);
            //
            if (value == null || value) {
                checkStopPoint(() -> {
                    RunThrowsThrowable afterDelay = () -> checkStopPoint(
                            TimedTask.this::run,
                            true,
                            quitThen,
                            rejectThis
                    );
                    Duration d = duration.get();
                    if (d != null) {
                        ExecutorKt.delay(
                                d.toNanos(),
                                afterDelay,
                                rejectThis
                        );
                    } else {
                        afterDelay.run();
                    }
                }, false, quitThen, rejectThis);
            } else {
                end();
                quitThen.run();
            }
        })).Catch(reason -> {
            error(reason);
            return null;
        });
    }
}
