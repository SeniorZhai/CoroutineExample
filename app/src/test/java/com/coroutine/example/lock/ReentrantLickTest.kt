package com.coroutine.example.lock

import org.junit.Test
import java.util.concurrent.locks.ReentrantLock

class ReentrantLickTest {

  private val lock = ReentrantLock()

  private val condition = lock.newCondition()

  private fun await() {
    try {
      lock.lock()
      println("await begin")
      condition.await()
      println("await end")
    } finally {
      lock.unlock()
    }
  }

  private fun signal() {
    try {
      lock.lock()
      println("signal begin")
      condition.signal()
      println("signal end")
    } finally {
      lock.unlock()
    }
  }

  private val runnable = Runnable {
    await()
  }

  @Test
  fun testCondition() {
    Thread(runnable).start()
    Thread.sleep(3000)
    signal()
  }
  // Condition.await()会释放锁 会等到signal都才会被唤醒

}