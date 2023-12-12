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
 *
 *                                   TODO: add toggle() command; initialize 'switch' to unknown
 *                                   TODO: add power-on behavior option
 *                                   TODO: add 'allStatus' attribute
 *                                   TODO: add Info dummy preference w/ link to Hubitat forum page
 */

static String version() { "3.0.2" }
static String timeStamp() {"2023/12/12 10:57 PM"}

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
        logDebug "configureDeviceSwitch() : unbind ZBMINIL2 poll control cluster"
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
  * ver. 3.0.1  2023-12-06 kkossev  - (dev.branch) Info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 32
  * // library marker kkossev.commonLib, line 33
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 34
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 35
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 36
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 37
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 38
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 39
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 40
 * // library marker kkossev.commonLib, line 41
*/ // library marker kkossev.commonLib, line 42

def commonLibVersion()   {"3.0.1"} // library marker kkossev.commonLib, line 44
def thermostatLibStamp() {"2023/12/06 9:46 PM"} // library marker kkossev.commonLib, line 45

import groovy.transform.Field // library marker kkossev.commonLib, line 47
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 48
import hubitat.device.Protocol // library marker kkossev.commonLib, line 49
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 50
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 51
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 52
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 53


@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 56

metadata { // library marker kkossev.commonLib, line 58

        if (_DEBUG) { // library marker kkossev.commonLib, line 60
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]  // library marker kkossev.commonLib, line 61
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]] // library marker kkossev.commonLib, line 62
            command "tuyaTest", [ // library marker kkossev.commonLib, line 63
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]], // library marker kkossev.commonLib, line 64
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]], // library marker kkossev.commonLib, line 65
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] // library marker kkossev.commonLib, line 66
            ] // library marker kkossev.commonLib, line 67
        } // library marker kkossev.commonLib, line 68


        // common capabilities for all device types // library marker kkossev.commonLib, line 71
        capability 'Configuration' // library marker kkossev.commonLib, line 72
        capability 'Refresh' // library marker kkossev.commonLib, line 73
        capability 'Health Check' // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute "rtt", "number"  // library marker kkossev.commonLib, line 78
        attribute "Status", "string" // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 82
        command "configure", [[name:"normally it is not needed to configure anything", type: "ENUM",   constraints: ["--- select ---"]+ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 83

        // deviceType specific capabilities, commands and attributes          // library marker kkossev.commonLib, line 85
        if (deviceType in ["Device"]) { // library marker kkossev.commonLib, line 86
            if (_DEBUG) { // library marker kkossev.commonLib, line 87
                command "getAllProperties",       [[name: "Get All Properties"]] // library marker kkossev.commonLib, line 88
            } // library marker kkossev.commonLib, line 89
        } // library marker kkossev.commonLib, line 90
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) { // library marker kkossev.commonLib, line 91
            command "zigbeeGroups", [ // library marker kkossev.commonLib, line 92
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 93
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]] // library marker kkossev.commonLib, line 94
            ] // library marker kkossev.commonLib, line 95
        }         // library marker kkossev.commonLib, line 96
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube", "Radar"]) { // library marker kkossev.commonLib, line 97
            capability "Sensor" // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
        if (deviceType in  ["Device", "MotionSensor", "Radar"]) { // library marker kkossev.commonLib, line 100
            capability "MotionSensor" // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ["Device", "Switch", "Relay", "Plug", "Outlet", "Thermostat", "Fingerbot", "Dimmer", "Bulb", "IRBlaster"]) { // library marker kkossev.commonLib, line 103
            capability "Actuator" // library marker kkossev.commonLib, line 104
        } // library marker kkossev.commonLib, line 105
        if (deviceType in  ["Device", "THSensor", "LightSensor", "MotionSensor", "Thermostat", "Fingerbot", "ButtonDimmer", "AqaraCube", "IRBlaster"]) { // library marker kkossev.commonLib, line 106
            capability "Battery" // library marker kkossev.commonLib, line 107
            attribute "batteryVoltage", "number" // library marker kkossev.commonLib, line 108
        } // library marker kkossev.commonLib, line 109
        if (deviceType in  ["Thermostat"]) { // library marker kkossev.commonLib, line 110
            capability "Thermostat" // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
        if (deviceType in  ["Plug", "Outlet"]) { // library marker kkossev.commonLib, line 113
            capability "Outlet" // library marker kkossev.commonLib, line 114
        }         // library marker kkossev.commonLib, line 115
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot", "Bulb"]) { // library marker kkossev.commonLib, line 116
            capability "Switch" // library marker kkossev.commonLib, line 117
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 118
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 119
            } // library marker kkossev.commonLib, line 120
        }         // library marker kkossev.commonLib, line 121
        if (deviceType in ["Dimmer", "ButtonDimmer", "Bulb"]) { // library marker kkossev.commonLib, line 122
            capability "SwitchLevel" // library marker kkossev.commonLib, line 123
        } // library marker kkossev.commonLib, line 124
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) { // library marker kkossev.commonLib, line 125
            capability "PushableButton" // library marker kkossev.commonLib, line 126
            capability "DoubleTapableButton" // library marker kkossev.commonLib, line 127
            capability "HoldableButton" // library marker kkossev.commonLib, line 128
            capability "ReleasableButton" // library marker kkossev.commonLib, line 129
        } // library marker kkossev.commonLib, line 130
        if (deviceType in  ["Device", "Fingerbot"]) { // library marker kkossev.commonLib, line 131
            capability "Momentary" // library marker kkossev.commonLib, line 132
        } // library marker kkossev.commonLib, line 133
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) { // library marker kkossev.commonLib, line 134
            capability "TemperatureMeasurement" // library marker kkossev.commonLib, line 135
        } // library marker kkossev.commonLib, line 136
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 137
            capability "RelativeHumidityMeasurement"             // library marker kkossev.commonLib, line 138
        } // library marker kkossev.commonLib, line 139
        if (deviceType in  ["Device", "LightSensor", "Radar"]) { // library marker kkossev.commonLib, line 140
            capability "IlluminanceMeasurement" // library marker kkossev.commonLib, line 141
        } // library marker kkossev.commonLib, line 142
        if (deviceType in  ["AirQuality"]) { // library marker kkossev.commonLib, line 143
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500 // library marker kkossev.commonLib, line 144
        } // library marker kkossev.commonLib, line 145

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 147
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug"  // library marker kkossev.commonLib, line 148

    preferences { // library marker kkossev.commonLib, line 150
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 151
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 152
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 153

        if (advancedOptions == true || advancedOptions == false) { // groovy ... // library marker kkossev.commonLib, line 155
            if (device.hasCapability("TemperatureMeasurement") || device.hasCapability("RelativeHumidityMeasurement") || device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 156
                input name: "minReportingTime", type: "number", title: "<b>Minimum time between reports</b>", description: "<i>Minimum reporting interval, seconds (1..300)</i>", range: "1..300", defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 157
                input name: "maxReportingTime", type: "number", title: "<b>Maximum time between reports</b>", description: "<i>Maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 158
            } // library marker kkossev.commonLib, line 159
            if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 160
                input name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 161
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00 // library marker kkossev.commonLib, line 162

            } // library marker kkossev.commonLib, line 164
        } // library marker kkossev.commonLib, line 165

        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: "<i>These advanced options should be already automatically set in an optimal way for your device...</i>", defaultValue: false // library marker kkossev.commonLib, line 167
        if (advancedOptions == true || advancedOptions == true) { // library marker kkossev.commonLib, line 168
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 169
            //if (healthCheckMethod != null && safeToInt(healthCheckMethod.value) != 0) { // library marker kkossev.commonLib, line 170
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 171
            //} // library marker kkossev.commonLib, line 172
            if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 173
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 174

            } // library marker kkossev.commonLib, line 176
            if ((deviceType in  ["Switch", "Plug", "Dimmer"]) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 177
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 178
            } // library marker kkossev.commonLib, line 179
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 180
        } // library marker kkossev.commonLib, line 181
    } // library marker kkossev.commonLib, line 182

} // library marker kkossev.commonLib, line 184


@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 187
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 188
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events  // library marker kkossev.commonLib, line 189
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 190
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 191
@Field static final String  UNKNOWN = "UNKNOWN" // library marker kkossev.commonLib, line 192
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 193
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 194
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 195
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 196
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 197
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 198

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 200
    defaultValue: 1, // library marker kkossev.commonLib, line 201
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 202
] // library marker kkossev.commonLib, line 203
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 204
    defaultValue: 240, // library marker kkossev.commonLib, line 205
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 206
] // library marker kkossev.commonLib, line 207
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 208
    defaultValue: 0, // library marker kkossev.commonLib, line 209
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 210
] // library marker kkossev.commonLib, line 211

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 213
    defaultValue: 0, // library marker kkossev.commonLib, line 214
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 215
] // library marker kkossev.commonLib, line 216
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 217
    defaultValue: 0, // library marker kkossev.commonLib, line 218
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 219
] // library marker kkossev.commonLib, line 220

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 222
    "Configure the device only"  : [key:2, function: 'configure'], // library marker kkossev.commonLib, line 223
    "Reset Statistics"           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 224
    "           --            "  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 225
    "Delete All Preferences"     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 226
    "Delete All Current States"  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 227
    "Delete All Scheduled Jobs"  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 228
    "Delete All State Variables" : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 229
    "Delete All Child Devices"   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 230
    "           -             "  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 231
    "*** LOAD ALL DEFAULTS ***"  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 232
] // library marker kkossev.commonLib, line 233

def isVirtual() { device.controllerType == null || device.controllerType == ""} // library marker kkossev.commonLib, line 235
def isChattyDeviceReport(description)  {return false /*(description?.contains("cluster: FC7E")) */} // library marker kkossev.commonLib, line 236
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 237
def isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 238
def isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 239
def isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 240
def isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 241
def isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 242

/** // library marker kkossev.commonLib, line 244
 * Parse Zigbee message // library marker kkossev.commonLib, line 245
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 246
 */ // library marker kkossev.commonLib, line 247
void parse(final String description) { // library marker kkossev.commonLib, line 248
    checkDriverVersion() // library marker kkossev.commonLib, line 249
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 250
    if (state.stats != null) state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 251
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 252
    setHealthStatusOnline() // library marker kkossev.commonLib, line 253

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {     // library marker kkossev.commonLib, line 255
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 256
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 257
            logDebug "ignored IAS zone status" // library marker kkossev.commonLib, line 258
            return // library marker kkossev.commonLib, line 259
        } // library marker kkossev.commonLib, line 260
        else { // library marker kkossev.commonLib, line 261
            parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 262
        } // library marker kkossev.commonLib, line 263
    } // library marker kkossev.commonLib, line 264
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 265
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 266
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 267
        if (settings?.logEnable) logInfo "Sending IAS enroll response..." // library marker kkossev.commonLib, line 268
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 269
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 270
        sendZigbeeCommands( cmds )   // library marker kkossev.commonLib, line 271
    }  // library marker kkossev.commonLib, line 272
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 273
        return // library marker kkossev.commonLib, line 274
    }         // library marker kkossev.commonLib, line 275
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 276

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 278
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 279
        return // library marker kkossev.commonLib, line 280
    } // library marker kkossev.commonLib, line 281
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 282
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 283
        return // library marker kkossev.commonLib, line 284
    } // library marker kkossev.commonLib, line 285
    if (!isChattyDeviceReport(description)) {logDebug "parse: descMap = ${descMap} description=${description}"} // library marker kkossev.commonLib, line 286
    // // library marker kkossev.commonLib, line 287
    final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 288
    final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 289
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 290

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 292
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 293
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 294
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 295
            break // library marker kkossev.commonLib, line 296
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 297
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 298
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 301
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 302
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 305
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 306
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 307
            break // library marker kkossev.commonLib, line 308
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 309
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 310
            descMap.remove('additionalAttrs')?.each {final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 313
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 314
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 317
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 318
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro // library marker kkossev.commonLib, line 321
            parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 322
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 325
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
         case 0x0102 :                                      // window covering  // library marker kkossev.commonLib, line 328
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 329
            break        // library marker kkossev.commonLib, line 330
        case 0x0201 :                                       // Aqara E1 TRV  // library marker kkossev.commonLib, line 331
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
        case 0xFC11 :                                    // Sonoff  // library marker kkossev.commonLib, line 368
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

} // library marker kkossev.commonLib, line 386

/** // library marker kkossev.commonLib, line 388
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 389
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 390
 */ // library marker kkossev.commonLib, line 391
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 392
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 393
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 394
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 395
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 396
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 397
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 398
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 399
    }  // library marker kkossev.commonLib, line 400
    else { // library marker kkossev.commonLib, line 401
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 402
    } // library marker kkossev.commonLib, line 403
} // library marker kkossev.commonLib, line 404

/** // library marker kkossev.commonLib, line 406
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 407
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 408
 */ // library marker kkossev.commonLib, line 409
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 410
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 411
    switch (commandId) { // library marker kkossev.commonLib, line 412
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 413
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 414
            break // library marker kkossev.commonLib, line 415
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 416
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 417
            break // library marker kkossev.commonLib, line 418
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 419
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 420
            break // library marker kkossev.commonLib, line 421
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 422
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 423
            break // library marker kkossev.commonLib, line 424
        case 0x0B: // default command response // library marker kkossev.commonLib, line 425
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 426
            break // library marker kkossev.commonLib, line 427
        default: // library marker kkossev.commonLib, line 428
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 429
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 430
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 431
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 432
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 433
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 434
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 435
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 436
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 437
            } // library marker kkossev.commonLib, line 438
            break // library marker kkossev.commonLib, line 439
    } // library marker kkossev.commonLib, line 440
} // library marker kkossev.commonLib, line 441

