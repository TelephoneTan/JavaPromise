import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import pub.telephone.javapromise.async.kpromise.process
import pub.telephone.javapromise.async.kpromise.promise
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class TestKPromise {
    val time get() = Calendar.getInstance().time

    @Test
    fun test() {
        process {
            println("【${time}】hello world")
            //
            promise {
                delay(2.seconds)
                rsv(666)
            }.next {
                try {
                    withTimeout(2.seconds) {
                        coroutineContext
                        try {
                            delay(10.seconds)
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Throwable) {
                    throw e
                }
                rsv("echo $value to the world")
            }.next {
                println("【${time}】$value")
                rsv(Unit)
            }.next {
                delay(4.seconds)
                throw Throwable()
                rsv("【${time}】say hello to the world")
            }.catch<Any?> {
                try {
                    delay(10.seconds)
                } catch (e: Throwable) {
//                    println("$e")
                    e.printStackTrace()
                }
                println("【${time}】caught 1 $reason")
                throw IllegalStateException()
            }.setTimeout(2.seconds) {
                println("【${time}】time out! $it")
                delay(2.seconds)
                println("【${time}】time out! $it")
            }.setTimeout(8.5.seconds) {
                println("【${time}】time out! $it")
                delay(2.seconds)
                println("【${time}】time out! $it")
            }.forCancel<Any?> {
                try {
//                    delay(1.seconds)
                } catch (e: Throwable) {
//                    println("$e")
                    e.printStackTrace()
                }
                println("【${time}】cancelled here 1")
            }.catch<Any?> {
                println("【${time}】caught 2 $reason")
                throw reason
            }.finally {
                try {
                    delay(10.seconds)
                } catch (e: Throwable) {
//                    println("$e")
                    e.printStackTrace()
                }
                println("【${time}】finally here $isActive")
                rsp(promise {
                    delay(1.seconds)
                    rsp(promise {
                        println("调用A")
                        delay(1.seconds)
                        println("调用B")
                        rej(IndexOutOfBoundsException())
                    })
                })
            }.then {
                rsv(value as String)
            }.next {
                println("【${time}】say $value to the world ${value.length}")
                rsv(null)
            }.catch<String> {
                println("【${time}】caught $reason")
                rsv("bbb")
            }.next {
                println("【${time}】say $value to the world ${value.length}")
                rsv(null)
            }.forCancel<Any?> {
                try {
                    delay(1.seconds)
                } catch (e: Throwable) {
//                    println("$e")
                    e.printStackTrace()
                }
                println("【${time}】cancelled here 2")
            }
        }.also {
            GlobalScope.launch {
                delay(8.seconds)
                it.cancel()
            }
        }.await()
        println("【${time}】await pass")
        runBlocking { delay(60.seconds) }
    }

    @Test
    fun testRace() {
        promise {
            println("【${time}】hello")
            val p1 = promise {
                delay(5.seconds)
                rsv("1")
            }
            val p2 = promise {
                delay(5.seconds)
                rsv("2")
            }
//            p1.await()
//            p2.await()
            race(p1, p2).next {
                println("【${time}】$value")
            }.await()
            rsv(null)
        }.await()
    }

    suspend fun aa() {
        coroutineContext
    }
}