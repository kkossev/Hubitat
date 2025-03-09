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
 * ver. 3.2.2  2024-06-29 kkossev  - added on/off control for SWITCH_GENERIC_EF00_TUYA 'switch' dp;
 * ver. 3.3.0  2025-03-09 kkossev  - (dev.branch) healthCheck by pinging the device; added Sonoff ZBMINIR2; added 'ZBMINI-L' to a new SWITCH_SONOFF_GENERIC profile
 *
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { '3.3.0' }
static String timeStamp() { '2025/03/09 10:09 PM' }

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
        attribute 'networkIndicator', 'enum', ['Disabled', 'Enabled']
        attribute 'backLight', 'enum', ['Disabled', 'Enabled']
        attribute 'faultCode', 'number'
        attribute 'delayedPowerOnState', 'enum', ['Disabled', 'Enabled']
        attribute 'delayedPowerOnTime', 'number'

        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]

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
        //
        //input(name: "deviceNetworkId", type: "enum", title: "Router Device", description: "<small>Select a mains-powered device that you want to put in pairing mode.</small>", options: [ "0000":"👑 Hubitat Hub" ] + getDevices(), required: true)

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
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]]],     // also binds the cluster
            deviceJoinName: 'Generic Zigbee Switch (ZCL)'
    ],

    'SWITCH_GENERIC_EF00_TUYA' : [
            description   : 'Generic Tuya Switch (0xEF00)',
            models        : ['TS0601'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : ['powerOnBehavior':'0x0006:0x4003'],
            fingerprints  : [   // Tuya EF00 single gang switches
                [profileId:'0104', endpointId:'01', inClusters:'EF00', outClusters:'000A,0019', model:'Tuya', manufacturer:'Tuya', deviceJoinName: 'MOES dimmer/switch for TESTS (0xEF00)'],
                [profileId:'0104', model:'TS0601', manufacturer:'_TZE200_amp6tsvy', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', application:'42', deviceJoinName: 'Moes 1-Gang Switch / ZTS-EU1'],
                [profileId:'0104', model:'TS0601', manufacturer:'_TZE200_oisqyl4o', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', application:'42', deviceJoinName: 'No Neutral Push Button Light Switch 1 Gang'],
                [profileId:'0104', model:'TS0601', manufacturer:'_TZE200_wfxuhoea', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', application:'42', deviceJoinName: 'No Neutral Push Button Light Switch 1 Gang'],
                [profileId:'0104', model:'TS0601', manufacturer:'_TZE200_gbagoilo', endpointId:'01', inClusters:'0000,0004,0005,EF00', outClusters:'0019,000A', application:'46', deviceJoinName: 'OZ Smart Single Light Switch'],
            ],
            commands      : ['resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs:        [
                [dp:1,   name:'switch',  type:'enum', dt:'01',  rw: 'rw',  min:0,     max:1 ,   defVal:'0',  scale:1,  map:[0:'off', 1:'on'] ,   unit:'',  description:'switch']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', /*enum8*/ type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior'],
            configuration : [:],
            deviceJoinName: 'Generic Tuya Switch (0xEF00)'
    ],

    'SWITCH_SONOFF_GENERIC' : [
            description   : 'Sonoff Generic Switch',
            models        : ['ZBMINI-L', '01MINIZB', 'BASICZBR3'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003'],
            commands      : [resetStats:'resetStats', refresh:'refresh'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006,0007,0020,FC57,FCA0', outClusters:'0019', model:'ZBMINI-L', manufacturer:'SONOFF', deviceJoinName: 'SONOFF ZBMINI-L'],     // https://community.hubitat.com/t/sonoff-zigbee-switch-with-no-neutral-wire-zbmini-l/88629/14?u=kkossev
                [profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,1000", outClusters:"1000", model:"01MINIZB", manufacturer:"SONOFF", deviceJoinName: 'SONOFF 01MINIZB'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006', outClusters:'0000', model:'BASICZBR3', manufacturer:'SONOFF', deviceJoinName: 'SONOFF Relay BASICBR3'],
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0005,0006', outClusters:'0000', model:'SA-003-Zigbee', manufacturer:'eWeLink', deviceJoinName: 'eWeLink Relay SA-003-Zigbee'],
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior']
            ],
            refresh       : [ 'powerOnBehavior'],
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]], '0x0020':['unbind':true]],
            deviceJoinName: 'Sonoff Generic Switch'
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
            configuration : [
                '0x0006':['onOffReporting':[1, 1800, 0]],           // also binds the cluster
                '0xFC11':['read':['onOffRefresh','turboMode']]      // onOffRefresh is a method in onOffLib!
            ],
            deviceJoinName: 'Sonoff ZBMicro Zigbee USB Switch'
    ],

    'SWITCH_SONOFF_ZBMINIL2' : [
            description   : 'Sonoff ZBMini L2 Switch',
            models        : ['ZBMINIL2'],
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
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]], '0x0020':['unbind':true]],
            deviceJoinName: 'Sonoff ZBMini L2 Switch'
    ],

    'SWITCH_SONOFF_ZBMINIR2' : [        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/sonoff.ts#L1495-L1558
            description   : 'Sonoff ZBMINI R2 Switch',
            models        : ['ZBMINIR2'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003', turboMode:'0xFC11:0x0012', networkIndicator:'0xFC11:0x0001'/*, backLight:'0xFC11:0x0002', delayedPowerOnState:'0xFC11:0x0014', delayedPowerOnTime:'0xFC11:0x0015'*/, detachRelayMode:'0xFC11:0x0017'],
            commands      : [resetStats:'', refresh:'', initialize:'', updateAllPreferences: '',resetPreferencesToDefaults:'', validateAndFixPreferences:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0006,0B05,FC57,FC11', outClusters:'0003,0006,0019', model:'ZBMINIR2', manufacturer:'SONOFF', application:"10", deviceJoinName: 'SONOFF ZBMINI R2']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior',     type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255', map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior'],
                [at:'0xFC11:0x0001', name:'networkIndicator',    type:'enum',   dt:'0x29', /*mfgCode:'0x1286',*/    rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Network Indicator.</b>', description:'Enable/disable network indicator.'],                              // BOOLEAN
                [at:'0xFC11:0x0002', name:'backLight',           type:'enum',   dt:'0x29', mfgCode:'0x1286',    rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Backlight.</b>', description:'Enable/disable Backlight.'],                                              // BOOLEAN
                [at:'0xFC11:0x0010', name:'faultCode',           type:'number', rw: 'ro', title:'<b>Fault Code</b>' ],                                                                                                                                                                                                  // INT32
                [at:'0xFC11:0x0012', name:'turboMode',           type:'enum',   dt:'0x29', mfgCode:'0x1286',    rw: 'rw', min:9,   max:20,    defVal:'9',   scale:1,    map:[9:'Disabled', 20:'Enabled'], title:'<b>Zigbee Radio Power Turbo Mode.</b>', description:'Enable/disable Zigbee radio power Turbo mode.'],    // INT16
                [at:'0xFC11:0x0014', name:'delayedPowerOnState', type:'enum',   dt:'0x29', mfgCode:'0x1286',    rw: 'rw', min:9,   max:20,    defVal:'9',   scale:1,    map:[9:'Disabled', 20:'Enabled'], title:'<b>Delayed Power On State.</b>', description:'Enable/disable Delayed Power On State.'],                  // BOOLEAN
                [at:'0xFC11:0x0015', name:'delayedPowerOnTime',  type:'number', rw: 'rw', title:'<b>Delayed Power On Time</b>', description: 'Delayed Power On Time' ],         // UINT16
                [at:'0xFC11:0x0016', name:'externalTriggerMode', type:'number', rw: 'rw', title:'<b>External Trigger Mode</b>', description: 'External Trigger Mode' ],         // UINT8 //                 externalTriggerMode: {ID: 0x0016, type: Zcl.DataType.UINT8},
                [at:'0xFC11:0x0017', name:'detachRelayMode',     type:'enum',  advanced:true, dt:'0x29', rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Detach Relay Mode</b>', description: 'Enable/Disable detach relay mode' ],  // BOOLEAN //detachRelayMode: {ID: 0x0017, type: Zcl.DataType.BOOLEAN},
                // ZBM5
                // [at:'0xFC11:0x0018', name:'deviceWorkMode',      type:'number', rw: 'rw', title:'<b>Device Work Mode</b>', description: 'Device Work Mode' ],                   // UINT8 //deviceWorkMode: {ID: 0x0018, type: Zcl.DataType.UINT8},
                //[at:'0xFC11:0x0019', name:'detachRelayMode2',    type:'number', rw: 'rw', title:'<b>Detach Relay Mode 2</b>', description: 'Detach Relay Mode 2' ],             // BITMAP8 //detachRelayMode2: {ID: 0x0019, type: Zcl.DataType.BITMAP8},

            ],
            refresh       : [ 'powerOnBehavior', 'turboMode', 'networkIndicator', 'backLight', 'delayedPowerOnState', 'delayedPowerOnTime', 'detachRelayMode'],
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]]],     // also binds the cluster
            deviceJoinName: 'Sonoff ZBMINI R2 Switch'
    ],

    'SWITCH_IKEA_TRADFRI' : [
            description   : 'Ikea Tradfri control outlet',
            models        : ['TRADFRI control outlet'],
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
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]]],     // also binds the cluster
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
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]]],     // also binds the cluster
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
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]]],     // also binds the cluster
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

