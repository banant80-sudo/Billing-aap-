import { initializeApp } from "https://www.gstatic.com/firebasejs/11.14.0/firebase-app.js";
import { getAuth, GoogleAuthProvider } from "https://www.gstatic.com/firebasejs/11.14.0/firebase-auth.js";
import { initializeFirestore, persistentLocalCache, persistentMultipleTabManager } from "https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js";
import { FIREBASE_WEB_CONFIG, FIRESTORE_DATABASE_ID } from "./firebase-config.js";

export const app = initializeApp(FIREBASE_WEB_CONFIG);
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

let firestoreDb;
try {
  firestoreDb = initializeFirestore(
    app,
    {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() })
    },
    FIRESTORE_DATABASE_ID
  );
} catch (_) {
  const { getFirestore } = await import("https://www.gstatic.com/firebasejs/11.14.0/firebase-firestore.js");
  firestoreDb = getFirestore(app, FIRESTORE_DATABASE_ID);
}
export const db = firestoreDb;

export const PLAN_SPECS = {
  trial: {
    name: "30-Day Free Trial",
    badge: "FREE TRIAL",
    monthly: 0,
    yearly: 0,
    maxProfiles: 3,
    maxSalesmen: 5,
    eInvoice: true,
    allowedSlots: ["main_1", "main_2", "plain_1"],
    allowedSeats: ["seat_1", "seat_2", "seat_3", "seat_4", "seat_5"]
  },
  silver: {
    name: "Silver",
    badge: "SILVER",
    monthly: 300,
    yearly: 3600,
    maxProfiles: 1,
    maxSalesmen: 0,
    eInvoice: false,
    allowedSlots: ["main_1"],
    allowedSeats: []
  },
  gold: {
    name: "Gold",
    badge: "GOLD",
    monthly: 500,
    yearly: 5400,
    maxProfiles: 2,
    maxSalesmen: 2,
    eInvoice: false,
    allowedSlots: ["main_1", "main_2"],
    allowedSeats: ["seat_1", "seat_2"]
  },
  diamond: {
    name: "Diamond",
    badge: "DIAMOND",
    monthly: 700,
    yearly: 6720,
    maxProfiles: 3,
    maxSalesmen: 5,
    eInvoice: true,
    allowedSlots: ["main_1", "main_2", "plain_1"],
    allowedSeats: ["seat_1", "seat_2", "seat_3", "seat_4", "seat_5"]
  }
};

export const PROFILE_SLOT_META = {
  main_1: { label: "Main Account 1 (GST)", mode: "main_gst", badge: "Main GST #1" },
  main_2: { label: "Main Account 2 (GST)", mode: "main_gst", badge: "Main GST #2" },
  plain_1: { label: "Plain Account (Non-GST)", mode: "plain_non_gst", badge: "Plain Non-GST" }
};

export const EXPENSE_CATEGORIES = [
  { key: "purchases", label: "Purchases & Stock" },
  { key: "rent", label: "Shop / Office Rent" },
  { key: "payroll", label: "Staff Payroll" },
  { key: "utilities", label: "Utilities & Internet" },
  { key: "transport", label: "Freight & Fuel" },
  { key: "marketing", label: "Marketing & Ads" },
  { key: "tax", label: "GST / Tax Fees" },
  { key: "other", label: "Other Expenses" }
];

export const INDIA_STATES = [
  "Maharashtra", "Delhi", "Karnataka", "Gujarat", "Tamil Nadu", "Odisha",
  "Uttar Pradesh", "West Bengal", "Rajasthan", "Telangana", "Andhra Pradesh",
  "Madhya Pradesh", "Haryana", "Punjab", "Bihar", "Jharkhand", "Kerala",
  "Chhattisgarh", "Assam", "Chandigarh", "Uttarakhand", "Goa"
];

export const SALESMAN_SEAT_IDS = ["seat_1", "seat_2", "seat_3", "seat_4", "seat_5"];

export const SALESMAN_PERMISSION_DEFS = [
  { key: "canViewOverview", label: "Overview KPIs" },
  { key: "canAddQuickEntry", label: "Add Quick Cashbook Entry" },
  { key: "canViewBilling", label: "View Billing & Bills" },
  { key: "canCreateInvoices", label: "Create New Bills / Invoices" },
  { key: "canEditDeleteInvoices", label: "Edit & Delete Invoices" },
  { key: "canPrintInvoices", label: "Print & Share PDF Bills" },
  { key: "canViewStock", label: "View Stock Catalog" },
  { key: "canManageStock", label: "Add / Edit Stock & Purchases" },
  { key: "canViewParties", label: "View Parties & Ledger" },
  { key: "canManageParties", label: "Add / Edit Parties & Collect Dues" },
  { key: "canViewExpenses", label: "View Expenses & Cashbook" },
  { key: "canManageExpenses", label: "Add / Delete Expenses" },
  { key: "canViewReports", label: "View GST & P&L Reports" }
];

