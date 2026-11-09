package com.example.sublive.engine

/**
 * Result of Local Agreement evaluation for a single stream (ASR or Translation).
 */
data class LocalAgreementResult(
    val frozenPrefix: String = "",
    val unconfirmedTail: String = "",
    val fullText: String = "",
    val committedCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val isStabilityEnforced: Boolean = true
)

/**
 * LocalAgreementPolicy enforces a mathematical Stability Protocol on streaming text:
 *
 * 1. Append-Only Invariant: Words in the committed prefix are permanently frozen and NEVER mutate,
 *    flicker, or re-order.
 * 2. Mathematical Confirmation: When words across consecutive inference windows match (Local Agreement N>=2),
 *    they are immediately locked into the committed prefix.
 * 3. Dynamic Tail: Only trailing words at the very end of the sentence remain mutable while
 *    speech continues.
 * 4. Punctuation / Turn Finalization: Completing a sentence or turn-taking pause finalizes any
 *    remaining tail into the committed prefix, ready for the next sentence.
 */
class LocalAgreementPolicy(
    private val agreementStreakRequired: Int = 2
) {
    // Strictly append-only list of permanently locked tokens
    private val committedTokens = mutableListOf<String>()

    // Unconfirmed candidate tokens from the previous evaluation window
    private var prevHypothesisTokens = listOf<String>()

    @Synchronized
    fun processHypothesis(incomingText: String): LocalAgreementResult {
        val clean = cleanText(incomingText)
        if (clean.isBlank()) {
            val frozen = detokenize(committedTokens)
            val tail = detokenize(prevHypothesisTokens)
            return LocalAgreementResult(
                frozenPrefix = frozen,
                unconfirmedTail = tail,
                fullText = combineTokens(frozen, tail),
                committedCount = committedTokens.size,
                unconfirmedCount = prevHypothesisTokens.size
            )
        }

        val incomingTokens = tokenize(clean)
        if (incomingTokens.isEmpty()) {
            val frozen = detokenize(committedTokens)
            val tail = detokenize(prevHypothesisTokens)
            return LocalAgreementResult(
                frozenPrefix = frozen,
                unconfirmedTail = tail,
                fullText = combineTokens(frozen, tail),
                committedCount = committedTokens.size,
                unconfirmedCount = prevHypothesisTokens.size
            )
        }

        // Determine candidate unconfirmed tokens after accounting for already committed prefix
        val candidateTokens = extractCandidateTokens(incomingTokens)

        if (prevHypothesisTokens.isEmpty()) {
            // First hypothesis window in this clause: candidate tokens enter unconfirmed tail
            prevHypothesisTokens = candidateTokens
        } else {
            // Compute Longest Common Prefix (LCP) between previous tail and new candidate tokens
            val commonPrefixLength = computeLongestCommonPrefix(prevHypothesisTokens, candidateTokens)

            if (commonPrefixLength > 0) {
                // Tokens in the common prefix have reached mathematical agreement (N >= 2)!
                val tokensToFreeze = candidateTokens.take(commonPrefixLength)
                committedTokens.addAll(tokensToFreeze)

                // The remainder forms the new dynamic unconfirmed tail
                val remainingTail = candidateTokens.drop(commonPrefixLength)
                prevHypothesisTokens = remainingTail
            } else {
                // Candidate tokens updated
                prevHypothesisTokens = candidateTokens
            }
        }

        // Check if current tail ends with sentence boundary punctuation (., ?, !)
        val lastToken = prevHypothesisTokens.lastOrNull() ?: committedTokens.lastOrNull()
        if (lastToken != null && isSentenceBoundary(lastToken)) {
            finalizeSentence()
        }

        val frozen = detokenize(committedTokens)
        val tail = detokenize(prevHypothesisTokens)
        val full = combineTokens(frozen, tail)

        return LocalAgreementResult(
            frozenPrefix = frozen,
            unconfirmedTail = tail,
            fullText = full,
            committedCount = committedTokens.size,
            unconfirmedCount = prevHypothesisTokens.size
        )
    }

    /**
     * Extracts tokens from the incoming hypothesis that extend beyond the committed prefix.
     */
    private fun extractCandidateTokens(incoming: List<String>): List<String> {
        if (committedTokens.isEmpty()) return incoming

        // Case A: Incoming starts exactly with committedTokens
        if (incoming.size >= committedTokens.size &&
            incoming.subList(0, committedTokens.size) == committedTokens) {
            return incoming.drop(committedTokens.size)
        }

        // Case B: Partial prefix match of committed tokens
        val maxPrefix = minOf(committedTokens.size, incoming.size)
        for (len in maxPrefix downTo 1) {
            val commSuffix = committedTokens.takeLast(len)
            val incPrefix = incoming.take(len)
            if (commSuffix == incPrefix) {
                return incoming.drop(len)
            }
        }

        // Case C: Check if incoming contains any sub-sequence of committed tokens
        val matchIndex = indexOfSubList(incoming, committedTokens.takeLast(minOf(3, committedTokens.size)))
        if (matchIndex >= 0) {
            val after = matchIndex + minOf(3, committedTokens.size)
            if (after < incoming.size) {
                return incoming.drop(after)
            }
        }

        return incoming
    }

    private fun indexOfSubList(source: List<String>, target: List<String>): Int {
        if (target.isEmpty() || target.size > source.size) return -1
        for (i in 0..(source.size - target.size)) {
            if (source.subList(i, i + target.size) == target) {
                return i
            }
        }
        return -1
    }

    private fun computeLongestCommonPrefix(a: List<String>, b: List<String>): Int {
        val limit = minOf(a.size, b.size)
        var i = 0
        while (i < limit && a[i].equals(b[i], ignoreCase = true)) {
            i++
        }
        return i
    }

    @Synchronized
    fun finalizeSentence(): String {
        if (prevHypothesisTokens.isNotEmpty()) {
            committedTokens.addAll(prevHypothesisTokens)
            prevHypothesisTokens = emptyList()
        }
        return detokenize(committedTokens)
    }

    @Synchronized
    fun getFrozenPrefix(): String = detokenize(committedTokens)

    @Synchronized
    fun getUnconfirmedTail(): String = detokenize(prevHypothesisTokens)

    @Synchronized
    fun reset() {
        committedTokens.clear()
        prevHypothesisTokens = emptyList()
    }

    companion object {
        fun tokenize(text: String): List<String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            return if (trimmed.contains(" ")) {
                trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
            } else if (trimmed.any { it in '\u4e00'..'\u9fff' || it in '\u3040'..'\u30ff' }) {
                trimmed.map { it.toString() }
            } else {
                listOf(trimmed)
            }
        }

        fun detokenize(tokens: List<String>): String {
            if (tokens.isEmpty()) return ""
            val hasCjk = tokens.firstOrNull()?.any { it in '\u4e00'..'\u9fff' || it in '\u3040'..'\u30ff' } == true
            return if (hasCjk) {
                tokens.joinToString("")
            } else {
                tokens.joinToString(" ")
            }
        }

        fun combineTokens(prefix: String, tail: String): String {
            return when {
                prefix.isBlank() -> tail
                tail.isBlank() -> prefix
                else -> {
                    val hasCjk = prefix.any { it in '\u4e00'..'\u9fff' || it in '\u3040'..'\u30ff' }
                    if (hasCjk) "$prefix$tail" else "$prefix $tail"
                }
            }
        }

        fun cleanText(text: String): String {
            return text.trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .trim()
        }

        fun isSentenceBoundary(token: String): Boolean {
            val trimmed = token.trim()
            return trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!") ||
                    trimmed.endsWith("。") || trimmed.endsWith("？") || trimmed.endsWith("！")
        }
    }
}

