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
 * ver. 3.0.3  2024-02-24 kkossev  - (dev. branch) first test version - decoding success! refresh() and configure();
 *
 *                                   TODO: thresholds!
 *                                   TODO: ZigUSB on/off (inverted)! https://github.com/Koenkk/zigbee-herdsman-converters/pull/7077 https://github.com/Koenkk/zigbee-herdsman-converters/commit/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b
 */

static String version() { "3.0.3" }
static String timeStamp() { "2024/02/24 11:58 PM" }

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
/* groovylint-disable-next-line NglParseError */
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
        capability 'Switch'
        capability 'TemperatureMeasurement'
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'VoltageMeasurement'
        capability 'CurrentMeter'

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
        // https://github.com/xyzroe/ZigUSB
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b/src/devices/xyzroe.ts
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0007,0006", outClusters:"0000,0006", model:"ZigUSB", manufacturer:"xyzroe.cc", deviceJoinName: "Zigbee USB power monitor and switch"
        // ep2: current; ep3: voltage; ep4: power; ep5: energy; ep6: frequency; ep7: power factor
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: "alwaysOn", type: "bool", title: "<b>Always On</b>", description: "<i>Disable switching OFF for plugs that must be always On</i>", defaultValue: false
        input name: 'autoReportingTime', type: 'number', title: '<b>Automatic reporting time period</b>', description: '<i>V/A/W reporting interval, seconds (0..3600)<br>0 (zero) disables the automatic reproting!</i>', range: '0..3600', defaultValue: DEFAULT_REPORTING_TIME
        if (advancedOptions == true || advancedOptions == true) {
            input name: "ignoreDuplicated", type: "bool", title: "<b>Ignore Duplicated Switch Events</b>", description: "<i>Some switches and plugs send periodically the switch status as a heart-beat </i>", defaultValue: false
            input name: "inverceSwitch", type: "bool", title: "<b>Invert the switch on/off</b>", description: "<i>ZigUSB has the on and off states inverted!</i>", defaultValue: true
        }
    }
}

@Field static final int    DEFAULT_REPORTING_TIME = 60
@Field static final int    MAX_POWER_LIMIT = 999
@Field static final String ONOFF = "Switch"
@Field static final String POWER = "Power"
@Field static final String INST_POWER = "InstPower"
@Field static final String ENERGY = "Energy"
@Field static final String VOLTAGE = "Voltage"
@Field static final String AMPERAGE = "Amperage"
@Field static final String FREQUENCY = "Frequency"
@Field static final String POWER_FACTOR = "PowerFactor"

boolean isZBMINIL2()   { /*true*/(device?.getDataValue('model') ?: 'n/a') in ['ZBMINIL2'] }

/**
 * ZigUSB has a really wierd way of reporting the on/off state back to the hub...
 */
void customParseDefaultCommandResponse(final Map descMap) {
    logDebug "ZigUSB:  parseDefaultCommandResponse: ${descMap}"
    parseOnOffCluster([attrId: '0000', value: descMap.data[0]])
}

List<String> customRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [destEndpoint :01], delay=200)     // switch state
    // ANALOG_INPUT_CLUSTER attribute 0x0055 error: Unsupported COMMAND
    //cmds += zigbee.readAttribute(0x000C, 0x0055, [destEndpoint :02], delay=200)     // current, voltage, power, reporting interval
    //  TEMPERATURE_MEASUREMENT_CLUSTER attribute 0x0000 error: 0x[00, 00, 8F]
    //cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint :04], delay=200)     // temperature
    // ANALOG_INPUT_CLUSTER attribute 0x0055 error: Unsupported COMMAND
    //cmds += zigbee.readAttribute(0x000C, 0x0055, [destEndpoint :05], delay=200)     // uptime
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

boolean customInitVars(boolean fullInit=false) {
    logDebug "customInitVars(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.ignoreDuplicated == null) device.updateSetting("ignoreDuplicated", true)
    if (fullInit || settings?.inverceSwitch == null) device.updateSetting("inverceSwitch", true)
    if (fullInit || settings?.autoReportingTime == null) device.updateSetting("autoReportingTime", DEFAULT_REPORTING_TIME)
    return true
}

