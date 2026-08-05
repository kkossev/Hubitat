/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Ultrasonic Water Flow Meter - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *  https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.3.0  2024-08-03 kkossev  - first dummy version
 * ver. 3.4.0  2026-08-05 kkossev  - added _TZE284_ajlu4cud (meter, no valve) and _TZE284_vuwtqx0t; split into two device profiles;
 *                                   fixed the dp scaling and types; valve open()/close() (dp13); fault bitmap, meter id and the
 *                                   8-byte consumption blobs are decoded from the raw payload; volumeUnit preference (m3 / L)
 *
 *                                   TODO: decode the frozen-date stamp in the first 4 bytes of dp2/dp3
 *                                   TODO: confirm the dp15 function on the _TZE200_vuwtqx0t meters
 */

static String version() { '3.4.0' }
static String timeStamp() { '2026/08/06 12:15 AM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true  // disable it for production

#include kkossev.deviceProfileLib
#include kkossev.commonLib

deviceType = "WaterFlowMeter"
@Field static final String DEVICE_TYPE = "WaterFlowMeter"

metadata {
    definition (
        name: 'Tuya Ultrasonic Water Flow Meter',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Ultrasonic%20Water%20Flow%20Meter/Tuya_Ultrasonic_Water_Flow_Meter_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        capability 'Sensor'
        capability 'Actuator'
        capability 'Refresh'
        capability 'Battery'                    // battery (%)
        capability 'TemperatureMeasurement'     // temperature (C)
        capability 'PowerSource'                // powerSource
        capability 'Valve'                      // valve : open, closed  + open() / close()   (dp13, meters with a valve only)
        capability 'LiquidFlowRate'             // rate  : LPM
        capability 'HealthCheck'                // healthStatus

        attribute 'healthStatus', 'enum', ['offline', 'online']
        attribute 'rtt', 'number'
        attribute 'batteryVoltage', 'number'                                                    // dp26
        attribute 'waterConsumed', 'number'                                                     // dp1  - total water consumed
        attribute 'monthConsumption', 'number'                                                  // dp2  - month consumption
        attribute 'dailyConsumption', 'number'                                                  // dp3  - daily consumption
        attribute 'reverseWaterConsumed', 'number'                                              // dp18 - reverse water consumption
        attribute 'instantaneousFlowRate', 'number'                                             // dp21 - instantaneous flow rate (L/h)
        attribute 'reportPeriod', 'enum', ['1h', '2h', '3h', '4h', '6h', '8h', '12h', '24h']    // dp4
        attribute 'autoClean', 'enum', ['off', 'on']                                            // dp14 - meters with a valve only
        attribute 'faults', 'string'                                                            // dp5  - decoded fault bitmap
        attribute 'meterId', 'string'                                                           // dp16 - meter identification number
        attribute 'monthAndDailyFrozenSet', 'number'                                            // dp6

       // no commands
       if (_DEBUG) {
            command 'tuyaDataQuery'
        }

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
               }
            }
        }
    }

    preferences {
        if (device) {
            // input(name: 'info',    type: 'hidden', title: "<a href='https://github.com/kkossev/Hubitat/wiki/Tuya-Multi-Sensor-4-In-1' target='_blank'><i>For more info, click on this link to visit the WiKi page</i></a>")
        }
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables events logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputIt from the deviceProfileLib and from the included libraries
        if (device) {
            input name: 'volumeUnit', type: 'enum', title: '<b>Water Volume Unit</b>', options: VolumeUnitOpts.options, defaultValue: VolumeUnitOpts.defaultValue, required: true, description: 'The meter always sends liters - this is the unit the consumption attributes are reported in.'
            input name: 'pollingInterval', type: 'enum', title: '<b>Polling Interval</b>', options: PollingIntervalOpts.options, defaultValue: PollingIntervalOpts.defaultValue, required: true, description: 'Changes how often the hub will poll the meter. Battery meters transmit on their own report period - polling is normally not needed.'
        }
    }
}

