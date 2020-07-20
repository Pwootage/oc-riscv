package com.pwootage.riscwm.CPU.instr

import com.pwootage.riscwm.CPU.*
import java.lang.IllegalStateException
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.withSign

object OPCODES {
  val OP_IMM = 0b0010011

  object OP_IMM_FUNCT3 {
    val ADDI = 0b000
    val SLTI = 0b010
    val SLTIU = 0b011
    val XORI = 0b100
    val ORI = 0b110
    val ANDI = 0b111
    val SLLI = 0b001
    val SRLI = 0b101
    val SRAI = 0b101
  }

  val LUI = 0b0110111
  val AUIPC = 0b0010111
  val OP = 0b0110011

  object OP_FUNCT7 {
    val BASE = 0b0000000
    val MUL = 0b0000001
  }

  object OP_FUNCT3 {
    val ADD = 0b000
    val SUB = 0b000
    val SLL = 0b001
    val SLT = 0b010
    val SLTU = 0b011
    val XOR = 0b100
    val SRL = 0b101
    val SRA = 0b101
    val OR = 0b110
    val AND = 0b111
  }

  object OP_MUL_FUNCT3 {
    val MUL = 0b000
    val MULH = 0b001
    val MULHSU = 0b010
    val MULHU = 0b011
    val DIV = 0b100
    val DIVU = 0b101
    val REM = 0b110
    val REMU = 0b111
  }

  val JAL = 0b1101111
  val JALR = 0b1100111
  val B = 0b1100011

  object B_FUNCT3 {
    val BEQ = 0b000
    val BNE = 0b001
    val BLT = 0b100
    val BGE = 0b101
    val BLTU = 0b110
    val BGEU = 0b111
  }

  val LOAD = 0b0000011

  object LOAD_FUNCT3 {
    val LB = 0b000
    val LH = 0b001
    val LW = 0b010
    val LBU = 0b100
    val LHU = 0b101
  }

  val STORE = 0b0100011

  object STORE_FUNCT3 {
    val SB = 0b000
    val SH = 0b001
    val SW = 0b010
  }

  val MISC_MEM = 0b0001111

  object MISC_MEM_FUNCT3 {
    val FENCE = 0b000
    val FENCE_I = 0b001
  }

  val SYSTEM = 0b1110011

  object SYSTEM_FUNCT3 {
    val PRIV = 0b000
    val CSRRW = 0b001
    val CSRRS = 0b010
    val CSRRC = 0b011
    val CSRRWI = 0b101
    val CSRRSI = 0b110
    val CSRRCI = 0b111
  }

  object SYSTEM_FUNCT12 {
    val ECALL = 0b000000000000
    val EBREAK = 0b000000000001
    val MRET = 0b000000000010
    val SRET = 0b000100000010
    val URET = 0b001100000010
  }

  val LOAD_FP = 0b0000111

  object LOAD_FP_FUNCT3 {
    val LOAD_FLOAT = 0b010
    val LOAD_DOUBLE = 0b011
  }

  val STORE_FP = 0b0100111

  object STORE_FP_FUNCT3 {
    val STORE_FLOAT = 0b010
    val STORE_DOUBLE = 0b011
  }

  val OP_FP = 0b1000011

  object OP_FP_FUNCT5 {
    val FADD = 0b0000000
    val FSUB = 0b0000100
    val FMUL = 0b0001000
    val FDIV = 0b0001100
    val FSQRT = 0b0101100
    val FMIN_MAX = 0b0010100
    val FCVT_W_S = 0b1100000
    val FCVT_S_W = 0b1101000
    val FSGNJ = 0b0010000
    val FMV_X_W = 0b1110000
    val FMV_W_X = 0b1111000
    val FCMP = 0b1010000
    val FCLASS = 0b1110000
  }

  object OP_FP_FMT {
    val FLOAT = 0b010
    val DOUBLE = 0b011
  }

  object OP_FP_MIN_MAX_RM {
    val MIN = 0b000
    val MAX = 0b001
  }

  object OP_FP_CVT_MODE {
    val W = 0b00000
    val WU = 0b00001
  }

  object OP_FP_FSGNJ_FUNCT3 {
    val FSGNJ_S = 0b000
    val FSGNJN_S = 0b001
    val FSGNJX_S = 0b010
  }

