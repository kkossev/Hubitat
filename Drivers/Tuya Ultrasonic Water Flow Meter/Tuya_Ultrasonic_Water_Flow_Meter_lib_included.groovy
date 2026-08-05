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
static String timeStamp() { '2026/08/06 12:03 AM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean _TRACE_ALL = false              // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = false  // disable it for production




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


// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
library( // library marker kkossev.deviceProfileLib, line 1
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 2
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLib, line 3
    version: '3.5.7' // library marker kkossev.deviceProfileLib, line 4
) // library marker kkossev.deviceProfileLib, line 5
/* // library marker kkossev.deviceProfileLib, line 6
 *  Device Profile Library (V3) // library marker kkossev.deviceProfileLib, line 7
 * // library marker kkossev.deviceProfileLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 10
 * // library marker kkossev.deviceProfileLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 12
 * // library marker kkossev.deviceProfileLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 16
 * // library marker kkossev.deviceProfileLib, line 17
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 18
 * ver. 3.0.0  2023-11-27 kkossev  - fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.1  2023-12-02 kkossev  - release candidate // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.2  2023-12-17 kkossev  - inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.4  2024-03-30 kkossev  - more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.1.0  2024-04-03 kkossev  - more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.1  2024-04-21 kkossev  - deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.2  2024-05-05 kkossev  - added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.3.0  2024-06-29 kkossev  - empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.1  2024-07-06 kkossev  - added powerSource event in the initEventsDeviceProfile // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.3.2  2024-08-18 kkossev  - release 3.3.2 // library marker kkossev.deviceProfileLib, line 31
 * ver. 3.3.3  2024-08-18 kkossev  - sendCommand and setPar commands commented out; must be declared in the main driver where really needed // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 33
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.2  2025-03-24 kkossev  - added refreshFromConfigureReadList() method; documentation update; getDeviceNameAndProfile uses DEVICE.description instead of deviceJoinName // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.3  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround. // library marker kkossev.deviceProfileLib, line 37
 * ver. 3.5.0  2025-08-14 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLib, line 38
 * ver. 3.5.1  2025-09-15 kkossev  - commonLib ver 4.0.0 allignment; log.trace leftover removed;  // library marker kkossev.deviceProfileLib, line 39
 * ver. 3.5.2  2025-10-04 kkossev  - SIMULATED_DEVICE_MODEL and SIMULATED_DEVICE_MANUFACTURER added (for testing with simulated devices) // library marker kkossev.deviceProfileLib, line 40
 * ver. 3.5.3  2025-12-06 kkossev  - added digital/physical type to events in customProcessDeviceProfileEvent() // library marker kkossev.deviceProfileLib, line 41
 * ver. 3.5.4  2026-02-04 kkossev  - changed inputIt min param rounding to floor instead of ceil // library marker kkossev.deviceProfileLib, line 42
 * ver. 3.5.5  2026-03-05 kkossev  - added deviceProfilesV3defaults?.defaultCommands // library marker kkossev.deviceProfileLib, line 43
 * ver. 3.5.6  2026-06-04 kkossev  - fixed setPar() invalid virtual enum parameter false error when preference key is passed instead of label // library marker kkossev.deviceProfileLib, line 44
 * ver. 3.5.7  2026-08-03 kkossev  - (BUGS.md B13) processFoundItem() no longer skips illuminance events based on the raw-DP dedupe, which ignored illuminanceCoeff // library marker kkossev.deviceProfileLib, line 45
 * // library marker kkossev.deviceProfileLib, line 46
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 47
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 48
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 49
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 50
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 51
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 52
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 53
 * // library marker kkossev.deviceProfileLib, line 54
*/ // library marker kkossev.deviceProfileLib, line 55

static String deviceProfileLibVersion()   { '3.5.7' } // library marker kkossev.deviceProfileLib, line 57
static String deviceProfileLibStamp() { '2026/08/05 11:12 PM' } // library marker kkossev.deviceProfileLib, line 58
import groovy.json.* // library marker kkossev.deviceProfileLib, line 59
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 60
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 61
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 62
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 63

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 65

metadata { // library marker kkossev.deviceProfileLib, line 67
    // no capabilities // library marker kkossev.deviceProfileLib, line 68
    // no attributes // library marker kkossev.deviceProfileLib, line 69
    /* // library marker kkossev.deviceProfileLib, line 70
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 71
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 72
        [name:'command', type: 'STRING', description: '▶️ Run one of the commands supported by this device profile • Leave empty to list the valid names in the log', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 73
        [name:'val',     type: 'STRING', description: 'Optional value, needed only by some commands', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 74
    ] // library marker kkossev.deviceProfileLib, line 75
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 76
            [name:'par', type: 'STRING', description: '🎛️ Set a device profile preference and write it to the device • Leave empty to list the valid parameter names in the log', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 77
            [name:'val', type: 'STRING', description: 'Leave empty to see the allowed range or values for that parameter', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 78
    ] // library marker kkossev.deviceProfileLib, line 79
    */ // library marker kkossev.deviceProfileLib, line 80
    preferences { // library marker kkossev.deviceProfileLib, line 81
        if (device) { // library marker kkossev.deviceProfileLib, line 82
            input(name: 'forcedProfile', type: 'enum', title: '<b>⚠️ Device Profile</b>', description: 'Which set of datapoints, attributes and preferences this driver uses for your device. Matched automatically from the model and manufacturer when the device is paired.<br>Change it manually only if your device was not recognized - the wrong profile stops it working correctly.<br>After changing the profile, pair the device again to your hub, without deleting it! Otherwise the new configuration may never reach a battery-powered sleepy device.',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 83
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 84
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 85
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 86
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 87
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 88
                        input inputMap // library marker kkossev.deviceProfileLib, line 89
                    } // library marker kkossev.deviceProfileLib, line 90
                } // library marker kkossev.deviceProfileLib, line 91
            } // library marker kkossev.deviceProfileLib, line 92
        } // library marker kkossev.deviceProfileLib, line 93
    } // library marker kkossev.deviceProfileLib, line 94
} // library marker kkossev.deviceProfileLib, line 95

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 97

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 99
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 100
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 101

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 103
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 104
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 105
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 106
    } // library marker kkossev.deviceProfileLib, line 107
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 108
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 109
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 110
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 111
        } // library marker kkossev.deviceProfileLib, line 112
    } // library marker kkossev.deviceProfileLib, line 113
    return activeProfiles // library marker kkossev.deviceProfileLib, line 114
} // library marker kkossev.deviceProfileLib, line 115

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 117

/** // library marker kkossev.deviceProfileLib, line 119
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 120
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 121
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 122
 */ // library marker kkossev.deviceProfileLib, line 123
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 124
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 125
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 126
    else { return null } // library marker kkossev.deviceProfileLib, line 127
} // library marker kkossev.deviceProfileLib, line 128

/** // library marker kkossev.deviceProfileLib, line 130
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 131
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 132
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 133
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 134
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 135
 */ // library marker kkossev.deviceProfileLib, line 136
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 137
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 138
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 139
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 140
    def preference // library marker kkossev.deviceProfileLib, line 141
    try { // library marker kkossev.deviceProfileLib, line 142
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 143
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 144
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 145
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 146
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 147
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 148
        } // library marker kkossev.deviceProfileLib, line 149
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 150
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 151
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 152
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 153
        } // library marker kkossev.deviceProfileLib, line 154
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 155
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 156
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 157
        } // library marker kkossev.deviceProfileLib, line 158
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 159
    } catch (e) { // library marker kkossev.deviceProfileLib, line 160
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 161
        return [:] // library marker kkossev.deviceProfileLib, line 162
    } // library marker kkossev.deviceProfileLib, line 163
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 164
    return foundMap // library marker kkossev.deviceProfileLib, line 165
} // library marker kkossev.deviceProfileLib, line 166

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 168
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 169
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 170
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 171
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 172
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 173
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 174
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 175
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 176
            return foundMap // library marker kkossev.deviceProfileLib, line 177
        } // library marker kkossev.deviceProfileLib, line 178
    } // library marker kkossev.deviceProfileLib, line 179
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 180
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 181
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 182
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 183
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 184
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 185
            return foundMap // library marker kkossev.deviceProfileLib, line 186
        } // library marker kkossev.deviceProfileLib, line 187
    } // library marker kkossev.deviceProfileLib, line 188
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 189
    return [:] // library marker kkossev.deviceProfileLib, line 190
} // library marker kkossev.deviceProfileLib, line 191

/** // library marker kkossev.deviceProfileLib, line 193
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 194
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 195
 */ // library marker kkossev.deviceProfileLib, line 196
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 197
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 198
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 199
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 200
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 201
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 202
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 203
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 204
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 205
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 206
            return // continue // library marker kkossev.deviceProfileLib, line 207
        } // library marker kkossev.deviceProfileLib, line 208
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 209
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 210
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 211
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 212
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 213
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 214
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 215
    } // library marker kkossev.deviceProfileLib, line 216
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 217
} // library marker kkossev.deviceProfileLib, line 218

/** // library marker kkossev.deviceProfileLib, line 220
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 221
 * // library marker kkossev.deviceProfileLib, line 222
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 223
 */ // library marker kkossev.deviceProfileLib, line 224
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 225
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 226
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 227
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 228
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 229
    } // library marker kkossev.deviceProfileLib, line 230
    return validPars // library marker kkossev.deviceProfileLib, line 231
} // library marker kkossev.deviceProfileLib, line 232

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 234
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 235
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 236
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 237
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 238
    def scaledValue // library marker kkossev.deviceProfileLib, line 239
    if (value == null) { // library marker kkossev.deviceProfileLib, line 240
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 241
        return null // library marker kkossev.deviceProfileLib, line 242
    } // library marker kkossev.deviceProfileLib, line 243
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 244
        case 'number' : // library marker kkossev.deviceProfileLib, line 245
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 246
            break // library marker kkossev.deviceProfileLib, line 247
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 248
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 249
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 250
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 251
            } // library marker kkossev.deviceProfileLib, line 252
            break // library marker kkossev.deviceProfileLib, line 253
        case 'bool' : // library marker kkossev.deviceProfileLib, line 254
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 255
            break // library marker kkossev.deviceProfileLib, line 256
        case 'enum' : // library marker kkossev.deviceProfileLib, line 257
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 258
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 259
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 260
                return null // library marker kkossev.deviceProfileLib, line 261
            } // library marker kkossev.deviceProfileLib, line 262
            scaledValue = value // library marker kkossev.deviceProfileLib, line 263
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 264
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 265
            } // library marker kkossev.deviceProfileLib, line 266
            break // library marker kkossev.deviceProfileLib, line 267
        default : // library marker kkossev.deviceProfileLib, line 268
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 269
            return null // library marker kkossev.deviceProfileLib, line 270
    } // library marker kkossev.deviceProfileLib, line 271
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 272
    return scaledValue // library marker kkossev.deviceProfileLib, line 273
} // library marker kkossev.deviceProfileLib, line 274

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 276
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 277
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 278
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 279
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 280
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 281
        return // library marker kkossev.deviceProfileLib, line 282
    } // library marker kkossev.deviceProfileLib, line 283
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 284
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 285
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 286
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 287
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 288
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 289
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 290
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 291
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 292
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 293
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 294
                // scale the value // library marker kkossev.deviceProfileLib, line 295
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 296
            } // library marker kkossev.deviceProfileLib, line 297
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 298
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 299
            } // library marker kkossev.deviceProfileLib, line 300
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 301
        } // library marker kkossev.deviceProfileLib, line 302
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 303
    } // library marker kkossev.deviceProfileLib, line 304
    return // library marker kkossev.deviceProfileLib, line 305
} // library marker kkossev.deviceProfileLib, line 306

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 308
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 309
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 310
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 311
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 312
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 313
} // library marker kkossev.deviceProfileLib, line 314
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 315
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 316
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 317
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 318
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 319
} // library marker kkossev.deviceProfileLib, line 320
int invert(int val) { // library marker kkossev.deviceProfileLib, line 321
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 322
    else { return val } // library marker kkossev.deviceProfileLib, line 323
} // library marker kkossev.deviceProfileLib, line 324

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 326
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 327
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 328
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 329
    Map map = [:] // library marker kkossev.deviceProfileLib, line 330
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 331
    try { // library marker kkossev.deviceProfileLib, line 332
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 333
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 334
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 335
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 336
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLib, line 337
    } // library marker kkossev.deviceProfileLib, line 338
    catch (e) { logWarn "zclWriteAttribute: Exception caught while splitting the cluster and attribute <b>${attributesMap?.at}</b> (scaledValue=${scaledValue}) : '${e}'" ; return [] } // library marker kkossev.deviceProfileLib, line 339
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 340
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 341
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 342
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 343
    } // library marker kkossev.deviceProfileLib, line 344
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLib, line 345
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 346
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLib, line 347
        Map mapOptions = [:] // library marker kkossev.deviceProfileLib, line 348
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLib, line 349
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLib, line 350
        //log.trace "$mapOptions" // library marker kkossev.deviceProfileLib, line 351
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLib, line 352
    } // library marker kkossev.deviceProfileLib, line 353
    else { // library marker kkossev.deviceProfileLib, line 354
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 355
    } // library marker kkossev.deviceProfileLib, line 356
    return cmds // library marker kkossev.deviceProfileLib, line 357
} // library marker kkossev.deviceProfileLib, line 358

