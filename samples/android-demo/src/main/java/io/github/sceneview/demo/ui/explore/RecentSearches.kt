package io.github.sceneview.demo.ui.explore

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * SharedPreferences-backed list of the last 5 search queries surfaced under
 * "Recent searches" on the Explore tab. Mirrors the iOS [`RecentSearches`]
 * helper — same 5-item cap so QA can compare side-by-side.
 *
 * Implementation note: we use `SharedPreferences` rather than DataStore here
 * to keep the dependency footprint of the demo app tight. The data is a
 * single ordered list of short strings — DataStore would add ~1.5 MB to the
 * APK for no real gain.
 *
 * Storage format: queries are joined with the Unit-Separator control
 * character (U+001F) — a non-printable byte that users can't type, so a
 * search query can never poison the persisted blob.
 */
private const val PREFS_NAME = "io.github.sceneview.demo.explore"
private const val KEY_RECENT_SEARCHES = "recentSearches"
private const val MAX_ITEMS = 5
private const val SEPARATOR = '\u001F'

class RecentSearchesStore internal constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> {
        val joined = prefs.getString(KEY_RECENT_SEARCHES, null).orEmpty()
        if (joined.isEmpty()) return emptyList()
        return joined.split(SEPARATOR).filter { it.isNotBlank() }.take(MAX_ITEMS)
    }

    fun save(items: List<String>) {
        prefs.edit().putString(KEY_RECENT_SEARCHES, items.joinToString(SEPARATOR.toString())).apply()
    }
}

/** Compose-friendly handle around [RecentSearchesStore]. */
class RecentSearchesState internal constructor(
    initial: List<String>,
    private val store: RecentSearchesStore,
) {
    private val _items = mutableStateListOf<String>().also { it.addAll(initial) }
    val items: List<String> get() = _items

    fun push(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _items.removeAll { it.equals(trimmed, ignoreCase = true) }
        _items.add(0, trimmed)
        while (_items.size > MAX_ITEMS) _items.removeAt(_items.lastIndex)
        store.save(_items.toList())
    }

    fun remove(query: String) {
        _items.removeAll { it == query }
        store.save(_items.toList())
    }

    fun clear() {
        _items.clear()
        store.save(emptyList())
    }
}

@Composable
fun rememberRecentSearches(): RecentSearchesState {
    val context = LocalContext.current
    val store = remember(context) { RecentSearchesStore(context) }
    return remember(store) { RecentSearchesState(store.load(), store) }
}
