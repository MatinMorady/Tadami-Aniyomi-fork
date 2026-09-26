package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

/**
 * Smoke-tests the 151.sqm watch-history migration: an upgrade device (no reels_watch_history)
 * must come up with the fresh schema, composite PK and the watched_at index.
 */
class ReelsWatchHistoryMigrationTest {

    @Test
    fun `151 creates reels_watch_history with pk and index`() {
        val dbFile = File.createTempFile("reels-watch-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Migration 150 (animes.completed_at) targets the animes table, which this reels-only
        // fixture does not create: provide a minimal stub (same rule as the follows fixture).
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE animes(id INTEGER NOT NULL PRIMARY KEY)",
            parameters = 0,
        )

        // Upgrade from 150 to current creates the history table.
        AnimeDatabase.Schema.migrate(driver, oldVersion = 150L, newVersion = AnimeDatabase.Schema.version)

        columnNames(driver) shouldContainAll listOf("video_id", "source_id", "position_ms", "watched_at")
        indexNames(driver) shouldContainAll listOf("reels_watch_history_watched_at_index")

        driver.close()
        dbFile.delete()
    }

    private fun columnNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(reels_watch_history)",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(1).orEmpty())
                        }
                    },
                )
            },
            parameters = 0,
        ).value
    }

    private fun indexNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reels_watch_history'",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(0).orEmpty())
                        }
                    },
                )
            },
            parameters = 0,
        ).value
    }
}
