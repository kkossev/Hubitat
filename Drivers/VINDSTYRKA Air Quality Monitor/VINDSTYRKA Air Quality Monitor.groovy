/**
 *  VINDSTYRKA Air Quality Monitor - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 2.0.0  2023-05-08 kkossev  - Initial test version (VINDSTYRKA driver)
 * ver. 2.1.5  2023-09-02 kkossev  - VINDSTYRKA: removed airQualityLevel, added thresholds; airQualityIndex replaced by sensirionVOCindex
 * ver. 3.0.5  2024-04-05 kkossev  - commonLib 3.0.5 check; Groovy lint;
 * ver. 3.1.0  2024-04-24 kkossev  - (dev. branch) commonLib 3.1.0 speed optimization
 *
 *                                   TODO: 
 */

static String version() { "3.1.0" }
static String timeStamp() {"2024/04/25 11:53 PM"}

@Field static final boolean _DEBUG = false

#include kkossev.commonLib
#include kkossev.xiaomiLib
#include kkossev.temperatureLib
#include kkossev.humidityLib

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.lang.Float

deviceType = 'AirQuality'
@Field static final String DEVICE_TYPE = 'AirQuality'

metadata {
    definition(
        name: 'VINDSTYRKA Air Quality Monitor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/VINDSTYRKA%20Air%20Quality%20Monitor/VINDSTYRKA_Air_Quality_Monitor_lib_included.groovy',,
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'Actuator'
        capability 'Refresh'
        capability 'Sensor'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'
        capability 'AirQuality'            // Attributes: airQualityIndex - NUMBER, range:0..500

        attribute 'pm25', 'number'
        attribute 'sensirionVOCindex', 'number'    // VINDSTYRKA used sensirionVOCindex instead of airQualityIndex
        attribute 'airQualityLevel', 'enum', ['Good', 'Moderate', 'Unhealthy for Sensitive Groups', 'Unhealthy', 'Very Unhealthy', 'Hazardous']    // https://www.airnow.gov/aqi/aqi-basics/ **** for Aqara only! ***

        if (isAqaraTVOC()) {
            capability 'Battery'
            attribute 'batteryVoltage', 'number'
        }

        if (_DEBUG) { command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  }

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0004,0402,0405,FC57,FC7C,042A,FC7E', outClusters:'0003,0019,0020,0202', model:'VINDSTYRKA', manufacturer:'IKEA of Sweden', deviceJoinName: 'VINDSTYRKA Air Quality Monitor E2112'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0003,0500,0001', outClusters:'0019', model:'lumi.airmonitor.acn01', manufacturer:'LUMI', deviceJoinName: 'Aqara TVOC Air Quality Monitor'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: 'pm25Threshold', type: 'number', title: '<b>PM 2.5 Reporting Threshold</b>', description: '<i>PM 2.5 reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_PM25_THRESHOLD
        if (isVINDSTYRKA()) {
            //input name: 'airQualityIndexCheckInterval', type: 'enum', title: '<b>Air Quality Index check interval</b>', options: AirQualityIndexCheckIntervalOpts.options, defaultValue: AirQualityIndexCheckIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub retreives the Air Quality Index.</i>'
            input name: 'airQualityIndexCheckInterval', type: 'enum', title: '<b>Sensirion VOC index check interval</b>', options: AirQualityIndexCheckIntervalOpts.options, defaultValue: AirQualityIndexCheckIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub retreives the Sensirion VOC index.</i>'
            input name: 'airQualityIndexThreshold', type: 'number', title: '<b>Sensirion VOC index Reporting Threshold</b>', description: '<i>Sensirion VOC index reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD
        }
        else  if (isAqaraTVOC()) {
            input name: 'airQualityIndexThreshold', type: 'number', title: '<b>Air Quality Index Reporting Threshold</b>', description: '<i>Air quality index reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD
            input name: 'temperatureScale', type: 'enum', title: '<b>Temperaure Scale on the Screen</b>', options: TemperatureScaleOpts.options, defaultValue: TemperatureScaleOpts.defaultValue, required: true, description: '<i>Changes the temperature scale (Celsius, Fahrenheit) on the screen.</i>'
            input name: 'tVocUnut', type: 'enum', title: '<b>tVOC unit on the Screen</b>', options: TvocUnitOpts.options, defaultValue: TvocUnitOpts.defaultValue, required: true, description: '<i>Changes the tVOC unit (mg/m³, ppb) on the screen.</i>'
        }
    }
}

