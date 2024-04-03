package pub.telephone.javapromise.async.kpromise

import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

open class PromiseSemaphore(n: Long) {
    @Volatile
    private var parent: PromiseSemaphore? = null
    private val ticketChannel: Channel<Unit> = Channel(Channel.UNLIMITED)
    private val darkChannel: Channel<Unit> = Channel(Channel.UNLIMITED)

    init {
        increase(n)
    }

    constructor(n: Int) : this(n.toLong())

    @JvmSynthetic
    suspend fun acquire(n: ULong = 1UL, after: RunThrow? = null) {
        var s: PromiseSemaphore? = this
        while (s != null) {
            for (i in 1UL..n) {
                s.ticketChannel.receive()
            }
            s = s.parent
        }
        after?.run()
    }

    suspend fun acquire(n: Long, after: RunThrow? = null) {
        acquire(n.toULong(), after)
    }

    @JvmSynthetic
    suspend fun acquire(n: UInt, after: RunThrow? = null) {
        acquire(n.toULong(), after)
    }

    suspend fun acquire(n: Int, after: RunThrow? = null) {
        acquire(n.toULong(), after)
    }

    @JvmSynthetic
    fun release(n: ULong = 1UL) {
        var s: PromiseSemaphore? = this
        while (s != null) {
            s.increase(n)
            s = s.parent
        }
    }

    fun release(n: Long) {
        release(n.toULong())
    }

    @JvmSynthetic
    fun release(n: UInt) {
        release(n.toULong())
    }

    fun release(n: Int) {
        release(n.toULong())
    }

    @JvmSynthetic
    fun increase(n: ULong) {
        for (i in 1UL..n) {
            if (darkChannel.tryReceive().isSuccess) {
                continue
            }
            ticketChannel.trySend(Unit)
        }
    }

    fun increase(n: Long) {
        increase(n.toULong())
    }

    @JvmSynthetic
    fun increase(n: UInt) {
        increase(n.toULong())
    }

    @JvmName("Post")
    fun increase(n: Int) {
        increase(n.toULong())
    }

    @JvmSynthetic
    fun reduce(n: ULong) {
        for (i in 1UL..n) {
            darkChannel.trySend(Unit)
        }
    }

    fun reduce(n: Long) {
        reduce(n.toULong())
    }

    @JvmSynthetic
    fun reduce(n: UInt) {
        reduce(n.toULong())
    }

    @JvmName("Reduce")
    fun reduce(n: Int) {
        reduce(n.toULong())
    }

    fun then(vararg children: PromiseSemaphore?): PromiseSemaphore {
        for (child in children) {
            child?.parent = this
        }
        return this
    }
}