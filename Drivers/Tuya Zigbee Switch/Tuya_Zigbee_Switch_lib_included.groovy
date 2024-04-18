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
 * ver. 3.0.7  2024-02-24 kkossev  - (dev. branch) commonLib 3.0.7 and groupsLib allignment
 *
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { "3.0.7" }
static String timeStamp() {"2024/04/17 1:29 PM"}

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
    version: '3.0.7', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 36
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 37
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * ver. 3.0.7  2024-04-17 kkossev  - (dev. branch) tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; // library marker kkossev.commonLib, line 39
  * // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 44
  * // library marker kkossev.commonLib, line 45
*/ // library marker kkossev.commonLib, line 46

String commonLibVersion() { '3.0.7' } // library marker kkossev.commonLib, line 48
String commonLibStamp() { '2024/04/17 5:36 PM' } // library marker kkossev.commonLib, line 49

import groovy.transform.Field // library marker kkossev.commonLib, line 51
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 52
import hubitat.device.Protocol // library marker kkossev.commonLib, line 53
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 54
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 55
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 56
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 57
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 58
import java.math.BigDecimal // library marker kkossev.commonLib, line 59

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 61

metadata { // library marker kkossev.commonLib, line 63
        if (_DEBUG) { // library marker kkossev.commonLib, line 64
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 66
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 67
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 69
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 70
            ] // library marker kkossev.commonLib, line 71
        } // library marker kkossev.commonLib, line 72

        // common capabilities for all device types // library marker kkossev.commonLib, line 74
        capability 'Configuration' // library marker kkossev.commonLib, line 75
        capability 'Refresh' // library marker kkossev.commonLib, line 76
        capability 'Health Check' // library marker kkossev.commonLib, line 77

        // common attributes for all device types // library marker kkossev.commonLib, line 79
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 80
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 81
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 82

        // common commands for all device types // library marker kkossev.commonLib, line 84
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 85

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 87
            capability 'Switch' // library marker kkossev.commonLib, line 88
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 89
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 90
            } // library marker kkossev.commonLib, line 91
        } // library marker kkossev.commonLib, line 92

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 94
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 95

    preferences { // library marker kkossev.commonLib, line 97
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 98
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 99
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 100

        if (device) { // library marker kkossev.commonLib, line 102
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 103
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 104
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 105
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 106
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 107
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 108
                } // library marker kkossev.commonLib, line 109
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 110
            } // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
    } // library marker kkossev.commonLib, line 113
} // library marker kkossev.commonLib, line 114

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 116
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 117
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 118
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 119
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 120
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 123
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 124
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 125
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 126

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 128
    defaultValue: 1, // library marker kkossev.commonLib, line 129
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 130
] // library marker kkossev.commonLib, line 131
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 132
    defaultValue: 240, // library marker kkossev.commonLib, line 133
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 134
] // library marker kkossev.commonLib, line 135
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 136
    defaultValue: 0, // library marker kkossev.commonLib, line 137
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 138
] // library marker kkossev.commonLib, line 139

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 141
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 142
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 143
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 144
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 145
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 146
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 147
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 148
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 149
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 150
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 151
] // library marker kkossev.commonLib, line 152

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 154
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 155
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 156
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 157
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 158
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 159
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 160
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 161
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 162

/** // library marker kkossev.commonLib, line 164
 * Parse Zigbee message // library marker kkossev.commonLib, line 165
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 166
 */ // library marker kkossev.commonLib, line 167
void parse(final String description) { // library marker kkossev.commonLib, line 168
    checkDriverVersion() // library marker kkossev.commonLib, line 169
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 170
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 171
    setHealthStatusOnline() // library marker kkossev.commonLib, line 172

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 174
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 175
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 176
            parseIasMessage(description) // library marker kkossev.commonLib, line 177
        } // library marker kkossev.commonLib, line 178
        else { // library marker kkossev.commonLib, line 179
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 180
        } // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 184
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 185
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 186
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 187
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 188
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 189
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 190
        return // library marker kkossev.commonLib, line 191
    } // library marker kkossev.commonLib, line 192
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 193
        return // library marker kkossev.commonLib, line 194
    } // library marker kkossev.commonLib, line 195
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 196

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 198
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 199

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 201
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 202
        return // library marker kkossev.commonLib, line 203
    } // library marker kkossev.commonLib, line 204
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 205
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 206
        return // library marker kkossev.commonLib, line 207
    } // library marker kkossev.commonLib, line 208
    // // library marker kkossev.commonLib, line 209
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 210
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 211
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 212

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 214
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 215
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 216
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 217
            break // library marker kkossev.commonLib, line 218
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 219
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 220
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 221
            break // library marker kkossev.commonLib, line 222
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 223
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 224
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 225
            break // library marker kkossev.commonLib, line 226
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 227
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 228
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 229
            break // library marker kkossev.commonLib, line 230
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 231
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 232
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 233
            break // library marker kkossev.commonLib, line 234
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 235
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 236
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 237
            break // library marker kkossev.commonLib, line 238
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 239
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 240
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 241
            break // library marker kkossev.commonLib, line 242
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 243
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 244
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 245
            break // library marker kkossev.commonLib, line 246
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 247
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 248
            break // library marker kkossev.commonLib, line 249
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 250
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 253
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 254
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 255
            break // library marker kkossev.commonLib, line 256
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 257
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 258
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 259
            break // library marker kkossev.commonLib, line 260
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 261
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 262
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 263
            break // library marker kkossev.commonLib, line 264
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 265
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 266
            break // library marker kkossev.commonLib, line 267
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 268
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 269
            break // library marker kkossev.commonLib, line 270
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 271
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 272
            break // library marker kkossev.commonLib, line 273
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 274
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 275
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 276
            break // library marker kkossev.commonLib, line 277
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 278
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 279
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 280
            break // library marker kkossev.commonLib, line 281
        case 0xE002 : // library marker kkossev.commonLib, line 282
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 283
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 284
            break // library marker kkossev.commonLib, line 285
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 286
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 287
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 288
            break // library marker kkossev.commonLib, line 289
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 290
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 291
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 294
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 295
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 298
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 301
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 302
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        default: // library marker kkossev.commonLib, line 305
            if (settings.logEnable) { // library marker kkossev.commonLib, line 306
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 307
            } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
    } // library marker kkossev.commonLib, line 310
} // library marker kkossev.commonLib, line 311

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 313
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 314
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 315
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 316
    } // library marker kkossev.commonLib, line 317
    return false // library marker kkossev.commonLib, line 318
} // library marker kkossev.commonLib, line 319

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 321
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 322
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 323
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 324
    } // library marker kkossev.commonLib, line 325
    return false // library marker kkossev.commonLib, line 326
} // library marker kkossev.commonLib, line 327

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 329
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 330
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 331
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 332
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 333
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 334
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 335
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 336
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 337
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 338
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 339
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 340
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 341
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 342
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 343
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 344
] // library marker kkossev.commonLib, line 345

