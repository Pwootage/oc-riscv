package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.memory.MemoryDevice

class RAMSizeDevice(val ram: RAMMemoryDevice): MemoryDevice {
  override val name = "ram_size"
  override val description = "Ram size"
  override val start = (ram.start - 1u) and 0xFFFF_0000u
  override val length = 4u

  override fun read8(offset: UInt): Byte {
    return read32(offset).toByte()
  }

  override fun read16(offset: UInt): Short {
    return read32(offset).toShort()
  }

  override fun read32(offset: UInt): Int {
    if (offset == start) {
      return ram.length.toInt()
    } else {
      return 0
    }
  }

  override fun read64(offset: UInt): Long {
    return read32(offset).toLong()
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