package ws.reslib.util

import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import ws.reslib.preset.ApplyReport
import ws.reslib.preset.ShaderResult
import ws.reslib.preset.ShaderStatus
import ws.reslib.preset.SodiumStatus

object Notify {
    private val logger = LoggerFactory.getLogger("resourcepackslib")

    fun actionBar(text: Text) {
        val player = MinecraftClient.getInstance().player
        if (player == null) {
            logger.info(text.string)
        } else {
            player.sendMessage(text, true)
        }
    }

    fun chat(text: Text) {
        val player = MinecraftClient.getInstance().player
        if (player == null) {
            logger.info(text.string)
        } else {
            player.sendMessage(text, false)
        }
    }

    fun shader(result: ShaderResult): Text = when (result.status) {
        ShaderStatus.APPLIED -> Text.literal(result.pack.orEmpty())
        ShaderStatus.DISABLED -> Text.translatable("message.resourcepackslib.shader.disabled")
        ShaderStatus.IRIS_MISSING -> Text.translatable("message.resourcepackslib.shader.iris_missing")
        ShaderStatus.PACK_MISSING -> Text.translatable("message.resourcepackslib.shader.pack_missing", result.pack.orEmpty())
        ShaderStatus.NEEDS_RESTART -> Text.translatable("message.resourcepackslib.shader.restart")
        ShaderStatus.FAILED -> Text.translatable("message.resourcepackslib.shader.failed")
    }

    fun applied(name: String, report: ApplyReport): MutableText {
        val text = Text.translatable(
            "message.resourcepackslib.applied",
            name,
            report.enabledPacks,
            shader(report.shader),
            report.shaderOptions,
        )
        if (report.missingPacks.isNotEmpty()) {
            text.append(Text.translatable("message.resourcepackslib.missing", report.missingPacks.size))
        }
        when (report.sodium) {
            SodiumStatus.WRITTEN -> text.append(Text.translatable("message.resourcepackslib.sodium.written"))
            SodiumStatus.FAILED -> text.append(Text.translatable("message.resourcepackslib.sodium.failed"))
            else -> Unit
        }
        return text
    }

    fun error(throwable: Throwable?): Text {
        val key = when {
            throwable is NoSuchElementException -> "message.resourcepackslib.error.not_found"
            throwable?.message == "empty name" -> "message.resourcepackslib.error.empty_name"
            throwable?.message == "name too long" -> "message.resourcepackslib.error.too_long"
            throwable?.message == "preset limit reached" -> "message.resourcepackslib.error.limit"
            throwable?.message == "duplicate name" -> "message.resourcepackslib.error.duplicate"
            else -> "message.resourcepackslib.error.generic"
        }
        return Text.translatable(key)
    }
}
