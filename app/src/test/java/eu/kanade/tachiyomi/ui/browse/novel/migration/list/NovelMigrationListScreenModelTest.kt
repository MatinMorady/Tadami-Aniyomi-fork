package eu.kanade.tachiyomi.ui.browse.novel.migration.list

import android.app.Application
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

/**
 * Regression (crash log 0.62 build 209, 2026-09-19, OPPO CPH2591): the constructor resolved
 * its `context` default as `Context` from Injekt (added by 22834f00d to localize the
 * migration search progress label), but the production DI graph binds only `Application`
 * (`addSingleton(app)` in AppModule) -- Injekt matches exact type keys and does not walk
 * supertypes, so opening the migration list screen threw
 * "InjektionException: No registered instance or factory for type class android.content.Context"
 * on the main thread. Voyager restored the screen after each process death, turning it into a
 * crash loop.
 *
 * This test deliberately registers ONLY what production registers: Application. Do not add a
 * Context binding here -- its absence is the contract under test.
 */
class NovelMigrationListScreenModelTest {

    private val created = mutableListOf<NovelMigrationListScreenModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runCatching { Injekt.get<Application>() }
            .getOrElse {
                Injekt.addSingleton(fullType<Application>(), mockk(relaxed = true))
            }
    }

    @AfterEach
    fun tearDown() {
        created.forEach { runCatching { it.onDispose() } }
        created.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `constructor resolves its context default from the Application binding`() {
        val screenModel = NovelMigrationListScreenModel(
            novelIds = emptyList(),
            sourceIds = emptyList(),
            extraSearchQuery = null,
            sourcePreferences = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            getNovel = mockk(relaxed = true),
            novelChapterRepository = mockk(relaxed = true),
            migrateNovel = mockk(relaxed = true),
            preferenceStore = mockk(relaxed = true),
        )
        created += screenModel

        // Empty ids: the init search pipeline is a no-op, items stay empty.
        screenModel.state.value.items.isEmpty() shouldBe true
    }
}
