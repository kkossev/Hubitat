/* groovylint-disable LineLength */
/**
 *  ZigUSB - Device Driver for Hubitat Elevation
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
 * ver. 3.0.3  2024-12-12 kkossev  - (dev. branch) first test version
 *
 *                                   TODO: ZigUSB on/off (inverted)! https://github.com/Koenkk/zigbee-herdsman-converters/pull/7077 https://github.com/Koenkk/zigbee-herdsman-converters/commit/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b
 */

static String version() { "3.0.3" }
static String timeStamp() {"2024/02/22 10:01 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = "Plug"
@Field static final String DEVICE_TYPE = "Plug"
#include kkossev.commonLib

// @Field static final Boolean _THREE_STATE = true  // move from the commonLib here?

metadata {
    definition (
        name: 'ZigUSB',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/ZigUSB/ZigUSB.groovy',
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
        capability "Outlet"
        capability 'TemperatureMeasurement'
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'VoltageMeasurement'
        capability 'CurrentMeter'

        if (_THREE_STATE == true) {
            attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String>
        }

        // deviceType specific capabilities, commands and attributes
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Plug", "Valve"])) {
            command "zigbeeGroups", [
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>],
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]]
            ]
        }
        // https://github.com/xyzroe/ZigUSB
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b/src/devices/xyzroe.ts
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0007,0006", outClusters:"0000,0006", model:"ZigUSB", manufacturer:"xyzroe.cc"
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        input (name: "alwaysOn", type: "bool", title: "<b>Always On</b>", description: "<i>Disable switching OFF for plugs that must be always On</i>", defaultValue: false)
        if (advancedOptions == true || advancedOptions == true) {
            input (name: "ignoreDuplicated", type: "bool", title: "<b>Ignore Duplicated Switch Events</b>", description: "<i>Some switches and plugs send periodically the switch status as a heart-beet </i>", defaultValue: false)
            input (name: "inverceSwitch", type: "bool", title: "<b>Invert the switch on/off</b>", description: "<i>ZigUSB has the on and off states inverted!</i>", defaultValue: true)
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

def refreshPlug() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
    logDebug "refreshPlug() : ${cmds}"
    return cmds
}

def initVarsPlug(boolean fullInit=false) {
    logDebug "initVarsPlug(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.ignoreDuplicated == null) device.updateSetting("ignoreDuplicated", false)
    if (fullInit || settings?.inverceSwitch == null) device.updateSetting("inverceSwitch", true)

}

void initEventPlug(boolean fullInit=false) {
}

def configureDevicePlug() {
    List<String> cmds = []
    if (isZBMINIL2()) {
        logDebug "configureDevicePlug() : unbind ZBMINIL2 poll control cluster"
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
    cmds += configureReporting("Write", ONOFF,  "1", "30", "0", sendNow=false)    // switch state should be always reported
    logDebug "configureDevicePlug() : ${cmds}"
    return cmds
}

void parseElectricalMeasureClusterPlug(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseElectricalMeasureClusterPlug: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void parseMeteringClusterPlug(descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseMeteringClusterPlug: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
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
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", "delay 251", ]    // disable Plug automatic reporting
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