boolean isVINDSTYRKA() { return (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] }
boolean isAqaraTVOC()  { return (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }

@Field static final Integer DEFAULT_PM25_THRESHOLD = 1
@Field static final Integer DEFAULT_AIR_QUALITY_INDEX_THRESHOLD = 1

@Field static final Map AirQualityIndexCheckIntervalOpts = [        // used by airQualityIndexCheckInterval
    defaultValue: 60,
    options     : [0: 'Disabled', 10: 'Every 10 seconds', 30: 'Every 30 seconds', 60: 'Every 1 minute', 300: 'Every 5 minutes', 900: 'Every 15 minutes', 3600: 'Every 1 hour']
]
@Field static final Map TemperatureScaleOpts = [            // bit 7
    defaultValue: 0,
    options     : [0: 'Celsius', 1: 'Fahrenheit']
]
@Field static final Map TvocUnitOpts = [                    // bit 0
    defaultValue: 1,
    options     : [0: 'mg/m³', 1: 'ppb']
]

// TODO - use for all events sent by this driver !!
// TODO - move to the commonLib !!
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void airQualityEvent(final String eventName, value, raw) {
    final String descriptionText = "${eventName} is ${value}"
    Map eventMap = [name: eventName, value: value, descriptionText: descriptionText, type: 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force event to be sent
    }
    if (logEnable) { eventMap.descriptionText += " (raw ${raw})" }
    sendEvent(eventMap)
    logInfo "${eventMap.descriptionText}"
}

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterAirQualityLib(final Map descMap) {
    //logWarn "parseXiaomiClusterAirQualityLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"

    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    logTrace "zigbee received AirQuality 0xFCC0 attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    Boolean result

    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        parseXiaomiClusterAirQualityTags(tags)
        return
    }

    result = processClusterAttributeFromDeviceProfile(descMap)

    if ( result == false ) {
        logWarn "parseXiaomiClusterAirQualityLib: received unknown AirQuality cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

    return

/*
    final Integer raw
    //final String  value
    switch (descMap.attrInt as Integer) {
        case 0x040a:    // E1 battery - read only
            raw = hexStrToUnsignedInt(descMap.value)
            airQuality("battery", raw, raw)
            break
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterAirQualityTags(tags)
            break
        default:
            logWarn "parseXiaomiClusterAirQualityLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
    */
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
// called from parseXiaomiClusterAirQualitytLib
//
void parseXiaomiClusterAirQualityTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg; (ignore it!)"
                break
            case 0x05:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
                break
            case 0x06:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}"
                break
            case 0x08:            // SWBUILD_TAG_ID:
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
                device.updateDataValue('aqaraVersion', swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer, 2)
                if (state.health == null) { state.health = [:] }
                String oldNWK = state.health['parentNWK'] ?: 'n/a'
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
                if (oldNWK != nwk ) {
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                    state.health['parentNWK']  = nwk
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                }
                break
            case 0x64:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})" // Aqara TVOC
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

/**
 * Schedule airQuality polling
 * @param intervalMins interval in seconds
 */
