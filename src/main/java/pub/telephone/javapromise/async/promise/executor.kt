package pub.telephone.javapromise.async.promise

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.time.toKotlinDuration

private val factory = ThreadFactory {
    val thread = Thread(it)
    thread.isDaemon = true
    thread
}

private fun newFixedDispatcher(n: Int) = Executors.newFixedThreadPool(n, factory).asCoroutineDispatcher()

private fun newCachedDispatcher() = Executors.newCachedThreadPool(factory).asCoroutineDispatcher()

private var dispatcher = newCachedDispatcher()

fun initDispatcher(n: Int) {
    dispatcher = newFixedDispatcher(n)
}

private val cancelDispatcher = Executors.newSingleThreadExecutor { r ->
    val thread = Thread(r)
    thread.isDaemon = true
    thread
}.asCoroutineDispatcher()

fun <E> newChannel(capacity: Int = Channel.RENDEZVOUS): Channel<E> {
    return Channel(capacity)
}

fun newSemaphore(n: Int): Semaphore {
    return Semaphore(n)
}

fun newSemaphore(n: Int, acquired: Int): Semaphore {
    return Semaphore(n, acquired)
}

fun newMutex(): Mutex {
    return Mutex()
}

interface RunThrowsThrowable {
    @Throws(Throwable::class)
    fun run()
}

interface RunWithValueThrowsThrowable<E> {
    @Throws(Throwable::class)
    fun run(v: E)
}

suspend fun awaitClose(
    channel: Channel<Unit>,
    then: RunThrowsThrowable,
    outerChannel: Channel<Unit>?,
    outerThen: RunThrowsThrowable?,
) {
    select {
        if (outerChannel != null && outerThen != null) {
            outerChannel.onReceiveCatching {
                outerThen.run()
            }
        }
        channel.onReceiveCatching {
            then.run()
        }
    }
}

suspend fun withLock(mutex: Mutex, then: RunThrowsThrowable) {
    mutex.lock()
    then.run()
    mutex.unlock()
}

suspend fun withLock(mutexList: Array<Mutex>, then: RunThrowsThrowable) {
    for (mutex in mutexList) {
        mutex.lock()
    }
    then.run()
    for (mutex in mutexList) {
        mutex.unlock()
    }
}

suspend fun awaitGroup(
    channel: Channel<Unit>,
    num: Int,
    then: RunThrowsThrowable
) {
    repeat(num) {
        channel.receive()
    }
    then.run()
}

fun readFromOrSendTo(
    from: Channel<Unit>,
    to: Channel<Unit>
) {
    if (from.tryReceive().isFailure) {
        to.trySend(Unit)
    }
}

fun <E> tryReceiveAll(from: Channel<E>): List<E> {
    val res = mutableListOf<E>()
    var r: ChannelResult<E>
    while (true) {
        r = from.tryReceive()
        if (r.isSuccess) {
            res.add(r.getOrThrow())
        } else {
            break
        }
    }
    return res
}

fun trySend(to: Channel<Unit>): Boolean {
    return to.trySend(Unit).isSuccess
}

fun <E> trySend(to: Channel<E>, x: E): Boolean {
    return to.trySend(x).isSuccess
}

fun tryReceive(from: Channel<Unit>): Boolean {
    return from.tryReceive().isSuccess
}

fun <E> tryReceive(from: Channel<E>, then: RunWithValueThrowsThrowable<E>, failThen: RunThrowsThrowable) {
    val res = from.tryReceive()
    if (res.isSuccess) {
        then.run(res.getOrThrow())
    } else {
        failThen.run()
    }
}

fun trySendToCloseable(to: Channel<Unit>): Boolean? {
    val result = to.trySend(Unit)
    return if (result.isClosed) {
        null
    } else {
        result.isSuccess
    }
}

suspend fun delay(d: Duration, then: RunThrowsThrowable) {
    delay(d.toKotlinDuration())
    then.run()
}

suspend fun onReceive(from: Channel<Unit>, then: RunThrowsThrowable) {
    from.receive()
    then.run()
}

suspend fun <E> onReceive(from: Channel<E>, then: RunWithValueThrowsThrowable<E>) {
    val v = from.receive()
    then.run(v)
}

suspend fun <E> onReceive(
    from: Channel<E>,
    then: RunWithValueThrowsThrowable<E>,
    quit: Channel<Unit>,
    quitThen: RunThrowsThrowable
) {
    select {
        quit.onReceiveCatching {
            quitThen.run()
        }
        from.onReceive { v ->
            then.run(v)
        }
    }
}

suspend fun <A, B, C> onReceive(
    one: Channel<A>,
    oneThen: RunWithValueThrowsThrowable<A>,
    two: Channel<B>,
    twoThen: RunWithValueThrowsThrowable<B>,
    three: Channel<C>,
    threeThen: RunWithValueThrowsThrowable<C>
) {
    select {
        one.onReceive { v ->
            oneThen.run(v)
        }
        two.onReceive { v ->
            twoThen.run(v)
        }
        three.onReceive { v ->
            threeThen.run(v)
        }
    }
}

suspend fun onSend(to: Channel<Unit>, then: RunThrowsThrowable) {
    to.send(Unit)
    then.run()
}

suspend fun acquirePromiseSemaphore(semaphore: PromiseSemaphore?, n: Int, depth: Int, then: RunThrowsThrowable) {
    var s = semaphore
    var d = depth
    while (s != null) {
        for (i in 1..n) {
            s.ticketChannel.receive()
        }
        s = s.parent
        if (d >= 1) {
            d--
            if (d <= 0) {
                break
            }
        }
    }
    then.run()
}

interface ErrorListener {
    fun onError(e: Throwable?)
}

private fun buildContext(dispatcher: ExecutorCoroutineDispatcher, onError: ErrorListener?): CoroutineContext {
    return dispatcher + CoroutineExceptionHandler { _, e ->
        onError?.onError(e)
    }
}

private fun <E> buildContinuation(dispatcher: ExecutorCoroutineDispatcher, onError: ErrorListener?): Continuation<E> {
    return Continuation(buildContext(dispatcher, onError)) { result ->
        try {
            result.getOrThrow()
        } catch (e: Throwable) {
            onError?.onError(e)
        }
    }
}

fun <E> normalContinuation(onError: ErrorListener): Continuation<E> {
    return buildContinuation(dispatcher, onError)
}

fun <E> ignoreErrorContinuation(): Continuation<E> {
    return buildContinuation(dispatcher, object : ErrorListener {
        override fun onError(e: Throwable?) {
        }
    })
}

fun <E> noErrorContinuation(): Continuation<E> {
    return buildContinuation(dispatcher, null)
}

fun <E> wontSuspendContinuation(): Continuation<E> {
    return buildContinuation(dispatcher, null)
}

fun <E> cancelContinuation(): Continuation<E> {
    return buildContinuation(cancelDispatcher, null)
}

@OptIn(DelicateCoroutinesApi::class)
fun submitAsync(runnable: Runnable, onError: ErrorListener) {
    val l = CountDownLatch(1)
    l.countDown()
    GlobalScope.launch(buildContext(dispatcher, onError)) {
        l.await()
        runnable.run()
    }
}