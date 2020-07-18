package com.pwootage.oc.js

import com.pwootage.oc.riscv.OCRISCV
import com.pwootage.oc.riscv.taggedFormat.*
import com.pwootage.oc.riscv.value.ValueManager
import com.pwootage.riscwm.RiscWM
import com.pwootage.riscwm.memory.devices.*
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.Memory
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.ExecutionResult
import li.cil.oc.api.machine.LimitReachedException
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Component
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.io.ByteArrayInputStream
import java.lang.Exception
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

sealed class InvokeResult {
  data class Success(
    val results: Array<Any?>
  ) : InvokeResult()

  data class Error(
    val message: String
  ) : InvokeResult()

  data class Sync(
    val id: String,
    val method: String,
    val args: Array<Any?>
  ) : InvokeResult()
}

@Architecture.Name("Risc-V rv32gc")
class RiscVArchitecture(val machine: Machine) : Architecture {
  private var _initialized = false
  private var connectedPromise = CompletableFuture<Boolean>()
  private var vm: RiscWM? = null
  private var componentFifo: BasicFIFO? = null
  private var panicFifo: BasicFIFO? = null
  private var syncCall: InvokeResult.Sync? = null
  private var valueManger = ValueManager(machine)

  override fun isInitialized(): Boolean = _initialized

  override fun onConnect() {
    connectedPromise.complete(true)
  }

  override fun onSignal() {
    // TODO?
  }

  override fun initialize(): Boolean {
    try {
      if (_initialized) return _initialized

      valueManger.clear()
      vm = createVM()

      _initialized = true
    } catch (e: Throwable) {
      OCRISCV.log.error("Error in initialize", e)
      machine.crash("Error in initialize: $e")
    }
    return _initialized
  }

  private fun createVM(): RiscWM {
    return RiscWM().apply {
      // TODO: based on RAM size
      val ram = RAMMemoryDevice(0x8000_0000u, 8u * 1024u * 1024u) // 8mb
      mmu.physicalMemorySpace.addDevice(ram)
      mmu.physicalMemorySpace.addDevice(
        RAMSizeDevice(ram)
      )
      mmu.physicalMemorySpace.addDevice(
        FIFOPrintDevice(0x1000_0000u)
      )
      // TODO: replace w/ real deal
      val data = Files.readAllBytes(Paths.get("eeprom.bin"))
      mmu.physicalMemorySpace.addDevice(
        ROMMemoryDevice(0x2000_0000u, data)
      )
      mmu.physicalMemorySpace.addDevice(
        ROMMemoryDevice(0x2001_0000u, ByteArray(256))
      )
      componentFifo = BasicFIFO(0x1000_1000u)
      mmu.physicalMemorySpace.addDevice(componentFifo!!)
      panicFifo = BasicFIFO(0x1000_2000u)
      mmu.physicalMemorySpace.addDevice(panicFifo!!)

      cpu.pc = 0x2000_0000u
    }
  }

  override fun recomputeMemory(components: Iterable<ItemStack>): Boolean {
    // TODO: config option for 64 bit ratio? Do we need this ratio?
    val memory = ceil(memoryInBytes(components) * 1.8).toInt()
    if (_initialized) {
      // TODO: configure this. Can actually *increase* at runtime,
      // and *possibly* decrease if I'm very, very careful with the OS-level support
    }
    return memory > 0
  }

  private fun memoryInBytes(components: Iterable<ItemStack>) = components.sumByDouble {
    when (val mem = Driver.driverFor(it)) {
      is Memory -> mem.amount(it) * 1024
      else -> 0.0
    }
  }.roundToInt().coerceIn(0..64 * 1024 * 1024) // TODO: 64mb? Sure! Make configurable.

  override fun close() {
    vm = null
    _initialized = false
    valueManger.clear()
  }

  override fun save(nbt: NBTTagCompound) {
    // TODO: save state
    valueManger.save(nbt)
  }

  override fun load(nbt: NBTTagCompound) {
    // TODO: load state
    if (machine.isRunning) {
      machine.stop()

      valueManger.load(nbt)

      machine.start()
    }
  }

  override fun runThreaded(isSynchronizedReturn: Boolean): ExecutionResult {
    // TODO: handle these
    val signal = if (isSynchronizedReturn) {
      null
    } else {
      machine.popSignal()
    }

    while (true) {
      // TODO: better limit than just 1m
      vm!!.interpret(1024 * 1024)
      val panicBuffer = panicFifo!!.writeBufferIfReady()
      if (panicBuffer != null) {
        val panicStr = String(panicBuffer)
        println("PANIC: $panicStr")
        return ExecutionResult.Error(panicStr)
      }

      val buffer = componentFifo?.writeBufferIfReady()
      if (buffer != null) {
        val bois = ByteArrayInputStream(buffer)
        val tags = mutableListOf<TaggedBinary>()
        while (bois.available() > 0) {
          val data = bois.readTagged()
          tags.add(data)
        }
        val callRes = processComponentCall(tags)
        if (callRes != null) {
          return callRes
        }
      } else {
        break
      }
    }

    return ExecutionResult.Error("Not yet implemented ;)")
  }

  override fun runSynchronized() {
    val sc = syncCall
    syncCall = null
    if (sc != null) {
      val r = invoke(sc.id, sc.method, sc.args)
      if (r != null) {
        throw IllegalStateException("Somehow managed to get a sync call during a sync call")
      }
    }
  }

