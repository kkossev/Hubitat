/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnusedImport, UnusedPrivateMethod, VariableName */
library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Common ZCL Library',
    name: 'commonLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy',
    version: '3.1.0',
    documentationLink: ''
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
  * ver. 3.0.1  2023-12-06 kkossev  - nfo event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck();
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster;
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix;
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix;
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices;
  * ver. 3.1.0  2024-04-24 kkossev  - (dev. branch) unnecesery unschedule() speed optimization.
  *
  *                                   TODO: MOVE ZDO counters to health state;
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib
  *                                   TODO: add GetInfo (endpoints list) command
  *                                   TODO: disableDefaultResponse for Tuya commands
  *
*/

String commonLibVersion() { '3.1.0' }
String commonLibStamp() { '2024/04/24 11:50 PM' }

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import java.math.BigDecimal

@Field static final Boolean _THREE_STATE = true

metadata {
        if (_DEBUG) {
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']]
            command 'tuyaTest', [
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
        }

        // common capabilities for all device types
        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'

        // common attributes for all device types
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'

        // common commands for all device types
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]]

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) {
            capability 'Switch'
            if (_THREE_STATE == true) {
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String>
            }
        }

        // trap for Hubitat F2 bug
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug'

    preferences {
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ...
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'

        if (device) {
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false
            if (advancedOptions == true) {
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) {
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false
                }
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>'
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
    defaultValue: 1,
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240,
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map SwitchThreeStateOpts = [
    defaultValue: 0,
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure']
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

boolean isVirtual() { device.controllerType == null || device.controllerType == '' }
/* groovylint-disable-next-line UnusedMethodParameter */
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] }
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] }
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] }
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false }
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] }
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] }

/**
 * Parse Zigbee message
 * @param description Zigbee message in hex format
 */
void parse(final String description) {
    checkDriverVersion(state)    // +1 ms
    updateRxStats(state)         // +1 ms
    unscheduleCommandTimeoutCheck(state)
    setHealthStatusOnline(state) // +2 ms

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {
        logDebug "parse: zone status: $description"
        if (this.respondsTo('parseIasMessage')) {
            parseIasMessage(description)
        }
        else {
            logDebug 'ignored IAS zone status'
        }
        return
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' }
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
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
    //final String clusterName = clusterLookup(descMap.clusterInt)
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : ''
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute }

    switch (descMap.clusterInt as Integer) {
        case zigbee.BASIC_CLUSTER:                          // 0x0000
            parseBasicCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) }
            break
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001
            parsePowerCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) }
            break
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003
            parseIdentityCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) }
            break
        case zigbee.GROUPS_CLUSTER:                        // 0x0004
            parseGroupsCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) }
            break
        case zigbee.SCENES_CLUSTER:                         // 0x0005
            parseScenesCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) }
            break
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006
            parseOnOffCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) }
            break
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008
            parseLevelControlCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) }
            break
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro;
            parseAnalogInputCluster(descMap, description)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) }
            break
        case 0x0012 :                                       // Aqara Cube - Multistate Input
            parseMultistateInputCluster(descMap)
            break
         case 0x0102 :                                      // window covering
            parseWindowCoveringCluster(descMap)
            break
        case 0x0201 :                                       // Aqara E1 TRV
            parseThermostatCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) }
            break
        case 0x0300 :                                       // Aqara LED Strip T1
            parseColorControlCluster(descMap, description)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) }
            break
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400
            parseIlluminanceCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) }
            break
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402
            parseTemperatureCluster(descMap)
            break
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405
            parseHumidityCluster(descMap)
            break
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06
            parseOccupancyCluster(descMap)
            break
        case 0x042A :                                       // pm2.5
            parsePm25Cluster(descMap)
            break
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER:
            parseElectricalMeasureCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) }
            break
        case zigbee.METERING_CLUSTER:
            parseMeteringCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) }
            break
        case 0xE002 :
            parseE002Cluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) }
            break
        case 0xEC03 :   // Linptech unknown cluster
            parseEC03Cluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) }
            break
        case 0xEF00 :                                       // Tuya famous cluster
            parseTuyaCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) }
            break
        case 0xFC11 :                                       // Sonoff
            parseFC11Cluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) }
            break
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf
            parseAirQualityIndexCluster(descMap)
            break
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster
            parseXiaomiCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) }
            break
        default:
            if (settings.logEnable) {
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})"
            }
            break
    }
}