/** // library marker kkossev.commonLib, line 347
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 348
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 349
 */ // library marker kkossev.commonLib, line 350
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 351
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 352
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 353
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 354
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 355
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 356
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 357
    switch (clusterId) { // library marker kkossev.commonLib, line 358
        case 0x0005 : // library marker kkossev.commonLib, line 359
            if (state.stats == null) { state.stats = [:] } ; state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 360
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 361
            break // library marker kkossev.commonLib, line 362
        case 0x0006 : // library marker kkossev.commonLib, line 363
            if (state.stats == null) { state.stats = [:] } ; state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 364
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 367
            if (state.stats == null) { state.stats = [:] } ; state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 368
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 369
            break // library marker kkossev.commonLib, line 370
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 371
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 372
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 373
            break // library marker kkossev.commonLib, line 374
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 375
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 376
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 377
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 378
            break // library marker kkossev.commonLib, line 379
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 380
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 381
            break // library marker kkossev.commonLib, line 382
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 383
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 384
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 385
            break // library marker kkossev.commonLib, line 386
        default : // library marker kkossev.commonLib, line 387
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 388
            break // library marker kkossev.commonLib, line 389
    } // library marker kkossev.commonLib, line 390
} // library marker kkossev.commonLib, line 391

/** // library marker kkossev.commonLib, line 393
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 394
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 395
 */ // library marker kkossev.commonLib, line 396
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 397
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 398
    switch (commandId) { // library marker kkossev.commonLib, line 399
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 400
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 401
            break // library marker kkossev.commonLib, line 402
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 403
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 404
            break // library marker kkossev.commonLib, line 405
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 406
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 407
            break // library marker kkossev.commonLib, line 408
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 409
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 410
            break // library marker kkossev.commonLib, line 411
        case 0x0B: // default command response // library marker kkossev.commonLib, line 412
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
        default: // library marker kkossev.commonLib, line 415
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 416
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 417
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 418
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 419
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 420
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 421
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 422
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 423
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 424
            } // library marker kkossev.commonLib, line 425
            break // library marker kkossev.commonLib, line 426
    } // library marker kkossev.commonLib, line 427
} // library marker kkossev.commonLib, line 428

/** // library marker kkossev.commonLib, line 430
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 431
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 432
 */ // library marker kkossev.commonLib, line 433
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 434
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 435
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 436
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 437
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 438
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 439
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 440
    } // library marker kkossev.commonLib, line 441
    else { // library marker kkossev.commonLib, line 442
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 443
    } // library marker kkossev.commonLib, line 444
} // library marker kkossev.commonLib, line 445

/** // library marker kkossev.commonLib, line 447
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 448
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 449
 */ // library marker kkossev.commonLib, line 450
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 451
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 452
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 453
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 454
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 455
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 456
    } // library marker kkossev.commonLib, line 457
    else { // library marker kkossev.commonLib, line 458
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 459
    } // library marker kkossev.commonLib, line 460
} // library marker kkossev.commonLib, line 461

/** // library marker kkossev.commonLib, line 463
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 464
 */ // library marker kkossev.commonLib, line 465
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 466
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 467
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 468
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 469
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 470
        state.reportingEnabled = true // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 473
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 474
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 475
    } else { // library marker kkossev.commonLib, line 476
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 477
    } // library marker kkossev.commonLib, line 478
} // library marker kkossev.commonLib, line 479

/** // library marker kkossev.commonLib, line 481
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 482
 */ // library marker kkossev.commonLib, line 483
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 484
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 485
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 486
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 487
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 488
    if (status == 0) { // library marker kkossev.commonLib, line 489
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 490
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 491
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 492
        int delta = 0 // library marker kkossev.commonLib, line 493
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 494
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 495
        } // library marker kkossev.commonLib, line 496
        else { // library marker kkossev.commonLib, line 497
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 498
        } // library marker kkossev.commonLib, line 499
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 500
    } // library marker kkossev.commonLib, line 501
    else { // library marker kkossev.commonLib, line 502
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 503
    } // library marker kkossev.commonLib, line 504
} // library marker kkossev.commonLib, line 505

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 507
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 508
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 509
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 510
        return false // library marker kkossev.commonLib, line 511
    } // library marker kkossev.commonLib, line 512
    // execute the customHandler function // library marker kkossev.commonLib, line 513
    boolean result = false // library marker kkossev.commonLib, line 514
    try { // library marker kkossev.commonLib, line 515
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 516
    } // library marker kkossev.commonLib, line 517
    catch (e) { // library marker kkossev.commonLib, line 518
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 519
        return false // library marker kkossev.commonLib, line 520
    } // library marker kkossev.commonLib, line 521
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 522
    return result // library marker kkossev.commonLib, line 523
} // library marker kkossev.commonLib, line 524

/** // library marker kkossev.commonLib, line 526
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 527
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 528
 */ // library marker kkossev.commonLib, line 529
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 530
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 531
    final String commandId = data[0] // library marker kkossev.commonLib, line 532
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 533
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 534
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 535
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 536
    } else { // library marker kkossev.commonLib, line 537
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 538
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 539
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 540
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 541
        } // library marker kkossev.commonLib, line 542
    } // library marker kkossev.commonLib, line 543
} // library marker kkossev.commonLib, line 544

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 546
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 547
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 548
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 549

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 551
    0x00: 'Success', // library marker kkossev.commonLib, line 552
    0x01: 'Failure', // library marker kkossev.commonLib, line 553
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 554
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 555
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 556
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 557
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 558
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 559
    0x88: 'Read Only', // library marker kkossev.commonLib, line 560
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 561
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 562
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 563
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 564
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 565
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 566
    0x94: 'Time out', // library marker kkossev.commonLib, line 567
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 568
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 569
] // library marker kkossev.commonLib, line 570

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 572
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 573
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 574
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 575
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 576
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 577
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 578
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 579
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 580
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 581
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 582
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 583
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 584
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 585
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 586
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 587
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 588
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 589
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 590
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 591
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 592
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 593
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 594
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 595
] // library marker kkossev.commonLib, line 596

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 598
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 599
} // library marker kkossev.commonLib, line 600

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 602
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 603
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 604
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 605
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 606
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 607
    return avg // library marker kkossev.commonLib, line 608
} // library marker kkossev.commonLib, line 609

/* // library marker kkossev.commonLib, line 611
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 612
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 613
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 614
*/ // library marker kkossev.commonLib, line 615
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 616

