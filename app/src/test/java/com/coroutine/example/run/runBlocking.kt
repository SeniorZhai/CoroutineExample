package com.coroutine.example.run

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.delay
import org.junit.Test
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine


fun <T> runBlocking(context: CoroutineContext, block: suspend () -> T): T =
    BlockingContinuation<T>(context).also { block.startCoroutine(it) }.getValue()

private class BlockingContinuation<T>(override val context: CoroutineContext) : Continuation<T> {
  private val lock = ReentrantLock()
  private val done = lock.newCondition()
  private var completed = false
  private var value: T? = null
  private var exception: Throwable? = null

  private inline fun <T> locked(block: () -> T): T {
    lock.lock()
    return try {
      block()
    } finally {
      lock.unlock()
    }
  }

  override fun resume(value: T) = locked {
    this.value = value
    completed = true
    done.signal()
  }

  override fun resumeWithException(exception: Throwable) {
    this.exception = exception
    completed = true
    done.signal()
  }

  fun getValue(): T = locked {
    while (!completed) done.awaitUninterruptibly() // await 等到Continuation执行完
    exception?.let { throw it }
    value as T
  }
}

class RunBlocking {
  @Test
  fun blockingTest() {
    val result = runBlocking(GlobalScope.coroutineContext) {
      delay(1000)
      "Hello world!"
    }
    println(result)
  }
}