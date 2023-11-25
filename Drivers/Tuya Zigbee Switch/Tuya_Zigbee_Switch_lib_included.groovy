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
 *
 *                                   TODO: 
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { "3.0.1" }
static String timeStamp() {"2023/11/25 11:57 PM"}

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

def isZBMINIL2()   { (device?.getDataValue('model') ?: 'n/a') in ['ZBMINIL2'] }

def refreshSwitch() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
    logDebug "refreshSwitch() : ${cmds}"
    return cmds
}

def initVarsSwitch(boolean fullInit=false) {
    logDebug "initVarsSwitch(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.ignoreDuplicated == null) device.updateSetting("ignoreDuplicated", false)

    
}

void initEventsSwitch(boolean fullInit=false) {
}


def configureDeviceSwitch() {
    List<String> cmds = []
    if (isZBMINIL2()) {
        logDebug "configureDeviceSwitch() : ZBMINIL2"
        // Unbind genPollCtrl to prevent device from sending checkin message.
        // Zigbee-herdsmans responds to the checkin message which causes the device to poll slower.
        // https://github.com/Koenkk/zigbee2mqtt/issues/11676        
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
    logDebug "configureDeviceSwitch() : ${cmds}"
    return cmds    
}

/* parsed in the commonLib
void parseOnOffClusterSwitch(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseOnOffClusterSwitch: (0x0006)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
    switchEvent(value)
}
*/

void parseElectricalMeasureClusterSwitch(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseElectricalMeasureClusterSwitch: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void parseMeteringClusterSwitch(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseMeteringClusterSwitch: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
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
library ( // library marker kkossev.commonLib, line 1
    base: "driver", // library marker kkossev.commonLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.commonLib, line 3
    category: "zigbee", // library marker kkossev.commonLib, line 4
    description: "Common ZCL Library", // library marker kkossev.commonLib, line 5
    name: "commonLib", // library marker kkossev.commonLib, line 6
    namespace: "kkossev", // library marker kkossev.commonLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy", // library marker kkossev.commonLib, line 8
    version: "3.0.0", // library marker kkossev.commonLib, line 9
    documentationLink: "" // library marker kkossev.commonLib, line 10
) // library marker kkossev.commonLib, line 11
/* // library marker kkossev.commonLib, line 12
  *  Common ZCL Library // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 15
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 16
  * // library marker kkossev.commonLib, line 17
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 18
  * // library marker kkossev.commonLib, line 19
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 20
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 21
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 22
  * // library marker kkossev.commonLib, line 23
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 24
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 25
  * // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 28
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 29
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.1  2023-11-24 kkossev  - (dev.branch) Info event renamed to Status; txtEnable and logEnable moved to the custom driver settings // library marker kkossev.commonLib, line 32
  * // library marker kkossev.commonLib, line 33
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 34
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 35
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 36
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 37
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 38
 * // library marker kkossev.commonLib, line 39
*/ // library marker kkossev.commonLib, line 40

def commonLibVersion()   {"3.0.1"} // library marker kkossev.commonLib, line 42
def thermostatLibStamp() {"2023/11/24 3:10 PM"} // library marker kkossev.commonLib, line 43

import groovy.transform.Field // library marker kkossev.commonLib, line 45
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 46
import hubitat.device.Protocol // library marker kkossev.commonLib, line 47
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 48
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 49
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 50
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 51


@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 54

metadata { // library marker kkossev.commonLib, line 56

        if (_DEBUG) { // library marker kkossev.commonLib, line 58
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]  // library marker kkossev.commonLib, line 59
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]] // library marker kkossev.commonLib, line 60
            command "tuyaTest", [ // library marker kkossev.commonLib, line 61
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]], // library marker kkossev.commonLib, line 62
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]], // library marker kkossev.commonLib, line 63
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] // library marker kkossev.commonLib, line 64
            ] // library marker kkossev.commonLib, line 65
        } // library marker kkossev.commonLib, line 66


        // common capabilities for all device types // library marker kkossev.commonLib, line 69
        capability 'Configuration' // library marker kkossev.commonLib, line 70
        capability 'Refresh' // library marker kkossev.commonLib, line 71
        capability 'Health Check' // library marker kkossev.commonLib, line 72

        // common attributes for all device types // library marker kkossev.commonLib, line 74
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 75
        attribute "rtt", "number"  // library marker kkossev.commonLib, line 76
        attribute "Status", "string" // library marker kkossev.commonLib, line 77

        // common commands for all device types // library marker kkossev.commonLib, line 79
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 80
        command "configure", [[name:"normally it is not needed to configure anything", type: "ENUM",   constraints: ["--- select ---"]+ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 81

        // deviceType specific capabilities, commands and attributes          // library marker kkossev.commonLib, line 83
        if (deviceType in ["Device"]) { // library marker kkossev.commonLib, line 84
            if (_DEBUG) { // library marker kkossev.commonLib, line 85
                command "getAllProperties",       [[name: "Get All Properties"]] // library marker kkossev.commonLib, line 86
            } // library marker kkossev.commonLib, line 87
        } // library marker kkossev.commonLib, line 88
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) { // library marker kkossev.commonLib, line 89
            command "zigbeeGroups", [ // library marker kkossev.commonLib, line 90
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 91
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]] // library marker kkossev.commonLib, line 92
            ] // library marker kkossev.commonLib, line 93
        }         // library marker kkossev.commonLib, line 94
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube", "Radar"]) { // library marker kkossev.commonLib, line 95
            capability "Sensor" // library marker kkossev.commonLib, line 96
        } // library marker kkossev.commonLib, line 97
        if (deviceType in  ["Device", "MotionSensor", "Radar"]) { // library marker kkossev.commonLib, line 98
            capability "MotionSensor" // library marker kkossev.commonLib, line 99
        } // library marker kkossev.commonLib, line 100
        if (deviceType in  ["Device", "Switch", "Relay", "Plug", "Outlet", "Thermostat", "Fingerbot", "Dimmer", "Bulb", "IRBlaster"]) { // library marker kkossev.commonLib, line 101
            capability "Actuator" // library marker kkossev.commonLib, line 102
        } // library marker kkossev.commonLib, line 103
        if (deviceType in  ["Device", "THSensor", "LightSensor", "MotionSensor", "Thermostat", "Fingerbot", "ButtonDimmer", "AqaraCube", "IRBlaster"]) { // library marker kkossev.commonLib, line 104
            capability "Battery" // library marker kkossev.commonLib, line 105
            attribute "batteryVoltage", "number" // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ["Thermostat"]) { // library marker kkossev.commonLib, line 108
            capability "Thermostat" // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ["Plug", "Outlet"]) { // library marker kkossev.commonLib, line 111
            capability "Outlet" // library marker kkossev.commonLib, line 112
        }         // library marker kkossev.commonLib, line 113
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot", "Bulb"]) { // library marker kkossev.commonLib, line 114
            capability "Switch" // library marker kkossev.commonLib, line 115
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 116
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 117
            } // library marker kkossev.commonLib, line 118
        }         // library marker kkossev.commonLib, line 119
        if (deviceType in ["Dimmer", "ButtonDimmer", "Bulb"]) { // library marker kkossev.commonLib, line 120
            capability "SwitchLevel" // library marker kkossev.commonLib, line 121
        } // library marker kkossev.commonLib, line 122
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) { // library marker kkossev.commonLib, line 123
            capability "PushableButton" // library marker kkossev.commonLib, line 124
            capability "DoubleTapableButton" // library marker kkossev.commonLib, line 125
            capability "HoldableButton" // library marker kkossev.commonLib, line 126
            capability "ReleasableButton" // library marker kkossev.commonLib, line 127
        } // library marker kkossev.commonLib, line 128
        if (deviceType in  ["Device", "Fingerbot"]) { // library marker kkossev.commonLib, line 129
            capability "Momentary" // library marker kkossev.commonLib, line 130
        } // library marker kkossev.commonLib, line 131
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) { // library marker kkossev.commonLib, line 132
            capability "TemperatureMeasurement" // library marker kkossev.commonLib, line 133
        } // library marker kkossev.commonLib, line 134
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 135
            capability "RelativeHumidityMeasurement"             // library marker kkossev.commonLib, line 136
        } // library marker kkossev.commonLib, line 137
        if (deviceType in  ["Device", "LightSensor", "Radar"]) { // library marker kkossev.commonLib, line 138
            capability "IlluminanceMeasurement" // library marker kkossev.commonLib, line 139
        } // library marker kkossev.commonLib, line 140
        if (deviceType in  ["AirQuality"]) { // library marker kkossev.commonLib, line 141
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500 // library marker kkossev.commonLib, line 142
        } // library marker kkossev.commonLib, line 143

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 145
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug"  // library marker kkossev.commonLib, line 146

    preferences { // library marker kkossev.commonLib, line 148
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 149
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 150
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 151

        if (advancedOptions == true || advancedOptions == false) { // groovy ... // library marker kkossev.commonLib, line 153
            if (device.hasCapability("TemperatureMeasurement") || device.hasCapability("RelativeHumidityMeasurement") || device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 154
                input name: "minReportingTime", type: "number", title: "<b>Minimum time between reports</b>", description: "<i>Minimum reporting interval, seconds (1..300)</i>", range: "1..300", defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 155
                input name: "maxReportingTime", type: "number", title: "<b>Maximum time between reports</b>", description: "<i>Maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 156
            } // library marker kkossev.commonLib, line 157
            if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 158
                input name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 159
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00 // library marker kkossev.commonLib, line 160

            } // library marker kkossev.commonLib, line 162
        } // library marker kkossev.commonLib, line 163

        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: "<i>These advanced options should be already automatically set in an optimal way for your device...</i>", defaultValue: false // library marker kkossev.commonLib, line 165
        if (advancedOptions == true || advancedOptions == true) { // library marker kkossev.commonLib, line 166
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 167
            //if (healthCheckMethod != null && safeToInt(healthCheckMethod.value) != 0) { // library marker kkossev.commonLib, line 168
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 169
            //} // library marker kkossev.commonLib, line 170
            if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 171
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 172

            } // library marker kkossev.commonLib, line 174
            if ((deviceType in  ["Switch", "Plug", "Dimmer"]) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 175
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 176
            } // library marker kkossev.commonLib, line 177
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 178
        } // library marker kkossev.commonLib, line 179
    } // library marker kkossev.commonLib, line 180

} // library marker kkossev.commonLib, line 182


@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 185
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 186
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events  // library marker kkossev.commonLib, line 187
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 188
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 189
@Field static final String  UNKNOWN = "UNKNOWN" // library marker kkossev.commonLib, line 190
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 191
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 192
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 193
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 194
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 195
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 30      // automatically clear the Info attribute after 30 seconds // library marker kkossev.commonLib, line 196

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 198
    defaultValue: 1, // library marker kkossev.commonLib, line 199
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 200
] // library marker kkossev.commonLib, line 201
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 202
    defaultValue: 240, // library marker kkossev.commonLib, line 203
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 204
] // library marker kkossev.commonLib, line 205
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 211
    defaultValue: 0, // library marker kkossev.commonLib, line 212
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 213
] // library marker kkossev.commonLib, line 214
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 215
    defaultValue: 0, // library marker kkossev.commonLib, line 216
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 217
] // library marker kkossev.commonLib, line 218

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 220
    "Configure the device only"  : [key:2, function: 'configure'], // library marker kkossev.commonLib, line 221
    "Reset Statistics"           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 222
    "           --            "  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 223
    "Delete All Preferences"     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 224
    "Delete All Current States"  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 225
    "Delete All Scheduled Jobs"  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 226
    "Delete All State Variables" : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 227
    "Delete All Child Devices"   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 228
    "           -             "  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 229
    "*** LOAD ALL DEFAULTS ***"  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 230
] // library marker kkossev.commonLib, line 231


def isChattyDeviceReport(description)  {return false /*(description?.contains("cluster: FC7E")) */} // library marker kkossev.commonLib, line 234
def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 235
def isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 236
def isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 237
def isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 238
def isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 239
def isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 240

/** // library marker kkossev.commonLib, line 242
 * Parse Zigbee message // library marker kkossev.commonLib, line 243
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 244
 */ // library marker kkossev.commonLib, line 245
void parse(final String description) { // library marker kkossev.commonLib, line 246
    checkDriverVersion() // library marker kkossev.commonLib, line 247
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 248
    if (state.stats != null) state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 249
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 250
    setHealthStatusOnline() // library marker kkossev.commonLib, line 251

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {     // library marker kkossev.commonLib, line 253
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 254
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 255
            logDebug "ignored IAS zone status" // library marker kkossev.commonLib, line 256
            return // library marker kkossev.commonLib, line 257
        } // library marker kkossev.commonLib, line 258
        else { // library marker kkossev.commonLib, line 259
            parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 260
        } // library marker kkossev.commonLib, line 261
    } // library marker kkossev.commonLib, line 262
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 263
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 264
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 265
        if (settings?.logEnable) logInfo "Sending IAS enroll response..." // library marker kkossev.commonLib, line 266
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 267
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 268
        sendZigbeeCommands( cmds )   // library marker kkossev.commonLib, line 269
    }  // library marker kkossev.commonLib, line 270
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 271
        return // library marker kkossev.commonLib, line 272
    }         // library marker kkossev.commonLib, line 273
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 274

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 276
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 277
        return // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 280
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 281
        return // library marker kkossev.commonLib, line 282
    } // library marker kkossev.commonLib, line 283
    if (!isChattyDeviceReport(description)) {logDebug "parse: descMap = ${descMap} description=${description}"} // library marker kkossev.commonLib, line 284
    // // library marker kkossev.commonLib, line 285
    final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 286
    final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 287
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 288

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 290
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 291
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 292
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 293
            break // library marker kkossev.commonLib, line 294
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 295
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 296
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 299
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 300
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 303
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 304
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 307
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each {final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 311
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 312
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 315
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 316
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro // library marker kkossev.commonLib, line 319
            parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 320
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 321
            break // library marker kkossev.commonLib, line 322
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 323
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
         case 0x0102 :                                      // window covering  // library marker kkossev.commonLib, line 326
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 327
            break        // library marker kkossev.commonLib, line 328
        case 0x0201 :                                       // Aqara E1 TRV  // library marker kkossev.commonLib, line 329
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 330
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 333
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 334
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 335
            break // library marker kkossev.commonLib, line 336
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 337
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 338
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 339
            break // library marker kkossev.commonLib, line 340
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 341
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 344
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 347
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 350
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 351
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 354
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 355
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 356
            break // library marker kkossev.commonLib, line 357
        case 0xE002 : // library marker kkossev.commonLib, line 358
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 359
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 360
            break // library marker kkossev.commonLib, line 361
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 362
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 363
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 364
            break // library marker kkossev.commonLib, line 365
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 366
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 367
            break // library marker kkossev.commonLib, line 368
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 369
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 370
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        default: // library marker kkossev.commonLib, line 373
            if (settings.logEnable) { // library marker kkossev.commonLib, line 374
                logWarn "zigbee received <b>unknown cluster:${descMap.clusterId}</b> message (${descMap})" // library marker kkossev.commonLib, line 375
            } // library marker kkossev.commonLib, line 376
            break // library marker kkossev.commonLib, line 377
    } // library marker kkossev.commonLib, line 378

} // library marker kkossev.commonLib, line 380

