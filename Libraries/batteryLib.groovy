/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Zigbee Battery Library',
    name: 'batteryLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy',
    version: '3.0.0',
    documentationLink: ''
)
/*
 *  Zigbee Level Library
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
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy
 *
 *                                   TODO: battery voltage low/high limits configuration
*/

static String batteryLibVersion()   { '3.0.0' }
static String batteryLibStamp() { '2024/04/06 11:49 PM' }

metadata {
    capability 'Battery'
    attribute 'batteryVoltage', 'number'
    // no commands
    preferences {
        if (device) {
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>'
        }
    }
}

void customParsePowerCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (descMap.attrId in ['0020', '0021']) {
        state.lastRx['batteryTime'] = new Date().getTime()
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1
    }
    if (this.respondsTo('customParsePowerCluster')) {
        customParsePowerCluster(descMap)
    }
    else {
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

    final int rawValue = hexStrToUnsignedInt(descMap.value)
    if (descMap.attrId == '0020') {
        sendBatteryVoltageEvent(rawValue)
        if ((settings.voltageToPercent ?: false) == true) {
            sendBatteryVoltageEvent(rawValue, convertToPercent = true)
        }
    }
    else if (descMap.attrId == '0021') {
        sendBatteryPercentageEvent(rawValue * 2)
    }
    else {
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) {
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V"
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
    }
    else {
        logWarn "ignoring BatteryResult(${rawValue})"
    }
}

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) {
    if ((batteryPercent as int) == 255) {
        logWarn "ignoring battery report raw=${batteryPercent}"
        return
    }
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
    }
    else {
        int delayedTime = (settings?.batteryDelay as int) - timeDiff
        map.delayed = delayedTime
        map.descriptionText += " [delayed ${map.delayed} seconds]"
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds"
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map])
    }
}

private void sendDelayedBatteryPercentageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedBatteryVoltageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}