  object OP_FP_FCMP_FUNCT3 {
    val FEQ = 0b010
    val FLT = 0b001
    val FLE = 0b000
  }

  object OP_FP_FMV_X_W_FUNCT3 {
    val FMV_X_V = 0b000
    val FCLASS = 0b001
  }

  val AMO = 0b0101111

  object AMO_FUNCT3 {
    val W = 0b010
  }

  object AMO_FUNCT5 {
    val AMOSWAP = 0b00001
    val AMOADD = 0b00000
    val AMOXOR = 0b00100
    val AMOAND = 0b01100
    val AMOOR = 0b01000
    val AMOMIN = 0b10000
    val AMOMAX = 0b10100
    val AMOMINU = 0b11000
    val AMOMAXU = 0b11100
  }
}

val CANONICAL_NAN = Float.NaN

fun RiscVInstruction.invalidInstruction(hart: Hart): Nothing {
  throw CPU_TRAP(Trap(
    type = TrapType.IllegalInstruction,
    pc = hart.pc,
    value = instr
  ))
}

fun RiscVInstruction.exec(hart: Hart) {
  val c = c_opcpde
  if (c != 0b11) {
    exec_compressed(hart, c)
    if (hart.update_pc) {
      hart.pc += 2u
    } else {
      hart.update_pc = true
    }
  } else {
    when (opcode) {
      OPCODES.OP_IMM -> op_imm(hart)
      OPCODES.LUI -> lui(hart)
      OPCODES.AUIPC -> auipc(hart)
      OPCODES.OP -> op(hart)
      OPCODES.JAL -> jal(hart)
      OPCODES.JALR -> jalr(hart)
      OPCODES.B -> b(hart)
      OPCODES.LOAD -> load(hart)
      OPCODES.STORE -> store(hart)
      OPCODES.MISC_MEM -> misc_mem(hart)
      OPCODES.SYSTEM -> system(hart)
      OPCODES.LOAD_FP -> load_fp(hart)
      OPCODES.STORE_FP -> store_fp(hart)
      OPCODES.OP_FP -> op_fp(hart)
      OPCODES.AMO -> amo(hart)
      else -> invalidInstruction(hart)
    }
    if (hart.update_pc) {
      hart.pc += 4u
    } else {
      hart.update_pc = true
    }
  }
}

