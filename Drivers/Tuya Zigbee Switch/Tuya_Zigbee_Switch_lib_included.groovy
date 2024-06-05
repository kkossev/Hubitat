/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, NglParseError, ParameterCount, UnnecessaryGetter, UnusedImport */
/**
 *  Tuya Zigbee Switch - Device Driver for Hubitat Elevation
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
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 2.0.4  2023-06-29 kkossev  - Tuya Zigbee Switch;
 * ver. 2.1.2  2023-07-23 kkossev  - Switch library;
 * ver. 2.1.3  2023-08-12 kkossev  - ping() improvements; added ping OK, Fail, Min, Max, rolling average counters; added clearStatistics(); added updateTuyaVersion() updateAqaraVersion(); added HE hub model and platform version;
 * ver. 3.0.0  2023-11-24 kkossev  - use commonLib; added AlwaysOn option; added ignore duplcated on/off events option;
 * ver. 3.0.1  2023-11-25 kkossev  - added LEDVANCE Plug 03; added TS0101 _TZ3000_pnzfdr9y SilverCrest Outdoor Plug Model HG06619 manufactured by Lidl; added configuration for 0x0006 cluster reproting for all devices;
 * ver. 3.0.2  2023-12-12 kkossev  - added ZBMINIL2
 * ver. 3.0.3  2024-02-24 kkossev  - commonLib 3.0.3 allignment
 * ver. 3.0.7  2024-04-18 kkossev  - commonLib 3.0.7 and groupsLib allignment
 * ver. 3.1.1  2024-05-15 kkossev  - added SONOFF ZBMicro; commonLib 3.1.1 allignment; Groovy linting;
 * ver. 3.2.1  2024-06-04 kkossev  - commonLib 3.2.1 allignment; ZBMicro - do a refresh() after saving the preferences;
 *
 *                                   TODO: Sonof ZBMINIL2 :zigbee read BASIC_CLUSTER attribute 0x0001 error: Unsupported Attribute
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { '3.2.1' }
static String timeStamp() { '2024/06/05 2:46 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = 'Switch'
@Field static final String DEVICE_TYPE = 'Switch'







metadata {
    definition(
        name: 'Tuya Zigbee Switch',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Switch/Tuya_Zigbee_Switch_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        // all the capabilities are already declared in the libraries
        attribute 'powerOnBehavior', 'enum', ['Turn power Off', 'Turn power On', 'Restore previous state']
        attribute 'turboMode', 'enum', ['Disabled', 'Enabled']

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                if (profileMap.device?.isDepricated != true) {
                    profileMap.fingerprints.each {
                        fingerprint it
                    }
                }
            }
        }
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.'
    }
}

boolean isZBMINIL2()   { return (device?.getDataValue('model') ?: 'n/a') in ['ZBMINIL2'] }

@Field static final Map deviceProfilesV3 = [
    'SWITCH_GENERIC_ZCL' : [
            description   : 'Generic Zigbee Switch (ZCL)',
            models        : ['GenericModel'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : ['powerOnBehavior':'0x0006:0x4003'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0005,0006', outClusters:'000A,0019', model:'GenericModel', manufacturer:'GenericManufacturer', deviceJoinName: 'Generic Zigbee Switch (ZCL)']
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            //tuyaDPs       : [:],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', /*enum8*/ type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior'],
            //configuration : ["0x0406":"bind"]     // TODO !!
            configuration : [:],
            deviceJoinName: 'Generic Zigbee Switch (ZCL)'
    ],

    'SWITCH_GENERIC_TUYA' : [
            description   : 'Generic Tuya Switch (0xEF00)',
            models        : ['GenericModel'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : ['powerOnBehavior':'0x0006:0x4003'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0005,0006,EF00', outClusters:'000A,0019', model:'Tuya', manufacturer:'Tuya', deviceJoinName: 'Generic Tuya Switch (0xEF00)']
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:        [
                [dp:1,   name:'switch',                                type:'enum',    rw: 'ro', min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'off', 1:'on'] ,   unit:'',  description:'switch']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', /*enum8*/ type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior'],
            //configuration : ["0x0406":"bind"]     // TODO !!
            configuration : [:],
            deviceJoinName: 'Generic Tuya Switch (0xEF00)'
    ],

    'SWITCH_SONOFF_ZBMICRO' : [
            description   : 'Sonoff ZBMicro Zigbee USB Switch',
            models        : ['ZBMicro'],
            device        : [type: 'switch', powerSource: 'dc', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003', turboMode:'0xFC11:0x0012'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0006,FC57,FC11', outClusters:'0003,0019', model:'ZBMicro', manufacturer:'SONOFF', deviceJoinName: 'Sonoff ZBMicro Zigbee USB Switch']
            ],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', /*enum8*/ type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior'],
                [at:'0xFC11:0x0012', name:'turboMode', dt:'0x29', mfgCode:'0x1286',      /*int16*/ type:'enum',   rw: 'rw', min:9,   max:20,     defVal:'9',   scale:1,    map:[9:'Disabled', 20:'Enabled'], title:'<b>Zigbee Radio Power Turbo Mode.</b>', description:'Enable/disable Zigbee radio power Turbo mode.']
            ],
            refresh       : [ 'powerOnBehavior', 'turboMode'],
            //configuration : ["0x0406":"bind"]     // TODO !!
            configuration : [:],
            deviceJoinName: 'Sonoff ZBMicro Zigbee USB Switch'
    ],

    'SWITCH_SONOFF_ZBMINIL2' : [
            description   : 'Sonoff ZBMini L2 Switch',
            models        : ['ZBMicro'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003'],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0007,0B05,FC57', outClusters:'0019', model:'ZBMINIL2', manufacturer:'SONOFF', deviceJoinName: 'SONOFF ZBMINIL2']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior', 'turboMode'],
            configuration : [:],
            deviceJoinName: 'Sonoff ZBMini L2 Switch'
    ],

    'SWITCH_IKEA_TRADFRI' : [
            description   : 'Ikea Tradfri control outlet',
            models        : ['ZBMicro'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003'],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0008,1000,FC7C', outClusters:'0005,0019,0020,1000', model:'TRADFRI control outlet', manufacturer:'IKEA of Sweden', deviceJoinName: 'TRADFRI control outlet'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0008,1000,FC7C', outClusters:'0019,0020,1000', model:'TRADFRI control outlet', manufacturer:'IKEA of Sweden', deviceJoinName: 'TRADFRI control outlet']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior'],
            configuration : [:],
            deviceJoinName: 'Ikea Tradfri control outlet'
    ],

    'PLUG_LIDL' : [
            description   : 'Lidl outlet',
            models        : ['TS0101'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [:],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006', outClusters:'0019,000A', model:'TS0101', manufacturer:'_TZ3000_pnzfdr9y', deviceJoinName: 'Lidl outlet']
            ],
            attributes    : [:],
            refresh       : [ ],
            configuration : [:],
            deviceJoinName: 'Lidl outlet'
    ],

    'PLUG_LEDVANCE' : [
            description   : 'LEDVANCE outlet',
            models        : ['Plug Z3'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [:],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0B05,FC0F', outClusters:'0019', model:'Plug Z3', manufacturer:'LEDVANCE', deviceJoinName: 'Plug Z3']
            ],
            attributes    : [:],
            refresh       : [ ],
            configuration : [:],
            deviceJoinName: 'LEDVANCE outlet'
    ],

]


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
    // Itterates through all settings
    cmds += updateAllPreferences()  // defined in deviceProfileLib
    sendZigbeeCommands(cmds)
    runIn(2, 'refresh')
}

List<String> customRefresh() {
    List<String> cmds = []
    cmds += onOffRefresh()          // in onOffLib
    //cmds += groupsRefresh()         // in groupsLib
    List<String> devProfCmds = refreshFromDeviceProfileList()
    if (devProfCmds != null && !devProfCmds.isEmpty()) {
        cmds += devProfCmds
    }

    logDebug "customRefresh() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    if (fullInit || settings?.turboMode == null) { device.updateSetting('turboMode', [value: '9', type: 'enum']) }
}

/* groovylint-disable-next-line EmptyMethod, UnusedMethodParameter */
void customInitEvents(boolean fullInit=false) {
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (isZBMINIL2()) {
        logDebug 'customConfigureDevice() : unbind ZBMINIL2 poll control cluster'
        // Unbind genPollCtrl (0x0020) to prevent device from sending checkin message.
        // Zigbee-herdsmans responds to the checkin message which causes the device to poll slower.
        // https://github.com/Koenkk/zigbee2mqtt/issues/11676
        // https://github.com/Koenkk/zigbee2mqtt/issues/10282
        // https://github.com/zigpy/zha-device-handlers/issues/1519
        cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",]
    }
/*
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", "delay 251", ]

    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)
*/
    cmds += configureReporting('Write', ONOFF,  '1', '65534', '0', sendNow = false)    // switch state should be always reported
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

void customParseOnOffCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseOnOffCluster: zigbee received cluster 0x0006 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logTrace "customParseOnOffCluster: unprocessed 0x0006 attribute 0x${descMap.attrId} (value ${descMap.value})"
        standardParseOnOffCluster(descMap)
    }
}

void customParseElectricalMeasureCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseMeteringCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseFC11Cluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "customParseFC11Cluster: zigbee received cluster 0xFC11 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logWarn "customParseFC11Cluster: received unknown 0xFC11 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.2.0' // library marker kkossev.commonLib, line 5
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
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 24
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 25
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 26
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 27
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 28
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 29
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 30
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 31
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 32
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 33
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 34
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 35
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-06-05 kkossev  - 4 in 1 V3 compatibility; added IAS cluster; setDeviceNameAndProfile() fix; // library marker kkossev.commonLib, line 37
  * // library marker kkossev.commonLib, line 38
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 43
  * // library marker kkossev.commonLib, line 44
*/ // library marker kkossev.commonLib, line 45

String commonLibVersion() { '3.2.1' } // library marker kkossev.commonLib, line 47
String commonLibStamp() { '2024/06/05 11:02 AM' } // library marker kkossev.commonLib, line 48

import groovy.transform.Field // library marker kkossev.commonLib, line 50
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 51
import hubitat.device.Protocol // library marker kkossev.commonLib, line 52
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 55
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 56
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 57
import java.math.BigDecimal // library marker kkossev.commonLib, line 58

metadata { // library marker kkossev.commonLib, line 60
        if (_DEBUG) { // library marker kkossev.commonLib, line 61
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 62
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 64
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 65
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 67
            ] // library marker kkossev.commonLib, line 68
        } // library marker kkossev.commonLib, line 69

        // common capabilities for all device types // library marker kkossev.commonLib, line 71
        capability 'Configuration' // library marker kkossev.commonLib, line 72
        capability 'Refresh' // library marker kkossev.commonLib, line 73
        capability 'Health Check' // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 78
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 82

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 84
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 85

    preferences { // library marker kkossev.commonLib, line 87
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 88
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 89
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 90

        if (device) { // library marker kkossev.commonLib, line 92
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 93
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 94
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 95
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 96
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 97
            } // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
    } // library marker kkossev.commonLib, line 100
} // library marker kkossev.commonLib, line 101

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 103
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 104
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 105
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 106
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 107
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 108
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 110
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 111
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 112
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 113

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 115
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 116
] // library marker kkossev.commonLib, line 117
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 118
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 119
] // library marker kkossev.commonLib, line 120

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 122
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 123
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 124
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 125
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 126
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 127
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 128
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 129
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 130
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 131
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 135
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 136
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 137
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 138
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 139
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 140

/** // library marker kkossev.commonLib, line 142
 * Parse Zigbee message // library marker kkossev.commonLib, line 143
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 144
 */ // library marker kkossev.commonLib, line 145
