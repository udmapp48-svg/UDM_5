package com.example.data

import com.example.data.entity.BrandEntity
import com.example.data.entity.FaultEntity
import com.example.data.entity.ModelEntity
import com.example.data.entity.PriceEntity
import com.example.data.entity.RepairTypeEntity

object DefaultData {
    val BRANDS = listOf(
        "Samsung", "Apple", "Xiaomi", "Redmi", "POCO",
        "Oppo", "Vivo", "Realme", "Huawei", "Honor",
        "Nokia", "OnePlus", "Motorola", "Sony", "Asus",
        "Tecno", "Infinix", "Itel", "ZTE", "TCL",
        "Google Pixel", "Lenovo", "HTC", "LG", "Other"
    ).map { BrandEntity(name = it) }

    val MODELS_BY_BRAND = mapOf(
        "Samsung" to listOf(
            "Galaxy M02", "Galaxy M11", "Galaxy M12", "Galaxy M21", "Galaxy M31",
            "Galaxy A03", "Galaxy A04", "Galaxy A05", "Galaxy A10", "Galaxy A12",
            "Galaxy A13", "Galaxy A14", "Galaxy A15", "Galaxy A20", "Galaxy A21s",
            "Galaxy A22", "Galaxy A23", "Galaxy A30", "Galaxy A32", "Galaxy A33",
            "Galaxy A34", "Galaxy A50", "Galaxy A51", "Galaxy A52", "Galaxy A53",
            "Galaxy A54", "Galaxy A70", "Galaxy A71", "Galaxy A72", "Galaxy A73",
            "Galaxy S20", "Galaxy S21", "Galaxy S22", "Galaxy S23", "Galaxy S24",
            "Galaxy Note 10", "Galaxy Note 20", "Galaxy J2", "Galaxy J4", "Galaxy J7"
        ),
        "Apple" to listOf(
            "iPhone 6", "iPhone 6s", "iPhone 7", "iPhone 7 Plus", "iPhone 8", "iPhone 8 Plus",
            "iPhone SE (2020)", "iPhone SE (2022)",
            "iPhone X", "iPhone XR", "iPhone XS", "iPhone XS Max",
            "iPhone 11", "iPhone 11 Pro", "iPhone 11 Pro Max",
            "iPhone 12", "iPhone 12 Mini", "iPhone 12 Pro", "iPhone 12 Pro Max",
            "iPhone 13", "iPhone 13 Mini", "iPhone 13 Pro", "iPhone 13 Pro Max",
            "iPhone 14", "iPhone 14 Plus", "iPhone 14 Pro", "iPhone 14 Pro Max",
            "iPhone 15", "iPhone 15 Plus", "iPhone 15 Pro", "iPhone 15 Pro Max",
            "iPhone 16", "iPhone 16 Pro"
        ),
        "Xiaomi" to listOf(
            "Xiaomi 11 Lite", "Xiaomi 11T", "Xiaomi 11T Pro", "Xiaomi 12",
            "Xiaomi 12 Pro", "Xiaomi 13", "Xiaomi 13T", "Xiaomi 14", "Mi 9", "Mi 10"
        ),
        "Redmi" to listOf(
            "Redmi 9", "Redmi 9A", "Redmi 9C", "Redmi 10", "Redmi 10C",
            "Redmi 12", "Redmi 12C", "Redmi 13C", "Redmi A1", "Redmi A2",
            "Redmi Note 8", "Redmi Note 8 Pro", "Redmi Note 9", "Redmi Note 9 Pro",
            "Redmi Note 10", "Redmi Note 10 Pro", "Redmi Note 11", "Redmi Note 11 Pro",
            "Redmi Note 12", "Redmi Note 12 Pro", "Redmi Note 13", "Redmi Note 13 Pro"
        ),
        "POCO" to listOf(
            "POCO C31", "POCO C40", "POCO C55", "POCO M3", "POCO M4 Pro",
            "POCO M5", "POCO X3", "POCO X3 Pro", "POCO X4 Pro", "POCO X5 Pro",
            "POCO F3", "POCO F4", "POCO F5"
        ),
        "Oppo" to listOf(
            "A15", "A16", "A17", "A18", "A38", "A53", "A54", "A57", "A58",
            "A74", "A76", "A77", "A78", "A96", "F11", "F15", "F17", "F19",
            "F21 Pro", "Reno 5", "Reno 6", "Reno 7", "Reno 8", "Reno 10"
        ),
        "Vivo" to listOf(
            "Y02", "Y11", "Y12", "Y15", "Y16", "Y17", "Y20", "Y21", "Y22",
            "Y27", "Y30", "Y33s", "Y35", "Y36", "V20", "V21", "V23", "V25",
            "V27", "V29"
        ),
        "Realme" to listOf(
            "C11", "C12", "C15", "C21", "C25", "C30", "C33", "C35", "C51",
            "C53", "C55", "Realme 5", "Realme 6", "Realme 7", "Realme 8",
            "Realme 9", "Realme 10", "Realme 11"
        ),
        "Huawei" to listOf(
            "Y5", "Y6", "Y7", "Y9", "Y9 Prime", "Nova 3i", "Nova 7i", "Nova 8i",
            "Nova 9", "P30 Lite", "P40 Lite"
        ),
        "Honor" to listOf(
            "Honor X5", "Honor X6", "Honor X7", "Honor X8", "Honor X9",
            "Honor 50", "Honor 70", "Honor 90"
        ),
        "Nokia" to listOf(
            "Nokia 2.1", "Nokia 2.2", "Nokia 2.4", "Nokia 3.1", "Nokia 3.4",
            "Nokia 5.1", "Nokia 5.3", "Nokia 6.1", "Nokia G10", "Nokia G20",
            "Nokia G21", "Nokia C1", "Nokia C10", "Nokia C20", "Nokia C30",
            "Nokia 105", "Nokia 110", "Nokia 150"
        ),
        "OnePlus" to listOf(
            "Nord", "Nord CE", "Nord CE 2", "Nord CE 3", "Nord N10", "Nord N20",
            "OnePlus 7", "OnePlus 7T", "OnePlus 8", "OnePlus 8T", "OnePlus 9",
            "OnePlus 9R", "OnePlus 10 Pro", "OnePlus 11"
        ),
        "Motorola" to listOf(
            "Moto E7", "Moto E20", "Moto E32", "Moto G10", "Moto G20",
            "Moto G30", "Moto G31", "Moto G51", "Moto G52", "Moto G60", "Moto G72"
        ),
        "Sony" to listOf(
            "Xperia 1", "Xperia 5", "Xperia 10", "Xperia XZ", "Xperia XZ2", "Xperia XZ3"
        ),
        "Asus" to listOf(
            "ROG Phone 3", "ROG Phone 5", "ROG Phone 6", "Zenfone 8", "Zenfone 9", "Zenfone 10"
        ),
        "Tecno" to listOf(
            "Spark 6", "Spark 7", "Spark 8", "Spark 9", "Spark 10", "Spark 20",
            "Camon 17", "Camon 18", "Camon 19", "Camon 20", "Pova 2", "Pova 3", "Pova 4", "Pova 5"
        ),
        "Infinix" to listOf(
            "Hot 9", "Hot 10", "Hot 11", "Hot 12", "Hot 20", "Hot 30", "Hot 40",
            "Note 10", "Note 11", "Note 12", "Note 30", "Smart 5", "Smart 6", "Smart 7", "Smart 8"
        ),
        "Itel" to listOf(
            "A16", "A23", "A25", "A48", "A56", "A58", "A60", "Vision 1", "Vision 2", "Vision 3"
        ),
        "ZTE" to listOf(
            "Blade A31", "Blade A51", "Blade A71", "Blade V30", "Blade V40"
        ),
        "TCL" to listOf(
            "TCL 20 SE", "TCL 20L", "TCL 30", "TCL 30 SE", "TCL 40 SE"
        ),
        "Google Pixel" to listOf(
            "Pixel 3", "Pixel 3a", "Pixel 4", "Pixel 4a", "Pixel 5", "Pixel 5a",
            "Pixel 6", "Pixel 6a", "Pixel 6 Pro", "Pixel 7", "Pixel 7a", "Pixel 7 Pro",
            "Pixel 8", "Pixel 8a", "Pixel 8 Pro", "Pixel 9"
        ),
        "Lenovo" to listOf(
            "K6 Note", "K8 Note", "K10 Note", "Legion Duel", "Tab M10"
        ),
        "HTC" to listOf(
            "Desire 12", "Desire 20 Pro", "Desire 21 Pro", "U11", "U12+"
        ),
        "LG" to listOf(
            "G6", "G7 ThinQ", "G8 ThinQ", "V30", "V40 ThinQ", "V50 ThinQ", "Velvet", "Wing"
        ),
        "Other" to listOf(
            "Standard Feature Phone", "Universal Smartphone", "Generic Android"
        )
    )

