package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BusinessProfile
import com.example.data.model.DashboardEntry
import com.example.data.model.ErpExpense
import com.example.data.model.ErpModuleConfig
import com.example.data.model.ErpParty
import com.example.data.model.GstInvoice
import com.example.data.model.InventoryItem
import com.example.data.model.InvoiceLineItem
import com.example.data.model.PhoneAccessGrant
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SalesmanSeat
import com.example.data.model.SubscriptionStatus
import com.example.data.model.SubscriptionTier
import com.example.data.reference.EInvoiceGenerationResult
import com.example.data.reference.EWayBillGenerationResult
import com.example.data.repository.ErpDashboardRepository
import com.example.data.repository.ErpOperationsRepository
import com.example.data.repository.TeamRepository
import com.example.data.sync.ErpAppSettings
import com.example.data.sync.ErpSettingsStore
import com.example.data.sync.FinbossBackupPayload
import com.example.data.sync.FinbossBackupSerializer
import com.example.data.sync.SyncMetadataSummary
import com.example.data.sync.SyncPreferences
import com.example.data.sync.SyncStatusTracker
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

data class DashboardKpiSummary(
  val totalSales: Double = 0.0,
  val pendingReceivables: Double = 0.0,
  val totalExpenses: Double = 0.0,
  val netCashflow: Double = 0.0,
  val totalEntriesCount: Int = 0,
  val pendingCount: Int = 0,
  val targetProgress: Float = 0f,
  val invoiceCount: Int = 0,
  val lowStockCount: Int = 0,
  val stockValuation: Double = 0.0,
  val partyCount: Int = 0,
  val expenseCount: Int = 0,
  val netGstPayable: Double = 0.0,
)

data class WorkspaceSyncState(
  val isOnline: Boolean = true,
  val hasPendingWrites: Boolean = false,
  val pendingCollectionsCount: Int = 0,
  val isFromCacheOnly: Boolean = false,
  val lastCloudSyncMs: Long = 0L,
  val lastLocalBackupMs: Long = 0L,
  val isSyncingNow: Boolean = false,
)

