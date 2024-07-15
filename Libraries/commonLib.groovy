/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '',
    version: '3.3.2'
)
/*
  *  Common ZCL Library
  *
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
  *  in compliance with the License. You may obtain a copy of the License at:
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
  *  for the specific language governing permissions and limitations under the License.
  *
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project).
  * For a big portions of code all credits go to Jonathan Bradshaw.
  *
  *
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck();
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster;
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix;
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix;
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices;
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix.
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX
  * ver. 3.2.1  2024-06-05 kkossev  - 4 in 1 V3 compatibility; added IAS cluster; setDeviceNameAndProfile() fix;
  * ver. 3.2.2  2024-06-12 kkossev  - removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204;
  * ver. 3.3.0  2024-06-25 kkossev  - fixed exception for unknown clusters; added cluster 0xE001; added powerSource - if 5 minutes after initialize() the powerSource is still unknown, query the device for the powerSource
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize();
  * ver. 3.3.2  2024-07-12 kkossev  - (dev.branch) added PollControl (0x0020) cluster; ping for SONOFF
  *
  *                                   TODO: check deviceCommandTimeout()
  *                                   TODO: offlineCtr is not increasing! (ZBMicro);
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices)
  *                                   TODO: refresh() to include updating the softwareBuild data version
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options
  *                                   TODO: MOVE ZDO counters to health state?
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?)
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?)
  *                                   TODO: disableDefaultResponse for Tuya commands
  *
*/

String commonLibVersion() { '3.3.2' }
String commonLibStamp() { '2024/07/12 8:53 PM' }

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import java.math.BigDecimal

metadata {
        if (_DEBUG) {
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']]
            command 'tuyaTest', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
        }

        // common capabilities for all device types
        capability 'Configuration'
        capability 'Refresh'
        capability 'HealthCheck'
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"]

        // common attributes for all device types
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'

        // common commands for all device types
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]]

        // trap for Hubitat F2 bug
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug'

    preferences {
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ...
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.'
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.'

        if (device) {
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false
            if (advancedOptions == true) {
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.'
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"'
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.'
            }
        }
    }
}

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final String  UNKNOWN = 'UNKNOWN'
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]

@Field static final Map ConfigureOpts = [
    'Configure the device'       : [key:2, function: 'configureNow'],
    'Reset Statistics'           : [key:9, function: 'resetStatistics'],
    '           --            '  : [key:3, function: 'configureHelp'],
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'],
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'],
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'],
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'],
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'],
    '           -             '  : [key:1, function: 'configureHelp'],
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults']
]

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' }

/**
 * Parse Zigbee message
 * @param description Zigbee message in hex format
 */
public void parse(final String description) {
    checkDriverVersion(state)    // +1 ms
    updateRxStats(state)         // +1 ms
    unscheduleCommandTimeoutCheck(state)
    setHealthStatusOnline(state) // +2 ms

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {
        logDebug "parse: zone status: $description"
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) }
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) }
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) }
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" }
        return
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' }
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "enroll response: ${cmds}"
        sendZigbeeCommands(cmds)
        return
    }

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms
        return
    }
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" }
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both)

    if (descMap.profileId == '0000') {
        parseZdoClusters(descMap)
        return
    }
    if (descMap.isClusterSpecific == false) {
        parseGeneralCommandResponse(descMap)
        return
    }
    //
    if (standardAndCustomParseCluster(descMap, description)) { return }
    //
    switch (descMap.clusterInt as Integer) {
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro;
            if (this.respondsTo('customParseAnalogInputClusterDescription')) {
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) }
            }
            break
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster !
            if (this.respondsTo('standardParseColorControlCluster')) {
                standardParseColorControlCluster(descMap, description)
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) }
            }
            break
        default:
            if (settings.logEnable) {
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})"
            }
            break
    }
}

@Field static final Map<Integer, String> ClustersMap = [
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0008: 'LevelControl', 
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering',
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index
    0xFCC0: 'XiaomiFCC0',
]

