/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Zigbee Illuminance Library',
    name: 'illuminanceLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy',
    version: '3.0.0',
    documentationLink: ''
)
/*
 *  Zigbee Illuminance Library
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
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy
 *
 *                                   TODO:
*/

static String illuminanceLibVersion()   { '3.0.0' }
static String illuminanceLibStamp() { '2024/04/06 2:40 PM' }

metadata {
    // no capabilities
    // no attributes
    // no commands
    preferences {
        // no prefrences
    }
}

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5

void customParseIlluminanceCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final int value = hexStrToUnsignedInt(descMap.value)
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0
    handleIlluminanceEvent(lux)
}

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) {
    Map eventMap = [:]
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] }
    eventMap.name = 'illuminance'
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    eventMap.value  = illumCorrected
    eventMap.type = isDigital ? 'digital' : 'physical'
    eventMap.unit = 'lx'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastIllum = device.currentValue('illuminance') ?: 0
    Integer delta = Math.abs(lastIllum - illumCorrected)
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})"
        return
    }
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports
        state.lastRx['illumTime'] = now()
        sendEvent(eventMap)
    }
    else {         // queue the event
        eventMap.type = 'delayed'
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap])
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedIllumEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high']

/* groovylint-disable-next-line UnusedMethodParameter */
void customProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) {
    switch (dp) {
        case 0x01 : // on/off
            if (DEVICE_TYPE in  ['LightSensor']) {
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})"
            }
            else {
                sendSwitchEvent(fncmd)
            }
            break
        case 0x02 :
            if (DEVICE_TYPE in  ['LightSensor']) {
                handleIlluminanceEvent(fncmd)
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            }
            break
        case 0x04 : // battery
            sendBatteryPercentageEvent(fncmd)
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}

void customInitializeVars( boolean fullInit = false ) {
    logDebug "customInitializeVars()... fullInit = ${fullInit}"
    if (device.hasCapability('IlluminanceMeasurement')) {
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) }
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) }
    }
    if (device.hasCapability('IlluminanceMeasurement')) {
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) }
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) }
    }
}
