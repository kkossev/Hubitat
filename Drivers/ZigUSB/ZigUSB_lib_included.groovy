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
 * ver. 1.0.0  2024-02-25 kkossev  - (dev. branch) first test version - decoding success! refresh() and configure();
 *
 *                                   TODO: individual thresholds for each attribute
 *                                   TODO: ZigUSB on/off (inverted)!
 */

static String version() { "1.0.0" }
static String timeStamp() { "2024/02/25 9:54 AM" }

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
        // https://github.com/Koenkk/zigbee-herdsman-converters/pull/7077 https://github.com/Koenkk/zigbee-herdsman-converters/commit/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0007,0006", outClusters:"0000,0006", model:"ZigUSB", manufacturer:"xyzroe.cc", deviceJoinName: "Zigbee USB power monitor and switch"
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: "alwaysOn", type: "bool", title: "<b>Always On</b>", description: "<i>Disable switching OFF for plugs that must be always On</i>", defaultValue: false
        input name: 'autoReportingTime', type: 'number', title: '<b>Automatic reporting time period</b>', description: '<i>V/A/W reporting interval, seconds (0..3600)<br>0 (zero) disables the automatic reporting!</i>', range: '0..3600', defaultValue: DEFAULT_REPORTING_TIME
        if (advancedOptions == true || advancedOptions == true) {
            input name: "ignoreDuplicated", type: "bool", title: "<b>Ignore Duplicated Switch Events</b>", description: "<i>Some switches and plugs send periodically the switch status as a heart-beat </i>", defaultValue: true
            input name: "inverceSwitch", type: "bool", title: "<b>Invert the switch on/off</b>", description: "<i>ZigUSB has the on and off states inverted!</i>", defaultValue: true
        }
    }
}

@Field static final int    DEFAULT_REPORTING_TIME = 30
@Field static final int    DEFAULT_PRECISION = 3           // 3 decimal places
@Field static final BigDecimal DEFAULT_DELTA = 0.001
@Field static final int    MAX_POWER_LIMIT = 999
@Field static final String ONOFF = "Switch"
@Field static final String POWER = "Power"
@Field static final String INST_POWER = "InstPower"
@Field static final String ENERGY = "Energy"
@Field static final String VOLTAGE = "Voltage"
@Field static final String AMPERAGE = "Amperage"
@Field static final String FREQUENCY = "Frequency"
@Field static final String POWER_FACTOR = "PowerFactor"

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
    List<String> cmds = customConfigureDevice()
    sendZigbeeCommands(cmds)
    if (settings?.autoReportingTime == 0) {
        device.deleteCurrentState("amperage")
        device.deleteCurrentState("voltage")
        device.deleteCurrentState("power")
    }
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
    map.value = voltage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'V'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0
    final BigDecimal  voltageThreshold = DEFAULT_DELTA
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
    map.value = amperage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'A'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0
    final BigDecimal amperageThreshold = DEFAULT_DELTA
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
    map.value = power.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP)
    map.unit = 'W'
    map.type = isDigital == true ? 'digital' : 'physical'
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' }
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0
    final BigDecimal powerThreshold = DEFAULT_DELTA
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

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, NoDef, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods */ // library marker kkossev.commonLib, line 1
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
  * ver. 3.0.3  2024-02-25 kkossev  - (dev.branch) more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; // library marker kkossev.commonLib, line 35
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
String thermostatLibStamp() { '2024/02/25 8:47 AM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
//import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
//import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
//import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
//import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59

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
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 85
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 86

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 88
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 89
            if (_DEBUG) { // library marker kkossev.commonLib, line 90
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 91
            } // library marker kkossev.commonLib, line 92
        } // library marker kkossev.commonLib, line 93
        if (_DEBUG || (deviceType in ['Dimmer', 'ButtonDimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 94
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 95
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 96
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 97
            ] // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'AirQuality', 'Thermostat', 'AqaraCube', 'Radar']) { // library marker kkossev.commonLib, line 100
            capability 'Sensor' // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ['Device', 'MotionSensor', 'Radar']) { // library marker kkossev.commonLib, line 103
            capability 'MotionSensor' // library marker kkossev.commonLib, line 104
        } // library marker kkossev.commonLib, line 105
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Thermostat', 'Fingerbot', 'Dimmer', 'Bulb', 'IRBlaster']) { // library marker kkossev.commonLib, line 106
            capability 'Actuator' // library marker kkossev.commonLib, line 107
        } // library marker kkossev.commonLib, line 108
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'Fingerbot', 'ButtonDimmer', 'AqaraCube', 'IRBlaster']) { // library marker kkossev.commonLib, line 109
            capability 'Battery' // library marker kkossev.commonLib, line 110
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
        if (deviceType in  ['Thermostat']) { // library marker kkossev.commonLib, line 113
            capability 'Thermostat' // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Fingerbot', 'Bulb']) { // library marker kkossev.commonLib, line 116
            capability 'Switch' // library marker kkossev.commonLib, line 117
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 118
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 119
            } // library marker kkossev.commonLib, line 120
        } // library marker kkossev.commonLib, line 121
        if (deviceType in ['Dimmer', 'ButtonDimmer', 'Bulb']) { // library marker kkossev.commonLib, line 122
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 123
        } // library marker kkossev.commonLib, line 124
        if (deviceType in  ['Button', 'ButtonDimmer', 'AqaraCube']) { // library marker kkossev.commonLib, line 125
            capability 'PushableButton' // library marker kkossev.commonLib, line 126
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 127
            capability 'HoldableButton' // library marker kkossev.commonLib, line 128
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 129
        } // library marker kkossev.commonLib, line 130
        if (deviceType in  ['Device', 'Fingerbot']) { // library marker kkossev.commonLib, line 131
            capability 'Momentary' // library marker kkossev.commonLib, line 132
        } // library marker kkossev.commonLib, line 133
        if (deviceType in  ['Device', 'THSensor', 'AirQuality', 'Thermostat']) { // library marker kkossev.commonLib, line 134
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 135
        } // library marker kkossev.commonLib, line 136
        if (deviceType in  ['Device', 'THSensor', 'AirQuality']) { // library marker kkossev.commonLib, line 137
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 138
        } // library marker kkossev.commonLib, line 139
        if (deviceType in  ['Device', 'LightSensor', 'Radar']) { // library marker kkossev.commonLib, line 140
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 141
        } // library marker kkossev.commonLib, line 142
        if (deviceType in  ['AirQuality']) { // library marker kkossev.commonLib, line 143
            capability 'AirQuality'            // Attributes: airQualityIndex - NUMBER, range:0..500 // library marker kkossev.commonLib, line 144
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
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 172
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
boolean isChattyDeviceReport(description)  { return false /*(description?.contains("cluster: FC7E")) */ } // library marker kkossev.commonLib, line 231
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 232
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 233
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 234
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 235
boolean isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 236
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
        def cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 264
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
BigDecimal approxRollingAverage(BigDecimal avg, BigDecimal newSample) { // library marker kkossev.commonLib, line 654
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 655
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 656
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 657
    return avg // library marker kkossev.commonLib, line 658
} // library marker kkossev.commonLib, line 659

/* // library marker kkossev.commonLib, line 661
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 662
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 663
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 664
*/ // library marker kkossev.commonLib, line 665
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 666

/** // library marker kkossev.commonLib, line 668
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 669
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 670
 */ // library marker kkossev.commonLib, line 671
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 672
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 673
    /* // library marker kkossev.commonLib, line 674
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 675
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 676
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 677
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 678
    */ // library marker kkossev.commonLib, line 679
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 680
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 681
        case 0x0000: // library marker kkossev.commonLib, line 682
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 685
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 686
            if (isPing) { // library marker kkossev.commonLib, line 687
                def timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 688
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 689
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 690
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 691
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 692
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 693
                    sendRttEvent() // library marker kkossev.commonLib, line 694
                } // library marker kkossev.commonLib, line 695
                else { // library marker kkossev.commonLib, line 696
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 697
                } // library marker kkossev.commonLib, line 698
                state.states['isPing'] = false // library marker kkossev.commonLib, line 699
            } // library marker kkossev.commonLib, line 700
            else { // library marker kkossev.commonLib, line 701
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 702
            } // library marker kkossev.commonLib, line 703
            break // library marker kkossev.commonLib, line 704
        case 0x0004: // library marker kkossev.commonLib, line 705
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 706
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 707
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 708
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 709
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 710
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 711
            } // library marker kkossev.commonLib, line 712
            break // library marker kkossev.commonLib, line 713
        case 0x0005: // library marker kkossev.commonLib, line 714
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 715
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 716
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 717
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 718
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 719
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 720
            } // library marker kkossev.commonLib, line 721
            break // library marker kkossev.commonLib, line 722
        case 0x0007: // library marker kkossev.commonLib, line 723
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 724
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 725
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 726
            break // library marker kkossev.commonLib, line 727
        case 0xFFDF: // library marker kkossev.commonLib, line 728
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 729
            break // library marker kkossev.commonLib, line 730
        case 0xFFE2: // library marker kkossev.commonLib, line 731
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 732
            break // library marker kkossev.commonLib, line 733
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 734
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 735
            break // library marker kkossev.commonLib, line 736
        case 0xFFFE: // library marker kkossev.commonLib, line 737
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 738
            break // library marker kkossev.commonLib, line 739
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 740
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 741
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 742
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 743
            break // library marker kkossev.commonLib, line 744
        default: // library marker kkossev.commonLib, line 745
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 746
            break // library marker kkossev.commonLib, line 747
    } // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