static void updateRxStats(final Map state) {
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms
}

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower?
    if (_TRACE_ALL == true) { return false }
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib
        return isSpammyDPsToNotTrace(descMap)
    }
    return false
}

boolean isSpammyDeviceReport(final Map descMap) {
    if (_TRACE_ALL == true) { return false }
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib
        return isSpammyDPsToIgnore(descMap)
    }
    return false
}

@Field static final Map<Integer, String> ZdoClusterEnum = [
    0x0002: 'Node Descriptor Request',
    0x0005: 'Active Endpoints Request',
    0x0006: 'Match Descriptor Request',
    0x0022: 'Unbind Request',
    0x0013: 'Device announce',
    0x0034: 'Management Leave Request',
    0x8002: 'Node Descriptor Response',
    0x8004: 'Simple Descriptor Response',
    0x8005: 'Active Endpoints Response',
    0x801D: 'Extended Simple Descriptor Response',
    0x801E: 'Extended Active Endpoint Response',
    0x8021: 'Bind Response',
    0x8022: 'Unbind Response',
    0x8023: 'Bind Register Response',
    0x8034: 'Management Leave Response'
]

/**
 * ZDO (Zigbee Data Object) Clusters Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseZdoClusters(final Map descMap) {
    final Integer clusterId = descMap.clusterInt as Integer
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})"
    final String statusHex = ((List)descMap.data)[1]
    final Integer statusCode = hexStrToUnsignedInt(statusHex)
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}"
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}"
    switch (clusterId) {
        case 0x0005 :
            if (state.stats == null) { state.stats = [:] } ; state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" }
            break
        case 0x0006 :
            if (state.stats == null) { state.stats = [:] } ; state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            break
        case 0x0013 : // device announcement
            if (state.stats == null) { state.stats = [:] } ; state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
        case 0x8004 : // simple descriptor response
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            //parseSimpleDescriptorResponse( descMap )
            break
        case 0x8005 : // endpoint response
            String endpointCount = descMap.data[4]
            String endpointList = descMap.data[5]
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" }
            break
        case 0x8021 : // bind response
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case 0x8022 : //unbind request
        case 0x8034 : //leave response
            if (settings?.logEnable) { log.info "${clusterInfo}" }
            break
        default :
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break
    }
}

/**
 * Zigbee General Command Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseGeneralCommandResponse(final Map descMap) {
    final int commandId = hexStrToUnsignedInt(descMap.command)
    switch (commandId) {
        case 0x01: // read attribute response
            parseReadAttributeResponse(descMap)
            break
        case 0x04: // write attribute response
            parseWriteAttributeResponse(descMap)
            break
        case 0x07: // configure reporting response
            parseConfigureResponse(descMap)
            break
        case 0x09: // read reporting configuration response
            parseReadReportingConfigResponse(descMap)
            break
        case 0x0B: // default command response
            parseDefaultCommandResponse(descMap)
            break
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

/**
 * Zigbee Read Attribute Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseReadAttributeResponse(final Map descMap) {
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

/**
 * Zigbee Write Attribute Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseWriteAttributeResponse(final Map descMap) {
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

/**
 * Zigbee Configure Reporting Response Parsing  - command 0x07
 */
