package io.github.liki4.peek.protocol

import java.util.UUID

object Constants {
    val KEEP_SERVICE: UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")
    val KEEP_CHAR: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    /**
     * Advertised-name prefixes of the supported bikes, upper case.
     *
     * C1 Mini advertises `Keep_CC_*`; C3 (K0105D) advertises `KEEP_C3_*`
     * (all upper case). Matching is case-insensitive, so both work.
     */
    val BIKE_PREFIXES: List<String> = listOf("KEEP_CC", "KEEP_C3")

    /** True if [name] belongs to a supported Keep bike. */
    fun matchesBikeName(name: String): Boolean {
        val upper = name.uppercase(java.util.Locale.ROOT)
        return BIKE_PREFIXES.any { upper.startsWith(it) }
    }

    const val PHONE_EP = 0x3216
    const val BIKE_EP = 0xEF23

    const val MTU = 185
}