/* // library marker kkossev.commonLib, line 751
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 752
 * power cluster            0x0001 // library marker kkossev.commonLib, line 753
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 754
*/ // library marker kkossev.commonLib, line 755
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 756
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 757
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 758
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 759
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 760
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 761
    } // library marker kkossev.commonLib, line 762

    final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 764
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 765
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 766
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 767
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 768
        } // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 771
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 772
    } // library marker kkossev.commonLib, line 773
    else { // library marker kkossev.commonLib, line 774
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 775
    } // library marker kkossev.commonLib, line 776
} // library marker kkossev.commonLib, line 777

void sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) { // library marker kkossev.commonLib, line 779
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 780
    Map result = [:] // library marker kkossev.commonLib, line 781
    def volts = rawValue / 10 // library marker kkossev.commonLib, line 782
    if (!(rawValue == 0 || rawValue == 255)) { // library marker kkossev.commonLib, line 783
        def minVolts = 2.2 // library marker kkossev.commonLib, line 784
        def maxVolts = 3.2 // library marker kkossev.commonLib, line 785
        def pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 786
        def roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 787
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 788
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 789
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 790
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 791
            result.name = 'battery' // library marker kkossev.commonLib, line 792
            result.unit  = '%' // library marker kkossev.commonLib, line 793
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 794
        } // library marker kkossev.commonLib, line 795
        else { // library marker kkossev.commonLib, line 796
            result.value = volts // library marker kkossev.commonLib, line 797
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 798
            result.unit  = 'V' // library marker kkossev.commonLib, line 799
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 800
        } // library marker kkossev.commonLib, line 801
        result.type = 'physical' // library marker kkossev.commonLib, line 802
        result.isStateChange = true // library marker kkossev.commonLib, line 803
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 804
        sendEvent(result) // library marker kkossev.commonLib, line 805
    } // library marker kkossev.commonLib, line 806
    else { // library marker kkossev.commonLib, line 807
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 808
    } // library marker kkossev.commonLib, line 809
} // library marker kkossev.commonLib, line 810

void sendBatteryPercentageEvent(batteryPercent, isDigital=false) { // library marker kkossev.commonLib, line 812
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 813
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 814
        return // library marker kkossev.commonLib, line 815
    } // library marker kkossev.commonLib, line 816
    Map map = [:] // library marker kkossev.commonLib, line 817
    map.name = 'battery' // library marker kkossev.commonLib, line 818
    map.timeStamp = now() // library marker kkossev.commonLib, line 819
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 820
    map.unit  = '%' // library marker kkossev.commonLib, line 821
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 822
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 823
    map.isStateChange = true // library marker kkossev.commonLib, line 824
    // // library marker kkossev.commonLib, line 825
    def latestBatteryEvent = device.latestState('battery', skipCache=true) // library marker kkossev.commonLib, line 826
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 827
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 828
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 829
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 830
        // send it now! // library marker kkossev.commonLib, line 831
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 832
    } // library marker kkossev.commonLib, line 833
    else { // library marker kkossev.commonLib, line 834
        def delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 835
        map.delayed = delayedTime // library marker kkossev.commonLib, line 836
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 837
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 838
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 839
    } // library marker kkossev.commonLib, line 840
} // library marker kkossev.commonLib, line 841

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 843
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 844
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 845
    sendEvent(map) // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 849
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 850
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 851
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 852
    sendEvent(map) // library marker kkossev.commonLib, line 853
} // library marker kkossev.commonLib, line 854

/* // library marker kkossev.commonLib, line 856
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 857
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 858
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 859
*/ // library marker kkossev.commonLib, line 860
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 861
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 862
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

/* // library marker kkossev.commonLib, line 866
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 867
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 868
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 869
*/ // library marker kkossev.commonLib, line 870
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 871
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 872
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    else { // library marker kkossev.commonLib, line 875
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 876
    } // library marker kkossev.commonLib, line 877
} // library marker kkossev.commonLib, line 878

/* // library marker kkossev.commonLib, line 880
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 881
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 883
*/ // library marker kkossev.commonLib, line 884
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 885
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 886
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 887
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 888
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 889
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 890
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 891
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 892
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 893
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 894
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 895
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 896
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 897
            } // library marker kkossev.commonLib, line 898
            else { // library marker kkossev.commonLib, line 899
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 900
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 901
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 902
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 903
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 904
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 905
                        return // library marker kkossev.commonLib, line 906
                    } // library marker kkossev.commonLib, line 907
                } // library marker kkossev.commonLib, line 908
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 909
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 910
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 911
            } // library marker kkossev.commonLib, line 912
            break // library marker kkossev.commonLib, line 913
        case 0x01: // View group // library marker kkossev.commonLib, line 914
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 915
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 916
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 917
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 918
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 919
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 920
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 921
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 922
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 923
            } // library marker kkossev.commonLib, line 924
            else { // library marker kkossev.commonLib, line 925
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 926
            } // library marker kkossev.commonLib, line 927
            break // library marker kkossev.commonLib, line 928
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 929
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 930
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 931
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 932
            final Set<String> groups = [] // library marker kkossev.commonLib, line 933
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 934
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 935
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 936
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 937
            } // library marker kkossev.commonLib, line 938
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 939
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 940
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 941
            break // library marker kkossev.commonLib, line 942
        case 0x03: // Remove group // library marker kkossev.commonLib, line 943
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 944
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 945
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 946
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 947
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 948
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 949
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 950
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 951
            } // library marker kkossev.commonLib, line 952
            else { // library marker kkossev.commonLib, line 953
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 954
            } // library marker kkossev.commonLib, line 955
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 956
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 957
            if (index >= 0) { // library marker kkossev.commonLib, line 958
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 959
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 960
            } // library marker kkossev.commonLib, line 961
            break // library marker kkossev.commonLib, line 962
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 963
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 964
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 965
            break // library marker kkossev.commonLib, line 966
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 967
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 968
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 969
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 970
            break // library marker kkossev.commonLib, line 971
        default: // library marker kkossev.commonLib, line 972
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 973
            break // library marker kkossev.commonLib, line 974
    } // library marker kkossev.commonLib, line 975
} // library marker kkossev.commonLib, line 976

List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 978
    List<String> cmds = [] // library marker kkossev.commonLib, line 979
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 980
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 981
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 982
        return [] // library marker kkossev.commonLib, line 983
    } // library marker kkossev.commonLib, line 984
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 985
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 986
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 987
    return cmds // library marker kkossev.commonLib, line 988
} // library marker kkossev.commonLib, line 989

List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 991
    List<String> cmds = [] // library marker kkossev.commonLib, line 992
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 993
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 994
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 995
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 996
    return cmds // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1000
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1001
    List<String> cmds = [] // library marker kkossev.commonLib, line 1002
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1003
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1004
    return cmds // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1008
    List<String> cmds = [] // library marker kkossev.commonLib, line 1009
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1010
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1011
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1012
        return [] // library marker kkossev.commonLib, line 1013
    } // library marker kkossev.commonLib, line 1014
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1015
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1016
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1017
    return cmds // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1021
    List<String> cmds = [] // library marker kkossev.commonLib, line 1022
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1023
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1024
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1025
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1026
    return cmds // library marker kkossev.commonLib, line 1027
} // library marker kkossev.commonLib, line 1028

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1030
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1031
    List<String> cmds = [] // library marker kkossev.commonLib, line 1032
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1033
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1034
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1035
    return cmds // library marker kkossev.commonLib, line 1036
} // library marker kkossev.commonLib, line 1037

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1039
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1040
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1041
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1042
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1043
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1044
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1045
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1046
] // library marker kkossev.commonLib, line 1047

void zigbeeGroups(command=null, par=null) { // library marker kkossev.commonLib, line 1049
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1050
    List<String> cmds = [] // library marker kkossev.commonLib, line 1051
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1052
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1053
    def value // library marker kkossev.commonLib, line 1054
    Boolean validated = false // library marker kkossev.commonLib, line 1055
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1056
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1057
        return // library marker kkossev.commonLib, line 1058
    } // library marker kkossev.commonLib, line 1059
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1060
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1061
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1062
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1063
        return // library marker kkossev.commonLib, line 1064
    } // library marker kkossev.commonLib, line 1065
    // // library marker kkossev.commonLib, line 1066
    def func // library marker kkossev.commonLib, line 1067
    try { // library marker kkossev.commonLib, line 1068
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1069
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1070
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1071
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1072
    } // library marker kkossev.commonLib, line 1073
    catch (e) { // library marker kkossev.commonLib, line 1074
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1075
        return // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1079
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1080
} // library marker kkossev.commonLib, line 1081

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1083
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1084
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1085
} // library marker kkossev.commonLib, line 1086

/* // library marker kkossev.commonLib, line 1088
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1089
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1090
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1091
*/ // library marker kkossev.commonLib, line 1092

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1094
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1095
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1096
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1097
    } // library marker kkossev.commonLib, line 1098

    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1100
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1101
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1102
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1105
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1106
    } // library marker kkossev.commonLib, line 1107
    else { // library marker kkossev.commonLib, line 1108
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
} // library marker kkossev.commonLib, line 1111

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1113
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1114
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1115