void parseConfigureResponse(final Map descMap) {
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

/**
 * Parses the response of reading reporting configuration - command 0x09
 */
void parseReadReportingConfigResponse(final Map descMap) {
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600
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

/* groovylint-disable-next-line MethodParameterTypeRequired */
def executeCustomHandler(String handlerName, handlerArgs) {
    if (!this.respondsTo(handlerName)) {
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found"
        return false
    }
    // execute the customHandler function
    boolean result = false
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

/**
 * Zigbee Default Command Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseDefaultCommandResponse(final Map descMap) {
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
    0x00: 'Success',
    0x01: 'Failure',
    0x02: 'Not Authorized',
    0x80: 'Malformed Command',
    0x81: 'Unsupported COMMAND',
    0x85: 'Invalid Field',
    0x86: 'Unsupported Attribute',
    0x87: 'Invalid Value',
    0x88: 'Read Only',
    0x89: 'Insufficient Space',
    0x8A: 'Duplicate Exists',
    0x8B: 'Not Found',
    0x8C: 'Unreportable Attribute',
    0x8D: 'Invalid Data Type',
    0x8E: 'Invalid Selector',
    0x94: 'Time out',
    0x9A: 'Notification Pending',
    0xC3: 'Unsupported Cluster'
]

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [
    0x00: 'Read Attributes',
    0x01: 'Read Attributes Response',
    0x02: 'Write Attributes',
    0x03: 'Write Attributes Undivided',
    0x04: 'Write Attributes Response',
    0x05: 'Write Attributes No Response',
    0x06: 'Configure Reporting',
    0x07: 'Configure Reporting Response',
    0x08: 'Read Reporting Configuration',
    0x09: 'Read Reporting Configuration Response',
    0x0A: 'Report Attributes',
    0x0B: 'Default Response',
    0x0C: 'Discover Attributes',
    0x0D: 'Discover Attributes Response',
    0x0E: 'Read Attributes Structured',
    0x0F: 'Write Attributes Structured',
    0x10: 'Write Attributes Structured Response',
    0x11: 'Discover Commands Received',
    0x12: 'Discover Commands Received Response',
    0x13: 'Discover Commands Generated',
    0x14: 'Discover Commands Generated Response',
    0x15: 'Discover Attributes Extended',
    0x16: 'Discover Attributes Extended Response'
]

void parseXiaomiCluster(final Map descMap) {
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' }
}

@Field static final int ROLLING_AVERAGE_N = 10
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) {
    BigDecimal avg = avgPar
    if (avg == null || avg == 0) { avg = newSample }
    avg -= avg / ROLLING_AVERAGE_N
    avg += newSample / ROLLING_AVERAGE_N
    return avg
}

/*
 * -----------------------------------------------------------------------------
 * Standard clusters reporting handlers
 * -----------------------------------------------------------------------------
*/
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']]

/**
 * Zigbee Basic Cluster Parsing  0x0000
 * @param descMap Zigbee message in parsed map format
 */
void parseBasicCluster(final Map descMap) {
    Long now = new Date().getTime()
    state.lastRx['checkInTime'] = now
    switch (descMap.attrInt as Integer) {
        case 0x0000:
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}"
            break
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism
            boolean isPing = state.states['isPing'] ?: false
            if (isPing) {
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
            else {
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
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
            logDebug "received device model ${descMap?.value}"
            // received device model Remote Control N2
            String model = device.getDataValue('model')
            if ((model == null || model == 'unknown') && (descMap?.value != null)) {
                logWarn "updating device model from ${model} to ${descMap?.value}"
                device.updateDataValue('model', descMap?.value)
            }
            break
        case 0x0007:
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            //powerSourceEvent( powerSourceReported )
            break
        case 0xFFDF:
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})"
            break
        case 0xFFE2:
            logDebug "Tuya check-in (AppVersion=${descMap?.value})"
            break
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] :
            logDebug "Tuya attribute ${descMap?.attrId} value=${descMap?.value}"
            break
        case 0xFFFE:
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}"
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

// power cluster            0x0001
void parsePowerCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (descMap.attrId in ['0020', '0021']) {
        state.lastRx['batteryTime'] = new Date().getTime()
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1
    }
    if (this.respondsTo('customParsePowerCluster')) {
        customParsePowerCluster(descMap)
    }
    else {
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

/* groovylint-disable-next-line UnusedMethodParameter */
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' }

void parseScenesCluster(final Map descMap) {
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" }
}

void parseGroupsCluster(final Map descMap) {
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" }
}

/*
 * -----------------------------------------------------------------------------
 * on/off cluster            0x0006
 * -----------------------------------------------------------------------------
*/

void parseOnOffCluster(final Map descMap) {
    if (this.respondsTo('customParseOnOffCluster')) {
        customParseOnOffCluster(descMap)
    }
    else if (descMap.attrId == '0000') {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        int rawValue = hexStrToUnsignedInt(descMap.value)
        sendSwitchEvent(rawValue)
    }
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) {
        parseOnOffAttributes(descMap)
    }
    else {
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  }
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :(
    }
}

void clearIsDigital()        { state.states['isDigital'] = false }
void switchDebouncingClear() { state.states['debounce']  = false }
void isRefreshRequestClear() { state.states['isRefresh'] = false }

void toggle() {
    String descriptionText = 'central button switch is '
    String state = ''
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') {
        state = 'on'
    }
    else {
        state = 'off'
    }
    descriptionText += state
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true)
    logInfo "${descriptionText}"
}

