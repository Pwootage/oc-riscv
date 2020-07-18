package com.pwootage.riscwm.CPU

import com.pwootage.riscwm.CPU.instr.CANONICAL_NAN
import com.pwootage.riscwm.CPU.instr.exec
import com.pwootage.riscwm.memory.MMU
import java.lang.IllegalArgumentException

class CPU(val mmu: MMU) {
  // CPU state
  val x: Array<Int> = Array(32) { 0 }
  val d: Array<Long> = Array(32) { 0L }
  val csr: Array<Int> = Array(4096) { 0 }
  var pc: UInt = 0x8000_0000u

  val priv_mode = PRIV_MODES.machine
  var cycle: Long = 0

  // Non-persisting state
  var user_halt = false
  var update_pc = true


  fun cycle() {
    val instr = RiscVInstruction(mmu.read32(pc))
    instr.exec(this)
    cycle += 1
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

  fun readCSRRaw(csrNum: Int): Int {
    return when (csrNum) {
      CSR_ID.cycle, CSR_ID.mcycle, CSR_ID.instret, CSR_ID.minstret -> cycle.toInt()
      CSR_ID.time -> System.currentTimeMillis().toInt()
      CSR_ID.cycleh, CSR_ID.mcycleh, CSR_ID.instreth, CSR_ID.minstreth -> (cycle shr 32).toInt()
      CSR_ID.timeh -> (System.currentTimeMillis() shr 32).toInt()
      else -> csr[csrNum]
    }
  }

  fun writeCSRRaw(csrNum: Int, value: Int) {
    when (csrNum) {
      CSR_ID.mcycle, CSR_ID.minstret ->
        cycle = (cycle and 0xFFFF_FFFF_0000_0000UL.toLong()) or (value.toLong() and 0xFFFF_FFFFL)
      CSR_ID.mcycleh, CSR_ID.minstreth ->
        cycle = (cycle and 0xFFFF_FFFFL) or ((value.toLong() and 0xFFFF_FFFFL) shl 32)
      else -> csr[csrNum] = value
    }
  }

  fun updateCsr(csrNum: Int, read: Boolean, write: ((Int?) -> Int)?): Int? {
    val level = (csrNum ushr 8) and 0b11
    val canWrite = ((csrNum ushr 10) and 0b11) != 0b11

    if (priv_mode < level) {
      throw IllegalArgumentException("Do not have read permission") // TODO: cpu exceptoin
    }

    val old = if (read) readCSRRaw(csrNum) else null
    if (write != null) {
      if (canWrite) {
        writeCSRRaw(csrNum, write(old))
      } else {
        throw IllegalArgumentException("Do not have write permission") // TODO: cpu exceptoin
      }
    }
    return old
  }
}

object CSR_ID {
  // User read/write
  // User trap setup
  val ustatus = 0x000
  val uie = 0x004
  val utvec = 0x05

  // User trap handling
  val uscratch = 0x040
  val uepc = 0x041
  val ucause = 0x042
  val utval = 0x043
  val uip = 0x044

  // User floating-point
  val fflags = 0x001
  val frm = 0x002
  val fcsr = 0x003

  // User read-only
  // Timers
  val cycle = 0xC00
  val time = 0xC01
  val instret = 0xC02

  // Don't bother with the hardware perf monitors
  val cycleh = 0xC80
  val timeh = 0xC81
  val instreth = 0xC82
  // Don't bother with the hardware perf monitors

  // Supervisor read/write
  // Trap setup
  val sstatus = 0x100
  val sedeleg = 0x102
  val sideleg = 0x103
  val sie = 0x104
  val stvec = 0x105
  val scounteren = 0x106

  // Trap handling
  val sscratch = 0x140
  val sepc = 0x141
  val scause = 0x142
  val stval = 0x143
  val sip = 0x144

  // Supervisor protection and translation
  val satp = 0x180

  // Machine readonly
  val mvendorid = 0xF11
  val marchid = 0xF12
  val mimpid = 0xF13
  val mhartid = 0xF14

  // Machine trap setup
  val mstatus = 0x300
  val misa = 0x301
  val medeleg = 0x302
  val mideleg = 0x303
  val mie = 0x304
  val mtvec = 0x305
  val mcounteren = 0x306

  // Machine trap handling
  val mscratch = 0x340
  val mepc = 0x341
  val mcause = 0x342
  val mtval = 0x343
  val mip = 0x344

  // Machine memory protection
  val pmpcfg0 = 0x3A0
  val pmpcfg1 = 0x3A1
  val pmpcfg2 = 0x3A2
  val pmpcfg3 = 0x3A3
  val pmpaddr0 = 0x3B0
  val pmpaddr1 = 0x3B1
  val pmpaddr2 = 0x3B2
  val pmpaddr3 = 0x3B3
  val pmpaddr4 = 0x3B4
  val pmpaddr5 = 0x3B5
  val pmpaddr6 = 0x3B6
  val pmpaddr7 = 0x3B7
  val pmpaddr8 = 0x3B8
  val pmpaddr9 = 0x3B9
  val pmpaddr10 = 0x3BA
  val pmpaddr11 = 0x3BB
  val pmpaddr12 = 0x3BC
  val pmpaddr13 = 0x3BD
  val pmpaddr14 = 0x3BE
  val pmpaddr15 = 0x3BF

  // Machine counters/timers
  val mcycle = 0xB00
  val minstret = 0xB02
  // Don't bother with the perf counters
  val mcycleh = 0xB80
  val minstreth = 0xB82
  // Don't bother with the perf counters

  // Machine counter setup
  // Don't bother

  // Debug registers
  // Don't bother
}

object PRIV_MODES {
  val user = 0
  val system = 1
  val hypervisor = 2 // not supported, atm
  val machine = 3
}

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