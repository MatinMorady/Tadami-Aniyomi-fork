package eu.kanade.tachiyomi.ui.more

import eu.kanade.domain.ui.UiPreferences
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.junit.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class MoreMenuCustomizeScreenModelTest {

    private val available = availableMoreEntryIds(
        showReelsEntry = false,
        latticeGridAvailable = false,
        isDebugBuild = false,
    )

    private val defaultVisible = MoreEntryId.entries.filter { it in available }

    /**
     * SharedPreferences-like store: every getter wraps the same map, so a write is visible to later
     * lookups. `InMemoryPreferenceStore` copies values per lookup, which would hide the persistence
     * this screen depends on (same reason as in `TrustMangaExtensionTest`).
     */
    private class MapPreferenceStore : PreferenceStore {
        private val values = mutableMapOf<String, Any?>()

        private inner class MapPreference<T>(
            private val key: String,
            private val default: T,
        ) : Preference<T> {
            @Suppress("UNCHECKED_CAST")
            override fun get(): T = (values[key] as? T) ?: default
            override fun key() = key
            override fun isSet() = values.containsKey(key)
            override fun defaultValue() = default
            override fun set(value: T) {
                values[key] = value
            }
            override fun delete() {
                values.remove(key)
            }
            override fun changes(): Flow<T> = flow { emit(get()) }
            override fun stateIn(scope: CoroutineScope): StateFlow<T> =
                changes().stateIn(scope, SharingStarted.Eagerly, get())
        }

        override fun getString(key: String, defaultValue: String) = MapPreference(key, defaultValue)
        override fun getLong(key: String, defaultValue: Long) = MapPreference(key, defaultValue)
        override fun getInt(key: String, defaultValue: Int) = MapPreference(key, defaultValue)
        override fun getFloat(key: String, defaultValue: Float) = MapPreference(key, defaultValue)
        override fun getBoolean(key: String, defaultValue: Boolean) = MapPreference(key, defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>) = MapPreference(key, defaultValue)
        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ) = MapPreference(key, defaultValue)
        override fun getAll(): Map<String, *> = values
    }

    private fun model(store: MapPreferenceStore = MapPreferenceStore()) =
        MoreMenuCustomizeScreenModel(available, UiPreferences(store))

    @Test
    fun `fresh state lists every available entry in default order`() {
        val state = model().state.value

        state.visible shouldContainExactly defaultVisible
        state.hidden shouldBe emptyList()
    }

    @Test
    fun `hiding an entry moves it out of the visible list`() {
        val model = model()

        model.toggleHidden(MoreEntryId.STATS)

        model.state.value.visible shouldContainExactly defaultVisible.filterNot { it == MoreEntryId.STATS }
        model.state.value.hidden shouldContainExactly listOf(MoreEntryId.STATS)
    }

    @Test
    fun `restoring an entry appends it to the end of the visible list`() {
        val model = model()

        model.toggleHidden(MoreEntryId.SETTINGS)
        model.toggleHidden(MoreEntryId.SETTINGS)

        model.state.value.hidden shouldBe emptyList()
        model.state.value.visible.last() shouldBe MoreEntryId.SETTINGS
    }

    @Test
    fun `the pinned moved tab cannot be hidden`() {
        val model = model()

        model.toggleHidden(MoreEntryId.MOVED_TAB)

        model.state.value.visible.first() shouldBe MoreEntryId.MOVED_TAB
        model.state.value.hidden shouldBe emptyList()
    }

    @Test
    fun `move reorders the visible list`() {
        val model = model()
        val before = model.state.value.visible

        model.move(from = 0, to = 2)

        model.state.value.visible shouldContainExactly listOf(before[1], before[2], before[0]) + before.drop(3)
    }

    @Test
    fun `an out of range move target clamps to the end of the list`() {
        val model = model()

        model.move(from = 0, to = 99)

        model.state.value.visible.last() shouldBe defaultVisible.first()
    }

    @Test
    fun `reset restores the default order and visibility`() {
        val model = model()

        model.toggleHidden(MoreEntryId.STATS)
        model.move(from = 0, to = 3)
        model.reset()

        model.state.value.visible shouldContainExactly defaultVisible
        model.state.value.hidden shouldBe emptyList()
    }

    @Test
    fun `order and hidden set survive a new screen model over the same store`() {
        val store = MapPreferenceStore()
        val first = model(store)

        first.toggleHidden(MoreEntryId.ABOUT)
        first.move(from = 0, to = 2)

        val second = model(store)

        second.state.value.visible shouldBe first.state.value.visible
        second.state.value.hidden shouldContainExactly listOf(MoreEntryId.ABOUT)
    }

    @Test
    fun `unavailable entries stay out of the list even when another entry was hidden earlier`() {
        val store = MapPreferenceStore()
        model(store).toggleHidden(MoreEntryId.STATS)

        val withReels = availableMoreEntryIds(
            showReelsEntry = true,
            latticeGridAvailable = false,
            isDebugBuild = false,
        )
        val reopened = MoreMenuCustomizeScreenModel(withReels, UiPreferences(store))

        reopened.state.value.hidden shouldContainExactly listOf(MoreEntryId.STATS)
        reopened.state.value.visible shouldContainExactly
            MoreEntryId.entries.filter { it in withReels && it != MoreEntryId.STATS }
    }

    @Test
    fun `reels entry appears when the feed source is enabled`() {
        val withReels = availableMoreEntryIds(
            showReelsEntry = true,
            latticeGridAvailable = false,
            isDebugBuild = false,
        )
        val model = MoreMenuCustomizeScreenModel(withReels, UiPreferences(MapPreferenceStore()))

        model.state.value.visible shouldContainExactly MoreEntryId.entries.filter { it in withReels }
        (MoreEntryId.REELS in model.state.value.visible) shouldBe true
    }
}
