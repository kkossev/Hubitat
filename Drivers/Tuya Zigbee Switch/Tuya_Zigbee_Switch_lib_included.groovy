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
 * ver. 3.0.0  2023-11-24 kkossev  - (dev. branch) use commonLib; added AlwaysOn option; added ignore duplcated on/off events option;
 * ver. 3.0.1  2023-11-25 kkossev  - (dev. branch) added LEDVANCE Plug 03; added TS0101 _TZ3000_pnzfdr9y SilverCrest Outdoor Plug Model HG06619 manufactured by Lidl; added configuration for 0x0006 cluster reproting for all devices;
 * ver. 3.0.2  2023-12-12 kkossev  - (dev. branch) added ZBMINIL2
 * ver. 3.0.3  2024-02-24 kkossev  - commonLib 3.0.3 allignment
 *
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { "3.0.3" }
static String timeStamp() {"2024/02/24 10:47 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = "Switch"
@Field static final String DEVICE_TYPE = "Switch"


// @Field static final Boolean _THREE_STATE = true  // move from the commonLib here?

metadata {
    definition (
        name: 'Tuya Zigbee Switch',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Switch/Tuya_Zigbee_Switch_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]]
            command "tuyaTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"]
            ]
        }
        capability "Actuator"
        capability "Switch"
        if (_THREE_STATE == true) {
            attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String>
        }

        // deviceType specific capabilities, commands and attributes
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) {
            command "zigbeeGroups", [
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>],
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]]
            ]
        }

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0006,0007,0B05,FC57", outClusters:"0019", model:"ZBMINIL2", manufacturer:"SONOFF", deviceJoinName: "SONOFF ZBMINIL2"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0008,1000,FC7C", outClusters:"0005,0019,0020,1000", model:"TRADFRI control outlet", manufacturer:"IKEA of Sweden", deviceJoinName: "TRADFRI control outlet"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006,0008,1000,FC7C", outClusters:"0019,0020,1000", model:"TRADFRI control outlet", manufacturer:"IKEA of Sweden", deviceJoinName: "TRADFRI control outlet"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0005,0006", outClusters:"0019,000A", model:"TS0101", manufacturer:"_TZ3000_pnzfdr9y", deviceJoinName: "SONOFF ZBMINIL2"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0006,0B05,FC0F", outClusters:"0019", model:"Plug Z3", manufacturer:"LEDVANCE", deviceJoinName: "Plug Z3"
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        input (name: "alwaysOn", type: "bool", title: "<b>Always On</b>", description: "<i>Disable switching OFF for plugs that must be always On</i>", defaultValue: false)
        if (advancedOptions == true || advancedOptions == true) {
            input (name: "ignoreDuplicated", type: "bool", title: "<b>Ignore Duplicated Switch Events</b>", description: "<i>Some switches and plugs send periodically the switch status as a heart-beet </i>", defaultValue: false)
        }

    }

}

@Field static final String ONOFF = "Switch"
@Field static final String POWER = "Power"
@Field static final String INST_POWER = "InstPower"
@Field static final String ENERGY = "Energy"
@Field static final String VOLTAGE = "Voltage"
@Field static final String AMPERAGE = "Amperage"
@Field static final String FREQUENCY = "Frequency"
@Field static final String POWER_FACTOR = "PowerFactor"

def isZBMINIL2()   { /*true*/(device?.getDataValue('model') ?: 'n/a') in ['ZBMINIL2'] }

List<String> customRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

void customInitVars(boolean fullInit=false) {
    logDebug "customInitVars(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.ignoreDuplicated == null) device.updateSetting("ignoreDuplicated", false)
}

void customInitEvents(boolean fullInit=false) {
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (isZBMINIL2()) {
        logDebug "customConfigureDevice() : unbind ZBMINIL2 poll control cluster"
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
    cmds += configureReporting("Write", ONOFF,  "1", "65534", "0", sendNow=false)    // switch state should be always reported
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

void customParseElectricalMeasureCluster(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseMeteringCluster(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

def configureReporting(String operation, String measurement,  String minTime="0", String maxTime="0", String delta="0", Boolean sendNow=true ) {
    int intMinTime = safeToInt(minTime)
    int intMaxTime = safeToInt(maxTime)
    int intDelta = safeToInt(delta)
    String epString = state.destinationEP
    def ep = safeToInt(epString)
    if (ep==null || ep==0) {
        ep = 1
        epString = "01"
    }

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )"

    List<String> cmds = []

    switch (measurement) {
        case ONOFF :
            if (operation == "Write") {
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 251", ]
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", "delay 251", ]
            }
            else if (operation == "Disable") {
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", "delay 251", ]    // disable switch automatic reporting
            }
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back
            break
        case ENERGY :    // default delta = 1 Wh (0.001 kWh)
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta*getEnergyDiv() as int))
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252)
            break
        case INST_POWER :        // 0x702:0x400
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta*getPowerDiv() as int))
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253)
            break
        case POWER :        // Active power default delta = 1
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta*getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254)
            break
        case VOLTAGE :    // RMS Voltage default delta = 1
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta*getVoltageDiv() as int))
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255)
            break
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta*getCurrentDiv() as int))
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256)
            break
        case FREQUENCY :    // added 03/27/2023
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta*getFrequencyDiv() as int))
            }
            else if (operation == "Disable") {
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient
            }
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257)
            break
        case POWER_FACTOR : // added 03/27/2023
            if (operation == "Write") {
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta*getPowerFactorDiv() as int))
            }
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258)
            break
        default :
            break
    }
    if (cmds != null) {
        if (sendNow == true) {
            sendZigbeeCommands(cmds)
        }
        else {
            return cmds
        }
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.3', // library marker kkossev.commonLib, line 10
    documentationLink: '' // library marker kkossev.commonLib, line 11
) // library marker kkossev.commonLib, line 12
/* // library marker kkossev.commonLib, line 13
  *  Common ZCL Library // library marker kkossev.commonLib, line 14
  * // library marker kkossev.commonLib, line 15
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 16
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 19
  * // library marker kkossev.commonLib, line 20
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 21
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 22
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 23
  * // library marker kkossev.commonLib, line 24
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 25
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * // library marker kkossev.commonLib, line 28
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 29
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 32
  * ver. 3.0.1  2023-12-06 kkossev  - nfo event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 33
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 34
  * ver. 3.0.3  2024-03-17 kkossev  - (dev.branch) more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * // library marker kkossev.commonLib, line 36
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 37
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 38
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 39
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 40
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 43
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 44
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 45
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 46
 * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '3.0.3' } // library marker kkossev.commonLib, line 50
String thermostatLibStamp() { '2024/03/04 9:56 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 63

metadata { // library marker kkossev.commonLib, line 65
        if (_DEBUG) { // library marker kkossev.commonLib, line 66
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 67
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 69
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 70
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 72
            ] // library marker kkossev.commonLib, line 73
        } // library marker kkossev.commonLib, line 74

        // common capabilities for all device types // library marker kkossev.commonLib, line 76
        capability 'Configuration' // library marker kkossev.commonLib, line 77
        capability 'Refresh' // library marker kkossev.commonLib, line 78
        capability 'Health Check' // library marker kkossev.commonLib, line 79

        // common attributes for all device types // library marker kkossev.commonLib, line 81
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 82
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 83
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 84

        // common commands for all device types // library marker kkossev.commonLib, line 86
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 87
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 88

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 90
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 91
            if (_DEBUG) { // library marker kkossev.commonLib, line 92
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 93
            } // library marker kkossev.commonLib, line 94
        } // library marker kkossev.commonLib, line 95
        if (_DEBUG || (deviceType in ['Dimmer', 'ButtonDimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 96
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 97
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 98
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 99
            ] // library marker kkossev.commonLib, line 100
        } // library marker kkossev.commonLib, line 101
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 102
            capability 'Sensor' // library marker kkossev.commonLib, line 103
        } // library marker kkossev.commonLib, line 104
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 105
            capability 'MotionSensor' // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Thermostat', 'Fingerbot', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 108
            capability 'Actuator' // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'Fingerbot', 'ButtonDimmer', 'AqaraCube']) { // library marker kkossev.commonLib, line 111
            capability 'Battery' // library marker kkossev.commonLib, line 112
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 113
        } // library marker kkossev.commonLib, line 114
        if (deviceType in  ['Thermostat']) { // library marker kkossev.commonLib, line 115
            capability 'Thermostat' // library marker kkossev.commonLib, line 116
        } // library marker kkossev.commonLib, line 117
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Fingerbot', 'Bulb']) { // library marker kkossev.commonLib, line 118
            capability 'Switch' // library marker kkossev.commonLib, line 119
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 120
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 121
            } // library marker kkossev.commonLib, line 122
        } // library marker kkossev.commonLib, line 123
        if (deviceType in ['Dimmer', 'ButtonDimmer', 'Bulb']) { // library marker kkossev.commonLib, line 124
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 125
        } // library marker kkossev.commonLib, line 126
        if (deviceType in  ['Button', 'ButtonDimmer', 'AqaraCube']) { // library marker kkossev.commonLib, line 127
            capability 'PushableButton' // library marker kkossev.commonLib, line 128
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 129
            capability 'HoldableButton' // library marker kkossev.commonLib, line 130
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 131
        } // library marker kkossev.commonLib, line 132
        if (deviceType in  ['Device', 'Fingerbot']) { // library marker kkossev.commonLib, line 133
            capability 'Momentary' // library marker kkossev.commonLib, line 134
        } // library marker kkossev.commonLib, line 135
        if (deviceType in  ['Device', 'THSensor', 'Thermostat']) { // library marker kkossev.commonLib, line 136
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 137
        } // library marker kkossev.commonLib, line 138
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 139
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 140
        } // library marker kkossev.commonLib, line 141
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 142
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 143
        } // library marker kkossev.commonLib, line 144

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 146
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 147

    preferences { // library marker kkossev.commonLib, line 149
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 150
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 151
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 152

        if (device) { // library marker kkossev.commonLib, line 154
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) && !isZigUSB()) { // library marker kkossev.commonLib, line 155
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 156
                input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 157
            } // library marker kkossev.commonLib, line 158
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 159
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 160
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 161
            } // library marker kkossev.commonLib, line 162

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 164
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 165
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 166
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 167
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 168
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 169
                } // library marker kkossev.commonLib, line 170
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 171
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 172
                } // library marker kkossev.commonLib, line 173
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 174
            } // library marker kkossev.commonLib, line 175
        } // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177
} // library marker kkossev.commonLib, line 178

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 180
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 181
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 182
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 183
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 184
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 185
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 186
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 187
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 188
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 189
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 190
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 191

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 193
    defaultValue: 1, // library marker kkossev.commonLib, line 194
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 195
] // library marker kkossev.commonLib, line 196
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 197
    defaultValue: 240, // library marker kkossev.commonLib, line 198
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 199
] // library marker kkossev.commonLib, line 200
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 201
    defaultValue: 0, // library marker kkossev.commonLib, line 202
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 203
] // library marker kkossev.commonLib, line 204

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 210
    defaultValue: 0, // library marker kkossev.commonLib, line 211
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 212
] // library marker kkossev.commonLib, line 213

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 215
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 216
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 217
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 218
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 219
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 220
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 221
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 222
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 223
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 224
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 225
] // library marker kkossev.commonLib, line 226

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 228
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 229
boolean isChattyDeviceReport(final String description)  { return false /*(description?.contains("cluster: FC7E")) */ } // library marker kkossev.commonLib, line 230
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 231
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 232
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 233
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 234
boolean isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 235
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 236
boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 237