/** // library marker kkossev.deviceProfileLib, line 360
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 361
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 362
 * // library marker kkossev.deviceProfileLib, line 363
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 364
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 365
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 366
 */ // library marker kkossev.deviceProfileLib, line 367
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 368
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 369
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 370
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 371
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 372
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 373
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 374
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 375
        case 'number' : // library marker kkossev.deviceProfileLib, line 376
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 377
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 378
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 379
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 380
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 381
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 382
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 383
            } // library marker kkossev.deviceProfileLib, line 384
            else { // library marker kkossev.deviceProfileLib, line 385
                scaledValue = value // library marker kkossev.deviceProfileLib, line 386
            } // library marker kkossev.deviceProfileLib, line 387
            break // library marker kkossev.deviceProfileLib, line 388

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 390
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 391
            // scale the value // library marker kkossev.deviceProfileLib, line 392
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 393
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 394
            } // library marker kkossev.deviceProfileLib, line 395
            else { // library marker kkossev.deviceProfileLib, line 396
                scaledValue = value // library marker kkossev.deviceProfileLib, line 397
            } // library marker kkossev.deviceProfileLib, line 398
            break // library marker kkossev.deviceProfileLib, line 399

        case 'bool' : // library marker kkossev.deviceProfileLib, line 401
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 402
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 403
            else { // library marker kkossev.deviceProfileLib, line 404
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 405
                return null // library marker kkossev.deviceProfileLib, line 406
            } // library marker kkossev.deviceProfileLib, line 407
            break // library marker kkossev.deviceProfileLib, line 408
        case 'enum' : // library marker kkossev.deviceProfileLib, line 409
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 410
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 411
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 412
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 413
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 414
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 415
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 416
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 417
            } // library marker kkossev.deviceProfileLib, line 418
            else { // library marker kkossev.deviceProfileLib, line 419
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 420
            } // library marker kkossev.deviceProfileLib, line 421
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 422
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 423
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 424
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 425
                // find the key for the value // library marker kkossev.deviceProfileLib, line 426
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 427
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 428
                if (key == null) { // library marker kkossev.deviceProfileLib, line 429
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 430
                    return null // library marker kkossev.deviceProfileLib, line 431
                } // library marker kkossev.deviceProfileLib, line 432
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 433
            //return null // library marker kkossev.deviceProfileLib, line 434
            } // library marker kkossev.deviceProfileLib, line 435
            break // library marker kkossev.deviceProfileLib, line 436
        default : // library marker kkossev.deviceProfileLib, line 437
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 438
            return null // library marker kkossev.deviceProfileLib, line 439
    } // library marker kkossev.deviceProfileLib, line 440
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 441
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 442
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 443
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 444
        return null // library marker kkossev.deviceProfileLib, line 445
    } // library marker kkossev.deviceProfileLib, line 446
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 447
    return scaledValue // library marker kkossev.deviceProfileLib, line 448
} // library marker kkossev.deviceProfileLib, line 449

/** // library marker kkossev.deviceProfileLib, line 451
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 452
 * // library marker kkossev.deviceProfileLib, line 453
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 454
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 455
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 456
 */ // library marker kkossev.deviceProfileLib, line 457
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 458
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 459
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 460
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 461
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 462
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 463
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 464
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 465
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 466
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 467
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 468
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 469
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 470
        logTrace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 471
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 472
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 473
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 474
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 475
        return false // library marker kkossev.deviceProfileLib, line 476
    } // library marker kkossev.deviceProfileLib, line 477

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 479
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 480
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 481
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 482
        logDebug "setPar: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 483
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 484
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 485
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 486
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 487
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 488
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 489
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 490
            return true // library marker kkossev.deviceProfileLib, line 491
        } // library marker kkossev.deviceProfileLib, line 492
        else { // library marker kkossev.deviceProfileLib, line 493
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 494
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 495
        } // library marker kkossev.deviceProfileLib, line 496
    } // library marker kkossev.deviceProfileLib, line 497
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 498
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 499
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 500
        def valMiscType // library marker kkossev.deviceProfileLib, line 501
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 502
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 503
            // find the key for the value // library marker kkossev.deviceProfileLib, line 504
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 505
            if (key == null) { // library marker kkossev.deviceProfileLib, line 506
                // val may be the numeric key itself (e.g. when called from updated()) // library marker kkossev.deviceProfileLib, line 507
                key = dpMap.map.containsKey(safeToInt(val)) ? val : null // library marker kkossev.deviceProfileLib, line 508
            } // library marker kkossev.deviceProfileLib, line 509
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 510
            if (key == null) { // library marker kkossev.deviceProfileLib, line 511
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 512
                return false // library marker kkossev.deviceProfileLib, line 513
            } // library marker kkossev.deviceProfileLib, line 514
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 515
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 516
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 517
        } // library marker kkossev.deviceProfileLib, line 518
        else { // library marker kkossev.deviceProfileLib, line 519
            valMiscType = val // library marker kkossev.deviceProfileLib, line 520
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 521
        } // library marker kkossev.deviceProfileLib, line 522
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 523
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 524
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 525
        return true // library marker kkossev.deviceProfileLib, line 526
    } // library marker kkossev.deviceProfileLib, line 527

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 529
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 530

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 532
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 533
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 534
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 535
        // Tuya DP // library marker kkossev.deviceProfileLib, line 536
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 537
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 538
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 539
            return false // library marker kkossev.deviceProfileLib, line 540
        } // library marker kkossev.deviceProfileLib, line 541
        else { // library marker kkossev.deviceProfileLib, line 542
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 543
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 544
            return true // library marker kkossev.deviceProfileLib, line 545
        } // library marker kkossev.deviceProfileLib, line 546
    } // library marker kkossev.deviceProfileLib, line 547
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 548
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 549
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 550
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 551
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 552
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 553
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 554
            return false // library marker kkossev.deviceProfileLib, line 555
        } // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 558
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 559
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 560
    return true // library marker kkossev.deviceProfileLib, line 561
} // library marker kkossev.deviceProfileLib, line 562

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 564
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 565
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 566
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 567
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 568
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 569
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 570
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 571
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 572
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 573
        return [] // library marker kkossev.deviceProfileLib, line 574
    } // library marker kkossev.deviceProfileLib, line 575
    String dpType // library marker kkossev.deviceProfileLib, line 576
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 577
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 578
    } // library marker kkossev.deviceProfileLib, line 579
    else { // library marker kkossev.deviceProfileLib, line 580
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 581
    } // library marker kkossev.deviceProfileLib, line 582
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 583
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 584
        return [] // library marker kkossev.deviceProfileLib, line 585
    } // library marker kkossev.deviceProfileLib, line 586
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 587
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 588
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 589
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 590
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 591
    } // library marker kkossev.deviceProfileLib, line 592
    else { // library marker kkossev.deviceProfileLib, line 593
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 594
    } // library marker kkossev.deviceProfileLib, line 595
    return cmds // library marker kkossev.deviceProfileLib, line 596
} // library marker kkossev.deviceProfileLib, line 597

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 599
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 600
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 601
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 602
    } // library marker kkossev.deviceProfileLib, line 603
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 604
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 605
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 606
    } // library marker kkossev.deviceProfileLib, line 607
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 608
} // library marker kkossev.deviceProfileLib, line 609

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 611
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 612
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 613
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 614
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 615
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 616

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 618
    //log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 619
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 620
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 621
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 622
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 623
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 624
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 625
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 626
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 627
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 628
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 629
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 630
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 631
        try { // library marker kkossev.deviceProfileLib, line 632
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 633
        } // library marker kkossev.deviceProfileLib, line 634
        catch (e) { // library marker kkossev.deviceProfileLib, line 635
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 636
            return false // library marker kkossev.deviceProfileLib, line 637
        } // library marker kkossev.deviceProfileLib, line 638
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 639
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 640
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 641
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 642
            return true // library marker kkossev.deviceProfileLib, line 643
        } // library marker kkossev.deviceProfileLib, line 644
        else { // library marker kkossev.deviceProfileLib, line 645
            logDebug "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 646
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 647
        } // library marker kkossev.deviceProfileLib, line 648
    } // library marker kkossev.deviceProfileLib, line 649
    else { // library marker kkossev.deviceProfileLib, line 650
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 651
    } // library marker kkossev.deviceProfileLib, line 652
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 653
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 654
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 655
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 656
        // patch !! // library marker kkossev.deviceProfileLib, line 657
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 658
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 659
        } // library marker kkossev.deviceProfileLib, line 660
        else { // library marker kkossev.deviceProfileLib, line 661
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 662
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 663
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 664
        } // library marker kkossev.deviceProfileLib, line 665
        return true // library marker kkossev.deviceProfileLib, line 666
    } // library marker kkossev.deviceProfileLib, line 667
    else { // library marker kkossev.deviceProfileLib, line 668
        logTrace "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 669
    } // library marker kkossev.deviceProfileLib, line 670
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 671
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 672
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 673
    try { // library marker kkossev.deviceProfileLib, line 674
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 675
    } // library marker kkossev.deviceProfileLib, line 676
    catch (e) { // library marker kkossev.deviceProfileLib, line 677
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 678
        return false // library marker kkossev.deviceProfileLib, line 679
    } // library marker kkossev.deviceProfileLib, line 680
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 681
        // Tuya DP // library marker kkossev.deviceProfileLib, line 682
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 683
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 684
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 685
            return false // library marker kkossev.deviceProfileLib, line 686
        } // library marker kkossev.deviceProfileLib, line 687
        else { // library marker kkossev.deviceProfileLib, line 688
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 689
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 690
            return true // library marker kkossev.deviceProfileLib, line 691
        } // library marker kkossev.deviceProfileLib, line 692
    } // library marker kkossev.deviceProfileLib, line 693
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 694
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 695
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 696
    } // library marker kkossev.deviceProfileLib, line 697
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 698
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 699
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 700
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 701
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 702
            return false // library marker kkossev.deviceProfileLib, line 703
        } // library marker kkossev.deviceProfileLib, line 704
    } // library marker kkossev.deviceProfileLib, line 705
    else { // library marker kkossev.deviceProfileLib, line 706
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 707
        return false // library marker kkossev.deviceProfileLib, line 708
    } // library marker kkossev.deviceProfileLib, line 709
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 710
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 711
    return true // library marker kkossev.deviceProfileLib, line 712
} // library marker kkossev.deviceProfileLib, line 713

