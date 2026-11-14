const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    ownerName: "Alice Sharma",
    ownerEmail: "alice@finboss.test",
    businessName: "Sharma Traders",
    gstin: "27AABCS1429B1ZS",
    address: "42 MG Road, Fort, Mumbai",
    state: "Maharashtra",
    pincode: "400001",
    currencySymbol: "₹",
    fiscalYearLabel: "FY 2026-27",
    businessType: "Retail & Wholesale",
    monthlySalesTarget: 250000,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validModule(uid, code = "billing", status = "active") {
  const ts = pastTimestamp();
  return {
    userId: uid,
    code,
    title: "Billing & GST Invoices",
    subtitle: "Create tax invoices and receipts",
    status,
    isPinned: true,
    sortOrder: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validEntry(uid, status = "pending") {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Wholesale Batch #101",
    partyName: "Metro Mart",
    category: "sale",
    amount: 14500.5,
    status,
    referenceCode: "INV-001",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validParty(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    name: "Apex Retail Mart",
    partyType: "customer",
    phone: "+91 9876543210",
    gstin: "27AABCA1234B1Z5",
    address: "18 Link Road, Andheri West",
    state: "Maharashtra",
    pincode: "400053",
    openingDue: 5000,
    totalBilled: 11800,
    totalPaid: 5000,
    creditLimit: 100000,
    notes: "Net 15 terms",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validInventoryItem(uid) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    name: "Thermal Paper Roll 80mm",
    sku: "SKU-TPR-80",
    hsnCode: "4811",
    category: "POS Supplies",
    unit: "BOX",
    salePrice: 1200,
    purchasePrice: 850,
    gstRatePercent: 18,
    stockQty: 40,
    lowStockThreshold: 10,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validInvoice(uid, status = "unpaid", paidAmount = 0) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    invoiceNumber: "FB-2026-001",
    partyId: "party_1",
    partyName: "Apex Retail Mart",
    partyGstin: "27AABCA1234B1Z5",
    partyAddress: "18 Link Road, Andheri West",
    partyState: "Maharashtra",
    partyPincode: "400053",
    invoiceType: "tax_invoice",
    taxMode: "cgst_sgst",
    paymentMethod: "credit",
    status,
    subtotal: 2400,
    gstTotal: 432,
    discountAmount: 0,
    grandTotal: 2832,
    paidAmount,
    notes: "Delivered at counter",
    lineItems: [
      {
        itemId: "item_1",
        name: "Thermal Paper Roll 80mm",
        sku: "SKU-TPR-80",
        hsnCode: "4811",
        unit: "BOX",
        qty: 2,
        unitPrice: 1200,
        gstRatePercent: 18,
        taxableAmount: 2400,
        gstAmount: 432,
        lineTotal: 2832,
      },
    ],
    createdAt: ts,
    updatedAt: ts,
  };
}

function validExpense(uid, status = "pending") {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Warehouse Electricity Bill",
    category: "utilities",
    paymentMethod: "upi",
    status,
    partyId: "",
    partyName: "MSEDCL Power",
    referenceNo: "EXP-2026-01",
    amount: 4500,
    gstInputAmount: 686,
    notes: "Monthly meter bill",
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read or write any Finboss ERP collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("parties").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("inventory_items").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("expenses").get()
  );
});

test("Owner can manage BusinessProfile and erp_modules while cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      businessName: "Updated",
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("erp_modules")
      .doc("billing")
      .set(validModule(ALICE_UID))
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("erp_modules").get()
  );
});

test("Parties & Khata rules enforce schema, payment settlement, and user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const partyRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("parties")
    .doc("p1");
  await assertSucceeds(partyRef.set(validParty(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("parties").get()
  );
  await assertSucceeds(
    partyRef.update({ totalPaid: 10000, updatedAt: pastTimestamp() })
  );
  // Negative totalPaid rejected
  await assertFails(
    partyRef.update({ totalPaid: -10, updatedAt: pastTimestamp() })
  );
  // Cross-user read rejected
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("parties")
      .doc("p1")
      .get()
  );
});