// first try calling the custom parser, if not found, call the standard parser
/* groovylint-disable-next-line UnusedMethodParameter */
boolean standardAndCustomParseCluster(Map descMap, final String description) {
    Integer clusterInt = descMap.clusterInt as Integer
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN
    if (clusterName == null || clusterName == UNKNOWN) {
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})"
        return false
    }
    String customParser = "customParse${clusterName}Cluster"
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed
    if (this.respondsTo(customParser)) {
        this."${customParser}"(descMap)
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) }
        return true
    }
    String standardParser = "standardParse${clusterName}Cluster"
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file
    if (this.respondsTo(standardParser)) {
        this."${standardParser}"(descMap)
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) }
        return true
    }
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch!
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})"
    }
    return false
}

private static void updateRxStats(final Map state) {
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms
}

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower?
    if (_TRACE_ALL == true) { return false }
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib
        return isSpammyDPsToNotTrace(descMap)
    }
    return false
}

public boolean isSpammyDeviceReport(final Map descMap) {
    if (_TRACE_ALL == true) { return false }
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib
        return isSpammyDPsToIgnore(descMap)
    }
    return false
}

public boolean isSpammyTuyaRadar() {
    if (_TRACE_ALL == true) { return false }
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib
        return isSpammyDeviceProfile()
    }
    return false
}

@Field static final Map<Integer, String> ZdoClusterEnum = [
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request',
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response',
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response'
]

// ZDO (Zigbee Data Object) Clusters Parsing
private void parseZdoClusters(final Map descMap) {
    if (state.stats == null) { state.stats = [:] }
    final Integer clusterId = descMap.clusterInt as Integer
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})"
    final String statusHex = ((List)descMap.data)[1]
    final Integer statusCode = hexStrToUnsignedInt(statusHex)
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}"
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}"
    List<String> cmds = []
    switch (clusterId) {
        case 0x0005 :
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" }
            // send the active endpoint response
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"]
            sendZigbeeCommands(cmds)
            break
        case 0x0006 :
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"]
            sendZigbeeCommands(cmds)
            break
        case 0x0013 : // device announcement
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
        case 0x8004 : // simple descriptor response
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            //parseSimpleDescriptorResponse( descMap )
            break
        case 0x8005 : // endpoint response
            String endpointCount = descMap.data[4]
            String endpointList = descMap.data[5]
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" }
            break
        case 0x8021 : // bind response
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case 0x8022 : //unbind request
        case 0x8034 : //leave response
            if (settings?.logEnable) { log.debug "${clusterInfo}" }
            break
        default :
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break
    }
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) }
}

// Zigbee General Command Parsing
private void parseGeneralCommandResponse(final Map descMap) {
    final int commandId = hexStrToUnsignedInt(descMap.command)
    switch (commandId) {
        case 0x01: parseReadAttributeResponse(descMap); break
        case 0x04: parseWriteAttributeResponse(descMap); break
        case 0x07: parseConfigureResponse(descMap); break
        case 0x09: parseReadReportingConfigResponse(descMap); break
        case 0x0B: parseDefaultCommandResponse(descMap); break
        default:
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})"
            final String clusterName = clusterLookup(descMap.clusterInt)
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data
            final int statusCode = hexStrToUnsignedInt(status)
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}"
            if (statusCode > 0x00) {
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}"
            } else if (settings.logEnable) {
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}"
            }
            break
    }
}

// Zigbee Read Attribute Response Parsing
private void parseReadAttributeResponse(final Map descMap) {
    final List<String> data = descMap.data as List<String>
    final String attribute = data[1] + data[0]
    final int statusCode = hexStrToUnsignedInt(data[2])
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}"
    if (statusCode > 0x00) {
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}"
    }
    else {
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}"
    }
}

// Zigbee Write Attribute Response Parsing
private void parseWriteAttributeResponse(final Map descMap) {
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data
    final int statusCode = hexStrToUnsignedInt(data)
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}"
    if (statusCode > 0x00) {
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}"
    }
    else {
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}"
    }
}

// Zigbee Configure Reporting Response Parsing  - command 0x07
private void parseConfigureResponse(final Map descMap) {
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ...
    final String status = ((List)descMap.data).first()
    final int statusCode = hexStrToUnsignedInt(status)
    if (statusCode == 0x00 && settings.enableReporting != false) {
        state.reportingEnabled = true
    }
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}"
    if (statusCode > 0x00) {
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}"
    } else {
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}"
    }
}

