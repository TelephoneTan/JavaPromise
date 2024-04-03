package pub.telephone.javapromise.async.kpromise

interface PromiseScope {
    val scopeCancelledBroadcast: PromiseCancelledBroadcast?
    fun <RESULT> promise(
            config: PromiseConfig? = null,
            job: PromiseJob<RESULT>,
    ) = Promise((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), job)
    fun <RESULT, NEXT_RESULT> Promise<RESULT>.then(
            config: PromiseConfig? = null,
            onSucceeded: SucceededHandler<RESULT, NEXT_RESULT>
    ) = then((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onSucceeded)

    fun <RESULT> Promise<RESULT>.next(
            config: PromiseConfig? = null,
            onSucceeded: SucceededConsumer<RESULT>
    ) = next((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onSucceeded)

    fun <NEXT_RESULT> Promise<*>.catch(
            config: PromiseConfig? = null,
            onFailed: FailedHandler<NEXT_RESULT>
    ) = catch((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onFailed)

    fun Promise<*>.recover(
            config: PromiseConfig? = null,
            onFailed: FailedConsumer
    ) = recover((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onFailed)

    fun <NEXT_RESULT> Promise<*>.forCancel(
            config: PromiseConfig? = null,
            onCancelled: CancelledListener
    ) = forCancel<NEXT_RESULT>((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onCancelled)

    fun Promise<*>.aborted(
            config: PromiseConfig? = null,
            onCancelled: CancelledListener
    ) = aborted((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onCancelled)

    fun Promise<*>.terminated(
            config: PromiseConfig? = null,
            onCancelled: CancelledListener
    ) = terminated((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onCancelled)

    fun <RESULT> Promise<RESULT>.finally(
            config: PromiseConfig? = null,
            onFinally: FinallyHandler<RESULT>
    ) = finally((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onFinally)

    fun <RESULT> Promise<RESULT>.last(
            config: PromiseConfig? = null,
            onFinally: FinallyConsumer<RESULT>
    ) = last((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), onFinally)

    fun <RESULT> race(
            config: PromiseConfig? = null,
            vararg promises: Promise<RESULT>
    ) = Promise.race((config ?: PromiseConfig.EMPTY_CONFIG).copy(
            scopeCancelledBroadcast = scopeCancelledBroadcast
    ), *promises)

    fun <RESULT> race(
            vararg promises: Promise<RESULT>
    ) = race(null, *promises)
}