/** // library marker kkossev.deviceProfileLib, line 715
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 716
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 717
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 718
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 719
 */ // library marker kkossev.deviceProfileLib, line 720
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 721
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 722
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 723
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 724
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 725
    // merge default commands with device-specific commands (device-specific takes precedence) // library marker kkossev.deviceProfileLib, line 726
    Map allCommandsMap = [:]  // library marker kkossev.deviceProfileLib, line 727
    if (deviceProfilesV3defaults?.defaultCommands != null) { allCommandsMap.putAll(deviceProfilesV3defaults.defaultCommands) } // library marker kkossev.deviceProfileLib, line 728
    if (DEVICE?.commands != null) { allCommandsMap.putAll(DEVICE.commands) } // library marker kkossev.deviceProfileLib, line 729
    if (allCommandsMap.isEmpty()) { // library marker kkossev.deviceProfileLib, line 730
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 731
        return false // library marker kkossev.deviceProfileLib, line 732
    } // library marker kkossev.deviceProfileLib, line 733
    // build case-insensitive command lookup map (lowercase -> actual command name) // library marker kkossev.deviceProfileLib, line 734
    Map<String, String> commandLookupMap = [:] // library marker kkossev.deviceProfileLib, line 735
    allCommandsMap.each { k, v ->  // library marker kkossev.deviceProfileLib, line 736
        commandLookupMap[k.toLowerCase()] = k  // library marker kkossev.deviceProfileLib, line 737
    } // library marker kkossev.deviceProfileLib, line 738
    // find the actual command name (case-insensitive lookup) // library marker kkossev.deviceProfileLib, line 739
    String actualCommand = command != null ? commandLookupMap[command.toLowerCase()] : null // library marker kkossev.deviceProfileLib, line 740
    if (actualCommand == null) { // library marker kkossev.deviceProfileLib, line 741
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${commandLookupMap.values()}" // library marker kkossev.deviceProfileLib, line 742
        return false // library marker kkossev.deviceProfileLib, line 743
    } // library marker kkossev.deviceProfileLib, line 744
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 745
    def func, funcResult // library marker kkossev.deviceProfileLib, line 746
    try { // library marker kkossev.deviceProfileLib, line 747
        // look up function from merged commands map // library marker kkossev.deviceProfileLib, line 748
        func = allCommandsMap.find { it.key == actualCommand }?.value // library marker kkossev.deviceProfileLib, line 749
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 750
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 751
            func = actualCommand // library marker kkossev.deviceProfileLib, line 752
        } // library marker kkossev.deviceProfileLib, line 753
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 754
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 755
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 756
        } // library marker kkossev.deviceProfileLib, line 757
        else { // library marker kkossev.deviceProfileLib, line 758
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 759
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 760
        } // library marker kkossev.deviceProfileLib, line 761
    } // library marker kkossev.deviceProfileLib, line 762
    catch (e) { // library marker kkossev.deviceProfileLib, line 763
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 764
        return false // library marker kkossev.deviceProfileLib, line 765
    } // library marker kkossev.deviceProfileLib, line 766
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 767
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 768
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 769
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 770
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 771
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 772
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 773
        } // library marker kkossev.deviceProfileLib, line 774
    } // library marker kkossev.deviceProfileLib, line 775
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 776
        return false // library marker kkossev.deviceProfileLib, line 777
    } // library marker kkossev.deviceProfileLib, line 778
     else { // library marker kkossev.deviceProfileLib, line 779
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 780
        return false // library marker kkossev.deviceProfileLib, line 781
    } // library marker kkossev.deviceProfileLib, line 782
    return true // library marker kkossev.deviceProfileLib, line 783
} // library marker kkossev.deviceProfileLib, line 784

/** // library marker kkossev.deviceProfileLib, line 786
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 787
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 788
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 789
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 790
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 791
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 792
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 793
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 794
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 795
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 796
 */ // library marker kkossev.deviceProfileLib, line 797
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 798
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 799
    Map input = [:] // library marker kkossev.deviceProfileLib, line 800
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 801
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 802
    Object preference // library marker kkossev.deviceProfileLib, line 803
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 804
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 805
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 806
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 807
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 808
    /* // library marker kkossev.deviceProfileLib, line 809
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 810
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 811
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 812
    */ // library marker kkossev.deviceProfileLib, line 813
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 814
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 815
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 816
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 817
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 818
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 819
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 820
        return [:] // library marker kkossev.deviceProfileLib, line 821
    } // library marker kkossev.deviceProfileLib, line 822
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 823
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 824
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 825
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 826
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 827
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 828
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 829
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 830
            input.range = "${Math.floor(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 831
        } // library marker kkossev.deviceProfileLib, line 832
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 833
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 834
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 835
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 836
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 837
            } // library marker kkossev.deviceProfileLib, line 838
        } // library marker kkossev.deviceProfileLib, line 839
    } // library marker kkossev.deviceProfileLib, line 840
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 841
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 842
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 843
    }/* // library marker kkossev.deviceProfileLib, line 844
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 845
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 846
    }*/ // library marker kkossev.deviceProfileLib, line 847
    else { // library marker kkossev.deviceProfileLib, line 848
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 849
        return [:] // library marker kkossev.deviceProfileLib, line 850
    } // library marker kkossev.deviceProfileLib, line 851
    if (foundMap.defVal != null) { // library marker kkossev.deviceProfileLib, line 852
        input.defaultValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 853
    } // library marker kkossev.deviceProfileLib, line 854
    return input // library marker kkossev.deviceProfileLib, line 855
} // library marker kkossev.deviceProfileLib, line 856

/** // library marker kkossev.deviceProfileLib, line 858
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 859
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 860
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 861
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 862
 */ // library marker kkossev.deviceProfileLib, line 863
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 864
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 865
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 866
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 867
    if (_DEBUG && SIMULATED_DEVICE_MODEL != null && SIMULATED_DEVICE_MANUFACTURER != null) { // library marker kkossev.deviceProfileLib, line 868
        deviceModel = SIMULATED_DEVICE_MODEL // library marker kkossev.deviceProfileLib, line 869
        deviceManufacturer = SIMULATED_DEVICE_MANUFACTURER // library marker kkossev.deviceProfileLib, line 870
        logWarn "<b>getDeviceNameAndProfile: using SIMULATED_DEVICE_MODEL ${SIMULATED_DEVICE_MODEL} and SIMULATED_DEVICE_MANUFACTURER ${SIMULATED_DEVICE_MANUFACTURER} in _DEBUG mode</b>" // library marker kkossev.deviceProfileLib, line 871
    } // library marker kkossev.deviceProfileLib, line 872
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 873
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 874
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 875
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 876
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 877
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 878
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 879
            } // library marker kkossev.deviceProfileLib, line 880
        } // library marker kkossev.deviceProfileLib, line 881
    } // library marker kkossev.deviceProfileLib, line 882
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 883
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 884
    } // library marker kkossev.deviceProfileLib, line 885
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 886
} // library marker kkossev.deviceProfileLib, line 887

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 889
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 890
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 891
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 892
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 893
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 894
        logInfo "unknown model ${dataValueModel} manufacturer ${dataValueManufacturer}" // library marker kkossev.deviceProfileLib, line 895
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 896
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 897
    } // library marker kkossev.deviceProfileLib, line 898
    if (deviceName != null && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 899
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 900
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 901
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 902
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 903
    } else { // library marker kkossev.deviceProfileLib, line 904
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 905
    } // library marker kkossev.deviceProfileLib, line 906
} // library marker kkossev.deviceProfileLib, line 907

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 909
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 910
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 911
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 912
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 913
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 914
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 915
            if (k != null) { // library marker kkossev.deviceProfileLib, line 916
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 917
                Map map = DEVICE.attributes?.find { it.name == k } // library marker kkossev.deviceProfileLib, line 918
                if (map != null) { // library marker kkossev.deviceProfileLib, line 919
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 920
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 921
                } // library marker kkossev.deviceProfileLib, line 922
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 923
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 924
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 925
                } // library marker kkossev.deviceProfileLib, line 926
            } // library marker kkossev.deviceProfileLib, line 927
        } // library marker kkossev.deviceProfileLib, line 928
    } // library marker kkossev.deviceProfileLib, line 929
    return cmds // library marker kkossev.deviceProfileLib, line 930
} // library marker kkossev.deviceProfileLib, line 931

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 933
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 934
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 935
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 936
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 937
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 938
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 939
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 940
            if (k != null) { // library marker kkossev.deviceProfileLib, line 941
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 942
                Map map = DEVICE.attributes?.find { it.name == k } // library marker kkossev.deviceProfileLib, line 943
                if (map != null) { // library marker kkossev.deviceProfileLib, line 944
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 945
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 946
                } // library marker kkossev.deviceProfileLib, line 947
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 948
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 949
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 950
                } // library marker kkossev.deviceProfileLib, line 951
            } // library marker kkossev.deviceProfileLib, line 952
        } // library marker kkossev.deviceProfileLib, line 953
    } // library marker kkossev.deviceProfileLib, line 954
    return cmds // library marker kkossev.deviceProfileLib, line 955
} // library marker kkossev.deviceProfileLib, line 956

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 958
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 959
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 960
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 961
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 962
    return cmds // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 966
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 967
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 968
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 969
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 970
    return cmds // library marker kkossev.deviceProfileLib, line 971
} // library marker kkossev.deviceProfileLib, line 972

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 974
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 975
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 976
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 977
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 978
    return cmds // library marker kkossev.deviceProfileLib, line 979
} // library marker kkossev.deviceProfileLib, line 980

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 982
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 983
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 984
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 985
    } // library marker kkossev.deviceProfileLib, line 986
} // library marker kkossev.deviceProfileLib, line 987

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 989
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 990
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 991
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 992
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 993
    } // library marker kkossev.deviceProfileLib, line 994
} // library marker kkossev.deviceProfileLib, line 995

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 997