void parse(final String description) { // library marker kkossev.commonLib, line 146
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 147
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 148
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 149
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 150

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 152
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 153
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 154
            parseIasMessage(description) // library marker kkossev.commonLib, line 155
        } // library marker kkossev.commonLib, line 156
        else { // library marker kkossev.commonLib, line 157
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 158
        } // library marker kkossev.commonLib, line 159
        return // library marker kkossev.commonLib, line 160
    } // library marker kkossev.commonLib, line 161
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 162
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 163
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 164
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 165
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 166
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 167
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 168
        return // library marker kkossev.commonLib, line 169
    } // library marker kkossev.commonLib, line 170

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 175

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 177
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 178

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 180
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 184
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    // // library marker kkossev.commonLib, line 188
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 189
    // // library marker kkossev.commonLib, line 190
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 191
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 192
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 193
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 195
            } // library marker kkossev.commonLib, line 196
            break // library marker kkossev.commonLib, line 197
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 198
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 199
                standardParseColorControlCluster(descMap, description)  // library marker kkossev.commonLib, line 200
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 201
            } // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        default: // library marker kkossev.commonLib, line 204
            if (settings.logEnable) { // library marker kkossev.commonLib, line 205
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 206
            } // library marker kkossev.commonLib, line 207
            break // library marker kkossev.commonLib, line 208
    } // library marker kkossev.commonLib, line 209
} // library marker kkossev.commonLib, line 210

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 212
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 213
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   /*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 214
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'ElectricalMeasure', // library marker kkossev.commonLib, line 215
    0x0B04: 'Metering',             0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',             0xFC11: 'FC11',         0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 216
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 217
] // library marker kkossev.commonLib, line 218

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 220
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 221
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 222
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 223
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 224
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 225
        return false // library marker kkossev.commonLib, line 226
    } // library marker kkossev.commonLib, line 227
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 228
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 229
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 230
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 231
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 232
        return true // library marker kkossev.commonLib, line 233
    } // library marker kkossev.commonLib, line 234
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 235
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 236
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 237
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 238
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 239
        return true // library marker kkossev.commonLib, line 240
    } // library marker kkossev.commonLib, line 241
    if (device?.getDataValue('model') != 'ZigUSB' && description.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 242
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 243
    } // library marker kkossev.commonLib, line 244
    return false // library marker kkossev.commonLib, line 245
} // library marker kkossev.commonLib, line 246

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 248
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 249
} // library marker kkossev.commonLib, line 250

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 252
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 253
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 254
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    return false // library marker kkossev.commonLib, line 257
} // library marker kkossev.commonLib, line 258

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 260
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 261
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 262
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 263
    } // library marker kkossev.commonLib, line 264
    return false // library marker kkossev.commonLib, line 265
} // library marker kkossev.commonLib, line 266

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 268
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 269
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 270
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 271
    } // library marker kkossev.commonLib, line 272
    return false // library marker kkossev.commonLib, line 273
} // library marker kkossev.commonLib, line 274

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 276
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 277
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 278
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 279
] // library marker kkossev.commonLib, line 280

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 282
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 283
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
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 314
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 315
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 319
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 322
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 323
            if (settings?.logEnable) { log.debug "${clusterInfo}" } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        default : // library marker kkossev.commonLib, line 326
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
    } // library marker kkossev.commonLib, line 329
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 330
} // library marker kkossev.commonLib, line 331

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 333
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 334
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 335
    switch (commandId) { // library marker kkossev.commonLib, line 336
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 337
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 338
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 339
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 340
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 341
        default: // library marker kkossev.commonLib, line 342
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 343
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 344
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 345
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 346
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 347
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 348
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 349
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 350
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 351
            } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
    } // library marker kkossev.commonLib, line 354
} // library marker kkossev.commonLib, line 355

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 357
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 358
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 359
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 360
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 361
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 362
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 363
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 364
    } // library marker kkossev.commonLib, line 365
    else { // library marker kkossev.commonLib, line 366
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 367
    } // library marker kkossev.commonLib, line 368
} // library marker kkossev.commonLib, line 369

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 371
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 372
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 373
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 374
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 375
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 376
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 377
    } // library marker kkossev.commonLib, line 378
    else { // library marker kkossev.commonLib, line 379
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
} // library marker kkossev.commonLib, line 382

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 384
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 385
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 386
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 387
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 388
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 389
        state.reportingEnabled = true // library marker kkossev.commonLib, line 390
    } // library marker kkossev.commonLib, line 391
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 392
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 393
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 394
    } else { // library marker kkossev.commonLib, line 395
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 396
    } // library marker kkossev.commonLib, line 397
} // library marker kkossev.commonLib, line 398

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 400
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 401
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 402
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 403
    if (status == 0) { // library marker kkossev.commonLib, line 404
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 405
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 406
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 407
        int delta = 0 // library marker kkossev.commonLib, line 408
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 409
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 410
        } // library marker kkossev.commonLib, line 411
        else { // library marker kkossev.commonLib, line 412
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 413
        } // library marker kkossev.commonLib, line 414
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 415
    } // library marker kkossev.commonLib, line 416
    else { // library marker kkossev.commonLib, line 417
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
} // library marker kkossev.commonLib, line 420

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 422
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 423
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 424
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 425
        return false // library marker kkossev.commonLib, line 426
    } // library marker kkossev.commonLib, line 427
    // execute the customHandler function // library marker kkossev.commonLib, line 428
    boolean result = false // library marker kkossev.commonLib, line 429
    try { // library marker kkossev.commonLib, line 430
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 431
    } // library marker kkossev.commonLib, line 432
    catch (e) { // library marker kkossev.commonLib, line 433
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 434
        return false // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 437
    return result // library marker kkossev.commonLib, line 438
} // library marker kkossev.commonLib, line 439

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 441
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 442
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 443
    final String commandId = data[0] // library marker kkossev.commonLib, line 444
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 445
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 446
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 447
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 448
    } else { // library marker kkossev.commonLib, line 449
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 450
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 451
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 452
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 453
        } // library marker kkossev.commonLib, line 454
    } // library marker kkossev.commonLib, line 455
} // library marker kkossev.commonLib, line 456

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 458
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 459
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 460
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 461

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 463
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 464
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 465
] // library marker kkossev.commonLib, line 466

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 468
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 469
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 470
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 471
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 472
] // library marker kkossev.commonLib, line 473

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 475
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 476
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 477
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 478
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 479
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 480
    return avg // library marker kkossev.commonLib, line 481
} // library marker kkossev.commonLib, line 482

/* // library marker kkossev.commonLib, line 484
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 485
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 486
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 487
*/ // library marker kkossev.commonLib, line 488
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 489

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 491
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 492
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 493
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 494
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 495
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 496
        case 0x0000: // library marker kkossev.commonLib, line 497
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 498
            break // library marker kkossev.commonLib, line 499
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 500
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 501
            if (isPing) { // library marker kkossev.commonLib, line 502
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 503
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 504
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 505
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 506
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 507
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 508
                    sendRttEvent() // library marker kkossev.commonLib, line 509
                } // library marker kkossev.commonLib, line 510
                else { // library marker kkossev.commonLib, line 511
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 512
                } // library marker kkossev.commonLib, line 513
                state.states['isPing'] = false // library marker kkossev.commonLib, line 514
            } // library marker kkossev.commonLib, line 515
            else { // library marker kkossev.commonLib, line 516
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 517
            } // library marker kkossev.commonLib, line 518
            break // library marker kkossev.commonLib, line 519
        case 0x0004: // library marker kkossev.commonLib, line 520
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 521
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 522
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 523
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 524
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 525
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 526
            } // library marker kkossev.commonLib, line 527
            break // library marker kkossev.commonLib, line 528
        case 0x0005: // library marker kkossev.commonLib, line 529
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 530
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 531
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 532
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 533
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 534
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 535
            } // library marker kkossev.commonLib, line 536
            break // library marker kkossev.commonLib, line 537
        case 0x0007: // library marker kkossev.commonLib, line 538
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 539
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 540
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case 0xFFDF: // library marker kkossev.commonLib, line 543
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 544
            break // library marker kkossev.commonLib, line 545
        case 0xFFE2: // library marker kkossev.commonLib, line 546
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 549
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 550
            break // library marker kkossev.commonLib, line 551
        case 0xFFFE: // library marker kkossev.commonLib, line 552
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 555
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 556
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 557
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 558
            break // library marker kkossev.commonLib, line 559
        default: // library marker kkossev.commonLib, line 560
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 561
            break // library marker kkossev.commonLib, line 562
    } // library marker kkossev.commonLib, line 563
} // library marker kkossev.commonLib, line 564

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 566
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 567
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 568

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 570
    Map descMap = [:] // library marker kkossev.commonLib, line 571
    try { // library marker kkossev.commonLib, line 572
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 573
    } // library marker kkossev.commonLib, line 574
    catch (e1) { // library marker kkossev.commonLib, line 575
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 576
        // try alternative custom parsing // library marker kkossev.commonLib, line 577
        descMap = [:] // library marker kkossev.commonLib, line 578
        try { // library marker kkossev.commonLib, line 579
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 580
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 581
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 582
            } // library marker kkossev.commonLib, line 583
        } // library marker kkossev.commonLib, line 584
        catch (e2) { // library marker kkossev.commonLib, line 585
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 586
            return [:] // library marker kkossev.commonLib, line 587
        } // library marker kkossev.commonLib, line 588
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 589
    } // library marker kkossev.commonLib, line 590
    return descMap // library marker kkossev.commonLib, line 591
} // library marker kkossev.commonLib, line 592

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 594
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 595
        return false // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
    // try to parse ... // library marker kkossev.commonLib, line 598
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 599
    Map descMap = [:] // library marker kkossev.commonLib, line 600
    try { // library marker kkossev.commonLib, line 601
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 602
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604
    catch (e) { // library marker kkossev.commonLib, line 605
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 606
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 607
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 608
        return true // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 612
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 613
    } // library marker kkossev.commonLib, line 614
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 615
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 616
    } // library marker kkossev.commonLib, line 617
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 618
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 619
    } // library marker kkossev.commonLib, line 620
    else { // library marker kkossev.commonLib, line 621
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 622
        return false // library marker kkossev.commonLib, line 623
    } // library marker kkossev.commonLib, line 624
    return true    // processed // library marker kkossev.commonLib, line 625
} // library marker kkossev.commonLib, line 626

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 628
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 629
  /* // library marker kkossev.commonLib, line 630
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 631
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 632
        return true // library marker kkossev.commonLib, line 633
    } // library marker kkossev.commonLib, line 634
*/ // library marker kkossev.commonLib, line 635
    Map descMap = [:] // library marker kkossev.commonLib, line 636
    try { // library marker kkossev.commonLib, line 637
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 638
    } // library marker kkossev.commonLib, line 639
    catch (e1) { // library marker kkossev.commonLib, line 640
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 641
        // try alternative custom parsing // library marker kkossev.commonLib, line 642
        descMap = [:] // library marker kkossev.commonLib, line 643
        try { // library marker kkossev.commonLib, line 644
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 645
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 646
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 647
            } // library marker kkossev.commonLib, line 648
        } // library marker kkossev.commonLib, line 649
        catch (e2) { // library marker kkossev.commonLib, line 650
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 651
            return true // library marker kkossev.commonLib, line 652
        } // library marker kkossev.commonLib, line 653
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 656
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 657
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 658
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 659
        return false // library marker kkossev.commonLib, line 660
    } // library marker kkossev.commonLib, line 661
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 662
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 663
    // attribute report received // library marker kkossev.commonLib, line 664
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 665
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 666
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
    attrData.each { // library marker kkossev.commonLib, line 669
        if (it.status == '86') { // library marker kkossev.commonLib, line 670
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 671
        // TODO - skip parsing? // library marker kkossev.commonLib, line 672
        } // library marker kkossev.commonLib, line 673
        switch (it.cluster) { // library marker kkossev.commonLib, line 674
            case '0000' : // library marker kkossev.commonLib, line 675
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 676
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 677
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 678
                } // library marker kkossev.commonLib, line 679
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 680
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 681
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 682
                } // library marker kkossev.commonLib, line 683
                else { // library marker kkossev.commonLib, line 684
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 685
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 686
                } // library marker kkossev.commonLib, line 687
                break // library marker kkossev.commonLib, line 688
            default : // library marker kkossev.commonLib, line 689
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 690
                break // library marker kkossev.commonLib, line 691
        } // switch // library marker kkossev.commonLib, line 692
    } // for each attribute // library marker kkossev.commonLib, line 693
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 694
} // library marker kkossev.commonLib, line 695


String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 698
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 699
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 700
} // library marker kkossev.commonLib, line 701

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 703
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 704
} // library marker kkossev.commonLib, line 705

/* // library marker kkossev.commonLib, line 707
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 708
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 709
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 710
*/ // library marker kkossev.commonLib, line 711
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 712
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 713
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 714

// Tuya Commands // library marker kkossev.commonLib, line 716
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 717
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 718
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 719
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 720
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 721

// tuya DP type // library marker kkossev.commonLib, line 723
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 724
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 725
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 726
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 727
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 728
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 729

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 731
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 732
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 733
    long offset = 0 // library marker kkossev.commonLib, line 734
    int offsetHours = 0 // library marker kkossev.commonLib, line 735
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 736
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 737
    try { // library marker kkossev.commonLib, line 738
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 739
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 740
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 741
    } catch (e) { // library marker kkossev.commonLib, line 742
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 743
    } // library marker kkossev.commonLib, line 744
    // // library marker kkossev.commonLib, line 745
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 746
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 747
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

// called from the main parse method when the cluster is 0xEF00 // library marker kkossev.commonLib, line 751
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 752
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 753
        syncTuyaDateTime() // library marker kkossev.commonLib, line 754
    } // library marker kkossev.commonLib, line 755
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 756
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 757
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 758
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 759
        if (status != '00') { // library marker kkossev.commonLib, line 760
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 761
        } // library marker kkossev.commonLib, line 762
    } // library marker kkossev.commonLib, line 763
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 764
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 765
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 766
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 767
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 768
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 769
            return // library marker kkossev.commonLib, line 770
        } // library marker kkossev.commonLib, line 771
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 772
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 773
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 774
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 775
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 776
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 777
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 778
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 779
            } // library marker kkossev.commonLib, line 780
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 781
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 782
        } // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    else { // library marker kkossev.commonLib, line 785
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 786
    } // library marker kkossev.commonLib, line 787
} // library marker kkossev.commonLib, line 788

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 790
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 791
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 792
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 793
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 794
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 795
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 799
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {   // library marker kkossev.commonLib, line 800
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 801
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 805
} // library marker kkossev.commonLib, line 806

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 808
    int retValue = 0 // library marker kkossev.commonLib, line 809
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 810
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 811
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 812
        int power = 1 // library marker kkossev.commonLib, line 813
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 814
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 815
            power = power * 256 // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    return retValue // library marker kkossev.commonLib, line 819
} // library marker kkossev.commonLib, line 820

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 822

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 824
    List<String> cmds = [] // library marker kkossev.commonLib, line 825
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 826
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 827
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 828
    int tuyaCmd = isFingerbot() ? 0x04 : tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 829
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 830
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 831
    return cmds // library marker kkossev.commonLib, line 832
} // library marker kkossev.commonLib, line 833

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 835

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 837
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 838
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 839
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 840
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 841
} // library marker kkossev.commonLib, line 842

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 844
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 845

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 847
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 848
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 849
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 850
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 851
} // library marker kkossev.commonLib, line 852

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 854
    List<String> cmds = [] // library marker kkossev.commonLib, line 855
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 856
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 857
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 858
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 859
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 860
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 861
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 862
        } // library marker kkossev.commonLib, line 863
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 864
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 865
    } // library marker kkossev.commonLib, line 866
    else { // library marker kkossev.commonLib, line 867
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 868
    } // library marker kkossev.commonLib, line 869
} // library marker kkossev.commonLib, line 870

