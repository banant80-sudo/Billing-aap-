package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ai.BookAiViewModel
import com.example.auth.signOutUser
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpModuleConfig
import com.example.data.model.GstInvoice
import com.example.data.model.PhoneAccessGrant
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SalesmanSeat
import com.example.data.model.SalesmanSeatConfig
import com.example.data.model.SubscriptionStatus
import com.example.data.model.SubscriptionTier
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.FinbossBackupPayload
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.viewmodel.DashboardKpiSummary
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WorkspaceSyncState
import com.example.util.InvoicePdfPrinter
import com.example.util.WhatsAppShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DashboardTab {
  OVERVIEW,
  BILLING,
  STOCK,
  PARTIES,
  EXPENSES,
  REPORTS,
  AI_COPILOT,
  WORKSPACE,
}

private val BOTTOM_BAR_TABS =
  listOf(
    DashboardTab.OVERVIEW,
    DashboardTab.BILLING,
    DashboardTab.PARTIES,
    DashboardTab.REPORTS,
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  viewModel: DashboardViewModel,
  userDisplayName: String,
  userEmail: String?,
  userPhotoUrl: String?,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val profilesState by viewModel.profilesState.collectAsStateWithLifecycle()
  val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
  val modulesState by viewModel.modulesState.collectAsStateWithLifecycle()
  val entriesState by viewModel.entriesState.collectAsStateWithLifecycle()
  val partiesState by viewModel.partiesState.collectAsStateWithLifecycle()
  val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
  val invoicesState by viewModel.invoicesState.collectAsStateWithLifecycle()
  val expensesState by viewModel.expensesState.collectAsStateWithLifecycle()
  val kpiSummary by viewModel.kpiSummary.collectAsStateWithLifecycle()
  val syncState by viewModel.syncState.collectAsStateWithLifecycle()
  val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val effectivePermissions by viewModel.effectivePermissions.collectAsStateWithLifecycle()
  val allowedProfileIds by viewModel.allowedProfileIds.collectAsStateWithLifecycle()
  val salesmenState by viewModel.salesmenState.collectAsStateWithLifecycle()
  val activeSalesmanGrant by viewModel.activeSalesmanGrant.collectAsStateWithLifecycle()
  val subscriptionStatus by viewModel.subscriptionStatus.collectAsStateWithLifecycle()
  val isSalesmanSession = viewModel.isSalesmanSession

  val visibleTabs =
    remember(effectivePermissions) {
      buildList {
        if (effectivePermissions.canViewOverview) add(DashboardTab.OVERVIEW)
        if (effectivePermissions.canViewBilling || effectivePermissions.canCreateInvoices) {
          add(DashboardTab.BILLING)
        }
        if (effectivePermissions.canViewStock || effectivePermissions.canManageStock) {
          add(DashboardTab.STOCK)
        }
        if (effectivePermissions.canViewParties || effectivePermissions.canManageParties) {
          add(DashboardTab.PARTIES)
        }
        if (effectivePermissions.canViewExpenses || effectivePermissions.canManageExpenses) {
          add(DashboardTab.EXPENSES)
        }
        if (effectivePermissions.canViewReports) {
          add(DashboardTab.REPORTS)
        }
        add(DashboardTab.AI_COPILOT)
        add(DashboardTab.WORKSPACE)
      }
    }

  val aiViewModel: BookAiViewModel = viewModel()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  var aiCopilotMode by rememberSaveable { mutableStateOf(AiCopilotMode.CHAT) }
  var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.OVERVIEW) }
  var selectedLedgerMode by rememberSaveable { mutableStateOf(LedgerPageMode.PARTY_LEDGER) }
  var ledgerNavNonce by rememberSaveable { mutableIntStateOf(0) }
  var selectedReportSection by rememberSaveable { mutableStateOf(ReportPageSection.PROFIT_LOSS) }
  var reportNavNonce by rememberSaveable { mutableIntStateOf(0) }
  var purchaseTriggerNonce by rememberSaveable { mutableIntStateOf(0) }
  var showProfilesSheet by remember { mutableStateOf(false) }
  var editingProfileSlot by remember { mutableStateOf<Pair<String, BusinessProfile?>?>(null) }
  var showAddEntryDialog by remember { mutableStateOf(false) }
  var showNewInvoiceDialog by remember { mutableStateOf(false) }
  var showReceivePaymentDialog by remember { mutableStateOf(false) }
  var showAddInventoryDialog by remember { mutableStateOf(false) }
  var showAddPartyDialog by remember { mutableStateOf(false) }
  var showAddExpenseDialog by remember { mutableStateOf(false) }
  var showBackupSheet by remember { mutableStateOf(false) }
  var showAppSettingsSheet by remember { mutableStateOf(false) }
  var showSalesmanTeamSheet by remember { mutableStateOf(false) }
  var showSubscriptionSheet by remember { mutableStateOf(false) }
  var upgradeDialogSpec by remember { mutableStateOf<Triple<String, String, SubscriptionTier>?>(null) }
  var pendingRestorePayload by remember { mutableStateOf<FinbossBackupPayload?>(null) }
  var selectedModuleForSheet by remember { mutableStateOf<ErpModuleConfig?>(null) }

  LaunchedEffect(visibleTabs, selectedTab) {
    if (selectedTab !in visibleTabs) {
      selectedTab = visibleTabs.firstOrNull() ?: DashboardTab.WORKSPACE
    }
  }

  val exportBackupLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      val payload = viewModel.createBackupPayload()
      val jsonText = viewModel.createBackupJson()
      coroutineScope.launch {
        val writeResult =
          withContext(Dispatchers.IO) {
            runCatching {
              context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(jsonText.toByteArray(Charsets.UTF_8))
                stream.flush()
              } ?: error("Unable to open destination file")
            }
          }
        writeResult
          .onSuccess { viewModel.onLocalBackupSaved(payload.totalRecordsCount) }
          .onFailure { err ->
            viewModel.notifyBanner(err.localizedMessage ?: "Failed to write backup file")
          }
      }
    }

  val restoreBackupLauncher =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      coroutineScope.launch {
        val readResult =
          withContext(Dispatchers.IO) {
            runCatching {
              context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
              } ?: error("Unable to read backup file")
            }
          }
        readResult
          .onSuccess { rawJson ->
            viewModel
              .parseBackupPreview(rawJson)
              .onSuccess { parsed ->
                pendingRestorePayload = parsed
                showBackupSheet = false
              }
              .onFailure { err ->
                viewModel.notifyBanner(err.localizedMessage ?: "Invalid Finboss backup JSON")
              }
          }
          .onFailure { err ->
            viewModel.notifyBanner(err.localizedMessage ?: "Failed to read backup file")
          }
      }
    }

  val triggerExportBackup: () -> Unit = {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    exportBackupLauncher.launch("finboss_backup_$stamp.json")
  }

  val triggerRestoreBackup: () -> Unit = {
    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
  }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(statusBanner) {
    val msg = statusBanner ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearBanner()
  }

  val defaultHomeTab = visibleTabs.firstOrNull() ?: DashboardTab.WORKSPACE
  BackHandler(enabled = drawerState.isOpen || selectedTab != defaultHomeTab) {
    if (drawerState.isOpen) {
      coroutineScope.launch { drawerState.close() }
    } else {
      selectedTab = defaultHomeTab
    }
  }

  val profile = (profileState as? UiState.Success)?.data
  val profiles = (profilesState as? UiState.Success)?.data.orEmpty()
  val salesmen = (salesmenState as? UiState.Success)?.data.orEmpty()
  val activeSlotId = profile?.resolvedSlotId ?: activeProfileId
  val isPlainNonGst =
    profile?.isPlainNonGst == true || activeSlotId == BusinessProfile.SLOT_PLAIN_1
  val currency = profile?.currencySymbol ?: "₹"
  val parties = (partiesState as? UiState.Success)?.data.orEmpty()
  val inventory = (inventoryState as? UiState.Success)?.data.orEmpty()
  val invoices = (invoicesState as? UiState.Success)?.data.orEmpty()
  val expenses = (expensesState as? UiState.Success)?.data.orEmpty()
  val entries = (entriesState as? UiState.Success)?.data.orEmpty()

  val activeRouteKey =
    when (selectedTab) {
      DashboardTab.OVERVIEW -> "overview"
      DashboardTab.BILLING -> "billing"
      DashboardTab.STOCK -> "stock"
      DashboardTab.PARTIES ->
        if (selectedLedgerMode == LedgerPageMode.ITEM_LEDGER) "ledger_items" else "ledger_parties"
      DashboardTab.EXPENSES -> "expenses"
      DashboardTab.REPORTS -> "report_${selectedReportSection.key}"
      DashboardTab.AI_COPILOT -> "ai_${aiCopilotMode.name.lowercase()}"
      DashboardTab.WORKSPACE -> "workspace"
    }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      EnterpriseNavigationDrawerContent(
        profile = profile,
        userDisplayName = userDisplayName,
        isPlainNonGst = isPlainNonGst,
        isSalesmanSession = isSalesmanSession,
        effectivePermissions = effectivePermissions,
        allowedProfilesCount = allowedProfileIds.size,
        subscriptionStatus = subscriptionStatus,
        activeRouteKey = activeRouteKey,
        onSelectOverview = {
          selectedTab = DashboardTab.OVERVIEW
          coroutineScope.launch { drawerState.close() }
        },
        onSelectSaleInvoice = {
          selectedTab = DashboardTab.BILLING
          coroutineScope.launch { drawerState.close() }
        },
        onSelectPurchase = {
          selectedTab = DashboardTab.STOCK
          purchaseTriggerNonce++
          coroutineScope.launch { drawerState.close() }
        },
        onSelectInventoryCatalog = {
          selectedTab = DashboardTab.STOCK
          coroutineScope.launch { drawerState.close() }
        },
        onSelectExpense = {
          selectedTab = DashboardTab.EXPENSES
          coroutineScope.launch { drawerState.close() }
        },
        onSelectLedgerMode = { mode ->
          selectedLedgerMode = mode
          ledgerNavNonce++
          selectedTab = DashboardTab.PARTIES
          coroutineScope.launch { drawerState.close() }
        },
        onSelectReportSection = { sec ->
          selectedReportSection = sec
          reportNavNonce++
          selectedTab = DashboardTab.REPORTS
          coroutineScope.launch { drawerState.close() }
        },
        onSelectAiMode = { mode ->
          aiCopilotMode = mode
          selectedTab = DashboardTab.AI_COPILOT
          coroutineScope.launch { drawerState.close() }
        },
        onOpenWorkspace = {
          selectedTab = DashboardTab.WORKSPACE
          coroutineScope.launch { drawerState.close() }
        },
        onOpenSwitchProfile = {
          coroutineScope.launch { drawerState.close() }
          showProfilesSheet = true
        },
        onOpenAppSettings = {
          coroutineScope.launch { drawerState.close() }
          showAppSettingsSheet = true
        },
        onOpenBackupSync = {
          coroutineScope.launch { drawerState.close() }
          showBackupSheet = true
        },
        onOpenSubscriptionPlans = {
          coroutineScope.launch { drawerState.close() }
          showSubscriptionSheet = true
        },
        onSignOut = {
          coroutineScope.launch { drawerState.close() }
          viewModel.clearSalesmanSessionOnSignOut()
          signOutUser(CredentialManager.create(context), coroutineScope)
        },
      )
    },
  ) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surface,
          header = {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(
              onClick = { coroutineScope.launch { drawerState.open() } },
              modifier = Modifier.testTag("rail_menu_button"),
            ) {
              Icon(Icons.Filled.Menu, contentDescription = "Open Category Menu")
            }
          },
        ) {
          Spacer(modifier = Modifier.height(8.dp))
          visibleTabs.forEach { tab ->
            val selected = selectedTab == tab
            NavigationRailItem(
              selected = selected,
              onClick = { selectedTab = tab },
              icon = { Icon(tabIcon(tab, selected), contentDescription = tabLabel(tab)) },
              label = { Text(tabLabel(tab)) },
              modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
              ),
            navigationIcon = {
              IconButton(
                onClick = { coroutineScope.launch { drawerState.open() } },
                modifier = Modifier.testTag("drawer_menu_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Menu,
                  contentDescription = "Open Category Menu",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            },
            title = {
              Column {
                Text(
                  text =
                    when (selectedTab) {
                      DashboardTab.OVERVIEW -> stringResource(R.string.brand_full_title)
                      DashboardTab.BILLING ->
                        if (isPlainNonGst) "Plain Bills" else "Sale Invoices"
                      DashboardTab.STOCK -> "Inventory & Purchase"
                      DashboardTab.PARTIES -> selectedLedgerMode.label
                      DashboardTab.EXPENSES -> "Expenses & Cashbook"
                      DashboardTab.REPORTS -> selectedReportSection.title
                      DashboardTab.AI_COPILOT -> aiCopilotMode.label
                      DashboardTab.WORKSPACE -> "Workspace & Settings"
                    },
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val modeSubtitle = if (isPlainNonGst) "Plain Non-GST" else "Main GST"
                Text(
                  text = "${profile?.businessName ?: "$userDisplayName Enterprise"} • $modeSubtitle",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            },
            actions = {
              if (!isSalesmanSession) {
                Surface(
                  color =
                    if (subscriptionStatus.isTrial) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                  contentColor =
                    if (subscriptionStatus.isTrial) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                  shape = RoundedCornerShape(999.dp),
                  modifier =
                    Modifier.clickable { showSubscriptionSheet = true }
                      .testTag("topbar_subscription_badge"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.WorkspacePremium,
                      contentDescription = "Subscription & Free Trial",
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = subscriptionStatus.topBarBadgeText,
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.ExtraBold,
                    )
                  }
                }
              }
              if (!isSalesmanSession || allowedProfileIds.size > 1) {
                IconButton(
                  onClick = { showProfilesSheet = true },
                  modifier = Modifier.testTag("switch_profile_topbar_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = "Switch Business Account",
                  )
                }
              }
            },
          )
        },
        floatingActionButton = {
          when (selectedTab) {
            DashboardTab.BILLING ->
              if (effectivePermissions.canCreateInvoices) {
                ExtendedFloatingActionButton(
                  onClick = { showNewInvoiceDialog = true },
                  icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                  text = { Text("Create Invoice") },
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.testTag("new_invoice_fab"),
                )
              }
            DashboardTab.STOCK ->
              if (effectivePermissions.canManageStock) {
                ExtendedFloatingActionButton(
                  onClick = { showAddInventoryDialog = true },
                  icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                  text = { Text("Add SKU") },
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.testTag("add_inventory_fab"),
                )
              }
            DashboardTab.PARTIES ->
              if (effectivePermissions.canManageParties) {
                ExtendedFloatingActionButton(
                  onClick = { showAddPartyDialog = true },
                  icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                  text = { Text("Add Party") },
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.testTag("add_party_fab"),
                )
              }
            DashboardTab.EXPENSES ->
              if (effectivePermissions.canManageExpenses) {
                ExtendedFloatingActionButton(
                  onClick = { showAddExpenseDialog = true },
                  icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                  text = { Text("Add Expense") },
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.testTag("add_expense_fab"),
                )
              }
            DashboardTab.OVERVIEW,
            DashboardTab.REPORTS,
            DashboardTab.AI_COPILOT,
            DashboardTab.WORKSPACE -> {}
          }
        },
        bottomBar = {
          if (!useRail) {
            val bottomTabs =
              remember(visibleTabs) {
                val primary = BOTTOM_BAR_TABS.filter { it in visibleTabs }
                if (primary.size >= 2) primary else visibleTabs.take(4)
              }
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              tonalElevation = 3.dp,
            ) {
              bottomTabs.forEach { tab ->
                val selected = selectedTab == tab
                NavigationBarItem(
                  selected = selected,
                  onClick = { selectedTab = tab },
                  icon = { Icon(tabIcon(tab, selected), contentDescription = tabLabel(tab)) },
                  label = {
                    Text(
                      text = tabLabel(tab),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  },
                  modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                )
              }
              NavigationBarItem(
                selected = drawerState.isOpen,
                onClick = { coroutineScope.launch { drawerState.open() } },
                icon = { Icon(Icons.Filled.Menu, contentDescription = "Open Category Menu") },
                label = { Text("Menu", maxLines = 1) },
                modifier = Modifier.testTag("nav_drawer_menu"),
              )
            }
          }
        },
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.fillMaxSize().widthIn(max = 920.dp)) {
            when (selectedTab) {
              DashboardTab.OVERVIEW ->
                OverviewTabContent(
                  profileState = profileState,
                  profilesCount = profiles.size.coerceAtLeast(if (profile != null) 1 else 0),
                  activeSlotId = activeSlotId,
                  isPlainNonGst = isPlainNonGst,
                  isSalesmanSession = isSalesmanSession,
                  salesmanGrant = activeSalesmanGrant,
                  effectivePermissions = effectivePermissions,
                  allowedProfilesCount = allowedProfileIds.size,
                  invoicesState = invoicesState,
                  kpiSummary = kpiSummary,
                  syncState = syncState,
                  currency = currency,
                  onOpenSyncSheet = { if (!isSalesmanSession) showBackupSheet = true },
                  onOpenProfilesSheet = { showProfilesSheet = true },
                  onEditProfile = { if (!isSalesmanSession) editingProfileSlot = activeSlotId to profile },
                  onAddEntry = { if (effectivePermissions.canAddQuickEntry) showAddEntryDialog = true },
                  onCreateInvoiceClick = { if (effectivePermissions.canCreateInvoices) showNewInvoiceDialog = true },
                  onReceivePaymentClick = { showReceivePaymentDialog = true },
                  onOpenMenuDrawer = { coroutineScope.launch { drawerState.open() } },
                  onNavigateTab = { target ->
                    if (target in visibleTabs) selectedTab = target
                  },
                  onShareOverviewWhatsApp = {
                    val biz = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise"
                    val summary =
                      WhatsAppShareHelper.formatDashboardOverviewSummary(
                        businessName = biz,
                        isPlainNonGst = isPlainNonGst,
                        kpi = kpiSummary,
                        currency = currency,
                      )
                    WhatsAppShareHelper.shareTextToWhatsApp(
                      context = context,
                      text = summary,
                      chooserTitle = "Share Business Overview on WhatsApp",
                    )
                  },
                  onRetryWorkspace = { viewModel.initializeWorkspace() },
                )
              DashboardTab.BILLING ->
                BillingModuleContent(
                  invoicesState = invoicesState,
                  parties = parties,
                  inventory = inventory,
                  profile = profile,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  appSettings = appSettings,
                  isBusy = isBusy,
                  onCreateInvoice = { invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin, onDone ->
                    viewModel.createInvoice(
                      invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin, onDone
                    )
                  },
                  onUpdateInvoice = { existing, invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin, onDone ->
                    viewModel.updateInvoice(
                      existing, invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin, onDone
                    )
                  },
                  onRecordPayment = { inv, amt, method, onDone ->
                    viewModel.recordInvoicePayment(inv, amt, method, onDone)
                  },
                  onDeleteInvoice = { viewModel.deleteInvoice(it) },
                  onNotifyMessage = { viewModel.notifyBanner(it) },
                  onOpenAppSettings = { if (!isSalesmanSession) showAppSettingsSheet = true },
                  showNewInvoiceDialog = showNewInvoiceDialog && effectivePermissions.canCreateInvoices,
                  onDismissNewInvoiceDialog = { showNewInvoiceDialog = false },
                  onOpenNewInvoiceDialog = { if (effectivePermissions.canCreateInvoices) showNewInvoiceDialog = true },
                  canCreateInvoices = effectivePermissions.canCreateInvoices,
                  canEditDeleteInvoices = effectivePermissions.canEditDeleteInvoices,
                  canPrintInvoices = effectivePermissions.canPrintInvoices,
                  canRecordPayments = effectivePermissions.canCreateInvoices || effectivePermissions.canEditDeleteInvoices,
                )
              DashboardTab.STOCK ->
                InventoryModuleContent(
                  inventoryState = inventoryState,
                  parties = parties,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  appSettings = appSettings,
                  isBusy = isBusy,
                  onSaveItem = { id, name, sku, cat, unit, sale, purchase, gst, qty, thresh, hsnCode, onDone ->
                    viewModel.saveInventoryItem(id, name, sku, cat, unit, sale, purchase, gst, qty, thresh, hsnCode, onDone)
                  },
                  onRecordPurchase = { existingItem, newName, newSku, hsnCode, cat, unit, buyPrice, salePrice, gst, qty, thresh, supplier, supFallback, billNo, payMethod, isPaidNow, onDone ->
                    viewModel.recordPurchase(
                      existingItem, newName, newSku, hsnCode, cat, unit, buyPrice, salePrice, gst, qty, thresh, supplier, supFallback, billNo, payMethod, isPaidNow, onDone
                    )
                  },
                  onAdjustStock = { item, delta, onDone ->
                    viewModel.adjustInventoryStock(item, delta, onDone)
                  },
                  onDeleteItem = { viewModel.deleteInventoryItem(it) },
                  showAddItemDialog = showAddInventoryDialog && effectivePermissions.canManageStock,
                  onDismissAddItemDialog = { showAddInventoryDialog = false },
                  onOpenAddItemDialog = { if (effectivePermissions.canManageStock) showAddInventoryDialog = true },
                  canManageStock = effectivePermissions.canManageStock,
                  businessName = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise",
                  purchaseTriggerNonce = purchaseTriggerNonce,
                )
              DashboardTab.PARTIES ->
                PartiesModuleContent(
                  partiesState = partiesState,
                  currency = currency,
                  defaultCreditLimit = appSettings.defaultPartyCreditLimit,
                  isBusy = isBusy,
                  onSaveParty = { id, name, type, phone, gstin, opening, credit, notes, addr, state, pin, onDone ->
                    viewModel.saveParty(id, name, type, phone, gstin, opening, credit, notes, addr, state, pin, onDone)
                  },
                  onRecordPayment = { party, amt, onDone ->
                    viewModel.recordPartyPayment(party, amt, onDone)
                  },
                  onDeleteParty = { viewModel.deleteParty(it) },
                  showAddPartyDialog = showAddPartyDialog && effectivePermissions.canManageParties,
                  onDismissAddPartyDialog = { showAddPartyDialog = false },
                  onOpenAddPartyDialog = { if (effectivePermissions.canManageParties) showAddPartyDialog = true },
                  canManageParties = effectivePermissions.canManageParties,
                  businessName = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise",
                  invoices = invoices,
                  inventory = inventory,
                  expenses = expenses,
                  entries = entries,
                  profile = profile,
                  isPlainNonGst = isPlainNonGst,
                  selectedLedgerMode = selectedLedgerMode,
                  navigationNonce = ledgerNavNonce,
                )
              DashboardTab.EXPENSES ->
                ExpensesModuleContent(
                  expensesState = expensesState,
                  invoices = invoices,
                  entries = entries,
                  parties = parties,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  isBusy = isBusy,
                  onSaveExpense = { id, title, cat, method, status, party, fallback, refNo, amt, gstItc, notes, onDone ->
                    viewModel.saveExpense(
                      id, title, cat, method, status, party, fallback, refNo, amt, gstItc, notes, onDone
                    )
                  },
                  onMarkSettled = { id, method -> viewModel.markExpenseSettled(id, method) },
                  onDeleteExpense = { viewModel.deleteExpense(it) },
                  showAddExpenseDialog = showAddExpenseDialog && effectivePermissions.canManageExpenses,
                  onDismissAddExpenseDialog = { showAddExpenseDialog = false },
                  onOpenAddExpenseDialog = { if (effectivePermissions.canManageExpenses) showAddExpenseDialog = true },
                  canManageExpenses = effectivePermissions.canManageExpenses,
                  businessName = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise",
                )
              DashboardTab.REPORTS ->
                ReportsModuleContent(
                  profile = profile,
                  invoices = invoices,
                  expenses = expenses,
                  entries = entries,
                  parties = parties,
                  inventory = inventory,
                  currency = currency,
                  onSwitchProfileClick = { showProfilesSheet = true },
                  selectedSection = selectedReportSection,
                  navigationNonce = reportNavNonce,
                )
              DashboardTab.AI_COPILOT ->
                AiCopilotScreenContent(
                  aiViewModel = aiViewModel,
                  initialMode = aiCopilotMode,
                  profile = profile,
                  currency = currency,
                  isPlainNonGst = isPlainNonGst,
                  parties = parties,
                  inventory = inventory,
                  invoices = invoices,
                  expenses = expenses,
                  entries = entries,
                  isBusy = isBusy,
                  canCreateInvoices = effectivePermissions.canCreateInvoices,
                  canManageExpenses = effectivePermissions.canManageExpenses,
                  onSaveVoiceDraft = { draft ->
                    val matchedParty =
                      draft.matchedPartyId.takeIf { it.isNotBlank() }?.let { pid ->
                        parties.firstOrNull { it.id == pid }
                      }
                    if (draft.isInvoice) {
                      val lines = draft.lineItems.map { it.toInvoiceLineItem(isPlainNonGst) }
                      val invNo = "BK-AI-${System.currentTimeMillis().toString().takeLast(5)}"
                      val invType =
                        if (isPlainNonGst) GstInvoice.TYPE_POS_BILL else GstInvoice.TYPE_TAX_INVOICE
                      val taxMode =
                        if (isPlainNonGst) GstInvoice.TAX_MODE_NONE else GstInvoice.TAX_MODE_CGST_SGST
                      viewModel.createInvoice(
                        invoiceNumber = invNo,
                        party = matchedParty,
                        walkInPartyName = draft.partyName.ifBlank { "Walk-in Customer" },
                        walkInPartyGstin = draft.partyGstin,
                        invoiceType = invType,
                        taxMode = taxMode,
                        paymentMethod = draft.paymentMethod,
                        discountAmount = draft.discountAmount,
                        paidAmount = draft.paidAmount,
                        notes = draft.notes.ifBlank { "Voice AI: ${draft.transcript}".take(160) },
                        lineItems = lines,
                        onDone = {
                          aiViewModel.resetVoiceState()
                          selectedTab = DashboardTab.BILLING
                        },
                      )
                    } else {
                      viewModel.saveExpense(
                        existingId = null,
                        title = draft.expenseTitle.ifBlank { "Voice Expense" },
                        category = draft.expenseCategory,
                        paymentMethod = draft.paymentMethod,
                        status = ErpExpense.STATUS_SETTLED,
                        party = matchedParty,
                        partyNameFallback = draft.partyName,
                        referenceNo = "VOICE-${System.currentTimeMillis().toString().takeLast(4)}",
                        amount = draft.expenseAmount,
                        gstInputAmount = draft.gstInputAmount,
                        notes = draft.notes.ifBlank { draft.transcript.take(160) },
                        onDone = {
                          aiViewModel.resetVoiceState()
                          selectedTab = DashboardTab.EXPENSES
                        },
                      )
                    }
                  },
                  onSaveOcrExpense = { bill ->
                    val matchedParty =
                      bill.matchedPartyId.takeIf { it.isNotBlank() }?.let { pid ->
                        parties.firstOrNull { it.id == pid }
                      }
                    viewModel.saveExpense(
                      existingId = null,
                      title = "Bill: ${bill.partyName}".take(80),
                      category = bill.expenseCategory,
                      paymentMethod = bill.paymentMethod,
                      status = ErpExpense.STATUS_SETTLED,
                      party = matchedParty,
                      partyNameFallback = bill.partyName,
                      referenceNo = bill.billOrInvoiceNumber.ifBlank { "OCR-${System.currentTimeMillis().toString().takeLast(4)}" },
                      amount = bill.grandTotal,
                      gstInputAmount = if (isPlainNonGst) 0.0 else bill.gstAmount,
                      notes = bill.notes.ifBlank { "Scanned bill (${bill.billDate})" },
                      onDone = {
                        aiViewModel.resetOcrState()
                        selectedTab = DashboardTab.EXPENSES
                      },
                    )
                  },
                  onSaveOcrInvoice = { bill ->
                    val matchedParty =
                      bill.matchedPartyId.takeIf { it.isNotBlank() }?.let { pid ->
                        parties.firstOrNull { it.id == pid }
                      }
                    val lines = bill.lineItems.map { it.toInvoiceLineItem(isPlainNonGst) }
                    val invNo =
                      bill.billOrInvoiceNumber.ifBlank {
                        "BK-OCR-${System.currentTimeMillis().toString().takeLast(4)}"
                      }
                    viewModel.createInvoice(
                      invoiceNumber = invNo,
                      party = matchedParty,
                      walkInPartyName = bill.partyName.ifBlank { "Walk-in Customer" },
                      walkInPartyGstin = bill.partyGstin,
                      invoiceType = if (isPlainNonGst) GstInvoice.TYPE_POS_BILL else GstInvoice.TYPE_TAX_INVOICE,
                      taxMode = if (isPlainNonGst) GstInvoice.TAX_MODE_NONE else GstInvoice.TAX_MODE_CGST_SGST,
                      paymentMethod = bill.paymentMethod,
                      discountAmount = 0.0,
                      paidAmount = bill.grandTotal,
                      notes = bill.notes.ifBlank { "OCR scanned invoice (${bill.billDate})" },
                      lineItems = lines,
                      onDone = {
                        aiViewModel.resetOcrState()
                        selectedTab = DashboardTab.BILLING
                      },
                    )
                  },
                )
              DashboardTab.WORKSPACE ->
                WorkspaceTabContent(
                  profile = profile,
                  profilesCount = profiles.size.coerceAtLeast(if (profile != null) 1 else 0),
                  activeSlotId = activeSlotId,
                  isPlainNonGst = isPlainNonGst,
                  isSalesmanSession = isSalesmanSession,
                  salesmanGrant = activeSalesmanGrant,
                  effectivePermissions = effectivePermissions,
                  allowedProfileIds = allowedProfileIds,
                  salesmen = salesmen,
                  modulesState = modulesState,
                  syncState = syncState,
                  appSettings = appSettings,
                  isBusy = isBusy,
                  userDisplayName = userDisplayName,
                  userEmail = userEmail,
                  userPhotoUrl = userPhotoUrl,
                  currency = currency,
                  subscriptionStatus = subscriptionStatus,
                  onOpenSubscriptionPlans = { showSubscriptionSheet = true },
                  onManageProfiles = { showProfilesSheet = true },
                  onManageSalesmen = { showSalesmanTeamSheet = true },
                  onEditProfile = { editingProfileSlot = activeSlotId to profile },
                  onOpenAppSettings = { showAppSettingsSheet = true },
                  onSyncNow = { viewModel.syncNow() },
                  onExportBackup = triggerExportBackup,
                  onRestoreBackup = triggerRestoreBackup,
                  onSelectModule = { module ->
                    when (module.code) {
                      "dashboard" ->
                        if (DashboardTab.OVERVIEW in visibleTabs) selectedTab = DashboardTab.OVERVIEW
                      "billing" ->
                        if (DashboardTab.BILLING in visibleTabs) selectedTab = DashboardTab.BILLING
                      "inventory" ->
                        if (DashboardTab.STOCK in visibleTabs) selectedTab = DashboardTab.STOCK
                      "parties" ->
                        if (DashboardTab.PARTIES in visibleTabs) selectedTab = DashboardTab.PARTIES
                      "expenses" ->
                        if (DashboardTab.EXPENSES in visibleTabs) selectedTab = DashboardTab.EXPENSES
                      "gst_reports" ->
                        if (DashboardTab.REPORTS in visibleTabs) selectedTab = DashboardTab.REPORTS
                      else -> if (!isSalesmanSession) selectedModuleForSheet = module
                    }
                  },
                  onTogglePin = { if (!isSalesmanSession) viewModel.toggleModulePin(it) },
                  onSignOut = {
                    viewModel.clearSalesmanSessionOnSignOut()
                    signOutUser(CredentialManager.create(context), coroutineScope)
                  },
                )
            }
          }
        }
      }
    }
  }
  } // End ModalNavigationDrawer

  if (showReceivePaymentDialog) {
    ReceivePaymentDashboardDialog(
      invoices = invoices,
      parties = parties,
      businessName = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise",
      currency = currency,
      isBusy = isBusy,
      onDismiss = { showReceivePaymentDialog = false },
      onRecordInvoicePayment = { inv, amt, method, onDone ->
        viewModel.recordInvoicePayment(inv, amt, method, onDone)
      },
      onRecordPartyPayment = { party, amt, onDone ->
        viewModel.recordPartyPayment(party, amt, onDone)
      },
    )
  }

  // Allow creating an invoice from Overview FAB even while selectedTab == OVERVIEW
  if (showNewInvoiceDialog && selectedTab == DashboardTab.OVERVIEW) {
    val invoicesList = (invoicesState as? UiState.Success)?.data.orEmpty()
    NewInvoiceDialog(
      defaultInvoiceNumber = "${appSettings.invoicePrefix}${(invoicesList.size + 1).toString().padStart(3, '0')}",
      parties = parties,
      inventory = inventory,
      profile = profile,
      appSettings = appSettings,
      currency = currency,
      isPlainNonGst = isPlainNonGst,
      isBusy = isBusy,
      onDismiss = { showNewInvoiceDialog = false },
      onCreate = { invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin ->
        viewModel.createInvoice(
          invNo, party, walkInName, walkInGstin, type, taxMode, payMethod, discount, paid, notes, lines, walkAddr, walkState, walkPin
        ) {
          showNewInvoiceDialog = false
          selectedTab = DashboardTab.BILLING
        }
      },
    )
  }

  if (showProfilesSheet) {
    BusinessProfilesSheet(
      profiles = profiles,
      activeSlotId = activeSlotId,
      isBusy = isBusy,
      allowedSlots = allowedProfileIds,
      canManageProfiles = !isSalesmanSession,
      onSwitchProfile = { slotId ->
        if (!isSalesmanSession && !subscriptionStatus.canUseProfileSlot(slotId)) {
          showProfilesSheet = false
          val reqTier =
            if (slotId == BusinessProfile.SLOT_PLAIN_1) SubscriptionTier.DIAMOND
            else SubscriptionTier.GOLD
          upgradeDialogSpec =
            Triple(
              "Multi-Profile Account ($slotId)",
              "Your ${subscriptionStatus.effectiveTier.displayName} supports up to ${subscriptionStatus.maxProfiles} business profile(s). Upgrade to ${reqTier.displayName} to unlock this business account.",
              reqTier,
            )
        } else {
          viewModel.switchActiveProfile(slotId)
          showProfilesSheet = false
        }
      },
      onCreateOrEditSlot = { slotId, existing ->
        if (!isSalesmanSession) {
          showProfilesSheet = false
          if (!subscriptionStatus.canUseProfileSlot(slotId)) {
            val reqTier =
              if (slotId == BusinessProfile.SLOT_PLAIN_1) SubscriptionTier.DIAMOND
              else SubscriptionTier.GOLD
            upgradeDialogSpec =
              Triple(
                "Business Profile Slot ($slotId)",
                "Your ${subscriptionStatus.effectiveTier.displayName} allows up to ${subscriptionStatus.maxProfiles} business profile(s). Upgrade to ${reqTier.displayName} (or activate 30-Day Free Trial) to configure additional business profiles.",
                reqTier,
              )
          } else {
            editingProfileSlot = slotId to existing
          }
        }
      },
      onDeleteSlot = { slotId ->
        if (!isSalesmanSession) viewModel.deleteProfileAccount(slotId)
      },
      onDismiss = { showProfilesSheet = false },
    )
  }

  if (!isSalesmanSession) {
    editingProfileSlot?.let { (slotId, targetProfile) ->
      BusinessProfileDialog(
        profile = targetProfile,
        slotId = slotId,
        defaultOwnerName = profile?.ownerName ?: userDisplayName,
        isBusy = isBusy,
        onDismiss = { editingProfileSlot = null },
        onSave = { owner, biz, gstin, curr, fy, type, target, address, state, pincode ->
          viewModel.saveProfileAccount(
            slotId = slotId,
            ownerName = owner,
            businessName = biz,
            gstin = gstin,
            currencySymbol = curr,
            fiscalYearLabel = fy,
            businessType = type,
            monthlySalesTarget = target,
            address = address,
            state = state,
            pincode = pincode,
            switchToAfterSave = true,
            onDone = { editingProfileSlot = null },
          )
        },
      )
    }
  }

  if (showAddEntryDialog && effectivePermissions.canAddQuickEntry) {
    AddDashboardEntryDialog(
      currencySymbol = currency,
      isBusy = isBusy,
      onDismiss = { showAddEntryDialog = false },
      onSubmit = { title, party, category, amount, status, refCode ->
        viewModel.addDashboardEntry(
          title = title,
          partyName = party,
          category = category,
          amount = amount,
          status = status,
          referenceCode = refCode,
          onDone = { showAddEntryDialog = false },
        )
      },
    )
  }

  val activeSheetModule =
    selectedModuleForSheet?.let { selected ->
      val liveList = (modulesState as? UiState.Success)?.data.orEmpty()
      liveList.firstOrNull { it.id == selected.id } ?: selected
    }

  if (activeSheetModule != null && !isSalesmanSession) {
    ModuleDetailSheet(
      module = activeSheetModule,
      onDismiss = { selectedModuleForSheet = null },
      onTogglePin = { viewModel.toggleModulePin(activeSheetModule) },
      onSetQueued = { queued -> viewModel.setModuleQueued(activeSheetModule, queued) },
    )
  }

  if (showBackupSheet && !isSalesmanSession) {
    BackupSyncModalSheet(
      syncState = syncState,
      isBusy = isBusy,
      onSyncNow = { viewModel.syncNow() },
      onExportBackup = triggerExportBackup,
      onRestoreBackup = triggerRestoreBackup,
      onDismiss = { showBackupSheet = false },
    )
  }

  if (!isSalesmanSession) {
    pendingRestorePayload?.let { payload ->
      RestoreBackupConfirmDialog(
        payload = payload,
        isBusy = isBusy,
        onConfirm = {
          viewModel.restoreBackup(payload) {
            pendingRestorePayload = null
          }
        },
        onDismiss = { pendingRestorePayload = null },
      )
    }
  }

  if (showAppSettingsSheet && !isSalesmanSession) {
    AppSettingsSheet(
      settings = appSettings,
      isPlainNonGst = isPlainNonGst,
      onUpdateSettings = { transform -> viewModel.updateAppSettings(transform) },
      onResetDefaults = { viewModel.resetAppSettings() },
      onDismiss = { showAppSettingsSheet = false },
    )
  }

  if (showSalesmanTeamSheet && !isSalesmanSession) {
    SalesmanTeamSheet(
      salesmen = salesmen,
      profiles = profiles,
      isBusy = isBusy,
      onSaveSeat = { seatId, existing, name, phoneE164, roleLabel, isActive, allowedProfiles, perms, onDone ->
        if (!subscriptionStatus.canUseSalesmanSeat(seatId)) {
          showSalesmanTeamSheet = false
          val reqTier =
            if (seatId in listOf("seat_1", "seat_2")) SubscriptionTier.GOLD
            else SubscriptionTier.DIAMOND
          upgradeDialogSpec =
            Triple(
              "Salesman OTP Seat (${seatId.replace("_", " ").uppercase()})",
              "Your ${subscriptionStatus.effectiveTier.displayName} includes ${subscriptionStatus.maxSalesmen} Salesman OTP seat(s). Upgrade to ${reqTier.displayName} to unlock more salesman logins.",
              reqTier,
            )
        } else {
          viewModel.saveSalesmanSeat(
            seatId = seatId,
            existingSeat = existing,
            name = name,
            rawPhoneE164 = phoneE164,
            roleLabel = roleLabel,
            isActive = isActive,
            allowedProfiles = allowedProfiles,
            permissions = perms,
            onDone = onDone,
          )
        }
      },
      onToggleActive = { seat, isActive -> viewModel.toggleSalesmanActive(seat, isActive) },
      onDeleteSeat = { seat -> viewModel.deleteSalesmanSeat(seat) },
      onDismiss = { showSalesmanTeamSheet = false },
    )
  }

  if (showSubscriptionSheet) {
    val activeBizName =
      (profileState as? UiState.Success)?.data?.businessName?.ifBlank { null }
        ?: "$userDisplayName Enterprise"
    SubscriptionPlansSheet(
      status = subscriptionStatus,
      isOwnerSession = !isSalesmanSession,
      appSettings = appSettings,
      userEmail = userEmail,
      businessName = activeBizName,
      isBusy = isBusy,
      onActivatePlan = { tier, cycle, onSuccess ->
        viewModel.activateSubscriptionPlan(tier, cycle, onSuccess)
      },
      onDismiss = { showSubscriptionSheet = false },
    )
  }

  upgradeDialogSpec?.let { (featureTitle, featureDesc, recTier) ->
    UpgradePlanRequiredDialog(
      featureTitle = featureTitle,
      featureDescription = featureDesc,
      currentPlanLabel = subscriptionStatus.topBarBadgeText,
      recommendedTier = recTier,
      onViewPlans = {
        upgradeDialogSpec = null
        showSubscriptionSheet = true
      },
      onDismiss = { upgradeDialogSpec = null },
    )
  }
}