/** // library marker kkossev.commonLib, line 443
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 444
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 445
 */ // library marker kkossev.commonLib, line 446
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 447
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 448
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 449
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 450
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 451
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 452
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 453
    } // library marker kkossev.commonLib, line 454
    else { // library marker kkossev.commonLib, line 455
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 456
    } // library marker kkossev.commonLib, line 457
} // library marker kkossev.commonLib, line 458

/** // library marker kkossev.commonLib, line 460
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 461
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 462
 */ // library marker kkossev.commonLib, line 463
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 464
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 465
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 466
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 467
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 468
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 469
    } // library marker kkossev.commonLib, line 470
    else { // library marker kkossev.commonLib, line 471
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 472
    } // library marker kkossev.commonLib, line 473
} // library marker kkossev.commonLib, line 474

/** // library marker kkossev.commonLib, line 476
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 477
 */ // library marker kkossev.commonLib, line 478
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 479
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 480
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 481
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 482
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 483
        state.reportingEnabled = true // library marker kkossev.commonLib, line 484
    } // library marker kkossev.commonLib, line 485
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 486
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 487
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 488
    } else { // library marker kkossev.commonLib, line 489
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 490
    } // library marker kkossev.commonLib, line 491
} // library marker kkossev.commonLib, line 492

/** // library marker kkossev.commonLib, line 494
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 495
 */ // library marker kkossev.commonLib, line 496
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 497
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 498
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 499
    def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 500
    def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 501
    if (status == 0) { // library marker kkossev.commonLib, line 502
        def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 503
        def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 504
        def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7]) // library marker kkossev.commonLib, line 505
        def delta = 0 // library marker kkossev.commonLib, line 506
        if (descMap.data.size()>=10) {  // library marker kkossev.commonLib, line 507
            delta = zigbee.convertHexToInt(descMap.data[10]+descMap.data[9]) // library marker kkossev.commonLib, line 508
        } // library marker kkossev.commonLib, line 509
        else { // library marker kkossev.commonLib, line 510
            logDebug "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 511
        } // library marker kkossev.commonLib, line 512
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 513
    } // library marker kkossev.commonLib, line 514
    else { // library marker kkossev.commonLib, line 515
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 516
    } // library marker kkossev.commonLib, line 517
} // library marker kkossev.commonLib, line 518

/** // library marker kkossev.commonLib, line 520
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 521
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 522
 */ // library marker kkossev.commonLib, line 523
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 524
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 525
    final String commandId = data[0] // library marker kkossev.commonLib, line 526
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 527
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 528
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 529
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 530
    } else { // library marker kkossev.commonLib, line 531
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
} // library marker kkossev.commonLib, line 534


// Zigbee Attribute IDs // library marker kkossev.commonLib, line 537
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 538
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 539
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 540
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 541
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 542
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 543
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 544
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 545
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 546
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 547
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 548
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 549
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 550
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 551
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 552

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 554
    0x00: 'Success', // library marker kkossev.commonLib, line 555
    0x01: 'Failure', // library marker kkossev.commonLib, line 556
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 557
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 558
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 559
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 560
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 561
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 562
    0x88: 'Read Only', // library marker kkossev.commonLib, line 563
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 564
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 565
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 566
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 567
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 568
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 569
    0x94: 'Time out', // library marker kkossev.commonLib, line 570
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 571
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 572
] // library marker kkossev.commonLib, line 573

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 575
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 576
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 577
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 578
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 579
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 580
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 581
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 582
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 583
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 584
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 585
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 586
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 587
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 588
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 589
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 590
] // library marker kkossev.commonLib, line 591

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 593
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 594
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 595
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 596
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 597
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 598
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 599
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 600
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 601
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 602
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 603
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 604
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 605
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 606
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 607
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 608
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 609
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 610
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 611
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 612
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 613
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 614
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 615
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 616
] // library marker kkossev.commonLib, line 617


/* // library marker kkossev.commonLib, line 620
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 621
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 622
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 623
 */ // library marker kkossev.commonLib, line 624
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 625
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 626
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 627
    }     // library marker kkossev.commonLib, line 628
    else { // library marker kkossev.commonLib, line 629
        logWarn "Xiaomi cluster 0xFCC0" // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631
} // library marker kkossev.commonLib, line 632


@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 635
double approxRollingAverage (double avg, double new_sample) { // library marker kkossev.commonLib, line 636
    if (avg == null || avg == 0) { avg = new_sample} // library marker kkossev.commonLib, line 637
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 638
    avg += new_sample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 639
    // TOSO: try Method II : New average = old average * (n-1)/n + new value /n // library marker kkossev.commonLib, line 640
    return avg // library marker kkossev.commonLib, line 641
} // library marker kkossev.commonLib, line 642

/* // library marker kkossev.commonLib, line 644
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 645
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 646
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 647
*/ // library marker kkossev.commonLib, line 648
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 649

/** // library marker kkossev.commonLib, line 651
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 652
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 653
 */ // library marker kkossev.commonLib, line 654
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 655
    def now = new Date().getTime() // library marker kkossev.commonLib, line 656
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 657
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 658
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 659
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 660
    state.lastRx["checkInTime"] = now // library marker kkossev.commonLib, line 661
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 662
        case 0x0000: // library marker kkossev.commonLib, line 663
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 664
            break // library marker kkossev.commonLib, line 665
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 666
            boolean isPing = state.states["isPing"] ?: false // library marker kkossev.commonLib, line 667
            if (isPing) { // library marker kkossev.commonLib, line 668
                def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger() // library marker kkossev.commonLib, line 669
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 670
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 671
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 672
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 673
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 674
                    sendRttEvent() // library marker kkossev.commonLib, line 675
                } // library marker kkossev.commonLib, line 676
                else { // library marker kkossev.commonLib, line 677
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 678
                } // library marker kkossev.commonLib, line 679
                state.states["isPing"] = false // library marker kkossev.commonLib, line 680
            } // library marker kkossev.commonLib, line 681
            else { // library marker kkossev.commonLib, line 682
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 683
            } // library marker kkossev.commonLib, line 684
            break // library marker kkossev.commonLib, line 685
        case 0x0004: // library marker kkossev.commonLib, line 686
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 687
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 688
            def manufacturer = device.getDataValue("manufacturer") // library marker kkossev.commonLib, line 689
            if ((manufacturer == null || manufacturer == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 690
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 691
                device.updateDataValue("manufacturer", descMap?.value) // library marker kkossev.commonLib, line 692
            } // library marker kkossev.commonLib, line 693
            break // library marker kkossev.commonLib, line 694
        case 0x0005: // library marker kkossev.commonLib, line 695
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 696
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 697
            def model = device.getDataValue("model") // library marker kkossev.commonLib, line 698
            if ((model == null || model == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 699
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 700
                device.updateDataValue("model", descMap?.value) // library marker kkossev.commonLib, line 701
            } // library marker kkossev.commonLib, line 702
            break // library marker kkossev.commonLib, line 703
        case 0x0007: // library marker kkossev.commonLib, line 704
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 705
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 706
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 707
            break // library marker kkossev.commonLib, line 708
        case 0xFFDF: // library marker kkossev.commonLib, line 709
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 710
            break // library marker kkossev.commonLib, line 711
        case 0xFFE2: // library marker kkossev.commonLib, line 712
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 713
            break // library marker kkossev.commonLib, line 714
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 715
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 716
            break // library marker kkossev.commonLib, line 717
        case 0xFFFE: // library marker kkossev.commonLib, line 718
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 719
            break // library marker kkossev.commonLib, line 720
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 721
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 722
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 723
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 724
            break // library marker kkossev.commonLib, line 725
        default: // library marker kkossev.commonLib, line 726
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 727
            break // library marker kkossev.commonLib, line 728
    } // library marker kkossev.commonLib, line 729
} // library marker kkossev.commonLib, line 730

/* // library marker kkossev.commonLib, line 732
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 733
 * power cluster            0x0001 // library marker kkossev.commonLib, line 734
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 735
*/ // library marker kkossev.commonLib, line 736
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 737
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 738
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 739
    if (descMap.attrId in ["0020", "0021"]) { // library marker kkossev.commonLib, line 740
        state.lastRx["batteryTime"] = new Date().getTime() // library marker kkossev.commonLib, line 741
        state.stats["battCtr"] = (state.stats["battCtr"] ?: 0 ) + 1 // library marker kkossev.commonLib, line 742
    } // library marker kkossev.commonLib, line 743

    final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 745
    if (descMap.attrId == "0020") { // library marker kkossev.commonLib, line 746
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 747
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 748
            sendBatteryVoltageEvent(rawValue, convertToPercent=true) // library marker kkossev.commonLib, line 749
        } // library marker kkossev.commonLib, line 750
    } // library marker kkossev.commonLib, line 751
    else if (descMap.attrId == "0021") { // library marker kkossev.commonLib, line 752
        sendBatteryPercentageEvent(rawValue * 2)     // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    else { // library marker kkossev.commonLib, line 755
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 756
    } // library marker kkossev.commonLib, line 757
} // library marker kkossev.commonLib, line 758

def sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) { // library marker kkossev.commonLib, line 760
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 761
    def result = [:] // library marker kkossev.commonLib, line 762
    def volts = rawValue / 10 // library marker kkossev.commonLib, line 763
    if (!(rawValue == 0 || rawValue == 255)) { // library marker kkossev.commonLib, line 764
        def minVolts = 2.2 // library marker kkossev.commonLib, line 765
        def maxVolts = 3.2 // library marker kkossev.commonLib, line 766
        def pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 767
        def roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 768
        if (roundedPct <= 0) roundedPct = 1 // library marker kkossev.commonLib, line 769
        if (roundedPct >100) roundedPct = 100 // library marker kkossev.commonLib, line 770
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 771
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 772
            result.name = 'battery' // library marker kkossev.commonLib, line 773
            result.unit  = '%' // library marker kkossev.commonLib, line 774
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 775
        } // library marker kkossev.commonLib, line 776
        else { // library marker kkossev.commonLib, line 777
            result.value = volts // library marker kkossev.commonLib, line 778
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 779
            result.unit  = 'V' // library marker kkossev.commonLib, line 780
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 781
        } // library marker kkossev.commonLib, line 782
        result.type = 'physical' // library marker kkossev.commonLib, line 783
        result.isStateChange = true // library marker kkossev.commonLib, line 784
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 785
        sendEvent(result) // library marker kkossev.commonLib, line 786
    } // library marker kkossev.commonLib, line 787
    else { // library marker kkossev.commonLib, line 788
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 789
    }     // library marker kkossev.commonLib, line 790
} // library marker kkossev.commonLib, line 791

def sendBatteryPercentageEvent( batteryPercent, isDigital=false ) { // library marker kkossev.commonLib, line 793
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 794
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 795
        return // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
    def map = [:] // library marker kkossev.commonLib, line 798
    map.name = 'battery' // library marker kkossev.commonLib, line 799
    map.timeStamp = now() // library marker kkossev.commonLib, line 800
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 801
    map.unit  = '%' // library marker kkossev.commonLib, line 802
    map.type = isDigital ? 'digital' : 'physical'     // library marker kkossev.commonLib, line 803
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 804
    map.isStateChange = true // library marker kkossev.commonLib, line 805
    //  // library marker kkossev.commonLib, line 806
    def latestBatteryEvent = device.latestState('battery', skipCache=true) // library marker kkossev.commonLib, line 807
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 808
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 809
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 810
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 811
        // send it now! // library marker kkossev.commonLib, line 812
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 813
    } // library marker kkossev.commonLib, line 814
    else { // library marker kkossev.commonLib, line 815
        def delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 816
        map.delayed = delayedTime // library marker kkossev.commonLib, line 817
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 818
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 819
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 820
    } // library marker kkossev.commonLib, line 821
} // library marker kkossev.commonLib, line 822

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 824
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 825
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 826
    sendEvent(map) // library marker kkossev.commonLib, line 827
} // library marker kkossev.commonLib, line 828

private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 830
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 831
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 832
    sendEvent(map) // library marker kkossev.commonLib, line 833
} // library marker kkossev.commonLib, line 834

/* // library marker kkossev.commonLib, line 836
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 837
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 838
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 839
*/ // library marker kkossev.commonLib, line 840
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 841
    logDebug "unprocessed parseIdentityCluster" // library marker kkossev.commonLib, line 842
} // library marker kkossev.commonLib, line 843



/* // library marker kkossev.commonLib, line 847
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 848
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 849
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 850
*/ // library marker kkossev.commonLib, line 851
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 852
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 853
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 854
    }     // library marker kkossev.commonLib, line 855
    else { // library marker kkossev.commonLib, line 856
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 857
    } // library marker kkossev.commonLib, line 858
} // library marker kkossev.commonLib, line 859


