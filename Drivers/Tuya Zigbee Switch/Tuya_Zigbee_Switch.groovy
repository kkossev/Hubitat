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
 * ver. 3.3.0  2025-03-10 kkossev  - healthCheck by pinging the device; added Sonoff ZBMINIR2; added 'ZBMINI-L' to a new SWITCH_SONOFF_GENERIC profile
 * ver. 3.3.1  2025-03-13 kkossev  - added activeEndpoints() command in test mode; sending ZCL Default Response in ZBMINIR2 Detach Relay Mode; added PushableButton capability for ZBMINIR2;
 * ver. 3.3.2  2025-03-29 kkossev  - fixed ZCL Default Response in ZBMINIR2 Detach Relay Mode; added updateFirmware() command; added toggle() command; added delayedPowerOnState and delayedPowerOnTime preferences
 * ver. 3.4.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException in HE platform update 2.4.1.155
 * ver. 3.5.0  2025-09-15 kkossev  - (dev. branch) commonLib 4.0.0 allignment;
 *
 *                                   TODO: initialize 'switch' to unknown
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { '3.5.0' }
static String timeStamp() { '2025/09/15 8:05 PM' }

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

#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.deviceProfileLib
#include kkossev.reportingLib
#include kkossev.groupsLib
#include kkossev.buttonLib  // for the ZBMINIR2 Detach Relay Mode button

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
        attribute 'detachRelayMode',  'enum', ['Disabled', 'Enabled']
        attribute 'externalTriggerMode', 'enum', ['edge', 'pulse', 'following(off)', 'following(on)']
        attribute 'backLight', 'enum', ['Disabled', 'Enabled']
        attribute 'faultCode', 'number'
        attribute 'delayedPowerOnState', 'enum', ['Disabled', 'Enabled']
        attribute 'delayedPowerOnTime', 'number'
        attribute 'switchActions', 'enum', ['On', 'Off', 'Toggle']

        command 'toggle'
        command 'updateFirmware'
        command 'sendCommand', [
            [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']],
            [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']]
        ]
        command 'setPar', [
                [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']],
                [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']]
        ]
        if (_DEBUG) {
            command 'activeEndpoints', []
            command 'testDetachRelayMode', ['simulate the detach relay mode button']
        }

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
            capabilities  : ['Switch': true, 'Button': true],   // for testing only - TOBEDEL !
            preferences   : [powerOnBehavior:'0x0006:0x4003'/*, switchActions:'0x0007:0x0010'*/],
            commands      : [resetStats:'', refresh:'', customInitEvents:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0006,0007,0B05,FC57', outClusters:'0019', model:'ZBMINIL2', manufacturer:'SONOFF', deviceJoinName: 'SONOFF ZBMINIL2']
            ],
            attributes    : [
                [at:'0x0006:0x4003', name:'powerOnBehavior', type:'enum',   rw: 'rw', min:0,   max:255,    defVal:'255',   scale:1,    map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior'],
                [at:'0x0007:0x0000', name:'switchType',      type:'enum',   dt:'0x20', rw: 'ro',  map:[0:'Toggle', 1:'Momentary', 2:'Multifunction'], description:'Switch Type: '],                             // unusable
                [at:'0x0007:0x0010', name:'switchActions',   type:'enum',   dt:'0x30', rw: 'rw',  map:[0:'On', 1:'Off', 2:'Toggle'], title:'<b>Switch Actions</b>', description:'Select Switch Actions ']       // unusable
            ],
            refresh       : [ 'powerOnBehavior', 'switchType', 'switchActions'],
            configuration : ['0x0006':['onOffReporting':[1, 1800, 0]], '0x0020':['unbind':true]],
            deviceJoinName: 'Sonoff ZBMini L2 Switch'
    ],

    'SWITCH_SONOFF_ZBMINIR2' : [        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/sonoff.ts#L1495-L1558
            description   : 'Sonoff ZBMINI R2 Switch',
            models        : ['ZBMINIR2'],
            device        : [type: 'switch', powerSource: 'mains', isSleepy:false],
            capabilities  : ['Switch': true, 'Button': true],
            preferences   : [powerOnBehavior:'0x0006:0x4003', turboMode:'0xFC11:0x0012', networkIndicator:'0xFC11:0x0001', delayedPowerOnState:'0xFC11:0x0014', delayedPowerOnTime:'0xFC11:0x0015', externalTriggerMode: '0xFC11:0x0016', detachRelayMode:'0xFC11:0x0017'],
            commands      : [resetStats:'', refresh:'', initialize:'', updateAllPreferences: '',resetPreferencesToDefaults:'', validateAndFixPreferences:''],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0006,0B05,FC57,FC11', outClusters:'0003,0006,0019', model:'ZBMINIR2', manufacturer:'SONOFF', application:"10", deviceJoinName: 'SONOFF ZBMINI R2']
            ],
            attributes    : [           // defaults are :    number: DataType.INT16 (0x29) // decimal: DataType.INT16 (0x29) // enum: DataType.ENUM8 (0x30) .  BOOLEAN is 0x10 // DataType.UNT16 is 0x21 // UINT8 is 0x20
                [at:'0x0006:0x4003', name:'powerOnBehavior',     type:'enum',   rw: 'rw',  min:0,   max:255,    defVal:'255', map:[0:'Turn power Off', 1:'Turn power On', 255:'Restore previous state'], title:'<b>Power On Behavior</b>', description:'Power On Behavior'],
                [at:'0xFC11:0x0001', name:'networkIndicator',    type:'enum',   dt:'0x10', rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Network Indicator.</b>', description:'Enable/disable network indicator.'],          // BOOLEAN
                //[at:'0xFC11:0x0002', name:'backLight',           type:'enum',   dt:'0x10', rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Backlight.</b>', description:'Enable/disable Backlight.'],     // BOOLEAN
                [at:'0xFC11:0x0010', name:'faultCode',           type:'number', rw: 'ro',  title:'<b>Fault Code</b>' ],                                                                                                                                                                                                    // INT32
                [at:'0xFC11:0x0012', name:'turboMode',           type:'enum',   dt:'0x29', rw: 'rw', min:9,   max:20,    defVal:'9', map:[9:'Disabled', 20:'Enabled'], title:'<b>Zigbee Radio Power Turbo Mode.</b>', description:'Enable/disable Zigbee radio power Turbo mode.'],    // INT16
                [at:'0xFC11:0x0014', name:'delayedPowerOnState', type:'enum',   dt:'0x10', advanced:true, rw: 'rw', defVal:'0', map:[0:'Disabled',  1:'Enabled'], title:'<b>Delayed Power On State.</b>', description:'Enable/disable Delayed Power On State.'],                  // BOOLEAN
                [at:'0xFC11:0x0015', name:'delayedPowerOnTime',  type:'number', dt:'0x23', advanced:true, rw: 'rw', title:'<b>Delayed Power On Time</b>', description: 'Delayed Power On Time' ],                     // UINT16
                [at:'0xFC11:0x0016', name:'externalTriggerMode', type:'enum',   dt:'0x20', advanced:true, rw: 'rw', defVal:'0', map:[0:'edge',  1:'pulse', 2:'following(off)', 130:'following(on)'], title:'<b>External Trigger Mode</b>', description: 'Select the External Trigger Mode' ],                     // UINT8 //                 externalTriggerMode: {ID: 0x0016, type: Zcl.DataType.UINT8},
                [at:'0xFC11:0x0017', name:'detachRelayMode',     type:'enum',   dt:'0x10', /*advanced:true, */rw: 'rw', defVal:'0', map:[0:'Disabled', 1:'Enabled'], title:'<b>Detach Relay Mode</b>', description: 'Enable/Disable detach relay mode' ],  // BOOLEAN //detachRelayMode: {ID: 0x0017, type: Zcl.DataType.BOOLEAN},
                // ZBM5
                // [at:'0xFC11:0x0018', name:'deviceWorkMode',      type:'number', rw: 'rw', title:'<b>Device Work Mode</b>', description: 'Device Work Mode' ],                   // UINT8 //deviceWorkMode: {ID: 0x0018, type: Zcl.DataType.UINT8},
                //[at:'0xFC11:0x0019', name:'detachRelayMode2',    type:'number', rw: 'rw', title:'<b>Detach Relay Mode 2</b>', description: 'Detach Relay Mode 2' ],             // BITMAP8 //detachRelayMode2: {ID: 0x0019, type: Zcl.DataType.BITMAP8},
            ],
            refresh       : [ 'powerOnBehavior', 'turboMode', 'networkIndicator', 'delayedPowerOnState', 'delayedPowerOnTime', 'detachRelayMode', 'externalTriggerMode'],
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

void customPush() {    //pushableButton capability
    Integer buttonNumber = 1
    logDebug "push button $buttonNumber"
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = false)    // defined in buttonLib
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

void customInitEvents(boolean fullInit=false) {
    if (DEVICE?.capabilities?.Button == true) {
        logDebug "customInitEvents() : Button capability is enabled  (fullInit=${fullInit})"
        sendNumberOfButtonsEvent(1)
        sendSupportedButtonValuesEvent(['pushed'])      // defined in buttonLib
    }
    else {
        logTrace "customInitEvents() : Button capability is disabled (fullInit=${fullInit})"
    }
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
    logTrace "customParseOnOffCluster: zigbee received cluster 0x0006 command ${descMap.command} attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logTrace "customParseOnOffCluster: cluster 0x0006 command ${descMap.command} attribute 0x${descMap.attrId} (value ${descMap.value})"
        if (descMap.attrId == null) {
            logTrace 'null attribute id'
            if (descMap.command == '02' && descMap.data?.isEmpty())
            {
                // !!! When ZBMINIR2 reports the OnOff Client Toggle command -  expects a ZCL Default Response !!!
                // https://community.hubitat.com/t/zigbee-zcl-default-response-0x0b/11151/10?u=kkossev 
                // "he raw ${device.deviceNetworkId} 1 1 0x0006 {08 01 0B 0A 00}"
                logDebug ' => Detach Relay Mode - contact open or closed ...'
                //sendZigbeeCommands(["he raw ${device.deviceNetworkId} 1 1 0x0006 {08 01 0B 0A 00}"])
                sendZigbeeCommands(["he raw ${device.deviceNetworkId} 1 1 0x0006 {08 01 0B 02 00}"])
                customPush()
            }
            else {
                logTrace "customParseOnOffCluster: unprocessed 0x0006 command ${descMap.command} attribute 0x${descMap.attrId} (value ${descMap.value})"
            }
        }
        else {
            standardParseOnOffCluster(descMap)
        }
    }
}

public void standardParseonOffConfigurationCluster (final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "standardParseonOffConfigurationCluster: zigbee received cluster 0x0007 command ${descMap.command} attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    boolean result = processClusterAttributeFromDeviceProfile(descMap)    // deviceProfileLib
    if (result == false) {
        logTrace "standardParseonOffConfigurationCluster: unprocessed 0x0007 command ${descMap.command} attribute 0x${descMap.attrId} (value ${descMap.value})"
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

void activeEndpoints() {
    logInfo "sending activeEndpoints() request..."
    List<String> cmds = []
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get all the endpoints...
    String endpointIdTemp = device.endpointId ?: '01'
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} $endpointIdTemp} {0x0000}"]
    sendZigbeeCommands(cmds)
}

void parseSimpleDescriptorResponse(Map descMap) {
    logDebug "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, length:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
    if (logEnable == true) { log.info "${device.displayName} Endpoint: ${descMap.data[5]} Application Device:${descMap.data[9]}${descMap.data[8]}, Application Version:${descMap.data[10]}" }
    int inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    String inputClusterList = ''
    for (int i in 1..inputClusterCount) {
        inputClusterList += descMap.data[13 + (i - 1) * 2] + descMap.data[12 + (i - 1) * 2 ] + ','
    }
    inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
    if (logEnable == true) { log.info "${device.displayName} Input Cluster Count: ${inputClusterCount} Input Cluster List : ${inputClusterList}" }
    if (getDataValue('inClusters') != inputClusterList)  {
        if (logEnable == true) { log.warn "${device.displayName} inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!" }
        updateDataValue('inClusters', inputClusterList)
    }

    int outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12 + inputClusterCount * 2])
    String outputClusterList = ''
    if (outputClusterCount >= 1) {
        for (int i in 1..outputClusterCount) {
            outputClusterList += descMap.data[14 + inputClusterCount * 2 + (i - 1) * 2] + descMap.data[13 + inputClusterCount * 2 + (i - 1) * 2] + ','
        }
        outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
    }

    if (logEnable == true) { log.info "${device.displayName} Output Cluster Count: ${outputClusterCount} Output Cluster List : ${outputClusterList}" }
    if (getDataValue('outClusters') != outputClusterList)  {
        if (logEnable == true) { log.warn "${device.displayName} outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} -  will be updated!" }
        updateDataValue('outClusters', outputClusterList)
    }
}

void updateFirmware() {
    sendInfoEvent("updateFirmware: check 'Hub' live logs...")
    sendZigbeeCommands(zigbee.updateFirmware())
}


void toggle() {
    logDebug "toggling switch.."
    if (device.getDataValue('model') != "TS0601") {
        sendZigbeeCommands(zigbee.command(0x0006,0x02))
    }
    else {
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off') {
            on()
        }
        else {
            off()
        }
    }
}


void testDetachRelayMode() {
    logDebug "testDetachRelayMode()"
    List<String> cmds = []
    // cmds += ["he raw ${device.deviceNetworkId} 1 1 0xFC11 {0A 01 0B 0A 00}"] // ZCL Default Response
    parse('catchall: 0104 0006 01 01 0040 00 464B 01 00 0000 02 00')
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
