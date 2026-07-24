package com.github.x0x0b.codexlauncher.settings.options

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelTest {

    @Test
    fun entries_containsOnlyCurrentModelsAndSpecialChoices() {
        assertEquals(
            listOf(
                Model.DEFAULT,
                Model.GPT_5_6_SOL,
                Model.GPT_5_6_TERRA,
                Model.GPT_5_6_LUNA,
                Model.CUSTOM,
            ),
            Model.entries,
        )
    }

    @Test
    fun cliName_currentModels_matchesOfficialModelIds() {
        assertEquals("gpt-5.6-sol", Model.GPT_5_6_SOL.cliName())
        assertEquals("gpt-5.6-terra", Model.GPT_5_6_TERRA.cliName())
        assertEquals("gpt-5.6-luna", Model.GPT_5_6_LUNA.cliName())
    }

    @Test
    fun converter_removedModel_fallsBackToDefault() {
        assertEquals(Model.DEFAULT, ModelConverter().fromString("GPT_5_5"))
    }
}