/* groovylint-disable-next-line UnusedPrivateMethod */
private void scheduleAirQualityPolling(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    logDebug "cron = ${cron}"
    schedule(cron, 'autoPollAirQuality')
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void unScheduleAirQualityPolling() {
    unschedule('autoPollAirQuality')
}

List<String> pollAirQualityCluster() {
    return  zigbee.readAttribute(0x0201, [0x0000, 0x0012, 0x001B, 0x001C, 0x0029], [:], delay = 3500)      // 0x0000 = local temperature, 0x0012 = heating setpoint, 0x001B = controlledSequenceOfOperation, 0x001C = system mode (enum8 )
}

/**
 * Scheduled job for polling device specific attribute(s) - not used ??
 */
void autoPoll() {
    logDebug 'autoPoll()...'
    checkDriverVersion(state)
    List<String> cmds = []
    if (DEVICE_TYPE in  ['AirQuality']) {
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }

    if (cmds != null && !cmds.isEmpty()) {
        sendZigbeeCommands(cmds)
    }
}


/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPollAirQuality() {
    logDebug 'autoPollThermoautoPollAirQualitystat()...'
    checkDriverVersion()
    List<String> cmds = []
    setRefreshRequest()

    if (DEVICE.refresh != null && DEVICE.refresh != []) {
        logDebug "autoPollAirQuality: calling DEVICE.refresh() ${DEVICE.refresh}"
        DEVICE.refresh.each {
            logTrace "autoPollAirQuality: calling ${it}()"
            cmds += "${it}"()
        }
        if (cmds != null && cmds != [] ) {
            sendZigbeeCommands(cmds)
        }
        else {
            clearRefreshRequest()     // nothing to poll
        }
        return
    }
}

//
// called from updated() in the main code
void customUpdated() {
    if (isVINDSTYRKA()) {
        final int intervalAirQuality = (settings.airQualityIndexCheckInterval as Integer) ?: 0
        if (intervalAirQuality > 0) {
            logInfo "customUpdated: scheduling Air Quality Index check every ${intervalAirQuality} seconds"
            scheduleAirQualityIndexCheck(intervalAirQuality)
        }
        else {
            unScheduleAirQualityIndexCheck()
            logInfo 'customUpdated: Air Quality Index polling is disabled!'
            // 09/02/2023
            device.deleteCurrentState('airQualityIndex')
        }
    }
    else {
        logDebug 'customUpdated: skipping airQuality polling '
    }
}

/*
 * -----------------------------------------------------------------------------
 * handlePm25Event
 * -----------------------------------------------------------------------------
*/
// pm2.5
void customParsePm25Cluster(final Map descMap) {
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

void handlePm25Event( Integer pm25, Boolean isDigital=false ) {
    Map eventMap = [:]
    if (state.stats != null) { state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 } else { state.stats = [:] }
    /* groovylint-disable-next-line NoDouble */
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0)
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) {
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})"
        return
    }
    eventMap.value = Math.round(pm25AsDouble)
    eventMap.name = 'pm25'
    eventMap.unit = '\u03BCg/m3'    //"mg/m3"
    eventMap.type = isDigital == true ? 'digital' : 'physical'
    //eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now())) / 1000)
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastPm25 = device.currentValue('pm25') ?: 0
    Integer delta = Math.abs(lastPm25 - eventMap.value)
    if (delta < ((settings?.pm25Threshold ?: DEFAULT_PM25_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> pm25 report ${eventMap.value}, less than delta ${settings?.pm25Threshold} (lastPm25=${lastPm25})"
        return
    }
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedPm25Event')
        state.lastRx['pm25Time'] = now()
        sendEvent(eventMap)
    }
    else {
        eventMap.type = 'delayed'
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap])
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedPm25Event(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

/*
 * -----------------------------------------------------------------------------
 * airQualityIndex
 * -----------------------------------------------------------------------------
*/
void customParseAirQualityIndexCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    int value = hexStrToUnsignedInt(descMap.value)
    /* groovylint-disable-next-line NoFloat */
    float floatValue = Float.intBitsToFloat(value.intValue())
    handleAirQualityIndexEvent(floatValue as Integer)
}

