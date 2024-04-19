/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Zigbee Temperature Library',
    name: 'temperatureLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy',
    version: '3.0.1',
    documentationLink: ''
)
/*
 *  Zigbee Temperature Library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix
 *
 *                                   TODO:
*/

static String temperatureLibVersion()   { '3.0.1' }
static String temperatureLibStamp() { '2024/04/19 9:17 PM' }

metadata {
    capability 'TemperatureMeasurement'
    // no commands
    preferences {
        if (device) {
            if (settings?.minReportingTime == null) {
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME
            }
            if (settings?.minReportingTime == null) {
                if (deviceType != 'mmWaveSensor') {
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME
                }
            }
        }
    }
}

void customParseTemperatureCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToSignedInt(descMap.value)
    handleTemperatureEvent(value / 100.0F as BigDecimal)
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
    if (Math.abs(lastTemp - tempCorrected) < 0.1) {
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})"
        return
    }
    eventMap.type = isDigital == true ? 'digital' : 'physical'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    if (state.states['isRefresh'] == true) {
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
    return cmds
}