/** // library marker kkossev.commonLib, line 382
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 383
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 384
 */ // library marker kkossev.commonLib, line 385
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 386
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 387
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 388
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 389
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 390
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 391
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 392
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 393
    }  // library marker kkossev.commonLib, line 394
    else { // library marker kkossev.commonLib, line 395
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 396
    } // library marker kkossev.commonLib, line 397
} // library marker kkossev.commonLib, line 398

/** // library marker kkossev.commonLib, line 400
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 401
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 402
 */ // library marker kkossev.commonLib, line 403
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 404
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 405
    switch (commandId) { // library marker kkossev.commonLib, line 406
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 407
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 408
            break // library marker kkossev.commonLib, line 409
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 410
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 411
            break // library marker kkossev.commonLib, line 412
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 413
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 414
            break // library marker kkossev.commonLib, line 415
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 416
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 417
            break // library marker kkossev.commonLib, line 418
        case 0x0B: // default command response // library marker kkossev.commonLib, line 419
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 420
            break // library marker kkossev.commonLib, line 421
        default: // library marker kkossev.commonLib, line 422
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 423
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 424
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 425
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 426
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 427
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 428
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 429
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 430
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 431
            } // library marker kkossev.commonLib, line 432
            break // library marker kkossev.commonLib, line 433
    } // library marker kkossev.commonLib, line 434
} // library marker kkossev.commonLib, line 435

/** // library marker kkossev.commonLib, line 437
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 438
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 439
 */ // library marker kkossev.commonLib, line 440
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 441
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 442
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 443
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 444
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 445
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 446
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 447
    } // library marker kkossev.commonLib, line 448
    else { // library marker kkossev.commonLib, line 449
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 450
    } // library marker kkossev.commonLib, line 451
} // library marker kkossev.commonLib, line 452

/** // library marker kkossev.commonLib, line 454
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 455
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 456
 */ // library marker kkossev.commonLib, line 457
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 458
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 459
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 460
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 461
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 462
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 463
    } // library marker kkossev.commonLib, line 464
    else { // library marker kkossev.commonLib, line 465
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 466
    } // library marker kkossev.commonLib, line 467
} // library marker kkossev.commonLib, line 468

/** // library marker kkossev.commonLib, line 470
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 471
 */ // library marker kkossev.commonLib, line 472
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 473
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 474
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 475
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 476
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 477
        state.reportingEnabled = true // library marker kkossev.commonLib, line 478
    } // library marker kkossev.commonLib, line 479
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 480
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 481
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 482
    } else { // library marker kkossev.commonLib, line 483
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
} // library marker kkossev.commonLib, line 486

/** // library marker kkossev.commonLib, line 488
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 489
 */ // library marker kkossev.commonLib, line 490
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 491
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 492
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 493
    def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 494
    def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 495
    if (status == 0) { // library marker kkossev.commonLib, line 496
        def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 497
        def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 498
        def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7]) // library marker kkossev.commonLib, line 499
        def delta = 0 // library marker kkossev.commonLib, line 500
        if (descMap.data.size()>=10) {  // library marker kkossev.commonLib, line 501
            delta = zigbee.convertHexToInt(descMap.data[10]+descMap.data[9]) // library marker kkossev.commonLib, line 502
        } // library marker kkossev.commonLib, line 503
        else { // library marker kkossev.commonLib, line 504
            logDebug "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 505
        } // library marker kkossev.commonLib, line 506
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 507
    } // library marker kkossev.commonLib, line 508
    else { // library marker kkossev.commonLib, line 509
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
} // library marker kkossev.commonLib, line 512

/** // library marker kkossev.commonLib, line 514
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 515
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 516
 */ // library marker kkossev.commonLib, line 517
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 518
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 519
    final String commandId = data[0] // library marker kkossev.commonLib, line 520
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 521
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 522
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 523
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 524
    } else { // library marker kkossev.commonLib, line 525
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 526
    } // library marker kkossev.commonLib, line 527
} // library marker kkossev.commonLib, line 528


// Zigbee Attribute IDs // library marker kkossev.commonLib, line 531
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 532
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 533
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 534
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 535
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 536
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 537
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 538
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 539
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 540
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 541
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 542
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 543
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 544
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 545
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 546

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 548
    0x00: 'Success', // library marker kkossev.commonLib, line 549
    0x01: 'Failure', // library marker kkossev.commonLib, line 550
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 551
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 552
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 553
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 554
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 555
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 556
    0x88: 'Read Only', // library marker kkossev.commonLib, line 557
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 558
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 559
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 560
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 561
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 562
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 563
    0x94: 'Time out', // library marker kkossev.commonLib, line 564
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 565
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 566
] // library marker kkossev.commonLib, line 567

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 569
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 570
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 571
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 572
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 573
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 574
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 575
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 576
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 577
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 578
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 579
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 580
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 581
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 582
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 583
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 584
] // library marker kkossev.commonLib, line 585

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 587
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 588
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 589
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 590
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 591
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 592
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 593
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 594
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 595
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 596
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 597
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 598
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 599
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 600
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 601
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 602
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 603
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 604
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 605
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 606
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 607
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 608
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 609
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 610
] // library marker kkossev.commonLib, line 611


/* // library marker kkossev.commonLib, line 614
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 615
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 616
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 617
 */ // library marker kkossev.commonLib, line 618
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 619
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 620
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 621
    }     // library marker kkossev.commonLib, line 622
    else { // library marker kkossev.commonLib, line 623
        logWarn "Xiaomi cluster 0xFCC0" // library marker kkossev.commonLib, line 624
    } // library marker kkossev.commonLib, line 625
} // library marker kkossev.commonLib, line 626


/* // library marker kkossev.commonLib, line 629
@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.commonLib, line 630

// Zigbee Attributes // library marker kkossev.commonLib, line 632
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.commonLib, line 633
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.commonLib, line 634
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.commonLib, line 635
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.commonLib, line 636
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.commonLib, line 637
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.commonLib, line 638
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.commonLib, line 639
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.commonLib, line 640
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.commonLib, line 641
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.commonLib, line 642
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.commonLib, line 643
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.commonLib, line 644
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.commonLib, line 645
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.commonLib, line 646
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.commonLib, line 647

// Xiaomi Tags // library marker kkossev.commonLib, line 649
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.commonLib, line 650
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.commonLib, line 651
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.commonLib, line 652
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.commonLib, line 653
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.commonLib, line 654
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.commonLib, line 655
*/ // library marker kkossev.commonLib, line 656


// TODO - move to xiaomiLib // library marker kkossev.commonLib, line 659
// TODO - move to thermostatLib // library marker kkossev.commonLib, line 660
// TODO - move to aqaraQubeLib // library marker kkossev.commonLib, line 661




@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 666
double approxRollingAverage (double avg, double new_sample) { // library marker kkossev.commonLib, line 667
    if (avg == null || avg == 0) { avg = new_sample} // library marker kkossev.commonLib, line 668
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 669
    avg += new_sample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 670
    // TOSO: try Method II : New average = old average * (n-1)/n + new value /n // library marker kkossev.commonLib, line 671
    return avg // library marker kkossev.commonLib, line 672
} // library marker kkossev.commonLib, line 673

/* // library marker kkossev.commonLib, line 675
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 676
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 677
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 678
*/ // library marker kkossev.commonLib, line 679
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 680

/** // library marker kkossev.commonLib, line 682
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 683
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 684
 */ // library marker kkossev.commonLib, line 685
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 686
    def now = new Date().getTime() // library marker kkossev.commonLib, line 687
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 688
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 689
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 690
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 691
    state.lastRx["checkInTime"] = now // library marker kkossev.commonLib, line 692
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 693
        case 0x0000: // library marker kkossev.commonLib, line 694
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 695
            break // library marker kkossev.commonLib, line 696
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 697
            boolean isPing = state.states["isPing"] ?: false // library marker kkossev.commonLib, line 698
            if (isPing) { // library marker kkossev.commonLib, line 699
                def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger() // library marker kkossev.commonLib, line 700
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 701
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 702
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 703
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 704
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 705
                    sendRttEvent() // library marker kkossev.commonLib, line 706
                } // library marker kkossev.commonLib, line 707
                else { // library marker kkossev.commonLib, line 708
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 709
                } // library marker kkossev.commonLib, line 710
                state.states["isPing"] = false // library marker kkossev.commonLib, line 711
            } // library marker kkossev.commonLib, line 712
            else { // library marker kkossev.commonLib, line 713
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 714
            } // library marker kkossev.commonLib, line 715
            break // library marker kkossev.commonLib, line 716
        case 0x0004: // library marker kkossev.commonLib, line 717
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 718
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 719
            def manufacturer = device.getDataValue("manufacturer") // library marker kkossev.commonLib, line 720
            if ((manufacturer == null || manufacturer == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 721
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 722
                device.updateDataValue("manufacturer", descMap?.value) // library marker kkossev.commonLib, line 723
            } // library marker kkossev.commonLib, line 724
            break // library marker kkossev.commonLib, line 725
        case 0x0005: // library marker kkossev.commonLib, line 726
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 727
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 728
            def model = device.getDataValue("model") // library marker kkossev.commonLib, line 729
            if ((model == null || model == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 730
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 731
                device.updateDataValue("model", descMap?.value) // library marker kkossev.commonLib, line 732
            } // library marker kkossev.commonLib, line 733
            break // library marker kkossev.commonLib, line 734
        case 0x0007: // library marker kkossev.commonLib, line 735
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 736
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 737
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 738
            break // library marker kkossev.commonLib, line 739
        case 0xFFDF: // library marker kkossev.commonLib, line 740
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 741
            break // library marker kkossev.commonLib, line 742
        case 0xFFE2: // library marker kkossev.commonLib, line 743
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 744
            break // library marker kkossev.commonLib, line 745
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 746
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 747
            break // library marker kkossev.commonLib, line 748
        case 0xFFFE: // library marker kkossev.commonLib, line 749
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 750
            break // library marker kkossev.commonLib, line 751
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 752
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 753
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 754
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 755
            break // library marker kkossev.commonLib, line 756
        default: // library marker kkossev.commonLib, line 757
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 758
            break // library marker kkossev.commonLib, line 759
    } // library marker kkossev.commonLib, line 760
} // library marker kkossev.commonLib, line 761

/* // library marker kkossev.commonLib, line 763
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 764
 * power cluster            0x0001 // library marker kkossev.commonLib, line 765
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 766
*/ // library marker kkossev.commonLib, line 767
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 768
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 769
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 770
    if (descMap.attrId in ["0020", "0021"]) { // library marker kkossev.commonLib, line 771
        state.lastRx["batteryTime"] = new Date().getTime() // library marker kkossev.commonLib, line 772
        state.stats["battCtr"] = (state.stats["battCtr"] ?: 0 ) + 1 // library marker kkossev.commonLib, line 773
    } // library marker kkossev.commonLib, line 774

    final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 776
    if (descMap.attrId == "0020") { // library marker kkossev.commonLib, line 777
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 778
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 779
            sendBatteryVoltageEvent(rawValue, convertToPercent=true) // library marker kkossev.commonLib, line 780
        } // library marker kkossev.commonLib, line 781
    } // library marker kkossev.commonLib, line 782
    else if (descMap.attrId == "0021") { // library marker kkossev.commonLib, line 783
        sendBatteryPercentageEvent(rawValue * 2)     // library marker kkossev.commonLib, line 784
    } // library marker kkossev.commonLib, line 785
    else { // library marker kkossev.commonLib, line 786
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 787
    } // library marker kkossev.commonLib, line 788
} // library marker kkossev.commonLib, line 789

def sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) { // library marker kkossev.commonLib, line 791
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 792
    def result = [:] // library marker kkossev.commonLib, line 793
    def volts = rawValue / 10 // library marker kkossev.commonLib, line 794
    if (!(rawValue == 0 || rawValue == 255)) { // library marker kkossev.commonLib, line 795
        def minVolts = 2.2 // library marker kkossev.commonLib, line 796
        def maxVolts = 3.2 // library marker kkossev.commonLib, line 797
        def pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 798
        def roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 799
        if (roundedPct <= 0) roundedPct = 1 // library marker kkossev.commonLib, line 800
        if (roundedPct >100) roundedPct = 100 // library marker kkossev.commonLib, line 801
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 802
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 803
            result.name = 'battery' // library marker kkossev.commonLib, line 804
            result.unit  = '%' // library marker kkossev.commonLib, line 805
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 806
        } // library marker kkossev.commonLib, line 807
        else { // library marker kkossev.commonLib, line 808
            result.value = volts // library marker kkossev.commonLib, line 809
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 810
            result.unit  = 'V' // library marker kkossev.commonLib, line 811
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 812
        } // library marker kkossev.commonLib, line 813
        result.type = 'physical' // library marker kkossev.commonLib, line 814
        result.isStateChange = true // library marker kkossev.commonLib, line 815
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 816
        sendEvent(result) // library marker kkossev.commonLib, line 817
    } // library marker kkossev.commonLib, line 818
    else { // library marker kkossev.commonLib, line 819
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 820
    }     // library marker kkossev.commonLib, line 821
} // library marker kkossev.commonLib, line 822