/* // library marker kkossev.commonLib, line 862
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 863
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 864
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 865
*/ // library marker kkossev.commonLib, line 866
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 867
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 868
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 869
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]     // library marker kkossev.commonLib, line 870
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 871
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 872
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 873
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 874
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 875
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 876
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 877
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 878
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 879
            } // library marker kkossev.commonLib, line 880
            else { // library marker kkossev.commonLib, line 881
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 882
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 883
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 884
                for (int i=0; i<groupCount; i++ ) { // library marker kkossev.commonLib, line 885
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 886
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 887
                        return // library marker kkossev.commonLib, line 888
                    } // library marker kkossev.commonLib, line 889
                } // library marker kkossev.commonLib, line 890
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 891
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt,4)})" // library marker kkossev.commonLib, line 892
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 893
            } // library marker kkossev.commonLib, line 894
            break // library marker kkossev.commonLib, line 895
        case 0x01: // View group // library marker kkossev.commonLib, line 896
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 897
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 898
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 899
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 900
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 901
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 902
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 903
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 904
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 905
            } // library marker kkossev.commonLib, line 906
            else { // library marker kkossev.commonLib, line 907
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 908
            } // library marker kkossev.commonLib, line 909
            break // library marker kkossev.commonLib, line 910
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 911
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 912
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 913
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 914
            final Set<String> groups = [] // library marker kkossev.commonLib, line 915
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 916
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 917
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 918
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 919
            } // library marker kkossev.commonLib, line 920
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 921
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 922
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 923
            break // library marker kkossev.commonLib, line 924
        case 0x03: // Remove group // library marker kkossev.commonLib, line 925
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 926
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 927
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 928
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 929
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 930
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 931
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 932
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 933
            } // library marker kkossev.commonLib, line 934
            else { // library marker kkossev.commonLib, line 935
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 936
            } // library marker kkossev.commonLib, line 937
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 938
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 939
            if (index >= 0) { // library marker kkossev.commonLib, line 940
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 941
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 942
            } // library marker kkossev.commonLib, line 943
            break // library marker kkossev.commonLib, line 944
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 945
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 946
            logWarn "not implemented!" // library marker kkossev.commonLib, line 947
            break // library marker kkossev.commonLib, line 948
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 949
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5).  // library marker kkossev.commonLib, line 950
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 951
            logWarn "not implemented!" // library marker kkossev.commonLib, line 952
            break // library marker kkossev.commonLib, line 953
        default: // library marker kkossev.commonLib, line 954
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 955
            break // library marker kkossev.commonLib, line 956
    } // library marker kkossev.commonLib, line 957
} // library marker kkossev.commonLib, line 958

List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 960
    List<String> cmds = [] // library marker kkossev.commonLib, line 961
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 962
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 963
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 964
        return // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 967
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 968
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 969
    return cmds // library marker kkossev.commonLib, line 970
} // library marker kkossev.commonLib, line 971

List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 973
    List<String> cmds = [] // library marker kkossev.commonLib, line 974
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 975
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 976
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 977
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 978
    return cmds // library marker kkossev.commonLib, line 979
} // library marker kkossev.commonLib, line 980

List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 982
    List<String> cmds = [] // library marker kkossev.commonLib, line 983
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, "00") // library marker kkossev.commonLib, line 984
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 985
    return cmds // library marker kkossev.commonLib, line 986
} // library marker kkossev.commonLib, line 987

List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 989
    List<String> cmds = [] // library marker kkossev.commonLib, line 990
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 991
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 992
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 993
        return // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 996
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 997
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 998
    return cmds // library marker kkossev.commonLib, line 999
} // library marker kkossev.commonLib, line 1000

List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1002
    List<String> cmds = [] // library marker kkossev.commonLib, line 1003
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1004
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1005
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1006
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1007
    return cmds // library marker kkossev.commonLib, line 1008
} // library marker kkossev.commonLib, line 1009

List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1011
    List<String> cmds = [] // library marker kkossev.commonLib, line 1012
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1013
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1014
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1015
    return cmds // library marker kkossev.commonLib, line 1016
} // library marker kkossev.commonLib, line 1017

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1019
    "--- select ---"           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'GroupCommandsHelp'], // library marker kkossev.commonLib, line 1020
    "Add group"                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1021
    "View group"               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1022
    "Get group membership"     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1023
    "Remove group"             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1024
    "Remove all groups"        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1025
    "Add group if identifying" : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1026
] // library marker kkossev.commonLib, line 1027

def zigbeeGroups( command=null, par=null ) // library marker kkossev.commonLib, line 1029
{ // library marker kkossev.commonLib, line 1030
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1031
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 1032
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1033
    if (state.zigbeeGroups['groups'] == null) state.zigbeeGroups['groups'] = [] // library marker kkossev.commonLib, line 1034
    def value // library marker kkossev.commonLib, line 1035
    Boolean validated = false // library marker kkossev.commonLib, line 1036
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1037
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1038
        return // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
    value = GroupCommandsMap[command]?.type == "number" ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1041
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) validated = true // library marker kkossev.commonLib, line 1042
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1043
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1044
        return // library marker kkossev.commonLib, line 1045
    } // library marker kkossev.commonLib, line 1046
    // // library marker kkossev.commonLib, line 1047
    def func // library marker kkossev.commonLib, line 1048
   // try { // library marker kkossev.commonLib, line 1049
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1050
        def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1051
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1052
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1053
 //   } // library marker kkossev.commonLib, line 1054
//    catch (e) { // library marker kkossev.commonLib, line 1055
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1056
//        return // library marker kkossev.commonLib, line 1057
//    } // library marker kkossev.commonLib, line 1058

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1060
    sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1061
} // library marker kkossev.commonLib, line 1062

def GroupCommandsHelp( val ) { // library marker kkossev.commonLib, line 1064
    logWarn "GroupCommands: select one of the commands in this list!"              // library marker kkossev.commonLib, line 1065
} // library marker kkossev.commonLib, line 1066

/* // library marker kkossev.commonLib, line 1068
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1069
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1070
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1071
*/ // library marker kkossev.commonLib, line 1072

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1074
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1075
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1076
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1077
    }     // library marker kkossev.commonLib, line 1078

    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1080
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1081
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1082
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1083
    } // library marker kkossev.commonLib, line 1084
    else if (descMap.attrId in ["4000", "4001", "4002", "4004", "8000", "8001", "8002", "8003"]) { // library marker kkossev.commonLib, line 1085
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    else { // library marker kkossev.commonLib, line 1088
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1089
    } // library marker kkossev.commonLib, line 1090
} // library marker kkossev.commonLib, line 1091

def clearIsDigital()        { state.states["isDigital"] = false } // library marker kkossev.commonLib, line 1093
def switchDebouncingClear() { state.states["debounce"]  = false } // library marker kkossev.commonLib, line 1094
def isRefreshRequestClear() { state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 1095

def toggle() { // library marker kkossev.commonLib, line 1097
    def descriptionText = "central button switch is " // library marker kkossev.commonLib, line 1098
    def state = "" // library marker kkossev.commonLib, line 1099
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1100
        state = "on" // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
    else { // library marker kkossev.commonLib, line 1103
        state = "off" // library marker kkossev.commonLib, line 1104
    } // library marker kkossev.commonLib, line 1105
    descriptionText += state // library marker kkossev.commonLib, line 1106
    sendEvent(name: "switch", value: state, descriptionText: descriptionText, type: "physical", isStateChange: true) // library marker kkossev.commonLib, line 1107
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1108
} // library marker kkossev.commonLib, line 1109

def off() { // library marker kkossev.commonLib, line 1111
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOff(); return } // library marker kkossev.commonLib, line 1112
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1113
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1114
        return // library marker kkossev.commonLib, line 1115
    } // library marker kkossev.commonLib, line 1116
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1117
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1118
    logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1119
    def cmds = zigbee.off() // library marker kkossev.commonLib, line 1120
    /* // library marker kkossev.commonLib, line 1121
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1122
        cmds += zigbee.command(0x0006, 0x00, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1123
    } // library marker kkossev.commonLib, line 1124
        else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1125
            if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1126
                cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "00") // library marker kkossev.commonLib, line 1127
            } // library marker kkossev.commonLib, line 1128
            else { // library marker kkossev.commonLib, line 1129
                cmds = zigbee.command(0xEF00, 0x0, "00010101000100") // library marker kkossev.commonLib, line 1130
            } // library marker kkossev.commonLib, line 1131
        } // library marker kkossev.commonLib, line 1132
        else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1133
            cmds = ["he cmd 0x${device.deviceNetworkId}  0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1134
            logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1135
        } // library marker kkossev.commonLib, line 1136
        else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1137
            cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1138
        } // library marker kkossev.commonLib, line 1139
*/ // library marker kkossev.commonLib, line 1140
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1141
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1142
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1143
        } // library marker kkossev.commonLib, line 1144
        def value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1145
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1146
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1147
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1148
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1149
    } // library marker kkossev.commonLib, line 1150
    else { // library marker kkossev.commonLib, line 1151
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153


    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1156
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

def on() { // library marker kkossev.commonLib, line 1160
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOn(); return } // library marker kkossev.commonLib, line 1161
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1162
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1163
    logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1164
    def cmds = zigbee.on() // library marker kkossev.commonLib, line 1165
/* // library marker kkossev.commonLib, line 1166
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1167
        cmds += zigbee.command(0x0006, 0x01, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1168
    }     // library marker kkossev.commonLib, line 1169
    else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1170
        if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1171
            cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "01") // library marker kkossev.commonLib, line 1172
        } // library marker kkossev.commonLib, line 1173
        else { // library marker kkossev.commonLib, line 1174
            cmds = zigbee.command(0xEF00, 0x0, "00010101000101") // library marker kkossev.commonLib, line 1175
        } // library marker kkossev.commonLib, line 1176
    } // library marker kkossev.commonLib, line 1177
    else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1178
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1179
        logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
    else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1182
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
*/ // library marker kkossev.commonLib, line 1185
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1186
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on' ) { // library marker kkossev.commonLib, line 1187
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1188
        } // library marker kkossev.commonLib, line 1189
        def value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1190
        def descriptionText = "${value}" // library marker kkossev.commonLib, line 1191
        if (logEnable) { descriptionText += " (2)" } // library marker kkossev.commonLib, line 1192
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1193
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
    else { // library marker kkossev.commonLib, line 1196
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1197
    } // library marker kkossev.commonLib, line 1198

    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1200
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1201
} // library marker kkossev.commonLib, line 1202

def sendSwitchEvent( switchValue ) { // library marker kkossev.commonLib, line 1204
    def value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1205
    def map = [:]  // library marker kkossev.commonLib, line 1206
    boolean bWasChange = false // library marker kkossev.commonLib, line 1207
    boolean debounce   = state.states["debounce"] ?: false // library marker kkossev.commonLib, line 1208
    def lastSwitch = state.states["lastSwitch"] ?: "unknown" // library marker kkossev.commonLib, line 1209
    if (value == lastSwitch && (debounce == true || (settings.ignoreDuplicated ?: false) == true)) {    // some devices send only catchall events, some only readattr reports, but some will fire both... // library marker kkossev.commonLib, line 1210
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1211
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1212
        return null // library marker kkossev.commonLib, line 1213
    } // library marker kkossev.commonLib, line 1214
    else { // library marker kkossev.commonLib, line 1215
        logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1216
    } // library marker kkossev.commonLib, line 1217
    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1218
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1219
    if (lastSwitch != value ) { // library marker kkossev.commonLib, line 1220
        bWasChange = true // library marker kkossev.commonLib, line 1221
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1222
        state.states["debounce"]   = true // library marker kkossev.commonLib, line 1223
        state.states["lastSwitch"] = value // library marker kkossev.commonLib, line 1224
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])         // library marker kkossev.commonLib, line 1225
    } // library marker kkossev.commonLib, line 1226
    else { // library marker kkossev.commonLib, line 1227
        state.states["debounce"] = true // library marker kkossev.commonLib, line 1228
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])      // library marker kkossev.commonLib, line 1229
    } // library marker kkossev.commonLib, line 1230

    map.name = "switch" // library marker kkossev.commonLib, line 1232
    map.value = value // library marker kkossev.commonLib, line 1233
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1234
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1235
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1236
        map.isStateChange = true // library marker kkossev.commonLib, line 1237
    } // library marker kkossev.commonLib, line 1238
    else { // library marker kkossev.commonLib, line 1239
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1242
    sendEvent(map) // library marker kkossev.commonLib, line 1243
    clearIsDigital() // library marker kkossev.commonLib, line 1244
} // library marker kkossev.commonLib, line 1245

@Field static final Map powerOnBehaviourOptions = [    // library marker kkossev.commonLib, line 1247
    '0': 'switch off', // library marker kkossev.commonLib, line 1248
    '1': 'switch on', // library marker kkossev.commonLib, line 1249
    '2': 'switch last state' // library marker kkossev.commonLib, line 1250
] // library marker kkossev.commonLib, line 1251

@Field static final Map switchTypeOptions = [    // library marker kkossev.commonLib, line 1253
    '0': 'toggle', // library marker kkossev.commonLib, line 1254
    '1': 'state', // library marker kkossev.commonLib, line 1255
    '2': 'momentary' // library marker kkossev.commonLib, line 1256
] // library marker kkossev.commonLib, line 1257

Map myParseDescriptionAsMap( String description ) // library marker kkossev.commonLib, line 1259
{ // library marker kkossev.commonLib, line 1260
    def descMap = [:] // library marker kkossev.commonLib, line 1261
    try { // library marker kkossev.commonLib, line 1262
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1263
    } // library marker kkossev.commonLib, line 1264
    catch (e1) { // library marker kkossev.commonLib, line 1265
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1266
        // try alternative custom parsing // library marker kkossev.commonLib, line 1267
        descMap = [:] // library marker kkossev.commonLib, line 1268
        try { // library marker kkossev.commonLib, line 1269
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1270
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1271
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1272
            }         // library marker kkossev.commonLib, line 1273
        } // library marker kkossev.commonLib, line 1274
        catch (e2) { // library marker kkossev.commonLib, line 1275
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1276
            return [:] // library marker kkossev.commonLib, line 1277
        } // library marker kkossev.commonLib, line 1278
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1279
    } // library marker kkossev.commonLib, line 1280
    return descMap // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