List<Integer> parseMinMaxDelta(String cluster, String attribute) {
    List<Integer> result = [1, 7200, 0]
    if (DEVICE?.configuration == null || DEVICE?.configuration.isEmpty()) {
        logDebug 'No custom configuration found'
        return result
    }
    if (cluster in DEVICE?.configuration) {
        if (DEVICE?.configuration[cluster][attribute] != null) {
            result[0] = safeToInt(DEVICE?.configuration[cluster][attribute][0])
            result[1] = safeToInt(DEVICE?.configuration[cluster][attribute][1])
            result[2] = safeToInt(DEVICE?.configuration[cluster][attribute][2])
        }
    }
    return result
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (DEVICE?.device?.isDepricated == true) {
        logWarn 'The use of this driver with this device is depricated. Please update to the new driver!'
        return cmds
    }
    if (DEVICE?.configuration == null || DEVICE?.configuration.isEmpty()) {
        logDebug 'No custom configuration found'
        return cmds
    }
    int intMinTime = safeToInt(1)
    int intMaxTime = safeToInt(7200)
    if ('0x0006' in DEVICE?.configuration) {
        if (DEVICE?.configuration['0x0006']['bind'] == true) {
            cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 229', ]
            logDebug "binding the device to the OnOff cluster"
        }
        // '0x0006':['onOffReporting':[1, 1800, 0]],
        if (DEVICE?.configuration['0x0006']['onOffReporting'] != null) {
            (intMinTime, intMaxTime, delta) = parseMinMaxDelta('0x0006', 'onOffReporting')
            cmds += configureReportingInt('Write', ONOFF, intMinTime, intMaxTime, delta, sendNow = false)    // defined in reportingLib
            logDebug "reporting for cluster 0x0006 set to ${intMinTime} - ${intMaxTime}"
        }
    }
    if ('0x0020' in DEVICE?.configuration) {
        if (DEVICE?.configuration['0x0020']['unbind'] != null) {
            logDebug 'customConfigureDevice() : unbind ZBMINIL2 poll control cluster'
            // Unbind genPollCtrl (0x0020) to prevent device from sending checkin message. // Zigbee-herdsmans responds to the checkin message which causes the device to poll slower.
            // https://github.com/Koenkk/zigbee2mqtt/issues/11676  // https://github.com/Koenkk/zigbee2mqtt/issues/10282   // https://github.com/zigpy/zha-device-handlers/issues/1519
            cmds += ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",'delay 229',]
        }
    }
    //'0xFC11':['read':['turboMode']]
    if ('0xFC11' in DEVICE?.configuration) {
        if (DEVICE?.configuration['0xFC11']['read'] != null) {
            cmds += refreshFromConfigureReadList(DEVICE?.configuration['0xFC11']['read'])   // defined in deviceProfileLib
            logDebug "reading attributes ${DEVICE?.configuration['0xFC11']['read']} from cluster 0xFC11"
        }
    }

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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.4.0' // library marker kkossev.commonLib, line 5
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
  * ver. 3.2.2  2024-06-12 kkossev  - removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204; // library marker kkossev.commonLib, line 38
  * ver. 3.3.0  2024-06-25 kkossev  - fixed exception for unknown clusters; added cluster 0xE001; added powerSource - if 5 minutes after initialize() the powerSource is still unknown, query the device for the powerSource // library marker kkossev.commonLib, line 39
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize(); // library marker kkossev.commonLib, line 40
  * ver. 3.3.2  2024-07-12 kkossev  - added PollControl (0x0020) cluster; ping for SONOFF // library marker kkossev.commonLib, line 41
  * ver. 3.3.3  2024-09-15 kkossev  - added queryAllTuyaDP(); 2 minutes healthCheck option; // library marker kkossev.commonLib, line 42
  * ver. 3.3.4  2025-01-29 kkossev  - 'LOAD ALL DEFAULTS' is the default Configure command. // library marker kkossev.commonLib, line 43
  * ver. 3.3.5  2025-03-05 kkossev  - getTuyaAttributeValue made public; fixed checkDriverVersion bug on hub reboot. // library marker kkossev.commonLib, line 44
  * ver. 3.4.0  2025-03-09 kkossev  - (dev.branch) healthCheck by pinging the device; updateRxStats() replaced with inline code; added state.lastRx.timeStamp // library marker kkossev.commonLib, line 45
  * // library marker kkossev.commonLib, line 46
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 47
  *                                   TODO: offlineCtr is not increasing! (ZBMicro); // library marker kkossev.commonLib, line 48
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 49
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 50
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 51
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 52
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 53
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 54
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 55
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 56
  * // library marker kkossev.commonLib, line 57
*/ // library marker kkossev.commonLib, line 58

String commonLibVersion() { '3.4.0' } // library marker kkossev.commonLib, line 60
String commonLibStamp() { '2025/03/09 8:12 PM' } // library marker kkossev.commonLib, line 61

import groovy.transform.Field // library marker kkossev.commonLib, line 63
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 64
import hubitat.device.Protocol // library marker kkossev.commonLib, line 65
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 66
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 67
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 68
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 69
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 70
import java.math.BigDecimal // library marker kkossev.commonLib, line 71

metadata { // library marker kkossev.commonLib, line 73
        if (_DEBUG) { // library marker kkossev.commonLib, line 74
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 75
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 76
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 77
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 78
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 79
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 80
            ] // library marker kkossev.commonLib, line 81
        } // library marker kkossev.commonLib, line 82

        // common capabilities for all device types // library marker kkossev.commonLib, line 84
        capability 'Configuration' // library marker kkossev.commonLib, line 85
        capability 'Refresh' // library marker kkossev.commonLib, line 86
        capability 'HealthCheck' // library marker kkossev.commonLib, line 87
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 88

        // common attributes for all device types // library marker kkossev.commonLib, line 90
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 91
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 92
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 93

        // common commands for all device types // library marker kkossev.commonLib, line 95
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 96

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 98
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 99

    preferences { // library marker kkossev.commonLib, line 101
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 102
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 103
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 104

        if (device) { // library marker kkossev.commonLib, line 106
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 107
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 108
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 109
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 110
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 111
            } // library marker kkossev.commonLib, line 112
        } // library marker kkossev.commonLib, line 113
    } // library marker kkossev.commonLib, line 114
} // library marker kkossev.commonLib, line 115

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 117
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 118
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 119
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 120
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 121
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 123
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 124
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 125
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 126
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 127

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 129
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 132
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 136
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 137
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 138
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 139
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 140
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 141
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 142
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 143
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 144
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 145
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 146
] // library marker kkossev.commonLib, line 147

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 149

/** // library marker kkossev.commonLib, line 151
 * Parse Zigbee message // library marker kkossev.commonLib, line 152
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 153
 */ // library marker kkossev.commonLib, line 154
public void parse(final String description) { // library marker kkossev.commonLib, line 155
    Map stateCopy = state.clone() // copy the state to avoid concurrent modification // library marker kkossev.commonLib, line 156
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 157
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 158
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 159
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 160
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 161

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 163
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 164
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 165
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 166
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 167
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 168
        return // library marker kkossev.commonLib, line 169
    } // library marker kkossev.commonLib, line 170
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 171
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 172
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 173
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 174
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 175
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 176
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 177
        return // library marker kkossev.commonLib, line 178
    } // library marker kkossev.commonLib, line 179

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 184

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 186
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 187

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 189
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 190
        return // library marker kkossev.commonLib, line 191
    } // library marker kkossev.commonLib, line 192
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 193
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 194
        return // library marker kkossev.commonLib, line 195
    } // library marker kkossev.commonLib, line 196
    // // library marker kkossev.commonLib, line 197
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 198
    // // library marker kkossev.commonLib, line 199
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 200
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 201
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 202
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 203
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 204
            } // library marker kkossev.commonLib, line 205
            break // library marker kkossev.commonLib, line 206
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 207
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 208
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 209
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 210
            } // library marker kkossev.commonLib, line 211
            break // library marker kkossev.commonLib, line 212
        default: // library marker kkossev.commonLib, line 213
            if (settings.logEnable) { // library marker kkossev.commonLib, line 214
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 215
            } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
    } // library marker kkossev.commonLib, line 218
} // library marker kkossev.commonLib, line 219

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 221
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 222
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 223
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 224
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 225
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 226
] // library marker kkossev.commonLib, line 227

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 229
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 230
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 231
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 232
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 233
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 234
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 235
        return false // library marker kkossev.commonLib, line 236
    } // library marker kkossev.commonLib, line 237
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 238
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 239
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 240
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 241
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 242
        return true // library marker kkossev.commonLib, line 243
    } // library marker kkossev.commonLib, line 244
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 245
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 246
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 247
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 248
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 249
        return true // library marker kkossev.commonLib, line 250
    } // library marker kkossev.commonLib, line 251
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 252
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 253
    } // library marker kkossev.commonLib, line 254
    return false // library marker kkossev.commonLib, line 255
} // library marker kkossev.commonLib, line 256

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 258
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 259
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 260
} // library marker kkossev.commonLib, line 261

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 263
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 264
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 265
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 266
    } // library marker kkossev.commonLib, line 267
    return false // library marker kkossev.commonLib, line 268
} // library marker kkossev.commonLib, line 269

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 271
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 272
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 273
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 274
    } // library marker kkossev.commonLib, line 275
    return false // library marker kkossev.commonLib, line 276
} // library marker kkossev.commonLib, line 277

public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 279
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 280
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 281
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 282
    } // library marker kkossev.commonLib, line 283
    return false // library marker kkossev.commonLib, line 284
} // library marker kkossev.commonLib, line 285

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 287
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 288
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 289
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 290
] // library marker kkossev.commonLib, line 291

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 293
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 294
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 295
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 296
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 297
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 298
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 299
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 300
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 301
    List<String> cmds = [] // library marker kkossev.commonLib, line 302
    switch (clusterId) { // library marker kkossev.commonLib, line 303
        case 0x0005 : // library marker kkossev.commonLib, line 304
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 305
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 306
            // send the active endpoint response // library marker kkossev.commonLib, line 307
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 308
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case 0x0006 : // library marker kkossev.commonLib, line 311
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 312
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 313
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 314
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 317
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 318
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 321
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 322
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 325
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 326
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 327
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 328
            break // library marker kkossev.commonLib, line 329
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 333
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 334
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 335
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 336
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 337
            break // library marker kkossev.commonLib, line 338
        default : // library marker kkossev.commonLib, line 339
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
    } // library marker kkossev.commonLib, line 342
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 343
} // library marker kkossev.commonLib, line 344

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 346
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 347
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 348
    switch (commandId) { // library marker kkossev.commonLib, line 349
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 350
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 351
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 352
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 353
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 354
        default: // library marker kkossev.commonLib, line 355
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 356
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 357
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 358
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 359
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 360
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 361
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 362
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 363
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 364
            } // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
    } // library marker kkossev.commonLib, line 367
} // library marker kkossev.commonLib, line 368

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 370
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 371
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 372
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 373
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 374
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 375
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 376
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 377
    } // library marker kkossev.commonLib, line 378
    else { // library marker kkossev.commonLib, line 379
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 380
    } // library marker kkossev.commonLib, line 381
} // library marker kkossev.commonLib, line 382

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 384
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 385
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 386
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 387
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 388
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 389
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 390
    } // library marker kkossev.commonLib, line 391
    else { // library marker kkossev.commonLib, line 392
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 393
    } // library marker kkossev.commonLib, line 394
} // library marker kkossev.commonLib, line 395

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 397
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 398
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 399
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 400
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 401
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 402
        state.reportingEnabled = true // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 405
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 406
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 407
    } else { // library marker kkossev.commonLib, line 408
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 409
    } // library marker kkossev.commonLib, line 410
} // library marker kkossev.commonLib, line 411

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 413
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 414
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 415
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 416
    if (status == 0) { // library marker kkossev.commonLib, line 417
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 418
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 419
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 420
        int delta = 0 // library marker kkossev.commonLib, line 421
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 422
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 423
        } // library marker kkossev.commonLib, line 424
        else { // library marker kkossev.commonLib, line 425
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 426
        } // library marker kkossev.commonLib, line 427
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
    else { // library marker kkossev.commonLib, line 430
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 431
    } // library marker kkossev.commonLib, line 432
} // library marker kkossev.commonLib, line 433

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 435
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 436
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 437
        return false // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
    // execute the customHandler function // library marker kkossev.commonLib, line 440
    Boolean result = false // library marker kkossev.commonLib, line 441
    try { // library marker kkossev.commonLib, line 442
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 443
    } // library marker kkossev.commonLib, line 444
    catch (e) { // library marker kkossev.commonLib, line 445
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 446
        return false // library marker kkossev.commonLib, line 447
    } // library marker kkossev.commonLib, line 448
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 449
    return result // library marker kkossev.commonLib, line 450
} // library marker kkossev.commonLib, line 451

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 453
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 454
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 455
    final String commandId = data[0] // library marker kkossev.commonLib, line 456
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 457
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 458
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 459
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 460
    } else { // library marker kkossev.commonLib, line 461
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 462
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 463
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 464
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 465
        } // library marker kkossev.commonLib, line 466
    } // library marker kkossev.commonLib, line 467
} // library marker kkossev.commonLib, line 468

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 470
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 471
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 472
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 473

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 475
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 476
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 477
] // library marker kkossev.commonLib, line 478

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 480
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 481
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 482
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 483
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 484
] // library marker kkossev.commonLib, line 485

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 487
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 488
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 489
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 490
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 491
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 492
    return avg // library marker kkossev.commonLib, line 493
} // library marker kkossev.commonLib, line 494

void handlePingResponse() { // library marker kkossev.commonLib, line 496
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 497
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 498
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 499

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 501
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 502
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 503
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 504
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 505
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 506
        sendRttEvent() // library marker kkossev.commonLib, line 507
    } // library marker kkossev.commonLib, line 508
    else { // library marker kkossev.commonLib, line 509
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
    state.states['isPing'] = false // library marker kkossev.commonLib, line 512
} // library marker kkossev.commonLib, line 513