void off() {
    if (this.respondsTo('customOff')) {
        customOff()
        return
    }
    if ((settings?.alwaysOn ?: false) == true) {
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!"
        return
    }
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on()
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "off() currentState=${currentState}"
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if (currentState == 'off') {
            runIn(1, 'refresh',  [overwrite: true])
        }
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on'
        String descriptionText = "${value}"
        if (logEnable) { descriptionText += ' (2)' }
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true)
        logInfo "${descriptionText}"
    }
    /*
    else {
        if (currentState != 'off') {
            logDebug "Switching ${device.displayName} Off"
        }
        else {
            logDebug "ignoring off command for ${device.displayName} - already off"
            return
        }
    }
    */

    state.states['isDigital'] = true
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

void on() {
    if (this.respondsTo('customOn')) {
        customOn()
        return
    }
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off()
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "on() currentState=${currentState}"
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') {
            runIn(1, 'refresh',  [overwrite: true])
        }
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on'
        String descriptionText = "${value}"
        if (logEnable) { descriptionText += ' (2)' }
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true)
        logInfo "${descriptionText}"
    }
    /*
    else {
        if (currentState != 'on') {
            logDebug "Switching ${device.displayName} On"
        }
        else {
            logDebug "ignoring on command for ${device.displayName} - already on"
            return
        }
    }
    */
    state.states['isDigital'] = true
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

void sendSwitchEvent(int switchValuePar) {
    int switchValue = safeToInt(switchValuePar)
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) {
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00
    }
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown'
    Map map = [:]
    boolean debounce = state.states['debounce'] ?: false
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown'
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) {
        logDebug "Ignored duplicated switch event ${value}"
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
        return
    }
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}"
    boolean isDigital = state.states['isDigital'] ?: false
    map.type = isDigital ? 'digital' : 'physical'
    if (lastSwitch != value) {
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>"
        state.states['debounce'] = true
        state.states['lastSwitch'] = value
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    } else {
        state.states['debounce'] = true
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
    }
    map.name = 'switch'
    map.value = value
    boolean isRefresh = state.states['isRefresh'] ?: false
    if (isRefresh) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    } else {
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
    if (this.respondsTo('customSwitchEventPostProcesing')) {
        customSwitchEventPostProcesing(map)
    }
}

@Field static final Map powerOnBehaviourOptions = [
    '0': 'switch off',
    '1': 'switch on',
    '2': 'switch last state'
]

@Field static final Map switchTypeOptions = [
    '0': 'toggle',
    '1': 'state',
    '2': 'momentary'
]

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

boolean isTuyaE00xCluster(String description) {
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
boolean otherTuyaOddities(final String description) {
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
    //log.trace "Tuya oddity: filling in attrData ${attrData}"
    }
    attrData.each {
        //log.trace "each it=${it}"
        //def map = [:]
        if (it.status == '86') {
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}"
        // TODO - skip parsing?
        }
        switch (it.cluster) {
            case '0000' :
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) {
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
                    bWasAtLeastOneAttributeProcessed = true
                }
                else if (it.attrId in ['FFFE', 'FFDF']) {
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
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

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] }

void parseOnOffAttributes(final Map it) {
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
    /* groovylint-disable-next-line VariableTypeRequired */
    def mode
    String attrName
    if (it.value == null) {
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}"
        return
    }
    int value = zigbee.convertHexToInt(it.value)
    switch (it.attrId) {
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only
            attrName = 'Global Scene Control'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null
            break
        case '4001' :    // non-Tuya OnTime (UINT16), read-only
            attrName = 'On Time'
            mode = value
            break
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only
            attrName = 'Off Wait Time'
            mode = value
            break
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1
            attrName = 'Power On State'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN'
            break
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]]
            attrName = 'Child Lock'
            mode = value == 0 ? 'off' : 'on'
            break
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]]
            attrName = 'LED mode'
            if (isCircuitBreaker()) {
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null
            }
            else {
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null
            }
            break
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]]
            attrName = 'Power On State'
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null
            break
        case '8003' : //  Over current alarm
            attrName = 'Over current alarm'
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null
            break
        default :
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
            return
    }
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" }
}

