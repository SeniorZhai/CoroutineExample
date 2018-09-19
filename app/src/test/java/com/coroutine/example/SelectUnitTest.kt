package com.coroutine.example

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.selects.select
import org.junit.Test
import java.util.Random
import kotlin.coroutines.experimental.CoroutineContext

class SelectUnitTest {

  fun fizz(context: CoroutineContext) = produce<String>(context) {
    while (true) {
      delay(300)
      send("Fizz")
    }
  }

  fun buzz(context: CoroutineContext) = produce(context) {
    while (true) {
      delay(500)
      send("Buzz!")
    }
  }

  // select表达式能够同时等待多个suspending function 然后选择第一个可用结果
  suspend fun selectFizzBuzz(fizz: ReceiveChannel<String>, buzz: ReceiveChannel<String>) {
    select<Unit> {
      fizz.onReceive { value ->
        println("fizz -> $value")
      }
      buzz.onReceive { value ->
        println("buzz -> $value")
      }
    }
  }

  @Test
  fun selectTest() = runBlocking<Unit> {
    val fizz = fizz(coroutineContext)
    val buzz = buzz(coroutineContext)
    repeat(7) {
      selectFizzBuzz(fizz, buzz)
    }
    coroutineContext.cancelChildren()
  }


  suspend fun selectNull(a: ReceiveChannel<String>, b: ReceiveChannel<String>) {
    select<Unit> {
      a.onReceiveOrNull { value ->
        if (value == null) println("Channel 'a' is closed")
        else println("a -> $value")
      }
      b.onReceiveOrNull { value ->
        if (value == null) println("Channel 'b' is closed")
        else println("b -> $value")
      }
    }
  }

  @Test
  fun selectNullTest() = runBlocking {
    val a = produce<String>(coroutineContext) {
      repeat(4) {
        send("Hello $it")
      }
    }

    val b = produce<String>(coroutineContext) {
      repeat(4) {
        send("World $it")
      }
    }

    repeat(8) {
      selectNull(a, b)
    }

    coroutineContext.cancelChildren()
    /* select更倾向于第一条语句，两个channel不停的发送字符串，channel a更容易竞争成功
     * a -> Hello 0
     * a -> Hello 1
     * b -> World 0
     * a -> Hello 2
     * a -> Hello 3
     * b -> World 1
     * Channel 'a' is closed
     * Channel 'a' is closed
     */
  }

  fun produceNumbers(context: CoroutineContext, side: SendChannel<Int>) = produce<Int>(context) {
    for (num in 1..10) {
      delay(100)
      select<Unit> {
        onSend(num) {} // 发送到主 channel
        side.onSend(num) {} // 发送到副 channel
      }
    }
  }

  @Test
  fun sendSelectTest() = runBlocking<Unit> {
    val side = Channel<Int>()
    launch(coroutineContext) {
      // 副消费者处理的很快
      side.consumeEach { println("Side channel has $it") }
    }
    // 如果 主消费者不能马上处理 交给副消费者处理
    produceNumbers(coroutineContext, side).consumeEach {
      println("Consuming $it")
      delay(250) // 主消费者处理的有点慢
    }
    println("Done consuming")
    coroutineContext.cancelChildren()
  }

  private fun asyncString(time: Int) = async {
    delay(time.toLong())
    "Waied for $time ms"
  }

  private fun asyncStringsList(): List<Deferred<String>> {
    val random = Random()
    return List(12) {
      asyncString(random.nextInt(1000))
    } // 创建了12个Deferred
  }

  @Test
  fun onAwaitTest() = runBlocking {
    val list = asyncStringsList()
    val result = select<String> {
      list.withIndex().forEach { (index, deferred) ->
        // 惰性计算 返回第一个计算完成的
        deferred.onAwait { answer ->
          "Deferred $index produced answer '$answer'"
        }
      }
    }
    println(result)
    val countActive = list.count { it.isActive }
    println("$countActive coroutines are still active")
  }

  private fun switchMapDeferreds(intput: ReceiveChannel<Deferred<String>>) = produce<String> {
    var current = intput.receive() // 接受一个值
    while (isActive) { // 循环
      val next = select<Deferred<String>?> {
        intput.onReceiveOrNull { update -> // 取到下一个替换当前没完成的
          update // 替换
        }
        current.onAwait { value ->
          send(value)
          intput.receiveOrNull()
        }
      }
      if (next == null) {
        println("Channel was closed")
        break
      } else {
        current = next
      }
    }
  }

  // 模拟一个延时计算的函数
  private fun asyncString(str: String, time: Long) = async {
    delay(time)
    str
  }

  @Test
  fun switchTest() = runBlocking {
    val chan = Channel<Deferred<String>>()
    launch(coroutineContext) {
      // 从Channel中获取的Deferred中获取值
      for (s in switchMapDeferreds(chan))
        println(s)
    }
    chan.send(asyncString("BEGIN",100))
    delay(200)
    chan.send(asyncString("Slow",500))
    delay(100) // 没有给足够的时间
    chan.send(asyncString("Replace",100))
    delay(500)
    chan.send(asyncString("END",500))
    delay(1000)
    chan.close()
    delay(500)
  }



}