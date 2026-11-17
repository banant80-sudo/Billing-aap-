package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.BusinessProfile
import com.example.data.model.SalesmanPermissions
import com.example.data.model.SubscriptionStatus

enum class LedgerPageMode(val key: String, val label: String) {
  PARTY_LEDGER("parties", "Party Ledger"),
  ITEM_LEDGER("items", "Item / Stock Ledger"),
}

enum class ReportPageSection(
  val key: String,
  val title: String,
  val shortLabel: String,
) {
  PROFIT_LOSS("pl", "Profit & Loss (P&L) Report", "P&L Report"),
  GST_REPORT("gst", "GST Report (GSTR-1 / 2B / Summary)", "GST Report"),
  SALES_EXPENSE("sales_exp", "Sales & Expense Report", "Sales & Exp"),
  STOCK_VALUATION("stock_val", "Stock Valuation Report", "Stock Value"),
}

private enum class DrawerCategory {
  BILLING_VOUCHERS,
  LEDGER,
  REPORTS,
  AI_COPILOT,
  WORKSPACE_ADMIN,
}

@Composable
fun EnterpriseNavigationDrawerContent(
  profile: BusinessProfile?,
  userDisplayName: String,
  isPlainNonGst: Boolean,
  isSalesmanSession: Boolean,
  effectivePermissions: SalesmanPermissions,
  allowedProfilesCount: Int,
  subscriptionStatus: SubscriptionStatus,
  activeRouteKey: String,
  onSelectOverview: () -> Unit,
  onSelectSaleInvoice: () -> Unit,
  onSelectPurchase: () -> Unit,
  onSelectInventoryCatalog: () -> Unit,
  onSelectExpense: () -> Unit,
  onSelectLedgerMode: (LedgerPageMode) -> Unit,
  onSelectReportSection: (ReportPageSection) -> Unit,
  onSelectAiMode: (AiCopilotMode) -> Unit,
  onOpenWorkspace: () -> Unit,
  onOpenSwitchProfile: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onOpenBackupSync: () -> Unit,
  onOpenSubscriptionPlans: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  var expandedCategory by rememberSaveable {
    mutableStateOf<DrawerCategory?>(DrawerCategory.LEDGER)
  }

  val toggleCategory: (DrawerCategory) -> Unit = { cat ->
    expandedCategory = if (expandedCategory == cat) null else cat
  }

  ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.background,
    modifier = Modifier.width(316.dp).fillMaxHeight(),
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxHeight(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Executive Brand Header
      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(
                Brush.linearGradient(
                  colors = listOf(Color(0xFF0B253C), Color(0xFF123A5C), Color(0xFF0D4D52))
                )
              )
              .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              AsyncImage(
                model =
                  ImageRequest.Builder(context)
                    .data(R.drawable.img_business_emblem)
                    .crossfade(false)
                    .build(),
                contentDescription = "Finboss Billing No.1 ERP Emblem",
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = profile?.businessName?.ifBlank { null } ?: "$userDisplayName Enterprise",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = if (isPlainNonGst) "Plain Non-GST • Finboss Billing No.1 ERP" else "Finboss Billing No.1 ERP • GST",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFA7F3D0),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (!isSalesmanSession || allowedProfilesCount > 1) {
                Surface(
                  color = Color(0x26FFFFFF),
                  shape = RoundedCornerShape(999.dp),
                  modifier =
                    Modifier.clickable(onClick = onOpenSwitchProfile)
                      .testTag("drawer_switch_profile_btn"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.SwapHoriz,
                      contentDescription = "Switch business account",
                      tint = Color.White,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = "Switch Biz",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }
              }
              if (!isSalesmanSession) {
                Surface(
                  color = Color(0x3310B981),
                  shape = RoundedCornerShape(999.dp),
                  modifier =
                    Modifier.clickable(onClick = onOpenSubscriptionPlans)
                      .testTag("drawer_subscription_btn"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.WorkspacePremium,
                      contentDescription = "Subscription tier",
                      tint = Color(0xFFA7F3D0),
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = subscriptionStatus.topBarBadgeText,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFFA7F3D0),
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Direct Home / Overview Item
      if (effectivePermissions.canViewOverview) {
        item {
          Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            DrawerSingleItemCard(
              title = "Home / Dashboard",
              subtitle = "KPI overview & quick actions",
              icon = Icons.Filled.Dashboard,
              selected = activeRouteKey == "overview",
              testTag = "drawer_item_overview",
              onClick = onSelectOverview,
            )
          }
        }
      }

      // 1. Billing & Vouchers Dropdown
      if (
        effectivePermissions.canViewBilling ||
          effectivePermissions.canCreateInvoices ||
          effectivePermissions.canViewStock ||
          effectivePermissions.canManageStock ||
          effectivePermissions.canViewExpenses ||
          effectivePermissions.canManageExpenses
      ) {
        item {
          Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            ExpandableCategoryCard(
              title = "Billing & Vouchers",
              icon = Icons.AutoMirrored.Filled.ReceiptLong,
              expanded = expandedCategory == DrawerCategory.BILLING_VOUCHERS,
              accentColor = MaterialTheme.colorScheme.secondary,
              testTag = "drawer_cat_billing",
              onToggle = { toggleCategory(DrawerCategory.BILLING_VOUCHERS) },
            ) {
              if (effectivePermissions.canViewBilling || effectivePermissions.canCreateInvoices) {
                DrawerSubItemRow(
                  label = "Sale Invoice",
                  icon = Icons.AutoMirrored.Filled.ReceiptLong,
                  selected = activeRouteKey == "billing",
                  testTag = "drawer_sub_sale_invoice",
                  onClick = onSelectSaleInvoice,
                )
              }
              if (effectivePermissions.canManageStock) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DrawerSubItemRow(
                  label = "Purchase (Stock-In)",
                  icon = Icons.Filled.ShoppingCartCheckout,
                  selected = activeRouteKey == "purchase",
                  testTag = "drawer_sub_purchase",
                  onClick = onSelectPurchase,
                )
              }
              if (effectivePermissions.canViewStock || effectivePermissions.canManageStock) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DrawerSubItemRow(
                  label = "Inventory Catalog",
                  icon = Icons.Filled.Inventory2,
                  selected = activeRouteKey == "stock",
                  testTag = "drawer_sub_inventory",
                  onClick = onSelectInventoryCatalog,
                )
              }
              if (effectivePermissions.canViewExpenses || effectivePermissions.canManageExpenses) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                DrawerSubItemRow(
                  label = "Expense & Cashbook",
                  icon = Icons.Filled.Payments,
                  selected = activeRouteKey == "expenses",
                  testTag = "drawer_sub_expense",
                  onClick = onSelectExpense,
                )
              }
            }
          }
        }
      }

      // 2. Ledger Category Dropdown (Party Ledger & Item / Stock Ledger)
      if (effectivePermissions.canViewParties || effectivePermissions.canManageParties) {
        item {
          Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            ExpandableCategoryCard(
              title = "Ledger",
              icon = Icons.Filled.AccountBalance,
              expanded = expandedCategory == DrawerCategory.LEDGER,
              accentColor = MaterialTheme.colorScheme.primary,
              testTag = "drawer_cat_ledger",
              onToggle = { toggleCategory(DrawerCategory.LEDGER) },
            ) {
              DrawerSubItemRow(
                label = "Party Ledger",
                icon = Icons.Filled.Groups,
                selected = activeRouteKey == "ledger_parties",
                testTag = "drawer_sub_party_ledger",
                onClick = { onSelectLedgerMode(LedgerPageMode.PARTY_LEDGER) },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "Item / Stock Ledger",
                icon = Icons.Filled.Warehouse,
                selected = activeRouteKey == "ledger_items",
                testTag = "drawer_sub_item_ledger",
                onClick = { onSelectLedgerMode(LedgerPageMode.ITEM_LEDGER) },
              )
            }
          }
        }
      }

      // 3. Reports Category Dropdown (P&L, GST Report, Sales & Expense, Stock Valuation)
      if (effectivePermissions.canViewReports) {
        item {
          Box(modifier = Modifier.padding(horizontal = 12.dp)) {
            ExpandableCategoryCard(
              title = "Reports",
              icon = Icons.Filled.Assessment,
              expanded = expandedCategory == DrawerCategory.REPORTS,
              accentColor = MaterialTheme.colorScheme.tertiary,
              testTag = "drawer_cat_reports",
              onToggle = { toggleCategory(DrawerCategory.REPORTS) },
            ) {
              DrawerSubItemRow(
                label = "Profit & Loss (P&L) Report",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                selected = activeRouteKey == "report_pl",
                testTag = "drawer_sub_report_pl",
                onClick = { onSelectReportSection(ReportPageSection.PROFIT_LOSS) },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "GST Report (GSTR-1 / 2B / Summary)",
                icon = Icons.Filled.Receipt,
                selected = activeRouteKey == "report_gst",
                testTag = "drawer_sub_report_gst",
                onClick = { onSelectReportSection(ReportPageSection.GST_REPORT) },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "Sales & Expense Report",
                icon = Icons.Filled.Assessment,
                selected = activeRouteKey == "report_sales_exp",
                testTag = "drawer_sub_report_sales_exp",
                onClick = { onSelectReportSection(ReportPageSection.SALES_EXPENSE) },
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "Stock Valuation Report",
                icon = Icons.Filled.Inventory2,
                selected = activeRouteKey == "report_stock_val",
                testTag = "drawer_sub_report_stock_val",
                onClick = { onSelectReportSection(ReportPageSection.STOCK_VALUATION) },
              )
            }
          }
        }
      }

      // 4. AI Copilot Tools Dropdown (AI Analyst, Voice Entry, Bill Scanner)
      item {
        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
          ExpandableCategoryCard(
            title = "AI Copilot Tools",
            icon = Icons.Filled.AutoAwesome,
            expanded = expandedCategory == DrawerCategory.AI_COPILOT,
            accentColor = MaterialTheme.colorScheme.secondary,
            testTag = "drawer_cat_ai",
            onToggle = { toggleCategory(DrawerCategory.AI_COPILOT) },
          ) {
            DrawerSubItemRow(
              label = "AI Analyst",
              icon = Icons.Filled.AutoAwesome,
              selected = activeRouteKey == "ai_chat",
              testTag = "drawer_sub_ai_analyst",
              onClick = { onSelectAiMode(AiCopilotMode.CHAT) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DrawerSubItemRow(
              label = "Voice Entry",
              icon = Icons.Filled.Mic,
              selected = activeRouteKey == "ai_voice",
              testTag = "drawer_sub_ai_voice",
              onClick = { onSelectAiMode(AiCopilotMode.VOICE) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DrawerSubItemRow(
              label = "Bill Scanner",
              icon = Icons.Filled.DocumentScanner,
              selected = activeRouteKey == "ai_ocr",
              testTag = "drawer_sub_ai_ocr",
              onClick = { onSelectAiMode(AiCopilotMode.OCR) },
            )
          }
        }
      }

      // 5. Workspace & Settings Dropdown
      item {
        Box(modifier = Modifier.padding(horizontal = 12.dp)) {
          ExpandableCategoryCard(
            title = "Workspace & Settings",
            icon = Icons.Filled.Settings,
            expanded = expandedCategory == DrawerCategory.WORKSPACE_ADMIN,
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            testTag = "drawer_cat_workspace",
            onToggle = { toggleCategory(DrawerCategory.WORKSPACE_ADMIN) },
          ) {
            DrawerSubItemRow(
              label = if (isSalesmanSession) "Workspace & Access" else "Business & Team Seats",
              icon = Icons.Filled.Badge,
              selected = activeRouteKey == "workspace",
              testTag = "drawer_sub_workspace",
              onClick = onOpenWorkspace,
            )
            if (!isSalesmanSession) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "App & Print Settings",
                icon = Icons.Filled.Settings,
                selected = false,
                testTag = "drawer_sub_settings",
                onClick = onOpenAppSettings,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              DrawerSubItemRow(
                label = "Cloud Backup & Sync",
                icon = Icons.Filled.CloudDone,
                selected = false,
                testTag = "drawer_sub_backup",
                onClick = onOpenBackupSync,
              )
            }
          }
        }
      }

      // Clean Sign Out Footer
      item {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onSignOut)
                .testTag("drawer_sign_out_btn"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign Out",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Sign Out / Switch Account",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun DrawerSingleItemCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (selected) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.surface,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 52.dp)
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.size(32.dp)
            .clip(CircleShape)
            .background(
              if (selected) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint =
            if (selected) MaterialTheme.colorScheme.onSecondary
            else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun ExpandableCategoryCard(
  title: String,
  icon: ImageVector,
  expanded: Boolean,
  accentColor: Color,
  testTag: String,
  onToggle: () -> Unit,
  content: @Composable () -> Unit,
) {
  val chevronRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    label = "drawer_chevron_$title",
  )

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(30.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(17.dp),
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = Icons.Filled.ExpandMore,
          contentDescription = if (expanded) "Collapse $title" else "Expand $title",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp).rotate(chevronRotation),
        )
      }

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
          content()
        }
      }
    }
  }
}

@Composable
private fun DrawerSubItemRow(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .background(
          if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
          else Color.Transparent
        )
        .clickable(onClick = onClick)
        .padding(start = 22.dp, end = 14.dp, top = 11.dp, bottom = 11.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint =
        if (selected) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(17.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
  }
}