/** // library marker kkossev.commonLib, line 618
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 619
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 620
 */ // library marker kkossev.commonLib, line 621
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 622
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 623
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 624
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 625
        case 0x0000: // library marker kkossev.commonLib, line 626
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 627
            break // library marker kkossev.commonLib, line 628
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 629
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 630
            if (isPing) { // library marker kkossev.commonLib, line 631
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 632
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 633
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 634
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 635
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 636
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 637
                    sendRttEvent() // library marker kkossev.commonLib, line 638
                } // library marker kkossev.commonLib, line 639
                else { // library marker kkossev.commonLib, line 640
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 641
                } // library marker kkossev.commonLib, line 642
                state.states['isPing'] = false // library marker kkossev.commonLib, line 643
            } // library marker kkossev.commonLib, line 644
            else { // library marker kkossev.commonLib, line 645
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 646
            } // library marker kkossev.commonLib, line 647
            break // library marker kkossev.commonLib, line 648
        case 0x0004: // library marker kkossev.commonLib, line 649
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 650
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 651
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 652
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 653
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 654
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 655
            } // library marker kkossev.commonLib, line 656
            break // library marker kkossev.commonLib, line 657
        case 0x0005: // library marker kkossev.commonLib, line 658
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 659
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 660
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 661
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 662
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 663
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 664
            } // library marker kkossev.commonLib, line 665
            break // library marker kkossev.commonLib, line 666
        case 0x0007: // library marker kkossev.commonLib, line 667
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 668
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 669
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 670
            break // library marker kkossev.commonLib, line 671
        case 0xFFDF: // library marker kkossev.commonLib, line 672
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 673
            break // library marker kkossev.commonLib, line 674
        case 0xFFE2: // library marker kkossev.commonLib, line 675
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 676
            break // library marker kkossev.commonLib, line 677
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 678
            logDebug "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 679
            break // library marker kkossev.commonLib, line 680
        case 0xFFFE: // library marker kkossev.commonLib, line 681
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 682
            break // library marker kkossev.commonLib, line 683
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 684
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 685
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 686
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 687
            break // library marker kkossev.commonLib, line 688
        default: // library marker kkossev.commonLib, line 689
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 690
            break // library marker kkossev.commonLib, line 691
    } // library marker kkossev.commonLib, line 692
} // library marker kkossev.commonLib, line 693

// power cluster            0x0001 // library marker kkossev.commonLib, line 695
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 696
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 697
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 698
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 699
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 700
    } // library marker kkossev.commonLib, line 701
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 702
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 703
    } // library marker kkossev.commonLib, line 704
    else { // library marker kkossev.commonLib, line 705
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 706
    } // library marker kkossev.commonLib, line 707
} // library marker kkossev.commonLib, line 708

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 710
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 711

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 713
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 714
} // library marker kkossev.commonLib, line 715

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 717
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 718
} // library marker kkossev.commonLib, line 719

/* // library marker kkossev.commonLib, line 721
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 722
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 723
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 724
*/ // library marker kkossev.commonLib, line 725

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 727
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 728
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 729
    } // library marker kkossev.commonLib, line 730
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 731
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 732
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 733
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 734
    } // library marker kkossev.commonLib, line 735
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 736
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 737
    } // library marker kkossev.commonLib, line 738
    else { // library marker kkossev.commonLib, line 739
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 740
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 741
    } // library marker kkossev.commonLib, line 742
} // library marker kkossev.commonLib, line 743

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 745
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 746
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 747

void toggle() { // library marker kkossev.commonLib, line 749
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 750
    String state = '' // library marker kkossev.commonLib, line 751
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 752
        state = 'on' // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    else { // library marker kkossev.commonLib, line 755
        state = 'off' // library marker kkossev.commonLib, line 756
    } // library marker kkossev.commonLib, line 757
    descriptionText += state // library marker kkossev.commonLib, line 758
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 759
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 760
} // library marker kkossev.commonLib, line 761

void off() { // library marker kkossev.commonLib, line 763
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 764
        customOff() // library marker kkossev.commonLib, line 765
        return // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 768
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 769
        return // library marker kkossev.commonLib, line 770
    } // library marker kkossev.commonLib, line 771
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 772
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 773
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 774
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 775
        if (currentState == 'off') { // library marker kkossev.commonLib, line 776
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 777
        } // library marker kkossev.commonLib, line 778
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 779
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 780
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 781
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 782
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    /* // library marker kkossev.commonLib, line 785
    else { // library marker kkossev.commonLib, line 786
        if (currentState != 'off') { // library marker kkossev.commonLib, line 787
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 788
        } // library marker kkossev.commonLib, line 789
        else { // library marker kkossev.commonLib, line 790
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 791
            return // library marker kkossev.commonLib, line 792
        } // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    */ // library marker kkossev.commonLib, line 795

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 797
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 798
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 799
} // library marker kkossev.commonLib, line 800