/** // library marker kkossev.commonLib, line 239
 * Parse Zigbee message // library marker kkossev.commonLib, line 240
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 241
 */ // library marker kkossev.commonLib, line 242
void parse(final String description) { // library marker kkossev.commonLib, line 243
    checkDriverVersion() // library marker kkossev.commonLib, line 244
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 245
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 246
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 247
    setHealthStatusOnline() // library marker kkossev.commonLib, line 248

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 250
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 251
        /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.commonLib, line 252
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 253
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 254
            return // library marker kkossev.commonLib, line 255
        } // library marker kkossev.commonLib, line 256
        parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 259
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 260
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 261
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 262
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 263
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 264
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 267
        return // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 270

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 272
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 273
        return // library marker kkossev.commonLib, line 274
    } // library marker kkossev.commonLib, line 275
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 276
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 277
        return // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    if (!isChattyDeviceReport(description)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 280
    // // library marker kkossev.commonLib, line 281
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 282
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 283
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 284

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 286
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 287
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 288
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 289
            break // library marker kkossev.commonLib, line 290
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 291
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 292
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 293
            break // library marker kkossev.commonLib, line 294
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 295
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 296
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 299
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 300
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 303
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 304
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 307
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 311
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 312
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 315
            if (isZigUSB()) { // library marker kkossev.commonLib, line 316
                parseZigUSBAnlogInputCluster(description) // library marker kkossev.commonLib, line 317
            } // library marker kkossev.commonLib, line 318
            else { // library marker kkossev.commonLib, line 319
                parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 320
                descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 321
            } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 324
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 325
            break // library marker kkossev.commonLib, line 326
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 327
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 328
            break // library marker kkossev.commonLib, line 329
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 330
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 331
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 334
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 335
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 336
            break // library marker kkossev.commonLib, line 337
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 338
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 339
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 342
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 343
            break // library marker kkossev.commonLib, line 344
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 345
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 346
            break // library marker kkossev.commonLib, line 347
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 348
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 349
            break // library marker kkossev.commonLib, line 350
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 351
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 352
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 353
            break // library marker kkossev.commonLib, line 354
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 355
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 356
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 357
            break // library marker kkossev.commonLib, line 358
        case 0xE002 : // library marker kkossev.commonLib, line 359
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 360
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 361
            break // library marker kkossev.commonLib, line 362
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 363
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 364
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 367
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 368
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 369
            break // library marker kkossev.commonLib, line 370
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 371
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 372
            break // library marker kkossev.commonLib, line 373
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 374
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 375
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 376
            break // library marker kkossev.commonLib, line 377
        default: // library marker kkossev.commonLib, line 378
            if (settings.logEnable) { // library marker kkossev.commonLib, line 379
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 380
            } // library marker kkossev.commonLib, line 381
            break // library marker kkossev.commonLib, line 382
    } // library marker kkossev.commonLib, line 383
} // library marker kkossev.commonLib, line 384

/** // library marker kkossev.commonLib, line 386
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 387
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 388
 */ // library marker kkossev.commonLib, line 389
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 390
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 391
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 392
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 393
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 394
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 395
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 396
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
    else { // library marker kkossev.commonLib, line 399
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 400
    } // library marker kkossev.commonLib, line 401
} // library marker kkossev.commonLib, line 402

/** // library marker kkossev.commonLib, line 404
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 405
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 406
 */ // library marker kkossev.commonLib, line 407
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 409
    switch (commandId) { // library marker kkossev.commonLib, line 410
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 411
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 412
            break // library marker kkossev.commonLib, line 413
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 414
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 415
            break // library marker kkossev.commonLib, line 416
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 417
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 418
            break // library marker kkossev.commonLib, line 419
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 420
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 421
            break // library marker kkossev.commonLib, line 422
        case 0x0B: // default command response // library marker kkossev.commonLib, line 423
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 424
            break // library marker kkossev.commonLib, line 425
        default: // library marker kkossev.commonLib, line 426
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 427
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 428
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 429
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 430
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 431
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 432
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 433
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 434
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 435
            } // library marker kkossev.commonLib, line 436
            break // library marker kkossev.commonLib, line 437
    } // library marker kkossev.commonLib, line 438
} // library marker kkossev.commonLib, line 439

/** // library marker kkossev.commonLib, line 441
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 442
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 443
 */ // library marker kkossev.commonLib, line 444
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 445
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 446
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 447
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 448
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 449
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 450
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 451
    } // library marker kkossev.commonLib, line 452
    else { // library marker kkossev.commonLib, line 453
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 454
    } // library marker kkossev.commonLib, line 455
} // library marker kkossev.commonLib, line 456

/** // library marker kkossev.commonLib, line 458
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 459
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 460
 */ // library marker kkossev.commonLib, line 461
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 462
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 463
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 464
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 465
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 466
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 467
    } // library marker kkossev.commonLib, line 468
    else { // library marker kkossev.commonLib, line 469
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 470
    } // library marker kkossev.commonLib, line 471
} // library marker kkossev.commonLib, line 472

/** // library marker kkossev.commonLib, line 474
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 475
 */ // library marker kkossev.commonLib, line 476
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 477
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 478
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 479
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 480
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 481
        state.reportingEnabled = true // library marker kkossev.commonLib, line 482
    } // library marker kkossev.commonLib, line 483
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 484
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 485
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 486
    } else { // library marker kkossev.commonLib, line 487
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 488
    } // library marker kkossev.commonLib, line 489
} // library marker kkossev.commonLib, line 490

/** // library marker kkossev.commonLib, line 492
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 493
 */ // library marker kkossev.commonLib, line 494
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 495
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 496
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 497
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 498
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 499
    if (status == 0) { // library marker kkossev.commonLib, line 500
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 501
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 502
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 503
        int delta = 0 // library marker kkossev.commonLib, line 504
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 505
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 506
        } // library marker kkossev.commonLib, line 507
        else { // library marker kkossev.commonLib, line 508
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 509
        } // library marker kkossev.commonLib, line 510
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 511
    } // library marker kkossev.commonLib, line 512
    else { // library marker kkossev.commonLib, line 513
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 514
    } // library marker kkossev.commonLib, line 515
} // library marker kkossev.commonLib, line 516

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 518
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 519
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 520
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 521
        return false // library marker kkossev.commonLib, line 522
    } // library marker kkossev.commonLib, line 523
    // execute the customHandler function // library marker kkossev.commonLib, line 524
    boolean result = false // library marker kkossev.commonLib, line 525
    try { // library marker kkossev.commonLib, line 526
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 527
    } // library marker kkossev.commonLib, line 528
    catch (e) { // library marker kkossev.commonLib, line 529
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 530
        return false // library marker kkossev.commonLib, line 531
    } // library marker kkossev.commonLib, line 532
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 533
    return result // library marker kkossev.commonLib, line 534
} // library marker kkossev.commonLib, line 535

/** // library marker kkossev.commonLib, line 537
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 538
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 539
 */ // library marker kkossev.commonLib, line 540
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 541
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 542
    final String commandId = data[0] // library marker kkossev.commonLib, line 543
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 544
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 545
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 546
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 547
    } else { // library marker kkossev.commonLib, line 548
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 549
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 550
        if (isZigUSB()) { // library marker kkossev.commonLib, line 551
            executeCustomHandler('customParseDefaultCommandResponse', descMap) // library marker kkossev.commonLib, line 552
        } // library marker kkossev.commonLib, line 553
    } // library marker kkossev.commonLib, line 554
} // library marker kkossev.commonLib, line 555

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 557
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 558
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 559
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 560
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 561
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 562
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 563
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 564
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 565
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 566
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 567
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 568
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 569
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 570
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 571
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 572

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 574
    0x00: 'Success', // library marker kkossev.commonLib, line 575
    0x01: 'Failure', // library marker kkossev.commonLib, line 576
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 577
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 578
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 579
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 580
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 581
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 582
    0x88: 'Read Only', // library marker kkossev.commonLib, line 583
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 584
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 585
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 586
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 587
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 588
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 589
    0x94: 'Time out', // library marker kkossev.commonLib, line 590
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 591
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 592
] // library marker kkossev.commonLib, line 593

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 595
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 596
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 597
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 598
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 599
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 600
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 601
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 602
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 603
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 604
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 605
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 606
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 607
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 608
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 609
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 610
] // library marker kkossev.commonLib, line 611

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 613
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 614
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 615
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 616
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 617
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 618
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 619
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 620
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 621
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 622
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 623
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 624
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 625
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 626
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 627
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 628
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 629
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 630
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 631
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 632
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 633
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 634
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 635
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 636
] // library marker kkossev.commonLib, line 637

/* // library marker kkossev.commonLib, line 639
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 640
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 641
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 642
 */ // library marker kkossev.commonLib, line 643
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 644
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 645
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 646
    } // library marker kkossev.commonLib, line 647
    else { // library marker kkossev.commonLib, line 648
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
} // library marker kkossev.commonLib, line 651

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 653
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 654
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 655
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 656
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 657
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 658
    return avg // library marker kkossev.commonLib, line 659
} // library marker kkossev.commonLib, line 660

/* // library marker kkossev.commonLib, line 662
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 663
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 664
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 665
*/ // library marker kkossev.commonLib, line 666
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 667