fun RiscVInstruction.exec_compressed(hart: Hart, op: Int) {
  when (op) {
    0b00 -> {
      when (c_funct3) {
        0b000 -> { // C.ADDI4SPN
          val imm = (bits(5) shl 3) or
            (bits(6) shl 2) or
            (bits(7, 10) shl 6) or
            (bits(11, 12) shl 4)
          if (imm == 0) invalidInstruction(hart)
          val res = hart.x[2] + imm
          hart.setx(c_rs2_prime, res)
        }
        0b001 -> { // C.FLD
          val imm = (bits(10, 12) shl 3) or (bits(5, 6) shl 6)
          val addr = hart.x[c_rs1_prime] + imm
          hart.d[c_rs2_prime] = hart.vm.mmu.read64(hart, addr.toUInt())
        }
        0b010 -> { // C.LW
          val imm = (bits(6) shl 2) or (bits(10, 12) shl 3) or (bits(5) shl 6)
          val addr = hart.x[c_rs1_prime] + imm
          hart.setx(c_rs2_prime, hart.vm.mmu.read32(hart, addr.toUInt()))
        }
        0b101 -> { // C.FSD
          val imm = (bits(10, 12) shl 3) or (bits(5, 6) shl 6)
          val addr = hart.x[c_rs1_prime] + imm
          hart.vm.mmu.write64(hart, addr.toUInt(), hart.d[c_rs2_prime])
        }
        0b110 -> { // C.SW
          val imm = (bits(6) shl 2) or (bits(10, 12) shl 3) or (bits(5) shl 6)
          val addr = hart.x[c_rs1_prime] + imm
          hart.vm.mmu.write32(hart, addr.toUInt(), hart.x[c_rs2_prime])
        }
        else -> invalidInstruction(hart)
      }
    }
    0b01 -> {
      when (c_funct3) {
        0b000 -> { // C.ADDI C.NOP
          val imm = bits(2, 6) or
            ((bits(12) shl 31) shr (31 - 5))
          val src = hart.x[c_rs1]
          hart.setx(c_rs1, src + imm)
        }
        0b001 -> { // C.JAL
          val offset = (bits(2) shl 5) or
            (bits(3, 5) shl 1) or
            (bits(6) shl 7) or
            (bits(7) shl 6) or
            (bits(8) shl 10) or
            (bits(9, 10) shl 8) or
            (bits(11) shl 4) or
            ((bits(12) shl 31) shr (31 - 11))
          val oldPC = hart.pc
          val newPC = oldPC.toInt() + offset
          hart.pc = newPC.toUInt()
          hart.update_pc = false
          hart.setx(1, oldPC.toInt() + 2)
        }
        0b010 -> { // C.LI
          val imm = bits(2, 6) or
            ((bits(12) shl 31) shr (31 - 5))
          hart.setx(c_rs1, imm)
        }
        0b011 -> {
          when (val rd = c_rs1) {
            2 -> { // C.ADDI16SP
              val imm = (bits(2) shl 5) or
                (bits(3, 4) shl 7) or
                (bits(5) shl 6) or
                (bits(6) shl 4) or
                ((bits(12) shl 31) shr (31 - 9))
              if (imm == 0) invalidInstruction(hart)
              val sp = hart.x[2] + imm
              hart.x[2] = sp
            }
            else -> { //C.LUI
              val imm = (bits(2, 6) shl 12) or ((bits(12) shl 31) shr (31 - 17))
              if (imm == 0) invalidInstruction(hart)
              hart.setx(rd, imm)
            }
          }
        }
        0b100 -> {
          when (bits(10, 11)) {
            0b00 -> { // C.SRLI
              val shamt = bits(2, 6)
              val v = hart.x[c_rs1_prime] ushr shamt
              hart.setx(c_rs1_prime, v)
            }
            0b01 -> { //C.SRAI
              val shamt = bits(2, 6)
              val v = hart.x[c_rs1_prime] shr shamt
              hart.setx(c_rs1_prime, v)
            }
            0b10 -> { //C.ANDI
              val imm = bits(2, 6) or ((bits(12) shl 31) shr (31 - 5))
              val v = hart.x[c_rs1_prime] and imm
              hart.setx(c_rs1_prime, v)
            }
            0b11 -> {
              when (bits(12)) {
                0b0 -> {
                  when (bits(5, 6)) {
                    0b00 -> { // C.SUB
                      val res = hart.x[c_rs1_prime] - hart.x[c_rs2_prime]
                      hart.setx(c_rs1_prime, res)
                    }
                    0b01 -> { // C.XOR
                      val res = hart.x[c_rs1_prime] xor hart.x[c_rs2_prime]
                      hart.setx(c_rs1_prime, res)
                    }
                    0b10 -> { // C.OR
                      val res = hart.x[c_rs1_prime] or hart.x[c_rs2_prime]
                      hart.setx(c_rs1_prime, res)
                    }
                    0b11 -> { // C.AND
                      val res = hart.x[c_rs1_prime] and hart.x[c_rs2_prime]
                      hart.setx(c_rs1_prime, res)
                    }
                  }
                }
                else -> invalidInstruction(hart)
              }
            }
          }
        }
        0b101 -> { // C.J
          val offset = (bits(2) shl 5) or
            (bits(3, 5) shl 1) or
            (bits(6) shl 7) or
            (bits(7) shl 6) or
            (bits(8) shl 10) or
            (bits(9, 10) shl 8) or
            (bits(11) shl 4) or
            ((bits(12) shl 31) shr (31 - 11))
          val newPC = hart.pc.toInt() + offset
          hart.pc = newPC.toUInt()
          hart.update_pc = false
        }
        0b110 -> { // C.BEQZ
          if (hart.x[c_rs1_prime] == 0) {
            val offset = (bits(2) shl 5) or
              (bits(3, 4) shl 1) or
              (bits(5, 6) shl 6) or
              (bits(10, 11) shl 3) or
              ((bits(12) shl 31) shr (31 - 8))
            val newPC = hart.pc.toInt() + offset
            hart.pc = newPC.toUInt()
            hart.update_pc = false
          }
        }
        0b111 -> { // C.BNEZ
          if (hart.x[c_rs1_prime] != 0) {
            val offset = (bits(2) shl 5) or
              (bits(3, 4) shl 1) or
              (bits(5, 6) shl 6) or
              (bits(10, 11) shl 3) or
              ((bits(12) shl 31) shr (31 - 8))
            val newPC = hart.pc.toInt() + offset
            hart.pc = newPC.toUInt()
            hart.update_pc = false
          }
        }
        else -> invalidInstruction(hart)
      }
    }
    0b10 -> {
      when (c_funct3) {
        0b000 -> { //C.SLLI
          val shamt = bits(2, 6)
          val v = hart.x[c_rs1_prime] shl shamt
          hart.setx(c_rs1_prime, v)
        }
        0b001 -> { //C.FLDSP
          val imm = (bits(2, 4) shl 6) or (bits(12) shl 5) or (bits(5, 6) shl 3)
          val addr = imm + hart.x[2]
          hart.d[c_rs1] = hart.vm.mmu.read64(hart, addr.toUInt())
        }
        0b010 -> { //C.LWSP
          if (c_rs1 == 0) {
            invalidInstruction(hart)
          }
          val imm = (bits(2, 3) shl 6) or (bits(12) shl 5) or (bits(4, 6) shl 2)
          val addr = imm + hart.x[2]
          hart.setx(c_rs1, hart.vm.mmu.read32(hart, addr.toUInt()))
        }
        0b100 -> {
          when (bits(12)) {
            0b0 -> {
              when {
                c_rs1 != 0 && c_rs2 == 0 -> {  // C.JR
                  hart.pc = hart.x[c_rs1].toUInt()
                  hart.update_pc = false
                }
                c_rs1 != 0 && c_rs2 != 0 -> {  // C.MV
                  hart.setx(c_rs1, hart.x[c_rs2])
                }
              }
            }
            0b1 -> {
              when {
                c_rs1 == 0 && c_rs2 == 0 -> { // C.EBREAK
                  hart.pc += 2u // Exception prevents PC increment
                  throw CPU_EBREAK()
                }
                c_rs1 != 0 && c_rs2 == 0 -> { // C.JALR
                  val oldPC = hart.pc
                  hart.pc = hart.x[c_rs1].toUInt()
                  hart.update_pc = false
                  hart.x[1] = oldPC.toInt() + 2
                }
                c_rs1 != 0 && c_rs2 != 0 -> { // C.ADD
                  val res = hart.x[c_rs1] + hart.x[c_rs2]
                  hart.setx(c_rs1, res)
                }
              }
            }
          }
        }
        0b101 -> { //C.FSDSP
          val imm = (bits(10, 12) shl 3) or (bits(7, 9) shl 6)
          val addr = imm + hart.x[2]
          hart.vm.mmu.write64(hart, addr.toUInt(), hart.d[c_rs2])
        }
        0b110 -> { //C.SWSP
          val imm = (bits(9, 12) shl 2) or (bits(7, 8) shl 6)
          val addr = imm + hart.x[2]
          hart.vm.mmu.write32(hart, addr.toUInt(), hart.x[c_rs2])
        }
        else -> invalidInstruction(hart)
      }
    }
    else -> invalidInstruction(hart)
  }
}

