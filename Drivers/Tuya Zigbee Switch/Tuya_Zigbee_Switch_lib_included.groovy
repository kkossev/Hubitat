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
 * ver. 3.0.3  2024-02-24 kkossev  - (dev. branch) commonLib 3.0.3 allignment
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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.4', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.4  2024-03-29 kkossev  - (dev.branch) removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix;  // library marker kkossev.commonLib, line 36
  * // library marker kkossev.commonLib, line 37
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 38
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 39
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 41
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 44
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 47
 * // library marker kkossev.commonLib, line 48
*/ // library marker kkossev.commonLib, line 49

String commonLibVersion() { '3.0.4' } // library marker kkossev.commonLib, line 51
String thermostatLibStamp() { '2024/03/29 11:50 PM' } // library marker kkossev.commonLib, line 52

import groovy.transform.Field // library marker kkossev.commonLib, line 54
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 55
import hubitat.device.Protocol // library marker kkossev.commonLib, line 56
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 57
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 58
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 59
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 60
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 61
import java.math.BigDecimal // library marker kkossev.commonLib, line 62

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 64

metadata { // library marker kkossev.commonLib, line 66
        if (_DEBUG) { // library marker kkossev.commonLib, line 67
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 69
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 70
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 72
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 73
            ] // library marker kkossev.commonLib, line 74
        } // library marker kkossev.commonLib, line 75

        // common capabilities for all device types // library marker kkossev.commonLib, line 77
        capability 'Configuration' // library marker kkossev.commonLib, line 78
        capability 'Refresh' // library marker kkossev.commonLib, line 79
        capability 'Health Check' // library marker kkossev.commonLib, line 80

        // common attributes for all device types // library marker kkossev.commonLib, line 82
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 83
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 84
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 85

        // common commands for all device types // library marker kkossev.commonLib, line 87
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 91
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 92
            if (_DEBUG) { // library marker kkossev.commonLib, line 93
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 94
            } // library marker kkossev.commonLib, line 95
        } // library marker kkossev.commonLib, line 96
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 97
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 98
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 99
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 100
            ] // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 103
            capability 'Sensor' // library marker kkossev.commonLib, line 104
        } // library marker kkossev.commonLib, line 105
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 106
            capability 'MotionSensor' // library marker kkossev.commonLib, line 107
        } // library marker kkossev.commonLib, line 108
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Thermostat', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 109
            capability 'Actuator' // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 112
            capability 'Battery' // library marker kkossev.commonLib, line 113
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
        if (deviceType in  ['Thermostat']) { // library marker kkossev.commonLib, line 116
            capability 'Thermostat' // library marker kkossev.commonLib, line 117
        } // library marker kkossev.commonLib, line 118
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 119
            capability 'Switch' // library marker kkossev.commonLib, line 120
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 121
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 122
            } // library marker kkossev.commonLib, line 123
        } // library marker kkossev.commonLib, line 124
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 125
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 126
        } // library marker kkossev.commonLib, line 127
        if (deviceType in  ['AqaraCube']) { // library marker kkossev.commonLib, line 128
            capability 'PushableButton' // library marker kkossev.commonLib, line 129
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 130
            capability 'HoldableButton' // library marker kkossev.commonLib, line 131
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 132
        } // library marker kkossev.commonLib, line 133
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 134
            capability 'Momentary' // library marker kkossev.commonLib, line 135
        } // library marker kkossev.commonLib, line 136
        if (deviceType in  ['Device', 'THSensor', 'Thermostat']) { // library marker kkossev.commonLib, line 137
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 138
        } // library marker kkossev.commonLib, line 139
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 140
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 141
        } // library marker kkossev.commonLib, line 142
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 143
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 144
        } // library marker kkossev.commonLib, line 145

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 147
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 148

    preferences { // library marker kkossev.commonLib, line 150
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 151
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 152
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 153

        if (device) { // library marker kkossev.commonLib, line 155
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) && !isZigUSB()) { // library marker kkossev.commonLib, line 156
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 157
                input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 158
            } // library marker kkossev.commonLib, line 159
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 160
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 161
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 162
            } // library marker kkossev.commonLib, line 163

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 165
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 166
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 167
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 168
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 169
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 170
                } // library marker kkossev.commonLib, line 171
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 172
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 173
                } // library marker kkossev.commonLib, line 174
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 175
            } // library marker kkossev.commonLib, line 176
        } // library marker kkossev.commonLib, line 177
    } // library marker kkossev.commonLib, line 178
} // library marker kkossev.commonLib, line 179

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 181
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 182
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 183
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 184
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 185
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 186
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 187
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 188
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 189
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 190
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 191
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 192

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 194
    defaultValue: 1, // library marker kkossev.commonLib, line 195
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 196
] // library marker kkossev.commonLib, line 197
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 198
    defaultValue: 240, // library marker kkossev.commonLib, line 199
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 200
] // library marker kkossev.commonLib, line 201
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 202
    defaultValue: 0, // library marker kkossev.commonLib, line 203
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 204
] // library marker kkossev.commonLib, line 205

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 207
    defaultValue: 0, // library marker kkossev.commonLib, line 208
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 209
] // library marker kkossev.commonLib, line 210
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 211
    defaultValue: 0, // library marker kkossev.commonLib, line 212
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 213
] // library marker kkossev.commonLib, line 214

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 216
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 217
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 218
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 219
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 220
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 221
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 222
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 223
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 224
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 225
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 226
] // library marker kkossev.commonLib, line 227

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 229
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 230
boolean isChattyDeviceReport(final String description)  { return false /*(description?.contains("cluster: FC7E")) */ } // library marker kkossev.commonLib, line 231
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 232
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 233
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 234
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 235
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 236
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 237
boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 238

/** // library marker kkossev.commonLib, line 240
 * Parse Zigbee message // library marker kkossev.commonLib, line 241
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 242
 */ // library marker kkossev.commonLib, line 243
void parse(final String description) { // library marker kkossev.commonLib, line 244
    checkDriverVersion() // library marker kkossev.commonLib, line 245
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 246
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 247
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 248
    setHealthStatusOnline() // library marker kkossev.commonLib, line 249

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 251
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 252
        /* groovylint-disable-next-line ConstantIfExpression */ // library marker kkossev.commonLib, line 253
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 254
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 255
            return // library marker kkossev.commonLib, line 256
        } // library marker kkossev.commonLib, line 257
        parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 258
    } // library marker kkossev.commonLib, line 259
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 260
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 261
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 262
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 263
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 264
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 265
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 266
    } // library marker kkossev.commonLib, line 267
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 268
        return // library marker kkossev.commonLib, line 269
    } // library marker kkossev.commonLib, line 270
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 271

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 273
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 274
        return // library marker kkossev.commonLib, line 275
    } // library marker kkossev.commonLib, line 276
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 277
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 278
        return // library marker kkossev.commonLib, line 279
    } // library marker kkossev.commonLib, line 280
    if (!isChattyDeviceReport(description)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 281
    // // library marker kkossev.commonLib, line 282
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 283
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 284
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 285

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 287
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 288
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 292
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 296
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 300
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 304
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 305
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 308
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 309
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 310
            break // library marker kkossev.commonLib, line 311
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 312
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 313
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 316
            if (isZigUSB()) { // library marker kkossev.commonLib, line 317
                parseZigUSBAnlogInputCluster(description) // library marker kkossev.commonLib, line 318
            } // library marker kkossev.commonLib, line 319
            else { // library marker kkossev.commonLib, line 320
                parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 321
                descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 322
            } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 325
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 328
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 329
            break // library marker kkossev.commonLib, line 330
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 331
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 332
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 333
            break // library marker kkossev.commonLib, line 334
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 335
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 336
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 337
            break // library marker kkossev.commonLib, line 338
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 339
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 340
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 343
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 346
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 347
            break // library marker kkossev.commonLib, line 348
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 349
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 350
            break // library marker kkossev.commonLib, line 351
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 352
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 353
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 354
            break // library marker kkossev.commonLib, line 355
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 356
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 357
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 358
            break // library marker kkossev.commonLib, line 359
        case 0xE002 : // library marker kkossev.commonLib, line 360
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 361
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 362
            break // library marker kkossev.commonLib, line 363
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 364
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 365
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 366
            break // library marker kkossev.commonLib, line 367
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 368
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 369
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 370
            break // library marker kkossev.commonLib, line 371
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 372
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 373
            break // library marker kkossev.commonLib, line 374
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 375
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 376
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
        default: // library marker kkossev.commonLib, line 379
            if (settings.logEnable) { // library marker kkossev.commonLib, line 380
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 381
            } // library marker kkossev.commonLib, line 382
            break // library marker kkossev.commonLib, line 383
    } // library marker kkossev.commonLib, line 384
} // library marker kkossev.commonLib, line 385

/** // library marker kkossev.commonLib, line 387
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 388
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 389
 */ // library marker kkossev.commonLib, line 390
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 391
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 392
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 393
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 394
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 395
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 396
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 397
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 398
    } // library marker kkossev.commonLib, line 399
    else { // library marker kkossev.commonLib, line 400
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 401
    } // library marker kkossev.commonLib, line 402
} // library marker kkossev.commonLib, line 403

/** // library marker kkossev.commonLib, line 405
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 406
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 407
 */ // library marker kkossev.commonLib, line 408
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 409
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 410
    switch (commandId) { // library marker kkossev.commonLib, line 411
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 412
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 415
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 416
            break // library marker kkossev.commonLib, line 417
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 418
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 419
            break // library marker kkossev.commonLib, line 420
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 421
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 422
            break // library marker kkossev.commonLib, line 423
        case 0x0B: // default command response // library marker kkossev.commonLib, line 424
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 425
            break // library marker kkossev.commonLib, line 426
        default: // library marker kkossev.commonLib, line 427
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 428
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 429
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 430
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 431
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 432
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 433
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 434
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 435
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 436
            } // library marker kkossev.commonLib, line 437
            break // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
} // library marker kkossev.commonLib, line 440

