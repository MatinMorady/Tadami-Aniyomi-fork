package eu.kanade.presentation.reader.novel

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

// Method names use snake_case identifiers on purpose: backquoted names with spaces are
// rejected by D8 for DEX < 040 (minSdk 26), which broke dexing of the whole androidTest suite.
@RunWith(AndroidJUnit4::class)
class TtsNotificationLifecycleTest {

    @Test
    fun notification_does_not_appear_when_opening_chapters_with_TTS_disabled() {
        // TODO: Implement test
    }

    @Test
    fun notification_does_not_appear_when_TTS_is_enabled_but_not_actively_playing() {
        // TODO: Implement test
    }

    @Test
    fun notification_appears_when_TTS_is_enabled_and_playing() {
        // TODO: Implement test
    }

    @Test
    fun notification_remains_when_TTS_is_disabled_while_paused() {
        // TODO: Implement test
    }

    @Test
    fun notification_disappears_when_explicitly_stopped_while_paused() {
        // TODO: Implement test
    }

    @Test
    fun notification_does_not_reappear_when_swiped_away_and_TTS_disabled() {
        // TODO: Implement test
    }
}
