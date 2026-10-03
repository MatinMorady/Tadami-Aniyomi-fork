package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubKenBurnsProfileTest {

    private val titles = (1..40).map { "title $it" }

    @Test
    fun `profile is deterministic per title`() {
        titles.forEach { title ->
            stageKenBurnsProfile(title) shouldBe stageKenBurnsProfile(title)
        }
    }

    @Test
    fun `scale amplitude stays in sane ken-burns bounds`() {
        titles.forEach { title ->
            val amplitude = stageKenBurnsProfile(title).scaleAmplitude
            // Границы с ulp-запасом на float-арифметику (0.05f + 0.07f > 0.12f).
            (amplitude >= 0.049f && amplitude <= 0.121f) shouldBe true
        }
    }

    @Test
    fun `drift never exceeds half the zoom amplitude — no empty edges`() {
        titles.forEach { title ->
            val profile = stageKenBurnsProfile(title)
            (kotlin.math.abs(profile.driftXFraction) <= profile.scaleAmplitude / 2f) shouldBe true
            (kotlin.math.abs(profile.driftYFraction) <= profile.scaleAmplitude / 2f) shouldBe true
        }
    }

    @Test
    fun `profiles differ between posters with both drift directions present`() {
        val profiles = titles.map { stageKenBurnsProfile(it) }
        (profiles.toSet().size > titles.size / 2) shouldBe true
        (profiles.any { it.driftXFraction > 0f } && profiles.any { it.driftXFraction < 0f }) shouldBe true
        (profiles.any { it.driftYFraction > 0f } && profiles.any { it.driftYFraction < 0f }) shouldBe true
    }
}
