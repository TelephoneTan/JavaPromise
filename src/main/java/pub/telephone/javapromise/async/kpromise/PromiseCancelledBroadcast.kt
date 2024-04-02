package pub.telephone.javapromise.async.kpromise

import java.util.concurrent.atomic.AtomicBoolean

interface PromiseCancelledBroadcast {
    val isActive: Boolean
    fun listen(r: Runnable): Any
    fun unListen(key: Any)

    companion object {
        fun merge(
                vararg broadcasts: PromiseCancelledBroadcast?
        ): PromiseCancelledBroadcast = object : PromiseCancelledBroadcast {
            override val isActive: Boolean
                get() {
                    var res = true
                    for (broadcast in broadcasts) {
                        res = res && (broadcast?.isActive ?: true)
                    }
                    return res
                }

            override fun listen(r: Runnable): Any {
                val once = AtomicBoolean()
                val onceR = Runnable onceRun@{
                    if (!once.compareAndSet(false, true)) {
                        return@onceRun
                    }
                    r.run()
                }
                return broadcasts.asSequence()
                        .map { it?.listen(onceR) }
                        .toList()
            }

            override fun unListen(key: Any) {
                val keys = key as List<Any?>
                for ((i, k) in keys.withIndex()) {
                    k?.let { broadcasts[i]?.unListen(it) }
                }
            }
        }
    }
}