package com.coroutine.example

import kotlinx.coroutines.experimental.Job

interface JobHolder {
  val job:Job
}