void on() { // library marker kkossev.commonLib, line 802
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 803
        customOn() // library marker kkossev.commonLib, line 804
        return // library marker kkossev.commonLib, line 805
    } // library marker kkossev.commonLib, line 806
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 807
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 808
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 809
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 810
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 811
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 812
        } // library marker kkossev.commonLib, line 813
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 814
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 815
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 816
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 817
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
    /* // library marker kkossev.commonLib, line 820
    else { // library marker kkossev.commonLib, line 821
        if (currentState != 'on') { // library marker kkossev.commonLib, line 822
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 823
        } // library marker kkossev.commonLib, line 824
        else { // library marker kkossev.commonLib, line 825
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 826
            return // library marker kkossev.commonLib, line 827
        } // library marker kkossev.commonLib, line 828
    } // library marker kkossev.commonLib, line 829
    */ // library marker kkossev.commonLib, line 830
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 831
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 832
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 836
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 837
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 838
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 839
    } // library marker kkossev.commonLib, line 840
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 841
    Map map = [:] // library marker kkossev.commonLib, line 842
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 843
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 844
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 845
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 846
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 847
        return // library marker kkossev.commonLib, line 848
    } // library marker kkossev.commonLib, line 849
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 850
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 851
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 852
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 853
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 854
        state.states['debounce'] = true // library marker kkossev.commonLib, line 855
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 856
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 857
    } else { // library marker kkossev.commonLib, line 858
        state.states['debounce'] = true // library marker kkossev.commonLib, line 859
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 860
    } // library marker kkossev.commonLib, line 861
    map.name = 'switch' // library marker kkossev.commonLib, line 862
    map.value = value // library marker kkossev.commonLib, line 863
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 864
    if (isRefresh) { // library marker kkossev.commonLib, line 865
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 866
        map.isStateChange = true // library marker kkossev.commonLib, line 867
    } else { // library marker kkossev.commonLib, line 868
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 869
    } // library marker kkossev.commonLib, line 870
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 871
    sendEvent(map) // library marker kkossev.commonLib, line 872
    clearIsDigital() // library marker kkossev.commonLib, line 873
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 874
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 875
    } // library marker kkossev.commonLib, line 876
} // library marker kkossev.commonLib, line 877

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 879
    '0': 'switch off', // library marker kkossev.commonLib, line 880
    '1': 'switch on', // library marker kkossev.commonLib, line 881
    '2': 'switch last state' // library marker kkossev.commonLib, line 882
] // library marker kkossev.commonLib, line 883

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 885
    '0': 'toggle', // library marker kkossev.commonLib, line 886
    '1': 'state', // library marker kkossev.commonLib, line 887
    '2': 'momentary' // library marker kkossev.commonLib, line 888
] // library marker kkossev.commonLib, line 889

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 891
    Map descMap = [:] // library marker kkossev.commonLib, line 892
    try { // library marker kkossev.commonLib, line 893
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 894
    } // library marker kkossev.commonLib, line 895
    catch (e1) { // library marker kkossev.commonLib, line 896
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 897
        // try alternative custom parsing // library marker kkossev.commonLib, line 898
        descMap = [:] // library marker kkossev.commonLib, line 899
        try { // library marker kkossev.commonLib, line 900
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 901
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 902
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 903
            } // library marker kkossev.commonLib, line 904
        } // library marker kkossev.commonLib, line 905
        catch (e2) { // library marker kkossev.commonLib, line 906
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 907
            return [:] // library marker kkossev.commonLib, line 908
        } // library marker kkossev.commonLib, line 909
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 910
    } // library marker kkossev.commonLib, line 911
    return descMap // library marker kkossev.commonLib, line 912
} // library marker kkossev.commonLib, line 913

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 915
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 916
        return false // library marker kkossev.commonLib, line 917
    } // library marker kkossev.commonLib, line 918
    // try to parse ... // library marker kkossev.commonLib, line 919
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 920
    Map descMap = [:] // library marker kkossev.commonLib, line 921
    try { // library marker kkossev.commonLib, line 922
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 923
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 924
    } // library marker kkossev.commonLib, line 925
    catch (e) { // library marker kkossev.commonLib, line 926
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 927
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 928
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 929
        return true // library marker kkossev.commonLib, line 930
    } // library marker kkossev.commonLib, line 931

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 933
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 936
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 937
    } // library marker kkossev.commonLib, line 938
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 939
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 940
    } // library marker kkossev.commonLib, line 941
    else { // library marker kkossev.commonLib, line 942
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 943
        return false // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
    return true    // processed // library marker kkossev.commonLib, line 946
} // library marker kkossev.commonLib, line 947

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 949
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 950
  /* // library marker kkossev.commonLib, line 951
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 952
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 953
        return true // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
*/ // library marker kkossev.commonLib, line 956
    Map descMap = [:] // library marker kkossev.commonLib, line 957
    try { // library marker kkossev.commonLib, line 958
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 959
    } // library marker kkossev.commonLib, line 960
    catch (e1) { // library marker kkossev.commonLib, line 961
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 962
        // try alternative custom parsing // library marker kkossev.commonLib, line 963
        descMap = [:] // library marker kkossev.commonLib, line 964
        try { // library marker kkossev.commonLib, line 965
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 966
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 967
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 968
            } // library marker kkossev.commonLib, line 969
        } // library marker kkossev.commonLib, line 970
        catch (e2) { // library marker kkossev.commonLib, line 971
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 972
            return true // library marker kkossev.commonLib, line 973
        } // library marker kkossev.commonLib, line 974
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 975
    } // library marker kkossev.commonLib, line 976
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 977
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 978
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 979
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 980
        return false // library marker kkossev.commonLib, line 981
    } // library marker kkossev.commonLib, line 982
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 983
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 984
    // attribute report received // library marker kkossev.commonLib, line 985
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 986
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 987
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 988
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 989
    } // library marker kkossev.commonLib, line 990
    attrData.each { // library marker kkossev.commonLib, line 991
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 992
        //def map = [:] // library marker kkossev.commonLib, line 993
        if (it.status == '86') { // library marker kkossev.commonLib, line 994
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 995
        // TODO - skip parsing? // library marker kkossev.commonLib, line 996
        } // library marker kkossev.commonLib, line 997
        switch (it.cluster) { // library marker kkossev.commonLib, line 998
            case '0000' : // library marker kkossev.commonLib, line 999
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1000
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1001
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1002
                } // library marker kkossev.commonLib, line 1003
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1004
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1005
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1006
                } // library marker kkossev.commonLib, line 1007
                else { // library marker kkossev.commonLib, line 1008
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1009
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1010
                } // library marker kkossev.commonLib, line 1011
                break // library marker kkossev.commonLib, line 1012
            default : // library marker kkossev.commonLib, line 1013
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1014
                break // library marker kkossev.commonLib, line 1015
        } // switch // library marker kkossev.commonLib, line 1016
    } // for each attribute // library marker kkossev.commonLib, line 1017
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1021

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1023
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1024
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1025
    def mode // library marker kkossev.commonLib, line 1026
    String attrName // library marker kkossev.commonLib, line 1027
    if (it.value == null) { // library marker kkossev.commonLib, line 1028
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1029
        return // library marker kkossev.commonLib, line 1030
    } // library marker kkossev.commonLib, line 1031
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1032
    switch (it.attrId) { // library marker kkossev.commonLib, line 1033
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1034
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1035
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1036
            break // library marker kkossev.commonLib, line 1037
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1038
            attrName = 'On Time' // library marker kkossev.commonLib, line 1039
            mode = value // library marker kkossev.commonLib, line 1040
            break // library marker kkossev.commonLib, line 1041
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1042
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1043
            mode = value // library marker kkossev.commonLib, line 1044
            break // library marker kkossev.commonLib, line 1045
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1046
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1047
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1048
            break // library marker kkossev.commonLib, line 1049
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1050
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1051
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1052
            break // library marker kkossev.commonLib, line 1053
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1054
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1055
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1056
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1057
            } // library marker kkossev.commonLib, line 1058
            else { // library marker kkossev.commonLib, line 1059
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1060
            } // library marker kkossev.commonLib, line 1061
            break // library marker kkossev.commonLib, line 1062
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1063
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1064
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1065
            break // library marker kkossev.commonLib, line 1066
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1067
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1068
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1069
            break // library marker kkossev.commonLib, line 1070
        default : // library marker kkossev.commonLib, line 1071
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1072
            return // library marker kkossev.commonLib, line 1073
    } // library marker kkossev.commonLib, line 1074
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1075
} // library marker kkossev.commonLib, line 1076

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1078
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1079
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1082
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1083
    } // library marker kkossev.commonLib, line 1084
    else { // library marker kkossev.commonLib, line 1085
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
} // library marker kkossev.commonLib, line 1088

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1090
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1091
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1092
} // library marker kkossev.commonLib, line 1093

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1095
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1096
} // library marker kkossev.commonLib, line 1097

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1099
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1100
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1103
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1104
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1105
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1106
    } // library marker kkossev.commonLib, line 1107
    else { // library marker kkossev.commonLib, line 1108
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
} // library marker kkossev.commonLib, line 1111

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1113
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1117
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1118
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1119
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1120
    } // library marker kkossev.commonLib, line 1121
    else { // library marker kkossev.commonLib, line 1122
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1123
    } // library marker kkossev.commonLib, line 1124
} // library marker kkossev.commonLib, line 1125

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1127
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1128
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1129
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
    else { // library marker kkossev.commonLib, line 1132
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
} // library marker kkossev.commonLib, line 1135

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1137
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1138
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1139
} // library marker kkossev.commonLib, line 1140

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1142
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1143
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1144
} // library marker kkossev.commonLib, line 1145

