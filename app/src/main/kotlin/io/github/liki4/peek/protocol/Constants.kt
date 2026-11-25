package io.github.liki4.peek.protocol

import java.util.UUID

object Constants {
    val KEEP_SERVICE: UUID = UUID.fromString("000000ff-0000-1000-8000-00805f9b34fb")
    val KEEP_CHAR: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")

    /**
     * A supported bike model: the advertised-name prefix plus its resistance
     * range. [namePrefix] is upper case; matching against the advertised name
     * is case-insensitive.
     */
    data class BikeModel(
        val namePrefix: String,
        val displayName: String,
        val maxResistance: Int,
    )

    /**
     * Supported models. C1 Mini advertises `Keep_CC_*`, C3 (K0105D)
     * advertises `KEEP_C3_*` (all upper case). Both speak the same GATT
     * (0x00FF / 0xFF01) and wire protocol; they differ in brake range:
     * CC_23 has 18 levels, C3 has 24.
     */
    val BIKE_MODELS: List<BikeModel> = listOf(
        BikeModel("KEEP_CC", "C1 Mini (CC_23)", 18),
        BikeModel("KEEP_C3", "C3 (K0105D)", 24),
    )

    /** Used when the advertised name doesn't match a known model. */
    const val DEFAULT_MAX_RESISTANCE = 18

    /** The model whose advertised name matches [name], or null if unknown. */
    fun modelFor(name: String): BikeModel? {
        val upper = name.uppercase(java.util.Locale.ROOT)
        return BIKE_MODELS.firstOrNull { upper.startsWith(it.namePrefix) }
    }

    /** True if [name] belongs to a supported Keep bike. */
    fun matchesBikeName(name: String): Boolean = modelFor(name) != null

    /** Highest resistance level the bike behind [name] supports. */
    fun maxResistanceFor(name: String): Int =
        modelFor(name)?.maxResistance ?: DEFAULT_MAX_RESISTANCE

    const val PHONE_EP = 0x3216
    const val BIKE_EP = 0xEF23

    const val MTU = 185
}