/* // library marker kkossev.commonLib, line 515
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 516
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 517
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 518
*/ // library marker kkossev.commonLib, line 519
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 520

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 522
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 523
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 524
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 525
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 526
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 527
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 528
        case 0x0000: // library marker kkossev.commonLib, line 529
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 530
            break // library marker kkossev.commonLib, line 531
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 532
            if (isPing) { // library marker kkossev.commonLib, line 533
                handlePingResponse() // library marker kkossev.commonLib, line 534
            } // library marker kkossev.commonLib, line 535
            else { // library marker kkossev.commonLib, line 536
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 537
            } // library marker kkossev.commonLib, line 538
            break // library marker kkossev.commonLib, line 539
        case 0x0004: // library marker kkossev.commonLib, line 540
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 541
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 542
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 543
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 544
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 545
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 546
            } // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case 0x0005: // library marker kkossev.commonLib, line 549
            if (isPing) { // library marker kkossev.commonLib, line 550
                handlePingResponse() // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            else { // library marker kkossev.commonLib, line 553
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 554
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 555
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 556
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 557
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 558
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 559
                } // library marker kkossev.commonLib, line 560
            } // library marker kkossev.commonLib, line 561
            break // library marker kkossev.commonLib, line 562
        case 0x0007: // library marker kkossev.commonLib, line 563
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 564
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 565
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 566
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 567
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 568
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 569
            } // library marker kkossev.commonLib, line 570
            break // library marker kkossev.commonLib, line 571
        case 0xFFDF: // library marker kkossev.commonLib, line 572
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 573
            break // library marker kkossev.commonLib, line 574
        case 0xFFE2: // library marker kkossev.commonLib, line 575
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 576
            break // library marker kkossev.commonLib, line 577
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 578
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 579
            break // library marker kkossev.commonLib, line 580
        case 0xFFFE: // library marker kkossev.commonLib, line 581
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 582
            break // library marker kkossev.commonLib, line 583
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 584
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 585
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 586
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 587
            break // library marker kkossev.commonLib, line 588
        default: // library marker kkossev.commonLib, line 589
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 590
            break // library marker kkossev.commonLib, line 591
    } // library marker kkossev.commonLib, line 592
} // library marker kkossev.commonLib, line 593

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 595
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 596
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 597
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 598
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 599
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 600
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 601
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 604
    } // library marker kkossev.commonLib, line 605
} // library marker kkossev.commonLib, line 606

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 608
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 609
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 610

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 612
    Map descMap = [:] // library marker kkossev.commonLib, line 613
    try { // library marker kkossev.commonLib, line 614
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 615
    } // library marker kkossev.commonLib, line 616
    catch (e1) { // library marker kkossev.commonLib, line 617
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 618
        // try alternative custom parsing // library marker kkossev.commonLib, line 619
        descMap = [:] // library marker kkossev.commonLib, line 620
        try { // library marker kkossev.commonLib, line 621
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 622
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 623
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 624
            } // library marker kkossev.commonLib, line 625
        } // library marker kkossev.commonLib, line 626
        catch (e2) { // library marker kkossev.commonLib, line 627
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 628
            return [:] // library marker kkossev.commonLib, line 629
        } // library marker kkossev.commonLib, line 630
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 631
    } // library marker kkossev.commonLib, line 632
    return descMap // library marker kkossev.commonLib, line 633
} // library marker kkossev.commonLib, line 634

private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 636
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 637
        return false // library marker kkossev.commonLib, line 638
    } // library marker kkossev.commonLib, line 639
    // try to parse ... // library marker kkossev.commonLib, line 640
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 641
    Map descMap = [:] // library marker kkossev.commonLib, line 642
    try { // library marker kkossev.commonLib, line 643
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 644
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 645
    } // library marker kkossev.commonLib, line 646
    catch (e) { // library marker kkossev.commonLib, line 647
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 648
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 649
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 650
        return true // library marker kkossev.commonLib, line 651
    } // library marker kkossev.commonLib, line 652

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 654
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 655
    } // library marker kkossev.commonLib, line 656
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 657
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 658
    } // library marker kkossev.commonLib, line 659
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 660
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
    else { // library marker kkossev.commonLib, line 663
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 664
        return false // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    return true    // processed // library marker kkossev.commonLib, line 667
} // library marker kkossev.commonLib, line 668

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 670
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 671
  /* // library marker kkossev.commonLib, line 672
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 673
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 674
        return true // library marker kkossev.commonLib, line 675
    } // library marker kkossev.commonLib, line 676
*/ // library marker kkossev.commonLib, line 677
    Map descMap = [:] // library marker kkossev.commonLib, line 678
    try { // library marker kkossev.commonLib, line 679
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 680
    } // library marker kkossev.commonLib, line 681
    catch (e1) { // library marker kkossev.commonLib, line 682
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 683
        // try alternative custom parsing // library marker kkossev.commonLib, line 684
        descMap = [:] // library marker kkossev.commonLib, line 685
        try { // library marker kkossev.commonLib, line 686
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 687
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 688
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 689
            } // library marker kkossev.commonLib, line 690
        } // library marker kkossev.commonLib, line 691
        catch (e2) { // library marker kkossev.commonLib, line 692
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 693
            return true // library marker kkossev.commonLib, line 694
        } // library marker kkossev.commonLib, line 695
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 696
    } // library marker kkossev.commonLib, line 697
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 698
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 699
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 700
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 701
        return false // library marker kkossev.commonLib, line 702
    } // library marker kkossev.commonLib, line 703
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 704
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 705
    // attribute report received // library marker kkossev.commonLib, line 706
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 707
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 708
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 709
    } // library marker kkossev.commonLib, line 710
    attrData.each { // library marker kkossev.commonLib, line 711
        if (it.status == '86') { // library marker kkossev.commonLib, line 712
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 713
        // TODO - skip parsing? // library marker kkossev.commonLib, line 714
        } // library marker kkossev.commonLib, line 715
        switch (it.cluster) { // library marker kkossev.commonLib, line 716
            case '0000' : // library marker kkossev.commonLib, line 717
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 718
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 719
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 720
                } // library marker kkossev.commonLib, line 721
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 722
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 723
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 724
                } // library marker kkossev.commonLib, line 725
                else { // library marker kkossev.commonLib, line 726
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 727
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 728
                } // library marker kkossev.commonLib, line 729
                break // library marker kkossev.commonLib, line 730
            default : // library marker kkossev.commonLib, line 731
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 732
                break // library marker kkossev.commonLib, line 733
        } // switch // library marker kkossev.commonLib, line 734
    } // for each attribute // library marker kkossev.commonLib, line 735
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 736
} // library marker kkossev.commonLib, line 737

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 739
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 740
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 741
} // library marker kkossev.commonLib, line 742

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 744
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 745
} // library marker kkossev.commonLib, line 746

/* // library marker kkossev.commonLib, line 748
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 749
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 750
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 751
*/ // library marker kkossev.commonLib, line 752
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 753
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 754
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 755

// Tuya Commands // library marker kkossev.commonLib, line 757
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 758
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 759
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 760
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 761
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 762

// tuya DP type // library marker kkossev.commonLib, line 764
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 765
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 766
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 767
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 768
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 769
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 770

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 772
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 773
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 774
    long offset = 0 // library marker kkossev.commonLib, line 775
    int offsetHours = 0 // library marker kkossev.commonLib, line 776
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 777
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 778
    try { // library marker kkossev.commonLib, line 779
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 780
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 781
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 782
    } catch (e) { // library marker kkossev.commonLib, line 783
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 784
    } // library marker kkossev.commonLib, line 785
    // // library marker kkossev.commonLib, line 786
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 787
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 788
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 789
} // library marker kkossev.commonLib, line 790

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 792
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 793
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 794
        syncTuyaDateTime() // library marker kkossev.commonLib, line 795
    } // library marker kkossev.commonLib, line 796
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 797
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 798
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 799
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 800
        if (status != '00') { // library marker kkossev.commonLib, line 801
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 805
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 806
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 807
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 808
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 809
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 810
            return // library marker kkossev.commonLib, line 811
        } // library marker kkossev.commonLib, line 812
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 813
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 814
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 815
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 816
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 817
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 818
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 819
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 820
            } // library marker kkossev.commonLib, line 821
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 822
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 823
        } // library marker kkossev.commonLib, line 824
    } // library marker kkossev.commonLib, line 825
    else { // library marker kkossev.commonLib, line 826
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
} // library marker kkossev.commonLib, line 829

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 831
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 832
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 833
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 834
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 835
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 836
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 837
        } // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 840
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 841
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 842
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 843
        } // library marker kkossev.commonLib, line 844
    } // library marker kkossev.commonLib, line 845
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 849
    int retValue = 0 // library marker kkossev.commonLib, line 850
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 851
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 852
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 853
        int power = 1 // library marker kkossev.commonLib, line 854
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 855
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 856
            power = power * 256 // library marker kkossev.commonLib, line 857
        } // library marker kkossev.commonLib, line 858
    } // library marker kkossev.commonLib, line 859
    return retValue // library marker kkossev.commonLib, line 860
} // library marker kkossev.commonLib, line 861

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 863

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 865
    List<String> cmds = [] // library marker kkossev.commonLib, line 866
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 867
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 868
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 869
    int tuyaCmd // library marker kkossev.commonLib, line 870
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\ // library marker kkossev.commonLib, line 871
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 872
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    else { // library marker kkossev.commonLib, line 875
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 876
    } // library marker kkossev.commonLib, line 877
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 878
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 879
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

List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 900
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

void refresh() { // library marker kkossev.commonLib, line 965
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
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    else { // library marker kkossev.commonLib, line 996
        logInfo "${info}" // library marker kkossev.commonLib, line 997
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 998
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 999
    } // library marker kkossev.commonLib, line 1000
} // library marker kkossev.commonLib, line 1001

public void ping() { // library marker kkossev.commonLib, line 1003
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1004
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1005
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1006
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1007
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1008
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1009
    logDebug 'ping...' // library marker kkossev.commonLib, line 1010
} // library marker kkossev.commonLib, line 1011

private void virtualPong() { // library marker kkossev.commonLib, line 1013
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1014
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1015
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1016
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1017
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1018
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1019
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1020
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1021
        sendRttEvent() // library marker kkossev.commonLib, line 1022
    } // library marker kkossev.commonLib, line 1023
    else { // library marker kkossev.commonLib, line 1024
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1025
    } // library marker kkossev.commonLib, line 1026
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1027
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1028
} // library marker kkossev.commonLib, line 1029

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1031
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1032
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1033
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1034
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1035
    if (value == null) { // library marker kkossev.commonLib, line 1036
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1037
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1038
    } // library marker kkossev.commonLib, line 1039
    else { // library marker kkossev.commonLib, line 1040
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1041
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1042
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
} // library marker kkossev.commonLib, line 1045

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1047
    if (cluster != null) { // library marker kkossev.commonLib, line 1048
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1049
    } // library marker kkossev.commonLib, line 1050
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1051
    return 'NULL' // library marker kkossev.commonLib, line 1052
} // library marker kkossev.commonLib, line 1053

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1055
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1056
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1057
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1058
} // library marker kkossev.commonLib, line 1059

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1061
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1062
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1063
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1064
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1065
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1066
    } // library marker kkossev.commonLib, line 1067
} // library marker kkossev.commonLib, line 1068

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1070
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1071
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1072
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1073
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1074
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1075
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1076
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1077
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1078
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1079
            } // library marker kkossev.commonLib, line 1080
        } // library marker kkossev.commonLib, line 1081
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
} // library marker kkossev.commonLib, line 1084

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1086
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1087
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1088
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1089
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
    else { // library marker kkossev.commonLib, line 1092
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1093
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1094
    } // library marker kkossev.commonLib, line 1095
} // library marker kkossev.commonLib, line 1096

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1098
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1099
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1100
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1101
} // library marker kkossev.commonLib, line 1102

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1104
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1105
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1106
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1107
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1108
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1109
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
} // library marker kkossev.commonLib, line 1112

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1114
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1115
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1116
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1117
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1118
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1119
            logWarn 'not present!' // library marker kkossev.commonLib, line 1120
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1121
        } // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
    else { // library marker kkossev.commonLib, line 1124
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1127
    // added 03/06/2025 // library marker kkossev.commonLib, line 1128
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1129
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1130
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1131
    } // library marker kkossev.commonLib, line 1132
} // library marker kkossev.commonLib, line 1133

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1135
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1136
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1137
    if (value == 'online') { // library marker kkossev.commonLib, line 1138
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1139
    } // library marker kkossev.commonLib, line 1140
    else { // library marker kkossev.commonLib, line 1141
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
} // library marker kkossev.commonLib, line 1144

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1146
void updated() { // library marker kkossev.commonLib, line 1147
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1148
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1149
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1150
    unschedule() // library marker kkossev.commonLib, line 1151

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1153
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1154
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1155
    } // library marker kkossev.commonLib, line 1156
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1157
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1158
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1162
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1163
        // schedule the periodic timer // library marker kkossev.commonLib, line 1164
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1165
        if (interval > 0) { // library marker kkossev.commonLib, line 1166
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1167
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1168
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1169
        } // library marker kkossev.commonLib, line 1170
    } // library marker kkossev.commonLib, line 1171
    else { // library marker kkossev.commonLib, line 1172
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1173
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1174
    } // library marker kkossev.commonLib, line 1175
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1176
        customUpdated() // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1180
} // library marker kkossev.commonLib, line 1181