/** // library marker kkossev.commonLib, line 442
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 443
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 444
 */ // library marker kkossev.commonLib, line 445
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 446
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 447
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 448
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 449
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 450
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 451
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
    else { // library marker kkossev.commonLib, line 454
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

/** // library marker kkossev.commonLib, line 459
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 460
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 461
 */ // library marker kkossev.commonLib, line 462
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 463
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 464
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 465
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 466
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 467
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 468
    } // library marker kkossev.commonLib, line 469
    else { // library marker kkossev.commonLib, line 470
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

/** // library marker kkossev.commonLib, line 475
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 476
 */ // library marker kkossev.commonLib, line 477
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 478
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 479
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 480
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 481
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 482
        state.reportingEnabled = true // library marker kkossev.commonLib, line 483
    } // library marker kkossev.commonLib, line 484
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 485
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 486
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 487
    } else { // library marker kkossev.commonLib, line 488
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 489
    } // library marker kkossev.commonLib, line 490
} // library marker kkossev.commonLib, line 491

/** // library marker kkossev.commonLib, line 493
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 494
 */ // library marker kkossev.commonLib, line 495
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 496
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 497
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 498
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 499
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 500
    if (status == 0) { // library marker kkossev.commonLib, line 501
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 502
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 503
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 504
        int delta = 0 // library marker kkossev.commonLib, line 505
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 506
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 507
        } // library marker kkossev.commonLib, line 508
        else { // library marker kkossev.commonLib, line 509
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 510
        } // library marker kkossev.commonLib, line 511
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
} // library marker kkossev.commonLib, line 517

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 519
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 520
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 521
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 522
        return false // library marker kkossev.commonLib, line 523
    } // library marker kkossev.commonLib, line 524
    // execute the customHandler function // library marker kkossev.commonLib, line 525
    boolean result = false // library marker kkossev.commonLib, line 526
    try { // library marker kkossev.commonLib, line 527
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 528
    } // library marker kkossev.commonLib, line 529
    catch (e) { // library marker kkossev.commonLib, line 530
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 531
        return false // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 534
    return result // library marker kkossev.commonLib, line 535
} // library marker kkossev.commonLib, line 536

/** // library marker kkossev.commonLib, line 538
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 539
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 540
 */ // library marker kkossev.commonLib, line 541
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 542
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 543
    final String commandId = data[0] // library marker kkossev.commonLib, line 544
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 545
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 546
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 547
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 548
    } else { // library marker kkossev.commonLib, line 549
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 550
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 551
        if (isZigUSB()) { // library marker kkossev.commonLib, line 552
            executeCustomHandler('customParseDefaultCommandResponse', descMap) // library marker kkossev.commonLib, line 553
        } // library marker kkossev.commonLib, line 554
    } // library marker kkossev.commonLib, line 555
} // library marker kkossev.commonLib, line 556

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 558
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 559
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 560
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 561
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 562
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 563
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 564
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 565
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 566
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 567
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 568
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 569
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 570
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 571
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 572
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 573

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 575
    0x00: 'Success', // library marker kkossev.commonLib, line 576
    0x01: 'Failure', // library marker kkossev.commonLib, line 577
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 578
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 579
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 580
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 581
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 582
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 583
    0x88: 'Read Only', // library marker kkossev.commonLib, line 584
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 585
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 586
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 587
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 588
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 589
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 590
    0x94: 'Time out', // library marker kkossev.commonLib, line 591
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 592
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 593
] // library marker kkossev.commonLib, line 594

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 596
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 597
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 598
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 599
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 600
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 601
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 602
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 603
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 604
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 605
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 606
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 607
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 608
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 609
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 610
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 611
] // library marker kkossev.commonLib, line 612

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 614
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 615
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 616
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 617
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 618
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 619
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 620
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 621
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 622
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 623
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 624
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 625
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 626
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 627
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 628
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 629
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 630
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 631
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 632
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 633
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 634
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 635
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 636
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 637
] // library marker kkossev.commonLib, line 638

/* // library marker kkossev.commonLib, line 640
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 641
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 642
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 643
 */ // library marker kkossev.commonLib, line 644
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 645
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 646
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 647
    } // library marker kkossev.commonLib, line 648
    else { // library marker kkossev.commonLib, line 649
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 650
    } // library marker kkossev.commonLib, line 651
} // library marker kkossev.commonLib, line 652

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 654
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 655
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 656
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 657
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 658
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 659
    return avg // library marker kkossev.commonLib, line 660
} // library marker kkossev.commonLib, line 661

/* // library marker kkossev.commonLib, line 663
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 664
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 665
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 666
*/ // library marker kkossev.commonLib, line 667
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 668

/** // library marker kkossev.commonLib, line 670
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 671
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 672
 */ // library marker kkossev.commonLib, line 673
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 674
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 675
    /* // library marker kkossev.commonLib, line 676
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 677
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 678
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 679
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 680
    */ // library marker kkossev.commonLib, line 681
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 682
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 683
        case 0x0000: // library marker kkossev.commonLib, line 684
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 685
            break // library marker kkossev.commonLib, line 686
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 687
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 688
            if (isPing) { // library marker kkossev.commonLib, line 689
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 690
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 691
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 692
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 693
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 694
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 695
                    sendRttEvent() // library marker kkossev.commonLib, line 696
                } // library marker kkossev.commonLib, line 697
                else { // library marker kkossev.commonLib, line 698
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 699
                } // library marker kkossev.commonLib, line 700
                state.states['isPing'] = false // library marker kkossev.commonLib, line 701
            } // library marker kkossev.commonLib, line 702
            else { // library marker kkossev.commonLib, line 703
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 704
            } // library marker kkossev.commonLib, line 705
            break // library marker kkossev.commonLib, line 706
        case 0x0004: // library marker kkossev.commonLib, line 707
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 708
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 709
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 710
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 711
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 712
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 713
            } // library marker kkossev.commonLib, line 714
            break // library marker kkossev.commonLib, line 715
        case 0x0005: // library marker kkossev.commonLib, line 716
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 717
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 718
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 719
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 720
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 721
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 722
            } // library marker kkossev.commonLib, line 723
            break // library marker kkossev.commonLib, line 724
        case 0x0007: // library marker kkossev.commonLib, line 725
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 726
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 727
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 728
            break // library marker kkossev.commonLib, line 729
        case 0xFFDF: // library marker kkossev.commonLib, line 730
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 731
            break // library marker kkossev.commonLib, line 732
        case 0xFFE2: // library marker kkossev.commonLib, line 733
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 734
            break // library marker kkossev.commonLib, line 735
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 736
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 737
            break // library marker kkossev.commonLib, line 738
        case 0xFFFE: // library marker kkossev.commonLib, line 739
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 740
            break // library marker kkossev.commonLib, line 741
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 742
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 743
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 744
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 745
            break // library marker kkossev.commonLib, line 746
        default: // library marker kkossev.commonLib, line 747
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 748
            break // library marker kkossev.commonLib, line 749
    } // library marker kkossev.commonLib, line 750
} // library marker kkossev.commonLib, line 751

/* // library marker kkossev.commonLib, line 753
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 754
 * power cluster            0x0001 // library marker kkossev.commonLib, line 755
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 756
*/ // library marker kkossev.commonLib, line 757
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 758
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 759
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 760
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 761
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 762
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 766
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 767
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 768
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 769
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 770
        } // library marker kkossev.commonLib, line 771
    } // library marker kkossev.commonLib, line 772
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 773
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 774
    } // library marker kkossev.commonLib, line 775
    else { // library marker kkossev.commonLib, line 776
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 777
    } // library marker kkossev.commonLib, line 778
} // library marker kkossev.commonLib, line 779

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 781
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 782
    Map result = [:] // library marker kkossev.commonLib, line 783
    BigDecimal volts = BigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 784
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 785
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 786
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 787
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 788
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 789
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 790
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 791
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 792
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 793
            result.name = 'battery' // library marker kkossev.commonLib, line 794
            result.unit  = '%' // library marker kkossev.commonLib, line 795
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
        else { // library marker kkossev.commonLib, line 798
            result.value = volts // library marker kkossev.commonLib, line 799
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 800
            result.unit  = 'V' // library marker kkossev.commonLib, line 801
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
        result.type = 'physical' // library marker kkossev.commonLib, line 804
        result.isStateChange = true // library marker kkossev.commonLib, line 805
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 806
        sendEvent(result) // library marker kkossev.commonLib, line 807
    } // library marker kkossev.commonLib, line 808
    else { // library marker kkossev.commonLib, line 809
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 810
    } // library marker kkossev.commonLib, line 811
} // library marker kkossev.commonLib, line 812

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 814
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 815
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 816
        return // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    Map map = [:] // library marker kkossev.commonLib, line 819
    map.name = 'battery' // library marker kkossev.commonLib, line 820
    map.timeStamp = now() // library marker kkossev.commonLib, line 821
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 822
    map.unit  = '%' // library marker kkossev.commonLib, line 823
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 824
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 825
    map.isStateChange = true // library marker kkossev.commonLib, line 826
    // // library marker kkossev.commonLib, line 827
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 828
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 829
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 830
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 831
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 832
        // send it now! // library marker kkossev.commonLib, line 833
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 834
    } // library marker kkossev.commonLib, line 835
    else { // library marker kkossev.commonLib, line 836
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 837
        map.delayed = delayedTime // library marker kkossev.commonLib, line 838
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 839
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 840
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 841
    } // library marker kkossev.commonLib, line 842
} // library marker kkossev.commonLib, line 843

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 845
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 846
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 847
    sendEvent(map) // library marker kkossev.commonLib, line 848
} // library marker kkossev.commonLib, line 849

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 851
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 852
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 853
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 854
    sendEvent(map) // library marker kkossev.commonLib, line 855
} // library marker kkossev.commonLib, line 856