void toggle() { // library marker kkossev.commonLib, line 1117
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1118
    String state = '' // library marker kkossev.commonLib, line 1119
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1120
        state = 'on' // library marker kkossev.commonLib, line 1121
    } // library marker kkossev.commonLib, line 1122
    else { // library marker kkossev.commonLib, line 1123
        state = 'off' // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    descriptionText += state // library marker kkossev.commonLib, line 1126
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1127
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

void off() { // library marker kkossev.commonLib, line 1131
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1132
        customOff(); // library marker kkossev.commonLib, line 1133
        return // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1136
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1137
        return // library marker kkossev.commonLib, line 1138
    } // library marker kkossev.commonLib, line 1139
    List cmds = settings?.inverceSwitch == false ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1140
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1141
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1142
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1143
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1144
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1145
        } // library marker kkossev.commonLib, line 1146
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1147
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1148
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1149
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1150
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1151
    } // library marker kkossev.commonLib, line 1152
    /* // library marker kkossev.commonLib, line 1153
    else { // library marker kkossev.commonLib, line 1154
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1155
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1156
        } // library marker kkossev.commonLib, line 1157
        else { // library marker kkossev.commonLib, line 1158
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1159
            return // library marker kkossev.commonLib, line 1160
        } // library marker kkossev.commonLib, line 1161
    } // library marker kkossev.commonLib, line 1162
    */ // library marker kkossev.commonLib, line 1163

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1165
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1166
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

void on() { // library marker kkossev.commonLib, line 1170
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1171
        customOn(); // library marker kkossev.commonLib, line 1172
        return // library marker kkossev.commonLib, line 1173
    } // library marker kkossev.commonLib, line 1174
    List cmds = settings?.inverceSwitch == false ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1175
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1176
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1177
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1178
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1179
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1180
        } // library marker kkossev.commonLib, line 1181
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1182
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1183
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1184
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1185
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187
    /* // library marker kkossev.commonLib, line 1188
    else { // library marker kkossev.commonLib, line 1189
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1190
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1191
        } // library marker kkossev.commonLib, line 1192
        else { // library marker kkossev.commonLib, line 1193
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1194
            return // library marker kkossev.commonLib, line 1195
        } // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
    */ // library marker kkossev.commonLib, line 1198
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1199
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1200
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1201
} // library marker kkossev.commonLib, line 1202

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1204
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1205
    if (settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1206
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1207
    } // library marker kkossev.commonLib, line 1208
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1209
    Map map = [:] // library marker kkossev.commonLib, line 1210
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1211
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1212
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1213
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1214
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1215
        return // library marker kkossev.commonLib, line 1216
    } // library marker kkossev.commonLib, line 1217
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1218
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1219
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1220
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1221
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1222
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1223
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1224
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1225
    } else { // library marker kkossev.commonLib, line 1226
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1227
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1228
    } // library marker kkossev.commonLib, line 1229
    map.name = 'switch' // library marker kkossev.commonLib, line 1230
    map.value = value // library marker kkossev.commonLib, line 1231
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1232
    if (isRefresh) { // library marker kkossev.commonLib, line 1233
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1234
        map.isStateChange = true // library marker kkossev.commonLib, line 1235
    } else { // library marker kkossev.commonLib, line 1236
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1237
    } // library marker kkossev.commonLib, line 1238
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1239
    sendEvent(map) // library marker kkossev.commonLib, line 1240
    clearIsDigital() // library marker kkossev.commonLib, line 1241
} // library marker kkossev.commonLib, line 1242

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1244
    '0': 'switch off', // library marker kkossev.commonLib, line 1245
    '1': 'switch on', // library marker kkossev.commonLib, line 1246
    '2': 'switch last state' // library marker kkossev.commonLib, line 1247
] // library marker kkossev.commonLib, line 1248

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1250
    '0': 'toggle', // library marker kkossev.commonLib, line 1251
    '1': 'state', // library marker kkossev.commonLib, line 1252
    '2': 'momentary' // library marker kkossev.commonLib, line 1253
] // library marker kkossev.commonLib, line 1254

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1256
    def descMap = [:] // library marker kkossev.commonLib, line 1257
    try { // library marker kkossev.commonLib, line 1258
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    catch (e1) { // library marker kkossev.commonLib, line 1261
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1262
        // try alternative custom parsing // library marker kkossev.commonLib, line 1263
        descMap = [:] // library marker kkossev.commonLib, line 1264
        try { // library marker kkossev.commonLib, line 1265
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1266
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1267
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1268
            } // library marker kkossev.commonLib, line 1269
        } // library marker kkossev.commonLib, line 1270
        catch (e2) { // library marker kkossev.commonLib, line 1271
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1272
            return [:] // library marker kkossev.commonLib, line 1273
        } // library marker kkossev.commonLib, line 1274
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1275
    } // library marker kkossev.commonLib, line 1276
    return descMap // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1280
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1281
        return false // library marker kkossev.commonLib, line 1282
    } // library marker kkossev.commonLib, line 1283
    // try to parse ... // library marker kkossev.commonLib, line 1284
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1285
    def descMap = [:] // library marker kkossev.commonLib, line 1286
    try { // library marker kkossev.commonLib, line 1287
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1288
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    catch (e) { // library marker kkossev.commonLib, line 1291
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1292
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1293
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1294
        return true // library marker kkossev.commonLib, line 1295
    } // library marker kkossev.commonLib, line 1296

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1298
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1299
    } // library marker kkossev.commonLib, line 1300
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1301
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1302
    } // library marker kkossev.commonLib, line 1303
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1304
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306
    else { // library marker kkossev.commonLib, line 1307
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1308
        return false // library marker kkossev.commonLib, line 1309
    } // library marker kkossev.commonLib, line 1310
    return true    // processed // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1314
boolean otherTuyaOddities(String description) { // library marker kkossev.commonLib, line 1315
  /* // library marker kkossev.commonLib, line 1316
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1317
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1318
        return true // library marker kkossev.commonLib, line 1319
    } // library marker kkossev.commonLib, line 1320
*/ // library marker kkossev.commonLib, line 1321
    def descMap = [:] // library marker kkossev.commonLib, line 1322
    try { // library marker kkossev.commonLib, line 1323
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1324
    } // library marker kkossev.commonLib, line 1325
    catch (e1) { // library marker kkossev.commonLib, line 1326
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1327
        // try alternative custom parsing // library marker kkossev.commonLib, line 1328
        descMap = [:] // library marker kkossev.commonLib, line 1329
        try { // library marker kkossev.commonLib, line 1330
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1331
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1332
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1333
            } // library marker kkossev.commonLib, line 1334
        } // library marker kkossev.commonLib, line 1335
        catch (e2) { // library marker kkossev.commonLib, line 1336
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1337
            return true // library marker kkossev.commonLib, line 1338
        } // library marker kkossev.commonLib, line 1339
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1340
    } // library marker kkossev.commonLib, line 1341
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1342
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1343
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1344
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1345
        return false // library marker kkossev.commonLib, line 1346
    } // library marker kkossev.commonLib, line 1347
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1348
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1349
    // attribute report received // library marker kkossev.commonLib, line 1350
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1351
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1352
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1353
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1354
    } // library marker kkossev.commonLib, line 1355
    attrData.each { // library marker kkossev.commonLib, line 1356
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1357
        //def map = [:] // library marker kkossev.commonLib, line 1358
        if (it.status == '86') { // library marker kkossev.commonLib, line 1359
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1360
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1361
        } // library marker kkossev.commonLib, line 1362
        switch (it.cluster) { // library marker kkossev.commonLib, line 1363
            case '0000' : // library marker kkossev.commonLib, line 1364
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1365
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1366
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1367
                } // library marker kkossev.commonLib, line 1368
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1369
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1370
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1371
                } // library marker kkossev.commonLib, line 1372
                else { // library marker kkossev.commonLib, line 1373
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1374
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1375
                } // library marker kkossev.commonLib, line 1376
                break // library marker kkossev.commonLib, line 1377
            default : // library marker kkossev.commonLib, line 1378
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1379
                break // library marker kkossev.commonLib, line 1380
        } // switch // library marker kkossev.commonLib, line 1381
    } // for each attribute // library marker kkossev.commonLib, line 1382
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1383
} // library marker kkossev.commonLib, line 1384

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1386
//private boolean isRTXCircuitBreaker()   { device.getDataValue('manufacturer') in ['_TZE200_abatw3kj'] } // library marker kkossev.commonLib, line 1387

def parseOnOffAttributes(it) { // library marker kkossev.commonLib, line 1389
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1390
    def mode // library marker kkossev.commonLib, line 1391
    def attrName // library marker kkossev.commonLib, line 1392
    if (it.value == null) { // library marker kkossev.commonLib, line 1393
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1394
        return // library marker kkossev.commonLib, line 1395
    } // library marker kkossev.commonLib, line 1396
    def value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1397
    switch (it.attrId) { // library marker kkossev.commonLib, line 1398
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1399
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1400
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1401
            break // library marker kkossev.commonLib, line 1402
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1403
            attrName = 'On Time' // library marker kkossev.commonLib, line 1404
            mode = value // library marker kkossev.commonLib, line 1405
            break // library marker kkossev.commonLib, line 1406
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1407
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1408
            mode = value // library marker kkossev.commonLib, line 1409
            break // library marker kkossev.commonLib, line 1410
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1411
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1412
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1413
            break // library marker kkossev.commonLib, line 1414
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1415
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1416
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1417
            break // library marker kkossev.commonLib, line 1418
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1419
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1420
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1421
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1422
            } // library marker kkossev.commonLib, line 1423
            else { // library marker kkossev.commonLib, line 1424
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1425
            } // library marker kkossev.commonLib, line 1426
            break // library marker kkossev.commonLib, line 1427
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1428
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1429
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1430
            break // library marker kkossev.commonLib, line 1431
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1432
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1433
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1434
            break // library marker kkossev.commonLib, line 1435
        default : // library marker kkossev.commonLib, line 1436
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1437
            return // library marker kkossev.commonLib, line 1438
    } // library marker kkossev.commonLib, line 1439
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1440
} // library marker kkossev.commonLib, line 1441

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) { // library marker kkossev.commonLib, line 1443
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1444
    if (txtEnable) { log.info "${device.displayName } $event.descriptionText" } // library marker kkossev.commonLib, line 1445
    sendEvent(event) // library marker kkossev.commonLib, line 1446
} // library marker kkossev.commonLib, line 1447

