package pub.telephone.javapromise.async.kpromise

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

class JobResult private constructor() {
    companion object {
        internal val INSTANCE = JobResult()
    }
}

class PromiseState<RESULT>(
        internal val self: Promise<RESULT>
)

interface ResolvePack<RESULT> : PromiseScope, PromiseCancelledBroadcast {
    override val scopeCancelledBroadcast: PromiseCancelledBroadcast
    override val isActive: Boolean
        get() = scopeCancelledBroadcast.isActive

    override fun listen(r: java.lang.Runnable): Any {
        return scopeCancelledBroadcast.listen(r)
    }

    override fun unListen(key: Any) {
        scopeCancelledBroadcast.unListen(key)
    }
    suspend fun rsv(v: RESULT): JobResult
    suspend fun rsp(p: Promise<RESULT>): JobResult
    fun rej(e: Throwable): JobResult
    fun state(): PromiseState<RESULT>
}

interface ValuePack<VALUE, RESULT> : ResolvePack<RESULT> {
    val value: VALUE
}

interface ReasonPack<RESULT> : ResolvePack<RESULT> {
    val reason: Throwable
}

interface ForwardPack<RESULT> : ResolvePack<RESULT> {
    suspend fun forward(): JobResult
}

interface JobPack<RESULT> : ResolvePack<RESULT> {
    fun waiting(): JobResult = JobResult.INSTANCE
}

private val factory = ThreadFactory {
    val thread = Thread(it)
    thread.isDaemon = true
    thread
}

private fun newFixedDispatcher(n: Int) = Executors.newFixedThreadPool(n, factory).asCoroutineDispatcher()

private fun newCachedDispatcher() = Executors.newCachedThreadPool(factory).asCoroutineDispatcher()

private val dispatcher = AtomicReference(newCachedDispatcher())

private fun initDispatcher(n: Int) {
    dispatcher.set(newFixedDispatcher(n))
}

enum class Status {
    RUNNING,
    SUCCEED,
    FAILED,
    CANCELLED,
}

typealias PromiseJob<RESULT> = suspend JobPack<RESULT>.() -> JobResult
typealias SucceededHandler<RESULT, NEXT_RESULT> = suspend ValuePack<RESULT, NEXT_RESULT>.() -> JobResult
typealias SucceededConsumer<RESULT> = suspend ValuePack<RESULT, Any?>.() -> Unit
typealias FailedHandler<NEXT_RESULT> = suspend ReasonPack<NEXT_RESULT>.() -> JobResult
typealias FailedConsumer = suspend ReasonPack<Any?>.() -> Unit
typealias CancelledListener = suspend () -> Unit
typealias FinallyHandler<LAST_RESULT> = suspend ForwardPack<LAST_RESULT>.() -> JobResult
typealias FinallyConsumer<LAST_RESULT> = suspend ForwardPack<LAST_RESULT>.() -> Unit

data class PromiseConfig internal constructor(
        val semaphore: PromiseSemaphore?,
        val scopeCancelledBroadcast: PromiseCancelledBroadcast?,
        internal val shouldWrapJobWithSemaphore: Boolean
) {
    constructor(
            scopeCancelledBroadcast: PromiseCancelledBroadcast? = null,
            semaphore: PromiseSemaphore? = null,
    ) : this(semaphore, scopeCancelledBroadcast, true)

    companion object {
        val EMPTY_CONFIG = PromiseConfig()
    }
}