void parseLevelControlCluster(final Map descMap) {
    if (this.respondsTo('customParseLevelControlCluster')) {
        customParseLevelControlCluster(descMap)
    }
    else if (this.respondsTo('levelLibParseLevelControlCluster')) {
        levelLibParseLevelControlCluster(descMap)
    }
    else {
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}"
    }
}

String intTo16bitUnsignedHex(int value) {
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

String intTo8bitUnsignedHex(int value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

void parseColorControlCluster(final Map descMap, String description) {
    if (DEVICE_TYPE in ['Bulb']) {
        parseColorControlClusterBulb(descMap, description)
    }
    else if (descMap.attrId == '0000') {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final int rawValue = hexStrToUnsignedInt(descMap.value)
        sendLevelControlEvent(rawValue)
    }
    else {
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}"
    }
}

void parseIlluminanceCluster(final Map descMap) {
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" }
}

// Temperature Measurement Cluster 0x0402
void parseTemperatureCluster(final Map descMap) {
    if (this.respondsTo('customParseTemperatureCluster')) {
        customParseTemperatureCluster(descMap)
    }
    else {
        logWarn "unprocessed Temperature attribute ${descMap.attrId}"
    }
}

// Humidity Measurement Cluster 0x0405
void parseHumidityCluster(final Map descMap) {
    if (this.respondsTo('customParseHumidityCluster')) {
        customParseHumidityCluster(descMap)
    }
    else {
        logWarn "unprocessed Humidity attribute ${descMap.attrId}"
    }
}

// Occupancy Sensing Cluster 0x0406
void parseOccupancyCluster(final Map descMap) {
    if (this.respondsTo('customParseOccupancyCluster')) {
        customParseOccupancyCluster(descMap)
    }
    else {
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}"
    }
}

// Electrical Measurement Cluster 0x0702
void parseElectricalMeasureCluster(final Map descMap) {
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' }
}

// Metering Cluster 0x0B04
void parseMeteringCluster(final Map descMap) {
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' }
}

// pm2.5
void parsePm25Cluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    /* groovylint-disable-next-line NoFloat */
    float floatValue  = Float.intBitsToFloat(value.intValue())
    if (this.respondsTo('handlePm25Event')) {
        handlePm25Event(floatValue as Integer)
    }
    else {
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}"
    }
}

// Analog Input Cluster 0x000C
void parseAnalogInputCluster(final Map descMap, String description=null) {
    if (this.respondsTo('customParseAnalogInputCluster')) {
        customParseAnalogInputCluster(descMap)
    }
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) {
        customParseAnalogInputClusterDescription(description)                   // ZigUSB
    }
    else if (DEVICE_TYPE in ['AirQuality']) {
        parseAirQualityIndexCluster(descMap)
    }
    else {
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}"
    }
}

// Multistate Input Cluster 0x0012
void parseMultistateInputCluster(final Map descMap) {
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" }
}

// Window Covering Cluster 0x0102
void parseWindowCoveringCluster(final Map descMap) {
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" }
}

// thermostat cluster 0x0201
void parseThermostatCluster(final Map descMap) {
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" }
}

void parseFC11Cluster(final Map descMap) {
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" }
}

void parseE002Cluster(final Map descMap) {
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars
}

void parseEC03Cluster(final Map descMap) {
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars
}

/*
 * -----------------------------------------------------------------------------
 * Tuya cluster EF00 specific code
 * -----------------------------------------------------------------------------
*/
private static getCLUSTER_TUYA()       { 0xEF00 }
private static getSETDATA()            { 0x00 }
private static getSETTIME()            { 0x24 }

