package com.pwootage.riscwm.CPU

import com.pwootage.riscwm.CPU.instr.CANONICAL_NAN
import com.pwootage.riscwm.CPU.instr.exec
import com.pwootage.riscwm.RiscWM
import com.sun.org.apache.xpath.internal.operations.Bool
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class Hart(val vm: RiscWM, val hartID: Int) {
  // CPU state (persisting)
  val x: Array<Int> = Array(32) { 0 }
  val d: Array<Long> = Array(32) { 0L }
  val csr: Array<Int> = Array(4096) { 0 }
  var pc: UInt = 0x8000_0000u

  // Timer
  var cycle: Long = 0
  var mtimecmp: Long = Long.MAX_VALUE

  // Status
  // Note: these fields are assumed to have valid values! which is dangerous ;)
  // Zero-cost abstraction stuff could fix this in C++ or rust
  var TSR = 0 // Trap SRET
  var TW = 0 // Timeout Wait
  var TVM = 0 // Trap virtual memory
  var MXR = 0 // Make executable readable
  var SUM = 0 // Permit supervisor user memory access
  var MPRV = 0 // Modify priv mode: if 1, load and store are as if priv mode = MPP

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

  var MPP = 0 // Machine previous priv
  var SPP = 0 // Supervisor previous priv
  var MPIE = 0 // Machine previous interrupt enable
  var SPIE = 0 // Supervisor previous interrupt enable
  var UPIE = 0 // User previous interrupt enable
  var MIE = 0 // Machine Interrupt Enable
  var SIE = 0 // Supervisor Interrupt Enable
  var UIE = 0 // User Interrupt Enable

  /** Interrupt delegation registers */
  var medeleg = 0 // Delegate s exceptions to s?
  var mideleg = 0 // Delegate s interrupts to s?
  var sedeleg = 0 // Delegate u interrupts to u?
  var sideleg = 0 // Delegate u exceptions to u?

  /** Interrupt status bits */
  var MEIP = 0 // Machine external interrupt pending
  var SEIP = 0 // Supervisor external interrupt pending
  var UEIP = 0 // User external interupt pending
  var MTIP = 0 // Machine timer interrupt pending
  var STIP = 0 // Supervisor timer interrupt pending
  var UTIP = 0 // User timer interrupt pending
  var MSIP = 0 // Machine software interrupt pending
  var SSIP = 0 // Supervisor software interrupt pending
  var USIP = 0 // User software interrupt pending

  /** Interupt enable bits */
  var MEIE = 0 // Machine external interrupt enable
  var SEIE = 0 // Supervisor external interrupt enable
  var UEIE = 0 // User external interrupt enable
  var MTIE = 0 // Machine timer interrupt enable
  var STIE = 0 // Supervisor timer interrupt enable
  var UTIE = 0 // User timer interrupt enable
  var MSIE = 0 // Machine software interrupt enable
  var SSIE = 0 // Supervisor software interrupt enable
  var USIE = 0 // User software interrupt enable

  /** Interrupt registers */
  // Machine
  var mtvec_base = 0 // Machine vector base (right two bits are 0)
  var mtvec_mode = 0 // Machine vector mode (direct, vectored)
  var mscratch = 0 // Machine scratch: usually a pointer
  var mepc = 0 // Machine exception program counter
  var mcause = 0 // Machine cause register
  var mtval = 0 // Machine trap value
  // Supervisor
  var stvec_base = 0 // Supervisor vector base (right two bits are 0)
  var stvec_mode = 0 // Supervisor vector mode (direct, vectored)
  var sscratch = 0 // Supervisor scratch: usually a pointer
  var sepc = 0 // Supervisor exception program counter
  var scause = 0 // Supervisor cause register
  var stval = 0 // Supervisor trap value
  // User
  var utvec_base = 0 // User vector base (right two bits are 0)
  var utvec_mode = 0 // User vector mode (direct, vectored)
  var uscratch = 0 // User scratch: usually a pointer
  var uepc = 0 // User exception program counter
  var ucause = 0 // User cause register
  var utval = 0 // User trap value

  /** Wait for interrupt */
  val wfi = 0

  var priv_mode = PRIV_MODES.machine

  // Non-persisting state
  var update_pc = true

  fun interpret(cyclesToInterpret: Int): Boolean {
    // Optimization: only check certain things every n cycles
    // Trigger interrupts (e.g. timers)
    triggerInterrupts()
    // Check interrupts
    handleInterrupts()

    repeat(cyclesToInterpret) { partialCycles ->
      try {
        cycle()
        // Optimization: don't check a boolean for uncommon situations; throw exceptions
      } catch (e: CPU_EBREAK) {
        cycle += partialCycles
        return false
      } catch (e: CPU_TRAP) {
        handleTrap(e.trap)
      } catch (e: Exception) {
        cycle += partialCycles
        throw Exception("error @ ${pc.toString(16)}", e)
      }
    }
    cycle += cyclesToInterpret
    return true
  }

  fun triggerInterrupts() {
    val time = vm.readTime()
    if (time >= mtimecmp) {
      MTIP = 1
    }
  }

  fun handleInterrupts() {
    // Machine
    if (MEIP and MEIE > 0) {
      val trap = Trap(type = TrapType.MachineExternalInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        // TODO: Should we clear interrupts? The simulator I am referencing does
        // But I think the spec says no?
        MEIP = 0
        return
      }
    }
    if (MSIP and MSIE > 0) {
      val trap = Trap(type = TrapType.MachineSoftwareInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        MSIP = 0
        return
      }
    }
    if (MTIP and MTIE > 0) {
      val trap = Trap(type = TrapType.MachineTimerInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        MTIP = 0
        return
      }
    }
    // Supervisor
    if (SEIP and SEIE > 0) {
      val trap = Trap(type = TrapType.SupervisorExternalInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        SEIP = 0
        return
      }
    }
    if (SSIP and SSIE > 0) {
      val trap = Trap(type = TrapType.SupervisorSoftwareInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        SSIP = 0
        return
      }
    }
    if (STIP and STIE > 0) {
      val trap = Trap(type = TrapType.SupervisorTimerInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        STIP = 0
        return
      }
    }
    // User
    if (UEIP and UEIE > 0) {
      val trap = Trap(type = TrapType.UserExternalInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        UEIP = 0
        return
      }
    }
    if (USIP and USIE > 0) {
      val trap = Trap(type = TrapType.UserSoftwareInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        USIP = 0
        return
      }
    }
    if (UTIP and UTIE > 0) {
      val trap = Trap(type = TrapType.UserTimerInterrupt, pc = pc, value = 0)
      if (handleTrap(trap)) {
        UTIP = 0
        return
      }
    }
  }

  /** returns true if the trap was handled, false otherwise */
  fun handleTrap(trap: Trap): Boolean {
    println("TRAP!!!")

    // Figure out which priv mode should handle this
    // This is mostly ported from a different simulator
    // should verify against spec
    val currentMode = priv_mode
    val mdeleg = if (trap.type.interrupt) mideleg else medeleg
    val newMode = if (((mdeleg shr trap.type.cause) and 1) == 0) {
      PRIV_MODES.machine
    } else {
      val sdeleg = if (trap.type.interrupt) sideleg else sedeleg
      if (((sdeleg shr trap.type.cause) and 1) == 0) {
        PRIV_MODES.supervisor
      } else {
        PRIV_MODES.user
      }
    }

    // Ignore if disabled interrupt
    if (trap.type.interrupt) {
      // Interrupts aren enabled if new > current
      // Interrupts are disabled if new < current
      // Interrupts are enabled if new == current and xIE is 1
      val enabled = when {
        newMode > currentMode -> true
        newMode < currentMode -> false
        else -> when (currentMode) {
          PRIV_MODES.machine -> MIE == 1
          PRIV_MODES.supervisor -> SIE == 1
          PRIV_MODES.user -> UIE == 1
          else -> throw IllegalStateException("Invalid priv mode!")
        }

      }
      if (!enabled) {
        return false
      }

      val interruptEnabled = when (trap.type) {
        TrapType.MachineExternalInterrupt -> MEIE == 1
        TrapType.SupervisorExternalInterrupt -> SEIE == 1
        TrapType.UserExternalInterrupt -> UEIE == 1
        TrapType.MachineTimerInterrupt -> MTIE == 1
        TrapType.SupervisorTimerInterrupt -> STIE == 1
        TrapType.UserTimerInterrupt -> UTIE == 1
        TrapType.MachineSoftwareInterrupt -> MSIE == 1
        TrapType.SupervisorSoftwareInterrupt -> SSIE == 1
        TrapType.UserSoftwareInterrupt -> USIE == 1
        else -> throw IllegalStateException("Invalid interrup type in trap handler!")
      }
      if (!interruptEnabled) {
        return false
      }
    }

    priv_mode = newMode
    when (newMode) {
      PRIV_MODES.machine -> {
        mepc = trap.pc.toInt()
        mcause = trap.type.cause
        mtval = trap.value
        pc = mtvec_base.toUInt()
        if (mtvec_mode == MTVEC_MODES.vectored) {
          pc += trap.type.code.toUInt() * 4u
        }
        MPIE = MIE
        MIE = 0
        MPP = currentMode
      }
      PRIV_MODES.supervisor -> {
        sepc = trap.pc.toInt()
        scause = trap.type.cause
        stval = trap.value
        pc = stvec_base.toUInt()
        if (stvec_mode == MTVEC_MODES.vectored) {
          pc += trap.type.code.toUInt() * 4u
        }
        SPIE = SIE
        SIE = 0
        SPP = currentMode
      }
      PRIV_MODES.user -> {
        uepc = trap.pc.toInt()
        ucause = trap.type.cause
        utval = trap.value
        pc = utvec_base.toUInt()
        if (utvec_mode == MTVEC_MODES.vectored) {
          pc += trap.type.code.toUInt() * 4u
        }
        UPIE = UIE
        UIE = 0
      }
      else -> throw IllegalStateException("Invalid priv mode!")
    }

    return true
  }

  fun cycle() {
    val instr = RiscVInstruction(vm.mmu.read32(pc))
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

  fun readCSRRaw(csrNum: Int): Int {
    return when (csrNum) {
      // Timing CSRs
      CSR_ID.cycle, CSR_ID.mcycle, CSR_ID.instret, CSR_ID.minstret -> cycle.toInt()
      CSR_ID.time -> vm.readTime().toInt()
      CSR_ID.cycleh, CSR_ID.mcycleh, CSR_ID.instreth, CSR_ID.minstreth -> (cycle shr 32).toInt()
      CSR_ID.timeh -> (vm.readTime() shr 32).toInt()
      // Machine CSRs
      CSR_ID.misa, CSR_ID.mvendorid, CSR_ID.marchid, CSR_ID.mimpid -> 0
      CSR_ID.mhartid -> hartID
      CSR_ID.mstatus -> readMachineStatusRaw()
      CSR_ID.mip -> readMachineInterruptPending()
      CSR_ID.mie -> readMachineInterruptEnable()
      CSR_ID.medeleg -> medeleg
      CSR_ID.mideleg -> mideleg
      CSR_ID.sedeleg -> sedeleg
      CSR_ID.sideleg -> sideleg
      // Machine trap
      CSR_ID.mscratch -> mscratch
      CSR_ID.mepc -> mepc
      CSR_ID.mcause -> mcause
      CSR_ID.mtval -> mtval
      CSR_ID.mtvec -> mtvec_base or mtvec_mode
      // Supervisor trap
      CSR_ID.sscratch -> sscratch
      CSR_ID.sepc -> sepc
      CSR_ID.scause -> scause
      CSR_ID.stval -> stval
      CSR_ID.stvec -> stvec_base or stvec_mode
      // User trap
      CSR_ID.uscratch -> uscratch
      CSR_ID.uepc -> uepc
      CSR_ID.ucause -> ucause
      CSR_ID.utval -> utval
      CSR_ID.utvec -> utvec_base or utvec_mode
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
      CSR_ID.mip -> writeMachineInterruptPending(value)
      CSR_ID.mie -> writeMachineInterruptEnable(value)
      CSR_ID.medeleg -> medeleg = value
      CSR_ID.mideleg -> mideleg = value
      CSR_ID.sedeleg -> sedeleg = value
      CSR_ID.sideleg -> sideleg = value
      // Machine trap
      CSR_ID.mscratch -> mscratch = value
      CSR_ID.mepc -> mepc = value
      CSR_ID.mcause -> mcause = value
      CSR_ID.mtval -> mtval = value
      CSR_ID.mtvec -> {
        mtvec_base = value and (0b11.inv())
        mtvec_mode = value and 0b11
      }
      // Supervisor trap
      CSR_ID.sscratch -> sscratch = value
      CSR_ID.sepc -> sepc = value
      CSR_ID.scause -> scause = value
      CSR_ID.stval -> stval = value
      CSR_ID.stvec -> {
        stvec_base = value and (0b11.inv())
        stvec_mode = value and 0b11
      }
      // User trap
      CSR_ID.uscratch -> uscratch = value
      CSR_ID.uepc -> uepc = value
      CSR_ID.ucause -> ucause = value
      CSR_ID.utval -> utval = value
      CSR_ID.utvec -> {
        utvec_base = value and (0b11.inv())
        utvec_mode = value and 0b11
      }
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


  private fun readMachineStatusRaw(): Int {
    return (SD shr 31) or
      (TSR shr 22) or
      (TW shr 21) or
      (TVM shr 20) or
      (MXR shr 19) or
      (SUM shr 18) or
      (MPRV shr 17) or
      (XS shr 15) or
      (FS shr 13) or
      (MPP shr 11) or
      (SPP shr 8) or
      (MPIE shr 7) or
      (SPIE shr 5) or
      (UPIE shr 4) or
      (MIE shr 3) or
      (SIE shr 1) or
      (UIE shr 0)
  }

  private fun writeMachineStatusRaw(value: Int) {
//    SD = (value ushr 31) and 0b1
    TSR = (value ushr 22) and 0b1
    TW = (value ushr 21) and 0b1
    TVM = (value ushr 20) and 0b1
    MXR = (value ushr 19) and 0b1
    SUM = (value ushr 18) and 0b1
    MPRV = (value ushr 17) and 0b1
    //XS = (value ushr 15) and 0b11
    //FS = (value ushr 13) and 0b11
    MPP = (value ushr 11) and 0b11
    SPP = (value ushr 8) and 0b1
    MPIE = (value ushr 7) and 0b1
    SPIE = (value ushr 5) and 0b1
    UPIE = (value ushr 4) and 0b1
    MIE = (value ushr 3) and 0b1
    SIE = (value ushr 1) and 0b1
    UIE = (value ushr 0) and 0b1
  }

  private fun readMachineInterruptPending(): Int {
    return (MEIP shl INTERRUPT_BITS.MEI) or
      (SEIP shl INTERRUPT_BITS.SEI) or
      (UEIP shl INTERRUPT_BITS.UEI) or
      (MTIP shl INTERRUPT_BITS.MTI) or
      (STIP shl INTERRUPT_BITS.STI) or
      (UTIP shl INTERRUPT_BITS.UTI) or
      (MSIP shl INTERRUPT_BITS.MSI) or
      (SSIP shl INTERRUPT_BITS.SSI) or
      (USIP shl INTERRUPT_BITS.USI)
  }

  private fun writeMachineInterruptPending(value: Int) {
    //MEIP = (value ushr INTERRUPT_BITS.MEI) and 0b1
    SEIP = (value ushr INTERRUPT_BITS.SEI) and 0b1
    UEIP = (value ushr INTERRUPT_BITS.UEI) and 0b1
    //MTIP = (value ushr INTERRUPT_BITS.MTI) and 0b1
    STIP = (value ushr INTERRUPT_BITS.STI) and 0b1
    UTIP = (value ushr INTERRUPT_BITS.UTI) and 0b1
    MSIP = (value ushr INTERRUPT_BITS.MSI) and 0b1
    SSIP = (value ushr INTERRUPT_BITS.SSI) and 0b1
    USIP = (value ushr INTERRUPT_BITS.USI) and 0b1
  }

  private fun readMachineInterruptEnable(): Int {
    return (MEIE shl INTERRUPT_BITS.MEI) or
      (SEIE shl INTERRUPT_BITS.SEI) or
      (UEIE shl INTERRUPT_BITS.UEI) or
      (MTIE shl INTERRUPT_BITS.MTI) or
      (STIE shl INTERRUPT_BITS.STI) or
      (UTIE shl INTERRUPT_BITS.UTI) or
      (MSIE shl INTERRUPT_BITS.MSI) or
      (SSIE shl INTERRUPT_BITS.SSI) or
      (USIE shl INTERRUPT_BITS.USI)
  }

  private fun writeMachineInterruptEnable(value: Int) {
    MEIE = (value ushr INTERRUPT_BITS.MEI) and 0b1
    SEIE = (value ushr INTERRUPT_BITS.SEI) and 0b1
    UEIE = (value ushr INTERRUPT_BITS.UEI) and 0b1
    MTIE = (value ushr INTERRUPT_BITS.MTI) and 0b1
    STIE = (value ushr INTERRUPT_BITS.STI) and 0b1
    UTIE = (value ushr INTERRUPT_BITS.UTI) and 0b1
    MSIE = (value ushr INTERRUPT_BITS.MSI) and 0b1
    SSIE = (value ushr INTERRUPT_BITS.SSI) and 0b1
    USIE = (value ushr INTERRUPT_BITS.USI) and 0b1
  }
}

enum class TrapType(val interrupt: Boolean, val code: Int) {
  // Interrupts
  MachineExternalInterrupt(true, INTERRUPT_BITS.MEI),
  SupervisorExternalInterrupt(true, INTERRUPT_BITS.SEI),
  UserExternalInterrupt(true, INTERRUPT_BITS.UEI),
  MachineTimerInterrupt(true, INTERRUPT_BITS.MEI),
  SupervisorTimerInterrupt(true, INTERRUPT_BITS.STI),
  UserTimerInterrupt(true, INTERRUPT_BITS.UTI),
  MachineSoftwareInterrupt(true, INTERRUPT_BITS.MSI),
  SupervisorSoftwareInterrupt(true, INTERRUPT_BITS.SSI),
  UserSoftwareInterrupt(true, INTERRUPT_BITS.USI),

  // Exceptions
  InstructionAddressMisaligned(false, 0),
  InstructionAccessFault(false, 1),
  IllegalInstruction(false, 2),
  Breakpoint(false, 3),
  LoadAddressMisaligned(false, 4),
  LoadAccessFault(false, 5),
  StoreOrAMOAaddressMisaligned(false, 6),
  StoreOrAMOAccessFault(false, 7),
  EnvironmentCallFromUser(false, 8),
  EnvironmentCallFromSupervisor(false, 9),
  EnvironmentCallFromMachine(false, 11),
  InstructionPageFault(false, 12),
  LoadPageFault(false, 13),
  StoreOrAMOPageFault(false, 15),
  ;

  val cause = (if (interrupt) 0x8000_0000u.toInt() else 0) or code
}

data class Trap(
  val type: TrapType,
  val pc: UInt,
  val value: Int
)

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
  val supervisor = 1
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

object MTVEC_MODES {
  val direct = 0
  val vectored = 1
}

object INTERRUPT_BITS {
  val MEI = 11
  val SEI = 9
  val UEI = 8
  val MTI = 7
  val STI = 5
  val UTI = 4
  val MSI = 3
  val SSI = 1
  val USI = 0
}