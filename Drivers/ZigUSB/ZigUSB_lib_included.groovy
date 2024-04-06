/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, ParameterCount, UnnecessaryGetter */
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
 * ver. 1.0.0  2024-02-25 kkossev  - first test version - decoding success! refresh() and configure();
 * ver. 1.0.1  2024-04-01 kkossev  - commonLib 3.0.4 alligned
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) more GroovyLint fixes; created energyLib.groovy library;
 *
 *                                   TODO: individual thresholds for each attribute
 */

static String version() { '1.0.2' }
static String timeStamp() { '2024/04/06 10:57 AM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

deviceType = 'Plug'
@Field static final String DEVICE_TYPE = 'Plug'

/* groovylint-disable-next-line NglParseError */



metadata {
    definition(
        name: 'ZigUSB',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/ZigUSB/ZigUSB.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        if (_DEBUG) {
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']]
            command 'tuyaTest', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
        }
        capability 'Actuator'
        capability 'Outlet'
        capability 'Switch'
        //capability 'TemperatureMeasurement'   // do not expose the capability, this is the device internal temperature!
        capability 'PowerMeter'
        capability 'EnergyMeter'
        capability 'VoltageMeasurement'
        capability 'CurrentMeter'

        attribute 'temperature', 'number'   // make it a custom attribute

        if (_THREE_STATE == true) {
            attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String>
        }

        // deviceType specific capabilities, commands and attributes
        if (_DEBUG || (deviceType in ['Dimmer', 'ButtonDimmer', 'Switch', 'Valve'])) {
            command 'zigbeeGroups', [
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>],
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']]
            ]
        }
        // https://github.com/xyzroe/ZigUSB
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b/src/devices/xyzroe.ts
        // https://github.com/Koenkk/zigbee-herdsman-converters/pull/7077 https://github.com/Koenkk/zigbee-herdsman-converters/commit/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0007,0006', outClusters:'0000,0006', model:'ZigUSB', manufacturer:'xyzroe.cc', deviceJoinName: 'Zigbee USB power monitor and switch'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: '<i>Disable switching OFF for plugs that must be always On</i>', defaultValue: false
        input name: 'autoReportingTime', type: 'number', title: '<b>Automatic reporting time period</b>', description: '<i>V/A/W reporting interval, seconds (0..3600)<br>0 (zero) disables the automatic reporting!</i>', range: '0..3600', defaultValue: DEFAULT_REPORTING_TIME
        if (advancedOptions == true || advancedOptions == true) {
            input name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: '<i>Some switches and plugs send periodically the switch status as a heart-beat </i>', defaultValue: true
            input name: 'inverceSwitch', type: 'bool', title: '<b>Invert the switch on/off</b>', description: '<i>ZigUSB has the on and off states inverted!</i>', defaultValue: true
        }
    }
}

@Field static final int    DEFAULT_REPORTING_TIME = 30
@Field static final int    DEFAULT_PRECISION = 3           // 3 decimal places
@Field static final BigDecimal DEFAULT_DELTA = 0.001
@Field static final int    MAX_POWER_LIMIT = 999
@Field static final String ONOFF = 'Switch'
@Field static final String POWER = 'Power'
@Field static final String INST_POWER = 'InstPower'
@Field static final String ENERGY = 'Energy'
@Field static final String VOLTAGE = 'Voltage'
@Field static final String AMPERAGE = 'Amperage'
@Field static final String FREQUENCY = 'Frequency'
@Field static final String POWER_FACTOR = 'PowerFactor'

/**
 * ZigUSB has a really wierd way of reporting the on/off state back to the hub...
 */
void customParseDefaultCommandResponse(final Map descMap) {
    logDebug "ZigUSB:  parseDefaultCommandResponse: ${descMap}"
    parseOnOffCluster([attrId: '0000', value: descMap.data[0]])
}

List<String> customRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [destEndpoint :01], delay = 200)     // switch state
    // ANALOG_INPUT_CLUSTER attribute 0x0055 error: Unsupported COMMAND
    //cmds += zigbee.readAttribute(0x000C, 0x0055, [destEndpoint :02], delay=200)     // current, voltage, power, reporting interval
    //  TEMPERATURE_MEASUREMENT_CLUSTER attribute 0x0000 error: 0x[00, 00, 8F]
    //cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint :04], delay=200)     // temperature
    // ANALOG_INPUT_CLUSTER attribute 0x0055 error: Unsupported COMMAND
    //cmds += zigbee.readAttribute(0x000C, 0x0055, [destEndpoint :05], delay=200)     // uptime
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

void customInitVars(boolean fullInit=false) {
    logDebug "customInitVars(${fullInit})"
    if (fullInit || settings?.threeStateEnable == null) { device.updateSetting('threeStateEnable', false) }
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) }
    if (fullInit || settings?.inverceSwitch == null) { device.updateSetting('inverceSwitch', true) }
    if (fullInit || settings?.autoReportingTime == null) { device.updateSetting('autoReportingTime', DEFAULT_REPORTING_TIME) }
}

List<String> customConfigureDevice() {
    logInfo 'Configuring the device...'
    List<String> cmds = []
    int intMinTime = 1
    int intMaxTime = (settings?.autoReportingTime as int) ?: 60
    //cmds += configureReporting("Write", ONOFF,  "1", "30", "0", sendNow=false)    // switch state should be always reported
    cmds += configureReporting('Write', ONOFF,  intMinTime.toString(), intMaxTime.toString(), '0', sendNow = false)    // switch state should be always reported
    if (settings?.autoReportingTime != 0) {
        cmds += zigbee.configureReporting(0x000C, 0x0055, DataType.UINT16, intMinTime, intMaxTime, 0, [destEndpoint: 02])   // current, voltage, power, reporting interval
        logInfo "configuring the automatic reporting  : ${intMaxTime} seconds"
    }
    else {
        cmds += zigbee.configureReporting(0x000C, 0x0055, DataType.UINT16, 0xFFFF, 0xFFFF, 0, [destEndpoint: 02])   // disable reporting
        logInfo 'configuring the automatic reporting  : DISABLED'
    }
    cmds += zigbee.reportingConfiguration(0x000C, 0x0055, [destEndpoint: 02], 200)
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

void customUpdated() {
    logDebug 'customUpdated()'
    List<String> cmds = customConfigureDevice()
    sendZigbeeCommands(cmds)
    if (settings?.autoReportingTime == 0) {
        device.deleteCurrentState('amperage')
        device.deleteCurrentState('voltage')
        device.deleteCurrentState('power')
    }
}

void customParseAnalogInputClusterDescription(String description) {
    Map descMap = myParseDescriptionAsMap(description)
    if (descMap == null) {
        logWarn 'customParseAnalogInputClusterDescription: descMap is null'
        return
    }
    // descMap=[raw:1C9F02000C1E55003915AEA7401C004204562C3430, dni:1C9F, endpoint:02, cluster:000C, size:1E, attrId:0055, encoding:39, command:0A, value:40A7AE15, clusterInt:12, attrInt:85, additionalAttrs:[[value:V,40, encoding:42, attrId:001C, consumedBytes:7, attrInt:28]]]
    Map additionalAttrs = [:]
    if (descMap.additionalAttrs != null && descMap.additionalAttrs.size() > 0) {
        additionalAttrs = descMap.additionalAttrs[0] ?: [:]
    }
    // additionalAttrs=[value:W,40, encoding:42, attrId:001C, consumedBytes:7, attrInt:28]
    //logDebug "customParseAnalogInputClusterDescription: additionalAttrs=${additionalAttrs}"
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
        logTrace "customParseAnalogInputClusterDescription: measurementType=${measurementType}"
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
                logDebug "customParseAnalogInputClusterDescription: (0x001C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} "
            }
            else {
                try {
                    value = hexStrToUnsignedInt(descMap.value)
                    floatValue = Float.intBitsToFloat(value.intValue())
                    logDebug "customParseAnalogInputClusterDescription: (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
                    switch (measurementType) {
                        case VOLTAGE : sendVoltageEvent(floatValue, false); break
                        case AMPERAGE : sendAmperageEvent(floatValue, false); break
                        case POWER : sendPowerEvent(floatValue, false); break
                        default : logInfo "${measurementType} is ${floatValue.setScale(3, BigDecimal.ROUND_HALF_UP)} (raw:${value})"; break
                    }
                }
                catch (e) {
                    logWarn "customParseAnalogInputClusterDescription: EXCEPTION (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
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
            logWarn "customParseAnalogInputClusterDescription: (0x000C) <b>endpoint:${descMap.endpoint}</b> attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value} floatValue=${floatValue}"
            break
    }
}

void customParseElectricalMeasureCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

void customParseMeteringCluster(Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMeteringCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (166) kkossev.energyLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.energyLib, line 1
library( // library marker kkossev.energyLib, line 2
    base: 'driver', // library marker kkossev.energyLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.energyLib, line 4
    category: 'zigbee', // library marker kkossev.energyLib, line 5
    description: 'Zigbee Energy Library', // library marker kkossev.energyLib, line 6
    name: 'energyLib', // library marker kkossev.energyLib, line 7
    namespace: 'kkossev', // library marker kkossev.energyLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', // library marker kkossev.energyLib, line 9
    version: '3.0.0', // library marker kkossev.energyLib, line 10
    documentationLink: '' // library marker kkossev.energyLib, line 11
) // library marker kkossev.energyLib, line 12
/* // library marker kkossev.energyLib, line 13
 *  Zigbee Energy Library // library marker kkossev.energyLib, line 14
 * // library marker kkossev.energyLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.energyLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.energyLib, line 17
 * // library marker kkossev.energyLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.energyLib, line 19
 * // library marker kkossev.energyLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.energyLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.energyLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.energyLib, line 23
 * // library marker kkossev.energyLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.energyLib, line 25
 * // library marker kkossev.energyLib, line 26
 *                                   TODO: // library marker kkossev.energyLib, line 27
*/ // library marker kkossev.energyLib, line 28

static String energyLibVersion()   { '3.0.0' } // library marker kkossev.energyLib, line 30
static String energyLibStamp() { '2024/04/06 10:48 AM' } // library marker kkossev.energyLib, line 31

//import groovy.json.* // library marker kkossev.energyLib, line 33
//import groovy.transform.Field // library marker kkossev.energyLib, line 34
//import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.energyLib, line 35
//import hubitat.zigbee.zcl.DataType // library marker kkossev.energyLib, line 36
//import java.util.concurrent.ConcurrentHashMap // library marker kkossev.energyLib, line 37

//import groovy.transform.CompileStatic // library marker kkossev.energyLib, line 39

metadata { // library marker kkossev.energyLib, line 41
    // no capabilities // library marker kkossev.energyLib, line 42
    // no attributes // library marker kkossev.energyLib, line 43
    // no commands // library marker kkossev.energyLib, line 44
    preferences { // library marker kkossev.energyLib, line 45
        // no prefrences // library marker kkossev.energyLib, line 46
    } // library marker kkossev.energyLib, line 47
} // library marker kkossev.energyLib, line 48

void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) { // library marker kkossev.energyLib, line 50
    Map map = [:] // library marker kkossev.energyLib, line 51
    map.name = 'voltage' // library marker kkossev.energyLib, line 52
    map.value = voltage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 53
    map.unit = 'V' // library marker kkossev.energyLib, line 54
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 55
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 56
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 57
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0 // library marker kkossev.energyLib, line 58
    final BigDecimal  voltageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 59
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 60
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 61
        sendEvent(map) // library marker kkossev.energyLib, line 62
    } // library marker kkossev.energyLib, line 63
    else { // library marker kkossev.energyLib, line 64
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)" // library marker kkossev.energyLib, line 65
    } // library marker kkossev.energyLib, line 66
} // library marker kkossev.energyLib, line 67

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) { // library marker kkossev.energyLib, line 69
    Map map = [:] // library marker kkossev.energyLib, line 70
    map.name = 'amperage' // library marker kkossev.energyLib, line 71
    map.value = amperage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 72
    map.unit = 'A' // library marker kkossev.energyLib, line 73
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 74
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 75
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 76
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0 // library marker kkossev.energyLib, line 77
    final BigDecimal amperageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 78
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) { // library marker kkossev.energyLib, line 79
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 80
        sendEvent(map) // library marker kkossev.energyLib, line 81
    } // library marker kkossev.energyLib, line 82
    else { // library marker kkossev.energyLib, line 83
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)" // library marker kkossev.energyLib, line 84
    } // library marker kkossev.energyLib, line 85
} // library marker kkossev.energyLib, line 86