/* // library marker kkossev.commonLib, line 858
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 859
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 860
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 861
*/ // library marker kkossev.commonLib, line 862
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 863
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 864
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 865
} // library marker kkossev.commonLib, line 866

/* // library marker kkossev.commonLib, line 868
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 869
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 870
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 871
*/ // library marker kkossev.commonLib, line 872
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 873
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 874
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 875
    } // library marker kkossev.commonLib, line 876
    else { // library marker kkossev.commonLib, line 877
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 878
    } // library marker kkossev.commonLib, line 879
} // library marker kkossev.commonLib, line 880

/* // library marker kkossev.commonLib, line 882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 883
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 884
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 885
*/ // library marker kkossev.commonLib, line 886
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 887
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 888
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 889
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 890
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 891
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 892
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 893
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 894
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 895
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 896
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 897
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 898
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 899
            } // library marker kkossev.commonLib, line 900
            else { // library marker kkossev.commonLib, line 901
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 902
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 903
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 904
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 905
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 906
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 907
                        return // library marker kkossev.commonLib, line 908
                    } // library marker kkossev.commonLib, line 909
                } // library marker kkossev.commonLib, line 910
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 911
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 912
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 913
            } // library marker kkossev.commonLib, line 914
            break // library marker kkossev.commonLib, line 915
        case 0x01: // View group // library marker kkossev.commonLib, line 916
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 917
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 918
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 919
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 920
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 921
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 922
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 923
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 924
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 925
            } // library marker kkossev.commonLib, line 926
            else { // library marker kkossev.commonLib, line 927
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 928
            } // library marker kkossev.commonLib, line 929
            break // library marker kkossev.commonLib, line 930
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 931
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 932
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 933
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 934
            final Set<String> groups = [] // library marker kkossev.commonLib, line 935
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 936
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 937
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 938
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 939
            } // library marker kkossev.commonLib, line 940
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 941
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 942
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 943
            break // library marker kkossev.commonLib, line 944
        case 0x03: // Remove group // library marker kkossev.commonLib, line 945
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 946
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 947
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 948
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 949
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 950
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 951
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 952
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 953
            } // library marker kkossev.commonLib, line 954
            else { // library marker kkossev.commonLib, line 955
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 956
            } // library marker kkossev.commonLib, line 957
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 958
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 959
            if (index >= 0) { // library marker kkossev.commonLib, line 960
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 961
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 962
            } // library marker kkossev.commonLib, line 963
            break // library marker kkossev.commonLib, line 964
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 965
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 966
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 967
            break // library marker kkossev.commonLib, line 968
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 969
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 970
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 971
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 972
            break // library marker kkossev.commonLib, line 973
        default: // library marker kkossev.commonLib, line 974
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 975
            break // library marker kkossev.commonLib, line 976
    } // library marker kkossev.commonLib, line 977
} // library marker kkossev.commonLib, line 978

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 980
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 981
    List<String> cmds = [] // library marker kkossev.commonLib, line 982
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 983
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 984
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 985
        return [] // library marker kkossev.commonLib, line 986
    } // library marker kkossev.commonLib, line 987
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 988
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 989
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 990
    return cmds // library marker kkossev.commonLib, line 991
} // library marker kkossev.commonLib, line 992

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 994
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 995
    List<String> cmds = [] // library marker kkossev.commonLib, line 996
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 997
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 998
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 999
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1000
    return cmds // library marker kkossev.commonLib, line 1001
} // library marker kkossev.commonLib, line 1002

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1004
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1005
    List<String> cmds = [] // library marker kkossev.commonLib, line 1006
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1007
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1008
    return cmds // library marker kkossev.commonLib, line 1009
} // library marker kkossev.commonLib, line 1010

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1012
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1013
    List<String> cmds = [] // library marker kkossev.commonLib, line 1014
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1015
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1016
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1017
        return [] // library marker kkossev.commonLib, line 1018
    } // library marker kkossev.commonLib, line 1019
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1020
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1021
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1022
    return cmds // library marker kkossev.commonLib, line 1023
} // library marker kkossev.commonLib, line 1024

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1026
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1027
    List<String> cmds = [] // library marker kkossev.commonLib, line 1028
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1029
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1030
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1031
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1032
    return cmds // library marker kkossev.commonLib, line 1033
} // library marker kkossev.commonLib, line 1034

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1036
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1037
    List<String> cmds = [] // library marker kkossev.commonLib, line 1038
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1039
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1040
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1041
    return cmds // library marker kkossev.commonLib, line 1042
} // library marker kkossev.commonLib, line 1043

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1045
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1046
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1047
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1048
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1049
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1050
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1051
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1052
] // library marker kkossev.commonLib, line 1053

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1055
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1056
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1057
    List<String> cmds = [] // library marker kkossev.commonLib, line 1058
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1059
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1060
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1061
    def value // library marker kkossev.commonLib, line 1062
    Boolean validated = false // library marker kkossev.commonLib, line 1063
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1064
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1065
        return // library marker kkossev.commonLib, line 1066
    } // library marker kkossev.commonLib, line 1067
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1068
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1069
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1070
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1071
        return // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
    // // library marker kkossev.commonLib, line 1074
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1075
    def func // library marker kkossev.commonLib, line 1076
    try { // library marker kkossev.commonLib, line 1077
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1078
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1079
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1080
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1081
    } // library marker kkossev.commonLib, line 1082
    catch (e) { // library marker kkossev.commonLib, line 1083
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1084
        return // library marker kkossev.commonLib, line 1085
    } // library marker kkossev.commonLib, line 1086

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1088
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1089
} // library marker kkossev.commonLib, line 1090

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1092
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1093
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1094
} // library marker kkossev.commonLib, line 1095

/* // library marker kkossev.commonLib, line 1097
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1098
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1099
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1100
*/ // library marker kkossev.commonLib, line 1101

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1103
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1104
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1107
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1108
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1109
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1112
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1113
    } // library marker kkossev.commonLib, line 1114
    else { // library marker kkossev.commonLib, line 1115
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1116
    } // library marker kkossev.commonLib, line 1117
} // library marker kkossev.commonLib, line 1118

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1120
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1121
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1122

void toggle() { // library marker kkossev.commonLib, line 1124
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1125
    String state = '' // library marker kkossev.commonLib, line 1126
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1127
        state = 'on' // library marker kkossev.commonLib, line 1128
    } // library marker kkossev.commonLib, line 1129
    else { // library marker kkossev.commonLib, line 1130
        state = 'off' // library marker kkossev.commonLib, line 1131
    } // library marker kkossev.commonLib, line 1132
    descriptionText += state // library marker kkossev.commonLib, line 1133
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1134
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1135
} // library marker kkossev.commonLib, line 1136

void off() { // library marker kkossev.commonLib, line 1138
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1139
        customOff() // library marker kkossev.commonLib, line 1140
        return // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1143
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1144
        return // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1147
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1148
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1149
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1150
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1151
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1152
        } // library marker kkossev.commonLib, line 1153
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1154
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1155
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1156
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1157
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1158
    } // library marker kkossev.commonLib, line 1159
    /* // library marker kkossev.commonLib, line 1160
    else { // library marker kkossev.commonLib, line 1161
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1162
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1163
        } // library marker kkossev.commonLib, line 1164
        else { // library marker kkossev.commonLib, line 1165
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1166
            return // library marker kkossev.commonLib, line 1167
        } // library marker kkossev.commonLib, line 1168
    } // library marker kkossev.commonLib, line 1169
    */ // library marker kkossev.commonLib, line 1170

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1172
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1173
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1174
} // library marker kkossev.commonLib, line 1175