// Tuya Commands
private static getTUYA_REQUEST()       { 0x00 }
private static getTUYA_REPORTING()     { 0x01 }
private static getTUYA_QUERY()         { 0x02 }
private static getTUYA_STATUS_SEARCH() { 0x06 }
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ]
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ]
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ]
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ]
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ]
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits

void parseTuyaCluster(final Map descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}"
        Long offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
        }
        catch (e) {
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero'
        }
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8))
        logDebug "sending time data : ${cmds}"
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1
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
        for (int i = 0; i < (dataLen - 4); ) {
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i])
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          //
            if (!isChattyDeviceReport(descMap)) {
                logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})"
            }
            processTuyaDP(descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4
        }
    }
    else {
        logWarn "unprocessed Tuya command ${descMap?.command}"
    }
}

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}"
    if (this.respondsTo(customProcessTuyaDp)) {
        logTrace 'customProcessTuyaDp exists, calling it...'
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) {
            return
        }
    }
    // check if the method  method exists
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0
            return
        }
    }
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
}

private int getTuyaAttributeValue(final List<String> _data, final int index) {
    int retValue = 0
    if (_data.size() >= 6) {
        int dataLength = zigbee.convertHexToInt(_data[5 + index])
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5])
            power = power * 256
        }
    }
    return retValue
}

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) {
    List<String> cmds = []
    int ep = safeToInt(state.destinationEP)
    if (ep == null || ep == 0) { ep = 1 }
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd )
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}"
    return cmds
}

private getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

/* groovylint-disable-next-line MethodParameterTypeRequired */
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) {
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" }
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C }
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 }

String tuyaBlackMagic() {
    int ep = safeToInt(state.destinationEP ?: 01)
    if (ep == null || ep == 0) { ep = 1 }
    logInfo 'tuyaBlackMagic()...'
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200)
}

void aqaraBlackMagic() {
    List<String> cmds = []
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage
        if (isAqaraTVOC_OLD()) {
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only
        }
        sendZigbeeCommands( cmds )
        logDebug 'sent aqaraBlackMagic()'
    }
    else {
        logDebug 'aqaraBlackMagic() was SKIPPED'
    }
}

/**
 * initializes the device
 * Invoked from configure()
 * @return zigbee commands
 */
List<String> initializeDevice() {
    List<String> cmds = []
    logInfo 'initializeDevice...'
    if (this.respondsTo('customInitializeDevice')) {
        List<String> customCmds = customInitializeDevice()
        if (customCmds != null && customCmds != []) { cmds +=  customCmds }
    }
    return cmds
}

/**
 * configures the device
 * Invoked from configure()
 * @return zigbee commands
 */
List<String> configureDevice() {
    List<String> cmds = []
    logInfo 'configureDevice...'

    if (this.respondsTo('customConfigureDevice')) {
        List<String> customCmds = customConfigureDevice()
        if (customCmds != null && customCmds != []) { cmds +=  customCmds }
    }
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() }
    // sendZigbeeCommands(cmds) changed 03/04/2024
    return cmds
}

/*
 * -----------------------------------------------------------------------------
 * Hubitat default handlers methods
 * -----------------------------------------------------------------------------
*/

List<String> customHandlers(final List customHandlersList) {
    List<String> cmds = []
    if (customHandlersList != null && customHandlersList != []) {
        customHandlersList.each { handler ->
            if (this.respondsTo(handler)) {
                List<String> customCmds = this."${handler}"()
                if (customCmds != null && customCmds != []) { cmds +=  customCmds }
            }
        }
    }
    return cmds
}

void refresh() {
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}"
    checkDriverVersion(state)
    List<String> cmds = []
    setRefreshRequest()    // 3 seconds

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh'])
    if (customCmds != null && customCmds != []) { cmds +=  customCmds }

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() }
    else {
        if (DEVICE_TYPE in  ['Dimmer']) {
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200)
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200)
        }
        if (DEVICE_TYPE in  ['THSensor']) {
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200)
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200)
        }
    }

    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
    else {
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}"
    }
}

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }

public void clearInfoEvent() {
    sendInfoEvent('clear')
}

public void sendInfoEvent(String info=null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: 'Status', value: 'clear', isDigital: true)
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, isDigital: true)
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute
    }
}

