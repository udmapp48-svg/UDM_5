package com.example.util

import com.example.data.entity.ScannerLearningEntity
import com.google.mlkit.vision.text.Text
import kotlin.math.abs

data class OcrScanResult(
    val jobNumber: String,
    val hasPlusAnchor: Boolean,
    val confidence: Float,
    val isUnclear: Boolean = false,
    val rawMatchedText: String = "",
    val reason: String = "",
    val learnedPatternApplied: Boolean = false
)

object JobNumberOcrParser {

    /**
     * Standard digit lookalikes to handle carefully:
     * 0 / O
     * 1 / I / l
     * 2 / Z
     * 5 / S
     * 6 / G
     * 8 / B
     * 9 / g
     */
    fun mapCharToDigit(c: Char): Char? {
        return when (c) {
            in '0'..'9' -> c
            'O', 'o', 'Q', 'D', 'Ø', 'ø' -> '0'
            'I', 'l', '|' -> '1'
            'Z', 'z' -> '2'
            'S', 's' -> '5'
            'G' -> '6'
            'B' -> '8'
            'g' -> '9'
            else -> null
        }
    }

    /**
     * Extracts Job Number using "+" marker first, with adaptive learned patterns support.
     */
    fun extractJobNumberResult(
        rawText: String,
        learnedRecords: List<ScannerLearningEntity> = emptyList()
    ): OcrScanResult? {
        if (rawText.isBlank()) return null

        val lines = rawText.lines()

        // 1. PRIMARY PASS: Search for "+" marker across all lines
        for (i in lines.indices) {
            val line = lines[i]
            val plusIndex = line.indexOf('+')
            if (plusIndex >= 0) {
                val afterPlus = line.substring(plusIndex + 1).trim()
                if (afterPlus.isNotBlank()) {
                    val result = parseSequenceAfterPlus(afterPlus, "+ $afterPlus", learnedRecords)
                    if (result != null) return result
                } else if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val result = parseSequenceAfterPlus(nextLine, "+ $nextLine", learnedRecords)
                    if (result != null) return result
                }
            }
        }

        // 2. Multiline/Whitespace Regex search for "+" marker
        val plusRegex = Regex("""\+\s*([0-9A-Za-z\s]+)""")
        val match = plusRegex.find(rawText)
        if (match != null) {
            val captured = match.groupValues[1].trim()
            val result = parseSequenceAfterPlus(captured, match.value, learnedRecords)
            if (result != null) return result
        }

        // 3. Fallback search against learned raw text records (if + symbol had slight OCR smudge)
        if (learnedRecords.isNotEmpty()) {
            for (record in learnedRecords) {
                if (record.rawOcrText.isNotBlank() && rawText.contains(record.rawOcrText, ignoreCase = true)) {
                    return OcrScanResult(
                        jobNumber = record.confirmedJobNumber,
                        hasPlusAnchor = true,
                        confidence = 0.95f,
                        isUnclear = false,
                        rawMatchedText = record.rawOcrText,
                        learnedPatternApplied = true
                    )
                }
            }
        }

