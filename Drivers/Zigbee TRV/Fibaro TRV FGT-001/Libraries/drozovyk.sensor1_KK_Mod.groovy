//#include drozovyk.encapsulation1

library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Notification helpers",
    name: "sensor1_KK_Mod",
    namespace: "drozovyk"   
)

/*
SI prefixes

power of 10
−24            yocto    y
−21            zepto    z
−18            atto     a
−15            femto    f
−12            pico     p
 −9            nano     n
 −6            micro    µ
 −3            milli    m
 −2            centi    c
 −1            deci     d
 +1            deca     da
 +2            hecto    h
 +3            kilo     k
 +6            mega     M
 +9            giga     G
+12            tera     T
+15            peta     P
+18            exa      E
+21            zetta    Z
+24            yotta    Y

SI base units

Unit              Symbol
metre               m
square              metre m2
cubic               metre m3
micron              µ
litre               l
gram                g
tonne               t
second              s
erg                 erg
dyne                dyn
degree Celsius      °C
degree absolute     °K
calorie             cal
bar                 bar
hour                h
ampere              A
volt                V
watt                W
ohm                 Ω
coulomb             C
farad               F
henry               H
hertz               Hz
poise               P
newton              N
candela             cd
lux                 lx
lumen               lm
stilb               sb
*/

