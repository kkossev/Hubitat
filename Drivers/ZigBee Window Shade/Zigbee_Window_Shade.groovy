/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, LineLength, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Zigbee Shade Controller - Device Driver for Hubitat Elevation
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
 * ver. 3.3.0  2024-06-25 kkossev  - (dev. branch) new driver for Zigbee Shade Controller
 *
 *                                   TODO: new shade / curtain driver
 */

static String version() { '3.3.0' }
static String timeStamp() { '2024/06/25 10:22 PM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

#include kkossev.commonLib
#include kkossev.batteryLib
//#include kkossev.iasLib
//#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib

deviceType = 'Curtain'
@Field static final String DEVICE_TYPE = 'Curtain'

metadata {
    definition(
        name: 'Zigbee Window Shade',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Window%20Shade/Zigbee_Window_Shade_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'WindowShade'    // Attributes: position - NUMBER, unit:% windowShade - ENUM ["opening", "partially open", "closed", "open", "closing", "unknown"]
                                    // Commands: close(); open(); setPosition(position) position required (NUMBER) - Shade position (0 to 100);
                                    //           startPositionChange(direction): direction required (ENUM) - Direction for position change request ["open", "close"]
                                    //            stopPositionChange()

        attribute 'targetPosition', 'number'            // ZemiSmart M1 is updating this attribute, not the position :(
        attribute 'operationalStatus', 'number'         // 'enum', ['unknown', 'open', 'closed', 'opening', 'closing', 'partially open']

        attribute 'positionState', 'enum', ['up/open', 'stop', 'down/close']
        attribute 'upDownConfirm', 'enum', ['false', 'true']
        attribute 'controlBack', 'enum', ['false', 'true']
        attribute 'scheduleTime', 'number'

        

        command 'refreshAll'
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
        section {
            input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
            input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_DEBUG_LOGGING, description: 'Turns on debug logging for 24 hours.'
        }
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
        section {
            //input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
            input name: 'maxTravelTime', type: 'number', title: '<b>Maximum travel time</b>', description: '<i>The maximum time to fully open or close (Seconds)</i>', required: false, defaultValue: MAX_TRAVEL_TIME
            input name: 'deltaPosition', type: 'number', title: '<b>Position delta</b>', description: '<i>The maximum error step reaching the target position</i>', required: false, defaultValue: POSITION_DELTA
            input name: 'substituteOpenClose', type: 'bool', title: '<b>Substitute Open/Close w/ setPosition</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
            input name: 'invertPosition', type: 'bool', title: '<b>Reverse Position Reports</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
            input name: 'targetAsCurrentPosition', type: 'bool', title: '<b>Reverse Target and Current Position</b>', description: '<i>Non-standard Zemismart motors</i>', required: false, defaultValue: false
        }

    }
}


@Field static final String libWindowShadeVersion = '1.0.0'
@Field static final String libWindowShadeStamp   = '2024/03/16 9:29 AM'

private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL() { 0x0000 }
private getCOMMAND_MOVE_LEVEL_ONOFF() { 0x04 }

@Field static final Integer OPEN   = 0      // this is the standard!  Hubitat is inverted?
@Field static final Integer CLOSED = 100    // this is the standard!  Hubitat is inverted?
@Field static final Integer POSITION_DELTA = 5
@Field static final Integer MAX_TRAVEL_TIME = 15

@Field static final String DRIVER = 'Matter Advanced Bridge'
@Field static final String COMPONENT = 'Matter Generic Component Window Shade'
@Field static final String WIKI   = 'Get help on GitHub Wiki page:'
@Field static final String COMM_LINK =   "https://community.hubitat.com/t/project-nearing-beta-release-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009"
@Field static final String GITHUB_LINK = "https://github.com/kkossev/Hubitat/wiki/Matter-Advanced-Bridge-%E2%80%90-Window-Covering"

// credits @jtp10181
String fmtHelpInfo(String str) {
	String info = "${DRIVER} v${parent?.version()}<br> ${COMPONENT} v${matterComponentWindowShadeVersion}"
	String prefLink = "<a href='${GITHUB_LINK}' target='_blank'>${WIKI}<br><div style='font-size: 70%;'>${info}</div></a>"
    String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid green; border-radius: 6px; color: green;'"
    String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}

int getDelta() { return settings?.deltaPosition != null ? settings?.deltaPosition as int : POSITION_DELTA }
//int getFullyOpen()   { return settings?.invertOpenClose ? CLOSED : OPEN }
//int getFullyClosed() { return settings?.invertOpenClose ? OPEN : CLOSED }
//int getFullyOpen()   { return settings?.invertPosition ? CLOSED : OPEN }
//int getFullyClosed() { return settings?.invertPosition ? OPEN : CLOSED }
int getFullyOpen()   { return  OPEN }
int getFullyClosed() { return CLOSED }
boolean isFullyOpen(int position)   { return Math.abs(position - getFullyOpen()) < getDelta() }
boolean isFullyClosed(int position) { return Math.abs(position - getFullyClosed()) < getDelta() }

@Field final int DP_COMMAND_OPEN = 0x00
@Field final int DP_COMMAND_STOP = 0x01
@Field final int DP_COMMAND_CLOSE = 0x02
@Field final int DP_COMMAND_CONTINUE = 0x03
@Field final int DP_COMMAND_LIFTPERCENT = 0x05
@Field final int DP_COMMAND_CUSTOM = 0x06

String getModelX()  { return settings?.forcedTS130F == true ? 'TS130F' : device.getDataValue('model') }
boolean isTS130F() { return getModelX() == 'TS130F' }
boolean isZM85EL() { return device.getDataValue('manufacturer') in ['_TZE200_cf1sl3tj'] }
boolean isAM43()   { return device.getDataValue('manufacturer') in ['_TZE200_zah67ekd'] }
boolean isAM02()   { return device.getDataValue('manufacturer') in ['_TZE200_iossyxra', '_TZE200_cxu0jkjk'] }

// Open - default 0x00
int getDpCommandOpen() {
    String manufacturer = device.getDataValue('manufacturer')
    if (manufacturer in ['_TZE200_rddyvrci', '_TZE200_cowvfni3', '_TYST11_cowvfni3']) {
        return DP_COMMAND_CLOSE //0x02
    }
    return DP_COMMAND_OPEN //0x00
}

// Stop - default 0x01
int getDpCommandStop() {
    String manufacturer = device.getDataValue('manufacturer')
    if (manufacturer in ['_TZE200_nueqqe6k'] || isTS130F()) {
        return DP_COMMAND_CLOSE //0x02
    }
    else if (manufacturer in ['_TZE200_rddyvrci']) {
        return DP_COMMAND_OPEN //0x00
    }
    return DP_COMMAND_STOP //0x01
}

// Close - default 0x02
int getDpCommandClose() {
    String manufacturer = device.getDataValue('manufacturer')
    if (manufacturer in ['_TZE200_nueqqe6k', '_TZE200_rddyvrci'] || isTS130F()) {
        return DP_COMMAND_STOP //0x01
    }
    else if (manufacturer in ['_TZE200_cowvfni3', '_TYST11_cowvfni3']) {
        return DP_COMMAND_OPEN //0x00
    }
    return DP_COMMAND_CLOSE //0x02
}


@Field static final Map deviceProfilesV3 = [
    //
    //
    'TUYA_TS130F_MODULE'   : [
            description   : 'Tuya TS130F Module',   //
            device        : [type: 'COVERING', powerSource: 'ac', isSleepy:false],
            capabilities  : ['Battery': false],
            //preferences   : ['invertPosition':'invertPosition', 'custom1':'0xFCC0:0x014B'],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0004,0005,0006,0102,E001,0000", outClusters:"0019,000A", model:"TS130F", manufacturer:"_TZ3000_e3vhyirx", controllerType: "ZGB"]
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            // must be commands: buzzer
            attributes    : [
                [at:'0x0102:0x0000',  name:'currentLevel',          type:'number',  dt:'0x23', rw: 'ro',            description:'currentLevel (0x0102:0x0000)'],   // uint8
                [at:'0x0102:0x0008',  name:'position',              type:'number',  dt:'0x23', rw: 'rw', unit:'%',  description:'Current Position Lift Percentage'],
                [at:'0x0102:0x8000',  name:'0x0102:0x8000',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'disabled', 1: 'enabled'], title: '<b>0x0102:0x8000</b>',   description:'0x0102:0x8000'], // enum8
                [at:'0x0102:0xF000',  name:'positionState',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'up/open', 1: 'stop', 2: 'down/close' ], title: '<b>Position State</b>',   description:'position state (0x0102:0xF000)'],
                [at:'0x0102:0xF001',  name:'upDownConfirm',         type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'false', 1: 'true'], title: '<b>upDownConfirm</b>',   description:'upDownConfirm (0x0102:0xF001)'],
                [at:'0x0102:0xF002',  name:'controlBack',           type:'enum',    dt:'0x20', rw: 'rw', map:[0: 'false', 1: 'true'], title: '<b>controlBack</b>',   description:'controlBack (0x0102:0xF002)'],
                [at:'0x0102:0xF003',  name:'scheduleTime ',         type:'number',  dt:'0x29', rw: 'rw', title: '<b>ScheduleTime</b>',   description:'ScheduleTime (0x0102:0xF003)'],  // uint16 

                [at:'0xE001:0x0000',  name:'0xE001:0x0000',         type:'number',  dt:'0x23', rw: 'rw', unit:'?', title: '<b>0xE001:0x0000</b>', description:'0xE001:0x0000'],    // array
            ],
            //refresh: ['refreshTS130F'],
            refresh: ['position', 'positionState', 'upDownConfirm', 'controlBack', 'scheduleTime', '0xE001:0x0000'],
            deviceJoinName: 'Tuya TS130F Module',
            configuration : [:]
    ]
]



