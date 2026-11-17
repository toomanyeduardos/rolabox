package com.eduardoflores.rolabox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ActivityScenario
import com.eduardoflores.rolabox.common.designsystem.component.WHEEL_TEST_TAG
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import com.eduardoflores.rolabox.device.ui.api.DeviceKey
import com.eduardoflores.rolabox.device.ui.api.DeviceUiEntries
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The podcasts as the device assembles them, with the wheel as the only input (ADR-018, ADR-031): the main menu's
 * Podcasts row opens Podcasts, an owner opens its shows, and a show its Follow and episodes, each coming back on its
 * row with MENU. The podcasts are [PodcastSamples], in [FakePodcastsRepository], which each test fills before the
 * device starts.
 */
@HiltAndroidTest
class PodcastsNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<HiltTestActivity>? = null

    @Inject lateinit var deviceUiEntries: DeviceUiEntries

    @Inject lateinit var podcasts: FakePodcastsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        HiltTestActivity.content = {
            RolaboxTheme {
                val backStack = remember { mutableStateListOf<NavKey>(DeviceKey) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        deviceUiEntries.appStackEntries(this, onOpenFullScreen = {}, onBack = {})
                    },
                )
            }
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
    }

    @Test
    fun fromTheMainMenuDownToAShow_followTogglesInPlace_andMenuComesBackOnEveryRow() {
        podcasts.fillWithSamples()
        launch()

        // The main menu, then Podcasts: Search first, then the owners.
        assertHighlighted("Music")
        wheel().turn(1)
        assertHighlighted("Podcasts")
        wheel().pressCenter()
        assertHighlighted("Search")
        assertRow("Unknown owner")

        // NY Times, then its shows: The Daily is the second.
        val owner = PodcastSamples.nyTimes
        wheel().turn(PodcastSamples.owners.indexOf(owner) + 1)
        assertHighlighted(owner.name)
        wheel().pressCenter()
        assertHighlighted(PodcastSamples.hardFork.title)
        wheel().turn(1)
        assertHighlighted(PodcastSamples.theDaily.title)
        wheel().pressCenter()

        // The show: Follow first, On, then its episodes. Center turns Follow Off in place.
        assertHighlighted("Follow")
        assertRow("On")
        assertRow(PodcastSamples.unplayed.title)
        wheel().pressCenter()
        assertHighlighted("Follow")
        assertRow("Off")
        assertFalse(podcasts.shows.first { it.id == PodcastSamples.theDaily.id }.isFollowed)

        // And back up, one MENU at a time.
        wheel().pressMenu()
        assertHighlighted(PodcastSamples.theDaily.title)
        wheel().pressMenu()
        assertHighlighted(owner.name)
        wheel().pressMenu()
        assertHighlighted("Podcasts")
    }

    @Test
    fun noPodcastsYet_searchIsAloneAndHighlighted_withTheMessage() {
        launch()

        wheel().turn(1)
        wheel().pressCenter()

        assertHighlighted("Search")
        assertRow("No podcasts yet")
        assertRow("Select Search to find a show.")
    }

    @Test
    fun aShowThatDisappearsWhileOpen_saysSo_andMenuGoesBack() {
        podcasts.fillWithSamples()
        launch()
        wheel().turn(1)
        wheel().pressCenter()
        wheel().turn(PodcastSamples.owners.indexOf(PodcastSamples.nyTimes) + 1)
        wheel().pressCenter()
        wheel().turn(1)
        wheel().pressCenter()
        assertHighlighted("Follow")

        podcasts.shows = podcasts.shows - PodcastSamples.theDaily

        assertRow("No longer in your podcasts")
        wheel().pressMenu()
        assertRow(PodcastSamples.hardFork.title)
    }

    private fun wheel() = composeRule.onNodeWithTag(WHEEL_TEST_TAG)

    private fun assertRow(text: String?) {
        val row = checkNotNull(text) { "The samples name everything" }
        composeRule.onNode(hasText(row), useUnmergedTree = true).assertExists()
    }

    private fun assertHighlighted(name: String?) {
        val text = checkNotNull(name) { "The samples name everything" }
        composeRule.onNode(isSelected() and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
    }
}