def sendBatteryPercentageEvent( batteryPercent, isDigital=false ) { // library marker kkossev.commonLib, line 824
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 825
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 826
        return // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    def map = [:] // library marker kkossev.commonLib, line 829
    map.name = 'battery' // library marker kkossev.commonLib, line 830
    map.timeStamp = now() // library marker kkossev.commonLib, line 831
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 832
    map.unit  = '%' // library marker kkossev.commonLib, line 833
    map.type = isDigital ? 'digital' : 'physical'     // library marker kkossev.commonLib, line 834
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 835
    map.isStateChange = true // library marker kkossev.commonLib, line 836
    //  // library marker kkossev.commonLib, line 837
    def latestBatteryEvent = device.latestState('battery', skipCache=true) // library marker kkossev.commonLib, line 838
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 839
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 840
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 841
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 842
        // send it now! // library marker kkossev.commonLib, line 843
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 844
    } // library marker kkossev.commonLib, line 845
    else { // library marker kkossev.commonLib, line 846
        def delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 847
        map.delayed = delayedTime // library marker kkossev.commonLib, line 848
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 849
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 850
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 851
    } // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 855
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 856
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 857
    sendEvent(map) // library marker kkossev.commonLib, line 858
} // library marker kkossev.commonLib, line 859

private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 861
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 862
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 863
    sendEvent(map) // library marker kkossev.commonLib, line 864
} // library marker kkossev.commonLib, line 865


/* // library marker kkossev.commonLib, line 868
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 869
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 870
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 871
*/ // library marker kkossev.commonLib, line 872

void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 874
    logDebug "unprocessed parseIdentityCluster" // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876



/* // library marker kkossev.commonLib, line 880
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 881
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 883
*/ // library marker kkossev.commonLib, line 884

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 886
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 887
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 888
    }     // library marker kkossev.commonLib, line 889
    else { // library marker kkossev.commonLib, line 890
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 891
    } // library marker kkossev.commonLib, line 892
} // library marker kkossev.commonLib, line 893


/* // library marker kkossev.commonLib, line 896
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 897
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 898
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 899
*/ // library marker kkossev.commonLib, line 900

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 902
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 903
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 904
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]     // library marker kkossev.commonLib, line 905
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 906
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 907
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 908
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 909
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 910
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 911
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 912
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 913
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 914
            } // library marker kkossev.commonLib, line 915
            else { // library marker kkossev.commonLib, line 916
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 917
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 918
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 919
                for (int i=0; i<groupCount; i++ ) { // library marker kkossev.commonLib, line 920
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 921
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 922
                        return // library marker kkossev.commonLib, line 923
                    } // library marker kkossev.commonLib, line 924
                } // library marker kkossev.commonLib, line 925
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 926
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt,4)})" // library marker kkossev.commonLib, line 927
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 928
            } // library marker kkossev.commonLib, line 929
            break // library marker kkossev.commonLib, line 930
        case 0x01: // View group // library marker kkossev.commonLib, line 931
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 932
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 933
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 934
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 935
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 936
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 937
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 938
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 939
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 940
            } // library marker kkossev.commonLib, line 941
            else { // library marker kkossev.commonLib, line 942
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 943
            } // library marker kkossev.commonLib, line 944
            break // library marker kkossev.commonLib, line 945
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 946
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 947
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 948
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 949
            final Set<String> groups = [] // library marker kkossev.commonLib, line 950
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 951
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 952
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 953
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 954
            } // library marker kkossev.commonLib, line 955
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 956
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 957
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 958
            break // library marker kkossev.commonLib, line 959
        case 0x03: // Remove group // library marker kkossev.commonLib, line 960
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 961
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 962
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 963
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 964
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 965
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 966
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 967
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 968
            } // library marker kkossev.commonLib, line 969
            else { // library marker kkossev.commonLib, line 970
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 971
            } // library marker kkossev.commonLib, line 972
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 973
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 974
            if (index >= 0) { // library marker kkossev.commonLib, line 975
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 976
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 977
            } // library marker kkossev.commonLib, line 978
            break // library marker kkossev.commonLib, line 979
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 980
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 981
            logWarn "not implemented!" // library marker kkossev.commonLib, line 982
            break // library marker kkossev.commonLib, line 983
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 984
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5).  // library marker kkossev.commonLib, line 985
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 986
            logWarn "not implemented!" // library marker kkossev.commonLib, line 987
            break // library marker kkossev.commonLib, line 988
        default: // library marker kkossev.commonLib, line 989
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 990
            break // library marker kkossev.commonLib, line 991
    } // library marker kkossev.commonLib, line 992
} // library marker kkossev.commonLib, line 993

List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 995
    List<String> cmds = [] // library marker kkossev.commonLib, line 996
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 997
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 998
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 999
        return // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1002
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1003
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1004
    return cmds // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1008
    List<String> cmds = [] // library marker kkossev.commonLib, line 1009
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1010
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1011
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1012
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1013
    return cmds // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1017
    List<String> cmds = [] // library marker kkossev.commonLib, line 1018
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, "00") // library marker kkossev.commonLib, line 1019
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1020
    return cmds // library marker kkossev.commonLib, line 1021
} // library marker kkossev.commonLib, line 1022

List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1024
    List<String> cmds = [] // library marker kkossev.commonLib, line 1025
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1026
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1027
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1028
        return // library marker kkossev.commonLib, line 1029
    } // library marker kkossev.commonLib, line 1030
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1031
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1032
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1033
    return cmds // library marker kkossev.commonLib, line 1034
} // library marker kkossev.commonLib, line 1035

List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1037
    List<String> cmds = [] // library marker kkossev.commonLib, line 1038
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1039
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1040
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1041
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1042
    return cmds // library marker kkossev.commonLib, line 1043
} // library marker kkossev.commonLib, line 1044

List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1046
    List<String> cmds = [] // library marker kkossev.commonLib, line 1047
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1048
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1049
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1050
    return cmds // library marker kkossev.commonLib, line 1051
} // library marker kkossev.commonLib, line 1052

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1054
    "--- select ---"           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'GroupCommandsHelp'], // library marker kkossev.commonLib, line 1055
    "Add group"                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1056
    "View group"               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1057
    "Get group membership"     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1058
    "Remove group"             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1059
    "Remove all groups"        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1060
    "Add group if identifying" : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1061
] // library marker kkossev.commonLib, line 1062
/* // library marker kkossev.commonLib, line 1063
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 1064
    defaultValue: 0, // library marker kkossev.commonLib, line 1065
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 1066
] // library marker kkossev.commonLib, line 1067
*/ // library marker kkossev.commonLib, line 1068

def zigbeeGroups( command=null, par=null ) // library marker kkossev.commonLib, line 1070
{ // library marker kkossev.commonLib, line 1071
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1072
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 1073
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1074
    if (state.zigbeeGroups['groups'] == null) state.zigbeeGroups['groups'] = [] // library marker kkossev.commonLib, line 1075
    def value // library marker kkossev.commonLib, line 1076
    Boolean validated = false // library marker kkossev.commonLib, line 1077
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1078
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1079
        return // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    value = GroupCommandsMap[command]?.type == "number" ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1082
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) validated = true // library marker kkossev.commonLib, line 1083
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1084
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1085
        return // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    // // library marker kkossev.commonLib, line 1088
    def func // library marker kkossev.commonLib, line 1089
   // try { // library marker kkossev.commonLib, line 1090
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1091
        def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1092
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1093
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1094
 //   } // library marker kkossev.commonLib, line 1095
//    catch (e) { // library marker kkossev.commonLib, line 1096
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1097
//        return // library marker kkossev.commonLib, line 1098
//    } // library marker kkossev.commonLib, line 1099

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1101
    sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1102
} // library marker kkossev.commonLib, line 1103

def GroupCommandsHelp( val ) { // library marker kkossev.commonLib, line 1105
    logWarn "GroupCommands: select one of the commands in this list!"              // library marker kkossev.commonLib, line 1106
} // library marker kkossev.commonLib, line 1107

/* // library marker kkossev.commonLib, line 1109
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1110
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1111
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1112
*/ // library marker kkossev.commonLib, line 1113

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1115
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1116
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1117
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1118
    }     // library marker kkossev.commonLib, line 1119

    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1121
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1122
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1123
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else if (descMap.attrId in ["4000", "4001", "4002", "4004", "8000", "8001", "8002", "8003"]) { // library marker kkossev.commonLib, line 1126
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
    else { // library marker kkossev.commonLib, line 1129
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
} // library marker kkossev.commonLib, line 1132

def clearIsDigital()        { state.states["isDigital"] = false } // library marker kkossev.commonLib, line 1134
def switchDebouncingClear() { state.states["debounce"]  = false } // library marker kkossev.commonLib, line 1135
def isRefreshRequestClear() { state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 1136

def toggle() { // library marker kkossev.commonLib, line 1138
    def descriptionText = "central button switch is " // library marker kkossev.commonLib, line 1139
    def state = "" // library marker kkossev.commonLib, line 1140
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1141
        state = "on" // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    else { // library marker kkossev.commonLib, line 1144
        state = "off" // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146
    descriptionText += state // library marker kkossev.commonLib, line 1147
    sendEvent(name: "switch", value: state, descriptionText: descriptionText, type: "physical", isStateChange: true) // library marker kkossev.commonLib, line 1148
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1149
} // library marker kkossev.commonLib, line 1150

def off() { // library marker kkossev.commonLib, line 1152
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOff(); return } // library marker kkossev.commonLib, line 1153
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1154
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1155
        return // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1158
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1159
    logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1160
    def cmds = zigbee.off() // library marker kkossev.commonLib, line 1161
    /* // library marker kkossev.commonLib, line 1162
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1163
        cmds += zigbee.command(0x0006, 0x00, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165
        else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1166
            if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1167
                cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "00") // library marker kkossev.commonLib, line 1168
            } // library marker kkossev.commonLib, line 1169
            else { // library marker kkossev.commonLib, line 1170
                cmds = zigbee.command(0xEF00, 0x0, "00010101000100") // library marker kkossev.commonLib, line 1171
            } // library marker kkossev.commonLib, line 1172
        } // library marker kkossev.commonLib, line 1173
        else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1174
            cmds = ["he cmd 0x${device.deviceNetworkId}  0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1175
            logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1176
        } // library marker kkossev.commonLib, line 1177
        else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1178
            cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1179
        } // library marker kkossev.commonLib, line 1180
*/ // library marker kkossev.commonLib, line 1181
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1182
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1183
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1184
        } // library marker kkossev.commonLib, line 1185
        def value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1186
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1187
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1188
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1189
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    else { // library marker kkossev.commonLib, line 1192
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194


    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1197
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1198
} // library marker kkossev.commonLib, line 1199

def on() { // library marker kkossev.commonLib, line 1201
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOn(); return } // library marker kkossev.commonLib, line 1202
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1203
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1204
    logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1205
    def cmds = zigbee.on() // library marker kkossev.commonLib, line 1206
/* // library marker kkossev.commonLib, line 1207
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1208
        cmds += zigbee.command(0x0006, 0x01, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1209
    }     // library marker kkossev.commonLib, line 1210
    else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1211
        if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1212
            cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "01") // library marker kkossev.commonLib, line 1213
        } // library marker kkossev.commonLib, line 1214
        else { // library marker kkossev.commonLib, line 1215
            cmds = zigbee.command(0xEF00, 0x0, "00010101000101") // library marker kkossev.commonLib, line 1216
        } // library marker kkossev.commonLib, line 1217
    } // library marker kkossev.commonLib, line 1218
    else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1219
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1220
        logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1221
    } // library marker kkossev.commonLib, line 1222
    else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1223
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1224
    } // library marker kkossev.commonLib, line 1225
*/ // library marker kkossev.commonLib, line 1226
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1227
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on' ) { // library marker kkossev.commonLib, line 1228
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1229
        } // library marker kkossev.commonLib, line 1230
        def value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1231
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1232
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1233
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1234
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1235
    } // library marker kkossev.commonLib, line 1236
    else { // library marker kkossev.commonLib, line 1237
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1238
    } // library marker kkossev.commonLib, line 1239

    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1241
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1242
} // library marker kkossev.commonLib, line 1243

def sendSwitchEvent( switchValue ) { // library marker kkossev.commonLib, line 1245
    def value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1246
    def map = [:]  // library marker kkossev.commonLib, line 1247
    boolean bWasChange = false // library marker kkossev.commonLib, line 1248
    boolean debounce   = state.states["debounce"] ?: false // library marker kkossev.commonLib, line 1249
    def lastSwitch = state.states["lastSwitch"] ?: "unknown" // library marker kkossev.commonLib, line 1250
    if (value == lastSwitch && (debounce == true || (settings.ignoreDuplicated ?: false) == true)) {    // some devices send only catchall events, some only readattr reports, but some will fire both... // library marker kkossev.commonLib, line 1251
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1252
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1253
        return null // library marker kkossev.commonLib, line 1254
    } // library marker kkossev.commonLib, line 1255
    else { // library marker kkossev.commonLib, line 1256
        logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1257
    } // library marker kkossev.commonLib, line 1258
    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1259
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1260
    if (lastSwitch != value ) { // library marker kkossev.commonLib, line 1261
        bWasChange = true // library marker kkossev.commonLib, line 1262
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1263
        state.states["debounce"]   = true // library marker kkossev.commonLib, line 1264
        state.states["lastSwitch"] = value // library marker kkossev.commonLib, line 1265
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])         // library marker kkossev.commonLib, line 1266
    } // library marker kkossev.commonLib, line 1267
    else { // library marker kkossev.commonLib, line 1268
        state.states["debounce"] = true // library marker kkossev.commonLib, line 1269
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])      // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271

    map.name = "switch" // library marker kkossev.commonLib, line 1273
    map.value = value // library marker kkossev.commonLib, line 1274
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1275
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1276
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1277
        map.isStateChange = true // library marker kkossev.commonLib, line 1278
    } // library marker kkossev.commonLib, line 1279
    else { // library marker kkossev.commonLib, line 1280
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1281
    } // library marker kkossev.commonLib, line 1282
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1283
    sendEvent(map) // library marker kkossev.commonLib, line 1284
    clearIsDigital() // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

