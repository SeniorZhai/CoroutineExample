package com.coroutine.example

import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Unconfined
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.reactive.consumeEach
import kotlinx.coroutines.experimental.reactive.publish
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.coroutines.experimental.CoroutineContext

class RxTest {
  fun rangWithInterbalRx(scheduler: Scheduler, time: Long, start: Int, count: Int): Flowable<Int> =
      Flowable.zip(
          Flowable.range(start, count),
          Flowable.interval(time, MILLISECONDS, scheduler),
          BiFunction { x, _ -> x }
      )

  @Test
  fun zipTest() {
    rangWithInterbalRx(Schedulers.computation(), 100, 1, 3)
        .subscribe { println("$it on thread ${Thread.currentThread().name}") }
    Thread.sleep(1000)
  }

  fun rangWithInterbal(context: CoroutineContext, time: Long, start: Int,
      count: Int) = publish<Int>(context) {
    for (x in start until start + count) {
      delay(time)
      send(x)
    }
  }

  @Test
  fun coroutineTest() {
    Flowable.fromPublisher(rangWithInterbal(CommonPool, 100, 1, 3))
        .subscribe { println("$it on thread ${Thread.currentThread().name}") }
    Thread.sleep(1000)
  }

  @Test
  fun rxObserveOnTest() {
    Flowable.fromPublisher(rangWithInterbal(CommonPool, 100, 1, 3))
        .observeOn(Schedulers.computation())
        .subscribe { println("$it on thread ${Thread.currentThread().name}") }
    Thread.sleep(1000)
  }

  @Test
  fun coroutineTestFlowable() = runBlocking {
    rangWithInterbalRx(Schedulers.computation(), 100, 1, 3)
        .consumeEach { println("$it on thread ${Thread.currentThread().name}") }
  }

  // Rx大多数操作符没有指定线程(schedule)，它们一般会在调用它的线程中工作
  // Unconfined context 扮演同样的角色
  @Test
  fun unconfinedTest() = runBlocking {
    val job = launch(Unconfined) {
      rangWithInterbalRx(Schedulers.computation(), 100, 1, 3)
          .consumeEach {
            println("$it on thread ${Thread.currentThread().name}")
          }
    }
    job.join() // 等待协程结束
  }
}