// pm2.5 // library marker kkossev.commonLib, line 1147
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1148
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1149
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1150
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1151
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1152
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1153
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
    else { // library marker kkossev.commonLib, line 1156
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1157
    } // library marker kkossev.commonLib, line 1158
} // library marker kkossev.commonLib, line 1159

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1161
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1162
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1163
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1166
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1167
    } // library marker kkossev.commonLib, line 1168
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1169
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1170
    } // library marker kkossev.commonLib, line 1171
    else { // library marker kkossev.commonLib, line 1172
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1173
    } // library marker kkossev.commonLib, line 1174
} // library marker kkossev.commonLib, line 1175

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1177
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1178
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1179
} // library marker kkossev.commonLib, line 1180

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1182
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1183
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1184
} // library marker kkossev.commonLib, line 1185

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1187
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1188
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1192
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1193
} // library marker kkossev.commonLib, line 1194

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1196
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1197
} // library marker kkossev.commonLib, line 1198

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1200
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1201
} // library marker kkossev.commonLib, line 1202

/* // library marker kkossev.commonLib, line 1204
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1205
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1206
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1207
*/ // library marker kkossev.commonLib, line 1208
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1209
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1210
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1211

// Tuya Commands // library marker kkossev.commonLib, line 1213
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1214
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1215
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1216
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1217
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1218

// tuya DP type // library marker kkossev.commonLib, line 1220
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1221
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1222
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1223
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1224
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1225
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1226

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1228
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1229
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1230
        Long offset = 0 // library marker kkossev.commonLib, line 1231
        try { // library marker kkossev.commonLib, line 1232
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1233
        } // library marker kkossev.commonLib, line 1234
        catch (e) { // library marker kkossev.commonLib, line 1235
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1236
        } // library marker kkossev.commonLib, line 1237
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1238
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1239
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1240
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1243
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1244
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1245
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1246
        if (status != '00') { // library marker kkossev.commonLib, line 1247
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1248
        } // library marker kkossev.commonLib, line 1249
    } // library marker kkossev.commonLib, line 1250
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1251
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1252
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1253
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1254
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1255
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1256
            return // library marker kkossev.commonLib, line 1257
        } // library marker kkossev.commonLib, line 1258
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1259
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1260
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1261
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1262
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1263
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1264
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1265
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1266
        } // library marker kkossev.commonLib, line 1267
    } // library marker kkossev.commonLib, line 1268
    else { // library marker kkossev.commonLib, line 1269
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271
} // library marker kkossev.commonLib, line 1272

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1274
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1275
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1276
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1277
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1278
            return // library marker kkossev.commonLib, line 1279
        } // library marker kkossev.commonLib, line 1280
    } // library marker kkossev.commonLib, line 1281
    // check if the method  method exists // library marker kkossev.commonLib, line 1282
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1283
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1284
            return // library marker kkossev.commonLib, line 1285
        } // library marker kkossev.commonLib, line 1286
    } // library marker kkossev.commonLib, line 1287
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1288
} // library marker kkossev.commonLib, line 1289

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1291
    int retValue = 0 // library marker kkossev.commonLib, line 1292
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1293
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1294
        int power = 1 // library marker kkossev.commonLib, line 1295
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1296
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1297
            power = power * 256 // library marker kkossev.commonLib, line 1298
        } // library marker kkossev.commonLib, line 1299
    } // library marker kkossev.commonLib, line 1300
    return retValue // library marker kkossev.commonLib, line 1301
} // library marker kkossev.commonLib, line 1302

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1304
    List<String> cmds = [] // library marker kkossev.commonLib, line 1305
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1306
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1307
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1308
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1309
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1310
    return cmds // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

private getPACKET_ID() { // library marker kkossev.commonLib, line 1314
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1318
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1319
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1320
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1321
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1322
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1323
} // library marker kkossev.commonLib, line 1324

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1326
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1327

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1329
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1330
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1331
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1332
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1333
} // library marker kkossev.commonLib, line 1334

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1336
    List<String> cmds = [] // library marker kkossev.commonLib, line 1337
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1338
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1339
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1340
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1341
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1342
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1343
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1344
        } // library marker kkossev.commonLib, line 1345
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1346
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1347
    } // library marker kkossev.commonLib, line 1348
    else { // library marker kkossev.commonLib, line 1349
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351
} // library marker kkossev.commonLib, line 1352

/** // library marker kkossev.commonLib, line 1354
 * initializes the device // library marker kkossev.commonLib, line 1355
 * Invoked from configure() // library marker kkossev.commonLib, line 1356
 * @return zigbee commands // library marker kkossev.commonLib, line 1357
 */ // library marker kkossev.commonLib, line 1358
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1359
    List<String> cmds = [] // library marker kkossev.commonLib, line 1360
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1361
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1362
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1363
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
    return cmds // library marker kkossev.commonLib, line 1366
} // library marker kkossev.commonLib, line 1367

/** // library marker kkossev.commonLib, line 1369
 * configures the device // library marker kkossev.commonLib, line 1370
 * Invoked from configure() // library marker kkossev.commonLib, line 1371
 * @return zigbee commands // library marker kkossev.commonLib, line 1372
 */ // library marker kkossev.commonLib, line 1373
List<String> configureDevice() { // library marker kkossev.commonLib, line 1374
    List<String> cmds = [] // library marker kkossev.commonLib, line 1375
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1376

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1378
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1379
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1380
    } // library marker kkossev.commonLib, line 1381
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1382
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1383
    return cmds // library marker kkossev.commonLib, line 1384
} // library marker kkossev.commonLib, line 1385

/* // library marker kkossev.commonLib, line 1387
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1388
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1389
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1390
*/ // library marker kkossev.commonLib, line 1391

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1393
    List<String> cmds = [] // library marker kkossev.commonLib, line 1394
    if (customHandlersList != null && customHandlersList != []) { // library marker kkossev.commonLib, line 1395
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1396
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1397
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1398
                if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1399
            } // library marker kkossev.commonLib, line 1400
        } // library marker kkossev.commonLib, line 1401
    } // library marker kkossev.commonLib, line 1402
    return cmds // library marker kkossev.commonLib, line 1403
} // library marker kkossev.commonLib, line 1404

void refresh() { // library marker kkossev.commonLib, line 1406
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1407
    checkDriverVersion() // library marker kkossev.commonLib, line 1408
    List<String> cmds = [] // library marker kkossev.commonLib, line 1409
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1410

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1412
    if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1413

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1415
    else { // library marker kkossev.commonLib, line 1416
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1417
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1418
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1419
        } // library marker kkossev.commonLib, line 1420
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1421
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1422
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1423
        } // library marker kkossev.commonLib, line 1424
    } // library marker kkossev.commonLib, line 1425

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1427
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1428
    } // library marker kkossev.commonLib, line 1429
    else { // library marker kkossev.commonLib, line 1430
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1431
    } // library marker kkossev.commonLib, line 1432
} // library marker kkossev.commonLib, line 1433

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1435
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1436