/** // library marker kkossev.commonLib, line 669
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 670
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 671
 */ // library marker kkossev.commonLib, line 672
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 673
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 674
    /* // library marker kkossev.commonLib, line 675
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 676
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 677
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 678
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 679
    */ // library marker kkossev.commonLib, line 680
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 681
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 682
        case 0x0000: // library marker kkossev.commonLib, line 683
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 684
            break // library marker kkossev.commonLib, line 685
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 686
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 687
            if (isPing) { // library marker kkossev.commonLib, line 688
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 689
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 690
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 691
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 692
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 693
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 694
                    sendRttEvent() // library marker kkossev.commonLib, line 695
                } // library marker kkossev.commonLib, line 696
                else { // library marker kkossev.commonLib, line 697
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 698
                } // library marker kkossev.commonLib, line 699
                state.states['isPing'] = false // library marker kkossev.commonLib, line 700
            } // library marker kkossev.commonLib, line 701
            else { // library marker kkossev.commonLib, line 702
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 703
            } // library marker kkossev.commonLib, line 704
            break // library marker kkossev.commonLib, line 705
        case 0x0004: // library marker kkossev.commonLib, line 706
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 707
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 708
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 709
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 710
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 711
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 712
            } // library marker kkossev.commonLib, line 713
            break // library marker kkossev.commonLib, line 714
        case 0x0005: // library marker kkossev.commonLib, line 715
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 716
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 717
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 718
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 719
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 720
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 721
            } // library marker kkossev.commonLib, line 722
            break // library marker kkossev.commonLib, line 723
        case 0x0007: // library marker kkossev.commonLib, line 724
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 725
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 726
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 727
            break // library marker kkossev.commonLib, line 728
        case 0xFFDF: // library marker kkossev.commonLib, line 729
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 730
            break // library marker kkossev.commonLib, line 731
        case 0xFFE2: // library marker kkossev.commonLib, line 732
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 733
            break // library marker kkossev.commonLib, line 734
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 735
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 736
            break // library marker kkossev.commonLib, line 737
        case 0xFFFE: // library marker kkossev.commonLib, line 738
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 739
            break // library marker kkossev.commonLib, line 740
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 741
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 742
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 743
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 744
            break // library marker kkossev.commonLib, line 745
        default: // library marker kkossev.commonLib, line 746
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 747
            break // library marker kkossev.commonLib, line 748
    } // library marker kkossev.commonLib, line 749
} // library marker kkossev.commonLib, line 750

/* // library marker kkossev.commonLib, line 752
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 753
 * power cluster            0x0001 // library marker kkossev.commonLib, line 754
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 755
*/ // library marker kkossev.commonLib, line 756
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 757
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 758
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 759
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 760
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 761
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 762
    } // library marker kkossev.commonLib, line 763

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 765
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 766
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 767
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 768
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 769
        } // library marker kkossev.commonLib, line 770
    } // library marker kkossev.commonLib, line 771
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 772
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 773
    } // library marker kkossev.commonLib, line 774
    else { // library marker kkossev.commonLib, line 775
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 776
    } // library marker kkossev.commonLib, line 777
} // library marker kkossev.commonLib, line 778

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 780
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 781
    Map result = [:] // library marker kkossev.commonLib, line 782
    BigDecimal volts = BigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 783
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 784
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 785
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 786
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 787
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 788
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 789
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 790
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 791
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 792
            result.name = 'battery' // library marker kkossev.commonLib, line 793
            result.unit  = '%' // library marker kkossev.commonLib, line 794
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 795
        } // library marker kkossev.commonLib, line 796
        else { // library marker kkossev.commonLib, line 797
            result.value = volts // library marker kkossev.commonLib, line 798
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 799
            result.unit  = 'V' // library marker kkossev.commonLib, line 800
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 801
        } // library marker kkossev.commonLib, line 802
        result.type = 'physical' // library marker kkossev.commonLib, line 803
        result.isStateChange = true // library marker kkossev.commonLib, line 804
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 805
        sendEvent(result) // library marker kkossev.commonLib, line 806
    } // library marker kkossev.commonLib, line 807
    else { // library marker kkossev.commonLib, line 808
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 809
    } // library marker kkossev.commonLib, line 810
} // library marker kkossev.commonLib, line 811

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 813
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 814
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 815
        return // library marker kkossev.commonLib, line 816
    } // library marker kkossev.commonLib, line 817
    Map map = [:] // library marker kkossev.commonLib, line 818
    map.name = 'battery' // library marker kkossev.commonLib, line 819
    map.timeStamp = now() // library marker kkossev.commonLib, line 820
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 821
    map.unit  = '%' // library marker kkossev.commonLib, line 822
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 823
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 824
    map.isStateChange = true // library marker kkossev.commonLib, line 825
    // // library marker kkossev.commonLib, line 826
    int latestBatteryEvent = safeToInt(device.latestState('battery', skipCache=true)) // library marker kkossev.commonLib, line 827
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 828
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 829
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 830
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 831
        // send it now! // library marker kkossev.commonLib, line 832
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 833
    } // library marker kkossev.commonLib, line 834
    else { // library marker kkossev.commonLib, line 835
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 836
        map.delayed = delayedTime // library marker kkossev.commonLib, line 837
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 838
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 839
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 840
    } // library marker kkossev.commonLib, line 841
} // library marker kkossev.commonLib, line 842

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 844
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 845
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 846
    sendEvent(map) // library marker kkossev.commonLib, line 847
} // library marker kkossev.commonLib, line 848

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 850
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 851
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 852
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 853
    sendEvent(map) // library marker kkossev.commonLib, line 854
} // library marker kkossev.commonLib, line 855

/* // library marker kkossev.commonLib, line 857
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 858
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 859
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 860
*/ // library marker kkossev.commonLib, line 861
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 862
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 863
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 864
} // library marker kkossev.commonLib, line 865

/* // library marker kkossev.commonLib, line 867
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 868
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 869
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 870
*/ // library marker kkossev.commonLib, line 871
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 872
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 873
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 874
    } // library marker kkossev.commonLib, line 875
    else { // library marker kkossev.commonLib, line 876
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 877
    } // library marker kkossev.commonLib, line 878
} // library marker kkossev.commonLib, line 879

/* // library marker kkossev.commonLib, line 881
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 882
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 883
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 884
*/ // library marker kkossev.commonLib, line 885
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 886
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 887
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 888
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 889
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 890
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 891
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 892
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 893
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 894
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 895
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 896
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 897
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 898
            } // library marker kkossev.commonLib, line 899
            else { // library marker kkossev.commonLib, line 900
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 901
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 902
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 903
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 904
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 905
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 906
                        return // library marker kkossev.commonLib, line 907
                    } // library marker kkossev.commonLib, line 908
                } // library marker kkossev.commonLib, line 909
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 910
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 911
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 912
            } // library marker kkossev.commonLib, line 913
            break // library marker kkossev.commonLib, line 914
        case 0x01: // View group // library marker kkossev.commonLib, line 915
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 916
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 917
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 918
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 919
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 920
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 921
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 922
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 923
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 924
            } // library marker kkossev.commonLib, line 925
            else { // library marker kkossev.commonLib, line 926
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 927
            } // library marker kkossev.commonLib, line 928
            break // library marker kkossev.commonLib, line 929
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 930
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 931
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 932
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 933
            final Set<String> groups = [] // library marker kkossev.commonLib, line 934
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 935
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 936
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 937
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 938
            } // library marker kkossev.commonLib, line 939
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 940
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 941
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 942
            break // library marker kkossev.commonLib, line 943
        case 0x03: // Remove group // library marker kkossev.commonLib, line 944
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 945
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 946
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 947
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 948
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 949
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 950
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 951
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 952
            } // library marker kkossev.commonLib, line 953
            else { // library marker kkossev.commonLib, line 954
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 955
            } // library marker kkossev.commonLib, line 956
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 957
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 958
            if (index >= 0) { // library marker kkossev.commonLib, line 959
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 960
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 961
            } // library marker kkossev.commonLib, line 962
            break // library marker kkossev.commonLib, line 963
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 964
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 965
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 966
            break // library marker kkossev.commonLib, line 967
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 968
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 969
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 970
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 971
            break // library marker kkossev.commonLib, line 972
        default: // library marker kkossev.commonLib, line 973
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 974
            break // library marker kkossev.commonLib, line 975
    } // library marker kkossev.commonLib, line 976
} // library marker kkossev.commonLib, line 977

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 979
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 980
    List<String> cmds = [] // library marker kkossev.commonLib, line 981
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 982
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 983
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 984
        return [] // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 987
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 988
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 989
    return cmds // library marker kkossev.commonLib, line 990
} // library marker kkossev.commonLib, line 991

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 993
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 994
    List<String> cmds = [] // library marker kkossev.commonLib, line 995
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 996
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 997
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 998
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 999
    return cmds // library marker kkossev.commonLib, line 1000
} // library marker kkossev.commonLib, line 1001

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1003
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1004
    List<String> cmds = [] // library marker kkossev.commonLib, line 1005
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1006
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1007
    return cmds // library marker kkossev.commonLib, line 1008
} // library marker kkossev.commonLib, line 1009

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1011
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1012
    List<String> cmds = [] // library marker kkossev.commonLib, line 1013
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1014
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1015
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1016
        return [] // library marker kkossev.commonLib, line 1017
    } // library marker kkossev.commonLib, line 1018
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1019
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1020
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1021
    return cmds // library marker kkossev.commonLib, line 1022
} // library marker kkossev.commonLib, line 1023

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1025
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1026
    List<String> cmds = [] // library marker kkossev.commonLib, line 1027
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1028
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1029
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1030
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1031
    return cmds // library marker kkossev.commonLib, line 1032
} // library marker kkossev.commonLib, line 1033

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1035
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1036
    List<String> cmds = [] // library marker kkossev.commonLib, line 1037
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1038
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1039
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1040
    return cmds // library marker kkossev.commonLib, line 1041
} // library marker kkossev.commonLib, line 1042

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1044
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1045
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1046
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1047
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1048
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1049
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1050
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1051
] // library marker kkossev.commonLib, line 1052

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1054
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1055
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1056
    List<String> cmds = [] // library marker kkossev.commonLib, line 1057
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1058
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1059
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1060
    def value // library marker kkossev.commonLib, line 1061
    Boolean validated = false // library marker kkossev.commonLib, line 1062
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1063
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1064
        return // library marker kkossev.commonLib, line 1065
    } // library marker kkossev.commonLib, line 1066
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1067
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1068
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1069
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1070
        return // library marker kkossev.commonLib, line 1071
    } // library marker kkossev.commonLib, line 1072
    // // library marker kkossev.commonLib, line 1073
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1074
    def func // library marker kkossev.commonLib, line 1075
    try { // library marker kkossev.commonLib, line 1076
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1077
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1078
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1079
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    catch (e) { // library marker kkossev.commonLib, line 1082
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1083
        return // library marker kkossev.commonLib, line 1084
    } // library marker kkossev.commonLib, line 1085

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1087
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1091
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1092
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1093
} // library marker kkossev.commonLib, line 1094

