package tachiyomi.data.entries.novel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import datanovel.Novel_history
import datanovel.Novels
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.novel.data.NovelDatabase

/**
 * Regression net for the recurring library-view mapper NPE seen in crash logs (NovelHomeHub and
 * the novel migration source screen both died inside the library view mapper). The view must map
 * rows without chapters/categories, rows carrying the TEXT memo default the ALTER TABLE
 * migrations wrote (Android's getBlob appends a NUL terminator to TEXT), empty and garbage memo
 * values, and multi-category rows - without throwing.
 */
class NovelLibraryViewEdgeRowsTest {

    @Test
    fun `library view maps edge rows without throwing`() {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val database = createDatabase(driver)

        insertNovel(driver, 1, "'{}'") // TEXT default literal, as migrations wrote it
        insertNovel(driver, 2, "X'7B7D00'") // real blob with a trailing NUL
        insertNovel(driver, 3, "''") // empty text
        insertNovel(driver, 4, "'not json'") // garbage payload
        insertNovel(driver, 5, "'{}'") // chapters + history + two categories
        driver.execute(null, "INSERT INTO novels_categories (novel_id, category_id) VALUES (5, 7), (5, 2)", 0)
        driver.execute(
            null,
            "INSERT INTO novel_chapters (_id, novel_id, url, name, read, bookmark, last_page_read," +
                " chapter_number, source_order, date_fetch, date_upload)" +
                " VALUES (1, 5, '/c1', 'c1', 1, 0, 0, 1.0, 0, 0, 0)",
            0,
        )
        driver.execute(null, "INSERT INTO novel_history (chapter_id, last_read, time_read) VALUES (1, 123, 0)", 0)

        val rows = database.novellibraryViewQueries.library(NovelMapper::mapLibraryNovel).executeAsList()

        rows.size shouldBe 5
        rows.first { it.novel.id == 1L }.totalChapters shouldBe 0L
        rows.first { it.novel.id == 1L }.category shouldBe 0L
        rows.first { it.novel.id == 5L }.let {
            // One row per novel even with two categories; lowest category id wins.
            it.category shouldBe 2L
            it.totalChapters shouldBe 1L
            it.readCount shouldBe 1L
            it.lastRead shouldBe 123L
        }
        driver.close()
    }

    private fun insertNovel(driver: JdbcSqliteDriver, id: Long, memoLiteral: String) {
        driver.execute(
            null,
            "INSERT INTO novels (_id, source, url, title, status, notes, favorite, initialized," +
                " viewer, chapter_flags, cover_last_modified, date_added, memo)" +
                " VALUES ($id, 1, '/novel$id', 'Novel $id', 0, '', 1, 0, 0, 0, 0, 0, $memoLiteral)",
            0,
        )
    }

    private fun createDatabase(driver: JdbcSqliteDriver): NovelDatabase {
        NovelDatabase.Schema.create(driver)
        return NovelDatabase(
            driver = driver,
            novel_historyAdapter = Novel_history.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            novel_chaptersAdapter = datanovel.Novel_chapters.Adapter(memoAdapter = MemoColumnAdapter),
            novelsAdapter = Novels.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
            ),
        )
    }
}
