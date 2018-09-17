package com.coroutine.example

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.cancelAndJoin
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.sync.Mutex
import kotlinx.coroutines.experimental.sync.withLock
import kotlinx.coroutines.experimental.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.system.measureTimeMillis

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  // 如果一个协程的coroutineContext来启动另一个协程，那么新的协程是父协程的子协程，如果父协程取消它的所有子协程也会被取消
  @Test
  fun child() = runBlocking {
    val request = launch {
      val job1 = launch {
        println("job1: I have my own context and execute independently!")
        delay(1000)
        println("job1: I am not affected by cancellation of the request")
      }

      val job2 = launch(coroutineContext) {
        delay(100)
        println("job2: I am a child of the request coroutine")
        delay(1000)
        println("job2: I wall not execute this line if my parent request is cancelled")
      }

      job1.join()
      job2.join()
    }

    delay(500)
    request.cancel()
    delay(1000)
    println("main: Who has survived request cancellation?")
  }

  // 继承了父协程，同时替换了调度器
  @Test
  fun operator() = runBlocking {
    val request = launch(coroutineContext) {
      val job = launch(coroutineContext + CommonPool) {
        println("job: I am a child of the request coroutine, but with a different dispatcher")
        delay(1000)
        println("job: I wall not execute this line if my parent request is cancelled")
      }
      job.join()
    }
    delay(500)
    request.cancel()
    delay(1000)
    println("main:Who has survived request cancellation?")
  }

  @Test
  fun parentCancel() = runBlocking {
    val request = launch {
      repeat(3) { i ->
        launch(kotlin.coroutines.experimental.coroutineContext) {
          delay((i + 1) * 2000L)
          println("Coroutine $i is done")
        }
      }
      println("request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join()
    println("Now processing of the request is complete")
  }

  @Test
  fun cancelAndJoin() = runBlocking {
    val job = Job()
    val coroutines = List(10) { i ->
      launch(coroutineContext, parent = job) {
        delay((i + 1) * 200L)
        println("Coroutine $i is done")
      }
    }
    println("Launched ${coroutines.size} coroutines")
    delay(500L)
    println("Cancelling the job!")
    job.cancelAndJoin() // 等待子协程完成后取消任务
  }

  @Test
  fun channel() = runBlocking {
    val channel = Channel<Int>()

    launch {
      for (x in 1..5)
        channel.send(x * x)
    }
    repeat(5) {
      println(channel.receive())
    }
    println("Done!")
  }

  @Test
  fun channelClose() = runBlocking {
    val channel = Channel<Int>()
    launch {
      for (x in 1..5) channel.send(x * x)
      channel.close() // 可以保证能接受到关闭前发送的所有数据
    }
    for (y in channel) println(y)
    println("Done!")
  }

  // 构造Channel
  fun produceSquares() = produce {
    for (x in 1..5) send(x * x)
  }

  @Test
  fun produceTest() = runBlocking {
    val squars = produceSquares()
    squars.consumeEach { println(it) } // 消费者循环
    println("Done!")
  }

  fun produceNumbers() = produce {
    var x = 1
    while (true) send(x++)
  }

  fun square(numbers: ReceiveChannel<Int>) = produce {
    for (x in numbers) send(x * x + 0.1)
  }

  @Test
  fun channelTest() = runBlocking<Unit> {
    val numbers = produceNumbers()
    val squares = square(numbers)
    for (i in 1..6) println(squares.receive())
    println("Done!")
    squares.cancel()
    numbers.cancel()
  }

  private fun numbersFrom(context: CoroutineContext, start: Int) = produce(context) {
    var x = start
    while (true) send(x++)
  }

  private fun filter(context: CoroutineContext, numbers: ReceiveChannel<Int>,
      prime: Int) = produce(
      context) {
    for (x in numbers) { // 相当于 while(true)number.receive()
      if (x % prime != 0) send(x)
    }
  }

  @Test
  fun prime() = runBlocking {
    var cur = numbersFrom(coroutineContext, 2) // 创建一个从2开始的队列
    for (i in 1..10) {
      val prime = cur.receive()
      println(prime)
      cur = filter(coroutineContext, cur, prime) // numbers(2) -> filter(3) -> filter(5)
    }
    coroutineContext.cancelChildren() // 取消所有子协程
  }

  // 每0.1秒发送一个事件
  private fun produce() = produce {
    var x = 1 // 从 1 开始
    while (true) {
      send(x++)
      delay(100) // 等待 0.1s
    }
  }

  private fun launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    channel.consumeEach {
      println("Processor #$id received $it")
    }
  }

  @Test
  fun moreConsumer() = runBlocking<Unit> {
    val producer = produce()
    repeat(5) {
      // 5个消费者处理Channel
      launchProcessor(it, producer)
    }
    delay(950L)
    producer.cancel()
  }

  suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
      delay(time)
      channel.send(s)
    }
  }

  // 多个生产者一个消费者
  @Test
  fun moreProducer() = runBlocking<Unit> {
    val channel = Channel<String>()
    launch(coroutineContext) { sendString(channel, "foo", 200L) }
    launch(coroutineContext) { sendString(channel, "bar", 500L) }
    repeat(6) {
      println(channel.receive())
    }
    coroutineContext.cancelChildren()
  }

  // channel没有缓冲区 没有缓冲区的channel会在生产者和消费者准备好后进行数据传输
  // 如果send小调用会被挂起，等到receive被调用才调用，反之亦然
  @Test
  fun produceBuffer() = runBlocking<Unit> {
    val channel = Channel<Int>(4) // 带缓冲区的channel
    val sender = launch(coroutineContext) {
      repeat(10) {
        println("Sending $it")
        channel.send(it)
      }
    }
    delay(1000) // 没有消费者的情况下，也会发送5次
    sender.cancel()
  }

  data class Ball(var hits: Int)

  suspend fun player(name: String, table: Channel<Ball>) {
    for (ball in table) {
      ball.hits++
      println("$name $ball")
      delay(300)
      table.send(ball)
    }
  }

  // Channel是公平的，先receive的消费者会收到send的数据
  @Test
  fun order() = runBlocking<Unit> {
    val table = Channel<Ball>()
    launch(coroutineContext) { player("ping", table) }
    launch(coroutineContext) { player("pong", table) }
    table.send(Ball(0))
    delay(1000)
    coroutineContext.cancelChildren()
  }

  suspend fun massiveRun(context: CoroutineContext, action: suspend () -> Unit) {
    val n = 1000
    val k = 1000
    val time = measureTimeMillis {
      val jobs = List(n) {
        launch(context) {
          repeat(k) { action() }
        }
      }
      jobs.forEach { it.join() }
    }
    println("Completed ${n * k} actions in $time ms")
  }

  // 多并发的修改事不能使用普通的变量
  private var counter = AtomicInteger()

  @Test
  fun measureJobs() = runBlocking {
    massiveRun(CommonPool) {
      counter.incrementAndGet()
    }
    println("Counter = $counter")
  }

  private var sum = 0
  @Test
  fun singleThread() = runBlocking {
    massiveRun(CommonPool){
      withContext(coroutineContext) { // 单线程保证数据的线程安全
        sum++
      }
    }
    println("Sum = $sum")
  }

  @Test
  fun singleThread2() =  runBlocking {
    massiveRun(newSingleThreadContext("CoroutineContext")){
      sum++
    }
    println("Sum = $sum")
  }

  val mutex = Mutex()

  @Test
  fun mutex() = runBlocking {
    massiveRun(CommonPool) {
      mutex.withLock { // 保证关键代码锁住不被并发 且mutex.lock是一个suspending function 不会锁死进程
        sum ++
      }
    }
    println("Sum = $sum")
  }

}