// Invoked from configure() // library marker kkossev.commonLib, line 872
List<String> initializeDevice() { // library marker kkossev.commonLib, line 873
    List<String> cmds = [] // library marker kkossev.commonLib, line 874
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 875
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 876
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 877
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 878
    } // library marker kkossev.commonLib, line 879
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 880
    return cmds // library marker kkossev.commonLib, line 881
} // library marker kkossev.commonLib, line 882

// Invoked from configure() // library marker kkossev.commonLib, line 884
List<String> configureDevice() { // library marker kkossev.commonLib, line 885
    List<String> cmds = [] // library marker kkossev.commonLib, line 886
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 887
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 888
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 889
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 892
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 893
    return cmds // library marker kkossev.commonLib, line 894
} // library marker kkossev.commonLib, line 895

/* // library marker kkossev.commonLib, line 897
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 898
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 899
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 900
*/ // library marker kkossev.commonLib, line 901

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 903
    List<String> cmds = [] // library marker kkossev.commonLib, line 904
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 905
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 906
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 907
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 908
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 909
            } // library marker kkossev.commonLib, line 910
        } // library marker kkossev.commonLib, line 911
    } // library marker kkossev.commonLib, line 912
    return cmds // library marker kkossev.commonLib, line 913
} // library marker kkossev.commonLib, line 914

void refresh() { // library marker kkossev.commonLib, line 916
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 917
    checkDriverVersion(state) // library marker kkossev.commonLib, line 918
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 919
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 920
        customCmds = customRefresh() // library marker kkossev.commonLib, line 921
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 922
    } // library marker kkossev.commonLib, line 923
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 924
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 925
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 926
    } // library marker kkossev.commonLib, line 927
    /* // library marker kkossev.commonLib, line 928
    if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 929
        cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 930
        cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 931
    } // library marker kkossev.commonLib, line 932
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 933
        cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 934
        cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 935
    } // library marker kkossev.commonLib, line 936
    */ // library marker kkossev.commonLib, line 937
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 938
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 939
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 940
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 941
    } // library marker kkossev.commonLib, line 942
    else { // library marker kkossev.commonLib, line 943
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
} // library marker kkossev.commonLib, line 946

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 948
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 949
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 950

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 952
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 953
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 954
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 955
    } // library marker kkossev.commonLib, line 956
    else { // library marker kkossev.commonLib, line 957
        logInfo "${info}" // library marker kkossev.commonLib, line 958
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 959
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 960
    } // library marker kkossev.commonLib, line 961
} // library marker kkossev.commonLib, line 962

public void ping() { // library marker kkossev.commonLib, line 964
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 965
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 966
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 967
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 968
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 969
    logDebug 'ping...' // library marker kkossev.commonLib, line 970
} // library marker kkossev.commonLib, line 971

def virtualPong() { // library marker kkossev.commonLib, line 973
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 974
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 975
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 976
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 977
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 978
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 979
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 980
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 981
        sendRttEvent() // library marker kkossev.commonLib, line 982
    } // library marker kkossev.commonLib, line 983
    else { // library marker kkossev.commonLib, line 984
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    state.states['isPing'] = false // library marker kkossev.commonLib, line 987
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 988
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 989
} // library marker kkossev.commonLib, line 990

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 992
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 993
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 994
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 995
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 996
    if (value == null) { // library marker kkossev.commonLib, line 997
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 998
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 999
    } // library marker kkossev.commonLib, line 1000
    else { // library marker kkossev.commonLib, line 1001
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1002
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1003
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1004
    } // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1008
    if (cluster != null) { // library marker kkossev.commonLib, line 1009
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1010
    } // library marker kkossev.commonLib, line 1011
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1012
    return 'NULL' // library marker kkossev.commonLib, line 1013
} // library marker kkossev.commonLib, line 1014

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1016
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1017
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1018
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1019
} // library marker kkossev.commonLib, line 1020

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1022
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1023
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1024
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1025
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1026
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1027
    } // library marker kkossev.commonLib, line 1028
} // library marker kkossev.commonLib, line 1029

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1031
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1032
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1033
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1034
} // library marker kkossev.commonLib, line 1035

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1037
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1038
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1039
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1040
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1041
    } // library marker kkossev.commonLib, line 1042
    else { // library marker kkossev.commonLib, line 1043
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1044
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1045
    } // library marker kkossev.commonLib, line 1046
} // library marker kkossev.commonLib, line 1047

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1049
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1050
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1051
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1052
} // library marker kkossev.commonLib, line 1053

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1055
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1056
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1057
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1058
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1059
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1060
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1061
    } // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1065
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1066
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1067
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1068
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1069
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1070
            logWarn 'not present!' // library marker kkossev.commonLib, line 1071
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1072
        } // library marker kkossev.commonLib, line 1073
    } // library marker kkossev.commonLib, line 1074
    else { // library marker kkossev.commonLib, line 1075
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1078
} // library marker kkossev.commonLib, line 1079

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1081
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1082
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1083
    if (value == 'online') { // library marker kkossev.commonLib, line 1084
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1085
    } // library marker kkossev.commonLib, line 1086
    else { // library marker kkossev.commonLib, line 1087
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1088
    } // library marker kkossev.commonLib, line 1089
} // library marker kkossev.commonLib, line 1090

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1092
void updated() { // library marker kkossev.commonLib, line 1093
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1094
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1095
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1096
    unschedule() // library marker kkossev.commonLib, line 1097

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1099
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1100
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1103
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1104
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1108
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1109
        // schedule the periodic timer // library marker kkossev.commonLib, line 1110
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1111
        if (interval > 0) { // library marker kkossev.commonLib, line 1112
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1113
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1114
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1115
        } // library marker kkossev.commonLib, line 1116
    } // library marker kkossev.commonLib, line 1117
    else { // library marker kkossev.commonLib, line 1118
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1119
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1120
    } // library marker kkossev.commonLib, line 1121
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1122
        customUpdated() // library marker kkossev.commonLib, line 1123
    } // library marker kkossev.commonLib, line 1124

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1126
} // library marker kkossev.commonLib, line 1127

void logsOff() { // library marker kkossev.commonLib, line 1129
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1130
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1131
} // library marker kkossev.commonLib, line 1132
void traceOff() { // library marker kkossev.commonLib, line 1133
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1134
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1135
} // library marker kkossev.commonLib, line 1136

void configure(String command) { // library marker kkossev.commonLib, line 1138
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1139
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1140
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1141
        return // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    // // library marker kkossev.commonLib, line 1144
    String func // library marker kkossev.commonLib, line 1145
    try { // library marker kkossev.commonLib, line 1146
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1147
        "$func"() // library marker kkossev.commonLib, line 1148
    } // library marker kkossev.commonLib, line 1149
    catch (e) { // library marker kkossev.commonLib, line 1150
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1151
        return // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1154
} // library marker kkossev.commonLib, line 1155

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1157
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1158
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1159
} // library marker kkossev.commonLib, line 1160

void loadAllDefaults() { // library marker kkossev.commonLib, line 1162
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1163
    deleteAllSettings() // library marker kkossev.commonLib, line 1164
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1165
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1166
    deleteAllStates() // library marker kkossev.commonLib, line 1167
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1168

    initialize() // library marker kkossev.commonLib, line 1170
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1171
    updated() // library marker kkossev.commonLib, line 1172
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1173

} // library marker kkossev.commonLib, line 1175

void configureNow() { // library marker kkossev.commonLib, line 1177
    configure() // library marker kkossev.commonLib, line 1178
} // library marker kkossev.commonLib, line 1179

/** // library marker kkossev.commonLib, line 1181
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1182
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1183
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1184
 */ // library marker kkossev.commonLib, line 1185
void configure() { // library marker kkossev.commonLib, line 1186
    List<String> cmds = [] // library marker kkossev.commonLib, line 1187
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1188
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1189
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1190
    if (isTuya()) { // library marker kkossev.commonLib, line 1191
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1192
    } // library marker kkossev.commonLib, line 1193
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1194
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1195
    } // library marker kkossev.commonLib, line 1196
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1197
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1198
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1199
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1200
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1201
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1202
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1203
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1204
    } // library marker kkossev.commonLib, line 1205
    else { // library marker kkossev.commonLib, line 1206
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1207
    } // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1211
void installed() { // library marker kkossev.commonLib, line 1212
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1213
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1214
    // populate some default values for attributes // library marker kkossev.commonLib, line 1215
    sendEvent(name: 'healthStatus', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1216
    sendEvent(name: 'powerSource',  value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1217
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1218
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1219
} // library marker kkossev.commonLib, line 1220

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1222
void initialize() { // library marker kkossev.commonLib, line 1223
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1224
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1225
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1226
    updateTuyaVersion() // library marker kkossev.commonLib, line 1227
    updateAqaraVersion() // library marker kkossev.commonLib, line 1228
} // library marker kkossev.commonLib, line 1229

/* // library marker kkossev.commonLib, line 1231
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1232
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1233
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1234
*/ // library marker kkossev.commonLib, line 1235

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1237
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1238
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1239
} // library marker kkossev.commonLib, line 1240

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1242
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1243
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1244
} // library marker kkossev.commonLib, line 1245

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1247
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1248
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1249
} // library marker kkossev.commonLib, line 1250

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1252
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1253
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1254
        return // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1257
    cmd.each { // library marker kkossev.commonLib, line 1258
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1259
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1260
            return // library marker kkossev.commonLib, line 1261
        } // library marker kkossev.commonLib, line 1262
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1263
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1266
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1267
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1268
} // library marker kkossev.commonLib, line 1269

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1271

String getDeviceInfo() { // library marker kkossev.commonLib, line 1273
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1274
} // library marker kkossev.commonLib, line 1275

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1277
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1278
} // library marker kkossev.commonLib, line 1279

@CompileStatic // library marker kkossev.commonLib, line 1281
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1282
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1283
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1284
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1285
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1286
        initializeVars(false) // library marker kkossev.commonLib, line 1287
        updateTuyaVersion() // library marker kkossev.commonLib, line 1288
        updateAqaraVersion() // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1291
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1292
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1293
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1294
} // library marker kkossev.commonLib, line 1295

// credits @thebearmay // library marker kkossev.commonLib, line 1297
String getModel() { // library marker kkossev.commonLib, line 1298
    try { // library marker kkossev.commonLib, line 1299
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1300
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1301
    } catch (ignore) { // library marker kkossev.commonLib, line 1302
        try { // library marker kkossev.commonLib, line 1303
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1304
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1305
                return model // library marker kkossev.commonLib, line 1306
            } // library marker kkossev.commonLib, line 1307
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1308
            return '' // library marker kkossev.commonLib, line 1309
        } // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

// credits @thebearmay // library marker kkossev.commonLib, line 1314
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1315
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1316
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1317
    String revision = tokens.last() // library marker kkossev.commonLib, line 1318
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1319
} // library marker kkossev.commonLib, line 1320

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1322
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1323
    unschedule() // library marker kkossev.commonLib, line 1324
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1325
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1326

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1328
} // library marker kkossev.commonLib, line 1329

void resetStatistics() { // library marker kkossev.commonLib, line 1331
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1332
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1333
} // library marker kkossev.commonLib, line 1334

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1336
void resetStats() { // library marker kkossev.commonLib, line 1337
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1338
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1339
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1340
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1341
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1342
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1343
} // library marker kkossev.commonLib, line 1344

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1346
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1347
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1348
        state.clear() // library marker kkossev.commonLib, line 1349
        unschedule() // library marker kkossev.commonLib, line 1350
        resetStats() // library marker kkossev.commonLib, line 1351
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1352
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1353
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1354
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1355
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1356
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1357
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1358
    } // library marker kkossev.commonLib, line 1359

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1361
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1362
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1363
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1364
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1365

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1367
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1368
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1369
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1370
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1371
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1372
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1373

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1375

    // common libraries initialization // library marker kkossev.commonLib, line 1377
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1378
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1379
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1380
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1381

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1383
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1384
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1385
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1386

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1388
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1389
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1390
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1391
    if ( ep  != null) { // library marker kkossev.commonLib, line 1392
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1393
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1394
    } // library marker kkossev.commonLib, line 1395
    else { // library marker kkossev.commonLib, line 1396
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1397
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1398
    } // library marker kkossev.commonLib, line 1399
} // library marker kkossev.commonLib, line 1400

void setDestinationEP() { // library marker kkossev.commonLib, line 1402
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1403
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1404
        state.destinationEP = ep // library marker kkossev.commonLib, line 1405
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1406
    } // library marker kkossev.commonLib, line 1407
    else { // library marker kkossev.commonLib, line 1408
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1409
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1410
    } // library marker kkossev.commonLib, line 1411
} // library marker kkossev.commonLib, line 1412

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1414
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1415
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1416
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1417

// _DEBUG mode only // library marker kkossev.commonLib, line 1419
void getAllProperties() { // library marker kkossev.commonLib, line 1420
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1421
    device.properties.each { it -> // library marker kkossev.commonLib, line 1422
        log.debug it // library marker kkossev.commonLib, line 1423
    } // library marker kkossev.commonLib, line 1424
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1425
    settings.each { it -> // library marker kkossev.commonLib, line 1426
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1427
    } // library marker kkossev.commonLib, line 1428
    log.trace 'Done' // library marker kkossev.commonLib, line 1429
} // library marker kkossev.commonLib, line 1430

