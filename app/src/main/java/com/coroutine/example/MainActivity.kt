package com.coroutine.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.actor
import kotlinx.android.synthetic.main.activity_main.base
import kotlinx.android.synthetic.main.activity_main.conflated
import kotlinx.android.synthetic.main.activity_main.jump
import kotlinx.android.synthetic.main.activity_main.tv
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.Main
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withContext

class MainActivity : AppCompatActivity(),JobHolder {

  override val job = Job()

  override fun onDestroy() {
    super.onDestroy()
    job.cancel()
  }

  var index = 0
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    base.setOnClickListener {
      base()
    }
    jump.setOnClickListener {
      jump()
    }
    actor.onClick {
      delay(1000)
      showContent("\nClick")
    }
    conflated.onConflatedClick {
      showContent("\nconflated start")
      delay(1000)
      showContent("\nconflated end")
    }
  }

  private fun base() {
    runBlocking {
      val string = StringBuffer()
      val a = async {
        log(string, "I'm computing a piece of the answer")
        6
      }
      val b = async {
        log(string, "I'm computing another piece of the answer")
        7
      }
      log(string, "The answer is ${a.await() * b.await()}")
      showContent(string.toString())
    }
  }

  private fun jump(){
    val string = StringBuffer()
    // use 可以在不使用线程后释放线程
    newSingleThreadContext("Ctx1").use {
      ctx1->
      newSingleThreadContext("Ctx2").use {
        ctx2->
        runBlocking(ctx1) {
          log(string,"Started in ctx1")
          withContext(ctx2){
            log(string,"Working in ctx2")
          }
          log(string,"Back to ctx1")
          showContent(string.toString())
        }
      }
    }
  }

  private fun log(string: StringBuffer, msg: String) {
    string.append("\n[${Thread.currentThread().name}] $msg")
  }

  private fun showContent(content: String) {
     GlobalScope.launch(Dispatchers.Main) {
      tv.append(content)
    }
  }

}