// // library marker kkossev.deviceProfileLib, line 999
// called from parse() // library marker kkossev.deviceProfileLib, line 1000
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 1001
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 1002
// // library marker kkossev.deviceProfileLib, line 1003
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 1004
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 1005
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 1006
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 1007
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1008
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1009
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 1010
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 1011
} // library marker kkossev.deviceProfileLib, line 1012

// // library marker kkossev.deviceProfileLib, line 1014
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 1015
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 1016
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 1017
// // library marker kkossev.deviceProfileLib, line 1018
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 1019
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 1020
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 1021
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 1022
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1023
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1024
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 1025
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 1026
} // library marker kkossev.deviceProfileLib, line 1027

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 1029
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1030
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1031
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1032
    return isSpammy // library marker kkossev.deviceProfileLib, line 1033
} // library marker kkossev.deviceProfileLib, line 1034

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1036
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1037
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1038
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1039
    if (foundItem?.scale != null && foundItem?.scale != 0 && foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1040
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1041
    } // library marker kkossev.deviceProfileLib, line 1042
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1043
} // library marker kkossev.deviceProfileLib, line 1044

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1046
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1047
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1048
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1049
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1050
    } // library marker kkossev.deviceProfileLib, line 1051
    else { // library marker kkossev.deviceProfileLib, line 1052
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1053
    } // library marker kkossev.deviceProfileLib, line 1054
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1055
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1056
} // library marker kkossev.deviceProfileLib, line 1057

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1059
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1060
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1061
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1062
    } // library marker kkossev.deviceProfileLib, line 1063
    else { // library marker kkossev.deviceProfileLib, line 1064
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1065
    } // library marker kkossev.deviceProfileLib, line 1066
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1067
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1068
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1069
} // library marker kkossev.deviceProfileLib, line 1070

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1072
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1073
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1074
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1075
    def convertedValue // library marker kkossev.deviceProfileLib, line 1076
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1077
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1078
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1079
    } // library marker kkossev.deviceProfileLib, line 1080
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1081
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1082
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1083
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1084
            isEqual = false // library marker kkossev.deviceProfileLib, line 1085
        } // library marker kkossev.deviceProfileLib, line 1086
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1087
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1088
        } // library marker kkossev.deviceProfileLib, line 1089
    } // library marker kkossev.deviceProfileLib, line 1090
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1091
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1092
} // library marker kkossev.deviceProfileLib, line 1093

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1095
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1096
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1097
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1098
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1099
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1100
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1101
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1102
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1103
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1104
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1105
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1106
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1109
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1110
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1111
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1112
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1113
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1114
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1115
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1116
            } // library marker kkossev.deviceProfileLib, line 1117
            else { // library marker kkossev.deviceProfileLib, line 1118
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1119
            } // library marker kkossev.deviceProfileLib, line 1120
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1121
            break // library marker kkossev.deviceProfileLib, line 1122
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1123
        case 'number' : // library marker kkossev.deviceProfileLib, line 1124
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1125
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1126
            break // library marker kkossev.deviceProfileLib, line 1127
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1128
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1129
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1130
            break // library marker kkossev.deviceProfileLib, line 1131
        default : // library marker kkossev.deviceProfileLib, line 1132
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1133
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1134
    } // library marker kkossev.deviceProfileLib, line 1135
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1136
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1137
    } // library marker kkossev.deviceProfileLib, line 1138
    // // library marker kkossev.deviceProfileLib, line 1139
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1140
} // library marker kkossev.deviceProfileLib, line 1141

// // library marker kkossev.deviceProfileLib, line 1143
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1144
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1145
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1146
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1147
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1148
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1149
// // library marker kkossev.deviceProfileLib, line 1150
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1151
// // library marker kkossev.deviceProfileLib, line 1152
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1153
// // library marker kkossev.deviceProfileLib, line 1154
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1155
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1156
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1157
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1158
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1159
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1160
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1161
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1162
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1163
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1164
            break // library marker kkossev.deviceProfileLib, line 1165
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1166
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1167
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1168
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1169
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1170
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1171
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1172
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1173
            } // library marker kkossev.deviceProfileLib, line 1174
            else { // library marker kkossev.deviceProfileLib, line 1175
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1176
            } // library marker kkossev.deviceProfileLib, line 1177
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1178
            break // library marker kkossev.deviceProfileLib, line 1179
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1180
        case 'number' : // library marker kkossev.deviceProfileLib, line 1181
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1182
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1183
            break // library marker kkossev.deviceProfileLib, line 1184
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1185
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1186
            break // library marker kkossev.deviceProfileLib, line 1187
        default : // library marker kkossev.deviceProfileLib, line 1188
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1189
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1190
    } // library marker kkossev.deviceProfileLib, line 1191
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1192
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1193
} // library marker kkossev.deviceProfileLib, line 1194

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1196
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1197
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1198
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1199
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1200
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1201
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1202
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1203
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1204
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1205
    } // library marker kkossev.deviceProfileLib, line 1206
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1207
    try { // library marker kkossev.deviceProfileLib, line 1208
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1209
    } // library marker kkossev.deviceProfileLib, line 1210
    catch (e) { // library marker kkossev.deviceProfileLib, line 1211
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1212
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1213
    } // library marker kkossev.deviceProfileLib, line 1214
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1215
    return fncmd // library marker kkossev.deviceProfileLib, line 1216
} // library marker kkossev.deviceProfileLib, line 1217

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1219
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1220
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1221
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1222
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1223
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1224
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1225

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1227
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1228

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1230
    int value // library marker kkossev.deviceProfileLib, line 1231
    try { // library marker kkossev.deviceProfileLib, line 1232
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1233
    } // library marker kkossev.deviceProfileLib, line 1234
    catch (e) { // library marker kkossev.deviceProfileLib, line 1235
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1236
        return false // library marker kkossev.deviceProfileLib, line 1237
    } // library marker kkossev.deviceProfileLib, line 1238
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1239
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1240
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1241
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1242
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1243
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1244
        return false // library marker kkossev.deviceProfileLib, line 1245
    } // library marker kkossev.deviceProfileLib, line 1246
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1247
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1248
} // library marker kkossev.deviceProfileLib, line 1249

/** // library marker kkossev.deviceProfileLib, line 1251
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1252
 * // library marker kkossev.deviceProfileLib, line 1253
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1254
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1255
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1256
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1257
 * // library marker kkossev.deviceProfileLib, line 1258
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1259
 */ // library marker kkossev.deviceProfileLib, line 1260
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1261
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1262
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1263
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1264
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1265

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1267
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1268

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1270
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1271
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1272
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1273
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1274
        return false // library marker kkossev.deviceProfileLib, line 1275
    } // library marker kkossev.deviceProfileLib, line 1276
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1277
} // library marker kkossev.deviceProfileLib, line 1278

/* // library marker kkossev.deviceProfileLib, line 1280
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1281
 */ // library marker kkossev.deviceProfileLib, line 1282
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1283
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1284
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1285
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1286
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1287
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1288
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1289
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1290
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1291
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1292
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1293
        } // library marker kkossev.deviceProfileLib, line 1294
    } // library marker kkossev.deviceProfileLib, line 1295
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1296

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1298
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1299
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1300
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1301
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1302
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1303
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1304
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1305
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1306
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1307
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1308
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1309
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1310
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1311
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1312
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1313
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1314

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1316
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1317
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1318
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1319
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1320
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1321
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1322
        } // library marker kkossev.deviceProfileLib, line 1323
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1324
    } // library marker kkossev.deviceProfileLib, line 1325

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1327
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1328
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1329
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1330
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1331
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1332
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1333
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1334
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1335
            } // library marker kkossev.deviceProfileLib, line 1336
        } // library marker kkossev.deviceProfileLib, line 1337
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1338
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1339
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1340
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1341
            } // library marker kkossev.deviceProfileLib, line 1342
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1343
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1344
            try { // library marker kkossev.deviceProfileLib, line 1345
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1346
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1347
            } // library marker kkossev.deviceProfileLib, line 1348
            catch (e) { // library marker kkossev.deviceProfileLib, line 1349
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1350
            } // library marker kkossev.deviceProfileLib, line 1351
        } // library marker kkossev.deviceProfileLib, line 1352
    } // library marker kkossev.deviceProfileLib, line 1353
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1354
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1355
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1356
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1357
    } // library marker kkossev.deviceProfileLib, line 1358

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1360
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1361
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1362
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1363
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1364
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1365
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1366
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1367
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1368
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1369
            } // library marker kkossev.deviceProfileLib, line 1370
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1371
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1372
            } // library marker kkossev.deviceProfileLib, line 1373

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1375
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch ! // library marker kkossev.deviceProfileLib, line 1376
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1377
            // continue ... // library marker kkossev.deviceProfileLib, line 1378
            } // library marker kkossev.deviceProfileLib, line 1379
            // B13: raw-DP dedupe above ignores illuminanceCoeff - let handleIlluminanceEvent() do its own correct-space delta filter // library marker kkossev.deviceProfileLib, line 1380
            else if (name == 'illuminance' || name == 'illuminance_lux') { // library marker kkossev.deviceProfileLib, line 1381
                logDebug "patch for ${name} (B13)" // library marker kkossev.deviceProfileLib, line 1382
            // continue ... // library marker kkossev.deviceProfileLib, line 1383
            } // library marker kkossev.deviceProfileLib, line 1384

            else { // library marker kkossev.deviceProfileLib, line 1386
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1387
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1388
                } // library marker kkossev.deviceProfileLib, line 1389
                else { // library marker kkossev.deviceProfileLib, line 1390
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1391
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1392
                } // library marker kkossev.deviceProfileLib, line 1393
            } // library marker kkossev.deviceProfileLib, line 1394
        } // library marker kkossev.deviceProfileLib, line 1395

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1397
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1398
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1399
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1400
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1401
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1402
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1403
        } // library marker kkossev.deviceProfileLib, line 1404
        else { // library marker kkossev.deviceProfileLib, line 1405
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1406
            boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.deviceProfileLib, line 1407
            String eventType = isDigital ? 'digital' : 'physical' // library marker kkossev.deviceProfileLib, line 1408
            String eventDescText = "${descText}${isDigital ? ' [digital]' : ' [physical]'}" // library marker kkossev.deviceProfileLib, line 1409
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: eventDescText, type: eventType, isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1410
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1411
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1412
                logInfo "${eventDescText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1413
            } // library marker kkossev.deviceProfileLib, line 1414
        } // library marker kkossev.deviceProfileLib, line 1415
    } // library marker kkossev.deviceProfileLib, line 1416
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1417
} // library marker kkossev.deviceProfileLib, line 1418

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1420
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1421
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1422
    //debug = true // library marker kkossev.deviceProfileLib, line 1423
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1424
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1425
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1426
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1427
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1428
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1429
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1430
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1431
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1432
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1433
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1434
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1435
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1436
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1437
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1438
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1439
            try { // library marker kkossev.deviceProfileLib, line 1440
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1441
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1442
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1443
            } // library marker kkossev.deviceProfileLib, line 1444
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1445
            try { // library marker kkossev.deviceProfileLib, line 1446
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1447
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1448
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1449
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1450
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1451
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1452
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1453
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1454
                    } // library marker kkossev.deviceProfileLib, line 1455
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1456
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1457
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1458
                    } else { // library marker kkossev.deviceProfileLib, line 1459
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1460
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1461
                    } // library marker kkossev.deviceProfileLib, line 1462
                } // library marker kkossev.deviceProfileLib, line 1463
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1464
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1465
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1466
            } // library marker kkossev.deviceProfileLib, line 1467
            catch (e) { // library marker kkossev.deviceProfileLib, line 1468
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1469
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1470
                try { // library marker kkossev.deviceProfileLib, line 1471
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1472
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1473
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1474
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1475
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1476
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1477
                } // library marker kkossev.deviceProfileLib, line 1478
            } // library marker kkossev.deviceProfileLib, line 1479
        } // library marker kkossev.deviceProfileLib, line 1480
        total ++ // library marker kkossev.deviceProfileLib, line 1481
    } // library marker kkossev.deviceProfileLib, line 1482
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1483
    return true // library marker kkossev.deviceProfileLib, line 1484
} // library marker kkossev.deviceProfileLib, line 1485

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1487
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1488
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1489
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1490
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1491
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1492
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1493
    } // library marker kkossev.deviceProfileLib, line 1494
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1495
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1496
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1497
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1498
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1499
    } // library marker kkossev.deviceProfileLib, line 1500
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1501
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1502
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1503
} // library marker kkossev.deviceProfileLib, line 1504

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1506
    int count = 0 // library marker kkossev.deviceProfileLib, line 1507
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1508
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1509
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1510
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1511
            count++ // library marker kkossev.deviceProfileLib, line 1512
        } // library marker kkossev.deviceProfileLib, line 1513
    } // library marker kkossev.deviceProfileLib, line 1514
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1515
} // library marker kkossev.deviceProfileLib, line 1516

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1518
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1519
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1520
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1521
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1522
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1523
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1524
            } // library marker kkossev.deviceProfileLib, line 1525
        } // library marker kkossev.deviceProfileLib, line 1526
    } // library marker kkossev.deviceProfileLib, line 1527
} // library marker kkossev.deviceProfileLib, line 1528

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.1.0' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * .............................. // library marker kkossev.commonLib, line 24
  * ver. 3.5.2  2025-08-13 kkossev  - Status attribute renamed to _status_ // library marker kkossev.commonLib, line 25
  * ver. 4.0.0  2025-09-17 kkossev  - deviceProfileV4; HOBEIAN as Tuya device; customInitialize() hook; // library marker kkossev.commonLib, line 26
  * ver. 4.0.1  2025-10-14 kkossev  - added clusters 0xFC80 and 0xFC81 // library marker kkossev.commonLib, line 27
  * ver. 4.0.2  2025-10-18 kkossev  - added tuyaDelay in sendTuyaCommand() // library marker kkossev.commonLib, line 28
  * ver. 4.0.3  2025-10-18 kkossev  - added ignoreDuplicatedZigbeeMessages setting; DIGITAL_TIMER increased to 5000 ms // library marker kkossev.commonLib, line 29
  * ver. 4.0.4  2026-06-04 kkossev  - added ED00 cluster; // library marker kkossev.commonLib, line 30
  * ver. 4.0.5  2026-08-03 kkossev  - bug fixes // library marker kkossev.commonLib, line 31
  * ver. 4.1.0  2026-08-05 kkossev  - (dev. branch) the administrative commands drop-down moved from configure(par) to the new deviceUtilities(par) command, so that configure() is again a plain Configuration capability button; removed the two separator entries from ConfigureOpts; configureHelp() is callable again and shows the command list and a '_status_' event when nothing was selected; do not use 'defaultValue' in a command parameter - it does not preselect the drop-down, but it IS submitted when Run is pressed without a selection!; configure() now shows a 'sleepy devices can not be configured' warning text; ping() icon changed to the antenna bars; added a one-click 'loadAllDefaults' command button // library marker kkossev.commonLib, line 32
  * // library marker kkossev.commonLib, line 33
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 34
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 35
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 36
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 37
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 38
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 40
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 41
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 43
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 44
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 45
  * // library marker kkossev.commonLib, line 46
