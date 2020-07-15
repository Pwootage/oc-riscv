package com.pwootage.riscwm

import com.pwootage.riscwm.CPU.CPU
import com.pwootage.riscwm.memory.MMU

class RiscWM {
  val mmu = MMU()
  val cpu = CPU(mmu)

  var executedCycles: Long = 0

  fun interpret(cycles: Int) {
    repeat(cycles / 16) {
      repeat(16) {
        cpu.cycle()
        if (cpu.user_halt) {
          cpu.user_halt = false
          return
        }
      }
      executedCycles += 16
    }
  }
}