@Field static String ttStyleStr = '<style>.tTip {display:inline-block;border-bottom: 1px dotted black;}.tTip .tTipText {display:none;border-radius: 6px;padding: 5px 0;position: absolute;z-index: 1;}.tTip:hover .tTipText {display:inline-block;background-color:red;color:red;}</style>'

@Field static final Map PollingIntervalOpts = [
    defaultValue: 0,
    options     : [0: 'Disabled', 5: 'Every 5 seconds (DONT DO THAT!)', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]

@Field static final Map VolumeUnitOpts = [
    defaultValue: 0,
    options     : [0: 'm3 (cubic meters)', 1: 'L (liters)']
]

// dp5 - the fault bitmap, as decoded by the Zigbee2MQTT TS0601_water_meter / TS0601_water_valve converters
@Field static final Map FaultBitsMap = [
    0x0001: 'battery_alarm',    0x0002: 'magnetism_alarm',  0x0004: 'cover_alarm',      0x0008: 'credit_alarm',
    0x0010: 'switch_gaps_alarm', 0x0020: 'meter_body_alarm', 0x0040: 'abnormal_water_alarm', 0x0080: 'arrearage_alarm',
    0x0100: 'overflow_alarm',   0x0200: 'revflow_alarm',    0x0400: 'over_pre_alarm',   0x0800: 'empty_pipe_alarm',
    0x1000: 'transducer_alarm'
]

// the battery is a 3.6V ER14505 lithium cell - the batteryLib 2.2 .. 3.2V range does not apply here
@Field static final BigDecimal BATTERY_MIN_VOLTS = 2.5
@Field static final BigDecimal BATTERY_MAX_VOLTS = 3.7

/*
Measures : total / monthly / daily / reverse water consumption, instantaneous flow rate, water temperature, battery voltage.
Controls (meters with a valve only) : valve open/close, auto clean.
All the values are sent over the Tuya EF00 cluster - the meters expose no standard ZCL Power or Temperature clusters.
*/

// https://www.alibaba.com/product-detail/Smart-Ultrasonic-Water-Flow-Meter-With_1600722839075.html
// https://github.com/Koenkk/zigbee2mqtt/issues/21255
// https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/tuya.ts  (TS0601_water_valve, TS0601_water_meter)
@Field static final Map deviceProfilesV3 = [
    'TS0601_WATER_METER_VALVE'  : [            // ultrasonic water meter with a valve (dp13) and auto clean (dp14)
            description   : 'Tuya Ultrasonic Water Meter with Valve',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'battery', isSleepy:false],
            capabilities  : ['Battery': true, 'TemperatureMeasurement': true, 'Valve': true],
            preferences   : ['reportPeriod':'4', 'autoClean':'14'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_vuwtqx0t', deviceJoinName: 'Tuya 214C Ultrasonic Water Flow Meter'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000,ED00', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE284_vuwtqx0t', deviceJoinName: 'Tuya 214C Ultrasonic Water Flow Meter'],
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE200_zlwr0raf', deviceJoinName: 'Tuya 213E Ultrasonic Water Flow Meter'],      // no DP reports captured yet - assumed identical to the 214C // https://www.aliexpress.com/item/1005007308058989.html
            ],
            tuyaDPs:        [                                                                                                                                       // dp1, dp2, dp3, dp5, dp16, dp18 and dp21 are decoded in customProcessTuyaDp()
                [dp:4,   name:'reportPeriod',            type:'enum',    rw: 'rw',  defVal:'6',  scale:1,   unit:'',      title:'<b>Report Period</b>', description:'How often the meter wakes up and sends a report', map:[0:'1h', 1:'2h', 2:'3h', 3:'4h', 4:'6h', 5:'8h', 6:'12h', 7:'24h']],    // dtype 4 - enum
                [dp:6,   name:'monthAndDailyFrozenSet',  type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Month and daily frozen set'],   // dtype 0 - raw
                [dp:13,  name:'valve',                   type:'enum',    rw: 'ro',  scale:1,   unit:'',      description:'Valve state', map:[0:'closed', 1:'open']],           // dtype 1 - bool - written by open() / close()
                [dp:14,  name:'autoClean',               type:'enum',    rw: 'rw',  defVal:'0',  scale:1,   unit:'',      title:'<b>Auto Clean</b>', description:'Periodic self-cleaning of the valve', map:[0:'off', 1:'on']],   // dtype 1 - bool
                [dp:15,  name:'UnknownDp15',             type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Unknown DP15'],         // dtype 0 - raw  - ?
                [dp:22,  name:'temperature',             type:'decimal', rw: 'ro',  scale:100, unit:'C',     description:'Water Temperature'],    // dtype 2 - value
                [dp:26,  name:'batteryVoltage',          type:'decimal', rw: 'ro',  scale:100, unit:'V',     description:'Battery voltage'],      // dtype 2 - value
            ],
            refresh:        ['refreshQueryAllTuyaDP'],
            configuration : ['battery': false],
            deviceJoinName: 'Tuya Ultrasonic Water Meter with Valve'
    ],

    'TS0601_WATER_METER'  : [                  // ultrasonic water meter without a valve - sends dp 1,2,3,4,5,6,16,18,21,22,26 only
            description   : 'Tuya Ultrasonic Water Meter',
            models        : ['TS0601'],
            device        : [type: 'Sensor', powerSource: 'battery', isSleepy:false],
            capabilities  : ['Battery': true, 'TemperatureMeasurement': true],
            preferences   : ['reportPeriod':'4'],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences', 'printFingerprints':'printFingerprints', 'printPreferences':'printPreferences'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000,ED00', outClusters:'0019,000A', model:'TS0601',  manufacturer:'_TZE284_ajlu4cud', deviceJoinName: 'Tuya Ultrasonic Water Meter'],           // https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433/25
            ],
            tuyaDPs:        [                                                                                                                                       // dp1, dp2, dp3, dp5, dp16, dp18 and dp21 are decoded in customProcessTuyaDp()
                [dp:4,   name:'reportPeriod',            type:'enum',    rw: 'rw',  defVal:'6',  scale:1,   unit:'',      title:'<b>Report Period</b>', description:'How often the meter wakes up and sends a report', map:[0:'1h', 1:'2h', 2:'3h', 3:'4h', 4:'6h', 5:'8h', 6:'12h', 7:'24h']],    // dtype 4 - enum
                [dp:6,   name:'monthAndDailyFrozenSet',  type:'number',  rw: 'ro',  scale:1,   unit:'',      description:'Month and daily frozen set'],   // dtype 0 - raw
                [dp:22,  name:'temperature',             type:'decimal', rw: 'ro',  scale:100, unit:'C',     description:'Water Temperature'],    // dtype 2 - value
                [dp:26,  name:'batteryVoltage',          type:'decimal', rw: 'ro',  scale:100, unit:'V',     description:'Battery voltage'],      // dtype 2 - value
            ],
            refresh:        ['refreshQueryAllTuyaDP'],
            configuration : ['battery': false],
            deviceJoinName: 'Tuya Ultrasonic Water Meter'
    ]
]

