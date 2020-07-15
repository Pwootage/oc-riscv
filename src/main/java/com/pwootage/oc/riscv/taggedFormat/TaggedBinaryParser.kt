package com.pwootage.oc.riscv.taggedFormat

import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.util.*
import javax.swing.text.html.HTML

sealed class TaggedBinary(val id: Byte) {
  object Empty: TaggedBinary(0x00)
  data class Int8(val value: Byte): TaggedBinary(0x01)
  data class Int16(val value: Short): TaggedBinary(0x02)
  data class Int32(val value: Int): TaggedBinary(0x03)
  data class Int64(val value: Long): TaggedBinary(0x04)
  data class Int128(val value: UUID): TaggedBinary(0x05)
  data class Bytes(val value: ByteArray): TaggedBinary(0x06)
  object End: TaggedBinary(0xFF.toByte())
}

fun OutputStream.writeTaggedBinary(b: TaggedBinary) {
  write(b.id.toInt())
  when (b) {
    TaggedBinary.Empty -> {
    }
    is TaggedBinary.Int8 -> write(b.value.toInt())
    is TaggedBinary.Int16 -> {
      write(((b.value.toInt()) and 0xFF))
      write((b.value.toInt() shr 8) and 0xFF)
    }
    is TaggedBinary.Int32 -> {
      write(((b.value) and 0xFF))
      write((b.value shr 8) and 0xFF)
      write((b.value shr 16) and 0xFF)
      write((b.value shr 24) and 0xFF)
    }
    is TaggedBinary.Int64 -> {
      write(((b.value) and 0xFF).toInt())
      write(((b.value shr 8) and 0xFF).toInt())
      write(((b.value shr 16) and 0xFF).toInt())
      write(((b.value shr 24) and 0xFF).toInt())
      write(((b.value shr 32) and 0xFF).toInt())
      write(((b.value shr 40) and 0xFF).toInt())
      write(((b.value shr 48) and 0xFF).toInt())
      write(((b.value shr 56) and 0xFF).toInt())
    }
    is TaggedBinary.Int128 -> {
      val hi = b.value.mostSignificantBits
      val lo = b.value.leastSignificantBits
      write(((lo) and 0xFF).toInt())
      write(((lo shr 8) and 0xFF).toInt())
      write(((lo shr 16) and 0xFF).toInt())
      write(((lo shr 24) and 0xFF).toInt())
      write(((lo shr 32) and 0xFF).toInt())
      write(((lo shr 40) and 0xFF).toInt())
      write(((lo shr 48) and 0xFF).toInt())
      write(((lo shr 56) and 0xFF).toInt())

      write(((hi) and 0xFF).toInt())
      write(((hi shr 8) and 0xFF).toInt())
      write(((hi shr 16) and 0xFF).toInt())
      write(((hi shr 24) and 0xFF).toInt())
      write(((hi shr 32) and 0xFF).toInt())
      write(((hi shr 40) and 0xFF).toInt())
      write(((hi shr 48) and 0xFF).toInt())
      write(((hi shr 56) and 0xFF).toInt())
    }
    is TaggedBinary.Bytes -> {
      write(((b.value.size) and 0xFF))
      write((b.value.size shr 8) and 0xFF)
      write((b.value.size shr 16) and 0xFF)
      write((b.value.size shr 24) and 0xFF)
      write(b.value)
    }
    TaggedBinary.End -> {
    }
  }
}

fun InputStream.readTagged(): TaggedBinary {
  val type = read()
  return when (type) {
    0x00 -> TaggedBinary.Empty
    0x01 -> {
      val value = read()
      TaggedBinary.Int8(value.toByte())
    }
    0x02 -> {
      val value = read() or (read() shl 8)
      TaggedBinary.Int16(value.toShort())
    }
    0x03 -> {
      val value = read() or
        (read() shl 8) or
        (read() shl 16) or
        (read() shl 24)
      TaggedBinary.Int32(value)
    }
    0x04 -> {
      val value = read().toLong() or
        (read().toLong() shl 8) or
        (read().toLong() shl 16) or
        (read().toLong() shl 24) or
        (read().toLong() shl 32) or
        (read().toLong() shl 40) or
        (read().toLong() shl 48) or
        (read().toLong() shl 56)
      TaggedBinary.Int64(value)
    }
    0x05 -> {
      val lo = read().toLong() or
        (read().toLong() shl 8) or
        (read().toLong() shl 16) or
        (read().toLong() shl 24) or
        (read().toLong() shl 32) or
        (read().toLong() shl 40) or
        (read().toLong() shl 48) or
        (read().toLong() shl 56)
      val hi = read().toLong() or
        (read().toLong() shl 8) or
        (read().toLong() shl 16) or
        (read().toLong() shl 24) or
        (read().toLong() shl 32) or
        (read().toLong() shl 40) or
        (read().toLong() shl 48) or
        (read().toLong() shl 56)
      TaggedBinary.Int128(UUID(hi, lo))
    }
    0x06-> {
      val len = read() or
        (read() shl 8) or
        (read() shl 16) or
        (read() shl 24)
      if (len > 0x1024) {
        throw IllegalArgumentException("Too big of a byte array")
      }
      val value = ByteArray(len)
      read(value)
      TaggedBinary.Bytes(value)
    }
    else -> TaggedBinary.End
  }
}