/* // library marker kkossev.commonLib, line 1096
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1097
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1098
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1099
*/ // library marker kkossev.commonLib, line 1100

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1102
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1103
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1104
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106

    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1108
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1109
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1110
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1111
    } // library marker kkossev.commonLib, line 1112
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1113
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    else { // library marker kkossev.commonLib, line 1116
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1117
    } // library marker kkossev.commonLib, line 1118
} // library marker kkossev.commonLib, line 1119

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1121
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1122
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1123

void toggle() { // library marker kkossev.commonLib, line 1125
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1126
    String state = '' // library marker kkossev.commonLib, line 1127
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1128
        state = 'on' // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
    else { // library marker kkossev.commonLib, line 1131
        state = 'off' // library marker kkossev.commonLib, line 1132
    } // library marker kkossev.commonLib, line 1133
    descriptionText += state // library marker kkossev.commonLib, line 1134
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1135
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1136
} // library marker kkossev.commonLib, line 1137

void off() { // library marker kkossev.commonLib, line 1139
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1140
        customOff() // library marker kkossev.commonLib, line 1141
        return // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1144
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1145
        return // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
    List cmds = settings?.inverceSwitch == false ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1148
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1149
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1150
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1151
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1152
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1153
        } // library marker kkossev.commonLib, line 1154
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1155
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1156
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1157
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1158
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160
    /* // library marker kkossev.commonLib, line 1161
    else { // library marker kkossev.commonLib, line 1162
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1163
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1164
        } // library marker kkossev.commonLib, line 1165
        else { // library marker kkossev.commonLib, line 1166
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1167
            return // library marker kkossev.commonLib, line 1168
        } // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170
    */ // library marker kkossev.commonLib, line 1171

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1173
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1174
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1175
} // library marker kkossev.commonLib, line 1176

void on() { // library marker kkossev.commonLib, line 1178
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1179
        customOn() // library marker kkossev.commonLib, line 1180
        return // library marker kkossev.commonLib, line 1181
    } // library marker kkossev.commonLib, line 1182
    List cmds = settings?.inverceSwitch == false ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1183
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1184
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1185
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1186
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1187
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1188
        } // library marker kkossev.commonLib, line 1189
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1190
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1191
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1192
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1193
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
    /* // library marker kkossev.commonLib, line 1196
    else { // library marker kkossev.commonLib, line 1197
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1198
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1199
        } // library marker kkossev.commonLib, line 1200
        else { // library marker kkossev.commonLib, line 1201
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1202
            return // library marker kkossev.commonLib, line 1203
        } // library marker kkossev.commonLib, line 1204
    } // library marker kkossev.commonLib, line 1205
    */ // library marker kkossev.commonLib, line 1206
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1207
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1208
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1209
} // library marker kkossev.commonLib, line 1210

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1212
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1213
    if (settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1214
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1215
    } // library marker kkossev.commonLib, line 1216
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1217
    Map map = [:] // library marker kkossev.commonLib, line 1218
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1219
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1220
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1221
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1222
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1223
        return // library marker kkossev.commonLib, line 1224
    } // library marker kkossev.commonLib, line 1225
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1226
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1227
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1228
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1229
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1230
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1231
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1232
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1233
    } else { // library marker kkossev.commonLib, line 1234
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1235
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1236
    } // library marker kkossev.commonLib, line 1237
    map.name = 'switch' // library marker kkossev.commonLib, line 1238
    map.value = value // library marker kkossev.commonLib, line 1239
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1240
    if (isRefresh) { // library marker kkossev.commonLib, line 1241
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1242
        map.isStateChange = true // library marker kkossev.commonLib, line 1243
    } else { // library marker kkossev.commonLib, line 1244
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1247
    sendEvent(map) // library marker kkossev.commonLib, line 1248
    clearIsDigital() // library marker kkossev.commonLib, line 1249
} // library marker kkossev.commonLib, line 1250

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1252
    '0': 'switch off', // library marker kkossev.commonLib, line 1253
    '1': 'switch on', // library marker kkossev.commonLib, line 1254
    '2': 'switch last state' // library marker kkossev.commonLib, line 1255
] // library marker kkossev.commonLib, line 1256

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1258
    '0': 'toggle', // library marker kkossev.commonLib, line 1259
    '1': 'state', // library marker kkossev.commonLib, line 1260
    '2': 'momentary' // library marker kkossev.commonLib, line 1261
] // library marker kkossev.commonLib, line 1262

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1264
    Map descMap = [:] // library marker kkossev.commonLib, line 1265
    try { // library marker kkossev.commonLib, line 1266
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1267
    } // library marker kkossev.commonLib, line 1268
    catch (e1) { // library marker kkossev.commonLib, line 1269
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1270
        // try alternative custom parsing // library marker kkossev.commonLib, line 1271
        descMap = [:] // library marker kkossev.commonLib, line 1272
        try { // library marker kkossev.commonLib, line 1273
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1274
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1275
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1276
            } // library marker kkossev.commonLib, line 1277
        } // library marker kkossev.commonLib, line 1278
        catch (e2) { // library marker kkossev.commonLib, line 1279
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1280
            return [:] // library marker kkossev.commonLib, line 1281
        } // library marker kkossev.commonLib, line 1282
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1283
    } // library marker kkossev.commonLib, line 1284
    return descMap // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1288
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1289
        return false // library marker kkossev.commonLib, line 1290
    } // library marker kkossev.commonLib, line 1291
    // try to parse ... // library marker kkossev.commonLib, line 1292
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1293
    Map descMap = [:] // library marker kkossev.commonLib, line 1294
    try { // library marker kkossev.commonLib, line 1295
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1296
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1297
    } // library marker kkossev.commonLib, line 1298
    catch (e) { // library marker kkossev.commonLib, line 1299
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1300
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1301
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1302
        return true // library marker kkossev.commonLib, line 1303
    } // library marker kkossev.commonLib, line 1304

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1306
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1307
    } // library marker kkossev.commonLib, line 1308
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1309
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1312
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1313
    } // library marker kkossev.commonLib, line 1314
    else { // library marker kkossev.commonLib, line 1315
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1316
        return false // library marker kkossev.commonLib, line 1317
    } // library marker kkossev.commonLib, line 1318
    return true    // processed // library marker kkossev.commonLib, line 1319
} // library marker kkossev.commonLib, line 1320

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1322
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1323
  /* // library marker kkossev.commonLib, line 1324
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1325
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1326
        return true // library marker kkossev.commonLib, line 1327
    } // library marker kkossev.commonLib, line 1328
*/ // library marker kkossev.commonLib, line 1329
    Map descMap = [:] // library marker kkossev.commonLib, line 1330
    try { // library marker kkossev.commonLib, line 1331
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1332
    } // library marker kkossev.commonLib, line 1333
    catch (e1) { // library marker kkossev.commonLib, line 1334
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1335
        // try alternative custom parsing // library marker kkossev.commonLib, line 1336
        descMap = [:] // library marker kkossev.commonLib, line 1337
        try { // library marker kkossev.commonLib, line 1338
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1339
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1340
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1341
            } // library marker kkossev.commonLib, line 1342
        } // library marker kkossev.commonLib, line 1343
        catch (e2) { // library marker kkossev.commonLib, line 1344
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1345
            return true // library marker kkossev.commonLib, line 1346
        } // library marker kkossev.commonLib, line 1347
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1348
    } // library marker kkossev.commonLib, line 1349
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1350
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1351
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1352
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1353
        return false // library marker kkossev.commonLib, line 1354
    } // library marker kkossev.commonLib, line 1355
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1356
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1357
    // attribute report received // library marker kkossev.commonLib, line 1358
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1359
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1360
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1361
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1362
    } // library marker kkossev.commonLib, line 1363
    attrData.each { // library marker kkossev.commonLib, line 1364
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1365
        //def map = [:] // library marker kkossev.commonLib, line 1366
        if (it.status == '86') { // library marker kkossev.commonLib, line 1367
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1368
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1369
        } // library marker kkossev.commonLib, line 1370
        switch (it.cluster) { // library marker kkossev.commonLib, line 1371
            case '0000' : // library marker kkossev.commonLib, line 1372
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1373
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1374
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1375
                } // library marker kkossev.commonLib, line 1376
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1377
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1378
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1379
                } // library marker kkossev.commonLib, line 1380
                else { // library marker kkossev.commonLib, line 1381
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1382
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1383
                } // library marker kkossev.commonLib, line 1384
                break // library marker kkossev.commonLib, line 1385
            default : // library marker kkossev.commonLib, line 1386
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1387
                break // library marker kkossev.commonLib, line 1388
        } // switch // library marker kkossev.commonLib, line 1389
    } // for each attribute // library marker kkossev.commonLib, line 1390
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1391
} // library marker kkossev.commonLib, line 1392

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1394

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1396
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1397
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1398
    def mode // library marker kkossev.commonLib, line 1399
    String attrName // library marker kkossev.commonLib, line 1400
    if (it.value == null) { // library marker kkossev.commonLib, line 1401
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1402
        return // library marker kkossev.commonLib, line 1403
    } // library marker kkossev.commonLib, line 1404
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1405
    switch (it.attrId) { // library marker kkossev.commonLib, line 1406
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1407
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1408
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1409
            break // library marker kkossev.commonLib, line 1410
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1411
            attrName = 'On Time' // library marker kkossev.commonLib, line 1412
            mode = value // library marker kkossev.commonLib, line 1413
            break // library marker kkossev.commonLib, line 1414
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1415
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1416
            mode = value // library marker kkossev.commonLib, line 1417
            break // library marker kkossev.commonLib, line 1418
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1419
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1420
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1421
            break // library marker kkossev.commonLib, line 1422
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1423
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1424
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1425
            break // library marker kkossev.commonLib, line 1426
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1427
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1428
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1429
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1430
            } // library marker kkossev.commonLib, line 1431
            else { // library marker kkossev.commonLib, line 1432
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1433
            } // library marker kkossev.commonLib, line 1434
            break // library marker kkossev.commonLib, line 1435
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1436
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1437
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1438
            break // library marker kkossev.commonLib, line 1439
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1440
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1441
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1442
            break // library marker kkossev.commonLib, line 1443
        default : // library marker kkossev.commonLib, line 1444
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1445
            return // library marker kkossev.commonLib, line 1446
    } // library marker kkossev.commonLib, line 1447
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1448
} // library marker kkossev.commonLib, line 1449

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1451
    Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1452
    if (txtEnable) { log.info "${device.displayName } $event.descriptionText" } // library marker kkossev.commonLib, line 1453
    sendEvent(event) // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1457
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1458
    if (DEVICE_TYPE in ['Fingerbot'])     { pushFingerbot(); return } // library marker kkossev.commonLib, line 1459
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1460
} // library marker kkossev.commonLib, line 1461

