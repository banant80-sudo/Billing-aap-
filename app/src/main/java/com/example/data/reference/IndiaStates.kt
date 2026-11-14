package com.example.data.reference

data class IndianStateInfo(
  val name: String,
  val gstCode: String,
) {
  val displayLabel: String
    get() = "$gstCode - $name"
}

object IndiaStates {
  val allStates: List<IndianStateInfo> =
    listOf(
      IndianStateInfo("Jammu and Kashmir", "01"),
      IndianStateInfo("Himachal Pradesh", "02"),
      IndianStateInfo("Punjab", "03"),
      IndianStateInfo("Chandigarh", "04"),
      IndianStateInfo("Uttarakhand", "05"),
      IndianStateInfo("Haryana", "06"),
      IndianStateInfo("Delhi", "07"),
      IndianStateInfo("Rajasthan", "08"),
      IndianStateInfo("Uttar Pradesh", "09"),
      IndianStateInfo("Bihar", "10"),
      IndianStateInfo("Sikkim", "11"),
      IndianStateInfo("Arunachal Pradesh", "12"),
      IndianStateInfo("Nagaland", "13"),
      IndianStateInfo("Manipur", "14"),
      IndianStateInfo("Mizoram", "15"),
      IndianStateInfo("Tripura", "16"),
      IndianStateInfo("Meghalaya", "17"),
      IndianStateInfo("Assam", "18"),
      IndianStateInfo("West Bengal", "19"),
      IndianStateInfo("Jharkhand", "20"),
      IndianStateInfo("Odisha", "21"),
      IndianStateInfo("Chhattisgarh", "22"),
      IndianStateInfo("Madhya Pradesh", "23"),
      IndianStateInfo("Gujarat", "24"),
      IndianStateInfo("Dadra and Nagar Haveli and Daman and Diu", "26"),
      IndianStateInfo("Maharashtra", "27"),
      IndianStateInfo("Karnataka", "29"),
      IndianStateInfo("Goa", "30"),
      IndianStateInfo("Lakshadweep", "31"),
      IndianStateInfo("Kerala", "32"),
      IndianStateInfo("Tamil Nadu", "33"),
      IndianStateInfo("Puducherry", "34"),
      IndianStateInfo("Andaman and Nicobar Islands", "35"),
      IndianStateInfo("Telangana", "36"),
      IndianStateInfo("Andhra Pradesh", "37"),
      IndianStateInfo("Ladakh", "38"),
    )

  fun search(query: String): List<IndianStateInfo> {
    val clean = query.trim().lowercase()
    if (clean.isEmpty()) return allStates
    return allStates.filter {
      it.name.lowercase().contains(clean) || it.gstCode.contains(clean)
    }
  }

  fun findByNameOrCode(raw: String?): IndianStateInfo? {
    val clean = raw?.trim()?.lowercase()?.ifBlank { null } ?: return null
    return allStates.firstOrNull {
      it.name.lowercase() == clean ||
        it.gstCode == clean ||
        it.displayLabel.lowercase() == clean ||
        clean.endsWith("- ${it.name.lowercase()}")
    }
  }

  fun stateFromGstin(gstin: String?): IndianStateInfo? {
    val clean = gstin?.trim().orEmpty()
    if (clean.length < 2) return null
    val prefix = clean.substring(0, 2)
    return allStates.firstOrNull { it.gstCode == prefix }
  }

  fun areSameState(businessState: String?, partyState: String?): Boolean? {
    val biz = findByNameOrCode(businessState) ?: return null
    val party = findByNameOrCode(partyState) ?: return null
    return biz.gstCode == party.gstCode
  }
}