private void logsOff() { // library marker kkossev.commonLib, line 1183
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1184
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1185
} // library marker kkossev.commonLib, line 1186
private void traceOff() { // library marker kkossev.commonLib, line 1187
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1188
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190

public void configure(String command) { // library marker kkossev.commonLib, line 1192
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1193
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1194
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1195
        return // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
    // // library marker kkossev.commonLib, line 1198
    String func // library marker kkossev.commonLib, line 1199
    try { // library marker kkossev.commonLib, line 1200
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1201
        "$func"() // library marker kkossev.commonLib, line 1202
    } // library marker kkossev.commonLib, line 1203
    catch (e) { // library marker kkossev.commonLib, line 1204
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1205
        return // library marker kkossev.commonLib, line 1206
    } // library marker kkossev.commonLib, line 1207
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1211
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1212
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1213
} // library marker kkossev.commonLib, line 1214

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1216
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1217
    deleteAllSettings() // library marker kkossev.commonLib, line 1218
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1219
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1220
    deleteAllStates() // library marker kkossev.commonLib, line 1221
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1222

    initialize() // library marker kkossev.commonLib, line 1224
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1225
    updated() // library marker kkossev.commonLib, line 1226
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1227
} // library marker kkossev.commonLib, line 1228

private void configureNow() { // library marker kkossev.commonLib, line 1230
    configure() // library marker kkossev.commonLib, line 1231
} // library marker kkossev.commonLib, line 1232

/** // library marker kkossev.commonLib, line 1234
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1235
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1236
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1237
 */ // library marker kkossev.commonLib, line 1238
void configure() { // library marker kkossev.commonLib, line 1239
    List<String> cmds = [] // library marker kkossev.commonLib, line 1240
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1241
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1242
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1243
    if (isTuya()) { // library marker kkossev.commonLib, line 1244
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1247
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1248
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1249
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1250
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1251
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1252
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1253
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1254
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    else { // library marker kkossev.commonLib, line 1257
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1258
    } // library marker kkossev.commonLib, line 1259
} // library marker kkossev.commonLib, line 1260

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1262
void installed() { // library marker kkossev.commonLib, line 1263
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1264
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1265
    // populate some default values for attributes // library marker kkossev.commonLib, line 1266
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1267
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1268
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1269
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1270
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1271
} // library marker kkossev.commonLib, line 1272

private void queryPowerSource() { // library marker kkossev.commonLib, line 1274
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1275
} // library marker kkossev.commonLib, line 1276

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1278
private void initialize() { // library marker kkossev.commonLib, line 1279
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1280
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1281
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1282
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1283
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1284
    } // library marker kkossev.commonLib, line 1285
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1286
    updateTuyaVersion() // library marker kkossev.commonLib, line 1287
    updateAqaraVersion() // library marker kkossev.commonLib, line 1288
} // library marker kkossev.commonLib, line 1289

/* // library marker kkossev.commonLib, line 1291
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1292
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1293
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1294
*/ // library marker kkossev.commonLib, line 1295

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1297
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1298
} // library marker kkossev.commonLib, line 1299

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1301
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1302
} // library marker kkossev.commonLib, line 1303

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1305
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1306
} // library marker kkossev.commonLib, line 1307

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1309
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1310
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1311
        return // library marker kkossev.commonLib, line 1312
    } // library marker kkossev.commonLib, line 1313
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1314
    cmd.each { // library marker kkossev.commonLib, line 1315
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1316
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1317
            return // library marker kkossev.commonLib, line 1318
        } // library marker kkossev.commonLib, line 1319
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1320
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1321
    } // library marker kkossev.commonLib, line 1322
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1323
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1324
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1325
} // library marker kkossev.commonLib, line 1326

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1328

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1330
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1331
} // library marker kkossev.commonLib, line 1332

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1334
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

//@CompileStatic // library marker kkossev.commonLib, line 1338
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1339
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1340
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1341
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1342
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1343
        initializeVars(false) // library marker kkossev.commonLib, line 1344
        updateTuyaVersion() // library marker kkossev.commonLib, line 1345
        updateAqaraVersion() // library marker kkossev.commonLib, line 1346
    } // library marker kkossev.commonLib, line 1347
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1348
} // library marker kkossev.commonLib, line 1349

// credits @thebearmay // library marker kkossev.commonLib, line 1351
String getModel() { // library marker kkossev.commonLib, line 1352
    try { // library marker kkossev.commonLib, line 1353
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1354
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1355
    } catch (ignore) { // library marker kkossev.commonLib, line 1356
        try { // library marker kkossev.commonLib, line 1357
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1358
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1359
                return model // library marker kkossev.commonLib, line 1360
            } // library marker kkossev.commonLib, line 1361
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1362
            return '' // library marker kkossev.commonLib, line 1363
        } // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

// credits @thebearmay // library marker kkossev.commonLib, line 1368
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1369
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1370
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1371
    String revision = tokens.last() // library marker kkossev.commonLib, line 1372
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1373
} // library marker kkossev.commonLib, line 1374

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1376
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1377
    unschedule() // library marker kkossev.commonLib, line 1378
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1379
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1380

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

void resetStatistics() { // library marker kkossev.commonLib, line 1385
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1386
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1387
} // library marker kkossev.commonLib, line 1388

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1390
void resetStats() { // library marker kkossev.commonLib, line 1391
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1392
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1393
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1394
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1395
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1396
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1397
} // library marker kkossev.commonLib, line 1398

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1400
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1401
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1402
        state.clear() // library marker kkossev.commonLib, line 1403
        unschedule() // library marker kkossev.commonLib, line 1404
        resetStats() // library marker kkossev.commonLib, line 1405
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1406
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1407
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1408
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1409
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1410
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1411
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1412
    } // library marker kkossev.commonLib, line 1413

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1415
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1416
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1417
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1418
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1419

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1421
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1422
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1423
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1424
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1425
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1426
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1427

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1429

    // common libraries initialization // library marker kkossev.commonLib, line 1431
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1432
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1433
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1434
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1435
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1436
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1437
    // // library marker kkossev.commonLib, line 1438
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1439
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1440
    // // library marker kkossev.commonLib, line 1441
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1442
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1443
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1444
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1445

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1447
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1448
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1449
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1450
    if ( ep  != null) { // library marker kkossev.commonLib, line 1451
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1452
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1453
    } // library marker kkossev.commonLib, line 1454
    else { // library marker kkossev.commonLib, line 1455
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1456
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1457
    } // library marker kkossev.commonLib, line 1458
} // library marker kkossev.commonLib, line 1459

// not used!? // library marker kkossev.commonLib, line 1461
void setDestinationEP() { // library marker kkossev.commonLib, line 1462
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1463
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1464
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1468
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1469
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1470
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1471

// _DEBUG mode only // library marker kkossev.commonLib, line 1473
void getAllProperties() { // library marker kkossev.commonLib, line 1474
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1475
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1476
} // library marker kkossev.commonLib, line 1477

// delete all Preferences // library marker kkossev.commonLib, line 1479
void deleteAllSettings() { // library marker kkossev.commonLib, line 1480
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1481
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1482
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1483
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

// delete all attributes // library marker kkossev.commonLib, line 1487
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1488
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1489
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1490
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1491
} // library marker kkossev.commonLib, line 1492

// delete all State Variables // library marker kkossev.commonLib, line 1494
void deleteAllStates() { // library marker kkossev.commonLib, line 1495
    String stateDeleted = '' // library marker kkossev.commonLib, line 1496
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1497
    state.clear() // library marker kkossev.commonLib, line 1498
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1499
} // library marker kkossev.commonLib, line 1500

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1502
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1506
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1507
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1508
} // library marker kkossev.commonLib, line 1509

void testParse(String par) { // library marker kkossev.commonLib, line 1511
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1512
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1513
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1514
    parse(par) // library marker kkossev.commonLib, line 1515
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1516
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1517
} // library marker kkossev.commonLib, line 1518

Object testJob() { // library marker kkossev.commonLib, line 1520
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1521
} // library marker kkossev.commonLib, line 1522

/** // library marker kkossev.commonLib, line 1524
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1525
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1526
 */ // library marker kkossev.commonLib, line 1527
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1528
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1529
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1530
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1531
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1532
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1533
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1534
    String cron // library marker kkossev.commonLib, line 1535
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1536
    else { // library marker kkossev.commonLib, line 1537
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1538
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1539
    } // library marker kkossev.commonLib, line 1540
    return cron // library marker kkossev.commonLib, line 1541
} // library marker kkossev.commonLib, line 1542

// credits @thebearmay // library marker kkossev.commonLib, line 1544
String formatUptime() { // library marker kkossev.commonLib, line 1545
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1546
} // library marker kkossev.commonLib, line 1547

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1549
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1550
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1551
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1552
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1553
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1554
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

boolean isTuya() { // library marker kkossev.commonLib, line 1558
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1559
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1560
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1561
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1562
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1563
} // library marker kkossev.commonLib, line 1564

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1566
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1567
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1568
    if (application != null) { // library marker kkossev.commonLib, line 1569
        Integer ver // library marker kkossev.commonLib, line 1570
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1571
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1572
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1573
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1574
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1575
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1576
        } // library marker kkossev.commonLib, line 1577
    } // library marker kkossev.commonLib, line 1578
} // library marker kkossev.commonLib, line 1579

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1581

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1583
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1584
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1585
    if (application != null) { // library marker kkossev.commonLib, line 1586
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1587
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1588
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1589
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1590
        } // library marker kkossev.commonLib, line 1591
    } // library marker kkossev.commonLib, line 1592
} // library marker kkossev.commonLib, line 1593

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1595
    try { // library marker kkossev.commonLib, line 1596
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1597
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1598
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1599
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1600
    } catch (e) { // library marker kkossev.commonLib, line 1601
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1602
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1603
    } // library marker kkossev.commonLib, line 1604
} // library marker kkossev.commonLib, line 1605

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1607
    try { // library marker kkossev.commonLib, line 1608
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1609
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1610
        return date.getTime() // library marker kkossev.commonLib, line 1611
    } catch (e) { // library marker kkossev.commonLib, line 1612
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1613
        return now() // library marker kkossev.commonLib, line 1614
    } // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1618
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1619
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1620
    int seconds = time % 60 // library marker kkossev.commonLib, line 1621
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1622
} // library marker kkossev.commonLib, line 1623

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.2' // library marker kkossev.onOffLib, line 5
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
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.1 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * ver. 3.2.1  2024-06-07 kkossev  - the advanced options are excpluded for DEVICE_TYPE Thermostat // library marker kkossev.onOffLib, line 20
 * ver. 3.2.2  2024-06-29 kkossev  - added on/off control for Tuya device profiles with 'switch' dp; // library marker kkossev.onOffLib, line 21
 * // library marker kkossev.onOffLib, line 22
 *                                   TODO: // library marker kkossev.onOffLib, line 23
*/ // library marker kkossev.onOffLib, line 24

static String onOffLibVersion()   { '3.2.2' } // library marker kkossev.onOffLib, line 26
static String onOffLibStamp() { '2024/06/29 12:27 PM' } // library marker kkossev.onOffLib, line 27

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 29

metadata { // library marker kkossev.onOffLib, line 31
    capability 'Actuator' // library marker kkossev.onOffLib, line 32
    capability 'Switch' // library marker kkossev.onOffLib, line 33
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 34
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 35
    } // library marker kkossev.onOffLib, line 36
    // no commands // library marker kkossev.onOffLib, line 37
    preferences { // library marker kkossev.onOffLib, line 38
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 39
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 40
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 41
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 42
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 43
            } // library marker kkossev.onOffLib, line 44
        } // library marker kkossev.onOffLib, line 45
    } // library marker kkossev.onOffLib, line 46
} // library marker kkossev.onOffLib, line 47

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 49
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 50
] // library marker kkossev.onOffLib, line 51

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 53
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 54
] // library marker kkossev.onOffLib, line 55

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 57
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 58
] // library marker kkossev.onOffLib, line 59

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 61

/* // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
*/ // library marker kkossev.onOffLib, line 67
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 68
    /* // library marker kkossev.onOffLib, line 69
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 70
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 71
    } // library marker kkossev.onOffLib, line 72
    else */ // library marker kkossev.onOffLib, line 73
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 74
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 75
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 76
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 77
    } // library marker kkossev.onOffLib, line 78
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 79
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 80
    } // library marker kkossev.onOffLib, line 81
    else { // library marker kkossev.onOffLib, line 82
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 83
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 84
    } // library marker kkossev.onOffLib, line 85
} // library marker kkossev.onOffLib, line 86

void toggle() { // library marker kkossev.onOffLib, line 88
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 89
    String state = '' // library marker kkossev.onOffLib, line 90
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 91
        state = 'on' // library marker kkossev.onOffLib, line 92
    } // library marker kkossev.onOffLib, line 93
    else { // library marker kkossev.onOffLib, line 94
        state = 'off' // library marker kkossev.onOffLib, line 95
    } // library marker kkossev.onOffLib, line 96
    descriptionText += state // library marker kkossev.onOffLib, line 97
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 98
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 99
} // library marker kkossev.onOffLib, line 100

