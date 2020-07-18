package com.pwootage.riscwm.CPU

import com.pwootage.riscwm.CPU.instr.CANONICAL_NAN
import com.pwootage.riscwm.CPU.instr.exec
import com.pwootage.riscwm.memory.MMU
import java.lang.Exception
import java.lang.IllegalArgumentException

class CPU(val mmu: MMU) {
  // CPU state (persisting)
  val x: Array<Int> = Array(32) { 0 }
  val d: Array<Long> = Array(32) { 0L }
  val csr: Array<Int> = Array(4096) { 0 }
  var pc: UInt = 0x8000_0000u

  // Timer
  var cycle: Long = 0

  // Status
  // Note: these fields are assumed to have valid values! which is dangerous ;)
  // Zero-cost abstraction stuff could fix this in C++ or rust

  /** Trap SRET */
  var TSR = 0

  /** Timeout Wait */
  var TW = 0

  /** Trap virtual memory */
  var TVM = 0

  /** Make executable readable */
  var MXR = 0

  /** Permit supervisor user memory access */
  var SUM = 0

  /** Modify priv mode: if 1, load and store are as if priv mode = MPP */
  var MPRV = 0

  /** X/F register status, SD = FS/XS are dirty */
  // These are not supported
  //
  //| v| FS     | XS
  //|00| off    | All off
  //|01| initial| none dirty or clean, some on
  //|10| clean  | None dirty, some clean
  //|11| dirty  | some dirty
  val XS = 0
  val FS = 0
  val SD = 0

  /** Machine previous priv */
  var MPP = 0

  /* Supervisor previous priv */
  var SPP = 0

  /** Machine previous interrupt enable */
  var MPIE = 0

  /** Supervisor previous interrupt enable */
  var SPIE = 0

  /** User previous interrupt enable */
  var UPIE = 0

  /** Machine Interrupt Enable */
  var MIE = 0

  /** Supervisor Interrupt Enable */
  var SIE = 0

  /** User Interrupt Enable */
  var UIE = 0

  val priv_mode = PRIV_MODES.machine

  // Read-only, for now, but persisting
  val hartID = 0

  // Non-persisting state
  var update_pc = true

  fun interpret(cycles: Int) {
    val cyclesPerLoop = 16
    repeat(cycles / cyclesPerLoop) {
      // Optimization: only check certain things every 16 cycles
      repeat(cyclesPerLoop) { partialCycles ->
        try {
          cycle()
          // Optimization: don't check a boolean for uncommon situations; throw exceptions
        } catch (e: CPU_EBREAK) {
          cycle += partialCycles
          return
        } catch (e: Exception) {
          cycle += partialCycles
          throw Exception("error @ ${pc.toString(16)}", e)
        }
      }
      // Close enough ;)
      cycle += cyclesPerLoop
    }
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

  fun readMachineStatusRaw(): Int {
    return (SD shl 31) or
      (TSR shl 22) or
      (TW shl 21) or
      (TVM shl 20) or
      (MXR shl 19) or
      (SUM shl 18) or
      (MPRV shl 17) or
      (XS shl 15) or
      (FS shl 13) or
      (MPP shl 11) or
      (SPP shl 8) or
      (MPIE shl 7) or
      (SPIE shl 5) or
      (UPIE shl 4) or
      (MIE shl 3) or
      (SIE shl 1) or
      (UIE shl 0)
  }

  fun writeMachineStatusRaw(value: Int) {
//    SD = (value shr 31) and 0b1
    TSR = (value shr 22) and 0b1
    TW = (value shr 21) and 0b1
    TVM = (value shr 20) and 0b1
    MXR = (value shr 19) and 0b1
    SUM = (value shr 18) and 0b1
    MPRV = (value shr 17) and 0b1
    //XS = (value shr 15) and 0b11
    //FS = (value shr 13) and 0b11
    MPP = (value shr 11) and 0b11
    SPP = (value shr 8) and 0b1
    MPIE = (value shr 7) and 0b1
    SPIE = (value shr 5) and 0b1
    UPIE = (value shr 4) and 0b1
    MIE = (value shr 3) and 0b1
    SIE = (value shr 1) and 0b1
    UIE = (value shr 0) and 0b1
  }

  fun readCSRRaw(csrNum: Int): Int {
    return when (csrNum) {
      // Timing CSRs
      CSR_ID.cycle, CSR_ID.mcycle, CSR_ID.instret, CSR_ID.minstret -> cycle.toInt()
      CSR_ID.time -> System.currentTimeMillis().toInt()
      CSR_ID.cycleh, CSR_ID.mcycleh, CSR_ID.instreth, CSR_ID.minstreth -> (cycle shr 32).toInt()
      CSR_ID.timeh -> (System.currentTimeMillis() shr 32).toInt()
      // Machine CSRs
      CSR_ID.misa, CSR_ID.mvendorid, CSR_ID.marchid, CSR_ID.mimpid -> 0
      CSR_ID.mhartid -> hartID
      CSR_ID.mstatus -> readMachineStatusRaw()
      else -> csr[csrNum]
    }
  }

  fun writeCSRRaw(csrNum: Int, value: Int) {
    when (csrNum) {
      // Machine CSRs
      CSR_ID.mcycle, CSR_ID.minstret ->
        cycle = (cycle and 0xFFFF_FFFF_0000_0000UL.toLong()) or (value.toLong() and 0xFFFF_FFFFL)
      CSR_ID.mcycleh, CSR_ID.minstreth ->
        cycle = (cycle and 0xFFFF_FFFFL) or ((value.toLong() and 0xFFFF_FFFFL) shl 32)
      CSR_ID.mstatus -> writeMachineStatusRaw(value)
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