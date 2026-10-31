package ws.reslib.preset

import org.slf4j.LoggerFactory
import java.util.UUID

object PresetManager {
    const val MAX_PRESETS = 64
    const val MAX_NAME_LENGTH = 40

    private val logger = LoggerFactory.getLogger("resourcepackslib")
    private val lock = Any()
    private var data = PresetFile()

    fun init() {
        data = PresetStore.load()
        logger.info("Loaded {} preset(s) from {}", data.presets.size, PresetStore.file)
    }

    fun presets(): List<PresetRecord> = synchronized(lock) { data.presets.toList() }

    fun lastAppliedId(): String? = synchronized(lock) { data.lastAppliedId }

    fun find(name: String): PresetRecord? = synchronized(lock) {
        data.presets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun saveCurrent(rawName: String): Result<PresetRecord> = synchronized(lock) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("empty name"))
        }
        if (name.length > MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("name too long"))
        }
        val existing = data.presets.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing == null && data.presets.size >= MAX_PRESETS) {
            return Result.failure(IllegalStateException("preset limit reached"))
        }

        val snapshot = PresetApplier.capture()
        val now = System.currentTimeMillis()
        val record = existing ?: PresetRecord(id = UUID.randomUUID().toString(), name = name, createdAt = now)
        record.name = name
        record.shaderPack = snapshot.shaderPack
        record.shaderOptions = snapshot.shaderOptions.toMutableMap()
        record.sodiumOptions = snapshot.sodiumOptions?.deepCopy()
        record.resourcePacks = snapshot.resourcePacks.toMutableList()
        record.incompatibleResourcePacks = snapshot.incompatibleResourcePacks.toMutableList()
        record.updatedAt = now
        if (existing == null) {
            data.presets.add(record)
        }
        data.lastAppliedId = record.id
        persist()
        Result.success(record)
    }

    fun apply(record: PresetRecord): Result<ApplyReport> = runCatching {
        val report = PresetApplier.apply(record)
        synchronized(lock) {
            data.lastAppliedId = record.id
            persist()
        }
        report
    }

    fun apply(name: String): Result<ApplyReport> {
        val record = find(name) ?: return Result.failure(NoSuchElementException(name))
        return apply(record)
    }

    fun rename(id: String, rawName: String): Result<Unit> = synchronized(lock) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("empty name"))
        }
        if (name.length > MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("name too long"))
        }
        val record = data.presets.firstOrNull { it.id == id }
            ?: return Result.failure(NoSuchElementException(id))
        if (data.presets.any { it.id != id && it.name.equals(name, ignoreCase = true) }) {
            return Result.failure(IllegalStateException("duplicate name"))
        }
        record.name = name
        record.updatedAt = System.currentTimeMillis()
        persist()
        Result.success(Unit)
    }

    fun deleteById(id: String): Boolean = synchronized(lock) {
        val removed = data.presets.removeIf { it.id == id }
        if (removed) {
            if (data.lastAppliedId == id) {
                data.lastAppliedId = null
            }
            persist()
        }
        removed
    }

    fun delete(name: String): Boolean {
        val record = find(name) ?: return false
        return deleteById(record.id)
    }

    private fun persist() {
        try {
            PresetStore.save(data)
        } catch (e: Exception) {
            logger.error("Failed to save presets to {}", PresetStore.file, e)
        }
    }
}
