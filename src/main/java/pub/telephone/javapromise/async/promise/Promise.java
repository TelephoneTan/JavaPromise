package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.sync.Mutex;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Promise<T> {

    final CountDownLatch success = new CountDownLatch(1);
    final CountDownLatch fail = new CountDownLatch(1);
    final CountDownLatch cancelled = new CountDownLatch(1);
    public final PromiseCancelledBroadcaster CancelledBroadcast = new PromiseCancelledBroadcaster();
    final Channel<Unit> settled = ExecutorKt.newChannel(Channel.RENDEZVOUS);
    final CountDownLatch settledLatch = new CountDownLatch(1);
    final Channel<Unit> setResult = ExecutorKt.newChannel(1);
    final CountDownLatch go = new CountDownLatch(1);
    final PromiseSemaphore semaphore;
    final PromiseStatefulJob<T> job;
    public final PromiseCancelledBroadcast ScopeCancelledBroadcast;
    final Object scopeUnListenKey;
    final AtomicInteger timeoutSN = new AtomicInteger(0);
    T value;
    Throwable reason;

    protected Promise(@Nullable PromiseCancelledBroadcast scopeCancelledBroadcast) {
        this.semaphore = null;
        this.job = null;
        this.ScopeCancelledBroadcast = scopeCancelledBroadcast;
        this.scopeUnListenKey = null;
    }

    protected Promise(@Nullable PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulJob<T> job, PromiseSemaphore semaphore, boolean shouldWrapJobWithSemaphore) {
        this.semaphore = semaphore;
        this.job = (!shouldWrapJobWithSemaphore || semaphore == null) ? job : (resolver, rejector, state) ->
                semaphore.Acquire(
                        () -> job.Do(resolver, rejector, state),
                        ExecutorKt.normalContinuation(Promise.this::fail)
                );
        this.ScopeCancelledBroadcast = scopeCancelledBroadcast;
        this.scopeUnListenKey = this.ScopeCancelledBroadcast != null ?
                this.ScopeCancelledBroadcast.Listen(Promise.this::Cancel) :
                null;
        go();
    }

    public Promise(PromiseJob<T> job) {
        this(null, job);
    }

    public Promise(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job) {
        this(
                scopeCancelledBroadcast,
                (resolver, rejector, state) -> job.Do(resolver, rejector),
                null,
                false
        );
    }

    public Promise(PromiseStatefulJob<T> job) {
        this(null, job);
    }

    public Promise(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulJob<T> job) {
        this(
                scopeCancelledBroadcast,
                job,
                null,
                false
        );
    }

    public Promise(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public Promise(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(
                scopeCancelledBroadcast,
                (resolver, rejector, state) -> job.Do(resolver, rejector),
                semaphore,
                true
        );
    }

    public Promise(PromiseStatefulJob<T> job, PromiseSemaphore semaphore) {
        this(null, job, semaphore);
    }

    public Promise(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulJob<T> job, PromiseSemaphore semaphore) {
        this(
                scopeCancelledBroadcast,
                job,
                semaphore,
                true
        );
    }

    public static <T> void AwaitAll(Iterable<? extends Promise<T>> all) {
        if (all != null) {
            for (Promise<T> promise : all) {
                promise.Await();
            }
        }
    }

    static <E> void settleAll(
            List<Promise<E>> promiseList,
            CountDownLatch latch,
            Channel<Unit> channel,
            List<E> valueList,
            Mutex valueListMutex,
            Throwable[] reasonList,
            Mutex reasonListMutex,
            boolean[] cancelFlagList,
            Mutex cancelFlagListMutex,
            boolean[] successFlagList,
            Mutex successFlagListMutex
    ) {
        if (promiseList != null) {
            Continuation<Unit> noErrorContinuation = ExecutorKt.noErrorContinuation();
            Continuation<Unit> wontSuspendContinuation = ExecutorKt.wontSuspendContinuation();
            for (int i = 0; i < promiseList.size(); i++) {
                Promise<E> promise = promiseList.get(i);
                int ii = i;
                errorListener reportError = e -> ExecutorKt.withLock(new Mutex[]{reasonListMutex, successFlagListMutex}, () -> {
                    successFlagList[ii] = false;
                    reasonList[ii] = e;
                    //
                    latch.countDown();
                    channel.send(Unit.INSTANCE, wontSuspendContinuation);
                }, noErrorContinuation);
                promise.awaitSettle(() -> {
                    if (promise.cancelled()) {
                        ExecutorKt.withLock(cancelFlagListMutex, () -> {
                            cancelFlagList[ii] = true;
                            //
                            latch.countDown();
                            channel.send(Unit.INSTANCE, wontSuspendContinuation);
                        }, noErrorContinuation);
                    } else if (promise.failed()) {
                        reportError.onError(promise.reason);
                    } else if (promise.succeeded()) {
                        ExecutorKt.withLock(new Mutex[]{valueListMutex, successFlagListMutex}, () -> {
                            successFlagList[ii] = true;
                            valueList.set(ii, promise.value);
                            //
                            latch.countDown();
                            channel.send(Unit.INSTANCE, wontSuspendContinuation);
                        }, noErrorContinuation);
                    }
                }, null, null, reportError);
            }
        }
    }

    static <S, T, U> Promise<S> dependOn(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulCompoundFulfilledListener<T, U, S> f, PromiseStatefulRejectedListener<S> r, PromiseCancelledListener c, PromiseStatefulSettledListener s, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromiseList) {
        return new Promise<>(scopeCancelledBroadcast, (resolver, rejector, state) -> {
            int requiredNum = requiredPromiseList == null ? 0 : requiredPromiseList.size();
            int optionalNum = optionalPromiseList == null ? 0 : optionalPromiseList.size();
            int totalNum = requiredNum + optionalNum;
            //
            List<T> requiredValue = new ArrayList<T>(requiredNum) {{
                for (int i = 0; i < requiredNum; i++) {
                    add(null);
                }
            }};
            Mutex rv = ExecutorKt.newMutex();
            Throwable[] requiredReason = new Throwable[requiredNum];
            Mutex rr = ExecutorKt.newMutex();
            boolean[] requiredCancel = new boolean[requiredNum];
            Mutex rc = ExecutorKt.newMutex();
            boolean[] requiredSuccess = new boolean[requiredNum];
            Mutex rs = ExecutorKt.newMutex();
            //
            List<U> optionalValue = new ArrayList<U>(optionalNum) {{
                for (int i = 0; i < optionalNum; i++) {
                    add(null);
                }
            }};
            Mutex ov = ExecutorKt.newMutex();
            Throwable[] optionalReason = new Throwable[optionalNum];
            Mutex or = ExecutorKt.newMutex();
            boolean[] optionalCancel = new boolean[optionalNum];
            Mutex oc = ExecutorKt.newMutex();
            boolean[] optionalSuccess = new boolean[optionalNum];
            Mutex os = ExecutorKt.newMutex();
            //
            CountDownLatch latch = new CountDownLatch(totalNum);
            Channel<Unit> channel = ExecutorKt.newChannel(totalNum);
            settleAll(
                    requiredPromiseList,
                    latch,
                    channel,
                    requiredValue,
                    rv,
                    requiredReason,
                    rr,
                    requiredCancel,
                    rc,
                    requiredSuccess,
                    rs
            );
            settleAll(
                    optionalPromiseList,
                    latch,
                    channel,
                    optionalValue,
                    ov,
                    optionalReason,
                    or,
                    optionalCancel,
                    oc,
                    optionalSuccess,
                    os
            );
            Continuation<Unit> rejectMe = ExecutorKt.normalContinuation(rejector::Reject);
            ExecutorKt.awaitGroup(channel, totalNum, () -> ExecutorKt.withLock(new Mutex[]{rv, rr, rc, rs, ov, or, oc, os}, () -> {
                boolean succeeded = true;
                boolean cancelled = false;
                Throwable reason = null;
                for (boolean cf : requiredCancel) {
                    if (cf) {
                        cancelled = true;
                        break;
                    }
                }
                for (int i = 0; i < requiredSuccess.length; i++) {
                    boolean s1 = requiredSuccess[i];
                    if (!s1) {
                        succeeded = false;
                        reason = requiredReason[i];
                        break;
                    }
                }
                //
                boolean succeededF = succeeded;
                boolean cancelledF = cancelled;
                Throwable reasonF = reason;
                RunThrowsThrowable rest = () -> {
                    RunThrowsThrowable afterFinally = () -> {
                        if (!cancelledF) {
                            if (succeededF) {
                                if (f == null) {
                                    resolver.Resolve((S) null);
                                } else {
                                    Object res = f.OnFulfilled(
                                            new PromiseCompoundResult<T, U>()
                                                    .setRequiredValueList(requiredValue)
                                                    .setOptionalValueList(optionalValue)
                                                    .setOptionalReasonList(optionalReason)
                                                    .setOptionalCancelFlagList(optionalCancel)
                                                    .setOptionalSuccessFlagList(optionalSuccess),
                                            state
                                    );
                                    try {
                                        resolver.Resolve((Promise<S>) res);
                                    } catch (Throwable e) {
                                        resolver.Resolve((S) res);
                                    }
                                }
                            } else {
                                if (r == null) {
                                    rejector.Reject(reasonF);
                                } else {
                                    Object res = r.OnRejected(reasonF, state);
                                    try {
                                        resolver.Resolve((Promise<S>) res);
                                    } catch (Throwable e) {
                                        resolver.Resolve((S) res);
                                    }
                                }
                            }
                        }
                    };
                    if (cancelledF) {
                        rejector.cancel();
                        if (c != null) {
                            ExecutorKt.submitAsync(c::OnCancelled, e -> {
                            });
                        }
                    }
                    if (s != null) {
                        Promise<?> promise = s.OnSettled(state);
                        if (promise != null) {
                            promise.awaitSettle(() -> {
                                if (promise.cancelled()) {
                                    rejector.cancel();
                                }
                                if (promise.failed()) {
                                    rejector.Reject(promise.reason);
                                }
                                afterFinally.run();
                            }, state.self.settled, () -> {
                                if (state.self.cancelled()) {
                                    promise.Cancel();
                                }
                            }, rejector::Reject);
                            return;
                        }
                    }
                    afterFinally.run();
                };
                //
                if (semaphore != null) {
                    semaphore.Acquire(rest::run, rejectMe);
                } else {
                    rest.run();
                }
            }, rejectMe), rejectMe);
        }, semaphore, false);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return ThenAll((PromiseCancelledBroadcast) null, onFulfilled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, (value, cancelledBroadcast) -> onFulfilled.OnFulfilled(value), null, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ThenAll(PromiseStatefulCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return ThenAll((PromiseCancelledBroadcast) null, onFulfilled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, onFulfilled, null, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return ThenAll(null, semaphore, onFulfilled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, (value, cancelledBroadcast) -> onFulfilled.OnFulfilled(value), null, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseStatefulCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return ThenAll(null, semaphore, onFulfilled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, onFulfilled, null, null, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ThenAll((PromiseCancelledBroadcast) null, onFulfilled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, (value, cancelledBroadcast) -> onFulfilled.OnFulfilled(value), null, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseStatefulCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ThenAll((PromiseCancelledBroadcast) null, onFulfilled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, onFulfilled, null, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ThenAll(null, semaphore, onFulfilled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, (value, cancelledBroadcast) -> onFulfilled.OnFulfilled(value), null, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseStatefulCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ThenAll(null, semaphore, onFulfilled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, onFulfilled, null, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T> Promise<S> CatchAll(PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return CatchAll((PromiseCancelledBroadcast) null, onRejected, requiredPromiseList);
    }

    public static <S, T> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, null, (value, cancelledBroadcast) -> onRejected.OnRejected(value), null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> CatchAll(PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return CatchAll((PromiseCancelledBroadcast) null, onRejected, requiredPromiseList);
    }

    public static <S, T> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, null, onRejected, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return CatchAll(null, semaphore, onRejected, requiredPromiseList);
    }

    public static <S, T> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, (value, cancelledBroadcast) -> onRejected.OnRejected(value), null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return CatchAll(null, semaphore, onRejected, requiredPromiseList);
    }

    public static <S, T> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, onRejected, null, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return CatchAll((PromiseCancelledBroadcast) null, onRejected, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, null, (value, cancelledBroadcast) -> onRejected.OnRejected(value), null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return CatchAll((PromiseCancelledBroadcast) null, onRejected, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, null, onRejected, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return CatchAll(null, semaphore, onRejected, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, (value, cancelledBroadcast) -> onRejected.OnRejected(value), null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return CatchAll(null, semaphore, onRejected, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, onRejected, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return ForCancelAll((PromiseCancelledBroadcast) null, onCancelled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, null, null, onCancelled, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return ForCancelAll(null, semaphore, onCancelled, requiredPromiseList);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, onCancelled, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ForCancelAll((PromiseCancelledBroadcast) null, onCancelled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, null, null, onCancelled, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return ForCancelAll(null, semaphore, onCancelled, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, onCancelled, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return FinallyAll((PromiseCancelledBroadcast) null, onFinally, requiredPromiseList);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), requiredPromiseList, null);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return FinallyAll((PromiseCancelledBroadcast) null, onFinally, requiredPromiseList);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, null, null, null, null, onFinally, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return FinallyAll(null, semaphore, onFinally, requiredPromiseList);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), requiredPromiseList, null);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return FinallyAll(null, semaphore, onFinally, requiredPromiseList);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, null, onFinally, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return FinallyAll((PromiseCancelledBroadcast) null, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return FinallyAll((PromiseCancelledBroadcast) null, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, null, null, null, null, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return FinallyAll(null, semaphore, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return FinallyAll(null, semaphore, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseCancelledBroadcast scopeCancelledBroadcast, PromiseSemaphore semaphore, PromiseStatefulSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(scopeCancelledBroadcast, semaphore, null, null, null, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S> Promise<S> Resolve(S value) {
        return Resolve(null, value);
    }

    public static <S> Promise<S> Resolve(PromiseCancelledBroadcast scopeCancelledBroadcast, S value) {
        Promise<S> promise = new Promise<>(scopeCancelledBroadcast);
        promise.succeed(value);
        return promise;
    }

    public static <S> Promise<S> Reject(Throwable reason) {
        return Reject(null, reason);
    }

    public static <S> Promise<S> Reject(PromiseCancelledBroadcast scopeCancelledBroadcast, Throwable reason) {
        Promise<S> promise = new Promise<>(scopeCancelledBroadcast);
        promise.fail(reason);
        return promise;
    }

    public static <S> Promise<S> Cancelled() {
        return Cancelled(null);
    }

    public static <S> Promise<S> Cancelled(PromiseCancelledBroadcast scopeCancelledBroadcast) {
        Promise<S> promise = new Promise<>(scopeCancelledBroadcast);
        promise.Cancel();
        return promise;
    }

    void settle() {
        if (semaphore != null) {
            semaphore.Release();
        }
        settledLatch.countDown();
        settled.close(null);
        if (ScopeCancelledBroadcast != null) {
            ScopeCancelledBroadcast.UnListen(scopeUnListenKey);
        }
    }

    void succeed(T value) {
        if (!ExecutorKt.trySend(setResult)) {
            return;
        }
        this.value = value;
        success.countDown();
        settle();
    }

    void fail(Throwable reason) {
        if (!ExecutorKt.trySend(setResult)) {
            return;
        }
        this.reason = reason;
        fail.countDown();
        settle();
    }

    public boolean Cancel() {
        if (!ExecutorKt.trySend(setResult)) {
            return false;
        }
        cancelled.countDown();
        CancelledBroadcast.Broadcast();
        settle();
        return true;
    }

    public Promise<T> Await() {
        try {
            settledLatch.await();
        } catch (InterruptedException ignored) {
        }
        return this;
    }

    public boolean TryAwait() {
        try {
            return settledLatch.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return true;
        }
    }

    void awaitSettle(
            runThrowsThrowable then,
            @Nullable Channel<Unit> outerSettled,
            @Nullable runThrowsThrowable outerThen,
            errorListener onError
    ) {
        ExecutorKt.awaitClose(
                settled,
                then::run,
                outerSettled,
                outerThen == null ? null : outerThen::run,
                ExecutorKt.normalContinuation(onError::onError)
        );
    }

    boolean succeeded() {
        try {
            return success.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    boolean failed() {
        try {
            return fail.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    boolean cancelled() {
        try {
            return cancelled.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    void copyStateTo(Promise<T> promise, errorListener onError) {
        awaitSettle(() -> {
            if (cancelled()) {
                promise.Cancel();
            }
            if (succeeded()) {
                promise.succeed(value);
            }
            if (failed()) {
                promise.fail(reason);
            }
        }, promise.settled, () -> {
            if (promise.cancelled()) {
                Promise.this.Cancel();
            }
        }, onError);
    }

    void go() {
        go.countDown();
        ExecutorKt.submitAsync(() -> {
            try {
                go.await();
            } catch (InterruptedException ignored) {
            }
            try {
                PromiseResolver<T> resolver = new PromiseResolver<T>() {
                    @Override
                    public void Resolve(T value) {
                        succeed(value);
                    }

                    @Override
                    public void Resolve(Promise<T> promise) {
                        if (promise == null) {
                            succeed(null);
                        } else {
                            promise.copyStateTo(Promise.this, Promise.this::fail);
                        }
                    }
                };
                PromiseRejector rejector = new PromiseRejector() {
                    @Override
                    void cancel() {
                        Cancel();
                    }

                    @Override
                    public void Reject(Throwable reason) {
                        fail(reason);
                    }
                };
                job.Do(resolver, rejector, new PromiseState<>(ScopeCancelledBroadcast, CancelledBroadcast, Promise.this));
            } catch (Throwable e) {
                fail(e);
            }
        }, this::fail);
    }

    public <S> Promise<S> Then(PromiseFulfilledListener<T, S> onFulfilled) {
        return dependOn(ScopeCancelledBroadcast, null, (value, cancelledBroadcast) -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0)), null, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Then(PromiseStatefulFulfilledListener<T, S> onFulfilled) {
        return dependOn(ScopeCancelledBroadcast, null, (value, state) -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0), state), null, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Then(PromiseSemaphore semaphore, PromiseFulfilledListener<T, S> onFulfilled) {
        return dependOn(ScopeCancelledBroadcast, semaphore, (value, cancelledBroadcast) -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0)), null, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Then(PromiseSemaphore semaphore, PromiseStatefulFulfilledListener<T, S> onFulfilled) {
        return dependOn(ScopeCancelledBroadcast, semaphore, (value, state) -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0), state), null, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Catch(PromiseRejectedListener<S> onRejected) {
        return dependOn(ScopeCancelledBroadcast, null, null, (reason, cancelledBroadcast) -> onRejected.OnRejected(reason), null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Catch(PromiseStatefulRejectedListener<S> onRejected) {
        return dependOn(ScopeCancelledBroadcast, null, null, onRejected, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Catch(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected) {
        return dependOn(ScopeCancelledBroadcast, semaphore, null, (reason, cancelledBroadcast) -> onRejected.OnRejected(reason), null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Catch(PromiseSemaphore semaphore, PromiseStatefulRejectedListener<S> onRejected) {
        return dependOn(ScopeCancelledBroadcast, semaphore, null, onRejected, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> ForCancel(PromiseCancelledListener onCancelled) {
        return dependOn(ScopeCancelledBroadcast, null, null, null, onCancelled, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> ForCancel(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled) {
        return dependOn(ScopeCancelledBroadcast, semaphore, null, null, onCancelled, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Finally(PromiseSettledListener onFinally) {
        return dependOn(ScopeCancelledBroadcast, null, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), Collections.singletonList(this), null);
    }

    public <S> Promise<S> Finally(PromiseStatefulSettledListener onFinally) {
        return dependOn(ScopeCancelledBroadcast, null, null, null, null, onFinally, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Finally(PromiseSemaphore semaphore, PromiseSettledListener onFinally) {
        return dependOn(ScopeCancelledBroadcast, semaphore, null, null, null, cancelledBroadcast -> onFinally.OnSettled(), Collections.singletonList(this), null);
    }

    public <S> Promise<S> Finally(PromiseSemaphore semaphore, PromiseStatefulSettledListener onFinally) {
        return dependOn(ScopeCancelledBroadcast, semaphore, null, null, null, onFinally, Collections.singletonList(this), null);
    }

    public synchronized Promise<T> SetTimeout(Duration d, PromiseTimeOutListener onTimeOut) {
        boolean ok = true;
        try {
            ok = settledLatch.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        if (!ok) {
            int currentSN = timeoutSN.incrementAndGet();
            ExecutorKt.delay(d, () -> {
                if (timeoutSN.get() == currentSN) {
                    if (Cancel() && onTimeOut != null) {
                        ExecutorKt.submitAsync(() -> onTimeOut.OnTimeOut(d), e -> {
                        });
                    }
                }
            }, ExecutorKt.cancelContinuation());
        }
        return this;
    }

    public synchronized Promise<T> SetTimeout(Duration d) {
        return SetTimeout(d, null);
    }

    interface errorListener {
        void onError(Throwable e);
    }

    interface runThrowsThrowable {
        void run() throws Throwable;
    }

    public static void InitThreadPool(int n) {
        ExecutorKt.initDispatcher(n);
    }
}