inline fun RiscVInstruction.op_imm(hart: Hart) {
  if (rd == 0) return // nop
  val src = hart.x[rs1]
  val imm = immed_i

  @Suppress("DUPLICATE_LABEL_IN_WHEN")
  val res = when (funct3) {
    OPCODES.OP_IMM_FUNCT3.ADDI -> src + imm
    OPCODES.OP_IMM_FUNCT3.SLTI -> if (src < imm) 1 else 0
    OPCODES.OP_IMM_FUNCT3.SLTIU -> if (src.toUInt() < imm.toUInt()) 1 else 0
    OPCODES.OP_IMM_FUNCT3.XORI -> src xor imm
    OPCODES.OP_IMM_FUNCT3.ORI -> src or imm
    OPCODES.OP_IMM_FUNCT3.ANDI -> src and imm
    OPCODES.OP_IMM_FUNCT3.SLLI -> src shl (imm and 0b11111)
    OPCODES.OP_IMM_FUNCT3.SRLI, OPCODES.OP_IMM_FUNCT3.SRAI -> if (bits(30) == 0) {
      src ushr (imm and 0b11111)
    } else {
      src shr (imm and 0b11111)
    }
    else -> throw IllegalStateException("Impossible funct3")
  }
  hart.setx(rd, res)
}

