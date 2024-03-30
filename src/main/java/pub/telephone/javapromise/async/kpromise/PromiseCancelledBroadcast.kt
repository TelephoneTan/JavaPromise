package pub.telephone.javapromise.async.kpromise

interface PromiseCancelledBroadcast {
    val isActive: Boolean
    fun listen(r: Runnable): Any
    fun unListen(key: Any)
}