  private fun processComponentCall(tags: List<TaggedBinary>): ExecutionResult? {
    if (tags.isEmpty()) {
      return ExecutionResult.Error("Invalid component call: no data provided")
    }
    if (tags.last() !is TaggedBinary.End) {
      return ExecutionResult.Error("Invalid component call: last param must be End")
    }
    val first = tags[0]
    if (first !is TaggedBinary.Int8) {
      return ExecutionResult.Error("Invalid component call: first param must be int8")
    }
    return when (first.value) {
      0x00.toByte() -> {
        processComponentInvoke(tags.drop(1).dropLast(1))
      }
      0x01.toByte() -> {
        processComponentList(tags.drop(1).dropLast(1))
      }
      0x02.toByte() -> {
        processComponentDisposeValue(tags.drop(1).dropLast(1))
      }
      else -> ExecutionResult.Error("Invalid component call: Invalid call id 0x${first.value.toString(16)}")
    }
  }

  private fun processComponentInvoke(tags: List<TaggedBinary>): ExecutionResult? {
    if (tags.size < 2) {
      return ExecutionResult.Error("Invalid component invoke: requires at least 2 params <uuid>, <function-name>")
    }
    val idTag = tags[0]
    if (idTag !is TaggedBinary.Bytes) {
      return ExecutionResult.Error("Invalid component invoke: first param must be bytes (UUID)")
    }
    val methodTag = tags[1]
    if (methodTag !is TaggedBinary.Bytes) {
      return ExecutionResult.Error("Invalid component invoke: second param must be bytes (function name)")
    }
    val id = String(idTag.value)
    val method = String(methodTag.value)
    val args = tags.drop(2).map { it.toJava(valueManger) }
      .toTypedArray()

    val res = invoke(id, method, args)
    if (res != null) {
      syncCall = res
      return ExecutionResult.SynchronizedCall()
    }

    return null
  }

  private fun processComponentList(tags: List<TaggedBinary>): ExecutionResult? {
    val filter = tags.firstOrNull()?.let {
      if (it !is TaggedBinary.Bytes) {
        return ExecutionResult.Error("Invalid component invoke: first param must be bytes (filter)")
      }
      String(it.value)
    } ?: ""

    val res = synchronized(machine.components()) {
      machine.components()
        .filter { it.value.contains(filter) }
        .flatMap {
          listOf(
            TaggedBinary.Bytes(it.value.toByteArray()),
            TaggedBinary.Bytes(it.key.toByteArray())
          )
        }
        .toList()
    }

    val tb = res + listOf(TaggedBinary.End)
    val buff = tb.toBytes()
    componentFifo!!.setReadBuffer(buff)

    return null
  }

  private fun processComponentDisposeValue(tags: List<TaggedBinary>): ExecutionResult? {
    if (tags.size != 1) {
      return ExecutionResult.Error("Invalid component invoke: requires one parameter (value to dispose)")
    }
    val id = tags.first().let {
      if (it !is TaggedBinary.Value) {
        return ExecutionResult.Error("Invalid component invoke: first param must be the value to dispose")
      }
      it.value
    }

    valueManger.destroy(id)

    // Success, end
    val tb = listOf(TaggedBinary.Int8(0), TaggedBinary.End)
    val buff = tb.toBytes()
    componentFifo!!.setReadBuffer(buff)

    return null
  }

  private fun invoke(id: String, method: String, args: Array<Any?>): InvokeResult.Sync? {
    val invokeResult = withComponent(id) { comp ->
      val m = machine.methods(comp.host())[method]
      if (m == null) {
        InvokeResult.Error("Unknown method $method")
      } else {
        if (m.direct) {
          try {
            val res = machine.invoke(id, method, args)
            InvokeResult.Success(res)
          } catch (e: LimitReachedException) {
            InvokeResult.Sync(id, method, args)
          } catch (e: Exception) {
            InvokeResult.Error("Error invoking $method: ${e::class.java.name} ${e.message}")
          }
        } else {
          InvokeResult.Sync(id, method, args)
        }
      }
    } ?: InvokeResult.Error("Unknown component $id")

    when (invokeResult) {
      is InvokeResult.Success -> {
        val tb = invokeResult.results.toTaggedBinary(valueManger)
        val res =
          listOf(TaggedBinary.Int8(0)) +
          tb +
          listOf(TaggedBinary.End)
        val buff = res.toBytes()
        componentFifo!!.setReadBuffer(buff)
      }
      is InvokeResult.Error -> {
        val res = listOf(
          TaggedBinary.Int8(1),
          TaggedBinary.Bytes(invokeResult.message.toByteArray()),
          TaggedBinary.End
        )
        val buff = res.toBytes()
        componentFifo!!.setReadBuffer(buff)
      }
      is InvokeResult.Sync -> return invokeResult
    }
    return null
  }

  private fun <T> withComponent(address: String, f: (Component) -> T): T? = connected {
    val component = machine.node().network().node(address) as? Component

    if (component != null && (component.canBeReachedFrom(machine.node()) || component == machine.node())) {
      f(component)
    } else {
      // TODO: is this ok? or does this need to be nullable?
      null
    }
  }

  private fun <T> connected(fn: () -> T): T {
    if (!connectedPromise.isDone) connectedPromise.get(10, TimeUnit.SECONDS)
    return fn()
  }
}