*/ // library marker kkossev.commonLib, line 47

String commonLibVersion() { '4.1.0' } // library marker kkossev.commonLib, line 49
String commonLibStamp() { '2026/08/05 11:18 PM' } // library marker kkossev.commonLib, line 50

import groovy.transform.Field // library marker kkossev.commonLib, line 52
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 53
import hubitat.device.Protocol // library marker kkossev.commonLib, line 54
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 55
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 56
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 57
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 58
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 59
import java.math.BigDecimal // library marker kkossev.commonLib, line 60

metadata { // library marker kkossev.commonLib, line 62
        if (_DEBUG) { // library marker kkossev.commonLib, line 63
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 64
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 66
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 67
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 69
            ] // library marker kkossev.commonLib, line 70
        } // library marker kkossev.commonLib, line 71

        // common capabilities for all device types // library marker kkossev.commonLib, line 73
        capability 'Configuration' // library marker kkossev.commonLib, line 74
        capability 'Refresh' // library marker kkossev.commonLib, line 75
        capability 'HealthCheck' // library marker kkossev.commonLib, line 76
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 77

        // common attributes for all device types // library marker kkossev.commonLib, line 79
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 80
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 81
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 82

        // common commands for all device types // library marker kkossev.commonLib, line 84
        // 'configure' below carries a description-only parameter map (NO 'type' key!), exactly like ping and refresh - it just renders the help text under the button and submits nothing. // library marker kkossev.commonLib, line 85
        // NEVER give it a typed parameter: an ENUM here used to shadow the no-argument configure() of capability 'Configuration', making the dispatch depend on whether the platform happened to supply a value. // library marker kkossev.commonLib, line 86
        command 'configure', [[name:"✋ This button can not configure battery-powered 'sleepy' devices. Pair the device again to your hub, without deleting it!"]] // library marker kkossev.commonLib, line 87
        command 'deviceUtilities', [[name:'⚙️ Advanced administrative and diagnostic commands • Use only when troubleshooting or reconfiguring the device', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]]    // do NOT add a 'defaultValue' here! The drop-down still displays '- No selection -', but the platform submits the defaultValue when Run is pressed - i.e. an un-selected Run silently executed 'LOAD ALL DEFAULTS' (tested on C-8 Pro 2.5.1.143) // library marker kkossev.commonLib, line 88
        // one-click shortcut for the most used deviceUtilities entry. Description-only parameter map again - NEVER give loadAllDefaults a typed parameter: deviceUtilities dispatches it as "$func"() with no arguments, so an un-selected Run would hit the no-argument overload and wipe the device immediately. // library marker kkossev.commonLib, line 89
        command 'loadAllDefaults', [[name:'⚠️ Erases all preferences, states, scheduled jobs and child devices, then reloads the driver defaults • Use after switching drivers, or when the device was not recognised by an older version']] // library marker kkossev.commonLib, line 90
        command 'ping', [[name:'📶 Test device connectivity and measure response time • Updates the RTT attribute with round-trip time in milliseconds']] // library marker kkossev.commonLib, line 91
        command 'refresh', [[name:"🔄 Query the device for current state and update the attributes. • ⚠️ Battery-powered 'sleepy' devices may not respond!"]] // library marker kkossev.commonLib, line 92

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 94
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 95

    preferences { // library marker kkossev.commonLib, line 97
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 98
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 99
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 100

        if (device) { // library marker kkossev.commonLib, line 102
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 103
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 104
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 105
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 106
                input name: 'ignoreDuplicatedZigbeeMessages', type: 'bool', title: '<b>Ignore Duplicated Zigbee Messages</b>', defaultValue: false, description: 'Ignore identical Zigbee attribute reports received within short time periods to reduce log spam and redundant processing' // library marker kkossev.commonLib, line 107
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 108
            } // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
    } // library marker kkossev.commonLib, line 111
} // library marker kkossev.commonLib, line 112

@Field static final Integer IGNORE_DUPLICATED_ZIGBEE_MESSAGES_TIMER = 1000  // 1 second // library marker kkossev.commonLib, line 114
@Field static final Integer DIGITAL_TIMER = 5000             // command was sent by this driver // library marker kkossev.commonLib, line 115
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 116
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 117
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 118
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 119
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 120
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 122
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 123
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 124
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 125

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 127
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 128
] // library marker kkossev.commonLib, line 129
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 130
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 131
] // library marker kkossev.commonLib, line 132

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 134
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 135
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 136
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 137
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 138
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 139
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 140
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 141
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'] // library marker kkossev.commonLib, line 142
] // library marker kkossev.commonLib, line 143

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 145

/** // library marker kkossev.commonLib, line 147
 * Parse Zigbee message // library marker kkossev.commonLib, line 148
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 149
 */ // library marker kkossev.commonLib, line 150
