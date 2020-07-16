package com.pwootage.riscwm

import com.pwootage.riscwm.CPU.CPU
import com.pwootage.riscwm.memory.MMU
import java.lang.Exception

class RiscWM {
  val mmu = MMU()
  val cpu = CPU(mmu)

  var executedCycles: Long = 0

  fun interpret(cycles: Int) {
    repeat(cycles / 16) {
      repeat(16) {
        try {
          cpu.cycle()
        } catch (e: Exception) {
          throw Exception("error @ ${cpu.pc.toString(16)}", e)
        }
        if (cpu.user_halt) {
          cpu.user_halt = false
          return
        }
      }
      executedCycles += 16
    }
  }
}
