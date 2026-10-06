package tachiyomi.data.discovery

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoverySignal
import tachiyomi.domain.discovery.model.DiscoverySignalType

/**
 * Taste Learning Engine: restore из бэкапа уважает старшинство сигналов
 * (слабый бэкапный не затирает локальный сильный) и LRU-cap на медиатип.
 */
class DiscoverySignalsRestoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DiscoveryRepositoryImpl

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val database = Database(
            driver = driver,
            chaptersAdapter = data.Chapters.Adapter(memoAdapter = MemoColumnAdapter),
            historyAdapter = data.History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = data.Mangas.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
        repository = DiscoveryRepositoryImpl(
            AndroidMangaDatabaseHandler(
                db = database,
                driver = driver,
                queryDispatcher = Dispatchers.Default,
                transactionDispatcher = Dispatchers.Default,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    private fun signal(title: String, type: DiscoverySignalType, createdAt: Long = 1_000L) = DiscoverySignal(
        mediaType = DiscoveryMediaType.NOVEL,
        cleanTitle = title.lowercase(),
        title = title,
        signalType = type,
        genres = emptyList(),
        provider = null,
        sourceKey = null,
        createdAt = createdAt,
    )

    @Test
    fun `backup weaker signal does not overwrite local stronger one`() = runTest {
        // Локально: пользователь скрыл тайтл (HIDE — сильнейший).
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.HIDE,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 2_000L,
        )
        // Бэкап с другого устройства несёт LIKE по тому же тайтлу — НЕ должен затереть HIDE.
        repository.restoreSignals(listOf(signal("Overlord", DiscoverySignalType.LIKE)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().signalType shouldBe DiscoverySignalType.HIDE
    }

    @Test
    fun `backup stronger signal overwrites local weaker one`() = runTest {
        repository.recordSignal(
            mediaType = DiscoveryMediaType.NOVEL,
            cleanTitle = "overlord",
            title = "Overlord",
            signalType = DiscoverySignalType.CLICK,
            genres = emptyList(),
            provider = null,
            sourceKey = null,
            timestamp = 1_000L,
        )
        repository.restoreSignals(listOf(signal("Overlord", DiscoverySignalType.HIDE, createdAt = 3_000L)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().signalType shouldBe DiscoverySignalType.HIDE
    }

    @Test
    fun `restore keeps new titles and caps the log`() = runTest {
        repository.restoreSignals(listOf(signal("New One", DiscoverySignalType.LIKE)))
        repository.getSignals(DiscoveryMediaType.NOVEL).single().title shouldBe "New One"
    }
}