void sendPowerEvent(BigDecimal power, boolean isDigital=false) { // library marker kkossev.energyLib, line 88
    Map map = [:] // library marker kkossev.energyLib, line 89
    map.name = 'power' // library marker kkossev.energyLib, line 90
    map.value = power.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 91
    map.unit = 'W' // library marker kkossev.energyLib, line 92
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 93
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 94
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 95
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0 // library marker kkossev.energyLib, line 96
    final BigDecimal powerThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 97
    if (power  > MAX_POWER_LIMIT) { // library marker kkossev.energyLib, line 98
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)" // library marker kkossev.energyLib, line 99
        return // library marker kkossev.energyLib, line 100
    } // library marker kkossev.energyLib, line 101
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 102
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 103
        sendEvent(map) // library marker kkossev.energyLib, line 104
    } // library marker kkossev.energyLib, line 105
    else { // library marker kkossev.energyLib, line 106
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)" // library marker kkossev.energyLib, line 107
    } // library marker kkossev.energyLib, line 108
} // library marker kkossev.energyLib, line 109

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) { // library marker kkossev.energyLib, line 111
    Map map = [:] // library marker kkossev.energyLib, line 112
    map.name = 'frequency' // library marker kkossev.energyLib, line 113
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 114
    map.unit = 'Hz' // library marker kkossev.energyLib, line 115
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 116
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 117
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 118
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.0 // library marker kkossev.energyLib, line 119
    final BigDecimal frequencyThreshold = 0.1 // library marker kkossev.energyLib, line 120
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 121
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 122
        sendEvent(map) // library marker kkossev.energyLib, line 123
    } // library marker kkossev.energyLib, line 124
    else { // library marker kkossev.energyLib, line 125
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)" // library marker kkossev.energyLib, line 126
    } // library marker kkossev.energyLib, line 127
} // library marker kkossev.energyLib, line 128

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) { // library marker kkossev.energyLib, line 130
    Map map = [:] // library marker kkossev.energyLib, line 131
    map.name = 'powerFactor' // library marker kkossev.energyLib, line 132
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 133
    map.unit = '%' // library marker kkossev.energyLib, line 134
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 135
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 136
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 137
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.0 // library marker kkossev.energyLib, line 138
    final BigDecimal powerFactorThreshold = 0.01 // library marker kkossev.energyLib, line 139
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 140
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 141
        sendEvent(map) // library marker kkossev.energyLib, line 142
    } // library marker kkossev.energyLib, line 143
    else { // library marker kkossev.energyLib, line 144
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)" // library marker kkossev.energyLib, line 145
    } // library marker kkossev.energyLib, line 146
} // library marker kkossev.energyLib, line 147

List<String> configureReporting(String operation, String measurement,  String minTime='0', String maxTime='0', String delta='0', boolean sendNow=true ) { // library marker kkossev.energyLib, line 149
    int intMinTime = safeToInt(minTime) // library marker kkossev.energyLib, line 150
    int intMaxTime = safeToInt(maxTime) // library marker kkossev.energyLib, line 151
    int intDelta = safeToInt(delta) // library marker kkossev.energyLib, line 152
    String epString = state.destinationEP // library marker kkossev.energyLib, line 153
    int ep = safeToInt(epString) // library marker kkossev.energyLib, line 154
    if (ep == null || ep == 0) { // library marker kkossev.energyLib, line 155
        ep = 1 // library marker kkossev.energyLib, line 156
        epString = '01' // library marker kkossev.energyLib, line 157
    } // library marker kkossev.energyLib, line 158

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )" // library marker kkossev.energyLib, line 160

    List<String> cmds = [] // library marker kkossev.energyLib, line 162

    switch (measurement) { // library marker kkossev.energyLib, line 164
        case ONOFF : // library marker kkossev.energyLib, line 165
            if (operation == 'Write') { // library marker kkossev.energyLib, line 166
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 251', ] // library marker kkossev.energyLib, line 167
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", 'delay 251', ] // library marker kkossev.energyLib, line 168
            } // library marker kkossev.energyLib, line 169
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 170
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", 'delay 251', ]    // disable Plug automatic reporting // library marker kkossev.energyLib, line 171
            } // library marker kkossev.energyLib, line 172
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back // library marker kkossev.energyLib, line 173
            break // library marker kkossev.energyLib, line 174
        case ENERGY :    // default delta = 1 Wh (0.001 kWh) // library marker kkossev.energyLib, line 175
            if (operation == 'Write') { // library marker kkossev.energyLib, line 176
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta * getEnergyDiv() as int)) // library marker kkossev.energyLib, line 177
            } // library marker kkossev.energyLib, line 178
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 179
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient // library marker kkossev.energyLib, line 180
            } // library marker kkossev.energyLib, line 181
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252) // library marker kkossev.energyLib, line 182
            break // library marker kkossev.energyLib, line 183
        case INST_POWER :        // 0x702:0x400 // library marker kkossev.energyLib, line 184
            if (operation == 'Write') { // library marker kkossev.energyLib, line 185
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int)) // library marker kkossev.energyLib, line 186
            } // library marker kkossev.energyLib, line 187
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 188
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient // library marker kkossev.energyLib, line 189
            } // library marker kkossev.energyLib, line 190
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253) // library marker kkossev.energyLib, line 191
            break // library marker kkossev.energyLib, line 192
        case POWER :        // Active power default delta = 1 // library marker kkossev.energyLib, line 193
            if (operation == 'Write') { // library marker kkossev.energyLib, line 194
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee // library marker kkossev.energyLib, line 195
            } // library marker kkossev.energyLib, line 196
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 197
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient // library marker kkossev.energyLib, line 198
            } // library marker kkossev.energyLib, line 199
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254) // library marker kkossev.energyLib, line 200
            break // library marker kkossev.energyLib, line 201
        case VOLTAGE :    // RMS Voltage default delta = 1 // library marker kkossev.energyLib, line 202
            if (operation == 'Write') { // library marker kkossev.energyLib, line 203
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getVoltageDiv() as int)) // library marker kkossev.energyLib, line 204
            } // library marker kkossev.energyLib, line 205
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 206
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient // library marker kkossev.energyLib, line 207
            } // library marker kkossev.energyLib, line 208
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255) // library marker kkossev.energyLib, line 209
            break // library marker kkossev.energyLib, line 210
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A // library marker kkossev.energyLib, line 211
            if (operation == 'Write') { // library marker kkossev.energyLib, line 212
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getCurrentDiv() as int)) // library marker kkossev.energyLib, line 213
            } // library marker kkossev.energyLib, line 214
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 215
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient // library marker kkossev.energyLib, line 216
            } // library marker kkossev.energyLib, line 217
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256) // library marker kkossev.energyLib, line 218
            break // library marker kkossev.energyLib, line 219
        case FREQUENCY :    // added 03/27/2023 // library marker kkossev.energyLib, line 220
            if (operation == 'Write') { // library marker kkossev.energyLib, line 221
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getFrequencyDiv() as int)) // library marker kkossev.energyLib, line 222
            } // library marker kkossev.energyLib, line 223
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 224
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient // library marker kkossev.energyLib, line 225
            } // library marker kkossev.energyLib, line 226
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257) // library marker kkossev.energyLib, line 227
            break // library marker kkossev.energyLib, line 228
        case POWER_FACTOR : // added 03/27/2023 // library marker kkossev.energyLib, line 229
            if (operation == 'Write') { // library marker kkossev.energyLib, line 230
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getPowerFactorDiv() as int)) // library marker kkossev.energyLib, line 231
            } // library marker kkossev.energyLib, line 232
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258) // library marker kkossev.energyLib, line 233
            break // library marker kkossev.energyLib, line 234
        default : // library marker kkossev.energyLib, line 235
            break // library marker kkossev.energyLib, line 236
    } // library marker kkossev.energyLib, line 237
    if (cmds != null) { // library marker kkossev.energyLib, line 238
        if (sendNow == true) { // library marker kkossev.energyLib, line 239
            sendZigbeeCommands(cmds) // library marker kkossev.energyLib, line 240
        } // library marker kkossev.energyLib, line 241
        else { // library marker kkossev.energyLib, line 242
            return cmds // library marker kkossev.energyLib, line 243
        } // library marker kkossev.energyLib, line 244
    } // library marker kkossev.energyLib, line 245
} // library marker kkossev.energyLib, line 246

// ~~~~~ end include (166) kkossev.energyLib ~~~~~

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
    version: '3.0.6', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) removed isZigUSB() dependency; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 42
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 43
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 46
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 47
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 48
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 49
 * // library marker kkossev.commonLib, line 50
*/ // library marker kkossev.commonLib, line 51

String commonLibVersion() { '3.0.6' } // library marker kkossev.commonLib, line 53
String commonLibStamp() { '2024/04/06 9:51 AM' } // library marker kkossev.commonLib, line 54

import groovy.transform.Field // library marker kkossev.commonLib, line 56
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 57
import hubitat.device.Protocol // library marker kkossev.commonLib, line 58
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 59
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 60
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 61
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 62
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 63
import java.math.BigDecimal // library marker kkossev.commonLib, line 64

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 66