boolean isTuyaE00xCluster( String description ) // library marker kkossev.commonLib, line 1284
{ // library marker kkossev.commonLib, line 1285
    if(description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1286
        return false  // library marker kkossev.commonLib, line 1287
    } // library marker kkossev.commonLib, line 1288
    // try to parse ... // library marker kkossev.commonLib, line 1289
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1290
    def descMap = [:] // library marker kkossev.commonLib, line 1291
    try { // library marker kkossev.commonLib, line 1292
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1293
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1294
    } // library marker kkossev.commonLib, line 1295
    catch ( e ) { // library marker kkossev.commonLib, line 1296
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1297
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1298
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1299
        return true // library marker kkossev.commonLib, line 1300
    } // library marker kkossev.commonLib, line 1301

    if (descMap.cluster == "E000" && descMap.attrId in ["D001", "D002", "D003"]) { // library marker kkossev.commonLib, line 1303
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1304
    } // library marker kkossev.commonLib, line 1305
    else if (descMap.cluster == "E001" && descMap.attrId == "D010") { // library marker kkossev.commonLib, line 1306
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1307
    } // library marker kkossev.commonLib, line 1308
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") { // library marker kkossev.commonLib, line 1309
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311
    else { // library marker kkossev.commonLib, line 1312
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1313
        return false  // library marker kkossev.commonLib, line 1314
    } // library marker kkossev.commonLib, line 1315
    return true    // processed // library marker kkossev.commonLib, line 1316
} // library marker kkossev.commonLib, line 1317

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1319
boolean otherTuyaOddities( String description ) { // library marker kkossev.commonLib, line 1320
  /* // library marker kkossev.commonLib, line 1321
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1322
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4  // library marker kkossev.commonLib, line 1323
        return true // library marker kkossev.commonLib, line 1324
    } // library marker kkossev.commonLib, line 1325
*/ // library marker kkossev.commonLib, line 1326
    def descMap = [:] // library marker kkossev.commonLib, line 1327
    try { // library marker kkossev.commonLib, line 1328
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
    catch (e1) { // library marker kkossev.commonLib, line 1331
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1332
        // try alternative custom parsing // library marker kkossev.commonLib, line 1333
        descMap = [:] // library marker kkossev.commonLib, line 1334
        try { // library marker kkossev.commonLib, line 1335
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1336
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1337
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1338
            }         // library marker kkossev.commonLib, line 1339
        } // library marker kkossev.commonLib, line 1340
        catch (e2) { // library marker kkossev.commonLib, line 1341
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1342
            return true // library marker kkossev.commonLib, line 1343
        } // library marker kkossev.commonLib, line 1344
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1345
    } // library marker kkossev.commonLib, line 1346
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}         // library marker kkossev.commonLib, line 1347
    if (descMap.attrId == null ) { // library marker kkossev.commonLib, line 1348
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1349
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1350
        return false // library marker kkossev.commonLib, line 1351
    } // library marker kkossev.commonLib, line 1352
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1353
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1354
    // attribute report received // library marker kkossev.commonLib, line 1355
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1356
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1357
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1358
        //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1359
    } // library marker kkossev.commonLib, line 1360
    attrData.each { // library marker kkossev.commonLib, line 1361
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1362
        def map = [:] // library marker kkossev.commonLib, line 1363
        if (it.status == "86") { // library marker kkossev.commonLib, line 1364
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1365
            // TODO - skip parsing? // library marker kkossev.commonLib, line 1366
        } // library marker kkossev.commonLib, line 1367
        switch (it.cluster) { // library marker kkossev.commonLib, line 1368
            case "0000" : // library marker kkossev.commonLib, line 1369
                if (it.attrId in ["FFE0", "FFE1", "FFE2", "FFE4"]) { // library marker kkossev.commonLib, line 1370
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1371
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1372
                } // library marker kkossev.commonLib, line 1373
                else if (it.attrId in ["FFFE", "FFDF"]) { // library marker kkossev.commonLib, line 1374
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1375
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1376
                } // library marker kkossev.commonLib, line 1377
                else { // library marker kkossev.commonLib, line 1378
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1379
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1380
                } // library marker kkossev.commonLib, line 1381
                break // library marker kkossev.commonLib, line 1382
            default : // library marker kkossev.commonLib, line 1383
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1384
                break // library marker kkossev.commonLib, line 1385
        } // switch // library marker kkossev.commonLib, line 1386
    } // for each attribute // library marker kkossev.commonLib, line 1387
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1388
} // library marker kkossev.commonLib, line 1389

private boolean isCircuitBreaker()      { device.getDataValue("manufacturer") in ["_TZ3000_ky0fq4ho"] } // library marker kkossev.commonLib, line 1391
private boolean isRTXCircuitBreaker()   { device.getDataValue("manufacturer") in ["_TZE200_abatw3kj"] } // library marker kkossev.commonLib, line 1392

def parseOnOffAttributes( it ) { // library marker kkossev.commonLib, line 1394
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1395
    def mode // library marker kkossev.commonLib, line 1396
    def attrName // library marker kkossev.commonLib, line 1397
    if (it.value == null) { // library marker kkossev.commonLib, line 1398
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1399
        return // library marker kkossev.commonLib, line 1400
    } // library marker kkossev.commonLib, line 1401
    def value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1402
    switch (it.attrId) { // library marker kkossev.commonLib, line 1403
        case "4000" :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1404
            attrName = "Global Scene Control" // library marker kkossev.commonLib, line 1405
            mode = value == 0 ? "off" : value == 1 ? "on" : null // library marker kkossev.commonLib, line 1406
            break // library marker kkossev.commonLib, line 1407
        case "4001" :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1408
            attrName = "On Time" // library marker kkossev.commonLib, line 1409
            mode = value // library marker kkossev.commonLib, line 1410
            break // library marker kkossev.commonLib, line 1411
        case "4002" :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1412
            attrName = "Off Wait Time" // library marker kkossev.commonLib, line 1413
            mode = value // library marker kkossev.commonLib, line 1414
            break // library marker kkossev.commonLib, line 1415
        case "4003" :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1  // library marker kkossev.commonLib, line 1416
            attrName = "Power On State" // library marker kkossev.commonLib, line 1417
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : "UNKNOWN" // library marker kkossev.commonLib, line 1418
            break // library marker kkossev.commonLib, line 1419
        case "8000" :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1420
            attrName = "Child Lock" // library marker kkossev.commonLib, line 1421
            mode = value == 0 ? "off" : "on" // library marker kkossev.commonLib, line 1422
            break // library marker kkossev.commonLib, line 1423
        case "8001" :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1424
            attrName = "LED mode" // library marker kkossev.commonLib, line 1425
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1426
                mode = value == 0 ? "Always Green" : value == 1 ? "Red when On; Green when Off" : value == 2 ? "Green when On; Red when Off" : value == 3 ? "Always Red" : null // library marker kkossev.commonLib, line 1427
            } // library marker kkossev.commonLib, line 1428
            else { // library marker kkossev.commonLib, line 1429
                mode = value == 0 ? "Disabled"  : value == 1 ? "Lit when On" : value == 2 ? "Lit when Off" : value == 3 ? "Freeze": null // library marker kkossev.commonLib, line 1430
            } // library marker kkossev.commonLib, line 1431
            break // library marker kkossev.commonLib, line 1432
        case "8002" :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1433
            attrName = "Power On State" // library marker kkossev.commonLib, line 1434
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : null // library marker kkossev.commonLib, line 1435
            break // library marker kkossev.commonLib, line 1436
        case "8003" : //  Over current alarm // library marker kkossev.commonLib, line 1437
            attrName = "Over current alarm" // library marker kkossev.commonLib, line 1438
            mode = value == 0 ? "Over Current OK" : value == 1 ? "Over Current Alarm" : null // library marker kkossev.commonLib, line 1439
            break // library marker kkossev.commonLib, line 1440
        default : // library marker kkossev.commonLib, line 1441
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1442
            return // library marker kkossev.commonLib, line 1443
    } // library marker kkossev.commonLib, line 1444
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1445
} // library marker kkossev.commonLib, line 1446

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) { // library marker kkossev.commonLib, line 1448
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1449
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"} // library marker kkossev.commonLib, line 1450
    sendEvent(event) // library marker kkossev.commonLib, line 1451
} // library marker kkossev.commonLib, line 1452

def push() {                // Momentary capability // library marker kkossev.commonLib, line 1454
    logDebug "push momentary" // library marker kkossev.commonLib, line 1455
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(); return }     // library marker kkossev.commonLib, line 1456
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1457
} // library marker kkossev.commonLib, line 1458

def push(buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1460
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(buttonNumber); return }     // library marker kkossev.commonLib, line 1461
    sendButtonEvent(buttonNumber, "pushed", isDigital=true) // library marker kkossev.commonLib, line 1462
} // library marker kkossev.commonLib, line 1463

def doubleTap(buttonNumber) { // library marker kkossev.commonLib, line 1465
    sendButtonEvent(buttonNumber, "doubleTapped", isDigital=true) // library marker kkossev.commonLib, line 1466
} // library marker kkossev.commonLib, line 1467

def hold(buttonNumber) { // library marker kkossev.commonLib, line 1469
    sendButtonEvent(buttonNumber, "held", isDigital=true) // library marker kkossev.commonLib, line 1470
} // library marker kkossev.commonLib, line 1471

def release(buttonNumber) { // library marker kkossev.commonLib, line 1473
    sendButtonEvent(buttonNumber, "released", isDigital=true) // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

void sendNumberOfButtonsEvent(numberOfButtons) { // library marker kkossev.commonLib, line 1477
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1478
} // library marker kkossev.commonLib, line 1479

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1481
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1482
} // library marker kkossev.commonLib, line 1483


/* // library marker kkossev.commonLib, line 1486
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1487
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1488
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1489
*/ // library marker kkossev.commonLib, line 1490
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1491
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1492
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1493
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1494
    } // library marker kkossev.commonLib, line 1495
    else if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1496
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1497
    } // library marker kkossev.commonLib, line 1498
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1499
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1500
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1501
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1502
    } // library marker kkossev.commonLib, line 1503
    else { // library marker kkossev.commonLib, line 1504
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1505
    } // library marker kkossev.commonLib, line 1506
} // library marker kkossev.commonLib, line 1507


def sendLevelControlEvent( rawValue ) { // library marker kkossev.commonLib, line 1510
    def value = rawValue as int // library marker kkossev.commonLib, line 1511
    if (value <0) value = 0 // library marker kkossev.commonLib, line 1512
    if (value >100) value = 100 // library marker kkossev.commonLib, line 1513
    def map = [:]  // library marker kkossev.commonLib, line 1514

    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1516
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1517

    map.name = "level" // library marker kkossev.commonLib, line 1519
    map.value = value // library marker kkossev.commonLib, line 1520
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1521
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1522
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1523
        map.isStateChange = true // library marker kkossev.commonLib, line 1524
    } // library marker kkossev.commonLib, line 1525
    else { // library marker kkossev.commonLib, line 1526
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1527
    } // library marker kkossev.commonLib, line 1528
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1529
    sendEvent(map) // library marker kkossev.commonLib, line 1530
    clearIsDigital() // library marker kkossev.commonLib, line 1531
} // library marker kkossev.commonLib, line 1532

/** // library marker kkossev.commonLib, line 1534
 * Get the level transition rate // library marker kkossev.commonLib, line 1535
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1536
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1537
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1538
 */ // library marker kkossev.commonLib, line 1539
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1540
    int rate = 0 // library marker kkossev.commonLib, line 1541
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1542
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1543
    if (!isOn) { // library marker kkossev.commonLib, line 1544
        currentLevel = 0 // library marker kkossev.commonLib, line 1545
    } // library marker kkossev.commonLib, line 1546
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1547
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1548
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1549
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1550
    } else { // library marker kkossev.commonLib, line 1551
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1552
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1553
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1554
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1555
        } // library marker kkossev.commonLib, line 1556
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1557
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1558
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1559
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1560
        } // library marker kkossev.commonLib, line 1561
    } // library marker kkossev.commonLib, line 1562
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1563
    return rate // library marker kkossev.commonLib, line 1564
} // library marker kkossev.commonLib, line 1565

// Command option that enable changes when off // library marker kkossev.commonLib, line 1567
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1568

/** // library marker kkossev.commonLib, line 1570
 * Constrain a value to a range // library marker kkossev.commonLib, line 1571
 * @param value value to constrain // library marker kkossev.commonLib, line 1572
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1573
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1574
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1575
 */ // library marker kkossev.commonLib, line 1576
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1577
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1578
        return value // library marker kkossev.commonLib, line 1579
    } // library marker kkossev.commonLib, line 1580
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1581
} // library marker kkossev.commonLib, line 1582

/** // library marker kkossev.commonLib, line 1584
 * Constrain a value to a range // library marker kkossev.commonLib, line 1585
 * @param value value to constrain // library marker kkossev.commonLib, line 1586
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1587
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1588
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1589
 */ // library marker kkossev.commonLib, line 1590
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1591
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1592
        return value as Integer // library marker kkossev.commonLib, line 1593
    } // library marker kkossev.commonLib, line 1594
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1595
} // library marker kkossev.commonLib, line 1596

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1598
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1599

/** // library marker kkossev.commonLib, line 1601
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1602
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1603
 * @param commands commands to execute // library marker kkossev.commonLib, line 1604
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1605
 */ // library marker kkossev.commonLib, line 1606
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1607
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1608
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1609
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1610
    } // library marker kkossev.commonLib, line 1611
    return [] // library marker kkossev.commonLib, line 1612
} // library marker kkossev.commonLib, line 1613

def intTo16bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1615
    def hexStr = zigbee.convertToHexString(value.toInteger(),4) // library marker kkossev.commonLib, line 1616
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1617
} // library marker kkossev.commonLib, line 1618

def intTo8bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1620
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1621
} // library marker kkossev.commonLib, line 1622

/** // library marker kkossev.commonLib, line 1624
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1625
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1626
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1627
 */ // library marker kkossev.commonLib, line 1628
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1629
    List<String> cmds = [] // library marker kkossev.commonLib, line 1630
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1631
    final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1632
    final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1633
    final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1634
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1635
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1636
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1637
    } // library marker kkossev.commonLib, line 1638
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1639
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1640
    /* // library marker kkossev.commonLib, line 1641
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1642
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1643
    */ // library marker kkossev.commonLib, line 1644
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1645
    String endpointId = "01"     // TODO !!! // library marker kkossev.commonLib, line 1646
     cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1647

    return cmds // library marker kkossev.commonLib, line 1649
} // library marker kkossev.commonLib, line 1650


