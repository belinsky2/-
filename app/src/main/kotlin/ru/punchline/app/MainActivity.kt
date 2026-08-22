package ru.punchline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.punchline.app.backup.BackupScreen
import ru.punchline.app.backup.BackupViewModel
import ru.punchline.app.board.BoardScreen
import ru.punchline.app.board.BoardViewModel
import ru.punchline.app.inbox.InboxScreen
import ru.punchline.app.inbox.InboxViewModel
import ru.punchline.app.morning.MorningScreen
import ru.punchline.app.morning.MorningViewModel
import ru.punchline.app.practice.PracticeScreen
import ru.punchline.app.practice.PracticeViewModel
import ru.punchline.app.review.GigReviewScreen
import ru.punchline.app.review.GigReviewViewModel
import ru.punchline.app.setlist.SetListDetailScreen
import ru.punchline.app.setlist.SetListDetailViewModel
import ru.punchline.app.setlist.SetListsScreen
import ru.punchline.app.setlist.SetListsViewModel
import ru.punchline.app.stage.StageScreen
import ru.punchline.app.stage.StageViewModel
import ru.punchline.app.today.TodayScreen
import ru.punchline.app.today.TodayViewModel
import ru.punchline.app.topics.TopicsScreen
import ru.punchline.app.topics.TopicsViewModel
import ru.punchline.app.workshop.BitWorkshopScreen
import ru.punchline.app.workshop.BitWorkshopViewModel
import ru.punchline.app.workshop.MindMapScreen
import ru.punchline.app.workshop.MindMapViewModel
import ru.punchline.model.Id
import ru.punchline.model.MarkdownLabels

private const val ROUTE_TODAY = "today"
private const val ROUTE_INBOX = "inbox"
private const val ROUTE_TOPICS = "topics"
private const val ROUTE_BOARD = "board"
private const val ROUTE_PRACTICE = "practice"
private const val ROUTE_MORNING = "morning"
private const val ROUTE_SETLISTS = "setlists"
private const val ROUTE_SETLIST_DETAIL = "setlist"
private const val ROUTE_STAGE = "stage"
private const val ROUTE_REVIEW = "review"
private const val ARG_SET_ID = "setId"
private const val ROUTE_BACKUP = "backup"
private const val ARG_ID = "id"
private const val ROUTE_MINDMAP = "mindmap"
private const val ROUTE_WORKSHOP = "workshop"

private enum class Tab(val route: String, val labelRes: Int) {
    TODAY(ROUTE_TODAY, R.string.tab_today),
    INBOX(ROUTE_INBOX, R.string.tab_inbox),
    TOPICS(ROUTE_TOPICS, R.string.tab_topics),
    BOARD(ROUTE_BOARD, R.string.tab_board),
    PRACTICE(ROUTE_PRACTICE, R.string.tab_practice),
    SETLISTS(ROUTE_SETLISTS, R.string.tab_setlists),
    MORNING(ROUTE_MORNING, R.string.tab_morning),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PunchlineApp).container

        // Засев справочника упражнений идёт в фоне: он не должен задерживать
        // первый экран, а правки пользователя всё равно не затираются.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.seedExercises() }

        setContent { PunchlineTheme { PunchlineApp(container) } }
    }
}