// called from standardProcessTuyaDP in the commonLib for each Tuya dp report in a Zigbee message
// should always return true, as we are processing all the dp reports here
boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logDebug "customProcessTuyaDp: dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len} descMap.data = ${descMap?.data}"
    // the DPs that the deviceProfile engine can not express : a non-scalar payload, or a scale that depends on the volumeUnit preference
    if (processWaterMeterDP(descMap, dp, fncmd) == true) {
        return true
    }
    if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
        return true      // sucessfuly processed from the deviceProfile
    }

    logWarn "<b>NOT PROCESSED from deviceProfile</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
    localProcessTuyaDP(descMap, dp, dp_id, fncmd, dp_len)
    return true
}

void localProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len) {
    switch (dp) {
        default :
            logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}

// returns true if the DP was handled here, false to let the deviceProfile engine have it
private boolean processWaterMeterDP(final Map descMap, final int dp, final int fncmd) {
    switch (dp) {
        case 1  : sendVolumeEvent('waterConsumed', descMap, dp) ; return true
        case 2  : sendVolumeEvent('monthConsumption', descMap, dp) ; return true
        case 3  : sendVolumeEvent('dailyConsumption', descMap, dp) ; return true
        case 5  : sendFaultsEvent(fncmd) ; return true
        case 16 : sendMeterIdEvent(descMap, dp) ; return true
        case 18 : sendVolumeEvent('reverseWaterConsumed', descMap, dp) ; return true
        case 21 : sendFlowRateEvent(descMap, dp) ; return true
        default : return false
    }
}

// dp1, dp2, dp3 and dp18 - the meter always sends liters as a big-endian uint32 in the LAST 4 payload bytes.
// dp2 and dp3 prepend a 4-byte frozen-date stamp that is not decoded (yet).
private void sendVolumeEvent(final String attribute, final Map descMap, final int dp) {
    List<String> payload = getTuyaDpPayload(descMap, dp)
    if (payload == null) { logWarn "sendVolumeEvent: could not extract the dp=${dp} payload from ${descMap?.data}" ; return }
    long liters = lastUInt32BE(payload)
    boolean isCubicMeters = safeToInt(settings?.volumeUnit ?: VolumeUnitOpts.defaultValue) == 0
    BigDecimal value = isCubicMeters ? ((liters as BigDecimal) / 1000G) : (liters as BigDecimal)
    String unitText = isCubicMeters ? 'm3' : 'L'
    String descText = "${attribute} is ${value} ${unitText}"
    if (settings?.logEnable == true) { descText += " (raw:${liters} L)" }
    sendEvent(name: attribute, value: value, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true)
    logInfo "${descText}"
}

// dp21 - instantaneous flow rate, big-endian uint32 in L/h. Also feeds the LiquidFlowRate 'rate' attribute in LPM.
private void sendFlowRateEvent(final Map descMap, final int dp) {
    List<String> payload = getTuyaDpPayload(descMap, dp)
    if (payload == null) { logWarn "sendFlowRateEvent: could not extract the dp=${dp} payload from ${descMap?.data}" ; return }
    long litersPerHour = lastUInt32BE(payload)
    String descText = "instantaneousFlowRate is ${litersPerHour} L/h"
    sendEvent(name: 'instantaneousFlowRate', value: litersPerHour, unit: 'L/h', descriptionText: descText, type: 'physical', isStateChange: true)
    logInfo "${descText}"
    BigDecimal lpm = (Math.round((litersPerHour / 60.0d) * 100.0d) as BigDecimal) / 100G
    String rateText = "rate is ${lpm} LPM"
    sendEvent(name: 'rate', value: lpm, unit: 'LPM', descriptionText: rateText, type: 'physical', isStateChange: true)
    logInfo "${rateText}"
}

// dp5 - the fault bitmap. The deviceProfile engine has no 'bitmap' type, so it is decoded here.
private void sendFaultsEvent(final int fncmd) {
    String faults = 'no_alarm'
    if (fncmd != 0) {
        List<String> active = []
        FaultBitsMap.each { bit, name ->
            if ((fncmd & (bit as int)) != 0) { active.add(name as String) }
        }
        faults = active.isEmpty() ? "unknown (0x${zigbee.convertToHexString(fncmd, 4)})" : active.join(',')
    }
    String descText = "faults is ${faults}"
    if (settings?.logEnable == true) { descText += " (raw:0x${zigbee.convertToHexString(fncmd, 4)})" }
    sendEvent(name: 'faults', value: faults, descriptionText: descText, type: 'physical', isStateChange: true)
    logInfo "${descText}"
}

// dp16 - the meter identification number, a UTF-8 string (Tuya dtype 3). getTuyaAttributeValue() can not decode it.
private void sendMeterIdEvent(final Map descMap, final int dp) {
    List<String> payload = getTuyaDpPayload(descMap, dp)
    if (payload == null) { logWarn "sendMeterIdEvent: could not extract the dp=${dp} payload from ${descMap?.data}" ; return }
    String meterId = hexListToAscii(payload)
    String descText = "meterId is ${meterId}"
    sendEvent(name: 'meterId', value: meterId, descriptionText: descText, type: 'physical', isStateChange: true)
    logInfo "${descText}"
}

// commonLib standardParseTuyaCluster() forwards neither the chunk offset nor the DP length to customProcessTuyaDp(),
// so walk the frame the same way it does and return the raw payload bytes of this particular DP.
private List<String> getTuyaDpPayload(final Map descMap, final int dp) {
    List<String> data = descMap?.data
    if (data == null || data.size() < 7) { return null }
    for (int i = 0; i < (data.size() - 4); ) {
        int thisDp = zigbee.convertHexToInt(data[2 + i])
        int len    = zigbee.convertHexToInt(data[5 + i])
        if (len <= 0 || (6 + i + len) > data.size()) { return null }
        if (thisDp == dp) { return data[(6 + i)..(5 + i + len)] }
        i = i + len + 4
    }
    return null
}

private long lastUInt32BE(final List<String> payload) {
    if (payload == null || payload.size() < 4) { return 0L }
    List<String> b = payload[-4..-1]
    return (((long)zigbee.convertHexToInt(b[0])) << 24) + (((long)zigbee.convertHexToInt(b[1])) << 16) + (((long)zigbee.convertHexToInt(b[2])) << 8) + ((long)zigbee.convertHexToInt(b[3]))
}

private String hexListToAscii(final List<String> payload) {
    String result = ''
    for (int i = 0; i < payload.size(); i++) {
        result += (char)zigbee.convertHexToInt(payload[i])
    }
    return result
}

// called from processFoundItem in the deviceProfileLib
void customProcessDeviceProfileEvent(final Map descMap, final String name, valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
    logInfo "${descText}"   // TODO - send info log only if the value has changed?
    if (name == 'batteryVoltage') {
        sendBatteryPercentageFromVoltage(safeToBigDecimal(valueScaled))
    }
}

// the meters send no battery percentage - derive it from the dp26 voltage.
// batteryLib sendBatteryVoltageEvent() is deliberately not used : it expects 0.1V units and clamps to 2.2 .. 3.2V.
private void sendBatteryPercentageFromVoltage(final BigDecimal volts) {
    if (volts == null || volts <= 0) { return }
    BigDecimal pct = ((volts - BATTERY_MIN_VOLTS) / (BATTERY_MAX_VOLTS - BATTERY_MIN_VOLTS)) * 100G
    int roundedPct = Math.round(pct as double) as int
    if (roundedPct < 1) { roundedPct = 1 }
    if (roundedPct > 100) { roundedPct = 100 }
    String descText = "battery is ${roundedPct} %"
    if (settings?.logEnable == true) { descText += " (${volts} V)" }
    sendEvent(name: 'battery', value: roundedPct, unit: '%', descriptionText: descText, type: 'physical', isStateChange: true)
    logInfo "${descText}"
}

private boolean hasValve() { return DEVICE?.tuyaDPs?.find { it.dp == 13 } != null }

void open() {
    if (hasValve() == false) { logWarn 'open() : this water meter does not have a valve!' ; return }
    if (state.states == null) { state.states = [:] }
    state.states['isDigital'] = true
    scheduleCommandTimeoutCheck()
    List<String> cmds = sendTuyaCommand('0D', DP_TYPE_BOOL, '01')
    logDebug "open() : ${cmds}"
    sendZigbeeCommands(cmds)
}

void close() {
    if (hasValve() == false) { logWarn 'close() : this water meter does not have a valve!' ; return }
    if (state.states == null) { state.states = [:] }
    state.states['isDigital'] = true
    scheduleCommandTimeoutCheck()
    List<String> cmds = sendTuyaCommand('0D', DP_TYPE_BOOL, '00')
    logDebug "close() : ${cmds}"
    sendZigbeeCommands(cmds)
}

List<String> customRefresh() {
    logDebug "customRefresh()"
    List<String> cmds = []
    List<String> devProfCmds = refreshFromDeviceProfileList()
    if (devProfCmds != null && !devProfCmds.isEmpty()) {
        cmds += devProfCmds
    }
    return cmds
}

void customUpdated() {
    logDebug "customUpdated()"
    List<String> cmds = []
    if (settings?.forcedProfile != null) {
        if (this.respondsTo('getProfileKey') == false) {
            logWarn "getProfileKey() is not defined in the driver"
        }
        else {
            logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
            if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
                logInfo "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
                state.deviceProfile = getProfileKey(settings?.forcedProfile)
                initializeVars(fullInit = false)
                resetPreferencesToDefaults(debug = true)
                logInfo 'press F5 to refresh the page'
            }
        }
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
        logDebug "forcedProfile is not set"
    }

    final int interval = (settings?.pollingInterval as Integer) ?: 0
    if (interval > 0) {
        logInfo "customUpdated: scheduling polling every ${interval} seconds"
        schedulePolling(interval)
    }
    else {
        unSchedulePolling()
        logInfo 'customUpdated: polling is disabled!'
    }

    // Itterates through all settings
    cmds += updateAllPreferences()  // defined in deviceProfileLib
    sendZigbeeCommands(cmds)
}