def push() {                // Momentary capability // library marker kkossev.commonLib, line 1449
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1450
    if (DEVICE_TYPE in ['Fingerbot'])     { pushFingerbot(); return } // library marker kkossev.commonLib, line 1451
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1452
} // library marker kkossev.commonLib, line 1453

def push(buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1455
    if (DEVICE_TYPE in ['Fingerbot'])     { pushFingerbot(buttonNumber); return } // library marker kkossev.commonLib, line 1456
    sendButtonEvent(buttonNumber, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1457
} // library marker kkossev.commonLib, line 1458

def doubleTap(buttonNumber) { // library marker kkossev.commonLib, line 1460
    sendButtonEvent(buttonNumber, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1461
} // library marker kkossev.commonLib, line 1462

def hold(buttonNumber) { // library marker kkossev.commonLib, line 1464
    sendButtonEvent(buttonNumber, 'held', isDigital = true) // library marker kkossev.commonLib, line 1465
} // library marker kkossev.commonLib, line 1466

def release(buttonNumber) { // library marker kkossev.commonLib, line 1468
    sendButtonEvent(buttonNumber, 'released', isDigital = true) // library marker kkossev.commonLib, line 1469
} // library marker kkossev.commonLib, line 1470

void sendNumberOfButtonsEvent(numberOfButtons) { // library marker kkossev.commonLib, line 1472
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1473
} // library marker kkossev.commonLib, line 1474

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1476
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

/* // library marker kkossev.commonLib, line 1480
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1481
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1482
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1483
*/ // library marker kkossev.commonLib, line 1484
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1485
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1486
    if (DEVICE_TYPE in ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1487
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1488
    } // library marker kkossev.commonLib, line 1489
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1490
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1491
    } // library marker kkossev.commonLib, line 1492
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1493
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1494
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1495
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1496
    } // library marker kkossev.commonLib, line 1497
    else { // library marker kkossev.commonLib, line 1498
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1499
    } // library marker kkossev.commonLib, line 1500
} // library marker kkossev.commonLib, line 1501

def sendLevelControlEvent(rawValue) { // library marker kkossev.commonLib, line 1503
    def value = rawValue as int // library marker kkossev.commonLib, line 1504
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1505
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1506
    def map = [:] // library marker kkossev.commonLib, line 1507

    def isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1509
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1510

    map.name = 'level' // library marker kkossev.commonLib, line 1512
    map.value = value // library marker kkossev.commonLib, line 1513
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1514
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1515
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1516
        map.isStateChange = true // library marker kkossev.commonLib, line 1517
    } // library marker kkossev.commonLib, line 1518
    else { // library marker kkossev.commonLib, line 1519
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1520
    } // library marker kkossev.commonLib, line 1521
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1522
    sendEvent(map) // library marker kkossev.commonLib, line 1523
    clearIsDigital() // library marker kkossev.commonLib, line 1524
} // library marker kkossev.commonLib, line 1525

/** // library marker kkossev.commonLib, line 1527
 * Get the level transition rate // library marker kkossev.commonLib, line 1528
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1529
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1530
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1531
 */ // library marker kkossev.commonLib, line 1532
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1533
    int rate = 0 // library marker kkossev.commonLib, line 1534
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1535
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1536
    if (!isOn) { // library marker kkossev.commonLib, line 1537
        currentLevel = 0 // library marker kkossev.commonLib, line 1538
    } // library marker kkossev.commonLib, line 1539
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1540
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1541
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1542
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1543
    } else { // library marker kkossev.commonLib, line 1544
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1545
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1546
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1547
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1548
        } // library marker kkossev.commonLib, line 1549
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1550
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1551
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1552
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1553
        } // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1556
    return rate // library marker kkossev.commonLib, line 1557
} // library marker kkossev.commonLib, line 1558

// Command option that enable changes when off // library marker kkossev.commonLib, line 1560
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1561

/** // library marker kkossev.commonLib, line 1563
 * Constrain a value to a range // library marker kkossev.commonLib, line 1564
 * @param value value to constrain // library marker kkossev.commonLib, line 1565
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1566
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1567
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1568
 */ // library marker kkossev.commonLib, line 1569
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1570
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1571
        return value // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1574
} // library marker kkossev.commonLib, line 1575

/** // library marker kkossev.commonLib, line 1577
 * Constrain a value to a range // library marker kkossev.commonLib, line 1578
 * @param value value to constrain // library marker kkossev.commonLib, line 1579
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1580
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1581
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1582
 */ // library marker kkossev.commonLib, line 1583
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1584
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1585
        return value as Integer // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1591
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1592

/** // library marker kkossev.commonLib, line 1594
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1595
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1596
 * @param commands commands to execute // library marker kkossev.commonLib, line 1597
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1598
 */ // library marker kkossev.commonLib, line 1599
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1600
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1601
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1602
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1603
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1604
    } // library marker kkossev.commonLib, line 1605
    return [] // library marker kkossev.commonLib, line 1606
} // library marker kkossev.commonLib, line 1607

def intTo16bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1609
    def hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1610
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1611
} // library marker kkossev.commonLib, line 1612

def intTo8bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1614
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

/** // library marker kkossev.commonLib, line 1618
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1619
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1620
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1621
 */ // library marker kkossev.commonLib, line 1622
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1623
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1624
    List<String> cmds = [] // library marker kkossev.commonLib, line 1625
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1626
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1627
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1628
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1629
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1630
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1631
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1634
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1635
    /* // library marker kkossev.commonLib, line 1636
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1637
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1638
    */ // library marker kkossev.commonLib, line 1639
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1640
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1641
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1642

    return cmds // library marker kkossev.commonLib, line 1644
} // library marker kkossev.commonLib, line 1645

/** // library marker kkossev.commonLib, line 1647
 * Set Level Command // library marker kkossev.commonLib, line 1648
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1649
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1650
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1651
 */ // library marker kkossev.commonLib, line 1652
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1653
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1654
    if (DEVICE_TYPE in  ['ButtonDimmer']) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1655
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1656
    else { // library marker kkossev.commonLib, line 1657
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1658
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1659
        /*return*/ sendZigbeeCommands( setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1660
    } // library marker kkossev.commonLib, line 1661
} // library marker kkossev.commonLib, line 1662

/* // library marker kkossev.commonLib, line 1664
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1665
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1666
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1667
*/ // library marker kkossev.commonLib, line 1668
void parseColorControlCluster(final Map descMap, description) { // library marker kkossev.commonLib, line 1669
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1670
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1671
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1672
    } // library marker kkossev.commonLib, line 1673
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1674
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1675
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1676
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1677
    } // library marker kkossev.commonLib, line 1678
    else { // library marker kkossev.commonLib, line 1679
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1680
    } // library marker kkossev.commonLib, line 1681
} // library marker kkossev.commonLib, line 1682

/* // library marker kkossev.commonLib, line 1684
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1685
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1686
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1687
*/ // library marker kkossev.commonLib, line 1688
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1689
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1690
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1691
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1692
    def lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1693
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1694
} // library marker kkossev.commonLib, line 1695

void handleIlluminanceEvent(illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1697
    def eventMap = [:] // library marker kkossev.commonLib, line 1698
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1699
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1700
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1701
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1702
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1703
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1704
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1705
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1706
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1707
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1708
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1709
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1710
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1711
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1712
        return // library marker kkossev.commonLib, line 1713
    } // library marker kkossev.commonLib, line 1714
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1715
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1716
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1717
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1718
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1719
    } // library marker kkossev.commonLib, line 1720
    else {         // queue the event // library marker kkossev.commonLib, line 1721
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1722
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1723
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1724
    } // library marker kkossev.commonLib, line 1725
} // library marker kkossev.commonLib, line 1726

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1728
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1729
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1730
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1731
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1732
} // library marker kkossev.commonLib, line 1733

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1735

