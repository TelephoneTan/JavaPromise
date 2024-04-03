package pub.telephone.javapromise.async.kpromise

interface PromiseScope {
    val scopeCancelledBroadcast: PromiseCancelledBroadcast?
    fun <RESULT> promise(job: PromiseJob<RESULT>) = Promise(PromiseConfig(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), job)
    fun <RESULT, NEXT_RESULT> Promise<RESULT>.then(
            onSucceeded: SucceededHandler<RESULT, NEXT_RESULT>
    ) = then(scopeCancelledBroadcast, onSucceeded)

    fun <RESULT> Promise<RESULT>.next(
            onSucceeded: SucceededConsumer<RESULT>
    ) = next(scopeCancelledBroadcast, onSucceeded)

    fun <NEXT_RESULT> Promise<*>.catch(
            onFailed: FailedHandler<NEXT_RESULT>
    ) = catch(scopeCancelledBroadcast, onFailed)

    fun Promise<*>.recover(
            onFailed: FailedConsumer
    ) = recover(scopeCancelledBroadcast, onFailed)

    fun <NEXT_RESULT> Promise<*>.forCancel(
            onCancelled: CancelledListener
    ) = forCancel<NEXT_RESULT>(scopeCancelledBroadcast, onCancelled)

    fun Promise<*>.aborted(
            onCancelled: CancelledListener
    ) = aborted(scopeCancelledBroadcast, onCancelled)

    fun Promise<*>.terminated(
            onCancelled: CancelledListener
    ) = terminated(scopeCancelledBroadcast, onCancelled)

    fun <RESULT> Promise<RESULT>.finally(
            onFinally: FinallyHandler<RESULT>
    ) = finally(scopeCancelledBroadcast, onFinally)

    fun <RESULT> Promise<RESULT>.last(
            onFinally: FinallyConsumer<RESULT>
    ) = last(scopeCancelledBroadcast, onFinally)

    fun <RESULT> race(
            vararg promises: Promise<RESULT>
    ) = Promise.race(scopeCancelledBroadcast, *promises)
}