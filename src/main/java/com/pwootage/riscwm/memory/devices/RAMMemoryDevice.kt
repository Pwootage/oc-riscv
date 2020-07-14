package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.memory.MemoryDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class RAMMemoryDevice(
  override val start: UInt,
  startLength: UInt
) : MemoryDevice {
  override val name = "ram"
  override val description = "RAM buffer"
  override var length: UInt = startLength
    private set

  private var ram = ByteBuffer.allocate(startLength.toInt())
    .order(ByteOrder.LITTLE_ENDIAN)

  fun resize(newSize: UInt) {
    val newRam = ByteBuffer.allocate(newSize.toInt())
    ram.limit(min(newSize.toInt(), ram.capacity()))
    newRam.put(ram)
    ram = newRam
  }

  override fun read8(offset: UInt): Byte {
    return ram.get((offset - start).toInt())
  }

  override fun read16(offset: UInt): Short {
    return ram.getShort((offset - start).toInt())
  }

  override fun read32(offset: UInt): Int {
    return ram.getInt((offset - start).toInt())
  }

  override fun read64(offset: UInt): Long {
    return ram.getLong((offset - start).toInt())
  }

  override fun write8(offset: UInt, value: Byte) {
    ram.put((offset - start).toInt(), value)
  }

  override fun write16(offset: UInt, value: Short) {
    ram.putShort((offset - start).toInt(), value)
  }

  override fun write32(offset: UInt, value: Int) {
    ram.putInt((offset - start).toInt(), value)
  }

  override fun write64(offset: UInt, value: Long) {
    ram.putLong((offset - start).toInt(), value)
  }
}