/* // library marker kkossev.commonLib, line 1737
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1738
 * temperature // library marker kkossev.commonLib, line 1739
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1740
*/ // library marker kkossev.commonLib, line 1741
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1742
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1743
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1744
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1745
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1746
} // library marker kkossev.commonLib, line 1747

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1749
    Map eventMap = [:] // library marker kkossev.commonLib, line 1750
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1751
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1752
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1753
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1754
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1755
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1756
    } // library marker kkossev.commonLib, line 1757
    else { // library marker kkossev.commonLib, line 1758
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1759
    } // library marker kkossev.commonLib, line 1760
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1761
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1762
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1763
    if (Math.abs(lastTemp - tempCorrected) < 0.001) { // library marker kkossev.commonLib, line 1764
        logTrace "skipped temperature ${tempCorrected}, less than delta 0.001 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1765
        return // library marker kkossev.commonLib, line 1766
    } // library marker kkossev.commonLib, line 1767
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1768
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1769
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1770
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1771
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1772
    } // library marker kkossev.commonLib, line 1773
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1774
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1775
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1776
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1777
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1778
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1779
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1780
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1781
    } // library marker kkossev.commonLib, line 1782
    else {         // queue the event // library marker kkossev.commonLib, line 1783
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1784
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1785
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1786
    } // library marker kkossev.commonLib, line 1787
} // library marker kkossev.commonLib, line 1788

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1790
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
    handleHumidityEvent(value / 100.0F as Float) // library marker kkossev.commonLib, line 1806
} // library marker kkossev.commonLib, line 1807

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1809
    def eventMap = [:] // library marker kkossev.commonLib, line 1810
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1811
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1812
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) { // library marker kkossev.commonLib, line 1813
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})" // library marker kkossev.commonLib, line 1814
        return // library marker kkossev.commonLib, line 1815
    } // library marker kkossev.commonLib, line 1816
    eventMap.value = Math.round(humidityAsDouble) // library marker kkossev.commonLib, line 1817
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1818
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1819
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1820
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1821
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}" // library marker kkossev.commonLib, line 1822
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1823
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1824
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1825
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1826
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1827
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1828
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1829
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
    else { // library marker kkossev.commonLib, line 1832
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1833
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1834
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1835
    } // library marker kkossev.commonLib, line 1836
} // library marker kkossev.commonLib, line 1837

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1839
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1840
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1841
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1842
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1843
} // library marker kkossev.commonLib, line 1844

/* // library marker kkossev.commonLib, line 1846
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1847
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1848
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1849
*/ // library marker kkossev.commonLib, line 1850

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1852
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1853
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1854
    } // library marker kkossev.commonLib, line 1855
} // library marker kkossev.commonLib, line 1856

/* // library marker kkossev.commonLib, line 1858
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1859
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1860
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1861
*/ // library marker kkossev.commonLib, line 1862

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1864
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1865
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1866
    } // library marker kkossev.commonLib, line 1867
} // library marker kkossev.commonLib, line 1868

/* // library marker kkossev.commonLib, line 1870
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1871
 * pm2.5 // library marker kkossev.commonLib, line 1872
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1873
*/ // library marker kkossev.commonLib, line 1874
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1875
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1876
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1877
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1878
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1879
    //logDebug "pm25 float value = ${floatValue}" // library marker kkossev.commonLib, line 1880
    handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1881
} // library marker kkossev.commonLib, line 1882
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1883

/* // library marker kkossev.commonLib, line 1885
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1886
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1887
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1888
*/ // library marker kkossev.commonLib, line 1889
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1890
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1891
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1892
    } // library marker kkossev.commonLib, line 1893
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1894
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1895
    } // library marker kkossev.commonLib, line 1896
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1897
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1898
    } // library marker kkossev.commonLib, line 1899
    else { // library marker kkossev.commonLib, line 1900
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1901
    } // library marker kkossev.commonLib, line 1902
} // library marker kkossev.commonLib, line 1903

/* // library marker kkossev.commonLib, line 1905
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1906
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1907
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1908
*/ // library marker kkossev.commonLib, line 1909

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1911
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1912
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1913
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1914
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1915
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1916
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1917
    } // library marker kkossev.commonLib, line 1918
    else { // library marker kkossev.commonLib, line 1919
        handleMultistateInputEvent(value as Integer) // library marker kkossev.commonLib, line 1920
    } // library marker kkossev.commonLib, line 1921
} // library marker kkossev.commonLib, line 1922

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1924
    def eventMap = [:] // library marker kkossev.commonLib, line 1925
    eventMap.value = value // library marker kkossev.commonLib, line 1926
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1927
    eventMap.unit = '' // library marker kkossev.commonLib, line 1928
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1929
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1930
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1931
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1932
} // library marker kkossev.commonLib, line 1933

/* // library marker kkossev.commonLib, line 1935
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1936
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1937
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1938
*/ // library marker kkossev.commonLib, line 1939

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1941
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1942
    if (DEVICE_TYPE in  ['ButtonDimmer']) { // library marker kkossev.commonLib, line 1943
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1944
    } // library marker kkossev.commonLib, line 1945
    else { // library marker kkossev.commonLib, line 1946
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
} // library marker kkossev.commonLib, line 1949

/* // library marker kkossev.commonLib, line 1951
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1952
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1953
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1954
*/ // library marker kkossev.commonLib, line 1955
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1956
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1957
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1958
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1959
    } // library marker kkossev.commonLib, line 1960
    else { // library marker kkossev.commonLib, line 1961
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1962
    } // library marker kkossev.commonLib, line 1963
} // library marker kkossev.commonLib, line 1964

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1966

void parseFC11Cluster( descMap ) { // library marker kkossev.commonLib, line 1968
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1969
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1970
    } // library marker kkossev.commonLib, line 1971
    else { // library marker kkossev.commonLib, line 1972
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1973
    } // library marker kkossev.commonLib, line 1974
} // library marker kkossev.commonLib, line 1975

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1977

void parseE002Cluster( descMap ) { // library marker kkossev.commonLib, line 1979
    if (DEVICE_TYPE in ['Radar'])     { parseE002ClusterRadar(descMap) } // library marker kkossev.commonLib, line 1980
    else { // library marker kkossev.commonLib, line 1981
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 1982
    } // library marker kkossev.commonLib, line 1983
} // library marker kkossev.commonLib, line 1984

/* // library marker kkossev.commonLib, line 1986
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1987
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1988
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1989
*/ // library marker kkossev.commonLib, line 1990
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1991
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1992
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1993

// Tuya Commands // library marker kkossev.commonLib, line 1995
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1996
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1997
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1998
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1999
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2000

// tuya DP type // library marker kkossev.commonLib, line 2002
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2003
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2004
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2005
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2006
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2007
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2008

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2010
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2011
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2012
        def offset = 0 // library marker kkossev.commonLib, line 2013
        try { // library marker kkossev.commonLib, line 2014
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2015
        //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}" // library marker kkossev.commonLib, line 2016
        } // library marker kkossev.commonLib, line 2017
        catch (e) { // library marker kkossev.commonLib, line 2018
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2019
        } // library marker kkossev.commonLib, line 2020
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2021
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2022
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2023
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2024
    } // library marker kkossev.commonLib, line 2025
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2026
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2027
        def status = descMap?.data[1] // library marker kkossev.commonLib, line 2028
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2029
        if (status != '00') { // library marker kkossev.commonLib, line 2030
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2031
        } // library marker kkossev.commonLib, line 2032
    } // library marker kkossev.commonLib, line 2033
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2034
        def dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2035
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2036
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2037
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2038
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2039
            return // library marker kkossev.commonLib, line 2040
        } // library marker kkossev.commonLib, line 2041
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2042
            def dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2043
            def dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2044
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2045
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2046
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2047
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2048
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2049
        } // library marker kkossev.commonLib, line 2050
    } // library marker kkossev.commonLib, line 2051
    else { // library marker kkossev.commonLib, line 2052
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2053
    } // library marker kkossev.commonLib, line 2054
} // library marker kkossev.commonLib, line 2055

void processTuyaDP(descMap, dp, dp_id, fncmd, dp_len=0) { // library marker kkossev.commonLib, line 2057
    if (DEVICE_TYPE in ['Radar'])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return } // library marker kkossev.commonLib, line 2058
    if (DEVICE_TYPE in ['Fingerbot'])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return } // library marker kkossev.commonLib, line 2059
    // check if the method  method exists // library marker kkossev.commonLib, line 2060
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2061
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2062
            return // library marker kkossev.commonLib, line 2063
        } // library marker kkossev.commonLib, line 2064
    } // library marker kkossev.commonLib, line 2065
    switch (dp) { // library marker kkossev.commonLib, line 2066
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2067
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2068
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2069
            } // library marker kkossev.commonLib, line 2070
            else { // library marker kkossev.commonLib, line 2071
                sendSwitchEvent(fncmd as int) // library marker kkossev.commonLib, line 2072
            } // library marker kkossev.commonLib, line 2073
            break // library marker kkossev.commonLib, line 2074
        case 0x02 : // library marker kkossev.commonLib, line 2075
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.commonLib, line 2076
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2077
            } // library marker kkossev.commonLib, line 2078
            else { // library marker kkossev.commonLib, line 2079
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2080
            } // library marker kkossev.commonLib, line 2081
            break // library marker kkossev.commonLib, line 2082
        case 0x04 : // battery // library marker kkossev.commonLib, line 2083
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2084
            break // library marker kkossev.commonLib, line 2085
        default : // library marker kkossev.commonLib, line 2086
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2087
            break // library marker kkossev.commonLib, line 2088
    } // library marker kkossev.commonLib, line 2089
} // library marker kkossev.commonLib, line 2090

private int getTuyaAttributeValue(ArrayList _data, index) { // library marker kkossev.commonLib, line 2092
    int retValue = 0 // library marker kkossev.commonLib, line 2093

    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2095
        int dataLength = _data[5 + index] as Integer // library marker kkossev.commonLib, line 2096
        int power = 1 // library marker kkossev.commonLib, line 2097
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2098
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2099
            power = power * 256 // library marker kkossev.commonLib, line 2100
        } // library marker kkossev.commonLib, line 2101
    } // library marker kkossev.commonLib, line 2102
    return retValue // library marker kkossev.commonLib, line 2103
} // library marker kkossev.commonLib, line 2104

private sendTuyaCommand(dp, dp_type, fncmd) { // library marker kkossev.commonLib, line 2106
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2107
    def ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2108
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2109
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2110

    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2112
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2113
    return cmds // library marker kkossev.commonLib, line 2114
} // library marker kkossev.commonLib, line 2115

