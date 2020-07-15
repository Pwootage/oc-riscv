package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.memory.MemoryDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ROMMemoryDevice(
    override val start: UInt,
    data: ByteArray
): MemoryDevice {
    override val name = "rom"
    override val description = "ROM buffer"
    private val rom: ByteBuffer
    override val length: UInt

    init {
        rom = ByteBuffer.allocate(data.size)
        rom.order(ByteOrder.LITTLE_ENDIAN)
        rom.put(data)
        length = rom.capacity().toUInt()
    }

    override fun read8(offset: UInt): Byte {
        return rom.get((offset - start).toInt())
    }

    override fun read16(offset: UInt): Short {
        return rom.getShort((offset - start).toInt())
    }

    override fun read32(offset: UInt): Int {
        return rom.getInt((offset - start).toInt())
    }

    override fun read64(offset: UInt): Long {
        return rom.getLong((offset - start).toInt())
    }

    override fun write8(offset: UInt, value: Byte) {
    }

    override fun write16(offset: UInt, value: Short) {
    }

    override fun write32(offset: UInt, value: Int) {
    }

    override fun write64(offset: UInt, value: Long) {
    }
}