void clearInfoEvent() { // library marker kkossev.commonLib, line 1438
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1439
} // library marker kkossev.commonLib, line 1440

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1442
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1443
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1444
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    else { // library marker kkossev.commonLib, line 1447
        logInfo "${info}" // library marker kkossev.commonLib, line 1448
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1449
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1450
    } // library marker kkossev.commonLib, line 1451
} // library marker kkossev.commonLib, line 1452

void ping() { // library marker kkossev.commonLib, line 1454
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1455
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1456
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1457
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1458
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1459
    if (isVirtual()) { // library marker kkossev.commonLib, line 1460
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1461
    } // library marker kkossev.commonLib, line 1462
    else { // library marker kkossev.commonLib, line 1463
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1464
    } // library marker kkossev.commonLib, line 1465
    logDebug 'ping...' // library marker kkossev.commonLib, line 1466
} // library marker kkossev.commonLib, line 1467

def virtualPong() { // library marker kkossev.commonLib, line 1469
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1470
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1471
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1472
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1473
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1474
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1475
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1476
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1477
        sendRttEvent() // library marker kkossev.commonLib, line 1478
    } // library marker kkossev.commonLib, line 1479
    else { // library marker kkossev.commonLib, line 1480
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1481
    } // library marker kkossev.commonLib, line 1482
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1483
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

/** // library marker kkossev.commonLib, line 1487
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1488
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1489
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1490
 * @return none // library marker kkossev.commonLib, line 1491
 */ // library marker kkossev.commonLib, line 1492
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1493
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1494
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1495
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1496
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1497
    if (value == null) { // library marker kkossev.commonLib, line 1498
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1499
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
    else { // library marker kkossev.commonLib, line 1502
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1503
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1504
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1505
    } // library marker kkossev.commonLib, line 1506
} // library marker kkossev.commonLib, line 1507

/** // library marker kkossev.commonLib, line 1509
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1510
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1511
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1512
 */ // library marker kkossev.commonLib, line 1513
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1514
    if (cluster != null) { // library marker kkossev.commonLib, line 1515
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1516
    } // library marker kkossev.commonLib, line 1517
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1518
    return 'NULL' // library marker kkossev.commonLib, line 1519
} // library marker kkossev.commonLib, line 1520

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1522
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1523
} // library marker kkossev.commonLib, line 1524

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1526
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1527
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1528
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

/** // library marker kkossev.commonLib, line 1532
 * Schedule a device health check // library marker kkossev.commonLib, line 1533
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1534
 */ // library marker kkossev.commonLib, line 1535
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1536
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1537
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1538
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1539
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1540
    } // library marker kkossev.commonLib, line 1541
    else { // library marker kkossev.commonLib, line 1542
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1543
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1544
    } // library marker kkossev.commonLib, line 1545
} // library marker kkossev.commonLib, line 1546

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1548
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1549
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1550
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1554
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 1555
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1556
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1557
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1558
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1559
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1560
    } // library marker kkossev.commonLib, line 1561
} // library marker kkossev.commonLib, line 1562

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1564
    checkDriverVersion() // library marker kkossev.commonLib, line 1565
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1566
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1567
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1568
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1569
            logWarn 'not present!' // library marker kkossev.commonLib, line 1570
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1571
        } // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    else { // library marker kkossev.commonLib, line 1574
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1575
    } // library marker kkossev.commonLib, line 1576
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1577
} // library marker kkossev.commonLib, line 1578

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1580
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1581
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1582
    if (value == 'online') { // library marker kkossev.commonLib, line 1583
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
    else { // library marker kkossev.commonLib, line 1586
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1587
    } // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

/** // library marker kkossev.commonLib, line 1591
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1592
 */ // library marker kkossev.commonLib, line 1593
void autoPoll() { // library marker kkossev.commonLib, line 1594
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1595
    checkDriverVersion() // library marker kkossev.commonLib, line 1596
    List<String> cmds = [] // library marker kkossev.commonLib, line 1597
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1598
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1599
    } // library marker kkossev.commonLib, line 1600

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1602
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1603
    } // library marker kkossev.commonLib, line 1604
} // library marker kkossev.commonLib, line 1605

/** // library marker kkossev.commonLib, line 1607
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1608
 */ // library marker kkossev.commonLib, line 1609
void updated() { // library marker kkossev.commonLib, line 1610
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1611
    checkDriverVersion() // library marker kkossev.commonLib, line 1612
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1613
    unschedule() // library marker kkossev.commonLib, line 1614

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1616
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1617
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1618
    } // library marker kkossev.commonLib, line 1619
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1620
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1621
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1622
    } // library marker kkossev.commonLib, line 1623

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1625
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1626
        // schedule the periodic timer // library marker kkossev.commonLib, line 1627
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1628
        if (interval > 0) { // library marker kkossev.commonLib, line 1629
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1630
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1631
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1632
        } // library marker kkossev.commonLib, line 1633
    } // library marker kkossev.commonLib, line 1634
    else { // library marker kkossev.commonLib, line 1635
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1636
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1637
    } // library marker kkossev.commonLib, line 1638
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1639
        customUpdated() // library marker kkossev.commonLib, line 1640
    } // library marker kkossev.commonLib, line 1641

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1643
} // library marker kkossev.commonLib, line 1644

/** // library marker kkossev.commonLib, line 1646
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1647
 */ // library marker kkossev.commonLib, line 1648
void logsOff() { // library marker kkossev.commonLib, line 1649
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1650
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1651
} // library marker kkossev.commonLib, line 1652
void traceOff() { // library marker kkossev.commonLib, line 1653
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1654
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1655
} // library marker kkossev.commonLib, line 1656

void configure(String command) { // library marker kkossev.commonLib, line 1658
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1659
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1660
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1661
        return // library marker kkossev.commonLib, line 1662
    } // library marker kkossev.commonLib, line 1663
    // // library marker kkossev.commonLib, line 1664
    String func // library marker kkossev.commonLib, line 1665
    try { // library marker kkossev.commonLib, line 1666
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1667
        "$func"() // library marker kkossev.commonLib, line 1668
    } // library marker kkossev.commonLib, line 1669
    catch (e) { // library marker kkossev.commonLib, line 1670
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1671
        return // library marker kkossev.commonLib, line 1672
    } // library marker kkossev.commonLib, line 1673
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1674
} // library marker kkossev.commonLib, line 1675

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1677
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1678
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1679
} // library marker kkossev.commonLib, line 1680

void loadAllDefaults() { // library marker kkossev.commonLib, line 1682
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1683
    deleteAllSettings() // library marker kkossev.commonLib, line 1684
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1685
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1686
    deleteAllStates() // library marker kkossev.commonLib, line 1687
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1688
    initialize() // library marker kkossev.commonLib, line 1689
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1690
    updated() // library marker kkossev.commonLib, line 1691
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1692
} // library marker kkossev.commonLib, line 1693

