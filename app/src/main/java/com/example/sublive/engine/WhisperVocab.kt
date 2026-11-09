package com.example.sublive.engine

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * High-performance full vocabulary decoder for Whisper models (50,258 tokens).
 * Loads decompressed tokens on-demand from assets/whisper_vocab.gz.
 */
object WhisperVocab {
    private const val TAG = "WhisperVocab"
    private var tokenList: Array<String>? = null
    @Volatile
    private var isLoaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (isLoaded) return
        try {
            val assetName = if (context.assets.list("")?.contains("filters_vocab_multilingual.bin") == true) {
                "filters_vocab_multilingual.bin"
            } else {
                null
            }

            if (assetName != null) {
                context.assets.open(assetName).use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

                    // Header: magic (4 bytes), n_mel (4 bytes), n_fft (4 bytes)
                    val magic = buf.int
                    val nMel = buf.int
                    val nFft = buf.int

                    // Skip mel filter weights: nMel * nFft * 4
                    val filterBytes = nMel * nFft * 4
                    buf.position(buf.position() + filterBytes)

                    val numTokens = buf.int
                    val tokens = ArrayList<String>(numTokens)

                    for (i in 0 until numTokens) {
                        val slen = buf.int
                        val strBytes = ByteArray(slen)
                        buf.get(strBytes)
                        val rawWord = String(strBytes, Charsets.UTF_8)
                        val cleaned = rawWord.replace('Ġ', ' ')
                        tokens.add(cleaned)
                    }

                    tokenList = tokens.toTypedArray()
                    isLoaded = true
                    Log.i(TAG, "Loaded ${tokenList?.size} Whisper vocabulary tokens from $assetName")
                    return
                }
            }

            // Fallback to whisper_vocab.gz if binary not available
            val tokens = ArrayList<String>(50260)
            context.assets.open("whisper_vocab.gz").use { rawIn ->
                GZIPInputStream(rawIn).use { gzIn ->
                    BufferedReader(InputStreamReader(gzIn, Charsets.UTF_8)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val cleaned = line.replace('Ġ', ' ')
                            tokens.add(cleaned)
                            line = reader.readLine()
                        }
                    }
                }
            }
            tokenList = tokens.toTypedArray()
            isLoaded = true
            Log.d(TAG, "Loaded ${tokenList?.size} Whisper vocabulary tokens from fallback asset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vocabulary: ${e.message}", e)
        }
    }

    fun lookup(tokenId: Int): String? {
        val list = tokenList ?: return null
        return if (tokenId in 0 until list.size) list[tokenId] else null
    }

    val size: Int get() = tokenList?.size ?: 0
}
