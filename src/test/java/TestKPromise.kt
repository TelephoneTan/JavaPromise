import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import pub.telephone.javapromise.async.kpromise.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class TestKPromise {
    val time get() = Calendar.getInstance().time

    private fun log(msg: Any?) {
        println("【${time}】$msg")
    }

    @Test
    fun test() {
        try {
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
                    delay(9.seconds)
                    it.cancel()
                }
            }.await()
        } catch (e: CancellationException) {
            log("await cancel caught $e")
        }
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

    @Test
    fun testSemaphore() {
        testSemaphoreN(1)
        testSemaphoreN(5)
    }

    fun testSemaphoreN(n: Int) {
        process {
            val semaphore = PromiseSemaphore(n)
            val counter = AtomicInteger()
            val allWork = mutableListOf<Promise<Any?>>()
            val skip = arrayOf(55, 66, 77)
            for (i in 1..100) {
                allWork.add(promise(PromiseConfig(
                        semaphore = semaphore
                )) {
                    if (i in skip) {
                        throw Throwable("$i 不干了")
                    }
                    println("$i 开始值 ${counter.get()}")
                    for (j in 1..1000000) {
                        counter.incrementAndGet()
                    }
                    println("$i 结束值 ${counter.get()}")
                    rsv(null)
                })
            }
            promise {
                var allSucceeded = true
                for ((i, p) in allWork.withIndex()) {
                    try {
                        p.await()
                    } catch (e: CancellationException) {
                        allSucceeded = false
                        println("$i 取消了")
                    } catch (e: Throwable) {
                        allSucceeded = false
                        println("$i 出错了：$e")
                    }
                }
                if (allSucceeded) {
                    println("全部成功")
                }
                rsv(null)
            }.last {
                println("总体结束值 ${counter.get()}")
            }
        }.await()
    }

    @Test
    fun testMultipleSemaphore() {
        val total: PromiseSemaphore
        val host: PromiseSemaphore
        val hostA: PromiseSemaphore
        val hostB: PromiseSemaphore
        val user: PromiseSemaphore
        val userA: PromiseSemaphore
        val userB: PromiseSemaphore
        PromiseSemaphore(10).also { total = it }.apply {
            then(PromiseSemaphore(5).also { host = it }.apply {
                then(PromiseSemaphore(3).also { hostA = it })
                then(PromiseSemaphore(3).also { hostB = it })
            })
            then(PromiseSemaphore(5).also { user = it }.apply {
                then(PromiseSemaphore(3).also { userA = it })
                then(PromiseSemaphore(3).also { userB = it })
            })
        }
        val interval = 5.seconds
        process {
            val allWork = mutableListOf<Promise<Any?>>()
            val add = { name: String, semaphore: PromiseSemaphore ->
                for (i in 1..10) {
                    allWork.add(promise(PromiseConfig(semaphore = semaphore)) {
                        println("$name $i -> ${
                            SimpleDateFormat(
                                    "yyyy / MM / dd | HH : mm : ss . SSS"
                            ).format(Date())
                        }")
                        delay(interval)
                        rsv(null)
                    })
                }
            }
            add("hostA", hostA)
            add("hostB", hostB)
            add("userA", userA)
            add("userB", userB)
            promise {
                for (p in allWork) {
                    try {
                        p.await()
                    } catch (_: Throwable) {
                    }
                }
                rsv(null)
            }.last {
                println("结束了")
            }
        }.await()
    }

    suspend fun aa() {
        coroutineContext
    }
}