@Field static final Map powerOnBehaviourOptions = [    // library marker kkossev.commonLib, line 1288
    '0': 'switch off', // library marker kkossev.commonLib, line 1289
    '1': 'switch on', // library marker kkossev.commonLib, line 1290
    '2': 'switch last state' // library marker kkossev.commonLib, line 1291
] // library marker kkossev.commonLib, line 1292

@Field static final Map switchTypeOptions = [    // library marker kkossev.commonLib, line 1294
    '0': 'toggle', // library marker kkossev.commonLib, line 1295
    '1': 'state', // library marker kkossev.commonLib, line 1296
    '2': 'momentary' // library marker kkossev.commonLib, line 1297
] // library marker kkossev.commonLib, line 1298

Map myParseDescriptionAsMap( String description ) // library marker kkossev.commonLib, line 1300
{ // library marker kkossev.commonLib, line 1301
    def descMap = [:] // library marker kkossev.commonLib, line 1302
    try { // library marker kkossev.commonLib, line 1303
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1304
    } // library marker kkossev.commonLib, line 1305
    catch (e1) { // library marker kkossev.commonLib, line 1306
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1307
        // try alternative custom parsing // library marker kkossev.commonLib, line 1308
        descMap = [:] // library marker kkossev.commonLib, line 1309
        try { // library marker kkossev.commonLib, line 1310
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1311
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1312
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1313
            }         // library marker kkossev.commonLib, line 1314
        } // library marker kkossev.commonLib, line 1315
        catch (e2) { // library marker kkossev.commonLib, line 1316
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1317
            return [:] // library marker kkossev.commonLib, line 1318
        } // library marker kkossev.commonLib, line 1319
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1320
    } // library marker kkossev.commonLib, line 1321
    return descMap // library marker kkossev.commonLib, line 1322
} // library marker kkossev.commonLib, line 1323

boolean isTuyaE00xCluster( String description ) // library marker kkossev.commonLib, line 1325
{ // library marker kkossev.commonLib, line 1326
    if(description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1327
        return false  // library marker kkossev.commonLib, line 1328
    } // library marker kkossev.commonLib, line 1329
    // try to parse ... // library marker kkossev.commonLib, line 1330
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1331
    def descMap = [:] // library marker kkossev.commonLib, line 1332
    try { // library marker kkossev.commonLib, line 1333
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1334
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1335
    } // library marker kkossev.commonLib, line 1336
    catch ( e ) { // library marker kkossev.commonLib, line 1337
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1338
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1339
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1340
        return true // library marker kkossev.commonLib, line 1341
    } // library marker kkossev.commonLib, line 1342

    if (descMap.cluster == "E000" && descMap.attrId in ["D001", "D002", "D003"]) { // library marker kkossev.commonLib, line 1344
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1345
    } // library marker kkossev.commonLib, line 1346
    else if (descMap.cluster == "E001" && descMap.attrId == "D010") { // library marker kkossev.commonLib, line 1347
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1348
    } // library marker kkossev.commonLib, line 1349
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") { // library marker kkossev.commonLib, line 1350
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1351
    } // library marker kkossev.commonLib, line 1352
    else { // library marker kkossev.commonLib, line 1353
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1354
        return false  // library marker kkossev.commonLib, line 1355
    } // library marker kkossev.commonLib, line 1356
    return true    // processed // library marker kkossev.commonLib, line 1357
} // library marker kkossev.commonLib, line 1358

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1360
boolean otherTuyaOddities( String description ) { // library marker kkossev.commonLib, line 1361
  /* // library marker kkossev.commonLib, line 1362
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1363
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4  // library marker kkossev.commonLib, line 1364
        return true // library marker kkossev.commonLib, line 1365
    } // library marker kkossev.commonLib, line 1366
*/ // library marker kkossev.commonLib, line 1367
    def descMap = [:] // library marker kkossev.commonLib, line 1368
    try { // library marker kkossev.commonLib, line 1369
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1370
    } // library marker kkossev.commonLib, line 1371
    catch (e1) { // library marker kkossev.commonLib, line 1372
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1373
        // try alternative custom parsing // library marker kkossev.commonLib, line 1374
        descMap = [:] // library marker kkossev.commonLib, line 1375
        try { // library marker kkossev.commonLib, line 1376
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1377
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1378
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1379
            }         // library marker kkossev.commonLib, line 1380
        } // library marker kkossev.commonLib, line 1381
        catch (e2) { // library marker kkossev.commonLib, line 1382
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1383
            return true // library marker kkossev.commonLib, line 1384
        } // library marker kkossev.commonLib, line 1385
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1386
    } // library marker kkossev.commonLib, line 1387
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}         // library marker kkossev.commonLib, line 1388
    if (descMap.attrId == null ) { // library marker kkossev.commonLib, line 1389
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1390
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1391
        return false // library marker kkossev.commonLib, line 1392
    } // library marker kkossev.commonLib, line 1393
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1394
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1395
    // attribute report received // library marker kkossev.commonLib, line 1396
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1397
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1398
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1399
        //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1400
    } // library marker kkossev.commonLib, line 1401
    attrData.each { // library marker kkossev.commonLib, line 1402
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1403
        def map = [:] // library marker kkossev.commonLib, line 1404
        if (it.status == "86") { // library marker kkossev.commonLib, line 1405
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1406
            // TODO - skip parsing? // library marker kkossev.commonLib, line 1407
        } // library marker kkossev.commonLib, line 1408
        switch (it.cluster) { // library marker kkossev.commonLib, line 1409
            case "0000" : // library marker kkossev.commonLib, line 1410
                if (it.attrId in ["FFE0", "FFE1", "FFE2", "FFE4"]) { // library marker kkossev.commonLib, line 1411
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1412
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1413
                } // library marker kkossev.commonLib, line 1414
                else if (it.attrId in ["FFFE", "FFDF"]) { // library marker kkossev.commonLib, line 1415
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1416
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1417
                } // library marker kkossev.commonLib, line 1418
                else { // library marker kkossev.commonLib, line 1419
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1420
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1421
                } // library marker kkossev.commonLib, line 1422
                break // library marker kkossev.commonLib, line 1423
            default : // library marker kkossev.commonLib, line 1424
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1425
                break // library marker kkossev.commonLib, line 1426
        } // switch // library marker kkossev.commonLib, line 1427
    } // for each attribute // library marker kkossev.commonLib, line 1428
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1429
} // library marker kkossev.commonLib, line 1430

private boolean isCircuitBreaker()      { device.getDataValue("manufacturer") in ["_TZ3000_ky0fq4ho"] } // library marker kkossev.commonLib, line 1432
private boolean isRTXCircuitBreaker()   { device.getDataValue("manufacturer") in ["_TZE200_abatw3kj"] } // library marker kkossev.commonLib, line 1433

def parseOnOffAttributes( it ) { // library marker kkossev.commonLib, line 1435
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1436
    def mode // library marker kkossev.commonLib, line 1437
    def attrName // library marker kkossev.commonLib, line 1438
    if (it.value == null) { // library marker kkossev.commonLib, line 1439
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1440
        return // library marker kkossev.commonLib, line 1441
    } // library marker kkossev.commonLib, line 1442
    def value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1443
    switch (it.attrId) { // library marker kkossev.commonLib, line 1444
        case "4000" :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1445
            attrName = "Global Scene Control" // library marker kkossev.commonLib, line 1446
            mode = value == 0 ? "off" : value == 1 ? "on" : null // library marker kkossev.commonLib, line 1447
            break // library marker kkossev.commonLib, line 1448
        case "4001" :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1449
            attrName = "On Time" // library marker kkossev.commonLib, line 1450
            mode = value // library marker kkossev.commonLib, line 1451
            break // library marker kkossev.commonLib, line 1452
        case "4002" :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1453
            attrName = "Off Wait Time" // library marker kkossev.commonLib, line 1454
            mode = value // library marker kkossev.commonLib, line 1455
            break // library marker kkossev.commonLib, line 1456
        case "4003" :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1  // library marker kkossev.commonLib, line 1457
            attrName = "Power On State" // library marker kkossev.commonLib, line 1458
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : "UNKNOWN" // library marker kkossev.commonLib, line 1459
            break // library marker kkossev.commonLib, line 1460
        case "8000" :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1461
            attrName = "Child Lock" // library marker kkossev.commonLib, line 1462
            mode = value == 0 ? "off" : "on" // library marker kkossev.commonLib, line 1463
            break // library marker kkossev.commonLib, line 1464
        case "8001" :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1465
            attrName = "LED mode" // library marker kkossev.commonLib, line 1466
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1467
                mode = value == 0 ? "Always Green" : value == 1 ? "Red when On; Green when Off" : value == 2 ? "Green when On; Red when Off" : value == 3 ? "Always Red" : null // library marker kkossev.commonLib, line 1468
            } // library marker kkossev.commonLib, line 1469
            else { // library marker kkossev.commonLib, line 1470
                mode = value == 0 ? "Disabled"  : value == 1 ? "Lit when On" : value == 2 ? "Lit when Off" : value == 3 ? "Freeze": null // library marker kkossev.commonLib, line 1471
            } // library marker kkossev.commonLib, line 1472
            break // library marker kkossev.commonLib, line 1473
        case "8002" :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1474
            attrName = "Power On State" // library marker kkossev.commonLib, line 1475
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : null // library marker kkossev.commonLib, line 1476
            break // library marker kkossev.commonLib, line 1477
        case "8003" : //  Over current alarm // library marker kkossev.commonLib, line 1478
            attrName = "Over current alarm" // library marker kkossev.commonLib, line 1479
            mode = value == 0 ? "Over Current OK" : value == 1 ? "Over Current Alarm" : null // library marker kkossev.commonLib, line 1480
            break // library marker kkossev.commonLib, line 1481
        default : // library marker kkossev.commonLib, line 1482
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1483
            return // library marker kkossev.commonLib, line 1484
    } // library marker kkossev.commonLib, line 1485
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) { // library marker kkossev.commonLib, line 1489
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1490
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"} // library marker kkossev.commonLib, line 1491
    sendEvent(event) // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

def push() {                // Momentary capability // library marker kkossev.commonLib, line 1495
    logDebug "push momentary" // library marker kkossev.commonLib, line 1496
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(); return }     // library marker kkossev.commonLib, line 1497
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

def push(buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1501
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(buttonNumber); return }     // library marker kkossev.commonLib, line 1502
    sendButtonEvent(buttonNumber, "pushed", isDigital=true) // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

def doubleTap(buttonNumber) { // library marker kkossev.commonLib, line 1506
    sendButtonEvent(buttonNumber, "doubleTapped", isDigital=true) // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

def hold(buttonNumber) { // library marker kkossev.commonLib, line 1510
    sendButtonEvent(buttonNumber, "held", isDigital=true) // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

def release(buttonNumber) { // library marker kkossev.commonLib, line 1514
    sendButtonEvent(buttonNumber, "released", isDigital=true) // library marker kkossev.commonLib, line 1515
} // library marker kkossev.commonLib, line 1516

void sendNumberOfButtonsEvent(numberOfButtons) { // library marker kkossev.commonLib, line 1518
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1519
} // library marker kkossev.commonLib, line 1520

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1522
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1523
} // library marker kkossev.commonLib, line 1524


/* // library marker kkossev.commonLib, line 1527
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1528
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1529
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1530
*/ // library marker kkossev.commonLib, line 1531
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1532
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1533
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1534
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1535
    } // library marker kkossev.commonLib, line 1536
    else if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1537
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1538
    } // library marker kkossev.commonLib, line 1539
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1540
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1541
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1542
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
    else { // library marker kkossev.commonLib, line 1545
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1546
    } // library marker kkossev.commonLib, line 1547
} // library marker kkossev.commonLib, line 1548


def sendLevelControlEvent( rawValue ) { // library marker kkossev.commonLib, line 1551
    def value = rawValue as int // library marker kkossev.commonLib, line 1552
    if (value <0) value = 0 // library marker kkossev.commonLib, line 1553
    if (value >100) value = 100 // library marker kkossev.commonLib, line 1554
    def map = [:]  // library marker kkossev.commonLib, line 1555

    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1557
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1558

    map.name = "level" // library marker kkossev.commonLib, line 1560
    map.value = value // library marker kkossev.commonLib, line 1561
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1562
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1563
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1564
        map.isStateChange = true // library marker kkossev.commonLib, line 1565
    } // library marker kkossev.commonLib, line 1566
    else { // library marker kkossev.commonLib, line 1567
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1568
    } // library marker kkossev.commonLib, line 1569
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1570
    sendEvent(map) // library marker kkossev.commonLib, line 1571
    clearIsDigital() // library marker kkossev.commonLib, line 1572
} // library marker kkossev.commonLib, line 1573

/** // library marker kkossev.commonLib, line 1575
 * Get the level transition rate // library marker kkossev.commonLib, line 1576
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1577
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1578
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1579
 */ // library marker kkossev.commonLib, line 1580
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1581
    int rate = 0 // library marker kkossev.commonLib, line 1582
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1583
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1584
    if (!isOn) { // library marker kkossev.commonLib, line 1585
        currentLevel = 0 // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1588
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1589
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1590
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1591
    } else { // library marker kkossev.commonLib, line 1592
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1593
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1594
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1595
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1596
        } // library marker kkossev.commonLib, line 1597
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1598
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1599
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1600
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1601
        } // library marker kkossev.commonLib, line 1602
    } // library marker kkossev.commonLib, line 1603
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1604
    return rate // library marker kkossev.commonLib, line 1605
} // library marker kkossev.commonLib, line 1606