// delete all Preferences // library marker kkossev.commonLib, line 1432
void deleteAllSettings() { // library marker kkossev.commonLib, line 1433
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1434
    settings.each { it -> // library marker kkossev.commonLib, line 1435
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1436
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1437
    } // library marker kkossev.commonLib, line 1438
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1439
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1440
} // library marker kkossev.commonLib, line 1441

// delete all attributes // library marker kkossev.commonLib, line 1443
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1444
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1445
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1446
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

// delete all State Variables // library marker kkossev.commonLib, line 1450
void deleteAllStates() { // library marker kkossev.commonLib, line 1451
    String stateDeleted = '' // library marker kkossev.commonLib, line 1452
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1453
    state.clear() // library marker kkossev.commonLib, line 1454
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1455
} // library marker kkossev.commonLib, line 1456

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1458
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1459
} // library marker kkossev.commonLib, line 1460

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1462
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1463
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1464
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1465
    } // library marker kkossev.commonLib, line 1466
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1467
} // library marker kkossev.commonLib, line 1468

void testParse(String par) { // library marker kkossev.commonLib, line 1470
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1471
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1472
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1473
    parse(par) // library marker kkossev.commonLib, line 1474
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1475
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1476
} // library marker kkossev.commonLib, line 1477

def testJob() { // library marker kkossev.commonLib, line 1479
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

/** // library marker kkossev.commonLib, line 1483
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1484
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1485
 */ // library marker kkossev.commonLib, line 1486
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1487
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1488
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1489
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1490
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1491
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1492
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1493
    String cron // library marker kkossev.commonLib, line 1494
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1495
    else { // library marker kkossev.commonLib, line 1496
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1497
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1498
    } // library marker kkossev.commonLib, line 1499
    return cron // library marker kkossev.commonLib, line 1500
} // library marker kkossev.commonLib, line 1501

// credits @thebearmay // library marker kkossev.commonLib, line 1503
String formatUptime() { // library marker kkossev.commonLib, line 1504
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1505
} // library marker kkossev.commonLib, line 1506

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1508
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1509
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1510
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1511
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1512
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1513
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1514
} // library marker kkossev.commonLib, line 1515

boolean isTuya() { // library marker kkossev.commonLib, line 1517
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1518
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1519
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1520
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1521
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1522
} // library marker kkossev.commonLib, line 1523

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1525
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1526
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1527
    if (application != null) { // library marker kkossev.commonLib, line 1528
        Integer ver // library marker kkossev.commonLib, line 1529
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1530
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1531
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1532
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1533
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1534
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1535
        } // library marker kkossev.commonLib, line 1536
    } // library marker kkossev.commonLib, line 1537
} // library marker kkossev.commonLib, line 1538

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1540

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1542
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1543
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1544
    if (application != null) { // library marker kkossev.commonLib, line 1545
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1546
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1547
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1548
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1549
        } // library marker kkossev.commonLib, line 1550
    } // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1554
    try { // library marker kkossev.commonLib, line 1555
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1556
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1557
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1558
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1559
    } catch (e) { // library marker kkossev.commonLib, line 1560
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1561
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1562
    } // library marker kkossev.commonLib, line 1563
} // library marker kkossev.commonLib, line 1564

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1566
    try { // library marker kkossev.commonLib, line 1567
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1568
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1569
        return date.getTime() // library marker kkossev.commonLib, line 1570
    } catch (e) { // library marker kkossev.commonLib, line 1571
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1572
        return now() // library marker kkossev.commonLib, line 1573
    } // library marker kkossev.commonLib, line 1574
} // library marker kkossev.commonLib, line 1575

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.2.1' // library marker kkossev.deviceProfileLib, line 5
) // library marker kkossev.deviceProfileLib, line 6
/* // library marker kkossev.deviceProfileLib, line 7
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 8
 * // library marker kkossev.deviceProfileLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 11
 * // library marker kkossev.deviceProfileLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 17
 * // library marker kkossev.deviceProfileLib, line 18
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - (dev. branch) inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - (dev. branch) more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - (dev. branch) more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - (dev. branch) added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-05 kkossev  - (dev. branch) Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * // library marker kkossev.deviceProfileLib, line 30
 *                                   TODO - remove 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - check why the forcedProfile preference is not initialized? // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 36
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 37
 * // library marker kkossev.deviceProfileLib, line 38
*/ // library marker kkossev.deviceProfileLib, line 39

static String deviceProfileLibVersion()   { '3.2.1' } // library marker kkossev.deviceProfileLib, line 41
static String deviceProfileLibStamp() { '2024/06/05 1:06 PM' } // library marker kkossev.deviceProfileLib, line 42
import groovy.json.* // library marker kkossev.deviceProfileLib, line 43
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 44
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 45
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 46
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 47

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 49

metadata { // library marker kkossev.deviceProfileLib, line 51
    // no capabilities // library marker kkossev.deviceProfileLib, line 52
    // no attributes // library marker kkossev.deviceProfileLib, line 53
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 54
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 55
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 56
    ] // library marker kkossev.deviceProfileLib, line 57
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 58
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 59
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 60
    ] // library marker kkossev.deviceProfileLib, line 61

    preferences { // library marker kkossev.deviceProfileLib, line 63
        if (device) { // library marker kkossev.deviceProfileLib, line 64
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 65
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 66
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 67
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 68
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 69
                        input inputMap // library marker kkossev.deviceProfileLib, line 70
                    } // library marker kkossev.deviceProfileLib, line 71
                } // library marker kkossev.deviceProfileLib, line 72
            } // library marker kkossev.deviceProfileLib, line 73
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 74
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 75
            //} // library marker kkossev.deviceProfileLib, line 76
        } // library marker kkossev.deviceProfileLib, line 77
    } // library marker kkossev.deviceProfileLib, line 78
} // library marker kkossev.deviceProfileLib, line 79

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') }    // patch removed 05/29/2024 // library marker kkossev.deviceProfileLib, line 81

String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 83
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 84
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 85
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 86

List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 88
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 89
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 90
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 91
    } // library marker kkossev.deviceProfileLib, line 92
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 93
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 94
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 95
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 96
        } // library marker kkossev.deviceProfileLib, line 97
    } // library marker kkossev.deviceProfileLib, line 98
    return activeProfiles // library marker kkossev.deviceProfileLib, line 99
} // library marker kkossev.deviceProfileLib, line 100


// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 103

/** // library marker kkossev.deviceProfileLib, line 105
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 106
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 107
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 108
 */ // library marker kkossev.deviceProfileLib, line 109
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 110
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 111
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 112
    else { return null } // library marker kkossev.deviceProfileLib, line 113
} // library marker kkossev.deviceProfileLib, line 114

/** // library marker kkossev.deviceProfileLib, line 116
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 117
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 118
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 119
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 120
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 121
 */ // library marker kkossev.deviceProfileLib, line 122
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 123
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 124
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 125
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 126
    def preference // library marker kkossev.deviceProfileLib, line 127
    try { // library marker kkossev.deviceProfileLib, line 128
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 129
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 130
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 131
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 132
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 133
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 134
        } // library marker kkossev.deviceProfileLib, line 135
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 136
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 137
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 138
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 139
        } // library marker kkossev.deviceProfileLib, line 140
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 141
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 142
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 143
        } // library marker kkossev.deviceProfileLib, line 144
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 145
    } catch (e) { // library marker kkossev.deviceProfileLib, line 146
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 147
        return [:] // library marker kkossev.deviceProfileLib, line 148
    } // library marker kkossev.deviceProfileLib, line 149
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 150
    return foundMap // library marker kkossev.deviceProfileLib, line 151
} // library marker kkossev.deviceProfileLib, line 152

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 154
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 155
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 156
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 157
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 158
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 159
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 160
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 161
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 162
            return foundMap // library marker kkossev.deviceProfileLib, line 163
        } // library marker kkossev.deviceProfileLib, line 164
    } // library marker kkossev.deviceProfileLib, line 165
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 166
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 167
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 168
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 169
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 170
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 171
            return foundMap // library marker kkossev.deviceProfileLib, line 172
        } // library marker kkossev.deviceProfileLib, line 173
    } // library marker kkossev.deviceProfileLib, line 174
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 175
    return [:] // library marker kkossev.deviceProfileLib, line 176
} // library marker kkossev.deviceProfileLib, line 177

/** // library marker kkossev.deviceProfileLib, line 179
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 180
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 181
 */ // library marker kkossev.deviceProfileLib, line 182
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 183
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 184
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 185
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 186
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 187
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 188
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 189
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 190
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 191
            return // continue // library marker kkossev.deviceProfileLib, line 192
        } // library marker kkossev.deviceProfileLib, line 193
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 194
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 195
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 196
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 197
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 198
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 199
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 200
    } // library marker kkossev.deviceProfileLib, line 201
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 202
} // library marker kkossev.deviceProfileLib, line 203

/** // library marker kkossev.deviceProfileLib, line 205
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 206
 * // library marker kkossev.deviceProfileLib, line 207
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 208
 */ // library marker kkossev.deviceProfileLib, line 209
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 210
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 211
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 212
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 213
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 214
    } // library marker kkossev.deviceProfileLib, line 215
    return validPars // library marker kkossev.deviceProfileLib, line 216
} // library marker kkossev.deviceProfileLib, line 217

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 219
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 220
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 221
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 222
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 223
    def scaledValue // library marker kkossev.deviceProfileLib, line 224
    if (value == null) { // library marker kkossev.deviceProfileLib, line 225
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 226
        return null // library marker kkossev.deviceProfileLib, line 227
    } // library marker kkossev.deviceProfileLib, line 228
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 229
        case 'number' : // library marker kkossev.deviceProfileLib, line 230
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 231
            break // library marker kkossev.deviceProfileLib, line 232
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 233
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 234
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 235
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 236
            } // library marker kkossev.deviceProfileLib, line 237
            break // library marker kkossev.deviceProfileLib, line 238
        case 'bool' : // library marker kkossev.deviceProfileLib, line 239
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'enum' : // library marker kkossev.deviceProfileLib, line 242
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 243
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 244
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 245
                return null // library marker kkossev.deviceProfileLib, line 246
            } // library marker kkossev.deviceProfileLib, line 247
            scaledValue = value // library marker kkossev.deviceProfileLib, line 248
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 249
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 250
            } // library marker kkossev.deviceProfileLib, line 251
            break // library marker kkossev.deviceProfileLib, line 252
        default : // library marker kkossev.deviceProfileLib, line 253
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 254
            return null // library marker kkossev.deviceProfileLib, line 255
    } // library marker kkossev.deviceProfileLib, line 256
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 257
    return scaledValue // library marker kkossev.deviceProfileLib, line 258
} // library marker kkossev.deviceProfileLib, line 259

// called from updated() method // library marker kkossev.deviceProfileLib, line 261
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 262
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 263
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 264
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 265
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 266
        return // library marker kkossev.deviceProfileLib, line 267
    } // library marker kkossev.deviceProfileLib, line 268
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 269
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 270
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 271
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 272
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 273
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 274
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 275
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 276
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 277
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 278
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 279
                // scale the value // library marker kkossev.deviceProfileLib, line 280
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 281
            } // library marker kkossev.deviceProfileLib, line 282
            if (preferenceValue != null) { setPar(name, preferenceValue.toString()) } // library marker kkossev.deviceProfileLib, line 283
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 284
        } // library marker kkossev.deviceProfileLib, line 285
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 286
    } // library marker kkossev.deviceProfileLib, line 287
    return // library marker kkossev.deviceProfileLib, line 288
} // library marker kkossev.deviceProfileLib, line 289

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 291
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 292
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 293
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 294
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 295
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 296
} // library marker kkossev.deviceProfileLib, line 297
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 298
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 299
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 300
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 301
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 302
} // library marker kkossev.deviceProfileLib, line 303
int invert(int val) { // library marker kkossev.deviceProfileLib, line 304
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 305
    else { return val } // library marker kkossev.deviceProfileLib, line 306
} // library marker kkossev.deviceProfileLib, line 307

List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 309
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 310
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 311
    Map map = [:] // library marker kkossev.deviceProfileLib, line 312
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 313
    try { // library marker kkossev.deviceProfileLib, line 314
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 315
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 316
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 317
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 318
    } // library marker kkossev.deviceProfileLib, line 319
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 320
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 321
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 322
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 323
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 324
    } // library marker kkossev.deviceProfileLib, line 325
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 326
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 327
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 200) // library marker kkossev.deviceProfileLib, line 328
    } // library marker kkossev.deviceProfileLib, line 329
    else { // library marker kkossev.deviceProfileLib, line 330
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 331
    } // library marker kkossev.deviceProfileLib, line 332
    return cmds // library marker kkossev.deviceProfileLib, line 333
} // library marker kkossev.deviceProfileLib, line 334