/** // library marker kkossev.commonLib, line 1653
 * Set Level Command // library marker kkossev.commonLib, line 1654
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1655
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1656
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1657
 */ // library marker kkossev.commonLib, line 1658
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1659
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1660
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1661
    if (DEVICE_TYPE in  ["Bulb"]) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1662
    else { // library marker kkossev.commonLib, line 1663
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1664
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1665
        /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1666
    } // library marker kkossev.commonLib, line 1667
} // library marker kkossev.commonLib, line 1668

/* // library marker kkossev.commonLib, line 1670
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1671
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1672
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1673
*/ // library marker kkossev.commonLib, line 1674
void parseColorControlCluster(final Map descMap, description) { // library marker kkossev.commonLib, line 1675
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1676
    if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1677
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1678
    } // library marker kkossev.commonLib, line 1679
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1680
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1681
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1682
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1683
    } // library marker kkossev.commonLib, line 1684
    else { // library marker kkossev.commonLib, line 1685
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1686
    } // library marker kkossev.commonLib, line 1687
} // library marker kkossev.commonLib, line 1688

/* // library marker kkossev.commonLib, line 1690
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1691
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1692
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1693
*/ // library marker kkossev.commonLib, line 1694
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1695
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1696
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1697
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1698
    def lux = value > 0 ? Math.round(Math.pow(10,(value/10000))) : 0 // library marker kkossev.commonLib, line 1699
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1700
} // library marker kkossev.commonLib, line 1701

void handleIlluminanceEvent( illuminance, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1703
    def eventMap = [:] // library marker kkossev.commonLib, line 1704
    if (state.stats != null) state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1705
    eventMap.name = "illuminance" // library marker kkossev.commonLib, line 1706
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1707
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1708
    eventMap.type = isDigital ? "digital" : "physical" // library marker kkossev.commonLib, line 1709
    eventMap.unit = "lx" // library marker kkossev.commonLib, line 1710
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1711
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1712
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1713
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1714
    Integer lastIllum = device.currentValue("illuminance") ?: 0 // library marker kkossev.commonLib, line 1715
    Integer delta = Math.abs(lastIllum- illumCorrected) // library marker kkossev.commonLib, line 1716
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1717
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1718
        return // library marker kkossev.commonLib, line 1719
    } // library marker kkossev.commonLib, line 1720
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1721
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1722
        unschedule("sendDelayedIllumEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1723
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1724
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1725
    }         // library marker kkossev.commonLib, line 1726
    else {         // queue the event // library marker kkossev.commonLib, line 1727
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1728
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1729
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1730
    } // library marker kkossev.commonLib, line 1731
} // library marker kkossev.commonLib, line 1732

private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1734
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1735
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1736
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1737
} // library marker kkossev.commonLib, line 1738

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1740


/* // library marker kkossev.commonLib, line 1743
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1744
 * temperature // library marker kkossev.commonLib, line 1745
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1746
*/ // library marker kkossev.commonLib, line 1747
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1748
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1749
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1750
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1751
    handleTemperatureEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1752
} // library marker kkossev.commonLib, line 1753

void handleTemperatureEvent( Float temperature, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1755
    def eventMap = [:] // library marker kkossev.commonLib, line 1756
    if (state.stats != null) state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1757
    eventMap.name = "temperature" // library marker kkossev.commonLib, line 1758
    def Scale = location.temperatureScale // library marker kkossev.commonLib, line 1759
    if (Scale == "F") { // library marker kkossev.commonLib, line 1760
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1761
        eventMap.unit = "\u00B0"+"F" // library marker kkossev.commonLib, line 1762
    } // library marker kkossev.commonLib, line 1763
    else { // library marker kkossev.commonLib, line 1764
        eventMap.unit = "\u00B0"+"C" // library marker kkossev.commonLib, line 1765
    } // library marker kkossev.commonLib, line 1766
    def tempCorrected = (temperature + safeToDouble(settings?.temperatureOffset ?: 0)) as Float // library marker kkossev.commonLib, line 1767
    eventMap.value  =  (Math.round(tempCorrected * 10) / 10.0) as Float // library marker kkossev.commonLib, line 1768
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1769
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1770
    if (state.states["isRefresh"] == true) { // library marker kkossev.commonLib, line 1771
        eventMap.descriptionText += " [refresh]" // library marker kkossev.commonLib, line 1772
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1773
    }    // library marker kkossev.commonLib, line 1774
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1775
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1776
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1777
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1778
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1779
        unschedule("sendDelayedTempEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1780
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1781
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1782
    }         // library marker kkossev.commonLib, line 1783
    else {         // queue the event // library marker kkossev.commonLib, line 1784
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1785
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1786
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1787
    } // library marker kkossev.commonLib, line 1788
} // library marker kkossev.commonLib, line 1789

private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1791
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1792
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1793
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1794
} // library marker kkossev.commonLib, line 1795

/* // library marker kkossev.commonLib, line 1797
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1798
 * humidity // library marker kkossev.commonLib, line 1799
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1800
*/ // library marker kkossev.commonLib, line 1801
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1802
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1803
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1804
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1805
    handleHumidityEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1806
} // library marker kkossev.commonLib, line 1807

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1809
    def eventMap = [:] // library marker kkossev.commonLib, line 1810
    if (state.stats != null) state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1811
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1812
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) { // library marker kkossev.commonLib, line 1813
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})" // library marker kkossev.commonLib, line 1814
        return // library marker kkossev.commonLib, line 1815
    } // library marker kkossev.commonLib, line 1816
    eventMap.value = Math.round(humidityAsDouble) // library marker kkossev.commonLib, line 1817
    eventMap.name = "humidity" // library marker kkossev.commonLib, line 1818
    eventMap.unit = "% RH" // library marker kkossev.commonLib, line 1819
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1820
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1821
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}" // library marker kkossev.commonLib, line 1822
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1823
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1824
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1825
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1826
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1827
        unschedule("sendDelayedHumidityEvent") // library marker kkossev.commonLib, line 1828
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1829
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
    else { // library marker kkossev.commonLib, line 1832
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1833
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1834
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1835
    } // library marker kkossev.commonLib, line 1836
} // library marker kkossev.commonLib, line 1837

private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1839
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1840
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1841
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1842
} // library marker kkossev.commonLib, line 1843

/* // library marker kkossev.commonLib, line 1845
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1846
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1847
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1848
*/ // library marker kkossev.commonLib, line 1849

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1851
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1852
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1853
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1854
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1855
        parseElectricalMeasureClusterSwitch(descMap) // library marker kkossev.commonLib, line 1856
    } // library marker kkossev.commonLib, line 1857
    else { // library marker kkossev.commonLib, line 1858
        logWarn "parseElectricalMeasureCluster is NOT implemented1" // library marker kkossev.commonLib, line 1859
    } // library marker kkossev.commonLib, line 1860
} // library marker kkossev.commonLib, line 1861

/* // library marker kkossev.commonLib, line 1863
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1864
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1865
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1866
*/ // library marker kkossev.commonLib, line 1867

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1869
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1870
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1871
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1872
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1873
        parseMeteringClusterSwitch(descMap) // library marker kkossev.commonLib, line 1874
    } // library marker kkossev.commonLib, line 1875
    else { // library marker kkossev.commonLib, line 1876
        logWarn "parseMeteringCluster is NOT implemented1" // library marker kkossev.commonLib, line 1877
    } // library marker kkossev.commonLib, line 1878
} // library marker kkossev.commonLib, line 1879


/* // library marker kkossev.commonLib, line 1882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1883
 * pm2.5 // library marker kkossev.commonLib, line 1884
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1885
*/ // library marker kkossev.commonLib, line 1886
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1887
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1888
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1889
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1890
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1891
    //logDebug "pm25 float value = ${floatValue}" // library marker kkossev.commonLib, line 1892
    handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1893
} // library marker kkossev.commonLib, line 1894
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1895

/* // library marker kkossev.commonLib, line 1897
void handlePm25Event( Integer pm25, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1898
    def eventMap = [:] // library marker kkossev.commonLib, line 1899
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1900
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0) // library marker kkossev.commonLib, line 1901
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) { // library marker kkossev.commonLib, line 1902
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})" // library marker kkossev.commonLib, line 1903
        return // library marker kkossev.commonLib, line 1904
    } // library marker kkossev.commonLib, line 1905
    eventMap.value = Math.round(pm25AsDouble) // library marker kkossev.commonLib, line 1906
    eventMap.name = "pm25" // library marker kkossev.commonLib, line 1907
    eventMap.unit = "\u03BCg/m3"    //"mg/m3" // library marker kkossev.commonLib, line 1908
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1909
    eventMap.isStateChange = true // library marker kkossev.commonLib, line 1910
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}" // library marker kkossev.commonLib, line 1911
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000) // library marker kkossev.commonLib, line 1912
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1913
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1914
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1915
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1916
        unschedule("sendDelayedPm25Event") // library marker kkossev.commonLib, line 1917
        state.lastRx['pm25Time'] = now() // library marker kkossev.commonLib, line 1918
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1919
    } // library marker kkossev.commonLib, line 1920
    else { // library marker kkossev.commonLib, line 1921
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1922
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1923
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1924
    } // library marker kkossev.commonLib, line 1925
} // library marker kkossev.commonLib, line 1926

private void sendDelayedPm25Event(Map eventMap) { // library marker kkossev.commonLib, line 1928
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1929
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1930
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1931
} // library marker kkossev.commonLib, line 1932
*/ // library marker kkossev.commonLib, line 1933

/* // library marker kkossev.commonLib, line 1935
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1936
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1937
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1938
*/ // library marker kkossev.commonLib, line 1939
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1940
    if (DEVICE_TYPE in ["AirQuality"]) { // library marker kkossev.commonLib, line 1941
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1942
    } // library marker kkossev.commonLib, line 1943
    else if (DEVICE_TYPE in ["AqaraCube"]) { // library marker kkossev.commonLib, line 1944
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1945
    } // library marker kkossev.commonLib, line 1946
    else { // library marker kkossev.commonLib, line 1947
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1948
    } // library marker kkossev.commonLib, line 1949
} // library marker kkossev.commonLib, line 1950


/* // library marker kkossev.commonLib, line 1953
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1954
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1955
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1956
*/ // library marker kkossev.commonLib, line 1957

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1959
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1960
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1961
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1962
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1963
    if (DEVICE_TYPE in  ["AqaraCube"]) { // library marker kkossev.commonLib, line 1964
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1965
    } // library marker kkossev.commonLib, line 1966
    else { // library marker kkossev.commonLib, line 1967
        handleMultistateInputEvent(value as Integer) // library marker kkossev.commonLib, line 1968
    } // library marker kkossev.commonLib, line 1969
} // library marker kkossev.commonLib, line 1970

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1972
    def eventMap = [:] // library marker kkossev.commonLib, line 1973
    eventMap.value = value // library marker kkossev.commonLib, line 1974
    eventMap.name = "multistateInput" // library marker kkossev.commonLib, line 1975
    eventMap.unit = "" // library marker kkossev.commonLib, line 1976
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1977
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1978
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1979
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1980
} // library marker kkossev.commonLib, line 1981

/* // library marker kkossev.commonLib, line 1983
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1984
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1985
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1986
*/ // library marker kkossev.commonLib, line 1987

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1989
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1990
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1991
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1992
    } // library marker kkossev.commonLib, line 1993
    else { // library marker kkossev.commonLib, line 1994
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1995
    } // library marker kkossev.commonLib, line 1996
} // library marker kkossev.commonLib, line 1997

/* // library marker kkossev.commonLib, line 1999
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2000
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 2001
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2002
*/ // library marker kkossev.commonLib, line 2003
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 2004
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2005
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.commonLib, line 2006
        parseThermostatClusterThermostat(descMap) // library marker kkossev.commonLib, line 2007
    } // library marker kkossev.commonLib, line 2008
    else { // library marker kkossev.commonLib, line 2009
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2010
    } // library marker kkossev.commonLib, line 2011
} // library marker kkossev.commonLib, line 2012

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2014

def parseFC11Cluster( descMap ) { // library marker kkossev.commonLib, line 2016
    if (DEVICE_TYPE in ["Thermostat"])     { parseFC11ClusterThermostat(descMap) }     // library marker kkossev.commonLib, line 2017
    else { // library marker kkossev.commonLib, line 2018
        logWarn "Unprocessed cluster 0xFC11 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2019
    } // library marker kkossev.commonLib, line 2020
} // library marker kkossev.commonLib, line 2021

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2023

def parseE002Cluster( descMap ) { // library marker kkossev.commonLib, line 2025
    if (DEVICE_TYPE in ["Radar"])     { parseE002ClusterRadar(descMap) }     // library marker kkossev.commonLib, line 2026
    else { // library marker kkossev.commonLib, line 2027
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2028
    } // library marker kkossev.commonLib, line 2029
} // library marker kkossev.commonLib, line 2030


/* // library marker kkossev.commonLib, line 2033
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2034
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2035
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2036
*/ // library marker kkossev.commonLib, line 2037
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2038
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2039
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2040

// Tuya Commands // library marker kkossev.commonLib, line 2042
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2043
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2044
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2045
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2046
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2047

