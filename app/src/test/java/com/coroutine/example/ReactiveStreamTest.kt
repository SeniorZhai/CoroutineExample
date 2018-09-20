package com.coroutine.example

import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.consume
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.channels.use
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.reactive.consumeEach
import kotlinx.coroutines.experimental.reactive.openSubscription
import kotlinx.coroutines.experimental.reactive.publish
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.rx2.consumeEach
import kotlinx.coroutines.experimental.rx2.rxFlowable
import kotlinx.coroutines.experimental.selects.whileSelect
import kotlinx.coroutines.experimental.yield
import org.junit.Test
import org.reactivestreams.Publisher
import kotlin.coroutines.experimental.CoroutineContext

class ReactiveStreamTest {

  @Test
  fun channelTest() = runBlocking {
    // 创建一个channel
    val source = produce<Int>(coroutineContext) {
      println("Begin")
      for (x in 1..3) {
        delay(200)
        send(x)
      }
    }

    println("Elements:")
    source.consumeEach {
      // 消费数据
      println(it)
    }
    println("Again:")
    source.consumeEach {
      println(it)
    }
  }
  /*
   * 当生产协程关闭后，消费者再也获取不到数据
   *
   */

  @Test
  fun publishTest() = runBlocking {
    val source = publish<Int>(coroutineContext) {
      println("Begin")
      for (x in 1..3) {
        delay(200)
        send(x)
      }
    }

    println("Elements:")
    source.consumeEach {
      println(it)
    }

    println("Again:")
    source.consumeEach {
      println(it)
    }
  }
  /*
   * Reactive stream 是一个高阶函数的概，当订阅产生时，才会生产真正的流
   * Publich会为每一个订阅启动一个新的协程，每调用一次consumeEach就会产生一个新的订阅
   *
   */

  @Test
  fun subscriptionTest() = runBlocking {
    val source = Flowable.range(1, 5)
        .doOnSubscribe { println("OnSubscribe") } // 订阅开始
        .doFinally { println("Finally") } //结束
    var cnt = 0
    // Publisher生成一个channel consume会在结束后取消订阅
    source.openSubscription().consume {
      for (x in this) {
        println(x)
        if (++cnt >= 3) break
      }
    }
  }

  @Test
  fun consumeEachTest() = runBlocking {
    val source = Flowable.range(1, 5)
        .doOnSubscribe { println("OnSubscribe") } // 订阅开始
        .doFinally { println("Finally") } //结束
    source.consumeEach { println(it) }
  }
  /*
   * OnSubscribe
   * 1
   * 2
   * 3
   * 4
   * Finally
   * 5
   * 这是因为runBlocking开启了一个协程，这个主协程通过source.consumeEach消费数据
   * 当它等待数据源的数据时就会被挂起，在接收发射的最后一项数据时，主协程恢复，并在调度后的某个时间点打印
   * 但数据源会在结束时立即打印
   */

  @Test
  fun backPressure() = runBlocking {
    val source = rxFlowable(coroutineContext) {
      for (x in 1..3) {
        send(x)
        println("Sent $x")
      }
    }
    source
        .observeOn(Schedulers.io(), false, 1) // 缓冲区为1
        .doOnComplete { println("complete") }
        .subscribe { x ->
          Thread.sleep(500)
          println("processd $x")
        }
    delay(2000)
  }
  /*
   * 协程的挂起特能够很好的支持背压
   * rxFlowable定义了一个Flowable
   */

  @Test
  fun subjectTest() = runBlocking {
    val subject = BehaviorSubject.create<String>()
    subject.onNext("one")
    subject.onNext("two")
    subject.subscribe(System.out::println) //订阅
    subject.onNext("three")
    subject.onNext("four")
  }

  @Test
  fun subjectCoroutineTest() = runBlocking {
    val subject = BehaviorSubject.create<String>()
    subject.onNext("one")
    subject.onNext("two")

    launch(coroutineContext) {
      subject.consumeEach { println(it) }
    }

    subject.onNext("three")
    subject.onNext("four")
    yield() // 让步给上面的协程
    subject.onComplete() // 结束subject
    // 消费者只会打印最新的four
  }

  @Test
  fun broadcastTest() = runBlocking<Unit> {
    val broadcast = ConflatedBroadcastChannel<String>()
    broadcast.offer("one")
    broadcast.offer("two")
    launch(coroutineContext) {
      broadcast.consumeEach { println(it) }
    }
    broadcast.offer("three")
    broadcast.offer("four")
    yield()
    broadcast.close()
  }

  @Test
  fun mapTest() = runBlocking {
    rang(coroutineContext, 1, 5)
        .fusedFilterMap(coroutineContext, { it % 2 == 0 }, { "$it is even" })
        .consumeEach { println(it) }
  }

  @Test
  fun takeTest() = runBlocking {
    val slowNums = rangeWithInterval(this.coroutineContext, 200, 1, 10)
    val stop = rangeWithInterval(this.coroutineContext, 500, 1, 10)
    slowNums.takeUntil(coroutineContext, stop).consumeEach {
      println(it)
    }
  }

  // 一个带延迟的channel 用作测试
  private fun rangeWithInterval(context: CoroutineContext, time: Long, start: Int,
      count: Int) = publish<Int>(context) {
    for (x in start until start + count) {
      delay(time) // 发送前停顿
      send(x)
    }
  }

  private fun testPub(context: CoroutineContext) = publish(context) {
    send(rangeWithInterval(context, 250, 1, 4))
    delay(100)
    send(rangeWithInterval(context, 500, 11, 3))
    delay(1100)
  }

  @Test
  fun pubMerge() = runBlocking {
    testPub(coroutineContext).merge(coroutineContext).consumeEach {
      println(it)
    }
  }
}

fun <T> Publisher<Publisher<T>>.merge(context: CoroutineContext) = publish<T>(context) {
  // 没收到一个数据 开启一个协程 由这个协程转发
  consumeEach { pub ->
    launch(coroutineContext) {
      pub.consumeEach { send(it) }
    }
  }
}

fun <T, U> Publisher<T>.takeUntil(context: CoroutineContext,
    other: Publisher<U>) = GlobalScope.publish<T>(context) {
  this@takeUntil.openSubscription().use { thisChannl ->
    other.openSubscription().use { otherChannel ->
      whileSelect {
        otherChannel.onReceive { false } // 从other收到任何数据跳出循环
        thisChannl.onReceive { send(it);true } // 从thisChannle收到数据继续循环
      }
    }
  }
}

fun CoroutineScope.rang(context: CoroutineContext, start: Int, count: Int) = publish<Int> {
  for (x in start until start + count) send(x)
}

fun <T, R> Publisher<T>.fusedFilterMap(context: CoroutineContext, predicate: (T) -> Boolean,
    mapper: (T) -> R) = publish<R> {
  consumeEach {
    if (predicate(it)) send(mapper(it))
  }
}