    fun getModelsList(): List<ModelEntity> {
        val list = mutableListOf<ModelEntity>()
        for ((brand, models) in MODELS_BY_BRAND) {
            for (m in models) {
                list.add(ModelEntity(brandName = brand, modelName = m))
            }
        }
        return list
    }

    val FAULTS = listOf(
        "Display Replacement",
        "Display Broken",
        "Display No Light",
        "Display Touch Problem",
        "Battery Problem",
        "Battery Replacement",
        "Keypad Problem",
        "Short Circuit",
        "Charging Problem",
        "Charging Pin",
        "Charging Slowly",
        "Power Problem",
        "Power Key",
        "Dead",
        "Software Problem",
        "Hanging",
        "Restarting",
        "Network Problem",
        "No Signal",
        "SIM Problem",
        "Speaker Problem",
        "Earpiece Problem",
        "Microphone Problem",
        "Camera Problem",
        "Wi-Fi Problem",
        "Bluetooth Problem",
        "Fingerprint Problem",
        "Face ID Problem",
        "Button Problem",
        "Water Damage",
        "Overheating",
        "Vibration Problem",
        "Other"
    ).map { FaultEntity(name = it) }

    val REPAIR_TYPES = listOf(
        "Display Replacement",
        "Display Glass",
        "Display Repair",
        "Battery",
        "Charging Pin",
        "Charging Port",
        "Microphone",
        "Speaker",
        "Earpiece",
        "Camera",
        "Front Camera",
        "Back Camera",
        "Power Button",
        "Volume Button",
        "Flex Cable",
        "Software",
        "Water Damage",
        "IC Repair",
        "Connector",
        "Vibration",
        "Fingerprint",
        "Face ID",
        "Network Repair",
        "SIM Problem",
        "Other"
    ).map { RepairTypeEntity(name = it) }

