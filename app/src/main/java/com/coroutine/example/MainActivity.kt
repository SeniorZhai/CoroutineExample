package com.coroutine.example

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.base
import kotlinx.android.synthetic.main.activity_main.tv
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

class MainActivity : AppCompatActivity() {

  var index = 0
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    base.setOnClickListener {
      base()
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

  private fun log(string: StringBuffer, msg: String) {
    string.append("\n[${Thread.currentThread().name}] $msg")
  }

  private fun showContent(content: String) {
    launch(UI) {
      tv.append(content)
    }
  }
}
