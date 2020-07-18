package com.pwootage.riscwm.CPU.instr

import com.pwootage.riscwm.CPU.CPU
import com.pwootage.riscwm.CPU.RiscVInstruction
import java.lang.Float.min
import java.lang.IllegalStateException
import kotlin.math.max
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
    val ECALL = 0b000
    val EBREAK = 0b000
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
}

val CANONICAL_NAN = Float.NaN

fun RiscVInstruction.exec(cpu: CPU) {
  val c = c_opcpde
  if (c != 0b11) {
    exec_compressed(cpu, c)
    if (cpu.update_pc) {
      cpu.pc += 2u
    } else {
      cpu.update_pc = true
    }
  } else {
    when (opcode) {
      OPCODES.OP_IMM -> op_imm(cpu)
      OPCODES.LUI -> lui(cpu)
      OPCODES.AUIPC -> auipc(cpu)
      OPCODES.OP -> op(cpu)
      OPCODES.JAL -> jal(cpu)
      OPCODES.JALR -> jalr(cpu)
      OPCODES.B -> b(cpu)
      OPCODES.LOAD -> load(cpu)
      OPCODES.STORE -> store(cpu)
      OPCODES.MISC_MEM -> misc_mem(cpu)
      OPCODES.SYSTEM -> system(cpu)
      OPCODES.LOAD_FP -> load_fp(cpu)
      OPCODES.STORE_FP -> store_fp(cpu)
      OPCODES.OP_FP -> op_fp(cpu)
      // TODO: throw interrupt instead
      else -> throw IllegalStateException("Invalid opcode")
    }
    if (cpu.update_pc) {
      cpu.pc += 4u
    } else {
      cpu.update_pc = true
    }
  }
}

