package ws.reslib.preset

import com.google.gson.JsonObject
import java.util.UUID

data class PresetRecord(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var shaderPack: String? = null,
    var shaderOptions: MutableMap<String, String> = mutableMapOf(),
    var sodiumOptions: JsonObject? = null,
    var resourcePacks: MutableList<String> = mutableListOf(),
    var incompatibleResourcePacks: MutableList<String> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
)

data class PresetFile(
    var version: Int = PresetStore.FORMAT_VERSION,
    var lastAppliedId: String? = null,
    var presets: MutableList<PresetRecord> = mutableListOf(),
)

data class Snapshot(
    val shaderPack: String?,
    val shaderOptions: Map<String, String>,
    val sodiumOptions: JsonObject?,
    val resourcePacks: List<String>,
    val incompatibleResourcePacks: List<String>,
)

data class ShaderResult(
    val status: ShaderStatus,
    val pack: String?,
    val detail: String? = null,
)

enum class ShaderStatus {
    APPLIED,
    DISABLED,
    IRIS_MISSING,
    PACK_MISSING,
    NEEDS_RESTART,
    FAILED,
}

enum class SodiumStatus {
    UNAVAILABLE,
    UNCHANGED,
    WRITTEN,
    FAILED,
}

data class ApplyReport(
    val enabledPacks: Int,
    val missingPacks: List<String>,
    val shader: ShaderResult,
    val shaderOptions: Int,
    val sodium: SodiumStatus,
)