// Command option that enable changes when off // library marker kkossev.commonLib, line 1608
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1609

/** // library marker kkossev.commonLib, line 1611
 * Constrain a value to a range // library marker kkossev.commonLib, line 1612
 * @param value value to constrain // library marker kkossev.commonLib, line 1613
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1614
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1615
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1616
 */ // library marker kkossev.commonLib, line 1617
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1618
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1619
        return value // library marker kkossev.commonLib, line 1620
    } // library marker kkossev.commonLib, line 1621
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1622
} // library marker kkossev.commonLib, line 1623

/** // library marker kkossev.commonLib, line 1625
 * Constrain a value to a range // library marker kkossev.commonLib, line 1626
 * @param value value to constrain // library marker kkossev.commonLib, line 1627
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1628
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1629
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1630
 */ // library marker kkossev.commonLib, line 1631
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1632
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1633
        return value as Integer // library marker kkossev.commonLib, line 1634
    } // library marker kkossev.commonLib, line 1635
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1636
} // library marker kkossev.commonLib, line 1637

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1639
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1640

/** // library marker kkossev.commonLib, line 1642
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1643
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1644
 * @param commands commands to execute // library marker kkossev.commonLib, line 1645
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1646
 */ // library marker kkossev.commonLib, line 1647
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1648
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1649
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1650
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1651
    } // library marker kkossev.commonLib, line 1652
    return [] // library marker kkossev.commonLib, line 1653
} // library marker kkossev.commonLib, line 1654

def intTo16bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1656
    def hexStr = zigbee.convertToHexString(value.toInteger(),4) // library marker kkossev.commonLib, line 1657
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1658
} // library marker kkossev.commonLib, line 1659

def intTo8bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1661
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1662
} // library marker kkossev.commonLib, line 1663

/** // library marker kkossev.commonLib, line 1665
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1666
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1667
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1668
 */ // library marker kkossev.commonLib, line 1669
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1670
    List<String> cmds = [] // library marker kkossev.commonLib, line 1671
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1672
    final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1673
    final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1674
    final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1675
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1676
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1677
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1678
    } // library marker kkossev.commonLib, line 1679
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1680
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1681
    /* // library marker kkossev.commonLib, line 1682
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1683
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1684
    */ // library marker kkossev.commonLib, line 1685
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1686
    String endpointId = "01"     // TODO !!! // library marker kkossev.commonLib, line 1687
     cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1688

    return cmds // library marker kkossev.commonLib, line 1690
} // library marker kkossev.commonLib, line 1691


/** // library marker kkossev.commonLib, line 1694
 * Set Level Command // library marker kkossev.commonLib, line 1695
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1696
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1697
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1698
 */ // library marker kkossev.commonLib, line 1699
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1700
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1701
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1702
    if (DEVICE_TYPE in  ["Bulb"]) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1703
    else { // library marker kkossev.commonLib, line 1704
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1705
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1706
        /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1707
    } // library marker kkossev.commonLib, line 1708
} // library marker kkossev.commonLib, line 1709

/* // library marker kkossev.commonLib, line 1711
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1712
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1713
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1714
*/ // library marker kkossev.commonLib, line 1715
void parseColorControlCluster(final Map descMap, description) { // library marker kkossev.commonLib, line 1716
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1717
    if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1718
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1719
    } // library marker kkossev.commonLib, line 1720
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1721
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1722
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1723
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1724
    } // library marker kkossev.commonLib, line 1725
    else { // library marker kkossev.commonLib, line 1726
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1727
    } // library marker kkossev.commonLib, line 1728
} // library marker kkossev.commonLib, line 1729

/* // library marker kkossev.commonLib, line 1731
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1732
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1733
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1734
*/ // library marker kkossev.commonLib, line 1735
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1736
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1737
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1738
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1739
    def lux = value > 0 ? Math.round(Math.pow(10,(value/10000))) : 0 // library marker kkossev.commonLib, line 1740
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1741
} // library marker kkossev.commonLib, line 1742

void handleIlluminanceEvent( illuminance, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1744
    def eventMap = [:] // library marker kkossev.commonLib, line 1745
    if (state.stats != null) state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1746
    eventMap.name = "illuminance" // library marker kkossev.commonLib, line 1747
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1748
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1749
    eventMap.type = isDigital ? "digital" : "physical" // library marker kkossev.commonLib, line 1750
    eventMap.unit = "lx" // library marker kkossev.commonLib, line 1751
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1752
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1753
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1754
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1755
    Integer lastIllum = device.currentValue("illuminance") ?: 0 // library marker kkossev.commonLib, line 1756
    Integer delta = Math.abs(lastIllum- illumCorrected) // library marker kkossev.commonLib, line 1757
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1758
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1759
        return // library marker kkossev.commonLib, line 1760
    } // library marker kkossev.commonLib, line 1761
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1762
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1763
        unschedule("sendDelayedIllumEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1764
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1765
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1766
    }         // library marker kkossev.commonLib, line 1767
    else {         // queue the event // library marker kkossev.commonLib, line 1768
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1769
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1770
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1771
    } // library marker kkossev.commonLib, line 1772
} // library marker kkossev.commonLib, line 1773

private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1775
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1776
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1777
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1778
} // library marker kkossev.commonLib, line 1779

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1781


/* // library marker kkossev.commonLib, line 1784
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1785
 * temperature // library marker kkossev.commonLib, line 1786
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1787
*/ // library marker kkossev.commonLib, line 1788
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1789
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1790
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1791
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1792
    handleTemperatureEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1793
} // library marker kkossev.commonLib, line 1794

void handleTemperatureEvent( Float temperature, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1796
    def eventMap = [:] // library marker kkossev.commonLib, line 1797
    if (state.stats != null) state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1798
    eventMap.name = "temperature" // library marker kkossev.commonLib, line 1799
    def Scale = location.temperatureScale // library marker kkossev.commonLib, line 1800
    if (Scale == "F") { // library marker kkossev.commonLib, line 1801
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1802
        eventMap.unit = "\u00B0"+"F" // library marker kkossev.commonLib, line 1803
    } // library marker kkossev.commonLib, line 1804
    else { // library marker kkossev.commonLib, line 1805
        eventMap.unit = "\u00B0"+"C" // library marker kkossev.commonLib, line 1806
    } // library marker kkossev.commonLib, line 1807
    def tempCorrected = (temperature + safeToDouble(settings?.temperatureOffset ?: 0)) as Float // library marker kkossev.commonLib, line 1808
    eventMap.value  =  (Math.round(tempCorrected * 10) / 10.0) as Float // library marker kkossev.commonLib, line 1809
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1810
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1811
    if (state.states["isRefresh"] == true) { // library marker kkossev.commonLib, line 1812
        eventMap.descriptionText += " [refresh]" // library marker kkossev.commonLib, line 1813
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1814
    }    // library marker kkossev.commonLib, line 1815
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1816
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1817
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1818
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1819
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1820
        unschedule("sendDelayedTempEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1821
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1822
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1823
    }         // library marker kkossev.commonLib, line 1824
    else {         // queue the event // library marker kkossev.commonLib, line 1825
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1826
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1827
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1828
    } // library marker kkossev.commonLib, line 1829
} // library marker kkossev.commonLib, line 1830

private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1832
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1833
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1834
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1835
} // library marker kkossev.commonLib, line 1836

/* // library marker kkossev.commonLib, line 1838
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1839
 * humidity // library marker kkossev.commonLib, line 1840
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1841
*/ // library marker kkossev.commonLib, line 1842
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1843
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1844
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1845
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1846
    handleHumidityEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1847
} // library marker kkossev.commonLib, line 1848

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1850
    def eventMap = [:] // library marker kkossev.commonLib, line 1851
    if (state.stats != null) state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1852
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1853
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) { // library marker kkossev.commonLib, line 1854
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})" // library marker kkossev.commonLib, line 1855
        return // library marker kkossev.commonLib, line 1856
    } // library marker kkossev.commonLib, line 1857
    eventMap.value = Math.round(humidityAsDouble) // library marker kkossev.commonLib, line 1858
    eventMap.name = "humidity" // library marker kkossev.commonLib, line 1859
    eventMap.unit = "% RH" // library marker kkossev.commonLib, line 1860
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1861
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1862
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}" // library marker kkossev.commonLib, line 1863
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1864
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1865
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1866
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1867
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1868
        unschedule("sendDelayedHumidityEvent") // library marker kkossev.commonLib, line 1869
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1870
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1871
    } // library marker kkossev.commonLib, line 1872
    else { // library marker kkossev.commonLib, line 1873
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1874
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1875
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1876
    } // library marker kkossev.commonLib, line 1877
} // library marker kkossev.commonLib, line 1878

private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1880
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1881
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1882
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1883
} // library marker kkossev.commonLib, line 1884

/* // library marker kkossev.commonLib, line 1886
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1887
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1888
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1889
*/ // library marker kkossev.commonLib, line 1890

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1892
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1893
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1894
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1895
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1896
        parseElectricalMeasureClusterSwitch(descMap) // library marker kkossev.commonLib, line 1897
    } // library marker kkossev.commonLib, line 1898
    else { // library marker kkossev.commonLib, line 1899
        logWarn "parseElectricalMeasureCluster is NOT implemented1" // library marker kkossev.commonLib, line 1900
    } // library marker kkossev.commonLib, line 1901
} // library marker kkossev.commonLib, line 1902

/* // library marker kkossev.commonLib, line 1904
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1905
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1906
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1907
*/ // library marker kkossev.commonLib, line 1908

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1910
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1911
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1912
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1913
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1914
        parseMeteringClusterSwitch(descMap) // library marker kkossev.commonLib, line 1915
    } // library marker kkossev.commonLib, line 1916
    else { // library marker kkossev.commonLib, line 1917
        logWarn "parseMeteringCluster is NOT implemented1" // library marker kkossev.commonLib, line 1918
    } // library marker kkossev.commonLib, line 1919
} // library marker kkossev.commonLib, line 1920


/* // library marker kkossev.commonLib, line 1923
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1924
 * pm2.5 // library marker kkossev.commonLib, line 1925
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1926
*/ // library marker kkossev.commonLib, line 1927
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1928
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1929
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1930
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1931
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1932
    //logDebug "pm25 float value = ${floatValue}" // library marker kkossev.commonLib, line 1933
    handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1934
} // library marker kkossev.commonLib, line 1935

void handlePm25Event( Integer pm25, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1937
    def eventMap = [:] // library marker kkossev.commonLib, line 1938
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1939
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0) // library marker kkossev.commonLib, line 1940
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) { // library marker kkossev.commonLib, line 1941
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})" // library marker kkossev.commonLib, line 1942
        return // library marker kkossev.commonLib, line 1943
    } // library marker kkossev.commonLib, line 1944
    eventMap.value = Math.round(pm25AsDouble) // library marker kkossev.commonLib, line 1945
    eventMap.name = "pm25" // library marker kkossev.commonLib, line 1946
    eventMap.unit = "\u03BCg/m3"    //"mg/m3" // library marker kkossev.commonLib, line 1947
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1948
    eventMap.isStateChange = true // library marker kkossev.commonLib, line 1949
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}" // library marker kkossev.commonLib, line 1950
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000) // library marker kkossev.commonLib, line 1951
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1952
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1953
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1954
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1955
        unschedule("sendDelayedPm25Event") // library marker kkossev.commonLib, line 1956
        state.lastRx['pm25Time'] = now() // library marker kkossev.commonLib, line 1957
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1958
    } // library marker kkossev.commonLib, line 1959
    else { // library marker kkossev.commonLib, line 1960
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1961
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1962
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1963
    } // library marker kkossev.commonLib, line 1964
} // library marker kkossev.commonLib, line 1965

private void sendDelayedPm25Event(Map eventMap) { // library marker kkossev.commonLib, line 1967
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1968
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1969
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1970
} // library marker kkossev.commonLib, line 1971

/* // library marker kkossev.commonLib, line 1973
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1974
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1975
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1976
*/ // library marker kkossev.commonLib, line 1977
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1978
    if (DEVICE_TYPE in ["AirQuality"]) { // library marker kkossev.commonLib, line 1979
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1980
    } // library marker kkossev.commonLib, line 1981
    else if (DEVICE_TYPE in ["AqaraCube"]) { // library marker kkossev.commonLib, line 1982
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1983
    } // library marker kkossev.commonLib, line 1984
    else { // library marker kkossev.commonLib, line 1985
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1986
    } // library marker kkossev.commonLib, line 1987
} // library marker kkossev.commonLib, line 1988


/* // library marker kkossev.commonLib, line 1991
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1992
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1993
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1994
*/ // library marker kkossev.commonLib, line 1995

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1997
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1998
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1999
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 2000
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 2001
    if (DEVICE_TYPE in  ["AqaraCube"]) { // library marker kkossev.commonLib, line 2002
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 2003
    } // library marker kkossev.commonLib, line 2004
    else { // library marker kkossev.commonLib, line 2005
        handleMultistateInputEvent(value as Integer) // library marker kkossev.commonLib, line 2006
    } // library marker kkossev.commonLib, line 2007
} // library marker kkossev.commonLib, line 2008

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 2010
    def eventMap = [:] // library marker kkossev.commonLib, line 2011
    eventMap.value = value // library marker kkossev.commonLib, line 2012
    eventMap.name = "multistateInput" // library marker kkossev.commonLib, line 2013
    eventMap.unit = "" // library marker kkossev.commonLib, line 2014
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 2015
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 2016
    sendEvent(eventMap) // library marker kkossev.commonLib, line 2017
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 2018
} // library marker kkossev.commonLib, line 2019