void push(int buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1463
    if (DEVICE_TYPE in ['Fingerbot'])     { pushFingerbot(buttonNumber); return } // library marker kkossev.commonLib, line 1464
    sendButtonEvent(buttonNumber, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

void doubleTap(int buttonNumber) { // library marker kkossev.commonLib, line 1468
    sendButtonEvent(buttonNumber, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

void hold(int buttonNumber) { // library marker kkossev.commonLib, line 1472
    sendButtonEvent(buttonNumber, 'held', isDigital = true) // library marker kkossev.commonLib, line 1473
} // library marker kkossev.commonLib, line 1474

void release(int buttonNumber) { // library marker kkossev.commonLib, line 1476
    sendButtonEvent(buttonNumber, 'released', isDigital = true) // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1480
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1484
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1485
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

/* // library marker kkossev.commonLib, line 1489
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1490
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1491
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1492
*/ // library marker kkossev.commonLib, line 1493
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1494
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1495
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1496
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1497
    } // library marker kkossev.commonLib, line 1498
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1499
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1502
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1503
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1504
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1505
    } // library marker kkossev.commonLib, line 1506
    else { // library marker kkossev.commonLib, line 1507
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1512
    int value = rawValue as int // library marker kkossev.commonLib, line 1513
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1514
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1515
    Map map = [:] // library marker kkossev.commonLib, line 1516

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1518
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1519

    map.name = 'level' // library marker kkossev.commonLib, line 1521
    map.value = value // library marker kkossev.commonLib, line 1522
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1523
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1524
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1525
        map.isStateChange = true // library marker kkossev.commonLib, line 1526
    } // library marker kkossev.commonLib, line 1527
    else { // library marker kkossev.commonLib, line 1528
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1529
    } // library marker kkossev.commonLib, line 1530
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1531
    sendEvent(map) // library marker kkossev.commonLib, line 1532
    clearIsDigital() // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

/** // library marker kkossev.commonLib, line 1536
 * Get the level transition rate // library marker kkossev.commonLib, line 1537
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1538
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1539
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1540
 */ // library marker kkossev.commonLib, line 1541
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1542
    int rate = 0 // library marker kkossev.commonLib, line 1543
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1544
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1545
    if (!isOn) { // library marker kkossev.commonLib, line 1546
        currentLevel = 0 // library marker kkossev.commonLib, line 1547
    } // library marker kkossev.commonLib, line 1548
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1549
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1550
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1551
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1552
    } else { // library marker kkossev.commonLib, line 1553
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1554
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1555
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1556
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1557
        } // library marker kkossev.commonLib, line 1558
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1559
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1560
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1561
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1562
        } // library marker kkossev.commonLib, line 1563
    } // library marker kkossev.commonLib, line 1564
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1565
    return rate // library marker kkossev.commonLib, line 1566
} // library marker kkossev.commonLib, line 1567

// Command option that enable changes when off // library marker kkossev.commonLib, line 1569
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1570

/** // library marker kkossev.commonLib, line 1572
 * Constrain a value to a range // library marker kkossev.commonLib, line 1573
 * @param value value to constrain // library marker kkossev.commonLib, line 1574
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1575
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1576
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1577
 */ // library marker kkossev.commonLib, line 1578
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1579
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1580
        return value // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1583
} // library marker kkossev.commonLib, line 1584

/** // library marker kkossev.commonLib, line 1586
 * Constrain a value to a range // library marker kkossev.commonLib, line 1587
 * @param value value to constrain // library marker kkossev.commonLib, line 1588
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1589
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1590
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1591
 */ // library marker kkossev.commonLib, line 1592
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1593
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1594
        return value as Integer // library marker kkossev.commonLib, line 1595
    } // library marker kkossev.commonLib, line 1596
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1597
} // library marker kkossev.commonLib, line 1598

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1600
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1601

/** // library marker kkossev.commonLib, line 1603
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1604
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1605
 * @param commands commands to execute // library marker kkossev.commonLib, line 1606
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1607
 */ // library marker kkossev.commonLib, line 1608
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1609
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1610
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1611
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1612
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1613
    } // library marker kkossev.commonLib, line 1614
    return [] // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1618
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1619
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1620
} // library marker kkossev.commonLib, line 1621

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1623
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1624
} // library marker kkossev.commonLib, line 1625

/** // library marker kkossev.commonLib, line 1627
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1628
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1629
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1630
 */ // library marker kkossev.commonLib, line 1631
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1632
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1633
    List<String> cmds = [] // library marker kkossev.commonLib, line 1634
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1635
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1636
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1637
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1638
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1639
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1640
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1641
    } // library marker kkossev.commonLib, line 1642
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1643
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1644
    /* // library marker kkossev.commonLib, line 1645
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1646
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1647
    */ // library marker kkossev.commonLib, line 1648
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1649
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1650
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1651

    return cmds // library marker kkossev.commonLib, line 1653
} // library marker kkossev.commonLib, line 1654

/** // library marker kkossev.commonLib, line 1656
 * Set Level Command // library marker kkossev.commonLib, line 1657
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1658
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1659
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1660
 */ // library marker kkossev.commonLib, line 1661
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1662
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1663
    if (DEVICE_TYPE in  ['ButtonDimmer']) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1664
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1665
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1666
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1667
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1668
} // library marker kkossev.commonLib, line 1669

/* // library marker kkossev.commonLib, line 1671
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1672
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1673
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1674
*/ // library marker kkossev.commonLib, line 1675
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1676
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1677
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1678
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1679
    } // library marker kkossev.commonLib, line 1680
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1681
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1682
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1683
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1684
    } // library marker kkossev.commonLib, line 1685
    else { // library marker kkossev.commonLib, line 1686
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1687
    } // library marker kkossev.commonLib, line 1688
} // library marker kkossev.commonLib, line 1689

/* // library marker kkossev.commonLib, line 1691
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1692
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1693
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1694
*/ // library marker kkossev.commonLib, line 1695
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1696
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1697
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1698
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1699
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1700
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1701
} // library marker kkossev.commonLib, line 1702

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1704
    Map eventMap = [:] // library marker kkossev.commonLib, line 1705
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1706
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1707
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1708
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1709
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1710
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1711
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1712
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1713
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1714
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1715
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1716
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1717
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1718
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1719
        return // library marker kkossev.commonLib, line 1720
    } // library marker kkossev.commonLib, line 1721
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1722
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1723
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1724
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1725
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1726
    } // library marker kkossev.commonLib, line 1727
    else {         // queue the event // library marker kkossev.commonLib, line 1728
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1729
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1730
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1731
    } // library marker kkossev.commonLib, line 1732
} // library marker kkossev.commonLib, line 1733

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1735
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1736
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1737
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1738
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1739
} // library marker kkossev.commonLib, line 1740

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1742

/* // library marker kkossev.commonLib, line 1744
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1745
 * temperature // library marker kkossev.commonLib, line 1746
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1747
*/ // library marker kkossev.commonLib, line 1748
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1749
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1750
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1751
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1752
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1753
} // library marker kkossev.commonLib, line 1754

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1756
    Map eventMap = [:] // library marker kkossev.commonLib, line 1757
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1758
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1759
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1760
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1761
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1762
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1763
    } // library marker kkossev.commonLib, line 1764
    else { // library marker kkossev.commonLib, line 1765
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1766
    } // library marker kkossev.commonLib, line 1767
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1768
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1769
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1770
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1771
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1772
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1773
        return // library marker kkossev.commonLib, line 1774
    } // library marker kkossev.commonLib, line 1775
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1776
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1777
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1778
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1779
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1780
    } // library marker kkossev.commonLib, line 1781
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1782
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1783
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1784
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1785
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1786
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1787
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1788
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1789
    } // library marker kkossev.commonLib, line 1790
    else {         // queue the event // library marker kkossev.commonLib, line 1791
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1792
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1793
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1794
    } // library marker kkossev.commonLib, line 1795
} // library marker kkossev.commonLib, line 1796

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1798
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1799
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1800
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1801
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1802
} // library marker kkossev.commonLib, line 1803

/* // library marker kkossev.commonLib, line 1805
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1806
 * humidity // library marker kkossev.commonLib, line 1807
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1808
*/ // library marker kkossev.commonLib, line 1809
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1810
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1811
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1812
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1813
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1814
} // library marker kkossev.commonLib, line 1815

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1817
    Map eventMap = [:] // library marker kkossev.commonLib, line 1818
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1819
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1820
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1821
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1822
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1823
        return // library marker kkossev.commonLib, line 1824
    } // library marker kkossev.commonLib, line 1825
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1826
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1827
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1828
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1829
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1830
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1831
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1832
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1833
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1834
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1835
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1836
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1837
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1838
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1839
    } // library marker kkossev.commonLib, line 1840
    else { // library marker kkossev.commonLib, line 1841
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1842
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1843
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1844
    } // library marker kkossev.commonLib, line 1845
} // library marker kkossev.commonLib, line 1846

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1848
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1849
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1850
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1851
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1852
} // library marker kkossev.commonLib, line 1853

/* // library marker kkossev.commonLib, line 1855
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1856
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1857
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1858
*/ // library marker kkossev.commonLib, line 1859

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1861
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1862
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1863
    } // library marker kkossev.commonLib, line 1864
} // library marker kkossev.commonLib, line 1865

/* // library marker kkossev.commonLib, line 1867
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1868
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1869
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1870
*/ // library marker kkossev.commonLib, line 1871

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1873
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1874
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1875
    } // library marker kkossev.commonLib, line 1876
} // library marker kkossev.commonLib, line 1877

/* // library marker kkossev.commonLib, line 1879
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1880
 * pm2.5 // library marker kkossev.commonLib, line 1881
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1882
*/ // library marker kkossev.commonLib, line 1883
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1884
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1885
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1886
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1887
    BigInteger bigIntegerValue = intBitsToFloat(value.intValue()).toBigInteger() // library marker kkossev.commonLib, line 1888
    handlePm25Event(bigIntegerValue as Integer) // library marker kkossev.commonLib, line 1889
} // library marker kkossev.commonLib, line 1890
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1891