void off() { // library marker kkossev.onOffLib, line 102
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 103
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 104
    List<String> cmds = [] // library marker kkossev.onOffLib, line 105
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 106
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 107
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 108
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 109
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 110
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 111
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 112
        } // library marker kkossev.onOffLib, line 113
    } // library marker kkossev.onOffLib, line 114
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 115
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 116
    } // library marker kkossev.onOffLib, line 117

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 119
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 120
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 121
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 122
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 123
        } // library marker kkossev.onOffLib, line 124
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 125
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 126
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 127
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 128
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 129
    } // library marker kkossev.onOffLib, line 130
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 131
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 132
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 133
} // library marker kkossev.onOffLib, line 134

void on() { // library marker kkossev.onOffLib, line 136
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 137
    List<String> cmds = [] // library marker kkossev.onOffLib, line 138
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 139
    if (this.respondsTo(getDEVICE)) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 140
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 141
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 142
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 143
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 144
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 145
        } // library marker kkossev.onOffLib, line 146
    } // library marker kkossev.onOffLib, line 147
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 148
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 149
    } // library marker kkossev.onOffLib, line 150
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 151
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 152
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 153
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 154
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 155
        } // library marker kkossev.onOffLib, line 156
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 157
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 158
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 159
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 160
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 161
    } // library marker kkossev.onOffLib, line 162
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 163
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 164
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 165
} // library marker kkossev.onOffLib, line 166

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 168
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 169
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 170
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 171
    } // library marker kkossev.onOffLib, line 172
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 173
    Map map = [:] // library marker kkossev.onOffLib, line 174
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 175
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 176
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 177
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 178
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 179
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 180
        return // library marker kkossev.onOffLib, line 181
    } // library marker kkossev.onOffLib, line 182
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 183
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 184
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 185
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 186
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 187
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 188
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 189
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 190
    } else { // library marker kkossev.onOffLib, line 191
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 192
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 193
    } // library marker kkossev.onOffLib, line 194
    map.name = 'switch' // library marker kkossev.onOffLib, line 195
    map.value = value // library marker kkossev.onOffLib, line 196
    if (isRefresh) { // library marker kkossev.onOffLib, line 197
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 198
        map.isStateChange = true // library marker kkossev.onOffLib, line 199
    } else { // library marker kkossev.onOffLib, line 200
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 201
    } // library marker kkossev.onOffLib, line 202
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 203
    sendEvent(map) // library marker kkossev.onOffLib, line 204
    clearIsDigital() // library marker kkossev.onOffLib, line 205
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 206
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 207
    } // library marker kkossev.onOffLib, line 208
} // library marker kkossev.onOffLib, line 209

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 211
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 212
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 213
    String mode // library marker kkossev.onOffLib, line 214
    String attrName // library marker kkossev.onOffLib, line 215
    if (it.value == null) { // library marker kkossev.onOffLib, line 216
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 217
        return // library marker kkossev.onOffLib, line 218
    } // library marker kkossev.onOffLib, line 219
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 220
    switch (it.attrId) { // library marker kkossev.onOffLib, line 221
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 222
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 223
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 224
            break // library marker kkossev.onOffLib, line 225
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 226
            attrName = 'On Time' // library marker kkossev.onOffLib, line 227
            mode = value // library marker kkossev.onOffLib, line 228
            break // library marker kkossev.onOffLib, line 229
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 230
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 231
            mode = value // library marker kkossev.onOffLib, line 232
            break // library marker kkossev.onOffLib, line 233
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 234
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 235
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 236
            break // library marker kkossev.onOffLib, line 237
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 238
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 239
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 240
            break // library marker kkossev.onOffLib, line 241
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 242
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 243
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 244
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 245
            } // library marker kkossev.onOffLib, line 246
            else { // library marker kkossev.onOffLib, line 247
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 248
            } // library marker kkossev.onOffLib, line 249
            break // library marker kkossev.onOffLib, line 250
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 251
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 252
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 253
            break // library marker kkossev.onOffLib, line 254
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 255
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 256
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 257
            break // library marker kkossev.onOffLib, line 258
        default : // library marker kkossev.onOffLib, line 259
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 260
            return // library marker kkossev.onOffLib, line 261
    } // library marker kkossev.onOffLib, line 262
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 263
} // library marker kkossev.onOffLib, line 264

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 266
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 267
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 268
    return cmds // library marker kkossev.onOffLib, line 269
} // library marker kkossev.onOffLib, line 270

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 272
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 273
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 275
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 276
} // library marker kkossev.onOffLib, line 277

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.4.2' // library marker kkossev.deviceProfileLib, line 5
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
 * ver. 3.0.0  2023-11-27 kkossev  - fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.0  2024-06-29 kkossev  - empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.3.1  2024-07-06 kkossev  - added powerSource event in the initEventsDeviceProfile // library marker kkossev.deviceProfileLib, line 31
 * ver. 3.3.2  2024-08-18 kkossev  - release 3.3.2 // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.3.3  2024-08-18 kkossev  - sendCommand and setPar commands commented out; must be declared in the main driver where really needed // library marker kkossev.deviceProfileLib, line 33
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.2  2025-03-09 kkossev  - (dev. branch) added refreshFromConfigureReadList() method // library marker kkossev.deviceProfileLib, line 37
 * // library marker kkossev.deviceProfileLib, line 38
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 39
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 44
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 45
 * // library marker kkossev.deviceProfileLib, line 46
*/ // library marker kkossev.deviceProfileLib, line 47

static String deviceProfileLibVersion()   { '3.4.2' } // library marker kkossev.deviceProfileLib, line 49
static String deviceProfileLibStamp() { '2025/03/09 10:02 PM' } // library marker kkossev.deviceProfileLib, line 50
import groovy.json.* // library marker kkossev.deviceProfileLib, line 51
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 52
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 55

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 57

metadata { // library marker kkossev.deviceProfileLib, line 59
    // no capabilities // library marker kkossev.deviceProfileLib, line 60
    // no attributes // library marker kkossev.deviceProfileLib, line 61
    /* // library marker kkossev.deviceProfileLib, line 62
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 63
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 64
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 65
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 66
    ] // library marker kkossev.deviceProfileLib, line 67
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 68
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 69
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 70
    ] // library marker kkossev.deviceProfileLib, line 71
    */ // library marker kkossev.deviceProfileLib, line 72
    preferences { // library marker kkossev.deviceProfileLib, line 73
        if (device) { // library marker kkossev.deviceProfileLib, line 74
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 75
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 76
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 77
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 78
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 79
                        input inputMap // library marker kkossev.deviceProfileLib, line 80
                    } // library marker kkossev.deviceProfileLib, line 81
                } // library marker kkossev.deviceProfileLib, line 82
            } // library marker kkossev.deviceProfileLib, line 83
            //if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 84
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 85
            //} // library marker kkossev.deviceProfileLib, line 86
        } // library marker kkossev.deviceProfileLib, line 87
    } // library marker kkossev.deviceProfileLib, line 88
} // library marker kkossev.deviceProfileLib, line 89

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 91

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 93
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 94
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 95
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 96

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 98
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 99
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 100
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 101
    } // library marker kkossev.deviceProfileLib, line 102
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 103
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 104
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 105
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 106
        } // library marker kkossev.deviceProfileLib, line 107
    } // library marker kkossev.deviceProfileLib, line 108
    return activeProfiles // library marker kkossev.deviceProfileLib, line 109
} // library marker kkossev.deviceProfileLib, line 110

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 112

/** // library marker kkossev.deviceProfileLib, line 114
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 115
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 116
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 117
 */ // library marker kkossev.deviceProfileLib, line 118
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 119
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 120
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 121
    else { return null } // library marker kkossev.deviceProfileLib, line 122
} // library marker kkossev.deviceProfileLib, line 123

/** // library marker kkossev.deviceProfileLib, line 125
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 126
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 127
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 128
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 129
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 130
 */ // library marker kkossev.deviceProfileLib, line 131
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 132
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 133
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 134
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 135
    def preference // library marker kkossev.deviceProfileLib, line 136
    try { // library marker kkossev.deviceProfileLib, line 137
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 138
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 139
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 140
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 141
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 142
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 143
        } // library marker kkossev.deviceProfileLib, line 144
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 145
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 146
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 147
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 148
        } // library marker kkossev.deviceProfileLib, line 149
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 150
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 151
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 152
        } // library marker kkossev.deviceProfileLib, line 153
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 154
    } catch (e) { // library marker kkossev.deviceProfileLib, line 155
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 156
        return [:] // library marker kkossev.deviceProfileLib, line 157
    } // library marker kkossev.deviceProfileLib, line 158
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 159
    return foundMap // library marker kkossev.deviceProfileLib, line 160
} // library marker kkossev.deviceProfileLib, line 161

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 163
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 164
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 165
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 166
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 167
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 168
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 169
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 170
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 171
            return foundMap // library marker kkossev.deviceProfileLib, line 172
        } // library marker kkossev.deviceProfileLib, line 173
    } // library marker kkossev.deviceProfileLib, line 174
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 175
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 176
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 177
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 178
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 179
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 180
            return foundMap // library marker kkossev.deviceProfileLib, line 181
        } // library marker kkossev.deviceProfileLib, line 182
    } // library marker kkossev.deviceProfileLib, line 183
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 184
    return [:] // library marker kkossev.deviceProfileLib, line 185
} // library marker kkossev.deviceProfileLib, line 186

/** // library marker kkossev.deviceProfileLib, line 188
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 189
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 190
 */ // library marker kkossev.deviceProfileLib, line 191
void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 192
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 193
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 194
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 195
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 196
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 197
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 198
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 199
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 200
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 201
            return // continue // library marker kkossev.deviceProfileLib, line 202
        } // library marker kkossev.deviceProfileLib, line 203
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 204
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 205
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 206
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 207
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 208
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 209
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 210
    } // library marker kkossev.deviceProfileLib, line 211
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 212
} // library marker kkossev.deviceProfileLib, line 213

/** // library marker kkossev.deviceProfileLib, line 215
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 216
 * // library marker kkossev.deviceProfileLib, line 217
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 218
 */ // library marker kkossev.deviceProfileLib, line 219
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 220
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 221
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 222
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 223
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 224
    } // library marker kkossev.deviceProfileLib, line 225
    return validPars // library marker kkossev.deviceProfileLib, line 226
} // library marker kkossev.deviceProfileLib, line 227

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 229
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 230
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 231
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 232
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 233
    def scaledValue // library marker kkossev.deviceProfileLib, line 234
    if (value == null) { // library marker kkossev.deviceProfileLib, line 235
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 236
        return null // library marker kkossev.deviceProfileLib, line 237
    } // library marker kkossev.deviceProfileLib, line 238
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 239
        case 'number' : // library marker kkossev.deviceProfileLib, line 240
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 241
            break // library marker kkossev.deviceProfileLib, line 242
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 243
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 244
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 245
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 246
            } // library marker kkossev.deviceProfileLib, line 247
            break // library marker kkossev.deviceProfileLib, line 248
        case 'bool' : // library marker kkossev.deviceProfileLib, line 249
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 250
            break // library marker kkossev.deviceProfileLib, line 251
        case 'enum' : // library marker kkossev.deviceProfileLib, line 252
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 253
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 254
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 255
                return null // library marker kkossev.deviceProfileLib, line 256
            } // library marker kkossev.deviceProfileLib, line 257
            scaledValue = value // library marker kkossev.deviceProfileLib, line 258
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 259
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 260
            } // library marker kkossev.deviceProfileLib, line 261
            break // library marker kkossev.deviceProfileLib, line 262
        default : // library marker kkossev.deviceProfileLib, line 263
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 264
            return null // library marker kkossev.deviceProfileLib, line 265
    } // library marker kkossev.deviceProfileLib, line 266
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 267
    return scaledValue // library marker kkossev.deviceProfileLib, line 268
} // library marker kkossev.deviceProfileLib, line 269

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 271
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 272
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 273
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 274
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 275
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 276
        return // library marker kkossev.deviceProfileLib, line 277
    } // library marker kkossev.deviceProfileLib, line 278
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 279
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 280
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 281
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 282
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 283
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 284
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 285
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 286
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 287
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 288
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 289
                // scale the value // library marker kkossev.deviceProfileLib, line 290
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 291
            } // library marker kkossev.deviceProfileLib, line 292
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 293
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 294
            } // library marker kkossev.deviceProfileLib, line 295
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 296
        } // library marker kkossev.deviceProfileLib, line 297
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 298
    } // library marker kkossev.deviceProfileLib, line 299
    return // library marker kkossev.deviceProfileLib, line 300
} // library marker kkossev.deviceProfileLib, line 301

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 303
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 304
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 305
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 306
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 307
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 308
} // library marker kkossev.deviceProfileLib, line 309
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 310
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 311
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 312
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 313
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 314
} // library marker kkossev.deviceProfileLib, line 315
int invert(int val) { // library marker kkossev.deviceProfileLib, line 316
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 317
    else { return val } // library marker kkossev.deviceProfileLib, line 318
} // library marker kkossev.deviceProfileLib, line 319

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 321
List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 322
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 323
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 324
    Map map = [:] // library marker kkossev.deviceProfileLib, line 325
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 326
    try { // library marker kkossev.deviceProfileLib, line 327
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 328
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 329
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 330
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 331
    } // library marker kkossev.deviceProfileLib, line 332
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 333
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 334
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 335
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 336
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 337
    } // library marker kkossev.deviceProfileLib, line 338
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 339
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 340
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mfgCode, delay = 50) // library marker kkossev.deviceProfileLib, line 341
    } // library marker kkossev.deviceProfileLib, line 342
    else { // library marker kkossev.deviceProfileLib, line 343
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 344
    } // library marker kkossev.deviceProfileLib, line 345
    return cmds // library marker kkossev.deviceProfileLib, line 346
} // library marker kkossev.deviceProfileLib, line 347

