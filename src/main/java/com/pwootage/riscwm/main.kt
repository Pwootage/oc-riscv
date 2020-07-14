import com.pwootage.riscwm.CPU.RiscVInstruction
import com.pwootage.riscwm.RiscWM
import com.pwootage.riscwm.memory.devices.FIFOPrintDevice
import com.pwootage.riscwm.memory.devices.RAMMemoryDevice
import com.pwootage.riscwm.memory.devices.ROMMemoryDevice
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.measureNanoTime

fun main() {
    val vm = RiscWM()
    vm.mmu.physicalMemorySpace.addDevice(
        FIFOPrintDevice(0x1000_0000u)
    )
    val data = Files.readAllBytes(Paths.get("eeprom.bin"))
    vm.mmu.physicalMemorySpace.addDevice(
        ROMMemoryDevice(0x1100_0000u, data)
    )
    vm.mmu.physicalMemorySpace.addDevice(
        RAMMemoryDevice(0x8000_0000u, 8u * 1024u * 1024u) //8mb
    )

    vm.cpu.pc = 0x1100_0000u
    val time = measureNanoTime {
        vm.interpret(64*1024*1024)
    }
    val s = time.toDouble() / 1_000_000_000.0
    val ips = vm.executedCycles.toDouble() / s
    val mips = ips / 1_000_000

    println()
    println("Executed ${vm.executedCycles} cycles in ${time}ns/${time.toDouble()/1_000_000.0}ms")
    println("ips = $ips/mips = $mips")
}