/** // library marker kkossev.deviceProfileLib, line 336
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 337
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 338
 * // library marker kkossev.deviceProfileLib, line 339
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 340
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 341
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 342
 */ // library marker kkossev.deviceProfileLib, line 343
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 344
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 345
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 346
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 347
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 348
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 349
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 350
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 351
        case 'number' : // library marker kkossev.deviceProfileLib, line 352
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 353
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 354
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 355
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 356
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 357
            } // library marker kkossev.deviceProfileLib, line 358
            else { // library marker kkossev.deviceProfileLib, line 359
                scaledValue = value // library marker kkossev.deviceProfileLib, line 360
            } // library marker kkossev.deviceProfileLib, line 361
            break // library marker kkossev.deviceProfileLib, line 362

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 364
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 365
            // scale the value // library marker kkossev.deviceProfileLib, line 366
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 367
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 368
            } // library marker kkossev.deviceProfileLib, line 369
            else { // library marker kkossev.deviceProfileLib, line 370
                scaledValue = value // library marker kkossev.deviceProfileLib, line 371
            } // library marker kkossev.deviceProfileLib, line 372
            break // library marker kkossev.deviceProfileLib, line 373

        case 'bool' : // library marker kkossev.deviceProfileLib, line 375
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 376
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 377
            else { // library marker kkossev.deviceProfileLib, line 378
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 379
                return null // library marker kkossev.deviceProfileLib, line 380
            } // library marker kkossev.deviceProfileLib, line 381
            break // library marker kkossev.deviceProfileLib, line 382
        case 'enum' : // library marker kkossev.deviceProfileLib, line 383
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 384
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 385
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 386
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 387
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 388
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 389
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 390
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 391
            } // library marker kkossev.deviceProfileLib, line 392
            else { // library marker kkossev.deviceProfileLib, line 393
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 394
            } // library marker kkossev.deviceProfileLib, line 395
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 396
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 397
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 398
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 399
                // find the key for the value // library marker kkossev.deviceProfileLib, line 400
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 401
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 402
                if (key == null) { // library marker kkossev.deviceProfileLib, line 403
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 404
                    return null // library marker kkossev.deviceProfileLib, line 405
                } // library marker kkossev.deviceProfileLib, line 406
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 407
            //return null // library marker kkossev.deviceProfileLib, line 408
            } // library marker kkossev.deviceProfileLib, line 409
            break // library marker kkossev.deviceProfileLib, line 410
        default : // library marker kkossev.deviceProfileLib, line 411
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 412
            return null // library marker kkossev.deviceProfileLib, line 413
    } // library marker kkossev.deviceProfileLib, line 414
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 415
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 416
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 417
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 418
        return null // library marker kkossev.deviceProfileLib, line 419
    } // library marker kkossev.deviceProfileLib, line 420
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 421
    return scaledValue // library marker kkossev.deviceProfileLib, line 422
} // library marker kkossev.deviceProfileLib, line 423

/** // library marker kkossev.deviceProfileLib, line 425
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 426
 * // library marker kkossev.deviceProfileLib, line 427
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 428
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 429
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 430
 */ // library marker kkossev.deviceProfileLib, line 431
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 432
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 433
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 434
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 435
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 436
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 437
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 438
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 439
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 440
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 441
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 442
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 443
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 444

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 446
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 447
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 448
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 449
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 450
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 451
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 452
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 453
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 454
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 455
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 456
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 457
            return true // library marker kkossev.deviceProfileLib, line 458
        } // library marker kkossev.deviceProfileLib, line 459
        else { // library marker kkossev.deviceProfileLib, line 460
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 461
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 462
        } // library marker kkossev.deviceProfileLib, line 463
    } // library marker kkossev.deviceProfileLib, line 464
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 465
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 466
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 467
        def valMiscType // library marker kkossev.deviceProfileLib, line 468
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 469
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 470
            // find the key for the value // library marker kkossev.deviceProfileLib, line 471
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 472
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 473
            if (key == null) { // library marker kkossev.deviceProfileLib, line 474
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 475
                return false // library marker kkossev.deviceProfileLib, line 476
            } // library marker kkossev.deviceProfileLib, line 477
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 478
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 479
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 480
        } // library marker kkossev.deviceProfileLib, line 481
        else { // library marker kkossev.deviceProfileLib, line 482
            valMiscType = val // library marker kkossev.deviceProfileLib, line 483
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 484
        } // library marker kkossev.deviceProfileLib, line 485
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 486
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 487
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 488
        return true // library marker kkossev.deviceProfileLib, line 489
    } // library marker kkossev.deviceProfileLib, line 490

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 492
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 493

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 495
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 496
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 497
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 498
        // Tuya DP // library marker kkossev.deviceProfileLib, line 499
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 500
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 501
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 502
            return false // library marker kkossev.deviceProfileLib, line 503
        } // library marker kkossev.deviceProfileLib, line 504
        else { // library marker kkossev.deviceProfileLib, line 505
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 506
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 507
            return false // library marker kkossev.deviceProfileLib, line 508
        } // library marker kkossev.deviceProfileLib, line 509
    } // library marker kkossev.deviceProfileLib, line 510
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 511
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 512
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mapMfCode=${dpMap.mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 513
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 514
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 515
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 516
            return false // library marker kkossev.deviceProfileLib, line 517
        } // library marker kkossev.deviceProfileLib, line 518
    } // library marker kkossev.deviceProfileLib, line 519
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 520
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 521
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 522
    return true // library marker kkossev.deviceProfileLib, line 523
} // library marker kkossev.deviceProfileLib, line 524

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 526
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 527
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 528
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 529
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 530
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 531
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 532
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 533
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 534
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 535
        return [] // library marker kkossev.deviceProfileLib, line 536
    } // library marker kkossev.deviceProfileLib, line 537
    String dpType // library marker kkossev.deviceProfileLib, line 538
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 539
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 540
    } // library marker kkossev.deviceProfileLib, line 541
    else { // library marker kkossev.deviceProfileLib, line 542
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 543
    } // library marker kkossev.deviceProfileLib, line 544
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 545
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 546
        return [] // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 549
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 550
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 551
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 552
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 553
    } // library marker kkossev.deviceProfileLib, line 554
    else { // library marker kkossev.deviceProfileLib, line 555
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 556
    } // library marker kkossev.deviceProfileLib, line 557
    return cmds // library marker kkossev.deviceProfileLib, line 558
} // library marker kkossev.deviceProfileLib, line 559

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 561
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 562
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 563
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 564
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 565
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 566

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 568
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 569
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 570
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 571
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 572
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 573
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 574
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 575
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 576
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 577
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 578
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 579
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 580
        try { // library marker kkossev.deviceProfileLib, line 581
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 582
        } // library marker kkossev.deviceProfileLib, line 583
        catch (e) { // library marker kkossev.deviceProfileLib, line 584
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 585
            return false // library marker kkossev.deviceProfileLib, line 586
        } // library marker kkossev.deviceProfileLib, line 587
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 588
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 589
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 590
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 591
            return true // library marker kkossev.deviceProfileLib, line 592
        } // library marker kkossev.deviceProfileLib, line 593
        else { // library marker kkossev.deviceProfileLib, line 594
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 595
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 596
        } // library marker kkossev.deviceProfileLib, line 597
    } // library marker kkossev.deviceProfileLib, line 598
    else { // library marker kkossev.deviceProfileLib, line 599
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 600
    } // library marker kkossev.deviceProfileLib, line 601
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 602
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 603
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 604
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 605
        // patch !! // library marker kkossev.deviceProfileLib, line 606
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 607
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 608
        } // library marker kkossev.deviceProfileLib, line 609
        else { // library marker kkossev.deviceProfileLib, line 610
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 611
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 612
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 613
        } // library marker kkossev.deviceProfileLib, line 614
        return true // library marker kkossev.deviceProfileLib, line 615
    } // library marker kkossev.deviceProfileLib, line 616
    else { // library marker kkossev.deviceProfileLib, line 617
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 618
    } // library marker kkossev.deviceProfileLib, line 619
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 620
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 621
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 622
    try { // library marker kkossev.deviceProfileLib, line 623
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 624
    } // library marker kkossev.deviceProfileLib, line 625
    catch (e) { // library marker kkossev.deviceProfileLib, line 626
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 627
        return false // library marker kkossev.deviceProfileLib, line 628
    } // library marker kkossev.deviceProfileLib, line 629
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 630
        // Tuya DP // library marker kkossev.deviceProfileLib, line 631
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 632
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 633
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 634
            return false // library marker kkossev.deviceProfileLib, line 635
        } // library marker kkossev.deviceProfileLib, line 636
        else { // library marker kkossev.deviceProfileLib, line 637
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 638
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 639
            return true // library marker kkossev.deviceProfileLib, line 640
        } // library marker kkossev.deviceProfileLib, line 641
    } // library marker kkossev.deviceProfileLib, line 642
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 643
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 644
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 647
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 648
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 649
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 650
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 651
            return false // library marker kkossev.deviceProfileLib, line 652
        } // library marker kkossev.deviceProfileLib, line 653
    } // library marker kkossev.deviceProfileLib, line 654
    else { // library marker kkossev.deviceProfileLib, line 655
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 656
        return false // library marker kkossev.deviceProfileLib, line 657
    } // library marker kkossev.deviceProfileLib, line 658
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 659
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 660
    return true // library marker kkossev.deviceProfileLib, line 661
} // library marker kkossev.deviceProfileLib, line 662

/** // library marker kkossev.deviceProfileLib, line 664
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 665
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 666
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 667
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 668
 */ // library marker kkossev.deviceProfileLib, line 669
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 670
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 671
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 672
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 673
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 674
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 675
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 676
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 677
        return false // library marker kkossev.deviceProfileLib, line 678
    } // library marker kkossev.deviceProfileLib, line 679
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 680
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 681
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 682
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 683
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 684
        return false // library marker kkossev.deviceProfileLib, line 685
    } // library marker kkossev.deviceProfileLib, line 686
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 687
    def func, funcResult // library marker kkossev.deviceProfileLib, line 688
    try { // library marker kkossev.deviceProfileLib, line 689
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 690
        if (val != null) { // library marker kkossev.deviceProfileLib, line 691
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 692
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694
        else { // library marker kkossev.deviceProfileLib, line 695
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 696
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 697
        } // library marker kkossev.deviceProfileLib, line 698
    } // library marker kkossev.deviceProfileLib, line 699
    catch (e) { // library marker kkossev.deviceProfileLib, line 700
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 701
        return false // library marker kkossev.deviceProfileLib, line 702
    } // library marker kkossev.deviceProfileLib, line 703
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 704
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 705
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 706
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 707
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 708
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 709
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 710
        } // library marker kkossev.deviceProfileLib, line 711
    } else { // library marker kkossev.deviceProfileLib, line 712
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 713
        return false // library marker kkossev.deviceProfileLib, line 714
    } // library marker kkossev.deviceProfileLib, line 715
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 716
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 717
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 718
    } // library marker kkossev.deviceProfileLib, line 719
    return true // library marker kkossev.deviceProfileLib, line 720
} // library marker kkossev.deviceProfileLib, line 721

/** // library marker kkossev.deviceProfileLib, line 723
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 724
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 725
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 726
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 727
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 728
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 729
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 730
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 731
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 732
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 733
 */ // library marker kkossev.deviceProfileLib, line 734
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 735
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 736
    Map input = [:] // library marker kkossev.deviceProfileLib, line 737
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 738
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 739
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 740
        return [:] // library marker kkossev.deviceProfileLib, line 741
    } // library marker kkossev.deviceProfileLib, line 742
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 743
    def preference // library marker kkossev.deviceProfileLib, line 744
    try { // library marker kkossev.deviceProfileLib, line 745
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 746
    } // library marker kkossev.deviceProfileLib, line 747
    catch (e) { // library marker kkossev.deviceProfileLib, line 748
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 749
        return [:] // library marker kkossev.deviceProfileLib, line 750
    } // library marker kkossev.deviceProfileLib, line 751
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 752
    try { // library marker kkossev.deviceProfileLib, line 753
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 754
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 755
            return [:] // library marker kkossev.deviceProfileLib, line 756
        } // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    catch (e) { // library marker kkossev.deviceProfileLib, line 759
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 760
        return [:] // library marker kkossev.deviceProfileLib, line 761
    } // library marker kkossev.deviceProfileLib, line 762

    try { // library marker kkossev.deviceProfileLib, line 764
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 765
    } // library marker kkossev.deviceProfileLib, line 766
    catch (e) { // library marker kkossev.deviceProfileLib, line 767
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 768
        return [:] // library marker kkossev.deviceProfileLib, line 769
    } // library marker kkossev.deviceProfileLib, line 770

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 772
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 773
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 774
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 775
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 776
        return [:] // library marker kkossev.deviceProfileLib, line 777
    } // library marker kkossev.deviceProfileLib, line 778
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 779
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 780
        return [:] // library marker kkossev.deviceProfileLib, line 781
    } // library marker kkossev.deviceProfileLib, line 782
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 783
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 784
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 785
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 786
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 787
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 788
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 789
        } // library marker kkossev.deviceProfileLib, line 790
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 791
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 792
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 793
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 794
            } // library marker kkossev.deviceProfileLib, line 795
        } // library marker kkossev.deviceProfileLib, line 796
    } // library marker kkossev.deviceProfileLib, line 797
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 798
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 799
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 800
    }/* // library marker kkossev.deviceProfileLib, line 801
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 802
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 803
    }*/ // library marker kkossev.deviceProfileLib, line 804
    else { // library marker kkossev.deviceProfileLib, line 805
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 806
        return [:] // library marker kkossev.deviceProfileLib, line 807
    } // library marker kkossev.deviceProfileLib, line 808
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 809
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 810
    } // library marker kkossev.deviceProfileLib, line 811
    return input // library marker kkossev.deviceProfileLib, line 812
} // library marker kkossev.deviceProfileLib, line 813