/** // library marker kkossev.deviceProfileLib, line 349
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 350
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 351
 * // library marker kkossev.deviceProfileLib, line 352
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 353
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 354
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 355
 */ // library marker kkossev.deviceProfileLib, line 356
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 357
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 358
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 359
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 360
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 361
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 362
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 363
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 364
        case 'number' : // library marker kkossev.deviceProfileLib, line 365
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 366
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 367
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 368
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 369
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 370
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 371
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 372
            } // library marker kkossev.deviceProfileLib, line 373
            else { // library marker kkossev.deviceProfileLib, line 374
                scaledValue = value // library marker kkossev.deviceProfileLib, line 375
            } // library marker kkossev.deviceProfileLib, line 376
            break // library marker kkossev.deviceProfileLib, line 377

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 379
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 380
            // scale the value // library marker kkossev.deviceProfileLib, line 381
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 382
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 383
            } // library marker kkossev.deviceProfileLib, line 384
            else { // library marker kkossev.deviceProfileLib, line 385
                scaledValue = value // library marker kkossev.deviceProfileLib, line 386
            } // library marker kkossev.deviceProfileLib, line 387
            break // library marker kkossev.deviceProfileLib, line 388

        case 'bool' : // library marker kkossev.deviceProfileLib, line 390
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 391
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 392
            else { // library marker kkossev.deviceProfileLib, line 393
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 394
                return null // library marker kkossev.deviceProfileLib, line 395
            } // library marker kkossev.deviceProfileLib, line 396
            break // library marker kkossev.deviceProfileLib, line 397
        case 'enum' : // library marker kkossev.deviceProfileLib, line 398
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 399
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 400
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 401
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 402
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 403
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 404
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 405
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 406
            } // library marker kkossev.deviceProfileLib, line 407
            else { // library marker kkossev.deviceProfileLib, line 408
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 409
            } // library marker kkossev.deviceProfileLib, line 410
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 411
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 412
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 413
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 414
                // find the key for the value // library marker kkossev.deviceProfileLib, line 415
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 416
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 417
                if (key == null) { // library marker kkossev.deviceProfileLib, line 418
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 419
                    return null // library marker kkossev.deviceProfileLib, line 420
                } // library marker kkossev.deviceProfileLib, line 421
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 422
            //return null // library marker kkossev.deviceProfileLib, line 423
            } // library marker kkossev.deviceProfileLib, line 424
            break // library marker kkossev.deviceProfileLib, line 425
        default : // library marker kkossev.deviceProfileLib, line 426
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 427
            return null // library marker kkossev.deviceProfileLib, line 428
    } // library marker kkossev.deviceProfileLib, line 429
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 430
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 431
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 432
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 433
        return null // library marker kkossev.deviceProfileLib, line 434
    } // library marker kkossev.deviceProfileLib, line 435
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 436
    return scaledValue // library marker kkossev.deviceProfileLib, line 437
} // library marker kkossev.deviceProfileLib, line 438

/** // library marker kkossev.deviceProfileLib, line 440
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 441
 * // library marker kkossev.deviceProfileLib, line 442
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 443
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 444
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 445
 */ // library marker kkossev.deviceProfileLib, line 446
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 447
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 448
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 449
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 450
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 451
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 452
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 453
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 454
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 455
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 456
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 457
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 458
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 459
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 460
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 461
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 462
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 463
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 464
        return false // library marker kkossev.deviceProfileLib, line 465
    } // library marker kkossev.deviceProfileLib, line 466

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 468
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 469
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 470
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 471
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 472
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 473
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 474
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 475
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 476
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 477
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 478
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 479
            return true // library marker kkossev.deviceProfileLib, line 480
        } // library marker kkossev.deviceProfileLib, line 481
        else { // library marker kkossev.deviceProfileLib, line 482
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 483
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 484
        } // library marker kkossev.deviceProfileLib, line 485
    } // library marker kkossev.deviceProfileLib, line 486
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 487
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 488
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 489
        def valMiscType // library marker kkossev.deviceProfileLib, line 490
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 491
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 492
            // find the key for the value // library marker kkossev.deviceProfileLib, line 493
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 494
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 495
            if (key == null) { // library marker kkossev.deviceProfileLib, line 496
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 497
                return false // library marker kkossev.deviceProfileLib, line 498
            } // library marker kkossev.deviceProfileLib, line 499
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 500
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 501
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 502
        } // library marker kkossev.deviceProfileLib, line 503
        else { // library marker kkossev.deviceProfileLib, line 504
            valMiscType = val // library marker kkossev.deviceProfileLib, line 505
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 506
        } // library marker kkossev.deviceProfileLib, line 507
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 508
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 509
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 510
        return true // library marker kkossev.deviceProfileLib, line 511
    } // library marker kkossev.deviceProfileLib, line 512

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 514
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 515

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 517
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 518
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 519
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 520
        // Tuya DP // library marker kkossev.deviceProfileLib, line 521
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 522
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 523
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 524
            return false // library marker kkossev.deviceProfileLib, line 525
        } // library marker kkossev.deviceProfileLib, line 526
        else { // library marker kkossev.deviceProfileLib, line 527
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 528
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 529
            return false // library marker kkossev.deviceProfileLib, line 530
        } // library marker kkossev.deviceProfileLib, line 531
    } // library marker kkossev.deviceProfileLib, line 532
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 533
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 534
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 535
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 536
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 537
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 538
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 539
            return false // library marker kkossev.deviceProfileLib, line 540
        } // library marker kkossev.deviceProfileLib, line 541
    } // library marker kkossev.deviceProfileLib, line 542
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 543
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 544
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 545
    return true // library marker kkossev.deviceProfileLib, line 546
} // library marker kkossev.deviceProfileLib, line 547

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 549
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 550
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 551
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 552
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 553
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 554
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 555
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 556
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 557
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 558
        return [] // library marker kkossev.deviceProfileLib, line 559
    } // library marker kkossev.deviceProfileLib, line 560
    String dpType // library marker kkossev.deviceProfileLib, line 561
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 562
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 563
    } // library marker kkossev.deviceProfileLib, line 564
    else { // library marker kkossev.deviceProfileLib, line 565
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 566
    } // library marker kkossev.deviceProfileLib, line 567
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 568
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 569
        return [] // library marker kkossev.deviceProfileLib, line 570
    } // library marker kkossev.deviceProfileLib, line 571
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 572
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 573
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 574
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 575
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 576
    } // library marker kkossev.deviceProfileLib, line 577
    else { // library marker kkossev.deviceProfileLib, line 578
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 579
    } // library marker kkossev.deviceProfileLib, line 580
    return cmds // library marker kkossev.deviceProfileLib, line 581
} // library marker kkossev.deviceProfileLib, line 582

int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 584
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 585
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 586
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 587
    } // library marker kkossev.deviceProfileLib, line 588
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 589
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 590
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 591
    } // library marker kkossev.deviceProfileLib, line 592
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 593
} // library marker kkossev.deviceProfileLib, line 594

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 596
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 597
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 598
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 599
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 600
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 601

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 603
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 604
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 605
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 606
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 607
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 608
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 609
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 610
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 611
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 612
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 613
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 614
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 615
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 616
        try { // library marker kkossev.deviceProfileLib, line 617
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 618
        } // library marker kkossev.deviceProfileLib, line 619
        catch (e) { // library marker kkossev.deviceProfileLib, line 620
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 621
            return false // library marker kkossev.deviceProfileLib, line 622
        } // library marker kkossev.deviceProfileLib, line 623
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 624
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 625
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 626
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 627
            return true // library marker kkossev.deviceProfileLib, line 628
        } // library marker kkossev.deviceProfileLib, line 629
        else { // library marker kkossev.deviceProfileLib, line 630
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 631
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 632
        } // library marker kkossev.deviceProfileLib, line 633
    } // library marker kkossev.deviceProfileLib, line 634
    else { // library marker kkossev.deviceProfileLib, line 635
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 636
    } // library marker kkossev.deviceProfileLib, line 637
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 638
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 639
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 640
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 641
        // patch !! // library marker kkossev.deviceProfileLib, line 642
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 643
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 644
        } // library marker kkossev.deviceProfileLib, line 645
        else { // library marker kkossev.deviceProfileLib, line 646
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 647
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 648
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 649
        } // library marker kkossev.deviceProfileLib, line 650
        return true // library marker kkossev.deviceProfileLib, line 651
    } // library marker kkossev.deviceProfileLib, line 652
    else { // library marker kkossev.deviceProfileLib, line 653
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 654
    } // library marker kkossev.deviceProfileLib, line 655
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 656
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 657
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 658
    try { // library marker kkossev.deviceProfileLib, line 659
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 660
    } // library marker kkossev.deviceProfileLib, line 661
    catch (e) { // library marker kkossev.deviceProfileLib, line 662
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 663
        return false // library marker kkossev.deviceProfileLib, line 664
    } // library marker kkossev.deviceProfileLib, line 665
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 666
        // Tuya DP // library marker kkossev.deviceProfileLib, line 667
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 668
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 669
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 670
            return false // library marker kkossev.deviceProfileLib, line 671
        } // library marker kkossev.deviceProfileLib, line 672
        else { // library marker kkossev.deviceProfileLib, line 673
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 674
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 675
            return true // library marker kkossev.deviceProfileLib, line 676
        } // library marker kkossev.deviceProfileLib, line 677
    } // library marker kkossev.deviceProfileLib, line 678
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 679
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 680
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 681
    } // library marker kkossev.deviceProfileLib, line 682
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 683
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 684
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 685
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 686
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 687
            return false // library marker kkossev.deviceProfileLib, line 688
        } // library marker kkossev.deviceProfileLib, line 689
    } // library marker kkossev.deviceProfileLib, line 690
    else { // library marker kkossev.deviceProfileLib, line 691
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 692
        return false // library marker kkossev.deviceProfileLib, line 693
    } // library marker kkossev.deviceProfileLib, line 694
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 695
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 696
    return true // library marker kkossev.deviceProfileLib, line 697
} // library marker kkossev.deviceProfileLib, line 698

/** // library marker kkossev.deviceProfileLib, line 700
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 701
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 702
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 703
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 704
 */ // library marker kkossev.deviceProfileLib, line 705
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 706
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 707
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 708
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 709
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 710
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 711
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 712
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 713
        return false // library marker kkossev.deviceProfileLib, line 714
    } // library marker kkossev.deviceProfileLib, line 715
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 716
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 717
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 718
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 719
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 720
        return false // library marker kkossev.deviceProfileLib, line 721
    } // library marker kkossev.deviceProfileLib, line 722
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 723
    def func, funcResult // library marker kkossev.deviceProfileLib, line 724
    try { // library marker kkossev.deviceProfileLib, line 725
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 726
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 727
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 728
            func = command // library marker kkossev.deviceProfileLib, line 729
        } // library marker kkossev.deviceProfileLib, line 730
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 731
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 732
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 733
        } // library marker kkossev.deviceProfileLib, line 734
        else { // library marker kkossev.deviceProfileLib, line 735
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 736
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 737
        } // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    catch (e) { // library marker kkossev.deviceProfileLib, line 740
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 741
        return false // library marker kkossev.deviceProfileLib, line 742
    } // library marker kkossev.deviceProfileLib, line 743
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 744
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 745
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 746
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 747
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 748
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 749
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 750
        } // library marker kkossev.deviceProfileLib, line 751
    } // library marker kkossev.deviceProfileLib, line 752
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 753
        return false // library marker kkossev.deviceProfileLib, line 754
    } // library marker kkossev.deviceProfileLib, line 755
     else { // library marker kkossev.deviceProfileLib, line 756
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 757
        return false // library marker kkossev.deviceProfileLib, line 758
    } // library marker kkossev.deviceProfileLib, line 759
    return true // library marker kkossev.deviceProfileLib, line 760
} // library marker kkossev.deviceProfileLib, line 761