/*
 * -----------------------------------------------------------------------------
 * WindowCovering cluster 0x0102
 * called from parseWindowCovering() in the main code ...
 * -----------------------------------------------------------------------------
*/
void customParseWindowCoveringCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseWindowCoveringCluster: zigbee received WindowCovering cluster (0x0102) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseWindowCoveringCluster: received unknown WindowCovering cluster (0x0102) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void customParseE0001Cluster(final Map descMap) {
    logDebug "customParseE0001Cluster: ${descMap}"
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return }
    boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseE0001Cluster: received unknown cluster (0xE001) attribute 0x${descMap.attrId} (value ${descMap.value})"
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
    logDebug 'customUpdated: updateAllPreferences()...'
    updateAllPreferences()
}

//
List<String> refreshTS130F() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, [:], delay = 200)
    cmds += zigbee.readAttribute(zigbee.LEVEL_CONTROL_CLUSTER, ATTRIBUTE_CURRENT_LEVEL, [:], delay = 200)
    return cmds
}

// called on refresh() command from the commonLib. Thus supresses calling the standard XXXrefresh() commands from the included libraries!
List<String> customRefresh() {
    List<String> cmds = []
    cmds += refreshTS130F()
    cmds += batteryRefresh()
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> refreshAll() {
    logDebug 'refreshAll()'
    List<String> cmds = []
    cmds += customRefresh()         // all deviceProfile attributes + battery
    cmds += refreshFromDeviceProfileList()
    sendZigbeeCommands(cmds)
}

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}

