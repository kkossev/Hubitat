/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, LineLength, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Zigbee Smoke Detector - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.3.0  2024-06-22 kkossev  - (dev. branch) new driver for Aqara Smoke Detector
 * ver. 3.3.1  2024-06-23 kkossev  - (dev. branch) xiaomi tags debug decoding; added alarmSelfTest command; smoke state is derived from 0xFCC0:0x013A,
 *
 *                                   TODO: refresh the smoke custom cluster state and publish the first alpha version!
 *                                   TODO: mute() command (mute the buzzer)
 *                                   TODO: buzz() command (alarm the buzzer)
 *                                   TODO: setAlarm() command
 *                                   TODO: setClear() command
 *                                   TODO: handle the battery reporting (convert to percentage)
 */

static String version() { '3.3.1' }
static String timeStamp() { '2024/06/23 7:35 PM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.iasLib
#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib

deviceType = 'Alarm'
@Field static final String DEVICE_TYPE = 'Alarm'

metadata {
    definition(
        name: 'Zigbee Smoke Detector',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Smoke%20Detector/Zigbee_Smoke_Detector_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'SmokeDetector'

        
        // Aqaura Smoke Detectot attributes
        // smoke - ENUM ["clear", "tested", "detected"]                     // 0xA0 (160) 'Smoke alarm status'
        attribute 'smokeDensity', 'number'                                  // 0xA1 (161) 'Value of smoke concentration'
        attribute 'smokeDensityDbm', 'number'                               // 'Value of smoke concentration in dBm'
        attribute 'alarmSelfTest', 'enum', ['clear', 'selfTest']                 // 0xA2 (162) Starts the self-test process (checking the indicator + light and buzzer work properly)'
        attribute 'test', 'enum', ['false', 'true']                         // 'Self-test in progress'
        attribute 'buzzer', 'enum', ['mute', 'alarm']
                // 'The buzzer can be muted and alarmed manually. During a smoke alarm, the buzzer can be manually muted for 80 seconds ("mute") and unmuted ("alarm").
                // The buzzer cannot be pre-muted, as this function only works during a smoke alarm. During the absence of a smoke alarm, the buzzer can be manually alarmed ("alarm") and disalarmed ("mute"),
                // but for this "linkage_alarm" option must be enabled'
        attribute 'buzzerManualAlarm', 'enum', ['false', 'true']            // 'Buzzer alarmed (manually)'
        attribute 'buzzerManualMute', 'enum', ['false', 'true']             // 0xA3 (163) 'Buzzer muted (manually)'
        attribute 'heartbeatIndicator', 'enum', ['disabled', 'enabled']     // 0xA4 (164) 'When this option is enabled then in the normal monitoring state, the green indicator light flashes every 60 seconds'
        attribute 'linkageAlarm', 'enum', ['disabled', 'enabled']           // 0xA5 (165)
                // 'When this option is enabled and a smoke alarm has occurred, then "linkage_alarm_state"=true,
                // and when the smoke alarm has ended or the buzzer has been manually muted, then "linkage_alarm_state"=false'
        attribute 'linkageAlarmState', 'enum', ['false', 'true']    // ''"linkageAlarm" is triggered'
        // TODO: Xiaomi struct battery, battery_voltage, power_outage_count(false)

        command 'refreshAll'
        command 'alarmSelfTest'
        command 'mute'        // 
        command 'buzz'        // 
        if (_DEBUG) { command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  }

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
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
    }
}

@Field static final Map deviceProfilesV3 = [
    //
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/da65b1aeffd96527df02725b49de61e453fee059/src/devices/lumi.ts#L1708
    'AQARA_SMART_SMOKE_DETECTOR'   : [
            description   : 'Aqara Smart Smoke Detector',   // 'JY-GZ-01AQ',
            device        : [manufacturers: ['LUMI'], type: 'ALARM', powerSource: 'battery', isSleepy:false],
            capabilities  : ['SmokeDetector': true, 'Battery': true],
            preferences   : ['heartbeatIndicator':'0xFCC0:0x013C', 'linkageAlarm':'0xFCC0:0x014B'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0500,0003,0001", outClusters:"0019", model:"lumi.sensor_smoke.acn03", manufacturer:"LUMI", controllerType: "ZGB", deviceJoinName: 'Aqara Smoke Detector']
            ],
            commands      : ['alarmSelfTest':'alarmSelfTest','resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            // must be commands: buzzer
            attributes    : [
                [at:'0x0500:0x0002',  name:'smokeIAS',              type:'enum',    dt:'0x20',                    rw: 'ro', map:[0: 'clear', 1: 'detected'],  description:'Smoke IAS State'],
                [at:'0xFCC0:0x013C',  name:'heartbeatIndicator',    type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', map:[0: 'disabled', 1: 'enabled'],      title: '<b>Heartbeat Indicator</b>',   description:'When this option is enabled then in the normal monitoring state, the green indicator light flashes every 60 seconds'],
                [at:'0xFCC0:0x013E',  name:'buzzer',                type:'enum',    dt:'0x23', mfgCode:'0x115f',  rw: 'rw', map:[0: 'mute', 1: 'alarm'],      title: '<b>Buzzer</b>',   description:'Buzzer'],
                // https://github.com/Koenkk/zigbee-herdsman-converters/blob/da65b1aeffd96527df02725b49de61e453fee059/src/lib/lumi.ts#L4250
                [at:'0xFCC0:0x0126',  name:'buzzerManualMute',      type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', map:[0: 'false', 1: 'true'],      description:'Buzzer muted (manually)'],
                [at:'0xFCC0:0x013D',  name:'buzzerManualAlarm',     type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', map:[0: 'false', 1: 'true'],      description:'Buzzer alarmed (manually)'],
                [at:'0xFCC0:0x0127',  name:'alarmSelfTest',         type:'enum',    dt:'0x10', mfgCode:'0x115f',  rw: 'rw', map:[0: 'clear', 1: 'selfTest'],  description:'Starts the self-test process (checking the indicator + light and buzzer work properly)'],
                [at:'0xFCC0:0x014B',  name:'linkageAlarm',          type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', defVal: 1, map:[0: 'disabled', 1: 'enabled'],      title: '<b>Linkage Alarm</b>',   description:'Linkage Alarm'],
                [at:'0xFCC0:0x0139',  name:'linkageAlarmState',     type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', map:[0: 'false', 1: 'true'],      description:'linkageAlarm is triggered'],
                [at:'0xFCC0:0x013A',  name:'smoke',                 type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', map:[0: 'clear', 1: 'detected'],  description:'Smoke'],
                [at:'0xFCC0:0x013B',  name:'smokeDensity',          type:'number',  dt:'0x23', mfgCode:'0x115f',  rw: 'ro', unit:'-',                         description:'Smoke density'],
                [at:'0xFCC0:0x014C',  name:'smokeX',                type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'ro', map:[0: 'clear', 1: 'detected'],  description:'SmokeX'],
            ],
            refresh: ['refreshAqara'],
            deviceJoinName: 'Aqara Smart Smoke Detector',
            configuration : [:]
    ]
]

void customParseIASCluster(final Map descMap) {
    logDebug "customParseIASCluster: cluster=${descMap} attrInt=${descMap.attrInt} value=${descMap.value}"
    if (descMap.cluster != '0500') { return } // not IAS cluster
    if (descMap.attrInt == null) { return } // missing attribute

    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        standardParseIASCluster(descMap)
    }
 }

// XiaomiFCC0 cluster custom handled
//
void customParseXiaomiFCC0Cluster(final Map descMap) {
    logDebug "customParseXiaomiFCC0Cluster: zigbee received cluster 0xFCC0 attribute 0x${descMap.attrId} (raw value = ${descMap.value})"
    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        customParseXiaomiClusterTags(tags)
        return
    }
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseXiaomiFCC0Cluster: received Xiaomi cluster 0xFCC0 unknown attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes and when the smoke alarm button is pressed
// called from customParseXiaomiFCC0Cluster
//
void customParseXiaomiClusterTags(final Map<Integer, Object> tags) {
    final String funcName = 'customParseXiaomiClusterTags'
    logDebug "${funcName}: tags=${tags}"
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x04:  // unknown
            case 0x0C:  // (12)  unknown
            case 0x66:  // (102) unknown
            case 0x67:  // (103) unknown
            case 0x68:  // (104) unknown
                logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA0:  // (160) smoke
            case 0x13A: // (314)
                logDebug "${funcName} smoke: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA1:  // (161) smokeDensity       //smoke_density_dbm = getFromLookup(value, {0: 0, 1: 0.085, 2: 0.088, 3: 0.093, 4: 0.095, 5: 0.100, 6: 0.105, 7: 0.110, 8: 0.115, 9: 0.120, 10: 0.125});
            case 0x13B: // (315)
                logDebug "${funcName} smokeDensity: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA2:  // (162) self_test
            case 0x127: // (295)
                logDebug "${funcName} selfTest: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA3:  // (163) buzzer_manual_mute
            case 0x126: // (294) 
                logDebug "${funcName} buzzerManualMute: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA4:  // (164) heartbeat_indicator
            case 0x13C: // (316)
                logDebug "${funcName} heartbeatIndicator: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA5:  // (165) linkage_alarm
            case 0x14B: // (331)
                logDebug "${funcName} linkageAlarm: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0xA6:  // (166) unknown
            case 0x14C: // (332) linkage_alarm_state
                logDebug "${funcName} linkageAlarmState: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x13D: // (317) buzzer_manual_alarm
                logDebug "${funcName} buzzerManualAlarm: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x13E: // (318) buzzer
                logDebug "${funcName} buzzer: 0x${intToHexStr(tag, 1)}=${value}"
                break
            default:
                // no Smoke Detector specific tag - call the common parseXiaomiClusterTags method in the xiaomiLib
                parseXiaomiClusterSingeTag(tag, value)
        }
    }
}


/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * called from parseThermostatCluster() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseThermostatCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseThermostatCluster: zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "parseThermostatClusterThermostat: received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

//
// called from updated() in the main code
void customUpdated() {
    //ArrayList<String> cmds = []
    logDebug 'customUpdated: ...'
    //
    if (settings?.forcedProfile != null) {
        //logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false)
            customInitializeVars(fullInit = false)
            resetPreferencesToDefaults(debug = true)
            logInfo 'press F5 to refresh the page'
        }
    }
    else {
        logDebug 'forcedProfile is not set'
    }

    // Itterates through all settings
    logDebug 'updatedThermostat: updateAllPreferences()...'
    updateAllPreferences()
}

//
List<String> refreshAqara() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, [0x013A, 0x013B, 0x013C, 0x013D, 0x0126, 0x014C, 0x014B], [mfgCode: 0x115F], delay = 500)  // 0x14C - smokeX; 0x13B - smokeDensity
    cmds += zigbee.readAttribute(0x0500, 0x0002, [:], delay = 200)
    return cmds
}

// called on refresh() command from the commonLib. Thus supresses calling the standard XXXrefresh() commands from the included libraries!
List<String> customRefresh() {
    List<String> cmds = []
    cmds += refreshAqara()
    cmds += batteryRefresh()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> refreshAll() {
    logDebug 'refreshAll()'
    List<String> cmds = []
    cmds += customRefresh()         // all deviceProfile attributes + battery
    cmds += refreshFromDeviceProfileList()
    // refresh also the relevant IAS attributes
    [0x0000, 0x0001, 0x0002, 0x0010, 0x0011].each { //key, value ->
        cmds += zigbee.readAttribute(0x0500, it as int, [:], delay = 199)
    }
    sendZigbeeCommands(cmds)
}

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}

List<String> initializeAqara() {
    List<String> cmds = []
    logDebug 'configuring Aqara ...'
    cmds =  ["zdo bind 0x${device.deviceNetworkId} 1 1 0x0500 {${device.zigbeeId}} {}", "delay 200" ]
    cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 3600, 0x00, [:], delay=201)
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}", 'delay 202']        // 'delay 251', ]
    cmds += zigbee.configureReporting(0xFCC0, 0x013A, 0x20, 0, 3600, 0x00, [mfgCode:0x115f], delay=203)
    cmds += zigbee.configureReporting(0xFCC0, 0x013B, 0x23, 0, 3600, 0x00, [mfgCode:0x115f], delay=204)
    cmds += zigbee.enrollResponse(203)

    //cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, intMinTime as int, intMaxTime as int, 0x01, [:], delay=541)
    //cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 20, 120, 0x01, [:], delay=542)
    //cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", 'delay 551', ]
    //cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work

    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    cmds = initializeAqara()
    logDebug "customInitializeDevice() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    // init vars
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    sendEvent(name: 'smoke', value: 'unknown', type: 'digital')
}

