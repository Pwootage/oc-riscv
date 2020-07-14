package com.pwootage.riscwm.memory

interface MemoryDevice: MemoryRead, MemoryWrite {
    val name: String
    val description: String

    val start: UInt
    val length: UInt
    val end: UInt get() = start + length

    val prettyString: String get() =
        "$name[${start.toString(16)}:${end.toString(16)}]"

    fun contains(offset: UInt): Boolean {
        return offset in start..end
    }
}

interface MemoryRead {
    fun read8(offset: UInt): Byte
    fun read16(offset: UInt): Short
    fun read32(offset: UInt): Int
    fun read64(offset: UInt): Long
}

interface MemoryWrite {
    fun write8(offset: UInt, value: Byte)
    fun write16(offset: UInt, value: Short)
    fun write32(offset: UInt, value: Int)
    fun write64(offset: UInt, value: Long)
}