metadata { // library marker kkossev.commonLib, line 68
        if (_DEBUG) { // library marker kkossev.commonLib, line 69
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 70
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 71
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 72
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 73
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 74
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 75
            ] // library marker kkossev.commonLib, line 76
        } // library marker kkossev.commonLib, line 77

        // common capabilities for all device types // library marker kkossev.commonLib, line 79
        capability 'Configuration' // library marker kkossev.commonLib, line 80
        capability 'Refresh' // library marker kkossev.commonLib, line 81
        capability 'Health Check' // library marker kkossev.commonLib, line 82

        // common attributes for all device types // library marker kkossev.commonLib, line 84
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 85
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 86
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 87

        // common commands for all device types // library marker kkossev.commonLib, line 89
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 90
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 91

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 93
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 94
            if (_DEBUG) { // library marker kkossev.commonLib, line 95
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 96
            } // library marker kkossev.commonLib, line 97
        } // library marker kkossev.commonLib, line 98
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 99
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 100
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 101
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 102
            ] // library marker kkossev.commonLib, line 103
        } // library marker kkossev.commonLib, line 104
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'AqaraCube']) { // library marker kkossev.commonLib, line 105
            capability 'Sensor' // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 108
            capability 'MotionSensor' // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 111
            capability 'Actuator' // library marker kkossev.commonLib, line 112
        } // library marker kkossev.commonLib, line 113
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 114
            capability 'Battery' // library marker kkossev.commonLib, line 115
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 116
        } // library marker kkossev.commonLib, line 117
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 118
            capability 'Switch' // library marker kkossev.commonLib, line 119
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 120
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 121
            } // library marker kkossev.commonLib, line 122
        } // library marker kkossev.commonLib, line 123
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 124
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 125
        } // library marker kkossev.commonLib, line 126
        if (deviceType in  ['AqaraCube']) { // library marker kkossev.commonLib, line 127
            capability 'PushableButton' // library marker kkossev.commonLib, line 128
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 129
            capability 'HoldableButton' // library marker kkossev.commonLib, line 130
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 131
        } // library marker kkossev.commonLib, line 132
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 133
            capability 'Momentary' // library marker kkossev.commonLib, line 134
        } // library marker kkossev.commonLib, line 135
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 136
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
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) ) { // library marker kkossev.commonLib, line 155
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 156
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 157
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 158
                } // library marker kkossev.commonLib, line 159
            } // library marker kkossev.commonLib, line 160
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 161
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 162
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 163
            } // library marker kkossev.commonLib, line 164

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 166
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 167
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 168
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 169
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 170
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 171
                } // library marker kkossev.commonLib, line 172
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 173
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 174
                } // library marker kkossev.commonLib, line 175
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 176
            } // library marker kkossev.commonLib, line 177
        } // library marker kkossev.commonLib, line 178
    } // library marker kkossev.commonLib, line 179
} // library marker kkossev.commonLib, line 180

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 182
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 183
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 184
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 185
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 186
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 187
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 188
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 189
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 190
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 191
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 192
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 193

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 195
    defaultValue: 1, // library marker kkossev.commonLib, line 196
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 197
] // library marker kkossev.commonLib, line 198
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 199
    defaultValue: 240, // library marker kkossev.commonLib, line 200
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 201
] // library marker kkossev.commonLib, line 202
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 203
    defaultValue: 0, // library marker kkossev.commonLib, line 204
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 205
] // library marker kkossev.commonLib, line 206

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 208
    defaultValue: 0, // library marker kkossev.commonLib, line 209
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 210
] // library marker kkossev.commonLib, line 211
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 212
    defaultValue: 0, // library marker kkossev.commonLib, line 213
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 214
] // library marker kkossev.commonLib, line 215

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 217
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 218
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 219
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 220
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 221
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 222
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 223
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 224
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 225
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 226
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 227
] // library marker kkossev.commonLib, line 228

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 230
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 231
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 232
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 233
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 234
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 235
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 236
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 237
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 238

/** // library marker kkossev.commonLib, line 240
 * Parse Zigbee message // library marker kkossev.commonLib, line 241
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 242
 */ // library marker kkossev.commonLib, line 243
void parse(final String description) { // library marker kkossev.commonLib, line 244
    checkDriverVersion() // library marker kkossev.commonLib, line 245
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 246
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 247
    setHealthStatusOnline() // library marker kkossev.commonLib, line 248

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 250
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 251
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 252
            parseIasMessage(description) // library marker kkossev.commonLib, line 253
        } // library marker kkossev.commonLib, line 254
        else { // library marker kkossev.commonLib, line 255
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 256
        } // library marker kkossev.commonLib, line 257
        return // library marker kkossev.commonLib, line 258
    } // library marker kkossev.commonLib, line 259
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 260
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 261
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 262
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 263
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 264
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 265
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 266
        return // library marker kkossev.commonLib, line 267
    } // library marker kkossev.commonLib, line 268
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 269
        return // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 272

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 274
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 275
        return // library marker kkossev.commonLib, line 276
    } // library marker kkossev.commonLib, line 277
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 278
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 279
        return // library marker kkossev.commonLib, line 280
    } // library marker kkossev.commonLib, line 281
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 282
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 283
    // // library marker kkossev.commonLib, line 284
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 285
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 286
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 287

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 289
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 290
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 291
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 294
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 295
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 298
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 299
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 302
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 303
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 304
            break // library marker kkossev.commonLib, line 305
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 306
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 307
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 310
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 311
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 314
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 315
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 316
            break // library marker kkossev.commonLib, line 317
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 318
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 319
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 322
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 325
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 328
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 329
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 330
            break // library marker kkossev.commonLib, line 331
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 332
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 333
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 336
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 337
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 338
            break // library marker kkossev.commonLib, line 339
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 340
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 343
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 346
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 347
            break // library marker kkossev.commonLib, line 348
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 349
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 350
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 351
            break // library marker kkossev.commonLib, line 352
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 353
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 354
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
        case 0xE002 : // library marker kkossev.commonLib, line 357
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 358
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 361
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 362
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 363
            break // library marker kkossev.commonLib, line 364
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 365
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 366
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 367
            break // library marker kkossev.commonLib, line 368
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 369
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 370
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 373
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 374
            break // library marker kkossev.commonLib, line 375
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 376
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 377
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 378
            break // library marker kkossev.commonLib, line 379
        default: // library marker kkossev.commonLib, line 380
            if (settings.logEnable) { // library marker kkossev.commonLib, line 381
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 382
            } // library marker kkossev.commonLib, line 383
            break // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
} // library marker kkossev.commonLib, line 386

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 388
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 389
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 390
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 391
    } // library marker kkossev.commonLib, line 392
    return false // library marker kkossev.commonLib, line 393
} // library marker kkossev.commonLib, line 394

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 396
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 397
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 398
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 399
    } // library marker kkossev.commonLib, line 400
    return false // library marker kkossev.commonLib, line 401
} // library marker kkossev.commonLib, line 402

/** // library marker kkossev.commonLib, line 404
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 405
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 406
 */ // library marker kkossev.commonLib, line 407
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 408
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 409
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 410
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 411
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 412
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 413
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 414
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 415
    } // library marker kkossev.commonLib, line 416
    else { // library marker kkossev.commonLib, line 417
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
} // library marker kkossev.commonLib, line 420

/** // library marker kkossev.commonLib, line 422
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 423
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 424
 */ // library marker kkossev.commonLib, line 425
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 426
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 427
    switch (commandId) { // library marker kkossev.commonLib, line 428
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 429
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 430
            break // library marker kkossev.commonLib, line 431
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 432
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 433
            break // library marker kkossev.commonLib, line 434
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 435
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 436
            break // library marker kkossev.commonLib, line 437
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 438
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 439
            break // library marker kkossev.commonLib, line 440
        case 0x0B: // default command response // library marker kkossev.commonLib, line 441
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 442
            break // library marker kkossev.commonLib, line 443
        default: // library marker kkossev.commonLib, line 444
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 445
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 446
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 447
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 448
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 449
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 450
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 451
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 452
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 453
            } // library marker kkossev.commonLib, line 454
            break // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

/** // library marker kkossev.commonLib, line 459
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 460
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 461
 */ // library marker kkossev.commonLib, line 462
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 463
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 464
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 465
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 466
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 467
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 468
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 469
    } // library marker kkossev.commonLib, line 470
    else { // library marker kkossev.commonLib, line 471
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 472
    } // library marker kkossev.commonLib, line 473
} // library marker kkossev.commonLib, line 474

/** // library marker kkossev.commonLib, line 476
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 477
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 478
 */ // library marker kkossev.commonLib, line 479
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 480
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 481
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 482
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 483
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 484
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 485
    } // library marker kkossev.commonLib, line 486
    else { // library marker kkossev.commonLib, line 487
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 488
    } // library marker kkossev.commonLib, line 489
} // library marker kkossev.commonLib, line 490

/** // library marker kkossev.commonLib, line 492
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 493
 */ // library marker kkossev.commonLib, line 494
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 495
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 496
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 497
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 498
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 499
        state.reportingEnabled = true // library marker kkossev.commonLib, line 500
    } // library marker kkossev.commonLib, line 501
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 502
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 503
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 504
    } else { // library marker kkossev.commonLib, line 505
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 506
    } // library marker kkossev.commonLib, line 507
} // library marker kkossev.commonLib, line 508

/** // library marker kkossev.commonLib, line 510
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 511
 */ // library marker kkossev.commonLib, line 512
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 513
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 514
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 515
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 516
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 517
    if (status == 0) { // library marker kkossev.commonLib, line 518
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 519
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 520
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 521
        int delta = 0 // library marker kkossev.commonLib, line 522
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 523
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 524
        } // library marker kkossev.commonLib, line 525
        else { // library marker kkossev.commonLib, line 526
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 527
        } // library marker kkossev.commonLib, line 528
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 529
    } // library marker kkossev.commonLib, line 530
    else { // library marker kkossev.commonLib, line 531
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
} // library marker kkossev.commonLib, line 534

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 536
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 537
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 538
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 539
        return false // library marker kkossev.commonLib, line 540
    } // library marker kkossev.commonLib, line 541
    // execute the customHandler function // library marker kkossev.commonLib, line 542
    boolean result = false // library marker kkossev.commonLib, line 543
    try { // library marker kkossev.commonLib, line 544
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 545
    } // library marker kkossev.commonLib, line 546
    catch (e) { // library marker kkossev.commonLib, line 547
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 548
        return false // library marker kkossev.commonLib, line 549
    } // library marker kkossev.commonLib, line 550
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 551
    return result // library marker kkossev.commonLib, line 552
} // library marker kkossev.commonLib, line 553

/** // library marker kkossev.commonLib, line 555
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 556
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 557
 */ // library marker kkossev.commonLib, line 558
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 559
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 560
    final String commandId = data[0] // library marker kkossev.commonLib, line 561
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 562
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 563
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 564
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 565
    } else { // library marker kkossev.commonLib, line 566
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 567
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 568
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 569
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 570
        } // library marker kkossev.commonLib, line 571
    } // library marker kkossev.commonLib, line 572
} // library marker kkossev.commonLib, line 573

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 575
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 576
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 577
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 578
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 579
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 580
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 581
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 582
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 583
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 584
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 585
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 586
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 587
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 588
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 589
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 590

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 592
    0x00: 'Success', // library marker kkossev.commonLib, line 593
    0x01: 'Failure', // library marker kkossev.commonLib, line 594
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 595
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 596
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 597
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 598
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 599
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 600
    0x88: 'Read Only', // library marker kkossev.commonLib, line 601
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 602
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 603
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 604
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 605
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 606
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 607
    0x94: 'Time out', // library marker kkossev.commonLib, line 608
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 609
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 610
] // library marker kkossev.commonLib, line 611

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 613
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 614
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 615
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 616
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 617
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 618
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 619
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 620
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 621
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 622
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 623
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 624
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 625
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 626
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 627
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 628
] // library marker kkossev.commonLib, line 629

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 631
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 632
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 633
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 634
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 635
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 636
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 637
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 638
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 639
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 640
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 641
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 642
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 643
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 644
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 645
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 646
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 647
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 648
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 649
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 650
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 651
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 652
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 653
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 654
] // library marker kkossev.commonLib, line 655

/* // library marker kkossev.commonLib, line 657
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 658
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 659
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 660
 */ // library marker kkossev.commonLib, line 661
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 662
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 663
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 664
    } // library marker kkossev.commonLib, line 665
    else { // library marker kkossev.commonLib, line 666
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
} // library marker kkossev.commonLib, line 669

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 671
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 672
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 673
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 674
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 675
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 676
    return avg // library marker kkossev.commonLib, line 677
} // library marker kkossev.commonLib, line 678

/* // library marker kkossev.commonLib, line 680
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 681
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 682
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 683
*/ // library marker kkossev.commonLib, line 684
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 685