/**
 * Schedule polling
 * @param intervalMins interval in seconds
 */
private void schedulePolling(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    logDebug "cron = ${cron}"
    schedule(cron, 'autoPoll')
}

private void unSchedulePolling() {
    unschedule('autoPoll')
}

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPoll() {
    logDebug 'autoPoll()...'
    checkDriverVersion(state)
    List<String> cmds = []
    cmds = refreshFromDeviceProfileList()
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
}



void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null || state.deviceProfile == '' || state.deviceProfile == 'UNKNOWN') {
        String model        = device.getDataValue('model') ?: 'TS0601'
        String manufacturer = device.getDataValue('manufacturer') ?: ''
        setDeviceNameAndProfile(model, manufacturer)               // in deviceProfileiLib.groovy
        if (state.deviceProfile == null || state.deviceProfile == 'UNKNOWN') {
            logWarn "customInitializeVars: unknown model ${model} manufacturer ${manufacturer} - defaulting to the TS0601_WATER_METER_VALVE profile"
            state.deviceProfile = 'TS0601_WATER_METER_VALVE'
        }
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    if (fullInit || settings?.pollingInterval == null) { device.updateSetting('pollingInterval', [value: PollingIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.volumeUnit == null) { device.updateSetting('volumeUnit', [value: VolumeUnitOpts.defaultValue.toString(), type: 'enum']) }
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents()"
}

void customParseZdoClusters(Map descMap) {
    if (descMap.clusterInt == 0x0013) {
        logDebug "customParseZdoClusters() - device announce"
        sendZigbeeCommands(refreshQueryAllTuyaDP())
    }
}

List<String> refreshQueryAllTuyaDP() {
    return queryAllTuyaDP()
}

// _DEBUG only - replays the DP reports captured from a _TZE284_ajlu4cud meter
// https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433/25
@Field static final List<String> TEST_FRAMES = [
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 03140102000400000000',                    // dp1  waterConsumed 0
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 0315020000081506150600000000',            // dp2  monthConsumption 0
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 0316030000080707070700000000',            // dp3  dailyConsumption 0
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 03170404000106',                          // dp4  reportPeriod 12h
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 0318050500021800',                        // dp5  faults empty_pipe_alarm,transducer_alarm
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 0319060000020100',                        // dp6  monthAndDailyFrozenSet
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 031A1003000E3030303030303236303039313632', // dp16 meterId 00000026009162
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 031B1200000400000000',                    // dp18 reverseWaterConsumed 0
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 031C1500000400000000',                    // dp21 instantaneousFlowRate 0
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 031D1602000400000CD0',                    // dp22 temperature 32.8 C
    'catchall: 0104 EF00 01 01 0040 00 8F4F 01 00 0000 02 01 031E1A02000400000156'                     // dp26 batteryVoltage 3.42 V
]

void test(String par) {
    logDebug "test() replaying ${TEST_FRAMES.size()} captured DP reports"
    for (int i = 0; i < TEST_FRAMES.size(); i++) {
        parse(TEST_FRAMES[i])
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