List<String> customAqaraBlackMagic() {
    List<String> cmds = []
    cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',]
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
    //cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage
    logDebug 'customAqaraBlackMagic()'
    return cmds
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
// (works for BRT-100, Sonoff TRVZV)
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        /*
        case 'temperature' :
            handleTemperatureEvent(valueScaled as Float)
            break
            */
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
                //if (!doNotTrace) {
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            //}
            break
    }
}

void alarmSelfTest(Number par) {
    logDebug "alarmSelfTest(${par})"
    ping()  // make the device awake
    List<String> cmds = []
    cmds += zigbee.writeAttribute(0xFCC0, 0x0127, 0x10, 1, [mfgCode:0x115f], delay=200)
    sendZigbeeCommands(cmds)
}

void mute() {
    logDebug "mute()"
    ping()
    List<String> cmds = []
    cmds += zigbee.writeAttribute(0xFCC0, 0x013E, 0x23, 15360, [mfgCode:0x115f], delay=200)
    sendZigbeeCommands(cmds)
}

void buzz() {
    logDebug "buzz()"
    ping()
    List<String> cmds = []
    cmds += zigbee.writeAttribute(0xFCC0, 0x013E, 0x23, 15361, [mfgCode:0x115f], delay=200)
    sendZigbeeCommands(cmds)
}

void test(String par) {
    List<String> cmds = []
    //cmds += zigbee.configureReporting(0xFCC0, 0x013A, 0x20, 0, 3600, 0x00, [mfgCode:0x115f], delay=203)
    //cmds += zigbee.configureReporting(0xFCC0, 0x013B, 0x23, 0, 3600, 0x00, [mfgCode:0x115f], delay=204)
    cmds += zigbee.configureReporting(0xFCC0, 0x013C, 0x23, 0, 3600, 0x00, [:], delay=204)

    sendZigbeeCommands(cmds)
}

void testT(String par) {
    log.trace "testT(${par}) : DEVICE.preferences = ${DEVICE.preferences}"
    Map result
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            log.trace "testT: ${key} = ${value}"
            result = inputIt(key, debug = true)
            logDebug "inputIt: ${result}"
        }
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////