List<String> initializeTS130F() {
    List<String> cmds = []
    logDebug 'configuring TS130F ...'
    //cmds =  ["zdo bind 0x${device.deviceNetworkId} 1 1 0x0500 {${device.zigbeeId}} {}", "delay 200" ]
    //cmds += zigbee.configureReporting(0x0500, 0x0002, 0x19, 0, 3600, 0x00, [:], delay=201)
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    cmds = initializeTS130F()
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
    //sendEvent(name: 'position', value: 'unknown', type: 'digital')
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
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

//////////////////////////////////// Matter Generic Component Window Shade'//////////////////////////////////////




// parse commands from parent
void parseShade(List<Map> description) {
    if (logEnable) { log.debug "parse: ${description}" }
    description.each { d ->
        if (d?.name == 'position') {
            processCurrentPositionBridgeEvent(d)
        }
        else if (d?.name == 'targetPosition') {
            processTargetPositionBridgeEvent(d)
        }
        else if (d?.name == 'operationalStatus') {
            processOperationalStatusBridgeEvent(d)
        }
        else {
            if (d?.descriptionText && txtEnable) { log.info "${d.descriptionText}" }
            log.trace "parse: ${d}"
            sendEvent(d)
        }
    }
}

int invertPositionIfNeeded(int position) {
    int value =  (settings?.invertPosition ?: false) ? (100 - position) as Integer : position
    if (value < 0)   { value = 0 }
    if (value > 100) { value = 100 }
    return value
}

void processCurrentPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    //stopOperationTimeoutTimer()
    if (settings?.targetAsCurrentPosition == true) {
        map.name = 'targetPosition'
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: targetAsCurrentPosition is true -> <b>processing as targetPosition ${map.value} !</b>" }
        processTargetPosition(map)
    }
    else {
        if (logEnable) { log.debug "${device.displayName} processCurrentPositionBridgeEvent: currentPosition reported is ${map.value}" }
        processCurrentPosition(map)
    }
}