/* // library marker kkossev.commonLib, line 1893
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1894
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1895
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1896
*/ // library marker kkossev.commonLib, line 1897
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1898
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1899
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1900
    } // library marker kkossev.commonLib, line 1901
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1902
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1903
    } // library marker kkossev.commonLib, line 1904
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1905
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1906
    } // library marker kkossev.commonLib, line 1907
    else { // library marker kkossev.commonLib, line 1908
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1909
    } // library marker kkossev.commonLib, line 1910
} // library marker kkossev.commonLib, line 1911

/* // library marker kkossev.commonLib, line 1913
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1914
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1915
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1916
*/ // library marker kkossev.commonLib, line 1917

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1919
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1920
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1921
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1922
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1923
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1924
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1925
    } // library marker kkossev.commonLib, line 1926
    else { // library marker kkossev.commonLib, line 1927
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1928
    } // library marker kkossev.commonLib, line 1929
} // library marker kkossev.commonLib, line 1930

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1932
    Map eventMap = [:] // library marker kkossev.commonLib, line 1933
    eventMap.value = value // library marker kkossev.commonLib, line 1934
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1935
    eventMap.unit = '' // library marker kkossev.commonLib, line 1936
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1937
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1938
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1939
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1940
} // library marker kkossev.commonLib, line 1941

/* // library marker kkossev.commonLib, line 1943
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1944
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1945
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1946
*/ // library marker kkossev.commonLib, line 1947

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1949
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1950
    if (DEVICE_TYPE in  ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1951
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1952
    } // library marker kkossev.commonLib, line 1953
    else { // library marker kkossev.commonLib, line 1954
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1955
    } // library marker kkossev.commonLib, line 1956
} // library marker kkossev.commonLib, line 1957

/* // library marker kkossev.commonLib, line 1959
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1960
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1961
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1962
*/ // library marker kkossev.commonLib, line 1963
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1964
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1965
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1966
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1967
    } // library marker kkossev.commonLib, line 1968
    else { // library marker kkossev.commonLib, line 1969
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1970
    } // library marker kkossev.commonLib, line 1971
} // library marker kkossev.commonLib, line 1972

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1974

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1976
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1977
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1978
    } // library marker kkossev.commonLib, line 1979
    else { // library marker kkossev.commonLib, line 1980
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1981
    } // library marker kkossev.commonLib, line 1982
} // library marker kkossev.commonLib, line 1983

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1985
    logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1986
} // library marker kkossev.commonLib, line 1987

/* // library marker kkossev.commonLib, line 1989
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1990
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1991
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1992
*/ // library marker kkossev.commonLib, line 1993
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1994
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1995
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1996

// Tuya Commands // library marker kkossev.commonLib, line 1998
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1999
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2000
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2001
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2002
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2003

// tuya DP type // library marker kkossev.commonLib, line 2005
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2006
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2007
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2008
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2009
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2010
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2011

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2013
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2014
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2015
        Long offset = 0 // library marker kkossev.commonLib, line 2016
        try { // library marker kkossev.commonLib, line 2017
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2018
        } // library marker kkossev.commonLib, line 2019
        catch (e) { // library marker kkossev.commonLib, line 2020
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2021
        } // library marker kkossev.commonLib, line 2022
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2023
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2024
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2025
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2026
    } // library marker kkossev.commonLib, line 2027
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2028
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2029
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2030
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2031
        if (status != '00') { // library marker kkossev.commonLib, line 2032
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2033
        } // library marker kkossev.commonLib, line 2034
    } // library marker kkossev.commonLib, line 2035
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2036
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2037
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2038
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2039
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2040
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2041
            return // library marker kkossev.commonLib, line 2042
        } // library marker kkossev.commonLib, line 2043
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2044
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2045
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2046
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2047
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2048
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2049
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2050
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2051
        } // library marker kkossev.commonLib, line 2052
    } // library marker kkossev.commonLib, line 2053
    else { // library marker kkossev.commonLib, line 2054
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2055
    } // library marker kkossev.commonLib, line 2056
} // library marker kkossev.commonLib, line 2057

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2059
    if (DEVICE_TYPE in ['Fingerbot'])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return } // library marker kkossev.commonLib, line 2060
    // check if the method  method exists // library marker kkossev.commonLib, line 2061
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2062
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2063
            return // library marker kkossev.commonLib, line 2064
        } // library marker kkossev.commonLib, line 2065
    } // library marker kkossev.commonLib, line 2066
    switch (dp) { // library marker kkossev.commonLib, line 2067
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2068
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2069
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2070
            } // library marker kkossev.commonLib, line 2071
            else { // library marker kkossev.commonLib, line 2072
                sendSwitchEvent(fncmd as int) // library marker kkossev.commonLib, line 2073
            } // library marker kkossev.commonLib, line 2074
            break // library marker kkossev.commonLib, line 2075
        case 0x02 : // library marker kkossev.commonLib, line 2076
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2077
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2078
            } // library marker kkossev.commonLib, line 2079
            else { // library marker kkossev.commonLib, line 2080
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2081
            } // library marker kkossev.commonLib, line 2082
            break // library marker kkossev.commonLib, line 2083
        case 0x04 : // battery // library marker kkossev.commonLib, line 2084
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2085
            break // library marker kkossev.commonLib, line 2086
        default : // library marker kkossev.commonLib, line 2087
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2088
            break // library marker kkossev.commonLib, line 2089
    } // library marker kkossev.commonLib, line 2090
} // library marker kkossev.commonLib, line 2091

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2093
    int retValue = 0 // library marker kkossev.commonLib, line 2094
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2095
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2096
        int power = 1 // library marker kkossev.commonLib, line 2097
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2098
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2099
            power = power * 256 // library marker kkossev.commonLib, line 2100
        } // library marker kkossev.commonLib, line 2101
    } // library marker kkossev.commonLib, line 2102
    return retValue // library marker kkossev.commonLib, line 2103
} // library marker kkossev.commonLib, line 2104

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2106
    List<String> cmds = [] // library marker kkossev.commonLib, line 2107
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2108
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2109
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2110
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2111
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2112
    return cmds // library marker kkossev.commonLib, line 2113
} // library marker kkossev.commonLib, line 2114

private getPACKET_ID() { // library marker kkossev.commonLib, line 2116
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2117
} // library marker kkossev.commonLib, line 2118

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2120
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2121
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2122
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2123
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2124
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2125
} // library marker kkossev.commonLib, line 2126

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2128
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2129

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2131
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2132
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2133
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2137
    List<String> cmds = [] // library marker kkossev.commonLib, line 2138
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2139
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2140
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2141
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2142
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2143
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2144
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2145
        } // library marker kkossev.commonLib, line 2146
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2147
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2148
    } // library marker kkossev.commonLib, line 2149
    else { // library marker kkossev.commonLib, line 2150
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2151
    } // library marker kkossev.commonLib, line 2152
} // library marker kkossev.commonLib, line 2153

/** // library marker kkossev.commonLib, line 2155
 * initializes the device // library marker kkossev.commonLib, line 2156
 * Invoked from configure() // library marker kkossev.commonLib, line 2157
 * @return zigbee commands // library marker kkossev.commonLib, line 2158
 */ // library marker kkossev.commonLib, line 2159
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2160
    List<String> cmds = [] // library marker kkossev.commonLib, line 2161
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2162

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2164
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2165
        return customInitializeDevice() // library marker kkossev.commonLib, line 2166
    } // library marker kkossev.commonLib, line 2167
    else if (DEVICE_TYPE in  ['ButtonDimmer'])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2168

    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2170
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2171
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2172
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2173
    } // library marker kkossev.commonLib, line 2174
    // // library marker kkossev.commonLib, line 2175
    if (cmds == []) { // library marker kkossev.commonLib, line 2176
        cmds = ['delay 299'] // library marker kkossev.commonLib, line 2177
    } // library marker kkossev.commonLib, line 2178
    return cmds // library marker kkossev.commonLib, line 2179
} // library marker kkossev.commonLib, line 2180

/** // library marker kkossev.commonLib, line 2182
 * configures the device // library marker kkossev.commonLib, line 2183
 * Invoked from configure() // library marker kkossev.commonLib, line 2184
 * @return zigbee commands // library marker kkossev.commonLib, line 2185
 */ // library marker kkossev.commonLib, line 2186
List<String> configureDevice() { // library marker kkossev.commonLib, line 2187
    List<String> cmds = [] // library marker kkossev.commonLib, line 2188
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2189

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2191
        cmds += customConfigureDevice() // library marker kkossev.commonLib, line 2192
    } // library marker kkossev.commonLib, line 2193
    else if (DEVICE_TYPE in  ['Fingerbot'])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2194
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2195
    else if (DEVICE_TYPE in  ['ButtonDimmer']) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2196
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2197
    if ( cmds == null || cmds == []) { // library marker kkossev.commonLib, line 2198
        cmds = ['delay 277',] // library marker kkossev.commonLib, line 2199
    } // library marker kkossev.commonLib, line 2200
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2201
    return cmds // library marker kkossev.commonLib, line 2202
} // library marker kkossev.commonLib, line 2203

/* // library marker kkossev.commonLib, line 2205
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2206
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2207
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2208
*/ // library marker kkossev.commonLib, line 2209

void refresh() { // library marker kkossev.commonLib, line 2211
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2212
    checkDriverVersion() // library marker kkossev.commonLib, line 2213
    List<String> cmds = [] // library marker kkossev.commonLib, line 2214
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2215

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2217
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2218
        cmds += customRefresh() // library marker kkossev.commonLib, line 2219
    } // library marker kkossev.commonLib, line 2220
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2221
    else if (DEVICE_TYPE in  ['Fingerbot'])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2222
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2223
    else { // library marker kkossev.commonLib, line 2224
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2225
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2226
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2227
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2228
        } // library marker kkossev.commonLib, line 2229
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2230
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2231
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2232
        } // library marker kkossev.commonLib, line 2233
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2234
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2235
        } // library marker kkossev.commonLib, line 2236
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2237
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2238
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2239
        } // library marker kkossev.commonLib, line 2240
    } // library marker kkossev.commonLib, line 2241

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2243
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2244
    } // library marker kkossev.commonLib, line 2245
    else { // library marker kkossev.commonLib, line 2246
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2247
    } // library marker kkossev.commonLib, line 2248
} // library marker kkossev.commonLib, line 2249

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2251
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2252
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2253
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2254

