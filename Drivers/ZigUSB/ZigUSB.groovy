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
 * ver. 3.2.0  2024-06-09 kkossev  - (dev. branch) commonLib 3.2.0 allignment
 *
 *                                   TODO: power/voltage/amperage info logs are duplicated
 *                                   TODO: individual thresholds for each attribute
 */

static String version() { '3.2.0' }
static String timeStamp() { '2024/06/09 10:06 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

deviceType = 'Plug'
@Field static final String DEVICE_TYPE = 'Plug'

/* groovylint-disable-next-line NglParseError */
#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.reportingLib
#include kkossev.energyLib
#include kkossev.temperatureLib

metadata {
    definition(
        name: 'ZigUSB',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/ZigUSB/ZigUSB.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'Outlet'

        attribute 'temperature', 'number'   // make it a custom attribute

        // https://github.com/xyzroe/ZigUSB
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b/src/devices/xyzroe.ts
        // https://github.com/Koenkk/zigbee-herdsman-converters/pull/7077 https://github.com/Koenkk/zigbee-herdsman-converters/commit/9f761492fcfeffc4ef2f88f4e96ea3b6afa8ac0b
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0007,0006', outClusters:'0000,0006', model:'ZigUSB', manufacturer:'xyzroe.cc', deviceJoinName: 'Zigbee USB power monitor and switch'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: 'autoReportingTime', type: 'number', title: '<b>Automatic reporting time period</b>', description: '<i>V/A/W reporting interval, seconds (0..3600)<br>0 (zero) disables the automatic reporting!</i>', range: '0..3600', defaultValue: DEFAULT_REPORTING_TIME
        if (settings?.advancedOptions == true) {
            input name: 'inverceSwitch', type: 'bool', title: '<b>Invert the switch on/off</b>', description: '<i>ZigUSB has the on and off states inverted!</i>', defaultValue: true
        }
    }
}
/*
@Field static final int    DEFAULT_REPORTING_TIME = 30
@Field static final int    DEFAULT_PRECISION = 3           // 3 decimal places
@Field static final BigDecimal DEFAULT_DELTA = 0.001
@Field static final int    MAX_POWER_LIMIT = 999
*/

/**
 * ZigUSB has a really wierd way of reporting the on/off state back to the hub...
 */
void customParseDefaultCommandResponse(final Map descMap) {
    logDebug "ZigUSB:  parseDefaultCommandResponse: ${descMap}"
    standardParseOnOffCluster([attrId: '0000', value: descMap.data[0]])
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

void customParseAnalogInputClusterDescription(final Map descMapDummy, String description) {
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