/**
 * Subtitle state representation supporting dual recognized and translated text
 * with Local Agreement frozen prefix and unconfirmed tail metadata.
 */
data class SubtitleItem(
    val id: Long = System.currentTimeMillis(),
    val timestampMs: Long = System.currentTimeMillis(),
    val text: String = "",
    val originalText: String = "",
    val translatedText: String = "",
    val isTranslationActive: Boolean = false,
    val targetLanguage: String = "English",
    val originalKorean: String = originalText,
    val translatedEnglish: String = translatedText,
    val isFinalized: Boolean = false,
    val turnStatus: String = "",
    val frozenPrefix: String = "",
    val unconfirmedTail: String = "",
    val frozenOriginalPrefix: String = "",
    val unconfirmedOriginalTail: String = "",
    val isStabilityEnforced: Boolean = true
) {
    val displayText: String
        get() = if (isTranslationActive && translatedText.isNotBlank()) {
            translatedText
        } else {
            text.ifBlank { originalText.ifBlank { translatedEnglish.ifBlank { originalKorean } } }
        }

    val displayFrozenPrefix: String
        get() = if (isTranslationActive && translatedText.isNotBlank()) {
            frozenPrefix
        } else {
            frozenOriginalPrefix.ifBlank { frozenPrefix }
        }

    val displayUnconfirmedTail: String
        get() = if (isTranslationActive && translatedText.isNotBlank()) {
            unconfirmedTail
        } else {
            unconfirmedOriginalTail.ifBlank { unconfirmedTail }
        }
}