public void parse(final String description) { // library marker kkossev.commonLib, line 151

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 153
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 154
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 155
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 156
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 157
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 158

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 160
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 161
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 162
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 163
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 164
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 165
        return // library marker kkossev.commonLib, line 166
    } // library marker kkossev.commonLib, line 167
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 168
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 169
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 170
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 171
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 172
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 173
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 174
        return // library marker kkossev.commonLib, line 175
    } // library marker kkossev.commonLib, line 176

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 178
        return // library marker kkossev.commonLib, line 179
    } // library marker kkossev.commonLib, line 180
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 181

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 183
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 184

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 186
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 187
        return // library marker kkossev.commonLib, line 188
    } // library marker kkossev.commonLib, line 189
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 190
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 191
        return // library marker kkossev.commonLib, line 192
    } // library marker kkossev.commonLib, line 193
    // // library marker kkossev.commonLib, line 194
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 195
    // // library marker kkossev.commonLib, line 196
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 197
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 198
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 199
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 200
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 201
            } // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 204
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 205
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 206
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 207
            } // library marker kkossev.commonLib, line 208
            break // library marker kkossev.commonLib, line 209
        default: // library marker kkossev.commonLib, line 210
            if (settings.logEnable) { // library marker kkossev.commonLib, line 211
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 212
            } // library marker kkossev.commonLib, line 213
            break // library marker kkossev.commonLib, line 214
    } // library marker kkossev.commonLib, line 215
} // library marker kkossev.commonLib, line 216

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 218
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 219
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 220
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 221
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 222
    0xFC80: 'FC80',              0xFC81: 'FC81',             0xFCC0: 'XiaomiFCC0',       0xED00: 'ED00' // library marker kkossev.commonLib, line 223
] // library marker kkossev.commonLib, line 224

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 226
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 227
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 228
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 229
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 230
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 231
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 232
        return false // library marker kkossev.commonLib, line 233
    } // library marker kkossev.commonLib, line 234
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 235
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 236
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 237
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 238
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 239
        return true // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 242
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 243
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 244
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 245
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 246
        return true // library marker kkossev.commonLib, line 247
    } // library marker kkossev.commonLib, line 248
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 249
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 250
    } // library marker kkossev.commonLib, line 251
    return false // library marker kkossev.commonLib, line 252
} // library marker kkossev.commonLib, line 253

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 255
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 256
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 257
} // library marker kkossev.commonLib, line 258

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 260
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 261
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 262
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 263
    } // library marker kkossev.commonLib, line 264
    return false // library marker kkossev.commonLib, line 265
} // library marker kkossev.commonLib, line 266

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 268
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 269
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 270
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 271
    } // library marker kkossev.commonLib, line 272
    return false // library marker kkossev.commonLib, line 273
} // library marker kkossev.commonLib, line 274

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 276
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 277
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 278
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 279
] // library marker kkossev.commonLib, line 280

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 282
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 283
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 284
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 285
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 286
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 287
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 288
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 289
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 290
    List<String> cmds = [] // library marker kkossev.commonLib, line 291
    switch (clusterId) { // library marker kkossev.commonLib, line 292
        case 0x0005 : // library marker kkossev.commonLib, line 293
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 294
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 295
            // send the active endpoint response // library marker kkossev.commonLib, line 296
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 297
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0x0006 : // library marker kkossev.commonLib, line 300
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 302
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 303
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 304
            break // library marker kkossev.commonLib, line 305
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 306
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 307
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 311
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 314
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 315
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 319
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 322
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 323
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 324
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 325
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        default : // library marker kkossev.commonLib, line 328
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 329
            break // library marker kkossev.commonLib, line 330
    } // library marker kkossev.commonLib, line 331
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 332
} // library marker kkossev.commonLib, line 333

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 335
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 336
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 337
    switch (commandId) { // library marker kkossev.commonLib, line 338
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 339
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 340
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 341
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 342
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 343
        default: // library marker kkossev.commonLib, line 344
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 345
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 346
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 347
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 348
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 349
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 350
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 351
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 352
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 353
            } // library marker kkossev.commonLib, line 354
            break // library marker kkossev.commonLib, line 355
    } // library marker kkossev.commonLib, line 356
} // library marker kkossev.commonLib, line 357

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 359
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 360
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 361
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 362
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 363
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 364
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 365
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 366
    } // library marker kkossev.commonLib, line 367
    else { // library marker kkossev.commonLib, line 368
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 369
    } // library marker kkossev.commonLib, line 370
} // library marker kkossev.commonLib, line 371

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 373
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 374
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 375
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 376
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 377
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 378
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 379
    } // library marker kkossev.commonLib, line 380
    else { // library marker kkossev.commonLib, line 381
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 382
    } // library marker kkossev.commonLib, line 383
} // library marker kkossev.commonLib, line 384

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 386
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 387
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 388
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 389
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 390
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 391
        state.reportingEnabled = true // library marker kkossev.commonLib, line 392
    } // library marker kkossev.commonLib, line 393
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 394
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 395
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 396
    } else { // library marker kkossev.commonLib, line 397
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 398
    } // library marker kkossev.commonLib, line 399
} // library marker kkossev.commonLib, line 400

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 402
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 403
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 404
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 405
    if (status == 0) { // library marker kkossev.commonLib, line 406
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 407
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 408
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 409
        int delta = 0 // library marker kkossev.commonLib, line 410
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 411
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 412
        } // library marker kkossev.commonLib, line 413
        else { // library marker kkossev.commonLib, line 414
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 415
        } // library marker kkossev.commonLib, line 416
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 417
    } // library marker kkossev.commonLib, line 418
    else { // library marker kkossev.commonLib, line 419
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
} // library marker kkossev.commonLib, line 422

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 424
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 425
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 426
        return false // library marker kkossev.commonLib, line 427
    } // library marker kkossev.commonLib, line 428
    // execute the customHandler function // library marker kkossev.commonLib, line 429
    Boolean result = false // library marker kkossev.commonLib, line 430
    try { // library marker kkossev.commonLib, line 431
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 432
    } // library marker kkossev.commonLib, line 433
    catch (e) { // library marker kkossev.commonLib, line 434
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 435
        return false // library marker kkossev.commonLib, line 436
    } // library marker kkossev.commonLib, line 437
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 438
    return result // library marker kkossev.commonLib, line 439
} // library marker kkossev.commonLib, line 440

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 442
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 443
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 444
    final String commandId = data[0] // library marker kkossev.commonLib, line 445
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 446
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 447
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 448
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 449
    } else { // library marker kkossev.commonLib, line 450
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 451
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 452
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 453
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 454
        } // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 459
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 460
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 461
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 462

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 464
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 465
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 466
] // library marker kkossev.commonLib, line 467

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 469
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 470
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 471
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 472
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 473
] // library marker kkossev.commonLib, line 474

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 476
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 477
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 478
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 479
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 480
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 481
    return avg // library marker kkossev.commonLib, line 482
} // library marker kkossev.commonLib, line 483

private void handlePingResponse() { // library marker kkossev.commonLib, line 485
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 486
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 487
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 488

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 490
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 491
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 492
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 493
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 494
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 495
        sendRttEvent() // library marker kkossev.commonLib, line 496
    } // library marker kkossev.commonLib, line 497
    else { // library marker kkossev.commonLib, line 498
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 499
    } // library marker kkossev.commonLib, line 500
    state.states['isPing'] = false // library marker kkossev.commonLib, line 501
} // library marker kkossev.commonLib, line 502

/* // library marker kkossev.commonLib, line 504
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 505
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 506
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 507
*/ // library marker kkossev.commonLib, line 508
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 509

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 511
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 512
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 513
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 514
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 515
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 516
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 517
        case 0x0000: // library marker kkossev.commonLib, line 518
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 519
            break // library marker kkossev.commonLib, line 520
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 521
            if (isPing) { // library marker kkossev.commonLib, line 522
                handlePingResponse() // library marker kkossev.commonLib, line 523
            } // library marker kkossev.commonLib, line 524
            else { // library marker kkossev.commonLib, line 525
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 526
            } // library marker kkossev.commonLib, line 527
            break // library marker kkossev.commonLib, line 528
        case 0x0004: // library marker kkossev.commonLib, line 529
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 530
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 531
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 532
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 533
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 534
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 535
            } // library marker kkossev.commonLib, line 536
            break // library marker kkossev.commonLib, line 537
        case 0x0005: // library marker kkossev.commonLib, line 538
            if (isPing) { // library marker kkossev.commonLib, line 539
                handlePingResponse() // library marker kkossev.commonLib, line 540
            } // library marker kkossev.commonLib, line 541
            else { // library marker kkossev.commonLib, line 542
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 543
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 544
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 545
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 546
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 547
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 548
                } // library marker kkossev.commonLib, line 549
            } // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0x0007: // library marker kkossev.commonLib, line 552
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 553
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 554
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 555
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 556
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 557
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 558
            } // library marker kkossev.commonLib, line 559
            break // library marker kkossev.commonLib, line 560
        case 0xFFDF: // library marker kkossev.commonLib, line 561
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 562
            break // library marker kkossev.commonLib, line 563
        case 0xFFE2: // library marker kkossev.commonLib, line 564
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 565
            break // library marker kkossev.commonLib, line 566
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 567
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 568
            break // library marker kkossev.commonLib, line 569
        case 0xFFFE: // library marker kkossev.commonLib, line 570
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 571
            break // library marker kkossev.commonLib, line 572
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 573
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 574
            logInfo "device firmware version is ${version}" // library marker kkossev.commonLib, line 575
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 576
            break // library marker kkossev.commonLib, line 577
        default: // library marker kkossev.commonLib, line 578
            logDebug "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 579
            break // library marker kkossev.commonLib, line 580
    } // library marker kkossev.commonLib, line 581
} // library marker kkossev.commonLib, line 582

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 584
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 585
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 586
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 587
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 588
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 589
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 590
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 591
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 592
        default: logDebug "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 593
    } // library marker kkossev.commonLib, line 594
} // library marker kkossev.commonLib, line 595

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 597
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 598
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 599

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 601
    Map descMap = [:] // library marker kkossev.commonLib, line 602
    try { // library marker kkossev.commonLib, line 603
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 604
    } // library marker kkossev.commonLib, line 605
    catch (e1) { // library marker kkossev.commonLib, line 606
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 607
        // try alternative custom parsing // library marker kkossev.commonLib, line 608
        descMap = [:] // library marker kkossev.commonLib, line 609
        try { // library marker kkossev.commonLib, line 610
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 611
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 612
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 613
            } // library marker kkossev.commonLib, line 614
        } // library marker kkossev.commonLib, line 615
        catch (e2) { // library marker kkossev.commonLib, line 616
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 617
            return [:] // library marker kkossev.commonLib, line 618
        } // library marker kkossev.commonLib, line 619
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621
    return descMap // library marker kkossev.commonLib, line 622
} // library marker kkossev.commonLib, line 623

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 625
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 626
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 627
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 628
        return false // library marker kkossev.commonLib, line 629
    } // library marker kkossev.commonLib, line 630
    // try to parse ... // library marker kkossev.commonLib, line 631
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 632
    Map descMap = [:] // library marker kkossev.commonLib, line 633
    try { // library marker kkossev.commonLib, line 634
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 635
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 636
    } // library marker kkossev.commonLib, line 637
    catch (e) { // library marker kkossev.commonLib, line 638
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 639
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 640
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 641
        return true // library marker kkossev.commonLib, line 642
    } // library marker kkossev.commonLib, line 643

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 645
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 646
    } // library marker kkossev.commonLib, line 647
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 648
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 651
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
    else { // library marker kkossev.commonLib, line 654
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 655
        return false // library marker kkossev.commonLib, line 656
    } // library marker kkossev.commonLib, line 657
    return true    // processed // library marker kkossev.commonLib, line 658
} // library marker kkossev.commonLib, line 659

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 661
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 662
  /* // library marker kkossev.commonLib, line 663
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 664
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 665
        return true // library marker kkossev.commonLib, line 666
    } // library marker kkossev.commonLib, line 667
*/ // library marker kkossev.commonLib, line 668
    Map descMap = [:] // library marker kkossev.commonLib, line 669
    try { // library marker kkossev.commonLib, line 670
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
    catch (e1) { // library marker kkossev.commonLib, line 673
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 674
        // try alternative custom parsing // library marker kkossev.commonLib, line 675
        descMap = [:] // library marker kkossev.commonLib, line 676
        try { // library marker kkossev.commonLib, line 677
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 678
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 679
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 680
            } // library marker kkossev.commonLib, line 681
        } // library marker kkossev.commonLib, line 682
        catch (e2) { // library marker kkossev.commonLib, line 683
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 684
            return true // library marker kkossev.commonLib, line 685
        } // library marker kkossev.commonLib, line 686
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 687
    } // library marker kkossev.commonLib, line 688
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 689
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 690
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 691
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 692
        return false // library marker kkossev.commonLib, line 693
    } // library marker kkossev.commonLib, line 694
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 695
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 696
    // attribute report received // library marker kkossev.commonLib, line 697
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 698
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 699
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 700
    } // library marker kkossev.commonLib, line 701
    attrData.each { // library marker kkossev.commonLib, line 702
        if (it.status == '86') { // library marker kkossev.commonLib, line 703
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 704
        // TODO - skip parsing? // library marker kkossev.commonLib, line 705
        } // library marker kkossev.commonLib, line 706
        switch (it.cluster) { // library marker kkossev.commonLib, line 707
            case '0000' : // library marker kkossev.commonLib, line 708
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 709
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 710
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 711
                } // library marker kkossev.commonLib, line 712
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 713
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 714
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 715
                } // library marker kkossev.commonLib, line 716
                else { // library marker kkossev.commonLib, line 717
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 718
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 719
                } // library marker kkossev.commonLib, line 720
                break // library marker kkossev.commonLib, line 721
            default : // library marker kkossev.commonLib, line 722
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 723
                break // library marker kkossev.commonLib, line 724
        } // switch // library marker kkossev.commonLib, line 725
    } // for each attribute // library marker kkossev.commonLib, line 726
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 727
} // library marker kkossev.commonLib, line 728

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 730
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 731
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 732
} // library marker kkossev.commonLib, line 733

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 735
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 736
} // library marker kkossev.commonLib, line 737

