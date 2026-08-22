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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.punchline.app.inbox.InboxScreen
import ru.punchline.app.inbox.InboxViewModel

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
                val viewModel: InboxViewModel = viewModel(factory = container.viewModelFactory())
                InboxScreen(viewModel)
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
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