/** // library marker kkossev.deviceProfileLib, line 763
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 764
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 765
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 766
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 767
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 768
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 769
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 770
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 771
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 772
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 773
 */ // library marker kkossev.deviceProfileLib, line 774
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 775
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 776
    Map input = [:] // library marker kkossev.deviceProfileLib, line 777
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 778
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 779
    Object preference // library marker kkossev.deviceProfileLib, line 780
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 781
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 782
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 783
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 784
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 785
    /* // library marker kkossev.deviceProfileLib, line 786
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 787
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 788
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 789
    */ // library marker kkossev.deviceProfileLib, line 790
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 791
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 792
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 793
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 794
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 795
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 796
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 797
        return [:] // library marker kkossev.deviceProfileLib, line 798
    } // library marker kkossev.deviceProfileLib, line 799
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 800
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 801
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 802
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 803
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 804
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 805
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 806
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 807
        } // library marker kkossev.deviceProfileLib, line 808
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 809
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 810
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 811
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 812
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 813
            } // library marker kkossev.deviceProfileLib, line 814
        } // library marker kkossev.deviceProfileLib, line 815
    } // library marker kkossev.deviceProfileLib, line 816
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 817
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 818
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 819
    }/* // library marker kkossev.deviceProfileLib, line 820
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 821
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 822
    }*/ // library marker kkossev.deviceProfileLib, line 823
    else { // library marker kkossev.deviceProfileLib, line 824
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 825
        return [:] // library marker kkossev.deviceProfileLib, line 826
    } // library marker kkossev.deviceProfileLib, line 827
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 828
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 829
    } // library marker kkossev.deviceProfileLib, line 830
    return input // library marker kkossev.deviceProfileLib, line 831
} // library marker kkossev.deviceProfileLib, line 832

/** // library marker kkossev.deviceProfileLib, line 834
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 835
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 836
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 837
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 838
 */ // library marker kkossev.deviceProfileLib, line 839
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 840
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 841
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 842
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 843
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 844
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 845
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 846
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 847
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 848
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 849
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 850
            } // library marker kkossev.deviceProfileLib, line 851
        } // library marker kkossev.deviceProfileLib, line 852
    } // library marker kkossev.deviceProfileLib, line 853
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 854
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 855
    } // library marker kkossev.deviceProfileLib, line 856
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 857
} // library marker kkossev.deviceProfileLib, line 858

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 860
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 861
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 862
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 863
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 864
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 865
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 866
    } // library marker kkossev.deviceProfileLib, line 867
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 868
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 869
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 870
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 871
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 872
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 873
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 874
    } else { // library marker kkossev.deviceProfileLib, line 875
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 876
    } // library marker kkossev.deviceProfileLib, line 877
} // library marker kkossev.deviceProfileLib, line 878

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 880
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 881
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 882
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 883
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 884
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 885
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 886
            if (k != null) { // library marker kkossev.deviceProfileLib, line 887
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 888
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 889
                if (map != null) { // library marker kkossev.deviceProfileLib, line 890
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 891
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 892
                } // library marker kkossev.deviceProfileLib, line 893
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 894
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 895
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 896
                } // library marker kkossev.deviceProfileLib, line 897
            } // library marker kkossev.deviceProfileLib, line 898
        } // library marker kkossev.deviceProfileLib, line 899
    } // library marker kkossev.deviceProfileLib, line 900
    return cmds // library marker kkossev.deviceProfileLib, line 901
} // library marker kkossev.deviceProfileLib, line 902

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 904
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 905
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 906
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 907
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 908
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 909
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 910
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 911
            if (k != null) { // library marker kkossev.deviceProfileLib, line 912
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 913
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 914
                if (map != null) { // library marker kkossev.deviceProfileLib, line 915
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 916
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 917
                } // library marker kkossev.deviceProfileLib, line 918
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 919
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 920
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 921
                } // library marker kkossev.deviceProfileLib, line 922
            } // library marker kkossev.deviceProfileLib, line 923
        } // library marker kkossev.deviceProfileLib, line 924
    } // library marker kkossev.deviceProfileLib, line 925
    return cmds // library marker kkossev.deviceProfileLib, line 926
} // library marker kkossev.deviceProfileLib, line 927

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 929
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 930
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 931
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 932
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 933
    return cmds // library marker kkossev.deviceProfileLib, line 934
} // library marker kkossev.deviceProfileLib, line 935

// TODO ! // library marker kkossev.deviceProfileLib, line 937
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 938
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 939
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 940
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 941
    return cmds // library marker kkossev.deviceProfileLib, line 942
} // library marker kkossev.deviceProfileLib, line 943

// TODO // library marker kkossev.deviceProfileLib, line 945
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 946
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 947
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 948
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 949
    return cmds // library marker kkossev.deviceProfileLib, line 950
} // library marker kkossev.deviceProfileLib, line 951

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 953
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 954
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 955
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 956
    } // library marker kkossev.deviceProfileLib, line 957
} // library marker kkossev.deviceProfileLib, line 958

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 960
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 961
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 962
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 963
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 964
    } // library marker kkossev.deviceProfileLib, line 965
} // library marker kkossev.deviceProfileLib, line 966

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 968

// // library marker kkossev.deviceProfileLib, line 970
// called from parse() // library marker kkossev.deviceProfileLib, line 971
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 972
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 973
// // library marker kkossev.deviceProfileLib, line 974
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 975
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 976
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 977
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 978
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 979
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 980
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 981
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 982
} // library marker kkossev.deviceProfileLib, line 983

// // library marker kkossev.deviceProfileLib, line 985
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 986
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 987
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 988
// // library marker kkossev.deviceProfileLib, line 989
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 990
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 991
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 992
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 993
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 994
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 995
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 996
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 997
} // library marker kkossev.deviceProfileLib, line 998

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 1000
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1001
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1002
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1003
    return isSpammy // library marker kkossev.deviceProfileLib, line 1004
} // library marker kkossev.deviceProfileLib, line 1005

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1007
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1008
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1009
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1010
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1011
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1012
    } // library marker kkossev.deviceProfileLib, line 1013
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1014
} // library marker kkossev.deviceProfileLib, line 1015

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1017
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1018
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1019
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1020
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1021
    } // library marker kkossev.deviceProfileLib, line 1022
    else { // library marker kkossev.deviceProfileLib, line 1023
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1024
    } // library marker kkossev.deviceProfileLib, line 1025
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1026
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1027
} // library marker kkossev.deviceProfileLib, line 1028

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1030
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1031
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1032
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1033
    } // library marker kkossev.deviceProfileLib, line 1034
    else { // library marker kkossev.deviceProfileLib, line 1035
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1036
    } // library marker kkossev.deviceProfileLib, line 1037
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1038
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1039
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1040
} // library marker kkossev.deviceProfileLib, line 1041

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1043
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1044
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1045
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1046
    def convertedValue // library marker kkossev.deviceProfileLib, line 1047
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1048
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1049
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1050
    } // library marker kkossev.deviceProfileLib, line 1051
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1052
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1053
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1054
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1055
            isEqual = false // library marker kkossev.deviceProfileLib, line 1056
        } // library marker kkossev.deviceProfileLib, line 1057
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1058
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1059
        } // library marker kkossev.deviceProfileLib, line 1060
    } // library marker kkossev.deviceProfileLib, line 1061
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1062
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1063
} // library marker kkossev.deviceProfileLib, line 1064

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1066
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1067
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1068
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1069
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1070
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1071
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1072
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1073
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1074
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1075
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1076
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1077
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1078
            break // library marker kkossev.deviceProfileLib, line 1079
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1080
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1081
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1082
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1083
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1084
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1085
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1086
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1087
            } // library marker kkossev.deviceProfileLib, line 1088
            else { // library marker kkossev.deviceProfileLib, line 1089
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1090
            } // library marker kkossev.deviceProfileLib, line 1091
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1092
            break // library marker kkossev.deviceProfileLib, line 1093
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1094
        case 'number' : // library marker kkossev.deviceProfileLib, line 1095
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1096
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1097
            break // library marker kkossev.deviceProfileLib, line 1098
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1099
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1100
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1101
            break // library marker kkossev.deviceProfileLib, line 1102
        default : // library marker kkossev.deviceProfileLib, line 1103
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1104
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1105
    } // library marker kkossev.deviceProfileLib, line 1106
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1107
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1108
    } // library marker kkossev.deviceProfileLib, line 1109
    // // library marker kkossev.deviceProfileLib, line 1110
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1111
} // library marker kkossev.deviceProfileLib, line 1112

// // library marker kkossev.deviceProfileLib, line 1114
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1115
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1116
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1117
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1118
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1119
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1120
// // library marker kkossev.deviceProfileLib, line 1121
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1122
// // library marker kkossev.deviceProfileLib, line 1123
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1124
// // library marker kkossev.deviceProfileLib, line 1125
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1126
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1127
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1128
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1129
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1130
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1131
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1132
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1133
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1134
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1135
            break // library marker kkossev.deviceProfileLib, line 1136
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1137
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1138
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1139
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1140
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1141
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1142
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1143
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1144
            } // library marker kkossev.deviceProfileLib, line 1145
            else { // library marker kkossev.deviceProfileLib, line 1146
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1147
            } // library marker kkossev.deviceProfileLib, line 1148
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1149
            break // library marker kkossev.deviceProfileLib, line 1150
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1151
        case 'number' : // library marker kkossev.deviceProfileLib, line 1152
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1153
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1154
            break // library marker kkossev.deviceProfileLib, line 1155
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1156
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1157
            break // library marker kkossev.deviceProfileLib, line 1158
        default : // library marker kkossev.deviceProfileLib, line 1159
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1160
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1161
    } // library marker kkossev.deviceProfileLib, line 1162
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1163
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1164
} // library marker kkossev.deviceProfileLib, line 1165

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1167
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1168
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1169
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1170
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1171
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1172
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1173
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1174
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1175
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1176
    } // library marker kkossev.deviceProfileLib, line 1177
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1178
    try { // library marker kkossev.deviceProfileLib, line 1179
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1180
    } // library marker kkossev.deviceProfileLib, line 1181
    catch (e) { // library marker kkossev.deviceProfileLib, line 1182
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1183
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1184
    } // library marker kkossev.deviceProfileLib, line 1185
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1186
    return fncmd // library marker kkossev.deviceProfileLib, line 1187
} // library marker kkossev.deviceProfileLib, line 1188

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1190
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1191
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1192
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1193
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1194
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1195
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1196

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1198
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1199

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1201
    int value // library marker kkossev.deviceProfileLib, line 1202
    try { // library marker kkossev.deviceProfileLib, line 1203
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1204
    } // library marker kkossev.deviceProfileLib, line 1205
    catch (e) { // library marker kkossev.deviceProfileLib, line 1206
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1207
        return false // library marker kkossev.deviceProfileLib, line 1208
    } // library marker kkossev.deviceProfileLib, line 1209
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1210
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1211
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1212
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1213
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1214
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1215
        return false // library marker kkossev.deviceProfileLib, line 1216
    } // library marker kkossev.deviceProfileLib, line 1217
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1218
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1219
} // library marker kkossev.deviceProfileLib, line 1220

/** // library marker kkossev.deviceProfileLib, line 1222
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1223
 * // library marker kkossev.deviceProfileLib, line 1224
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1225
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1226
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1227
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1228
 * // library marker kkossev.deviceProfileLib, line 1229
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1230
 */ // library marker kkossev.deviceProfileLib, line 1231
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1232
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1233
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1234
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1235
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1236

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1238
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1239

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1241
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1242
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1243
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1244
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1245
        return false // library marker kkossev.deviceProfileLib, line 1246
    } // library marker kkossev.deviceProfileLib, line 1247
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1248
} // library marker kkossev.deviceProfileLib, line 1249

