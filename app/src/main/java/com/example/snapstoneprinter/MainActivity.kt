package com.example.snapstoneprinter

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
import com.example.snapstoneprinter.data.api.RetrofitClient
import com.example.snapstoneprinter.data.repository.CardRepository
import com.example.snapstoneprinter.ui.ProxyGeneratorScreen
import com.example.snapstoneprinter.ui.ProxyGeneratorViewModel
import com.example.snapstoneprinter.ui.navigation.Navigator
import com.example.snapstoneprinter.ui.navigation.ProxyGeneratorRoute
import com.example.snapstoneprinter.ui.navigation.rememberNavigationState
import com.example.snapstoneprinter.ui.navigation.toEntries
import com.example.snapstoneprinter.ui.theme.SnapstonePrinterTheme

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
