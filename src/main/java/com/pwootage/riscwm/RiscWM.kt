package com.pwootage.riscwm

import com.pwootage.riscwm.CPU.Hart
import com.pwootage.riscwm.memory.MMU

/** This determines interrupt check frequency */
const val CYCLES_PER_INTERPRET = 64

class RiscWM {
  val mmu = MMU()
  val harts = arrayOf(
    Hart(this)
  )

  var time_base: Long = System.currentTimeMillis()
  fun readTime(): Long {
    return System.currentTimeMillis() - time_base
  }
  fun writeTime(value: Long) {
    time_base = System.currentTimeMillis() - value
  }

  var executedCycles: Long = 0

  fun interpret(cycles: Int) {
    repeat(cycles / CYCLES_PER_INTERPRET) {
      // Run each hart for n cycles
      var cont = true
      harts.forEach {
        cont = cont && it.interpret(CYCLES_PER_INTERPRET)
      }
      if (!cont) {
        return
      }
    }
  }
}
