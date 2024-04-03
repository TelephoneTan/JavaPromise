package pub.telephone.javapromise.async.kpromise

fun interface RunThrow {
    @Throws(Throwable::class)
    fun run()
}