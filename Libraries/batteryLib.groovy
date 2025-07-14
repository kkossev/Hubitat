/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib',
    version: '3.2.3'
)
/*
 *  Zigbee Battery Library
 *
 *  Licensed Virtual the Apache License, Version 2.0
 *
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent'
 *
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline
 *                                   TODO: battery voltage low/high limits configuration
*/

static String batteryLibVersion()   { '3.2.3' }
static String batteryLibStamp() { '2025/07/13 7:45 PM' }

metadata {
    capability 'Battery'
    attribute  'batteryVoltage', 'number'
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024
    // no commands
    preferences {
        if (device && advancedOptions == true) {
            if ('BatteryVoltage' in DEVICE?.capabilities) {
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.'
            }
            if ('BatteryDelay' in DEVICE?.capabilities) {
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue)
            }
        }
    }
}

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']]

public void standardParsePowerCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final int rawValue = hexStrToUnsignedInt(descMap.value)
    if (descMap.attrId == '0020') { // battery voltage
        state.lastRx['batteryTime'] = new Date().getTime()
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1
        sendBatteryVoltageEvent(rawValue)
        if ((settings.voltageToPercent ?: false) == true) {
            sendBatteryVoltageEvent(rawValue, convertToPercent = true)
        }
    }
    else if (descMap.attrId == '0021') { // battery percentage
        state.lastRx['batteryTime'] = new Date().getTime()
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1
        if (isTuya()) {
            sendBatteryPercentageEvent(rawValue)
        }
        else {
            sendBatteryPercentageEvent((rawValue / 2) as int)
        }
    }
    else {
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) {
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V"
    final Date lastBattery = new Date()
    Map result = [:]
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G
    if (rawValue != 0 && rawValue != 255) {
        BigDecimal minVolts = 2.2
        BigDecimal maxVolts = 3.2
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts)
        int roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) { roundedPct = 1 }
        if (roundedPct > 100) { roundedPct = 100 }
        if (convertToPercent == true) {
            result.value = Math.min(100, roundedPct)
            result.name = 'battery'
            result.unit  = '%'
            result.descriptionText = "battery is ${roundedPct} %"
        }
        else {
            result.value = volts
            result.name = 'batteryVoltage'
            result.unit  = 'V'
            result.descriptionText = "battery is ${volts} Volts"
        }
        result.type = 'physical'
        result.isStateChange = true
        logInfo "${result.descriptionText}"
        sendEvent(result)
        sendEvent(name: 'lastBattery', value: lastBattery)
    }
    else {
        logWarn "ignoring BatteryResult(${rawValue})"
    }
}

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) {
    if ((batteryPercent as int) == 255) {
        logWarn "ignoring battery report raw=${batteryPercent}"
        return
    }
    final Date lastBattery = new Date()
    Map map = [:]
    map.name = 'battery'
    map.timeStamp = now()
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int)
    map.unit  = '%'
    map.type = isDigital ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    map.isStateChange = true
    //
    Object latestBatteryEvent = device.currentState('battery')
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now()
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}"
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) {
        // send it now!
        sendDelayedBatteryPercentageEvent(map)
        sendEvent(name: 'lastBattery', value: lastBattery)
    }
    else {
        int delayedTime = (settings?.batteryDelay as int) - timeDiff
        map.delayed = delayedTime
        map.descriptionText += " [delayed ${map.delayed} seconds]"
        map.lastBattery = lastBattery
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds"
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map])
    }
}

private void sendDelayedBatteryPercentageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
    sendEvent(name: 'lastBattery', value: map.lastBattery)
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedBatteryVoltageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
    sendEvent(name: 'lastBattery', value: map.lastBattery)
}

public int tuyaToBatteryLevel(int fncmd) {
    int rawValue = fncmd
    switch (fncmd) {
        case 0: rawValue = 100; break // Battery Full
        case 1: rawValue = 75;  break // Battery High
        case 2: rawValue = 50;  break // Battery Medium
        case 3: rawValue = 25;  break // Battery Low
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100%
    }
    return rawValue
}

public void handleTuyaBatteryLevel(int fncmd) {
    int rawValue = tuyaToBatteryLevel(fncmd)
    sendBatteryPercentageEvent(rawValue)
}

public void batteryInitializeVars( boolean fullInit = false ) {
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}"
    if (device.hasCapability('Battery')) {
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) }
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) }
    }
}

public List<String> batteryRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage
    return cmds
}
