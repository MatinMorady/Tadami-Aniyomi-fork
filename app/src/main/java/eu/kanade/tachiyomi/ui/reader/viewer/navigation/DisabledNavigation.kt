package eu.kanade.tachiyomi.ui.reader.viewer.navigation

import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation

/**
 * No tap zones configured: every tap resolves to the menu (ViewerNavigation.getAction default).
 * +---+---+---+
 * | M | M | M |   M: Menu
 * +---+---+---+
 * | M | M | M |
 * +---+---+---+
 * | M | M | M |
 * +---+---+---+
*/
class DisabledNavigation : ViewerNavigation() {

    override var regionList: List<Region> = emptyList()
}