@Composable
private fun OverviewTabContent(
  profileState: UiState<BusinessProfile?>,
  profilesCount: Int,
  activeSlotId: String,
  isPlainNonGst: Boolean,
  isSalesmanSession: Boolean,
  salesmanGrant: PhoneAccessGrant?,
  effectivePermissions: SalesmanPermissions,
  allowedProfilesCount: Int,
  invoicesState: UiState<List<GstInvoice>>,
  kpiSummary: DashboardKpiSummary,
  syncState: WorkspaceSyncState,
  currency: String,
  onOpenSyncSheet: () -> Unit,
  onOpenProfilesSheet: () -> Unit,
  onEditProfile: () -> Unit,
  onAddEntry: () -> Unit,
  onCreateInvoiceClick: () -> Unit,
  onReceivePaymentClick: () -> Unit,
  onOpenMenuDrawer: () -> Unit,
  onNavigateTab: (DashboardTab) -> Unit,
  onShareOverviewWhatsApp: () -> Unit = {},
  onRetryWorkspace: () -> Unit,
) {
  val profile = (profileState as? UiState.Success)?.data
  val recentInvoices = (invoicesState as? UiState.Success)?.data.orEmpty().take(3)
  val canCollectPayment =
    effectivePermissions.canCreateInvoices || effectivePermissions.canEditDeleteInvoices

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (isSalesmanSession) {
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.fillMaxWidth().testTag("salesman_active_session_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Column {
                Text(
                  text = "Salesman Mode • ${salesmanGrant?.roleLabel?.ifBlank { "Salesman" } ?: "Salesman"}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                  text = "${salesmanGrant?.salesmanName.orEmpty()} (${salesmanGrant?.phoneNumber.orEmpty()})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
              }
            }
            TextButton(onClick = { onNavigateTab(DashboardTab.WORKSPACE) }) {
              Text("Access Info")
            }
          }
        }
      }
    } else {
      item {
        SyncStatusPill(
          syncState = syncState,
          onClick = onOpenSyncSheet,
        )
      }
    }

    item {
      ExecutiveWorkspaceCard(
        profile = profile,
        profilesCount = profilesCount,
        activeSlotId = activeSlotId,
        isPlainNonGst = isPlainNonGst,
        isSalesmanSession = isSalesmanSession,
        allowedProfilesCount = allowedProfilesCount,
        kpiSummary = kpiSummary,
        currency = currency,
        isLoading = profileState is UiState.Loading,
        onOpenProfilesSheet = onOpenProfilesSheet,
        onEditProfile = onEditProfile,
      )
    }

    if (profileState is UiState.Error) {
      item { ErrorRetryCard(message = profileState.message, onRetry = onRetryWorkspace) }
    }

    // Top KPI Cards
    item {
      BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 640.dp
        val cards =
          listOf(
            KpiSpec(
              label = "Collected Revenue",
              value = formatErpMoney(currency, kpiSummary.totalSales),
              sublabel = "Invoices & settled sales",
              icon = Icons.AutoMirrored.Filled.TrendingUp,
              accent = MaterialTheme.colorScheme.secondary,
            ),
            KpiSpec(
              label = "Pending Receivables",
              value = formatErpMoney(currency, kpiSummary.pendingReceivables),
              sublabel = "${kpiSummary.pendingCount} pending",
              icon = Icons.Filled.PendingActions,
              accent = MaterialTheme.colorScheme.tertiary,
            ),
            KpiSpec(
              label = "Expenses",
              value = formatErpMoney(currency, kpiSummary.totalExpenses),
              sublabel = "Recorded costs",
              icon = Icons.Filled.Payments,
              accent = MaterialTheme.colorScheme.error,
            ),
            KpiSpec(
              label = "Net Cashflow",
              value = formatErpMoney(currency, kpiSummary.netCashflow),
              sublabel = "${kpiSummary.totalEntriesCount} total records",
              icon = Icons.Filled.AccountBalanceWallet,
              accent = MaterialTheme.colorScheme.primary,
            ),
          )

        if (wide) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            cards.forEach { spec -> KpiMetricCard(spec = spec, modifier = Modifier.weight(1f)) }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              KpiMetricCard(spec = cards[0], modifier = Modifier.weight(1f))
              KpiMetricCard(spec = cards[1], modifier = Modifier.weight(1f))
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              KpiMetricCard(spec = cards[2], modifier = Modifier.weight(1f))
              KpiMetricCard(spec = cards[3], modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }

    // Minimal Quick Actions Card
    item {
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().testTag("overview_quick_actions_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (effectivePermissions.canCreateInvoices) {
              Button(
                onClick = onCreateInvoiceClick,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                  ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("overview_create_invoice_btn"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Invoice", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
            if (canCollectPayment) {
              FilledTonalButton(
                onClick = onReceivePaymentClick,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("overview_receive_payment_btn"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Payments,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Receive Payment", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (effectivePermissions.canAddQuickEntry) {
              OutlinedButton(
                onClick = onAddEntry,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("overview_add_entry_btn"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Add,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Quick Entry", maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
            OutlinedButton(
              onClick = onShareOverviewWhatsApp,
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("overview_whatsapp_share_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "WhatsApp",
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpenMenuDrawer)
                .padding(horizontal = 6.dp, vertical = 10.dp)
                .testTag("overview_open_drawer_btn"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "All Tools, Ledgers & Reports",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = "Party & Item Ledger • P&L • GST • Stock Valuation • AI Copilot",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = "Open tools menu",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    item { CashflowMixCard(kpiSummary = kpiSummary, currency = currency) }

    if (recentInvoices.isNotEmpty()) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isPlainNonGst) "Recent Plain Bills" else "Recent GST Bills",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          TextButton(onClick = { onNavigateTab(DashboardTab.BILLING) }) { Text("View All") }
        }
      }
      items(recentInvoices, key = { "ov_${it.id}" }) { inv ->
        Card(
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().clickable { onNavigateTab(DashboardTab.BILLING) },
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${inv.invoiceNumber} • ${inv.partyName}", style = MaterialTheme.typography.titleSmall)
              Text(
                "${inv.lineItems.size} items • ${inv.status.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = formatErpMoney(currency, inv.grandTotal),
              style = MonoMetricMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecutiveWorkspaceCard(
  profile: BusinessProfile?,
  profilesCount: Int,
  activeSlotId: String,
  isPlainNonGst: Boolean,
  isSalesmanSession: Boolean = false,
  allowedProfilesCount: Int = 3,
  kpiSummary: DashboardKpiSummary,
  currency: String,
  isLoading: Boolean,
  onOpenProfilesSheet: () -> Unit,
  onEditProfile: () -> Unit,
) {
  val target = profile?.monthlySalesTarget ?: 250000.0
  val achieved = kpiSummary.totalSales + kpiSummary.pendingReceivables
  val pct = (kpiSummary.targetProgress * 100).toInt()
  val slotBadgeText =
    when (activeSlotId) {
      BusinessProfile.SLOT_PLAIN_1 -> "PLAIN NON-GST"
      BusinessProfile.SLOT_MAIN_2 -> "MAIN GST #2"
      else -> "MAIN GST #1"
    }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .background(
          Brush.linearGradient(
            colors = listOf(Color(0xFF0B253C), Color(0xFF133A5C), Color(0xFF0D4A4E))
          )
        )
        .padding(20.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Surface(color = Color(0x3310B981), shape = RoundedCornerShape(50)) {
              Text(
                text = profile?.fiscalYearLabel ?: "FY 2026-27",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA7F3D0),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
            Surface(
              color = if (isPlainNonGst) Color(0x40F59E0B) else Color(0x26FFFFFF),
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = slotBadgeText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isPlainNonGst) Color(0xFFFDE68A) else Color(0xFFE2E8F0),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
            if (!isPlainNonGst && !profile?.gstin.isNullOrBlank()) {
              Surface(color = Color(0x26FFFFFF), shape = RoundedCornerShape(50)) {
                Text(
                  text = "GST: ${profile?.gstin}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFE2E8F0),
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (isLoading) "Loading workspace…" else (profile?.businessName ?: "Finboss Enterprise"),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
          )
          Text(
            text = "${profile?.businessType ?: "Retail & Wholesale"} • ${profile?.ownerName ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCBD5E1),
          )
        }

        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (!isSalesmanSession || allowedProfilesCount > 1) {
            Surface(
              color = Color(0x2E10B981),
              shape = MaterialTheme.shapes.small,
              modifier =
                Modifier.clickable(onClick = onOpenProfilesSheet).testTag("banner_switch_profile"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.SwapHoriz,
                  contentDescription = "Switch business profile",
                  tint = Color(0xFFA7F3D0),
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text =
                    if (isSalesmanSession) "Switch ($profilesCount)"
                    else "Switch ($profilesCount/3)",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFFA7F3D0),
                )
              }
            }
          }
          if (!isSalesmanSession) {
            Surface(
              color = Color(0x1FFFFFFF),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.clickable(onClick = onEditProfile).testTag("banner_edit_profile"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Edit,
                  contentDescription = stringResource(R.string.action_edit_profile),
                  tint = Color.White,
                  modifier = Modifier.size(16.dp),
                )
                Text(text = "Setup", style = MaterialTheme.typography.labelMedium, color = Color.White)
              }
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            text = "Monthly Billing Target ($pct%)",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFCBD5E1),
          )
          Text(
            text = "${formatErpMoney(currency, achieved)} / ${formatErpMoney(currency, target)}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFA7F3D0),
          )
        }
        LinearProgressIndicator(
          progress = { kpiSummary.targetProgress },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
          color = Color(0xFF10B981),
          trackColor = Color(0x33FFFFFF),
        )
      }
    }
  }
}

private data class KpiSpec(
  val label: String,
  val value: String,
  val sublabel: String,
  val icon: ImageVector,
  val accent: Color,
)

@Composable
private fun KpiMetricCard(spec: KpiSpec, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = spec.label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Box(
          modifier = Modifier.size(30.dp).clip(CircleShape).background(spec.accent.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(imageVector = spec.icon, contentDescription = null, tint = spec.accent, modifier = Modifier.size(17.dp))
        }
      }
      Text(
        text = spec.value,
        style = MonoMetricLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(text = spec.sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun CashflowMixCard(kpiSummary: DashboardKpiSummary, currency: String) {
  val totalVolume =
    (kpiSummary.totalSales + kpiSummary.pendingReceivables + kpiSummary.totalExpenses).coerceAtLeast(0.0)
  val collectedColor = MaterialTheme.colorScheme.secondary
  val pendingColor = MaterialTheme.colorScheme.tertiary
  val expenseColor = MaterialTheme.colorScheme.error
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "Cashflow Pulse", style = MaterialTheme.typography.titleMedium)
        Text(
          text = if (totalVolume > 0) "Live Mix" else "Awaiting Entries",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        drawRoundRect(
          color = trackColor,
          size = size,
          cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
        if (totalVolume > 0.0) {
          val w1 = ((kpiSummary.totalSales / totalVolume) * size.width).toFloat()
          val w2 = ((kpiSummary.pendingReceivables / totalVolume) * size.width).toFloat()
          val w3 = ((kpiSummary.totalExpenses / totalVolume) * size.width).toFloat()
          var offsetX = 0f
          if (w1 > 0f) {
            drawRect(color = collectedColor, topLeft = Offset(offsetX, 0f), size = Size(w1, size.height))
            offsetX += w1
          }
          if (w2 > 0f) {
            drawRect(color = pendingColor, topLeft = Offset(offsetX, 0f), size = Size(w2, size.height))
            offsetX += w2
          }
          if (w3 > 0f) {
            drawRect(color = expenseColor, topLeft = Offset(offsetX, 0f), size = Size(w3, size.height))
          }
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LegendDot(collectedColor, "Settled", formatErpMoney(currency, kpiSummary.totalSales))
        LegendDot(pendingColor, "Pending", formatErpMoney(currency, kpiSummary.pendingReceivables))
        LegendDot(expenseColor, "Expense", formatErpMoney(currency, kpiSummary.totalExpenses))
      }
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String, amount: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
    Column {
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = amount, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceTabContent(
  profile: BusinessProfile?,
  profilesCount: Int,
  activeSlotId: String,
  isPlainNonGst: Boolean,
  isSalesmanSession: Boolean,
  salesmanGrant: PhoneAccessGrant?,
  effectivePermissions: SalesmanPermissions,
  allowedProfileIds: List<String>,
  salesmen: List<SalesmanSeat>,
  modulesState: UiState<List<ErpModuleConfig>>,
  syncState: WorkspaceSyncState,
  appSettings: ErpAppSettings,
  isBusy: Boolean,
  userDisplayName: String,
  userEmail: String?,
  userPhotoUrl: String?,
  currency: String,
  subscriptionStatus: SubscriptionStatus = SubscriptionStatus.fromProfile(profile),
  onOpenSubscriptionPlans: () -> Unit = {},
  onManageProfiles: () -> Unit,
  onManageSalesmen: () -> Unit,
  onEditProfile: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onSyncNow: () -> Unit,
  onExportBackup: () -> Unit,
  onRestoreBackup: () -> Unit,
  onSelectModule: (ErpModuleConfig) -> Unit,
  onTogglePin: (ErpModuleConfig) -> Unit,
  onSignOut: () -> Unit,
) {
  val modules = (modulesState as? UiState.Success)?.data.orEmpty()
  val slotLabel =
    when (activeSlotId) {
      BusinessProfile.SLOT_MAIN_2 -> "Main GST Account 2"
      BusinessProfile.SLOT_PLAIN_1 -> "Plain Non-GST Account"
      else -> "Main GST Account 1"
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          UserAvatar(photoUrl = userPhotoUrl, size = 54.dp)
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (isSalesmanSession) {
                  salesmanGrant?.salesmanName?.ifBlank { userDisplayName } ?: userDisplayName
                } else {
                  profile?.ownerName ?: userDisplayName
                },
              style = MaterialTheme.typography.titleLarge,
            )
            if (!userEmail.isNullOrBlank()) {
              Text(
                text =
                  if (isSalesmanSession) {
                    "Mobile OTP Login: $userEmail • ${salesmanGrant?.roleLabel?.ifBlank { "Salesman" } ?: "Salesman"}"
                  } else {
                    userEmail
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
              Text(
                text =
                  if (isSalesmanSession) {
                    "Connected to ${profile?.businessName ?: "Owner Workspace"} • Live Sync"
                  } else if (syncState.isOnline) {
                    "Google Authenticated & Cloud Synced"
                  } else {
                    "Offline Mode • Cached Locally"
                  },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }
      }
    }

    if (!isSalesmanSession) {
      // Subscription & Plan Access Card (30-Day Free Trial + Silver ₹300, Gold ₹500, Diamond ₹700)
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().testTag("workspace_subscription_plan_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "Subscription & Plan (${subscriptionStatus.topBarBadgeText})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text(
                  text = subscriptionStatus.statusSummaryText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenSubscriptionPlans,
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace_manage_subscription_button"),
              ) {
                Text("Plans & Upgrade")
              }
            }
            Text(
              text = "Silver ₹300/mo • Gold ₹500/mo (10% Off Yearly: ₹5,400/yr) • Diamond ₹700/mo (20% Off Yearly: ₹6,720/yr)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
      // Owner Salesman Team & OTP Access Card (Max 5 Seats)
      item {
        val activeCount = remember(salesmen) { salesmen.count { it.isActive } }
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().testTag("workspace_salesman_team_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "Salesman Team & OTP Access (${salesmen.size}/${SalesmanSeatConfig.MAX_SALESMEN} Seats)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Text(
                  text =
                    if (salesmen.isEmpty()) {
                      "Add up to 5 salesmen by Mobile Number (OTP login) and configure exact permissions & profiles."
                    } else {
                      "$activeCount active of ${salesmen.size} configured salesmen • Owner-controlled permissions"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onManageSalesmen,
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace_manage_team_button"),
              ) {
                Text("Manage Team")
              }
            }

            if (salesmen.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                salesmen.forEach { seat ->
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color =
                      if (seat.isActive) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant,
                  ) {
                    Text(
                      text = "${seat.name} • ${seat.phoneNumber}${if (!seat.isActive) " (Paused)" else ""}",
                      style = MaterialTheme.typography.labelSmall,
                      color =
                        if (seat.isActive) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }

      item {
        BackupSyncCard(
          syncState = syncState,
          isBusy = isBusy,
          onSyncNow = onSyncNow,
          onExportBackup = onExportBackup,
          onRestoreBackup = onRestoreBackup,
        )
      }

      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(text = "App Logic & Print Settings", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "${appSettings.defaultPaperSize.label} • ${appSettings.defaultPrintStyle.label} • Prefix ${appSettings.invoicePrefix}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(
              onClick = onOpenAppSettings,
              modifier = Modifier.heightIn(min = 48.dp).testTag("workspace_open_settings_button"),
            ) {
              Text("Configure")
            }
          }
        }
      }
    } else {
      // Salesman Access Summary Card
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().testTag("salesman_permissions_summary_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "Your Assigned Salesman Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            Text(
              text = "Configured by Business Owner • Seat ${salesmanGrant?.seatId?.removePrefix("seat_") ?: "1"} of 5",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val badges = buildList {
                if (effectivePermissions.canCreateInvoices) add("Create Bills")
                else if (effectivePermissions.canViewBilling) add("View Bills")
                if (effectivePermissions.canPrintInvoices) add("Print / PDF")
                if (effectivePermissions.canEditDeleteInvoices) add("Edit / Delete Bills")
                if (effectivePermissions.canManageStock) add("Manage Stock & Purchase")
                else if (effectivePermissions.canViewStock) add("View Stock")
                if (effectivePermissions.canManageParties) add("Manage Party Ledger")
                else if (effectivePermissions.canViewParties) add("View Parties")
                if (effectivePermissions.canManageExpenses) add("Manage Expenses")
                else if (effectivePermissions.canViewExpenses) add("View Cashbook")
                if (effectivePermissions.canViewReports) add("GST & P&L Reports")
                if (effectivePermissions.canAddQuickEntry) add("Quick Cashbook Entry")
              }
              badges.forEach { badge ->
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                  Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    if (!isSalesmanSession || allowedProfileIds.size > 1) {
      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (isSalesmanSession) "Allowed Business Profiles ($profilesCount)"
                    else "Business Profiles ($profilesCount/3 Accounts)",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "Active: $slotLabel • ${if (isPlainNonGst) "Plain Non-GST Bills" else "Main GST Full ERP"}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onManageProfiles,
                modifier = Modifier.heightIn(min = 48.dp).testTag("workspace_manage_profiles_button"),
              ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSalesmanSession) "Switch" else "Manage")
              }
            }
          }
        }
      }
    }

    item {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text(text = "Business Configuration", style = MaterialTheme.typography.titleMedium)
            }
            if (!isSalesmanSession) {
              OutlinedButton(onClick = onEditProfile, modifier = Modifier.testTag("workspace_edit_profile_button")) {
                Text("Edit")
              }
            }
          }
          WorkspaceInfoRow("Account Slot", slotLabel)
          WorkspaceInfoRow("Mode", if (isPlainNonGst) "Plain Non-GST (0% Tax)" else "Main GST • All ERP")
          WorkspaceInfoRow("Business Name", profile?.businessName ?: "Not configured")
          WorkspaceInfoRow("Sector", profile?.businessType ?: "Retail & Wholesale")
          val fullBizAddress =
            InvoicePdfPrinter.buildAddressLine(
              profile?.address.orEmpty(),
              profile?.state.orEmpty(),
              profile?.pincode.orEmpty(),
            )
          if (fullBizAddress.isNotBlank()) {
            WorkspaceInfoRow("Address & State", fullBizAddress)
          }
          if (!isPlainNonGst) {
            WorkspaceInfoRow("GSTIN / Tax ID", profile?.gstin?.ifBlank { "Not added" } ?: "Not added")
          }
          WorkspaceInfoRow("Fiscal Year", profile?.fiscalYearLabel ?: "FY 2026-27")
          WorkspaceInfoRow("Currency", currency)
          WorkspaceInfoRow("Monthly Target", formatErpMoney(currency, profile?.monthlySalesTarget ?: 250000.0))
        }
      }
    }

    if (!isSalesmanSession) {
      item {
        Text(
          text = "Finboss Modules Roadmap",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      items(modules, key = { it.id }) { module ->
        Card(
          modifier = Modifier.fillMaxWidth().clickable { onSelectModule(module) },
          shape = MaterialTheme.shapes.medium,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(imageVector = moduleIcon(module.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              ModuleStatusBadge(status = module.status)
              Text(text = module.title, style = MaterialTheme.typography.titleMedium)
              Text(text = module.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onTogglePin(module) }) {
              Icon(
                imageVector = if (module.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (module.isPinned) "Unpin module" else "Pin module",
                tint = if (module.isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    item {
      Button(
        onClick = onSignOut,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error,
          ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button"),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.action_sign_out), fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun WorkspaceInfoRow(label: String, value: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.small)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(text = value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
private fun ErrorRetryCard(message: String, onRetry: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
      TextButton(onClick = onRetry) { Text("Retry") }
    }
  }
}

@Composable
private fun UserAvatar(photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
  val context = LocalContext.current
  val model =
    remember(context, photoUrl) {
      ImageRequest.Builder(context)
        .data(photoUrl?.takeIf { it.isNotBlank() } ?: R.drawable.img_business_emblem)
        .placeholder(R.drawable.img_business_emblem)
        .error(R.drawable.img_business_emblem)
        .crossfade(false)
        .build()
    }
  AsyncImage(
    model = model,
    contentDescription = stringResource(R.string.cd_user_avatar),
    modifier = Modifier.size(size).clip(CircleShape),
    contentScale = ContentScale.Crop,
  )
}

private fun tabIcon(tab: DashboardTab, selected: Boolean): ImageVector =
  when (tab) {
    DashboardTab.OVERVIEW -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    DashboardTab.BILLING ->
      if (selected) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong
    DashboardTab.STOCK -> if (selected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
    DashboardTab.PARTIES -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
    DashboardTab.EXPENSES -> if (selected) Icons.Filled.Payments else Icons.Outlined.Payments
    DashboardTab.REPORTS -> if (selected) Icons.Filled.Assessment else Icons.Outlined.Assessment
    DashboardTab.AI_COPILOT -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    DashboardTab.WORKSPACE -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
  }

private fun tabLabel(tab: DashboardTab): String =
  when (tab) {
    DashboardTab.OVERVIEW -> "Home"
    DashboardTab.BILLING -> "Bills"
    DashboardTab.STOCK -> "Stock"
    DashboardTab.PARTIES -> "Ledger"
    DashboardTab.EXPENSES -> "Expenses"
    DashboardTab.REPORTS -> "Reports"
    DashboardTab.AI_COPILOT -> "Finboss AI"
    DashboardTab.WORKSPACE -> "Workspace"
  }

private fun moduleIcon(code: String): ImageVector =
  when (code) {
    "dashboard" -> Icons.Filled.Dashboard
    "billing" -> Icons.AutoMirrored.Filled.ReceiptLong
    "inventory" -> Icons.Filled.Inventory2
    "parties" -> Icons.Filled.Groups
    "expenses" -> Icons.Filled.Payments
    "gst_reports" -> Icons.Filled.Assessment
    else -> Icons.Filled.Schedule
  }
