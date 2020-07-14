package com.pwootage.riscwm.memory

import java.lang.IllegalStateException

class PhysicalMemorySpace(
    override val start: UInt,
    override val length: UInt
) : MemoryDevice {
    private val devices = mutableListOf<MemoryDevice>()

    override val name = "physical_memory"
    override val description = "Entire physical address space"

    fun getDevice(offset: UInt): MemoryDevice? {
        // TODO: swap out the device list for a device tree
        for (device in devices) {
            if (device.contains(offset)) {
                return device
            }
        }
        return null
    }

    fun addDevice(dev: MemoryDevice) {
        for (existingDevice in devices) {
            // Check if they overlap
            if (
                existingDevice.contains(dev.start) || existingDevice.contains(dev.end) ||
                dev.contains(existingDevice.start) || dev.contains(existingDevice.end)
            ) {
                throw IllegalStateException(
                    "Conflict with existing device: ${existingDevice.prettyString} (new device: ${dev.prettyString})"
                )
            }
        }
        devices.add(dev)
    }

    override fun read8(offset: UInt): Byte {
        return getDevice(offset)!!.read8(offset)
    }

    override fun read16(offset: UInt): Short {
        return getDevice(offset)!!.read16(offset)
    }

    override fun read32(offset: UInt): Int {
        return getDevice(offset)!!.read32(offset)
    }

    override fun read64(offset: UInt): Long {
        return getDevice(offset)!!.read64(offset)
    }

    override fun write8(offset: UInt, value: Byte) {
        getDevice(offset)!!.write8(offset, value)
    }

    override fun write16(offset: UInt, value: Short) {
        getDevice(offset)!!.write16(offset, value)
    }

    override fun write32(offset: UInt, value: Int) {
        getDevice(offset)!!.write32(offset, value)
    }

    override fun write64(offset: UInt, value: Long) {
        getDevice(offset)!!.write64(offset, value)
    }
}
