package com.example.obd.protocol

import com.example.obd.model.OemBrandPack
import com.example.obd.model.OemPidSpec

/**
 * Curated OEM & extended CAN UDS/OBD2 PID packs for popular platforms.
 * Users can browse packs in SensorsScreen and import them into Room with one tap.
 */
object OemPidCatalog {

    val packs: List<OemBrandPack> = listOf(
        OemBrandPack(
            id = "vag",
            brandName = "VAG (VW / Audi / Skoda)",
            subtitleRu = "Темп. масла, давление турбины, температура АКПП DSG",
            subtitleEn = "Oil temp, boost pressure & DSG gearbox temp",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Температура масла АКПП (DSG)",
                    nameEn = "DSG Gearbox Oil Temp",
                    shortName = "DSG Oil",
                    modeAndPid = "221E1C",
                    header = "7E1",
                    equation = "((A*256)+B)/10-273.1",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 115f,
                    criticalHigh = 130f
                ),
                OemPidSpec(
                    nameRu = "Фактическое давление наддува (VAG)",
                    nameEn = "Actual Boost Pressure (VAG)",
                    shortName = "Boost",
                    modeAndPid = "22114F",
                    header = "7E0",
                    equation = "((A*256)+B)*0.0365",
                    unit = "кПа",
                    minValue = 50f,
                    maxValue = 300f,
                    warningHigh = 245f,
                    criticalHigh = 275f
                ),
                OemPidSpec(
                    nameRu = "Давление в топливной рампе (TSI)",
                    nameEn = "Fuel Rail High Pressure (TSI)",
                    shortName = "Rail",
                    modeAndPid = "222014",
                    header = "7E0",
                    equation = "((A*256)+B)/100",
                    unit = "бар",
                    minValue = 0f,
                    maxValue = 250f
                )
            )
        ),
        OemBrandPack(
            id = "toyota_hybrid",
            brandName = "Toyota / Lexus Hybrid",
            subtitleRu = "Батарея ВВБ (SOC %), темп. ATF АКПП, инвертор",
            subtitleEn = "HV Battery SOC %, ATF Temp & Inverter Temp",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Заряд ВВБ (HV Battery SOC)",
                    nameEn = "HV Battery State of Charge",
                    shortName = "HV SOC",
                    modeAndPid = "21C3",
                    header = "7E2",
                    equation = "A*0.392",
                    unit = "%",
                    minValue = 0f,
                    maxValue = 100f
                ),
                OemPidSpec(
                    nameRu = "Температура ATF (Toyota)",
                    nameEn = "Transmission Fluid Temp (Toyota)",
                    shortName = "ATF",
                    modeAndPid = "2182",
                    header = "7E0",
                    equation = "((((A*256)+B)*(7/100)-400)/10)",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 112f,
                    criticalHigh = 128f
                ),
                OemPidSpec(
                    nameRu = "Температура инвертора MG1",
                    nameEn = "Inverter MG1 Temperature",
                    shortName = "Inv T",
                    modeAndPid = "21C4",
                    header = "7E2",
                    equation = "A-40",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 140f,
                    warningHigh = 95f,
                    criticalHigh = 115f
                )
            )
        ),
        OemBrandPack(
            id = "hyundai_kia",
            brandName = "Hyundai / Kia",
            subtitleRu = "Температура АКПП, давление топлива GDI, угол CVVT",
            subtitleEn = "ATF temp, GDI rail pressure & CVVT timing",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Температура масла АКПП (H/K)",
                    nameEn = "AT Oil Temperature (H/K)",
                    shortName = "ATF H/K",
                    modeAndPid = "22F102",
                    header = "7E1",
                    equation = "A-40",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 110f,
                    criticalHigh = 125f
                ),
                OemPidSpec(
                    nameRu = "Заряд аккумулятора АКБ (BMS)",
                    nameEn = "12V Battery SOC (BMS)",
                    shortName = "12V SOC",
                    modeAndPid = "22B003",
                    header = "7E0",
                    equation = "A",
                    unit = "%",
                    minValue = 0f,
                    maxValue = 100f
                )
            )
        ),
        OemBrandPack(
            id = "bmw",
            brandName = "BMW (E / F / G-Series)",
            subtitleRu = "Темп. масла (Mode 22), температура масла АКПП ZF",
            subtitleEn = "Engine oil temp & ZF gearbox fluid temp",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Температура масла ДВС (BMW)",
                    nameEn = "BMW Engine Oil Temp",
                    shortName = "BMW Oil",
                    modeAndPid = "225801",
                    header = "7E0",
                    equation = "A-48",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 122f,
                    criticalHigh = 135f
                ),
                OemPidSpec(
                    nameRu = "Температура АКПП ZF8HP",
                    nameEn = "ZF 8HP Gearbox Oil Temp",
                    shortName = "ZF ATF",
                    modeAndPid = "22D905",
                    header = "7E1",
                    equation = "A-40",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 110f,
                    criticalHigh = 125f
                )
            )
        ),
        OemBrandPack(
            id = "ford_mazda",
            brandName = "Ford / Mazda (Skyactiv / EcoBoost)",
            subtitleRu = "Датчик детонации, темп. АКПП, напряжение заряда",
            subtitleEn = "ATF temp, boost pressure & knock retard",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Температура АКПП (Ford/Mazda)",
                    nameEn = "Transmission Fluid Temp (F/M)",
                    shortName = "ATF F/M",
                    modeAndPid = "221E1C",
                    header = "7E1",
                    equation = "((A*256)+B)/16",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 150f,
                    warningHigh = 110f,
                    criticalHigh = 126f
                ),
                OemPidSpec(
                    nameRu = "Коррекция детонации (Knock)",
                    nameEn = "Knock Retard (Ignition)",
                    shortName = "Knock",
                    modeAndPid = "221746",
                    header = "7E0",
                    equation = "(A*256+B)*-0.25",
                    unit = "°",
                    minValue = -15f,
                    maxValue = 5f
                )
            )
        ),
        OemBrandPack(
            id = "diesel_dpf",
            brandName = "Diesel DPF / Common Rail",
            subtitleRu = "Заполненность сажевого фильтра (DPF), дифф. давление, EGR",
            subtitleEn = "DPF soot load %, DPF temp & rail pressure",
            pids = listOf(
                OemPidSpec(
                    nameRu = "Давление в рампе (SAE 0123)",
                    nameEn = "Fuel Rail Gauge Pressure (SAE 0123)",
                    shortName = "Rail kPa",
                    modeAndPid = "0123",
                    header = "7DF",
                    equation = "((A*256)+B)*10",
                    unit = "кПа",
                    minValue = 0f,
                    maxValue = 220000f
                ),
                OemPidSpec(
                    nameRu = "Температура сажевого DPF (SAE 017C)",
                    nameEn = "DPF Exhaust Temp (SAE 017C)",
                    shortName = "DPF Temp",
                    modeAndPid = "017C",
                    header = "7DF",
                    equation = "((A*256)+B)/10-40",
                    unit = "°C",
                    minValue = -20f,
                    maxValue = 850f,
                    warningHigh = 680f,
                    criticalHigh = 780f
                ),
                OemPidSpec(
                    nameRu = "Заданное положение EGR (SAE 012C)",
                    nameEn = "Commanded EGR (SAE 012C)",
                    shortName = "EGR",
                    modeAndPid = "012C",
                    header = "7DF",
                    equation = "A*100/255",
                    unit = "%",
                    minValue = 0f,
                    maxValue = 100f
                )
            )
        )
    )

    fun findById(id: String): OemBrandPack? = packs.find { it.id == id }
}
