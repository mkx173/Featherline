package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HrtPillUsageTest {
    @Test
    fun customHrtPillsWithLeadingIcons_applyEndPaddingToTrailingText() {
        val projectRoot = projectRoot()
        val sourceRoot = File(projectRoot, "app/src/main/java/com/mkx/hrttracker")
        val offenders = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file -> customHrtPillBlocks(file).asSequence() }
            .filter { block -> block.hasLeadingIconBeforeText() }
            .filterNot { block -> block.trailingTextHasEndPadding() }
            .map { block -> "${block.file.relativeTo(projectRoot)}:${block.lineNumber}" }
            .toList()

        assertTrue(
            "Custom HrtPill blocks with leading icons must add 2.dp end padding to the trailing Text:\n" +
                offenders.joinToString(separator = "\n"),
            offenders.isEmpty(),
        )
    }

    private fun customHrtPillBlocks(file: File): List<HrtPillBlock> {
        val source = file.readText()
        val blocks = mutableListOf<HrtPillBlock>()
        var searchIndex = 0
        while (true) {
            val callIndex = source.indexOf("HrtPill(", startIndex = searchIndex)
            if (callIndex == -1) break

            val argumentStart = callIndex + "HrtPill".length
            val argumentEnd = findMatching(source, argumentStart, open = '(', close = ')')
            if (argumentEnd == -1) {
                searchIndex = callIndex + 1
                continue
            }

            val contentStart = source.indexOfNextNonWhitespace(argumentEnd + 1)
            if (contentStart != -1 && source[contentStart] == '{') {
                val contentEnd = findMatching(source, contentStart, open = '{', close = '}')
                if (contentEnd != -1) {
                    blocks += HrtPillBlock(
                        file = file,
                        lineNumber = source.lineNumberAt(callIndex),
                        content = source.substring(contentStart + 1, contentEnd),
                    )
                    searchIndex = contentEnd + 1
                    continue
                }
            }

            searchIndex = argumentEnd + 1
        }
        return blocks
    }

    private fun HrtPillBlock.hasLeadingIconBeforeText(): Boolean {
        val firstTextIndex = content.indexOf("Text(")
        if (firstTextIndex == -1) return false

        val leadingIconIndex = listOf(
            "Icon(",
            "MedicationApplicationIcon(",
            "HistoryStatusIndicator(",
        ).map { marker -> content.indexOf(marker) }
            .filter { index -> index != -1 }
            .minOrNull()

        return leadingIconIndex != null && leadingIconIndex < firstTextIndex
    }

    private fun HrtPillBlock.trailingTextHasEndPadding(): Boolean {
        val textStart = content.lastIndexOf("Text(")
        if (textStart == -1) return false

        val textArgumentsStart = textStart + "Text".length
        val textEnd = findMatching(content, textArgumentsStart, open = '(', close = ')')
        return textEnd != -1 &&
            content.substring(textStart, textEnd).contains("padding(end = 2.dp)")
    }

    private fun findMatching(
        source: String,
        openIndex: Int,
        open: Char,
        close: Char,
    ): Int {
        var depth = 0
        var index = openIndex
        var inLineComment = false
        var inBlockComment = false
        var inString = false
        var inTripleString = false
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            val nextTwo = source.getOrNull(index + 2)

            when {
                inLineComment -> if (char == '\n') inLineComment = false
                inBlockComment -> if (char == '*' && next == '/') {
                    inBlockComment = false
                    index += 1
                }
                inTripleString -> if (char == '"' && next == '"' && nextTwo == '"') {
                    inTripleString = false
                    index += 2
                }
                inString -> when {
                    char == '\\' -> index += 1
                    char == '"' -> inString = false
                }
                char == '/' && next == '/' -> {
                    inLineComment = true
                    index += 1
                }
                char == '/' && next == '*' -> {
                    inBlockComment = true
                    index += 1
                }
                char == '"' && next == '"' && nextTwo == '"' -> {
                    inTripleString = true
                    index += 2
                }
                char == '"' -> inString = true
                char == open -> depth += 1
                char == close -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
            index += 1
        }
        return -1
    }

    private fun String.indexOfNextNonWhitespace(startIndex: Int): Int {
        var index = startIndex
        while (index < length && this[index].isWhitespace()) {
            index += 1
        }
        return index.takeIf { it < length } ?: -1
    }

    private fun String.lineNumberAt(index: Int): Int =
        take(index).count { char -> char == '\n' } + 1

    private fun projectRoot(): File {
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { file -> file.parentFile }
            .first { file -> File(file, "settings.gradle.kts").isFile }
    }

    private data class HrtPillBlock(
        val file: File,
        val lineNumber: Int,
        val content: String,
    )
}
