/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Humidity Library', name: 'humidityLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/humidityLib.groovy', documentationLink: '',
    version: '3.2.2'
)
/*
 *  Zigbee Humidity Library
 *
 *  Licensed Virtual the Apache License, Version 2.0
 *
 * ver. 3.0.0  2024-04-06 kkossev  - added humidityLib.groovy
 * ver. 3.2.0  2024-05-29 kkossev  - commonLib 3.2.0 allignment; added humidityRefresh()
 * ver. 3.2.2  2024-07-02 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug
 *
 *                                   TODO:
*/

static String humidityLibVersion()   { '3.2.2' }
static String humidityLibStamp() { '2024/07/02 11:17 PM' }

metadata {
    capability 'RelativeHumidityMeasurement'
    // no commands
    preferences {
        // the minReportingTime and maxReportingTime are already defined in the temperatureLib.groovy
    }
}

void standardParseHumidityCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (descMap.attrId == '0000') {
        final int value = hexStrToUnsignedInt(descMap.value)
        handleHumidityEvent(value / 100.0F as BigDecimal)
    }
    else {
        logWarn "standardParseHumidityCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}"
    }
}

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) {
    Map eventMap = [:]
    BigDecimal humidity = safeToBigDecimal(humidityPar)
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] }
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0)
    if (humidity <= 0.0 || humidity > 100.0) {
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})"
        return
    }
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP)
    eventMap.name = 'humidity'
    eventMap.unit = '% RH'
    eventMap.type = isDigital == true ? 'digital' : 'physical'
    //eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedHumidityEvent')
        state.lastRx['humiTime'] = now()
        sendEvent(eventMap)
    }
    else {
        eventMap.type = 'delayed'
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap])
    }
}

void sendDelayedHumidityEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

List<String> humidityLibInitializeDevice() {
    List<String> cmds = []
    cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity
    return cmds
}

List<String> humidityRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200)
    return cmds
}
