package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

/**
 * Smoke-tests migration 154 (albums collection): an upgrade device without reels_albums /
 * reels_album_watched must come up with both tables (composite PKs) in the current schema.
 */
class ReelsAlbumsMigrationTest {

    @Test
    fun `154 creates reels_albums and reels_album_watched with composite pks`() {
        val dbFile = File.createTempFile("reels-albums-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Migrations 150+ touch tables this reels-only fixture does not create: the animes
        // stub satisfies 150 (same rule as the hidden/watch-history fixtures).
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE animes(id INTEGER NOT NULL PRIMARY KEY)",
            parameters = 0,
        )

        // Upgrade from 152 to current runs 153 (hidden label) + 154 (album tables): the migrate
        // range includes oldVersion, so the fixture must start where reels_hidden exists after 152.
        AnimeDatabase.Schema.migrate(driver, oldVersion = 152L, newVersion = AnimeDatabase.Schema.version)

        columnNames(driver, "reels_albums") shouldContainAll
            listOf("source_id", "album_id", "name", "cover_url", "added_at")
        columnNames(driver, "reels_album_watched") shouldContainAll
            listOf("source_id", "video_id", "marked_at")
        indexNames(driver, "reels_albums") shouldContainAll listOf("sqlite_autoindex_reels_albums_1")
        indexNames(driver, "reels_album_watched") shouldContainAll
            listOf("sqlite_autoindex_reels_album_watched_1")

        driver.close()
        dbFile.delete()
    }

    private fun columnNames(driver: JdbcSqliteDriver, table: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
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

    private fun indexNames(driver: JdbcSqliteDriver, table: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table'",
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