/** // library marker kkossev.commonLib, line 687
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 688
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 689
 */ // library marker kkossev.commonLib, line 690
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 691
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 692
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 693
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 694
        case 0x0000: // library marker kkossev.commonLib, line 695
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 696
            break // library marker kkossev.commonLib, line 697
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 698
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 699
            if (isPing) { // library marker kkossev.commonLib, line 700
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 701
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 702
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 703
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 704
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 705
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 706
                    sendRttEvent() // library marker kkossev.commonLib, line 707
                } // library marker kkossev.commonLib, line 708
                else { // library marker kkossev.commonLib, line 709
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 710
                } // library marker kkossev.commonLib, line 711
                state.states['isPing'] = false // library marker kkossev.commonLib, line 712
            } // library marker kkossev.commonLib, line 713
            else { // library marker kkossev.commonLib, line 714
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 715
            } // library marker kkossev.commonLib, line 716
            break // library marker kkossev.commonLib, line 717
        case 0x0004: // library marker kkossev.commonLib, line 718
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 719
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 720
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 721
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 722
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 723
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 724
            } // library marker kkossev.commonLib, line 725
            break // library marker kkossev.commonLib, line 726
        case 0x0005: // library marker kkossev.commonLib, line 727
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 728
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 729
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 730
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 731
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 732
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 733
            } // library marker kkossev.commonLib, line 734
            break // library marker kkossev.commonLib, line 735
        case 0x0007: // library marker kkossev.commonLib, line 736
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 737
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 738
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 739
            break // library marker kkossev.commonLib, line 740
        case 0xFFDF: // library marker kkossev.commonLib, line 741
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 742
            break // library marker kkossev.commonLib, line 743
        case 0xFFE2: // library marker kkossev.commonLib, line 744
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 745
            break // library marker kkossev.commonLib, line 746
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 747
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 748
            break // library marker kkossev.commonLib, line 749
        case 0xFFFE: // library marker kkossev.commonLib, line 750
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 751
            break // library marker kkossev.commonLib, line 752
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 753
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 754
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 755
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 756
            break // library marker kkossev.commonLib, line 757
        default: // library marker kkossev.commonLib, line 758
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 759
            break // library marker kkossev.commonLib, line 760
    } // library marker kkossev.commonLib, line 761
} // library marker kkossev.commonLib, line 762

/* // library marker kkossev.commonLib, line 764
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 765
 * power cluster            0x0001 // library marker kkossev.commonLib, line 766
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 767
*/ // library marker kkossev.commonLib, line 768
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 769
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 770
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 771
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 772
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 773
    } // library marker kkossev.commonLib, line 774

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 776
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 777
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 778
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 779
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 780
        } // library marker kkossev.commonLib, line 781
    } // library marker kkossev.commonLib, line 782
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 783
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 784
    } // library marker kkossev.commonLib, line 785
    else { // library marker kkossev.commonLib, line 786
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 787
    } // library marker kkossev.commonLib, line 788
} // library marker kkossev.commonLib, line 789

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 791
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 792
    Map result = [:] // library marker kkossev.commonLib, line 793
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 794
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 795
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 796
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 797
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 798
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 799
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 800
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 801
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
    } // library marker kkossev.commonLib, line 821
} // library marker kkossev.commonLib, line 822

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 824
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 825
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 826
        return // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    Map map = [:] // library marker kkossev.commonLib, line 829
    map.name = 'battery' // library marker kkossev.commonLib, line 830
    map.timeStamp = now() // library marker kkossev.commonLib, line 831
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 832
    map.unit  = '%' // library marker kkossev.commonLib, line 833
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 834
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 835
    map.isStateChange = true // library marker kkossev.commonLib, line 836
    // // library marker kkossev.commonLib, line 837
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 838
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 839
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 840
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 841
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 842
        // send it now! // library marker kkossev.commonLib, line 843
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 844
    } // library marker kkossev.commonLib, line 845
    else { // library marker kkossev.commonLib, line 846
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 847
        map.delayed = delayedTime // library marker kkossev.commonLib, line 848
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 849
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 850
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 851
    } // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 855
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 856
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 857
    sendEvent(map) // library marker kkossev.commonLib, line 858
} // library marker kkossev.commonLib, line 859

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 861
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 862
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 863
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 864
    sendEvent(map) // library marker kkossev.commonLib, line 865
} // library marker kkossev.commonLib, line 866

/* // library marker kkossev.commonLib, line 868
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 869
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 870
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 871
*/ // library marker kkossev.commonLib, line 872
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 873
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 874
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

/* // library marker kkossev.commonLib, line 878
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 879
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 880
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 881
*/ // library marker kkossev.commonLib, line 882
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 883
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 884
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 885
    } // library marker kkossev.commonLib, line 886
    else { // library marker kkossev.commonLib, line 887
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 888
    } // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

/* // library marker kkossev.commonLib, line 892
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 893
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 894
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 895
*/ // library marker kkossev.commonLib, line 896
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 897
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 898
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 899
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 900
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 901
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 902
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 903
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 904
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 905
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 906
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 907
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 908
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 909
            } // library marker kkossev.commonLib, line 910
            else { // library marker kkossev.commonLib, line 911
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 912
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 913
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 914
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 915
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 916
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 917
                        return // library marker kkossev.commonLib, line 918
                    } // library marker kkossev.commonLib, line 919
                } // library marker kkossev.commonLib, line 920
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 921
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 922
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 923
            } // library marker kkossev.commonLib, line 924
            break // library marker kkossev.commonLib, line 925
        case 0x01: // View group // library marker kkossev.commonLib, line 926
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 927
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 928
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 929
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 930
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 931
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 932
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 933
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 934
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 935
            } // library marker kkossev.commonLib, line 936
            else { // library marker kkossev.commonLib, line 937
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 938
            } // library marker kkossev.commonLib, line 939
            break // library marker kkossev.commonLib, line 940
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 941
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 942
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 943
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 944
            final Set<String> groups = [] // library marker kkossev.commonLib, line 945
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 946
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 947
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 948
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 949
            } // library marker kkossev.commonLib, line 950
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 951
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 952
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 953
            break // library marker kkossev.commonLib, line 954
        case 0x03: // Remove group // library marker kkossev.commonLib, line 955
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 956
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 957
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 958
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 959
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 960
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 961
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 962
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 963
            } // library marker kkossev.commonLib, line 964
            else { // library marker kkossev.commonLib, line 965
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 966
            } // library marker kkossev.commonLib, line 967
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 968
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 969
            if (index >= 0) { // library marker kkossev.commonLib, line 970
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 971
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 972
            } // library marker kkossev.commonLib, line 973
            break // library marker kkossev.commonLib, line 974
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 975
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 976
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 977
            break // library marker kkossev.commonLib, line 978
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 979
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 980
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 981
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 982
            break // library marker kkossev.commonLib, line 983
        default: // library marker kkossev.commonLib, line 984
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 985
            break // library marker kkossev.commonLib, line 986
    } // library marker kkossev.commonLib, line 987
} // library marker kkossev.commonLib, line 988

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 990
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 991
    List<String> cmds = [] // library marker kkossev.commonLib, line 992
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 993
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 994
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 995
        return [] // library marker kkossev.commonLib, line 996
    } // library marker kkossev.commonLib, line 997
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 998
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 999
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1000
    return cmds // library marker kkossev.commonLib, line 1001
} // library marker kkossev.commonLib, line 1002

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1004
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1005
    List<String> cmds = [] // library marker kkossev.commonLib, line 1006
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1007
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1008
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1009
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1010
    return cmds // library marker kkossev.commonLib, line 1011
} // library marker kkossev.commonLib, line 1012

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1014
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1015
    List<String> cmds = [] // library marker kkossev.commonLib, line 1016
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1017
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1018
    return cmds // library marker kkossev.commonLib, line 1019
} // library marker kkossev.commonLib, line 1020

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1022
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1023
    List<String> cmds = [] // library marker kkossev.commonLib, line 1024
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1025
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1026
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1027
        return [] // library marker kkossev.commonLib, line 1028
    } // library marker kkossev.commonLib, line 1029
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1030
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1031
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1032
    return cmds // library marker kkossev.commonLib, line 1033
} // library marker kkossev.commonLib, line 1034

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1036
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1037
    List<String> cmds = [] // library marker kkossev.commonLib, line 1038
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1039
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1040
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1041
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1042
    return cmds // library marker kkossev.commonLib, line 1043
} // library marker kkossev.commonLib, line 1044

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1046
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1047
    List<String> cmds = [] // library marker kkossev.commonLib, line 1048
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1049
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1050
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1051
    return cmds // library marker kkossev.commonLib, line 1052
} // library marker kkossev.commonLib, line 1053

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1055
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1056
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1057
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1058
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1059
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1060
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1061
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1062
] // library marker kkossev.commonLib, line 1063

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1065
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1066
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1067
    List<String> cmds = [] // library marker kkossev.commonLib, line 1068
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1069
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1070
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1071
    def value // library marker kkossev.commonLib, line 1072
    Boolean validated = false // library marker kkossev.commonLib, line 1073
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1074
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1075
        return // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1078
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1079
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1080
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1081
        return // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
    // // library marker kkossev.commonLib, line 1084
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1085
    def func // library marker kkossev.commonLib, line 1086
    try { // library marker kkossev.commonLib, line 1087
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1088
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1089
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1090
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1091
    } // library marker kkossev.commonLib, line 1092
    catch (e) { // library marker kkossev.commonLib, line 1093
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1094
        return // library marker kkossev.commonLib, line 1095
    } // library marker kkossev.commonLib, line 1096

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1098
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1099
} // library marker kkossev.commonLib, line 1100

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1102
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1103
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1104
} // library marker kkossev.commonLib, line 1105

/* // library marker kkossev.commonLib, line 1107
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1108
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1109
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1110
*/ // library marker kkossev.commonLib, line 1111

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1113
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1114
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1115
    } // library marker kkossev.commonLib, line 1116
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1117
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1118
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1119
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1120
    } // library marker kkossev.commonLib, line 1121
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1122
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1123
    } // library marker kkossev.commonLib, line 1124
    else { // library marker kkossev.commonLib, line 1125
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 1126
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1131
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1132
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1133

void toggle() { // library marker kkossev.commonLib, line 1135
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1136
    String state = '' // library marker kkossev.commonLib, line 1137
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1138
        state = 'on' // library marker kkossev.commonLib, line 1139
    } // library marker kkossev.commonLib, line 1140
    else { // library marker kkossev.commonLib, line 1141
        state = 'off' // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    descriptionText += state // library marker kkossev.commonLib, line 1144
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1145
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1146
} // library marker kkossev.commonLib, line 1147

void off() { // library marker kkossev.commonLib, line 1149
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1150
        customOff() // library marker kkossev.commonLib, line 1151
        return // library marker kkossev.commonLib, line 1152
    } // library marker kkossev.commonLib, line 1153
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1154
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1155
        return // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1158
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1159
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1160
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1161
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1162
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1163
        } // library marker kkossev.commonLib, line 1164
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1165
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1166
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1167
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1168
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170
    /* // library marker kkossev.commonLib, line 1171
    else { // library marker kkossev.commonLib, line 1172
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1173
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1174
        } // library marker kkossev.commonLib, line 1175
        else { // library marker kkossev.commonLib, line 1176
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1177
            return // library marker kkossev.commonLib, line 1178
        } // library marker kkossev.commonLib, line 1179
    } // library marker kkossev.commonLib, line 1180
    */ // library marker kkossev.commonLib, line 1181

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1183
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1184
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1185
} // library marker kkossev.commonLib, line 1186