/* // library marker kkossev.deviceProfileLib, line 1251
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1252
 */ // library marker kkossev.deviceProfileLib, line 1253
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1254
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1255
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1256
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1257
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1258
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1259
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1260
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1261
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1262
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1263
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1264
        } // library marker kkossev.deviceProfileLib, line 1265
    } // library marker kkossev.deviceProfileLib, line 1266
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1267

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1269
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1270
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1271
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1272
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1273
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1274
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1275
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1276
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1277
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1278
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1279
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1280
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1281
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1282
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1283
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1284
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1285

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1287
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1288
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1289
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1290
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1291
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1292
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1293
        } // library marker kkossev.deviceProfileLib, line 1294
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1295
    } // library marker kkossev.deviceProfileLib, line 1296

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1298
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1299
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1300
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1301
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1302
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1303
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1304
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1305
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1306
            } // library marker kkossev.deviceProfileLib, line 1307
        } // library marker kkossev.deviceProfileLib, line 1308
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1309
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1310
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1311
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1312
            } // library marker kkossev.deviceProfileLib, line 1313
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1314
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1315
            try { // library marker kkossev.deviceProfileLib, line 1316
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1317
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            catch (e) { // library marker kkossev.deviceProfileLib, line 1320
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1321
            } // library marker kkossev.deviceProfileLib, line 1322
        } // library marker kkossev.deviceProfileLib, line 1323
    } // library marker kkossev.deviceProfileLib, line 1324
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1325
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1326
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1327
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1328
    } // library marker kkossev.deviceProfileLib, line 1329

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1331
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1332
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1333
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1334
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1335
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1336
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1337
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1338
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1339
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1340
            } // library marker kkossev.deviceProfileLib, line 1341
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1342
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1343
            } // library marker kkossev.deviceProfileLib, line 1344

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1346
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1347
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1348
            // continue ... // library marker kkossev.deviceProfileLib, line 1349
            } // library marker kkossev.deviceProfileLib, line 1350

            else { // library marker kkossev.deviceProfileLib, line 1352
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1353
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1354
                } // library marker kkossev.deviceProfileLib, line 1355
                else { // library marker kkossev.deviceProfileLib, line 1356
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1357
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1358
                } // library marker kkossev.deviceProfileLib, line 1359
            } // library marker kkossev.deviceProfileLib, line 1360
        } // library marker kkossev.deviceProfileLib, line 1361

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1363
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1364
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1365
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1366
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1367
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1368
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1369
        } // library marker kkossev.deviceProfileLib, line 1370
        else { // library marker kkossev.deviceProfileLib, line 1371
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1372
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1373
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1374
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1375
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1376
            } // library marker kkossev.deviceProfileLib, line 1377
        } // library marker kkossev.deviceProfileLib, line 1378
    } // library marker kkossev.deviceProfileLib, line 1379
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1380
} // library marker kkossev.deviceProfileLib, line 1381

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1383
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1384
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1385
    //debug = true // library marker kkossev.deviceProfileLib, line 1386
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1387
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1388
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1389
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1390
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1391
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1392
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1393
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1394
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1395
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1396
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1397
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1398
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1399
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1400
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1401
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1402
            try { // library marker kkossev.deviceProfileLib, line 1403
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1404
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1405
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1406
            } // library marker kkossev.deviceProfileLib, line 1407
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1408
            try { // library marker kkossev.deviceProfileLib, line 1409
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1410
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1411
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1412
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1413
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1414
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1415
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1416
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1417
                    } // library marker kkossev.deviceProfileLib, line 1418
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1419
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1420
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1421
                    } else { // library marker kkossev.deviceProfileLib, line 1422
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1423
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1424
                    } // library marker kkossev.deviceProfileLib, line 1425
                } // library marker kkossev.deviceProfileLib, line 1426
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1427
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1428
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1429
            } // library marker kkossev.deviceProfileLib, line 1430
            catch (e) { // library marker kkossev.deviceProfileLib, line 1431
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1432
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1433
                try { // library marker kkossev.deviceProfileLib, line 1434
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1435
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1436
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1437
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1438
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1439
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1440
                } // library marker kkossev.deviceProfileLib, line 1441
            } // library marker kkossev.deviceProfileLib, line 1442
        } // library marker kkossev.deviceProfileLib, line 1443
        total ++ // library marker kkossev.deviceProfileLib, line 1444
    } // library marker kkossev.deviceProfileLib, line 1445
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1446
    return true // library marker kkossev.deviceProfileLib, line 1447
} // library marker kkossev.deviceProfileLib, line 1448

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1450
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1451
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1452
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1453
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1454
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1455
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1456
    } // library marker kkossev.deviceProfileLib, line 1457
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1458
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1459
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1460
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1461
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1462
    } // library marker kkossev.deviceProfileLib, line 1463
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1464
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1465
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1466
} // library marker kkossev.deviceProfileLib, line 1467

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1469
    int count = 0 // library marker kkossev.deviceProfileLib, line 1470
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1471
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1472
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1473
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1474
            count++ // library marker kkossev.deviceProfileLib, line 1475
        } // library marker kkossev.deviceProfileLib, line 1476
    } // library marker kkossev.deviceProfileLib, line 1477
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1478
} // library marker kkossev.deviceProfileLib, line 1479

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1481
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1482
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1483
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1484
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1485
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1486
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1487
            } // library marker kkossev.deviceProfileLib, line 1488
        } // library marker kkossev.deviceProfileLib, line 1489
    } // library marker kkossev.deviceProfileLib, line 1490
} // library marker kkossev.deviceProfileLib, line 1491

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~

// ~~~~~ start include (177) kkossev.reportingLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.reportingLib, line 1
library( // library marker kkossev.reportingLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Reporting Config Library', name: 'reportingLib', namespace: 'kkossev', // library marker kkossev.reportingLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/reportingLib.groovy', documentationLink: '', // library marker kkossev.reportingLib, line 4
    version: '3.2.1' // library marker kkossev.reportingLib, line 5
) // library marker kkossev.reportingLib, line 6
/* // library marker kkossev.reportingLib, line 7
 *  Zigbee Reporting Config Library // library marker kkossev.reportingLib, line 8
 * // library marker kkossev.reportingLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.reportingLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.reportingLib, line 11
 * // library marker kkossev.reportingLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.reportingLib, line 13
 * // library marker kkossev.reportingLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.reportingLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.reportingLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.reportingLib, line 17
 * // library marker kkossev.reportingLib, line 18
 * ver. 3.2.0  2024-05-25 kkossev  - added reportingLib.groovy // library marker kkossev.reportingLib, line 19
 * ver. 3.2.1  2025-03-09 kkossev  - configureReportingInt() integer parameters overload // library marker kkossev.reportingLib, line 20
 * // library marker kkossev.reportingLib, line 21
 *                                   TODO: // library marker kkossev.reportingLib, line 22
*/ // library marker kkossev.reportingLib, line 23

static String reportingLibVersion()   { '3.2.1' } // library marker kkossev.reportingLib, line 25
static String reportingLibStamp() { '2025/03/09 7:29 PM' } // library marker kkossev.reportingLib, line 26

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

List<String> configureReportingInt(String operation, String measurement,  int minTime=0, int maxTime=0, int delta=0, boolean sendNow=true ) { // library marker kkossev.reportingLib, line 46
    configureReporting(operation, measurement, minTime.toString(), maxTime.toString(), delta.toString(), sendNow) // library marker kkossev.reportingLib, line 47
} // library marker kkossev.reportingLib, line 48

List<String> configureReporting(String operation, String measurement,  String minTime='0', String maxTime='0', String delta='0', boolean sendNow=true ) { // library marker kkossev.reportingLib, line 50
    int intMinTime = safeToInt(minTime) // library marker kkossev.reportingLib, line 51
    int intMaxTime = safeToInt(maxTime) // library marker kkossev.reportingLib, line 52
    int intDelta = safeToInt(delta) // library marker kkossev.reportingLib, line 53
    String epString = state.destinationEP // library marker kkossev.reportingLib, line 54
    int ep = safeToInt(epString) // library marker kkossev.reportingLib, line 55
    if (ep == null || ep == 0) { // library marker kkossev.reportingLib, line 56
        ep = 1 // library marker kkossev.reportingLib, line 57
        epString = '01' // library marker kkossev.reportingLib, line 58
    } // library marker kkossev.reportingLib, line 59

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )" // library marker kkossev.reportingLib, line 61

    List<String> cmds = [] // library marker kkossev.reportingLib, line 63

    switch (measurement) { // library marker kkossev.reportingLib, line 65
        case ONOFF : // library marker kkossev.reportingLib, line 66
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 67
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 68
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 69
            } // library marker kkossev.reportingLib, line 70
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 71
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", 'delay 251', ]    // disable Plug automatic reporting // library marker kkossev.reportingLib, line 72
            } // library marker kkossev.reportingLib, line 73
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back // library marker kkossev.reportingLib, line 74
            break // library marker kkossev.reportingLib, line 75
        case ENERGY :    // default delta = 1 Wh (0.001 kWh) // library marker kkossev.reportingLib, line 76
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 77
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta * getEnergyDiv() as int)) // library marker kkossev.reportingLib, line 78
            } // library marker kkossev.reportingLib, line 79
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 80
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 81
            } // library marker kkossev.reportingLib, line 82
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252) // library marker kkossev.reportingLib, line 83
            break // library marker kkossev.reportingLib, line 84
        case INST_POWER :        // 0x702:0x400 // library marker kkossev.reportingLib, line 85
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 86
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int)) // library marker kkossev.reportingLib, line 87
            } // library marker kkossev.reportingLib, line 88
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 89
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 90
            } // library marker kkossev.reportingLib, line 91
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253) // library marker kkossev.reportingLib, line 92
            break // library marker kkossev.reportingLib, line 93
        case POWER :        // Active power default delta = 1 // library marker kkossev.reportingLib, line 94
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 95
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee // library marker kkossev.reportingLib, line 96
            } // library marker kkossev.reportingLib, line 97
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 98
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 99
            } // library marker kkossev.reportingLib, line 100
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254) // library marker kkossev.reportingLib, line 101
            break // library marker kkossev.reportingLib, line 102
        case VOLTAGE :    // RMS Voltage default delta = 1 // library marker kkossev.reportingLib, line 103
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 104
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getVoltageDiv() as int)) // library marker kkossev.reportingLib, line 105
            } // library marker kkossev.reportingLib, line 106
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 107
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 108
            } // library marker kkossev.reportingLib, line 109
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255) // library marker kkossev.reportingLib, line 110
            break // library marker kkossev.reportingLib, line 111
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A // library marker kkossev.reportingLib, line 112
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 113
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getCurrentDiv() as int)) // library marker kkossev.reportingLib, line 114
            } // library marker kkossev.reportingLib, line 115
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 116
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 117
            } // library marker kkossev.reportingLib, line 118
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256) // library marker kkossev.reportingLib, line 119
            break // library marker kkossev.reportingLib, line 120
        case FREQUENCY :    // added 03/27/2023 // library marker kkossev.reportingLib, line 121
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 122
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getFrequencyDiv() as int)) // library marker kkossev.reportingLib, line 123
            } // library marker kkossev.reportingLib, line 124
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 125
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 126
            } // library marker kkossev.reportingLib, line 127
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257) // library marker kkossev.reportingLib, line 128
            break // library marker kkossev.reportingLib, line 129
        case POWER_FACTOR : // added 03/27/2023 // library marker kkossev.reportingLib, line 130
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 131
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getPowerFactorDiv() as int)) // library marker kkossev.reportingLib, line 132
            } // library marker kkossev.reportingLib, line 133
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258) // library marker kkossev.reportingLib, line 134
            break // library marker kkossev.reportingLib, line 135
        default : // library marker kkossev.reportingLib, line 136
            break // library marker kkossev.reportingLib, line 137
    } // library marker kkossev.reportingLib, line 138
    if (cmds != null) { // library marker kkossev.reportingLib, line 139
        if (sendNow == true) { // library marker kkossev.reportingLib, line 140
            sendZigbeeCommands(cmds) // library marker kkossev.reportingLib, line 141
        } // library marker kkossev.reportingLib, line 142
        else { // library marker kkossev.reportingLib, line 143
            return cmds // library marker kkossev.reportingLib, line 144
        } // library marker kkossev.reportingLib, line 145
    } // library marker kkossev.reportingLib, line 146
} // library marker kkossev.reportingLib, line 147


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
