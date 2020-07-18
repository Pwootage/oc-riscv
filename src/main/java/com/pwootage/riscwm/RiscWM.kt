package com.pwootage.riscwm

import com.pwootage.riscwm.CPU.CPU
import com.pwootage.riscwm.memory.MMU
import java.lang.Exception

class RiscWM {
  val mmu = MMU()
  val cpu = CPU(mmu)

  var executedCycles: Long = 0

  fun interpret(cycles: Int) {
    cpu.interpret(cycles)
  }
}