void on() { // library marker kkossev.commonLib, line 1188
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1189
        customOn() // library marker kkossev.commonLib, line 1190
        return // library marker kkossev.commonLib, line 1191
    } // library marker kkossev.commonLib, line 1192
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1193
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1194
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1195
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1196
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1197
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1198
        } // library marker kkossev.commonLib, line 1199
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1200
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1201
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1202
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1203
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1204
    } // library marker kkossev.commonLib, line 1205
    /* // library marker kkossev.commonLib, line 1206
    else { // library marker kkossev.commonLib, line 1207
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1208
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1209
        } // library marker kkossev.commonLib, line 1210
        else { // library marker kkossev.commonLib, line 1211
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1212
            return // library marker kkossev.commonLib, line 1213
        } // library marker kkossev.commonLib, line 1214
    } // library marker kkossev.commonLib, line 1215
    */ // library marker kkossev.commonLib, line 1216
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1217
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1218
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1219
} // library marker kkossev.commonLib, line 1220

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1222
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1223
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1224
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1225
    } // library marker kkossev.commonLib, line 1226
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1227
    Map map = [:] // library marker kkossev.commonLib, line 1228
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1229
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1230
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1231
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1232
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1233
        return // library marker kkossev.commonLib, line 1234
    } // library marker kkossev.commonLib, line 1235
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1236
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1237
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1238
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1239
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1240
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1241
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1242
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1243
    } else { // library marker kkossev.commonLib, line 1244
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1245
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1246
    } // library marker kkossev.commonLib, line 1247
    map.name = 'switch' // library marker kkossev.commonLib, line 1248
    map.value = value // library marker kkossev.commonLib, line 1249
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1250
    if (isRefresh) { // library marker kkossev.commonLib, line 1251
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1252
        map.isStateChange = true // library marker kkossev.commonLib, line 1253
    } else { // library marker kkossev.commonLib, line 1254
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1257
    sendEvent(map) // library marker kkossev.commonLib, line 1258
    clearIsDigital() // library marker kkossev.commonLib, line 1259
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1260
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1261
    } // library marker kkossev.commonLib, line 1262
} // library marker kkossev.commonLib, line 1263

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1265
    '0': 'switch off', // library marker kkossev.commonLib, line 1266
    '1': 'switch on', // library marker kkossev.commonLib, line 1267
    '2': 'switch last state' // library marker kkossev.commonLib, line 1268
] // library marker kkossev.commonLib, line 1269

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1271
    '0': 'toggle', // library marker kkossev.commonLib, line 1272
    '1': 'state', // library marker kkossev.commonLib, line 1273
    '2': 'momentary' // library marker kkossev.commonLib, line 1274
] // library marker kkossev.commonLib, line 1275

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1277
    Map descMap = [:] // library marker kkossev.commonLib, line 1278
    try { // library marker kkossev.commonLib, line 1279
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1280
    } // library marker kkossev.commonLib, line 1281
    catch (e1) { // library marker kkossev.commonLib, line 1282
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1283
        // try alternative custom parsing // library marker kkossev.commonLib, line 1284
        descMap = [:] // library marker kkossev.commonLib, line 1285
        try { // library marker kkossev.commonLib, line 1286
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1287
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1288
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1289
            } // library marker kkossev.commonLib, line 1290
        } // library marker kkossev.commonLib, line 1291
        catch (e2) { // library marker kkossev.commonLib, line 1292
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1293
            return [:] // library marker kkossev.commonLib, line 1294
        } // library marker kkossev.commonLib, line 1295
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1296
    } // library marker kkossev.commonLib, line 1297
    return descMap // library marker kkossev.commonLib, line 1298
} // library marker kkossev.commonLib, line 1299

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1301
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1302
        return false // library marker kkossev.commonLib, line 1303
    } // library marker kkossev.commonLib, line 1304
    // try to parse ... // library marker kkossev.commonLib, line 1305
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1306
    Map descMap = [:] // library marker kkossev.commonLib, line 1307
    try { // library marker kkossev.commonLib, line 1308
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1309
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311
    catch (e) { // library marker kkossev.commonLib, line 1312
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1313
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1314
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1315
        return true // library marker kkossev.commonLib, line 1316
    } // library marker kkossev.commonLib, line 1317

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1319
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1320
    } // library marker kkossev.commonLib, line 1321
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1322
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1323
    } // library marker kkossev.commonLib, line 1324
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1325
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1326
    } // library marker kkossev.commonLib, line 1327
    else { // library marker kkossev.commonLib, line 1328
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1329
        return false // library marker kkossev.commonLib, line 1330
    } // library marker kkossev.commonLib, line 1331
    return true    // processed // library marker kkossev.commonLib, line 1332
} // library marker kkossev.commonLib, line 1333

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1335
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1336
  /* // library marker kkossev.commonLib, line 1337
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1338
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1339
        return true // library marker kkossev.commonLib, line 1340
    } // library marker kkossev.commonLib, line 1341
*/ // library marker kkossev.commonLib, line 1342
    Map descMap = [:] // library marker kkossev.commonLib, line 1343
    try { // library marker kkossev.commonLib, line 1344
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1345
    } // library marker kkossev.commonLib, line 1346
    catch (e1) { // library marker kkossev.commonLib, line 1347
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1348
        // try alternative custom parsing // library marker kkossev.commonLib, line 1349
        descMap = [:] // library marker kkossev.commonLib, line 1350
        try { // library marker kkossev.commonLib, line 1351
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1352
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1353
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1354
            } // library marker kkossev.commonLib, line 1355
        } // library marker kkossev.commonLib, line 1356
        catch (e2) { // library marker kkossev.commonLib, line 1357
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1358
            return true // library marker kkossev.commonLib, line 1359
        } // library marker kkossev.commonLib, line 1360
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1361
    } // library marker kkossev.commonLib, line 1362
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1363
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1364
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1365
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1366
        return false // library marker kkossev.commonLib, line 1367
    } // library marker kkossev.commonLib, line 1368
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1369
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1370
    // attribute report received // library marker kkossev.commonLib, line 1371
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1372
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1373
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1374
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1375
    } // library marker kkossev.commonLib, line 1376
    attrData.each { // library marker kkossev.commonLib, line 1377
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1378
        //def map = [:] // library marker kkossev.commonLib, line 1379
        if (it.status == '86') { // library marker kkossev.commonLib, line 1380
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1381
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1382
        } // library marker kkossev.commonLib, line 1383
        switch (it.cluster) { // library marker kkossev.commonLib, line 1384
            case '0000' : // library marker kkossev.commonLib, line 1385
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1386
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1387
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1388
                } // library marker kkossev.commonLib, line 1389
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1390
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1391
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1392
                } // library marker kkossev.commonLib, line 1393
                else { // library marker kkossev.commonLib, line 1394
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1395
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1396
                } // library marker kkossev.commonLib, line 1397
                break // library marker kkossev.commonLib, line 1398
            default : // library marker kkossev.commonLib, line 1399
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1400
                break // library marker kkossev.commonLib, line 1401
        } // switch // library marker kkossev.commonLib, line 1402
    } // for each attribute // library marker kkossev.commonLib, line 1403
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1404
} // library marker kkossev.commonLib, line 1405

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1407

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1409
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1410
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1411
    def mode // library marker kkossev.commonLib, line 1412
    String attrName // library marker kkossev.commonLib, line 1413
    if (it.value == null) { // library marker kkossev.commonLib, line 1414
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1415
        return // library marker kkossev.commonLib, line 1416
    } // library marker kkossev.commonLib, line 1417
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1418
    switch (it.attrId) { // library marker kkossev.commonLib, line 1419
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1420
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1421
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1422
            break // library marker kkossev.commonLib, line 1423
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1424
            attrName = 'On Time' // library marker kkossev.commonLib, line 1425
            mode = value // library marker kkossev.commonLib, line 1426
            break // library marker kkossev.commonLib, line 1427
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1428
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1429
            mode = value // library marker kkossev.commonLib, line 1430
            break // library marker kkossev.commonLib, line 1431
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1432
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1433
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1434
            break // library marker kkossev.commonLib, line 1435
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1436
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1437
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1438
            break // library marker kkossev.commonLib, line 1439
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1440
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1441
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1442
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1443
            } // library marker kkossev.commonLib, line 1444
            else { // library marker kkossev.commonLib, line 1445
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1446
            } // library marker kkossev.commonLib, line 1447
            break // library marker kkossev.commonLib, line 1448
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1449
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1450
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1451
            break // library marker kkossev.commonLib, line 1452
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1453
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1454
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1455
            break // library marker kkossev.commonLib, line 1456
        default : // library marker kkossev.commonLib, line 1457
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1458
            return // library marker kkossev.commonLib, line 1459
    } // library marker kkossev.commonLib, line 1460
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1461
} // library marker kkossev.commonLib, line 1462

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1464
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.commonLib, line 1465
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.commonLib, line 1466
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.commonLib, line 1467
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1468
        logInfo "$descriptionText" // library marker kkossev.commonLib, line 1469
        sendEvent(event) // library marker kkossev.commonLib, line 1470
    } // library marker kkossev.commonLib, line 1471
    else { // library marker kkossev.commonLib, line 1472
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.commonLib, line 1473
    } // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1477
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1478
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.commonLib, line 1479
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1483
    logDebug "push button $buttonNumber" // library marker kkossev.commonLib, line 1484
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.commonLib, line 1485
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1486
} // library marker kkossev.commonLib, line 1487

void doubleTap(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1489
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1490
} // library marker kkossev.commonLib, line 1491

void hold(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1493
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.commonLib, line 1494
} // library marker kkossev.commonLib, line 1495

void release(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1497
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1501
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1502
} // library marker kkossev.commonLib, line 1503

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1505
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1506
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

/* // library marker kkossev.commonLib, line 1510
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1511
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1512
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1513
*/ // library marker kkossev.commonLib, line 1514
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1515
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1516
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1517
    } // library marker kkossev.commonLib, line 1518
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1519
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1520
    } // library marker kkossev.commonLib, line 1521
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1522
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1523
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1524
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1525
    } // library marker kkossev.commonLib, line 1526
    else { // library marker kkossev.commonLib, line 1527
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1532
    int value = rawValue as int // library marker kkossev.commonLib, line 1533
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1534
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1535
    Map map = [:] // library marker kkossev.commonLib, line 1536

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1538
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1539

    map.name = 'level' // library marker kkossev.commonLib, line 1541
    map.value = value // library marker kkossev.commonLib, line 1542
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1543
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1544
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1545
        map.isStateChange = true // library marker kkossev.commonLib, line 1546
    } // library marker kkossev.commonLib, line 1547
    else { // library marker kkossev.commonLib, line 1548
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1551
    sendEvent(map) // library marker kkossev.commonLib, line 1552
    clearIsDigital() // library marker kkossev.commonLib, line 1553
} // library marker kkossev.commonLib, line 1554

/** // library marker kkossev.commonLib, line 1556
 * Get the level transition rate // library marker kkossev.commonLib, line 1557
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1558
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1559
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1560
 */ // library marker kkossev.commonLib, line 1561
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1562
    int rate = 0 // library marker kkossev.commonLib, line 1563
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1564
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1565
    if (!isOn) { // library marker kkossev.commonLib, line 1566
        currentLevel = 0 // library marker kkossev.commonLib, line 1567
    } // library marker kkossev.commonLib, line 1568
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1569
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1570
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1571
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1572
    } else { // library marker kkossev.commonLib, line 1573
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1574
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1575
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1576
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1577
        } // library marker kkossev.commonLib, line 1578
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1579
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1580
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1581
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1582
        } // library marker kkossev.commonLib, line 1583
    } // library marker kkossev.commonLib, line 1584
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1585
    return rate // library marker kkossev.commonLib, line 1586
} // library marker kkossev.commonLib, line 1587

// Command option that enable changes when off // library marker kkossev.commonLib, line 1589
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1590

/** // library marker kkossev.commonLib, line 1592
 * Constrain a value to a range // library marker kkossev.commonLib, line 1593
 * @param value value to constrain // library marker kkossev.commonLib, line 1594
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1595
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1596
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1597
 */ // library marker kkossev.commonLib, line 1598
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1599
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1600
        return value // library marker kkossev.commonLib, line 1601
    } // library marker kkossev.commonLib, line 1602
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1603
} // library marker kkossev.commonLib, line 1604