void on() { // library marker kkossev.commonLib, line 1177
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1178
        customOn() // library marker kkossev.commonLib, line 1179
        return // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1182
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1183
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1184
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1185
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1186
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1187
        } // library marker kkossev.commonLib, line 1188
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1189
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1190
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1191
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1192
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    /* // library marker kkossev.commonLib, line 1195
    else { // library marker kkossev.commonLib, line 1196
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1197
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1198
        } // library marker kkossev.commonLib, line 1199
        else { // library marker kkossev.commonLib, line 1200
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1201
            return // library marker kkossev.commonLib, line 1202
        } // library marker kkossev.commonLib, line 1203
    } // library marker kkossev.commonLib, line 1204
    */ // library marker kkossev.commonLib, line 1205
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1206
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1207
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1208
} // library marker kkossev.commonLib, line 1209

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1211
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1212
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1213
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1214
    } // library marker kkossev.commonLib, line 1215
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1216
    Map map = [:] // library marker kkossev.commonLib, line 1217
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1218
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1219
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1220
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1221
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1222
        return // library marker kkossev.commonLib, line 1223
    } // library marker kkossev.commonLib, line 1224
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1225
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1226
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1227
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1228
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1229
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1230
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1231
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1232
    } else { // library marker kkossev.commonLib, line 1233
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1234
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1235
    } // library marker kkossev.commonLib, line 1236
    map.name = 'switch' // library marker kkossev.commonLib, line 1237
    map.value = value // library marker kkossev.commonLib, line 1238
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1239
    if (isRefresh) { // library marker kkossev.commonLib, line 1240
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1241
        map.isStateChange = true // library marker kkossev.commonLib, line 1242
    } else { // library marker kkossev.commonLib, line 1243
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1244
    } // library marker kkossev.commonLib, line 1245
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1246
    sendEvent(map) // library marker kkossev.commonLib, line 1247
    clearIsDigital() // library marker kkossev.commonLib, line 1248
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1249
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1250
    }     // library marker kkossev.commonLib, line 1251
} // library marker kkossev.commonLib, line 1252

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1254
    '0': 'switch off', // library marker kkossev.commonLib, line 1255
    '1': 'switch on', // library marker kkossev.commonLib, line 1256
    '2': 'switch last state' // library marker kkossev.commonLib, line 1257
] // library marker kkossev.commonLib, line 1258

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1260
    '0': 'toggle', // library marker kkossev.commonLib, line 1261
    '1': 'state', // library marker kkossev.commonLib, line 1262
    '2': 'momentary' // library marker kkossev.commonLib, line 1263
] // library marker kkossev.commonLib, line 1264

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1266
    Map descMap = [:] // library marker kkossev.commonLib, line 1267
    try { // library marker kkossev.commonLib, line 1268
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1269
    } // library marker kkossev.commonLib, line 1270
    catch (e1) { // library marker kkossev.commonLib, line 1271
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1272
        // try alternative custom parsing // library marker kkossev.commonLib, line 1273
        descMap = [:] // library marker kkossev.commonLib, line 1274
        try { // library marker kkossev.commonLib, line 1275
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1276
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1277
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1278
            } // library marker kkossev.commonLib, line 1279
        } // library marker kkossev.commonLib, line 1280
        catch (e2) { // library marker kkossev.commonLib, line 1281
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1282
            return [:] // library marker kkossev.commonLib, line 1283
        } // library marker kkossev.commonLib, line 1284
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1285
    } // library marker kkossev.commonLib, line 1286
    return descMap // library marker kkossev.commonLib, line 1287
} // library marker kkossev.commonLib, line 1288

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1290
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1291
        return false // library marker kkossev.commonLib, line 1292
    } // library marker kkossev.commonLib, line 1293
    // try to parse ... // library marker kkossev.commonLib, line 1294
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1295
    Map descMap = [:] // library marker kkossev.commonLib, line 1296
    try { // library marker kkossev.commonLib, line 1297
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1298
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1299
    } // library marker kkossev.commonLib, line 1300
    catch (e) { // library marker kkossev.commonLib, line 1301
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1302
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1303
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1304
        return true // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1308
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1309
    } // library marker kkossev.commonLib, line 1310
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1311
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1312
    } // library marker kkossev.commonLib, line 1313
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1314
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1315
    } // library marker kkossev.commonLib, line 1316
    else { // library marker kkossev.commonLib, line 1317
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1318
        return false // library marker kkossev.commonLib, line 1319
    } // library marker kkossev.commonLib, line 1320
    return true    // processed // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1324
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1325
  /* // library marker kkossev.commonLib, line 1326
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1327
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1328
        return true // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
*/ // library marker kkossev.commonLib, line 1331
    Map descMap = [:] // library marker kkossev.commonLib, line 1332
    try { // library marker kkossev.commonLib, line 1333
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1334
    } // library marker kkossev.commonLib, line 1335
    catch (e1) { // library marker kkossev.commonLib, line 1336
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1337
        // try alternative custom parsing // library marker kkossev.commonLib, line 1338
        descMap = [:] // library marker kkossev.commonLib, line 1339
        try { // library marker kkossev.commonLib, line 1340
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1341
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1342
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1343
            } // library marker kkossev.commonLib, line 1344
        } // library marker kkossev.commonLib, line 1345
        catch (e2) { // library marker kkossev.commonLib, line 1346
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1347
            return true // library marker kkossev.commonLib, line 1348
        } // library marker kkossev.commonLib, line 1349
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1352
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1353
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1354
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1355
        return false // library marker kkossev.commonLib, line 1356
    } // library marker kkossev.commonLib, line 1357
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1358
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1359
    // attribute report received // library marker kkossev.commonLib, line 1360
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1361
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1362
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1363
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
    attrData.each { // library marker kkossev.commonLib, line 1366
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1367
        //def map = [:] // library marker kkossev.commonLib, line 1368
        if (it.status == '86') { // library marker kkossev.commonLib, line 1369
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1370
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1371
        } // library marker kkossev.commonLib, line 1372
        switch (it.cluster) { // library marker kkossev.commonLib, line 1373
            case '0000' : // library marker kkossev.commonLib, line 1374
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1375
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1376
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1377
                } // library marker kkossev.commonLib, line 1378
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1379
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1380
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1381
                } // library marker kkossev.commonLib, line 1382
                else { // library marker kkossev.commonLib, line 1383
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1384
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1385
                } // library marker kkossev.commonLib, line 1386
                break // library marker kkossev.commonLib, line 1387
            default : // library marker kkossev.commonLib, line 1388
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1389
                break // library marker kkossev.commonLib, line 1390
        } // switch // library marker kkossev.commonLib, line 1391
    } // for each attribute // library marker kkossev.commonLib, line 1392
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1393
} // library marker kkossev.commonLib, line 1394

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1396

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1398
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1399
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1400
    def mode // library marker kkossev.commonLib, line 1401
    String attrName // library marker kkossev.commonLib, line 1402
    if (it.value == null) { // library marker kkossev.commonLib, line 1403
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1404
        return // library marker kkossev.commonLib, line 1405
    } // library marker kkossev.commonLib, line 1406
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1407
    switch (it.attrId) { // library marker kkossev.commonLib, line 1408
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1409
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1410
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1411
            break // library marker kkossev.commonLib, line 1412
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1413
            attrName = 'On Time' // library marker kkossev.commonLib, line 1414
            mode = value // library marker kkossev.commonLib, line 1415
            break // library marker kkossev.commonLib, line 1416
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1417
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1418
            mode = value // library marker kkossev.commonLib, line 1419
            break // library marker kkossev.commonLib, line 1420
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1421
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1422
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1423
            break // library marker kkossev.commonLib, line 1424
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1425
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1426
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1427
            break // library marker kkossev.commonLib, line 1428
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1429
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1430
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1431
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1432
            } // library marker kkossev.commonLib, line 1433
            else { // library marker kkossev.commonLib, line 1434
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1435
            } // library marker kkossev.commonLib, line 1436
            break // library marker kkossev.commonLib, line 1437
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1438
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1439
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1440
            break // library marker kkossev.commonLib, line 1441
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1442
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1443
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1444
            break // library marker kkossev.commonLib, line 1445
        default : // library marker kkossev.commonLib, line 1446
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1447
            return // library marker kkossev.commonLib, line 1448
    } // library marker kkossev.commonLib, line 1449
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1450
} // library marker kkossev.commonLib, line 1451

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1453
    Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1454
    if (txtEnable) { log.info "${device.displayName } $event.descriptionText" } // library marker kkossev.commonLib, line 1455
    sendEvent(event) // library marker kkossev.commonLib, line 1456
} // library marker kkossev.commonLib, line 1457

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1459
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1460
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.commonLib, line 1461
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1462
} // library marker kkossev.commonLib, line 1463

void push(int buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1465
    logDebug "push button $buttonNumber" // library marker kkossev.commonLib, line 1466
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.commonLib, line 1467
    sendButtonEvent(buttonNumber, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

void doubleTap(int buttonNumber) { // library marker kkossev.commonLib, line 1471
    sendButtonEvent(buttonNumber, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1472
} // library marker kkossev.commonLib, line 1473

void hold(int buttonNumber) { // library marker kkossev.commonLib, line 1475
    sendButtonEvent(buttonNumber, 'held', isDigital = true) // library marker kkossev.commonLib, line 1476
} // library marker kkossev.commonLib, line 1477

void release(int buttonNumber) { // library marker kkossev.commonLib, line 1479
    sendButtonEvent(buttonNumber, 'released', isDigital = true) // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1483
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1487
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1488
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

/* // library marker kkossev.commonLib, line 1492
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1493
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1494
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1495
*/ // library marker kkossev.commonLib, line 1496
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1497
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1498
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1499
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1502
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1503
    } // library marker kkossev.commonLib, line 1504
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1505
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1506
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1507
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
    else { // library marker kkossev.commonLib, line 1510
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1511
    } // library marker kkossev.commonLib, line 1512
} // library marker kkossev.commonLib, line 1513

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1515
    int value = rawValue as int // library marker kkossev.commonLib, line 1516
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1517
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1518
    Map map = [:] // library marker kkossev.commonLib, line 1519

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1521
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1522

    map.name = 'level' // library marker kkossev.commonLib, line 1524
    map.value = value // library marker kkossev.commonLib, line 1525
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1526
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1527
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1528
        map.isStateChange = true // library marker kkossev.commonLib, line 1529
    } // library marker kkossev.commonLib, line 1530
    else { // library marker kkossev.commonLib, line 1531
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1532
    } // library marker kkossev.commonLib, line 1533
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1534
    sendEvent(map) // library marker kkossev.commonLib, line 1535
    clearIsDigital() // library marker kkossev.commonLib, line 1536
} // library marker kkossev.commonLib, line 1537

/** // library marker kkossev.commonLib, line 1539
 * Get the level transition rate // library marker kkossev.commonLib, line 1540
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1541
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1542
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1543
 */ // library marker kkossev.commonLib, line 1544
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1545
    int rate = 0 // library marker kkossev.commonLib, line 1546
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1547
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1548
    if (!isOn) { // library marker kkossev.commonLib, line 1549
        currentLevel = 0 // library marker kkossev.commonLib, line 1550
    } // library marker kkossev.commonLib, line 1551
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1552
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1553
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1554
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1555
    } else { // library marker kkossev.commonLib, line 1556
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1557
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1558
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1559
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1560
        } // library marker kkossev.commonLib, line 1561
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1562
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1563
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1564
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1565
        } // library marker kkossev.commonLib, line 1566
    } // library marker kkossev.commonLib, line 1567
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1568
    return rate // library marker kkossev.commonLib, line 1569
} // library marker kkossev.commonLib, line 1570