fun RiscVInstruction.exec_compressed(cpu: CPU, op: Int) {
  when (op) {
    0b00 -> {
      when (c_funct3) {
        0b000 -> { // C.ADDI4SPN
          val imm = (bits(5) shl 3) or
            (bits(6) shl 2) or
            (bits(7, 10) shl 6) or
            (bits(11, 12) shl 4)
          if (imm == 0) throw IllegalStateException("Invalid C.ADDI4SPN immediate/invalid instruction (all zeros)") // TODO: CPU exception
          val res = cpu.x[2] + imm
          cpu.setx(c_rs2_prime, res)
        }
        0b001 -> { // C.FLD
          val imm = (bits(10, 12) shl 3) or (bits(5, 6) shl 6)
          val addr = cpu.x[c_rs1_prime] + imm
          cpu.d[c_rs2_prime] = cpu.mmu.read64(addr.toUInt())
        }
        0b010 -> { // C.LW
          val imm = (bits(6) shl 2) or (bits(10, 12) shl 3) or (bits(5) shl 6)
          val addr = cpu.x[c_rs1_prime] + imm
          cpu.setx(c_rs2_prime, cpu.mmu.read32(addr.toUInt()))
        }
        0b101 -> { // C.FSD
          val imm = (bits(10, 12) shl 3) or (bits(5, 6) shl 6)
          val addr = cpu.x[c_rs1_prime] + imm
          cpu.mmu.write64(addr.toUInt(), cpu.d[c_rs2_prime])
        }
        0b110 -> { // C.SW
          val imm = (bits(6) shl 2) or (bits(10, 12) shl 3) or (bits(5) shl 6)
          val addr = cpu.x[c_rs1_prime] + imm
          cpu.mmu.write32(addr.toUInt(), cpu.x[c_rs2_prime])
        }
        else -> throw IllegalStateException("Invalid compressed funct3") // TODO: CPU exception
      }
    }
    0b01 -> {
      when (c_funct3) {
        0b000 -> { // C.ADDI C.NOP
          val imm = bits(2, 6) or
            ((bits(12) shl 31) shr (31 - 5))
          val src = cpu.x[c_rs1]
          cpu.setx(c_rs1, src + imm)
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
          val oldPC = cpu.pc
          val newPC = oldPC.toInt() + offset
          cpu.pc = newPC.toUInt()
          cpu.update_pc = false
          cpu.setx(1, oldPC.toInt() + 2)
        }
        0b010 -> { // C.LI
          val imm = bits(2, 6) or
            ((bits(12) shl 31) shr (31 - 5))
          cpu.setx(c_rs1, imm)
        }
        0b011 -> {
          when (val rd = c_rs1) {
            2 -> { // C.ADDI16SP
              val imm = (bits(2) shl 5) or
                (bits(3, 4) shl 7) or
                (bits(5) shl 6) or
                (bits(6) shl 4) or
                ((bits(12) shl 31) shr (31 - 9))
              if (imm == 0) throw IllegalStateException("Invalid C.ADDI16SP immediate") // TODO: CPU exception
              val sp = cpu.x[2] + imm
              cpu.x[2] = sp
            }
            else -> { //C.LUI
              val imm = (bits(2, 6) shl 12) or ((bits(12) shl 31) shr (31 - 17))
              if (imm == 0) throw IllegalStateException("Invalid C.LUI immediate") // TODO: CPU exception
              cpu.setx(rd, imm)
            }
          }
        }
        0b100 -> {
          when (bits(10, 11)) {
            0b00 -> { // C.SRLI
              val shamt = bits(2, 6)
              val v = cpu.x[c_rs1] ushr shamt
              cpu.setx(c_rs1, v)
            }
            0b01 -> { //C.SRAI
              val shamt = bits(2, 6)
              val v = cpu.x[c_rs1] shr shamt
              cpu.setx(c_rs1, v)
            }
            0b10 -> { //C.ANDI
              val imm = bits(2, 6) or ((bits(12) shl 31) shr (31 - 5))
              val v = cpu.x[c_rs1] and imm
              cpu.setx(c_rs1, v)
            }
            0b11 -> {
              when (bits(12)) {
                0b0 -> {
                  when(bits(5,6)) {
                    0b00 -> { // C.SUB
                      val res = cpu.x[c_rs1] - cpu.x[c_rs2]
                      cpu.setx(c_rs1, res)
                    }
                    0b01 -> { // C.XOR
                      val res = cpu.x[c_rs1] xor cpu.x[c_rs2]
                      cpu.setx(c_rs1, res)
                    }
                    0b10 -> { // C.OR
                      val res = cpu.x[c_rs1] or cpu.x[c_rs2]
                      cpu.setx(c_rs1, res)
                    }
                    0b11 -> { // C.AND
                      val res = cpu.x[c_rs1] and cpu.x[c_rs2]
                      cpu.setx(c_rs1, res)
                    }
                  }
                }
                else -> {
                  throw IllegalStateException("Invalid compressed instruction") // TODO: CPU exception
                }
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
          val newPC = cpu.pc.toInt() + offset
          cpu.pc = newPC.toUInt()
          cpu.update_pc = false
        }
        0b110 -> { // C.BEQZ
          if (cpu.x[c_rs1_prime] == 0) {
            val offset = (bits(2) shl 5) or
              (bits(3, 4) shl 1) or
              (bits(5, 6) shl 6) or
              (bits(10, 11) shl 3) or
              ((bits(12) shl 31) shr (31 - 8))
            val newPC = cpu.pc.toInt() + offset
            cpu.pc = newPC.toUInt()
            cpu.update_pc = false
          }
        }
        0b111 -> { // C.BNEZ
          if (cpu.x[c_rs1_prime] != 0) {
            val offset = (bits(2) shl 5) or
              (bits(3, 4) shl 1) or
              (bits(5, 6) shl 6) or
              (bits(10, 11) shl 3) or
              ((bits(12) shl 31) shr (31 - 8))
            val newPC = cpu.pc.toInt() + offset
            cpu.pc = newPC.toUInt()
            cpu.update_pc = false
          }
        }
        else -> throw IllegalStateException("Invalid compressed funct3") // TODO: CPU exception
      }
    }
    0b10 -> {
      when (c_funct3) {
        0b000 -> { //C.SLLI
          val shamt = bits(2, 6)
          val v = cpu.x[c_rs1] shl shamt
          cpu.setx(c_rs1, v)
        }
        0b001 -> { //C.FLDSP
          val imm = (bits(2, 4) shl 6) or (bits(12) shl 5) or (bits(5, 6) shl 3)
          val addr = imm + cpu.x[2]
          cpu.d[c_rs1] = cpu.mmu.read64(addr.toUInt())
        }
        0b010 -> { //C.LWSP
          if (c_rs1 == 0) {
            throw IllegalStateException("Illegal c.lwsp target") // TODO: CPU exception
          }
          val imm = (bits(2, 3) shl 6) or (bits(12) shl 5) or (bits(4, 6) shl 2)
          val addr = imm + cpu.x[2]
          cpu.setx(c_rs1, cpu.mmu.read32(addr.toUInt()))
        }
        0b100 -> {
          when (bits(12)) {
            0b0 -> {
              when {
                c_rs1 != 0 && c_rs2 == 0 -> {  // C.JR
                  cpu.pc = cpu.x[c_rs1].toUInt()
                  cpu.update_pc = false
                }
                c_rs1 != 0 && c_rs2 != 0 -> {  // C.MV
                  cpu.setx(c_rs1, cpu.x[c_rs2])
                }
              }
            }
            0b1 -> {
              when {
                c_rs1 == 0 && c_rs2 == 0 -> { // C.EBREAK
                  cpu.user_halt = true
                }
                c_rs1 != 0 && c_rs2 == 0 -> { // C.JALR
                  val oldPC = cpu.pc
                  cpu.pc = cpu.x[c_rs1].toUInt()
                  cpu.update_pc = false
                  cpu.x[1] = oldPC.toInt() + 2
                }
                c_rs1 != 0 && c_rs2 != 0 -> { // C.ADD
                  val res = cpu.x[c_rs1] + cpu.x[c_rs2]
                  cpu.setx(c_rs1, res)
                }
              }
            }
          }
        }
        0b101 -> { //C.FSDSP
          val imm = (bits(10, 12) shl 3) or (bits(7, 9) shl 6)
          val addr = imm + cpu.x[2]
          cpu.mmu.write64(addr.toUInt(), cpu.d[c_rs2])
        }
        0b110 -> { //C.SWSP
          val imm = (bits(9, 12) shl 2) or (bits(7, 8) shl 6)
          val addr = imm + cpu.x[2]
          cpu.mmu.write32(addr.toUInt(), cpu.x[c_rs2])
        }
        else -> throw IllegalStateException("Invalid compressed funct3") // TODO: CPU exception
      }
    }
    else -> throw IllegalStateException("Invalid compressed opcode") // TODO: CPU exception
  }
}

inline fun RiscVInstruction.op_imm(cpu: CPU) {
  if (rd == 0) return // nop
  val src = cpu.x[rs1]
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
  cpu.setx(rd, res)
}

inline fun RiscVInstruction.lui(cpu: CPU) {
  cpu.setx(rd, immed_u)
}

inline fun RiscVInstruction.auipc(cpu: CPU) {
  // PC is still equal to this instruction
  cpu.setx(rd, immed_u + cpu.pc.toInt())
}

inline fun RiscVInstruction.op(cpu: CPU) {
  if (rd == 0) {
    if (funct3 == OPCODES.OP_FUNCT3.SLT) {
      if (rs1 == 1) {
        cpu.user_halt = true
      }
    }
    return // nop
  }
  val src1 = cpu.x[rs1]
  val src2 = cpu.x[rs2]
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
  cpu.setx(rd, res)
}

inline fun RiscVInstruction.jal(cpu: CPU) {
  // PC is still equal to this instruction
  val oldPC = cpu.pc
  val newPC = oldPC.toInt() + immed_j
  cpu.pc = newPC.toUInt()
  cpu.update_pc = false
  cpu.setx(rd, oldPC.toInt() + 4)
}

inline fun RiscVInstruction.jalr(cpu: CPU) {
  // PC is still equal to this instruction
  val oldPC = cpu.pc
  val newPC = (cpu.x[rs1] + immed_i) and 0b1.inv()
  cpu.pc = newPC.toUInt()
  cpu.update_pc = false
  cpu.setx(rd, oldPC.toInt() + 4)
}

inline fun RiscVInstruction.b(cpu: CPU) {
  val src1 = cpu.x[rs1]
  val src2 = cpu.x[rs2]
  val takeBranch = when (funct3) {
    OPCODES.B_FUNCT3.BEQ -> src1 == src2
    OPCODES.B_FUNCT3.BNE -> src1 != src2
    OPCODES.B_FUNCT3.BLT -> src1 < src2
    OPCODES.B_FUNCT3.BLTU -> src1.toUInt() < src2.toUInt()
    OPCODES.B_FUNCT3.BGE -> src1 >= src2
    OPCODES.B_FUNCT3.BGEU -> src1.toUInt() >= src2.toUInt()
    else -> throw IllegalStateException("Invalid branch funct3") // TODO: CPU exception
  }
  if (takeBranch) {
    // PC is still equal to this instruction
    val newPC = cpu.pc.toInt() + immed_b
    cpu.pc = newPC.toUInt()
    cpu.update_pc = false
  }
}

inline fun RiscVInstruction.load(cpu: CPU) {
  // Can't optimize x0 out, since it still will read the memory; this means it can have side effects for MMIO
  val addr = (cpu.x[rs1] + immed_i).toUInt()
  val v: Int = when (funct3) {
    OPCODES.LOAD_FUNCT3.LW -> cpu.mmu.read32(addr)
    OPCODES.LOAD_FUNCT3.LB -> cpu.mmu.read8(addr).toInt()
    OPCODES.LOAD_FUNCT3.LBU -> cpu.mmu.read8(addr).toInt() and 0xFF
    OPCODES.LOAD_FUNCT3.LH -> cpu.mmu.read16(addr).toInt()
    OPCODES.LOAD_FUNCT3.LHU -> cpu.mmu.read16(addr).toInt() and 0xFFFF
    else -> throw IllegalStateException("Invalid load funct3") // TODO: CPU exception
  }
  cpu.setx(rd, v)
}

inline fun RiscVInstruction.store(cpu: CPU) {
  val value = cpu.x[rs2]
  val addr = (cpu.x[rs1] + immed_s).toUInt()
  when (funct3) {
    OPCODES.STORE_FUNCT3.SW -> cpu.mmu.write32(addr, value)
    OPCODES.STORE_FUNCT3.SB -> cpu.mmu.write8(addr, value.toByte())
    OPCODES.STORE_FUNCT3.SH -> cpu.mmu.write16(addr, value.toShort())
    else -> throw IllegalStateException("Invalid write funct3") // TODO: CPU exception
  }
}

inline fun RiscVInstruction.misc_mem(cpu: CPU) {
  when (funct3) {
    OPCODES.MISC_MEM_FUNCT3.FENCE -> {
      //noop
      // TODO: multi-hart might need to impl this
    }
    OPCODES.MISC_MEM_FUNCT3.FENCE_I -> {
      //noop, unless we add an instruction cache
    }
    else -> throw IllegalStateException("Invalid memory funct3") // TODO: CPU exception
  }
}

fun RiscVInstruction.system(cpu: CPU) {
  when (funct3) {
    OPCODES.SYSTEM_FUNCT3.EBREAK/*, OPCODES.SYSTEM_FUNCT3.ECALL*/ -> {
      when (immed_i) {
        OPCODES.SYSTEM_FUNCT12.ECALL -> {
          //noop
          // TODO: implement this
        }
        OPCODES.SYSTEM_FUNCT12.EBREAK -> {
          cpu.user_halt = true
        }
        else -> throw IllegalStateException("Invalid system funct12") // TODO: CPU exception
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRW -> {
      val v = cpu.x[rs1]
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = rd != 0,
        write = { v }
      )
      if (old != null) {
        cpu.setx(rd, old)
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRS -> {
      val v = cpu.x[rs1]
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! or v }
      }
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      cpu.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRC -> {
      val v = cpu.x[rs1].inv()
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! and v }
      }
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      cpu.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRWI -> {
      val v = rs1
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = rd != 0,
        write = { v }
      )
      if (old != null) {
        cpu.setx(rd, old)
      }
    }
    OPCODES.SYSTEM_FUNCT3.CSRRSI -> {
      val v = rs1
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! or v }
      }
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      cpu.setx(rd, old!!)
    }
    OPCODES.SYSTEM_FUNCT3.CSRRCI -> {
      val v = rs1.inv()
      val write: ((Int?) -> Int)? = if (rs1 == 0) null else {
        { it!! and v }
      }
      val old = cpu.updateCsr(
        immed_i and 0xFFF,
        read = true,
        write = write
      )
      cpu.setx(rd, old!!)
    }
    else -> throw IllegalStateException("Invalid system funct3") // TODO: CPU exception
  }

}

inline fun RiscVInstruction.load_fp(cpu: CPU) {
  val addr = (cpu.x[rs1] + immed_i).toUInt()
  when (funct3) {
    OPCODES.LOAD_FP_FUNCT3.LOAD_FLOAT -> {
      val v = cpu.mmu.read32(addr).toLong()
      cpu.d[rd] = v or 0xFFFF_FFFF_0000_0000UL.toLong()
    }
    OPCODES.LOAD_FP_FUNCT3.LOAD_DOUBLE -> {
      val v = cpu.mmu.read64(addr)
      cpu.d[rd] = v
    }
    else -> throw IllegalStateException("Invalid memory funct3") // TODO: CPU exception
  }
}

inline fun RiscVInstruction.store_fp(cpu: CPU) {
  val addr = (cpu.x[rs1] + immed_s).toUInt()
  when (funct3) {
    OPCODES.STORE_FP_FUNCT3.STORE_FLOAT -> {
      val v: Int = cpu.d[rs2].toInt()
      cpu.mmu.write32(addr, v)
    }
    OPCODES.STORE_FP_FUNCT3.STORE_DOUBLE -> {
      val v = cpu.d[rs2]
      cpu.mmu.write64(addr, v)
    }
    else -> throw IllegalStateException("Invalid memory funct3") // TODO: CPU exception
  }
}

inline fun RiscVInstruction.op_fp(cpu: CPU) {
  if (fmt == OPCODES.OP_FP_FMT.FLOAT) {
    val src1 = cpu.getf(rs1)
    val src2 = cpu.getf(rs2)
    when (funct5) {
      // TODO: rounding mode, flags
      OPCODES.OP_FP_FUNCT5.FADD -> cpu.setf(rd, src2 + src2)
      OPCODES.OP_FP_FUNCT5.FSUB -> cpu.setf(rd, src1 - src2)
      OPCODES.OP_FP_FUNCT5.FMUL -> cpu.setf(rd, src1 * src2)
      OPCODES.OP_FP_FUNCT5.FDIV -> cpu.setf(rd, src1 / src2)
      OPCODES.OP_FP_FUNCT5.FSQRT -> cpu.setf(rd, sqrt(src1.toDouble()).toFloat())
      OPCODES.OP_FP_FUNCT5.FMIN_MAX -> cpu.setf(
        rd, when (rm) {
        OPCODES.OP_FP_MIN_MAX_RM.MIN -> min(src1, src2) // This should be correct for nan handling, I think
        OPCODES.OP_FP_MIN_MAX_RM.MAX -> max(src1, src2)
        else -> throw IllegalStateException("Invalid minmax mode") // TODO: CPU exception
      }
      )
      // TODO: flags, possibly missing edge cases
      OPCODES.OP_FP_FUNCT5.FCVT_W_S -> cpu.setx(
        rd, when (rs2) {
        OPCODES.OP_FP_CVT_MODE.W -> src1.toInt()
        OPCODES.OP_FP_CVT_MODE.WU -> src1.toUInt().toInt()
        else -> throw IllegalStateException("Invalid convert mode") // TODO: CPU exception
      }
      )
      OPCODES.OP_FP_FUNCT5.FCVT_S_W -> cpu.setf(
        rd, when (rs2) {
        OPCODES.OP_FP_CVT_MODE.W -> cpu.x[rs1].toFloat()
        OPCODES.OP_FP_CVT_MODE.WU -> cpu.x[rs1].toUInt().toFloat()
        else -> throw IllegalStateException("Invalid convert mode") // TODO: CPU exception
      }
      )
      OPCODES.OP_FP_FUNCT5.FSGNJ -> cpu.setf(
        rd, when (funct3) {
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJ_S -> src1.withSign(src2)
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJN_S -> src1.withSign(-src2)
        OPCODES.OP_FP_FSGNJ_FUNCT3.FSGNJX_S -> src1.withSign(src1 * src2)
        else -> throw IllegalStateException("Invalid sign injection mode") // TODO: CPU exception
      }
      )
      OPCODES.OP_FP_FUNCT5.FMV_W_X -> cpu.d[rd] = cpu.x[rs1].toLong() or 0xFFFF_FFFF_0000_0000u.toLong()
      OPCODES.OP_FP_FUNCT5.FMV_X_W, OPCODES.OP_FP_FUNCT5.FCLASS -> cpu.setx(rd, when (funct3) {
        OPCODES.OP_FP_FMV_X_W_FUNCT3.FMV_X_V -> cpu.d[rs1].toInt()
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
        else -> throw IllegalStateException("Invalid FMV/FCLASS mode") // TODO: CPU exception
      })
      OPCODES.OP_FP_FUNCT5.FCMP -> cpu.setx(
        rd, if (when (funct3) {
          OPCODES.OP_FP_FCMP_FUNCT3.FEQ -> src1 == src2
          OPCODES.OP_FP_FCMP_FUNCT3.FLE -> src1 <= src2
          OPCODES.OP_FP_FCMP_FUNCT3.FLT -> src1 < src2
          else -> throw IllegalStateException("Invalid float comparison") // TODO: CPU exception
        }
      ) 1 else 0
      )
      else -> throw IllegalStateException("Invalid memory funct3") // TODO: CPU exception
    }
  } else {
    // TODO: double
    throw IllegalStateException("Double not yet supported") // TODO: CPU exception
  }
}