/** // library marker kkossev.commonLib, line 1606
 * Constrain a value to a range // library marker kkossev.commonLib, line 1607
 * @param value value to constrain // library marker kkossev.commonLib, line 1608
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1609
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1610
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1611
 */ // library marker kkossev.commonLib, line 1612
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1613
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1614
        return value as Integer // library marker kkossev.commonLib, line 1615
    } // library marker kkossev.commonLib, line 1616
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1617
} // library marker kkossev.commonLib, line 1618

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1620
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1621

/** // library marker kkossev.commonLib, line 1623
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1624
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1625
 * @param commands commands to execute // library marker kkossev.commonLib, line 1626
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1627
 */ // library marker kkossev.commonLib, line 1628
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1629
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1630
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1631
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1632
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1633
    } // library marker kkossev.commonLib, line 1634
    return [] // library marker kkossev.commonLib, line 1635
} // library marker kkossev.commonLib, line 1636

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1638
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1639
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1640
} // library marker kkossev.commonLib, line 1641

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1643
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1644
} // library marker kkossev.commonLib, line 1645

/** // library marker kkossev.commonLib, line 1647
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1648
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1649
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1650
 */ // library marker kkossev.commonLib, line 1651
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1652
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1653
    List<String> cmds = [] // library marker kkossev.commonLib, line 1654
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1655
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1656
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1657
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1658
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1659
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1660
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1661
    } // library marker kkossev.commonLib, line 1662
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1663
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1664
    /* // library marker kkossev.commonLib, line 1665
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1666
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1667
    */ // library marker kkossev.commonLib, line 1668
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1669
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1670
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1671

    return cmds // library marker kkossev.commonLib, line 1673
} // library marker kkossev.commonLib, line 1674

/** // library marker kkossev.commonLib, line 1676
 * Set Level Command // library marker kkossev.commonLib, line 1677
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1678
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1679
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1680
 */ // library marker kkossev.commonLib, line 1681
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1682
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1683
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1684
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1685
        return // library marker kkossev.commonLib, line 1686
    } // library marker kkossev.commonLib, line 1687
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1688
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1689
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1690
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1691
} // library marker kkossev.commonLib, line 1692

/* // library marker kkossev.commonLib, line 1694
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1695
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1696
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1697
*/ // library marker kkossev.commonLib, line 1698
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1699
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1700
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1701
    } // library marker kkossev.commonLib, line 1702
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1703
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1704
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1705
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1706
    } // library marker kkossev.commonLib, line 1707
    else { // library marker kkossev.commonLib, line 1708
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1709
    } // library marker kkossev.commonLib, line 1710
} // library marker kkossev.commonLib, line 1711

/* // library marker kkossev.commonLib, line 1713
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1714
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1715
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1716
*/ // library marker kkossev.commonLib, line 1717
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1718
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1719
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1720
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1721
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1722
} // library marker kkossev.commonLib, line 1723

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1725
    Map eventMap = [:] // library marker kkossev.commonLib, line 1726
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1727
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1728
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1729
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1730
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1731
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1732
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1733
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1734
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1735
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1736
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1737
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1738
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1739
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1740
        return // library marker kkossev.commonLib, line 1741
    } // library marker kkossev.commonLib, line 1742
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1743
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1744
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1745
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1746
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1747
    } // library marker kkossev.commonLib, line 1748
    else {         // queue the event // library marker kkossev.commonLib, line 1749
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1750
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1751
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1752
    } // library marker kkossev.commonLib, line 1753
} // library marker kkossev.commonLib, line 1754

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1756
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1757
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1758
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1759
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1760
} // library marker kkossev.commonLib, line 1761

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1763

/* // library marker kkossev.commonLib, line 1765
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1766
 * temperature // library marker kkossev.commonLib, line 1767
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1768
*/ // library marker kkossev.commonLib, line 1769
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1770
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1771
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1772
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1773
} // library marker kkossev.commonLib, line 1774

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1776
    Map eventMap = [:] // library marker kkossev.commonLib, line 1777
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1778
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1779
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1780
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1781
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1782
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1783
    } // library marker kkossev.commonLib, line 1784
    else { // library marker kkossev.commonLib, line 1785
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1786
    } // library marker kkossev.commonLib, line 1787
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1788
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1789
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1790
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1791
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1792
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1793
        return // library marker kkossev.commonLib, line 1794
    } // library marker kkossev.commonLib, line 1795
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1796
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1797
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1798
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1799
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1800
    } // library marker kkossev.commonLib, line 1801
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1802
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1803
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1804
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1805
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1806
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1807
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1808
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1809
    } // library marker kkossev.commonLib, line 1810
    else {         // queue the event // library marker kkossev.commonLib, line 1811
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1812
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1813
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1814
    } // library marker kkossev.commonLib, line 1815
} // library marker kkossev.commonLib, line 1816

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1818
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1819
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1820
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1821
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1822
} // library marker kkossev.commonLib, line 1823

/* // library marker kkossev.commonLib, line 1825
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1826
 * humidity // library marker kkossev.commonLib, line 1827
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1828
*/ // library marker kkossev.commonLib, line 1829
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1830
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1831
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1832
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1833
} // library marker kkossev.commonLib, line 1834

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1836
    Map eventMap = [:] // library marker kkossev.commonLib, line 1837
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1838
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1839
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1840
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1841
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1842
        return // library marker kkossev.commonLib, line 1843
    } // library marker kkossev.commonLib, line 1844
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1845
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1846
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1847
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1848
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1849
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1850
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1851
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1852
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1853
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1854
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1855
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1856
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1857
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1858
    } // library marker kkossev.commonLib, line 1859
    else { // library marker kkossev.commonLib, line 1860
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1861
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1862
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1863
    } // library marker kkossev.commonLib, line 1864
} // library marker kkossev.commonLib, line 1865

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1867
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1868
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1869
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1870
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1871
} // library marker kkossev.commonLib, line 1872

/* // library marker kkossev.commonLib, line 1874
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1875
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1876
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1877
*/ // library marker kkossev.commonLib, line 1878

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1880
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1881
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1882
    } // library marker kkossev.commonLib, line 1883
} // library marker kkossev.commonLib, line 1884

/* // library marker kkossev.commonLib, line 1886
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1887
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1888
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1889
*/ // library marker kkossev.commonLib, line 1890
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1891
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1892
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1893
    } // library marker kkossev.commonLib, line 1894
} // library marker kkossev.commonLib, line 1895

/* // library marker kkossev.commonLib, line 1897
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1898
 * pm2.5 // library marker kkossev.commonLib, line 1899
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1900
*/ // library marker kkossev.commonLib, line 1901
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1902
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1903
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1904
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1905
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1906
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1907
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1908
    } // library marker kkossev.commonLib, line 1909
    else { // library marker kkossev.commonLib, line 1910
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1911
    } // library marker kkossev.commonLib, line 1912
} // library marker kkossev.commonLib, line 1913

/* // library marker kkossev.commonLib, line 1915
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1916
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1917
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1918
*/ // library marker kkossev.commonLib, line 1919
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1920
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1921
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1922
    } // library marker kkossev.commonLib, line 1923
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1924
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1925
    }    // library marker kkossev.commonLib, line 1926
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1927
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1928
    } // library marker kkossev.commonLib, line 1929
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1930
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
    else { // library marker kkossev.commonLib, line 1933
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1934
    } // library marker kkossev.commonLib, line 1935
} // library marker kkossev.commonLib, line 1936

/* // library marker kkossev.commonLib, line 1938
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1939
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1940
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1941
*/ // library marker kkossev.commonLib, line 1942

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1944
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1945
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1946
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1947
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1948
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1949
    } // library marker kkossev.commonLib, line 1950
    else { // library marker kkossev.commonLib, line 1951
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1952
    } // library marker kkossev.commonLib, line 1953
} // library marker kkossev.commonLib, line 1954

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1956
    Map eventMap = [:] // library marker kkossev.commonLib, line 1957
    eventMap.value = value // library marker kkossev.commonLib, line 1958
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1959
    eventMap.unit = '' // library marker kkossev.commonLib, line 1960
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1961
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1962
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1963
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1964
} // library marker kkossev.commonLib, line 1965

/* // library marker kkossev.commonLib, line 1967
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1968
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1969
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1970
*/ // library marker kkossev.commonLib, line 1971

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1973
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1974
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1975
    } // library marker kkossev.commonLib, line 1976
    else { // library marker kkossev.commonLib, line 1977
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1978
    } // library marker kkossev.commonLib, line 1979
} // library marker kkossev.commonLib, line 1980

/* // library marker kkossev.commonLib, line 1982
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1983
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1984
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1985
*/ // library marker kkossev.commonLib, line 1986
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1987
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1988
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1989
    } // library marker kkossev.commonLib, line 1990
    else { // library marker kkossev.commonLib, line 1991
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1992
    } // library marker kkossev.commonLib, line 1993
} // library marker kkossev.commonLib, line 1994

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1996

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1998
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1999
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 2000
    } // library marker kkossev.commonLib, line 2001
    else { // library marker kkossev.commonLib, line 2002
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2003
    } // library marker kkossev.commonLib, line 2004
} // library marker kkossev.commonLib, line 2005

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2007
    if (this.respondsTo('customParseE002Cluster')) { // library marker kkossev.commonLib, line 2008
        customParseE002Cluster(descMap) // library marker kkossev.commonLib, line 2009
    } // library marker kkossev.commonLib, line 2010
    else { // library marker kkossev.commonLib, line 2011
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
} // library marker kkossev.commonLib, line 2014

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2016
    if (this.respondsTo('customParseEC03Cluster')) { // library marker kkossev.commonLib, line 2017
        customParseEC03Cluster(descMap) // library marker kkossev.commonLib, line 2018
    } // library marker kkossev.commonLib, line 2019
    else { // library marker kkossev.commonLib, line 2020
        logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2021
    } // library marker kkossev.commonLib, line 2022
} // library marker kkossev.commonLib, line 2023

/* // library marker kkossev.commonLib, line 2025
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2026
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2027
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2028
*/ // library marker kkossev.commonLib, line 2029
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2030
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2031
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2032

// Tuya Commands // library marker kkossev.commonLib, line 2034
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2035
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2036
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2037
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2038
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2039

// tuya DP type // library marker kkossev.commonLib, line 2041
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2042
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2043
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2044
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2045
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2046
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2047

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2049
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2050
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2051
        Long offset = 0 // library marker kkossev.commonLib, line 2052
        try { // library marker kkossev.commonLib, line 2053
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2054
        } // library marker kkossev.commonLib, line 2055
        catch (e) { // library marker kkossev.commonLib, line 2056
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2057
        } // library marker kkossev.commonLib, line 2058
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2059
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2060
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2061
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2062
    } // library marker kkossev.commonLib, line 2063
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2064
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2065
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2066
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2067
        if (status != '00') { // library marker kkossev.commonLib, line 2068
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2069
        } // library marker kkossev.commonLib, line 2070
    } // library marker kkossev.commonLib, line 2071
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2072
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2073
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2074
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2075
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2076
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2077
            return // library marker kkossev.commonLib, line 2078
        } // library marker kkossev.commonLib, line 2079
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2080
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2081
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2082
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2083
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2084
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2085
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2086
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2087
        } // library marker kkossev.commonLib, line 2088
    } // library marker kkossev.commonLib, line 2089
    else { // library marker kkossev.commonLib, line 2090
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2091
    } // library marker kkossev.commonLib, line 2092
} // library marker kkossev.commonLib, line 2093

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2095
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 2096
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2097
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 2098
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2099
            return // library marker kkossev.commonLib, line 2100
        } // library marker kkossev.commonLib, line 2101
    } // library marker kkossev.commonLib, line 2102
    // check if the method  method exists // library marker kkossev.commonLib, line 2103
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2104
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2105
            return // library marker kkossev.commonLib, line 2106
        } // library marker kkossev.commonLib, line 2107
    } // library marker kkossev.commonLib, line 2108
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2109
} // library marker kkossev.commonLib, line 2110

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2112
    int retValue = 0 // library marker kkossev.commonLib, line 2113
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2114
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2115
        int power = 1 // library marker kkossev.commonLib, line 2116
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2117
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2118
            power = power * 256 // library marker kkossev.commonLib, line 2119
        } // library marker kkossev.commonLib, line 2120
    } // library marker kkossev.commonLib, line 2121
    return retValue // library marker kkossev.commonLib, line 2122
} // library marker kkossev.commonLib, line 2123

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2125
    List<String> cmds = [] // library marker kkossev.commonLib, line 2126
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2127
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2128
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2129
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2130
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2131
    return cmds // library marker kkossev.commonLib, line 2132
} // library marker kkossev.commonLib, line 2133