public void ping() {
    if (state.lastTx == null ) { state.lastTx = [:] }
    state.lastTx['pingTime'] = new Date().getTime()
    //if (state.states == null ) { state.states = [:] }
    state.states['isPing'] = true
    scheduleCommandTimeoutCheck()
    if (isVirtual()) {
        runInMillis(10, virtualPong)
    }
    else {
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
    }
    logDebug 'ping...'
}

def virtualPong() {
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
    //unschedule('deviceCommandTimeout')
    unscheduleCommandTimeoutCheck(state)
}

/**
 * sends 'rtt'event (after a ping() command)
 * @param null: calculate the RTT in ms
 *        value: send the text instead ('timeout', 'n/a', etc..)
 * @return none
 */
void sendRttEvent( String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null ) { state.lastTx = [:] }
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true)
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true)
    }
}

/**
 * Lookup the cluster name from the cluster ID
 * @param cluster cluster ID
 * @return cluster name if known, otherwise "private cluster"
 */
private String clusterLookup(final Object cluster) {
    if (cluster != null) {
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}"
    }
    logWarn 'cluster is NULL!'
    return 'NULL'
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    state.states['isTimeoutCheck'] = true
    runIn(delay, 'deviceCommandTimeout')
}

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call !
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( 
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

/**
 * Schedule a device health check
 * @param intervalMins interval in minutes
 */
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

void setHealthStatusOnline(Map state) {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {
        sendHealthStatusEvent('online')
        logInfo 'is now online!'
    }
}

void deviceHealthCheck() {
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

void sendHealthStatusEvent(final String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPoll() {
    logDebug 'autoPoll()...'
    checkDriverVersion(state)
    List<String> cmds = []
    if (DEVICE_TYPE in  ['AirQuality']) {
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }

    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
}

/**
 * Invoked by Hubitat when the driver configuration is updated
 */
void updated() {
    logInfo 'updated()...'
    checkDriverVersion(state)
    logInfo"driver version ${driverVersionAndTimeStamp()}"
    unschedule()

    if (settings.logEnable) {
        logTrace(settings.toString())
        runIn(86400, logsOff)
    }
    if (settings.traceEnable) {
        logTrace(settings.toString())
        runIn(1800, traceOff)
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

/**
 * Disable logging (for debugging)
 */
void logsOff() {
    logInfo 'debug logging disabled...'
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}
void traceOff() {
    logInfo 'trace logging disabled...'
    device.updateSetting('traceEnable', [value: 'false', type: 'bool'])
}

void configure(String command) {
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

void loadAllDefaults() {
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

void configureNow() {
    sendZigbeeCommands( configure() )
}

/**
 * Send configuration parameters to the device
 * Invoked when device is first installed and when the user updates the configuration  TODO
 * @return sends zigbee commands
 */
List<String> configure() {
    List<String> cmds = []
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}"
    logDebug "configure(): settings: $settings"
    if (isTuya()) {
        cmds += tuyaBlackMagic()
    }
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) {
        aqaraBlackMagic()   // zigbee commands are sent here!
    }
    List<String> initCmds = initializeDevice()
    if (initCmds != null && initCmds != [] ) { cmds += initCmds }
    List<String> cfgCmds = configureDevice()
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds }
    // commented out 12/15/2923 sendZigbeeCommands(cmds)
    sendInfoEvent('sent device configuration')
    logDebug "configure(): returning cmds = ${cmds}"
    //return cmds
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
    else {
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}"
    }
}

/**
 * Invoked by Hubitat when driver is installed
 */
void installed() {
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1
    logInfo "installed()... instCtr=${state.stats.instCtr}"
    // populate some default values for attributes
    sendEvent(name: 'healthStatus', value: 'unknown')
    sendEvent(name: 'powerSource', value: 'unknown')
    sendInfoEvent('installed')
    runIn(3, 'updated')
}

/**
 * Invoked when the initialize button is clicked
 */
void initialize() {
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1
    logInfo "initialize()... initCtr=${state.stats.initCtr}"
    initializeVars(fullInit = true)
    updateTuyaVersion()
    updateAqaraVersion()
}

/*
 *-----------------------------------------------------------------------------
 * kkossev drivers commonly used functions
 *-----------------------------------------------------------------------------
*/

/* groovylint-disable-next-line MethodParameterTypeRequired */
static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */
static Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

/* groovylint-disable-next-line MethodParameterTypeRequired */
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) {
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}

void sendZigbeeCommands(List<String> cmd) {
    if (cmd == null || cmd == [] || cmd == 'null') {
        logWarn 'sendZigbeeCommands: no commands to send!'
        return
    }
    logDebug "sendZigbeeCommands(cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] }
    }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(allActions)
}

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" }

String getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
}

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())]
    return state.destinationEP ?: device.endpointId ?: '01'
}