/** // library marker kkossev.deviceProfileLib, line 815
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 816
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 817
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 818
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 819
 */ // library marker kkossev.deviceProfileLib, line 820
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 821
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 822
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 823
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 824
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 825
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 826
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 827
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 828
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 829
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 830
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 831
            } // library marker kkossev.deviceProfileLib, line 832
        } // library marker kkossev.deviceProfileLib, line 833
    } // library marker kkossev.deviceProfileLib, line 834
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 835
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 836
    } // library marker kkossev.deviceProfileLib, line 837
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 838
} // library marker kkossev.deviceProfileLib, line 839

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 841
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 842
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 843
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 844
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 845
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 846
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 847
    } // library marker kkossev.deviceProfileLib, line 848
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 850
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 851
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 852
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 853
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 854
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 855
    } else { // library marker kkossev.deviceProfileLib, line 856
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 857
    } // library marker kkossev.deviceProfileLib, line 858
} // library marker kkossev.deviceProfileLib, line 859

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 861
List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 862
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 863
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 864
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 865
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 866
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 867
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 868
            if (k != null) { // library marker kkossev.deviceProfileLib, line 869
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 870
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 871
                if (map != null) { // library marker kkossev.deviceProfileLib, line 872
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 873
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 874
                } // library marker kkossev.deviceProfileLib, line 875
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 876
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 877
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 878
                } // library marker kkossev.deviceProfileLib, line 879
            } // library marker kkossev.deviceProfileLib, line 880
        } // library marker kkossev.deviceProfileLib, line 881
    } // library marker kkossev.deviceProfileLib, line 882
    return cmds // library marker kkossev.deviceProfileLib, line 883
} // library marker kkossev.deviceProfileLib, line 884

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 886
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 887
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 888
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 889
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 890
    return cmds // library marker kkossev.deviceProfileLib, line 891
} // library marker kkossev.deviceProfileLib, line 892

// TODO ! // library marker kkossev.deviceProfileLib, line 894
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 895
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 896
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 897
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 898
    return cmds // library marker kkossev.deviceProfileLib, line 899
} // library marker kkossev.deviceProfileLib, line 900

// TODO // library marker kkossev.deviceProfileLib, line 902
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 903
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 904
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 905
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 906
    return cmds // library marker kkossev.deviceProfileLib, line 907
} // library marker kkossev.deviceProfileLib, line 908

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 910
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 911
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 912
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 913
    } // library marker kkossev.deviceProfileLib, line 914
} // library marker kkossev.deviceProfileLib, line 915

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 917
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 918
} // library marker kkossev.deviceProfileLib, line 919

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 921

// // library marker kkossev.deviceProfileLib, line 923
// called from parse() // library marker kkossev.deviceProfileLib, line 924
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 925
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 926
// // library marker kkossev.deviceProfileLib, line 927
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 928
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 929
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 930
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 931
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 932
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 933
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 934
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 935
} // library marker kkossev.deviceProfileLib, line 936

// // library marker kkossev.deviceProfileLib, line 938
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 939
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 940
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 941
// // library marker kkossev.deviceProfileLib, line 942
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 943
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 944
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 945
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 946
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 947
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 948
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 949
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 950
} // library marker kkossev.deviceProfileLib, line 951

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 953
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 954
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 955
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 956
    return isSpammy // library marker kkossev.deviceProfileLib, line 957
} // library marker kkossev.deviceProfileLib, line 958

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 960
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 961
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 962
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 963
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 964
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 965
    } // library marker kkossev.deviceProfileLib, line 966
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 967
} // library marker kkossev.deviceProfileLib, line 968

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 970
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 971
    boolean isEqual // library marker kkossev.deviceProfileLib, line 972
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 973
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 974
    } // library marker kkossev.deviceProfileLib, line 975
    else { // library marker kkossev.deviceProfileLib, line 976
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 977
    } // library marker kkossev.deviceProfileLib, line 978
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 979
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 980
} // library marker kkossev.deviceProfileLib, line 981

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 983
    Double convertedValue // library marker kkossev.deviceProfileLib, line 984
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 985
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 986
    } // library marker kkossev.deviceProfileLib, line 987
    else { // library marker kkossev.deviceProfileLib, line 988
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 989
    } // library marker kkossev.deviceProfileLib, line 990
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 991
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 992
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 993
} // library marker kkossev.deviceProfileLib, line 994

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 996
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 997
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 998
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 999
    def convertedValue // library marker kkossev.deviceProfileLib, line 1000
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1001
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1002
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1003
    } // library marker kkossev.deviceProfileLib, line 1004
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1005
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1006
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1007
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1008
            isEqual = false // library marker kkossev.deviceProfileLib, line 1009
        } // library marker kkossev.deviceProfileLib, line 1010
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1011
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1012
        } // library marker kkossev.deviceProfileLib, line 1013
    } // library marker kkossev.deviceProfileLib, line 1014
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1015
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1016
} // library marker kkossev.deviceProfileLib, line 1017

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1019
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1020
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1021
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1022
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1023
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1024
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1025
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1026
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1027
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1028
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1029
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1030
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1031
            break // library marker kkossev.deviceProfileLib, line 1032
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1033
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1034
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1035
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1036
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1037
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1038
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1039
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1040
            } // library marker kkossev.deviceProfileLib, line 1041
            else { // library marker kkossev.deviceProfileLib, line 1042
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1043
            } // library marker kkossev.deviceProfileLib, line 1044
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1045
            break // library marker kkossev.deviceProfileLib, line 1046
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1047
        case 'number' : // library marker kkossev.deviceProfileLib, line 1048
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1049
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1050
            break // library marker kkossev.deviceProfileLib, line 1051
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1052
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1053
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1054
            break // library marker kkossev.deviceProfileLib, line 1055
        default : // library marker kkossev.deviceProfileLib, line 1056
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1057
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1058
    } // library marker kkossev.deviceProfileLib, line 1059
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1060
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1061
    } // library marker kkossev.deviceProfileLib, line 1062
    // // library marker kkossev.deviceProfileLib, line 1063
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1064
} // library marker kkossev.deviceProfileLib, line 1065

// // library marker kkossev.deviceProfileLib, line 1067
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1068
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1069
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1070
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1071
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1072
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1073
// // library marker kkossev.deviceProfileLib, line 1074
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1075
// // library marker kkossev.deviceProfileLib, line 1076
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1077
// // library marker kkossev.deviceProfileLib, line 1078
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1079
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1080
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1081
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1082
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1083
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1084
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1085
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1086
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1087
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1088
            break // library marker kkossev.deviceProfileLib, line 1089
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1090
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1091
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1092
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1093
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1094
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1095
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1096
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1097
            } // library marker kkossev.deviceProfileLib, line 1098
            else { // library marker kkossev.deviceProfileLib, line 1099
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1100
            } // library marker kkossev.deviceProfileLib, line 1101
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1102
            break // library marker kkossev.deviceProfileLib, line 1103
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1104
        case 'number' : // library marker kkossev.deviceProfileLib, line 1105
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1106
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1109
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1110
            break // library marker kkossev.deviceProfileLib, line 1111
        default : // library marker kkossev.deviceProfileLib, line 1112
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1113
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1114
    } // library marker kkossev.deviceProfileLib, line 1115
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1116
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1117
} // library marker kkossev.deviceProfileLib, line 1118

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1120
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1121
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1122
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1123
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1124
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1125
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1126
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1127
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1128
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1129
    } // library marker kkossev.deviceProfileLib, line 1130
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1131
    try { // library marker kkossev.deviceProfileLib, line 1132
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1133
    } // library marker kkossev.deviceProfileLib, line 1134
    catch (e) { // library marker kkossev.deviceProfileLib, line 1135
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1136
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1137
    } // library marker kkossev.deviceProfileLib, line 1138
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1139
    return fncmd // library marker kkossev.deviceProfileLib, line 1140
} // library marker kkossev.deviceProfileLib, line 1141

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1143
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1144
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1145
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1146
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1147
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1148
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1149

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1151
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1152

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1154
    int value // library marker kkossev.deviceProfileLib, line 1155
    try { // library marker kkossev.deviceProfileLib, line 1156
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1157
    } // library marker kkossev.deviceProfileLib, line 1158
    catch (e) { // library marker kkossev.deviceProfileLib, line 1159
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1160
        return false // library marker kkossev.deviceProfileLib, line 1161
    } // library marker kkossev.deviceProfileLib, line 1162
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1163
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1164
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1165
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1166
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1167
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1168
        return false // library marker kkossev.deviceProfileLib, line 1169
    } // library marker kkossev.deviceProfileLib, line 1170
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1171
} // library marker kkossev.deviceProfileLib, line 1172

/** // library marker kkossev.deviceProfileLib, line 1174
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1175
 * // library marker kkossev.deviceProfileLib, line 1176
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1177
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1178
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1179
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1180
 * // library marker kkossev.deviceProfileLib, line 1181
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1182
 */ // library marker kkossev.deviceProfileLib, line 1183
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1184
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1185
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1186
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1187
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1188

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1190
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1191

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1193
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1194
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1195
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1196
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1197
        return false // library marker kkossev.deviceProfileLib, line 1198
    } // library marker kkossev.deviceProfileLib, line 1199
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1200
} // library marker kkossev.deviceProfileLib, line 1201

/* // library marker kkossev.deviceProfileLib, line 1203
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1204
 */ // library marker kkossev.deviceProfileLib, line 1205
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1206
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1207
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1208
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1209
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1210
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1211
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1212
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1213
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1214
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1215
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1216
        } // library marker kkossev.deviceProfileLib, line 1217
    } // library marker kkossev.deviceProfileLib, line 1218
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1219

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1221
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1222
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1223
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1224
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1225
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1226
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1227
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1228
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1229
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1230
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1231
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1232
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1233
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1234
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1235
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1236
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1237

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1239
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1240
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1241
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1242
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1243
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1244
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1245
        } // library marker kkossev.deviceProfileLib, line 1246
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1247
    } // library marker kkossev.deviceProfileLib, line 1248

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1250
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1251
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1252
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1253
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1254
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1255
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1256
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1257
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1258
            } // library marker kkossev.deviceProfileLib, line 1259
        } // library marker kkossev.deviceProfileLib, line 1260
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1261
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1262
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1263
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1264
            } // library marker kkossev.deviceProfileLib, line 1265
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1266
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1267
            try { // library marker kkossev.deviceProfileLib, line 1268
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1269
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1270
            } // library marker kkossev.deviceProfileLib, line 1271
            catch (e) { // library marker kkossev.deviceProfileLib, line 1272
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1273
            } // library marker kkossev.deviceProfileLib, line 1274
        } // library marker kkossev.deviceProfileLib, line 1275
    } // library marker kkossev.deviceProfileLib, line 1276
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1277
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1278
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1279
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1280
    } // library marker kkossev.deviceProfileLib, line 1281

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1283
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1284
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1285
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1286
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1287
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1288
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1289
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1290
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1291
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1292
            } // library marker kkossev.deviceProfileLib, line 1293

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1295
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1296
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1297
            // continue ... // library marker kkossev.deviceProfileLib, line 1298
            } // library marker kkossev.deviceProfileLib, line 1299

            else { // library marker kkossev.deviceProfileLib, line 1301
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1302
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1303
                } // library marker kkossev.deviceProfileLib, line 1304
                else { // library marker kkossev.deviceProfileLib, line 1305
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1306
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1307
                } // library marker kkossev.deviceProfileLib, line 1308
            } // library marker kkossev.deviceProfileLib, line 1309
        } // library marker kkossev.deviceProfileLib, line 1310

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1312
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1313
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1314
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1315
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1316
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1317
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1318
        } // library marker kkossev.deviceProfileLib, line 1319
        else { // library marker kkossev.deviceProfileLib, line 1320
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1321
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1322
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1323
                logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1324
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1325
            } // library marker kkossev.deviceProfileLib, line 1326
        } // library marker kkossev.deviceProfileLib, line 1327
    } // library marker kkossev.deviceProfileLib, line 1328
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1329
} // library marker kkossev.deviceProfileLib, line 1330

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1332
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1333
    //debug = true // library marker kkossev.deviceProfileLib, line 1334
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1335
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1336
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1337
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1338
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1339
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1340
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1341
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1342
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1343
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1344
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1345
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1346
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1347
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1348
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1349
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1350
            try { // library marker kkossev.deviceProfileLib, line 1351
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1352
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1353
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1354
            } // library marker kkossev.deviceProfileLib, line 1355
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1356
            try { // library marker kkossev.deviceProfileLib, line 1357
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1358
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1359
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1360
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1361
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1362
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1363
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1364
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1365
                    } // library marker kkossev.deviceProfileLib, line 1366
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1367
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1368
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1369
                    } else { // library marker kkossev.deviceProfileLib, line 1370
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1371
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1372
                    } // library marker kkossev.deviceProfileLib, line 1373
                } // library marker kkossev.deviceProfileLib, line 1374
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1375
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1376
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1377
            } // library marker kkossev.deviceProfileLib, line 1378
            catch (e) { // library marker kkossev.deviceProfileLib, line 1379
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1380
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1381
                try { // library marker kkossev.deviceProfileLib, line 1382
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1383
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1384
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1385
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1386
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1387
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1388
                } // library marker kkossev.deviceProfileLib, line 1389
            } // library marker kkossev.deviceProfileLib, line 1390
        } // library marker kkossev.deviceProfileLib, line 1391
        total ++ // library marker kkossev.deviceProfileLib, line 1392
    } // library marker kkossev.deviceProfileLib, line 1393
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1394
    return true // library marker kkossev.deviceProfileLib, line 1395
} // library marker kkossev.deviceProfileLib, line 1396

// command for debugging // library marker kkossev.deviceProfileLib, line 1398
public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1399
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1400
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1401
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1402
        } // library marker kkossev.deviceProfileLib, line 1403
    } // library marker kkossev.deviceProfileLib, line 1404
} // library marker kkossev.deviceProfileLib, line 1405

// command for debugging // library marker kkossev.deviceProfileLib, line 1407
public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1408
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1409
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1410
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1411
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1412
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1413
                log.trace inputMap // library marker kkossev.deviceProfileLib, line 1414
            } // library marker kkossev.deviceProfileLib, line 1415
        } // library marker kkossev.deviceProfileLib, line 1416
    } // library marker kkossev.deviceProfileLib, line 1417
} // library marker kkossev.deviceProfileLib, line 1418

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.0' // library marker kkossev.onOffLib, line 5
) // library marker kkossev.onOffLib, line 6
/* // library marker kkossev.onOffLib, line 7
 *  Zigbee OnOff Cluster Library // library marker kkossev.onOffLib, line 8
 * // library marker kkossev.onOffLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.onOffLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.onOffLib, line 11
 * // library marker kkossev.onOffLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.onOffLib, line 13
 * // library marker kkossev.onOffLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.onOffLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.onOffLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.onOffLib, line 17
 * // library marker kkossev.onOffLib, line 18
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.0 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * // library marker kkossev.onOffLib, line 20
 *                                   TODO: // library marker kkossev.onOffLib, line 21
*/ // library marker kkossev.onOffLib, line 22