        // 4. FALLBACK MODE: Older repair stickers without "+"
        return extractFallbackJobNumber(rawText)
    }

    /**
     * Parses the numeric sequence immediately after "+".
     * Preserves leading zeros exactly (e.g. 00125, 00001, 01005).
     * Rejects unclear characters without silently guessing.
     */
    fun parseSequenceAfterPlus(
        afterPlus: String,
        rawMatch: String,
        learnedRecords: List<ScannerLearningEntity> = emptyList()
    ): OcrScanResult? {
        if (afterPlus.isBlank()) return null

        // 1. Handle spaced digits like "0 0 1 2 5" or "0 0 0 0 1"
        val spaceCleaned = afterPlus.split(Regex("""\s+"""))
        val candidateToken = if (spaceCleaned.size in 4..6 && spaceCleaned.all { it.length == 1 }) {
            spaceCleaned.joinToString("")
        } else {
            // First token before words/faults/prices
            spaceCleaned.firstOrNull()?.trim { it in ":#.-_,/ " } ?: ""
        }

        if (candidateToken.isBlank()) return null

        // Match run of 4 to 6 alphanumeric characters
        val matchRun = Regex("""^[0-9A-Za-z]{4,6}""").find(candidateToken)?.value ?: candidateToken

        // Case A: Pure digits (Highest Confidence) - Preserve leading zeros exactly
        if (matchRun.length in 4..6 && matchRun.all { it.isDigit() }) {
            return OcrScanResult(
                jobNumber = matchRun,
                hasPlusAnchor = true,
                confidence = 1.0f,
                isUnclear = false,
                rawMatchedText = rawMatch
            )
        }

        // Case B: Check against exact learned confirmed examples
        if (learnedRecords.isNotEmpty()) {
            val learnedExact = learnedRecords.firstOrNull {
                it.rawOcrText.equals(matchRun, ignoreCase = true) ||
                it.rawOcrText.equals(rawMatch.trim(), ignoreCase = true) ||
                it.rawOcrText.removePrefix("+").trim().equals(matchRun, ignoreCase = true)
            }
            if (learnedExact != null && learnedExact.confirmedJobNumber.all { it.isDigit() }) {
                return OcrScanResult(
                    jobNumber = learnedExact.confirmedJobNumber,
                    hasPlusAnchor = true,
                    confidence = 0.98f,
                    isUnclear = false,
                    rawMatchedText = rawMatch,
                    learnedPatternApplied = true
                )
            }
        }

        // Case C: Standard Lookalikes with Adaptive Confidence & Learned Reinforcement
        if (matchRun.length in 4..6) {
            val sb = StringBuilder()
            var substitutionCount = 0
            var hasUnrecognizedChar = false
            var learnedBonus = 0f

            for (i in matchRun.indices) {
                val c = matchRun[i]
                if (c.isDigit()) {
                    sb.append(c)
                } else {
                    val mapped = mapCharToDigit(c)
                    if (mapped != null) {
                        sb.append(mapped)
                        substitutionCount++

                        // Check if this specific substitution was reinforced in confirmed records
                        val hasReinforcement = learnedRecords.any {
                            it.characterSubstitutions.contains("$c->$mapped")
                        }
                        if (hasReinforcement) {
                            learnedBonus += 0.05f
                        }
                    } else {
                        hasUnrecognizedChar = true
                        break
                    }
                }
            }

            // Accept substitution if at most 2 standard lookalikes (or reinforced by learned records)
            val maxAllowedSubstitutions = if (learnedRecords.isNotEmpty()) 3 else 2
            if (!hasUnrecognizedChar && substitutionCount in 1..maxAllowedSubstitutions && sb.length in 4..6) {
                val confidence = (0.95f - (substitutionCount * 0.05f) + learnedBonus).coerceIn(0.80f, 0.98f)
                return OcrScanResult(
                    jobNumber = sb.toString(),
                    hasPlusAnchor = true,
                    confidence = confidence,
                    isUnclear = false,
                    rawMatchedText = rawMatch,
                    learnedPatternApplied = learnedBonus > 0
                )
            } else if (hasUnrecognizedChar || substitutionCount > maxAllowedSubstitutions) {
                // Ambiguous or unclear characters detected immediately after "+"
                // Must NOT silently guess
                return OcrScanResult(
                    jobNumber = "",
                    hasPlusAnchor = true,
                    confidence = 0.3f,
                    isUnclear = true,
                    rawMatchedText = rawMatch,
                    reason = "Unclear characters after '+' marker"
                )
            }
        }

        // Unclear / invalid format after "+"
        return OcrScanResult(
            jobNumber = "",
            hasPlusAnchor = true,
            confidence = 0.2f,
            isUnclear = true,
            rawMatchedText = rawMatch,
            reason = "Job number not clearly detected."
        )
    }

    /**
     * Fallback mode for legacy tags without "+".
     * Extracts 4-to-5 digit Job Number while strictly ignoring phone numbers, dates, prices, IMEIs.
     */
    fun extractFallbackJobNumber(rawText: String): OcrScanResult? {
        val lines = rawText.lines()
        for (line in lines) {
            val cleaned = line.replace(Regex("""(?i)\b(?:job\s*(?:no|num)?|no|job)[\s:#.-]*"""), " ").trim()

            // 1. Tokens
            val tokens = cleaned.split(Regex("""\s+"""))
            for (token in tokens) {
                val trimmedToken = token.trim { it in ":#.-_ " }
                if (isUnrelatedNumber(trimmedToken)) continue

                val validFour = parseExactDigits(trimmedToken, 4)
                if (validFour != null) {
                    return OcrScanResult(
                        jobNumber = validFour,
                        hasPlusAnchor = false,
                        confidence = 0.85f,
                        rawMatchedText = token
                    )
                }

                val validFive = parseExactDigits(trimmedToken, 5)
                if (validFive != null) {
                    return OcrScanResult(
                        jobNumber = validFive,
                        hasPlusAnchor = false,
                        confidence = 0.85f,
                        rawMatchedText = token
                    )
                }
            }

            // 2. Spaced characters, e.g. "0 0 0 6"
            val charsWithoutSeparators = cleaned.filter { !it.isWhitespace() && it !in ":#.-_" }
            if (charsWithoutSeparators.length in 4..5 && !isUnrelatedNumber(charsWithoutSeparators)) {
                val exact = parseExactDigits(charsWithoutSeparators, charsWithoutSeparators.length)
                if (exact != null) {
                    return OcrScanResult(
                        jobNumber = exact,
                        hasPlusAnchor = false,
                        confidence = 0.80f,
                        rawMatchedText = cleaned
                    )
                }
            }

            // 3. Contiguous 4-digit windows
            if (cleaned.length >= 4) {
                for (i in 0..cleaned.length - 4) {
                    val window = cleaned.substring(i, i + 4)
                    val four = parseExactDigits(window, 4)
                    if (four != null && !isUnrelatedNumber(four)) {
                        return OcrScanResult(
                            jobNumber = four,
                            hasPlusAnchor = false,
                            confidence = 0.75f,
                            rawMatchedText = window
                        )
                    }
                }
            }
        }
        return null
    }

    private fun isUnrelatedNumber(str: String): Boolean {
        // Ignore 10-digit phone numbers, 15-digit IMEIs, dates, prices
        if (str.length >= 7) return true
        if (str.contains("/") || (str.contains("-") && str.length > 5)) return true
        return false
    }

    fun parseExactDigits(str: String, expectedLength: Int): String? {
        if (str.length != expectedLength) return null
        val sb = StringBuilder()
        for (c in str) {
            val d = mapCharToDigit(c) ?: return null
            sb.append(d)
        }
        return sb.toString()
    }

    /**
     * Extracts Job Number from ML Kit Text hierarchy using spatial "+" anchor and learned records.
     */
    fun extractJobNumberFromVisionTextWithResult(
        visionText: Text,
        learnedRecords: List<ScannerLearningEntity> = emptyList()
    ): OcrScanResult? {
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val angle = line.angle
                if (abs(angle) in 60f..300f) continue

                val elements = line.elements
                if (elements.isNotEmpty()) {
                    val sortedElements = elements.sortedBy { it.boundingBox?.left ?: 0 }

                    for (idx in sortedElements.indices) {
                        val elem = sortedElements[idx]
                        if (elem.text.contains("+")) {
                            val localCandidate = parseSequenceAfterPlus(
                                elem.text.substringAfter("+").trim(),
                                elem.text,
                                learnedRecords
                            )
                            if (localCandidate != null) return localCandidate

                            if (idx + 1 < sortedElements.size) {
                                val nextElem = sortedElements[idx + 1]
                                val candidate = parseSequenceAfterPlus(
                                    nextElem.text.trim(),
                                    "+ ${nextElem.text}",
                                    learnedRecords
                                )
                                if (candidate != null) return candidate
                            }
                        }
                    }

                    if (line.text.contains("+")) {
                        val lineCandidate = parseSequenceAfterPlus(
                            line.text.substringAfter("+").trim(),
                            line.text,
                            learnedRecords
                        )
                        if (lineCandidate != null) return lineCandidate
                    }
                }
            }
        }

        return extractJobNumberResult(visionText.text, learnedRecords)
    }

    fun extractJobNumberFromText(
        rawText: String,
        learnedRecords: List<ScannerLearningEntity> = emptyList()
    ): String? {
        val result = extractJobNumberResult(rawText, learnedRecords) ?: return null
        return if (!result.isUnclear && result.jobNumber.isNotBlank()) result.jobNumber else null
    }

    fun extractJobNumberFromVisionText(
        visionText: Text,
        learnedRecords: List<ScannerLearningEntity> = emptyList()
    ): String? {
        val result = extractJobNumberFromVisionTextWithResult(visionText, learnedRecords) ?: return null
        return if (!result.isUnclear && result.jobNumber.isNotBlank()) result.jobNumber else null
    }

    /**
     * Multi-Pass comparator: selects best result based on '+' anchor, clarity, confidence, and learned reinforcement.
     */
    fun selectBestResult(pass1: OcrScanResult?, pass2: OcrScanResult?): OcrScanResult? {
        if (pass1 == null && pass2 == null) return null
        if (pass1 == null) return pass2
        if (pass2 == null) return pass1

        // Prefer result with "+" anchor
        if (pass1.hasPlusAnchor && !pass2.hasPlusAnchor) return pass1
        if (pass2.hasPlusAnchor && !pass1.hasPlusAnchor) return pass2

        // Prefer clear result over unclear
        if (!pass1.isUnclear && pass2.isUnclear) return pass1
        if (!pass2.isUnclear && pass1.isUnclear) return pass2

        // Prefer learned pattern applied
        if (pass1.learnedPatternApplied && !pass2.learnedPatternApplied) return pass1
        if (pass2.learnedPatternApplied && !pass1.learnedPatternApplied) return pass2

        // Prefer higher confidence
        return if (pass2.confidence > pass1.confidence) pass2 else pass1
    }
}