void clearInfoEvent() { // library marker kkossev.commonLib, line 2256
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2257
} // library marker kkossev.commonLib, line 2258

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2260
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2261
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2262
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2263
    } // library marker kkossev.commonLib, line 2264
    else { // library marker kkossev.commonLib, line 2265
        logInfo "${info}" // library marker kkossev.commonLib, line 2266
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2267
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2268
    } // library marker kkossev.commonLib, line 2269
} // library marker kkossev.commonLib, line 2270

void ping() { // library marker kkossev.commonLib, line 2272
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2273
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2274
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2275
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2276
    } // library marker kkossev.commonLib, line 2277
    else { // library marker kkossev.commonLib, line 2278
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2279
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2280
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2281
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2282
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2283
        if (isVirtual()) { // library marker kkossev.commonLib, line 2284
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2285
        } // library marker kkossev.commonLib, line 2286
        else { // library marker kkossev.commonLib, line 2287
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2288
        } // library marker kkossev.commonLib, line 2289
        logDebug 'ping...' // library marker kkossev.commonLib, line 2290
    } // library marker kkossev.commonLib, line 2291
} // library marker kkossev.commonLib, line 2292

def virtualPong() { // library marker kkossev.commonLib, line 2294
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2295
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2296
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2297
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2298
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2299
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2300
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2301
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2302
        sendRttEvent() // library marker kkossev.commonLib, line 2303
    } // library marker kkossev.commonLib, line 2304
    else { // library marker kkossev.commonLib, line 2305
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2306
    } // library marker kkossev.commonLib, line 2307
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2308
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2309
} // library marker kkossev.commonLib, line 2310

/** // library marker kkossev.commonLib, line 2312
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2313
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2314
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2315
 * @return none // library marker kkossev.commonLib, line 2316
 */ // library marker kkossev.commonLib, line 2317
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2318
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2319
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2320
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2321
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2322
    if (value == null) { // library marker kkossev.commonLib, line 2323
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2324
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2325
    } // library marker kkossev.commonLib, line 2326
    else { // library marker kkossev.commonLib, line 2327
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2328
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2329
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2330
    } // library marker kkossev.commonLib, line 2331
} // library marker kkossev.commonLib, line 2332

/** // library marker kkossev.commonLib, line 2334
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2335
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2336
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2337
 */ // library marker kkossev.commonLib, line 2338
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2339
    if (cluster != null) { // library marker kkossev.commonLib, line 2340
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2341
    } // library marker kkossev.commonLib, line 2342
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2343
    return 'NULL' // library marker kkossev.commonLib, line 2344
} // library marker kkossev.commonLib, line 2345

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2347
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2348
} // library marker kkossev.commonLib, line 2349

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2351
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2352
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2353
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2354
} // library marker kkossev.commonLib, line 2355

/** // library marker kkossev.commonLib, line 2357
 * Schedule a device health check // library marker kkossev.commonLib, line 2358
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2359
 */ // library marker kkossev.commonLib, line 2360
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2361
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2362
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2363
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2364
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2365
    } // library marker kkossev.commonLib, line 2366
    else { // library marker kkossev.commonLib, line 2367
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2368
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2369
    } // library marker kkossev.commonLib, line 2370
} // library marker kkossev.commonLib, line 2371

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2373
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2374
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2375
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2376
} // library marker kkossev.commonLib, line 2377

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2379
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2380
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2381
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2382
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2383
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2384
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2385
    } // library marker kkossev.commonLib, line 2386
} // library marker kkossev.commonLib, line 2387

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2389
    checkDriverVersion() // library marker kkossev.commonLib, line 2390
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2391
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2392
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2393
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2394
            logWarn 'not present!' // library marker kkossev.commonLib, line 2395
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2396
        } // library marker kkossev.commonLib, line 2397
    } // library marker kkossev.commonLib, line 2398
    else { // library marker kkossev.commonLib, line 2399
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2400
    } // library marker kkossev.commonLib, line 2401
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2402
} // library marker kkossev.commonLib, line 2403

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2405
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2406
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2407
    if (value == 'online') { // library marker kkossev.commonLib, line 2408
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2409
    } // library marker kkossev.commonLib, line 2410
    else { // library marker kkossev.commonLib, line 2411
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2412
    } // library marker kkossev.commonLib, line 2413
} // library marker kkossev.commonLib, line 2414

/** // library marker kkossev.commonLib, line 2416
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2417
 */ // library marker kkossev.commonLib, line 2418
void autoPoll() { // library marker kkossev.commonLib, line 2419
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2420
    checkDriverVersion() // library marker kkossev.commonLib, line 2421
    List<String> cmds = [] // library marker kkossev.commonLib, line 2422
    //if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2423
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2424
    // TODO !!!!!!!! // library marker kkossev.commonLib, line 2425
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2426
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2427
    } // library marker kkossev.commonLib, line 2428

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2430
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2431
    } // library marker kkossev.commonLib, line 2432
} // library marker kkossev.commonLib, line 2433

/** // library marker kkossev.commonLib, line 2435
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2436
 */ // library marker kkossev.commonLib, line 2437
void updated() { // library marker kkossev.commonLib, line 2438
    logInfo 'updated...' // library marker kkossev.commonLib, line 2439
    checkDriverVersion() // library marker kkossev.commonLib, line 2440
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2441
    unschedule() // library marker kkossev.commonLib, line 2442

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2444
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2445
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2446
    } // library marker kkossev.commonLib, line 2447
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2448
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2449
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2450
    } // library marker kkossev.commonLib, line 2451

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2453
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2454
        // schedule the periodic timer // library marker kkossev.commonLib, line 2455
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2456
        if (interval > 0) { // library marker kkossev.commonLib, line 2457
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2458
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2459
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2460
        } // library marker kkossev.commonLib, line 2461
    } // library marker kkossev.commonLib, line 2462
    else { // library marker kkossev.commonLib, line 2463
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2464
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2465
    } // library marker kkossev.commonLib, line 2466
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2467
        customUpdated() // library marker kkossev.commonLib, line 2468
    } // library marker kkossev.commonLib, line 2469

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2471
} // library marker kkossev.commonLib, line 2472

/** // library marker kkossev.commonLib, line 2474
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2475
 */ // library marker kkossev.commonLib, line 2476
void logsOff() { // library marker kkossev.commonLib, line 2477
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2478
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2479
} // library marker kkossev.commonLib, line 2480
void traceOff() { // library marker kkossev.commonLib, line 2481
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2482
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2483
} // library marker kkossev.commonLib, line 2484

void configure(String command) { // library marker kkossev.commonLib, line 2486
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2487
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2488
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2489
        return // library marker kkossev.commonLib, line 2490
    } // library marker kkossev.commonLib, line 2491
    // // library marker kkossev.commonLib, line 2492
    String func // library marker kkossev.commonLib, line 2493
    try { // library marker kkossev.commonLib, line 2494
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2495
        "$func"() // library marker kkossev.commonLib, line 2496
    } // library marker kkossev.commonLib, line 2497
    catch (e) { // library marker kkossev.commonLib, line 2498
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2499
        return // library marker kkossev.commonLib, line 2500
    } // library marker kkossev.commonLib, line 2501
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2502
} // library marker kkossev.commonLib, line 2503

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2505
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2506
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2507
} // library marker kkossev.commonLib, line 2508

void loadAllDefaults() { // library marker kkossev.commonLib, line 2510
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2511
    deleteAllSettings() // library marker kkossev.commonLib, line 2512
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2513
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2514
    deleteAllStates() // library marker kkossev.commonLib, line 2515
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2516
    initialize() // library marker kkossev.commonLib, line 2517
    configure()     // calls  also   configureDevice() // library marker kkossev.commonLib, line 2518
    updated() // library marker kkossev.commonLib, line 2519
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2520
} // library marker kkossev.commonLib, line 2521

void configureNow() { // library marker kkossev.commonLib, line 2523
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2524
} // library marker kkossev.commonLib, line 2525

/** // library marker kkossev.commonLib, line 2527
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2528
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2529
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2530
 */ // library marker kkossev.commonLib, line 2531
List<String> configure() { // library marker kkossev.commonLib, line 2532
    List<String> cmds = [] // library marker kkossev.commonLib, line 2533
    logInfo 'configure...' // library marker kkossev.commonLib, line 2534
    logDebug settings // library marker kkossev.commonLib, line 2535
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2536
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2537
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2538
    } // library marker kkossev.commonLib, line 2539
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2540
    cmds += configureDevice() // library marker kkossev.commonLib, line 2541
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2542
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2543
    return cmds // library marker kkossev.commonLib, line 2544
} // library marker kkossev.commonLib, line 2545

/** // library marker kkossev.commonLib, line 2547
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2548
 */ // library marker kkossev.commonLib, line 2549
void installed() { // library marker kkossev.commonLib, line 2550
    logInfo 'installed...' // library marker kkossev.commonLib, line 2551
    // populate some default values for attributes // library marker kkossev.commonLib, line 2552
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2553
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2554
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2555
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2556
} // library marker kkossev.commonLib, line 2557

/** // library marker kkossev.commonLib, line 2559
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2560
 */ // library marker kkossev.commonLib, line 2561
void initialize() { // library marker kkossev.commonLib, line 2562
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2563
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2564
    updateTuyaVersion() // library marker kkossev.commonLib, line 2565
    updateAqaraVersion() // library marker kkossev.commonLib, line 2566
} // library marker kkossev.commonLib, line 2567

/* // library marker kkossev.commonLib, line 2569
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2570
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2571
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2572
*/ // library marker kkossev.commonLib, line 2573

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2575
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2576
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2577
} // library marker kkossev.commonLib, line 2578

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2580
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2581
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2582
} // library marker kkossev.commonLib, line 2583

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2585
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2586
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2587
} // library marker kkossev.commonLib, line 2588

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2590
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2591
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2592
    cmd.each { // library marker kkossev.commonLib, line 2593
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2594
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2595
    } // library marker kkossev.commonLib, line 2596
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2597
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2598
} // library marker kkossev.commonLib, line 2599

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2601

String getDeviceInfo() { // library marker kkossev.commonLib, line 2603
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2604
} // library marker kkossev.commonLib, line 2605

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2607
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2608
} // library marker kkossev.commonLib, line 2609

void checkDriverVersion() { // library marker kkossev.commonLib, line 2611
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2612
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2613
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2614
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2615
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2616
        updateTuyaVersion() // library marker kkossev.commonLib, line 2617
        updateAqaraVersion() // library marker kkossev.commonLib, line 2618
    } // library marker kkossev.commonLib, line 2619
    // no driver version change // library marker kkossev.commonLib, line 2620
} // library marker kkossev.commonLib, line 2621

