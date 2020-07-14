package com.pwootage.oc.riscv

import com.pwootage.oc.js.RiscVArchitecture
import li.cil.oc.api.Items
import li.cil.oc.api.Machine
import li.cil.oc.api.FileSystem
import net.minecraft.item.EnumDyeColor
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.LogManager

@Mod(
  modid = OCRISCV.ID,
  name = OCRISCV.Name,
  version = OCRISCV.Version,
  modLanguage = "kotlin",
  modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter",
  useMetadata = true,
  dependencies = "required-after:opencomputers@[1.7.5,);required-after:forgelin@[1.8,);"
)
object OCRISCV {
  const val ID = "oc-riscv"
  const val Name = "oc-riscv"
  const val Version = "@VERSION@"
  val log = LogManager.getLogger("OC-JS")

  @Mod.EventHandler
  fun preInit(e: FMLPreInitializationEvent) {
    Machine.add(RiscVArchitecture::class.java)

//    Items.registerEEPROM("EEPROM (jsboot)", StaticJSSrc.loadSrc("/assets/oc-js/bios/bootloader.js").toByteArray(), null, true)

//    Items.registerFloppy("oc.js", EnumDyeColor.LIGHT_BLUE, {
//      FileSystem.fromClass(OCJS::class.java, "oc-js", "os/")
//    }, true)
  }

  @Mod.EventHandler
  fun init(e: FMLInitializationEvent) {
  }

  @Mod.EventHandler
  fun postInit(e: FMLPostInitializationEvent) {
  }
}