void processCurrentPosition(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    // we may have the currentPosition reported inverted !
    map.value = invertPositionIfNeeded(d.value as int)
    if (logEnable) { log.debug "${device.displayName} processCurrentPosition: ${map.value} (was ${d.value})" }
    map.name = 'position'
    map.unit = '%'
    map.descriptionText = "${device.displayName} position is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    if (txtEnable) { log.info "${map.descriptionText}" }
    sendEvent(map)
    updateWindowShadeStatus(map.value as int, device.currentValue('targetPosition') as int, /*isFinal =*/ true, /*isDigital =*/ false)
}

void updateWindowShadeStatus(Integer currentPositionPar, Integer targetPositionPar, Boolean isFinal, Boolean isDigital) {
    String value = 'unknown'
    String descriptionText = 'unknown'
    String type = isDigital ? 'digital' : 'physical'
    //log.trace "updateWindowShadeStatus: currentPositionPar = ${currentPositionPar}, targetPositionPar = ${targetPositionPar}"
    Integer currentPosition = safeToInt(currentPositionPar)
    Integer targetPosition = safeToInt(targetPositionPar)

    if (isFinal == true) {
        if (isFullyClosed(currentPosition)) {
            value = 'closed'
        }
        else if (isFullyOpen(currentPosition)) {
            value = 'open'
        }
        else {
            value = 'partially open'
        }
    }
    else {
        if (targetPosition < currentPosition) {
            value =  'opening'
        }
        else if (targetPosition > currentPosition) {
            value = 'closing'
        }
        else {
            //value = 'stopping'
            if (isFullyClosed(currentPosition)) {
                value = 'closed'
            }
            else if (isFullyOpen(currentPosition)) {
                value = 'open'
            }            
        }
    }
    descriptionText = "${device.displayName} windowShade is ${value} [${type}]"
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText, type: type)
    if (logEnable) { log.debug "${device.displayName} updateWindowShadeStatus: isFinal: ${isFinal}, substituteOpenClose: ${settings?.substituteOpenClose}, targetPosition: ${targetPosition}, currentPosition: ${currentPosition}, windowShade: ${device.currentValue('windowShade')}" }
    if (txtEnable) { log.info "${descriptionText}" }
}

void sendWindowShadeEvent(String value, String descriptionText) {
    sendEvent(name: 'windowShade', value: value, descriptionText: descriptionText)
    if (txtEnable) { log.info "${device.displayName} windowShade is ${value}" }
}

void processTargetPositionBridgeEvent(final Map d) {
    Map map = new HashMap(d)
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: ${d}" }
    if (settings?.targetAsCurrentPosition) {
        if (logEnable) { log.debug "${device.displayName} processTargetPositionBridgeEvent: targetAsCurrentPosition is true" }
        map.name = 'position'
        processCurrentPosition(map)
        return
    }
    processTargetPosition(map)
}

void processTargetPosition(final Map d) {
    //log.trace "processTargetPosition: value: ${d.value}"
    Map map = new HashMap(d)
    map.value = invertPositionIfNeeded(safeToInt(d.value))
    map.descriptionText = "${device.displayName} targetPosition is ${map.value}%"
    if (map.isRefresh) {
        map.descriptionText += ' [refresh]'
    }
    map.name = 'targetPosition'
    map.unit = '%'
    if (logEnable) { log.debug "${device.displayName} processTargetPosition: ${map.value} (was ${d.value})" }
    if (txtEnable) { log.info "${map.descriptionText}" }
    //
    //stopOperationTimeoutTimer()
    sendEvent(map)
    if (!map.isRefresh) {
        // skip upddating the windowShade status on targetPosition refresh
        updateWindowShadeStatus(device.currentValue('position') as int, map.value as int, /*isFinal =*/ false, /*isDigital =*/ false)
    }
}

void processOperationalStatusBridgeEvent(Map d) {
    stopOperationTimeoutTimer()
    if (logEnable) { log.debug "${device.displayName} processOperationalStatusBridgeEvent: ${d}" }
    if (d.descriptionText && txtEnable) { log.info "${device.displayName} ${d.descriptionText}" }
    sendEvent(d)
}