/* // library marker kkossev.commonLib, line 2021
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2022
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 2023
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2024
*/ // library marker kkossev.commonLib, line 2025

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 2027
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2028
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 2029
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 2030
    } // library marker kkossev.commonLib, line 2031
    else { // library marker kkossev.commonLib, line 2032
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2033
    } // library marker kkossev.commonLib, line 2034
} // library marker kkossev.commonLib, line 2035

/* // library marker kkossev.commonLib, line 2037
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2038
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 2039
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2040
*/ // library marker kkossev.commonLib, line 2041
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 2042
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2043
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.commonLib, line 2044
        parseThermostatClusterThermostat(descMap) // library marker kkossev.commonLib, line 2045
    } // library marker kkossev.commonLib, line 2046
    else { // library marker kkossev.commonLib, line 2047
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2048
    } // library marker kkossev.commonLib, line 2049
} // library marker kkossev.commonLib, line 2050



// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2054

def parseE002Cluster( descMap ) { // library marker kkossev.commonLib, line 2056
    if (DEVICE_TYPE in ["Radar"])     { parseE002ClusterRadar(descMap) }     // library marker kkossev.commonLib, line 2057
    else { // library marker kkossev.commonLib, line 2058
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2059
    } // library marker kkossev.commonLib, line 2060
} // library marker kkossev.commonLib, line 2061


/* // library marker kkossev.commonLib, line 2064
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2065
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2066
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2067
*/ // library marker kkossev.commonLib, line 2068
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2069
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2070
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2071

// Tuya Commands // library marker kkossev.commonLib, line 2073
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2074
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2075
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2076
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2077
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2078

// tuya DP type // library marker kkossev.commonLib, line 2080
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ] // library marker kkossev.commonLib, line 2081
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ] // library marker kkossev.commonLib, line 2082
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2083
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ] // library marker kkossev.commonLib, line 2084
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ] // library marker kkossev.commonLib, line 2085
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2086


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2089
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME // library marker kkossev.commonLib, line 2090
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2091
        def offset = 0 // library marker kkossev.commonLib, line 2092
        try { // library marker kkossev.commonLib, line 2093
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2094
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}" // library marker kkossev.commonLib, line 2095
        } // library marker kkossev.commonLib, line 2096
        catch(e) { // library marker kkossev.commonLib, line 2097
            logWarn "cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 2098
        } // library marker kkossev.commonLib, line 2099
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8)) // library marker kkossev.commonLib, line 2100
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2101
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2102
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2103
    } // library marker kkossev.commonLib, line 2104
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2105
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2106
        def status = descMap?.data[1]             // library marker kkossev.commonLib, line 2107
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2108
        if (status != "00") { // library marker kkossev.commonLib, line 2109
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                 // library marker kkossev.commonLib, line 2110
        } // library marker kkossev.commonLib, line 2111
    }  // library marker kkossev.commonLib, line 2112
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02" || descMap?.command == "05" || descMap?.command == "06")) // library marker kkossev.commonLib, line 2113
    { // library marker kkossev.commonLib, line 2114
        def dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2115
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2116
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2117
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2118
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2119
            return // library marker kkossev.commonLib, line 2120
        } // library marker kkossev.commonLib, line 2121
        for (int i = 0; i < (dataLen-4); ) { // library marker kkossev.commonLib, line 2122
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2123
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2124
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i])  // library marker kkossev.commonLib, line 2125
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2126
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2127
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2128
            i = i + fncmd_len + 4; // library marker kkossev.commonLib, line 2129
        } // library marker kkossev.commonLib, line 2130
    } // library marker kkossev.commonLib, line 2131
    else { // library marker kkossev.commonLib, line 2132
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2133
    } // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

void processTuyaDP(descMap, dp, dp_id, fncmd, dp_len=0) { // library marker kkossev.commonLib, line 2137
    if (DEVICE_TYPE in ["Radar"])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2138
    if (DEVICE_TYPE in ["Fingerbot"])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2139
    // check if the method  method exists // library marker kkossev.commonLib, line 2140
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2141
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0  // library marker kkossev.commonLib, line 2142
            return // library marker kkossev.commonLib, line 2143
        }     // library marker kkossev.commonLib, line 2144
    } // library marker kkossev.commonLib, line 2145
    switch (dp) { // library marker kkossev.commonLib, line 2146
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2147
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2148
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2149
            } // library marker kkossev.commonLib, line 2150
            else { // library marker kkossev.commonLib, line 2151
                sendSwitchEvent(fncmd) // library marker kkossev.commonLib, line 2152
            } // library marker kkossev.commonLib, line 2153
            break // library marker kkossev.commonLib, line 2154
        case 0x02 : // library marker kkossev.commonLib, line 2155
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2156
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2157
            } // library marker kkossev.commonLib, line 2158
            else { // library marker kkossev.commonLib, line 2159
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2160
            } // library marker kkossev.commonLib, line 2161
            break // library marker kkossev.commonLib, line 2162
        case 0x04 : // battery // library marker kkossev.commonLib, line 2163
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2164
            break // library marker kkossev.commonLib, line 2165
        default : // library marker kkossev.commonLib, line 2166
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2167
            break             // library marker kkossev.commonLib, line 2168
    } // library marker kkossev.commonLib, line 2169
} // library marker kkossev.commonLib, line 2170

private int getTuyaAttributeValue(ArrayList _data, index) { // library marker kkossev.commonLib, line 2172
    int retValue = 0 // library marker kkossev.commonLib, line 2173

    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2175
        int dataLength = _data[5+index] as Integer // library marker kkossev.commonLib, line 2176
        int power = 1; // library marker kkossev.commonLib, line 2177
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2178
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5]) // library marker kkossev.commonLib, line 2179
            power = power * 256 // library marker kkossev.commonLib, line 2180
        } // library marker kkossev.commonLib, line 2181
    } // library marker kkossev.commonLib, line 2182
    return retValue // library marker kkossev.commonLib, line 2183
} // library marker kkossev.commonLib, line 2184


private sendTuyaCommand(dp, dp_type, fncmd) { // library marker kkossev.commonLib, line 2187
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2188
    def ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2189
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2190
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2191

    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd ) // library marker kkossev.commonLib, line 2193
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2194
    return cmds // library marker kkossev.commonLib, line 2195
} // library marker kkossev.commonLib, line 2196

private getPACKET_ID() { // library marker kkossev.commonLib, line 2198
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)  // library marker kkossev.commonLib, line 2199
} // library marker kkossev.commonLib, line 2200

def tuyaTest( dpCommand, dpValue, dpTypeString ) { // library marker kkossev.commonLib, line 2202
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2203
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2204
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2205

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" // library marker kkossev.commonLib, line 2207

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2209
} // library marker kkossev.commonLib, line 2210

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2212
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2213

def tuyaBlackMagic() { // library marker kkossev.commonLib, line 2215
    def ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2216
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2217
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200) // library marker kkossev.commonLib, line 2218
} // library marker kkossev.commonLib, line 2219

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2221
    List<String> cmds = [] // library marker kkossev.commonLib, line 2222
    if (isAqaraTVOC() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2223
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",] // library marker kkossev.commonLib, line 2224
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2225
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2226
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2227
        if (isAqaraTVOC()) { // library marker kkossev.commonLib, line 2228
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // TVOC only // library marker kkossev.commonLib, line 2229
        } // library marker kkossev.commonLib, line 2230
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2231
        logDebug "sent aqaraBlackMagic()" // library marker kkossev.commonLib, line 2232
    } // library marker kkossev.commonLib, line 2233
    else { // library marker kkossev.commonLib, line 2234
        logDebug "aqaraBlackMagic() was SKIPPED" // library marker kkossev.commonLib, line 2235
    } // library marker kkossev.commonLib, line 2236
} // library marker kkossev.commonLib, line 2237


/** // library marker kkossev.commonLib, line 2240
 * initializes the device // library marker kkossev.commonLib, line 2241
 * Invoked from configure() // library marker kkossev.commonLib, line 2242
 * @return zigbee commands // library marker kkossev.commonLib, line 2243
 */ // library marker kkossev.commonLib, line 2244
def initializeDevice() { // library marker kkossev.commonLib, line 2245
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2246
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2247

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2249
    if (DEVICE_TYPE in  ["AirQuality"])          { return initializeDeviceAirQuality() } // library marker kkossev.commonLib, line 2250
    else if (DEVICE_TYPE in  ["IRBlaster"])      { return initializeDeviceIrBlaster() } // library marker kkossev.commonLib, line 2251
    else if (DEVICE_TYPE in  ["Radar"])          { return initializeDeviceRadar() } // library marker kkossev.commonLib, line 2252
    else if (DEVICE_TYPE in  ["ButtonDimmer"])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2253


    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2256
    if (DEVICE_TYPE in  ["THSensor"]) { // library marker kkossev.commonLib, line 2257
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2258
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2259
    } // library marker kkossev.commonLib, line 2260
    // // library marker kkossev.commonLib, line 2261
    if (cmds == []) { // library marker kkossev.commonLib, line 2262
        cmds = ["delay 299"] // library marker kkossev.commonLib, line 2263
    } // library marker kkossev.commonLib, line 2264
    return cmds // library marker kkossev.commonLib, line 2265
} // library marker kkossev.commonLib, line 2266


/** // library marker kkossev.commonLib, line 2269
 * configures the device // library marker kkossev.commonLib, line 2270
 * Invoked from updated() // library marker kkossev.commonLib, line 2271
 * @return zigbee commands // library marker kkossev.commonLib, line 2272
 */ // library marker kkossev.commonLib, line 2273
def configureDevice() { // library marker kkossev.commonLib, line 2274
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2275
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2276

    if (DEVICE_TYPE in  ["AirQuality"]) { cmds += configureDeviceAirQuality() } // library marker kkossev.commonLib, line 2278
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2279
    else if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2280
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() } // library marker kkossev.commonLib, line 2281
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureDeviceIrBlaster() } // library marker kkossev.commonLib, line 2282
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += configureDeviceRadar() } // library marker kkossev.commonLib, line 2283
    else if (DEVICE_TYPE in  ["ButtonDimmer"]) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2284
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2285
    if (cmds == []) {  // library marker kkossev.commonLib, line 2286
        cmds = ["delay 277",] // library marker kkossev.commonLib, line 2287
    } // library marker kkossev.commonLib, line 2288
    sendZigbeeCommands(cmds)   // library marker kkossev.commonLib, line 2289
} // library marker kkossev.commonLib, line 2290

/* // library marker kkossev.commonLib, line 2292
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2293
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2294
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2295
*/ // library marker kkossev.commonLib, line 2296

def refresh() { // library marker kkossev.commonLib, line 2298
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2299
    checkDriverVersion() // library marker kkossev.commonLib, line 2300
    List<String> cmds = [] // library marker kkossev.commonLib, line 2301
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2302

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2304
    if (DEVICE_TYPE in  ["AqaraCube"])       { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2305
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2306
    else if (DEVICE_TYPE in  ["AirQuality"]) { cmds += refreshAirQuality() } // library marker kkossev.commonLib, line 2307
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += refreshSwitch() } // library marker kkossev.commonLib, line 2308
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += refreshIrBlaster() } // library marker kkossev.commonLib, line 2309
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += refreshRadar() } // library marker kkossev.commonLib, line 2310
    else if (DEVICE_TYPE in  ["Thermostat"]) { cmds += refreshThermostat() } // library marker kkossev.commonLib, line 2311
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2312
    else { // library marker kkossev.commonLib, line 2313
        // generic refresh handling, based on teh device capabilities  // library marker kkossev.commonLib, line 2314
        if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 2315
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage // library marker kkossev.commonLib, line 2316
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)         // battery percentage  // library marker kkossev.commonLib, line 2317
        } // library marker kkossev.commonLib, line 2318
        if (DEVICE_TYPE in  ["Plug", "Dimmer"]) { // library marker kkossev.commonLib, line 2319
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200) // library marker kkossev.commonLib, line 2320
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2321
        } // library marker kkossev.commonLib, line 2322
        if (DEVICE_TYPE in  ["Dimmer"]) { // library marker kkossev.commonLib, line 2323
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2324
        } // library marker kkossev.commonLib, line 2325
        if (DEVICE_TYPE in  ["THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 2326
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2327
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2328
        } // library marker kkossev.commonLib, line 2329
    } // library marker kkossev.commonLib, line 2330

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2332
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2333
    } // library marker kkossev.commonLib, line 2334
    else { // library marker kkossev.commonLib, line 2335
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2336
    } // library marker kkossev.commonLib, line 2337
} // library marker kkossev.commonLib, line 2338