void handleAirQualityIndexEvent( Integer tVoc, Boolean isDigital=false ) {
    Map eventMap = [:]
    if (state.stats != null) { state.stats['tVocCtr'] = (state.stats['tVocCtr'] ?: 0) + 1 } else { state.stats = [:] }
    Integer tVocCorrected = safeToDouble(tVoc) + safeToDouble(settings?.tVocOffset ?: 0)
    if (tVocCorrected < 0 || tVocCorrected > 999) {
        logWarn "ignored invalid tVoc ${tVoc} (${tVocCorrected})"
        return
    }
    if (safeToInt((device.currentState('airQualityIndex')?.value ?: -1)) == tVocCorrected) {
        logDebug "ignored duplicated tVoc ${tVoc} (${tVocCorrected})"
        return
    }
    eventMap.value = tVocCorrected as Integer
    Integer lastAIQ
    if (isVINDSTYRKA()) {
        eventMap.name = 'sensirionVOCindex'
        lastAIQ = device.currentValue('sensirionVOCindex') ?: 0
    }
    else {
        eventMap.name = 'airQualityIndex'
        lastAIQ = device.currentValue('airQualityIndex') ?: 0
    }
    eventMap.unit = ''
    eventMap.type = isDigital == true ? 'digital' : 'physical'
    eventMap.descriptionText = "${eventMap.name} is ${tVocCorrected} ${eventMap.unit}"
    Integer timeElapsed = ((now() - (state.lastRx['tVocTime'] ?: now() - 10000 )) / 1000) as Integer
    Integer minTime = settings?.minReportingTimetVoc ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer delta = Math.abs(lastAIQ - eventMap.value)
    if (delta < ((settings?.airQualityIndexThreshold ?: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> airQualityIndex ${eventMap.value}, less than delta ${delta} (lastAIQ=${lastAIQ})"
        return
    }
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedtVocEvent')
        state.lastRx['tVocTime'] = now()
        sendEvent(eventMap)
        if (isAqaraTVOC()) {
            sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value)))
        }
    }
    else {
        eventMap.type = 'delayed'
        //logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedtVocEvent',  [overwrite: true, data: eventMap])
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedtVocEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['tVocTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
    if (isAqaraTVOC()) {
        sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value)))
    }
}

List<String> customRefresh() {
    List<String> cmds = []
    if (isAqaraTVOC()) {
            // TODO - check what is available for VINDSTYRKA
        cmds += zigbee.readAttribute(0x042a, 0x0000, [:], delay = 200)                    // pm2.5    attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; 3:Tolerance
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200)
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200)
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

private void sendAirQualityLevelEvent(final String level) {
    if (level == null || level == '') { return }
    final String descriptionText = "Air Quality Level is ${level}"
    logInfo "${descriptionText}"
    sendEvent(name: 'airQualityLevel', value: level, descriptionText: descriptionText, unit: '', isDigital: true)
}

// https://github.com/zigpy/zigpy/discussions/691
// 09/02/2023 - used by Aqara only !
String airQualityIndexToLevel(final Integer index) {
    String level
    if (index < 0 )        { level = 'unknown' }
    else if (index < 50)  { level = 'Good' }
    else if (index < 100) { level = 'Moderate' }
    else if (index < 150) { level = 'Unhealthy for Sensitive Groups' }
    else if (index < 200) { level = 'Unhealthy' }
    else if (index < 300) { level = 'Very Unhealthy' }
    else if (index < 501) { level = 'Hazardous' }
    else                  { level = 'Hazardous Out of Range' }

    return level
}

/**
 * Schedule a  Air Quality Index check
 * @param intervalMins interval in seconds
 */
private void scheduleAirQualityIndexCheck(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    schedule(cron, 'autoPoll')
}

private void unScheduleAirQualityIndexCheck() {
    unschedule('autoPoll')
}

List<String> customConfigureDevice() {
    List<String> cmds = []
    if (isAqaraTVOC()) {
        logDebug 'customConfigureDevice() AqaraTVOC'
        // https://forum.phoscon.de/t/aqara-tvoc-zhaairquality-data/1160/21
        final int tScale = (settings.temperatureScale as Integer) ?: TemperatureScaleOpts.defaultValue
        final int tUnit =  (settings.tVocUnut as Integer) ?: TvocUnitOpts.defaultValue
        logDebug "setting temperatureScale to ${TemperatureScaleOpts.options[tScale]} (${tScale})"
        int cfg = tUnit
        cfg |= (tScale << 4)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0114, DataType.UINT8, cfg, [mfgCode: 0x115F], delay = 200)
        cmds += zigbee.readAttribute(0xFCC0, 0x0114, [mfgCode: 0x115F], delay = 200)
    }
    else if (isVINDSTYRKA()) {
        logDebug 'customConfigureDevice() VINDSTYRKA (nothig to configure)'
    }
    else {
        logWarn 'customConfigureDevice: unsupported device?'
    }
    return cmds
}