// Command option that enable changes when off // library marker kkossev.commonLib, line 1572
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1573

/** // library marker kkossev.commonLib, line 1575
 * Constrain a value to a range // library marker kkossev.commonLib, line 1576
 * @param value value to constrain // library marker kkossev.commonLib, line 1577
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1578
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1579
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1580
 */ // library marker kkossev.commonLib, line 1581
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1582
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1583
        return value // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1586
} // library marker kkossev.commonLib, line 1587

/** // library marker kkossev.commonLib, line 1589
 * Constrain a value to a range // library marker kkossev.commonLib, line 1590
 * @param value value to constrain // library marker kkossev.commonLib, line 1591
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1592
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1593
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1594
 */ // library marker kkossev.commonLib, line 1595
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1596
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1597
        return value as Integer // library marker kkossev.commonLib, line 1598
    } // library marker kkossev.commonLib, line 1599
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1600
} // library marker kkossev.commonLib, line 1601

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1603
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1604

/** // library marker kkossev.commonLib, line 1606
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1607
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1608
 * @param commands commands to execute // library marker kkossev.commonLib, line 1609
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1610
 */ // library marker kkossev.commonLib, line 1611
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1612
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1613
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1614
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1615
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1616
    } // library marker kkossev.commonLib, line 1617
    return [] // library marker kkossev.commonLib, line 1618
} // library marker kkossev.commonLib, line 1619

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1621
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1622
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1623
} // library marker kkossev.commonLib, line 1624

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1626
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1627
} // library marker kkossev.commonLib, line 1628

/** // library marker kkossev.commonLib, line 1630
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1631
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1632
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1633
 */ // library marker kkossev.commonLib, line 1634
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1635
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1636
    List<String> cmds = [] // library marker kkossev.commonLib, line 1637
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1638
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1639
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1640
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1641
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1642
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1643
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1644
    } // library marker kkossev.commonLib, line 1645
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1646
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1647
    /* // library marker kkossev.commonLib, line 1648
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1649
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1650
    */ // library marker kkossev.commonLib, line 1651
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1652
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1653
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1654

    return cmds // library marker kkossev.commonLib, line 1656
} // library marker kkossev.commonLib, line 1657

/** // library marker kkossev.commonLib, line 1659
 * Set Level Command // library marker kkossev.commonLib, line 1660
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1661
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1662
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1663
 */ // library marker kkossev.commonLib, line 1664
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1665
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1666
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1667
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1668
        return // library marker kkossev.commonLib, line 1669
    } // library marker kkossev.commonLib, line 1670
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1671
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1672
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1673
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1674
} // library marker kkossev.commonLib, line 1675

/* // library marker kkossev.commonLib, line 1677
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1678
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1679
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1680
*/ // library marker kkossev.commonLib, line 1681
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1682
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1683
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1684
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1685
    } // library marker kkossev.commonLib, line 1686
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1687
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1688
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1689
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1690
    } // library marker kkossev.commonLib, line 1691
    else { // library marker kkossev.commonLib, line 1692
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1693
    } // library marker kkossev.commonLib, line 1694
} // library marker kkossev.commonLib, line 1695

/* // library marker kkossev.commonLib, line 1697
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1698
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1699
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1700
*/ // library marker kkossev.commonLib, line 1701
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1702
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1703
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1704
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1705
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1706
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1707
} // library marker kkossev.commonLib, line 1708

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1710
    Map eventMap = [:] // library marker kkossev.commonLib, line 1711
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1712
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1713
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1714
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1715
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1716
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1717
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1718
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1719
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1720
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1721
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1722
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1723
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1724
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1725
        return // library marker kkossev.commonLib, line 1726
    } // library marker kkossev.commonLib, line 1727
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1728
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1729
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1730
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1731
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1732
    } // library marker kkossev.commonLib, line 1733
    else {         // queue the event // library marker kkossev.commonLib, line 1734
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1735
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1736
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1737
    } // library marker kkossev.commonLib, line 1738
} // library marker kkossev.commonLib, line 1739

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1741
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1742
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1743
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1744
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1745
} // library marker kkossev.commonLib, line 1746

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1748

/* // library marker kkossev.commonLib, line 1750
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1751
 * temperature // library marker kkossev.commonLib, line 1752
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1753
*/ // library marker kkossev.commonLib, line 1754
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1755
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1756
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1757
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1758
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1759
} // library marker kkossev.commonLib, line 1760

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1762
    Map eventMap = [:] // library marker kkossev.commonLib, line 1763
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1764
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1765
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1766
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1767
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1768
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1769
    } // library marker kkossev.commonLib, line 1770
    else { // library marker kkossev.commonLib, line 1771
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1772
    } // library marker kkossev.commonLib, line 1773
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1774
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1775
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1776
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1777
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1778
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1779
        return // library marker kkossev.commonLib, line 1780
    } // library marker kkossev.commonLib, line 1781
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1782
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1783
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1784
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1785
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1786
    } // library marker kkossev.commonLib, line 1787
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1788
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1789
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1790
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1791
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1792
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1793
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1794
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1795
    } // library marker kkossev.commonLib, line 1796
    else {         // queue the event // library marker kkossev.commonLib, line 1797
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1798
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1799
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1800
    } // library marker kkossev.commonLib, line 1801
} // library marker kkossev.commonLib, line 1802

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1804
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1805
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1806
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1807
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1808
} // library marker kkossev.commonLib, line 1809

/* // library marker kkossev.commonLib, line 1811
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1812
 * humidity // library marker kkossev.commonLib, line 1813
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1814
*/ // library marker kkossev.commonLib, line 1815
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1816
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1817
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1818
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1819
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1820
} // library marker kkossev.commonLib, line 1821

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1823
    Map eventMap = [:] // library marker kkossev.commonLib, line 1824
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1825
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1826
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1827
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1828
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1829
        return // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1832
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1833
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1834
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1835
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1836
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1837
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1838
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1839
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1840
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1841
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1842
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1843
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1844
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1845
    } // library marker kkossev.commonLib, line 1846
    else { // library marker kkossev.commonLib, line 1847
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1848
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1849
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1850
    } // library marker kkossev.commonLib, line 1851
} // library marker kkossev.commonLib, line 1852

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1854
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1855
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1856
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1857
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1858
} // library marker kkossev.commonLib, line 1859

/* // library marker kkossev.commonLib, line 1861
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1862
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1863
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1864
*/ // library marker kkossev.commonLib, line 1865

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1867
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1868
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1869
    } // library marker kkossev.commonLib, line 1870
} // library marker kkossev.commonLib, line 1871

/* // library marker kkossev.commonLib, line 1873
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1874
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1875
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1876
*/ // library marker kkossev.commonLib, line 1877

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1879
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1880
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1881
    } // library marker kkossev.commonLib, line 1882
} // library marker kkossev.commonLib, line 1883

/* // library marker kkossev.commonLib, line 1885
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1886
 * pm2.5 // library marker kkossev.commonLib, line 1887
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1888
*/ // library marker kkossev.commonLib, line 1889
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1890
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1891
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1892
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1893
    BigInteger bigIntegerValue = intBitsToFloat(value.intValue()).toBigInteger() // library marker kkossev.commonLib, line 1894
    handlePm25Event(bigIntegerValue as Integer) // library marker kkossev.commonLib, line 1895
} // library marker kkossev.commonLib, line 1896
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1897

/* // library marker kkossev.commonLib, line 1899
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1900
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1901
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1902
*/ // library marker kkossev.commonLib, line 1903
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1904
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1905
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1906
    } // library marker kkossev.commonLib, line 1907
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1908
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1909
    } // library marker kkossev.commonLib, line 1910
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1911
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1912
    } // library marker kkossev.commonLib, line 1913
    else { // library marker kkossev.commonLib, line 1914
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1915
    } // library marker kkossev.commonLib, line 1916
} // library marker kkossev.commonLib, line 1917

/* // library marker kkossev.commonLib, line 1919
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1920
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1921
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1922
*/ // library marker kkossev.commonLib, line 1923

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1925
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1926
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1927
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1928
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1929
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1930
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
    else { // library marker kkossev.commonLib, line 1933
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1934
    } // library marker kkossev.commonLib, line 1935
} // library marker kkossev.commonLib, line 1936

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1938
    Map eventMap = [:] // library marker kkossev.commonLib, line 1939
    eventMap.value = value // library marker kkossev.commonLib, line 1940
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1941
    eventMap.unit = '' // library marker kkossev.commonLib, line 1942
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1943
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1944
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1945
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1946
} // library marker kkossev.commonLib, line 1947

/* // library marker kkossev.commonLib, line 1949
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1950
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1951
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1952
*/ // library marker kkossev.commonLib, line 1953

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1955
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1956
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1957
    } // library marker kkossev.commonLib, line 1958
    else { // library marker kkossev.commonLib, line 1959
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1960
    } // library marker kkossev.commonLib, line 1961
} // library marker kkossev.commonLib, line 1962