// Parses the response of reading reporting configuration - command 0x09
private void parseReadReportingConfigResponse(final Map descMap) {
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
    if (status == 0) {
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5])
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7])
        int delta = 0
        if (descMap.data.size() >= 10) {
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9])
        }
        else {
            logTrace "descMap.data.size = ${descMap.data.size()}"
        }
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}"
    }
    else {
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})"
    }
}

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) {
    if (!this.respondsTo(handlerName)) {
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found"
        return false
    }
    // execute the customHandler function
    Boolean result = false
    try {
        result = "$handlerName"(handlerArgs)
    }
    catch (e) {
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))"
        return false
    }
    //logDebug "customSetFunction result is ${fncmd}"
    return result
}

// Zigbee Default Command Response Parsing
private void parseDefaultCommandResponse(final Map descMap) {
    final List<String> data = descMap.data as List<String>
    final String commandId = data[0]
    final int statusCode = hexStrToUnsignedInt(data[1])
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}"
    if (statusCode > 0x00) {
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}"
    } else {
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}"
        // ZigUSB has its own interpretation of the Zigbee standards ... :(
        if (this.respondsTo('customParseDefaultCommandResponse')) {
            customParseDefaultCommandResponse(descMap)
        }
    }
}

// Zigbee Attribute IDs
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000
@Field static final int FIRMWARE_VERSION_ID = 0x4000
@Field static final int PING_ATTR_ID = 0x01

@Field static final Map<Integer, String> ZigbeeStatusEnum = [
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only',
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster'
]

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting',
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response',
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated',
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response'
]

@Field static final int ROLLING_AVERAGE_N = 10
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) {
    BigDecimal avg = avgPar
    if (avg == null || avg == 0) { avg = newSample }
    avg -= avg / ROLLING_AVERAGE_N
    avg += newSample / ROLLING_AVERAGE_N
    return avg
}

void handlePingResponse() {
    Long now = new Date().getTime()
    if (state.lastRx == null) { state.lastRx = [:] }
    state.lastRx['checkInTime'] = now

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger()
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning }
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning }
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int
        sendRttEvent()
    }
    else {
        logWarn "unexpected ping timeRunning=${timeRunning} "
    }
    state.states['isPing'] = false
}

/*
 * -----------------------------------------------------------------------------
 * Standard clusters reporting handlers
 * -----------------------------------------------------------------------------
*/
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']]

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method
private void standardParseBasicCluster(final Map descMap) {
    Long now = new Date().getTime()
    if (state.lastRx == null) { state.lastRx = [:] }
    state.lastRx['checkInTime'] = now
    boolean isPing = state.states['isPing'] ?: false
    switch (descMap.attrInt as Integer) {
        case 0x0000:
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}"
            break
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism
            if (isPing) {
                handlePingResponse()
            }
            else {
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
            }
            break
        case 0x0004:
            logDebug "received device manufacturer ${descMap?.value}"
            // received device manufacturer IKEA of Sweden
            String manufacturer = device.getDataValue('manufacturer')
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) {
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}"
                device.updateDataValue('manufacturer', descMap?.value)
            }
            break
        case 0x0005:
            if (isPing) {
                handlePingResponse()
            }
            else {
                logDebug "received device model ${descMap?.value}"
                // received device model Remote Control N2
                String model = device.getDataValue('model')
                if ((model == null || model == 'unknown') && (descMap?.value != null)) {
                    logWarn "updating device model from ${model} to ${descMap?.value}"
                    device.updateDataValue('model', descMap?.value)
                }
            }
            break
        case 0x0007:
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            String currentPowerSource = device.getDataValue('powerSource')
            if (currentPowerSource == null || currentPowerSource == 'unknown') {
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}"
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical')
            }
            break
        case 0xFFDF:
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})"
            break
        case 0xFFE2:
            logDebug "Tuya check-in (AppVersion=${descMap?.value})"
            break
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] :
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}"
            break
        case 0xFFFE:
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}"
            break
        case FIRMWARE_VERSION_ID:    // 0x4000
            final String version = descMap.value ?: 'unknown'
            log.info "device firmware version is ${version}"
            updateDataValue('softwareBuild', version)
            break
        default:
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

