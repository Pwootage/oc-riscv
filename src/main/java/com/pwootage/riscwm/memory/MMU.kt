package com.pwootage.riscwm.memory

import com.pwootage.riscwm.memory.devices.RAMMemoryDevice

class MMU: MemoryRead, MemoryWrite {
    val PHYS_BITS = 32
    val VIRT_BITS = 32
    val physicalMemorySpace = PhysicalMemorySpace(
        start = 0u,
        length = 1u shl PHYS_BITS
    )
    val virtual_memory_enabled = false

    override fun read8(offset: UInt): Byte {
        return physicalMemorySpace.read8(offset)
    }

    override fun read16(offset: UInt): Short {
        return physicalMemorySpace.read16(offset)
    }

    override fun read32(offset: UInt): Int {
        return physicalMemorySpace.read32(offset)
    }

    override fun read64(offset: UInt): Long {
        return physicalMemorySpace.read64(offset)
    }

    override fun write8(offset: UInt, value: Byte) {
        physicalMemorySpace.write8(offset, value)
    }

    override fun write16(offset: UInt, value: Short) {
        physicalMemorySpace.write16(offset, value)
    }

    override fun write32(offset: UInt, value: Int) {
        physicalMemorySpace.write32(offset, value)
    }

    override fun write64(offset: UInt, value: Long) {
        physicalMemorySpace.write64(offset, value)
    }
}