inline fun RiscVInstruction.lui(hart: Hart) {
  hart.setx(rd, immed_u)
}

inline fun RiscVInstruction.auipc(hart: Hart) {
  // PC is still equal to this instruction
  hart.setx(rd, immed_u + hart.pc.toInt())
}

inline fun RiscVInstruction.op(hart: Hart) {
  if (rd == 0) {
    if (funct3 == OPCODES.OP_FUNCT3.SLT) {
      if (rs1 == 1) {
        hart.pc += 4u // Exception prevents PC increment
        throw CPU_EBREAK()
      }
    }
    return // nop
  }
  val src1 = hart.x[rs1]
  val src2 = hart.x[rs2]
  val res: Int = if (funct7 == OPCODES.OP_FUNCT7.MUL) {
    when (funct3) {
      OPCODES.OP_MUL_FUNCT3.MUL -> src1 * src2
      OPCODES.OP_MUL_FUNCT3.MULH -> ((src1.toLong() * src2.toLong()) shr 32).toInt()
      OPCODES.OP_MUL_FUNCT3.MULHU -> ((src1.toUInt().toULong() * src2.toUInt().toULong()) shr 32).toInt()
      OPCODES.OP_MUL_FUNCT3.MULHSU -> ((src1.toULong() * src2.toUInt().toULong()) shr 32).toInt()
      OPCODES.OP_MUL_FUNCT3.DIV -> {
        if (src2 == 0) {
          0.inv()
        } else if (src1 == Int.MIN_VALUE && src2 == -1) {
          Int.MIN_VALUE
        } else {
          src1 / src2
        }
      }
      OPCODES.OP_MUL_FUNCT3.DIVU -> {
        if (src2 == 0) {
          0.inv()
        } else {
          (src1.toUInt() / src2.toUInt()).toInt()
        }
      }
      OPCODES.OP_MUL_FUNCT3.REM -> {
        if (src2 == 0) {
          src1
        } else if (src1 == Int.MIN_VALUE && src2 == -1) {
          0
        } else {
          src1 % src2
        }
      }
      OPCODES.OP_MUL_FUNCT3.REMU -> {
        if (src2 == 0) {
          src1
        } else {
          (src1.toUInt() % src2.toUInt()).toInt()
        }
      }
      else -> throw IllegalStateException("Impossible mul funct3")
    }
  } else {
    @Suppress("DUPLICATE_LABEL_IN_WHEN")
    when (funct3) {
      OPCODES.OP_FUNCT3.ADD, OPCODES.OP_FUNCT3.SUB -> if (bits(30) == 0) src1 + src2 else src1 - src2
      OPCODES.OP_FUNCT3.SLL -> src1 shl (src2 and 0b11111)
      OPCODES.OP_FUNCT3.SRL, OPCODES.OP_FUNCT3.SRA -> if (bits(30) == 0) src1 ushr (src2 and 0b11111) else src1 shr (src2 and 0b11111)
      OPCODES.OP_FUNCT3.SLT -> if (src1 < src2) 1 else 0
      OPCODES.OP_FUNCT3.SLTU -> if (src1.toUInt() < src2.toUInt()) 1 else 0
      OPCODES.OP_FUNCT3.XOR -> src1 xor src2
      OPCODES.OP_FUNCT3.OR -> src1 or src2
      OPCODES.OP_FUNCT3.AND -> src1 and src2
      else -> throw IllegalStateException("Impossible op funct3")
    }
  }
  hart.setx(rd, res)
}

inline fun RiscVInstruction.jal(hart: Hart) {
  // PC is still equal to this instruction
  val oldPC = hart.pc
  val newPC = oldPC.toInt() + immed_j
  hart.pc = newPC.toUInt()
  hart.update_pc = false
  hart.setx(rd, oldPC.toInt() + 4)
}

inline fun RiscVInstruction.jalr(hart: Hart) {
  // PC is still equal to this instruction
  val oldPC = hart.pc
  val newPC = (hart.x[rs1] + immed_i) and 0b1.inv()
  hart.pc = newPC.toUInt()
  hart.update_pc = false
  hart.setx(rd, oldPC.toInt() + 4)
}