private void standardParsePollControlCluster(final Map descMap) {
    switch (descMap.attrInt as Integer) {
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break
    }
}

public void clearIsDigital()        { state.states['isDigital'] = false }
void switchDebouncingClear() { state.states['debounce']  = false }
void isRefreshRequestClear() { state.states['isRefresh'] = false }

Map myParseDescriptionAsMap(String description) {
    Map descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch (e1) {
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}"
        // try alternative custom parsing
        descMap = [:]
        try {
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
                List<String> pair = entry.split(':')
                [(pair.first().trim()): pair.last().trim()]
            }
        }
        catch (e2) {
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}"
            return [:]
        }
        logDebug "alternative method parsing success: descMap=${descMap}"
    }
    return descMap
}

private boolean isTuyaE00xCluster(String description) {
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) {
        return false
    }
    // try to parse ...
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..."
    Map descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
    }
    catch (e) {
        logDebug "<b>exception</b> caught while parsing description:  ${description}"
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
        // cluster E001 is the one that is generating exceptions...
        return true
    }

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) {
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}"
    }
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') {
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" }
    }
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') {
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" }
    }
    else {
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap"
        return false
    }
    return true    // processed
}

// return true if further processing in the main parse method should be cancelled !
private boolean otherTuyaOddities(final String description) {
  /*
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) {
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4
        return true
    }
*/
    Map descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch (e1) {
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}"
        // try alternative custom parsing
        descMap = [:]
        try {
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
                List<String> pair = entry.split(':')
                [(pair.first().trim()): pair.last().trim()]
            }
        }
        catch (e2) {
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}"
            return true
        }
        logDebug "alternative method parsing success: descMap=${descMap}"
    }
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}
    if (descMap.attrId == null) {
        //logDebug "otherTuyaOddities: descMap = ${descMap}"
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping"
        return false
    }
    boolean bWasAtLeastOneAttributeProcessed = false
    boolean bWasThereAnyStandardAttribite = false
    // attribute report received
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
    descMap.additionalAttrs.each {
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
    }
    attrData.each {
        if (it.status == '86') {
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}"
        // TODO - skip parsing?
        }
        switch (it.cluster) {
            case '0000' :
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) {
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
                    bWasAtLeastOneAttributeProcessed = true
                }
                else if (it.attrId in ['FFFE', 'FFDF']) {
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
                    bWasAtLeastOneAttributeProcessed = true
                }
                else {
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping"
                    bWasThereAnyStandardAttribite = true
                }
                break
            default :
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping"
                break
        } // switch
    } // for each attribute
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite
}

public String intTo16bitUnsignedHex(int value) {
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

public String intTo8bitUnsignedHex(int value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

/*
 * -----------------------------------------------------------------------------
 * Tuya cluster EF00 specific code
 * -----------------------------------------------------------------------------
*/
private static int getCLUSTER_TUYA()       { 0xEF00 }
private static int getSETDATA()            { 0x00 }
private static int getSETTIME()            { 0x24 }

// Tuya Commands
private static int getTUYA_REQUEST()       { 0x00 }
private static int getTUYA_REPORTING()     { 0x01 }
private static int getTUYA_QUERY()         { 0x02 }
private static int getTUYA_STATUS_SEARCH() { 0x06 }
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ]
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ]
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ]
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ]
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ]
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits

private void syncTuyaDateTime() {
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present.
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800
    long offset = 0
    int offsetHours = 0
    Calendar cal = Calendar.getInstance()    //it return same time as new Date()
    int hour = cal.get(Calendar.HOUR_OF_DAY)
    try {
        offset = location.getTimeZone().getOffset(new Date().getTime())
        offsetHours = (offset / 3600000) as int
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h"
    } catch (e) {
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
    }
    //
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8))
    sendZigbeeCommands(cmds)
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})"
}

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined
public void standardParseTuyaCluster(final Map descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME
        syncTuyaDateTime()
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        String status = descMap?.data[1]
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != '00') {
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"
        }
    }
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) {
        int dataLen = descMap?.data.size()
        //log.warn "dataLen=${dataLen}"
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        if (dataLen <= 5) {
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})"
            return
        }
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024
        for (int i = 0; i < (dataLen - 4); ) {
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i])
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          //
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) {
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})"
            }
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4
        }
    }
    else {
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}"
    }
}

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message)
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}"
    if (this.respondsTo('customProcessTuyaDp')) {
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...'
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) {
            return       // EF00 DP has been processed in the custom handler - we are done!
        }
    }
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver)
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {
            return      // sucessfuly processed the new way - we are done.  (version 3.0)
        }
    }
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
}

