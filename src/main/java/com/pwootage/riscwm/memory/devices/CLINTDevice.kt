package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.RiscWM
import com.pwootage.riscwm.memory.MemoryDevice
import java.lang.IllegalArgumentException

/**
 * Based on CLINT from [sifive spec](https://sifive.cdn.prismic.io/sifive%2Fc89f6e5a-cf9e-44c3-a3db-04420702dcc1_sifive+e31+manual+v19.08.pdf)
 */
class CLINTDevice(
  val vm: RiscWM
) : MemoryDevice {
  override val name = "clint"
  override val description = "core local interruptor"
  override val start = 0x0200_0000u
  override val length = 0xFFFFu

  override fun read8(offset: UInt): Byte {
    // Ignore, we only support 32/64 bit reads for now
    return 0
  }

  override fun read16(offset: UInt): Short {
    // Ignore, we only support 32/64 bit reads for now
    return 0
  }

  override fun read32(offset: UInt): Int {
    return when (offset) { // msip
      in 0x0200_0000u..0x0200_3FFFu -> {
        //MSIP
        val hart = ((offset - 0x0200_0000u) / 4u).toInt()
        if (hart < vm.harts.size) {
          vm.harts[hart].MSIP
        } else {
          0 //ignore
        }
      }
      in 0x0200_4000u..0x0200_BFF0u -> { // mtimecmp
        val hart = ((offset - 0x0200_0000u) / 8u).toInt()
        if (hart < vm.harts.size) {
          if (offset % 8u == 4u) {
            (vm.harts[hart].mtimecmp shr 32).toInt()
          } else {
            vm.harts[hart].mtimecmp.toInt()
          }
        } else {
          0 //ignore
        }
      }
      0x0200_BFF8u -> { // mtime
        if (offset % 8u == 4u) {
          (vm.readTime() shr 32).toInt()
        } else {
          vm.readTime().toInt()
        }
      }
      else -> 0
    }
  }

  override fun read64(offset: UInt): Long {
    return read32(offset).toLong() or (read32(offset + 4u).toLong() shl 32)
  }

  override fun write8(offset: UInt, value: Byte) {
    // Ignore, we only support 32/64 bit writes for now
  }

  override fun write16(offset: UInt, value: Short) {
    // Ignore, we only support 32/64 bit writes for now
  }

  override fun write32(offset: UInt, value: Int) {
    when (offset) {
      in 0x0200_0000u..0x0200_FFFFu -> {
        //MSIP
        val hart = ((offset - 0x0200_0000u) / 4u).toInt()
        if (hart < vm.harts.size) {
          if (value > 0) {
            vm.harts[hart].MSIP = 1
          } else {
            vm.harts[hart].MSIP = 0
          }
        }
      }
      in 0x0200_4000u..0x0200_BFF0u -> { // mtimecmp
        val hart = ((offset - 0x0200_0000u) / 8u).toInt()
        if (hart < vm.harts.size) {
          if (offset % 8u == 4u) {
            vm.harts[hart].mtimecmp =
              (vm.harts[hart].mtimecmp and 0xFFFF_FFFF) or
                (value.toLong() shl 32)
          } else {
            vm.harts[hart].mtimecmp =
              (vm.harts[hart].mtimecmp and 0xFFFF_FFFF_0000_0000UL.toLong()) or
                value.toLong()
          }
        }
      }
      0x0200_BFF8u -> { // mtime
        val oldTime = vm.readTime()
        if (offset % 8u == 4u) {
          val newTime = (oldTime and 0xFFFF_FFFF) or (value.toLong() shl 32)
          vm.writeTime(newTime)
        } else {
          val newTime = (oldTime and 0xFFFF_FFFF_0000_0000UL.toLong()) or value.toLong()
          vm.writeTime(newTime)
        }
      }
    }
  }

  override fun write64(offset: UInt, value: Long) {
    write32(offset, value.toInt())
    write32(offset + 4u, (value shr 32).toInt())
  }
}