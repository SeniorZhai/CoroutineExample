package com.coroutine.example.run

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.delay
import org.junit.Test
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.startCoroutine

fun launch(context: CoroutineContext, block: suspend () -> Unit) = block.startCoroutine(
    StandaloneCoroutine(context))

private class StandaloneCoroutine(override val context: CoroutineContext) : Continuation<Unit> {
  override fun resume(value: Unit) {}

  override fun resumeWithException(exception: Throwable) {
    val currentThread = Thread.currentThread()
    currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, exception)
  }
}

class Launch {

  @Test
  fun launchTest() {
    launch(GlobalScope.coroutineContext, {
      delay(1000)
      println("Done!!!")
    })
    Thread.sleep(2000)
  }
}