class DashboardViewModel(
  private val repository: ErpDashboardRepository,
  private val operationsRepository: ErpOperationsRepository,
  private val currentUserId: String,
  private val initialOwnerName: String,
  private val initialOwnerEmail: String?,
  private val syncTracker: SyncStatusTracker? = null,
  private val syncPrefs: SyncPreferences? = null,
  private val settingsStore: ErpSettingsStore? = null,
  networkStatusFlow: Flow<Boolean>? = null,
  private val teamRepository: TeamRepository? = null,
  val workspaceOwnerUid: String = currentUserId,
  val isSalesmanSession: Boolean = false,
  val salesmanPhone: String? = null,
  initialSalesmanGrant: PhoneAccessGrant? = null,
) : ViewModel() {

  private val fallbackSettingsFlow = MutableStateFlow(ErpAppSettings())
  val appSettings: StateFlow<ErpAppSettings> =
    settingsStore?.settings ?: fallbackSettingsFlow.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  fun clearSalesmanSessionOnSignOut() {
    syncPrefs?.setSalesmanSessionPhone(currentUserId, null)
  }

  val activeSalesmanGrant: StateFlow<PhoneAccessGrant?> =
    if (isSalesmanSession && teamRepository != null && !salesmanPhone.isNullOrBlank()) {
      teamRepository
        .observeMyPhoneAccess(salesmanPhone)
        .catch { emit(initialSalesmanGrant) }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          initialValue = initialSalesmanGrant,
        )
    } else {
      MutableStateFlow(initialSalesmanGrant).asStateFlow()
    }

  val effectivePermissions: StateFlow<SalesmanPermissions> =
    activeSalesmanGrant
      .map { grant ->
        if (!isSalesmanSession) {
          SalesmanPermissions.OWNER_FULL
        } else {
          grant?.permissions ?: SalesmanPermissions()
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue =
          if (!isSalesmanSession) SalesmanPermissions.OWNER_FULL
          else initialSalesmanGrant?.permissions ?: SalesmanPermissions(),
      )

  val allowedProfileIds: StateFlow<List<String>> =
    activeSalesmanGrant
      .map { grant ->
        if (!isSalesmanSession) {
          BusinessProfile.ALL_SLOTS
        } else {
          grant
            ?.allowedProfiles
            ?.filter { it in BusinessProfile.ALL_SLOTS }
            ?.distinct()
            ?.ifEmpty { listOf(BusinessProfile.SLOT_MAIN_1) }
            ?: listOf(BusinessProfile.SLOT_MAIN_1)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue =
          if (!isSalesmanSession) BusinessProfile.ALL_SLOTS
          else
            initialSalesmanGrant
              ?.allowedProfiles
              ?.filter { it in BusinessProfile.ALL_SLOTS }
              ?.distinct()
              ?.ifEmpty { listOf(BusinessProfile.SLOT_MAIN_1) }
              ?: listOf(BusinessProfile.SLOT_MAIN_1),
      )

  val salesmenState: StateFlow<UiState<List<SalesmanSeat>>> =
    if (!isSalesmanSession && teamRepository != null) {
      teamRepository
        .observeSalesmen(workspaceOwnerUid)
        .map<List<SalesmanSeat>, UiState<List<SalesmanSeat>>> { UiState.Success(it) }
        .catch { error ->
          emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load salesman seats")))
        }
        .stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
          UiState.Loading,
        )
    } else {
      MutableStateFlow<UiState<List<SalesmanSeat>>>(UiState.Success(emptyList())).asStateFlow()
    }

  private val _activeProfileId =
    MutableStateFlow(
      run {
        val preferred = syncPrefs?.getActiveProfileId(currentUserId) ?: BusinessProfile.SLOT_MAIN_1
        val initialAllowed = allowedProfileIds.value
        if (preferred in initialAllowed) preferred else initialAllowed.firstOrNull() ?: BusinessProfile.SLOT_MAIN_1
      }
    )
  val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

  private val _isSyncingNow = MutableStateFlow(false)
  private val _lastCloudSyncMs =
    MutableStateFlow(syncPrefs?.getLastCloudSyncMs(currentUserId) ?: 0L)
  private val _lastLocalBackupMs =
    MutableStateFlow(syncPrefs?.getLastLocalBackupMs(currentUserId) ?: 0L)

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  val syncState: StateFlow<WorkspaceSyncState> =
    combine(
        networkStatusFlow ?: flowOf(true),
        syncTracker?.state ?: flowOf(SyncMetadataSummary()),
        _isSyncingNow,
        _lastCloudSyncMs,
        _lastLocalBackupMs,
      ) { isOnline, meta, isSyncingNow, savedCloudSyncMs, savedLocalBackupMs ->
        val latestConfirmed =
          if (isOnline && !meta.hasPendingWrites && meta.lastServerConfirmedAtMs > savedCloudSyncMs) {
            syncPrefs?.setLastCloudSyncMs(currentUserId, meta.lastServerConfirmedAtMs)
            meta.lastServerConfirmedAtMs
          } else {
            savedCloudSyncMs
          }
        WorkspaceSyncState(
          isOnline = isOnline,
          hasPendingWrites = meta.hasPendingWrites,
          pendingCollectionsCount = meta.pendingCollectionsCount,
          isFromCacheOnly = meta.isFromCacheOnly,
          lastCloudSyncMs = latestConfirmed,
          lastLocalBackupMs = savedLocalBackupMs,
          isSyncingNow = isSyncingNow,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue =
          WorkspaceSyncState(
            lastCloudSyncMs = syncPrefs?.getLastCloudSyncMs(currentUserId) ?: 0L,
            lastLocalBackupMs = syncPrefs?.getLastLocalBackupMs(currentUserId) ?: 0L,
          ),
      )

  private val rawProfilesState: StateFlow<UiState<List<BusinessProfile>>> =
    repository
      .observeBusinessProfiles()
      .map<List<BusinessProfile>, UiState<List<BusinessProfile>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load business profiles"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val profilesState: StateFlow<UiState<List<BusinessProfile>>> =
    combine(rawProfilesState, allowedProfileIds) { state, allowed ->
        when (state) {
          is UiState.Success -> {
            val filtered = state.data.filter { it.id in allowed }
            UiState.Success(filtered)
          }
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val legacyProfileState: StateFlow<UiState<BusinessProfile?>> =
    repository
      .observeBusinessProfile()
      .map<BusinessProfile?, UiState<BusinessProfile?>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load business profile"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val profileState: StateFlow<UiState<BusinessProfile?>> =
    combine(profilesState, legacyProfileState, _activeProfileId) { slotsState, legacyState, activeId ->
        when (slotsState) {
          is UiState.Success -> {
            val slots = slotsState.data
            if (slots.isNotEmpty()) {
              val matched =
                slots.firstOrNull { it.id == activeId }
                  ?: slots.firstOrNull { it.id == BusinessProfile.SLOT_MAIN_1 }
                  ?: slots.first()
              UiState.Success(matched)
            } else {
              legacyState
            }
          }
          is UiState.Error -> legacyState
          is UiState.Loading ->
            if (legacyState is UiState.Success && legacyState.data != null) legacyState
            else UiState.Loading
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val subscriptionStatus: StateFlow<SubscriptionStatus> =
    combine(legacyProfileState, profileState) { rootState, activeState ->
        val rootProfile = (rootState as? UiState.Success)?.data
        val activeProfile = (activeState as? UiState.Success)?.data
        SubscriptionStatus.fromProfile(rootProfile ?: activeProfile)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SubscriptionStatus.fromProfile(null),
      )

  val modulesState: StateFlow<UiState<List<ErpModuleConfig>>> =
    repository
      .observeModules()
      .map<List<ErpModuleConfig>, UiState<List<ErpModuleConfig>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load ERP modules"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val rawEntriesState: StateFlow<UiState<List<DashboardEntry>>> =
    repository
      .observeDashboardEntries()
      .map<List<DashboardEntry>, UiState<List<DashboardEntry>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load dashboard entries"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val rawPartiesState: StateFlow<UiState<List<ErpParty>>> =
    operationsRepository
      .observeParties()
      .map<List<ErpParty>, UiState<List<ErpParty>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load parties"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val rawInventoryState: StateFlow<UiState<List<InventoryItem>>> =
    operationsRepository
      .observeInventory()
      .map<List<InventoryItem>, UiState<List<InventoryItem>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load inventory"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val rawInvoicesState: StateFlow<UiState<List<GstInvoice>>> =
    operationsRepository
      .observeInvoices()
      .map<List<GstInvoice>, UiState<List<GstInvoice>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load invoices"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  private val rawExpensesState: StateFlow<UiState<List<ErpExpense>>> =
    operationsRepository
      .observeExpenses()
      .map<List<ErpExpense>, UiState<List<ErpExpense>>> { UiState.Success(it) }
      .catch { error -> emit(UiState.Error(unwrapFirestoreMessage(error, "Failed to load expenses"))) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val entriesState: StateFlow<UiState<List<DashboardEntry>>> =
    combine(rawEntriesState, _activeProfileId) { state, activeId ->
        when (state) {
          is UiState.Success -> UiState.Success(state.data.filter { it.resolvedProfileId() == activeId })
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val partiesState: StateFlow<UiState<List<ErpParty>>> =
    combine(rawPartiesState, _activeProfileId) { state, activeId ->
        when (state) {
          is UiState.Success -> UiState.Success(state.data.filter { it.resolvedProfileId() == activeId })
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val inventoryState: StateFlow<UiState<List<InventoryItem>>> =
    combine(rawInventoryState, _activeProfileId) { state, activeId ->
        when (state) {
          is UiState.Success -> UiState.Success(state.data.filter { it.resolvedProfileId() == activeId })
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val invoicesState: StateFlow<UiState<List<GstInvoice>>> =
    combine(rawInvoicesState, _activeProfileId) { state, activeId ->
        when (state) {
          is UiState.Success -> UiState.Success(state.data.filter { it.resolvedProfileId() == activeId })
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val expensesState: StateFlow<UiState<List<ErpExpense>>> =
    combine(rawExpensesState, _activeProfileId) { state, activeId ->
        when (state) {
          is UiState.Success -> UiState.Success(state.data.filter { it.resolvedProfileId() == activeId })
          else -> state
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

  val kpiSummary: StateFlow<DashboardKpiSummary> =
    combine(
        combine(profileState, entriesState, invoicesState) { p, e, i -> Triple(p, e, i) },
        combine(inventoryState, partiesState, expensesState) { s, pa, ex -> Triple(s, pa, ex) },
      ) { (profState, entState, invState), (stockState, partyState, expState) ->
        val profile = (profState as? UiState.Success)?.data
        val entries = (entState as? UiState.Success)?.data.orEmpty()
        val invoices = (invState as? UiState.Success)?.data.orEmpty()
        val inventory = (stockState as? UiState.Success)?.data.orEmpty()
        val parties = (partyState as? UiState.Success)?.data.orEmpty()
        val expenses = (expState as? UiState.Success)?.data.orEmpty()
        computeKpis(profile, entries, invoices, inventory, parties, expenses)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardKpiSummary(),
      )

  init {
    if (!isSalesmanSession) {
      initializeWorkspace()
    } else {
      viewModelScope.launch {
        allowedProfileIds.collect { allowed ->
          val current = _activeProfileId.value
          if (current !in allowed && allowed.isNotEmpty()) {
            _activeProfileId.value = allowed.first()
          }
        }
      }
    }
  }

  fun initializeWorkspace() {
    if (isSalesmanSession) return
    viewModelScope.launch {
      _isBusy.value = true
      yield()
      val result =
        withContext(Dispatchers.IO) {
          repository.ensureWorkspaceInitialized(initialOwnerName, initialOwnerEmail)
        }
      result.onFailure { err ->
        _statusBanner.value = err.localizedMessage ?: "Unable to initialize ERP workspace"
      }
      _isBusy.value = false
    }
  }

  fun switchActiveProfile(slotId: String) {
    if (slotId !in BusinessProfile.ALL_SLOTS) return
    if (slotId !in allowedProfileIds.value) {
      _statusBanner.value = "Access to this business profile is restricted by the owner"
      return
    }
    val profiles = (profilesState.value as? UiState.Success)?.data.orEmpty()
    val target = profiles.firstOrNull { it.id == slotId } ?: return
    _activeProfileId.value = slotId
    syncPrefs?.setActiveProfileId(currentUserId, slotId)
    val modeLabel = if (target.isPlainNonGst) "Plain Non-GST" else "Main GST"
    _statusBanner.value = "Switched to ${target.businessName} ($modeLabel)"
  }

  fun saveSalesmanSeat(
    seatId: String,
    existingSeat: SalesmanSeat?,
    name: String,
    rawPhoneE164: String,
    roleLabel: String,
    isActive: Boolean,
    allowedProfiles: List<String>,
    permissions: SalesmanPermissions,
    onDone: () -> Unit = {},
  ) {
    val repo = teamRepository
    if (isSalesmanSession || repo == null) {
      _statusBanner.value = "Only the business owner can manage salesman accounts"
      return
    }
    val allSeats = (salesmenState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        withContext(Dispatchers.IO) {
          repo.saveSalesmanSeat(
            ownerUid = workspaceOwnerUid,
            seatId = seatId,
            existingSeat = existingSeat,
            allSeats = allSeats,
            name = name,
            rawPhoneE164 = rawPhoneE164,
            roleLabel = roleLabel,
            isActive = isActive,
            allowedProfiles = allowedProfiles,
            permissions = permissions,
          )
        }
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value =
            if (existingSeat != null) "Updated salesman access for ${name.trim()}"
            else "Added salesman ${name.trim()} (${rawPhoneE164.trim()})"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to save salesman account"
        }
    }
  }

  fun toggleSalesmanActive(seat: SalesmanSeat, isActive: Boolean) {
    val repo = teamRepository
    if (isSalesmanSession || repo == null) return
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        withContext(Dispatchers.IO) {
          repo.toggleSalesmanActive(workspaceOwnerUid, seat, isActive)
        }
      _isBusy.value = false
      res
        .onSuccess {
          val stateText = if (isActive) "activated" else "paused"
          _statusBanner.value = "Salesman ${seat.name} $stateText"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to update salesman status"
        }
    }
  }

  fun deleteSalesmanSeat(seat: SalesmanSeat) {
    val repo = teamRepository
    if (isSalesmanSession || repo == null) return
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        withContext(Dispatchers.IO) {
          repo.deleteSalesmanSeat(workspaceOwnerUid, seat)
        }
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "Removed salesman ${seat.name} (${seat.phoneNumber})"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to remove salesman"
        }
    }
  }

  fun saveBusinessProfile(
    ownerName: String,
    businessName: String,
    gstin: String,
    currencySymbol: String,
    fiscalYearLabel: String,
    businessType: String,
    monthlySalesTarget: Double,
    address: String = "",
    state: String = "",
    pincode: String = "",
    onDone: () -> Unit,
  ) {
    saveProfileAccount(
      slotId = _activeProfileId.value,
      ownerName = ownerName,
      businessName = businessName,
      gstin = gstin,
      currencySymbol = currencySymbol,
      fiscalYearLabel = fiscalYearLabel,
      businessType = businessType,
      monthlySalesTarget = monthlySalesTarget,
      address = address,
      state = state,
      pincode = pincode,
      switchToAfterSave = false,
      onDone = onDone,
    )
  }

  fun saveProfileAccount(
    slotId: String,
    ownerName: String,
    businessName: String,
    gstin: String,
    currencySymbol: String,
    fiscalYearLabel: String,
    businessType: String,
    monthlySalesTarget: Double,
    address: String = "",
    state: String = "",
    pincode: String = "",
    switchToAfterSave: Boolean = true,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val result =
        repository.saveBusinessProfileAccount(
          slotId = slotId,
          ownerName = ownerName,
          businessName = businessName,
          gstin = gstin,
          currencySymbol = currencySymbol,
          fiscalYearLabel = fiscalYearLabel,
          businessType = businessType,
          monthlySalesTarget = monthlySalesTarget,
          address = address,
          state = state,
          pincode = pincode,
        )
      _isBusy.value = false
      result
        .onSuccess {
          if (switchToAfterSave) {
            _activeProfileId.value = slotId
            syncPrefs?.setActiveProfileId(currentUserId, slotId)
          }
          val modeLabel =
            if (slotId == BusinessProfile.SLOT_PLAIN_1) "Plain Non-GST account saved"
            else "Business profile saved"
          _statusBanner.value = modeLabel
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to save profile" }
    }
  }

  fun deleteProfileAccount(slotId: String, onDone: () -> Unit = {}) {
    if (slotId == BusinessProfile.SLOT_MAIN_1) {
      _statusBanner.value = "Primary Main GST profile cannot be deleted"
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.deleteBusinessProfileAccount(slotId)
      _isBusy.value = false
      res
        .onSuccess {
          if (_activeProfileId.value == slotId) {
            _activeProfileId.value = BusinessProfile.SLOT_MAIN_1
            syncPrefs?.setActiveProfileId(currentUserId, BusinessProfile.SLOT_MAIN_1)
          }
          _statusBanner.value = "Business profile removed"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to remove profile" }
    }
  }

  fun activateSubscriptionPlan(
    tier: SubscriptionTier,
    billingCycle: String,
    onDone: () -> Unit = {},
  ) {
    if (isSalesmanSession) {
      _statusBanner.value = "Only the business owner can change subscription plans"
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        withContext(Dispatchers.IO) {
          repository.activateSubscriptionPlan(tier, billingCycle)
        }
      _isBusy.value = false
      res
        .onSuccess {
          val cycleLabel =
            when (billingCycle.lowercase()) {
              SubscriptionTier.CYCLE_YEARLY -> "Yearly"
              SubscriptionTier.CYCLE_TRIAL -> "30-Day Free Trial"
              else -> "Monthly"
            }
          _statusBanner.value = "${tier.displayName} ($cycleLabel) activated!"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to activate subscription plan"
        }
    }
  }

  fun toggleModulePin(module: ErpModuleConfig) {
    viewModelScope.launch {
      repository.toggleModulePin(module).onFailure { err ->
        _statusBanner.value = err.localizedMessage ?: "Failed to update module pin"
      }
    }
  }

  fun setModuleQueued(module: ErpModuleConfig, queued: Boolean) {
    val targetStatus =
      if (queued) ErpModuleConfig.STATUS_QUEUED else ErpModuleConfig.STATUS_PLANNED
    viewModelScope.launch {
      repository
        .setModuleStatus(module, targetStatus)
        .onSuccess {
          _statusBanner.value =
            if (queued) "${module.title} prioritized for next rollout"
            else "${module.title} moved to planned roadmap"
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to update status" }
    }
  }

  fun addDashboardEntry(
    title: String,
    partyName: String,
    category: String,
    amount: Double,
    status: String,
    referenceCode: String,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val result =
        repository.addDashboardEntry(
          title = title,
          partyName = partyName,
          category = category,
          amount = amount,
          status = status,
          referenceCode = referenceCode,
          businessProfileId = _activeProfileId.value,
        )
      _isBusy.value = false
      result
        .onSuccess {
          _statusBanner.value = "Ledger entry added"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to save entry" }
    }
  }

  fun markEntryPaid(entryId: String) {
    viewModelScope.launch {
      repository
        .markEntryPaid(entryId)
        .onSuccess { _statusBanner.value = "Marked as settled" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to settle entry" }
    }
  }

  fun deleteDashboardEntry(entryId: String) {
    viewModelScope.launch {
      repository
        .deleteDashboardEntry(entryId)
        .onSuccess { _statusBanner.value = "Entry removed" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to delete entry" }
    }
  }

  // --- Parties & Khata Actions ---

  fun saveParty(
    existingId: String?,
    name: String,
    partyType: String,
    phone: String,
    gstin: String,
    openingDue: Double,
    creditLimit: Double,
    notes: String,
    address: String = "",
    state: String = "",
    pincode: String = "",
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.saveParty(
          existingId = existingId,
          name = name,
          partyType = partyType,
          phone = phone,
          gstin = gstin,
          openingDue = openingDue,
          creditLimit = creditLimit,
          notes = notes,
          businessProfileId = _activeProfileId.value,
          address = address,
          state = state,
          pincode = pincode,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = if (existingId == null) "Party added to Ledger" else "Party updated"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to save party" }
    }
  }

  fun recordPartyPayment(party: ErpParty, amount: Double, onDone: () -> Unit) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = operationsRepository.recordPartyPayment(party, amount)
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "Party Ledger payment recorded"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Payment failed" }
    }
  }

  fun deleteParty(partyId: String) {
    viewModelScope.launch {
      operationsRepository
        .deleteParty(partyId)
        .onSuccess { _statusBanner.value = "Party removed" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Delete failed" }
    }
  }

  // --- Inventory & Stock Actions ---

  fun saveInventoryItem(
    existingId: String?,
    name: String,
    sku: String,
    category: String,
    unit: String,
    salePrice: Double,
    purchasePrice: Double,
    gstRatePercent: Double,
    stockQty: Double,
    lowStockThreshold: Double,
    hsnCode: String = "",
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.saveInventoryItem(
          existingId = existingId,
          name = name,
          sku = sku,
          category = category,
          unit = unit,
          salePrice = salePrice,
          purchasePrice = purchasePrice,
          gstRatePercent = if (_activeProfileId.value == BusinessProfile.SLOT_PLAIN_1) 0.0 else gstRatePercent,
          stockQty = stockQty,
          lowStockThreshold = lowStockThreshold,
          businessProfileId = _activeProfileId.value,
          hsnCode = hsnCode,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = if (existingId == null) "Item added to stock" else "Stock item updated"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to save item" }
    }
  }

  fun recordPurchase(
    existingItem: InventoryItem?,
    newItemName: String,
    newItemSku: String,
    hsnCode: String,
    category: String,
    unit: String,
    purchasePrice: Double,
    salePrice: Double,
    gstRatePercent: Double,
    purchaseQty: Double,
    lowStockThreshold: Double,
    supplier: ErpParty?,
    supplierNameFallback: String,
    billNumber: String,
    paymentMethod: String,
    isPaidNow: Boolean,
    onDone: () -> Unit,
  ) {
    val activeSlot = _activeProfileId.value
    val isPlain = activeSlot == BusinessProfile.SLOT_PLAIN_1
    val recordInExpenses = appSettings.value.autoRecordPurchaseInExpenses
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.recordPurchase(
          existingItem = existingItem,
          newItemName = newItemName,
          newItemSku = newItemSku,
          hsnCode = if (isPlain) "" else hsnCode,
          category = category,
          unit = unit,
          purchasePrice = purchasePrice,
          salePrice = salePrice,
          gstRatePercent = if (isPlain) 0.0 else gstRatePercent,
          purchaseQty = purchaseQty,
          lowStockThreshold = lowStockThreshold,
          supplier = supplier,
          supplierNameFallback = supplierNameFallback,
          billNumber = billNumber,
          paymentMethod = paymentMethod,
          isPaidNow = isPaidNow,
          recordInExpenses = recordInExpenses,
          businessProfileId = activeSlot,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value =
            if (recordInExpenses) "Purchase stock-in & expense ITC recorded"
            else "Purchase stock-in updated"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Purchase failed" }
    }
  }

  fun adjustInventoryStock(item: InventoryItem, deltaQty: Double, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      operationsRepository
        .adjustInventoryStock(item, deltaQty)
        .onSuccess {
          _statusBanner.value = "Stock updated for ${item.name}"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Stock update failed" }
    }
  }

  fun deleteInventoryItem(itemId: String) {
    viewModelScope.launch {
      operationsRepository
        .deleteInventoryItem(itemId)
        .onSuccess { _statusBanner.value = "Item removed" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Delete failed" }
    }
  }

  // --- Billing & GST Invoices Actions ---

  fun createInvoice(
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    walkInPartyAddress: String = "",
    walkInPartyState: String = "",
    walkInPartyPincode: String = "",
    onDone: () -> Unit,
  ) {
    val currentInventory = (inventoryState.value as? UiState.Success)?.data.orEmpty()
    val activeSlot = _activeProfileId.value
    val isPlain = activeSlot == BusinessProfile.SLOT_PLAIN_1
    val autoDeduct = appSettings.value.autoDeductStockOnSale
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.createInvoice(
          invoiceNumber = invoiceNumber,
          party = party,
          walkInPartyName = walkInPartyName,
          walkInPartyGstin = if (isPlain) "" else walkInPartyGstin,
          invoiceType = invoiceType,
          taxMode = if (isPlain) GstInvoice.TAX_MODE_NONE else taxMode,
          paymentMethod = paymentMethod,
          discountAmount = discountAmount,
          paidAmount = paidAmount,
          notes = notes,
          lineItems = lineItems,
          inventorySnapshot = currentInventory,
          businessProfileId = activeSlot,
          walkInPartyAddress = walkInPartyAddress,
          walkInPartyState = walkInPartyState,
          walkInPartyPincode = walkInPartyPincode,
          autoDeductStock = autoDeduct,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "Invoice $invoiceNumber created"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to create invoice" }
    }
  }

  fun updateInvoice(
    existingInvoice: GstInvoice,
    invoiceNumber: String,
    party: ErpParty?,
    walkInPartyName: String,
    walkInPartyGstin: String,
    invoiceType: String,
    taxMode: String,
    paymentMethod: String,
    discountAmount: Double,
    paidAmount: Double,
    notes: String,
    lineItems: List<InvoiceLineItem>,
    walkInPartyAddress: String = "",
    walkInPartyState: String = "",
    walkInPartyPincode: String = "",
    onDone: () -> Unit,
  ) {
    val currentInventory = (inventoryState.value as? UiState.Success)?.data.orEmpty()
    val currentParties = (partiesState.value as? UiState.Success)?.data.orEmpty()
    val autoDeduct = appSettings.value.autoDeductStockOnSale
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.updateInvoice(
          existingInvoice = existingInvoice,
          invoiceNumber = invoiceNumber,
          party = party,
          walkInPartyName = walkInPartyName,
          walkInPartyGstin = walkInPartyGstin,
          invoiceType = invoiceType,
          taxMode = taxMode,
          paymentMethod = paymentMethod,
          discountAmount = discountAmount,
          paidAmount = paidAmount,
          notes = notes,
          lineItems = lineItems,
          inventorySnapshot = currentInventory,
          partiesSnapshot = currentParties,
          walkInPartyAddress = walkInPartyAddress,
          walkInPartyState = walkInPartyState,
          walkInPartyPincode = walkInPartyPincode,
          autoDeductStock = autoDeduct,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "Invoice $invoiceNumber updated"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to update invoice"
        }
    }
  }

  fun recordInvoicePayment(
    invoice: GstInvoice,
    additionalPayment: Double,
    paymentMethod: String,
    onDone: () -> Unit,
  ) {
    val currentParties = (partiesState.value as? UiState.Success)?.data.orEmpty()
    val linkedParty =
      invoice.partyId.takeIf { it.isNotBlank() }?.let { pid -> currentParties.firstOrNull { it.id == pid } }
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.recordInvoicePayment(invoice, additionalPayment, paymentMethod, linkedParty)
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "Payment recorded for ${invoice.invoiceNumber}"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Payment update failed" }
    }
  }

  fun deleteInvoice(invoice: GstInvoice) {
    val currentInventory = (inventoryState.value as? UiState.Success)?.data.orEmpty()
    val currentParties = (partiesState.value as? UiState.Success)?.data.orEmpty()
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.deleteInvoice(
          invoice = invoice,
          inventorySnapshot = currentInventory,
          partiesSnapshot = currentParties,
        )
      _isBusy.value = false
      res
        .onSuccess { _statusBanner.value = "Invoice ${invoice.invoiceNumber} deleted" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Delete failed" }
    }
  }

  fun saveInvoiceEInvoice(
    invoice: GstInvoice,
    result: EInvoiceGenerationResult,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = operationsRepository.saveInvoiceEInvoiceDetails(invoice, result)
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "E-Invoice IRN & Signed QR generated for ${invoice.invoiceNumber}"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to generate E-Invoice IRN"
        }
    }
  }

  fun cancelInvoiceEInvoice(
    invoice: GstInvoice,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = operationsRepository.cancelInvoiceEInvoice(invoice)
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "E-Invoice IRN cancelled for ${invoice.invoiceNumber}"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to cancel E-Invoice"
        }
    }
  }

  fun saveInvoiceEWayBill(
    invoice: GstInvoice,
    result: EWayBillGenerationResult,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = operationsRepository.saveInvoiceEWayBillDetails(invoice, result)
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = "E-Way Bill #${result.ewbNumber} saved for ${invoice.invoiceNumber}"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to save E-Way Bill"
        }
    }
  }

  // --- Expenses & Cashbook Actions ---

  fun saveExpense(
    existingId: String?,
    title: String,
    category: String,
    paymentMethod: String,
    status: String,
    party: ErpParty?,
    partyNameFallback: String,
    referenceNo: String,
    amount: Double,
    gstInputAmount: Double,
    notes: String,
    onDone: () -> Unit,
  ) {
    val isPlain = _activeProfileId.value == BusinessProfile.SLOT_PLAIN_1
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        operationsRepository.saveExpense(
          existingId = existingId,
          title = title,
          category = category,
          paymentMethod = paymentMethod,
          status = status,
          party = party,
          partyNameFallback = partyNameFallback,
          referenceNo = referenceNo,
          amount = amount,
          gstInputAmount = if (isPlain) 0.0 else gstInputAmount,
          notes = notes,
          businessProfileId = _activeProfileId.value,
        )
      _isBusy.value = false
      res
        .onSuccess {
          _statusBanner.value = if (existingId == null) "Expense recorded" else "Expense updated"
          onDone()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to save expense" }
    }
  }

  fun markExpenseSettled(expenseId: String, paymentMethod: String) {
    viewModelScope.launch {
      operationsRepository
        .markExpenseSettled(expenseId, paymentMethod)
        .onSuccess { _statusBanner.value = "Expense marked settled" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Update failed" }
    }
  }

  fun deleteExpense(expenseId: String) {
    viewModelScope.launch {
      operationsRepository
        .deleteExpense(expenseId)
        .onSuccess { _statusBanner.value = "Expense deleted" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Delete failed" }
    }
  }

  fun syncNow() {
    if (!syncState.value.isOnline) {
      _statusBanner.value =
        "Offline mode active. Changes are saved locally and will auto-sync online."
      return
    }
    viewModelScope.launch {
      _isSyncingNow.value = true
      val res = repository.syncPendingWritesNow()
      _isSyncingNow.value = false
      res
        .onSuccess {
          val now = System.currentTimeMillis()
          syncPrefs?.setLastCloudSyncMs(currentUserId, now)
          _lastCloudSyncMs.value = now
          _statusBanner.value = "Cloud sync complete"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Pending writes remain queued locally"
        }
    }
  }

  fun createBackupPayload(): FinbossBackupPayload =
    FinbossBackupPayload(
      exportedAtMs = System.currentTimeMillis(),
      businessProfile = (profileState.value as? UiState.Success)?.data,
      businessProfiles = (profilesState.value as? UiState.Success)?.data.orEmpty(),
      parties = (rawPartiesState.value as? UiState.Success)?.data.orEmpty(),
      inventory = (rawInventoryState.value as? UiState.Success)?.data.orEmpty(),
      invoices = (rawInvoicesState.value as? UiState.Success)?.data.orEmpty(),
      expenses = (rawExpensesState.value as? UiState.Success)?.data.orEmpty(),
      dashboardEntries = (rawEntriesState.value as? UiState.Success)?.data.orEmpty(),
    )

  fun createBackupJson(): String = FinbossBackupSerializer.encodeToJson(createBackupPayload())

  fun onLocalBackupSaved(recordCount: Int) {
    val now = System.currentTimeMillis()
    syncPrefs?.setLastLocalBackupMs(currentUserId, now)
    _lastLocalBackupMs.value = now
    _statusBanner.value = "Offline backup exported ($recordCount records)"
  }

  fun parseBackupPreview(rawJson: String): Result<FinbossBackupPayload> =
    FinbossBackupSerializer.decodeFromJson(rawJson)

  fun restoreBackup(payload: FinbossBackupPayload, onDone: () -> Unit) {
    val existingProfile = (profileState.value as? UiState.Success)?.data
    val existingProfilesMap =
      (profilesState.value as? UiState.Success)?.data.orEmpty().associateBy { it.resolvedSlotId }
    val existingPartyIds =
      (rawPartiesState.value as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()
    val existingInventoryIds =
      (rawInventoryState.value as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()
    val existingInvoiceIds =
      (rawInvoicesState.value as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()
    val existingExpenseIds =
      (rawExpensesState.value as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()
    val existingEntryIds =
      (rawEntriesState.value as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()

    viewModelScope.launch {
      _isBusy.value = true
      payload.businessProfiles.forEach { bp ->
        val slot = bp.resolvedSlotId
        if (slot in BusinessProfile.ALL_SLOTS && !existingProfilesMap.containsKey(slot)) {
          repository.saveBusinessProfileAccount(
            slotId = slot,
            ownerName = bp.ownerName,
            businessName = bp.businessName,
            gstin = bp.gstin.orEmpty(),
            currencySymbol = bp.currencySymbol,
            fiscalYearLabel = bp.fiscalYearLabel,
            businessType = bp.businessType,
            monthlySalesTarget = bp.monthlySalesTarget,
            address = bp.address,
            state = bp.state,
            pincode = bp.pincode,
          )
        }
      }
      if (existingProfile == null && payload.businessProfiles.isEmpty() && payload.businessProfile != null) {
        val bp = payload.businessProfile
        repository.saveBusinessProfileAccount(
          slotId = bp.resolvedSlotId,
          ownerName = bp.ownerName,
          businessName = bp.businessName,
          gstin = bp.gstin.orEmpty(),
          currencySymbol = bp.currencySymbol,
          fiscalYearLabel = bp.fiscalYearLabel,
          businessType = bp.businessType,
          monthlySalesTarget = bp.monthlySalesTarget,
          address = bp.address,
          state = bp.state,
          pincode = bp.pincode,
        )
      }
      val res =
        operationsRepository.restoreFromBackup(
          payload = payload,
          existingPartyIds = existingPartyIds,
          existingInventoryIds = existingInventoryIds,
          existingInvoiceIds = existingInvoiceIds,
          existingExpenseIds = existingExpenseIds,
          existingEntryIds = existingEntryIds,
        )
      _isBusy.value = false
      res
        .onSuccess { restoredCount ->
          _statusBanner.value =
            if (restoredCount > 0) "Restored $restoredCount records from backup"
            else "All backup records already exist in workspace"
          onDone()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to restore backup"
        }
    }
  }

  fun updateAppSettings(transform: (ErpAppSettings) -> ErpAppSettings) {
    if (settingsStore != null) {
      settingsStore.update(transform)
    } else {
      fallbackSettingsFlow.value = transform(fallbackSettingsFlow.value)
    }
  }

  fun resetAppSettings() {
    if (settingsStore != null) {
      settingsStore.resetToDefaults()
    } else {
      fallbackSettingsFlow.value = ErpAppSettings()
    }
    _statusBanner.value = "All app logic & print settings reset to defaults"
  }

  fun notifyBanner(message: String) {
    _statusBanner.value = message
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  private fun computeKpis(
    profile: BusinessProfile?,
    entries: List<DashboardEntry>,
    invoices: List<GstInvoice>,
    inventory: List<InventoryItem>,
    parties: List<ErpParty>,
    expenses: List<ErpExpense>,
  ): DashboardKpiSummary {
    var paidSales = 0.0
    var pendingReceivables = 0.0
    var entryExpenses = 0.0
    var pendingCount = 0

    for (entry in entries) {
      when (entry.category) {
        DashboardEntry.CATEGORY_SALE,
        DashboardEntry.CATEGORY_RECEIVABLE -> {
          if (entry.status == DashboardEntry.STATUS_PAID) {
            paidSales += entry.amount
          } else {
            pendingReceivables += entry.amount
            pendingCount++
          }
        }
        DashboardEntry.CATEGORY_EXPENSE -> {
          entryExpenses += entry.amount
          if (entry.status == DashboardEntry.STATUS_PENDING) pendingCount++
        }
      }
    }

    val invoiceCollected = invoices.sumOf { it.paidAmount }
    val invoiceDue = invoices.sumOf { it.balanceDue }
    val unpaidInvoicesCount = invoices.count { it.status != GstInvoice.STATUS_PAID }
    val partyOpeningReceivable =
      parties.filter { it.partyType == ErpParty.TYPE_CUSTOMER }.sumOf { it.openingDue }

    val ledgerExpenseTotal = expenses.sumOf { it.amount }
    val pendingExpensesCount = expenses.count { it.status == ErpExpense.STATUS_PENDING }
    val outputGst = invoices.sumOf { it.gstTotal }
    val inputGstCredit = expenses.sumOf { it.gstInputAmount }

    val totalCollected = paidSales + invoiceCollected
    val totalPending = pendingReceivables + invoiceDue + partyOpeningReceivable
    val totalExpenses = entryExpenses + ledgerExpenseTotal
    val netCash = totalCollected - totalExpenses
    val target = (profile?.monthlySalesTarget ?: 250000.0).coerceAtLeast(1.0)
    val progress = ((totalCollected + invoiceDue) / target).toFloat().coerceIn(0f, 1f)

    return DashboardKpiSummary(
      totalSales = totalCollected,
      pendingReceivables = totalPending,
      totalExpenses = totalExpenses,
      netCashflow = netCash,
      totalEntriesCount = entries.size + invoices.size + expenses.size,
      pendingCount = pendingCount + unpaidInvoicesCount + pendingExpensesCount,
      targetProgress = progress,
      invoiceCount = invoices.size,
      lowStockCount = inventory.count { it.isLowStock || it.isOutOfStock },
      stockValuation = inventory.sumOf { it.stockValuation },
      partyCount = parties.size,
      expenseCount = expenses.size,
      netGstPayable = (outputGst - inputGstCredit).coerceAtLeast(0.0),
    )
  }

  private fun unwrapFirestoreMessage(error: Throwable, fallback: String): String {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Stream error for uid=$currentUserId", rootError)
    return rootError.localizedMessage ?: fallback
  }

  private companion object {
    const val TAG = "DashboardVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