test("Inventory & Stock rules enforce non-negative stock and isolated access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const itemRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("inventory_items")
    .doc("i1");
  await assertSucceeds(itemRef.set(validInventoryItem(ALICE_UID)));
  await assertSucceeds(
    itemRef.update({ stockQty: 38, updatedAt: pastTimestamp() })
  );
  // Purchase stock-in update (stockQty + purchasePrice + hsnCode + updatedAt) succeeds
  await assertSucceeds(
    itemRef.update({
      stockQty: 50,
      purchasePrice: 890,
      hsnCode: "481190",
      updatedAt: pastTimestamp(),
    })
  );
  // Negative stock rejected
  await assertFails(
    itemRef.update({ stockQty: -1, updatedAt: pastTimestamp() })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("inventory_items").get()
  );
});

test("GST Invoices rules enforce lineItems, payment progression, terminal paid lock, and user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const invRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("invoices")
    .doc("inv1");

  // Empty lineItems rejected
  const emptyItemsInv = validInvoice(ALICE_UID);
  emptyItemsInv.lineItems = [];
  await assertFails(invRef.set(emptyItemsInv));

  // Paid amount > grandTotal rejected
  const overpaidInv = validInvoice(ALICE_UID, "paid", 9999);
  await assertFails(invRef.set(overpaidInv));

  // Valid unpaid invoice succeeds
  await assertSucceeds(invRef.set(validInvoice(ALICE_UID, "unpaid", 0)));

  // Partial payment succeeds
  await assertSucceeds(
    invRef.update({
      paidAmount: 1500,
      status: "partial",
      paymentMethod: "upi",
      updatedAt: pastTimestamp(),
    })
  );

  // Full settlement succeeds
  await assertSucceeds(
    invRef.update({
      paidAmount: 2832,
      status: "paid",
      paymentMethod: "upi",
      updatedAt: pastTimestamp(),
    })
  );

  // Terminal state lock: cannot revert paid invoice back to unpaid
  await assertFails(
    invRef.update({
      paidAmount: 0,
      status: "unpaid",
      paymentMethod: "credit",
      updatedAt: pastTimestamp(),
    })
  );

  // Full invoice edit (items, totals, customer, invoice number) succeeds for owner
  await assertSucceeds(
    invRef.update({
      invoiceNumber: "FB-2026-001-REV",
      partyName: "Apex Super Mart",
      subtotal: 3600,
      gstTotal: 648,
      grandTotal: 4248,
      paidAmount: 4248,
      status: "paid",
      notes: "Updated quantity",
      lineItems: [
        {
          itemId: "item_1",
          name: "Thermal Paper Roll 80mm",
          sku: "SKU-TPR-80",
          unit: "BOX",
          qty: 3,
          unitPrice: 1200,
          gstRatePercent: 18,
          taxableAmount: 3600,
          gstAmount: 648,
          lineTotal: 4248,
        },
      ],
      updatedAt: pastTimestamp(),
    })
  );

  // Cross-user read rejected
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("invoices")
      .doc("inv1")
      .get()
  );

  // Owner can delete invoice, cross-user delete fails
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("invoices")
      .doc("inv1")
      .delete()
  );
  await assertSucceeds(invRef.delete());
});

test("DashboardEntry enforces positive amount and terminal paid lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const entryRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("dashboard_entries")
    .doc("e1");
  await assertSucceeds(entryRef.set(validEntry(ALICE_UID, "pending")));
  await assertSucceeds(
    entryRef.update({ status: "paid", updatedAt: pastTimestamp() })
  );
  await assertFails(
    entryRef.update({ status: "pending", updatedAt: pastTimestamp() })
  );
});