/* // library marker kkossev.commonLib, line 739
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 740
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 741
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 742
*/ // library marker kkossev.commonLib, line 743
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 744
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 745
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 746

// Tuya Commands // library marker kkossev.commonLib, line 748
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 749
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 750
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 751
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 752
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 753

// tuya DP type // library marker kkossev.commonLib, line 755
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 756
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 757
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 758
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 759
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 760
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 761

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 763
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 764
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 765
    long offset = 0 // library marker kkossev.commonLib, line 766
    int offsetHours = 0 // library marker kkossev.commonLib, line 767
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 768
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 769
    try { // library marker kkossev.commonLib, line 770
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 771
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 772
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 773
    } catch (e) { // library marker kkossev.commonLib, line 774
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 775
    } // library marker kkossev.commonLib, line 776
    // // library marker kkossev.commonLib, line 777
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 778
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 779
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 780
} // library marker kkossev.commonLib, line 781

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 783
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 784
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 785
        syncTuyaDateTime() // library marker kkossev.commonLib, line 786
    } // library marker kkossev.commonLib, line 787
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 788
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 789
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 790
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 791
        if (status != '00') { // library marker kkossev.commonLib, line 792
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 793
        } // library marker kkossev.commonLib, line 794
    } // library marker kkossev.commonLib, line 795
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 796
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 797
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 798
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 799
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 800
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} data=${descMap?.data})" // library marker kkossev.commonLib, line 801
            return // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 804
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 805
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 806
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 807
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 808
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 809
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 810
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 811
            } // library marker kkossev.commonLib, line 812
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 813
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 814
        } // library marker kkossev.commonLib, line 815
    } // library marker kkossev.commonLib, line 816
    else { // library marker kkossev.commonLib, line 817
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
} // library marker kkossev.commonLib, line 820

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 822
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 823
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 824
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 825
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 826
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 827
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 828
        } // library marker kkossev.commonLib, line 829
    } // library marker kkossev.commonLib, line 830
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 831
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 832
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 833
        if (this.respondsTo('isInCooldown') && isInCooldown()) { // library marker kkossev.commonLib, line 834
            logDebug "standardProcessTuyaDP: device is in cooldown, skipping processing of dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 835
            return // library marker kkossev.commonLib, line 836
        } // library marker kkossev.commonLib, line 837
        if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 838
            ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 839
        } // library marker kkossev.commonLib, line 840
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 841
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 842
        } // library marker kkossev.commonLib, line 843
    } // library marker kkossev.commonLib, line 844
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 845
} // library marker kkossev.commonLib, line 846

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 848
    int retValue = 0 // library marker kkossev.commonLib, line 849
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 850
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 851
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 852
        int power = 1 // library marker kkossev.commonLib, line 853
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 854
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 855
            power = power * 256 // library marker kkossev.commonLib, line 856
        } // library marker kkossev.commonLib, line 857
    } // library marker kkossev.commonLib, line 858
    return retValue // library marker kkossev.commonLib, line 859
} // library marker kkossev.commonLib, line 860

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 862

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 864
    List<String> cmds = [] // library marker kkossev.commonLib, line 865
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 866
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 867
    int tuyaCmd // library marker kkossev.commonLib, line 868
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 869
    if (this.respondsTo('getDEVICE') && getDEVICE()?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 870
        tuyaCmd = getDEVICE().device.tuyaCmd // library marker kkossev.commonLib, line 871
    } // library marker kkossev.commonLib, line 872
    else { // library marker kkossev.commonLib, line 873
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 874
    } // library marker kkossev.commonLib, line 875
    // Get delay from device profile or use default // library marker kkossev.commonLib, line 876
    int tuyaDelay = DEVICE?.device?.tuyaDelay as Integer ?: 201 // library marker kkossev.commonLib, line 877
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = tuyaDelay, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 878
    logDebug "getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 879
    return cmds // library marker kkossev.commonLib, line 880
} // library marker kkossev.commonLib, line 881

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 883

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 885
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 886
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 887
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 888
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 893
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 894
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 895
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 896
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 897
} // library marker kkossev.commonLib, line 898

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 900
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 901
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 902
    return cmds // library marker kkossev.commonLib, line 903
} // library marker kkossev.commonLib, line 904

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 906
    List<String> cmds = [] // library marker kkossev.commonLib, line 907
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 908
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 909
    } // library marker kkossev.commonLib, line 910
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 911
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 912
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 913
        return // library marker kkossev.commonLib, line 914
    } // library marker kkossev.commonLib, line 915
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 916
} // library marker kkossev.commonLib, line 917

// Invoked from configure() // library marker kkossev.commonLib, line 919
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 920
    List<String> cmds = [] // library marker kkossev.commonLib, line 921
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 922
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 923
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 924
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 925
    } // library marker kkossev.commonLib, line 926
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 927
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 928
    return cmds // library marker kkossev.commonLib, line 929
} // library marker kkossev.commonLib, line 930

// Invoked from configure() // library marker kkossev.commonLib, line 932
public List<String> configureDevice() { // library marker kkossev.commonLib, line 933
    List<String> cmds = [] // library marker kkossev.commonLib, line 934
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 935
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 936
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 937
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 938
    } // library marker kkossev.commonLib, line 939
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 940
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 941
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 942
    return cmds // library marker kkossev.commonLib, line 943
} // library marker kkossev.commonLib, line 944

/* // library marker kkossev.commonLib, line 946
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 947
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 948
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 949
*/ // library marker kkossev.commonLib, line 950

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 952
    List<String> cmds = [] // library marker kkossev.commonLib, line 953
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 954
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 955
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 956
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 957
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 958
            } // library marker kkossev.commonLib, line 959
        } // library marker kkossev.commonLib, line 960
    } // library marker kkossev.commonLib, line 961
    return cmds // library marker kkossev.commonLib, line 962
} // library marker kkossev.commonLib, line 963

public void refresh() { // library marker kkossev.commonLib, line 965
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 966
    checkDriverVersion(state) // library marker kkossev.commonLib, line 967
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 968
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 969
        customCmds = customRefresh() // library marker kkossev.commonLib, line 970
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 971
    } // library marker kkossev.commonLib, line 972
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 973
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 974
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 975
    } // library marker kkossev.commonLib, line 976
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 977
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 978
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 979
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
    else { // library marker kkossev.commonLib, line 982
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 983
    } // library marker kkossev.commonLib, line 984
} // library marker kkossev.commonLib, line 985

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 987
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 988
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 989

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 991
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 992
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 993
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    else { // library marker kkossev.commonLib, line 996
        logInfo "${info}" // library marker kkossev.commonLib, line 997
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 998
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 999
    } // library marker kkossev.commonLib, line 1000
} // library marker kkossev.commonLib, line 1001

public void ping() { // library marker kkossev.commonLib, line 1003
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1004
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1005
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1006
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1007
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1008
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1009
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1010
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1011
    } // library marker kkossev.commonLib, line 1012
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1013
    logDebug 'ping...' // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

private void virtualPong() { // library marker kkossev.commonLib, line 1017
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1018
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1019
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1020
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1021
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1022
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1023
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1024
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1025
        sendRttEvent() // library marker kkossev.commonLib, line 1026
    } // library marker kkossev.commonLib, line 1027
    else { // library marker kkossev.commonLib, line 1028
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1031
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1032
} // library marker kkossev.commonLib, line 1033

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1035
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1036
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1037
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1038
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1039
    if (value == null) { // library marker kkossev.commonLib, line 1040
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1041
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1042
    } // library marker kkossev.commonLib, line 1043
    else { // library marker kkossev.commonLib, line 1044
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1045
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1046
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
} // library marker kkossev.commonLib, line 1049

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1051
    if (cluster != null) { // library marker kkossev.commonLib, line 1052
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1053
    } // library marker kkossev.commonLib, line 1054
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1055
    return 'NULL' // library marker kkossev.commonLib, line 1056
} // library marker kkossev.commonLib, line 1057

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1059
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1060
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1061
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1065
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1066
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1067
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1068
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1069
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
} // library marker kkossev.commonLib, line 1072

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1074
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1075
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1076
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1077
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1078
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1079
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1080
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1081
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1082
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1083
            } // library marker kkossev.commonLib, line 1084
        } // library marker kkossev.commonLib, line 1085
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
} // library marker kkossev.commonLib, line 1088

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1090
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1091
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1092
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1093
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1094
    } // library marker kkossev.commonLib, line 1095
    else { // library marker kkossev.commonLib, line 1096
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1097
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1098
    } // library marker kkossev.commonLib, line 1099
} // library marker kkossev.commonLib, line 1100

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1102
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1103
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1104
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1105
} // library marker kkossev.commonLib, line 1106

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1108
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1109
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1110
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1111
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1112
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1113
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
} // library marker kkossev.commonLib, line 1116

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1118
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1119
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1120
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1121
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1122
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1123
            logWarn 'not present!' // library marker kkossev.commonLib, line 1124
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1125
        } // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
    else { // library marker kkossev.commonLib, line 1128
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1131
    // added 03/06/2025 // library marker kkossev.commonLib, line 1132
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1133
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1134
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1135
    } // library marker kkossev.commonLib, line 1136
} // library marker kkossev.commonLib, line 1137

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1139
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1140
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1141
    if (value == 'online') { // library marker kkossev.commonLib, line 1142
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
    else { // library marker kkossev.commonLib, line 1145
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
} // library marker kkossev.commonLib, line 1148

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1150
void updated() { // library marker kkossev.commonLib, line 1151
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1152
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1153
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1154
    unschedule() // library marker kkossev.commonLib, line 1155

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1157
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1158
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1161
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1162
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1166
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1167
        // schedule the periodic timer // library marker kkossev.commonLib, line 1168
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1169
        if (interval > 0) { // library marker kkossev.commonLib, line 1170
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1171
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthMethod]} method" // library marker kkossev.commonLib, line 1172
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1173
        } // library marker kkossev.commonLib, line 1174
    } // library marker kkossev.commonLib, line 1175
    else { // library marker kkossev.commonLib, line 1176
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1177
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1180
        customUpdated() // library marker kkossev.commonLib, line 1181
    } // library marker kkossev.commonLib, line 1182

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1184
} // library marker kkossev.commonLib, line 1185