static String onOffLibVersion()   { '3.2.0' } // library marker kkossev.onOffLib, line 24
static String onOffLibStamp() { '2024/06/04 1:54 PM' } // library marker kkossev.onOffLib, line 25

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 27

metadata { // library marker kkossev.onOffLib, line 29
    capability 'Actuator' // library marker kkossev.onOffLib, line 30
    capability 'Switch' // library marker kkossev.onOffLib, line 31
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 32
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 33
    } // library marker kkossev.onOffLib, line 34
    // no commands // library marker kkossev.onOffLib, line 35
    preferences { // library marker kkossev.onOffLib, line 36
        if (settings?.advancedOptions == true && device != null && DEVICE_TYPE != 'Device') { // library marker kkossev.onOffLib, line 37
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 38
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching OFF for plugs that must be always On', defaultValue: false) // library marker kkossev.onOffLib, line 39
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 40
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 41
            } // library marker kkossev.onOffLib, line 42
        } // library marker kkossev.onOffLib, line 43
    } // library marker kkossev.onOffLib, line 44
} // library marker kkossev.onOffLib, line 45

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 47
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 48
] // library marker kkossev.onOffLib, line 49

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 51
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 52
] // library marker kkossev.onOffLib, line 53

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 55
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 56
] // library marker kkossev.onOffLib, line 57

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 59

/* // library marker kkossev.onOffLib, line 61
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 62
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
*/ // library marker kkossev.onOffLib, line 65
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 66
    /* // library marker kkossev.onOffLib, line 67
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 68
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 69
    } // library marker kkossev.onOffLib, line 70
    else */ // library marker kkossev.onOffLib, line 71
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 72
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 73
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 74
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 75
    } // library marker kkossev.onOffLib, line 76
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 77
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 78
    } // library marker kkossev.onOffLib, line 79
    else { // library marker kkossev.onOffLib, line 80
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 81
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 82
    } // library marker kkossev.onOffLib, line 83
} // library marker kkossev.onOffLib, line 84

void toggle() { // library marker kkossev.onOffLib, line 86
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 87
    String state = '' // library marker kkossev.onOffLib, line 88
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 89
        state = 'on' // library marker kkossev.onOffLib, line 90
    } // library marker kkossev.onOffLib, line 91
    else { // library marker kkossev.onOffLib, line 92
        state = 'off' // library marker kkossev.onOffLib, line 93
    } // library marker kkossev.onOffLib, line 94
    descriptionText += state // library marker kkossev.onOffLib, line 95
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 96
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 97
} // library marker kkossev.onOffLib, line 98

void off() { // library marker kkossev.onOffLib, line 100
    if (this.respondsTo('customOff')) { // library marker kkossev.onOffLib, line 101
        customOff() // library marker kkossev.onOffLib, line 102
        return // library marker kkossev.onOffLib, line 103
    } // library marker kkossev.onOffLib, line 104
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.onOffLib, line 105
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.onOffLib, line 106
        return // library marker kkossev.onOffLib, line 107
    } // library marker kkossev.onOffLib, line 108
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 109
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 110
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 111
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 112
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 113
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 114
        } // library marker kkossev.onOffLib, line 115
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 116
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 117
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 118
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 119
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 120
    } // library marker kkossev.onOffLib, line 121
    /* // library marker kkossev.onOffLib, line 122
    else { // library marker kkossev.onOffLib, line 123
        if (currentState != 'off') { // library marker kkossev.onOffLib, line 124
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.onOffLib, line 125
        } // library marker kkossev.onOffLib, line 126
        else { // library marker kkossev.onOffLib, line 127
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.onOffLib, line 128
            return // library marker kkossev.onOffLib, line 129
        } // library marker kkossev.onOffLib, line 130
    } // library marker kkossev.onOffLib, line 131
    */ // library marker kkossev.onOffLib, line 132

    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 134
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 135
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 136
} // library marker kkossev.onOffLib, line 137

void on() { // library marker kkossev.onOffLib, line 139
    if (this.respondsTo('customOn')) { // library marker kkossev.onOffLib, line 140
        customOn() // library marker kkossev.onOffLib, line 141
        return // library marker kkossev.onOffLib, line 142
    } // library marker kkossev.onOffLib, line 143
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 144
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 145
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 146
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 147
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 148
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 149
        } // library marker kkossev.onOffLib, line 150
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 151
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 152
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 153
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 154
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 155
    } // library marker kkossev.onOffLib, line 156
    /* // library marker kkossev.onOffLib, line 157
    else { // library marker kkossev.onOffLib, line 158
        if (currentState != 'on') { // library marker kkossev.onOffLib, line 159
            logDebug "Switching ${device.displayName} On" // library marker kkossev.onOffLib, line 160
        } // library marker kkossev.onOffLib, line 161
        else { // library marker kkossev.onOffLib, line 162
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.onOffLib, line 163
            return // library marker kkossev.onOffLib, line 164
        } // library marker kkossev.onOffLib, line 165
    } // library marker kkossev.onOffLib, line 166
    */ // library marker kkossev.onOffLib, line 167
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 168
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 169
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 170
} // library marker kkossev.onOffLib, line 171

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 173
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 174
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 175
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 176
    } // library marker kkossev.onOffLib, line 177
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 178
    Map map = [:] // library marker kkossev.onOffLib, line 179
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 180
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 181
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 182
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 183
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 184
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 185
        return // library marker kkossev.onOffLib, line 186
    } // library marker kkossev.onOffLib, line 187
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 188
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 189
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 190
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 191
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 192
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 193
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 194
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 195
    } else { // library marker kkossev.onOffLib, line 196
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 197
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 198
    } // library marker kkossev.onOffLib, line 199
    map.name = 'switch' // library marker kkossev.onOffLib, line 200
    map.value = value // library marker kkossev.onOffLib, line 201
    if (isRefresh) { // library marker kkossev.onOffLib, line 202
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 203
        map.isStateChange = true // library marker kkossev.onOffLib, line 204
    } else { // library marker kkossev.onOffLib, line 205
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 206
    } // library marker kkossev.onOffLib, line 207
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 208
    sendEvent(map) // library marker kkossev.onOffLib, line 209
    clearIsDigital() // library marker kkossev.onOffLib, line 210
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 211
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 212
    } // library marker kkossev.onOffLib, line 213
} // library marker kkossev.onOffLib, line 214

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 216
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 217
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 218
    String mode // library marker kkossev.onOffLib, line 219
    String attrName // library marker kkossev.onOffLib, line 220
    if (it.value == null) { // library marker kkossev.onOffLib, line 221
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 222
        return // library marker kkossev.onOffLib, line 223
    } // library marker kkossev.onOffLib, line 224
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 225
    switch (it.attrId) { // library marker kkossev.onOffLib, line 226
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 227
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 228
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 229
            break // library marker kkossev.onOffLib, line 230
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 231
            attrName = 'On Time' // library marker kkossev.onOffLib, line 232
            mode = value // library marker kkossev.onOffLib, line 233
            break // library marker kkossev.onOffLib, line 234
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 235
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 236
            mode = value // library marker kkossev.onOffLib, line 237
            break // library marker kkossev.onOffLib, line 238
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 239
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 240
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 241
            break // library marker kkossev.onOffLib, line 242
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 243
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 244
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 245
            break // library marker kkossev.onOffLib, line 246
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 247
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 248
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 249
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 250
            } // library marker kkossev.onOffLib, line 251
            else { // library marker kkossev.onOffLib, line 252
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 253
            } // library marker kkossev.onOffLib, line 254
            break // library marker kkossev.onOffLib, line 255
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 256
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 257
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 258
            break // library marker kkossev.onOffLib, line 259
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 260
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 261
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 262
            break // library marker kkossev.onOffLib, line 263
        default : // library marker kkossev.onOffLib, line 264
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 265
            return // library marker kkossev.onOffLib, line 266
    } // library marker kkossev.onOffLib, line 267
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 268
} // library marker kkossev.onOffLib, line 269

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 271
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 272
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 273
    return cmds // library marker kkossev.onOffLib, line 274
} // library marker kkossev.onOffLib, line 275

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 277
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 278
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 279
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 280
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 281
} // library marker kkossev.onOffLib, line 282

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (177) kkossev.reportingLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.reportingLib, line 1
library( // library marker kkossev.reportingLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Reporting Config Library', name: 'reportingLib', namespace: 'kkossev', // library marker kkossev.reportingLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/reportingLib.groovy', documentationLink: '', // library marker kkossev.reportingLib, line 4
    version: '3.0.0' // library marker kkossev.reportingLib, line 5

) // library marker kkossev.reportingLib, line 7
/* // library marker kkossev.reportingLib, line 8
 *  Zigbee Reporting Config Library // library marker kkossev.reportingLib, line 9
 * // library marker kkossev.reportingLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.reportingLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.reportingLib, line 12
 * // library marker kkossev.reportingLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.reportingLib, line 14
 * // library marker kkossev.reportingLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.reportingLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.reportingLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.reportingLib, line 18
 * // library marker kkossev.reportingLib, line 19
 * ver. 3.2.0  2024-05-25 kkossev  - added reportingLib.groovy // library marker kkossev.reportingLib, line 20
 * // library marker kkossev.reportingLib, line 21
 *                                   TODO: // library marker kkossev.reportingLib, line 22
*/ // library marker kkossev.reportingLib, line 23

static String reportingLibVersion()   { '3.2.0' } // library marker kkossev.reportingLib, line 25
static String reportingLibStamp() { '2024/05/25 7:27 AM' } // library marker kkossev.reportingLib, line 26

metadata { // library marker kkossev.reportingLib, line 28
    // no capabilities // library marker kkossev.reportingLib, line 29
    // no attributes // library marker kkossev.reportingLib, line 30
    // no commands // library marker kkossev.reportingLib, line 31
    preferences { // library marker kkossev.reportingLib, line 32
        // no prefrences // library marker kkossev.reportingLib, line 33
    } // library marker kkossev.reportingLib, line 34
} // library marker kkossev.reportingLib, line 35

@Field static final String ONOFF = 'Switch' // library marker kkossev.reportingLib, line 37
@Field static final String POWER = 'Power' // library marker kkossev.reportingLib, line 38
@Field static final String INST_POWER = 'InstPower' // library marker kkossev.reportingLib, line 39
@Field static final String ENERGY = 'Energy' // library marker kkossev.reportingLib, line 40
@Field static final String VOLTAGE = 'Voltage' // library marker kkossev.reportingLib, line 41
@Field static final String AMPERAGE = 'Amperage' // library marker kkossev.reportingLib, line 42
@Field static final String FREQUENCY = 'Frequency' // library marker kkossev.reportingLib, line 43
@Field static final String POWER_FACTOR = 'PowerFactor' // library marker kkossev.reportingLib, line 44

