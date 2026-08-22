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
import ru.punchline.app.topics.TopicsScreen
import ru.punchline.app.topics.TopicsViewModel
import ru.punchline.app.workshop.BitWorkshopScreen
import ru.punchline.app.workshop.BitWorkshopViewModel
import ru.punchline.app.workshop.MindMapScreen
import ru.punchline.app.workshop.MindMapViewModel
import ru.punchline.model.Id

private const val ROUTE_INBOX = "inbox"
private const val ROUTE_TOPICS = "topics"
private const val ROUTE_BOARD = "board"
private const val ROUTE_BACKUP = "backup"
private const val ARG_ID = "id"
private const val ROUTE_MINDMAP = "mindmap"
private const val ROUTE_WORKSHOP = "workshop"

private enum class Tab(val route: String, val labelRes: Int) {
    INBOX(ROUTE_INBOX, R.string.tab_inbox),
    TOPICS(ROUTE_TOPICS, R.string.tab_topics),
    BOARD(ROUTE_BOARD, R.string.tab_board),
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
            startDestination = ROUTE_INBOX,
            modifier = Modifier.padding(insets),
        ) {
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
                modelClass.isAssignableFrom(InboxViewModel::class.java) ->
                    InboxViewModel(bits) as T

                modelClass.isAssignableFrom(TopicsViewModel::class.java) ->
                    TopicsViewModel(topics) as T

                modelClass.isAssignableFrom(BoardViewModel::class.java) ->
                    BoardViewModel(bits) as T

                modelClass.isAssignableFrom(BackupViewModel::class.java) ->
                    BackupViewModel(context, vault, sink) as T

                modelClass.isAssignableFrom(MindMapViewModel::class.java) ->
                    MindMapViewModel(workshop, requireNotNull(argument)) as T

                modelClass.isAssignableFrom(BitWorkshopViewModel::class.java) ->
                    BitWorkshopViewModel(bits, requireNotNull(argument)) as T

                else -> error("Unknown ViewModel: ${modelClass.name}")
            }
    }