// tuya DP type // library marker kkossev.commonLib, line 2049
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ] // library marker kkossev.commonLib, line 2050
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ] // library marker kkossev.commonLib, line 2051
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2052
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ] // library marker kkossev.commonLib, line 2053
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ] // library marker kkossev.commonLib, line 2054
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2055


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2058
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME // library marker kkossev.commonLib, line 2059
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2060
        def offset = 0 // library marker kkossev.commonLib, line 2061
        try { // library marker kkossev.commonLib, line 2062
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2063
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}" // library marker kkossev.commonLib, line 2064
        } // library marker kkossev.commonLib, line 2065
        catch(e) { // library marker kkossev.commonLib, line 2066
            logWarn "cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 2067
        } // library marker kkossev.commonLib, line 2068
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8)) // library marker kkossev.commonLib, line 2069
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2070
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2071
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2072
    } // library marker kkossev.commonLib, line 2073
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2074
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2075
        def status = descMap?.data[1]             // library marker kkossev.commonLib, line 2076
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2077
        if (status != "00") { // library marker kkossev.commonLib, line 2078
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                 // library marker kkossev.commonLib, line 2079
        } // library marker kkossev.commonLib, line 2080
    }  // library marker kkossev.commonLib, line 2081
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02" || descMap?.command == "05" || descMap?.command == "06")) // library marker kkossev.commonLib, line 2082
    { // library marker kkossev.commonLib, line 2083
        def dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2084
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2085
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2086
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2087
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2088
            return // library marker kkossev.commonLib, line 2089
        } // library marker kkossev.commonLib, line 2090
        for (int i = 0; i < (dataLen-4); ) { // library marker kkossev.commonLib, line 2091
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2092
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2093
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i])  // library marker kkossev.commonLib, line 2094
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2095
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2096
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2097
            i = i + fncmd_len + 4; // library marker kkossev.commonLib, line 2098
        } // library marker kkossev.commonLib, line 2099
    } // library marker kkossev.commonLib, line 2100
    else { // library marker kkossev.commonLib, line 2101
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2102
    } // library marker kkossev.commonLib, line 2103
} // library marker kkossev.commonLib, line 2104

void processTuyaDP(descMap, dp, dp_id, fncmd, dp_len=0) { // library marker kkossev.commonLib, line 2106
    if (DEVICE_TYPE in ["Radar"])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2107
    if (DEVICE_TYPE in ["Fingerbot"])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2108
    // check if the method  method exists // library marker kkossev.commonLib, line 2109
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2110
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0  // library marker kkossev.commonLib, line 2111
            return // library marker kkossev.commonLib, line 2112
        }     // library marker kkossev.commonLib, line 2113
    } // library marker kkossev.commonLib, line 2114
    switch (dp) { // library marker kkossev.commonLib, line 2115
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2116
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2117
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2118
            } // library marker kkossev.commonLib, line 2119
            else { // library marker kkossev.commonLib, line 2120
                sendSwitchEvent(fncmd) // library marker kkossev.commonLib, line 2121
            } // library marker kkossev.commonLib, line 2122
            break // library marker kkossev.commonLib, line 2123
        case 0x02 : // library marker kkossev.commonLib, line 2124
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2125
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2126
            } // library marker kkossev.commonLib, line 2127
            else { // library marker kkossev.commonLib, line 2128
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2129
            } // library marker kkossev.commonLib, line 2130
            break // library marker kkossev.commonLib, line 2131
        case 0x04 : // battery // library marker kkossev.commonLib, line 2132
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2133
            break // library marker kkossev.commonLib, line 2134
        default : // library marker kkossev.commonLib, line 2135
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2136
            break             // library marker kkossev.commonLib, line 2137
    } // library marker kkossev.commonLib, line 2138
} // library marker kkossev.commonLib, line 2139

private int getTuyaAttributeValue(ArrayList _data, index) { // library marker kkossev.commonLib, line 2141
    int retValue = 0 // library marker kkossev.commonLib, line 2142

    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2144
        int dataLength = _data[5+index] as Integer // library marker kkossev.commonLib, line 2145
        int power = 1; // library marker kkossev.commonLib, line 2146
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2147
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5]) // library marker kkossev.commonLib, line 2148
            power = power * 256 // library marker kkossev.commonLib, line 2149
        } // library marker kkossev.commonLib, line 2150
    } // library marker kkossev.commonLib, line 2151
    return retValue // library marker kkossev.commonLib, line 2152
} // library marker kkossev.commonLib, line 2153


private sendTuyaCommand(dp, dp_type, fncmd) { // library marker kkossev.commonLib, line 2156
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2157
    def ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2158
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2159
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2160

    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd ) // library marker kkossev.commonLib, line 2162
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2163
    return cmds // library marker kkossev.commonLib, line 2164
} // library marker kkossev.commonLib, line 2165

private getPACKET_ID() { // library marker kkossev.commonLib, line 2167
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)  // library marker kkossev.commonLib, line 2168
} // library marker kkossev.commonLib, line 2169

def tuyaTest( dpCommand, dpValue, dpTypeString ) { // library marker kkossev.commonLib, line 2171
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2172
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2173
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2174

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" // library marker kkossev.commonLib, line 2176

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2178
} // library marker kkossev.commonLib, line 2179

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2181
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2182

def tuyaBlackMagic() { // library marker kkossev.commonLib, line 2184
    def ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2185
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2186
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200) // library marker kkossev.commonLib, line 2187
} // library marker kkossev.commonLib, line 2188

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2190
    List<String> cmds = [] // library marker kkossev.commonLib, line 2191
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2192
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",] // library marker kkossev.commonLib, line 2193
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2194
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2195
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2196
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2197
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // TVOC only // library marker kkossev.commonLib, line 2198
        } // library marker kkossev.commonLib, line 2199
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2200
        logDebug "sent aqaraBlackMagic()" // library marker kkossev.commonLib, line 2201
    } // library marker kkossev.commonLib, line 2202
    else { // library marker kkossev.commonLib, line 2203
        logDebug "aqaraBlackMagic() was SKIPPED" // library marker kkossev.commonLib, line 2204
    } // library marker kkossev.commonLib, line 2205
} // library marker kkossev.commonLib, line 2206


/** // library marker kkossev.commonLib, line 2209
 * initializes the device // library marker kkossev.commonLib, line 2210
 * Invoked from configure() // library marker kkossev.commonLib, line 2211
 * @return zigbee commands // library marker kkossev.commonLib, line 2212
 */ // library marker kkossev.commonLib, line 2213
def initializeDevice() { // library marker kkossev.commonLib, line 2214
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2215
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2216

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2218
    if (DEVICE_TYPE in  ["AirQuality"])          { return initializeDeviceAirQuality() } // library marker kkossev.commonLib, line 2219
    else if (DEVICE_TYPE in  ["IRBlaster"])      { return initializeDeviceIrBlaster() } // library marker kkossev.commonLib, line 2220
    else if (DEVICE_TYPE in  ["Radar"])          { return initializeDeviceRadar() } // library marker kkossev.commonLib, line 2221
    else if (DEVICE_TYPE in  ["ButtonDimmer"])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2222
    else if (DEVICE_TYPE in  ["Thermostat"])     { return initializeDeviceThermostat() } // library marker kkossev.commonLib, line 2223


    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2226
    if (DEVICE_TYPE in  ["THSensor"]) { // library marker kkossev.commonLib, line 2227
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2228
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2229
    } // library marker kkossev.commonLib, line 2230
    // // library marker kkossev.commonLib, line 2231
    if (cmds == []) { // library marker kkossev.commonLib, line 2232
        cmds = ["delay 299"] // library marker kkossev.commonLib, line 2233
    } // library marker kkossev.commonLib, line 2234
    return cmds // library marker kkossev.commonLib, line 2235
} // library marker kkossev.commonLib, line 2236


/** // library marker kkossev.commonLib, line 2239
 * configures the device // library marker kkossev.commonLib, line 2240
 * Invoked from updated() // library marker kkossev.commonLib, line 2241
 * @return zigbee commands // library marker kkossev.commonLib, line 2242
 */ // library marker kkossev.commonLib, line 2243
def configureDevice() { // library marker kkossev.commonLib, line 2244
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2245
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2246

    if (DEVICE_TYPE in  ["AirQuality"]) { cmds += configureDeviceAirQuality() } // library marker kkossev.commonLib, line 2248
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2249
    else if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2250
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() } // library marker kkossev.commonLib, line 2251
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureDeviceIrBlaster() } // library marker kkossev.commonLib, line 2252
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += configureDeviceRadar() } // library marker kkossev.commonLib, line 2253
    else if (DEVICE_TYPE in  ["ButtonDimmer"]) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2254
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2255
    if (cmds == []) {  // library marker kkossev.commonLib, line 2256
        cmds = ["delay 277",] // library marker kkossev.commonLib, line 2257
    } // library marker kkossev.commonLib, line 2258
    sendZigbeeCommands(cmds)   // library marker kkossev.commonLib, line 2259
} // library marker kkossev.commonLib, line 2260

/* // library marker kkossev.commonLib, line 2262
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2263
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2264
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2265
*/ // library marker kkossev.commonLib, line 2266

def refresh() { // library marker kkossev.commonLib, line 2268
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2269
    checkDriverVersion() // library marker kkossev.commonLib, line 2270
    List<String> cmds = [] // library marker kkossev.commonLib, line 2271
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2272

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2274
    if (DEVICE_TYPE in  ["AqaraCube"])       { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2275
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2276
    else if (DEVICE_TYPE in  ["AirQuality"]) { cmds += refreshAirQuality() } // library marker kkossev.commonLib, line 2277
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += refreshSwitch() } // library marker kkossev.commonLib, line 2278
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += refreshIrBlaster() } // library marker kkossev.commonLib, line 2279
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += refreshRadar() } // library marker kkossev.commonLib, line 2280
    else if (DEVICE_TYPE in  ["Thermostat"]) { cmds += refreshThermostat() } // library marker kkossev.commonLib, line 2281
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2282
    else { // library marker kkossev.commonLib, line 2283
        // generic refresh handling, based on teh device capabilities  // library marker kkossev.commonLib, line 2284
        if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 2285
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage // library marker kkossev.commonLib, line 2286
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)         // battery percentage  // library marker kkossev.commonLib, line 2287
        } // library marker kkossev.commonLib, line 2288
        if (DEVICE_TYPE in  ["Plug", "Dimmer"]) { // library marker kkossev.commonLib, line 2289
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200) // library marker kkossev.commonLib, line 2290
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2291
        } // library marker kkossev.commonLib, line 2292
        if (DEVICE_TYPE in  ["Dimmer"]) { // library marker kkossev.commonLib, line 2293
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2294
        } // library marker kkossev.commonLib, line 2295
        if (DEVICE_TYPE in  ["THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 2296
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2297
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2298
        } // library marker kkossev.commonLib, line 2299
    } // library marker kkossev.commonLib, line 2300

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2302
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2303
    } // library marker kkossev.commonLib, line 2304
    else { // library marker kkossev.commonLib, line 2305
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2306
    } // library marker kkossev.commonLib, line 2307
} // library marker kkossev.commonLib, line 2308

def setRefreshRequest()   { if (state.states == null) {state.states = [:]};   state.states["isRefresh"] = true; runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds // library marker kkossev.commonLib, line 2310
def clearRefreshRequest() { if (state.states == null) {state.states = [:] }; state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 2311

void clearInfoEvent() { // library marker kkossev.commonLib, line 2313
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2314
} // library marker kkossev.commonLib, line 2315

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2317
    if (info == null || info == "clear") { // library marker kkossev.commonLib, line 2318
        logDebug "clearing the Status event" // library marker kkossev.commonLib, line 2319
        sendEvent(name: "Status", value: "clear", isDigital: true) // library marker kkossev.commonLib, line 2320
    } // library marker kkossev.commonLib, line 2321
    else { // library marker kkossev.commonLib, line 2322
        logInfo "${info}" // library marker kkossev.commonLib, line 2323
        sendEvent(name: "Status", value: info, isDigital: true) // library marker kkossev.commonLib, line 2324
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2325
    } // library marker kkossev.commonLib, line 2326
} // library marker kkossev.commonLib, line 2327

def ping() { // library marker kkossev.commonLib, line 2329
    if (!(isAqaraTVOC_OLD())) { // library marker kkossev.commonLib, line 2330
        if (state.lastTx == nill ) state.lastTx = [:]  // library marker kkossev.commonLib, line 2331
        state.lastTx["pingTime"] = new Date().getTime() // library marker kkossev.commonLib, line 2332
        if (state.states == nill ) state.states = [:]  // library marker kkossev.commonLib, line 2333
        state.states["isPing"] = true // library marker kkossev.commonLib, line 2334
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2335
        if (!isVirtual()) { // library marker kkossev.commonLib, line 2336
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2337
        } // library marker kkossev.commonLib, line 2338
        else { // library marker kkossev.commonLib, line 2339
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2340
        } // library marker kkossev.commonLib, line 2341
        logDebug 'ping...' // library marker kkossev.commonLib, line 2342
    } // library marker kkossev.commonLib, line 2343
    else { // library marker kkossev.commonLib, line 2344
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2345
        logInfo "ping() command is not available for this sleepy device." // library marker kkossev.commonLib, line 2346
        sendRttEvent("n/a") // library marker kkossev.commonLib, line 2347
    } // library marker kkossev.commonLib, line 2348
} // library marker kkossev.commonLib, line 2349

def virtualPong() { // library marker kkossev.commonLib, line 2351
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2352
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2353
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger() // library marker kkossev.commonLib, line 2354
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2355
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2356
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2357
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2358
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2359
        sendRttEvent() // library marker kkossev.commonLib, line 2360
    } // library marker kkossev.commonLib, line 2361
    else { // library marker kkossev.commonLib, line 2362
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2363
    } // library marker kkossev.commonLib, line 2364
    state.states["isPing"] = false // library marker kkossev.commonLib, line 2365
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2366
} // library marker kkossev.commonLib, line 2367