private getPACKET_ID() { // library marker kkossev.commonLib, line 2135
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2136
} // library marker kkossev.commonLib, line 2137

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2139
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2140
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2141
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2142
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2143
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2144
} // library marker kkossev.commonLib, line 2145

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2147
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2148

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2150
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2151
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2152
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 2153
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2154
} // library marker kkossev.commonLib, line 2155

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2157
    List<String> cmds = [] // library marker kkossev.commonLib, line 2158
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2159
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2160
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2161
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2162
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2163
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2164
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2165
        } // library marker kkossev.commonLib, line 2166
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2167
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2168
    } // library marker kkossev.commonLib, line 2169
    else { // library marker kkossev.commonLib, line 2170
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2171
    } // library marker kkossev.commonLib, line 2172
} // library marker kkossev.commonLib, line 2173

/** // library marker kkossev.commonLib, line 2175
 * initializes the device // library marker kkossev.commonLib, line 2176
 * Invoked from configure() // library marker kkossev.commonLib, line 2177
 * @return zigbee commands // library marker kkossev.commonLib, line 2178
 */ // library marker kkossev.commonLib, line 2179
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2180
    List<String> cmds = [] // library marker kkossev.commonLib, line 2181
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2182

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2184
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2185
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 2186
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2187
    } // library marker kkossev.commonLib, line 2188
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2189
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2190
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2191
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2192
    } // library marker kkossev.commonLib, line 2193
    // // library marker kkossev.commonLib, line 2194
    return cmds // library marker kkossev.commonLib, line 2195
} // library marker kkossev.commonLib, line 2196

/** // library marker kkossev.commonLib, line 2198
 * configures the device // library marker kkossev.commonLib, line 2199
 * Invoked from configure() // library marker kkossev.commonLib, line 2200
 * @return zigbee commands // library marker kkossev.commonLib, line 2201
 */ // library marker kkossev.commonLib, line 2202
List<String> configureDevice() { // library marker kkossev.commonLib, line 2203
    List<String> cmds = [] // library marker kkossev.commonLib, line 2204
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2205

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2207
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 2208
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2209
    } // library marker kkossev.commonLib, line 2210
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2211
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2212
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2213
    return cmds // library marker kkossev.commonLib, line 2214
} // library marker kkossev.commonLib, line 2215

/* // library marker kkossev.commonLib, line 2217
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2218
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2219
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2220
*/ // library marker kkossev.commonLib, line 2221

void refresh() { // library marker kkossev.commonLib, line 2223
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2224
    checkDriverVersion() // library marker kkossev.commonLib, line 2225
    List<String> cmds = [] // library marker kkossev.commonLib, line 2226
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2227

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2229
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2230
        cmds += customRefresh() // library marker kkossev.commonLib, line 2231
    } // library marker kkossev.commonLib, line 2232
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2233
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2234
    else { // library marker kkossev.commonLib, line 2235
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2236
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2237
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2238
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2239
        } // library marker kkossev.commonLib, line 2240
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2241
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2242
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2243
        } // library marker kkossev.commonLib, line 2244
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2245
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2246
        } // library marker kkossev.commonLib, line 2247
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2248
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2249
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2250
        } // library marker kkossev.commonLib, line 2251
    } // library marker kkossev.commonLib, line 2252

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2254
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2255
    } // library marker kkossev.commonLib, line 2256
    else { // library marker kkossev.commonLib, line 2257
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2258
    } // library marker kkossev.commonLib, line 2259
} // library marker kkossev.commonLib, line 2260

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2262
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2263
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2264
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2265

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
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2284
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2285
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2286
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2287
    } // library marker kkossev.commonLib, line 2288
    else { // library marker kkossev.commonLib, line 2289
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2290
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2291
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2292
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2293
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2294
        if (isVirtual()) { // library marker kkossev.commonLib, line 2295
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2296
        } // library marker kkossev.commonLib, line 2297
        else { // library marker kkossev.commonLib, line 2298
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2299
        } // library marker kkossev.commonLib, line 2300
        logDebug 'ping...' // library marker kkossev.commonLib, line 2301
    } // library marker kkossev.commonLib, line 2302
} // library marker kkossev.commonLib, line 2303

def virtualPong() { // library marker kkossev.commonLib, line 2305
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2306
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2307
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2308
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
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2330
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2331
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2332
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2333
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
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2354
    return 'NULL' // library marker kkossev.commonLib, line 2355
} // library marker kkossev.commonLib, line 2356

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2358
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2359
} // library marker kkossev.commonLib, line 2360

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2362
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2363
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2364
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2365
} // library marker kkossev.commonLib, line 2366

/** // library marker kkossev.commonLib, line 2368
 * Schedule a device health check // library marker kkossev.commonLib, line 2369
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2370
 */ // library marker kkossev.commonLib, line 2371
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2372
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2373
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2374
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2375
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2376
    } // library marker kkossev.commonLib, line 2377
    else { // library marker kkossev.commonLib, line 2378
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2379
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2380
    } // library marker kkossev.commonLib, line 2381
} // library marker kkossev.commonLib, line 2382

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2384
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2385
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2386
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2387
} // library marker kkossev.commonLib, line 2388

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2390
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2391
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2392
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2393
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2394
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2395
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2396
    } // library marker kkossev.commonLib, line 2397
} // library marker kkossev.commonLib, line 2398

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2400
    checkDriverVersion() // library marker kkossev.commonLib, line 2401
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2402
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2403
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2404
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2405
            logWarn 'not present!' // library marker kkossev.commonLib, line 2406
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2407
        } // library marker kkossev.commonLib, line 2408
    } // library marker kkossev.commonLib, line 2409
    else { // library marker kkossev.commonLib, line 2410
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2411
    } // library marker kkossev.commonLib, line 2412
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2413
} // library marker kkossev.commonLib, line 2414

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2416
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2417
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2418
    if (value == 'online') { // library marker kkossev.commonLib, line 2419
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2420
    } // library marker kkossev.commonLib, line 2421
    else { // library marker kkossev.commonLib, line 2422
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2423
    } // library marker kkossev.commonLib, line 2424
} // library marker kkossev.commonLib, line 2425

/** // library marker kkossev.commonLib, line 2427
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2428
 */ // library marker kkossev.commonLib, line 2429
void autoPoll() { // library marker kkossev.commonLib, line 2430
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2431
    checkDriverVersion() // library marker kkossev.commonLib, line 2432
    List<String> cmds = [] // library marker kkossev.commonLib, line 2433
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2434
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2435
    } // library marker kkossev.commonLib, line 2436

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2438
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2439
    } // library marker kkossev.commonLib, line 2440
} // library marker kkossev.commonLib, line 2441

/** // library marker kkossev.commonLib, line 2443
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2444
 */ // library marker kkossev.commonLib, line 2445
void updated() { // library marker kkossev.commonLib, line 2446
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2447
    checkDriverVersion() // library marker kkossev.commonLib, line 2448
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2449
    unschedule() // library marker kkossev.commonLib, line 2450

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2452
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2453
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2454
    } // library marker kkossev.commonLib, line 2455
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2456
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2457
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2458
    } // library marker kkossev.commonLib, line 2459

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2461
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2462
        // schedule the periodic timer // library marker kkossev.commonLib, line 2463
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2464
        if (interval > 0) { // library marker kkossev.commonLib, line 2465
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2466
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2467
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2468
        } // library marker kkossev.commonLib, line 2469
    } // library marker kkossev.commonLib, line 2470
    else { // library marker kkossev.commonLib, line 2471
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2472
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2473
    } // library marker kkossev.commonLib, line 2474
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2475
        customUpdated() // library marker kkossev.commonLib, line 2476
    } // library marker kkossev.commonLib, line 2477

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2479
} // library marker kkossev.commonLib, line 2480

/** // library marker kkossev.commonLib, line 2482
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2483
 */ // library marker kkossev.commonLib, line 2484
void logsOff() { // library marker kkossev.commonLib, line 2485
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2486
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2487
} // library marker kkossev.commonLib, line 2488
void traceOff() { // library marker kkossev.commonLib, line 2489
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2490
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2491
} // library marker kkossev.commonLib, line 2492

void configure(String command) { // library marker kkossev.commonLib, line 2494
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2495
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2496
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2497
        return // library marker kkossev.commonLib, line 2498
    } // library marker kkossev.commonLib, line 2499
    // // library marker kkossev.commonLib, line 2500
    String func // library marker kkossev.commonLib, line 2501
    try { // library marker kkossev.commonLib, line 2502
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2503
        "$func"() // library marker kkossev.commonLib, line 2504
    } // library marker kkossev.commonLib, line 2505
    catch (e) { // library marker kkossev.commonLib, line 2506
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2507
        return // library marker kkossev.commonLib, line 2508
    } // library marker kkossev.commonLib, line 2509
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2510
} // library marker kkossev.commonLib, line 2511

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2513
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2514
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2515
} // library marker kkossev.commonLib, line 2516

void loadAllDefaults() { // library marker kkossev.commonLib, line 2518
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2519
    deleteAllSettings() // library marker kkossev.commonLib, line 2520
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2521
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2522
    deleteAllStates() // library marker kkossev.commonLib, line 2523
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2524
    initialize() // library marker kkossev.commonLib, line 2525
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 2526
    updated() // library marker kkossev.commonLib, line 2527
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2528
} // library marker kkossev.commonLib, line 2529

void configureNow() { // library marker kkossev.commonLib, line 2531
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2532
} // library marker kkossev.commonLib, line 2533

/** // library marker kkossev.commonLib, line 2535
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2536
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2537
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2538
 */ // library marker kkossev.commonLib, line 2539
List<String> configure() { // library marker kkossev.commonLib, line 2540
    List<String> cmds = [] // library marker kkossev.commonLib, line 2541
    logInfo 'configure...' // library marker kkossev.commonLib, line 2542
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2543
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2544
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2545
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 2546
    } // library marker kkossev.commonLib, line 2547
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2548
    cmds += configureDevice() // library marker kkossev.commonLib, line 2549
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2550
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2551
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 2552
    //return cmds // library marker kkossev.commonLib, line 2553
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2554
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2555
    } // library marker kkossev.commonLib, line 2556
    else { // library marker kkossev.commonLib, line 2557
        logDebug "no configure() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2558
    } // library marker kkossev.commonLib, line 2559
} // library marker kkossev.commonLib, line 2560

/** // library marker kkossev.commonLib, line 2562
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2563
 */ // library marker kkossev.commonLib, line 2564
void installed() { // library marker kkossev.commonLib, line 2565
    logInfo 'installed...' // library marker kkossev.commonLib, line 2566
    // populate some default values for attributes // library marker kkossev.commonLib, line 2567
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2568
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2569
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2570
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2571
} // library marker kkossev.commonLib, line 2572

/** // library marker kkossev.commonLib, line 2574
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2575
 */ // library marker kkossev.commonLib, line 2576
