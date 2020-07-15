package com.pwootage.riscwm.CPU

import com.pwootage.riscwm.CPU.instr.CANONICAL_NAN
import com.pwootage.riscwm.CPU.instr.exec
import com.pwootage.riscwm.memory.MMU

class CPU(val mmu: MMU) {
  val x: Array<Int> = Array(32) { 0 }
  val d: Array<Long> = Array(32) { 0L }
  var pc: UInt = 0x8000_0000u
  var user_halt = false

  /**
   * [4] NV DZ OF UF NX [0]
   * NV - 0b10000 - Invalid Operation
   * DZ - 0b01000 - Divide by Zero
   * OF - 0b00100 - Overflow
   * UF - 0b00010 - Underflow
   * NX - 0b00001 - Inexact
   */
  val fflags = 0x0
  val frm = 0x0

  object ROUNDING_MODE {
    /** Round to Nearest, ties to Even */
    val RNE = 0b000

    /** Round towards Zero */
    val RTZ = 0b001

    /** Round Down (towards −∞) */
    val RDN = 0b010

    /** Round Up (towards ∞) */
    val RUP = 0b010

    /** Round to Nearest, ties to Max Magnitude */
    val RMM = 0b100

    /** In instruction’s rm field, selects dynamic rounding mode; In Rounding Mode register, Invalid. */
    val DYN = 0b111
  }


  fun cycle() {
    val instr = RiscVInstruction(mmu.read32(pc))
    instr.exec(this)
  }

  inline fun setx(rd: Int, res: Int) {
    if (rd == 0) return
    x[rd] = res
  }

  inline fun setf(rd: Int, res: Float, fixNAN: Boolean = true) {
    // TODO: this may be unnecessary
    val f = if (fixNAN && res.isNaN()) {
      CANONICAL_NAN
    } else {
      res
    }
    d[rd] = (f.toBits().toLong()) or 0xFFFF_FFFF_0000_0000UL.toLong()
  }

  inline fun getf(rs: Int): Float {
    return Float.fromBits(d[rs].toInt())
  }

  inline fun setd(rd: Int, res: Double) {
    d[rd] = res.toBits()
  }

  inline fun getd(rs: Int): Double {
    return Double.fromBits(d[rs])
  }
}