/** // library marker kkossev.commonLib, line 2369
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2370
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2371
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2372
 * @return none // library marker kkossev.commonLib, line 2373
 */ // library marker kkossev.commonLib, line 2374
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2375
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2376
    if (state.lastTx == null ) state.lastTx = [:] // library marker kkossev.commonLib, line 2377
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: now).toInteger() // library marker kkossev.commonLib, line 2378
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats["pingsMin"]} max=${state.stats["pingsMax"]} average=${state.stats["pingsAvg"]})" // library marker kkossev.commonLib, line 2379
    if (value == null) { // library marker kkossev.commonLib, line 2380
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2381
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)     // library marker kkossev.commonLib, line 2382
    } // library marker kkossev.commonLib, line 2383
    else { // library marker kkossev.commonLib, line 2384
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2385
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2386
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)     // library marker kkossev.commonLib, line 2387
    } // library marker kkossev.commonLib, line 2388
} // library marker kkossev.commonLib, line 2389

/** // library marker kkossev.commonLib, line 2391
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2392
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2393
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2394
 */ // library marker kkossev.commonLib, line 2395
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2396
    if (cluster != null) { // library marker kkossev.commonLib, line 2397
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2398
    } // library marker kkossev.commonLib, line 2399
    else { // library marker kkossev.commonLib, line 2400
        logWarn "cluster is NULL!" // library marker kkossev.commonLib, line 2401
        return "NULL" // library marker kkossev.commonLib, line 2402
    } // library marker kkossev.commonLib, line 2403
} // library marker kkossev.commonLib, line 2404

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2406
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2407
} // library marker kkossev.commonLib, line 2408

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2410
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2411
    sendRttEvent("timeout") // library marker kkossev.commonLib, line 2412
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2413
} // library marker kkossev.commonLib, line 2414

/** // library marker kkossev.commonLib, line 2416
 * Schedule a device health check // library marker kkossev.commonLib, line 2417
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2418
 */ // library marker kkossev.commonLib, line 2419
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2420
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2421
        String cron = getCron( intervalMins*60 ) // library marker kkossev.commonLib, line 2422
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2423
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2424
    } // library marker kkossev.commonLib, line 2425
    else { // library marker kkossev.commonLib, line 2426
        logWarn "deviceHealthCheck is not scheduled!" // library marker kkossev.commonLib, line 2427
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2428
    } // library marker kkossev.commonLib, line 2429
} // library marker kkossev.commonLib, line 2430

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2432
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2433
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2434
    logWarn "device health check is disabled!" // library marker kkossev.commonLib, line 2435

} // library marker kkossev.commonLib, line 2437

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2439
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2440
    if(state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2441
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2442
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {    // library marker kkossev.commonLib, line 2443
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2444
        logInfo "is now online!" // library marker kkossev.commonLib, line 2445
    } // library marker kkossev.commonLib, line 2446
} // library marker kkossev.commonLib, line 2447


def deviceHealthCheck() { // library marker kkossev.commonLib, line 2450
    checkDriverVersion() // library marker kkossev.commonLib, line 2451
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2452
    def ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2453
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2454
        if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2455
            logWarn "not present!" // library marker kkossev.commonLib, line 2456
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2457
        } // library marker kkossev.commonLib, line 2458
    } // library marker kkossev.commonLib, line 2459
    else { // library marker kkossev.commonLib, line 2460
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2461
    } // library marker kkossev.commonLib, line 2462
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2463
} // library marker kkossev.commonLib, line 2464

void sendHealthStatusEvent(value) { // library marker kkossev.commonLib, line 2466
    def descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2467
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2468
    if (value == 'online') { // library marker kkossev.commonLib, line 2469
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2470
    } // library marker kkossev.commonLib, line 2471
    else { // library marker kkossev.commonLib, line 2472
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2473
    } // library marker kkossev.commonLib, line 2474
} // library marker kkossev.commonLib, line 2475



/** // library marker kkossev.commonLib, line 2479
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2480
 */ // library marker kkossev.commonLib, line 2481
void autoPoll() { // library marker kkossev.commonLib, line 2482
    logDebug "autoPoll()..." // library marker kkossev.commonLib, line 2483
    checkDriverVersion() // library marker kkossev.commonLib, line 2484
    List<String> cmds = [] // library marker kkossev.commonLib, line 2485
    if (state.states == null) state.states = [:] // library marker kkossev.commonLib, line 2486
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2487

    if (DEVICE_TYPE in  ["AirQuality"]) { // library marker kkossev.commonLib, line 2489
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2490
    } // library marker kkossev.commonLib, line 2491

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2493
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2494
    }     // library marker kkossev.commonLib, line 2495
} // library marker kkossev.commonLib, line 2496


/** // library marker kkossev.commonLib, line 2499
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2500
 */ // library marker kkossev.commonLib, line 2501
void updated() { // library marker kkossev.commonLib, line 2502
    logInfo 'updated...' // library marker kkossev.commonLib, line 2503
    checkDriverVersion() // library marker kkossev.commonLib, line 2504
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2505
    unschedule() // library marker kkossev.commonLib, line 2506

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2508
        logTrace settings // library marker kkossev.commonLib, line 2509
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2510
    } // library marker kkossev.commonLib, line 2511
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2512
        logTrace settings // library marker kkossev.commonLib, line 2513
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2514
    }     // library marker kkossev.commonLib, line 2515

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2517
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2518
        // schedule the periodic timer // library marker kkossev.commonLib, line 2519
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2520
        if (interval > 0) { // library marker kkossev.commonLib, line 2521
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2522
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2523
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2524
        } // library marker kkossev.commonLib, line 2525
    } // library marker kkossev.commonLib, line 2526
    else { // library marker kkossev.commonLib, line 2527
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2528
        log.info "Health Check is disabled!" // library marker kkossev.commonLib, line 2529
    } // library marker kkossev.commonLib, line 2530

    if (DEVICE_TYPE in ["AirQuality"])  { updatedAirQuality() } // library marker kkossev.commonLib, line 2532
    if (DEVICE_TYPE in ["IRBlaster"])   { updatedIrBlaster() } // library marker kkossev.commonLib, line 2533
    if (DEVICE_TYPE in ["Thermostat"])  { updatedThermostat() } // library marker kkossev.commonLib, line 2534

    //configureDevice()    // sends Zigbee commands  // commented out 11/18/2023 // library marker kkossev.commonLib, line 2536

    sendInfoEvent("updated") // library marker kkossev.commonLib, line 2538
} // library marker kkossev.commonLib, line 2539

/** // library marker kkossev.commonLib, line 2541
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2542
 */ // library marker kkossev.commonLib, line 2543
void logsOff() { // library marker kkossev.commonLib, line 2544
    logInfo "debug logging disabled..." // library marker kkossev.commonLib, line 2545
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2546
} // library marker kkossev.commonLib, line 2547
void traceOff() { // library marker kkossev.commonLib, line 2548
    logInfo "trace logging disabled..." // library marker kkossev.commonLib, line 2549
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2550
} // library marker kkossev.commonLib, line 2551

def configure(command) { // library marker kkossev.commonLib, line 2553
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2554
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2555

    Boolean validated = false // library marker kkossev.commonLib, line 2557
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2558
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2559
        return // library marker kkossev.commonLib, line 2560
    } // library marker kkossev.commonLib, line 2561
    // // library marker kkossev.commonLib, line 2562
    def func // library marker kkossev.commonLib, line 2563
   // try { // library marker kkossev.commonLib, line 2564
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2565
        cmds = "$func"() // library marker kkossev.commonLib, line 2566
 //   } // library marker kkossev.commonLib, line 2567
//    catch (e) { // library marker kkossev.commonLib, line 2568
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2569
//        return // library marker kkossev.commonLib, line 2570
//    } // library marker kkossev.commonLib, line 2571

    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2573
} // library marker kkossev.commonLib, line 2574

def configureHelp( val ) { // library marker kkossev.commonLib, line 2576
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2577
} // library marker kkossev.commonLib, line 2578


def loadAllDefaults() { // library marker kkossev.commonLib, line 2581
    logWarn "loadAllDefaults() !!!" // library marker kkossev.commonLib, line 2582
    deleteAllSettings() // library marker kkossev.commonLib, line 2583
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2584
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2585
    deleteAllStates() // library marker kkossev.commonLib, line 2586
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2587
    initialize() // library marker kkossev.commonLib, line 2588
    configure() // library marker kkossev.commonLib, line 2589
    updated() // calls  also   configureDevice() // library marker kkossev.commonLib, line 2590
    sendInfoEvent("All Defaults Loaded! F5 to refresh") // library marker kkossev.commonLib, line 2591
} // library marker kkossev.commonLib, line 2592

/** // library marker kkossev.commonLib, line 2594
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2595
 * Invoked when device is first installed and when the user updates the configuration // library marker kkossev.commonLib, line 2596
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2597
 */ // library marker kkossev.commonLib, line 2598
def configure() { // library marker kkossev.commonLib, line 2599
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2600
    logInfo 'configure...' // library marker kkossev.commonLib, line 2601
    logDebug settings // library marker kkossev.commonLib, line 2602
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2603
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2604
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2605
    } // library marker kkossev.commonLib, line 2606
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2607
    cmds += configureDevice() // library marker kkossev.commonLib, line 2608
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2609
    sendInfoEvent("sent device configuration") // library marker kkossev.commonLib, line 2610
} // library marker kkossev.commonLib, line 2611

/** // library marker kkossev.commonLib, line 2613
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2614
 */ // library marker kkossev.commonLib, line 2615
void installed() { // library marker kkossev.commonLib, line 2616
    logInfo 'installed...' // library marker kkossev.commonLib, line 2617
    // populate some default values for attributes // library marker kkossev.commonLib, line 2618
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2619
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2620
    sendInfoEvent("installed") // library marker kkossev.commonLib, line 2621
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2622
} // library marker kkossev.commonLib, line 2623

/** // library marker kkossev.commonLib, line 2625
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2626
 */ // library marker kkossev.commonLib, line 2627
void initialize() { // library marker kkossev.commonLib, line 2628
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2629
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2630
    updateTuyaVersion() // library marker kkossev.commonLib, line 2631
    updateAqaraVersion() // library marker kkossev.commonLib, line 2632
} // library marker kkossev.commonLib, line 2633


/* // library marker kkossev.commonLib, line 2636
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2637
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2638
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2639
*/ // library marker kkossev.commonLib, line 2640

static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2642
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2643
} // library marker kkossev.commonLib, line 2644

static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2646
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2647
} // library marker kkossev.commonLib, line 2648

void sendZigbeeCommands(ArrayList<String> cmd) { // library marker kkossev.commonLib, line 2650
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2651
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2652
    cmd.each { // library marker kkossev.commonLib, line 2653
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2654
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] } // library marker kkossev.commonLib, line 2655
    } // library marker kkossev.commonLib, line 2656
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2657
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2658
} // library marker kkossev.commonLib, line 2659

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!) " : " ") + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString}) "} // library marker kkossev.commonLib, line 2661

def getDeviceInfo() { // library marker kkossev.commonLib, line 2663
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2664
} // library marker kkossev.commonLib, line 2665

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2667
    return state.destinationEP ?: device.endpointId ?: "01" // library marker kkossev.commonLib, line 2668
} // library marker kkossev.commonLib, line 2669

def checkDriverVersion() { // library marker kkossev.commonLib, line 2671
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2672
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2673
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2674
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2675
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2676
        updateTuyaVersion() // library marker kkossev.commonLib, line 2677
        updateAqaraVersion() // library marker kkossev.commonLib, line 2678
    } // library marker kkossev.commonLib, line 2679
    else { // library marker kkossev.commonLib, line 2680
        // no driver version change // library marker kkossev.commonLib, line 2681
    } // library marker kkossev.commonLib, line 2682
} // library marker kkossev.commonLib, line 2683

// credits @thebearmay // library marker kkossev.commonLib, line 2685
String getModel(){ // library marker kkossev.commonLib, line 2686
    try{ // library marker kkossev.commonLib, line 2687
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2688
    } catch (ignore){ // library marker kkossev.commonLib, line 2689
        try{ // library marker kkossev.commonLib, line 2690
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2691
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2692
            return model // library marker kkossev.commonLib, line 2693
            }         // library marker kkossev.commonLib, line 2694
        } catch(ignore_again) { // library marker kkossev.commonLib, line 2695
            return "" // library marker kkossev.commonLib, line 2696
        } // library marker kkossev.commonLib, line 2697
    } // library marker kkossev.commonLib, line 2698
} // library marker kkossev.commonLib, line 2699

// credits @thebearmay // library marker kkossev.commonLib, line 2701
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2702
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2703
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2704
    String revision = tokens.last() // library marker kkossev.commonLib, line 2705
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2706
} // library marker kkossev.commonLib, line 2707

/** // library marker kkossev.commonLib, line 2709
 * called from TODO // library marker kkossev.commonLib, line 2710
 *  // library marker kkossev.commonLib, line 2711
 */ // library marker kkossev.commonLib, line 2712

def deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2714
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2715
    unschedule() // library marker kkossev.commonLib, line 2716
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2717
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2718

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2720
} // library marker kkossev.commonLib, line 2721


def resetStatistics() { // library marker kkossev.commonLib, line 2724
    runIn(1, "resetStats") // library marker kkossev.commonLib, line 2725
    sendInfoEvent("Statistics are reset. Refresh the web page") // library marker kkossev.commonLib, line 2726
} // library marker kkossev.commonLib, line 2727

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2729
void resetStats() { // library marker kkossev.commonLib, line 2730
    logDebug "resetStats..." // library marker kkossev.commonLib, line 2731
    state.stats = [:] // library marker kkossev.commonLib, line 2732
    state.states = [:] // library marker kkossev.commonLib, line 2733
    state.lastRx = [:] // library marker kkossev.commonLib, line 2734
    state.lastTx = [:] // library marker kkossev.commonLib, line 2735
    state.health = [:] // library marker kkossev.commonLib, line 2736
    state.zigbeeGroups = [:]  // library marker kkossev.commonLib, line 2737
    state.stats["rxCtr"] = 0 // library marker kkossev.commonLib, line 2738
    state.stats["txCtr"] = 0 // library marker kkossev.commonLib, line 2739
    state.states["isDigital"] = false // library marker kkossev.commonLib, line 2740
    state.states["isRefresh"] = false // library marker kkossev.commonLib, line 2741
    state.health["offlineCtr"] = 0 // library marker kkossev.commonLib, line 2742
    state.health["checkCtr3"] = 0 // library marker kkossev.commonLib, line 2743
} // library marker kkossev.commonLib, line 2744

