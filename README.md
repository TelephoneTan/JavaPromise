# Java Promise

一个仿照 JavaScript 中 `Promise` 风格，内部基于 Kotlin 协程实现的 Java 异步任务框架。

## Promise 风格

除了下述的不同点外，其余特性与 JavaScript 中的 `Promise` 保持一致。

不同点：

1. 由于 Java 的 `强类型` 特性，成功值无法在 `Promise` 链条上持续传递，只能传递到下一个节点。

## 用法

具体的用法体现在测试用例中：

### [TestMultipleSemaphore.java](./src/test/java/TestMultipleSemaphore.java)

同一个任务受到多个并发度控制

### [TestOnceTask.java](./src/test/java/TestOnceTask.java)

无论结果如何，只会被执行一次的任务

### [TestPromise.java](./src/test/java/TestPromise.java)

* 通过构造任务之间的依赖关系来创建任务链
* 给任务设置超时

### [TestPromiseSemaphore.java](./src/test/java/TestPromiseSemaphore.java)

受单个并发度控制的任务

### [TestSharedTask.java](./src/test/java/TestSharedTask.java)

同一时间只会有一个任务在执行，但可以接受多次结果请求，任务结束后统一发布结果的“共享任务”

### [TestTimedTask.java](./src/test/java/TestTimedTask.java)

* 创建定时任务
* 更改定时任务的计划运行次数
* 更改定时任务的运行时间间隔

### [TestVersionedTask.java](./src/test/java/TestVersionedTask.java)

不定期手动触发、结果可复用的“版本任务”