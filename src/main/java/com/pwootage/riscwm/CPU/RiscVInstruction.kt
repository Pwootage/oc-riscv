package com.pwootage.riscwm.CPU

inline class RiscVInstruction(val instr: Int) {
    fun bits(start: Int, end: Int = start): Int {
        require(start <= end ) { "Start must be before end" }
        return (instr shr start) and mask(end - start + 1)
    }

    inline fun mask(bits: Int): Int {
        return ((1 shl (bits)) - 1)
    }

    val is_16_bit: Boolean get() = (instr and 0b11) != 0b11

    val opcode: Int get() = bits(0, 6)
    val rd: Int get() = bits(7, 11)
    val funct3: Int get() = bits(12, 14)
    val rs1: Int get() = bits(15, 19)
    val rs2: Int get() = bits(20, 24)
    val funct7: Int get() = bits(25, 31)
    // All immediates sign extend from bit 31 - so not all of these use bits
    // Sign extension: (n, 31) >> n << dest
    val immed_i: Int get() = (instr shr 20) shl 0
    val immed_s: Int get() = bits(7, 11) or ((instr shr 25) shl 5)
    val immed_b: Int get() = (bits(8, 11) shl 1) or (bits(25, 30) shl 5) or (bits(7) shl 11) or ((instr shr 31) shl 12)
    val immed_u: Int get() = instr and 0b11111111_11111111_11110000_00000000u.toInt()
    val immed_j: Int get() = (bits(21, 30) shl 1) or (bits(20) shl 11) or (bits(12, 19) shl 12) or ((instr shr 31) shl 20)
    // FP
    val funct5: Int get() = bits(27, 31)
    val fmt: Int get() = bits(25, 26)
    val rm: Int get() = bits(12, 14)
}
