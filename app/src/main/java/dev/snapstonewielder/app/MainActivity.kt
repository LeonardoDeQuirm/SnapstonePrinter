package dev.snapstonewielder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import dev.snapstonewielder.app.data.api.RetrofitClient
import dev.snapstonewielder.app.data.repository.CardRepository
import dev.snapstonewielder.app.ui.ProxyGeneratorScreen
import dev.snapstonewielder.app.ui.ProxyGeneratorViewModel
import dev.snapstonewielder.app.ui.navigation.Navigator
import dev.snapstonewielder.app.ui.navigation.ProxyGeneratorRoute
import dev.snapstonewielder.app.ui.navigation.rememberNavigationState
import dev.snapstonewielder.app.ui.navigation.toEntries
import dev.snapstonewielder.app.ui.theme.SnapstonePrinterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapstonePrinterTheme {
                val navigationState = rememberNavigationState(
                    startRoute = ProxyGeneratorRoute,
                    topLevelRoutes = setOf(ProxyGeneratorRoute)
                )
                val navigator = remember { Navigator(navigationState) }

                val entryProvider = entryProvider<NavKey> {
                    entry<ProxyGeneratorRoute> {
                        val viewModel: ProxyGeneratorViewModel = viewModel<ProxyGeneratorViewModel>(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    // AndroidViewModel: the ViewModel holds the process-scoped
                                    // Application, never an Activity Context.
                                    return ProxyGeneratorViewModel(
                                        application,
                                        CardRepository(RetrofitClient.scryfallApiService)
                                    ) as T
                                }
                            }
                        )
                        ProxyGeneratorScreen(viewModel = viewModel)
                    }
                }

                NavDisplay(
                    entries = navigationState.toEntries(entryProvider),
                    onBack = { navigator.goBack() }
                )
            }
        }
    }
}