void sendOpen(Object device) { // 100 %
    logDebug "sendOpen()"
    /*
    if (mode == MODE_TILT || settings?.substituteOpenClose == true) {
        logDebug "sending command open : ${settings?.substituteOpenClose == true ? 'substituted with setPosition(100)' : 'MODE_TILT'} "
        setPosition(100)
    }
    */
   // else {
        int dpCommandOpen = getDpCommandOpen()
        logDebug "sending command open (${dpCommandOpen}), direction = ${settings.direction as Integer}"
        if (isTS130F()) {
            sendZigbeeCommands(zigbee.command(0x0102, dpCommandOpen as int, [:], delay = 200))
        }
        else if (isZM85EL()) {    // situation_set ?
            //sendTuyaCommand(0x0B, DP_TYPE_ENUM, 0x00, 2)
            setPosition(100)
            //sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, 0, 2)
        }
        else {
            sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, dpCommandOpen, 2)
        }
    //}

}

// command to open device
void open() {
    if (txtEnable) { log.info "${device.displayName} opening" }
    sendEvent(name: 'targetPosition', value: OPEN, descriptionText: "targetPosition set to ${OPEN}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        sendOpen(device)
    }
    else {
        setPosition(getFullyOpen())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('opening', "${device.displayName} windowShade is opening")
}

void sendClose(Object device) {     // 0 %
    logDebug "sendClose()"
    /*
    if (mode == MODE_TILT || settings?.substituteOpenClose == true) {
        logDebug "sending command close : ${settings?.substituteOpenClose == true ? 'substituted with setPosition(0)' : 'MODE_TILT'} "
        setPosition(0)
    }
    */
    //else {
        int dpCommandClose = getDpCommandClose()
        logDebug "sending command close (${dpCommandClose}), direction = ${settings.direction as Integer}"
        if (isTS130F()) {
            sendZigbeeCommands(zigbee.command(0x0102, dpCommandClose as int, [:], delay = 200))
        }
        else if (isZM85EL()) {    // situation_set ?
            //sendTuyaCommand(0x0B, DP_TYPE_ENUM, 0x00, 2)
            setPosition(0)
            //sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, 0, 2)
        }
        else {
            sendTuyaCommand(DP_ID_COMMAND, DP_TYPE_ENUM, dpCommandClose, 2)
        }
    //}

}

// command to close device
void close() {
    if (logEnable) { log.debug "${device.displayName} closing [digital]" }
    sendEvent(name: 'targetPosition', value: CLOSED, descriptionText: "targetPosition set to ${CLOSED}", type: 'digital')
    if (settings?.substituteOpenClose == false) {
        if (logEnable) { log.debug "${device.displayName} sending sendClose() command" }
        sendClose(device)
    }
    else {
        if (logEnable) { log.debug "${device.displayName} sending sendSetPosition(${getFullyClosed()}) command" }
        setPosition(getFullyClosed())
    }
    startOperationTimeoutTimer()
    sendWindowShadeEvent('closing', "${device.displayName} windowShade is closing [digital]")
}

void sendSetPosition(Object device, final BigDecimal positionParam) {
    int position = positionParam as Integer
    if (position == null || position < 0 || position > 100) {
        throw new Exception("Invalid position ${position}. Position must be between 0 and 100 inclusive.")
    }
    logDebug("setPosition: target is ${position}, currentPosition=${device.currentValue('position')}")
    if (settings?.invertPosition == true) {
        position = 100 - position
    }
    if (isTS130F()) {
        sendZigbeeCommands(zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(position, 2)))
    }
    else {
        sendTuyaCommand(DP_ID_TARGET_POSITION, DP_TYPE_VALUE, position.intValue(), 8)
    }
}

// Component command to set position of device
void setPosition(BigDecimal targetPosition) {
    if (txtEnable) { log.info "${device.displayName} setting target position ${targetPosition}% (current position is ${device.currentValue('position')})" }
    sendEvent(name: 'targetPosition', value: targetPosition as Integer, descriptionText: "targetPosition set to ${targetPosition}", type: 'digital')
    updateWindowShadeStatus(device?.currentValue('position') as Integer, targetPosition as Integer, isFinal = false, isDigital = true)
    BigDecimal componentTargetPosition = invertPositionIfNeeded(targetPosition as Integer)
    if (logEnable) { log.debug "inverted componentTargetPosition: ${componentTargetPosition}" }
    sendSetPosition(device, componentTargetPosition)
    startOperationTimeoutTimer()
}