void configureNow() { // library marker kkossev.commonLib, line 1695
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1696
} // library marker kkossev.commonLib, line 1697

/** // library marker kkossev.commonLib, line 1699
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1700
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1701
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1702
 */ // library marker kkossev.commonLib, line 1703
List<String> configure() { // library marker kkossev.commonLib, line 1704
    List<String> cmds = [] // library marker kkossev.commonLib, line 1705
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1706
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1707
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1708
    if (isTuya()) { // library marker kkossev.commonLib, line 1709
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1710
    } // library marker kkossev.commonLib, line 1711
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1712
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1713
    } // library marker kkossev.commonLib, line 1714
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1715
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1716
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1717
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1718
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1719
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1720
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1721
    //return cmds // library marker kkossev.commonLib, line 1722
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1723
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1724
    } // library marker kkossev.commonLib, line 1725
    else { // library marker kkossev.commonLib, line 1726
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1727
    } // library marker kkossev.commonLib, line 1728
} // library marker kkossev.commonLib, line 1729

/** // library marker kkossev.commonLib, line 1731
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1732
 */ // library marker kkossev.commonLib, line 1733
void installed() { // library marker kkossev.commonLib, line 1734
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1735
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1736
    // populate some default values for attributes // library marker kkossev.commonLib, line 1737
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1738
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1739
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1740
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1741
} // library marker kkossev.commonLib, line 1742

/** // library marker kkossev.commonLib, line 1744
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1745
 */ // library marker kkossev.commonLib, line 1746
void initialize() { // library marker kkossev.commonLib, line 1747
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1748
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1749
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1750
    updateTuyaVersion() // library marker kkossev.commonLib, line 1751
    updateAqaraVersion() // library marker kkossev.commonLib, line 1752
} // library marker kkossev.commonLib, line 1753

/* // library marker kkossev.commonLib, line 1755
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1756
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1757
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1758
*/ // library marker kkossev.commonLib, line 1759

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1761
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1762
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1763
} // library marker kkossev.commonLib, line 1764

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1766
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1767
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1768
} // library marker kkossev.commonLib, line 1769

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1771
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1772
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1773
} // library marker kkossev.commonLib, line 1774

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1776
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1777
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1778
        return // library marker kkossev.commonLib, line 1779
    } // library marker kkossev.commonLib, line 1780
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1781
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1782
    cmd.each { // library marker kkossev.commonLib, line 1783
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1784
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1785
    } // library marker kkossev.commonLib, line 1786
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1787
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1788
} // library marker kkossev.commonLib, line 1789

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1791

String getDeviceInfo() { // library marker kkossev.commonLib, line 1793
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1794
} // library marker kkossev.commonLib, line 1795

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1797
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1798
} // library marker kkossev.commonLib, line 1799

void checkDriverVersion() { // library marker kkossev.commonLib, line 1801
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1802
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1803
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1804
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1805
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 1806
        updateTuyaVersion() // library marker kkossev.commonLib, line 1807
        updateAqaraVersion() // library marker kkossev.commonLib, line 1808
    } // library marker kkossev.commonLib, line 1809
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1810
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1811
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1812
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1813
} // library marker kkossev.commonLib, line 1814

// credits @thebearmay // library marker kkossev.commonLib, line 1816
String getModel() { // library marker kkossev.commonLib, line 1817
    try { // library marker kkossev.commonLib, line 1818
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1819
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1820
    } catch (ignore) { // library marker kkossev.commonLib, line 1821
        try { // library marker kkossev.commonLib, line 1822
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1823
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1824
                return model // library marker kkossev.commonLib, line 1825
            } // library marker kkossev.commonLib, line 1826
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1827
            return '' // library marker kkossev.commonLib, line 1828
        } // library marker kkossev.commonLib, line 1829
    } // library marker kkossev.commonLib, line 1830
} // library marker kkossev.commonLib, line 1831

// credits @thebearmay // library marker kkossev.commonLib, line 1833
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1834
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1835
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1836
    String revision = tokens.last() // library marker kkossev.commonLib, line 1837
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1838
} // library marker kkossev.commonLib, line 1839

/** // library marker kkossev.commonLib, line 1841
 * called from TODO // library marker kkossev.commonLib, line 1842
 */ // library marker kkossev.commonLib, line 1843

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1845
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1846
    unschedule() // library marker kkossev.commonLib, line 1847
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1848
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1849

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1851
} // library marker kkossev.commonLib, line 1852

void resetStatistics() { // library marker kkossev.commonLib, line 1854
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1855
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1856
} // library marker kkossev.commonLib, line 1857

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1859
void resetStats() { // library marker kkossev.commonLib, line 1860
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1861
    state.stats = [:] // library marker kkossev.commonLib, line 1862
    state.states = [:] // library marker kkossev.commonLib, line 1863
    state.lastRx = [:] // library marker kkossev.commonLib, line 1864
    state.lastTx = [:] // library marker kkossev.commonLib, line 1865
    state.health = [:] // library marker kkossev.commonLib, line 1866
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1867
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1868
    } // library marker kkossev.commonLib, line 1869
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1870
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1871
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1872
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1873
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1874
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1875
} // library marker kkossev.commonLib, line 1876

/** // library marker kkossev.commonLib, line 1878
 * called from TODO // library marker kkossev.commonLib, line 1879
 */ // library marker kkossev.commonLib, line 1880
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1881
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1882
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1883
        state.clear() // library marker kkossev.commonLib, line 1884
        unschedule() // library marker kkossev.commonLib, line 1885
        resetStats() // library marker kkossev.commonLib, line 1886
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1887
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1888
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1889
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1890
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1891
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1892
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1893
    } // library marker kkossev.commonLib, line 1894

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1896
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1897
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1898
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1899
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1900

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1902
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1903
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1904
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1905
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1906
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1907
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1908
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1909
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1910
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1911

    // common libraries initialization // library marker kkossev.commonLib, line 1913
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1914

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1916
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1917
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1918
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1919
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1920

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1922
    if ( mm != null) { // library marker kkossev.commonLib, line 1923
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1924
    } // library marker kkossev.commonLib, line 1925
    else { // library marker kkossev.commonLib, line 1926
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1927
    } // library marker kkossev.commonLib, line 1928
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1929
    if ( ep  != null) { // library marker kkossev.commonLib, line 1930
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1931
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1932
    } // library marker kkossev.commonLib, line 1933
    else { // library marker kkossev.commonLib, line 1934
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1935
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1936
    } // library marker kkossev.commonLib, line 1937
} // library marker kkossev.commonLib, line 1938

/** // library marker kkossev.commonLib, line 1940
 * called from TODO // library marker kkossev.commonLib, line 1941
 */ // library marker kkossev.commonLib, line 1942
