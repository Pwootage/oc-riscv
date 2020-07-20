package com.pwootage.riscwm.memory

import com.pwootage.riscwm.CPU.Hart
import com.pwootage.riscwm.CPU.PRIV_MODES
import com.pwootage.riscwm.memory.devices.RAMMemoryDevice
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class MMU {
  val PHYS_BITS = 32
  val physicalMemorySpace = PhysicalMemorySpace(
    start = 0u,
    length = 1u shl PHYS_BITS
  )
  val virtualMemory = VirtualMemoryManager(physicalMemorySpace)
  val atomicLock = ReentrantLock()
  private val reservation = AtomicInteger(0)

  inline fun translate(hart: Hart, address: UInt, xwr: Int): UInt {
    if (hart.priv_mode > PRIV_MODES.supervisor) {
      return address
    }
    return if (hart.satp_mode == 0) {
      address
    } else {
      //sv32
      virtualMemory.translate(hart, address, xwr)
    }
  }

  fun setReservation(address: UInt) {
    reservation.set((address and 0xFFFF_FFF0u).toInt())
  }

  fun reservationValid(address: UInt): Boolean {
    return reservation.compareAndSet((address and 0xFFFF_FFF0u).toInt(), 0)
  }

  private inline fun handle_reservation(address: UInt) {
    reservation.compareAndSet((address and 0xFFFF_FFF0u).toInt(), 0)
  }

  fun fetchInstruction(hart: Hart, offset: UInt): Int {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.execute)
    handle_reservation(offset)
    return physicalMemorySpace.read32(addr)
  }

  fun read8(hart: Hart, offset: UInt): Byte {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.read)
    handle_reservation(offset)
    return physicalMemorySpace.read8(addr)
  }

  fun read16(hart: Hart, offset: UInt): Short {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.read)
    handle_reservation(offset)
    return physicalMemorySpace.read16(addr)
  }

  fun read32(hart: Hart, offset: UInt): Int {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.read)
    handle_reservation(offset)
    return physicalMemorySpace.read32(addr)
  }

  fun read64(hart: Hart, offset: UInt): Long {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.read)
    handle_reservation(offset)
    return physicalMemorySpace.read64(addr)
  }

  fun write8(hart: Hart, offset: UInt, value: Byte) {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.write)
    handle_reservation(offset)
    physicalMemorySpace.write8(addr, value)
  }

  fun write16(hart: Hart, offset: UInt, value: Short) {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.write)
    handle_reservation(offset)
    physicalMemorySpace.write16(addr, value)
  }

  fun write32(hart: Hart, offset: UInt, value: Int) {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.write)
    handle_reservation(offset)
    physicalMemorySpace.write32(addr, value)
  }

  fun write64(hart: Hart, offset: UInt, value: Long) {
    val addr = translate(hart, offset, MEMORY_ACCESS_TYPE.write)
    handle_reservation(offset)
    physicalMemorySpace.write64(addr, value)
  }
}
