package ws.reslib.ui

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.VideoOptionsScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import ws.reslib.compat.IrisBridge
import ws.reslib.compat.SodiumBridge
import ws.reslib.preset.PresetManager
import ws.reslib.preset.PresetRecord
import ws.reslib.util.Notify

class PresetScreen(
    private val parent: Screen? = null,
    private val page: Int = 0,
) : Screen(Text.translatable("screen.resourcepackslib.title")) {
    private companion object {
        const val PANEL_WIDTH = 420
        const val ROWS_PER_PAGE = 6
        const val ROW_HEIGHT = 24
        const val ROWS_TOP = 84
    }

    private lateinit var nameField: TextFieldWidget
    private var visible: List<PresetRecord> = emptyList()
    private var currentPage = 0
    private var pageCount = 1
    private var status: Text? = null

    override fun init() {
        val all = PresetManager.presets()
        pageCount = maxOf(1, (all.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE)
        currentPage = page.coerceIn(0, pageCount - 1)
        visible = all.drop(currentPage * ROWS_PER_PAGE).take(ROWS_PER_PAGE)

        val left = width / 2 - PANEL_WIDTH / 2

        nameField = TextFieldWidget(textRenderer, left, 52, 150, 20, Text.translatable("screen.resourcepackslib.name"))
        nameField.setMaxLength(PresetManager.MAX_NAME_LENGTH)
        nameField.setPlaceholder(Text.translatable("screen.resourcepackslib.name_hint"))
        addDrawableChild(nameField)

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("screen.resourcepackslib.save_current")) { saveCurrent() }
                .dimensions(left + 156, 52, 108, 20)
                .build()
        )

        if (IrisBridge.available) {
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.resourcepackslib.open_shader")) { openShaderScreen() }
                    .dimensions(left + 270, 52, 64, 20)
                    .build()
            )
        }

        addDrawableChild(
            ButtonWidget.builder(Text.translatable("screen.resourcepackslib.open_video")) { openVideoSettings() }
                .dimensions(left + 340, 52, 64, 20)
                .build()
        )

        visible.forEachIndexed { index, record ->
            val y = rowY(index)
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.resourcepackslib.apply")) { applyPreset(record) }
                    .dimensions(left + 258, y, 74, 20)
                    .build()
            )
            addDrawableChild(
                ButtonWidget.builder(Text.translatable("screen.resourcepackslib.delete")) { deletePreset(record) }
                    .dimensions(left + 338, y, 82, 20)
                    .build()
            )
        }

        addDrawableChild(
            ButtonWidget.builder(Text.literal("<")) { goToPage(currentPage - 1) }
                .dimensions(left, height - 34, 32, 20)
                .build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal(">")) { goToPage(currentPage + 1) }
                .dimensions(left + 38, height - 34, 32, 20)
                .build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("gui.done")) { close() }
                .dimensions(left + 338, height - 34, 82, 20)
                .build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)

        val left = width / 2 - PANEL_WIDTH / 2
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, 0xFFFFFF)

        val shader = when {
            !IrisBridge.available -> Text.translatable("screen.resourcepackslib.shader.none").string
            else -> IrisBridge.currentPack() ?: Text.translatable("screen.resourcepackslib.shader.off").string
        }
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("screen.resourcepackslib.current", shader, PresetManager.presets().size),
            left,
            36,
            0xA0A0A0,
        )

        if (visible.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.resourcepackslib.empty"),
                width / 2,
                ROWS_TOP + 6,
                0xA0A0A0,
            )
        }

        val lastApplied = PresetManager.lastAppliedId()
        val noShader = Text.translatable("screen.resourcepackslib.shader.off").string
        visible.forEachIndexed { index, record ->
            val prefix = if (record.id == lastApplied) "★ " else "${currentPage * ROWS_PER_PAGE + index + 1}. "
            val line = buildString {
                append(prefix).append(record.name)
                append("   ").append(record.shaderPack ?: noShader)
                append(" · ").append(record.resourcePacks.size)
                if (record.shaderOptions.isNotEmpty()) {
                    append(" · ").append(record.shaderOptions.size)
                    append(Text.translatable("screen.resourcepackslib.options_suffix").string)
                }
                if (record.sodiumOptions != null) {
                    append(Text.translatable("screen.resourcepackslib.sodium_mark").string)
                }
            }
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(line, 240), left, rowY(index) + 6, 0xFFFFFF)
        }

        val pageLine = Text.translatable("screen.resourcepackslib.page", currentPage + 1, pageCount).string
        context.drawTextWithShadow(textRenderer, pageLine, left + 82, height - 28, 0xA0A0A0)

        status?.let {
            context.drawCenteredTextWithShadow(textRenderer, it, width / 2, height - 52, 0xFFFF80)
        }
    }

    override fun close() {
        client.setScreen(parent)
    }

    private fun rowY(index: Int) = ROWS_TOP + index * ROW_HEIGHT

    private fun goToPage(target: Int) {
        if (target in 0 until pageCount) {
            currentPage = target
            rebuild()
        }
    }

    private fun rebuild() {
        clearAndInit()
    }

    private fun saveCurrent() {
        val result = PresetManager.saveCurrent(nameField.text)
        result.onSuccess { record ->
            val text = Text.translatable("message.resourcepackslib.saved", record.name)
            status = text
            Notify.actionBar(text)
            rebuild()
            nameField.setText("")
        }.onFailure {
            status = Notify.error(it)
        }
    }

    private fun applyPreset(record: PresetRecord) {
        val result = PresetManager.apply(record)
        result.onSuccess { report ->
            val text = Notify.applied(record.name, report)
            status = text
            Notify.actionBar(text)
            rebuild()
        }.onFailure {
            status = Notify.error(it)
        }
    }

    private fun deletePreset(record: PresetRecord) {
        if (PresetManager.deleteById(record.id)) {
            val text = Text.translatable("message.resourcepackslib.deleted", record.name)
            status = text
            Notify.actionBar(text)
            rebuild()
        }
    }

    private fun openShaderScreen() {
        if (!IrisBridge.openShaderScreen(this)) {
            status = Text.translatable("message.resourcepackslib.error.iris_screen")
        }
    }

    private fun openVideoSettings() {
        if (!SodiumBridge.openVideoSettings(this)) {
            client.setScreen(VideoOptionsScreen(this, client, client.options))
        }
    }
}