class Promise<RESULT> private constructor(
        val job: PromiseJob<RESULT>?,
        private val config: PromiseConfig,
) {
    constructor(
            config: PromiseConfig?,
            job: PromiseJob<RESULT>,
    ) : this(job, config ?: PromiseConfig.EMPTY_CONFIG)

    private val status = AtomicReference(Status.RUNNING)
    private val settled = Channel<Any>()
    private val submit = Channel<Any?>(1)
    private val setTimeoutMutex = Any()
    private var timeoutSN = 0
    private var timeoutTriggered = false
    private var value: RESULT? = null
    private var reason: Throwable? = null
    private val semaphoreMutex = Any()
    private var semaphoreAcquired = false
    private var semaphoreReleased = false

    private suspend fun acquireSemaphore() {
        config.semaphore?.run semaphoreRun@{
            this@semaphoreRun.acquire()
            synchronized(semaphoreMutex) {
                semaphoreAcquired = true
                if (semaphoreReleased) {
                    this@semaphoreRun.release()
                    throw CancellationException()
                }
            }
        }
    }

    private fun releaseSemaphore() {
        config.semaphore?.run semaphoreRun@{
            synchronized(semaphoreMutex) {
                if (semaphoreAcquired) {
                    this@semaphoreRun.release()
                }
                semaphoreReleased = true
            }
        }
    }

    private val runningJob: Job? = job?.run jobRun@{
        if (config.shouldWrapJobWithSemaphore) {
            wrap@{
                acquireSemaphore()
                this@jobRun.invoke(this@wrap)
            }
        } else {
            this@jobRun
        }
    }?.let {
        CoroutineScope(dispatcher.get() + CoroutineExceptionHandler { _, _ ->
            // 不在这里捕获异常，因为 CancellationException 不会在这里被捕获，而实际上
            // 并不是所有内部抛出的 CancellationException 都意味着此 Job 已经被取消。
            // 例如在此 Job 内部使用 withTimeout(){} 时，该代码块超时后会抛出
            // TimeoutCancellationException（是 CancellationException 的一个子类），
            // 但这种情形下该 TimeoutCancellationException 是 Job 内部逻辑抛出的异常，
            // 与此 Job 的取消状态无关，该异常应被捕获并使此 Promise 转为“失败”状态。
            // 因为这里无法捕获所有该被捕获的异常，所以将异常捕获放在 invokeOnCompletion 中。
        }).launch {
            perform { it(object : JobPack<RESULT>, ResolvePack<RESULT> by this {}) }
        }.apply {
            invokeOnCompletion {
                when (it) {
                    is CancellationException -> if (status.get() != Status.CANCELLED) fail(it)
                    is Throwable -> fail(it)
                }
            }
        }
    }
    private val cancelledBroadcaster: PromiseCancelledBroadcaster = pub.telephone.javapromise.async.promise.PromiseCancelledBroadcaster()

    // 此字段必须放在最后一个，因为 cancel 方法可能会被立即调用
    private val scopeUnListenKey = config.scopeCancelledBroadcast?.listen(this::cancel)

    private fun settle(status: Status, op: () -> Unit = {}): Boolean {
        return submit.trySend(null).takeIf { it.isSuccess }?.run {
            op()
            this@Promise.status.set(status)
            settled.close()
            // scopeUnListenKey 为空时不一定表示没有监听，有可能是初始化字段时已经取消导致 cancel 被立即调用
            scopeUnListenKey?.let { config.scopeCancelledBroadcast?.unListen(it) }
            releaseSemaphore()
            if (status == Status.CANCELLED) {
                runningJob?.cancel()
                cancelledBroadcaster.broadcast()
            }
        }?.let { true } ?: false
    }

    private fun succeed(v: RESULT) = settle(Status.SUCCEED) { value = v }

    private fun fail(e: Throwable) = settle(Status.FAILED) { reason = e }

    fun cancel() = settle(Status.CANCELLED)

    val onSettled get() = settled.onReceiveCatching

    suspend fun awaitSettled() {
        select {
            onSettled { }
        }
    }

    private suspend fun perform(
            fixedPromise: AtomicReference<Promise<RESULT>>? = null,
            op: suspend ForwardPack<RESULT>.() -> Unit,
    ) {
        val s = PromiseState(this)
        op(object : ForwardPack<RESULT> {
            override suspend fun rsv(v: RESULT): JobResult {
                fixedPromise?.get()?.run {
                    forward()
                } ?: succeed(v)
                return JobResult.INSTANCE
            }

            override suspend fun rsp(p: Promise<RESULT>): JobResult {
                transfer(false, p, fixedPromise)
                return JobResult.INSTANCE
            }

            override fun rej(e: Throwable): JobResult {
                fail(e)
                return JobResult.INSTANCE
            }

            override suspend fun forward(): JobResult {
                return rsp(fixedPromise!!.getAndSet(null)!!)
            }

            override fun state(): PromiseState<RESULT> {
                return s
            }

            override val scopeCancelledBroadcast = PromiseCancelledBroadcast.merge(
                    *arrayOf(
                            this@Promise.config.scopeCancelledBroadcast,
                            this@Promise.cancelledBroadcaster
                    )
            )
        })
    }

    private suspend fun <ANOTHER> transfer(
            shouldAcquireSemaphore: Boolean,
            from: Promise<ANOTHER>,
            fixedPromise: AtomicReference<Promise<RESULT>>? = null,
            onSucceeded: SucceededHandler<ANOTHER, RESULT> = {
                rsv(value as RESULT)
            },
            onFailed: FailedHandler<RESULT> = {
                rej(reason)
            },
            onCancelled: CancelledListener = {},
            onFinally: FinallyHandler<ANOTHER>? = null,
    ) {
        val fixedPromiseF: AtomicReference<Promise<RESULT>>? =
                if (onFinally != null) AtomicReference(from as Promise<RESULT>) else fixedPromise
        var selfCancelled = false
        try {
            from.awaitSettled()
        } catch (e: CancellationException) {
            selfCancelled = true
        }
        if (shouldAcquireSemaphore) {
            acquireSemaphore()
        }
        val runCancelCallback: suspend (Boolean) -> Unit = { isSelfCancelled ->
            perform(fixedPromiseF) perform@{
                val callback: suspend () -> Unit = {
                    if (onFinally != null) {
                        onFinally(this@perform as ForwardPack<ANOTHER>)
                    } else {
                        onCancelled()
                    }
                }
                if (!isSelfCancelled) {
                    this@Promise.cancel()
                }
                withContext(NonCancellable) {
                    callback()
                }
            }
        }
        if (selfCancelled) {
            runCancelCallback(true)
        } else {
            when (from.status.get()) {
                null -> throw Throwable()
                Status.RUNNING -> throw Throwable()
                Status.SUCCEED -> perform(fixedPromiseF) perform@{
                    if (onFinally != null) {
                        onFinally(this@perform as ForwardPack<ANOTHER>)
                    } else {
                        onSucceeded(object : ValuePack<ANOTHER, RESULT>, ResolvePack<RESULT> by this {
                            override val value: ANOTHER
                                get() = from.value as ANOTHER
                        })
                    }
                }

                Status.FAILED -> perform(fixedPromiseF) perform@{
                    if (onFinally != null) {
                        onFinally(this@perform as ForwardPack<ANOTHER>)
                    } else {
                        onFailed(object : ReasonPack<RESULT>, ResolvePack<RESULT> by this {
                            override val reason: Throwable
                                get() = from.reason as Throwable
                        })
                    }
                }

                Status.CANCELLED -> runCancelCallback(false)
            }
        }
    }

    fun <NEXT_RESULT> then(
            config: PromiseConfig?,
            onSucceeded: SucceededHandler<RESULT, NEXT_RESULT>
    ) =
            Promise((config ?: PromiseConfig.EMPTY_CONFIG).copy(
                    shouldWrapJobWithSemaphore = false
            )) next@{
                state().self.transfer(
                        true,
                        this@Promise,
                        onSucceeded = { onSucceeded() }
                )
                waiting()
            }

    fun next(
            config: PromiseConfig?,
            onSucceeded: SucceededConsumer<RESULT>
    ) = then(config) {
        onSucceeded()
        rsv(null)
    }

    fun <NEXT_RESULT> catch(
            config: PromiseConfig?,
            onFailed: FailedHandler<NEXT_RESULT>
    ) =
            Promise((config ?: PromiseConfig.EMPTY_CONFIG).copy(
                    shouldWrapJobWithSemaphore = false
            )) next@{
                state().self.transfer(
                        true,
                        this@Promise,
                        onFailed = { onFailed() }
                )
                waiting()
            }

    fun recover(
            config: PromiseConfig?,
            onFailed: FailedConsumer
    ) = catch(config) {
        onFailed()
        rsv(null)
    }

    fun <NEXT_RESULT> forCancel(
            config: PromiseConfig?,
            onCancelled: CancelledListener
    ) =
            Promise<NEXT_RESULT>((config ?: PromiseConfig.EMPTY_CONFIG).copy(
                    shouldWrapJobWithSemaphore = false
            )) next@{
                state().self.transfer(
                        true,
                        this@Promise,
                        onCancelled = { onCancelled() }
                )
                waiting()
            }

    fun aborted(
            config: PromiseConfig?,
            onCancelled: CancelledListener
    ) = forCancel<RESULT>(config, onCancelled)

    fun terminated(
            config: PromiseConfig?,
            onCancelled: CancelledListener
    ) = forCancel<Any?>(config, onCancelled)

    fun finally(
            config: PromiseConfig?,
            onFinally: FinallyHandler<RESULT>
    ) =
            Promise<RESULT>((config ?: PromiseConfig.EMPTY_CONFIG).copy(
                    shouldWrapJobWithSemaphore = false
            )) next@{
                state().self.transfer(
                        true,
                        this@Promise,
                        onFinally = { onFinally() }
                )
                waiting()
            }

    fun last(
            config: PromiseConfig?,
            onFinally: FinallyConsumer<RESULT>
    ) = finally(config) {
        onFinally()
        forward()
    }

    fun setTimeout(d: Duration, onTimeOut: suspend (d: Duration) -> Unit = { }): Promise<RESULT> {
        val currentSN: Int
        synchronized(setTimeoutMutex) {
            if (timeoutTriggered) {
                return this
            }
            currentSN = ++timeoutSN
        }
        Promise(null) delayP@{
            delay(d)
            val valid = synchronized(setTimeoutMutex) valid@{
                if (timeoutTriggered) {
                    return@valid false
                }
                if (currentSN != timeoutSN) {
                    return@valid false
                }
                true.also { timeoutTriggered = it }
            }
            if (!valid || !cancel()) {
                return@delayP rsv(null)
            }
            onTimeOut(d)
            rsv(null)
        }
        return this
    }

    suspend fun await(): RESULT {
        awaitSettled()
        return when (status.get()) {
            null, Status.RUNNING -> throw Throwable()
            Status.SUCCEED -> value as RESULT
            Status.FAILED -> throw reason!!
            Status.CANCELLED -> throw CancellationException()
        }
    }

    fun toJavaPromise() = pub.telephone.javapromise.async.promise.Promise(
            PromiseCancelledBroadcast.merge(
                    config.scopeCancelledBroadcast,
                    cancelledBroadcaster
            ).let {
                object
                    : pub.telephone.javapromise.async.promise.PromiseCancelledBroadcast,
                        PromiseCancelledBroadcast by it {}
            },
            { resolver, rejector, state ->
                state.toPromiseScope().run {
                    next {
                        resolver.Resolve(value)
                    }.recover {
                        rejector.Reject(reason)
                    }
                }
            },
            null
    )

    companion object {
        fun <RESULT> race(
                config: PromiseConfig?,
                vararg promises: Promise<RESULT>
        ): Promise<RESULT> {
            return Promise(config) {
                rsp(select {
                    for (p in promises) {
                        p.onSettled { p }
                    }
                })
            }
        }

        fun <RESULT> resolve(value: RESULT) = Promise<RESULT>(
                null,
                PromiseConfig.EMPTY_CONFIG
        ).apply {
            succeed(value)
        }

        fun <RESULT> reject(reason: Throwable) = Promise<RESULT>(
                null,
                PromiseConfig.EMPTY_CONFIG
        ).apply {
            fail(reason)
        }

        fun error(reason: Throwable) = reject<Any?>(reason)

        fun <RESULT> cancel() = Promise<RESULT>(
                null,
                PromiseConfig.EMPTY_CONFIG
        ).apply {
            cancel()
        }

        fun abort() = cancel<Any?>()
    }
}