@Composable
private fun PunchlineApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination

    Scaffold(
        bottomBar = {
            // Панель прячется на вложенных экранах: в мастерской и в дереве
            // ассоциаций она только отвлекает от одной текущей задачи.
            if (Tab.entries.any { tab -> currentRoute?.hierarchy?.any { it.route == tab.route } == true }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {},
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_TODAY,
            modifier = Modifier.padding(insets),
        ) {
            composable(ROUTE_TODAY) {
                TodayScreen(
                    viewModel = viewModel(factory = container.factory()),
                    onOpenInbox = { navController.navigate(ROUTE_INBOX) },
                    onOpenBackup = { navController.navigate(ROUTE_BACKUP) },
                )
            }
            composable(ROUTE_INBOX) {
                InboxScreen(
                    viewModel = viewModel(factory = container.factory()),
                    onOpenBackup = { navController.navigate(ROUTE_BACKUP) },
                )
            }
            composable(ROUTE_TOPICS) {
                TopicsScreen(
                    viewModel = viewModel(factory = container.factory()),
                    onOpenMindMap = { navController.navigate("$ROUTE_MINDMAP/${it.value}") },
                )
            }
            composable(ROUTE_BOARD) {
                BoardScreen(
                    viewModel = viewModel(factory = container.factory()),
                    onOpenBit = { navController.navigate("$ROUTE_WORKSHOP/${it.value}") },
                )
            }
            composable(ROUTE_PRACTICE) {
                PracticeScreen(viewModel = viewModel(factory = container.factory()))
            }
            composable(ROUTE_MORNING) {
                MorningScreen(viewModel = viewModel(factory = container.factory()))
            }
            composable(ROUTE_SETLISTS) {
                SetListsScreen(
                    viewModel = viewModel(factory = container.factory()),
                    onOpen = { navController.navigate("$ROUTE_SETLIST_DETAIL/${it.value}") },
                    onStage = { navController.navigate("$ROUTE_STAGE/${it.value}") },
                )
            }
            composable("$ROUTE_SETLIST_DETAIL/{$ARG_ID}") { entry ->
                val id = Id(entry.arguments?.getString(ARG_ID).orEmpty())
                SetListDetailScreen(viewModel = viewModel(factory = container.factory(id)))
            }
            composable("$ROUTE_STAGE/{$ARG_ID}") { entry ->
                val id = Id(entry.arguments?.getString(ARG_ID).orEmpty())
                StageScreen(
                    viewModel = viewModel(factory = container.factory(id)),
                    onExit = { navController.popBackStack() },
                    onReview = { gig ->
                        navController.navigate("$ROUTE_REVIEW/$gig/${id.value}") {
                            popUpTo(ROUTE_SETLISTS)
                        }
                    },
                )
            }
            composable("$ROUTE_REVIEW/{$ARG_ID}/{$ARG_SET_ID}") { entry ->
                val gigId = Id(entry.arguments?.getString(ARG_ID).orEmpty())
                val setId = Id(entry.arguments?.getString(ARG_SET_ID).orEmpty())
                GigReviewScreen(
                    viewModel = viewModel(factory = container.reviewFactory(gigId, setId)),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(ROUTE_BACKUP) {
                BackupScreen(viewModel = viewModel(factory = container.factory()))
            }
            composable("$ROUTE_MINDMAP/{$ARG_ID}") { entry ->
                val topicId = Id(entry.arguments?.getString(ARG_ID).orEmpty())
                MindMapScreen(viewModel = viewModel(factory = container.factory(topicId)))
            }
            composable("$ROUTE_WORKSHOP/{$ARG_ID}") { entry ->
                val bitId = Id(entry.arguments?.getString(ARG_ID).orEmpty())
                BitWorkshopScreen(viewModel = viewModel(factory = container.factory(bitId)))
            }
        }
    }
}

/**
 * Тёмная схема по умолчанию: приложение открывают в тёмном зале перед выходом,
 * и светлый экран там слепит и автора, и первый ряд.
 */
@Composable
fun PunchlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

/**
 * Фабрика вместо DI-фреймворка: зависимостей мало, и все они видны здесь.
 * [argument] передаётся экранам, которые открываются для конкретной записи.
 */
fun AppContainer.factory(argument: Id? = null): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            when {
                modelClass.isAssignableFrom(TodayViewModel::class.java) ->
                    TodayViewModel(stats, bits, DEFAULT_GOAL_MINUTES) as T

                modelClass.isAssignableFrom(InboxViewModel::class.java) ->
                    InboxViewModel(bits) as T

                modelClass.isAssignableFrom(TopicsViewModel::class.java) ->
                    TopicsViewModel(topics) as T

                modelClass.isAssignableFrom(BoardViewModel::class.java) ->
                    BoardViewModel(bits) as T

                modelClass.isAssignableFrom(PracticeViewModel::class.java) ->
                    PracticeViewModel(practice) as T

                modelClass.isAssignableFrom(MorningViewModel::class.java) ->
                    MorningViewModel(practice, streaks, bits) as T

                modelClass.isAssignableFrom(SetListsViewModel::class.java) ->
                    SetListsViewModel(setLists) as T

                modelClass.isAssignableFrom(SetListDetailViewModel::class.java) ->
                    SetListDetailViewModel(setLists, bits, gigs, requireNotNull(argument)) as T

                modelClass.isAssignableFrom(StageViewModel::class.java) ->
                    StageViewModel(setLists, bits, gigs, streaks, requireNotNull(argument)) as T

                modelClass.isAssignableFrom(BackupViewModel::class.java) ->
                    BackupViewModel(context, vault, sink, bits, topics, markdownLabels()) as T

                modelClass.isAssignableFrom(MindMapViewModel::class.java) ->
                    MindMapViewModel(workshop, requireNotNull(argument)) as T

                modelClass.isAssignableFrom(BitWorkshopViewModel::class.java) ->
                    BitWorkshopViewModel(bits, requireNotNull(argument)) as T

                else -> error("Unknown ViewModel: ${modelClass.name}")
            }
    }

/** У разбора выступления два аргумента, поэтому для него отдельная фабрика. */
fun AppContainer.reviewFactory(gigId: Id, setListId: Id?): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            GigReviewViewModel(gigs, setLists, bits, gigId, setListId) as T
    }

/**
 * Цель по хронометражу до появления экрана настроек. Пять минут — стандартная
 * длина открытого микрофона и первая осмысленная веха у Картер.
 */
private const val DEFAULT_GOAL_MINUTES = 5

/**
 * Подписи для выгрузки в Markdown. Берутся из ресурсов, а не зашиты в слое
 * данных: правило «никакого видимого текста в коде» действует и здесь.
 */
private fun AppContainer.markdownLabels(): MarkdownLabels {
    val res = context.resources
    return MarkdownLabels(
        documentTitle = res.getString(R.string.markdown_title),
        myAct = res.getString(R.string.markdown_my_act),
        inProgress = res.getString(R.string.markdown_in_progress),
        archive = res.getString(R.string.markdown_archive),
        premise = res.getString(R.string.markdown_premise),
        punch = res.getString(R.string.markdown_punch),
        actOut = res.getString(R.string.markdown_act_out),
        tags = res.getString(R.string.markdown_tags),
    )
}
