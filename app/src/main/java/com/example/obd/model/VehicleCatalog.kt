package com.example.obd.model

import com.example.obd.protocol.OemPidCatalog

data class CatalogMaintenanceSpec(
    val titleRu: String,
    val titleEn: String,
    val category: String,
    val intervalKm: Int,
    val intervalHours: Int = 0
)

data class CatalogVehicleTemplate(
    val id: String,
    val brand: String,
    val modelName: String,
    val engineSpecRu: String,
    val engineSpecEn: String,
    val displacementLiters: Float,
    val fuelType: FuelType,
    val redlineRpm: Int,
    val shiftLightRpm: Int,
    val curbWeightKg: Int,
    val preferredPresetId: String,
    val oemPackIds: List<String>,
    val maintenanceDefaults: List<CatalogMaintenanceSpec>
) {
    fun displayTitle(isEnglish: Boolean): String =
        "$brand $modelName (${if (isEnglish) engineSpecEn else engineSpecRu})"
}

object VehicleCatalog {

    val templates: List<CatalogVehicleTemplate> = listOf(
        // VAG (VW / Audi / Skoda)
        CatalogVehicleTemplate(
            id = "vag_tsi_dsg",
            brand = "VAG (VW / Audi / Skoda)",
            modelName = "Tiguan / Octavia / Golf",
            engineSpecRu = "1.4 / 2.0 TSI • DSG",
            engineSpecEn = "1.4 / 2.0 TSI • DSG",
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6500,
            shiftLightRpm = 6000,
            curbWeightKg = 1520,
            preferredPresetId = "main",
            oemPackIds = listOf("vag"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30/5W-40", "Engine Oil 5W-30/5W-40", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Масло КПП DSG", "DSG Transmission Oil", "OIL", 60000),
                CatalogMaintenanceSpec("Свечи зажигания TSI", "TSI Spark Plugs", "IGNITION", 30000)
            )
        ),
        CatalogVehicleTemplate(
            id = "vag_tdi_dpf",
            brand = "VAG (VW / Audi / Skoda)",
            modelName = "Passat / Superb / Touareg",
            engineSpecRu = "2.0 / 3.0 TDI Дизель DPF",
            engineSpecEn = "2.0 / 3.0 TDI Diesel DPF",
            displacementLiters = 2.0f,
            fuelType = FuelType.DIESEL,
            redlineRpm = 4800,
            shiftLightRpm = 4100,
            curbWeightKg = 1650,
            preferredPresetId = "diesel_trip",
            oemPackIds = listOf("vag", "diesel_dpf"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30 504/507", "Engine Oil 5W-30 504/507", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Топливный фильтр TDI", "TDI Fuel Filter", "FILTERS", 20000)
            )
        ),
        // Toyota / Lexus
        CatalogVehicleTemplate(
            id = "toyota_hybrid",
            brand = "Toyota / Lexus",
            modelName = "Prius / Camry / RAV4 Hybrid",
            engineSpecRu = "1.8 / 2.5 Hybrid + ВВБ",
            engineSpecEn = "1.8 / 2.5 Hybrid + HV Battery",
            displacementLiters = 1.8f,
            fuelType = FuelType.HYBRID,
            redlineRpm = 5200,
            shiftLightRpm = 4600,
            curbWeightKg = 1420,
            preferredPresetId = "main",
            oemPackIds = listOf("toyota_hybrid"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 0W-20", "Engine Oil 0W-20", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Жидкость e-CVT (ATF WS)", "e-CVT Fluid (ATF WS)", "OIL", 60000),
                CatalogMaintenanceSpec("Чистка охлаждения ВВБ", "HV Battery Cooling Fan Clean", "FILTERS", 30000)
            )
        ),
        CatalogVehicleTemplate(
            id = "toyota_petrol",
            brand = "Toyota / Lexus",
            modelName = "Corolla / Camry / Land Cruiser",
            engineSpecRu = "1.6 / 2.5 / 3.5 Бензин",
            engineSpecEn = "1.6 / 2.5 / 3.5 Petrol",
            displacementLiters = 2.5f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6400,
            shiftLightRpm = 5800,
            curbWeightKg = 1510,
            preferredPresetId = "main",
            oemPackIds = listOf("toyota_hybrid"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 0W-20/5W-30", "Engine Oil 0W-20/5W-30", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Воздушный фильтр ДВС", "Engine Air Filter", "FILTERS", 15000)
            )
        ),
        // Hyundai / Kia
        CatalogVehicleTemplate(
            id = "hyundai_kia_petrol",
            brand = "Hyundai / Kia",
            modelName = "Solaris / Rio / Creta / K5",
            engineSpecRu = "1.6 / 2.0 MPI / GDI",
            engineSpecEn = "1.6 / 2.0 MPI / GDI",
            displacementLiters = 1.6f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6500,
            shiftLightRpm = 5900,
            curbWeightKg = 1280,
            preferredPresetId = "main",
            oemPackIds = listOf("hyundai_kia"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30", "Engine Oil 5W-30", "OIL", 7500, 220),
                CatalogMaintenanceSpec("Жидкость АКПП SP-IV", "ATF SP-IV Fluid", "OIL", 50000),
                CatalogMaintenanceSpec("Свечи зажигания", "Spark Plugs", "IGNITION", 30000)
            )
        ),
        CatalogVehicleTemplate(
            id = "hyundai_kia_crdi",
            brand = "Hyundai / Kia",
            modelName = "Santa Fe / Sorento / Sportage",
            engineSpecRu = "2.0 / 2.2 CRDi Дизель DPF",
            engineSpecEn = "2.0 / 2.2 CRDi Diesel DPF",
            displacementLiters = 2.2f,
            fuelType = FuelType.DIESEL,
            redlineRpm = 4800,
            shiftLightRpm = 4200,
            curbWeightKg = 1820,
            preferredPresetId = "diesel_trip",
            oemPackIds = listOf("hyundai_kia", "diesel_dpf"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30 C3", "Engine Oil 5W-30 C3", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Топливный фильтр CRDi", "CRDi Fuel Filter", "FILTERS", 20000)
            )
        ),
        // BMW
        CatalogVehicleTemplate(
            id = "bmw_petrol",
            brand = "BMW / MINI",
            modelName = "3 / 5 Series / X3",
            engineSpecRu = "2.0 / 3.0 TwinPower Turbo",
            engineSpecEn = "2.0 / 3.0 TwinPower Turbo",
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            redlineRpm = 7000,
            shiftLightRpm = 6400,
            curbWeightKg = 1580,
            preferredPresetId = "main",
            oemPackIds = listOf("bmw"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС LL-01/LL-04", "Engine Oil LL-01/LL-04", "OIL", 7500, 230),
                CatalogMaintenanceSpec("Масло ZF 8HP", "ZF 8HP Transmission Service", "OIL", 60000)
            )
        ),
        CatalogVehicleTemplate(
            id = "bmw_diesel",
            brand = "BMW / MINI",
            modelName = "X5 / 530d / X3",
            engineSpecRu = "2.0 / 3.0d Дизель DPF",
            engineSpecEn = "2.0 / 3.0d Diesel DPF",
            displacementLiters = 3.0f,
            fuelType = FuelType.DIESEL,
            redlineRpm = 5000,
            shiftLightRpm = 4400,
            curbWeightKg = 1950,
            preferredPresetId = "diesel_trip",
            oemPackIds = listOf("bmw", "diesel_dpf"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС LL-04 5W-30", "Engine Oil LL-04 5W-30", "OIL", 8000, 240),
                CatalogMaintenanceSpec("Топливный фильтр", "Fuel Filter", "FILTERS", 25000)
            )
        ),
        // Ford / Mazda
        CatalogVehicleTemplate(
            id = "ford_mazda",
            brand = "Ford / Mazda",
            modelName = "Focus / Kuga / CX-5 / Mazda 6",
            engineSpecRu = "1.5 / 2.0 / 2.5 EcoBoost / SkyActiv",
            engineSpecEn = "1.5 / 2.0 / 2.5 EcoBoost / SkyActiv",
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6600,
            shiftLightRpm = 6000,
            curbWeightKg = 1440,
            preferredPresetId = "main",
            oemPackIds = listOf("ford_mazda"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30", "Engine Oil 5W-30", "OIL", 8000, 240),
                CatalogMaintenanceSpec("Тормозная жидкость DOT4", "Brake Fluid DOT4", "BRAKES", 40000)
            )
        ),
        // Renault / Nissan / Lada
        CatalogVehicleTemplate(
            id = "lada_renault",
            brand = "Lada / Renault / Nissan",
            modelName = "Vesta / Duster / Qashqai / X-Trail",
            engineSpecRu = "1.6 / 1.8 / 2.0 Атмо / CVT",
            engineSpecEn = "1.6 / 1.8 / 2.0 NA / CVT",
            displacementLiters = 1.6f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6200,
            shiftLightRpm = 5700,
            curbWeightKg = 1320,
            preferredPresetId = "main",
            oemPackIds = emptyList(),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-40", "Engine Oil 5W-40", "OIL", 8000, 250),
                CatalogMaintenanceSpec("Воздушный и салонный фильтры", "Air & Cabin Filters", "FILTERS", 15000)
            )
        ),
        // Honda / Acura
        CatalogVehicleTemplate(
            id = "honda_petrol",
            brand = "Honda / Acura",
            modelName = "Fit / Civic / CR-V / Stepwgn",
            engineSpecRu = "1.3 / 1.5 / 2.0 i-VTEC / e:HEV",
            engineSpecEn = "1.3 / 1.5 / 2.0 i-VTEC / e:HEV",
            displacementLiters = 1.5f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6800,
            shiftLightRpm = 6200,
            curbWeightKg = 1260,
            preferredPresetId = "main",
            oemPackIds = emptyList(),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 0W-20 / 5W-30", "Engine Oil 0W-20 / 5W-30", "OIL", 7500, 230),
                CatalogMaintenanceSpec("Жидкость вариатора HMMF / HCF-2", "CVT Fluid HMMF / HCF-2", "OIL", 40000)
            )
        ),
        // Subaru
        CatalogVehicleTemplate(
            id = "subaru_boxer",
            brand = "Subaru",
            modelName = "Forester / Outback / Impreza / Legacy",
            engineSpecRu = "2.0 / 2.5 Boxer / Turbo AWD",
            engineSpecEn = "2.0 / 2.5 Boxer / Turbo AWD",
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6700,
            shiftLightRpm = 6100,
            curbWeightKg = 1510,
            preferredPresetId = "main",
            oemPackIds = emptyList(),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 5W-30 / 0W-20", "Engine Oil 5W-30 / 0W-20", "OIL", 7000, 220),
                CatalogMaintenanceSpec("Масло вариатора Lineartronic / АКПП", "CVT Lineartronic / ATF", "OIL", 45000),
                CatalogMaintenanceSpec("Масло редукторов 75W-90", "Diff Gear Oil 75W-90", "OIL", 45000)
            )
        ),
        // Mitsubishi
        CatalogVehicleTemplate(
            id = "mitsubishi_main",
            brand = "Mitsubishi",
            modelName = "Outlander / Lancer / ASX / Pajero",
            engineSpecRu = "1.6 / 2.0 / 2.4 MIVEC / PHEV",
            engineSpecEn = "1.6 / 2.0 / 2.4 MIVEC / PHEV",
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            redlineRpm = 6500,
            shiftLightRpm = 5900,
            curbWeightKg = 1490,
            preferredPresetId = "main",
            oemPackIds = emptyList(),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС 0W-20 / 5W-30", "Engine Oil 0W-20 / 5W-30", "OIL", 8000, 240),
                CatalogMaintenanceSpec("Жидкость CVT J4", "CVT Fluid J4", "OIL", 45000)
            )
        ),
        // Universal / Custom
        CatalogVehicleTemplate(
            id = "universal_diesel",
            brand = "Универсальный / Other",
            modelName = "Дизель OBD2 + DPF",
            engineSpecRu = "Любой турбодизель с сажевым DPF",
            engineSpecEn = "Generic Turbo Diesel with DPF",
            displacementLiters = 2.0f,
            fuelType = FuelType.DIESEL,
            redlineRpm = 4800,
            shiftLightRpm = 4100,
            curbWeightKg = 1600,
            preferredPresetId = "diesel_trip",
            oemPackIds = listOf("diesel_dpf"),
            maintenanceDefaults = listOf(
                CatalogMaintenanceSpec("Масло ДВС Low-SAPS C3", "Engine Oil Low-SAPS C3", "OIL", 8000, 250)
            )
        )
    )

    data class VehicleIdentifierMatch(
        val brand: String,
        val templateId: String? = null,
        val suggestedName: String? = null,
        val displacementLiters: Float? = null,
        val fuelType: FuelType? = null,
        val isFrameNumber: Boolean = false
    )

    private data class FrameRule(
        val prefix: String,
        val brand: String,
        val templateId: String,
        val modelTitle: String,
        val displacementLiters: Float,
        val fuelType: FuelType
    )

    private val frameRules: List<FrameRule> = listOf(
        // Toyota / Lexus Hybrids
        FrameRule("ZVW30", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW35", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius PHV", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW40", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius Alpha", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW41", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius Alpha", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW50", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW51", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius", 1.8f, FuelType.HYBRID),
        FrameRule("ZVW55", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius E-Four", 1.8f, FuelType.HYBRID),
        FrameRule("NHW20", "Toyota / Lexus", "toyota_hybrid", "Toyota Prius", 1.5f, FuelType.HYBRID),
        FrameRule("NHP10", "Toyota / Lexus", "toyota_hybrid", "Toyota Aqua", 1.5f, FuelType.HYBRID),
        FrameRule("MXPK1", "Toyota / Lexus", "toyota_hybrid", "Toyota Aqua", 1.5f, FuelType.HYBRID),
        FrameRule("ZWR80", "Toyota / Lexus", "toyota_hybrid", "Toyota Noah / Voxy Hybrid", 1.8f, FuelType.HYBRID),
        FrameRule("ZWE211", "Toyota / Lexus", "toyota_hybrid", "Toyota Corolla Hybrid", 1.8f, FuelType.HYBRID),
        FrameRule("ZWE214", "Toyota / Lexus", "toyota_hybrid", "Toyota Corolla Hybrid", 1.8f, FuelType.HYBRID),
        FrameRule("NKE165", "Toyota / Lexus", "toyota_hybrid", "Toyota Corolla Fielder Hybrid", 1.5f, FuelType.HYBRID),
        FrameRule("NHP170", "Toyota / Lexus", "toyota_hybrid", "Toyota Sienta Hybrid", 1.5f, FuelType.HYBRID),
        FrameRule("AVV50", "Toyota / Lexus", "toyota_hybrid", "Toyota Camry Hybrid", 2.5f, FuelType.HYBRID),
        FrameRule("AXVH70", "Toyota / Lexus", "toyota_hybrid", "Toyota Camry Hybrid", 2.5f, FuelType.HYBRID),
        FrameRule("ZYX10", "Toyota / Lexus", "toyota_hybrid", "Toyota C-HR Hybrid", 1.8f, FuelType.HYBRID),
        FrameRule("AYZ10", "Toyota / Lexus", "toyota_hybrid", "Lexus NX 300h", 2.5f, FuelType.HYBRID),
        FrameRule("ZWA10", "Toyota / Lexus", "toyota_hybrid", "Lexus CT 200h", 1.8f, FuelType.HYBRID),
        FrameRule("AWS210", "Toyota / Lexus", "toyota_hybrid", "Toyota Crown Hybrid", 2.5f, FuelType.HYBRID),
        FrameRule("ATH20", "Toyota / Lexus", "toyota_hybrid", "Toyota Alphard Hybrid", 2.4f, FuelType.HYBRID),
        FrameRule("AYH30", "Toyota / Lexus", "toyota_hybrid", "Toyota Alphard Hybrid", 2.5f, FuelType.HYBRID),
        // Toyota Petrol / Diesel JDM
        FrameRule("NZE121", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla", 1.5f, FuelType.PETROL),
        FrameRule("NZE141", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla Axio/Fielder", 1.5f, FuelType.PETROL),
        FrameRule("NZE144", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla Fielder 4WD", 1.5f, FuelType.PETROL),
        FrameRule("NZE161", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla Axio/Fielder", 1.5f, FuelType.PETROL),
        FrameRule("NZE164", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla 4WD", 1.5f, FuelType.PETROL),
        FrameRule("ZRE142", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla", 1.8f, FuelType.PETROL),
        FrameRule("ZRE162", "Toyota / Lexus", "toyota_petrol", "Toyota Corolla Fielder", 1.8f, FuelType.PETROL),
        FrameRule("ZGE20", "Toyota / Lexus", "toyota_petrol", "Toyota Wish", 1.8f, FuelType.PETROL),
        FrameRule("ZGE25", "Toyota / Lexus", "toyota_petrol", "Toyota Wish 4WD", 1.8f, FuelType.PETROL),
        FrameRule("NZT260", "Toyota / Lexus", "toyota_petrol", "Toyota Allion / Premio", 1.5f, FuelType.PETROL),
        FrameRule("ZRT260", "Toyota / Lexus", "toyota_petrol", "Toyota Allion / Premio", 1.8f, FuelType.PETROL),
        FrameRule("NCP50", "Toyota / Lexus", "toyota_petrol", "Toyota Probox / Succeed", 1.3f, FuelType.PETROL),
        FrameRule("NCP51", "Toyota / Lexus", "toyota_petrol", "Toyota Probox / Succeed", 1.5f, FuelType.PETROL),
        FrameRule("NCP58", "Toyota / Lexus", "toyota_petrol", "Toyota Probox / Succeed", 1.5f, FuelType.PETROL),
        FrameRule("NCP160", "Toyota / Lexus", "toyota_petrol", "Toyota Probox / Succeed", 1.5f, FuelType.PETROL),
        FrameRule("NCP91", "Toyota / Lexus", "toyota_petrol", "Toyota Vitz RS", 1.5f, FuelType.PETROL),
        FrameRule("NCP131", "Toyota / Lexus", "toyota_petrol", "Toyota Vitz", 1.5f, FuelType.PETROL),
        FrameRule("NSP130", "Toyota / Lexus", "toyota_petrol", "Toyota Vitz", 1.3f, FuelType.PETROL),
        FrameRule("NSP170", "Toyota / Lexus", "toyota_petrol", "Toyota Sienta", 1.5f, FuelType.PETROL),
        FrameRule("ZRR70", "Toyota / Lexus", "toyota_petrol", "Toyota Noah / Voxy", 2.0f, FuelType.PETROL),
        FrameRule("ZRR80", "Toyota / Lexus", "toyota_petrol", "Toyota Noah / Voxy", 2.0f, FuelType.PETROL),
        FrameRule("ANH20", "Toyota / Lexus", "toyota_petrol", "Toyota Alphard / Vellfire", 2.4f, FuelType.PETROL),
        FrameRule("AGH30", "Toyota / Lexus", "toyota_petrol", "Toyota Alphard / Vellfire", 2.5f, FuelType.PETROL),
        FrameRule("GGH20", "Toyota / Lexus", "toyota_petrol", "Toyota Alphard / Vellfire", 3.5f, FuelType.PETROL),
        FrameRule("ACA31", "Toyota / Lexus", "toyota_petrol", "Toyota RAV4", 2.4f, FuelType.PETROL),
        FrameRule("ACA33", "Toyota / Lexus", "toyota_petrol", "Toyota RAV4", 2.4f, FuelType.PETROL),
        FrameRule("ACU30", "Toyota / Lexus", "toyota_petrol", "Toyota Harrier", 2.4f, FuelType.PETROL),
        FrameRule("ZSU60", "Toyota / Lexus", "toyota_petrol", "Toyota Harrier", 2.0f, FuelType.PETROL),
        FrameRule("GRS180", "Toyota / Lexus", "toyota_petrol", "Toyota Crown", 2.5f, FuelType.PETROL),
        FrameRule("GRS200", "Toyota / Lexus", "toyota_petrol", "Toyota Crown", 2.5f, FuelType.PETROL),
        FrameRule("GRX120", "Toyota / Lexus", "toyota_petrol", "Toyota Mark X", 2.5f, FuelType.PETROL),
        FrameRule("GRX130", "Toyota / Lexus", "toyota_petrol", "Toyota Mark X", 2.5f, FuelType.PETROL),
        FrameRule("JZX90", "Toyota / Lexus", "toyota_petrol", "Toyota Mark II / Chaser", 2.5f, FuelType.PETROL),
        FrameRule("JZX100", "Toyota / Lexus", "toyota_petrol", "Toyota Mark II / Chaser", 2.5f, FuelType.PETROL),
        FrameRule("JZX110", "Toyota / Lexus", "toyota_petrol", "Toyota Mark II / Verossa", 2.5f, FuelType.PETROL),
        FrameRule("TRJ150", "Toyota / Lexus", "toyota_petrol", "Toyota Land Cruiser Prado", 2.7f, FuelType.PETROL),
        FrameRule("GRJ150", "Toyota / Lexus", "toyota_petrol", "Toyota Land Cruiser Prado", 4.0f, FuelType.PETROL),
        FrameRule("KDJ150", "Toyota / Lexus", "universal_diesel", "Toyota Land Cruiser Prado D-4D", 3.0f, FuelType.DIESEL),
        FrameRule("GDJ150", "Toyota / Lexus", "universal_diesel", "Toyota Land Cruiser Prado D-4D", 2.8f, FuelType.DIESEL),
        FrameRule("UZJ100", "Toyota / Lexus", "toyota_petrol", "Toyota Land Cruiser 100", 4.7f, FuelType.PETROL),
        // Nissan
        FrameRule("T30", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail", 2.0f, FuelType.PETROL),
        FrameRule("NT30", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail 4WD", 2.0f, FuelType.PETROL),
        FrameRule("T31", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail", 2.0f, FuelType.PETROL),
        FrameRule("NT31", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail 4WD", 2.0f, FuelType.PETROL),
        FrameRule("T32", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail", 2.0f, FuelType.PETROL),
        FrameRule("NT32", "Lada / Renault / Nissan", "lada_renault", "Nissan X-Trail 4WD", 2.0f, FuelType.PETROL),
        FrameRule("C25", "Lada / Renault / Nissan", "lada_renault", "Nissan Serena", 2.0f, FuelType.PETROL),
        FrameRule("C26", "Lada / Renault / Nissan", "lada_renault", "Nissan Serena", 2.0f, FuelType.PETROL),
        FrameRule("HC26", "Lada / Renault / Nissan", "lada_renault", "Nissan Serena S-Hybrid", 2.0f, FuelType.HYBRID),
        FrameRule("C27", "Lada / Renault / Nissan", "lada_renault", "Nissan Serena", 2.0f, FuelType.PETROL),
        FrameRule("GC27", "Lada / Renault / Nissan", "lada_renault", "Nissan Serena S-Hybrid", 2.0f, FuelType.HYBRID),
        FrameRule("E11", "Lada / Renault / Nissan", "lada_renault", "Nissan Note", 1.5f, FuelType.PETROL),
        FrameRule("E12", "Lada / Renault / Nissan", "lada_renault", "Nissan Note", 1.2f, FuelType.PETROL),
        FrameRule("HE12", "Lada / Renault / Nissan", "lada_renault", "Nissan Note e-Power", 1.2f, FuelType.HYBRID),
        FrameRule("FB15", "Lada / Renault / Nissan", "lada_renault", "Nissan Sunny", 1.5f, FuelType.PETROL),
        FrameRule("G11", "Lada / Renault / Nissan", "lada_renault", "Nissan Bluebird Sylphy", 1.5f, FuelType.PETROL),
        FrameRule("V36", "Lada / Renault / Nissan", "lada_renault", "Nissan Skyline", 2.5f, FuelType.PETROL),
        FrameRule("J10", "Lada / Renault / Nissan", "lada_renault", "Nissan Dualis / Qashqai", 2.0f, FuelType.PETROL),
        // Honda
        FrameRule("GD1", "Honda / Acura", "honda_petrol", "Honda Fit", 1.3f, FuelType.PETROL),
        FrameRule("GD3", "Honda / Acura", "honda_petrol", "Honda Fit", 1.5f, FuelType.PETROL),
        FrameRule("GE6", "Honda / Acura", "honda_petrol", "Honda Fit", 1.3f, FuelType.PETROL),
        FrameRule("GE8", "Honda / Acura", "honda_petrol", "Honda Fit RS", 1.5f, FuelType.PETROL),
        FrameRule("GK3", "Honda / Acura", "honda_petrol", "Honda Fit", 1.3f, FuelType.PETROL),
        FrameRule("GK5", "Honda / Acura", "honda_petrol", "Honda Fit", 1.5f, FuelType.PETROL),
        FrameRule("GP1", "Honda / Acura", "honda_petrol", "Honda Fit Hybrid", 1.3f, FuelType.HYBRID),
        FrameRule("GP5", "Honda / Acura", "honda_petrol", "Honda Fit Hybrid", 1.5f, FuelType.HYBRID),
        FrameRule("GP6", "Honda / Acura", "honda_petrol", "Honda Fit Hybrid 4WD", 1.5f, FuelType.HYBRID),
        FrameRule("RU1", "Honda / Acura", "honda_petrol", "Honda Vezel", 1.5f, FuelType.PETROL),
        FrameRule("RU3", "Honda / Acura", "honda_petrol", "Honda Vezel Hybrid", 1.5f, FuelType.HYBRID),
        FrameRule("RU4", "Honda / Acura", "honda_petrol", "Honda Vezel Hybrid 4WD", 1.5f, FuelType.HYBRID),
        FrameRule("FD1", "Honda / Acura", "honda_petrol", "Honda Civic", 1.8f, FuelType.PETROL),
        FrameRule("CL7", "Honda / Acura", "honda_petrol", "Honda Accord", 2.0f, FuelType.PETROL),
        FrameRule("CL9", "Honda / Acura", "honda_petrol", "Honda Accord", 2.4f, FuelType.PETROL),
        FrameRule("CU2", "Honda / Acura", "honda_petrol", "Honda Accord", 2.4f, FuelType.PETROL),
        FrameRule("RD5", "Honda / Acura", "honda_petrol", "Honda CR-V", 2.0f, FuelType.PETROL),
        FrameRule("RE4", "Honda / Acura", "honda_petrol", "Honda CR-V", 2.4f, FuelType.PETROL),
        FrameRule("RM4", "Honda / Acura", "honda_petrol", "Honda CR-V", 2.4f, FuelType.PETROL),
        FrameRule("RK1", "Honda / Acura", "honda_petrol", "Honda Stepwgn", 2.0f, FuelType.PETROL),
        FrameRule("RK5", "Honda / Acura", "honda_petrol", "Honda Stepwgn Spada", 2.0f, FuelType.PETROL),
        FrameRule("RP3", "Honda / Acura", "honda_petrol", "Honda Stepwgn Spada", 1.5f, FuelType.PETROL),
        FrameRule("RP5", "Honda / Acura", "honda_petrol", "Honda Stepwgn e:HEV", 2.0f, FuelType.HYBRID),
        FrameRule("GB3", "Honda / Acura", "honda_petrol", "Honda Freed", 1.5f, FuelType.PETROL),
        FrameRule("GB5", "Honda / Acura", "honda_petrol", "Honda Freed", 1.5f, FuelType.PETROL),
        FrameRule("GB7", "Honda / Acura", "honda_petrol", "Honda Freed Hybrid", 1.5f, FuelType.HYBRID),
        // Subaru
        FrameRule("SF5", "Subaru", "subaru_boxer", "Subaru Forester", 2.0f, FuelType.PETROL),
        FrameRule("SG5", "Subaru", "subaru_boxer", "Subaru Forester", 2.0f, FuelType.PETROL),
        FrameRule("SG9", "Subaru", "subaru_boxer", "Subaru Forester 2.5", 2.5f, FuelType.PETROL),
        FrameRule("SH5", "Subaru", "subaru_boxer", "Subaru Forester", 2.0f, FuelType.PETROL),
        FrameRule("SH9", "Subaru", "subaru_boxer", "Subaru Forester 2.5", 2.5f, FuelType.PETROL),
        FrameRule("SHJ", "Subaru", "subaru_boxer", "Subaru Forester", 2.0f, FuelType.PETROL),
        FrameRule("SJ5", "Subaru", "subaru_boxer", "Subaru Forester", 2.0f, FuelType.PETROL),
        FrameRule("SJG", "Subaru", "subaru_boxer", "Subaru Forester XT", 2.0f, FuelType.PETROL),
        FrameRule("SK9", "Subaru", "subaru_boxer", "Subaru Forester", 2.5f, FuelType.PETROL),
        FrameRule("SKE", "Subaru", "subaru_boxer", "Subaru Forester e-Boxer", 2.0f, FuelType.HYBRID),
        FrameRule("BL5", "Subaru", "subaru_boxer", "Subaru Legacy B4", 2.0f, FuelType.PETROL),
        FrameRule("BP5", "Subaru", "subaru_boxer", "Subaru Legacy Wagon", 2.0f, FuelType.PETROL),
        FrameRule("BPE", "Subaru", "subaru_boxer", "Subaru Legacy 3.0R", 3.0f, FuelType.PETROL),
        FrameRule("BR9", "Subaru", "subaru_boxer", "Subaru Outback / Legacy", 2.5f, FuelType.PETROL),
        FrameRule("BS9", "Subaru", "subaru_boxer", "Subaru Outback", 2.5f, FuelType.PETROL),
        FrameRule("GF8", "Subaru", "subaru_boxer", "Subaru Impreza WRX", 2.0f, FuelType.PETROL),
        FrameRule("GC8", "Subaru", "subaru_boxer", "Subaru Impreza WRX", 2.0f, FuelType.PETROL),
        FrameRule("GDA", "Subaru", "subaru_boxer", "Subaru Impreza WRX", 2.0f, FuelType.PETROL),
        FrameRule("GDB", "Subaru", "subaru_boxer", "Subaru Impreza WRX STI", 2.0f, FuelType.PETROL),
        FrameRule("GP7", "Subaru", "subaru_boxer", "Subaru XV / Crosstrek", 2.0f, FuelType.PETROL),
        FrameRule("GT7", "Subaru", "subaru_boxer", "Subaru XV / Crosstrek", 2.0f, FuelType.PETROL),
        FrameRule("VM4", "Subaru", "subaru_boxer", "Subaru Levorg", 1.6f, FuelType.PETROL),
        // Mazda
        FrameRule("BK3P", "Ford / Mazda", "ford_mazda", "Mazda Axela / Mazda 3", 2.3f, FuelType.PETROL),
        FrameRule("BK5P", "Ford / Mazda", "ford_mazda", "Mazda Axela / Mazda 3", 1.5f, FuelType.PETROL),
        FrameRule("BKEP", "Ford / Mazda", "ford_mazda", "Mazda Axela / Mazda 3", 2.0f, FuelType.PETROL),
        FrameRule("BL5FW", "Ford / Mazda", "ford_mazda", "Mazda Axela / Mazda 3", 1.5f, FuelType.PETROL),
        FrameRule("BM5FS", "Ford / Mazda", "ford_mazda", "Mazda Axela / Mazda 3", 1.5f, FuelType.PETROL),
        FrameRule("GJ2FP", "Ford / Mazda", "ford_mazda", "Mazda Atenza / 6 Diesel", 2.2f, FuelType.DIESEL),
        FrameRule("GJ2FW", "Ford / Mazda", "ford_mazda", "Mazda Atenza Wagon Diesel", 2.2f, FuelType.DIESEL),
        FrameRule("GJEFP", "Ford / Mazda", "ford_mazda", "Mazda Atenza / Mazda 6", 2.0f, FuelType.PETROL),
        FrameRule("KE2FW", "Ford / Mazda", "ford_mazda", "Mazda CX-5 Diesel", 2.2f, FuelType.DIESEL),
        FrameRule("KEEFW", "Ford / Mazda", "ford_mazda", "Mazda CX-5", 2.0f, FuelType.PETROL),
        FrameRule("KF2P", "Ford / Mazda", "ford_mazda", "Mazda CX-5 Diesel", 2.2f, FuelType.DIESEL),
        FrameRule("KFEP", "Ford / Mazda", "ford_mazda", "Mazda CX-5", 2.0f, FuelType.PETROL),
        FrameRule("DE3FS", "Ford / Mazda", "ford_mazda", "Mazda Demio / Mazda 2", 1.3f, FuelType.PETROL),
        FrameRule("DJ3FS", "Ford / Mazda", "ford_mazda", "Mazda Demio / Mazda 2", 1.3f, FuelType.PETROL),
        // Mitsubishi
        FrameRule("CS3A", "Mitsubishi", "mitsubishi_main", "Mitsubishi Lancer Cedia", 1.6f, FuelType.PETROL),
        FrameRule("CY4A", "Mitsubishi", "mitsubishi_main", "Mitsubishi Galant Fortis / Lancer X", 2.0f, FuelType.PETROL),
        FrameRule("CW5W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Outlander", 2.4f, FuelType.PETROL),
        FrameRule("CW6W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Outlander V6", 3.0f, FuelType.PETROL),
        FrameRule("GF7W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Outlander", 2.0f, FuelType.PETROL),
        FrameRule("GF8W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Outlander", 2.4f, FuelType.PETROL),
        FrameRule("GG2W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Outlander PHEV", 2.0f, FuelType.HYBRID),
        FrameRule("GA2W", "Mitsubishi", "mitsubishi_main", "Mitsubishi RVR / ASX", 1.8f, FuelType.PETROL),
        FrameRule("CV5W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Delica D:5", 2.4f, FuelType.PETROL),
        FrameRule("CV1W", "Mitsubishi", "universal_diesel", "Mitsubishi Delica D:5 Diesel", 2.2f, FuelType.DIESEL),
        FrameRule("V93W", "Mitsubishi", "mitsubishi_main", "Mitsubishi Pajero", 3.0f, FuelType.PETROL),
        FrameRule("V98W", "Mitsubishi", "universal_diesel", "Mitsubishi Pajero DI-D", 3.2f, FuelType.DIESEL)
    )

    val brands: List<String> by lazy {
        templates.map { it.brand }.distinct()
    }

    fun modelsForBrand(brand: String): List<CatalogVehicleTemplate> =
        templates.filter { it.brand == brand }

    fun findById(id: String): CatalogVehicleTemplate? =
        templates.find { it.id == id }

    fun detectFromIdentifier(identifier: String?): VehicleIdentifierMatch? {
        val raw = identifier?.trim()?.uppercase()?.replace(" ", "") ?: return null
        if (raw.length < 3) return null

        // 1. Check JDM / Chassis Frame Number (e.g. "ZVW30-1234567", "DAA-ZVW30-1234567", "ZVW30")
        val segments = raw.split('-').filter { it.isNotBlank() }
        val candidateCode = when {
            segments.size >= 3 -> segments[1] // e.g., DAA-ZVW30-1234567 -> ZVW30
            segments.size == 2 && segments[0].length in 2..3 && segments[1].any { it.isLetter() } ->
                segments[1] // e.g., DAA-ZVW30 -> ZVW30
            segments.isNotEmpty() -> segments[0] // e.g., ZVW30-1234567 -> ZVW30 or ZVW301234567
            else -> raw
        }

        val frameMatch = frameRules
            .sortedByDescending { it.prefix.length }
            .firstOrNull { rule ->
                candidateCode == rule.prefix ||
                    (candidateCode.startsWith(rule.prefix) &&
                        (raw.contains('-') || (raw.length < 17 && candidateCode.length <= rule.prefix.length + 8)))
            }
        if (frameMatch != null) {
            return VehicleIdentifierMatch(
                brand = frameMatch.brand,
                templateId = frameMatch.templateId,
                suggestedName = "${frameMatch.modelTitle} (${frameMatch.prefix})",
                displacementLiters = frameMatch.displacementLiters,
                fuelType = frameMatch.fuelType,
                isFrameNumber = true
            )
        }

        // 2. Fallback to standard VIN WMI (first 3 characters)
        val wmi = raw.take(3)
        val vinBrand = when {
            wmi.startsWith("WVW") || wmi.startsWith("WV1") || wmi.startsWith("WV2") ||
                wmi.startsWith("WAU") || wmi.startsWith("WA1") || wmi.startsWith("TMB") ||
                wmi.startsWith("VSS") || wmi.startsWith("XW8") -> "VAG (VW / Audi / Skoda)"
            wmi.startsWith("JTD") || wmi.startsWith("JTE") || wmi.startsWith("JTH") ||
                wmi.startsWith("JTJ") || wmi.startsWith("SB1") || wmi.startsWith("NMT") ||
                wmi.startsWith("JTN") || wmi.startsWith("JTK") -> "Toyota / Lexus"
            wmi.startsWith("KMH") || wmi.startsWith("KNA") || wmi.startsWith("KNB") ||
                wmi.startsWith("KNC") || wmi.startsWith("XWE") || wmi.startsWith("Z94") ||
                wmi.startsWith("U5Y") || wmi.startsWith("TMA") -> "Hyundai / Kia"
            wmi.startsWith("WBA") || wmi.startsWith("WBS") || wmi.startsWith("WBX") -> "BMW / MINI"
            wmi.startsWith("WF0") || wmi.startsWith("1FA") || wmi.startsWith("1FT") ||
                wmi.startsWith("JMZ") || wmi.startsWith("JM1") || wmi.startsWith("JM6") ||
                wmi.startsWith("Z6F") -> "Ford / Mazda"
            wmi.startsWith("XTA") || wmi.startsWith("X7L") || wmi.startsWith("Z8N") ||
                wmi.startsWith("VF1") || wmi.startsWith("SJN") || wmi.startsWith("JN1") ||
                wmi.startsWith("JN8") || wmi.startsWith("5N1") -> "Lada / Renault / Nissan"
            wmi.startsWith("JHM") || wmi.startsWith("1HG") || wmi.startsWith("2HG") ||
                wmi.startsWith("5J6") || wmi.startsWith("SHH") -> "Honda / Acura"
            wmi.startsWith("JF1") || wmi.startsWith("JF2") || wmi.startsWith("4S3") ||
                wmi.startsWith("4S4") -> "Subaru"
            wmi.startsWith("JMB") || wmi.startsWith("JMY") || wmi.startsWith("4A3") -> "Mitsubishi"
            else -> null
        } ?: return null

        return VehicleIdentifierMatch(
            brand = vinBrand,
            isFrameNumber = false
        )
    }

    fun detectBrandFromVin(vin: String?): String? = detectFromIdentifier(vin)?.brand

    fun countOemPidsForTemplate(template: CatalogVehicleTemplate): Int {
        return template.oemPackIds.sumOf { packId ->
            OemPidCatalog.packs.find { it.id == packId }?.pids?.size ?: 0
        }
    }
}
