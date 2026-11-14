package com.example.data.reference

data class HsnCodeEntry(
  val code: String,
  val itemName: String,
  val category: String,
  val gstRatePercent: Double,
  val keywords: String = "",
)

object HsnCodeDirectory {
  val entries: List<HsnCodeEntry> =
    listOf(
      // POS, Paper, Stationery & Office
      HsnCodeEntry("4811", "Thermal Paper Roll / Billing Roll", "POS & Paper", 18.0, "pos roll thermal 80mm 58mm receipt"),
      HsnCodeEntry("4820", "Account Books, Registers & Notebooks", "Stationery", 12.0, "register diary notebook copy bill book"),
      HsnCodeEntry("4821", "Barcode Labels & Adhesive Stickers", "POS & Paper", 18.0, "label sticker barcode tag"),
      HsnCodeEntry("4802", "A4 Copier & Printing Paper", "Stationery", 12.0, "a4 paper rim sheet print"),
      HsnCodeEntry("9608", "Ballpoint Pens & Markers", "Stationery", 18.0, "pen marker highlighter"),
      HsnCodeEntry("3215", "Printer Ink & Toner Cartridges", "Office", 18.0, "ink toner ribbon cartridge"),

      // Electronics, IT & Appliances
      HsnCodeEntry("8471", "Laptop, Desktop, POS Terminal & Barcode Scanner", "Electronics", 18.0, "computer laptop scanner pos terminal billing machine"),
      HsnCodeEntry("8443", "Thermal Printer, Laser Printer & Parts", "Electronics", 18.0, "printer receipt printer thermal"),
      HsnCodeEntry("8517", "Mobile Phone, Smartphone & Router", "Electronics", 18.0, "mobile phone smartphone router wifi"),
      HsnCodeEntry("8504", "UPS, Inverter, Adapter & Power Supply", "Electronics", 18.0, "charger adapter inverter ups"),
      HsnCodeEntry("8507", "Battery, Lithium Cell & Power Bank", "Electronics", 28.0, "battery power bank cell"),
      HsnCodeEntry("8528", "LED TV, Monitor & Display Screen", "Electronics", 18.0, "tv monitor display led"),
      HsnCodeEntry("8536", "Electrical Switches, Sockets & MCB", "Electrical", 18.0, "switch socket plug board mcb"),
      HsnCodeEntry("8544", "Electric Wire, Copper Cable & USB Cable", "Electrical", 18.0, "wire cable usb cord"),
      HsnCodeEntry("8539", "LED Bulb, Tube Light & Lamps", "Electrical", 12.0, "bulb light led tube lamp"),
      HsnCodeEntry("8414", "Ceiling Fan, Exhaust Fan & Air Cooler", "Appliances", 18.0, "fan cooler exhaust"),
      HsnCodeEntry("8415", "Air Conditioner & HVAC Units", "Appliances", 28.0, "ac air conditioner"),
      HsnCodeEntry("8418", "Refrigerator & Deep Freezer", "Appliances", 18.0, "fridge freezer refrigerator"),

      // Grocery, Kirana & FMCG
      HsnCodeEntry("1006", "Rice (Packaged / Branded)", "Grocery", 5.0, "rice basmati grain chawal"),
      HsnCodeEntry("1001", "Wheat & Atta Flour", "Grocery", 5.0, "wheat atta flour gehu"),
      HsnCodeEntry("0713", "Pulses & Dal (Packaged)", "Grocery", 5.0, "dal pulses toor moong chana"),
      HsnCodeEntry("1507", "Soyabean & Edible Cooking Oil", "Grocery", 5.0, "oil cooking tel mustard refined"),
      HsnCodeEntry("1701", "Sugar & Jaggery", "Grocery", 5.0, "sugar cheeni gur"),
      HsnCodeEntry("0902", "Tea Leaves & Green Tea", "Beverages", 5.0, "tea chai patti"),
      HsnCodeEntry("0901", "Coffee Beans & Instant Coffee", "Beverages", 18.0, "coffee"),
      HsnCodeEntry("0910", "Spices, Turmeric, Masala & Chilli", "Grocery", 5.0, "masala spice haldi mirch"),
      HsnCodeEntry("1905", "Biscuits, Bread & Bakery Items", "Bakery & Snacks", 18.0, "biscuit cookie bread cake rusk"),
      HsnCodeEntry("2106", "Namkeen, Snacks & Food Mixes", "Bakery & Snacks", 12.0, "namkeen chips snacks bhujia"),
      HsnCodeEntry("2201", "Packaged Drinking Water", "Beverages", 18.0, "water bottle mineral"),
      HsnCodeEntry("2202", "Cold Drinks, Juice & Energy Drinks", "Beverages", 28.0, "cold drink soda juice"),
      HsnCodeEntry("0401", "Fresh Milk & Curd (Unpacked/Basic)", "Dairy", 0.0, "milk doodh curd dahi"),
      HsnCodeEntry("0405", "Butter, Ghee & Dairy Spreads", "Dairy", 12.0, "ghee butter makhan"),
      HsnCodeEntry("0406", "Cheese & Paneer (Branded)", "Dairy", 12.0, "cheese paneer"),
      HsnCodeEntry("1806", "Chocolates & Cocoa Confectionery", "Snacks", 18.0, "chocolate candy toffee"),
      HsnCodeEntry("3401", "Bath Soap, Handwash & Detergent Bar", "FMCG & Care", 18.0, "soap saboon handwash detergent"),
      HsnCodeEntry("3402", "Washing Powder & Liquid Cleaner", "FMCG & Care", 18.0, "surf washing powder cleaner"),
      HsnCodeEntry("3305", "Hair Oil & Shampoo", "FMCG & Care", 18.0, "shampoo hair oil"),
      HsnCodeEntry("3306", "Toothpaste & Oral Care", "FMCG & Care", 18.0, "toothpaste brush colgate"),
      HsnCodeEntry("3304", "Face Cream, Lotion & Cosmetics", "Cosmetics", 18.0, "cream lotion cosmetic"),

      // Garments, Textiles & Footwear
      HsnCodeEntry("6109", "T-Shirts, Vests & Knitted Tops", "Garments", 5.0, "tshirt tee shirt vest garment"),
      HsnCodeEntry("6203", "Men's Trousers, Jeans & Suits", "Garments", 12.0, "jeans pant trouser cloth"),
      HsnCodeEntry("6205", "Men's & Boys' Shirts", "Garments", 12.0, "shirt formal casual"),
      HsnCodeEntry("6204", "Women's Sarees, Kurtis & Dresses", "Garments", 5.0, "saree kurti dress ladies"),
      HsnCodeEntry("5208", "Cotton Fabric & Cloth Rolls", "Textiles", 5.0, "fabric cotton cloth"),
      HsnCodeEntry("6403", "Leather Shoes, Sandals & Footwear", "Footwear", 18.0, "shoes chappal sandal footwear"),
      HsnCodeEntry("4202", "Bags, Backpacks, Wallets & Luggage", "Accessories", 18.0, "bag backpack wallet suitcase"),

      // Hardware, Building Material & Sanitary
      HsnCodeEntry("2523", "Portland Cement & White Cement", "Construction", 28.0, "cement concrete"),
      HsnCodeEntry("7214", "TMT Iron Bars, Steel Rods & Angles", "Hardware", 18.0, "tmt bar saria steel iron rod"),
      HsnCodeEntry("7318", "Nuts, Bolts, Screws & Fasteners", "Hardware", 18.0, "screw bolt nut fastener"),
      HsnCodeEntry("3208", "Wall Paint, Primer & Enamel", "Paints", 18.0, "paint colour primer putty"),
      HsnCodeEntry("3917", "PVC Pipes, Fittings & Plumbing Tubes", "Plumbing", 18.0, "pvc pipe plumbing fitting"),
      HsnCodeEntry("6907", "Ceramic & Vitrified Floor Tiles", "Construction", 18.0, "tiles ceramic granite"),
      HsnCodeEntry("4412", "Plywood, Veneer & Laminated Sheets", "Timber", 18.0, "plywood wood board laminate"),
      HsnCodeEntry("8301", "Padlocks, Door Locks & Handles", "Hardware", 18.0, "lock tala handle door hardware"),
      HsnCodeEntry("8205", "Hand Tools, Hammer, Wrench & Pliers", "Tools", 18.0, "tools wrench hammer screwdriver"),

      // Pharma & Healthcare
      HsnCodeEntry("3004", "Medicines, Tablets & Syrups", "Pharma", 12.0, "medicine tablet syrup capsule pharma"),
      HsnCodeEntry("3005", "Bandages, Cotton & First Aid Dressings", "Pharma", 12.0, "bandage cotton first aid"),
      HsnCodeEntry("9018", "Medical Instruments, BP Monitor & Thermometer", "Medical", 12.0, "thermometer oximeter bp medical"),
      HsnCodeEntry("9004", "Spectacles, Sunglasses & Eyewear Frames", "Optical", 12.0, "glasses spectacle chashma"),

      // Auto Parts & Accessories
      HsnCodeEntry("4011", "New Rubber Tyres (Two/Four Wheeler)", "Auto", 28.0, "tyre tire bike car"),
      HsnCodeEntry("8708", "Auto Spare Parts & Brake Pads", "Auto", 28.0, "spare parts brake filter clutch"),
      HsnCodeEntry("2710", "Engine Oil, Grease & Lubricants", "Auto", 18.0, "engine oil mobil lubricant grease"),
      HsnCodeEntry("8512", "Vehicle Headlights, Horn & Indicators", "Auto", 28.0, "headlight horn bike car"),

      // Packaging & Plastics
      HsnCodeEntry("3923", "Plastic Bags, Pouches, Boxes & Containers", "Packaging", 18.0, "plastic box container pouch carry bag"),
      HsnCodeEntry("4819", "Corrugated Carton Boxes & Packaging", "Packaging", 12.0, "carton box courier packaging"),
      HsnCodeEntry("3919", "BOPP Packing Tape & Adhesive Rolls", "Packaging", 18.0, "tape packing cello"),

      // Agriculture & Jewellery
      HsnCodeEntry("3105", "Fertilizers (NPK / Urea Blend)", "Agriculture", 5.0, "fertilizer khad urea"),
      HsnCodeEntry("1209", "Agricultural Seeds (Exempt Basic)", "Agriculture", 0.0, "seed beej"),
      HsnCodeEntry("7113", "Gold & Silver Jewellery Articles", "Jewellery", 3.0, "gold silver ring ornament"),
      HsnCodeEntry("9403", "Wooden & Metal Furniture, Tables, Chairs", "Furniture", 18.0, "table chair desk almirah furniture"),

      // Services (SAC Codes)
      HsnCodeEntry("9954", "Construction & Civil Repair Services", "Service SAC", 18.0, "sac repair civil work service"),
      HsnCodeEntry("9965", "Goods Transport & Freight Charges", "Service SAC", 5.0, "sac freight transport bhada delivery"),
      HsnCodeEntry("9972", "Commercial Property Rent & Lease", "Service SAC", 18.0, "sac rent lease kiraya"),
      HsnCodeEntry("9983", "IT, Software, Accounting & Consulting Service", "Service SAC", 18.0, "sac software consulting accounting"),
      HsnCodeEntry("9987", "Repair, Maintenance & AMC Services", "Service SAC", 18.0, "sac amc repair maintenance"),
      HsnCodeEntry("9963", "Restaurant, Catering & Food Service", "Service SAC", 5.0, "sac food hotel restaurant catering"),
    )

  fun search(query: String, limit: Int = 12): List<HsnCodeEntry> {
    val clean = query.trim().lowercase()
    if (clean.isEmpty()) return entries.take(limit)
    val tokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return entries
      .mapNotNull { entry ->
        val codeL = entry.code.lowercase()
        val nameL = entry.itemName.lowercase()
        val catL = entry.category.lowercase()
        val keyL = entry.keywords.lowercase()
        val haystack = "$codeL $nameL $catL $keyL"
        val matchesAll = tokens.all { token -> haystack.contains(token) }
        if (!matchesAll) return@mapNotNull null

        val score =
          when {
            codeL == clean -> 100
            codeL.startsWith(clean) -> 90
            nameL.startsWith(clean) -> 80
            nameL.contains(clean) -> 60
            keyL.contains(clean) -> 40
            else -> 20
          }
        entry to score
      }
      .sortedByDescending { it.second }
      .take(limit)
      .map { it.first }
  }
}