private getPACKET_ID() { // library marker kkossev.commonLib, line 2117
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2118
} // library marker kkossev.commonLib, line 2119

def tuyaTest( dpCommand, dpValue, dpTypeString ) { // library marker kkossev.commonLib, line 2121
    //ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2122
    def dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2123
    def dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2124

    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2126

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2128
} // library marker kkossev.commonLib, line 2129

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2131
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2132

def tuyaBlackMagic() { // library marker kkossev.commonLib, line 2134
    def ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2135
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2136
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2137
} // library marker kkossev.commonLib, line 2138

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2140
    List<String> cmds = [] // library marker kkossev.commonLib, line 2141
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2142
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2143
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2144
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2145
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2146
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2147
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2148
        } // library marker kkossev.commonLib, line 2149
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2150
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2151
    } // library marker kkossev.commonLib, line 2152
    else { // library marker kkossev.commonLib, line 2153
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2154
    } // library marker kkossev.commonLib, line 2155
} // library marker kkossev.commonLib, line 2156

/** // library marker kkossev.commonLib, line 2158
 * initializes the device // library marker kkossev.commonLib, line 2159
 * Invoked from configure() // library marker kkossev.commonLib, line 2160
 * @return zigbee commands // library marker kkossev.commonLib, line 2161
 */ // library marker kkossev.commonLib, line 2162
def initializeDevice() { // library marker kkossev.commonLib, line 2163
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2164
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2165

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2167
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2168
        return customInitializeDevice() // library marker kkossev.commonLib, line 2169
    } // library marker kkossev.commonLib, line 2170

    if (DEVICE_TYPE in  ['AirQuality'])          { return initializeDeviceAirQuality() } // library marker kkossev.commonLib, line 2172
    else if (DEVICE_TYPE in  ['IRBlaster'])      { return initializeDeviceIrBlaster() } // library marker kkossev.commonLib, line 2173
    else if (DEVICE_TYPE in  ['Radar'])          { return initializeDeviceRadar() } // library marker kkossev.commonLib, line 2174
    else if (DEVICE_TYPE in  ['ButtonDimmer'])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2175

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
 * Invoked from updated() // library marker kkossev.commonLib, line 2191
 * @return zigbee commands // library marker kkossev.commonLib, line 2192
 */ // library marker kkossev.commonLib, line 2193
def configureDevice() { // library marker kkossev.commonLib, line 2194
    List<String> cmds = [] // library marker kkossev.commonLib, line 2195
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2196

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2198
        cmds += customConfigureDevice() // library marker kkossev.commonLib, line 2199
    } // library marker kkossev.commonLib, line 2200
    else if (DEVICE_TYPE in  ['AirQuality']) { cmds += configureDeviceAirQuality() } // library marker kkossev.commonLib, line 2201
    else if (DEVICE_TYPE in  ['Fingerbot'])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2202
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2203
    else if (DEVICE_TYPE in  ['IRBlaster'])  { cmds += configureDeviceIrBlaster() } // library marker kkossev.commonLib, line 2204
    else if (DEVICE_TYPE in  ['Radar'])      { cmds += configureDeviceRadar() } // library marker kkossev.commonLib, line 2205
    else if (DEVICE_TYPE in  ['ButtonDimmer']) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2206
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2207
    if ( cmds == null || cmds == []) { // library marker kkossev.commonLib, line 2208
        cmds = ['delay 277',] // library marker kkossev.commonLib, line 2209
    } // library marker kkossev.commonLib, line 2210
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2211
} // library marker kkossev.commonLib, line 2212

/* // library marker kkossev.commonLib, line 2214
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2215
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2216
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2217
*/ // library marker kkossev.commonLib, line 2218

void refresh() { // library marker kkossev.commonLib, line 2220
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2221
    checkDriverVersion() // library marker kkossev.commonLib, line 2222
    List<String> cmds = [] // library marker kkossev.commonLib, line 2223
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2224

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2226
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2227
        cmds += customRefresh() // library marker kkossev.commonLib, line 2228

    } // library marker kkossev.commonLib, line 2230
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2231
    else if (DEVICE_TYPE in  ['Fingerbot'])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2232
    else if (DEVICE_TYPE in  ['AirQuality']) { cmds += refreshAirQuality() } // library marker kkossev.commonLib, line 2233
    else if (DEVICE_TYPE in  ['IRBlaster'])  { cmds += refreshIrBlaster() } // library marker kkossev.commonLib, line 2234
    else if (DEVICE_TYPE in  ['Radar'])      { cmds += refreshRadar() } // library marker kkossev.commonLib, line 2235
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2236
    else { // library marker kkossev.commonLib, line 2237
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2238
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2239
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2240
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2241
        } // library marker kkossev.commonLib, line 2242
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2243
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2244
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2245
        } // library marker kkossev.commonLib, line 2246
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2247
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2248
        } // library marker kkossev.commonLib, line 2249
        if (DEVICE_TYPE in  ['THSensor', 'AirQuality']) { // library marker kkossev.commonLib, line 2250
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2251
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2252
        } // library marker kkossev.commonLib, line 2253
    } // library marker kkossev.commonLib, line 2254

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2256
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2257
    } // library marker kkossev.commonLib, line 2258
    else { // library marker kkossev.commonLib, line 2259
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2260
    } // library marker kkossev.commonLib, line 2261
} // library marker kkossev.commonLib, line 2262

def setRefreshRequest()   { if (state.states == null) { state.states = [:] };   state.states['isRefresh'] = true; runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds // library marker kkossev.commonLib, line 2264
def clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2265

void clearInfoEvent() { // library marker kkossev.commonLib, line 2267
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2268
} // library marker kkossev.commonLib, line 2269

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2271
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2272
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2273
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2274
    } // library marker kkossev.commonLib, line 2275
    else { // library marker kkossev.commonLib, line 2276
        logInfo "${info}" // library marker kkossev.commonLib, line 2277
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2278
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2279
    } // library marker kkossev.commonLib, line 2280
} // library marker kkossev.commonLib, line 2281

void ping() { // library marker kkossev.commonLib, line 2283
    if (!(isAqaraTVOC_OLD())) { // library marker kkossev.commonLib, line 2284
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2285
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2286
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2287
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2288
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2289
        if (!isVirtual()) { // library marker kkossev.commonLib, line 2290
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2291
        } // library marker kkossev.commonLib, line 2292
        else { // library marker kkossev.commonLib, line 2293
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2294
        } // library marker kkossev.commonLib, line 2295
        logDebug 'ping...' // library marker kkossev.commonLib, line 2296
    } // library marker kkossev.commonLib, line 2297
    else { // library marker kkossev.commonLib, line 2298
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2299
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2300
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2301
    } // library marker kkossev.commonLib, line 2302
} // library marker kkossev.commonLib, line 2303

def virtualPong() { // library marker kkossev.commonLib, line 2305
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2306
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2307
    def timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2308
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2309
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2310
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2311
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2312
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2313
        sendRttEvent() // library marker kkossev.commonLib, line 2314
    } // library marker kkossev.commonLib, line 2315
    else { // library marker kkossev.commonLib, line 2316
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2317
    } // library marker kkossev.commonLib, line 2318
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2319
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2320
} // library marker kkossev.commonLib, line 2321

/** // library marker kkossev.commonLib, line 2323
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2324
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2325
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2326
 * @return none // library marker kkossev.commonLib, line 2327
 */ // library marker kkossev.commonLib, line 2328
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2329
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2330
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2331
    def timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2332
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2333
    if (value == null) { // library marker kkossev.commonLib, line 2334
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2335
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2336
    } // library marker kkossev.commonLib, line 2337
    else { // library marker kkossev.commonLib, line 2338
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2339
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2340
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2341
    } // library marker kkossev.commonLib, line 2342
} // library marker kkossev.commonLib, line 2343

/** // library marker kkossev.commonLib, line 2345
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2346
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2347
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2348
 */ // library marker kkossev.commonLib, line 2349
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2350
    if (cluster != null) { // library marker kkossev.commonLib, line 2351
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2352
    } // library marker kkossev.commonLib, line 2353
    else { // library marker kkossev.commonLib, line 2354
        logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2355
        return 'NULL' // library marker kkossev.commonLib, line 2356
    } // library marker kkossev.commonLib, line 2357
} // library marker kkossev.commonLib, line 2358

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2360
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2361
} // library marker kkossev.commonLib, line 2362

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2364
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2365
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2366
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2367
} // library marker kkossev.commonLib, line 2368

/** // library marker kkossev.commonLib, line 2370
 * Schedule a device health check // library marker kkossev.commonLib, line 2371
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2372
 */ // library marker kkossev.commonLib, line 2373
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2374
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2375
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2376
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2377
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2378
    } // library marker kkossev.commonLib, line 2379
    else { // library marker kkossev.commonLib, line 2380
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2381
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2382
    } // library marker kkossev.commonLib, line 2383
} // library marker kkossev.commonLib, line 2384

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2386
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2387
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2388
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2389
} // library marker kkossev.commonLib, line 2390

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2392
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2393
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2394
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2395
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2396
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2397
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2398
    } // library marker kkossev.commonLib, line 2399
} // library marker kkossev.commonLib, line 2400

def deviceHealthCheck() { // library marker kkossev.commonLib, line 2402
    checkDriverVersion() // library marker kkossev.commonLib, line 2403
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2404
    def ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2405
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2406
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2407
            logWarn 'not present!' // library marker kkossev.commonLib, line 2408
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2409
        } // library marker kkossev.commonLib, line 2410
    } // library marker kkossev.commonLib, line 2411
    else { // library marker kkossev.commonLib, line 2412
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2413
    } // library marker kkossev.commonLib, line 2414
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2415
} // library marker kkossev.commonLib, line 2416