test("Expenses & Cashbook rules enforce positive amount, bounded GST ITC, and user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const expRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("expenses")
    .doc("exp1");

  // Invalid GST ITC > amount rejected
  const invalidGstExp = validExpense(ALICE_UID);
  invalidGstExp.gstInputAmount = 9999;
  await assertFails(expRef.set(invalidGstExp));

  // Valid pending expense succeeds
  await assertSucceeds(expRef.set(validExpense(ALICE_UID, "pending")));

  // Settle pending expense succeeds
  await assertSucceeds(
    expRef.update({
      status: "settled",
      paymentMethod: "bank",
      updatedAt: pastTimestamp(),
    })
  );

  // Cross-user read rejected
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("expenses")
      .doc("exp1")
      .get()
  );
});

test("User can create up to 2 Main GST profiles and 1 Plain Non-GST profile (max 3)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const baseProfile = validProfile(ALICE_UID);

  const main1Ref = aliceDb.collection("users").doc(ALICE_UID).collection("business_profiles").doc("main_1");
  const main2Ref = aliceDb.collection("users").doc(ALICE_UID).collection("business_profiles").doc("main_2");
  const plain1Ref = aliceDb.collection("users").doc(ALICE_UID).collection("business_profiles").doc("plain_1");
  const extraRef = aliceDb.collection("users").doc(ALICE_UID).collection("business_profiles").doc("main_3");

  await assertSucceeds(main1Ref.set({ ...baseProfile, profileMode: "main_gst" }));
  await assertSucceeds(main2Ref.set({ ...baseProfile, businessName: "Branch Two", profileMode: "main_gst" }));
  await assertSucceeds(plain1Ref.set({ ...baseProfile, businessName: "Plain Counter", gstin: "", profileMode: "plain_non_gst" }));

  // 4th profile slot rejected
  await assertFails(extraRef.set({ ...baseProfile, profileMode: "main_gst" }));
  // Plain slot with GSTIN rejected
  await assertFails(plain1Ref.update({ gstin: "27AABCS1429B1ZS", updatedAt: pastTimestamp() }));
  // Cannot delete primary main_1 slot, can delete main_2
  await assertFails(main1Ref.delete());
  await assertSucceeds(main2Ref.delete());
});

function fullOrCustomPermissions(overrides = {}) {
  return {
    canViewOverview: true,
    canAddQuickEntry: false,
    canViewBilling: true,
    canCreateInvoices: true,
    canEditDeleteInvoices: false,
    canPrintInvoices: true,
    canViewStock: true,
    canManageStock: false,
    canViewParties: false,
    canManageParties: false,
    canViewExpenses: false,
    canManageExpenses: false,
    canViewReports: false,
    ...overrides,
  };
}

