package com.graviton94.notificationcleaner.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.math.BigInteger

/** Big numbers are saved as decimal strings (07 architecture: exact, never floating point). */
object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor = PrimitiveSerialDescriptor("BigInteger", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigInteger) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder) = BigInteger(decoder.decodeString())
}

/**
 * The whole game save: one JSON document, written as a transaction after every swipe, purchase and reset
 * (18 meta-and-ops). Unknown keys are ignored and missing keys take their defaults, so older saves load into
 * newer rules; [SAVE_VERSION] marks format breaks that need a migration.
 */
object SaveCodec {
    const val SAVE_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    fun encode(s: GameState): String = """{"save":$SAVE_VERSION,"packs":${json.encodeToString(Catalog.packs.map { it.name })},"state":${json.encodeToString(GameState.serializer(), s)}}"""

    fun decode(text: String): GameState {
        val root = json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
        val version = root["save"]?.toString()?.toInt() ?: 0
        require(version <= SAVE_VERSION) { "save $version is newer than this build ($SAVE_VERSION)" }
        return json.decodeFromJsonElement(GameState.serializer(), root["state"]!!)
    }
}
