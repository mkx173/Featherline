package com.mkx.hrttracker.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces the layering documented in docs/architecture.md: `model` is pure
 * Kotlin domain logic and must not depend on `data` (repositories, Room,
 * DataStore, backup codecs). This guards the boundary restored when
 * DoseInstructionCalculator and RunwayProjection were moved out of
 * data.repository; see notes/featherline_fix_list.md issue 5. Without this
 * test the violation silently re-appears the next time domain logic reaches
 * for a repository-side helper.
 */
class ModelLayerPurityTest {
    @Test
    fun model_layer_does_not_import_data_layer() {
        val modelDir = File("src/main/java/com/mkx/hrttracker/model")
        require(modelDir.isDirectory) {
            "Expected model source directory at ${modelDir.absolutePath}; unit tests " +
                "must run with the app module as the working directory."
        }

        val offenders = modelDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex().mapNotNull { (index, line) ->
                    if (FORBIDDEN_IMPORT.containsMatchIn(line)) {
                        "${file.relativeTo(modelDir)}:${index + 1}  ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertTrue(
            "model must not import com.mkx.hrttracker.data.*; offending imports:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private companion object {
        private val FORBIDDEN_IMPORT = Regex("""^\s*import\s+com\.mkx\.hrttracker\.data\.""")
    }
}
