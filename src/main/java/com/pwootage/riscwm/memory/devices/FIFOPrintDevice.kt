package com.pwootage.riscwm.memory.devices

import com.pwootage.riscwm.memory.MemoryDevice

class FIFOPrintDevice(
    override val start: UInt
) : MemoryDevice {
    override val name = "debug_fifo"
    override val description = "Debug print FIFO"
    override val length: UInt = 1u

    override fun read8(offset: UInt): Byte {
        return 0
    }

    override fun read16(offset: UInt): Short {
        return 0
    }

    override fun read32(offset: UInt): Int {
        return 0
    }

    override fun read64(offset: UInt): Long {
        return 0
    }

    override fun write8(offset: UInt, value: Byte) {
        if (offset == this.start) {
            print(value.toChar())
        }
    }

    override fun write16(offset: UInt, value: Short) {
        if (offset == this.start) {
            print(value.toChar())
        }
    }

    override fun write32(offset: UInt, value: Int) {
        if (offset == this.start) {
            print(value.toChar())
        }
    }

    override fun write64(offset: UInt, value: Long) {
        if (offset == this.start) {
            print(value.toChar())
        }
    }
}