inline fun RiscVInstruction.b(hart: Hart) {
  val src1 = hart.x[rs1]
  val src2 = hart.x[rs2]
  val takeBranch = when (funct3) {
    OPCODES.B_FUNCT3.BEQ -> src1 == src2
    OPCODES.B_FUNCT3.BNE -> src1 != src2
    OPCODES.B_FUNCT3.BLT -> src1 < src2
    OPCODES.B_FUNCT3.BLTU -> src1.toUInt() < src2.toUInt()
    OPCODES.B_FUNCT3.BGE -> src1 >= src2
    OPCODES.B_FUNCT3.BGEU -> src1.toUInt() >= src2.toUInt()
    else -> invalidInstruction(hart)
  }
  if (takeBranch) {
    // PC is still equal to this instruction
    val newPC = hart.pc.toInt() + immed_b
    hart.pc = newPC.toUInt()
    hart.update_pc = false
  }
}

inline fun RiscVInstruction.load(hart: Hart) {
  // Can't optimize x0 out, since it still will read the memory; this means it can have side effects for MMIO
  val addr = (hart.x[rs1] + immed_i).toUInt()
  val v: Int = when (funct3) {
    OPCODES.LOAD_FUNCT3.LW -> hart.vm.mmu.read32(hart, addr)
    OPCODES.LOAD_FUNCT3.LB -> hart.vm.mmu.read8(hart, addr).toInt()
    OPCODES.LOAD_FUNCT3.LBU -> hart.vm.mmu.read8(hart, addr).toInt() and 0xFF
    OPCODES.LOAD_FUNCT3.LH -> hart.vm.mmu.read16(hart, addr).toInt()
    OPCODES.LOAD_FUNCT3.LHU -> hart.vm.mmu.read16(hart, addr).toInt() and 0xFFFF
    else -> invalidInstruction(hart)
  }
  hart.setx(rd, v)
}

inline fun RiscVInstruction.store(hart: Hart) {
  val value = hart.x[rs2]
  val addr = (hart.x[rs1] + immed_s).toUInt()
  when (funct3) {
    OPCODES.STORE_FUNCT3.SW -> hart.vm.mmu.write32(hart, addr, value)
    OPCODES.STORE_FUNCT3.SB -> hart.vm.mmu.write8(hart, addr, value.toByte())
    OPCODES.STORE_FUNCT3.SH -> hart.vm.mmu.write16(hart, addr, value.toShort())
    else -> invalidInstruction(hart)
  }
}

inline fun RiscVInstruction.misc_mem(hart: Hart) {
  when (funct3) {
    OPCODES.MISC_MEM_FUNCT3.FENCE -> {
      //noop
      // TODO: multi-hart might need to impl this
    }
    OPCODES.MISC_MEM_FUNCT3.FENCE_I -> {
      //noop, unless we add an instruction cache
    }
    else -> invalidInstruction(hart)
  }
}

fun RiscVInstruction.system(hart: Hart) {
  when (funct3) {
    OPCODES.SYSTEM_FUNCT3.PRIV -> {
      when (immed_i) {
        OPCODES.SYSTEM_FUNCT12.ECALL -> {
          val type = when (hart.priv_mode) {
            PRIV_MODES.machine -> TrapType.EnvironmentCallFromMachine
            PRIV_MODES.supervisor -> TrapType.EnvironmentCallFromSupervisor
            PRIV_MODES.user -> TrapType.EnvironmentCallFromUser
            else -> throw IllegalStateException("Invalid priv mode!")
          }
          throw CPU_TRAP(
            Trap(
              type = type,
              pc = hart.pc,
              value = 0
            )
          )
        }
        OPCODES.SYSTEM_FUNCT12.EBREAK -> {
          hart.pc += 4u // Exception prevents PC increment
          throw CPU_EBREAK()
        }
        OPCODES.SYSTEM_FUNCT12.MRET -> {
          if (hart.priv_mode < PRIV_MODES.machine) invalidInstruction(hart)
          hart.MIE = hart.MPIE
          hart.MPIE = 1
          val mpp = hart.MPP
          hart.MPP = 0
          hart.priv_mode = mpp
        }
        OPCODES.SYSTEM_FUNCT12.SRET -> {
          if (hart.priv_mode < PRIV_MODES.supervisor) invalidInstruction(hart)
          hart.SIE = hart.SPIE
          hart.SPIE = 1
          val spp = hart.SPP
          hart.SPP = 0
          hart.priv_mode = spp
        }
        OPCODES.SYSTEM_FUNCT12.URET -> {
          hart.UIE = hart.UPIE
          hart.UPIE = 1
        }
        else -> invalidInstruction(hart)
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRW -> {
      val v = hart.x[rs1]
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = rd != 0,
        write = { v }
      )
      if (old != null) {
        hart.setx(rd, old)
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRS -> {
      val v = hart.x[rs1]
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! or v }
      }
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      hart.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRC -> {
      val v = hart.x[rs1].inv()
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! and v }
      }
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      hart.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRWI -> {
      val v = rs1
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = rd != 0,
        write = { v }
      )
      if (old != null) {
        hart.setx(rd, old)
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRSI -> {
      val v = rs1
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! or v }
      }
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      hart.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRCI -> {
      val v = rs1.inv()
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! and v }
      }
      val old = hart.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      hart.setx(rd, old!!)
    }
    else -> invalidInstruction(hart)
  }

}

