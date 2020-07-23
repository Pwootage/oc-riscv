package com.pwootage.riscwm.memory

import com.pwootage.riscwm.CPU.*
import java.lang.IllegalStateException

inline class PageTableEntry(val entry: Int) {
  inline val valid: Boolean get() = (entry and 0b1) == 1
  inline val xwr: Int get() = (entry shr 1) and 0b111
  inline val read: Boolean get() = ((entry shr 1) and 0b1) == 1
  inline val write: Boolean get() = ((entry shr 2) and 0b1) == 1
  inline val execute: Boolean get() = ((entry shr 3) and 0b1) == 1
  inline val user: Boolean get() = ((entry shr 4) and 0b1) == 1
  inline val global: Boolean get() = ((entry shr 5) and 0b1) == 1

  // Intentionally ignored, reserved for software
  // inline val rsw: Boolean get() = ((entry shl 8) and 0b11) == 1
  // Ignored for performance reasons, for now
  //  inline val accessed: Boolean get() = ((entry shl 6) and 0b1) == 1
  //  inline val dirty: Boolean get() = ((entry shl 7) and 0b1) == 1
  inline val ppn0: UInt get() = ((entry shr 10) and 0x3FF).toUInt()
  inline val ppn1: UInt get() = ((entry shr 20) and 0xFFF).toUInt()
  inline val ppn: UInt get() = ((entry shr 10) and 0x3FFFFF).toUInt()
}

inline class VirtualAddress(val address: UInt) {
  inline val pageOffset: UInt get() = address and 0xFFFu
  inline val vpn0: UInt get() = (address shr 12) and 0x3FFu
  inline val vpn1: UInt get() = (address shr 22) and 0x3FFu
}

class PageTableCache(src: UInt, dest: UInt) {
  val src = src and PAGE_CACHE_MASK
  val dest = dest and PAGE_CACHE_MASK
  inline fun matches(address: UInt): Boolean {
    return (address and PAGE_CACHE_MASK) == src
  }

  inline fun translate(address: UInt): UInt {
    return (address and 0xFFFu) or dest
  }
}


const val PAGE_CACHE_MASK = 0xFFFF_F000u
const val PAGE_SIZE = 4096u // 2^12
const val LEVELS = 2u
const val PTE_SIZE = 4u

class VirtualMemoryManager(val physicalMemorySpace: PhysicalMemorySpace) {
  // Cache by xwr
  private val cache = Array(2) { // Priv mode (only supervisor/user will hit this code)
    Array(3) {// rwx
      PageTableCache(0u, 0u) // invalid
    }
  }

  fun translate(hart: Hart, address: UInt, rwxBit: Int): UInt {
    // TODO: Try caching by ASID? Shouldn't matter
    val c = cache[hart.priv_mode][rwxBit]
    if (c.matches(address)) {
      return c.translate(address)
    }

    val res = traversePages(hart, address, rwxBit)
    cache[hart.priv_mode][rwxBit] = PageTableCache(address, res)
    return res
  }

  private fun traversePages(hart: Hart, address: UInt, rwxBit: Int): UInt {
    // SEE: risc-v privileged spec section 4.3.2
    val va = VirtualAddress(address)
    var a = hart.satp_ppn.toUInt() * PAGE_SIZE
    var pteAddress = a + va.vpn1 * PTE_SIZE
    var pte = PageTableEntry(physicalMemorySpace.read32(pteAddress))
    if (!pte.valid) {
      pageFault(hart, va.address, rwxBit)
    }
    var pteXWR = pte.xwr
    val pa = if (pteXWR == 0) { // Branch node
      a = pte.ppn * PAGE_SIZE
      pteAddress = a + va.vpn0 * PTE_SIZE
      pte = PageTableEntry(physicalMemorySpace.read32(pteAddress))
      if (!pte.valid) {
        pageFault(hart, va.address, rwxBit)
      }
      pteXWR = pte.xwr
      if (pteXWR == 0) {
        pageFault(hart, va.address, rwxBit)
      }
      // Ignore a/d for now
      va.pageOffset or (pte.ppn0 shl 12) or (pte.ppn1 shl 22)
    } else {
      // superpage
      if (pte.ppn0 != 0u) { // verify superpage is aligned
        pageFault(hart, va.address, rwxBit)
      }
      va.pageOffset or (pte.ppn0 shl 12) or (pte.ppn1 shl 22)
    }
    if ((1 shl rwxBit) and pteXWR == 0) { // Check access
      pageFault(hart, va.address, rwxBit)
    }
    when (hart.priv_mode) {
      PRIV_MODES.user -> {
        if (!pte.user) {
          pageFault(hart, address, rwxBit)
        }
      }
      PRIV_MODES.supervisor -> {
        if (pte.user) {
          if (hart.SUM == 0 || rwxBit == MEMORY_ACCESS_TYPE.execute) {
            pageFault(hart, address, rwxBit)
          }
        }
      }
      else -> throw IllegalStateException("Invalid priv mode")
    }
    return pa
  }

  private fun pageFault(hart: Hart, address: UInt, rwxBit: Int): Nothing {
    throw CPU_TRAP(
      Trap(
        type = when (rwxBit) {
          MEMORY_ACCESS_TYPE.execute -> TrapType.InstructionPageFault
          MEMORY_ACCESS_TYPE.write -> TrapType.StoreOrAMOPageFault
          else -> TrapType.LoadPageFault
        },
        pc = hart.pc,
        value = address.toInt()
      )
    )
  }
}

object MEMORY_ACCESS_TYPE {
  val read = 0
  val write = 1
  val execute = 2
}