package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.collections.shouldContainAll
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase
import java.io.File

/**
 * Smoke-tests the hidden-content migrations: 152 creates reels_hidden (composite PK),
 * 153 adds the manager-screen `label` column. An upgrade device (no reels_hidden) must
 * come up with the full current schema.
 */
class ReelsHiddenMigrationTest {

    @Test
    fun `152 and 153 create reels_hidden with pk and label`() {
        val dbFile = File.createTempFile("reels-hidden-mig", ".db").apply { delete() }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Migrations 150+ touch tables this reels-only fixture does not create: the animes
        // stub satisfies 150 (same rule as the watch-history fixture).
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE animes(id INTEGER NOT NULL PRIMARY KEY)",
            parameters = 0,
        )

        // Upgrade from 151 to current runs 152 (create) + 153 (label).
        AnimeDatabase.Schema.migrate(driver, oldVersion = 151L, newVersion = AnimeDatabase.Schema.version)

        columnNames(driver) shouldContainAll listOf("source_id", "kind", "hidden_value", "label", "hidden_at")
        indexNames(driver) shouldContainAll listOf("sqlite_autoindex_reels_hidden_1")

        driver.close()
        dbFile.delete()
    }

    private fun columnNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info(reels_hidden)",
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
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reels_hidden'",
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