void sendHealthStatusEvent(value) { // library marker kkossev.commonLib, line 2418
    def descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2419
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2420
    if (value == 'online') { // library marker kkossev.commonLib, line 2421
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2422
    } // library marker kkossev.commonLib, line 2423
    else { // library marker kkossev.commonLib, line 2424
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2425
    } // library marker kkossev.commonLib, line 2426
} // library marker kkossev.commonLib, line 2427

/** // library marker kkossev.commonLib, line 2429
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2430
 */ // library marker kkossev.commonLib, line 2431
void autoPoll() { // library marker kkossev.commonLib, line 2432
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2433
    checkDriverVersion() // library marker kkossev.commonLib, line 2434
    List<String> cmds = [] // library marker kkossev.commonLib, line 2435
    //if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2436
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2437

    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2439
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2440
    } // library marker kkossev.commonLib, line 2441

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2443
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2444
    } // library marker kkossev.commonLib, line 2445
} // library marker kkossev.commonLib, line 2446

/** // library marker kkossev.commonLib, line 2448
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2449
 */ // library marker kkossev.commonLib, line 2450
void updated() { // library marker kkossev.commonLib, line 2451
    logInfo 'updated...' // library marker kkossev.commonLib, line 2452
    checkDriverVersion() // library marker kkossev.commonLib, line 2453
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2454
    unschedule() // library marker kkossev.commonLib, line 2455

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2457
        logTrace settings // library marker kkossev.commonLib, line 2458
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2459
    } // library marker kkossev.commonLib, line 2460
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2461
        logTrace settings // library marker kkossev.commonLib, line 2462
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2463
    } // library marker kkossev.commonLib, line 2464

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2466
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2467
        // schedule the periodic timer // library marker kkossev.commonLib, line 2468
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2469
        if (interval > 0) { // library marker kkossev.commonLib, line 2470
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2471
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2472
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2473
        } // library marker kkossev.commonLib, line 2474
    } // library marker kkossev.commonLib, line 2475
    else { // library marker kkossev.commonLib, line 2476
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2477
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2478
    } // library marker kkossev.commonLib, line 2479
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2480
        customUpdated() // library marker kkossev.commonLib, line 2481
    } // library marker kkossev.commonLib, line 2482
    if (DEVICE_TYPE in ['AirQuality'])  { updatedAirQuality() } // library marker kkossev.commonLib, line 2483
    if (DEVICE_TYPE in ['IRBlaster'])   { updatedIrBlaster() } // library marker kkossev.commonLib, line 2484

    //configureDevice()    // sends Zigbee commands  // commented out 11/18/2023 // library marker kkossev.commonLib, line 2486

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2488
} // library marker kkossev.commonLib, line 2489

/** // library marker kkossev.commonLib, line 2491
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2492
 */ // library marker kkossev.commonLib, line 2493
void logsOff() { // library marker kkossev.commonLib, line 2494
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2495
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2496
} // library marker kkossev.commonLib, line 2497
void traceOff() { // library marker kkossev.commonLib, line 2498
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2499
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2500
} // library marker kkossev.commonLib, line 2501

void configure(String command) { // library marker kkossev.commonLib, line 2503
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2504
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2505
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2506
        return // library marker kkossev.commonLib, line 2507
    } // library marker kkossev.commonLib, line 2508
    // // library marker kkossev.commonLib, line 2509
    String func // library marker kkossev.commonLib, line 2510
    try { // library marker kkossev.commonLib, line 2511
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2512
        /*cmds =*/ "$func"() // library marker kkossev.commonLib, line 2513
    } // library marker kkossev.commonLib, line 2514
    catch (e) { // library marker kkossev.commonLib, line 2515
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2516
        return // library marker kkossev.commonLib, line 2517
    } // library marker kkossev.commonLib, line 2518
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2519
} // library marker kkossev.commonLib, line 2520

void configureHelp( val ) { // library marker kkossev.commonLib, line 2522
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2523
} // library marker kkossev.commonLib, line 2524

void loadAllDefaults() { // library marker kkossev.commonLib, line 2526
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2527
    deleteAllSettings() // library marker kkossev.commonLib, line 2528
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2529
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2530
    deleteAllStates() // library marker kkossev.commonLib, line 2531
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2532
    initialize() // library marker kkossev.commonLib, line 2533
    configure() // library marker kkossev.commonLib, line 2534
    updated() // calls  also   configureDevice() // library marker kkossev.commonLib, line 2535
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2536
} // library marker kkossev.commonLib, line 2537

void configureNow() { // library marker kkossev.commonLib, line 2539
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2540
} // library marker kkossev.commonLib, line 2541

/** // library marker kkossev.commonLib, line 2543
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2544
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2545
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2546
 */ // library marker kkossev.commonLib, line 2547
List<String> configure() { // library marker kkossev.commonLib, line 2548
    List<String> cmds = [] // library marker kkossev.commonLib, line 2549
    logInfo 'configure...' // library marker kkossev.commonLib, line 2550
    logDebug settings // library marker kkossev.commonLib, line 2551
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2552
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2553
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2554
    } // library marker kkossev.commonLib, line 2555
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2556
    cmds += configureDevice() // library marker kkossev.commonLib, line 2557
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2558
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2559
    return cmds // library marker kkossev.commonLib, line 2560
} // library marker kkossev.commonLib, line 2561

/** // library marker kkossev.commonLib, line 2563
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2564
 */ // library marker kkossev.commonLib, line 2565
void installed() { // library marker kkossev.commonLib, line 2566
    logInfo 'installed...' // library marker kkossev.commonLib, line 2567
    // populate some default values for attributes // library marker kkossev.commonLib, line 2568
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2569
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2570
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2571
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2572
} // library marker kkossev.commonLib, line 2573

/** // library marker kkossev.commonLib, line 2575
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2576
 */ // library marker kkossev.commonLib, line 2577
void initialize() { // library marker kkossev.commonLib, line 2578
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2579
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2580
    updateTuyaVersion() // library marker kkossev.commonLib, line 2581
    updateAqaraVersion() // library marker kkossev.commonLib, line 2582
} // library marker kkossev.commonLib, line 2583

/* // library marker kkossev.commonLib, line 2585
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2586
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2587
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2588
*/ // library marker kkossev.commonLib, line 2589

static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2591
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2592
} // library marker kkossev.commonLib, line 2593

static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2595
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2596
} // library marker kkossev.commonLib, line 2597

static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2599
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2600
} // library marker kkossev.commonLib, line 2601

void sendZigbeeCommands(ArrayList<String> cmd) { // library marker kkossev.commonLib, line 2603
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2604
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2605
    cmd.each { // library marker kkossev.commonLib, line 2606
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2607
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2608
    } // library marker kkossev.commonLib, line 2609
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2610
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2611
} // library marker kkossev.commonLib, line 2612

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model') } ${device.getDataValue('manufacturer') }) (${getModel()} ${location.hub.firmwareVersionString}) "} // library marker kkossev.commonLib, line 2614

String getDeviceInfo() { // library marker kkossev.commonLib, line 2616
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2617
} // library marker kkossev.commonLib, line 2618

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2620
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2621
} // library marker kkossev.commonLib, line 2622

void checkDriverVersion() { // library marker kkossev.commonLib, line 2624
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2625
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2626
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2627
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2628
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2629
        updateTuyaVersion() // library marker kkossev.commonLib, line 2630
        updateAqaraVersion() // library marker kkossev.commonLib, line 2631
    } // library marker kkossev.commonLib, line 2632
    // no driver version change // library marker kkossev.commonLib, line 2633
} // library marker kkossev.commonLib, line 2634

// credits @thebearmay // library marker kkossev.commonLib, line 2636
String getModel() { // library marker kkossev.commonLib, line 2637
    try { // library marker kkossev.commonLib, line 2638
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2639
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2640
    } catch (ignore) { // library marker kkossev.commonLib, line 2641
        try { // library marker kkossev.commonLib, line 2642
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2643
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2644
                return model // library marker kkossev.commonLib, line 2645
            } // library marker kkossev.commonLib, line 2646
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2647
            return '' // library marker kkossev.commonLib, line 2648
        } // library marker kkossev.commonLib, line 2649
    } // library marker kkossev.commonLib, line 2650
} // library marker kkossev.commonLib, line 2651

// credits @thebearmay // library marker kkossev.commonLib, line 2653
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2654
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2655
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2656
    String revision = tokens.last() // library marker kkossev.commonLib, line 2657
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2658
} // library marker kkossev.commonLib, line 2659

/** // library marker kkossev.commonLib, line 2661
 * called from TODO // library marker kkossev.commonLib, line 2662
 */ // library marker kkossev.commonLib, line 2663

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2665
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2666
    unschedule() // library marker kkossev.commonLib, line 2667
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2668
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2669

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2671
} // library marker kkossev.commonLib, line 2672

void resetStatistics() { // library marker kkossev.commonLib, line 2674
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2675
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2676
} // library marker kkossev.commonLib, line 2677

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2679
void resetStats() { // library marker kkossev.commonLib, line 2680
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2681
    state.stats = [:] // library marker kkossev.commonLib, line 2682
    state.states = [:] // library marker kkossev.commonLib, line 2683
    state.lastRx = [:] // library marker kkossev.commonLib, line 2684
    state.lastTx = [:] // library marker kkossev.commonLib, line 2685
    state.health = [:] // library marker kkossev.commonLib, line 2686
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2687
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2688
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2689
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2690
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2691
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2692
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2693
} // library marker kkossev.commonLib, line 2694