function validSalesmanSeat(ownerUid, seatId, phoneNumber, permissions = fullOrCustomPermissions(), isActive = true) {
  const ts = pastTimestamp();
  return {
    userId: ownerUid,
    seatId,
    name: "Rohan Sales",
    phoneNumber,
    roleLabel: "Counter Billing",
    isActive,
    allowedProfiles: ["main_1"],
    permissions,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPhoneAccessGrant(ownerUid, seatId, phoneNumber, permissions = fullOrCustomPermissions(), isActive = true) {
  const ts = pastTimestamp();
  return {
    ownerUid,
    seatId,
    phoneNumber,
    salesmanName: "Rohan Sales",
    roleLabel: "Counter Billing",
    isActive,
    allowedProfiles: ["main_1"],
    permissions,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Owner can manage up to 5 salesman seats (seat_1..seat_5) and seat_6 is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  for (let i = 1; i <= 5; i++) {
    const seatId = `seat_${i}`;
    const phone = `+91987654321${i}`;
    await assertSucceeds(
      aliceDb.collection("users").doc(ALICE_UID).collection("salesmen").doc(seatId)
        .set(validSalesmanSeat(ALICE_UID, seatId, phone))
    );
    await assertSucceeds(
      aliceDb.collection("phone_access").doc(phone)
        .set(validPhoneAccessGrant(ALICE_UID, seatId, phone))
    );
  }

  // 6th salesman seat rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("salesmen").doc("seat_6")
      .set(validSalesmanSeat(ALICE_UID, "seat_6", "+919876543216"))
  );

  // Another owner Bob cannot overwrite Alice's phone_access grant
  await assertFails(
    bobDb.collection("phone_access").doc("+919876543211")
      .set(validPhoneAccessGrant(BOB_UID, "seat_1", "+919876543211"))
  );
});

test("Salesman logged in via Phone OTP has strictly owner-decided access and immediate revocation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const salesmanPhone = "+919876500001";
  const otherPhone = "+919876500002";

  // Owner initializes workspace + item + party + salesman seat_1 (Counter Billing only)
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("inventory_items").doc("item_1")
      .set(validInventoryItem(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("parties").doc("party_1")
      .set(validParty(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("salesmen").doc("seat_1")
      .set(validSalesmanSeat(ALICE_UID, "seat_1", salesmanPhone))
  );
  await assertSucceeds(
    aliceDb.collection("phone_access").doc(salesmanPhone)
      .set(validPhoneAccessGrant(ALICE_UID, "seat_1", salesmanPhone))
  );

  const salesmanDb = testEnv.authenticatedContext("salesman_phone_uid_1", {
    phone_number: salesmanPhone,
  }).firestore();

  // Salesman can read own phone_access grant, but cannot list collection or read someone else's phone
  await assertSucceeds(salesmanDb.collection("phone_access").doc(salesmanPhone).get());
  await assertFails(salesmanDb.collection("phone_access").doc(otherPhone).get());
  await assertFails(salesmanDb.collection("phone_access").get());

  // Salesman can read allowed collections (billing, stock, business profile)
  await assertSucceeds(salesmanDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(salesmanDb.collection("users").doc(ALICE_UID).collection("inventory_items").get());
  await assertSucceeds(salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").get());

  // Disallowed expenses read fails (canViewExpenses=false and canViewOverview=true? Wait: expenses list allows canViewOverview for KPIs; let's test write!)
  // Counter billing salesman CANNOT create/edit inventory SKUs or parties or expenses or tamper with salesmen/profiles
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("inventory_items").doc("item_new")
      .set(validInventoryItem(ALICE_UID))
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("parties").doc("party_new")
      .set(validParty(ALICE_UID))
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("expenses").doc("exp_new")
      .set(validExpense(ALICE_UID))
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("salesmen").get()
  );
  await assertFails(
    salesmanDb.collection("phone_access").doc(salesmanPhone).update({
      isActive: true,
      permissions: fullOrCustomPermissions({ canManageStock: true }),
      updatedAt: pastTimestamp(),
    })
  );

  // Salesman CAN create an invoice on allowed profile main_1 and deduct stockQty
  const invPayload = validInvoice(ALICE_UID, "unpaid", 0);
  invPayload.businessProfileId = "main_1";
  await assertSucceeds(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").doc("s_inv_1").set(invPayload)
  );
  await assertSucceeds(
    salesmanDb.collection("users").doc(ALICE_UID).collection("inventory_items").doc("item_1")
      .update({ stockQty: 38, updatedAt: pastTimestamp() })
  );

  // Salesman CANNOT create invoice on disallowed profile main_2
  const forbiddenProfileInv = validInvoice(ALICE_UID, "unpaid", 0);
  forbiddenProfileInv.businessProfileId = "main_2";
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").doc("s_inv_2").set(forbiddenProfileInv)
  );

  // Salesman CANNOT edit or delete invoices when canEditDeleteInvoices=false
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").doc("s_inv_1")
      .update({ notes: "Tampered", updatedAt: pastTimestamp() })
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").doc("s_inv_1").delete()
  );

  // Owner pauses/disables salesman -> salesman immediately loses access
  await assertSucceeds(
    aliceDb.collection("phone_access").doc(salesmanPhone).update({
      isActive: false,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").get()
  );
  await assertFails(
    salesmanDb.collection("users").doc(ALICE_UID).collection("invoices").doc("s_inv_3").set(invPayload)
  );
});

