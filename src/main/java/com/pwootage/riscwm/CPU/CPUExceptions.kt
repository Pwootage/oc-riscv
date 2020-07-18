package com.pwootage.riscwm.CPU

class CPU_EBREAK : Exception()
data class CPU_TRAP(val trap: Trap) : Exception()