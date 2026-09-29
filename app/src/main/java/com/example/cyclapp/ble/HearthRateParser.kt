package com.example.cyclapp.ble


object HeartRateParser {
    fun parse(data: ByteArray): Int? {
        if (data.size < 2) return null
        val flags = data[0].toInt()
        val isSixteenBit = (flags and 0x01) != 0

        return if (isSixteenBit) {
            if (data.size < 3) null
            else ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8))
        } else {
            data[1].toInt() and 0xFF
        }
    }
}