/* // library marker kkossev.commonLib, line 1964
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1965
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1966
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1967
*/ // library marker kkossev.commonLib, line 1968
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1969
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1970
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1971
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1972
    } // library marker kkossev.commonLib, line 1973
    else { // library marker kkossev.commonLib, line 1974
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1975
    } // library marker kkossev.commonLib, line 1976
} // library marker kkossev.commonLib, line 1977

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1979

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1981
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1982
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1983
    } // library marker kkossev.commonLib, line 1984
    else { // library marker kkossev.commonLib, line 1985
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1986
    } // library marker kkossev.commonLib, line 1987
} // library marker kkossev.commonLib, line 1988

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1990
    logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1991
} // library marker kkossev.commonLib, line 1992

/* // library marker kkossev.commonLib, line 1994
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1995
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1996
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1997
*/ // library marker kkossev.commonLib, line 1998
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1999
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2000
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2001

// Tuya Commands // library marker kkossev.commonLib, line 2003
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2004
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2005
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2006
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2007
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2008

// tuya DP type // library marker kkossev.commonLib, line 2010
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2011
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2012
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2013
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2014
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2015
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2016

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2018
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2019
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2020
        Long offset = 0 // library marker kkossev.commonLib, line 2021
        try { // library marker kkossev.commonLib, line 2022
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2023
        } // library marker kkossev.commonLib, line 2024
        catch (e) { // library marker kkossev.commonLib, line 2025
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2026
        } // library marker kkossev.commonLib, line 2027
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2028
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2029
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2030
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2031
    } // library marker kkossev.commonLib, line 2032
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2033
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2034
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2035
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2036
        if (status != '00') { // library marker kkossev.commonLib, line 2037
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2038
        } // library marker kkossev.commonLib, line 2039
    } // library marker kkossev.commonLib, line 2040
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2041
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2042
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2043
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2044
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2045
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2046
            return // library marker kkossev.commonLib, line 2047
        } // library marker kkossev.commonLib, line 2048
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2049
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2050
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2051
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2052
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2053
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2054
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2055
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2056
        } // library marker kkossev.commonLib, line 2057
    } // library marker kkossev.commonLib, line 2058
    else { // library marker kkossev.commonLib, line 2059
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2060
    } // library marker kkossev.commonLib, line 2061
} // library marker kkossev.commonLib, line 2062

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2064
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2065
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2066
            return // library marker kkossev.commonLib, line 2067
        } // library marker kkossev.commonLib, line 2068
    } // library marker kkossev.commonLib, line 2069
    // check if the method  method exists // library marker kkossev.commonLib, line 2070
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2071
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2072
            return // library marker kkossev.commonLib, line 2073
        } // library marker kkossev.commonLib, line 2074
    } // library marker kkossev.commonLib, line 2075
    switch (dp) { // library marker kkossev.commonLib, line 2076
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2077
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2078
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2079
            } // library marker kkossev.commonLib, line 2080
            else { // library marker kkossev.commonLib, line 2081
                sendSwitchEvent(fncmd as int) // library marker kkossev.commonLib, line 2082
            } // library marker kkossev.commonLib, line 2083
            break // library marker kkossev.commonLib, line 2084
        case 0x02 : // library marker kkossev.commonLib, line 2085
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2086
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2087
            } // library marker kkossev.commonLib, line 2088
            else { // library marker kkossev.commonLib, line 2089
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2090
            } // library marker kkossev.commonLib, line 2091
            break // library marker kkossev.commonLib, line 2092
        case 0x04 : // battery // library marker kkossev.commonLib, line 2093
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2094
            break // library marker kkossev.commonLib, line 2095
        default : // library marker kkossev.commonLib, line 2096
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2097
            break // library marker kkossev.commonLib, line 2098
    } // library marker kkossev.commonLib, line 2099
} // library marker kkossev.commonLib, line 2100

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2102
    int retValue = 0 // library marker kkossev.commonLib, line 2103
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2104
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2105
        int power = 1 // library marker kkossev.commonLib, line 2106
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2107
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2108
            power = power * 256 // library marker kkossev.commonLib, line 2109
        } // library marker kkossev.commonLib, line 2110
    } // library marker kkossev.commonLib, line 2111
    return retValue // library marker kkossev.commonLib, line 2112
} // library marker kkossev.commonLib, line 2113

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2115
    List<String> cmds = [] // library marker kkossev.commonLib, line 2116
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2117
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2118
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2119
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2120
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2121
    return cmds // library marker kkossev.commonLib, line 2122
} // library marker kkossev.commonLib, line 2123

private getPACKET_ID() { // library marker kkossev.commonLib, line 2125
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2126
} // library marker kkossev.commonLib, line 2127

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2129
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2130
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2131
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2132
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2133
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2137
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2138

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2140
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2141
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2142
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2143
} // library marker kkossev.commonLib, line 2144

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2146
    List<String> cmds = [] // library marker kkossev.commonLib, line 2147
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2148
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2149
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2150
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2151
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2152
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2153
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2154
        } // library marker kkossev.commonLib, line 2155
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2156
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2157
    } // library marker kkossev.commonLib, line 2158
    else { // library marker kkossev.commonLib, line 2159
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2160
    } // library marker kkossev.commonLib, line 2161
} // library marker kkossev.commonLib, line 2162

/** // library marker kkossev.commonLib, line 2164
 * initializes the device // library marker kkossev.commonLib, line 2165
 * Invoked from configure() // library marker kkossev.commonLib, line 2166
 * @return zigbee commands // library marker kkossev.commonLib, line 2167
 */ // library marker kkossev.commonLib, line 2168
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2169
    List<String> cmds = [] // library marker kkossev.commonLib, line 2170
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2171

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2173
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2174
        return customInitializeDevice() // library marker kkossev.commonLib, line 2175
    } // library marker kkossev.commonLib, line 2176
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2177
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2178
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2179
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2180
    } // library marker kkossev.commonLib, line 2181
    // // library marker kkossev.commonLib, line 2182
    if (cmds == []) { // library marker kkossev.commonLib, line 2183
        cmds = ['delay 299'] // library marker kkossev.commonLib, line 2184
    } // library marker kkossev.commonLib, line 2185
    return cmds // library marker kkossev.commonLib, line 2186
} // library marker kkossev.commonLib, line 2187

/** // library marker kkossev.commonLib, line 2189
 * configures the device // library marker kkossev.commonLib, line 2190
 * Invoked from configure() // library marker kkossev.commonLib, line 2191
 * @return zigbee commands // library marker kkossev.commonLib, line 2192
 */ // library marker kkossev.commonLib, line 2193
List<String> configureDevice() { // library marker kkossev.commonLib, line 2194
    List<String> cmds = [] // library marker kkossev.commonLib, line 2195
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2196

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2198
        cmds += customConfigureDevice() // library marker kkossev.commonLib, line 2199
    } // library marker kkossev.commonLib, line 2200
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2201
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2202
    if ( cmds == null || cmds == []) { // library marker kkossev.commonLib, line 2203
        cmds = ['delay 277',] // library marker kkossev.commonLib, line 2204
    } // library marker kkossev.commonLib, line 2205
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2206
    return cmds // library marker kkossev.commonLib, line 2207
} // library marker kkossev.commonLib, line 2208

/* // library marker kkossev.commonLib, line 2210
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2211
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2212
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2213
*/ // library marker kkossev.commonLib, line 2214

void refresh() { // library marker kkossev.commonLib, line 2216
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2217
    checkDriverVersion() // library marker kkossev.commonLib, line 2218
    List<String> cmds = [] // library marker kkossev.commonLib, line 2219
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2220

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2222
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2223
        cmds += customRefresh() // library marker kkossev.commonLib, line 2224
    } // library marker kkossev.commonLib, line 2225
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2226
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2227
    else { // library marker kkossev.commonLib, line 2228
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2229
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2230
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2231
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2232
        } // library marker kkossev.commonLib, line 2233
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2234
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2235
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2236
        } // library marker kkossev.commonLib, line 2237
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2238
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2239
        } // library marker kkossev.commonLib, line 2240
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2241
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2242
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2243
        } // library marker kkossev.commonLib, line 2244
    } // library marker kkossev.commonLib, line 2245

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2247
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2248
    } // library marker kkossev.commonLib, line 2249
    else { // library marker kkossev.commonLib, line 2250
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2251
    } // library marker kkossev.commonLib, line 2252
} // library marker kkossev.commonLib, line 2253

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2255
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2256
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2257
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2258

void clearInfoEvent() { // library marker kkossev.commonLib, line 2260
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2261
} // library marker kkossev.commonLib, line 2262

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2264
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2265
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2266
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2267
    } // library marker kkossev.commonLib, line 2268
    else { // library marker kkossev.commonLib, line 2269
        logInfo "${info}" // library marker kkossev.commonLib, line 2270
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2271
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2272
    } // library marker kkossev.commonLib, line 2273
} // library marker kkossev.commonLib, line 2274

void ping() { // library marker kkossev.commonLib, line 2276
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2277
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2278
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2279
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2280
    } // library marker kkossev.commonLib, line 2281
    else { // library marker kkossev.commonLib, line 2282
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2283
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2284
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2285
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2286
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2287
        if (isVirtual()) { // library marker kkossev.commonLib, line 2288
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2289
        } // library marker kkossev.commonLib, line 2290
        else { // library marker kkossev.commonLib, line 2291
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2292
        } // library marker kkossev.commonLib, line 2293
        logDebug 'ping...' // library marker kkossev.commonLib, line 2294
    } // library marker kkossev.commonLib, line 2295
} // library marker kkossev.commonLib, line 2296

def virtualPong() { // library marker kkossev.commonLib, line 2298
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2299
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2300
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2301
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2302
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2303
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2304
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2305
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2306
        sendRttEvent() // library marker kkossev.commonLib, line 2307
    } // library marker kkossev.commonLib, line 2308
    else { // library marker kkossev.commonLib, line 2309
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2310
    } // library marker kkossev.commonLib, line 2311
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2312
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2313
} // library marker kkossev.commonLib, line 2314

