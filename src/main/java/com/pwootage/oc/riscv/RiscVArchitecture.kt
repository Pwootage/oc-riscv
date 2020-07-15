package com.pwootage.oc.js

import com.pwootage.oc.riscv.OCRISCV
import com.pwootage.oc.riscv.taggedFormat.TaggedBinary
import com.pwootage.oc.riscv.taggedFormat.readTagged
import com.pwootage.riscwm.RiscWM
import com.pwootage.riscwm.memory.devices.*
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.Memory
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.ExecutionResult
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Component
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

@Architecture.Name("Risc-V rv32gc")
class RiscVArchitecture(val machine: Machine) : Architecture {
  private var _initialized = false
  private var connectedPromise = CompletableFuture<Boolean>()
  private var vm: RiscWM? = null
  private var componentFifo: BasicFIFO? = null

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
      val ram = RAMMemoryDevice(0x8000_0000u, 10000u)
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
      componentFifo = BasicFIFO(
        0x1000_1000u
      )
      mmu.physicalMemorySpace.addDevice(componentFifo!!)

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
  }

  override fun save(nbt: NBTTagCompound) {
    // TODO: save state
  }

  override fun load(nbt: NBTTagCompound) {
    // TODO: load state
    if (machine.isRunning) {
      machine.stop()
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

    // TODO: better limit than just 1m
    vm!!.interpret(1024 * 1024)

    val buffer = componentFifo?.writeBufferIfReady()
    if (buffer != null) {
      val bois = ByteArrayInputStream(buffer)
      val tags = mutableListOf<TaggedBinary>()
      while (bois.available() > 0) {
        val data = bois.readTagged()
        tags.add(data)
      }
//      processComponent
    }

    return ExecutionResult.Error("Not yet implemented ;)")
  }

  override fun runSynchronized() {
    // TODO
  }

  private fun <T> withComponent(address: String, f: (Component) -> T): T = connected {
    val component = machine.node().network().node(address) as? Component

    if (component != null && (component.canBeReachedFrom(machine.node()) || component == machine.node())) {
      f(component)
    } else {
      // TODO: is this ok? or does this need to be nullable?
      throw IllegalStateException("Couldn't find component")
    }
  }

  private fun <T> connected(fn: () -> T): T {
    if (!connectedPromise.isDone) connectedPromise.get(10, TimeUnit.SECONDS)
    return fn()
  }
}
