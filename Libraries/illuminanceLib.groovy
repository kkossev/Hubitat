/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Illuminance Library', name: 'illuminanceLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', documentationLink: '',
    version: '3.2.2'
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
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement'; added illuminanceRefresh()
 * ver. 3.2.1  2024-07-06 kkossev  - added illuminanceCoeff; added luxThreshold and illuminanceCoeff to preferences (if applicable)
 * ver. 3.2.2  2026-08-03 kkossev  - (BUGS.md B10) illuminanceThreshold UI default corrected from 5 to 10 lx; (BUGS.md A4) a lux threshold of 0 (no filtering) is no longer silently replaced by the default
 *
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage
*/

static String illuminanceLibVersion()   { '3.2.2' }
static String illuminanceLibStamp() { '2026/08/03 11:46 PM' }

metadata {
    capability 'IlluminanceMeasurement'
    // no attributes
    // no commands
    preferences {
        if (device) {
            // note: only 'illuminanceThreshold':false in a device profile 'preferences' map is meaningful here - it hides the input; declaring it as 'true' or omitting the key altogether behave identically
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.illuminanceThreshold != false) && !(DEVICE?.device?.isDepricated == true)) {
                input('illuminanceThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 10)
                if (advancedOptions) {
                    input('illuminanceCoeff', 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: 'Illuminance correction coefficient, range (0.10..10.00)', range: '0.10..10.00', defaultValue: 1.00)
                    input('illuminanceMinReportingTime', 'number', title: '<b>Illuminance Minimum Reporting Time</b>', description: 'Minimum time (seconds) between illuminance events; overrides the shared Minimum Reporting Time for illuminance only', range: '1..3600', defaultValue: DEFAULT_ILLUMINANCE_MIN_REPORTING_TIME)
                }
            }
        }
    }
}

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10
@Field static final Integer DEFAULT_ILLUMINANCE_MIN_REPORTING_TIME = 10

void standardParseIlluminanceCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final int value = hexStrToUnsignedInt(descMap.value)
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0
    handleIlluminanceEvent(lux)
}

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) {
    Map eventMap = [:]
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] }
    eventMap.name = 'illuminance'
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    eventMap.value  = illumCorrected
    eventMap.type = isDigital ? 'digital' : 'physical'
    eventMap.unit = 'lx'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    boolean isRefresh = state.states['isRefresh'] == true
    if (isRefresh) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true
    }
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000)
    Integer minTime = (settings?.illuminanceMinReportingTime != null ? settings.illuminanceMinReportingTime : (settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME)) as int    // illuminance-specific override takes precedence over the shared minReportingTime
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastIllum = device.currentValue('illuminance') as Integer    // null when the attribute was never set - the first report must be published to establish the baseline
    Integer threshold = (settings?.illuminanceThreshold != null ? settings.illuminanceThreshold : DEFAULT_ILLUMINANCE_THRESHOLD) as int    // 0 is a valid threshold, meaning 'no filtering'
    if (!isRefresh && lastIllum != null) {
        Integer delta = Math.abs(lastIllum - illumCorrected)
        if (delta < threshold) {
            logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${threshold} (lastIllum=${lastIllum}, delta=${delta})"
            return
        }
    }
    if (isRefresh || timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports
        state.lastRx['illumTime'] = now()
        sendEvent(eventMap)
    }
    else {         // queue the event
        eventMap.type = 'delayed'
        logDebug "${device.displayName} delaying ${timeRamaining} seconds event : ${eventMap}"
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
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) {
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

void illuminanceInitializeVars( boolean fullInit = false ) {
    logDebug "illuminanceInitializeVars()... fullInit = ${fullInit}"
    if (DEVICE?.capabilities?.IlluminanceMeasurement == true) {
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // defined in commonLib
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) }
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) }
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) }
        if (fullInit || settings?.illuminanceMinReportingTime == null) { device.updateSetting('illuminanceMinReportingTime', [value:DEFAULT_ILLUMINANCE_MIN_REPORTING_TIME, type:'number']) }
    }
}

List<String> illuminanceRefresh() {
    List<String> cmds = []
    cmds = zigbee.readAttribute(0x0400, 0x0000, [:], delay = 200) // illuminance
    return cmds
}