@Field static Map          sensorTypeScales = [
    (0x00): [name: "Reserved"],
    (0x01): [name: "Air temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x02): [name: "General purpose", // deprecated
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: ""],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x03): [name: "Illuminance",
             0: [attribute: null,           unit: "%"],
             1: [attribute: "illuminance",  unit: "lx"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x04): [name: "Power",
             0: [attribute: "power",        unit: "W"],
             1: [attribute: "power",        unit: "Btu/h"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x05): [name: "Humidity",
             0: [attribute: "humidity",     unit: "%"],
             1: [attribute: null,           unit: "g/m³"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x06): [name: "Velocity",
             0: [attribute: null,           unit: "m/s"],
             1: [attribute: null,           unit: "Mph"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x07): [name: "Direction",
             0: [attribute: null,           unit: "degrees"], //   0 = no wind, 90 = east, 180 = south, 270 = west  and 360 = north
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x08): [name: "Atmospheric pressure",
             0: [attribute: null,           unit: "kPa"],
             1: [attribute: null,           unit: "Inches of Mercury"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x09): [name: "Barometric pressure",
             0: [attribute: null,           unit: "kPa"],
             1: [attribute: null,           unit: "Inches of Mercury"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0A): [name: "Solar radiation",
             0: [attribute: null,           unit: "W/m²"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0B): [name: "Dew point", 
             0: [attribute: null,           unit: "°C"],
             1: [attribute: null,           unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0C): [name: "Rain rate",
             0: [attribute: null,           unit: "mm/h"],
             1: [attribute: null,           unit: "in/h"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0D): [name: "Tide level",
             0: [attribute: null,           unit: "m"],
             1: [attribute: null,           unit: "ft"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0E): [name: "Weight",
             0: [attribute: null,           unit: "kg"],
             1: [attribute: null,           unit: "lb"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x0F): [name: "Voltage",
             0: [attribute: "voltage",      unit: "V"],
             1: [attribute: "voltage",      unit: "mV"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x10): [name: "Current",
             0: [attribute: "amperage",     unit: "A"],
             1: [attribute: "amperage",     unit: "mA"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x11): [name: "Carbon dioxide CO2-level",
             0: [attribute: null,           unit: "ppm"], // Parts/million
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x12): [name: "Air flow", 
             0: [attribute: null,           unit: "m³/h"],
             1: [attribute: null,           unit: "cfm"], // Cubic feet per minute
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x13): [name: "Tank capacity",
             0: [attribute: null,           unit: "l"],
             1: [attribute: null,           unit: "m³"],
             2: [attribute: null,           unit: "Gallons"],
             3: [attribute: null,           unit: null]
            ],
    (0x14): [name: "Distance",
             0: [attribute: null,           unit: "m"],
             1: [attribute: null,           unit: "cm"],
             2: [attribute: null,           unit: "ft"],
             3: [attribute: null,           unit: null]
            ],
    (0x15): [name: "Angle position", // deprecated
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: "deg"], // Degrees relative to north pole of standing eye view
             2: [attribute: null,           unit: "deg"], // Degrees relative to north pole of standing eye view
             3: [attribute: null,           unit: null]
            ],
    (0x16): [name: "Rotation speed",
             0: [attribute: null,           unit: "rpm"],
             1: [attribute: null,           unit: "Hz"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x17): [name: "Water temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x18): [name: "Soil temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x19): [name: "Seismic Intensity",
             0: [attribute: null,           unit: "Mercalli"],
             1: [attribute: null,           unit: "European Macroseismic"],
             2: [attribute: null,           unit: "Liedu"],
             3: [attribute: null,           unit: "Shindo"]
            ],
    (0x1A): [name: "Seismic magnitude",
             0: [attribute: null,           unit: "Local"],
             1: [attribute: null,           unit: "Moment"],
             2: [attribute: null,           unit: "Surface wave"],
             3: [attribute: null,           unit: "Body wave"]
            ],
    (0x1B): [name: "Ultraviolet",
             0: [attribute: null,           unit: "UV index"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x1C): [name: "Electrical resistivity",
             0: [attribute: null,           unit: "Ω"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x1D): [name: "Electrical conductivity",
             0: [attribute: null,           unit: "S/m"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x1E): [name: "Loudness",
             0: [attribute: null,           unit: "dB"],
             1: [attribute: null,           unit: "dBA"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x1F): [name: "Moisture",
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: "m³/m³"], // Volume water content
             2: [attribute: null,           unit: "kΩ"],    // Impedance
             3: [attribute: null,           unit: "aw"]     // Water activity
            ],
    (0x20): [name: "Frequency",
             0: [attribute: "frequency",    unit: "Hz"],    // MUST be used until 2.147483647 GHz
             1: [attribute: "frequency",    unit: "kHz"],   // MUST be used after 2.147483647 GHz
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x21): [name: "Time",
             0: [attribute: null,           unit: "s"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x22): [name: "Target temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x23): [name: "Particulate Matter 2.5", 
             0: [attribute: null,           unit: "mol/m³"],   // Mole per cubic meter
             1: [attribute: null,           unit: "µg/m³"],    // Microgram per cubic meter
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x24): [name: "Formaldehyde CH2O-level", 
             0: [attribute: null,           unit: "mol/m³"],   // Mole per cubic meter
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x25): [name: "Radon concentration", 
             0: [attribute: null,           unit: "bq/m³"],    // Becquerel per cubic meter
             1: [attribute: null,           unit: "pCi/l"],    // Picocuries per liter
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x26): [name: "Methane (CH4) density", 
             0: [attribute: null,           unit: "mol/m³"],   // Mole per cubic meter
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x27): [name: "Volatile Organic Compound level", 
             0: [attribute: null,           unit: "mol/m³"],
             1: [attribute: null,           unit: "ppm"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x28): [name: "Carbon monoxide (CO) level", 
             0: [attribute: null,           unit: "mol/m³"],
             1: [attribute: null,           unit: "ppm"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x29): [name: "Soil humidity", 
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2A): [name: "Soil reactivity", 
             0: [attribute: null,           unit: "pH"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2B): [name: "Soil salinity", 
             0: [attribute: null,           unit: "mol/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2C): [name: "Heart rate", 
             0: [attribute: null,           unit: "bpm"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2D): [name: "Blood pressure", 
             0: [attribute: null,           unit: "mmHg"], // Systolic (upper)
             1: [attribute: null,           unit: "mmHg"], // Diastolic (lower)
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2E): [name: "Muscle mass", 
             0: [attribute: null,           unit: "kg"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x2F): [name: "Fat mass", 
             0: [attribute: null,           unit: "kg"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x30): [name: "Bone mass", 
             0: [attribute: null,           unit: "kg"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x31): [name: "Total body water (TBW)", 
             0: [attribute: null,           unit: "kg"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x32): [name: "Basis metabolic rate (BMR)", 
             0: [attribute: null,           unit: "J"], // Joule
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x33): [name: "Body Mass Index (BMI)", 
             0: [attribute: null,           unit: "BMI Index"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x34): [name: "Acceleration X-axis", 
             0: [attribute: null,           unit: "m/s²"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x35): [name: "Acceleration Y-axis", 
             0: [attribute: null,           unit: "m/s²"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x36): [name: "Acceleration Z-axis", 
             0: [attribute: null,           unit: "m/s²"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x37): [name: "Smoke density", 
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x38): [name: "Water flow", 
             0: [attribute: null,           unit: "l/h"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x39): [name: "Water pressure", 
             0: [attribute: null,           unit: "kPa"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3A): [name: "RF signal strength", 
             0: [attribute: null,           unit: "% RSSI"],
             1: [attribute: null,           unit: "dBm"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3B): [name: "Particulate Matter 10", 
             0: [attribute: null,           unit: "mol/m³"],
             1: [attribute: null,           unit: "µg/m³"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3C): [name: "Respiratory rate", 
             0: [attribute: null,           unit: "bpm"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3D): [name: "Relative Modulation level", 
             0: [attribute: null,           unit: "%"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3E): [name: "Boiler water temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x3F): [name: "Domestic Hot Water (DHW) temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x40): [name: "Outside temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x41): [name: "Exhaust temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x42): [name: "Water Chlorine level", 
             0: [attribute: null,           unit: "mg/l"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x43): [name: "Water acidity", 
             0: [attribute: null,           unit: "pH"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x44): [name: "Water Oxidation reduction potential", 
             0: [attribute: null,           unit: "mV"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x45): [name: "Water Oxidation reduction potential", 
             0: [attribute: null,           unit: ""], // Unitless
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x46): [name: "Motion Direction", 
             0: [attribute: null,           unit: "deg"], // 0 to 360 degrees 0 = no motion detected 90 = east, 180 = south, 270 = west and 360 = north
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x47): [name: "Applied force on the sensor", 
             0: [attribute: null,           unit: "N"], // Newton
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x48): [name: "Return Air temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x49): [name: "Supply Air temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4A): [name: "Condenser Coil temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4B): [name: "Evaporator Coil temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4C): [name: "Liquid Line temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4D): [name: "Discharge Line temperature", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4E): [name: "Suction (input pump/compressor) Pressure", 
             0: [attribute: null,           unit: "kPa"],
             1: [attribute: null,           unit: "psi"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x4F): [name: "Discharge (output pump/compressor) Pressure", 
             0: [attribute: null,           unit: "kPa"],
             1: [attribute: null,           unit: "psi"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x50): [name: "Defrost temperature (sensor used to decide when to defrost)", 
             0: [attribute: "temperature",  unit: "°C"],
             1: [attribute: "temperature",  unit: "℉"],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x51): [name: "Ozone (O3)", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x52): [name: "Sulfur dioxide (SO2)", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x53): [name: "Nitrogen dioxide (NO2)", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x54): [name: "Ammonia (NH3)", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x55): [name: "Lead (Pb)", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ],
    (0x56): [name: "Particulate Matter 1", 
             0: [attribute: null,           unit: "μg/m³"],
             1: [attribute: null,           unit: null],
             2: [attribute: null,           unit: null],
             3: [attribute: null,           unit: null]
            ]
]

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd, Short ep = 0) {
    sensorInfo        = sensorTypeScales[cmd.sensorType as Integer]    
    sensorScale       = sensorInfo[cmd.scale as Integer]
    sensorAttribute   = sensorScale.attribute
    sensorUnit        = sensorScale.unit
    
    logInfo("(${ep}) Sensor multilevel V5 report: ${sensorInfo.name}:${sensorAttribute} ${cmd.scaledSensorValue}${sensorUnit}")
    
    if(sensorAttribute != null) {
        def event = [
            name: sensorAttribute, 
            value: cmd.scaledSensorValue, 
            unit: sensorUnit, 
            descriptionText: "${sensorInfo.name} ${cmd.scaledSensorValue}${sensorUnit}"
        ]
        
        // Check for refresh enhancement (driver sets state.refreshInProgress and state.refreshTimestamp)
        if(state?.refreshInProgress && state?.refreshTimestamp && 
           (now() - state.refreshTimestamp) <= 15000) {
            event.isStateChange = true
            event.descriptionText = "${event.descriptionText} [refresh]"
        }
        
        parse([event], ep)
    }
    else {
        logWarn("Unexpected sensor multilevel V5 report type/scale from endpoint (${ep}): ${cmd}")
    }
}

void zwaveEvent(hubitat.zwave.commands.sensormultilevelv11.SensorMultilevelReport cmd, Short ep = 0) {
    sensorInfo        = sensorTypeScales[cmd.sensorType as Integer]    
    sensorScale       = sensorInfo[cmd.scale as Integer]
    sensorAttribute   = sensorScale.attribute
    sensorUnit        = sensorScale.unit
    
    logInfo("(${ep}) Sensor multilevel V11 report: ${sensorInfo.name}:${sensorAttribute} ${cmd.scaledSensorValue}${sensorUnit}")
    
    if(sensorAttribute != null) {
        def event = [
            name: sensorAttribute, 
            value: cmd.scaledSensorValue, 
            unit: sensorUnit, 
            descriptionText: "${sensorInfo.name} ${cmd.scaledSensorValue}${sensorUnit}"
        ]
        
        // Check for refresh enhancement (driver sets state.refreshInProgress and state.refreshTimestamp)
        if(state?.refreshInProgress && state?.refreshTimestamp && 
           (now() - state.refreshTimestamp) <= 15000) {
            event.isStateChange = true
            event.descriptionText = "${event.descriptionText} [refresh]"
        }
        
        parse([event], ep)
    }
    else {
        logWarn("Unexpected sensor multilevel V11 report type/scale from endpoint (${ep}): ${cmd}")
    }
}

private void genericSensorEvent(Number sensorValue, Integer sensorTypeAndScale, endPointDevice) {
    Integer sensorType      = (sensorTypeAndScale >> 8) & 0xFF
    Integer sensorScaleType = (sensorTypeAndScale >> 0) & 0xFF
    
    sensorInfo        = sensorTypeScales[sensorType as Integer]    
    sensorScale       = sensorInfo[sensorScaleType as Integer]
    sensorAttribute   = sensorScale.attribute
    sensorUnit        = sensorScale.unit
    
    logInfo("(${endPointDevice}) Sensor generic report: ${sensorInfo.name}:${sensorAttribute} ${sensorValue}${sensorUnit}")
    
    if(sensorAttribute != null) {
        def event = [
            name: sensorAttribute, 
            value: sensorValue, 
            unit: sensorUnit, 
            descriptionText: "${sensorInfo.name} ${sensorValue}${sensorUnit}"
        ]
        
        // Check for refresh enhancement (driver sets state.refreshInProgress and state.refreshTimestamp)
        if(state?.refreshInProgress && state?.refreshTimestamp && 
           (now() - state.refreshTimestamp) <= 15000) {
            event.isStateChange = true
            event.descriptionText = "${event.descriptionText} [refresh]"
        }
        
        parse([event], endPointDevice)
    }
    else {
        logWarn("Unexpected sensor generic report type/scale from endpoint (${endPointDevice}): ${sensorValue}, ${sensorType}, ${sensorScaleType}")
    }
}