private int getTuyaAttributeValue(final List<String> _data, final int index) {
    int retValue = 0
    if (_data.size() >= 6) {
        int dataLength = zigbee.convertHexToInt(_data[5 + index])
        if (dataLength == 0) { return 0 }
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5])
            power = power * 256
        }
    }
    return retValue
}

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) }

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) {
    List<String> cmds = []
    int ep = safeToInt(state.destinationEP)
    if (ep == null || ep == 0) { ep = 1 }
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA
    int tuyaCmd
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified!\
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) {
        tuyaCmd = DEVICE?.device?.tuyaCmd
    }
    else {
        tuyaCmd = /*isFingerbot() ? 0x04 : */ tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some ..
    }
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd )
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}"
    return cmds
}

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) }

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) {
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" }
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

//private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C }
//private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 }

public List<String> tuyaBlackMagic() {
    int ep = safeToInt(state.destinationEP ?: 01)
    if (ep == null || ep == 0) { ep = 1 }
    logInfo 'tuyaBlackMagic()...'
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200)
}

public void aqaraBlackMagic() {
    List<String> cmds = []
    if (this.respondsTo('customAqaraBlackMagic')) {
        cmds = customAqaraBlackMagic()
    }
    if (cmds != null && !cmds.isEmpty()) {
        logDebug 'sending aqaraBlackMagic()'
        sendZigbeeCommands(cmds)
        return
    }
    logDebug 'aqaraBlackMagic() was SKIPPED'
}

// Invoked from configure()
public List<String> initializeDevice() {
    List<String> cmds = []
    logInfo 'initializeDevice...'
    if (this.respondsTo('customInitializeDevice')) {
        List<String> customCmds = customInitializeDevice()
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds }
    }
    logDebug "initializeDevice(): cmds=${cmds}"
    return cmds
}

// Invoked from configure()
public List<String> configureDevice() {
    List<String> cmds = []
    logInfo 'configureDevice...'
    if (this.respondsTo('customConfigureDevice')) {
        List<String> customCmds = customConfigureDevice()
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds }
    }
    // sendZigbeeCommands(cmds) changed 03/04/2024
    logDebug "configureDevice(): cmds=${cmds}"
    return cmds
}

/*
 * -----------------------------------------------------------------------------
 * Hubitat default handlers methods
 * -----------------------------------------------------------------------------
*/

List<String> customHandlers(final List customHandlersList) {
    List<String> cmds = []
    if (customHandlersList != null && !customHandlersList.isEmpty()) {
        customHandlersList.each { handler ->
            if (this.respondsTo(handler)) {
                List<String> customCmds = this."${handler}"()
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds }
            }
        }
    }
    return cmds
}

void refresh() {
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}"
    checkDriverVersion(state)
    List<String> cmds = [], customCmds = []
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it
        customCmds = customRefresh()
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' }
    }
    else {  // call all known libraryRefresh methods
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh'])
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' }
    }
    if (cmds != null && !cmds.isEmpty()) {
        logDebug "refresh() cmds=${cmds}"
        setRefreshRequest()    // 3 seconds
        sendZigbeeCommands(cmds)
    }
    else {
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}"
    }
}

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) }
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }
public void clearInfoEvent()      { sendInfoEvent('clear') }

public void sendInfoEvent(String info=null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: 'Status', value: 'clear', type: 'digital')
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, type: 'digital')
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute
    }
}

public void ping() {
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime()
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true
    scheduleCommandTimeoutCheck()
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID
    if (isVirtual()) { runInMillis(10, 'virtualPong') }
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) }
    logDebug 'ping...'
}