void initialize() { // library marker kkossev.commonLib, line 2577
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2578
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2579
    updateTuyaVersion() // library marker kkossev.commonLib, line 2580
    updateAqaraVersion() // library marker kkossev.commonLib, line 2581
} // library marker kkossev.commonLib, line 2582

/* // library marker kkossev.commonLib, line 2584
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2585
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2586
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2587
*/ // library marker kkossev.commonLib, line 2588

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2590
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2591
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2592
} // library marker kkossev.commonLib, line 2593

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2595
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2596
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2597
} // library marker kkossev.commonLib, line 2598

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2600
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2601
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2602
} // library marker kkossev.commonLib, line 2603

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2605
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 2606
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 2607
        return // library marker kkossev.commonLib, line 2608
    } // library marker kkossev.commonLib, line 2609
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2610
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2611
    cmd.each { // library marker kkossev.commonLib, line 2612
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2613
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2614
    } // library marker kkossev.commonLib, line 2615
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2616
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2617
} // library marker kkossev.commonLib, line 2618

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2620

String getDeviceInfo() { // library marker kkossev.commonLib, line 2622
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2623
} // library marker kkossev.commonLib, line 2624

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2626
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2627
} // library marker kkossev.commonLib, line 2628

void checkDriverVersion() { // library marker kkossev.commonLib, line 2630
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2631
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2632
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2633
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2634
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2635
        updateTuyaVersion() // library marker kkossev.commonLib, line 2636
        updateAqaraVersion() // library marker kkossev.commonLib, line 2637
    } // library marker kkossev.commonLib, line 2638
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2639
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2640
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2641
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 2642
} // library marker kkossev.commonLib, line 2643

// credits @thebearmay // library marker kkossev.commonLib, line 2645
String getModel() { // library marker kkossev.commonLib, line 2646
    try { // library marker kkossev.commonLib, line 2647
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2648
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2649
    } catch (ignore) { // library marker kkossev.commonLib, line 2650
        try { // library marker kkossev.commonLib, line 2651
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2652
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2653
                return model // library marker kkossev.commonLib, line 2654
            } // library marker kkossev.commonLib, line 2655
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2656
            return '' // library marker kkossev.commonLib, line 2657
        } // library marker kkossev.commonLib, line 2658
    } // library marker kkossev.commonLib, line 2659
} // library marker kkossev.commonLib, line 2660

// credits @thebearmay // library marker kkossev.commonLib, line 2662
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2663
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2664
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2665
    String revision = tokens.last() // library marker kkossev.commonLib, line 2666
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2667
} // library marker kkossev.commonLib, line 2668

/** // library marker kkossev.commonLib, line 2670
 * called from TODO // library marker kkossev.commonLib, line 2671
 */ // library marker kkossev.commonLib, line 2672

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2674
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2675
    unschedule() // library marker kkossev.commonLib, line 2676
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2677
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2678

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2680
} // library marker kkossev.commonLib, line 2681

void resetStatistics() { // library marker kkossev.commonLib, line 2683
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2684
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2685
} // library marker kkossev.commonLib, line 2686

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2688
void resetStats() { // library marker kkossev.commonLib, line 2689
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2690
    state.stats = [:] // library marker kkossev.commonLib, line 2691
    state.states = [:] // library marker kkossev.commonLib, line 2692
    state.lastRx = [:] // library marker kkossev.commonLib, line 2693
    state.lastTx = [:] // library marker kkossev.commonLib, line 2694
    state.health = [:] // library marker kkossev.commonLib, line 2695
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2696
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2697
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2698
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2699
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2700
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2701
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2702
} // library marker kkossev.commonLib, line 2703

/** // library marker kkossev.commonLib, line 2705
 * called from TODO // library marker kkossev.commonLib, line 2706
 */ // library marker kkossev.commonLib, line 2707
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2708
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2709
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2710
        state.clear() // library marker kkossev.commonLib, line 2711
        unschedule() // library marker kkossev.commonLib, line 2712
        resetStats() // library marker kkossev.commonLib, line 2713
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2714
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2715
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2716
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2717
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2718
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2719
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2720
    } // library marker kkossev.commonLib, line 2721

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2723
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2724
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2725
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2726
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2727
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2728

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2730
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2731
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2732
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2733
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2734
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2735
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2736
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2737
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2738
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2739

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2741
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2742
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2743
    } // library marker kkossev.commonLib, line 2744
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2745
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2746
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2747
    } // library marker kkossev.commonLib, line 2748
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2749
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2750
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2751
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2752
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2753

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2755
    if ( mm != null) { // library marker kkossev.commonLib, line 2756
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2757
    } // library marker kkossev.commonLib, line 2758
    else { // library marker kkossev.commonLib, line 2759
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2760
    } // library marker kkossev.commonLib, line 2761
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2762
    if ( ep  != null) { // library marker kkossev.commonLib, line 2763
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2764
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2765
    } // library marker kkossev.commonLib, line 2766
    else { // library marker kkossev.commonLib, line 2767
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2768
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2769
    } // library marker kkossev.commonLib, line 2770
} // library marker kkossev.commonLib, line 2771

/** // library marker kkossev.commonLib, line 2773
 * called from TODO // library marker kkossev.commonLib, line 2774
 */ // library marker kkossev.commonLib, line 2775
void setDestinationEP() { // library marker kkossev.commonLib, line 2776
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2777
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2778
        state.destinationEP = ep // library marker kkossev.commonLib, line 2779
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2780
    } // library marker kkossev.commonLib, line 2781
    else { // library marker kkossev.commonLib, line 2782
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2783
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2784
    } // library marker kkossev.commonLib, line 2785
} // library marker kkossev.commonLib, line 2786

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2788
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2789
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2790
    } // library marker kkossev.commonLib, line 2791
} // library marker kkossev.commonLib, line 2792

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2794
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2795
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2796
    } // library marker kkossev.commonLib, line 2797
} // library marker kkossev.commonLib, line 2798

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2800
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2801
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2802
    } // library marker kkossev.commonLib, line 2803
} // library marker kkossev.commonLib, line 2804

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2806
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2807
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2808
    } // library marker kkossev.commonLib, line 2809
} // library marker kkossev.commonLib, line 2810

// _DEBUG mode only // library marker kkossev.commonLib, line 2812
void getAllProperties() { // library marker kkossev.commonLib, line 2813
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2814
    device.properties.each { it -> // library marker kkossev.commonLib, line 2815
        log.debug it // library marker kkossev.commonLib, line 2816
    } // library marker kkossev.commonLib, line 2817
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2818
    settings.each { it -> // library marker kkossev.commonLib, line 2819
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2820
    } // library marker kkossev.commonLib, line 2821
    log.trace 'Done' // library marker kkossev.commonLib, line 2822
} // library marker kkossev.commonLib, line 2823

// delete all Preferences // library marker kkossev.commonLib, line 2825
void deleteAllSettings() { // library marker kkossev.commonLib, line 2826
    settings.each { it -> // library marker kkossev.commonLib, line 2827
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2828
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2829
    } // library marker kkossev.commonLib, line 2830
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2831
} // library marker kkossev.commonLib, line 2832

// delete all attributes // library marker kkossev.commonLib, line 2834
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2835
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2836
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2837
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2838
    } // library marker kkossev.commonLib, line 2839
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2840
} // library marker kkossev.commonLib, line 2841

// delete all State Variables // library marker kkossev.commonLib, line 2843
void deleteAllStates() { // library marker kkossev.commonLib, line 2844
    state.each { it -> // library marker kkossev.commonLib, line 2845
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2846
    } // library marker kkossev.commonLib, line 2847
    state.clear() // library marker kkossev.commonLib, line 2848
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2849
} // library marker kkossev.commonLib, line 2850

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2852
    unschedule() // library marker kkossev.commonLib, line 2853
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2854
} // library marker kkossev.commonLib, line 2855

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2857
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2858
} // library marker kkossev.commonLib, line 2859

void parseTest(String par) { // library marker kkossev.commonLib, line 2861
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2862
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2863
    parse(par) // library marker kkossev.commonLib, line 2864
} // library marker kkossev.commonLib, line 2865

def testJob() { // library marker kkossev.commonLib, line 2867
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2868
} // library marker kkossev.commonLib, line 2869

/** // library marker kkossev.commonLib, line 2871
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2872
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2873
 */ // library marker kkossev.commonLib, line 2874
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2875
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2876
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2877
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2878
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2879
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2880
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2881
    String cron // library marker kkossev.commonLib, line 2882
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2883
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2884
    } // library marker kkossev.commonLib, line 2885
    else { // library marker kkossev.commonLib, line 2886
        if (minutes < 60) { // library marker kkossev.commonLib, line 2887
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2888
        } // library marker kkossev.commonLib, line 2889
        else { // library marker kkossev.commonLib, line 2890
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2891
        } // library marker kkossev.commonLib, line 2892
    } // library marker kkossev.commonLib, line 2893
    return cron // library marker kkossev.commonLib, line 2894
} // library marker kkossev.commonLib, line 2895

// credits @thebearmay // library marker kkossev.commonLib, line 2897
String formatUptime() { // library marker kkossev.commonLib, line 2898
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2899
} // library marker kkossev.commonLib, line 2900

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2902
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2903
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2904
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2905
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2906
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2907
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2908
} // library marker kkossev.commonLib, line 2909

boolean isTuya() { // library marker kkossev.commonLib, line 2911
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2912
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2913
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2914
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2915
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2916
} // library marker kkossev.commonLib, line 2917

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2919
    if (!isTuya()) { // library marker kkossev.commonLib, line 2920
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2921
        return // library marker kkossev.commonLib, line 2922
    } // library marker kkossev.commonLib, line 2923
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2924
    if (application != null) { // library marker kkossev.commonLib, line 2925
        Integer ver // library marker kkossev.commonLib, line 2926
        try { // library marker kkossev.commonLib, line 2927
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2928
        } // library marker kkossev.commonLib, line 2929
        catch (e) { // library marker kkossev.commonLib, line 2930
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2931
            return // library marker kkossev.commonLib, line 2932
        } // library marker kkossev.commonLib, line 2933
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2934
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2935
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2936
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2937
        } // library marker kkossev.commonLib, line 2938
    } // library marker kkossev.commonLib, line 2939
} // library marker kkossev.commonLib, line 2940

boolean isAqara() { // library marker kkossev.commonLib, line 2942
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2943
} // library marker kkossev.commonLib, line 2944

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2946
    if (!isAqara()) { // library marker kkossev.commonLib, line 2947
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2948
        return // library marker kkossev.commonLib, line 2949
    } // library marker kkossev.commonLib, line 2950
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2951
    if (application != null) { // library marker kkossev.commonLib, line 2952
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2953
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2954
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2955
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2956
        } // library marker kkossev.commonLib, line 2957
    } // library marker kkossev.commonLib, line 2958
} // library marker kkossev.commonLib, line 2959

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2961
    try { // library marker kkossev.commonLib, line 2962
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2963
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2964
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2965
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2966
    } catch (e) { // library marker kkossev.commonLib, line 2967
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2968
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2969
    } // library marker kkossev.commonLib, line 2970
} // library marker kkossev.commonLib, line 2971

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2973
    try { // library marker kkossev.commonLib, line 2974
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2975
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2976
        return date.getTime() // library marker kkossev.commonLib, line 2977
    } catch (e) { // library marker kkossev.commonLib, line 2978
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2979
        return now() // library marker kkossev.commonLib, line 2980
    } // library marker kkossev.commonLib, line 2981
} // library marker kkossev.commonLib, line 2982

void test(String par) { // library marker kkossev.commonLib, line 2984
    List<String> cmds = [] // library marker kkossev.commonLib, line 2985
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2986

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2988
    //parse(par) // library marker kkossev.commonLib, line 2989

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2991
} // library marker kkossev.commonLib, line 2992

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
