package com.coroutine.example

import android.view.View
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.NonCancellable
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.actor

fun View.onClick(action: suspend () -> Unit) {
  val eventActor = GlobalScope.actor<Unit>(contextJob + Dispatchers.Main) {
    for (event in channel) {
      action()
    }
  }
  // 单线程除了点击事件
  setOnClickListener {
    eventActor.offer(Unit)
  }
}

fun View.onConflatedClick(action: suspend () -> Unit) {
  // CONFLATED 合并
  val eventActor = GlobalScope.actor<Unit>(contextJob + Dispatchers.Main,
      capacity = Channel.CONFLATED) {
    for (event in channel) {
      action()
    }
  }

  setOnClickListener {
    eventActor.offer(Unit)
  }
}

// 扩展属性
val View.contextJob: Job
  get() = (context as? JobHolder)?.job ?: NonCancellable