/** // library marker kkossev.commonLib, line 2746
 * called from TODO // library marker kkossev.commonLib, line 2747
 *  // library marker kkossev.commonLib, line 2748
 */ // library marker kkossev.commonLib, line 2749
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2750
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2751
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2752
        state.clear() // library marker kkossev.commonLib, line 2753
        unschedule() // library marker kkossev.commonLib, line 2754
        resetStats() // library marker kkossev.commonLib, line 2755
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2756
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2757
        logInfo "all states and scheduled jobs cleared!" // library marker kkossev.commonLib, line 2758
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2759
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2760
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2761
        sendInfoEvent("Initialized") // library marker kkossev.commonLib, line 2762
    } // library marker kkossev.commonLib, line 2763

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2765
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2766
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2767
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2768
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2769
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2770

    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true) // library marker kkossev.commonLib, line 2772
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true) // library marker kkossev.commonLib, line 2773
    if (fullInit || settings?.traceEnable == null) device.updateSetting("traceEnable", false) // library marker kkossev.commonLib, line 2774
    if (fullInit || settings?.alwaysOn == null) device.updateSetting("alwaysOn", false) // library marker kkossev.commonLib, line 2775
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"]) // library marker kkossev.commonLib, line 2776
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2777
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2778
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown') // library marker kkossev.commonLib, line 2779
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false) // library marker kkossev.commonLib, line 2780
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2781
        if (fullInit || settings?.minReportingTime == null) device.updateSetting("minReportingTime", [value:DEFAULT_MIN_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2782
        if (fullInit || settings?.maxReportingTime == null) device.updateSetting("maxReportingTime", [value:DEFAULT_MAX_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2783
    } // library marker kkossev.commonLib, line 2784
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2785
        if (fullInit || settings?.illuminanceThreshold == null) device.updateSetting("illuminanceThreshold", [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:"number"]) // library marker kkossev.commonLib, line 2786
        if (fullInit || settings?.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.00, type:"decimal"]) // library marker kkossev.commonLib, line 2787
    } // library marker kkossev.commonLib, line 2788
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2789
    if (DEVICE_TYPE in ["AirQuality"]) { initVarsAirQuality(fullInit) } // library marker kkossev.commonLib, line 2790
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2791
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2792
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit);    initEventsSwitch(fullInit) }         // threeStateEnable, ignoreDuplicated // library marker kkossev.commonLib, line 2793
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none // library marker kkossev.commonLib, line 2794
    if (DEVICE_TYPE in ["Radar"])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none // library marker kkossev.commonLib, line 2795
    if (DEVICE_TYPE in ["ButtonDimmer"]) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2796
    if (DEVICE_TYPE in ["Thermostat"]) { initVarsThermostat(fullInit);     initEventsThermostat(fullInit) } // library marker kkossev.commonLib, line 2797
    if (DEVICE_TYPE in ["Bulb"])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2798

    def mm = device.getDataValue("model") // library marker kkossev.commonLib, line 2800
    if ( mm != null) { // library marker kkossev.commonLib, line 2801
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2802
    } // library marker kkossev.commonLib, line 2803
    else { // library marker kkossev.commonLib, line 2804
        logWarn " Model not found, please re-pair the device!" // library marker kkossev.commonLib, line 2805
    } // library marker kkossev.commonLib, line 2806
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2807
    if ( ep  != null) { // library marker kkossev.commonLib, line 2808
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2809
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2810
    } // library marker kkossev.commonLib, line 2811
    else { // library marker kkossev.commonLib, line 2812
        logWarn " Destination End Point not found, please re-pair the device!" // library marker kkossev.commonLib, line 2813
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2814
    }     // library marker kkossev.commonLib, line 2815
} // library marker kkossev.commonLib, line 2816


/** // library marker kkossev.commonLib, line 2819
 * called from TODO // library marker kkossev.commonLib, line 2820
 *  // library marker kkossev.commonLib, line 2821
 */ // library marker kkossev.commonLib, line 2822
def setDestinationEP() { // library marker kkossev.commonLib, line 2823
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2824
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2825
        state.destinationEP = ep // library marker kkossev.commonLib, line 2826
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2827
    } // library marker kkossev.commonLib, line 2828
    else { // library marker kkossev.commonLib, line 2829
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2830
        state.destinationEP = "01"    // fallback EP // library marker kkossev.commonLib, line 2831
    }       // library marker kkossev.commonLib, line 2832
} // library marker kkossev.commonLib, line 2833


def logDebug(msg) { // library marker kkossev.commonLib, line 2836
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2837
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2838
    } // library marker kkossev.commonLib, line 2839
} // library marker kkossev.commonLib, line 2840

def logInfo(msg) { // library marker kkossev.commonLib, line 2842
    if (settings.txtEnable) { // library marker kkossev.commonLib, line 2843
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2844
    } // library marker kkossev.commonLib, line 2845
} // library marker kkossev.commonLib, line 2846

def logWarn(msg) { // library marker kkossev.commonLib, line 2848
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2849
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2850
    } // library marker kkossev.commonLib, line 2851
} // library marker kkossev.commonLib, line 2852

def logTrace(msg) { // library marker kkossev.commonLib, line 2854
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2855
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2856
    } // library marker kkossev.commonLib, line 2857
} // library marker kkossev.commonLib, line 2858



// _DEBUG mode only // library marker kkossev.commonLib, line 2862
void getAllProperties() { // library marker kkossev.commonLib, line 2863
    log.trace 'Properties:'     // library marker kkossev.commonLib, line 2864
    device.properties.each { it-> // library marker kkossev.commonLib, line 2865
        log.debug it // library marker kkossev.commonLib, line 2866
    } // library marker kkossev.commonLib, line 2867
    log.trace 'Settings:'     // library marker kkossev.commonLib, line 2868
    settings.each { it-> // library marker kkossev.commonLib, line 2869
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2870
    }     // library marker kkossev.commonLib, line 2871
    log.trace 'Done'     // library marker kkossev.commonLib, line 2872
} // library marker kkossev.commonLib, line 2873

// delete all Preferences // library marker kkossev.commonLib, line 2875
void deleteAllSettings() { // library marker kkossev.commonLib, line 2876
    settings.each { it-> // library marker kkossev.commonLib, line 2877
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2878
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2879
    } // library marker kkossev.commonLib, line 2880
    logInfo  "All settings (preferences) DELETED" // library marker kkossev.commonLib, line 2881
} // library marker kkossev.commonLib, line 2882

// delete all attributes // library marker kkossev.commonLib, line 2884
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2885
    device.properties.supportedAttributes.each { it-> // library marker kkossev.commonLib, line 2886
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2887
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2888
    } // library marker kkossev.commonLib, line 2889
    logInfo "All current states (attributes) DELETED" // library marker kkossev.commonLib, line 2890
} // library marker kkossev.commonLib, line 2891

// delete all State Variables // library marker kkossev.commonLib, line 2893
void deleteAllStates() { // library marker kkossev.commonLib, line 2894
    state.each { it-> // library marker kkossev.commonLib, line 2895
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2896
    } // library marker kkossev.commonLib, line 2897
    state.clear() // library marker kkossev.commonLib, line 2898
    logInfo "All States DELETED" // library marker kkossev.commonLib, line 2899
} // library marker kkossev.commonLib, line 2900

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2902
    unschedule() // library marker kkossev.commonLib, line 2903
    logInfo "All scheduled jobs DELETED" // library marker kkossev.commonLib, line 2904
} // library marker kkossev.commonLib, line 2905

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2907
    logDebug "deleteAllChildDevices : not implemented!" // library marker kkossev.commonLib, line 2908
} // library marker kkossev.commonLib, line 2909

def parseTest(par) { // library marker kkossev.commonLib, line 2911
//read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2912
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2913
    parse(par) // library marker kkossev.commonLib, line 2914
} // library marker kkossev.commonLib, line 2915

def testJob() { // library marker kkossev.commonLib, line 2917
    log.warn "test job executed" // library marker kkossev.commonLib, line 2918
} // library marker kkossev.commonLib, line 2919

/** // library marker kkossev.commonLib, line 2921
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2922
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2923
 */ // library marker kkossev.commonLib, line 2924
def getCron( timeInSeconds ) { // library marker kkossev.commonLib, line 2925
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2926
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2927
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2928
    def minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2929
    def hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2930
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2931
    String cron // library marker kkossev.commonLib, line 2932
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2933
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2934
    } // library marker kkossev.commonLib, line 2935
    else { // library marker kkossev.commonLib, line 2936
        if (minutes < 60) { // library marker kkossev.commonLib, line 2937
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"   // library marker kkossev.commonLib, line 2938
        } // library marker kkossev.commonLib, line 2939
        else { // library marker kkossev.commonLib, line 2940
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"                    // library marker kkossev.commonLib, line 2941
        } // library marker kkossev.commonLib, line 2942
    } // library marker kkossev.commonLib, line 2943
    return cron // library marker kkossev.commonLib, line 2944
} // library marker kkossev.commonLib, line 2945

boolean isTuya() { // library marker kkossev.commonLib, line 2947
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2948
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2949
    if (model?.startsWith("TS") && manufacturer?.startsWith("_TZ")) { // library marker kkossev.commonLib, line 2950
        return true // library marker kkossev.commonLib, line 2951
    } // library marker kkossev.commonLib, line 2952
    return false // library marker kkossev.commonLib, line 2953
} // library marker kkossev.commonLib, line 2954

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2956
    if (!isTuya()) { // library marker kkossev.commonLib, line 2957
        logTrace "not Tuya" // library marker kkossev.commonLib, line 2958
        return // library marker kkossev.commonLib, line 2959
    } // library marker kkossev.commonLib, line 2960
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2961
    if (application != null) { // library marker kkossev.commonLib, line 2962
        Integer ver // library marker kkossev.commonLib, line 2963
        try { // library marker kkossev.commonLib, line 2964
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2965
        } // library marker kkossev.commonLib, line 2966
        catch (e) { // library marker kkossev.commonLib, line 2967
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2968
            return // library marker kkossev.commonLib, line 2969
        } // library marker kkossev.commonLib, line 2970
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString() // library marker kkossev.commonLib, line 2971
        if (device.getDataValue("tuyaVersion") != str) { // library marker kkossev.commonLib, line 2972
            device.updateDataValue("tuyaVersion", str) // library marker kkossev.commonLib, line 2973
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2974
        } // library marker kkossev.commonLib, line 2975
    } // library marker kkossev.commonLib, line 2976
} // library marker kkossev.commonLib, line 2977

boolean isAqara() { // library marker kkossev.commonLib, line 2979
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2980
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2981
    if (model?.startsWith("lumi")) { // library marker kkossev.commonLib, line 2982
        return true // library marker kkossev.commonLib, line 2983
    } // library marker kkossev.commonLib, line 2984
    return false // library marker kkossev.commonLib, line 2985
} // library marker kkossev.commonLib, line 2986

def updateAqaraVersion() { // library marker kkossev.commonLib, line 2988
    if (!isAqara()) { // library marker kkossev.commonLib, line 2989
        logTrace "not Aqara" // library marker kkossev.commonLib, line 2990
        return // library marker kkossev.commonLib, line 2991
    }     // library marker kkossev.commonLib, line 2992
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2993
    if (application != null) { // library marker kkossev.commonLib, line 2994
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2)))); // library marker kkossev.commonLib, line 2995
        if (device.getDataValue("aqaraVersion") != str) { // library marker kkossev.commonLib, line 2996
            device.updateDataValue("aqaraVersion", str) // library marker kkossev.commonLib, line 2997
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2998
        } // library marker kkossev.commonLib, line 2999
    } // library marker kkossev.commonLib, line 3000
    else { // library marker kkossev.commonLib, line 3001
        return null // library marker kkossev.commonLib, line 3002
    } // library marker kkossev.commonLib, line 3003
} // library marker kkossev.commonLib, line 3004

String unix2formattedDate( unixTime ) { // library marker kkossev.commonLib, line 3006
    try { // library marker kkossev.commonLib, line 3007
        if (unixTime == null) return null // library marker kkossev.commonLib, line 3008
        def date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 3009
        return date.format("yyyy-MM-dd HH:mm:ss.SSS", location.timeZone) // library marker kkossev.commonLib, line 3010
    } catch (Exception e) { // library marker kkossev.commonLib, line 3011
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 3012
        return new Date().format("yyyy-MM-dd HH:mm:ss.SSS", location.timeZone) // library marker kkossev.commonLib, line 3013
    } // library marker kkossev.commonLib, line 3014
} // library marker kkossev.commonLib, line 3015

def formattedDate2unix( formattedDate ) { // library marker kkossev.commonLib, line 3017
    try { // library marker kkossev.commonLib, line 3018
        if (formattedDate == null) return null // library marker kkossev.commonLib, line 3019
        def date = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", formattedDate) // library marker kkossev.commonLib, line 3020
        return date.getTime() // library marker kkossev.commonLib, line 3021
    } catch (Exception e) { // library marker kkossev.commonLib, line 3022
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 3023
        return now() // library marker kkossev.commonLib, line 3024
    } // library marker kkossev.commonLib, line 3025
} // library marker kkossev.commonLib, line 3026

def test(par) { // library marker kkossev.commonLib, line 3028
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 3029
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 3030

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 3032
    //parse(par) // library marker kkossev.commonLib, line 3033

    sendZigbeeCommands(cmds)     // library marker kkossev.commonLib, line 3035
} // library marker kkossev.commonLib, line 3036

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