List<String> configureReporting(String operation, String measurement,  String minTime='0', String maxTime='0', String delta='0', boolean sendNow=true ) { // library marker kkossev.reportingLib, line 46
    int intMinTime = safeToInt(minTime) // library marker kkossev.reportingLib, line 47
    int intMaxTime = safeToInt(maxTime) // library marker kkossev.reportingLib, line 48
    int intDelta = safeToInt(delta) // library marker kkossev.reportingLib, line 49
    String epString = state.destinationEP // library marker kkossev.reportingLib, line 50
    int ep = safeToInt(epString) // library marker kkossev.reportingLib, line 51
    if (ep == null || ep == 0) { // library marker kkossev.reportingLib, line 52
        ep = 1 // library marker kkossev.reportingLib, line 53
        epString = '01' // library marker kkossev.reportingLib, line 54
    } // library marker kkossev.reportingLib, line 55

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )" // library marker kkossev.reportingLib, line 57

    List<String> cmds = [] // library marker kkossev.reportingLib, line 59

    switch (measurement) { // library marker kkossev.reportingLib, line 61
        case ONOFF : // library marker kkossev.reportingLib, line 62
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 63
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 64
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 65
            } // library marker kkossev.reportingLib, line 66
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 67
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", 'delay 251', ]    // disable Plug automatic reporting // library marker kkossev.reportingLib, line 68
            } // library marker kkossev.reportingLib, line 69
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back // library marker kkossev.reportingLib, line 70
            break // library marker kkossev.reportingLib, line 71
        case ENERGY :    // default delta = 1 Wh (0.001 kWh) // library marker kkossev.reportingLib, line 72
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 73
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta * getEnergyDiv() as int)) // library marker kkossev.reportingLib, line 74
            } // library marker kkossev.reportingLib, line 75
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 76
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 77
            } // library marker kkossev.reportingLib, line 78
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252) // library marker kkossev.reportingLib, line 79
            break // library marker kkossev.reportingLib, line 80
        case INST_POWER :        // 0x702:0x400 // library marker kkossev.reportingLib, line 81
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 82
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int)) // library marker kkossev.reportingLib, line 83
            } // library marker kkossev.reportingLib, line 84
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 85
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 86
            } // library marker kkossev.reportingLib, line 87
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253) // library marker kkossev.reportingLib, line 88
            break // library marker kkossev.reportingLib, line 89
        case POWER :        // Active power default delta = 1 // library marker kkossev.reportingLib, line 90
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 91
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee // library marker kkossev.reportingLib, line 92
            } // library marker kkossev.reportingLib, line 93
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 94
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 95
            } // library marker kkossev.reportingLib, line 96
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254) // library marker kkossev.reportingLib, line 97
            break // library marker kkossev.reportingLib, line 98
        case VOLTAGE :    // RMS Voltage default delta = 1 // library marker kkossev.reportingLib, line 99
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 100
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getVoltageDiv() as int)) // library marker kkossev.reportingLib, line 101
            } // library marker kkossev.reportingLib, line 102
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 103
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 104
            } // library marker kkossev.reportingLib, line 105
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255) // library marker kkossev.reportingLib, line 106
            break // library marker kkossev.reportingLib, line 107
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A // library marker kkossev.reportingLib, line 108
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 109
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getCurrentDiv() as int)) // library marker kkossev.reportingLib, line 110
            } // library marker kkossev.reportingLib, line 111
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 112
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 113
            } // library marker kkossev.reportingLib, line 114
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256) // library marker kkossev.reportingLib, line 115
            break // library marker kkossev.reportingLib, line 116
        case FREQUENCY :    // added 03/27/2023 // library marker kkossev.reportingLib, line 117
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 118
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getFrequencyDiv() as int)) // library marker kkossev.reportingLib, line 119
            } // library marker kkossev.reportingLib, line 120
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 121
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 122
            } // library marker kkossev.reportingLib, line 123
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257) // library marker kkossev.reportingLib, line 124
            break // library marker kkossev.reportingLib, line 125
        case POWER_FACTOR : // added 03/27/2023 // library marker kkossev.reportingLib, line 126
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 127
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getPowerFactorDiv() as int)) // library marker kkossev.reportingLib, line 128
            } // library marker kkossev.reportingLib, line 129
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258) // library marker kkossev.reportingLib, line 130
            break // library marker kkossev.reportingLib, line 131
        default : // library marker kkossev.reportingLib, line 132
            break // library marker kkossev.reportingLib, line 133
    } // library marker kkossev.reportingLib, line 134
    if (cmds != null) { // library marker kkossev.reportingLib, line 135
        if (sendNow == true) { // library marker kkossev.reportingLib, line 136
            sendZigbeeCommands(cmds) // library marker kkossev.reportingLib, line 137
        } // library marker kkossev.reportingLib, line 138
        else { // library marker kkossev.reportingLib, line 139
            return cmds // library marker kkossev.reportingLib, line 140
        } // library marker kkossev.reportingLib, line 141
    } // library marker kkossev.reportingLib, line 142
} // library marker kkossev.reportingLib, line 143


// ~~~~~ end include (177) kkossev.reportingLib ~~~~~

// ~~~~~ start include (169) kkossev.groupsLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.groupsLib, line 1
library( // library marker kkossev.groupsLib, line 2
    base: 'driver', // library marker kkossev.groupsLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.groupsLib, line 4
    category: 'zigbee', // library marker kkossev.groupsLib, line 5
    description: 'Zigbee Groups Library', // library marker kkossev.groupsLib, line 6
    name: 'groupsLib', // library marker kkossev.groupsLib, line 7
    namespace: 'kkossev', // library marker kkossev.groupsLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/groupsLib.groovy', // library marker kkossev.groupsLib, line 9
    version: '3.0.1', // library marker kkossev.groupsLib, line 10
    documentationLink: '' // library marker kkossev.groupsLib, line 11
) // library marker kkossev.groupsLib, line 12
/* // library marker kkossev.groupsLib, line 13
 *  Zigbee Groups Library // library marker kkossev.groupsLib, line 14
 * // library marker kkossev.groupsLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.groupsLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.groupsLib, line 17
 * // library marker kkossev.groupsLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.groupsLib, line 19
 * // library marker kkossev.groupsLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.groupsLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.groupsLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.groupsLib, line 23
 * // library marker kkossev.groupsLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added groupsLib.groovy // library marker kkossev.groupsLib, line 25
 * ver. 3.0.1  2024-04-14 kkossev  - groupsInitializeVars() groupsRefresh() // library marker kkossev.groupsLib, line 26
 * // library marker kkossev.groupsLib, line 27
 *                                   TODO: // library marker kkossev.groupsLib, line 28
*/ // library marker kkossev.groupsLib, line 29

static String groupsLibVersion()   { '3.0.1' } // library marker kkossev.groupsLib, line 31
static String groupsLibStamp() { '2024/04/15 7:09 AM' } // library marker kkossev.groupsLib, line 32

metadata { // library marker kkossev.groupsLib, line 34
    // no capabilities // library marker kkossev.groupsLib, line 35
    // no attributes // library marker kkossev.groupsLib, line 36
    command 'zigbeeGroups', [ // library marker kkossev.groupsLib, line 37
        [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.groupsLib, line 38
        [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.groupsLib, line 39
    ] // library marker kkossev.groupsLib, line 40

    preferences { // library marker kkossev.groupsLib, line 42
        // no prefrences // library marker kkossev.groupsLib, line 43
    } // library marker kkossev.groupsLib, line 44
} // library marker kkossev.groupsLib, line 45

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.groupsLib, line 47
    defaultValue: 0, // library marker kkossev.groupsLib, line 48
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.groupsLib, line 49
] // library marker kkossev.groupsLib, line 50
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.groupsLib, line 51
    defaultValue: 0, // library marker kkossev.groupsLib, line 52
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.groupsLib, line 53
] // library marker kkossev.groupsLib, line 54

/* // library marker kkossev.groupsLib, line 56
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 57
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.groupsLib, line 58
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 59
*/ // library marker kkossev.groupsLib, line 60
void customParseGroupsCluster(final Map descMap) { // library marker kkossev.groupsLib, line 61
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.groupsLib, line 62
    logDebug "customParseGroupsCluster: customParseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.groupsLib, line 63
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 64
    switch (descMap.command as Integer) { // library marker kkossev.groupsLib, line 65
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.groupsLib, line 66
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 67
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 68
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 69
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 70
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 71
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 72
                logWarn "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.groupsLib, line 73
            } // library marker kkossev.groupsLib, line 74
            else { // library marker kkossev.groupsLib, line 75
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.groupsLib, line 76
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.groupsLib, line 77
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.groupsLib, line 78
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 79
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.groupsLib, line 80
                        logDebug "customParseGroupsCluster: Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.groupsLib, line 81
                        return // library marker kkossev.groupsLib, line 82
                    } // library marker kkossev.groupsLib, line 83
                } // library marker kkossev.groupsLib, line 84
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.groupsLib, line 85
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.groupsLib, line 86
                state.zigbeeGroups['groups'].sort() // library marker kkossev.groupsLib, line 87
            } // library marker kkossev.groupsLib, line 88
            break // library marker kkossev.groupsLib, line 89
        case 0x01: // View group // library marker kkossev.groupsLib, line 90
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.groupsLib, line 91
            logDebug "customParseGroupsCluster: received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 92
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 93
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 94
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 95
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 96
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 97
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 98
                logWarn "customParseGroupsCluster: zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 99
            } // library marker kkossev.groupsLib, line 100
            else { // library marker kkossev.groupsLib, line 101
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 102
            } // library marker kkossev.groupsLib, line 103
            break // library marker kkossev.groupsLib, line 104
        case 0x02: // Get group membership // library marker kkossev.groupsLib, line 105
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 106
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 107
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.groupsLib, line 108
            final Set<String> groups = [] // library marker kkossev.groupsLib, line 109
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 110
                int pos = (i * 2) + 2 // library marker kkossev.groupsLib, line 111
                String group = data[pos + 1] + data[pos] // library marker kkossev.groupsLib, line 112
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.groupsLib, line 113
            } // library marker kkossev.groupsLib, line 114
            state.zigbeeGroups['groups'] = groups // library marker kkossev.groupsLib, line 115
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.groupsLib, line 116
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.groupsLib, line 117
            break // library marker kkossev.groupsLib, line 118
        case 0x03: // Remove group // library marker kkossev.groupsLib, line 119
            logInfo "customParseGroupsCluster: received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 120
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 121
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 122
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 123
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 124
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 125
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 126
                logWarn "customParseGroupsCluster: zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 127
            } // library marker kkossev.groupsLib, line 128
            else { // library marker kkossev.groupsLib, line 129
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 130
            } // library marker kkossev.groupsLib, line 131
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.groupsLib, line 132
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.groupsLib, line 133
            if (index >= 0) { // library marker kkossev.groupsLib, line 134
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.groupsLib, line 135
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.groupsLib, line 136
            } // library marker kkossev.groupsLib, line 137
            break // library marker kkossev.groupsLib, line 138
        case 0x04: //Remove all groups // library marker kkossev.groupsLib, line 139
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 140
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 141
            break // library marker kkossev.groupsLib, line 142
        case 0x05: // Add group if identifying // library marker kkossev.groupsLib, line 143
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.groupsLib, line 144
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 145
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 146
            break // library marker kkossev.groupsLib, line 147
        default: // library marker kkossev.groupsLib, line 148
            logWarn "customParseGroupsCluster: received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 149
            break // library marker kkossev.groupsLib, line 150
    } // library marker kkossev.groupsLib, line 151
} // library marker kkossev.groupsLib, line 152

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 154
List<String> addGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 155
    List<String> cmds = [] // library marker kkossev.groupsLib, line 156
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 157
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 158
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 159
        return [] // library marker kkossev.groupsLib, line 160
    } // library marker kkossev.groupsLib, line 161
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 162
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 163
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 164
    return cmds // library marker kkossev.groupsLib, line 165
} // library marker kkossev.groupsLib, line 166

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 168
List<String> viewGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 169
    List<String> cmds = [] // library marker kkossev.groupsLib, line 170
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 171
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 172
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 173
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 174
    return cmds // library marker kkossev.groupsLib, line 175
} // library marker kkossev.groupsLib, line 176

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 178
List<String> getGroupMembership(dummy) { // library marker kkossev.groupsLib, line 179
    List<String> cmds = [] // library marker kkossev.groupsLib, line 180
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.groupsLib, line 181
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 182
    return cmds // library marker kkossev.groupsLib, line 183
} // library marker kkossev.groupsLib, line 184

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 186
List<String> removeGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 187
    List<String> cmds = [] // library marker kkossev.groupsLib, line 188
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 189
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 190
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 191
        return [] // library marker kkossev.groupsLib, line 192
    } // library marker kkossev.groupsLib, line 193
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 194
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 195
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 196
    return cmds // library marker kkossev.groupsLib, line 197
} // library marker kkossev.groupsLib, line 198

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 200
List<String> removeAllGroups(groupNr) { // library marker kkossev.groupsLib, line 201
    List<String> cmds = [] // library marker kkossev.groupsLib, line 202
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 203
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 204
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 205
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 206
    return cmds // library marker kkossev.groupsLib, line 207
} // library marker kkossev.groupsLib, line 208

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 210
List<String> notImplementedGroups(groupNr) { // library marker kkossev.groupsLib, line 211
    List<String> cmds = [] // library marker kkossev.groupsLib, line 212
    //final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 213
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 214
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 215
    return cmds // library marker kkossev.groupsLib, line 216
} // library marker kkossev.groupsLib, line 217

@Field static final Map GroupCommandsMap = [ // library marker kkossev.groupsLib, line 219
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.groupsLib, line 220
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.groupsLib, line 221
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.groupsLib, line 222
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.groupsLib, line 223
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.groupsLib, line 224
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.groupsLib, line 225
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.groupsLib, line 226
] // library marker kkossev.groupsLib, line 227

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 229
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.groupsLib, line 230
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.groupsLib, line 231
    List<String> cmds = [] // library marker kkossev.groupsLib, line 232
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 233
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.groupsLib, line 234
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 235
    def value // library marker kkossev.groupsLib, line 236
    Boolean validated = false // library marker kkossev.groupsLib, line 237
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.groupsLib, line 238
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.groupsLib, line 239
        return // library marker kkossev.groupsLib, line 240
    } // library marker kkossev.groupsLib, line 241
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.groupsLib, line 242
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.groupsLib, line 243
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.groupsLib, line 244
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.groupsLib, line 245
        return // library marker kkossev.groupsLib, line 246
    } // library marker kkossev.groupsLib, line 247
    // // library marker kkossev.groupsLib, line 248
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 249
    def func // library marker kkossev.groupsLib, line 250
    try { // library marker kkossev.groupsLib, line 251
        func = GroupCommandsMap[command]?.function // library marker kkossev.groupsLib, line 252
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.groupsLib, line 253
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.groupsLib, line 254
        cmds = "$func"(value) // library marker kkossev.groupsLib, line 255
    } // library marker kkossev.groupsLib, line 256
    catch (e) { // library marker kkossev.groupsLib, line 257
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 258
        return // library marker kkossev.groupsLib, line 259
    } // library marker kkossev.groupsLib, line 260

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 262
    sendZigbeeCommands(cmds) // library marker kkossev.groupsLib, line 263
} // library marker kkossev.groupsLib, line 264

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 266
void groupCommandsHelp(val) { // library marker kkossev.groupsLib, line 267
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.groupsLib, line 268
} // library marker kkossev.groupsLib, line 269

List<String> groupsRefresh() { // library marker kkossev.groupsLib, line 271
    logDebug 'groupsRefresh()' // library marker kkossev.groupsLib, line 272
    return getGroupMembership(null) // library marker kkossev.groupsLib, line 273
} // library marker kkossev.groupsLib, line 274

void groupsInitializeVars(boolean fullInit = false) { // library marker kkossev.groupsLib, line 276
    logDebug "groupsInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.groupsLib, line 277
    if (fullInit || state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 278
} // library marker kkossev.groupsLib, line 279

// ~~~~~ end include (169) kkossev.groupsLib ~~~~~
