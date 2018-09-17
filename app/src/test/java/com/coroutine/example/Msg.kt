package com.coroutine.example

import kotlinx.coroutines.experimental.CompletableDeferred

sealed class CounterMsg

object IncConter : CounterMsg()

class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg()