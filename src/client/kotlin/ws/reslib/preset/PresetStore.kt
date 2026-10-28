package ws.reslib.preset

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object PresetStore {
    const val FORMAT_VERSION = 3

    private val logger = LoggerFactory.getLogger("resourcepackslib")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val directory: Path = FabricLoader.getInstance().configDir.resolve("resourcepackslib")
    val file: Path = directory.resolve("presets.json")

    fun load(): PresetFile {
        if (!Files.isRegularFile(file)) {
            return PresetFile()
        }
        return try {
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            val result = PresetFile()
            result.version = root.get("version")?.takeIf { !it.isJsonNull }?.asInt ?: FORMAT_VERSION
            result.lastAppliedId = root.get("last_applied_id")?.takeIf { !it.isJsonNull }?.asString
            val array = root.getAsJsonArray("presets")
            if (array != null) {
                for (element in array) {
                    val record = parseRecord(element)
                    if (record != null) {
                        result.presets.add(record)
                    }
                }
            }
            result
        } catch (e: Exception) {
            logger.warn("Failed to read {}, backing it up and starting fresh", file, e)
            backupBrokenFile()
            PresetFile()
        }
    }

    fun save(data: PresetFile) {
        Files.createDirectories(directory)
        val root = JsonObject()
        root.addProperty("version", FORMAT_VERSION)
        if (data.lastAppliedId == null) {
            root.add("last_applied_id", JsonNull.INSTANCE)
        } else {
            root.addProperty("last_applied_id", data.lastAppliedId)
        }
        val array = JsonArray()
        data.presets.forEach { array.add(writeRecord(it)) }
        root.add("presets", array)

        val temp = directory.resolve("presets.json.tmp")
        Files.writeString(temp, gson.toJson(root))
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parseRecord(element: com.google.gson.JsonElement): PresetRecord? {
        if (!element.isJsonObject) {
            return null
        }
        val obj = element.asJsonObject
        val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
        if (name.isEmpty()) {
            return null
        }
        val record = PresetRecord()
        record.id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString ?: record.id
        record.name = name
        record.shaderPack = obj.get("shader_pack")?.takeIf { !it.isJsonNull }?.asString
        record.shaderOptions = readMap(obj.get("shader_options")?.takeIf { it.isJsonObject }?.asJsonObject)
        record.sodiumOptions = obj.get("sodium_options")?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy()
        record.resourcePacks = readList(obj.getAsJsonArray("resource_packs"))
        record.incompatibleResourcePacks = readList(obj.getAsJsonArray("incompatible_resource_packs"))
        record.createdAt = obj.get("created_at")?.takeIf { !it.isJsonNull }?.asLong ?: System.currentTimeMillis()
        record.updatedAt = obj.get("updated_at")?.takeIf { !it.isJsonNull }?.asLong ?: record.createdAt
        return record
    }

    private fun readList(array: JsonArray?): MutableList<String> {
        val list = mutableListOf<String>()
        array?.forEach { element ->
            if (!element.isJsonNull) {
                list.add(element.asString)
            }
        }
        return list
    }

    private fun readMap(obj: JsonObject?): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        obj?.entrySet()?.forEach { (key, value) ->
            if (!value.isJsonNull) {
                map[key] = value.asString
            }
        }
        return map
    }

    private fun writeRecord(record: PresetRecord): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", record.id)
        obj.addProperty("name", record.name)
        if (record.shaderPack == null) {
            obj.add("shader_pack", JsonNull.INSTANCE)
        } else {
            obj.addProperty("shader_pack", record.shaderPack)
        }
        obj.add("shader_options", JsonObject().apply {
            record.shaderOptions.forEach { (key, value) -> addProperty(key, value) }
        })
        if (record.sodiumOptions == null) {
            obj.add("sodium_options", JsonNull.INSTANCE)
        } else {
            obj.add("sodium_options", record.sodiumOptions)
        }
        obj.add("resource_packs", JsonArray().apply { record.resourcePacks.forEach { add(it) } })
        obj.add("incompatible_resource_packs", JsonArray().apply { record.incompatibleResourcePacks.forEach { add(it) } })
        obj.addProperty("created_at", record.createdAt)
        obj.addProperty("updated_at", record.updatedAt)
        return obj
    }

    private fun backupBrokenFile() {
        try {
            val backup = directory.resolve("presets.broken-${System.currentTimeMillis()}.json")
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING)
            logger.warn("Broken preset file moved to {}", backup)
        } catch (e: Exception) {
            logger.warn("Failed to back up broken preset file", e)
        }
    }
}