/** // library marker kkossev.commonLib, line 2316
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2317
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2318
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2319
 * @return none // library marker kkossev.commonLib, line 2320
 */ // library marker kkossev.commonLib, line 2321
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2322
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2323
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2324
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2325
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2326
    if (value == null) { // library marker kkossev.commonLib, line 2327
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2328
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2329
    } // library marker kkossev.commonLib, line 2330
    else { // library marker kkossev.commonLib, line 2331
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2332
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2333
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2334
    } // library marker kkossev.commonLib, line 2335
} // library marker kkossev.commonLib, line 2336

/** // library marker kkossev.commonLib, line 2338
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2339
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2340
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2341
 */ // library marker kkossev.commonLib, line 2342
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2343
    if (cluster != null) { // library marker kkossev.commonLib, line 2344
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2345
    } // library marker kkossev.commonLib, line 2346
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2347
    return 'NULL' // library marker kkossev.commonLib, line 2348
} // library marker kkossev.commonLib, line 2349

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2351
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2352
} // library marker kkossev.commonLib, line 2353

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2355
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2356
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2357
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2358
} // library marker kkossev.commonLib, line 2359

/** // library marker kkossev.commonLib, line 2361
 * Schedule a device health check // library marker kkossev.commonLib, line 2362
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2363
 */ // library marker kkossev.commonLib, line 2364
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2365
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2366
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2367
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2368
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2369
    } // library marker kkossev.commonLib, line 2370
    else { // library marker kkossev.commonLib, line 2371
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2372
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2373
    } // library marker kkossev.commonLib, line 2374
} // library marker kkossev.commonLib, line 2375

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2377
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2378
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2379
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2380
} // library marker kkossev.commonLib, line 2381

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2383
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2384
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2385
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2386
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2387
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2388
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2389
    } // library marker kkossev.commonLib, line 2390
} // library marker kkossev.commonLib, line 2391

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2393
    checkDriverVersion() // library marker kkossev.commonLib, line 2394
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2395
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2396
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2397
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2398
            logWarn 'not present!' // library marker kkossev.commonLib, line 2399
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2400
        } // library marker kkossev.commonLib, line 2401
    } // library marker kkossev.commonLib, line 2402
    else { // library marker kkossev.commonLib, line 2403
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2404
    } // library marker kkossev.commonLib, line 2405
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2406
} // library marker kkossev.commonLib, line 2407

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2409
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2410
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2411
    if (value == 'online') { // library marker kkossev.commonLib, line 2412
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2413
    } // library marker kkossev.commonLib, line 2414
    else { // library marker kkossev.commonLib, line 2415
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2416
    } // library marker kkossev.commonLib, line 2417
} // library marker kkossev.commonLib, line 2418

/** // library marker kkossev.commonLib, line 2420
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2421
 */ // library marker kkossev.commonLib, line 2422
void autoPoll() { // library marker kkossev.commonLib, line 2423
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2424
    checkDriverVersion() // library marker kkossev.commonLib, line 2425
    List<String> cmds = [] // library marker kkossev.commonLib, line 2426
    //if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2427
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2428
    // TODO !!!!!!!! // library marker kkossev.commonLib, line 2429
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2430
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2431
    } // library marker kkossev.commonLib, line 2432

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2434
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2435
    } // library marker kkossev.commonLib, line 2436
} // library marker kkossev.commonLib, line 2437

/** // library marker kkossev.commonLib, line 2439
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2440
 */ // library marker kkossev.commonLib, line 2441
void updated() { // library marker kkossev.commonLib, line 2442
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2443
    checkDriverVersion() // library marker kkossev.commonLib, line 2444
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2445
    unschedule() // library marker kkossev.commonLib, line 2446

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2448
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2449
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2450
    } // library marker kkossev.commonLib, line 2451
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2452
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2453
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2454
    } // library marker kkossev.commonLib, line 2455

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2457
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2458
        // schedule the periodic timer // library marker kkossev.commonLib, line 2459
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2460
        if (interval > 0) { // library marker kkossev.commonLib, line 2461
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2462
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2463
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2464
        } // library marker kkossev.commonLib, line 2465
    } // library marker kkossev.commonLib, line 2466
    else { // library marker kkossev.commonLib, line 2467
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2468
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2469
    } // library marker kkossev.commonLib, line 2470
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2471
        customUpdated() // library marker kkossev.commonLib, line 2472
    } // library marker kkossev.commonLib, line 2473

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2475
} // library marker kkossev.commonLib, line 2476

/** // library marker kkossev.commonLib, line 2478
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2479
 */ // library marker kkossev.commonLib, line 2480
void logsOff() { // library marker kkossev.commonLib, line 2481
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2482
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2483
} // library marker kkossev.commonLib, line 2484
void traceOff() { // library marker kkossev.commonLib, line 2485
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2486
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2487
} // library marker kkossev.commonLib, line 2488

void configure(String command) { // library marker kkossev.commonLib, line 2490
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2491
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2492
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2493
        return // library marker kkossev.commonLib, line 2494
    } // library marker kkossev.commonLib, line 2495
    // // library marker kkossev.commonLib, line 2496
    String func // library marker kkossev.commonLib, line 2497
    try { // library marker kkossev.commonLib, line 2498
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2499
        "$func"() // library marker kkossev.commonLib, line 2500
    } // library marker kkossev.commonLib, line 2501
    catch (e) { // library marker kkossev.commonLib, line 2502
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2503
        return // library marker kkossev.commonLib, line 2504
    } // library marker kkossev.commonLib, line 2505
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2506
} // library marker kkossev.commonLib, line 2507

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2509
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2510
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2511
} // library marker kkossev.commonLib, line 2512

void loadAllDefaults() { // library marker kkossev.commonLib, line 2514
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2515
    deleteAllSettings() // library marker kkossev.commonLib, line 2516
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2517
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2518
    deleteAllStates() // library marker kkossev.commonLib, line 2519
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2520
    initialize() // library marker kkossev.commonLib, line 2521
    configure()     // calls  also   configureDevice() // library marker kkossev.commonLib, line 2522
    updated() // library marker kkossev.commonLib, line 2523
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2524
} // library marker kkossev.commonLib, line 2525

void configureNow() { // library marker kkossev.commonLib, line 2527
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2528
} // library marker kkossev.commonLib, line 2529

/** // library marker kkossev.commonLib, line 2531
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2532
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2533
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2534
 */ // library marker kkossev.commonLib, line 2535
List<String> configure() { // library marker kkossev.commonLib, line 2536
    List<String> cmds = [] // library marker kkossev.commonLib, line 2537
    logInfo 'configure...' // library marker kkossev.commonLib, line 2538
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2539
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2540
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2541
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2542
    } // library marker kkossev.commonLib, line 2543
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2544
    cmds += configureDevice() // library marker kkossev.commonLib, line 2545
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2546
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2547
    return cmds // library marker kkossev.commonLib, line 2548
} // library marker kkossev.commonLib, line 2549

/** // library marker kkossev.commonLib, line 2551
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2552
 */ // library marker kkossev.commonLib, line 2553
void installed() { // library marker kkossev.commonLib, line 2554
    logInfo 'installed...' // library marker kkossev.commonLib, line 2555
    // populate some default values for attributes // library marker kkossev.commonLib, line 2556
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2557
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2558
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2559
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2560
} // library marker kkossev.commonLib, line 2561

/** // library marker kkossev.commonLib, line 2563
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2564
 */ // library marker kkossev.commonLib, line 2565
void initialize() { // library marker kkossev.commonLib, line 2566
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2567
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2568
    updateTuyaVersion() // library marker kkossev.commonLib, line 2569
    updateAqaraVersion() // library marker kkossev.commonLib, line 2570
} // library marker kkossev.commonLib, line 2571

/* // library marker kkossev.commonLib, line 2573
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2574
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2575
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2576
*/ // library marker kkossev.commonLib, line 2577

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2579
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2580
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2581
} // library marker kkossev.commonLib, line 2582

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2584
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2585
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2586
} // library marker kkossev.commonLib, line 2587

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2589
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2590
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2591
} // library marker kkossev.commonLib, line 2592

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2594
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2595
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2596
    cmd.each { // library marker kkossev.commonLib, line 2597
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2598
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2599
    } // library marker kkossev.commonLib, line 2600
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2601
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2602
} // library marker kkossev.commonLib, line 2603

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2605

String getDeviceInfo() { // library marker kkossev.commonLib, line 2607
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2608
} // library marker kkossev.commonLib, line 2609

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2611
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2612
} // library marker kkossev.commonLib, line 2613

void checkDriverVersion() { // library marker kkossev.commonLib, line 2615
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2616
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2617
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2618
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2619
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2620
        updateTuyaVersion() // library marker kkossev.commonLib, line 2621
        updateAqaraVersion() // library marker kkossev.commonLib, line 2622
    } // library marker kkossev.commonLib, line 2623
    // no driver version change // library marker kkossev.commonLib, line 2624
} // library marker kkossev.commonLib, line 2625

// credits @thebearmay // library marker kkossev.commonLib, line 2627
String getModel() { // library marker kkossev.commonLib, line 2628
    try { // library marker kkossev.commonLib, line 2629
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2630
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2631
    } catch (ignore) { // library marker kkossev.commonLib, line 2632
        try { // library marker kkossev.commonLib, line 2633
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2634
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2635
                return model // library marker kkossev.commonLib, line 2636
            } // library marker kkossev.commonLib, line 2637
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2638
            return '' // library marker kkossev.commonLib, line 2639
        } // library marker kkossev.commonLib, line 2640
    } // library marker kkossev.commonLib, line 2641
} // library marker kkossev.commonLib, line 2642