boolean  customInitEvents(boolean fullInit=false) {
    return true
}

List<String> customConfigureDevice() {
    logInfo "Configuring the device..."
    List<String> cmds = []
    int intMinTime = 1
    int intMaxTime = (settings?.autoReportingTime as int) ?: 60
    //cmds += configureReporting("Write", ONOFF,  "1", "30", "0", sendNow=false)    // switch state should be always reported
    cmds += configureReporting("Write", ONOFF,  intMinTime.toString(), intMaxTime.toString(), "0", sendNow=false)    // switch state should be always reported
    if (settings?.autoReportingTime != 0) {
        cmds += zigbee.configureReporting(0x000C, 0x0055, DataType.UINT16, intMinTime, intMaxTime, 0, [destEndpoint: 02])   // current, voltage, power, reporting interval
        logInfo "configuring the automatic reporting  : ${intMaxTime} seconds"
    }
    else {
        cmds += zigbee.configureReporting(0x000C, 0x0055, DataType.UINT16, 0xFFFF, 0xFFFF, 0, [destEndpoint: 02])   // disable reporting
        logInfo "configuring the automatic reporting  : DISABLED"
    }
    cmds += zigbee.reportingConfiguration(0x000C, 0x0055, [destEndpoint: 02], 200)
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

void customUpdated() {
    logDebug "customUpdated()"
}

void parseZigUSBAnlogInputCluster(String description) {

    Map descMap = myParseDescriptionAsMap(description)
    if (descMap == null) {
        logWarn "parseZigUSBAnlogInputCluster: descMap is null"
        return
    }
    // descMap=[raw:1C9F02000C1E55003915AEA7401C004204562C3430, dni:1C9F, endpoint:02, cluster:000C, size:1E, attrId:0055, encoding:39, command:0A, value:40A7AE15, clusterInt:12, attrInt:85, additionalAttrs:[[value:V,40, encoding:42, attrId:001C, consumedBytes:7, attrInt:28]]]
    Map additionalAttrs = [:]
    if (descMap.additionalAttrs != null && descMap.additionalAttrs.size() > 0) {
        additionalAttrs = descMap.additionalAttrs[0] ?: [:]
    }
    // additionalAttrs=[value:W,40, encoding:42, attrId:001C, consumedBytes:7, attrInt:28]
    //logDebug "parseZigUSBAnlogInputCluster: additionalAttrs=${additionalAttrs}"
    String measurementType = UNKNOWN
    if (additionalAttrs.value != null) {
        String value = additionalAttrs['value']
        String firstElement = value.split(',')[0]
        switch (firstElement) {
            case 'C' : measurementType = TEMPERATURE; break
            case 'V' : measurementType = VOLTAGE; break
            case 'A' : measurementType = AMPERAGE; break
            case 'W' : measurementType = POWER; break
            default : break
        }
        logTrace "parseZigUSBAnlogInputCluster: measurementType=${measurementType}"
    }

    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int  value
    BigDecimal floatValue
    /*
    descMap = [raw:1C9F02000C1E550039B98D863E 1C004204  41 2C3430  dni:1C9F, endpoint:02, cluster:000C, size:1E, attrId:0055, encoding:39, command:0A, value:3E868DB9, clusterInt:12, attrInt:85
                   1C9F 02 000C 1E 5500 39 B98D863E
                                   ^^^^ attribute
                                           ^^^^^^^^ value : descMap.value=3E868DB9 floatValue =  floatValue=0.2628 (Amperage??)

                                   1C00 42 04 412C3430

                    'C' (0x43): 'temperature'
                    'V' (0x56): 'voltage',
                    'A' (0x41): 'current'
                    'W' (0x57): 'power',

                    read attr - raw: 1C9F02000C1E550039 643B8F40 1C004204 57 2C3430 -> power  value=408F3B64 floatValue=4.476 (W)
                                                        ^^^^^^^^ value
                                raw: 1C9F02000C1E550039 91ED2C3F 1C004204 41 2C3430 -> current value=3F2CED91 floatValue=0.6755 (A)
                                                        ^^^^^^^^ value
                                raw: 1C9F02000C1E550039 0781A540 1C004204 56 2C3430 -> voltage value=40A58107 floatValue=5.1720004 (v)

    */

    switch (descMap.endpoint) {
        case '01' : // switch
        case '02' : // current, voltage, power, reporting interval
            if (descMap.attrId == '001C') {
                logDebug "parseZigUSBAnlogInputCluster: (0x001C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} "
            }
            else {
                try {
                    value = hexStrToUnsignedInt(descMap.value)
                    floatValue = Float.intBitsToFloat(value.intValue())
                    logDebug "parseZigUSBAnlogInputCluster: (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
                    switch (measurementType) {
                        case VOLTAGE : sendVoltageEvent(floatValue, false); break
                        case AMPERAGE : sendAmperageEvent(floatValue, false); break
                        case POWER : sendPowerEvent(floatValue, false); break
                        default : logInfo "${measurementType} is ${floatValue.setScale(3, BigDecimal.ROUND_HALF_UP)} (raw:${value})"; break
                    }
                }
                catch (Exception e) {
                    logWarn "parseZigUSBAnlogInputCluster: EXCEPTION (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
                }
            }
            break
        case '05' : // uptime, seconds
            value = hexStrToUnsignedInt(descMap.value)
            logDebug "uptime is ${formatTime(value)} (raw:${value})" // 14042d 3h 46m 8s (raw:1213242368)
            break
        case '04' : // temperature
        default :
            value = hexStrToUnsignedInt(descMap.value)
            logWarn "parseZigUSBAnlogInputCluster: (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
            break

    }
}

void customParseElectricalMeasureCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseMeteringCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) {
    Map map = [:]
    map.name = 'voltage'
    map.value = voltage.setScale(3, BigDecimal.ROUND_HALF_UP)
    map.unit = 'V'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0
    final BigDecimal  voltageThreshold = 0.001
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
        //runIn(1, formatAttrib, [overwrite: true])
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)"
    }
}

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) {
    Map map = [:]
    map.name = 'amperage'
    map.value = amperage.setScale(3, BigDecimal.ROUND_HALF_UP)
    map.unit = 'A'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0
    final BigDecimal amperageThreshold = 0.001
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
        //runIn(1, formatAttrib, [overwrite: true])
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)"
    }
}