inline fun RiscVInstruction.load_fp(hart: Hart) {
  val addr = (hart.x[rs1] + immed_i).toUInt()
  when (funct3) {
    OPCODES.LOAD_FP_FUNCT3.LOAD_FLOAT -> {
      val v = hart.vm.mmu.read32(hart, addr).toLong()
      hart.d[rd] = v or 0xFFFF_FFFF_0000_0000UL.toLong()
    }
    OPCODES.LOAD_FP_FUNCT3.LOAD_DOUBLE -> {
      val v = hart.vm.mmu.read64(hart, addr)
      hart.d[rd] = v
    }
    else -> invalidInstruction(hart)
  }
}

inline fun RiscVInstruction.store_fp(hart: Hart) {
  val addr = (hart.x[rs1] + immed_s).toUInt()
  when (funct3) {
    OPCODES.STORE_FP_FUNCT3.STORE_FLOAT -> {
      val v: Int = hart.d[rs2].toInt()
      hart.vm.mmu.write32(hart, addr, v)
    }
    OPCODES.STORE_FP_FUNCT3.STORE_DOUBLE -> {
      val v = hart.d[rs2]
      hart.vm.mmu.write64(hart, addr, v)
    }
    else -> invalidInstruction(hart)
  }
}

inline fun RiscVInstruction.op_fp(hart: Hart) {
  if (fmt == OPCODES.OP_FP_FMT.FLOAT) {
    val src1 = hart.getf(rs1)
    val src2 = hart.getf(rs2)
    when (funct5) {
      // TODO: rounding mode, flags
      OPCODES.OP_FP_FUNCT5.FADD -> hart.setf(rd, src2 + src2)
      OPCODES.OP_FP_FUNCT5.FSUB -> hart.setf(rd, src1 - src2)
      OPCODES.OP_FP_FUNCT5.FMUL -> hart.setf(rd, src1 * src2)
      OPCODES.OP_FP_FUNCT5.FDIV -> hart.setf(rd, src1 / src2)
      OPCODES.OP_FP_FUNCT5.FSQRT -> hart.setf(rd, sqrt(src1.toDouble()).toFloat())
      OPCODES.OP_FP_FUNCT5.FMIN_MAX -> hart.setf(
        rd, when (rm) {
        OPCODES.OP_FP_MIN_MAX_RM.MIN -> min(src1, src2) // This should be correct for nan handling, I think
        OPCODES.OP_FP_MIN_MAX_RM.MAX -> max(src1, src2)
        else -> invalidInstruction(hart)
      })
      // TODO: flags, possibly missing edge cases
      OPCODES.OP_FP_FUNCT5.FCVT_W_S -> hart.setx(
        rd, when (rs2) {
        OPCODES.OP_FP_CVT_MODE.W -> src1.toInt()
        OPCODES.OP_FP_CVT_MODE.WU -> src1.toUInt().toInt()
        else -> invalidInstruction(hart)
      }
      )
      OPCODES.OP_FP_FUNCT5.FCVT_S_W -> hart.setf(
        rd, when (rs2) {
        OPCODES.OP_FP_CVT_MODE.W -> hart.x[rs1].toFloat()
        OPCODES.OP_FP_CVT_MODE.WU -> hart.x[rs1].toUInt().toFloat()
        else -> invalidInstruction(hart)
      }
      )
      OPCODES.OP_FP_FUNCT5.FSGNJ -> hart.setf(
        rd, when (funct3) {
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJ_S -> src1.withSign(src2)
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJN_S -> src1.withSign(-src2)
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJX_S -> src1.withSign(src1 * src2)
        else -> invalidInstruction(hart)
      }
      )
      OPCODES.OP_FP_FUNCT5.FMV_W_X -> hart.d[rd] = hart.x[rs1].toLong() or 0xFFFF_FFFF_0000_0000u.toLong()
      OPCODES.OP_FP_FUNCT5.FMV_X_W, OPCODES.OP_FP_FUNCT5.FCLASS -> hart.setx(rd, when (funct3) {
        OPCODES.OP_FP_FMV_X_W_FUNCT3.FMV_X_V -> hart.d[rs1].toInt()
        OPCODES.OP_FP_FMV_X_W_FUNCT3.FCLASS -> {
          var clazz = 0
          if (src1.isInfinite() && src1 < 0) {
            clazz = clazz or 0b00_0000_0001
          }
          if (src1 < 0) {
            clazz = clazz or 0b00_0000_0010
          }
          // I have no idea what subnormal means
          //if (src1.isNegativeSubnormal()) {
          //    clazz = clazz or 0b00_0000_0100
          //}
          if (src1.toBits() == 0x8000_0000u.toInt()) {
            clazz = clazz or 0b00_0000_1000
          }
          if (src1.toBits() == 0) {
            clazz = clazz or 0b00_0001_0000
          }
          // I have no idea what subnormal means
          //if (src1.isPositiveSubnormal()) {
          //    clazz = clazz or 0b00_0010_0000
          //}
          if (src1 > 0) {
            clazz = clazz or 0b00_0100_0000
          }
          if (src1.isInfinite() && src1 > 0) {
            clazz = clazz or 0b00_1000_0000
          }
          if (src1.toBits() == CANONICAL_NAN.toBits()) {
            clazz = clazz or 0b01_0000_0000
          }
          if (src1.isNaN() && src1.toBits() != CANONICAL_NAN.toBits()) {
            clazz = clazz or 0b10_0000_0000
          }
          clazz
        }
        else -> invalidInstruction(hart)
      })
      OPCODES.OP_FP_FUNCT5.FCMP -> hart.setx(
        rd, if (when (funct3) {
          OPCODES.OP_FP_FCMP_FUNCT3.FEQ -> src1 == src2
          OPCODES.OP_FP_FCMP_FUNCT3.FLE -> src1 <= src2
          OPCODES.OP_FP_FCMP_FUNCT3.FLT -> src1 < src2
          else -> invalidInstruction(hart)
        }
      ) 1 else 0
      )
      else -> invalidInstruction(hart)
    }
  } else {
    // TODO: double
    invalidInstruction(hart)
  }
}