private void logsOff() { // library marker kkossev.commonLib, line 1187
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1188
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190
private void traceOff() { // library marker kkossev.commonLib, line 1191
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1192
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1193
} // library marker kkossev.commonLib, line 1194

// the administrative / diagnostic commands drop-down list. Deliberately NOT named 'configure' - overloading the Configuration capability command made the dispatch depend on whether the platform happens to supply an argument // library marker kkossev.commonLib, line 1196
public void deviceUtilities(String command = null) { // library marker kkossev.commonLib, line 1197
    logInfo "deviceUtilities(${command})..." // library marker kkossev.commonLib, line 1198
    if (command == null || !(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1199
        configureHelp(command)      // nothing was selected, or the value is not one of ours - show the help and do nothing else // library marker kkossev.commonLib, line 1200
        return // library marker kkossev.commonLib, line 1201
    } // library marker kkossev.commonLib, line 1202
    // // library marker kkossev.commonLib, line 1203
    String func // library marker kkossev.commonLib, line 1204
    try { // library marker kkossev.commonLib, line 1205
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1206
        "$func"() // library marker kkossev.commonLib, line 1207
    } // library marker kkossev.commonLib, line 1208
    catch (e) { // library marker kkossev.commonLib, line 1209
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1210
        return // library marker kkossev.commonLib, line 1211
    } // library marker kkossev.commonLib, line 1212
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1213
} // library marker kkossev.commonLib, line 1214

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1216
void configureHelp(final String val = null) { // library marker kkossev.commonLib, line 1217
    logInfo "select one of the commands from the list: ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1218
    sendInfoEvent('Please select a command from the drop-down list')      // short _status_ event, auto-cleared after INFO_AUTO_CLEAR_PERIOD // library marker kkossev.commonLib, line 1219
} // library marker kkossev.commonLib, line 1220

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1222
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1223
    deleteAllSettings() // library marker kkossev.commonLib, line 1224
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1225
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1226
    deleteAllStates() // library marker kkossev.commonLib, line 1227
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1228

    initialize() // library marker kkossev.commonLib, line 1230
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1231
    updated() // library marker kkossev.commonLib, line 1232
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1233
} // library marker kkossev.commonLib, line 1234

private void configureNow() { // library marker kkossev.commonLib, line 1236
    configure() // library marker kkossev.commonLib, line 1237
} // library marker kkossev.commonLib, line 1238

/** // library marker kkossev.commonLib, line 1240
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1241
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1242
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1243
 */ // library marker kkossev.commonLib, line 1244
void configure() { // library marker kkossev.commonLib, line 1245
    List<String> cmds = [] // library marker kkossev.commonLib, line 1246
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1247
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1248
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1249
    if (isTuya()) { // library marker kkossev.commonLib, line 1250
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1251
    } // library marker kkossev.commonLib, line 1252
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1253
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1254
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1255
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1256
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1257
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1258
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1259
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1260
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1261
    } // library marker kkossev.commonLib, line 1262
    else { // library marker kkossev.commonLib, line 1263
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
} // library marker kkossev.commonLib, line 1266

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1268
void installed() { // library marker kkossev.commonLib, line 1269
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1270
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1271
    // populate some default values for attributes // library marker kkossev.commonLib, line 1272
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1273
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1274
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1275
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1276
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

private void queryPowerSource() { // library marker kkossev.commonLib, line 1280
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1284
private void initialize() { // library marker kkossev.commonLib, line 1285
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1286
    logDebug "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1287
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1288
        logDebug "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1289
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1290
    } // library marker kkossev.commonLib, line 1291
    if (this.respondsTo('customInitialize')) { customInitialize() }  // library marker kkossev.commonLib, line 1292
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1293
    updateTuyaVersion() // library marker kkossev.commonLib, line 1294
    updateAqaraVersion() // library marker kkossev.commonLib, line 1295
} // library marker kkossev.commonLib, line 1296

/* // library marker kkossev.commonLib, line 1298
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1299
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1300
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1301
*/ // library marker kkossev.commonLib, line 1302

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1304
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1305
} // library marker kkossev.commonLib, line 1306

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1308
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1309
} // library marker kkossev.commonLib, line 1310

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1312
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1313
} // library marker kkossev.commonLib, line 1314

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1316
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1317
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1318
        return // library marker kkossev.commonLib, line 1319
    } // library marker kkossev.commonLib, line 1320
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1321
    cmd.each { // library marker kkossev.commonLib, line 1322
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1323
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1324
            return // library marker kkossev.commonLib, line 1325
        } // library marker kkossev.commonLib, line 1326
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1327
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1328
    } // library marker kkossev.commonLib, line 1329
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1330
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1331
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1332
} // library marker kkossev.commonLib, line 1333

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1335

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1337
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1338
} // library marker kkossev.commonLib, line 1339

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1341
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1342
} // library marker kkossev.commonLib, line 1343

//@CompileStatic // library marker kkossev.commonLib, line 1345
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1346
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1347
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1348
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1349
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1350
        initializeVars(false) // library marker kkossev.commonLib, line 1351
        updateTuyaVersion() // library marker kkossev.commonLib, line 1352
        updateAqaraVersion() // library marker kkossev.commonLib, line 1353
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1354
    } // library marker kkossev.commonLib, line 1355
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1356
} // library marker kkossev.commonLib, line 1357

// credits @thebearmay // library marker kkossev.commonLib, line 1359
String getModel() { // library marker kkossev.commonLib, line 1360
    try { // library marker kkossev.commonLib, line 1361
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1362
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1363
    } catch (ignore) { // library marker kkossev.commonLib, line 1364
        try { // library marker kkossev.commonLib, line 1365
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1366
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1367
                return model // library marker kkossev.commonLib, line 1368
            } // library marker kkossev.commonLib, line 1369
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1370
            return '' // library marker kkossev.commonLib, line 1371
        } // library marker kkossev.commonLib, line 1372
    } // library marker kkossev.commonLib, line 1373
} // library marker kkossev.commonLib, line 1374

// credits @thebearmay // library marker kkossev.commonLib, line 1376
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1377
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1378
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1379
    String revision = tokens.last() // library marker kkossev.commonLib, line 1380
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1381
} // library marker kkossev.commonLib, line 1382

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1384
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1385
    unschedule() // library marker kkossev.commonLib, line 1386
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1387
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1388

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1390
} // library marker kkossev.commonLib, line 1391

void resetStatistics() { // library marker kkossev.commonLib, line 1393
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1394
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1395
} // library marker kkossev.commonLib, line 1396

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1398
void resetStats() { // library marker kkossev.commonLib, line 1399
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1400
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1401
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1402
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1403
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1404
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1405
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1406
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1407
} // library marker kkossev.commonLib, line 1408

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1410
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1411
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1412
        state.clear() // library marker kkossev.commonLib, line 1413
        unschedule() // library marker kkossev.commonLib, line 1414
        resetStats() // library marker kkossev.commonLib, line 1415
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1416
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1417
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1418
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1419
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1420
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1421
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1422
    } // library marker kkossev.commonLib, line 1423

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1425
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1426
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1427
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1428
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1429

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1431
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1432
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1433
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1434
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1435
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1436
    if (fullInit || settings?.ignoreDuplicatedZigbeeMessages == null) { device.updateSetting('ignoreDuplicatedZigbeeMessages', false) } // library marker kkossev.commonLib, line 1437
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1438

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1440

    // common libraries initialization // library marker kkossev.commonLib, line 1442
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1443
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1444
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1445
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1446
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1447
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1448
    // // library marker kkossev.commonLib, line 1449
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1450
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1451
    // // library marker kkossev.commonLib, line 1452
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1453
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1454
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1455
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1456

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1458
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1459
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1460
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1461
    if ( ep  != null) { // library marker kkossev.commonLib, line 1462
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1463
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1464
    } // library marker kkossev.commonLib, line 1465
    else { // library marker kkossev.commonLib, line 1466
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1467
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1468
    } // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

// not used!? // library marker kkossev.commonLib, line 1472
void setDestinationEP() { // library marker kkossev.commonLib, line 1473
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1474
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1475
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1476
} // library marker kkossev.commonLib, line 1477

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1479
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1480
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1481
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1482
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1483

// _DEBUG mode only // library marker kkossev.commonLib, line 1485
void getAllProperties() { // library marker kkossev.commonLib, line 1486
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1487
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

// delete all Preferences // library marker kkossev.commonLib, line 1491
void deleteAllSettings() { // library marker kkossev.commonLib, line 1492
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1493
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1494
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1495
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1496
} // library marker kkossev.commonLib, line 1497

// delete all attributes // library marker kkossev.commonLib, line 1499
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1500
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1501
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1502
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

// delete all State Variables // library marker kkossev.commonLib, line 1506
void deleteAllStates() { // library marker kkossev.commonLib, line 1507
    String stateDeleted = '' // library marker kkossev.commonLib, line 1508
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1509
    state.clear() // library marker kkossev.commonLib, line 1510
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1514
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1515
} // library marker kkossev.commonLib, line 1516

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1518
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1519
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1520
} // library marker kkossev.commonLib, line 1521

void testParse(String par) { // library marker kkossev.commonLib, line 1523
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1524
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1525
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1526
    parse(par) // library marker kkossev.commonLib, line 1527
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1528
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

Object testJob() { // library marker kkossev.commonLib, line 1532
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

/** // library marker kkossev.commonLib, line 1536
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1537
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1538
 */ // library marker kkossev.commonLib, line 1539
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1540
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1541
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1542
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1543
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1544
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1545
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1546
    String cron // library marker kkossev.commonLib, line 1547
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1548
    else { // library marker kkossev.commonLib, line 1549
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1550
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1551
    } // library marker kkossev.commonLib, line 1552
    return cron // library marker kkossev.commonLib, line 1553
} // library marker kkossev.commonLib, line 1554

// credits @thebearmay // library marker kkossev.commonLib, line 1556
String formatUptime() { // library marker kkossev.commonLib, line 1557
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1558
} // library marker kkossev.commonLib, line 1559

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1561
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1562
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1563
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1564
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1565
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1566
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1567
} // library marker kkossev.commonLib, line 1568

boolean isTuya() { // library marker kkossev.commonLib, line 1570
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1571
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1572
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1573
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1574
    return ((model?.startsWith('TS') && manufacturer?.startsWith('_T')) || model == 'HOBEIAN') ? true : false // library marker kkossev.commonLib, line 1575
} // library marker kkossev.commonLib, line 1576

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1578
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1579
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1580
    if (application != null) { // library marker kkossev.commonLib, line 1581
        Integer ver // library marker kkossev.commonLib, line 1582
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1583
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1584
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1585
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1586
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1587
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1588
        } // library marker kkossev.commonLib, line 1589
    } // library marker kkossev.commonLib, line 1590
} // library marker kkossev.commonLib, line 1591

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1593

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1595
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1596
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1597
    if (application != null) { // library marker kkossev.commonLib, line 1598
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1599
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1600
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1601
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1602
        } // library marker kkossev.commonLib, line 1603
    } // library marker kkossev.commonLib, line 1604
} // library marker kkossev.commonLib, line 1605

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1607
    try { // library marker kkossev.commonLib, line 1608
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1609
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1610
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1611
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1612
    } catch (e) { // library marker kkossev.commonLib, line 1613
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1614
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1615
    } // library marker kkossev.commonLib, line 1616
} // library marker kkossev.commonLib, line 1617

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1619
    try { // library marker kkossev.commonLib, line 1620
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1621
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1622
        return date.getTime() // library marker kkossev.commonLib, line 1623
    } catch (e) { // library marker kkossev.commonLib, line 1624
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1625
        return now() // library marker kkossev.commonLib, line 1626
    } // library marker kkossev.commonLib, line 1627
} // library marker kkossev.commonLib, line 1628

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1630
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1631
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1632
    int seconds = time % 60 // library marker kkossev.commonLib, line 1633
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1634
} // library marker kkossev.commonLib, line 1635

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