void setDestinationEP() { // library marker kkossev.commonLib, line 1943
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1944
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1945
        state.destinationEP = ep // library marker kkossev.commonLib, line 1946
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
    else { // library marker kkossev.commonLib, line 1949
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1950
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1951
    } // library marker kkossev.commonLib, line 1952
} // library marker kkossev.commonLib, line 1953

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1955
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1956
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1957
    } // library marker kkossev.commonLib, line 1958
} // library marker kkossev.commonLib, line 1959

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1961
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1962
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 1963
    } // library marker kkossev.commonLib, line 1964
} // library marker kkossev.commonLib, line 1965

void logWarn(final String msg) { // library marker kkossev.commonLib, line 1967
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1968
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 1969
    } // library marker kkossev.commonLib, line 1970
} // library marker kkossev.commonLib, line 1971

void logTrace(final String msg) { // library marker kkossev.commonLib, line 1973
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 1974
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 1975
    } // library marker kkossev.commonLib, line 1976
} // library marker kkossev.commonLib, line 1977

// _DEBUG mode only // library marker kkossev.commonLib, line 1979
void getAllProperties() { // library marker kkossev.commonLib, line 1980
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1981
    device.properties.each { it -> // library marker kkossev.commonLib, line 1982
        log.debug it // library marker kkossev.commonLib, line 1983
    } // library marker kkossev.commonLib, line 1984
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1985
    settings.each { it -> // library marker kkossev.commonLib, line 1986
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1987
    } // library marker kkossev.commonLib, line 1988
    log.trace 'Done' // library marker kkossev.commonLib, line 1989
} // library marker kkossev.commonLib, line 1990

// delete all Preferences // library marker kkossev.commonLib, line 1992
void deleteAllSettings() { // library marker kkossev.commonLib, line 1993
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1994
    settings.each { it -> // library marker kkossev.commonLib, line 1995
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1996
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1997
    } // library marker kkossev.commonLib, line 1998
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1999
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2000
} // library marker kkossev.commonLib, line 2001

// delete all attributes // library marker kkossev.commonLib, line 2003
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2004
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2005
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2006
        attributesDeleted += "${it}, " // library marker kkossev.commonLib, line 2007
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2008
    } // library marker kkossev.commonLib, line 2009
    logDebug "Deleted attributes: ${attributesDeleted}" // library marker kkossev.commonLib, line 2010
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2011
} // library marker kkossev.commonLib, line 2012

// delete all State Variables // library marker kkossev.commonLib, line 2014
void deleteAllStates() { // library marker kkossev.commonLib, line 2015
    state.each { it -> // library marker kkossev.commonLib, line 2016
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2017
    } // library marker kkossev.commonLib, line 2018
    state.clear() // library marker kkossev.commonLib, line 2019
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2020
} // library marker kkossev.commonLib, line 2021

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2023
    unschedule() // library marker kkossev.commonLib, line 2024
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2025
} // library marker kkossev.commonLib, line 2026

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2028
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2029
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2030
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2031
    } // library marker kkossev.commonLib, line 2032
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2033
} // library marker kkossev.commonLib, line 2034

void parseTest(String par) { // library marker kkossev.commonLib, line 2036
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2037
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2038
    parse(par) // library marker kkossev.commonLib, line 2039
} // library marker kkossev.commonLib, line 2040

def testJob() { // library marker kkossev.commonLib, line 2042
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2043
} // library marker kkossev.commonLib, line 2044

/** // library marker kkossev.commonLib, line 2046
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2047
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2048
 */ // library marker kkossev.commonLib, line 2049
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2050
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2051
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2052
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2053
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2054
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2055
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2056
    String cron // library marker kkossev.commonLib, line 2057
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2058
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2059
    } // library marker kkossev.commonLib, line 2060
    else { // library marker kkossev.commonLib, line 2061
        if (minutes < 60) { // library marker kkossev.commonLib, line 2062
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2063
        } // library marker kkossev.commonLib, line 2064
        else { // library marker kkossev.commonLib, line 2065
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2066
        } // library marker kkossev.commonLib, line 2067
    } // library marker kkossev.commonLib, line 2068
    return cron // library marker kkossev.commonLib, line 2069
} // library marker kkossev.commonLib, line 2070

// credits @thebearmay // library marker kkossev.commonLib, line 2072
String formatUptime() { // library marker kkossev.commonLib, line 2073
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2074
} // library marker kkossev.commonLib, line 2075

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2077
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2078
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2079
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2080
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2081
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2082
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2083
} // library marker kkossev.commonLib, line 2084

boolean isTuya() { // library marker kkossev.commonLib, line 2086
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2087
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2088
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2089
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2090
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2091
} // library marker kkossev.commonLib, line 2092

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2094
    if (!isTuya()) { // library marker kkossev.commonLib, line 2095
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2096
        return // library marker kkossev.commonLib, line 2097
    } // library marker kkossev.commonLib, line 2098
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2099
    if (application != null) { // library marker kkossev.commonLib, line 2100
        Integer ver // library marker kkossev.commonLib, line 2101
        try { // library marker kkossev.commonLib, line 2102
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2103
        } // library marker kkossev.commonLib, line 2104
        catch (e) { // library marker kkossev.commonLib, line 2105
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2106
            return // library marker kkossev.commonLib, line 2107
        } // library marker kkossev.commonLib, line 2108
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2109
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2110
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2111
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2112
        } // library marker kkossev.commonLib, line 2113
    } // library marker kkossev.commonLib, line 2114
} // library marker kkossev.commonLib, line 2115

boolean isAqara() { // library marker kkossev.commonLib, line 2117
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2118
} // library marker kkossev.commonLib, line 2119

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2121
    if (!isAqara()) { // library marker kkossev.commonLib, line 2122
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2123
        return // library marker kkossev.commonLib, line 2124
    } // library marker kkossev.commonLib, line 2125
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2126
    if (application != null) { // library marker kkossev.commonLib, line 2127
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2128
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2129
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2130
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2131
        } // library marker kkossev.commonLib, line 2132
    } // library marker kkossev.commonLib, line 2133
} // library marker kkossev.commonLib, line 2134

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2136
    try { // library marker kkossev.commonLib, line 2137
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2138
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2139
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2140
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2141
    } catch (e) { // library marker kkossev.commonLib, line 2142
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2143
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2144
    } // library marker kkossev.commonLib, line 2145
} // library marker kkossev.commonLib, line 2146

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2148
    try { // library marker kkossev.commonLib, line 2149
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2150
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2151
        return date.getTime() // library marker kkossev.commonLib, line 2152
    } catch (e) { // library marker kkossev.commonLib, line 2153
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2154
        return now() // library marker kkossev.commonLib, line 2155
    } // library marker kkossev.commonLib, line 2156
} // library marker kkossev.commonLib, line 2157
/* // library marker kkossev.commonLib, line 2158
void test(String par) { // library marker kkossev.commonLib, line 2159
    List<String> cmds = [] // library marker kkossev.commonLib, line 2160
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2161

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2163
    //parse(par) // library marker kkossev.commonLib, line 2164

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2166
} // library marker kkossev.commonLib, line 2167
*/ // library marker kkossev.commonLib, line 2168

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

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