inline fun RiscVInstruction.amo(hart: Hart) {
  when (funct3) {
    OPCODES.AMO_FUNCT3.W -> {
      hart.vm.mmu.atomicLock.withLock {
        val addr = hart.x[rs1].toUInt()
        val src = hart.x[rs2]
        val mem = hart.vm.mmu.read32(hart, addr)
        val res = when (funct5) {
          OPCODES.AMO_FUNCT5.AMOSWAP -> src
          OPCODES.AMO_FUNCT5.AMOADD -> mem + src
          OPCODES.AMO_FUNCT5.AMOAND -> mem and src
          OPCODES.AMO_FUNCT5.AMOOR -> mem or src
          OPCODES.AMO_FUNCT5.AMOXOR -> mem xor src
          OPCODES.AMO_FUNCT5.AMOMAX -> max(mem, src)
          OPCODES.AMO_FUNCT5.AMOMAXU -> max(mem.toUInt(), src.toUInt()).toInt()
          OPCODES.AMO_FUNCT5.AMOMIN -> min(mem, src)
          OPCODES.AMO_FUNCT5.AMOMINU -> min(mem.toUInt(), src.toUInt()).toInt()
          else -> invalidInstruction(hart)
        }
        hart.vm.mmu.write32(hart, addr, res)
        hart.setx(rd, mem)
      }
    }
    else -> invalidInstruction(hart)
  }
}