// Component command to start position change of device
void startPositionChange(String change) {
    if (logEnable) { log.debug "${device.displayName} startPositionChange ${change}" }
    if (change == 'open') {
        open()
    }
    else {
        close()
    }
}

// Component command to start position change of device
void stopPositionChange() {
    if (logEnable) { log.debug "${device.displayName} stopPositionChange" }
    parent?.componentStopPositionChange(device)
}


// Component command to refresh the device
// TODO !
void refreshMatter() {
    if (txtEnable) { log.info "${device.displayName} refreshing ..." }
    state.standardOpenClose = 'OPEN = 0% CLOSED = 100%'
    state.driverVersion = matterComponentWindowShadeVersion + ' (' + matterComponentWindowShadeStamp + ')'
    parent?.componentRefresh(device)
}


// Called when the settings are updated
// TODO !
void updatedMatter() {
    if (txtEnable) { log.info "${device.displayName} driver configuration updated" }
    if (logEnable) {
        log.debug settings
        runIn(86400, 'logsOff')
    }
    if ((state.substituteOpenClose ?: false) != settings?.substituteOpenClose) {
        state.substituteOpenClose = settings?.substituteOpenClose
        if (logEnable) { log.debug "${device.displayName} substituteOpenClose: ${settings?.substituteOpenClose}" }
        /*
        String currentOpenClose = device.currentWindowShade
        String newOpenClose = currentOpenClose == 'open' ? 'closed' : currentOpenClose == 'closed' ? 'open' : currentOpenClose
        if (currentOpenClose != newOpenClose) {
            sendEvent([name:'windowShade', value: newOpenClose, type: 'digital', descriptionText: "windowShade state inverted to ${newOpenClose}", isStateChange:true])
        }
        */
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertMotion: no change" }
    }
    //
    if ((state.invertPosition ?: false) != settings?.invertPosition) {
        state.invertPosition = settings?.invertPosition
        if (logEnable) { log.debug "${device.displayName} invertPosition: ${settings?.invertPosition}" }
    }
    else {
        if (logEnable) { log.debug "${device.displayName} invertPosition: no change" }
    }
}

BigDecimal scale(int value, int fromLow, int fromHigh, int toLow, int toHigh) {
    return  BigDecimal.valueOf(toHigh - toLow) *  BigDecimal.valueOf(value - fromLow) /  BigDecimal.valueOf(fromHigh - fromLow) + toLow
}

void startOperationTimeoutTimer() {
    int travelTime = Math.abs(device.currentValue('position') ?: 0 - device.currentValue('targetPosition') ?: 0)
    Integer scaledTimerValue = scale(travelTime, 0, 100, 1, (settings?.maxTravelTime as Integer) ?: 0) + 1.5
    if (logEnable) { log.debug "${device.displayName} startOperationTimeoutTimer: ${scaledTimerValue} seconds" }
    runIn(scaledTimerValue, 'operationTimeoutTimer', [overwrite: true])
}

void stopOperationTimeoutTimer() {
    if (logEnable) { log.debug "${device.displayName} stopOperationTimeoutTimer" }
    unschedule('operationTimeoutTimer')
}

void operationTimeoutTimer() {
    if (logEnable) { log.warn "${device.displayName} operationTimeout!" }
    updateWindowShadeStatus(device.currentValue('position') as Integer, device.currentValue('targetPosition') as Integer, /*isFinal =*/ true, /*isDigital =*/ true)
}



void parseTest(description) {
    log.warn "parseTest: ${description}"
    //String str = "name:position, value:0, descriptionText:Bridge#4266 Device#32 (tuya CURTAIN) position is is reported as 0 (to be re-processed in the child driver!) [refresh], unit:null, type:physical, isStateChange:true, isRefresh:true"
    String str = description
    // Split the string into key-value pairs
    List<String> pairs = str.split(', ')
    Map map = [:]
    pairs.each { pair ->
        // Split each pair into a key and a value
        List<String> keyValue = pair.split(':')
        String key = keyValue[0]
        String value = keyValue[1..-1].join(':') // Join the rest of the elements in case the value contains colons
        // Try to convert the value to a boolean or integer if possible
        if (value == 'true' || value == 'false' || value == true || value == false) {
            value = Boolean.parseBoolean(value)
        } else if (value.isInteger()) {
            value = Integer.parseInt(value)
        } else if (value == 'null') {
            value = null
        }
        // Add the key-value pair to the map
        map[key] = value
    }
    log.debug map
    parse([map])
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
