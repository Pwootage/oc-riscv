package com.pwootage.oc.riscv.value

import com.pwootage.oc.riscv.OCRISCV
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.Value
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants
import java.util.concurrent.atomic.AtomicInteger

class ValueManager(val machine: Machine) {
  private var currentID = AtomicInteger(0)
  private val values = mutableMapOf<Int, Value>()
  private val reverseMap = mutableMapOf<Value, Int>()

  fun add(value: Value): Int {
    val existingID = reverseMap[value]
    return if (existingID != null) {
      existingID
    } else {
      val id = currentID.incrementAndGet()
      values[id] = value
      id
    }
  }

  fun get(id: Int): Value? {
    return values[id]
  }

  fun destroy(id: Int) {
    val value = values.remove(id)
    reverseMap.remove(value)
    value?.dispose(machine)
  }

  fun clear() {
    val ids = values.keys
    for (id in ids) {
      destroy(id)
    }
  }

  fun load(nbt: NBTTagCompound) {
    clear()
    val list = nbt.getTagList("values", Constants.NBT.TAG_COMPOUND)
    val count = list.tagCount()
    for (i in 0 until count) {
      val wrapper = list.getCompoundTagAt(i)
      val id = wrapper.getInteger("id")
      val valueCompound = wrapper.getCompoundTag("value")
      val clazzName = wrapper.getString("class")
      try {
        val clazz = Class.forName(clazzName)
        val value = clazz.newInstance() as Value
        value.load(valueCompound)
        values[id] = value
      } catch (e: Exception) {
        OCRISCV.log.error("Failed to restore Value from NBT", e)
      }
    }
  }

  fun save(nbt: NBTTagCompound) {
    val list = NBTTagList()
    for ((id, value) in values) {
      val wrapper = NBTTagCompound()
      wrapper.setInteger("id", id)
      wrapper.setString("class", value::class.java.name)
      val valueCompound = NBTTagCompound()
      value.save(valueCompound)
      wrapper.setTag("value", valueCompound)
      list.appendTag(wrapper)
    }
  }
}