/** // library marker kkossev.commonLib, line 2696
 * called from TODO // library marker kkossev.commonLib, line 2697
 */ // library marker kkossev.commonLib, line 2698
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2699
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2700
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2701
        state.clear() // library marker kkossev.commonLib, line 2702
        unschedule() // library marker kkossev.commonLib, line 2703
        resetStats() // library marker kkossev.commonLib, line 2704
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2705
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2706
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2707
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2708
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2709
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2710
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2711
    } // library marker kkossev.commonLib, line 2712

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2714
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2715
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2716
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2717
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2718
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2719

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2721
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2722
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2723
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2724
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2725
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2726
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2727
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2728
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2729
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2730
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2731
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2732
    } // library marker kkossev.commonLib, line 2733
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2734
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2735
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2736
    } // library marker kkossev.commonLib, line 2737
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2738
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2739
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2740
    if (DEVICE_TYPE in ['AirQuality']) { initVarsAirQuality(fullInit) } // library marker kkossev.commonLib, line 2741
    if (DEVICE_TYPE in ['Fingerbot'])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2742
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2743
    if (DEVICE_TYPE in ['IRBlaster'])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none // library marker kkossev.commonLib, line 2744
    if (DEVICE_TYPE in ['Radar'])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none // library marker kkossev.commonLib, line 2745
    if (DEVICE_TYPE in ['ButtonDimmer']) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2746
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2747

    def mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2749
    if ( mm != null) { // library marker kkossev.commonLib, line 2750
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2751
    } // library marker kkossev.commonLib, line 2752
    else { // library marker kkossev.commonLib, line 2753
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2754
    } // library marker kkossev.commonLib, line 2755
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2756
    if ( ep  != null) { // library marker kkossev.commonLib, line 2757
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2758
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2759
    } // library marker kkossev.commonLib, line 2760
    else { // library marker kkossev.commonLib, line 2761
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2762
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2763
    } // library marker kkossev.commonLib, line 2764
} // library marker kkossev.commonLib, line 2765

/** // library marker kkossev.commonLib, line 2767
 * called from TODO // library marker kkossev.commonLib, line 2768
 */ // library marker kkossev.commonLib, line 2769
void setDestinationEP() { // library marker kkossev.commonLib, line 2770
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2771
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2772
        state.destinationEP = ep // library marker kkossev.commonLib, line 2773
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2774
    } // library marker kkossev.commonLib, line 2775
    else { // library marker kkossev.commonLib, line 2776
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2777
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2778
    } // library marker kkossev.commonLib, line 2779
} // library marker kkossev.commonLib, line 2780

def logDebug(msg) { // library marker kkossev.commonLib, line 2782
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2783
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2784
    } // library marker kkossev.commonLib, line 2785
} // library marker kkossev.commonLib, line 2786

def logInfo(msg) { // library marker kkossev.commonLib, line 2788
    if (settings.txtEnable) { // library marker kkossev.commonLib, line 2789
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2790
    } // library marker kkossev.commonLib, line 2791
} // library marker kkossev.commonLib, line 2792

def logWarn(msg) { // library marker kkossev.commonLib, line 2794
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2795
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2796
    } // library marker kkossev.commonLib, line 2797
} // library marker kkossev.commonLib, line 2798

def logTrace(msg) { // library marker kkossev.commonLib, line 2800
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2801
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2802
    } // library marker kkossev.commonLib, line 2803
} // library marker kkossev.commonLib, line 2804

// _DEBUG mode only // library marker kkossev.commonLib, line 2806
void getAllProperties() { // library marker kkossev.commonLib, line 2807
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2808
    device.properties.each { it -> // library marker kkossev.commonLib, line 2809
        log.debug it // library marker kkossev.commonLib, line 2810
    } // library marker kkossev.commonLib, line 2811
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2812
    settings.each { it -> // library marker kkossev.commonLib, line 2813
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2814
    } // library marker kkossev.commonLib, line 2815
    log.trace 'Done' // library marker kkossev.commonLib, line 2816
} // library marker kkossev.commonLib, line 2817

// delete all Preferences // library marker kkossev.commonLib, line 2819
void deleteAllSettings() { // library marker kkossev.commonLib, line 2820
    settings.each { it -> // library marker kkossev.commonLib, line 2821
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2822
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2823
    } // library marker kkossev.commonLib, line 2824
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2825
} // library marker kkossev.commonLib, line 2826

// delete all attributes // library marker kkossev.commonLib, line 2828
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2829
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2830
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2831
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2832
    } // library marker kkossev.commonLib, line 2833
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2834
} // library marker kkossev.commonLib, line 2835

// delete all State Variables // library marker kkossev.commonLib, line 2837
void deleteAllStates() { // library marker kkossev.commonLib, line 2838
    state.each { it -> // library marker kkossev.commonLib, line 2839
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2840
    } // library marker kkossev.commonLib, line 2841
    state.clear() // library marker kkossev.commonLib, line 2842
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2843
} // library marker kkossev.commonLib, line 2844

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2846
    unschedule() // library marker kkossev.commonLib, line 2847
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2848
} // library marker kkossev.commonLib, line 2849

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2851
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2852
} // library marker kkossev.commonLib, line 2853

void parseTest(String par) { // library marker kkossev.commonLib, line 2855
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2856
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2857
    parse(par) // library marker kkossev.commonLib, line 2858
} // library marker kkossev.commonLib, line 2859

def testJob() { // library marker kkossev.commonLib, line 2861
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2862
} // library marker kkossev.commonLib, line 2863

/** // library marker kkossev.commonLib, line 2865
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2866
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2867
 */ // library marker kkossev.commonLib, line 2868
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2869
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2870
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2871
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2872
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2873
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2874
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2875
    String cron // library marker kkossev.commonLib, line 2876
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2877
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2878
    } // library marker kkossev.commonLib, line 2879
    else { // library marker kkossev.commonLib, line 2880
        if (minutes < 60) { // library marker kkossev.commonLib, line 2881
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2882
        } // library marker kkossev.commonLib, line 2883
        else { // library marker kkossev.commonLib, line 2884
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2885
        } // library marker kkossev.commonLib, line 2886
    } // library marker kkossev.commonLib, line 2887
    return cron // library marker kkossev.commonLib, line 2888
} // library marker kkossev.commonLib, line 2889

// credits @thebearmay // library marker kkossev.commonLib, line 2891
String formatUptime() { // library marker kkossev.commonLib, line 2892
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2893
} // library marker kkossev.commonLib, line 2894

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2896
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2897
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2898
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2899
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2900
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2901
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2902
} // library marker kkossev.commonLib, line 2903

boolean isTuya() { // library marker kkossev.commonLib, line 2905
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2906
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2907
    if (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) { // library marker kkossev.commonLib, line 2908
        return true // library marker kkossev.commonLib, line 2909
    } // library marker kkossev.commonLib, line 2910
    return false // library marker kkossev.commonLib, line 2911
} // library marker kkossev.commonLib, line 2912

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2914
    if (!isTuya()) { // library marker kkossev.commonLib, line 2915
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2916
        return // library marker kkossev.commonLib, line 2917
    } // library marker kkossev.commonLib, line 2918
    def application = device.getDataValue('application') // library marker kkossev.commonLib, line 2919
    if (application != null) { // library marker kkossev.commonLib, line 2920
        Integer ver // library marker kkossev.commonLib, line 2921
        try { // library marker kkossev.commonLib, line 2922
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2923
        } // library marker kkossev.commonLib, line 2924
        catch (e) { // library marker kkossev.commonLib, line 2925
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2926
            return // library marker kkossev.commonLib, line 2927
        } // library marker kkossev.commonLib, line 2928
        def str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2929
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2930
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2931
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2932
        } // library marker kkossev.commonLib, line 2933
    } // library marker kkossev.commonLib, line 2934
} // library marker kkossev.commonLib, line 2935

boolean isAqara() { // library marker kkossev.commonLib, line 2937
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2938
} // library marker kkossev.commonLib, line 2939

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2941
    if (!isAqara()) { // library marker kkossev.commonLib, line 2942
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2943
        return // library marker kkossev.commonLib, line 2944
    } // library marker kkossev.commonLib, line 2945
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2946
    if (application != null) { // library marker kkossev.commonLib, line 2947
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2948
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2949
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2950
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2951
        } // library marker kkossev.commonLib, line 2952
    } // library marker kkossev.commonLib, line 2953
} // library marker kkossev.commonLib, line 2954

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2956
    try { // library marker kkossev.commonLib, line 2957
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2958
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2959
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2960
    } catch (e) { // library marker kkossev.commonLib, line 2961
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2962
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2963
    } // library marker kkossev.commonLib, line 2964
} // library marker kkossev.commonLib, line 2965

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2967
    try { // library marker kkossev.commonLib, line 2968
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2969
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2970
        return date.getTime() // library marker kkossev.commonLib, line 2971
    } catch (e) { // library marker kkossev.commonLib, line 2972
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2973
        return now() // library marker kkossev.commonLib, line 2974
    } // library marker kkossev.commonLib, line 2975
} // library marker kkossev.commonLib, line 2976

void test(String par) { // library marker kkossev.commonLib, line 2978
    List<String> cmds = [] // library marker kkossev.commonLib, line 2979
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2980

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2982
    //parse(par) // library marker kkossev.commonLib, line 2983

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2985
} // library marker kkossev.commonLib, line 2986

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
