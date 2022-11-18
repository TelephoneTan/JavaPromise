package pub.telephone.javapromise.async.promise;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.sync.Mutex;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Promise<T> {

    T value;
    final CountDownLatch success = new CountDownLatch(1);
    Throwable reason;
    final CountDownLatch fail = new CountDownLatch(1);
    final CountDownLatch cancelled = new CountDownLatch(1);
    final Channel<Unit> settled = ExecutorKt.newChannel(Channel.RENDEZVOUS);
    final CountDownLatch settledLatch = new CountDownLatch(1);
    final Channel<Unit> setResult = ExecutorKt.newChannel(1);
    final CountDownLatch go = new CountDownLatch(1);
    final PromiseSemaphore semaphore;

    final PromiseJob<T> job;

    final AtomicInteger timeoutSN = new AtomicInteger(0);

    void settle() {
        if (semaphore != null) {
            semaphore.Release();
        }
        settledLatch.countDown();
        settled.close(null);
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
        settle();
        return true;
    }

    public Promise<T> Await() {
        try {
            settledLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return this;
    }

    public static <T> void AwaitAll(List<Promise<T>> all) {
        if (all != null) {
            for (Promise<T> promise : all) {
                promise.Await();
            }
        }
    }

    public boolean TryAwait() {
        try {
            return settledLatch.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return true;
        }
    }

    void awaitSettle(runThrowsThrowable then, errorListener onError) {
        ExecutorKt.awaitClose(settled, settledLatch, then::run, ExecutorKt.normalContinuation(onError::onError));
    }

    boolean succeeded() {
        try {
            return success.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    boolean failed() {
        try {
            return fail.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    boolean cancelled() {
        try {
            return cancelled.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    interface errorListener {
        void onError(Throwable e);
    }

    interface runThrowsThrowable {
        void run() throws Throwable;
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
        }, onError);
    }

    void go() {
        go.countDown();
        ExecutorKt.submitAsync(() -> {
            try {
                go.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                job.Do(new PromiseResolver<T>() {
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
                }, new PromiseRejector() {
                    @Override
                    void cancel() {
                        Cancel();
                    }

                    @Override
                    public void Reject(Throwable reason) {
                        fail(reason);
                    }
                });
            } catch (Throwable e) {
                fail(e);
            }
        }, this::fail);
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
                }, reportError);
            }
        }
    }

    static <S, T, U> Promise<S> dependOn(PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, U, S> f, PromiseRejectedListener<S> r, PromiseCancelledListener c, PromiseSettledListener s, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromiseList) {
        return new Promise<>((resolver, rejector) -> {
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
            ExecutorKt.awaitGroup(channel, totalNum, latch, () -> ExecutorKt.withLock(new Mutex[]{rv, rr, rc, rs, ov, or, oc, os}, () -> {
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
                                    Object res = f.OnFulfilled(new PromiseCompoundResult<T, U>()
                                            .setRequiredValueList(requiredValue)
                                            .setOptionalValueList(optionalValue)
                                            .setOptionalReasonList(optionalReason)
                                            .setOptionalCancelFlagList(optionalCancel)
                                            .setOptionalSuccessFlagList(optionalSuccess)
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
                                    Object res = r.OnRejected(reasonF);
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
                        Promise<?> promise = s.OnSettled();
                        if (promise != null) {
                            promise.awaitSettle(() -> {
                                if (promise.cancelled()) {
                                    rejector.cancel();
                                }
                                if (promise.failed()) {
                                    rejector.Reject(promise.reason);
                                }
                                afterFinally.run();
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

    public <S> Promise<S> Then(PromiseFulfilledListener<T, S> onFulfilled) {
        return dependOn(null, value -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0)), null, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Then(PromiseSemaphore semaphore, PromiseFulfilledListener<T, S> onFulfilled) {
        return dependOn(semaphore, value -> onFulfilled == null ? null : onFulfilled.OnFulfilled(value.RequiredValueList.get(0)), null, null, null, Collections.singletonList(this), null);
    }

    public static <S, T> Promise<S> ThenAll(PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(null, onFulfilled, null, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, Object, S> onFulfilled, List<Promise<T>> requiredPromiseList) {
        return dependOn(semaphore, onFulfilled, null, null, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(null, onFulfilled, null, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ThenAll(PromiseSemaphore semaphore, PromiseCompoundFulfilledListener<T, U, S> onFulfilled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(semaphore, onFulfilled, null, null, null, requiredPromiseList, optionalPromises);
    }

    public <S> Promise<S> Catch(PromiseRejectedListener<S> onRejected) {
        return dependOn(null, null, onRejected, null, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Catch(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected) {
        return dependOn(semaphore, null, onRejected, null, null, Collections.singletonList(this), null);
    }

    public static <S, T> Promise<S> CatchAll(PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(null, null, onRejected, null, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList) {
        return dependOn(semaphore, null, onRejected, null, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(null, null, onRejected, null, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> CatchAll(PromiseSemaphore semaphore, PromiseRejectedListener<S> onRejected, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(semaphore, null, onRejected, null, null, requiredPromiseList, optionalPromises);
    }

    public <S> Promise<S> ForCancel(PromiseCancelledListener onCancelled) {
        return dependOn(null, null, null, onCancelled, null, Collections.singletonList(this), null);
    }

    public <S> Promise<S> ForCancel(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled) {
        return dependOn(semaphore, null, null, onCancelled, null, Collections.singletonList(this), null);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return dependOn(null, null, null, onCancelled, null, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> ForCancelAll(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList) {
        return dependOn(semaphore, null, null, onCancelled, null, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(null, null, null, onCancelled, null, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> ForCancelAll(PromiseSemaphore semaphore, PromiseCancelledListener onCancelled, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(semaphore, null, null, onCancelled, null, requiredPromiseList, optionalPromises);
    }

    public <S> Promise<S> Finally(PromiseSettledListener onFinally) {
        return dependOn(null, null, null, null, onFinally, Collections.singletonList(this), null);
    }

    public <S> Promise<S> Finally(PromiseSemaphore semaphore, PromiseSettledListener onFinally) {
        return dependOn(semaphore, null, null, null, onFinally, Collections.singletonList(this), null);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(null, null, null, null, onFinally, requiredPromiseList, null);
    }

    public static <S, T> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList) {
        return dependOn(semaphore, null, null, null, onFinally, requiredPromiseList, null);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(null, null, null, null, onFinally, requiredPromiseList, optionalPromises);
    }

    public static <S, T, U> Promise<S> FinallyAll(PromiseSemaphore semaphore, PromiseSettledListener onFinally, List<Promise<T>> requiredPromiseList, List<Promise<U>> optionalPromises) {
        return dependOn(semaphore, null, null, null, onFinally, requiredPromiseList, optionalPromises);
    }

    protected Promise() {
        this.semaphore = null;
        this.job = null;
    }

    protected Promise(PromiseJob<T> job, PromiseSemaphore semaphore, boolean shouldWrapJobWithSemaphore) {
        this.semaphore = semaphore;
        this.job = (!shouldWrapJobWithSemaphore || semaphore == null) ? job :
                (resolver, rejector) -> semaphore.Acquire(
                        () -> job.Do(resolver, rejector),
                        ExecutorKt.normalContinuation(Promise.this::fail)
                );
        go();
    }

    public Promise(PromiseJob<T> job) {
        this(job, null, false);
    }

    public Promise(PromiseJob<T> job, PromiseSemaphore semaphore) {
        this(job, semaphore, true);
    }

    public static <S> Promise<S> Resolve(S value) {
        Promise<S> promise = new Promise<>();
        promise.succeed(value);
        return promise;
    }

    public static <S> Promise<S> Reject(Throwable reason) {
        Promise<S> promise = new Promise<>();
        promise.fail(reason);
        return promise;
    }

    public synchronized Promise<T> SetTimeout(Duration d, PromiseTimeOutListener onTimeOut) {
        boolean ok = true;
        try {
            ok = settledLatch.await(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!ok) {
            int currentSN = timeoutSN.incrementAndGet();
            ExecutorKt.delay(d.toNanos(), () -> {
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
}