private void virtualPong() {
    logDebug 'virtualPing: pong!'
    Long now = new Date().getTime()
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger()
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning }
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning }
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int
        sendRttEvent()
    }
    else {
        logWarn "unexpected ping timeRunning=${timeRunning} "
    }
    state.states['isPing'] = false
    unscheduleCommandTimeoutCheck(state)
}

public void sendRttEvent( String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null ) { state.lastTx = [:] }
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical')
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical')
    }
}

private String clusterLookup(final Object cluster) {
    if (cluster != null) {
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}"
    }
    logWarn 'cluster is NULL!'
    return 'NULL'
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    if (state.states == null) { state.states = [:] }
    state.states['isTimeoutCheck'] = true
    runIn(delay, 'deviceCommandTimeout')
}

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call !
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(
    if (state.states == null) { state.states = [:] }
    if (state.states['isTimeoutCheck'] == true) {
        state.states['isTimeoutCheck'] = false
        unschedule('deviceCommandTimeout')
    }
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent('timeout')
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1
}

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2)  {
        String cron = getCron( intervalMins * 60 )
        schedule(cron, 'deviceHealthCheck')
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes"
    }
    else {
        logWarn 'deviceHealthCheck is not scheduled!'
        unschedule('deviceHealthCheck')
    }
}

private void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    device.deleteCurrentState('healthStatus')
    logWarn 'device health check is disabled!'
}

// called when any event was received from the Zigbee device in the parse() method.
private void setHealthStatusOnline(Map state) {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {
        sendHealthStatusEvent('online')
        logInfo 'is now online!'
    }
}

private void deviceHealthCheck() {
    checkDriverVersion(state)
    if (state.health == null) { state.health = [:] }
    int ctr = state.health['checkCtr3'] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    state.health['checkCtr3'] = ctr + 1
}

private void sendHealthStatusEvent(final String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital')
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

 // Invoked by Hubitat when the driver configuration is updated
void updated() {
    logInfo 'updated()...'
    checkDriverVersion(state)
    logInfo"driver version ${driverVersionAndTimeStamp()}"
    unschedule()

    if (settings.logEnable) {
        logTrace(settings.toString())
        runIn(86400, 'logsOff')
    }
    if (settings.traceEnable) {
        logTrace(settings.toString())
        runIn(1800, 'traceOff')
    }

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        // schedule the periodic timer
        final int interval = (settings.healthCheckInterval as Integer) ?: 0
        if (interval > 0) {
            //log.trace "healthMethod=${healthMethod} interval=${interval}"
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method"
            scheduleDeviceHealthCheck(interval, healthMethod)
        }
    }
    else {
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod
        log.info 'Health Check is disabled!'
    }
    if (this.respondsTo('customUpdated')) {
        customUpdated()
    }

    sendInfoEvent('updated')
}

private void logsOff() {
    logInfo 'debug logging disabled...'
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}
private void traceOff() {
    logInfo 'trace logging disabled...'
    device.updateSetting('traceEnable', [value: 'false', type: 'bool'])
}

public void configure(String command) {
    logInfo "configure(${command})..."
    if (!(command in (ConfigureOpts.keySet() as List))) {
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}"
        return
    }
    //
    String func
    try {
        func = ConfigureOpts[command]?.function
        "$func"()
    }
    catch (e) {
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)"
        return
    }
    logInfo "executed '${func}'"
}

/* groovylint-disable-next-line UnusedMethodParameter */
void configureHelp(final String val) {
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" }
}

public void loadAllDefaults() {
    logWarn 'loadAllDefaults() !!!'
    deleteAllSettings()
    deleteAllCurrentStates()
    deleteAllScheduledJobs()
    deleteAllStates()
    deleteAllChildDevices()

    initialize()
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024
    updated()
    sendInfoEvent('All Defaults Loaded! F5 to refresh')
}

private void configureNow() {
    configure()
}

/**
 * Send configuration parameters to the device
 * Invoked when device is first installed and when the user updates the configuration  TODO
 * @return sends zigbee commands
 */