@CompileStatic
void checkDriverVersion(final Map state) {
    return
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

/**
 * called from TODO
 */

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
    state.stats = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.health = [:]
    if (this.respondsTo('groupsLibVersion')) {
        state.zigbeeGroups = [:]
    }
    state.stats['rxCtr'] = 0
    state.stats['txCtr'] = 0
    state.states['isDigital'] = false
    state.states['isRefresh'] = false
    state.health['offlineCtr'] = 0
    state.health['checkCtr3'] = 0
}

/**
 * called from TODO
 */
void initializeVars( boolean fullInit = false ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        //setDeviceNameAndProfile()
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
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) }
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) }

    // common libraries initialization
    executeCustomHandler('groupsInitializeVars', fullInit)
    executeCustomHandler('deviceProfileInitializeVars', fullInit)

    // device specific initialization should be at the end
    executeCustomHandler('customInitializeVars', fullInit)
    executeCustomHandler('customCreateChildDevices', fullInit)
    executeCustomHandler('customInitEvents', fullInit)
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) }

    final String mm = device.getDataValue('model')
    if ( mm != null) {
        logTrace " model = ${mm}"
    }
    else {
        logWarn ' Model not found, please re-pair the device!'
    }
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

/**
 * called from TODO
 */
void setDestinationEP() {
    String ep = device.getEndpointId()
    if (ep != null && ep != 'F2') {
        state.destinationEP = ep
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}"
    }
    else {
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!"
        state.destinationEP = '01'    // fallback EP
    }
}

void logDebug(final String msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(final String msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(final String msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void logTrace(final String msg) {
    if (settings?.traceEnable) {
        log.trace "${device.displayName} " + msg
    }
}

// _DEBUG mode only
void getAllProperties() {
    log.trace 'Properties:'
    device.properties.each { it ->
        log.debug it
    }
    log.trace 'Settings:'
    settings.each { it ->
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev
    }
    log.trace 'Done'
}

// delete all Preferences
void deleteAllSettings() {
    String preferencesDeleted = ''
    settings.each { it ->
        preferencesDeleted += "${it.key} (${it.value}), "
        device.removeSetting("${it.key}")
    }
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
    getChildDevices().each { child ->
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}"
        deleteChildDevice(child.deviceNetworkId)
    }
    sendInfoEvent 'All child devices DELETED'
}

void parseTest(String par) {
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A
    log.warn "parseTest(${par})"
    parse(par)
}

def testJob() {
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
    if (timeInSeconds < 60) {
        cron = "*/$timeInSeconds * * * * ? *"
    }
    else {
        if (minutes < 60) {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"
        }
        else {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"
        }
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
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false
}

void updateTuyaVersion() {
    if (!isTuya()) {
        logTrace 'not Tuya'
        return
    }
    final String application = device.getDataValue('application')
    if (application != null) {
        Integer ver
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString()
        if (device.getDataValue('tuyaVersion') != str) {
            device.updateDataValue('tuyaVersion', str)
            logInfo "tuyaVersion set to $str"
        }
    }
}

boolean isAqara() {
    return device.getDataValue('model')?.startsWith('lumi') ?: false
}

void updateAqaraVersion() {
    if (!isAqara()) {
        logTrace 'not Aqara'
        return
    }
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

long formattedDate2unix(String formattedDate) {
    try {
        if (formattedDate == null) { return null }
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate)
        return date.getTime()
    } catch (e) {
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead."
        return now()
    }
}
/*
void test(String par) {
    List<String> cmds = []
    log.warn "test... ${par}"

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",]
    //parse(par)

    sendZigbeeCommands(cmds)
}
*/