enum class TargetLanguageMode {
    TRANSCRIBE,
    KOREAN_TO_ENGLISH,
    NATIVE_KOREAN
}

data class StabilizedSubtitleState(
    val currentPartial: SubtitleItem? = null,
    val finalizedHistory: List<SubtitleItem> = emptyList(),
    val isProcessing: Boolean = false,
    val targetLanguageMode: TargetLanguageMode = TargetLanguageMode.TRANSCRIBE
)

/**
 * SubtitleStabilizer reconciles overlapping inference outputs, manages
 * partial/stabilized/finalized states, and suppresses duplicate flickering
 * via the Local Agreement Stability Protocol.
 */
class SubtitleStabilizer(
    private val maxHistoryCount: Int = 20,
    private val silenceFinalizeThresholdMs: Long = 1400L
) {
    private var nextId: Long = 1
    private val history = mutableListOf<SubtitleItem>()
    private var activeItem: SubtitleItem? = null
    private var lastSpokenTimeMs: Long = 0

    // Local Agreement Policy engines for ASR and MT streams
    private val recognizedPolicy = LocalAgreementPolicy()
    private val translatedPolicy = LocalAgreementPolicy()

    @Synchronized
    fun processWindowResult(
        recognizedText: String,
        translatedText: String = "",
        isTranslationActive: Boolean = false,
        targetLanguage: String = "English",
        isSpeechDetected: Boolean = true,
        turnStatus: String = "",
        enableLocalAgreement: Boolean = true
    ): StabilizedSubtitleState {
        val now = System.currentTimeMillis()
        val cleanRecognized = LocalAgreementPolicy.cleanText(recognizedText)
        val cleanTranslated = LocalAgreementPolicy.cleanText(translatedText)

        val effectiveInput = if (isTranslationActive && cleanTranslated.isNotBlank()) cleanTranslated else cleanRecognized

        if (!isSpeechDetected || effectiveInput.isBlank()) {
            // Finalize active item after silence threshold
            if (activeItem != null && (now - lastSpokenTimeMs > silenceFinalizeThresholdMs)) {
                finalizeActiveItem()
            }
            return StabilizedSubtitleState(
                currentPartial = activeItem,
                finalizedHistory = history.toList(),
                isProcessing = false
            )
        }

        lastSpokenTimeMs = now

        if (enableLocalAgreement) {
            val recResult = recognizedPolicy.processHypothesis(cleanRecognized)
            val transResult = if (cleanTranslated.isNotBlank()) {
                translatedPolicy.processHypothesis(cleanTranslated)
            } else {
                LocalAgreementResult(
                    frozenPrefix = "",
                    unconfirmedTail = "",
                    fullText = "",
                    committedCount = 0,
                    unconfirmedCount = 0
                )
            }

            val effectiveFrozen = if (isTranslationActive && transResult.fullText.isNotBlank()) {
                transResult.frozenPrefix
            } else {
                recResult.frozenPrefix
            }

            val effectiveTail = if (isTranslationActive && transResult.fullText.isNotBlank()) {
                transResult.unconfirmedTail
            } else {
                recResult.unconfirmedTail
            }

            val effectiveFull = if (isTranslationActive && transResult.fullText.isNotBlank()) {
                transResult.fullText
            } else {
                recResult.fullText
            }

            activeItem = SubtitleItem(
                id = activeItem?.id ?: nextId++,
                timestampMs = now,
                text = effectiveFull,
                originalText = recResult.fullText,
                translatedText = transResult.fullText,
                isTranslationActive = isTranslationActive,
                targetLanguage = targetLanguage,
                originalKorean = recResult.fullText,
                translatedEnglish = transResult.fullText,
                isFinalized = false,
                turnStatus = turnStatus,
                frozenPrefix = effectiveFrozen,
                unconfirmedTail = effectiveTail,
                frozenOriginalPrefix = recResult.frozenPrefix,
                unconfirmedOriginalTail = recResult.unconfirmedTail,
                isStabilityEnforced = true
            )

            if (LocalAgreementPolicy.isSentenceBoundary(effectiveFull)) {
                finalizeActiveItem()
            }
        } else {
            // Standard fallback reconciliation without mathematical freezing
            val current = activeItem
            if (current == null) {
                activeItem = SubtitleItem(
                    id = nextId++,
                    timestampMs = now,
                    text = effectiveInput,
                    originalText = cleanRecognized,
                    translatedText = cleanTranslated,
                    isTranslationActive = isTranslationActive,
                    targetLanguage = targetLanguage,
                    originalKorean = cleanRecognized,
                    translatedEnglish = cleanTranslated,
                    isFinalized = false,
                    turnStatus = turnStatus,
                    frozenPrefix = "",
                    unconfirmedTail = effectiveInput,
                    isStabilityEnforced = false
                )
            } else {
                val mergedRecognized = reconcileText(current.originalText, cleanRecognized)
                val mergedTranslated = if (cleanTranslated.isNotBlank()) {
                    reconcileText(current.translatedText, cleanTranslated)
                } else {
                    current.translatedText
                }
                val mergedEffective = if (isTranslationActive && mergedTranslated.isNotBlank()) mergedTranslated else mergedRecognized

                activeItem = current.copy(
                    text = mergedEffective,
                    originalText = mergedRecognized,
                    translatedText = mergedTranslated,
                    isTranslationActive = isTranslationActive,
                    targetLanguage = targetLanguage,
                    originalKorean = mergedRecognized,
                    translatedEnglish = mergedTranslated,
                    turnStatus = turnStatus,
                    frozenPrefix = "",
                    unconfirmedTail = mergedEffective,
                    isStabilityEnforced = false
                )

                if (hasSentenceEnding(mergedEffective)) {
                    finalizeActiveItem()
                }
            }
        }

        return StabilizedSubtitleState(
            currentPartial = activeItem,
            finalizedHistory = history.toList(),
            isProcessing = false
        )
    }

    // Overload for rawText backwards compatibility
    fun processWindowResult(
        rawText: String,
        isSpeechDetected: Boolean
    ): StabilizedSubtitleState = processWindowResult(
        recognizedText = rawText,
        translatedText = "",
        isTranslationActive = false,
        targetLanguage = "English",
        isSpeechDetected = isSpeechDetected
    )

    // Overload for two-parameter compatibility
    fun processWindowResult(
        rawKorean: String,
        rawEnglish: String,
        isSpeechDetected: Boolean
    ): StabilizedSubtitleState {
        val isTranslating = rawEnglish.isNotBlank() && rawEnglish != rawKorean
        return processWindowResult(
            recognizedText = rawKorean.ifBlank { rawEnglish },
            translatedText = if (isTranslating) rawEnglish else "",
            isTranslationActive = isTranslating,
            targetLanguage = "English",
            isSpeechDetected = isSpeechDetected
        )
    }

    @Synchronized
    fun finalizeActiveItem(): StabilizedSubtitleState {
        val current = activeItem
        if (current != null && current.displayText.isNotBlank()) {
            val finalizedRec = recognizedPolicy.finalizeSentence()
            val finalizedTrans = translatedPolicy.finalizeSentence()

            val finalizedItem = current.copy(
                isFinalized = true,
                frozenPrefix = if (current.isTranslationActive && finalizedTrans.isNotBlank()) finalizedTrans else finalizedRec,
                unconfirmedTail = "",
                text = if (current.isTranslationActive && finalizedTrans.isNotBlank()) finalizedTrans else finalizedRec,
                translatedText = if (current.isTranslationActive && finalizedTrans.isNotBlank()) finalizedTrans else current.translatedText,
                originalText = finalizedRec.ifBlank { current.originalText }
            )

            history.add(finalizedItem)
            if (history.size > maxHistoryCount) {
                history.removeAt(0)
            }
            activeItem = null
            recognizedPolicy.reset()
            translatedPolicy.reset()
        }
        return StabilizedSubtitleState(
            currentPartial = null,
            finalizedHistory = history.toList(),
            isProcessing = false
        )
    }

    @Synchronized
    fun clear() {
        activeItem = null
        history.clear()
        recognizedPolicy.reset()
        translatedPolicy.reset()
    }

    private fun reconcileText(prev: String, incoming: String): String {
        if (prev.isBlank()) return incoming
        if (incoming.isBlank()) return prev

        if (incoming.startsWith(prev, ignoreCase = true)) return incoming
        if (prev.startsWith(incoming, ignoreCase = true)) return prev

        val prevWords = prev.split(" ").filter { it.isNotBlank() }
        val incomingWords = incoming.split(" ").filter { it.isNotBlank() }

        val maxOverlap = minOf(prevWords.size, incomingWords.size)
        for (overlapLen in maxOverlap downTo 1) {
            val prevSuffix = prevWords.takeLast(overlapLen).joinToString(" ")
            val incomingPrefix = incomingWords.take(overlapLen).joinToString(" ")
            if (prevSuffix.equals(incomingPrefix, ignoreCase = true)) {
                val nonOverlapping = incomingWords.drop(overlapLen).joinToString(" ")
                return if (nonOverlapping.isBlank()) prev else "$prev $nonOverlapping"
            }
        }

        return if (incomingWords.size > 2 && prevWords.size > 2) {
            incoming
        } else {
            "$prev $incoming"
        }
    }

    private fun hasSentenceEnding(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")
    }
}
