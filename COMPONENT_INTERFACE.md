# Memory map

|  start    |length|name|
|-----------|------|----|
|0x0200_0000|0xFFFF|Machine timer registers (mtime, mtimecmp)|
|0x1000_1000|0x3   |Component FIFO|
|0x1000_2000|0x3   |Panic FIFO (causes PANIC)|
|0x2000_0000|4096* |EEPROM|
|0x2001_0000|256*  |EEPROM data|
|0x7FFF_0000|4     |Main RAM size in bytes|
|0x8000_0000|msie  |Main RAM|
\* = configurable

# Basic FIFO (component, panic)
|start|length|name|
|-----|------|----|
|0x0|0x1|FIFO read/write|
|0x1|0x1|FIFO read ready|
|0x2|0x1|FIFO write ready|

# Machine timers
Based on CLINT from [sifive spec](https://sifive.cdn.prismic.io/sifive%2Fc89f6e5a-cf9e-44c3-a3db-04420702dcc1_sifive+e31+manual+v19.08.pdf)

|start        |length|name|
|-------------|------|----|
|0x0+hart*4   |4     |msip (soft interrupt pending)|
|0x4000+hart*8|8     |mtimecmp (interrupt when time greater than)|
|0xBFF8       |8     |mtime|
# Binary component interface

It's a simple tagged binary format, in little endian:

Types:

* null `0x00`
* int8 `0x01 0x12`
* int16 `0x02 0x1234`
* int32 `0x03 0x12345678`
* int64 `0x04 0x123456789ABCDEF`
* int128 `0x05 0x123456789ABCDEF123456789ABCDEF`
* bytes `0x06 <length_int32> (<data>)[length]` `0x6 0x00000002 0x01 0x02`
* object `0x7 [string(name) <type> <value>] string '' END`
* value `0x8 0x12345678` (OpenComputers Value - these must be disposed)
* END `0xFF`

Strings are just `bytes` objects

# Component FIFO command IDs

`command_id data`

## INVOKE `0x00`
### INPUT
* `string id`
* `string 'function_call'`
* `(param)*`
* `end`
### OUTPUT
* `int8(isError)` 0=success, 1=error
* `(result)*`
* `end`

## LIST `0x01`
### INPUT
* `(string 'type filter goes here')?`
* `end`
### OUTPUT
* `(string(type) int128(uuid))*`
* `end`

## DISPOSE_VALUE `0x02`
### INPUT
* `value`
* `end`
### OUTPUT
* `int8(isError)` 0=success 1=error (can usually be ignored) 
* `end`

# Example invoke call

*