void configure() {
    List<String> cmds = []
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}"
    logDebug "configure(): settings: $settings"
    if (isTuya()) {
        cmds += tuyaBlackMagic()
    }
    aqaraBlackMagic()   // zigbee commands are sent here!
    List<String> initCmds = initializeDevice()
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds }
    List<String> cfgCmds = configureDevice()
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds }
    if (cmds != null && !cmds.isEmpty()) {
        sendZigbeeCommands(cmds)
        logDebug "configure(): sent cmds = ${cmds}"
        sendInfoEvent('sent device configuration')
    }
    else {
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}"
    }
}

 // Invoked when the device is installed with this driver automatically selected.
void installed() {
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1
    logInfo "installed()... instCtr=${state.stats.instCtr}"
    // populate some default values for attributes
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital')
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital')
    sendInfoEvent('installed')
    runIn(3, 'updated')
    runIn(5, 'queryPowerSource')
}

private void queryPowerSource() {
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0))
}

 // Invoked from 'LoadAllDefaults'
private void initialize() {
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1
    logInfo "initialize()... initCtr=${state.stats.initCtr}"
    if (device.getDataValue('powerSource') == null) {
        logInfo "initializing device powerSource 'unknown'"
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital')
    }
    initializeVars(fullInit = true)
    updateTuyaVersion()
    updateAqaraVersion()
}

/*
 *-----------------------------------------------------------------------------
 * kkossev drivers commonly used functions
 *-----------------------------------------------------------------------------
*/

static Integer safeToInt(Object val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

static Double safeToDouble(Object val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) {
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

public void sendZigbeeCommands(List<String> cmd) {
    if (cmd == null || cmd.isEmpty()) {
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}"
        return
    }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        if (it == null || it.isEmpty() || it == 'null') {
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})"
            return
        }
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] }
    }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(allActions)
    logDebug "sendZigbeeCommands: sent cmd=${cmd}"
}

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" }

private String getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
}

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())]
    return state.destinationEP ?: device.endpointId ?: '01'
}

@CompileStatic
public void checkDriverVersion(final Map state) {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(false)
        updateTuyaVersion()
        updateAqaraVersion()
    }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.stats  == null) { state.stats =  [:] }
}

// credits @thebearmay
String getModel() {
    try {
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */
        String model = getHubVersion() // requires >=2.2.8.141
    } catch (ignore) {
        try {
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res ->
                model = res.data.device.modelName
                return model
            }
        } catch (ignore_again) {
            return ''
        }
    }
}

// credits @thebearmay
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 )
    String model = getModel()            // <modelName>Rev C-7</modelName>
    String[] tokens = model.split('-')
    String revision = tokens.last()
    return (Integer.parseInt(revision) >= minLevel)
}

void deleteAllStatesAndJobs() {
    state.clear()    // clear all states
    unschedule()
    device.deleteCurrentState('*')
    device.deleteCurrentState('')

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
}

void resetStatistics() {
    runIn(1, 'resetStats')
    sendInfoEvent('Statistics are reset. Refresh the web page')
}

// called from initializeVars(true) and resetStatistics()
void resetStats() {
    logDebug 'resetStats...'
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:]
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] }
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0
}

void initializeVars( boolean fullInit = false ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() }
        //state.comment = 'Works with Tuya Zigbee Devices'
        logInfo 'all states and scheduled jobs cleared!'
        state.driverVersion = driverVersionAndTimeStamp()
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}"
        state.deviceType = DEVICE_TYPE
        sendInfoEvent('Initialized')
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) }
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) }

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }

    // common libraries initialization
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024
    executeCustomHandler('groupsInitializeVars', fullInit)
    executeCustomHandler('illuminanceInitializeVars', fullInit)
    executeCustomHandler('onOfInitializeVars', fullInit)
    executeCustomHandler('energyInitializeVars', fullInit)
    //
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers!
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024
    //
    // custom device driver specific initialization should be at the end
    executeCustomHandler('customInitializeVars', fullInit)
    executeCustomHandler('customCreateChildDevices', fullInit)
    executeCustomHandler('customInitEvents', fullInit)

    final String mm = device.getDataValue('model')
    if (mm != null) { logTrace " model = ${mm}" }
    else { logWarn ' Model not found, please re-pair the device!' }
    final String ep = device.getEndpointId()
    if ( ep  != null) {
        //state.destinationEP = ep
        logTrace " destinationEP = ${ep}"
    }
    else {
        logWarn ' Destination End Point not found, please re-pair the device!'
        //state.destinationEP = "01"    // fallback
    }
}

