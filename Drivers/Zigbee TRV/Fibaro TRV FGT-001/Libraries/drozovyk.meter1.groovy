library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Notification helpers",
    name: "meter1",
    namespace: "drozovyk"   
)

@Field static Map          meterTypeScales = [
    (0x01): [name: "Electric", 
             0: [attribute: "energy",      unit: "kWh"],
             1: [attribute: null,          unit: "kVAh"],
             2: [attribute: "power",       unit: "W"],
             3: [attribute: null,          unit: "Pulse count"],
             4: [attribute: "voltage",     unit: "V"], 
             5: [attribute: "amperage",    unit: "A"],
             6: [attribute: "powerFactor", unit: "cos(φ)"], // custom attribute for Hubitat; no built-in in capabilities
             7: [attribute: null,       unit: "", extended: [  // See 'scale2' (V4 and up)
               0: [attribute: null,       unit: "kVar"], 
               1: [attribute: null,       unit: "kVarh"]
             ]]
            ],
    (0x02): [name: "Gas",
             0: [attribute: null,       unit: "m³"],
             1: [attribute: null,       unit: "ft³"],
             3: [attribute: null,       unit: "Pulse count"],
             7: [attribute: null,       unit: "", extended: []] // See 'scale2' (V4 and up)
            ],
    (0x03): [name: "Water",
             0: [attribute: null,       unit: "m³"],
             1: [attribute: null,       unit: "ft³"],
             2: [attribute: null,       unit: "g(US)"],
             3: [attribute: null,       unit: "Pulse count"],
             7: [attribute: null,       unit: "", extended: []] // See 'scale2' (V4 and up)
            ],
    (0x04): [name: "Heating",
             0: [attribute: null,       unit: "kWh"]
            ],
    (0x05): [name: "Cooling",
             0: [attribute: null,       unit: "kWh"]
            ]    
]

@Field static int meterElectricEnergy      = 0x0100
@Field static int meterElectricPower       = 0x0102
@Field static int meterElectricVoltage     = 0x0104
@Field static int meterElectricCurrent     = 0x0105
@Field static int meterElectricPowerFactor = 0x0106

void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd, Short ep = 0) {
    meterInfo        = meterTypeScales[cmd.meterType as Integer]    
    meterScale       = meterInfo[cmd.scale as Integer]
    meterAttribute   = meterScale.attribute
    meterUnit        = meterScale.unit
    
    if(cmd?.scaledPreviousMeterValue != null) {
        logInfo("(${ep}) Meter V3 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledPreviousMeterValue}${meterUnit} -> ${cmd.scaledMeterValue}${meterUnit}")
    }
    else {
        logInfo("(${ep}) Meter V3 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}")
    }    
    
    if(meterAttribute != null) {
        parse([[name: meterAttribute, value: "${cmd.scaledMeterValue}", unit: meterUnit, descriptionText: "${meterInfo.name} ${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}"]], ep)
    }
    else {
        logWarn("Unexpected meter V3 report type/scale from endpoint (${ep}): ${cmd}")
    }
}

void zwaveEvent(hubitat.zwave.commands.meterv4.MeterReport cmd, Short ep = 0) {
    meterInfo        = meterTypeScales[cmd.meterType as Integer]    
    meterScale       = meterInfo[cmd.scale as Integer]
    if(7 == cmd.scale) {
        meterScale   = meterScale.extended[cmd.scale2 as Integer]
    }
    meterAttribute   = meterScale.attribute
    meterUnit        = meterScale.unit
    
    if(cmd?.scaledPreviousMeterValue != null) {
        logInfo("(${ep}) Meter V4 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledPreviousMeterValue}${meterUnit} -> ${cmd.scaledMeterValue}${meterUnit}")
    }
    else {
        logInfo("(${ep}) Meter V4 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}")
    }    
    
    if(meterAttribute != null) {
        parse([[name: meterAttribute, value: "${cmd.scaledMeterValue}", unit: meterUnit, descriptionText: "${meterInfo.name} ${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}"]], ep)
    }
    else {
        logWarn("Unexpected meter V4 report type/scale from endpoint (${ep}): ${cmd}")
    }
}

void zwaveEvent(hubitat.zwave.commands.meterv5.MeterReport cmd, Short ep = 0) {
    meterInfo        = meterTypeScales[cmd.meterType as Integer]    
    meterScale       = meterInfo[cmd.scale as Integer]
    if(7 == cmd.scale) {
        meterScale   = meterScale.extended[cmd.scale2 as Integer]
    }
    String meterAttribute   = meterScale.attribute
    String meterUnit        = meterScale.unit
    
    if(cmd?.scaledPreviousMeterValue != null) {
        logInfo("(${ep}) Meter V5 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledPreviousMeterValue}${meterUnit} -> ${cmd.scaledMeterValue}${meterUnit}")
    }
    else {
        logInfo("(${ep}) Meter V5 report: ${meterInfo.name}:${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}")
    }    
    
    if(meterAttribute != null) {
        parse([[name: meterAttribute, value: "${cmd.scaledMeterValue}", unit: meterUnit, descriptionText: "${meterInfo.name} ${meterAttribute} ${cmd.scaledMeterValue}${meterUnit}"]], ep)
    }
    else {
        logWarn("Unexpected meter V5 report type/scale from endpoint (${ep}): ${cmd}")
    }
}

private void genericMeterEvent(BigDecimal meterValue, Integer meterTypeAndScale, com.hubitat.app.DeviceWrapper endPointDevice) {
    Integer meterType      = (meterTypeAndScale >> 8) & 0xFF
    Integer meterScaleType = (meterTypeAndScale >> 0) & 0xFF
    
    meterInfo        = meterTypeScales[meterType]
    meterScale       = meterInfo[meterScaleType]
    
    String meterAttribute   = meterScale.attribute
    String meterUnit        = meterScale.unit
    
    logInfo("(${endPointDevice}) Meter generic report: ${meterInfo.name}:${meterAttribute} ${meterValue}${meterUnit}")
    
    if(meterAttribute != null) {
        parse([[name: meterAttribute, value: meterValue, unit: meterUnit, descriptionText: "${meterInfo.name} ${meterAttribute} ${meterValue}${meterUnit}"]], endPointDevice)
    }
    else {
        logWarn("Unexpected meter generic report type/scale from endpoint (${endPointDevice}): ${meterValue}, ${meterType}, ${meterScaleType}")
    }
}
