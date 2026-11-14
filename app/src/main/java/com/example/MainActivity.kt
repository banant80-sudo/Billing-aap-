package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.PhoneAccessGrant
import com.example.data.repository.ErpDashboardRepository
import com.example.data.repository.ErpOperationsRepository
import com.example.data.repository.TeamRepository
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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FinbossBootstrapDeps(
  val auth: FirebaseAuth,
  val db: FirebaseFirestore,
  val teamRepository: TeamRepository,
  val syncTracker: SyncStatusTracker,
  val syncPrefs: SyncPreferences,
  val settingsStore: ErpSettingsStore,
  val appContext: Context,
)

private sealed interface SalesmanAccessResolution {
  data object Loading : SalesmanAccessResolution

  data class Resolved(val grant: PhoneAccessGrant?) : SalesmanAccessResolution
}

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
          teamRepository = TeamRepository(db = db, auth = resolvedAuth),
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
    return
  }

  val uid = user.uid
  val savedSalesmanPhone by
    remember(loadedDeps.syncPrefs, uid) {
        loadedDeps.syncPrefs.observeSalesmanSessionPhone(uid)
      }
      .collectAsStateWithLifecycle(
        initialValue = loadedDeps.syncPrefs.getSalesmanSessionPhone(uid)
      )
  val providerIds = remember(user) { user.providerData.map { it.providerId } }
  val isNativePhoneUser =
    remember(user, providerIds) {
      PhoneAuthProvider.PROVIDER_ID in providerIds &&
        GoogleAuthProvider.PROVIDER_ID !in providerIds
    }
  val isPhoneSalesman = isNativePhoneUser || !savedSalesmanPhone.isNullOrBlank()

  if (isPhoneSalesman) {
    val phoneNumber =
      savedSalesmanPhone?.takeIf { it.isNotBlank() } ?: user.phoneNumber.orEmpty().trim()
    val grantResolutionFlow =
      remember(loadedDeps.teamRepository, phoneNumber) {
        loadedDeps.teamRepository
          .observeMyPhoneAccess(phoneNumber)
          .map<PhoneAccessGrant?, SalesmanAccessResolution> { SalesmanAccessResolution.Resolved(it) }
      }
    val grantResolution by
      grantResolutionFlow.collectAsStateWithLifecycle(
        initialValue = SalesmanAccessResolution.Loading
      )

    when (val res = grantResolution) {
      is SalesmanAccessResolution.Loading -> {
        Box(
          modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      is SalesmanAccessResolution.Resolved -> {
        val grant = res.grant
        if (grant == null || !grant.isActive || grant.ownerUid.isBlank()) {
          SalesmanAccessBlockedScreen(
            phoneNumber = phoneNumber,
            grant = grant,
            uid = uid,
            syncPrefs = loadedDeps.syncPrefs,
            canSwitchToOwnerMode = !isNativePhoneUser,
          )
        } else {
          val ownerUid = grant.ownerUid
          val salesmanDisplayName =
            grant.salesmanName.ifBlank { user.displayName?.takeIf { it.isNotBlank() } ?: "Salesman" }
          val vmKey = "${uid}_${ownerUid}_${phoneNumber}"
          key(vmKey) {
            val dashboardViewModel: DashboardViewModel =
              viewModel(
                key = vmKey,
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
                            workspaceOwnerUidOverride = ownerUid,
                          ),
                        operationsRepository =
                          ErpOperationsRepository(
                            db = loadedDeps.db,
                            auth = loadedDeps.auth,
                            syncTracker = loadedDeps.syncTracker,
                            isOnlineProvider = isOnlineProvider,
                            workspaceOwnerUidOverride = ownerUid,
                          ),
                        currentUserId = uid,
                        initialOwnerName = salesmanDisplayName,
                        initialOwnerEmail = phoneNumber,
                        syncTracker = loadedDeps.syncTracker,
                        syncPrefs = loadedDeps.syncPrefs,
                        settingsStore = loadedDeps.settingsStore,
                        networkStatusFlow = observeNetworkConnectivity(loadedDeps.appContext),
                        teamRepository = loadedDeps.teamRepository,
                        workspaceOwnerUid = ownerUid,
                        isSalesmanSession = true,
                        salesmanPhone = phoneNumber,
                        initialSalesmanGrant = grant,
                      )
                    }
                  },
              )

            DashboardScreen(
              viewModel = dashboardViewModel,
              userDisplayName = salesmanDisplayName,
              userEmail = phoneNumber,
              userPhotoUrl = null,
            )
          }
        }
      }
    }
  } else {
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
                  teamRepository = loadedDeps.teamRepository,
                  workspaceOwnerUid = uid,
                  isSalesmanSession = false,
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

@Composable
private fun SalesmanAccessBlockedScreen(
  phoneNumber: String,
  grant: PhoneAccessGrant?,
  uid: String,
  syncPrefs: SyncPreferences,
  canSwitchToOwnerMode: Boolean,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    ElevatedCard(
      modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
      shape = RoundedCornerShape(24.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.LockPerson,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(32.dp),
            )
          }
        }

        Text(
          text =
            if (grant != null && !grant.isActive) {
              "Salesman Account Paused by Owner"
            } else {
              "Salesman Mobile Number Not Assigned"
            },
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.PhoneAndroid,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
              text = phoneNumber.ifBlank { "No phone number" },
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }

        Text(
          text =
            if (grant != null && !grant.isActive) {
              "Your mobile number (${grant.salesmanName}) is registered, but the Business Owner has temporarily paused your salesman access. As soon as the Owner activates your seat, this screen will unlock automatically."
            } else {
              "Ask your Business Owner to open Finboss ERP -> Workspace -> Salesman Team & OTP Access (up to 5 seats), add your mobile number ($phoneNumber), and choose your allowed modules & profiles."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (canSwitchToOwnerMode) {
          androidx.compose.material3.OutlinedButton(
            onClick = { syncPrefs.setSalesmanSessionPhone(uid, null) },
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("salesman_switch_to_owner_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(
              text = "Open Owner Mode to Register $phoneNumber",
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        Button(
          onClick = {
            syncPrefs.setSalesmanSessionPhone(uid, null)
            signOutUser(credentialManager = credentialManager, scope = scope)
          },
          modifier =
            Modifier.fillMaxWidth()
              .height(50.dp)
              .testTag("salesman_blocked_sign_out_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sign Out / Switch Account", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