// credits @thebearmay // library marker kkossev.commonLib, line 2644
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2645
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2646
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2647
    String revision = tokens.last() // library marker kkossev.commonLib, line 2648
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2649
} // library marker kkossev.commonLib, line 2650

/** // library marker kkossev.commonLib, line 2652
 * called from TODO // library marker kkossev.commonLib, line 2653
 */ // library marker kkossev.commonLib, line 2654

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2656
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2657
    unschedule() // library marker kkossev.commonLib, line 2658
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2659
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2660

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2662
} // library marker kkossev.commonLib, line 2663

void resetStatistics() { // library marker kkossev.commonLib, line 2665
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2666
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2667
} // library marker kkossev.commonLib, line 2668

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2670
void resetStats() { // library marker kkossev.commonLib, line 2671
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2672
    state.stats = [:] // library marker kkossev.commonLib, line 2673
    state.states = [:] // library marker kkossev.commonLib, line 2674
    state.lastRx = [:] // library marker kkossev.commonLib, line 2675
    state.lastTx = [:] // library marker kkossev.commonLib, line 2676
    state.health = [:] // library marker kkossev.commonLib, line 2677
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2678
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2679
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2680
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2681
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2682
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2683
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2684
} // library marker kkossev.commonLib, line 2685

/** // library marker kkossev.commonLib, line 2687
 * called from TODO // library marker kkossev.commonLib, line 2688
 */ // library marker kkossev.commonLib, line 2689
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2690
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2691
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2692
        state.clear() // library marker kkossev.commonLib, line 2693
        unschedule() // library marker kkossev.commonLib, line 2694
        resetStats() // library marker kkossev.commonLib, line 2695
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2696
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2697
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2698
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2699
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2700
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2701
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2702
    } // library marker kkossev.commonLib, line 2703

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2705
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2706
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2707
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2708
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2709
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2710

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2712
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2713
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2714
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2715
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2716
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2717
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2718
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2719
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2720
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2721

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2723
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2724
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2725
    } // library marker kkossev.commonLib, line 2726
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2727
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2728
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2729
    } // library marker kkossev.commonLib, line 2730
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2731
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2732
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2733
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2734
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2735

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2737
    if ( mm != null) { // library marker kkossev.commonLib, line 2738
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2739
    } // library marker kkossev.commonLib, line 2740
    else { // library marker kkossev.commonLib, line 2741
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2742
    } // library marker kkossev.commonLib, line 2743
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2744
    if ( ep  != null) { // library marker kkossev.commonLib, line 2745
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2746
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2747
    } // library marker kkossev.commonLib, line 2748
    else { // library marker kkossev.commonLib, line 2749
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2750
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2751
    } // library marker kkossev.commonLib, line 2752
} // library marker kkossev.commonLib, line 2753

/** // library marker kkossev.commonLib, line 2755
 * called from TODO // library marker kkossev.commonLib, line 2756
 */ // library marker kkossev.commonLib, line 2757
void setDestinationEP() { // library marker kkossev.commonLib, line 2758
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2759
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2760
        state.destinationEP = ep // library marker kkossev.commonLib, line 2761
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2762
    } // library marker kkossev.commonLib, line 2763
    else { // library marker kkossev.commonLib, line 2764
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2765
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2766
    } // library marker kkossev.commonLib, line 2767
} // library marker kkossev.commonLib, line 2768

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2770
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2771
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2772
    } // library marker kkossev.commonLib, line 2773
} // library marker kkossev.commonLib, line 2774

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2776
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2777
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2778
    } // library marker kkossev.commonLib, line 2779
} // library marker kkossev.commonLib, line 2780

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2782
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2783
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2784
    } // library marker kkossev.commonLib, line 2785
} // library marker kkossev.commonLib, line 2786

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2788
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2789
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2790
    } // library marker kkossev.commonLib, line 2791
} // library marker kkossev.commonLib, line 2792

// _DEBUG mode only // library marker kkossev.commonLib, line 2794
void getAllProperties() { // library marker kkossev.commonLib, line 2795
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2796
    device.properties.each { it -> // library marker kkossev.commonLib, line 2797
        log.debug it // library marker kkossev.commonLib, line 2798
    } // library marker kkossev.commonLib, line 2799
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2800
    settings.each { it -> // library marker kkossev.commonLib, line 2801
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2802
    } // library marker kkossev.commonLib, line 2803
    log.trace 'Done' // library marker kkossev.commonLib, line 2804
} // library marker kkossev.commonLib, line 2805

// delete all Preferences // library marker kkossev.commonLib, line 2807
void deleteAllSettings() { // library marker kkossev.commonLib, line 2808
    settings.each { it -> // library marker kkossev.commonLib, line 2809
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2810
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2811
    } // library marker kkossev.commonLib, line 2812
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2813
} // library marker kkossev.commonLib, line 2814

// delete all attributes // library marker kkossev.commonLib, line 2816
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2817
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2818
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2819
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2820
    } // library marker kkossev.commonLib, line 2821
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2822
} // library marker kkossev.commonLib, line 2823

// delete all State Variables // library marker kkossev.commonLib, line 2825
void deleteAllStates() { // library marker kkossev.commonLib, line 2826
    state.each { it -> // library marker kkossev.commonLib, line 2827
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2828
    } // library marker kkossev.commonLib, line 2829
    state.clear() // library marker kkossev.commonLib, line 2830
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2831
} // library marker kkossev.commonLib, line 2832

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2834
    unschedule() // library marker kkossev.commonLib, line 2835
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2836
} // library marker kkossev.commonLib, line 2837

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2839
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2840
} // library marker kkossev.commonLib, line 2841

void parseTest(String par) { // library marker kkossev.commonLib, line 2843
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2844
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2845
    parse(par) // library marker kkossev.commonLib, line 2846
} // library marker kkossev.commonLib, line 2847

def testJob() { // library marker kkossev.commonLib, line 2849
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2850
} // library marker kkossev.commonLib, line 2851

/** // library marker kkossev.commonLib, line 2853
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2854
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2855
 */ // library marker kkossev.commonLib, line 2856
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2857
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2858
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2859
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2860
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2861
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2862
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2863
    String cron // library marker kkossev.commonLib, line 2864
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2865
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2866
    } // library marker kkossev.commonLib, line 2867
    else { // library marker kkossev.commonLib, line 2868
        if (minutes < 60) { // library marker kkossev.commonLib, line 2869
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2870
        } // library marker kkossev.commonLib, line 2871
        else { // library marker kkossev.commonLib, line 2872
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2873
        } // library marker kkossev.commonLib, line 2874
    } // library marker kkossev.commonLib, line 2875
    return cron // library marker kkossev.commonLib, line 2876
} // library marker kkossev.commonLib, line 2877

// credits @thebearmay // library marker kkossev.commonLib, line 2879
String formatUptime() { // library marker kkossev.commonLib, line 2880
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2881
} // library marker kkossev.commonLib, line 2882

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2884
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2885
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2886
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2887
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2888
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2889
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2890
} // library marker kkossev.commonLib, line 2891

boolean isTuya() { // library marker kkossev.commonLib, line 2893
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2894
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2895
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2896
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2897
} // library marker kkossev.commonLib, line 2898

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2900
    if (!isTuya()) { // library marker kkossev.commonLib, line 2901
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2902
        return // library marker kkossev.commonLib, line 2903
    } // library marker kkossev.commonLib, line 2904
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2905
    if (application != null) { // library marker kkossev.commonLib, line 2906
        Integer ver // library marker kkossev.commonLib, line 2907
        try { // library marker kkossev.commonLib, line 2908
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2909
        } // library marker kkossev.commonLib, line 2910
        catch (e) { // library marker kkossev.commonLib, line 2911
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2912
            return // library marker kkossev.commonLib, line 2913
        } // library marker kkossev.commonLib, line 2914
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2915
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2916
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2917
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2918
        } // library marker kkossev.commonLib, line 2919
    } // library marker kkossev.commonLib, line 2920
} // library marker kkossev.commonLib, line 2921

boolean isAqara() { // library marker kkossev.commonLib, line 2923
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2924
} // library marker kkossev.commonLib, line 2925

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2927
    if (!isAqara()) { // library marker kkossev.commonLib, line 2928
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2929
        return // library marker kkossev.commonLib, line 2930
    } // library marker kkossev.commonLib, line 2931
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2932
    if (application != null) { // library marker kkossev.commonLib, line 2933
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2934
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2935
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2936
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2937
        } // library marker kkossev.commonLib, line 2938
    } // library marker kkossev.commonLib, line 2939
} // library marker kkossev.commonLib, line 2940

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2942
    try { // library marker kkossev.commonLib, line 2943
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2944
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2945
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2946
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2947
    } catch (e) { // library marker kkossev.commonLib, line 2948
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2949
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2950
    } // library marker kkossev.commonLib, line 2951
} // library marker kkossev.commonLib, line 2952

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2954
    try { // library marker kkossev.commonLib, line 2955
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2956
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2957
        return date.getTime() // library marker kkossev.commonLib, line 2958
    } catch (e) { // library marker kkossev.commonLib, line 2959
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2960
        return now() // library marker kkossev.commonLib, line 2961
    } // library marker kkossev.commonLib, line 2962
} // library marker kkossev.commonLib, line 2963

void test(String par) { // library marker kkossev.commonLib, line 2965
    List<String> cmds = [] // library marker kkossev.commonLib, line 2966
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2967

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2969
    //parse(par) // library marker kkossev.commonLib, line 2970

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2972
} // library marker kkossev.commonLib, line 2973

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
