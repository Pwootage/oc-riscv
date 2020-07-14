package com.pwootage.riscwm.elf

import com.pwootage.riscwm.memory.MMU
import java.io.File
import java.io.RandomAccessFile
import java.lang.IllegalStateException

class ELFParser(file: File) {
    private val raf = RandomAccessFile(file, "rb")

    fun parseInto(mmu: MMU) {
        raf.seek(0)
        // Magic
        if (raf.readInt() != 0x7F454C46) {
            throw IllegalStateException("Invalid ELF magic number")
        }
        if (raf.readByte() != 1.toByte()) { // class
            throw IllegalStateException("Must be 32bit")
        }
        if (raf.readByte() != 1.toByte()) { // endian
            throw IllegalStateException("Must be little endian")
        }
        raf.readByte() // version
        raf.readByte() // abi
        raf.readByte() // abi version
        raf.skipBytes(7) // Padding
        if (raf.readShort() != 0x02.toShort()) {
            throw IllegalStateException("Must be executable")
        }

    }
}
