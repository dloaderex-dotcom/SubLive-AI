package com.example

import org.junit.Assert.*
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun subtitleEngineChoice_containsSileroSenseVoiceLfm3() {
    val choices = com.example.sublive.overlay.SubtitleEngineChoice.values()
    assertTrue(choices.contains(com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3))
    assertTrue(choices.contains(com.example.sublive.overlay.SubtitleEngineChoice.SENSEVOICE_MLKIT))
    assertTrue(choices.contains(com.example.sublive.overlay.SubtitleEngineChoice.VOSK_MLKIT))
    assertTrue(choices.contains(com.example.sublive.overlay.SubtitleEngineChoice.LFM3_OMNI_LITERT))
    assertTrue(choices.contains(com.example.sublive.overlay.SubtitleEngineChoice.WHISPER_DIRECT))
  }

  @Test
  fun overlaySettings_sileroActiveFlag_evaluatesCorrectly() {
    val settings = com.example.sublive.overlay.OverlaySettings(
      engineChoice = com.example.sublive.overlay.SubtitleEngineChoice.SILERO_SENSEVOICE_LFM3
    )
    assertTrue(settings.isSileroSenseVoiceLfm3Active)
    assertFalse(settings.isSenseVoiceActive)
    assertFalse(settings.isVoskActive)
    assertFalse(settings.isLfm3Active)
    assertTrue(settings.enableTurnTakingWait)
    assertEquals(800L, settings.turnTakingWaitMs)
  }

  @Test
  fun subtitleStabilizer_handlesTurnStatus() {
    val stabilizer = com.example.sublive.engine.SubtitleStabilizer()
    val state = stabilizer.processWindowResult(
      recognizedText = "Hello world",
      translatedText = "Hello world",
      isTranslationActive = true,
      targetLanguage = "English",
      isSpeechDetected = true,
      turnStatus = "⏳ Turn-Taking Wait (400ms)..."
    )
    assertNotNull(state.currentPartial)
    assertEquals("⏳ Turn-Taking Wait (400ms)...", state.currentPartial?.turnStatus)
  }

  @Test
  fun sileroVadUrls_arePopulated() {
    val urls = com.example.sublive.vad.SileroVadManager.VAD_URLS
    assertTrue(urls.isNotEmpty())
    assertTrue(urls.any { it.contains("silero_vad.onnx") || it.contains("model_quantized.onnx") })
  }

  @Test
  fun lfm3TextUrls_arePopulated() {
    val urls = com.example.sublive.lfm.Lfm3TextTranslator.MODEL_URLS
    assertTrue(urls.isNotEmpty())
    assertTrue(urls.any { it.endsWith(".litertlm") })
  }

  @Test
  fun lfm3TextTranslator_translatesCommonPhrases() = kotlinx.coroutines.runBlocking {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val translator = com.example.sublive.lfm.Lfm3TextTranslator(context)

    val helloZh = translator.translate("你好")
    assertTrue("Should translate Chinese greeting, got: $helloZh", helloZh.contains("Hello", ignoreCase = true))

    val thankJa = translator.translate("ありがとう")
    assertTrue("Should translate Japanese thank you, got: $thankJa", thankJa.contains("Thank you", ignoreCase = true))

    val helloKo = translator.translate("안녕하세요")
    assertTrue("Should translate Korean hello, got: $helloKo", helloKo.contains("Hello", ignoreCase = true))

    val helloEs = translator.translate("hola amigos")
    assertTrue("Should translate Spanish greeting, got: $helloEs", helloEs.contains("Hello", ignoreCase = true))
  }

  @Test
  fun overlaySettings_localAgreement_defaultsToTrue() {
    val settings = com.example.sublive.overlay.OverlaySettings()
    assertTrue(settings.enableLocalAgreement)
  }

  @Test
  fun localAgreementPolicy_freezesConfirmedPrefix_appendOnly() {
    val policy = com.example.sublive.engine.LocalAgreementPolicy()

    // Pass 1: First window hypothesis
    val res1 = policy.processHypothesis("We are testing")
    assertEquals("", res1.frozenPrefix)
    assertEquals("We are testing", res1.unconfirmedTail)
    assertEquals("We are testing", res1.fullText)

    // Pass 2: Second window hypothesis agreeing on "We are testing" and introducing "speech"
    val res2 = policy.processHypothesis("We are testing speech")
    assertEquals("We are testing", res2.frozenPrefix)
    assertEquals("speech", res2.unconfirmedTail)
    assertEquals("We are testing speech", res2.fullText)

    // Pass 3: Third window agreeing on "speech" and introducing "recognition"
    val res3 = policy.processHypothesis("We are testing speech recognition")
    assertEquals("We are testing speech", res3.frozenPrefix)
    assertEquals("recognition", res3.unconfirmedTail)
    assertEquals("We are testing speech recognition", res3.fullText)

    // Verify append-only invariant: frozen prefix grew from "" -> "We are testing" -> "We are testing speech"
    assertTrue(res3.frozenPrefix.startsWith(res2.frozenPrefix))
    assertTrue(res2.frozenPrefix.startsWith(res1.frozenPrefix))

    // Pass 4: Punctuation boundary finalizes sentence into append-only committed state
    val res4 = policy.processHypothesis("We are testing speech recognition.")
    assertEquals("We are testing speech recognition.", res4.frozenPrefix)
    assertEquals("", res4.unconfirmedTail)
  }

  @Test
  fun subtitleStabilizer_enforcesStabilityProtocolOnDualStreams() {
    val stabilizer = com.example.sublive.engine.SubtitleStabilizer()

    // Step 1: Incoming audio hypothesis 1
    val s1 = stabilizer.processWindowResult(
      recognizedText = "Good morning everyone",
      translatedText = "Good morning everyone",
      isTranslationActive = true,
      targetLanguage = "English",
      isSpeechDetected = true,
      enableLocalAgreement = true
    )
    assertNotNull(s1.currentPartial)
    val item1 = s1.currentPartial!!
    assertTrue(item1.isStabilityEnforced)
    assertEquals("Good morning everyone", item1.unconfirmedTail)

    // Step 2: Consecutive hypothesis 2 mathematically confirming "Good morning everyone"
    val s2 = stabilizer.processWindowResult(
      recognizedText = "Good morning everyone welcome to",
      translatedText = "Good morning everyone welcome to",
      isTranslationActive = true,
      targetLanguage = "English",
      isSpeechDetected = true,
      enableLocalAgreement = true
    )
    val item2 = s2.currentPartial!!
    assertEquals("Good morning everyone", item2.frozenPrefix)
    assertEquals("welcome to", item2.unconfirmedTail)
    assertEquals("Good morning everyone welcome to", item2.displayText)
  }
}
