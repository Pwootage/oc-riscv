package com.pwootage.riscwm.CPU

inline class RiscVInstruction(val instr: Int) {
  inline fun bits(start: Int, end: Int = start): Int {
    require(start <= end) { "Start must be before end" }
    return (instr shr start) and mask(end - start + 1)
  }

  inline fun mask(bits: Int): Int {
    return ((1 shl (bits)) - 1)
  }

  inline val opcode: Int get() = bits(0, 6)
  inline val rd: Int get() = bits(7, 11)
  inline val funct3: Int get() = bits(12, 14)
  inline val rs1: Int get() = bits(15, 19)
  inline val rs2: Int get() = bits(20, 24)
  inline val funct7: Int get() = bits(25, 31)

  // All immediates sign extend from bit 31 - so not all of these use bits
  // Sign extension: (n, 31) >> n << dest
  inline val immed_i: Int get() = (instr shr 20) shl 0
  inline val immed_s: Int get() = bits(7, 11) or ((instr shr 25) shl 5)
  inline val immed_b: Int get() = (bits(8, 11) shl 1) or (bits(25, 30) shl 5) or (bits(7) shl 11) or ((instr shr 31) shl 12)
  inline val immed_u: Int get() = instr and 0b11111111_11111111_11110000_00000000u.toInt()
  inline val immed_j: Int get() = (bits(21, 30) shl 1) or (bits(20) shl 11) or (bits(12, 19) shl 12) or ((instr shr 31) shl 20)

  // FP
  inline val funct5: Int get() = bits(27, 31)
  inline val fmt: Int get() = bits(25, 26)
  inline val rm: Int get() = bits(12, 14)

  // Compressed
  inline val c_opcpde: Int get() = bits(0, 1)
  inline val c_rs2: Int get() = bits(2, 6)
  inline val c_rs1: Int get() = bits(7, 11)
  inline val c_funct4: Int get() = bits(12, 15)
  inline val c_funct3: Int get() = bits(13, 15)
  inline val c_rs1_prime: Int get() = bits(7, 9) + 8
  inline val c_rs2_prime: Int get() = bits(2, 4) + 8
  inline val c_funct6: Int get() = bits(10, 15)
  inline val c_funct2: Int get() = bits(5, 6)
}