private fun Job.toPromiseScope(): PromiseScope = run {
    with(object : PromiseCancelledBroadcast {
        override val isActive: Boolean
            get() = this@run.isActive

        override fun listen(r: java.lang.Runnable): Any {
            return this@run.invokeOnCompletion {
                if (it is CancellationException) {
                    r.run()
                }
            }
        }

        override fun unListen(key: Any) {
            (key as DisposableHandle).dispose()
        }
    }) {
        object : PromiseScope {
            override val scopeCancelledBroadcast: PromiseCancelledBroadcast
                get() = this@with
        }
    }
}

private fun pub.telephone.javapromise.async.promise.PromiseState<*>.toPromiseScope(): PromiseScope {
    val broadcast = PromiseCancelledBroadcast.merge(
            ScopeCancelledBroadcast,
            CancelledBroadcast
    )
    return object : PromiseScope {
        override val scopeCancelledBroadcast: PromiseCancelledBroadcast
            get() = broadcast
    }
}

interface Work {
    fun cancel()
}

class Task<RESULT>(val promise: Promise<RESULT>, private val cancelledBroadcaster: PromiseCancelledBroadcaster?) : Work {
    fun await() = runBlocking { promise.await() }
    override fun cancel() {
        cancelledBroadcaster!!.broadcast()
    }
}