// credits @thebearmay // library marker kkossev.commonLib, line 2623
String getModel() { // library marker kkossev.commonLib, line 2624
    try { // library marker kkossev.commonLib, line 2625
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2626
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2627
    } catch (ignore) { // library marker kkossev.commonLib, line 2628
        try { // library marker kkossev.commonLib, line 2629
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2630
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2631
                return model // library marker kkossev.commonLib, line 2632
            } // library marker kkossev.commonLib, line 2633
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2634
            return '' // library marker kkossev.commonLib, line 2635
        } // library marker kkossev.commonLib, line 2636
    } // library marker kkossev.commonLib, line 2637
} // library marker kkossev.commonLib, line 2638

// credits @thebearmay // library marker kkossev.commonLib, line 2640
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2641
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2642
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2643
    String revision = tokens.last() // library marker kkossev.commonLib, line 2644
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2645
} // library marker kkossev.commonLib, line 2646

/** // library marker kkossev.commonLib, line 2648
 * called from TODO // library marker kkossev.commonLib, line 2649
 */ // library marker kkossev.commonLib, line 2650

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2652
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2653
    unschedule() // library marker kkossev.commonLib, line 2654
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2655
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2656

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2658
} // library marker kkossev.commonLib, line 2659

void resetStatistics() { // library marker kkossev.commonLib, line 2661
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2662
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2663
} // library marker kkossev.commonLib, line 2664

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2666
void resetStats() { // library marker kkossev.commonLib, line 2667
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2668
    state.stats = [:] // library marker kkossev.commonLib, line 2669
    state.states = [:] // library marker kkossev.commonLib, line 2670
    state.lastRx = [:] // library marker kkossev.commonLib, line 2671
    state.lastTx = [:] // library marker kkossev.commonLib, line 2672
    state.health = [:] // library marker kkossev.commonLib, line 2673
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2674
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2675
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2676
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2677
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2678
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2679
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2680
} // library marker kkossev.commonLib, line 2681

/** // library marker kkossev.commonLib, line 2683
 * called from TODO // library marker kkossev.commonLib, line 2684
 */ // library marker kkossev.commonLib, line 2685
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2686
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2687
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2688
        state.clear() // library marker kkossev.commonLib, line 2689
        unschedule() // library marker kkossev.commonLib, line 2690
        resetStats() // library marker kkossev.commonLib, line 2691
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2692
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2693
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2694
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2695
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2696
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2697
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2698
    } // library marker kkossev.commonLib, line 2699

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2701
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2702
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2703
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2704
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2705
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2706

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2708
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2709
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2710
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2711
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2712
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2713
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2714
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2715
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2716
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2717

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2719
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2720
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2721
    } // library marker kkossev.commonLib, line 2722
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2723
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2724
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2725
    } // library marker kkossev.commonLib, line 2726
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2727
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2728
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2729
    if (DEVICE_TYPE in ['Fingerbot'])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2730
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2731
    if (DEVICE_TYPE in ['ButtonDimmer']) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2732
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2733

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2735
    if ( mm != null) { // library marker kkossev.commonLib, line 2736
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2737
    } // library marker kkossev.commonLib, line 2738
    else { // library marker kkossev.commonLib, line 2739
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2740
    } // library marker kkossev.commonLib, line 2741
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2742
    if ( ep  != null) { // library marker kkossev.commonLib, line 2743
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2744
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2745
    } // library marker kkossev.commonLib, line 2746
    else { // library marker kkossev.commonLib, line 2747
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2748
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2749
    } // library marker kkossev.commonLib, line 2750
} // library marker kkossev.commonLib, line 2751

/** // library marker kkossev.commonLib, line 2753
 * called from TODO // library marker kkossev.commonLib, line 2754
 */ // library marker kkossev.commonLib, line 2755
void setDestinationEP() { // library marker kkossev.commonLib, line 2756
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2757
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2758
        state.destinationEP = ep // library marker kkossev.commonLib, line 2759
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2760
    } // library marker kkossev.commonLib, line 2761
    else { // library marker kkossev.commonLib, line 2762
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2763
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2764
    } // library marker kkossev.commonLib, line 2765
} // library marker kkossev.commonLib, line 2766

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2768
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2769
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2770
    } // library marker kkossev.commonLib, line 2771
} // library marker kkossev.commonLib, line 2772

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2774
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2775
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2776
    } // library marker kkossev.commonLib, line 2777
} // library marker kkossev.commonLib, line 2778

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2780
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2781
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2782
    } // library marker kkossev.commonLib, line 2783
} // library marker kkossev.commonLib, line 2784

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2786
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2787
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2788
    } // library marker kkossev.commonLib, line 2789
} // library marker kkossev.commonLib, line 2790

// _DEBUG mode only // library marker kkossev.commonLib, line 2792
void getAllProperties() { // library marker kkossev.commonLib, line 2793
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2794
    device.properties.each { it -> // library marker kkossev.commonLib, line 2795
        log.debug it // library marker kkossev.commonLib, line 2796
    } // library marker kkossev.commonLib, line 2797
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2798
    settings.each { it -> // library marker kkossev.commonLib, line 2799
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2800
    } // library marker kkossev.commonLib, line 2801
    log.trace 'Done' // library marker kkossev.commonLib, line 2802
} // library marker kkossev.commonLib, line 2803

// delete all Preferences // library marker kkossev.commonLib, line 2805
void deleteAllSettings() { // library marker kkossev.commonLib, line 2806
    settings.each { it -> // library marker kkossev.commonLib, line 2807
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2808
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2809
    } // library marker kkossev.commonLib, line 2810
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2811
} // library marker kkossev.commonLib, line 2812

// delete all attributes // library marker kkossev.commonLib, line 2814
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2815
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2816
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2817
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2818
    } // library marker kkossev.commonLib, line 2819
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2820
} // library marker kkossev.commonLib, line 2821

// delete all State Variables // library marker kkossev.commonLib, line 2823
void deleteAllStates() { // library marker kkossev.commonLib, line 2824
    state.each { it -> // library marker kkossev.commonLib, line 2825
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2826
    } // library marker kkossev.commonLib, line 2827
    state.clear() // library marker kkossev.commonLib, line 2828
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2829
} // library marker kkossev.commonLib, line 2830

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2832
    unschedule() // library marker kkossev.commonLib, line 2833
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2834
} // library marker kkossev.commonLib, line 2835

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2837
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2838
} // library marker kkossev.commonLib, line 2839

void parseTest(String par) { // library marker kkossev.commonLib, line 2841
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2842
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2843
    parse(par) // library marker kkossev.commonLib, line 2844
} // library marker kkossev.commonLib, line 2845

def testJob() { // library marker kkossev.commonLib, line 2847
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2848
} // library marker kkossev.commonLib, line 2849

/** // library marker kkossev.commonLib, line 2851
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2852
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2853
 */ // library marker kkossev.commonLib, line 2854
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2855
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2856
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2857
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2858
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2859
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2860
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2861
    String cron // library marker kkossev.commonLib, line 2862
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2863
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2864
    } // library marker kkossev.commonLib, line 2865
    else { // library marker kkossev.commonLib, line 2866
        if (minutes < 60) { // library marker kkossev.commonLib, line 2867
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2868
        } // library marker kkossev.commonLib, line 2869
        else { // library marker kkossev.commonLib, line 2870
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2871
        } // library marker kkossev.commonLib, line 2872
    } // library marker kkossev.commonLib, line 2873
    return cron // library marker kkossev.commonLib, line 2874
} // library marker kkossev.commonLib, line 2875

// credits @thebearmay // library marker kkossev.commonLib, line 2877
String formatUptime() { // library marker kkossev.commonLib, line 2878
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2879
} // library marker kkossev.commonLib, line 2880

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2882
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2883
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2884
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2885
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2886
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2887
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2888
} // library marker kkossev.commonLib, line 2889

boolean isTuya() { // library marker kkossev.commonLib, line 2891
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2892
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2893
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2894
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2895
} // library marker kkossev.commonLib, line 2896

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2898
    if (!isTuya()) { // library marker kkossev.commonLib, line 2899
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2900
        return // library marker kkossev.commonLib, line 2901
    } // library marker kkossev.commonLib, line 2902
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2903
    if (application != null) { // library marker kkossev.commonLib, line 2904
        Integer ver // library marker kkossev.commonLib, line 2905
        try { // library marker kkossev.commonLib, line 2906
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2907
        } // library marker kkossev.commonLib, line 2908
        catch (e) { // library marker kkossev.commonLib, line 2909
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2910
            return // library marker kkossev.commonLib, line 2911
        } // library marker kkossev.commonLib, line 2912
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2913
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2914
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2915
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2916
        } // library marker kkossev.commonLib, line 2917
    } // library marker kkossev.commonLib, line 2918
} // library marker kkossev.commonLib, line 2919

boolean isAqara() { // library marker kkossev.commonLib, line 2921
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2922
} // library marker kkossev.commonLib, line 2923

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2925
    if (!isAqara()) { // library marker kkossev.commonLib, line 2926
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2927
        return // library marker kkossev.commonLib, line 2928
    } // library marker kkossev.commonLib, line 2929
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2930
    if (application != null) { // library marker kkossev.commonLib, line 2931
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2932
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2933
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2934
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2935
        } // library marker kkossev.commonLib, line 2936
    } // library marker kkossev.commonLib, line 2937
} // library marker kkossev.commonLib, line 2938

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2940
    try { // library marker kkossev.commonLib, line 2941
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2942
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2943
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2944
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2945
    } catch (e) { // library marker kkossev.commonLib, line 2946
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2947
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2948
    } // library marker kkossev.commonLib, line 2949
} // library marker kkossev.commonLib, line 2950

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2952
    try { // library marker kkossev.commonLib, line 2953
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2954
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2955
        return date.getTime() // library marker kkossev.commonLib, line 2956
    } catch (e) { // library marker kkossev.commonLib, line 2957
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2958
        return now() // library marker kkossev.commonLib, line 2959
    } // library marker kkossev.commonLib, line 2960
} // library marker kkossev.commonLib, line 2961

void test(String par) { // library marker kkossev.commonLib, line 2963
    List<String> cmds = [] // library marker kkossev.commonLib, line 2964
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2965

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2967
    //parse(par) // library marker kkossev.commonLib, line 2968

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2970
} // library marker kkossev.commonLib, line 2971

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