export const SALESMAN_ROLE_PRESETS = {
  counter_billing: {
    label: "Counter Billing",
    description: "Create & print bills, view stock and customer dues",
    permissions: {
      canViewOverview: true, canAddQuickEntry: false, canViewBilling: true,
      canCreateInvoices: true, canEditDeleteInvoices: false, canPrintInvoices: true,
      canViewStock: true, canManageStock: false, canViewParties: true,
      canManageParties: false, canViewExpenses: false, canManageExpenses: false,
      canViewReports: false
    }
  },
  field_sales: {
    label: "Field Sales & Ledger",
    description: "Create bills, manage parties & collect Party Ledger payments",
    permissions: {
      canViewOverview: true, canAddQuickEntry: true, canViewBilling: true,
      canCreateInvoices: true, canEditDeleteInvoices: false, canPrintInvoices: true,
      canViewStock: true, canManageStock: false, canViewParties: true,
      canManageParties: true, canViewExpenses: true, canManageExpenses: false,
      canViewReports: false
    }
  },
  store_manager: {
    label: "Store Manager",
    description: "Full ERP access across bills, stock, purchases & reports",
    permissions: {
      canViewOverview: true, canAddQuickEntry: true, canViewBilling: true,
      canCreateInvoices: true, canEditDeleteInvoices: true, canPrintInvoices: true,
      canViewStock: true, canManageStock: true, canViewParties: true,
      canManageParties: true, canViewExpenses: true, canManageExpenses: true,
      canViewReports: true
    }
  }
};

export const DEFAULT_APP_SETTINGS = {
  invoicePrefix: "BK-",
  defaultInvoiceType: "tax_invoice",
  defaultGstSlabPercent: 18,
  autoDetectGstModeFromState: true,
  defaultPaymentMethod: "upi",
  defaultPaperSize: "a4",
  defaultPrintStyle: "modern",
  askPrintOptionsBeforePrint: true,
  showHsnOnInvoicePrint: true,
  showAddressAndStateOnPrint: true,
  showAmountInWordsOnPrint: true,
  invoiceFooterNote: "Thank you for your business!",
  invoiceTermsText: "Goods once sold will not be taken back. Subject to local jurisdiction.",
  autoDeductStockOnSale: true,
  autoRecordPurchaseInExpenses: true,
  defaultPurchasePaymentMethod: "upi",
  defaultLowStockThreshold: 5,
  defaultPartyCreditLimit: 50000,
  warnOnCreditLimitExceeded: true,
  gstPortalMode: "sandbox",
  gstPortalGstin: "",
  defaultDispatchCity: "Mumbai",
  defaultTransporterName: "",
  defaultTransporterId: ""
};

export const state = {
  user: null,
  rootProfile: null,
  profiles: [],
  activeProfileId: localStorage.getItem("book_web_active_profile") || "main_1",
  invoices: [],
  inventory: [],
  parties: [],
  expenses: [],
  dashboardEntries: [],
  salesmen: [],
  syncMeta: { hasPendingWrites: false, fromCache: false },
  ledgerPeriod: "month",
  ledgerView: "parties",
  reportFocus: "all",
  gstApiBaseUrl: localStorage.getItem("finboss_gst_api_url") || "",
  billingStatusFilter: "all",
  stockFilter: "all",
  partyTypeFilter: "all",
  expenseViewMode: "expenses",
  expenseCatFilter: "all",
  aiMode: "chat",
  aiMessages: [
    {
      id: "welcome",
      isUser: false,
      text: "Namaste! Main Finboss Billing No.1 ERP AI Analyst hoon. Aap mujhse Sales, Party Ledger Profit/Loss %, Stock alerts, ya GST ITC ke baare me koi bhi sawaal pooch sakte hain."
    }
  ],
  aiStreaming: false,
  aiVoiceDraft: null,
  aiVoiceStatus: "idle",
  aiOcrBill: null,
  aiOcrPreviewUrl: "",
  aiOcrStatus: "idle",
  unsubs: [],
  renderCallback: null
};
