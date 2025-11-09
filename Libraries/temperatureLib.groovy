/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '',
    version: '3.3.1'
)
/*
 *  Zigbee Temperature Library
 *
 *  Licensed Virtual the Apache License, Version 2.0
 *
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh()
 * ver. 3.2.1  2024-06-07 kkossev  - excluded maxReportingTime for mmWaveSensor and Thermostat
 * ver. 3.2.2  2024-07-06 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug
 * ver. 3.2.3  2024-07-18 kkossev  - added 'ReportingConfiguration' capability check for minReportingTime and maxReportingTime
 * ver. 3.3.0  2025-09-15 kkossev  - commonLib 4.0.0 allignment; added temperatureOffset
 * ver. 3.3.1  2025-10-31 kkossev  - bugfix: isRefresh was not checked if temperature delta < 0.1
 *
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent())
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster()
*/

static String temperatureLibVersion()   { '3.3.1' }
static String temperatureLibStamp() { '2025/10/31 3:13 PM' }

metadata {
    capability 'TemperatureMeasurement'
    // no commands
    preferences {
        if (device && advancedOptions == true) {
            if ('ReportingConfiguration' in DEVICE?.capabilities) {
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) {
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME
                }
            }
        }
        input name: 'temperatureOffset', type: 'decimal', title: '<b>Temperature Offset</b>', description: '<i>Adjust temperature by this many degrees</i>', range: '-100..100', defaultValue: 0
   }
}

void standardParseTemperatureCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (descMap.attrId == '0000') {
        int value = hexStrToSignedInt(descMap.value)
        handleTemperatureEvent(value / 100.0F as BigDecimal)
    }
    else {
        logWarn "standardParseTemperatureCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}"
    }
}

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) {
    Map eventMap = [:]
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP)
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] }
    eventMap.name = 'temperature'
    if (location.temperatureScale == 'F') {
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP)
        eventMap.unit = '\u00B0F'
    }
    else {
        eventMap.unit = '\u00B0C'
    }
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP)
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}"
    
    boolean isRefresh = state.states['isRefresh'] == true
    
    if (!isRefresh && Math.abs(lastTemp - tempCorrected) < 0.1) {
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})"
        return
    }
    eventMap.type = isDigital == true ? 'digital' : 'physical'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    if (isRefresh) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true
    }
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports
        state.lastRx['tempTime'] = now()
        sendEvent(eventMap)
    }
    else {         // queue the event
        eventMap.type = 'delayed'
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap])
    }
}

void sendDelayedTempEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

List<String> temperatureLibInitializeDevice() {
    List<String> cmds = []
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature
    logDebug "temperatureLibInitializeDevice() cmds=${cmds}"
    return cmds
}

List<String> temperatureRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200)
    return cmds
}