void sendPowerEvent(BigDecimal power, boolean isDigital=false) {
    Map map = [:]
    map.name = 'power'
    map.value = power.setScale(2, BigDecimal.ROUND_HALF_UP)
    map.unit = 'W'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0
    final BigDecimal powerThreshold = 0.01
    if (power  > MAX_POWER_LIMIT) {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)"
        return
    }
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
        //runIn(1, formatAttrib, [overwrite: true])
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)"
    }
}

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) {
    Map map = [:]
    map.name = 'frequency'
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP)
    map.unit = 'Hz'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.0
    final BigDecimal frequencyThreshold = 0.1
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
        //runIn(1, formatAttrib, [overwrite: true])
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)"
    }
}

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) {
    Map map = [:]
    map.name = 'powerFactor'
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP)
    map.unit = '%'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.0
    final BigDecimal powerFactorThreshold = 0.01
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) {
        logInfo "${map.descriptionText}"
        sendEvent(map)
        //runIn(1, formatAttrib, [overwrite: true])
    }
    else {
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)"
    }
}


List<String> configureReporting(String operation, String measurement,  String minTime="0", String maxTime="0", String delta="0", boolean sendNow=true ) {
    int intMinTime = safeToInt(minTime)
    int intMaxTime = safeToInt(maxTime)
    int intDelta = safeToInt(delta)
    String epString = state.destinationEP
    int ep = safeToInt(epString)
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
