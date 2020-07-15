package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.memory.MemoryDevice
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BasicFIFO(
  override val start: UInt
) : MemoryDevice {
  override val name = "component_fifo"
  override val description = "Component interafce FIFO"
  override val length = 3u

  private var write_ready = false
  private val writeBuffer = ByteArrayOutputStream()
  private var readBuffer: ByteArrayInputStream? = null

  fun writeBufferIfReady(): ByteArray? {
    if (write_ready) {
      val res = writeBuffer.toByteArray()
      writeBuffer.reset()
      write_ready = false
      return res
    } else {
      return null
    }
  }

  override fun read8(offset: UInt): Byte {
    val rb = readBuffer
    return when (offset) {
      start -> if (rb != null && rb.available() > 0) {
        rb.read().toByte()
      } else {
        0
      }
      start + 1u -> if (rb != null && rb.available() > 0) {
        1
      } else {
        0
      }
      start + 2u -> if (write_ready) {
        1
      } else {
        0
      }
      else -> 0
    }
  }

  override fun read16(offset: UInt): Short {
    return read8(offset).toShort()
  }

  override fun read32(offset: UInt): Int {
    return read8(offset).toInt()
  }

  override fun read64(offset: UInt): Long {
    return read8(offset).toLong()
  }

  override fun write8(offset: UInt, value: Byte) {
    when (offset) {
      start -> writeBuffer.write(value.toInt())
      start + 2u -> write_ready = (value != 0.toByte())
    }
  }

  override fun write16(offset: UInt, value: Short) {
    write8(offset, value.toByte())
  }

  override fun write32(offset: UInt, value: Int) {
    write8(offset, value.toByte())
  }

  override fun write64(offset: UInt, value: Long) {
    write8(offset, value.toByte())
  }
}