    val SAMPLE_PRICES = listOf(
        // Samsung M02 (from prompt example)
        PriceEntity(brand = "Samsung", model = "Galaxy M02", repairType = "Display Replacement", price = 5500.0),
        PriceEntity(brand = "Samsung", model = "Galaxy M02", repairType = "Battery", price = 3000.0),
        PriceEntity(brand = "Samsung", model = "Galaxy M02", repairType = "Charging Pin", price = 1500.0),
        // Samsung A10 & A20
        PriceEntity(brand = "Samsung", model = "Galaxy A10", repairType = "Display Replacement", price = 5000.0),
        PriceEntity(brand = "Samsung", model = "Galaxy A10", repairType = "Battery", price = 2800.0),
        PriceEntity(brand = "Samsung", model = "Galaxy A20", repairType = "Display Replacement", price = 6500.0),
        PriceEntity(brand = "Samsung", model = "Galaxy A12", repairType = "Display Replacement", price = 6500.0),
        PriceEntity(brand = "Samsung", model = "Galaxy A12", repairType = "Charging Pin", price = 1800.0),
        PriceEntity(brand = "Samsung", model = "Galaxy A51", repairType = "Display Replacement", price = 9500.0),
        // Apple
        PriceEntity(brand = "Apple", model = "iPhone 11", repairType = "Display Replacement", price = 14500.0),
        PriceEntity(brand = "Apple", model = "iPhone 11", repairType = "Battery", price = 7500.0),
        PriceEntity(brand = "Apple", model = "iPhone 11", repairType = "Charging Port", price = 4500.0),
        PriceEntity(brand = "Apple", model = "iPhone 12", repairType = "Display Replacement", price = 22000.0),
        PriceEntity(brand = "Apple", model = "iPhone 12", repairType = "Battery", price = 9500.0),
        PriceEntity(brand = "Apple", model = "iPhone 7", repairType = "Display Replacement", price = 6500.0),
        PriceEntity(brand = "Apple", model = "iPhone 8", repairType = "Display Replacement", price = 7500.0),
        PriceEntity(brand = "Apple", model = "iPhone X", repairType = "Display Replacement", price = 12500.0),
        // Redmi & Xiaomi
        PriceEntity(brand = "Redmi", model = "Redmi Note 10", repairType = "Display Replacement", price = 7500.0),
        PriceEntity(brand = "Redmi", model = "Redmi Note 10", repairType = "Battery", price = 3500.0),
        PriceEntity(brand = "Redmi", model = "Redmi 9A", repairType = "Display Replacement", price = 4500.0),
        PriceEntity(brand = "Redmi", model = "Redmi 9A", repairType = "Charging Pin", price = 1200.0),
        // POCO
        PriceEntity(brand = "POCO", model = "POCO X3 Pro", repairType = "Display Replacement", price = 8500.0),
        PriceEntity(brand = "POCO", model = "POCO X3 Pro", repairType = "IC Repair", price = 6500.0),
        // Oppo & Vivo
        PriceEntity(brand = "Oppo", model = "A15", repairType = "Display Replacement", price = 5000.0),
        PriceEntity(brand = "Vivo", model = "Y20", repairType = "Display Replacement", price = 5200.0),
        PriceEntity(brand = "Huawei", model = "Y9", repairType = "Display Replacement", price = 6000.0)
    )
}
