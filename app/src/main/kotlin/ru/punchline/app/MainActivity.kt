package ru.punchline.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.punchline.app.backup.BackupScreen
import ru.punchline.app.backup.BackupViewModel
import ru.punchline.app.inbox.InboxScreen
import ru.punchline.app.inbox.InboxViewModel

private const val ROUTE_INBOX = "inbox"
private const val ROUTE_BACKUP = "backup"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PunchlineApp).container

        // Засев справочника упражнений идёт в фоне: он не должен задерживать
        // первый экран, а правки пользователя всё равно не затираются.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { container.seedExercises() }

        setContent {
            PunchlineTheme {
                val navController = rememberNavController()
                val factory = container.viewModelFactory()

                NavHost(navController = navController, startDestination = ROUTE_INBOX) {
                    composable(ROUTE_INBOX) {
                        val vm: InboxViewModel = viewModel(factory = factory)
                        InboxScreen(vm, onOpenBackup = { navController.navigate(ROUTE_BACKUP) })
                    }
                    composable(ROUTE_BACKUP) {
                        val vm: BackupViewModel = viewModel(factory = factory)
                        BackupScreen(vm)
                    }
                }
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

/** Фабрика вместо DI-фреймворка: зависимостей мало, и все они видны здесь. */
fun AppContainer.viewModelFactory(): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(InboxViewModel::class.java) ->
                InboxViewModel(bits) as T
            modelClass.isAssignableFrom(BackupViewModel::class.java) ->
                BackupViewModel(context, vault, sink) as T
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