// not used!?
void setDestinationEP() {
    String ep = device.getEndpointId()
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" }
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP
}

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } }
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } }
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } }
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } }

// _DEBUG mode only
void getAllProperties() {
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it }
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev
}

// delete all Preferences
void deleteAllSettings() {
    String preferencesDeleted = ''
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") }
    logDebug "Deleted settings: ${preferencesDeleted}"
    logInfo  'All settings (preferences) DELETED'
}

// delete all attributes
void deleteAllCurrentStates() {
    String attributesDeleted = ''
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") }
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED'
}

// delete all State Variables
void deleteAllStates() {
    String stateDeleted = ''
    state.each { it -> stateDeleted += "${it.key}, " }
    state.clear()
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED'
}

void deleteAllScheduledJobs() {
    unschedule() ; logInfo 'All scheduled jobs DELETED'
}

void deleteAllChildDevices() {
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) }
    sendInfoEvent 'All child devices DELETED'
}

void testParse(String par) {
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A
    log.trace '------------------------------------------------------'
    log.warn "testParse - <b>START</b> (${par})"
    parse(par)
    log.warn "testParse -   <b>END</b> (${par})"
    log.trace '------------------------------------------------------'
}

Object testJob() {
    log.warn 'test job executed'
}

/**
 * Calculates and returns the cron expression
 * @param timeInSeconds interval in seconds
 */
String getCron(int timeInSeconds) {
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping')
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours
    final Random rnd = new Random()
    int minutes = (timeInSeconds / 60 ) as int
    int  hours = (minutes / 60 ) as int
    if (hours > 23) { hours = 23 }
    String cron
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" }
    else {
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" }
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  }
    }
    return cron
}

// credits @thebearmay
String formatUptime() {
    return formatTime(location.hub.uptime)
}

String formatTime(int timeInSeconds) {
    if (timeInSeconds == null) { return UNKNOWN }
    int days = (timeInSeconds / 86400).toInteger()
    int hours = ((timeInSeconds % 86400) / 3600).toInteger()
    int minutes = ((timeInSeconds % 3600) / 60).toInteger()
    int seconds = (timeInSeconds % 60).toInteger()
    return "${days}d ${hours}h ${minutes}m ${seconds}s"
}

boolean isTuya() {
    if (!device) { return true }    // fallback - added 04/03/2024
    String model = device.getDataValue('model')
    String manufacturer = device.getDataValue('manufacturer')
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false
}

void updateTuyaVersion() {
    if (!isTuya()) { logTrace 'not Tuya' ; return }
    final String application = device.getDataValue('application')
    if (application != null) {
        Integer ver
        try { ver = zigbee.convertHexToInt(application) }
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return }
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString()
        if (device.getDataValue('tuyaVersion') != str) {
            device.updateDataValue('tuyaVersion', str)
            logInfo "tuyaVersion set to $str"
        }
    }
}

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false }

void updateAqaraVersion() {
    if (!isAqara()) { logTrace 'not Aqara' ; return }
    String application = device.getDataValue('application')
    if (application != null) {
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2)))
        if (device.getDataValue('aqaraVersion') != str) {
            device.updateDataValue('aqaraVersion', str)
            logInfo "aqaraVersion set to $str"
        }
    }
}

String unix2formattedDate(Long unixTime) {
    try {
        if (unixTime == null) { return null }
        /* groovylint-disable-next-line NoJavaUtilDate */
        Date date = new Date(unixTime.toLong())
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone)
    } catch (e) {
        logDebug "Error formatting date: ${e.message}. Returning current time instead."
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone)
    }
}

Long formattedDate2unix(String formattedDate) {
    try {
        if (formattedDate == null) { return null }
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate)
        return date.getTime()
    } catch (e) {
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead."
        return now()
    }
}

static String timeToHMS(final int time) {
    int hours = (time / 3600) as int
    int minutes = ((time % 3600) / 60) as int
    int seconds = time % 60
    return "${hours}h ${minutes}m ${seconds}s"
}