typealias ProcessFunc<RESULT> = PromiseScope.() -> Promise<RESULT>

typealias WorkFunc = PromiseScope.() -> Unit

fun WorkFunc.toProcessFunc(): ProcessFunc<Any?> = {
    this@toProcessFunc()
    Promise.resolve(null)
}

private fun <RESULT> process(
        promiseScope: PromiseScope,
        cancelledBroadcaster: PromiseCancelledBroadcaster?,
        builder: ProcessFunc<RESULT>
): Task<RESULT> = Task(promiseScope.builder(), cancelledBroadcaster)

private fun <RESULT> processInNewJob(builder: ProcessFunc<RESULT>) = Job().run newJob@{
    toPromiseScope().run scope@{
        process(
                this@scope,
                object :
                        PromiseCancelledBroadcaster,
                        PromiseCancelledBroadcast by this@scope.scopeCancelledBroadcast!! {
                    override fun broadcast() {
                        this@newJob.cancel()
                    }
                },
                builder
        )
    }
}

fun <RESULT> process(builder: ProcessFunc<RESULT>) = processInNewJob(builder)
fun work(builder: WorkFunc) = process(builder.toProcessFunc())
fun <RESULT> promise(job: PromiseJob<RESULT>) = process { promise { job() } }
fun <RESULT> PromiseScope.process(builder: ProcessFunc<RESULT>) = process(this, null, builder)
fun PromiseScope.work(builder: WorkFunc) = process(builder.toProcessFunc())
fun <RESULT> CoroutineScope.process(builder: ProcessFunc<RESULT>) = coroutineContext[Job]?.run {
    process(toPromiseScope(), null, builder)
} ?: processInNewJob(builder)
fun CoroutineScope.work(builder: WorkFunc) = process(builder.toProcessFunc())
fun <RESULT> CoroutineScope.promise(job: PromiseJob<RESULT>) = process { promise { job() } }
fun <RESULT> pub.telephone.javapromise.async.promise.PromiseState<*>.process(
        builder: ProcessFunc<RESULT>
) = process(toPromiseScope(), null, builder)

fun pub.telephone.javapromise.async.promise.PromiseState<*>.work(
        builder: WorkFunc
) = process(builder.toProcessFunc())

fun <RESULT> pub.telephone.javapromise.async.promise.PromiseState<*>.promise(
        job: PromiseJob<RESULT>
) = process { promise { job() } }