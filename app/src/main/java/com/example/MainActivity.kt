package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.data.repository.ErpDashboardRepository
import com.example.data.repository.ErpOperationsRepository
import com.example.data.sync.ErpSettingsStore
import com.example.data.sync.SyncPreferences
import com.example.data.sync.SyncStatusTracker
import com.example.data.sync.configureFirestoreOfflinePersistence
import com.example.data.sync.isNetworkCurrentlyConnected
import com.example.data.sync.observeNetworkConnectivity
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.FinbossErpTheme
import com.example.ui.viewmodel.DashboardViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class FinbossBootstrapDeps(
  val auth: FirebaseAuth,
  val db: FirebaseFirestore,
  val syncTracker: SyncStatusTracker,
  val syncPrefs: SyncPreferences,
  val settingsStore: ErpSettingsStore,
  val appContext: Context,
)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FinbossErpTheme {
        FinbossAppNavigation()
      }
    }
  }
}

@Composable
fun FinbossAppNavigation(authOverride: FirebaseAuth? = null) {
  val appContext = LocalContext.current.applicationContext
  var deps by remember { mutableStateOf<FinbossBootstrapDeps?>(null) }

  LaunchedEffect(appContext, authOverride) {
    // Allow MainActivity's initial SurfaceSyncGroup frame to commit before initializing Firebase & disk caches.
    withFrameNanos {}
    deps =
      withContext(Dispatchers.IO) {
        val resolvedAuth = authOverride ?: Firebase.auth
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        configureFirestoreOfflinePersistence(db)
        val syncPrefs = SyncPreferences(appContext).apply { warmup() }
        val settingsStore = ErpSettingsStore(appContext).apply { warmup() }
        FinbossBootstrapDeps(
          auth = resolvedAuth,
          db = db,
          syncTracker = SyncStatusTracker(),
          syncPrefs = syncPrefs,
          settingsStore = settingsStore,
          appContext = appContext,
        )
      }
  }

  val loadedDeps = deps
  if (loadedDeps == null) {
    Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(36.dp),
        color = MaterialTheme.colorScheme.primary,
      )
    }
    return
  }

  val currentUser by
    loadedDeps.auth
      .authStateFlow()
      .collectAsStateWithLifecycle(initialValue = loadedDeps.auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    val uid = user.uid
    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Business Owner"
    val email = user.email
    val photoUrl = user.photoUrl?.toString()

    key(uid) {
      val dashboardViewModel: DashboardViewModel =
        viewModel(
          key = uid,
          factory =
            viewModelFactory {
              initializer {
                val isOnlineProvider: () -> Boolean = {
                  isNetworkCurrentlyConnected(loadedDeps.appContext)
                }
                DashboardViewModel(
                  repository =
                    ErpDashboardRepository(
                      db = loadedDeps.db,
                      auth = loadedDeps.auth,
                      syncTracker = loadedDeps.syncTracker,
                      isOnlineProvider = isOnlineProvider,
                    ),
                  operationsRepository =
                    ErpOperationsRepository(
                      db = loadedDeps.db,
                      auth = loadedDeps.auth,
                      syncTracker = loadedDeps.syncTracker,
                      isOnlineProvider = isOnlineProvider,
                    ),
                  currentUserId = uid,
                  initialOwnerName = displayName,
                  initialOwnerEmail = email,
                  syncTracker = loadedDeps.syncTracker,
                  syncPrefs = loadedDeps.syncPrefs,
                  settingsStore = loadedDeps.settingsStore,
                  networkStatusFlow = observeNetworkConnectivity(loadedDeps.appContext),
                )
              }
            },
        )

      DashboardScreen(
        viewModel = dashboardViewModel,
        userDisplayName = displayName,
        userEmail = email,
        userPhotoUrl = photoUrl,
      )
    }
  }
}