def setRefreshRequest()   { if (state.states == null) {state.states = [:]};   state.states["isRefresh"] = true; runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds // library marker kkossev.commonLib, line 2340
def clearRefreshRequest() { if (state.states == null) {state.states = [:] }; state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 2341

void clearInfoEvent() { // library marker kkossev.commonLib, line 2343
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2344
} // library marker kkossev.commonLib, line 2345

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2347
    if (info == null || info == "clear") { // library marker kkossev.commonLib, line 2348
        logDebug "clearing the Status event" // library marker kkossev.commonLib, line 2349
        sendEvent(name: "Status", value: "clear", isDigital: true) // library marker kkossev.commonLib, line 2350
    } // library marker kkossev.commonLib, line 2351
    else { // library marker kkossev.commonLib, line 2352
        logInfo "${info}" // library marker kkossev.commonLib, line 2353
        sendEvent(name: "Status", value: info, isDigital: true) // library marker kkossev.commonLib, line 2354
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2355
    } // library marker kkossev.commonLib, line 2356
} // library marker kkossev.commonLib, line 2357

def ping() { // library marker kkossev.commonLib, line 2359
    if (!(isAqaraTVOC())) { // library marker kkossev.commonLib, line 2360
        if (state.lastTx == nill ) state.lastTx = [:]  // library marker kkossev.commonLib, line 2361
        state.lastTx["pingTime"] = new Date().getTime() // library marker kkossev.commonLib, line 2362
        if (state.states == nill ) state.states = [:]  // library marker kkossev.commonLib, line 2363
        state.states["isPing"] = true // library marker kkossev.commonLib, line 2364
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2365
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2366
        logDebug 'ping...' // library marker kkossev.commonLib, line 2367
    } // library marker kkossev.commonLib, line 2368
    else { // library marker kkossev.commonLib, line 2369
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2370
        logInfo "ping() command is not available for this sleepy device." // library marker kkossev.commonLib, line 2371
        sendRttEvent("n/a") // library marker kkossev.commonLib, line 2372
    } // library marker kkossev.commonLib, line 2373
} // library marker kkossev.commonLib, line 2374

/** // library marker kkossev.commonLib, line 2376
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2377
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2378
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2379
 * @return none // library marker kkossev.commonLib, line 2380
 */ // library marker kkossev.commonLib, line 2381
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2382
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2383
    if (state.lastTx == null ) state.lastTx = [:] // library marker kkossev.commonLib, line 2384
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: now).toInteger() // library marker kkossev.commonLib, line 2385
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats["pingsMin"]} max=${state.stats["pingsMax"]} average=${state.stats["pingsAvg"]})" // library marker kkossev.commonLib, line 2386
    if (value == null) { // library marker kkossev.commonLib, line 2387
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2388
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)     // library marker kkossev.commonLib, line 2389
    } // library marker kkossev.commonLib, line 2390
    else { // library marker kkossev.commonLib, line 2391
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2392
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2393
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)     // library marker kkossev.commonLib, line 2394
    } // library marker kkossev.commonLib, line 2395
} // library marker kkossev.commonLib, line 2396

/** // library marker kkossev.commonLib, line 2398
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2399
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2400
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2401
 */ // library marker kkossev.commonLib, line 2402
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2403
    if (cluster != null) { // library marker kkossev.commonLib, line 2404
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2405
    } // library marker kkossev.commonLib, line 2406
    else { // library marker kkossev.commonLib, line 2407
        logWarn "cluster is NULL!" // library marker kkossev.commonLib, line 2408
        return "NULL" // library marker kkossev.commonLib, line 2409
    } // library marker kkossev.commonLib, line 2410
} // library marker kkossev.commonLib, line 2411

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2413
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2414
} // library marker kkossev.commonLib, line 2415

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2417
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2418
    sendRttEvent("timeout") // library marker kkossev.commonLib, line 2419
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2420
} // library marker kkossev.commonLib, line 2421

/** // library marker kkossev.commonLib, line 2423
 * Schedule a device health check // library marker kkossev.commonLib, line 2424
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2425
 */ // library marker kkossev.commonLib, line 2426
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2427
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2428
        String cron = getCron( intervalMins*60 ) // library marker kkossev.commonLib, line 2429
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2430
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2431
    } // library marker kkossev.commonLib, line 2432
    else { // library marker kkossev.commonLib, line 2433
        logWarn "deviceHealthCheck is not scheduled!" // library marker kkossev.commonLib, line 2434
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2435
    } // library marker kkossev.commonLib, line 2436
} // library marker kkossev.commonLib, line 2437

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2439
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2440
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2441
    logWarn "device health check is disabled!" // library marker kkossev.commonLib, line 2442

} // library marker kkossev.commonLib, line 2444

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2446
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2447
    if(state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2448
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2449
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {    // library marker kkossev.commonLib, line 2450
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2451
        logInfo "is now online!" // library marker kkossev.commonLib, line 2452
    } // library marker kkossev.commonLib, line 2453
} // library marker kkossev.commonLib, line 2454


def deviceHealthCheck() { // library marker kkossev.commonLib, line 2457
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2458
    def ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2459
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2460
        if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2461
            logWarn "not present!" // library marker kkossev.commonLib, line 2462
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2463
        } // library marker kkossev.commonLib, line 2464
    } // library marker kkossev.commonLib, line 2465
    else { // library marker kkossev.commonLib, line 2466
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2467
    } // library marker kkossev.commonLib, line 2468
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2469
} // library marker kkossev.commonLib, line 2470

void sendHealthStatusEvent(value) { // library marker kkossev.commonLib, line 2472
    def descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2473
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2474
    if (value == 'online') { // library marker kkossev.commonLib, line 2475
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2476
    } // library marker kkossev.commonLib, line 2477
    else { // library marker kkossev.commonLib, line 2478
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2479
    } // library marker kkossev.commonLib, line 2480
} // library marker kkossev.commonLib, line 2481



/** // library marker kkossev.commonLib, line 2485
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2486
 */ // library marker kkossev.commonLib, line 2487
void autoPoll() { // library marker kkossev.commonLib, line 2488
    logDebug "autoPoll()..." // library marker kkossev.commonLib, line 2489
    checkDriverVersion() // library marker kkossev.commonLib, line 2490
    List<String> cmds = [] // library marker kkossev.commonLib, line 2491
    if (state.states == null) state.states = [:] // library marker kkossev.commonLib, line 2492
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2493

    if (DEVICE_TYPE in  ["AirQuality"]) { // library marker kkossev.commonLib, line 2495
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2496
    } // library marker kkossev.commonLib, line 2497

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2499
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2500
    }     // library marker kkossev.commonLib, line 2501
} // library marker kkossev.commonLib, line 2502


/** // library marker kkossev.commonLib, line 2505
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2506
 */ // library marker kkossev.commonLib, line 2507
void updated() { // library marker kkossev.commonLib, line 2508
    logInfo 'updated...' // library marker kkossev.commonLib, line 2509
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2510
    unschedule() // library marker kkossev.commonLib, line 2511

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2513
        logTrace settings // library marker kkossev.commonLib, line 2514
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2515
    } // library marker kkossev.commonLib, line 2516
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2517
        logTrace settings // library marker kkossev.commonLib, line 2518
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2519
    }     // library marker kkossev.commonLib, line 2520

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2522
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2523
        // schedule the periodic timer // library marker kkossev.commonLib, line 2524
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2525
        if (interval > 0) { // library marker kkossev.commonLib, line 2526
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2527
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2528
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2529
        } // library marker kkossev.commonLib, line 2530
    } // library marker kkossev.commonLib, line 2531
    else { // library marker kkossev.commonLib, line 2532
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2533
        log.info "Health Check is disabled!" // library marker kkossev.commonLib, line 2534
    } // library marker kkossev.commonLib, line 2535

    if (DEVICE_TYPE in ["AirQuality"])  { updatedAirQuality() } // library marker kkossev.commonLib, line 2537
    if (DEVICE_TYPE in ["IRBlaster"])   { updatedIrBlaster() } // library marker kkossev.commonLib, line 2538
    if (DEVICE_TYPE in ["Thermostat"])  { updatedThermostat() } // library marker kkossev.commonLib, line 2539

    //configureDevice()    // sends Zigbee commands  // commented out 11/18/2023 // library marker kkossev.commonLib, line 2541

    sendInfoEvent("updated") // library marker kkossev.commonLib, line 2543
} // library marker kkossev.commonLib, line 2544

/** // library marker kkossev.commonLib, line 2546
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2547
 */ // library marker kkossev.commonLib, line 2548
void logsOff() { // library marker kkossev.commonLib, line 2549
    logInfo "debug logging disabled..." // library marker kkossev.commonLib, line 2550
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2551
} // library marker kkossev.commonLib, line 2552
void traceOff() { // library marker kkossev.commonLib, line 2553
    logInfo "trace logging disabled..." // library marker kkossev.commonLib, line 2554
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2555
} // library marker kkossev.commonLib, line 2556

def configure(command) { // library marker kkossev.commonLib, line 2558
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2559
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2560

    Boolean validated = false // library marker kkossev.commonLib, line 2562
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2563
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2564
        return // library marker kkossev.commonLib, line 2565
    } // library marker kkossev.commonLib, line 2566
    // // library marker kkossev.commonLib, line 2567
    def func // library marker kkossev.commonLib, line 2568
   // try { // library marker kkossev.commonLib, line 2569
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2570
        cmds = "$func"() // library marker kkossev.commonLib, line 2571
 //   } // library marker kkossev.commonLib, line 2572
//    catch (e) { // library marker kkossev.commonLib, line 2573
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2574
//        return // library marker kkossev.commonLib, line 2575
//    } // library marker kkossev.commonLib, line 2576

    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2578
} // library marker kkossev.commonLib, line 2579

def configureHelp( val ) { // library marker kkossev.commonLib, line 2581
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2582
} // library marker kkossev.commonLib, line 2583

def loadAllDefaults() { // library marker kkossev.commonLib, line 2585
    logWarn "loadAllDefaults() !!!" // library marker kkossev.commonLib, line 2586
    deleteAllSettings() // library marker kkossev.commonLib, line 2587
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2588
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2589
    deleteAllStates() // library marker kkossev.commonLib, line 2590
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2591
    initialize() // library marker kkossev.commonLib, line 2592
    configure() // library marker kkossev.commonLib, line 2593
    updated() // calls  also   configureDevice() // library marker kkossev.commonLib, line 2594
    sendInfoEvent("All Defaults Loaded! F5 to refresh") // library marker kkossev.commonLib, line 2595
} // library marker kkossev.commonLib, line 2596

/** // library marker kkossev.commonLib, line 2598
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2599
 * Invoked when device is first installed and when the user updates the configuration // library marker kkossev.commonLib, line 2600
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2601
 */ // library marker kkossev.commonLib, line 2602
def configure() { // library marker kkossev.commonLib, line 2603
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2604
    logInfo 'configure...' // library marker kkossev.commonLib, line 2605
    logDebug settings // library marker kkossev.commonLib, line 2606
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2607
    if (isAqaraTVOC() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2608
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2609
    } // library marker kkossev.commonLib, line 2610
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2611
    cmds += configureDevice() // library marker kkossev.commonLib, line 2612
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2613
    sendInfoEvent("sent device configuration") // library marker kkossev.commonLib, line 2614
} // library marker kkossev.commonLib, line 2615

/** // library marker kkossev.commonLib, line 2617
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2618
 */ // library marker kkossev.commonLib, line 2619
void installed() { // library marker kkossev.commonLib, line 2620
    logInfo 'installed...' // library marker kkossev.commonLib, line 2621
    // populate some default values for attributes // library marker kkossev.commonLib, line 2622
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2623
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2624
    sendInfoEvent("installed") // library marker kkossev.commonLib, line 2625
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2626
} // library marker kkossev.commonLib, line 2627

/** // library marker kkossev.commonLib, line 2629
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2630
 */ // library marker kkossev.commonLib, line 2631
void initialize() { // library marker kkossev.commonLib, line 2632
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2633
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2634
    updateTuyaVersion() // library marker kkossev.commonLib, line 2635
    updateAqaraVersion() // library marker kkossev.commonLib, line 2636
} // library marker kkossev.commonLib, line 2637


/* // library marker kkossev.commonLib, line 2640
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2641
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2642
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2643
*/ // library marker kkossev.commonLib, line 2644

static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2646
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2647
} // library marker kkossev.commonLib, line 2648

static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2650
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2651
} // library marker kkossev.commonLib, line 2652

void sendZigbeeCommands(ArrayList<String> cmd) { // library marker kkossev.commonLib, line 2654
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2655
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2656
    cmd.each { // library marker kkossev.commonLib, line 2657
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2658
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] } // library marker kkossev.commonLib, line 2659
    } // library marker kkossev.commonLib, line 2660
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2661
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2662
} // library marker kkossev.commonLib, line 2663

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!) " : " ") + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString}) "} // library marker kkossev.commonLib, line 2665

def getDeviceInfo() { // library marker kkossev.commonLib, line 2667
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2668
} // library marker kkossev.commonLib, line 2669

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2671
    return state.destinationEP ?: device.endpointId ?: "01" // library marker kkossev.commonLib, line 2672
} // library marker kkossev.commonLib, line 2673

def checkDriverVersion() { // library marker kkossev.commonLib, line 2675
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2676
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2677
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2678
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2679
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2680
        updateTuyaVersion() // library marker kkossev.commonLib, line 2681
        updateAqaraVersion() // library marker kkossev.commonLib, line 2682
    } // library marker kkossev.commonLib, line 2683
    else { // library marker kkossev.commonLib, line 2684
        // no driver version change // library marker kkossev.commonLib, line 2685
    } // library marker kkossev.commonLib, line 2686
} // library marker kkossev.commonLib, line 2687

// credits @thebearmay // library marker kkossev.commonLib, line 2689
String getModel(){ // library marker kkossev.commonLib, line 2690
    try{ // library marker kkossev.commonLib, line 2691
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2692
    } catch (ignore){ // library marker kkossev.commonLib, line 2693
        try{ // library marker kkossev.commonLib, line 2694
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2695
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2696
            return model // library marker kkossev.commonLib, line 2697
            }         // library marker kkossev.commonLib, line 2698
        } catch(ignore_again) { // library marker kkossev.commonLib, line 2699
            return "" // library marker kkossev.commonLib, line 2700
        } // library marker kkossev.commonLib, line 2701
    } // library marker kkossev.commonLib, line 2702
} // library marker kkossev.commonLib, line 2703

// credits @thebearmay // library marker kkossev.commonLib, line 2705
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2706
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2707
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2708
    String revision = tokens.last() // library marker kkossev.commonLib, line 2709
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2710
} // library marker kkossev.commonLib, line 2711

/** // library marker kkossev.commonLib, line 2713
 * called from TODO // library marker kkossev.commonLib, line 2714
 *  // library marker kkossev.commonLib, line 2715
 */ // library marker kkossev.commonLib, line 2716

def deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2718
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2719
    unschedule() // library marker kkossev.commonLib, line 2720
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2721
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2722

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2724
} // library marker kkossev.commonLib, line 2725


def resetStatistics() { // library marker kkossev.commonLib, line 2728
    runIn(1, "resetStats") // library marker kkossev.commonLib, line 2729
    sendInfoEvent("Statistics are reset. Refresh the web page") // library marker kkossev.commonLib, line 2730
} // library marker kkossev.commonLib, line 2731

