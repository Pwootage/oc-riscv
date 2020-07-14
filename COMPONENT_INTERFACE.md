# Memory map

|start|length|name|
|-----|------|----|
|0x1000_1000|0x1|Component FIFO|
|0x2000_0000|4096 (configurable)|EEPROM|
|0x2001_0000|256(configurable)|EEPROM data|
|0x8000_0000|<memory_size>|Main RAM

# Binary component interface

It's a simple tagged binary format, in little endian:

Types:

* EMPTY `0x00`
* int8 `0x01 0x12`
* int16 `0x02 0x1234`
* int32 `0x03 0x12345678`
* int64 `0x04 0x123456789ABCDEF`
* int128 `0x05 0x123456789ABCDEF123456789ABCDEF`
* bytes `0x06 <length_int32> (<data>)[length]` `0x6 0x00000002 0x01 0x02`
* object `0x7 [string(name) <type> <value>] string '' END`
* END `0xFF`

Strings are just `bytes` objects

# Component FIFO command IDs

`command_id data`

## INVOKE `0x00`
### INPUT
* `int128 uuid`
* `string 'function_call'`
* `(param)*`
* `end`
### OUTPUT
* `(result)*`
* `end`

## LIST `0x01`
### INPUT
* `(string 'type filter goes here')?`
* `end`
### OUTPUT
* `(string(type) int128(uuid))*`
* `end`

# Example invoke call

*