List<String> customInitializeDevice() {
    List<String> cmds = []
    if (isAqaraTVOC()) {
        logDebug 'customInitializeDevice() AqaraTVOC'
        return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) +
            zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
            zigbee.readAttribute(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000) +
            zigbee.readAttribute(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE) +
            zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000, DataType.UINT16, 30, 300, 1 * 100) +
            zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.INT16, 30, 300, 0x1) +
            zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, 30, 21600, 0x1) +
            zigbee.configureReporting(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE, DataType.FLOAT4, 10, 3600, 5)
    }
    else if (isVINDSTYRKA()) {
        logDebug 'customInitializeDevice() VINDSTYRKA'
        // Ikea VINDSTYRKA : bind clusters 402, 405, 42A (PM2.5)
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                 // 402 - temperature
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)    // 405 - humidity
        cmds += zigbee.configureReporting(0x042a, 0, 0x39, 30, 60, 1)    // 405 - pm2.5
        //cmds += zigbee.configureReporting(0xfc7e, 0, 0x39, 10, 60, 50)     // provides a measurement in the range of 0-500 that correlates with the tVOC trend display on the unit itself.
        cmds += ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0xfc7e {${device.zigbeeId}} {}", 'delay 251', ]
    }
    else {
        logWarn 'customInitializeDevice: unsupported device?'
    }
    return cmds
}

void customInitializeVars(boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (fullInit || settings?.airQualityIndexCheckInterval == null) { device.updateSetting('airQualityIndexCheckInterval', [value: AirQualityIndexCheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.TemperatureScaleOpts == null) { device.updateSetting('temperatureScale', [value: TemperatureScaleOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.tVocUnut == null) { device.updateSetting('tVocUnut', [value: TvocUnitOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.pm25Threshold == null) { device.updateSetting('pm25Threshold', [value:DEFAULT_PM25_THRESHOLD, type:'number']) }
    if (fullInit || settings?.airQualityIndexThreshold == null) { device.updateSetting('airQualityIndexThreshold', [value:DEFAULT_AIR_QUALITY_INDEX_THRESHOLD, type:'number']) }

    if (isVINDSTYRKA()) {     // 09/02/2023 removed airQualityLevel, replaced airQualityIndex w/ sensirionVOCindex
        device.deleteCurrentState('airQualityLevel')
        device.deleteCurrentState('airQualityIndex')
    }
}

// called from initializeVars() in the main code ...
/* groovylint-disable-next-line EmptyMethod, UnusedMethodParameter */
void initEventsAirQuality(boolean fullInit=false) {
    // nothing to do
}

private String getDescriptionText(final String msg) {
    final String descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) { log.info "${descriptionText}" }
    return descriptionText
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
// (works for BRT-100, Sonoff TRVZV)
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void processDeviceEventAirQuality(final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "processDeviceEventThermostat(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'temperature' :
            handleTemperatureEvent(valueScaled as Float)
            break
        case 'humidity' :
            handleHumidityEvent(valueScaled)
            break
        default :
            sendEvent(eventMap)    // attribute value is changed - send an event !
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            break
    }
}

void testT(final String par) {
    /*
    def descMap = [raw:"3A870102010A120029C409", dni:"3A87", endpoint:"01", cluster:"0201", size:"0A", attrId:"0012", encoding:"29", command:"0A", value:"09C5", clusterInt:513, attrInt:18]
    log.trace "testT(${descMap})"
    def result = processClusterAttributeFromDeviceProfile(descMap)
    log.trace "result=${result}"
    */
    /*
    List<String> cmds = []
    cmds = zigbee.readAttribute(0xFC11, [0x6003, 0x6004, 0x6005, 0x6006, 0x6007], [:], delay=300)
    sendZigbeeCommands(cmds)
    */

    log.trace "testT(${par}) : DEVICE.preferences = ${DEVICE.preferences}"
    String result
    if (DEVICE != null && DEVICE.preferences != null && DEVICE.preferences != [:]) {
        (DEVICE.preferences).each { key, value ->
            log.trace "testT: ${key} = ${value}"
            result = inputIt(key, debug = true)
            logDebug "inputIt: ${result}"
        }
    }
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