/** // library marker kkossev.commonLib, line 2733
 * called from TODO // library marker kkossev.commonLib, line 2734
 *  // library marker kkossev.commonLib, line 2735
 */ // library marker kkossev.commonLib, line 2736
def resetStats() { // library marker kkossev.commonLib, line 2737
    logDebug "resetStats..." // library marker kkossev.commonLib, line 2738
    state.stats = [:] // library marker kkossev.commonLib, line 2739
    state.states = [:] // library marker kkossev.commonLib, line 2740
    state.lastRx = [:] // library marker kkossev.commonLib, line 2741
    state.lastTx = [:] // library marker kkossev.commonLib, line 2742
    state.health = [:] // library marker kkossev.commonLib, line 2743
    state.zigbeeGroups = [:]  // library marker kkossev.commonLib, line 2744
    state.stats["rxCtr"] = 0 // library marker kkossev.commonLib, line 2745
    state.stats["txCtr"] = 0 // library marker kkossev.commonLib, line 2746
    state.states["isDigital"] = false // library marker kkossev.commonLib, line 2747
    state.states["isRefresh"] = false // library marker kkossev.commonLib, line 2748
    state.health["offlineCtr"] = 0 // library marker kkossev.commonLib, line 2749
    state.health["checkCtr3"] = 0 // library marker kkossev.commonLib, line 2750
} // library marker kkossev.commonLib, line 2751

/** // library marker kkossev.commonLib, line 2753
 * called from TODO // library marker kkossev.commonLib, line 2754
 *  // library marker kkossev.commonLib, line 2755
 */ // library marker kkossev.commonLib, line 2756
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2757
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2758
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2759
        state.clear() // library marker kkossev.commonLib, line 2760
        unschedule() // library marker kkossev.commonLib, line 2761
        resetStats() // library marker kkossev.commonLib, line 2762
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2763
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2764
        logInfo "all states and scheduled jobs cleared!" // library marker kkossev.commonLib, line 2765
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2766
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2767
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2768
        sendInfoEvent("Initialized") // library marker kkossev.commonLib, line 2769
    } // library marker kkossev.commonLib, line 2770

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2772
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2773
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2774
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2775
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2776
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2777

    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true) // library marker kkossev.commonLib, line 2779
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true) // library marker kkossev.commonLib, line 2780
    if (fullInit || settings?.traceEnable == null) device.updateSetting("traceEnable", false) // library marker kkossev.commonLib, line 2781
    if (fullInit || settings?.alwaysOn == null) device.updateSetting("alwaysOn", false) // library marker kkossev.commonLib, line 2782
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"]) // library marker kkossev.commonLib, line 2783
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2784
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2785
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown') // library marker kkossev.commonLib, line 2786
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false) // library marker kkossev.commonLib, line 2787
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2788
        if (fullInit || settings?.minReportingTime == null) device.updateSetting("minReportingTime", [value:DEFAULT_MIN_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2789
        if (fullInit || settings?.maxReportingTime == null) device.updateSetting("maxReportingTime", [value:DEFAULT_MAX_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2790
    } // library marker kkossev.commonLib, line 2791
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2792
        if (fullInit || settings?.illuminanceThreshold == null) device.updateSetting("illuminanceThreshold", [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:"number"]) // library marker kkossev.commonLib, line 2793
        if (fullInit || settings?.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.00, type:"decimal"]) // library marker kkossev.commonLib, line 2794
    } // library marker kkossev.commonLib, line 2795
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2796
    if (DEVICE_TYPE in ["AirQuality"]) { initVarsAirQuality(fullInit) } // library marker kkossev.commonLib, line 2797
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2798
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2799
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit);    initEventsSwitch(fullInit) }         // threeStateEnable, ignoreDuplicated // library marker kkossev.commonLib, line 2800
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none // library marker kkossev.commonLib, line 2801
    if (DEVICE_TYPE in ["Radar"])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none // library marker kkossev.commonLib, line 2802
    if (DEVICE_TYPE in ["ButtonDimmer"]) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2803
    if (DEVICE_TYPE in ["Thermostat"]) { initVarsThermostat(fullInit);     initEventsThermostat(fullInit) } // library marker kkossev.commonLib, line 2804
    if (DEVICE_TYPE in ["Bulb"])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2805

    def mm = device.getDataValue("model") // library marker kkossev.commonLib, line 2807
    if ( mm != null) { // library marker kkossev.commonLib, line 2808
        logDebug " model = ${mm}" // library marker kkossev.commonLib, line 2809
    } // library marker kkossev.commonLib, line 2810
    else { // library marker kkossev.commonLib, line 2811
        logWarn " Model not found, please re-pair the device!" // library marker kkossev.commonLib, line 2812
    } // library marker kkossev.commonLib, line 2813
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2814
    if ( ep  != null) { // library marker kkossev.commonLib, line 2815
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2816
        logDebug " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2817
    } // library marker kkossev.commonLib, line 2818
    else { // library marker kkossev.commonLib, line 2819
        logWarn " Destination End Point not found, please re-pair the device!" // library marker kkossev.commonLib, line 2820
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2821
    }     // library marker kkossev.commonLib, line 2822
} // library marker kkossev.commonLib, line 2823


/** // library marker kkossev.commonLib, line 2826
 * called from TODO // library marker kkossev.commonLib, line 2827
 *  // library marker kkossev.commonLib, line 2828
 */ // library marker kkossev.commonLib, line 2829
def setDestinationEP() { // library marker kkossev.commonLib, line 2830
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2831
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2832
        state.destinationEP = ep // library marker kkossev.commonLib, line 2833
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2834
    } // library marker kkossev.commonLib, line 2835
    else { // library marker kkossev.commonLib, line 2836
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2837
        state.destinationEP = "01"    // fallback EP // library marker kkossev.commonLib, line 2838
    }       // library marker kkossev.commonLib, line 2839
} // library marker kkossev.commonLib, line 2840


def logDebug(msg) { // library marker kkossev.commonLib, line 2843
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2844
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2845
    } // library marker kkossev.commonLib, line 2846
} // library marker kkossev.commonLib, line 2847

def logInfo(msg) { // library marker kkossev.commonLib, line 2849
    if (settings.txtEnable) { // library marker kkossev.commonLib, line 2850
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2851
    } // library marker kkossev.commonLib, line 2852
} // library marker kkossev.commonLib, line 2853

def logWarn(msg) { // library marker kkossev.commonLib, line 2855
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2856
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2857
    } // library marker kkossev.commonLib, line 2858
} // library marker kkossev.commonLib, line 2859

def logTrace(msg) { // library marker kkossev.commonLib, line 2861
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2862
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2863
    } // library marker kkossev.commonLib, line 2864
} // library marker kkossev.commonLib, line 2865



// _DEBUG mode only // library marker kkossev.commonLib, line 2869
void getAllProperties() { // library marker kkossev.commonLib, line 2870
    log.trace 'Properties:'     // library marker kkossev.commonLib, line 2871
    device.properties.each { it-> // library marker kkossev.commonLib, line 2872
        log.debug it // library marker kkossev.commonLib, line 2873
    } // library marker kkossev.commonLib, line 2874
    log.trace 'Settings:'     // library marker kkossev.commonLib, line 2875
    settings.each { it-> // library marker kkossev.commonLib, line 2876
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2877
    }     // library marker kkossev.commonLib, line 2878
    log.trace 'Done'     // library marker kkossev.commonLib, line 2879
} // library marker kkossev.commonLib, line 2880

// delete all Preferences // library marker kkossev.commonLib, line 2882
void deleteAllSettings() { // library marker kkossev.commonLib, line 2883
    settings.each { it-> // library marker kkossev.commonLib, line 2884
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2885
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2886
    } // library marker kkossev.commonLib, line 2887
    logInfo  "All settings (preferences) DELETED" // library marker kkossev.commonLib, line 2888
} // library marker kkossev.commonLib, line 2889

// delete all attributes // library marker kkossev.commonLib, line 2891
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2892
    device.properties.supportedAttributes.each { it-> // library marker kkossev.commonLib, line 2893
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2894
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2895
    } // library marker kkossev.commonLib, line 2896
    logInfo "All current states (attributes) DELETED" // library marker kkossev.commonLib, line 2897
} // library marker kkossev.commonLib, line 2898

// delete all State Variables // library marker kkossev.commonLib, line 2900
void deleteAllStates() { // library marker kkossev.commonLib, line 2901
    state.each { it-> // library marker kkossev.commonLib, line 2902
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2903
    } // library marker kkossev.commonLib, line 2904
    state.clear() // library marker kkossev.commonLib, line 2905
    logInfo "All States DELETED" // library marker kkossev.commonLib, line 2906
} // library marker kkossev.commonLib, line 2907

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2909
    unschedule() // library marker kkossev.commonLib, line 2910
    logInfo "All scheduled jobs DELETED" // library marker kkossev.commonLib, line 2911
} // library marker kkossev.commonLib, line 2912

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2914
    logDebug "deleteAllChildDevices : not implemented!" // library marker kkossev.commonLib, line 2915
} // library marker kkossev.commonLib, line 2916

def parseTest(par) { // library marker kkossev.commonLib, line 2918
//read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2919
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2920
    parse(par) // library marker kkossev.commonLib, line 2921
} // library marker kkossev.commonLib, line 2922

def testJob() { // library marker kkossev.commonLib, line 2924
    log.warn "test job executed" // library marker kkossev.commonLib, line 2925
} // library marker kkossev.commonLib, line 2926

/** // library marker kkossev.commonLib, line 2928
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2929
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2930
 */ // library marker kkossev.commonLib, line 2931
def getCron( timeInSeconds ) { // library marker kkossev.commonLib, line 2932
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2933
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2934
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2935
    def minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2936
    def hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2937
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2938
    String cron // library marker kkossev.commonLib, line 2939
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2940
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2941
    } // library marker kkossev.commonLib, line 2942
    else { // library marker kkossev.commonLib, line 2943
        if (minutes < 60) { // library marker kkossev.commonLib, line 2944
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"   // library marker kkossev.commonLib, line 2945
        } // library marker kkossev.commonLib, line 2946
        else { // library marker kkossev.commonLib, line 2947
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"                    // library marker kkossev.commonLib, line 2948
        } // library marker kkossev.commonLib, line 2949
    } // library marker kkossev.commonLib, line 2950
    return cron // library marker kkossev.commonLib, line 2951
} // library marker kkossev.commonLib, line 2952

boolean isTuya() { // library marker kkossev.commonLib, line 2954
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2955
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2956
    if (model?.startsWith("TS") && manufacturer?.startsWith("_TZ")) { // library marker kkossev.commonLib, line 2957
        return true // library marker kkossev.commonLib, line 2958
    } // library marker kkossev.commonLib, line 2959
    return false // library marker kkossev.commonLib, line 2960
} // library marker kkossev.commonLib, line 2961

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2963
    if (!isTuya()) { // library marker kkossev.commonLib, line 2964
        logDebug "not Tuya" // library marker kkossev.commonLib, line 2965
        return // library marker kkossev.commonLib, line 2966
    } // library marker kkossev.commonLib, line 2967
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2968
    if (application != null) { // library marker kkossev.commonLib, line 2969
        Integer ver // library marker kkossev.commonLib, line 2970
        try { // library marker kkossev.commonLib, line 2971
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2972
        } // library marker kkossev.commonLib, line 2973
        catch (e) { // library marker kkossev.commonLib, line 2974
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2975
            return // library marker kkossev.commonLib, line 2976
        } // library marker kkossev.commonLib, line 2977
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString() // library marker kkossev.commonLib, line 2978
        if (device.getDataValue("tuyaVersion") != str) { // library marker kkossev.commonLib, line 2979
            device.updateDataValue("tuyaVersion", str) // library marker kkossev.commonLib, line 2980
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2981
        } // library marker kkossev.commonLib, line 2982
    } // library marker kkossev.commonLib, line 2983
} // library marker kkossev.commonLib, line 2984

boolean isAqara() { // library marker kkossev.commonLib, line 2986
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2987
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2988
    if (model?.startsWith("lumi")) { // library marker kkossev.commonLib, line 2989
        return true // library marker kkossev.commonLib, line 2990
    } // library marker kkossev.commonLib, line 2991
    return false // library marker kkossev.commonLib, line 2992
} // library marker kkossev.commonLib, line 2993

def updateAqaraVersion() { // library marker kkossev.commonLib, line 2995
    if (!isAqara()) { // library marker kkossev.commonLib, line 2996
        logDebug "not Aqara" // library marker kkossev.commonLib, line 2997
        return // library marker kkossev.commonLib, line 2998
    }     // library marker kkossev.commonLib, line 2999
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 3000
    if (application != null) { // library marker kkossev.commonLib, line 3001
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2)))); // library marker kkossev.commonLib, line 3002
        if (device.getDataValue("aqaraVersion") != str) { // library marker kkossev.commonLib, line 3003
            device.updateDataValue("aqaraVersion", str) // library marker kkossev.commonLib, line 3004
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 3005
        } // library marker kkossev.commonLib, line 3006
    } // library marker kkossev.commonLib, line 3007
    else { // library marker kkossev.commonLib, line 3008
        return null // library marker kkossev.commonLib, line 3009
    } // library marker kkossev.commonLib, line 3010
} // library marker kkossev.commonLib, line 3011

def test(par) { // library marker kkossev.commonLib, line 3013
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 3014
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 3015

    parse(par) // library marker kkossev.commonLib, line 3017

   // sendZigbeeCommands(cmds)     // library marker kkossev.commonLib, line 3019
} // library marker kkossev.commonLib, line 3020

// /////////////////////////////////////////////////////////////////// Libraries ////////////////////////////////////////////////////////////////////// // library marker kkossev.commonLib, line 3022



// ~~~~~ end include (144) kkossev.commonLib ~~~~~
