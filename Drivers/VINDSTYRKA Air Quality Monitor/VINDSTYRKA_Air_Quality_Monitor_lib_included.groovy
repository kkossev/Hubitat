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
void parseAirQualityIndexCluster(final Map descMap) {
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


// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.1.0', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 39
  * ver. 3.1.0  2024-04-24 kkossev  - (dev. branch) unnecesery unschedule() speed optimization. // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 43
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '3.1.0' } // library marker kkossev.commonLib, line 50
String commonLibStamp() { '2024/04/24 11:50 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 63

metadata { // library marker kkossev.commonLib, line 65
        if (_DEBUG) { // library marker kkossev.commonLib, line 66
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 67
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 69
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 70
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 72
            ] // library marker kkossev.commonLib, line 73
        } // library marker kkossev.commonLib, line 74

        // common capabilities for all device types // library marker kkossev.commonLib, line 76
        capability 'Configuration' // library marker kkossev.commonLib, line 77
        capability 'Refresh' // library marker kkossev.commonLib, line 78
        capability 'Health Check' // library marker kkossev.commonLib, line 79

        // common attributes for all device types // library marker kkossev.commonLib, line 81
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 82
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 83
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 84

        // common commands for all device types // library marker kkossev.commonLib, line 86
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 87

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 89
            capability 'Switch' // library marker kkossev.commonLib, line 90
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 91
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 92
            } // library marker kkossev.commonLib, line 93
        } // library marker kkossev.commonLib, line 94

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 96
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 97

    preferences { // library marker kkossev.commonLib, line 99
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 100
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 101
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 102

        if (device) { // library marker kkossev.commonLib, line 104
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 105
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 106
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 107
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 108
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 109
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 110
                } // library marker kkossev.commonLib, line 111
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 112
            } // library marker kkossev.commonLib, line 113
        } // library marker kkossev.commonLib, line 114
    } // library marker kkossev.commonLib, line 115
} // library marker kkossev.commonLib, line 116

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 118
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 119
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 120
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 121
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 122
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 123
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 124
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 125
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 126
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 127
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 128

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 130
    defaultValue: 1, // library marker kkossev.commonLib, line 131
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 134
    defaultValue: 240, // library marker kkossev.commonLib, line 135
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 138
    defaultValue: 0, // library marker kkossev.commonLib, line 139
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 140
] // library marker kkossev.commonLib, line 141

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 143
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 144
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 145
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 146
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 147
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 148
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 149
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 150
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 151
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 152
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 153
] // library marker kkossev.commonLib, line 154

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 156
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 157
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 158
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 159
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 160
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 161
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 162
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 163
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 164

/** // library marker kkossev.commonLib, line 166
 * Parse Zigbee message // library marker kkossev.commonLib, line 167
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 168
 */ // library marker kkossev.commonLib, line 169
void parse(final String description) { // library marker kkossev.commonLib, line 170
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 171
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 172
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 173
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 174

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 176
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 177
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 178
            parseIasMessage(description) // library marker kkossev.commonLib, line 179
        } // library marker kkossev.commonLib, line 180
        else { // library marker kkossev.commonLib, line 181
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 182
        } // library marker kkossev.commonLib, line 183
        return // library marker kkossev.commonLib, line 184
    } // library marker kkossev.commonLib, line 185
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 186
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 187
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 188
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 189
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 190
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 191
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 196
        return // library marker kkossev.commonLib, line 197
    } // library marker kkossev.commonLib, line 198
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 199

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" }   // library marker kkossev.commonLib, line 201
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 202

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 204
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 205
        return // library marker kkossev.commonLib, line 206
    } // library marker kkossev.commonLib, line 207
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 208
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 209
        return // library marker kkossev.commonLib, line 210
    } // library marker kkossev.commonLib, line 211
    // // library marker kkossev.commonLib, line 212
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 213
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 214
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 215

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 217
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 218
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 219
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 220
            break // library marker kkossev.commonLib, line 221
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 222
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 223
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 224
            break // library marker kkossev.commonLib, line 225
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 226
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 227
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 228
            break // library marker kkossev.commonLib, line 229
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 230
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 231
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 232
            break // library marker kkossev.commonLib, line 233
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 234
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 235
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 236
            break // library marker kkossev.commonLib, line 237
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 238
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 239
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 240
            break // library marker kkossev.commonLib, line 241
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 242
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 243
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 244
            break // library marker kkossev.commonLib, line 245
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 246
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 247
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 248
            break // library marker kkossev.commonLib, line 249
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 250
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 253
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 254
            break // library marker kkossev.commonLib, line 255
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 256
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 257
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 258
            break // library marker kkossev.commonLib, line 259
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 260
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 261
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 262
            break // library marker kkossev.commonLib, line 263
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 264
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 265
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 266
            break // library marker kkossev.commonLib, line 267
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 268
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 269
            break // library marker kkossev.commonLib, line 270
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 271
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 272
            break // library marker kkossev.commonLib, line 273
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06 // library marker kkossev.commonLib, line 274
            parseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 275
            break // library marker kkossev.commonLib, line 276
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 277
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 278
            break // library marker kkossev.commonLib, line 279
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 280
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 281
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 284
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0xE002 : // library marker kkossev.commonLib, line 288
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 292
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 296
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0xFC11 :                                       // Sonoff // library marker kkossev.commonLib, line 300
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 304
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 307
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        default: // library marker kkossev.commonLib, line 311
            if (settings.logEnable) { // library marker kkossev.commonLib, line 312
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 313
            } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
    } // library marker kkossev.commonLib, line 316
} // library marker kkossev.commonLib, line 317

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 319
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 320
} // library marker kkossev.commonLib, line 321

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 323
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 324
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 325
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 326
    } // library marker kkossev.commonLib, line 327
    return false // library marker kkossev.commonLib, line 328
} // library marker kkossev.commonLib, line 329

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 331
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 332
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 333
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 334
    } // library marker kkossev.commonLib, line 335
    return false // library marker kkossev.commonLib, line 336
} // library marker kkossev.commonLib, line 337

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 339
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 340
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 341
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 342
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 343
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 344
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 345
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 346
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 347
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 348
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 349
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 350
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 351
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 352
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 353
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 354
] // library marker kkossev.commonLib, line 355

/** // library marker kkossev.commonLib, line 357
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 358
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 359
 */ // library marker kkossev.commonLib, line 360
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 361
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 362
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 363
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 364
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 365
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 366
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 367
    switch (clusterId) { // library marker kkossev.commonLib, line 368
        case 0x0005 : // library marker kkossev.commonLib, line 369
            if (state.stats == null) { state.stats = [:] } ; state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 370
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        case 0x0006 : // library marker kkossev.commonLib, line 373
            if (state.stats == null) { state.stats = [:] } ; state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 374
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 375
            break // library marker kkossev.commonLib, line 376
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 377
            if (state.stats == null) { state.stats = [:] } ; state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 378
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 379
            break // library marker kkossev.commonLib, line 380
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 381
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 382
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 383
            break // library marker kkossev.commonLib, line 384
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 385
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 386
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 387
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 388
            break // library marker kkossev.commonLib, line 389
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 390
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 391
            break // library marker kkossev.commonLib, line 392
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 393
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 394
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 395
            break // library marker kkossev.commonLib, line 396
        default : // library marker kkossev.commonLib, line 397
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 398
            break // library marker kkossev.commonLib, line 399
    } // library marker kkossev.commonLib, line 400
} // library marker kkossev.commonLib, line 401

/** // library marker kkossev.commonLib, line 403
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 404
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 405
 */ // library marker kkossev.commonLib, line 406
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 407
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 408
    switch (commandId) { // library marker kkossev.commonLib, line 409
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 410
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 411
            break // library marker kkossev.commonLib, line 412
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 413
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 414
            break // library marker kkossev.commonLib, line 415
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 416
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 417
            break // library marker kkossev.commonLib, line 418
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 419
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 420
            break // library marker kkossev.commonLib, line 421
        case 0x0B: // default command response // library marker kkossev.commonLib, line 422
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 423
            break // library marker kkossev.commonLib, line 424
        default: // library marker kkossev.commonLib, line 425
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 426
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 427
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 428
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 429
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 430
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 431
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 432
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 433
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 434
            } // library marker kkossev.commonLib, line 435
            break // library marker kkossev.commonLib, line 436
    } // library marker kkossev.commonLib, line 437
} // library marker kkossev.commonLib, line 438

/** // library marker kkossev.commonLib, line 440
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 441
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 442
 */ // library marker kkossev.commonLib, line 443
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 444
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 445
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 446
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 447
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 448
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 449
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 450
    } // library marker kkossev.commonLib, line 451
    else { // library marker kkossev.commonLib, line 452
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 453
    } // library marker kkossev.commonLib, line 454
} // library marker kkossev.commonLib, line 455

/** // library marker kkossev.commonLib, line 457
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 458
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 459
 */ // library marker kkossev.commonLib, line 460
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 461
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 462
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 463
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 464
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 465
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 466
    } // library marker kkossev.commonLib, line 467
    else { // library marker kkossev.commonLib, line 468
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 469
    } // library marker kkossev.commonLib, line 470
} // library marker kkossev.commonLib, line 471

/** // library marker kkossev.commonLib, line 473
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 474
 */ // library marker kkossev.commonLib, line 475
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 476
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 477
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 478
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 479
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 480
        state.reportingEnabled = true // library marker kkossev.commonLib, line 481
    } // library marker kkossev.commonLib, line 482
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 483
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 484
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 485
    } else { // library marker kkossev.commonLib, line 486
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 487
    } // library marker kkossev.commonLib, line 488
} // library marker kkossev.commonLib, line 489

/** // library marker kkossev.commonLib, line 491
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 492
 */ // library marker kkossev.commonLib, line 493
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 494
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 495
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 496
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 497
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 498
    if (status == 0) { // library marker kkossev.commonLib, line 499
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 500
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 501
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 502
        int delta = 0 // library marker kkossev.commonLib, line 503
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 504
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 505
        } // library marker kkossev.commonLib, line 506
        else { // library marker kkossev.commonLib, line 507
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 508
        } // library marker kkossev.commonLib, line 509
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
    else { // library marker kkossev.commonLib, line 512
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 513
    } // library marker kkossev.commonLib, line 514
} // library marker kkossev.commonLib, line 515

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 517
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 518
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 519
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 520
        return false // library marker kkossev.commonLib, line 521
    } // library marker kkossev.commonLib, line 522
    // execute the customHandler function // library marker kkossev.commonLib, line 523
    boolean result = false // library marker kkossev.commonLib, line 524
    try { // library marker kkossev.commonLib, line 525
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 526
    } // library marker kkossev.commonLib, line 527
    catch (e) { // library marker kkossev.commonLib, line 528
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 529
        return false // library marker kkossev.commonLib, line 530
    } // library marker kkossev.commonLib, line 531
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 532
    return result // library marker kkossev.commonLib, line 533
} // library marker kkossev.commonLib, line 534

/** // library marker kkossev.commonLib, line 536
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 537
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 538
 */ // library marker kkossev.commonLib, line 539
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 540
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 541
    final String commandId = data[0] // library marker kkossev.commonLib, line 542
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 543
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 544
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 545
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 546
    } else { // library marker kkossev.commonLib, line 547
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 548
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 549
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 550
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 551
        } // library marker kkossev.commonLib, line 552
    } // library marker kkossev.commonLib, line 553
} // library marker kkossev.commonLib, line 554

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 556
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 557
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 558
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 559

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 561
    0x00: 'Success', // library marker kkossev.commonLib, line 562
    0x01: 'Failure', // library marker kkossev.commonLib, line 563
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 564
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 565
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 566
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 567
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 568
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 569
    0x88: 'Read Only', // library marker kkossev.commonLib, line 570
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 571
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 572
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 573
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 574
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 575
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 576
    0x94: 'Time out', // library marker kkossev.commonLib, line 577
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 578
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 579
] // library marker kkossev.commonLib, line 580

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 582
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 583
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 584
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 585
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 586
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 587
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 588
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 589
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 590
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 591
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 592
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 593
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 594
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 595
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 596
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 597
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 598
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 599
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 600
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 601
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 602
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 603
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 604
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 605
] // library marker kkossev.commonLib, line 606

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 608
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 609
} // library marker kkossev.commonLib, line 610

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 612
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 613
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 614
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 615
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 616
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 617
    return avg // library marker kkossev.commonLib, line 618
} // library marker kkossev.commonLib, line 619

/* // library marker kkossev.commonLib, line 621
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 622
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 623
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 624
*/ // library marker kkossev.commonLib, line 625
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 626

/** // library marker kkossev.commonLib, line 628
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 629
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 630
 */ // library marker kkossev.commonLib, line 631
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 632
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 633
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 634
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 635
        case 0x0000: // library marker kkossev.commonLib, line 636
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 637
            break // library marker kkossev.commonLib, line 638
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 639
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 640
            if (isPing) { // library marker kkossev.commonLib, line 641
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 642
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 643
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 644
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 645
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 646
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 647
                    sendRttEvent() // library marker kkossev.commonLib, line 648
                } // library marker kkossev.commonLib, line 649
                else { // library marker kkossev.commonLib, line 650
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 651
                } // library marker kkossev.commonLib, line 652
                state.states['isPing'] = false // library marker kkossev.commonLib, line 653
            } // library marker kkossev.commonLib, line 654
            else { // library marker kkossev.commonLib, line 655
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 656
            } // library marker kkossev.commonLib, line 657
            break // library marker kkossev.commonLib, line 658
        case 0x0004: // library marker kkossev.commonLib, line 659
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 660
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 661
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 662
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 663
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 664
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 665
            } // library marker kkossev.commonLib, line 666
            break // library marker kkossev.commonLib, line 667
        case 0x0005: // library marker kkossev.commonLib, line 668
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 669
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 670
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 671
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 672
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 673
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 674
            } // library marker kkossev.commonLib, line 675
            break // library marker kkossev.commonLib, line 676
        case 0x0007: // library marker kkossev.commonLib, line 677
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 678
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 679
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 680
            break // library marker kkossev.commonLib, line 681
        case 0xFFDF: // library marker kkossev.commonLib, line 682
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case 0xFFE2: // library marker kkossev.commonLib, line 685
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 688
            logDebug "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 689
            break // library marker kkossev.commonLib, line 690
        case 0xFFFE: // library marker kkossev.commonLib, line 691
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 692
            break // library marker kkossev.commonLib, line 693
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 694
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 695
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 696
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 697
            break // library marker kkossev.commonLib, line 698
        default: // library marker kkossev.commonLib, line 699
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 700
            break // library marker kkossev.commonLib, line 701
    } // library marker kkossev.commonLib, line 702
} // library marker kkossev.commonLib, line 703

// power cluster            0x0001 // library marker kkossev.commonLib, line 705
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 706
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 707
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 708
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 709
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 710
    } // library marker kkossev.commonLib, line 711
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 712
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 713
    } // library marker kkossev.commonLib, line 714
    else { // library marker kkossev.commonLib, line 715
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
} // library marker kkossev.commonLib, line 718

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 720
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 721

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 723
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 724
} // library marker kkossev.commonLib, line 725

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 727
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 728
} // library marker kkossev.commonLib, line 729

/* // library marker kkossev.commonLib, line 731
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 732
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 733
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 734
*/ // library marker kkossev.commonLib, line 735

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 737
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 738
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 739
    } // library marker kkossev.commonLib, line 740
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 741
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 742
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 743
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 744
    } // library marker kkossev.commonLib, line 745
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 746
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 747
    } // library marker kkossev.commonLib, line 748
    else { // library marker kkossev.commonLib, line 749
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 750
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 751
    } // library marker kkossev.commonLib, line 752
} // library marker kkossev.commonLib, line 753

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 755
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 756
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 757

void toggle() { // library marker kkossev.commonLib, line 759
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 760
    String state = '' // library marker kkossev.commonLib, line 761
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 762
        state = 'on' // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764
    else { // library marker kkossev.commonLib, line 765
        state = 'off' // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    descriptionText += state // library marker kkossev.commonLib, line 768
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 769
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 770
} // library marker kkossev.commonLib, line 771

void off() { // library marker kkossev.commonLib, line 773
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 774
        customOff() // library marker kkossev.commonLib, line 775
        return // library marker kkossev.commonLib, line 776
    } // library marker kkossev.commonLib, line 777
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 778
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 779
        return // library marker kkossev.commonLib, line 780
    } // library marker kkossev.commonLib, line 781
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 782
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 783
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 784
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 785
        if (currentState == 'off') { // library marker kkossev.commonLib, line 786
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 787
        } // library marker kkossev.commonLib, line 788
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 789
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 790
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 791
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 792
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    /* // library marker kkossev.commonLib, line 795
    else { // library marker kkossev.commonLib, line 796
        if (currentState != 'off') { // library marker kkossev.commonLib, line 797
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 798
        } // library marker kkossev.commonLib, line 799
        else { // library marker kkossev.commonLib, line 800
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 801
            return // library marker kkossev.commonLib, line 802
        } // library marker kkossev.commonLib, line 803
    } // library marker kkossev.commonLib, line 804
    */ // library marker kkossev.commonLib, line 805

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 807
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 808
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 809
} // library marker kkossev.commonLib, line 810

void on() { // library marker kkossev.commonLib, line 812
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 813
        customOn() // library marker kkossev.commonLib, line 814
        return // library marker kkossev.commonLib, line 815
    } // library marker kkossev.commonLib, line 816
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 817
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 818
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 819
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 820
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 821
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 822
        } // library marker kkossev.commonLib, line 823
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 824
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 825
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 826
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 827
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 828
    } // library marker kkossev.commonLib, line 829
    /* // library marker kkossev.commonLib, line 830
    else { // library marker kkossev.commonLib, line 831
        if (currentState != 'on') { // library marker kkossev.commonLib, line 832
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 833
        } // library marker kkossev.commonLib, line 834
        else { // library marker kkossev.commonLib, line 835
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 836
            return // library marker kkossev.commonLib, line 837
        } // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
    */ // library marker kkossev.commonLib, line 840
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 841
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 842
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 843
} // library marker kkossev.commonLib, line 844

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 846
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 847
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 848
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 849
    } // library marker kkossev.commonLib, line 850
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 851
    Map map = [:] // library marker kkossev.commonLib, line 852
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 853
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 854
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 855
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 856
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 857
        return // library marker kkossev.commonLib, line 858
    } // library marker kkossev.commonLib, line 859
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 860
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 861
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 862
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 863
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 864
        state.states['debounce'] = true // library marker kkossev.commonLib, line 865
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 866
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 867
    } else { // library marker kkossev.commonLib, line 868
        state.states['debounce'] = true // library marker kkossev.commonLib, line 869
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 870
    } // library marker kkossev.commonLib, line 871
    map.name = 'switch' // library marker kkossev.commonLib, line 872
    map.value = value // library marker kkossev.commonLib, line 873
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 874
    if (isRefresh) { // library marker kkossev.commonLib, line 875
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 876
        map.isStateChange = true // library marker kkossev.commonLib, line 877
    } else { // library marker kkossev.commonLib, line 878
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 881
    sendEvent(map) // library marker kkossev.commonLib, line 882
    clearIsDigital() // library marker kkossev.commonLib, line 883
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 884
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 885
    } // library marker kkossev.commonLib, line 886
} // library marker kkossev.commonLib, line 887

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 889
    '0': 'switch off', // library marker kkossev.commonLib, line 890
    '1': 'switch on', // library marker kkossev.commonLib, line 891
    '2': 'switch last state' // library marker kkossev.commonLib, line 892
] // library marker kkossev.commonLib, line 893

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 895
    '0': 'toggle', // library marker kkossev.commonLib, line 896
    '1': 'state', // library marker kkossev.commonLib, line 897
    '2': 'momentary' // library marker kkossev.commonLib, line 898
] // library marker kkossev.commonLib, line 899

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 901
    Map descMap = [:] // library marker kkossev.commonLib, line 902
    try { // library marker kkossev.commonLib, line 903
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 904
    } // library marker kkossev.commonLib, line 905
    catch (e1) { // library marker kkossev.commonLib, line 906
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 907
        // try alternative custom parsing // library marker kkossev.commonLib, line 908
        descMap = [:] // library marker kkossev.commonLib, line 909
        try { // library marker kkossev.commonLib, line 910
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 911
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 912
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 913
            } // library marker kkossev.commonLib, line 914
        } // library marker kkossev.commonLib, line 915
        catch (e2) { // library marker kkossev.commonLib, line 916
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 917
            return [:] // library marker kkossev.commonLib, line 918
        } // library marker kkossev.commonLib, line 919
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    return descMap // library marker kkossev.commonLib, line 922
} // library marker kkossev.commonLib, line 923

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 925
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 926
        return false // library marker kkossev.commonLib, line 927
    } // library marker kkossev.commonLib, line 928
    // try to parse ... // library marker kkossev.commonLib, line 929
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 930
    Map descMap = [:] // library marker kkossev.commonLib, line 931
    try { // library marker kkossev.commonLib, line 932
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 933
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 934
    } // library marker kkossev.commonLib, line 935
    catch (e) { // library marker kkossev.commonLib, line 936
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 937
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 938
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 939
        return true // library marker kkossev.commonLib, line 940
    } // library marker kkossev.commonLib, line 941

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 943
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 944
    } // library marker kkossev.commonLib, line 945
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 946
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 947
    } // library marker kkossev.commonLib, line 948
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 949
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 950
    } // library marker kkossev.commonLib, line 951
    else { // library marker kkossev.commonLib, line 952
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 953
        return false // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
    return true    // processed // library marker kkossev.commonLib, line 956
} // library marker kkossev.commonLib, line 957

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 959
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 960
  /* // library marker kkossev.commonLib, line 961
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 962
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 963
        return true // library marker kkossev.commonLib, line 964
    } // library marker kkossev.commonLib, line 965
*/ // library marker kkossev.commonLib, line 966
    Map descMap = [:] // library marker kkossev.commonLib, line 967
    try { // library marker kkossev.commonLib, line 968
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
    catch (e1) { // library marker kkossev.commonLib, line 971
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 972
        // try alternative custom parsing // library marker kkossev.commonLib, line 973
        descMap = [:] // library marker kkossev.commonLib, line 974
        try { // library marker kkossev.commonLib, line 975
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 976
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 977
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 978
            } // library marker kkossev.commonLib, line 979
        } // library marker kkossev.commonLib, line 980
        catch (e2) { // library marker kkossev.commonLib, line 981
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 982
            return true // library marker kkossev.commonLib, line 983
        } // library marker kkossev.commonLib, line 984
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 987
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 988
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 989
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 990
        return false // library marker kkossev.commonLib, line 991
    } // library marker kkossev.commonLib, line 992
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 993
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 994
    // attribute report received // library marker kkossev.commonLib, line 995
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 996
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 997
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 998
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 999
    } // library marker kkossev.commonLib, line 1000
    attrData.each { // library marker kkossev.commonLib, line 1001
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1002
        //def map = [:] // library marker kkossev.commonLib, line 1003
        if (it.status == '86') { // library marker kkossev.commonLib, line 1004
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1005
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1006
        } // library marker kkossev.commonLib, line 1007
        switch (it.cluster) { // library marker kkossev.commonLib, line 1008
            case '0000' : // library marker kkossev.commonLib, line 1009
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1010
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1011
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1012
                } // library marker kkossev.commonLib, line 1013
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1014
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1015
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1016
                } // library marker kkossev.commonLib, line 1017
                else { // library marker kkossev.commonLib, line 1018
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1019
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1020
                } // library marker kkossev.commonLib, line 1021
                break // library marker kkossev.commonLib, line 1022
            default : // library marker kkossev.commonLib, line 1023
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1024
                break // library marker kkossev.commonLib, line 1025
        } // switch // library marker kkossev.commonLib, line 1026
    } // for each attribute // library marker kkossev.commonLib, line 1027
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1028
} // library marker kkossev.commonLib, line 1029

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1031

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1033
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1034
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1035
    def mode // library marker kkossev.commonLib, line 1036
    String attrName // library marker kkossev.commonLib, line 1037
    if (it.value == null) { // library marker kkossev.commonLib, line 1038
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1039
        return // library marker kkossev.commonLib, line 1040
    } // library marker kkossev.commonLib, line 1041
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1042
    switch (it.attrId) { // library marker kkossev.commonLib, line 1043
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1044
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1045
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1046
            break // library marker kkossev.commonLib, line 1047
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1048
            attrName = 'On Time' // library marker kkossev.commonLib, line 1049
            mode = value // library marker kkossev.commonLib, line 1050
            break // library marker kkossev.commonLib, line 1051
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1052
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1053
            mode = value // library marker kkossev.commonLib, line 1054
            break // library marker kkossev.commonLib, line 1055
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1056
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1057
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1058
            break // library marker kkossev.commonLib, line 1059
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1060
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1061
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1062
            break // library marker kkossev.commonLib, line 1063
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1064
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1065
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1066
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1067
            } // library marker kkossev.commonLib, line 1068
            else { // library marker kkossev.commonLib, line 1069
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1070
            } // library marker kkossev.commonLib, line 1071
            break // library marker kkossev.commonLib, line 1072
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1073
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1074
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1075
            break // library marker kkossev.commonLib, line 1076
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1077
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1078
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1079
            break // library marker kkossev.commonLib, line 1080
        default : // library marker kkossev.commonLib, line 1081
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1082
            return // library marker kkossev.commonLib, line 1083
    } // library marker kkossev.commonLib, line 1084
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1085
} // library marker kkossev.commonLib, line 1086

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1088
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1089
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1092
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
    else { // library marker kkossev.commonLib, line 1095
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1096
    } // library marker kkossev.commonLib, line 1097
} // library marker kkossev.commonLib, line 1098

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1100
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1101
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1102
} // library marker kkossev.commonLib, line 1103

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1105
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1106
} // library marker kkossev.commonLib, line 1107

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1109
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1110
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1111
    } // library marker kkossev.commonLib, line 1112
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1113
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1114
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1115
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1116
    } // library marker kkossev.commonLib, line 1117
    else { // library marker kkossev.commonLib, line 1118
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1123
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1124
} // library marker kkossev.commonLib, line 1125

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1127
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1128
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1129
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
    else { // library marker kkossev.commonLib, line 1132
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
} // library marker kkossev.commonLib, line 1135

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1137
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1138
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1139
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1140
    } // library marker kkossev.commonLib, line 1141
    else { // library marker kkossev.commonLib, line 1142
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
} // library marker kkossev.commonLib, line 1145

// Occupancy Sensing Cluster 0x0406 // library marker kkossev.commonLib, line 1147
void parseOccupancyCluster(final Map descMap) { // library marker kkossev.commonLib, line 1148
    if (this.respondsTo('customParseOccupancyCluster')) { // library marker kkossev.commonLib, line 1149
        customParseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 1150
    } // library marker kkossev.commonLib, line 1151
    else { // library marker kkossev.commonLib, line 1152
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1153
    } // library marker kkossev.commonLib, line 1154
} // library marker kkossev.commonLib, line 1155

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1157
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1158
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1159
} // library marker kkossev.commonLib, line 1160

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1162
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1163
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1164
} // library marker kkossev.commonLib, line 1165

// pm2.5 // library marker kkossev.commonLib, line 1167
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1168
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1169
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1170
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1171
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1172
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1173
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1174
    } // library marker kkossev.commonLib, line 1175
    else { // library marker kkossev.commonLib, line 1176
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178
} // library marker kkossev.commonLib, line 1179

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1181
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1182
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1183
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1186
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1189
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    else { // library marker kkossev.commonLib, line 1192
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
} // library marker kkossev.commonLib, line 1195

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1197
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1198
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1199
} // library marker kkossev.commonLib, line 1200

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1202
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1203
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1204
} // library marker kkossev.commonLib, line 1205

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1207
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1208
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1209
} // library marker kkossev.commonLib, line 1210

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1212
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1213
} // library marker kkossev.commonLib, line 1214

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1216
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1220
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1221
} // library marker kkossev.commonLib, line 1222

/* // library marker kkossev.commonLib, line 1224
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1225
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1226
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1227
*/ // library marker kkossev.commonLib, line 1228
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1229
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1230
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1231

// Tuya Commands // library marker kkossev.commonLib, line 1233
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1234
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1235
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1236
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1237
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1238

// tuya DP type // library marker kkossev.commonLib, line 1240
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1241
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1242
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1243
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1244
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1245
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1246

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1248
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1249
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1250
        Long offset = 0 // library marker kkossev.commonLib, line 1251
        try { // library marker kkossev.commonLib, line 1252
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1253
        } // library marker kkossev.commonLib, line 1254
        catch (e) { // library marker kkossev.commonLib, line 1255
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1256
        } // library marker kkossev.commonLib, line 1257
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1258
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1259
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1260
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1261
    } // library marker kkossev.commonLib, line 1262
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1263
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1264
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1265
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1266
        if (status != '00') { // library marker kkossev.commonLib, line 1267
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1268
        } // library marker kkossev.commonLib, line 1269
    } // library marker kkossev.commonLib, line 1270
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1271
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1272
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1273
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1274
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1275
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1276
            return // library marker kkossev.commonLib, line 1277
        } // library marker kkossev.commonLib, line 1278
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1279
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1280
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1281
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1282
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1283
            if (!isChattyDeviceReport(descMap)) { // library marker kkossev.commonLib, line 1284
                logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1285
            } // library marker kkossev.commonLib, line 1286
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1287
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1288
        } // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    else { // library marker kkossev.commonLib, line 1291
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1292
    } // library marker kkossev.commonLib, line 1293
} // library marker kkossev.commonLib, line 1294

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1296
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1297
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1298
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1299
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1300
            return // library marker kkossev.commonLib, line 1301
        } // library marker kkossev.commonLib, line 1302
    } // library marker kkossev.commonLib, line 1303
    // check if the method  method exists // library marker kkossev.commonLib, line 1304
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1305
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1306
            return // library marker kkossev.commonLib, line 1307
        } // library marker kkossev.commonLib, line 1308
    } // library marker kkossev.commonLib, line 1309
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1310
} // library marker kkossev.commonLib, line 1311

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1313
    int retValue = 0 // library marker kkossev.commonLib, line 1314
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1315
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1316
        int power = 1 // library marker kkossev.commonLib, line 1317
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1318
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1319
            power = power * 256 // library marker kkossev.commonLib, line 1320
        } // library marker kkossev.commonLib, line 1321
    } // library marker kkossev.commonLib, line 1322
    return retValue // library marker kkossev.commonLib, line 1323
} // library marker kkossev.commonLib, line 1324

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1326
    List<String> cmds = [] // library marker kkossev.commonLib, line 1327
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1328
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1329
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1330
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1331
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1332
    return cmds // library marker kkossev.commonLib, line 1333
} // library marker kkossev.commonLib, line 1334

private getPACKET_ID() { // library marker kkossev.commonLib, line 1336
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1337
} // library marker kkossev.commonLib, line 1338

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1340
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1341
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1342
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1343
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1344
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1348
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1349

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1351
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1352
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1353
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1354
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1355
} // library marker kkossev.commonLib, line 1356

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1358
    List<String> cmds = [] // library marker kkossev.commonLib, line 1359
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1360
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1361
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1362
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1363
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1364
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1365
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1366
        } // library marker kkossev.commonLib, line 1367
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1368
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1369
    } // library marker kkossev.commonLib, line 1370
    else { // library marker kkossev.commonLib, line 1371
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1372
    } // library marker kkossev.commonLib, line 1373
} // library marker kkossev.commonLib, line 1374

/** // library marker kkossev.commonLib, line 1376
 * initializes the device // library marker kkossev.commonLib, line 1377
 * Invoked from configure() // library marker kkossev.commonLib, line 1378
 * @return zigbee commands // library marker kkossev.commonLib, line 1379
 */ // library marker kkossev.commonLib, line 1380
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1381
    List<String> cmds = [] // library marker kkossev.commonLib, line 1382
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1383
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1384
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1385
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1386
    } // library marker kkossev.commonLib, line 1387
    return cmds // library marker kkossev.commonLib, line 1388
} // library marker kkossev.commonLib, line 1389

/** // library marker kkossev.commonLib, line 1391
 * configures the device // library marker kkossev.commonLib, line 1392
 * Invoked from configure() // library marker kkossev.commonLib, line 1393
 * @return zigbee commands // library marker kkossev.commonLib, line 1394
 */ // library marker kkossev.commonLib, line 1395
List<String> configureDevice() { // library marker kkossev.commonLib, line 1396
    List<String> cmds = [] // library marker kkossev.commonLib, line 1397
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1398

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1400
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1401
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1402
    } // library marker kkossev.commonLib, line 1403
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1404
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1405
    return cmds // library marker kkossev.commonLib, line 1406
} // library marker kkossev.commonLib, line 1407

/* // library marker kkossev.commonLib, line 1409
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1410
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1411
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1412
*/ // library marker kkossev.commonLib, line 1413

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1415
    List<String> cmds = [] // library marker kkossev.commonLib, line 1416
    if (customHandlersList != null && customHandlersList != []) { // library marker kkossev.commonLib, line 1417
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1418
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1419
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1420
                if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1421
            } // library marker kkossev.commonLib, line 1422
        } // library marker kkossev.commonLib, line 1423
    } // library marker kkossev.commonLib, line 1424
    return cmds // library marker kkossev.commonLib, line 1425
} // library marker kkossev.commonLib, line 1426

void refresh() { // library marker kkossev.commonLib, line 1428
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1429
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1430
    List<String> cmds = [] // library marker kkossev.commonLib, line 1431
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1432

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1434
    if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1435

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1437
    else { // library marker kkossev.commonLib, line 1438
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1439
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1440
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1441
        } // library marker kkossev.commonLib, line 1442
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1443
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1444
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1445
        } // library marker kkossev.commonLib, line 1446
    } // library marker kkossev.commonLib, line 1447

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1449
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1450
    } // library marker kkossev.commonLib, line 1451
    else { // library marker kkossev.commonLib, line 1452
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1453
    } // library marker kkossev.commonLib, line 1454
} // library marker kkossev.commonLib, line 1455

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1457
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1458

public void clearInfoEvent() { // library marker kkossev.commonLib, line 1460
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1461
} // library marker kkossev.commonLib, line 1462

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1464
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1465
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1466
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1467
    } // library marker kkossev.commonLib, line 1468
    else { // library marker kkossev.commonLib, line 1469
        logInfo "${info}" // library marker kkossev.commonLib, line 1470
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1471
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1472
    } // library marker kkossev.commonLib, line 1473
} // library marker kkossev.commonLib, line 1474

public void ping() { // library marker kkossev.commonLib, line 1476
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1477
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1478
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1479
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1480
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1481
    if (isVirtual()) { // library marker kkossev.commonLib, line 1482
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1483
    } // library marker kkossev.commonLib, line 1484
    else { // library marker kkossev.commonLib, line 1485
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1486
    } // library marker kkossev.commonLib, line 1487
    logDebug 'ping...' // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

def virtualPong() { // library marker kkossev.commonLib, line 1491
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1492
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1493
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1494
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1495
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1496
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1497
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1498
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1499
        sendRttEvent() // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
    else { // library marker kkossev.commonLib, line 1502
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1503
    } // library marker kkossev.commonLib, line 1504
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1505
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1506
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

/** // library marker kkossev.commonLib, line 1510
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1511
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1512
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1513
 * @return none // library marker kkossev.commonLib, line 1514
 */ // library marker kkossev.commonLib, line 1515
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1516
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1517
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1518
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1519
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1520
    if (value == null) { // library marker kkossev.commonLib, line 1521
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1522
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1523
    } // library marker kkossev.commonLib, line 1524
    else { // library marker kkossev.commonLib, line 1525
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1526
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1527
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

/** // library marker kkossev.commonLib, line 1532
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1533
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1534
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1535
 */ // library marker kkossev.commonLib, line 1536
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1537
    if (cluster != null) { // library marker kkossev.commonLib, line 1538
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1539
    } // library marker kkossev.commonLib, line 1540
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1541
    return 'NULL' // library marker kkossev.commonLib, line 1542
} // library marker kkossev.commonLib, line 1543

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1545
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1546
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1547
} // library marker kkossev.commonLib, line 1548

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1550
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(  // library marker kkossev.commonLib, line 1551
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1552
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1553
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1558
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1559
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1560
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1561
} // library marker kkossev.commonLib, line 1562

/** // library marker kkossev.commonLib, line 1564
 * Schedule a device health check // library marker kkossev.commonLib, line 1565
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1566
 */ // library marker kkossev.commonLib, line 1567
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1568
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1569
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1570
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1571
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    else { // library marker kkossev.commonLib, line 1574
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1575
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1576
    } // library marker kkossev.commonLib, line 1577
} // library marker kkossev.commonLib, line 1578

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1580
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1581
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1582
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1583
} // library marker kkossev.commonLib, line 1584

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1586

void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1588
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1589
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1590
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1591
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1592
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1593
    } // library marker kkossev.commonLib, line 1594
} // library marker kkossev.commonLib, line 1595

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1597
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1598
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1599
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1600
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1601
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1602
            logWarn 'not present!' // library marker kkossev.commonLib, line 1603
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1604
        } // library marker kkossev.commonLib, line 1605
    } // library marker kkossev.commonLib, line 1606
    else { // library marker kkossev.commonLib, line 1607
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1608
    } // library marker kkossev.commonLib, line 1609
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1610
} // library marker kkossev.commonLib, line 1611

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1613
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1614
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1615
    if (value == 'online') { // library marker kkossev.commonLib, line 1616
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1617
    } // library marker kkossev.commonLib, line 1618
    else { // library marker kkossev.commonLib, line 1619
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1620
    } // library marker kkossev.commonLib, line 1621
} // library marker kkossev.commonLib, line 1622

/** // library marker kkossev.commonLib, line 1624
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1625
 */ // library marker kkossev.commonLib, line 1626
void autoPoll() { // library marker kkossev.commonLib, line 1627
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1628
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1629
    List<String> cmds = [] // library marker kkossev.commonLib, line 1630
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1631
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1635
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1636
    } // library marker kkossev.commonLib, line 1637
} // library marker kkossev.commonLib, line 1638

/** // library marker kkossev.commonLib, line 1640
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1641
 */ // library marker kkossev.commonLib, line 1642
void updated() { // library marker kkossev.commonLib, line 1643
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1644
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1645
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1646
    unschedule() // library marker kkossev.commonLib, line 1647

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1649
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1650
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1651
    } // library marker kkossev.commonLib, line 1652
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1653
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1654
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1655
    } // library marker kkossev.commonLib, line 1656

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1658
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1659
        // schedule the periodic timer // library marker kkossev.commonLib, line 1660
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1661
        if (interval > 0) { // library marker kkossev.commonLib, line 1662
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1663
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1664
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1665
        } // library marker kkossev.commonLib, line 1666
    } // library marker kkossev.commonLib, line 1667
    else { // library marker kkossev.commonLib, line 1668
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1669
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1670
    } // library marker kkossev.commonLib, line 1671
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1672
        customUpdated() // library marker kkossev.commonLib, line 1673
    } // library marker kkossev.commonLib, line 1674

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1676
} // library marker kkossev.commonLib, line 1677

/** // library marker kkossev.commonLib, line 1679
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1680
 */ // library marker kkossev.commonLib, line 1681
void logsOff() { // library marker kkossev.commonLib, line 1682
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1683
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1684
} // library marker kkossev.commonLib, line 1685
void traceOff() { // library marker kkossev.commonLib, line 1686
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1687
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1688
} // library marker kkossev.commonLib, line 1689

void configure(String command) { // library marker kkossev.commonLib, line 1691
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1692
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1693
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1694
        return // library marker kkossev.commonLib, line 1695
    } // library marker kkossev.commonLib, line 1696
    // // library marker kkossev.commonLib, line 1697
    String func // library marker kkossev.commonLib, line 1698
    try { // library marker kkossev.commonLib, line 1699
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1700
        "$func"() // library marker kkossev.commonLib, line 1701
    } // library marker kkossev.commonLib, line 1702
    catch (e) { // library marker kkossev.commonLib, line 1703
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1704
        return // library marker kkossev.commonLib, line 1705
    } // library marker kkossev.commonLib, line 1706
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1707
} // library marker kkossev.commonLib, line 1708

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1710
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1711
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1712
} // library marker kkossev.commonLib, line 1713

void loadAllDefaults() { // library marker kkossev.commonLib, line 1715
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1716
    deleteAllSettings() // library marker kkossev.commonLib, line 1717
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1718
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1719
    deleteAllStates() // library marker kkossev.commonLib, line 1720
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1721
    initialize() // library marker kkossev.commonLib, line 1722
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1723
    updated() // library marker kkossev.commonLib, line 1724
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1725
} // library marker kkossev.commonLib, line 1726

void configureNow() { // library marker kkossev.commonLib, line 1728
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1729
} // library marker kkossev.commonLib, line 1730

/** // library marker kkossev.commonLib, line 1732
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1733
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1734
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1735
 */ // library marker kkossev.commonLib, line 1736
List<String> configure() { // library marker kkossev.commonLib, line 1737
    List<String> cmds = [] // library marker kkossev.commonLib, line 1738
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1739
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1740
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1741
    if (isTuya()) { // library marker kkossev.commonLib, line 1742
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1743
    } // library marker kkossev.commonLib, line 1744
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1745
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1746
    } // library marker kkossev.commonLib, line 1747
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1748
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1749
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1750
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1751
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1752
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1753
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1754
    //return cmds // library marker kkossev.commonLib, line 1755
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1756
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1757
    } // library marker kkossev.commonLib, line 1758
    else { // library marker kkossev.commonLib, line 1759
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1760
    } // library marker kkossev.commonLib, line 1761
} // library marker kkossev.commonLib, line 1762

/** // library marker kkossev.commonLib, line 1764
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1765
 */ // library marker kkossev.commonLib, line 1766
void installed() { // library marker kkossev.commonLib, line 1767
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1768
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1769
    // populate some default values for attributes // library marker kkossev.commonLib, line 1770
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1771
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1772
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1773
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1774
} // library marker kkossev.commonLib, line 1775

/** // library marker kkossev.commonLib, line 1777
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1778
 */ // library marker kkossev.commonLib, line 1779
void initialize() { // library marker kkossev.commonLib, line 1780
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1781
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1782
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1783
    updateTuyaVersion() // library marker kkossev.commonLib, line 1784
    updateAqaraVersion() // library marker kkossev.commonLib, line 1785
} // library marker kkossev.commonLib, line 1786

/* // library marker kkossev.commonLib, line 1788
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1789
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1790
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1791
*/ // library marker kkossev.commonLib, line 1792

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1794
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1795
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1796
} // library marker kkossev.commonLib, line 1797

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1799
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1800
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1801
} // library marker kkossev.commonLib, line 1802

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1804
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1805
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1806
} // library marker kkossev.commonLib, line 1807

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1809
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1810
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1811
        return // library marker kkossev.commonLib, line 1812
    } // library marker kkossev.commonLib, line 1813
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1814
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1815
    cmd.each { // library marker kkossev.commonLib, line 1816
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1817
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1818
    } // library marker kkossev.commonLib, line 1819
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1820
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1821
} // library marker kkossev.commonLib, line 1822

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1824

String getDeviceInfo() { // library marker kkossev.commonLib, line 1826
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1827
} // library marker kkossev.commonLib, line 1828

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1830
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1831
} // library marker kkossev.commonLib, line 1832

@CompileStatic // library marker kkossev.commonLib, line 1834
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1835
    return // library marker kkossev.commonLib, line 1836
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1837
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1838
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1839
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1840
        initializeVars(false) // library marker kkossev.commonLib, line 1841
        updateTuyaVersion() // library marker kkossev.commonLib, line 1842
        updateAqaraVersion() // library marker kkossev.commonLib, line 1843
    } // library marker kkossev.commonLib, line 1844
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1845
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1846
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1847
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1848
} // library marker kkossev.commonLib, line 1849


// credits @thebearmay // library marker kkossev.commonLib, line 1852
String getModel() { // library marker kkossev.commonLib, line 1853
    try { // library marker kkossev.commonLib, line 1854
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1855
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1856
    } catch (ignore) { // library marker kkossev.commonLib, line 1857
        try { // library marker kkossev.commonLib, line 1858
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1859
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1860
                return model // library marker kkossev.commonLib, line 1861
            } // library marker kkossev.commonLib, line 1862
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1863
            return '' // library marker kkossev.commonLib, line 1864
        } // library marker kkossev.commonLib, line 1865
    } // library marker kkossev.commonLib, line 1866
} // library marker kkossev.commonLib, line 1867

// credits @thebearmay // library marker kkossev.commonLib, line 1869
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1870
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1871
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1872
    String revision = tokens.last() // library marker kkossev.commonLib, line 1873
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1874
} // library marker kkossev.commonLib, line 1875

/** // library marker kkossev.commonLib, line 1877
 * called from TODO // library marker kkossev.commonLib, line 1878
 */ // library marker kkossev.commonLib, line 1879

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1881
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1882
    unschedule() // library marker kkossev.commonLib, line 1883
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1884
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1885

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1887
} // library marker kkossev.commonLib, line 1888

void resetStatistics() { // library marker kkossev.commonLib, line 1890
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1891
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1892
} // library marker kkossev.commonLib, line 1893

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1895
void resetStats() { // library marker kkossev.commonLib, line 1896
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1897
    state.stats = [:] // library marker kkossev.commonLib, line 1898
    state.states = [:] // library marker kkossev.commonLib, line 1899
    state.lastRx = [:] // library marker kkossev.commonLib, line 1900
    state.lastTx = [:] // library marker kkossev.commonLib, line 1901
    state.health = [:] // library marker kkossev.commonLib, line 1902
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1903
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1904
    } // library marker kkossev.commonLib, line 1905
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1906
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1907
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1908
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1909
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1910
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1911
} // library marker kkossev.commonLib, line 1912

/** // library marker kkossev.commonLib, line 1914
 * called from TODO // library marker kkossev.commonLib, line 1915
 */ // library marker kkossev.commonLib, line 1916
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1917
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1918
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1919
        state.clear() // library marker kkossev.commonLib, line 1920
        unschedule() // library marker kkossev.commonLib, line 1921
        resetStats() // library marker kkossev.commonLib, line 1922
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1923
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1924
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1925
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1926
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1927
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1928
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1929
    } // library marker kkossev.commonLib, line 1930

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1932
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1933
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1934
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1935
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1936

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1938
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1939
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1940
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1941
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1942
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1943
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1944
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1945
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1946
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1947

    // common libraries initialization // library marker kkossev.commonLib, line 1949
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1950
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1951

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1953
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1954
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1955
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1956
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1957

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1959
    if ( mm != null) { // library marker kkossev.commonLib, line 1960
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1961
    } // library marker kkossev.commonLib, line 1962
    else { // library marker kkossev.commonLib, line 1963
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1964
    } // library marker kkossev.commonLib, line 1965
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1966
    if ( ep  != null) { // library marker kkossev.commonLib, line 1967
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1968
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1969
    } // library marker kkossev.commonLib, line 1970
    else { // library marker kkossev.commonLib, line 1971
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1972
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1973
    } // library marker kkossev.commonLib, line 1974
} // library marker kkossev.commonLib, line 1975

/** // library marker kkossev.commonLib, line 1977
 * called from TODO // library marker kkossev.commonLib, line 1978
 */ // library marker kkossev.commonLib, line 1979
void setDestinationEP() { // library marker kkossev.commonLib, line 1980
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1981
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1982
        state.destinationEP = ep // library marker kkossev.commonLib, line 1983
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1984
    } // library marker kkossev.commonLib, line 1985
    else { // library marker kkossev.commonLib, line 1986
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1987
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1988
    } // library marker kkossev.commonLib, line 1989
} // library marker kkossev.commonLib, line 1990

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1992
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1993
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1994
    } // library marker kkossev.commonLib, line 1995
} // library marker kkossev.commonLib, line 1996

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1998
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1999
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2000
    } // library marker kkossev.commonLib, line 2001
} // library marker kkossev.commonLib, line 2002

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2004
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2005
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2006
    } // library marker kkossev.commonLib, line 2007
} // library marker kkossev.commonLib, line 2008

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2010
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2011
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
} // library marker kkossev.commonLib, line 2014

// _DEBUG mode only // library marker kkossev.commonLib, line 2016
void getAllProperties() { // library marker kkossev.commonLib, line 2017
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2018
    device.properties.each { it -> // library marker kkossev.commonLib, line 2019
        log.debug it // library marker kkossev.commonLib, line 2020
    } // library marker kkossev.commonLib, line 2021
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2022
    settings.each { it -> // library marker kkossev.commonLib, line 2023
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2024
    } // library marker kkossev.commonLib, line 2025
    log.trace 'Done' // library marker kkossev.commonLib, line 2026
} // library marker kkossev.commonLib, line 2027

// delete all Preferences // library marker kkossev.commonLib, line 2029
void deleteAllSettings() { // library marker kkossev.commonLib, line 2030
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 2031
    settings.each { it -> // library marker kkossev.commonLib, line 2032
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 2033
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2034
    } // library marker kkossev.commonLib, line 2035
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 2036
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2037
} // library marker kkossev.commonLib, line 2038

// delete all attributes // library marker kkossev.commonLib, line 2040
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2041
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2042
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 2043
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2044
} // library marker kkossev.commonLib, line 2045

// delete all State Variables // library marker kkossev.commonLib, line 2047
void deleteAllStates() { // library marker kkossev.commonLib, line 2048
    String stateDeleted = '' // library marker kkossev.commonLib, line 2049
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 2050
    state.clear() // library marker kkossev.commonLib, line 2051
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2052
} // library marker kkossev.commonLib, line 2053

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2055
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2056
} // library marker kkossev.commonLib, line 2057

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2059
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2060
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2061
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2062
    } // library marker kkossev.commonLib, line 2063
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2064
} // library marker kkossev.commonLib, line 2065

void parseTest(String par) { // library marker kkossev.commonLib, line 2067
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2068
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2069
    parse(par) // library marker kkossev.commonLib, line 2070
} // library marker kkossev.commonLib, line 2071

def testJob() { // library marker kkossev.commonLib, line 2073
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2074
} // library marker kkossev.commonLib, line 2075

/** // library marker kkossev.commonLib, line 2077
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2078
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2079
 */ // library marker kkossev.commonLib, line 2080
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2081
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2082
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2083
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2084
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2085
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2086
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2087
    String cron // library marker kkossev.commonLib, line 2088
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2089
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2090
    } // library marker kkossev.commonLib, line 2091
    else { // library marker kkossev.commonLib, line 2092
        if (minutes < 60) { // library marker kkossev.commonLib, line 2093
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2094
        } // library marker kkossev.commonLib, line 2095
        else { // library marker kkossev.commonLib, line 2096
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2097
        } // library marker kkossev.commonLib, line 2098
    } // library marker kkossev.commonLib, line 2099
    return cron // library marker kkossev.commonLib, line 2100
} // library marker kkossev.commonLib, line 2101

// credits @thebearmay // library marker kkossev.commonLib, line 2103
String formatUptime() { // library marker kkossev.commonLib, line 2104
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2105
} // library marker kkossev.commonLib, line 2106

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2108
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2109
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2110
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2111
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2112
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2113
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2114
} // library marker kkossev.commonLib, line 2115

boolean isTuya() { // library marker kkossev.commonLib, line 2117
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2118
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2119
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2120
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2121
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2122
} // library marker kkossev.commonLib, line 2123

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2125
    if (!isTuya()) { // library marker kkossev.commonLib, line 2126
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2127
        return // library marker kkossev.commonLib, line 2128
    } // library marker kkossev.commonLib, line 2129
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2130
    if (application != null) { // library marker kkossev.commonLib, line 2131
        Integer ver // library marker kkossev.commonLib, line 2132
        try { // library marker kkossev.commonLib, line 2133
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2134
        } // library marker kkossev.commonLib, line 2135
        catch (e) { // library marker kkossev.commonLib, line 2136
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2137
            return // library marker kkossev.commonLib, line 2138
        } // library marker kkossev.commonLib, line 2139
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2140
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2141
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2142
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2143
        } // library marker kkossev.commonLib, line 2144
    } // library marker kkossev.commonLib, line 2145
} // library marker kkossev.commonLib, line 2146

boolean isAqara() { // library marker kkossev.commonLib, line 2148
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2149
} // library marker kkossev.commonLib, line 2150

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2152
    if (!isAqara()) { // library marker kkossev.commonLib, line 2153
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2154
        return // library marker kkossev.commonLib, line 2155
    } // library marker kkossev.commonLib, line 2156
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2157
    if (application != null) { // library marker kkossev.commonLib, line 2158
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2159
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2160
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2161
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2162
        } // library marker kkossev.commonLib, line 2163
    } // library marker kkossev.commonLib, line 2164
} // library marker kkossev.commonLib, line 2165

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2167
    try { // library marker kkossev.commonLib, line 2168
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2169
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2170
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2171
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2172
    } catch (e) { // library marker kkossev.commonLib, line 2173
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2174
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2175
    } // library marker kkossev.commonLib, line 2176
} // library marker kkossev.commonLib, line 2177

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2179
    try { // library marker kkossev.commonLib, line 2180
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2181
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2182
        return date.getTime() // library marker kkossev.commonLib, line 2183
    } catch (e) { // library marker kkossev.commonLib, line 2184
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2185
        return now() // library marker kkossev.commonLib, line 2186
    } // library marker kkossev.commonLib, line 2187
} // library marker kkossev.commonLib, line 2188
/* // library marker kkossev.commonLib, line 2189
void test(String par) { // library marker kkossev.commonLib, line 2190
    List<String> cmds = [] // library marker kkossev.commonLib, line 2191
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2192

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2194
    //parse(par) // library marker kkossev.commonLib, line 2195

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2197
} // library marker kkossev.commonLib, line 2198
*/ // library marker kkossev.commonLib, line 2199

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', // library marker kkossev.xiaomiLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.xiaomiLib, line 4
    category: 'zigbee', // library marker kkossev.xiaomiLib, line 5
    description: 'Xiaomi Library', // library marker kkossev.xiaomiLib, line 6
    name: 'xiaomiLib', // library marker kkossev.xiaomiLib, line 7
    namespace: 'kkossev', // library marker kkossev.xiaomiLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', // library marker kkossev.xiaomiLib, line 9
    version: '1.0.2', // library marker kkossev.xiaomiLib, line 10
    documentationLink: '' // library marker kkossev.xiaomiLib, line 11
) // library marker kkossev.xiaomiLib, line 12
/* // library marker kkossev.xiaomiLib, line 13
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 14
 * // library marker kkossev.xiaomiLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 17
 * // library marker kkossev.xiaomiLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 19
 * // library marker kkossev.xiaomiLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 25
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 26
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/04/06 12:14 PM' } // library marker kkossev.xiaomiLib, line 35

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 37
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 38

// no metadata for this library! // library marker kkossev.xiaomiLib, line 40

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 42

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 44
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 45
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 46
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 47
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 48
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 49
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 50
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 53
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 54
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 55
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 56
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 57
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 58
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 59

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 61
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 62
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 64
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 65
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 66
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 67

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 69
// // library marker kkossev.xiaomiLib, line 70
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 71
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 72
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 73
    } // library marker kkossev.xiaomiLib, line 74
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 75
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 76
        return // library marker kkossev.xiaomiLib, line 77
    } // library marker kkossev.xiaomiLib, line 78
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 79
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 80
        return // library marker kkossev.xiaomiLib, line 81
    } // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 83
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 91
            break // library marker kkossev.xiaomiLib, line 92
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 93
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 94
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 95
            break // library marker kkossev.xiaomiLib, line 96
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 97
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 98
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 99
            break // library marker kkossev.xiaomiLib, line 100
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 101
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 102
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 103
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 104
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 105
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
            } // library marker kkossev.xiaomiLib, line 107
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 108
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 109
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 110
            } // library marker kkossev.xiaomiLib, line 111
            break // library marker kkossev.xiaomiLib, line 112
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 113
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 114
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 115
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 116
            break // library marker kkossev.xiaomiLib, line 117
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 118
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 119
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 120
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 121
            break // library marker kkossev.xiaomiLib, line 122
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 123
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 124
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 125
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 126
            break // library marker kkossev.xiaomiLib, line 127
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 128
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 129
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
            break // library marker kkossev.xiaomiLib, line 135
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 136
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 137
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 138
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 139
                sendZigbeeCommands(customRefresh()) // library marker kkossev.xiaomiLib, line 140
            } // library marker kkossev.xiaomiLib, line 141
            break // library marker kkossev.xiaomiLib, line 142
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 143
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 144
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 145
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 146
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 147
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 148
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 149
                } // library marker kkossev.xiaomiLib, line 150
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 151
            } // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
        default: // library marker kkossev.xiaomiLib, line 154
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 160
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 161
        switch (tag) { // library marker kkossev.xiaomiLib, line 162
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x03: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x05: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x06: // library marker kkossev.xiaomiLib, line 172
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 173
                break // library marker kkossev.xiaomiLib, line 174
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 175
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 176
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 177
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 178
                break // library marker kkossev.xiaomiLib, line 179
            case 0x0a: // library marker kkossev.xiaomiLib, line 180
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 181
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 182
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 183
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 184
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 185
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 186
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 187
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 188
                } // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x0b: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 192
                break // library marker kkossev.xiaomiLib, line 193
            case 0x64: // library marker kkossev.xiaomiLib, line 194
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 195
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 196
                break // library marker kkossev.xiaomiLib, line 197
            case 0x65: // library marker kkossev.xiaomiLib, line 198
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 199
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 200
                break // library marker kkossev.xiaomiLib, line 201
            case 0x66: // library marker kkossev.xiaomiLib, line 202
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 203
                else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 204
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 205
                break // library marker kkossev.xiaomiLib, line 206
            case 0x67: // library marker kkossev.xiaomiLib, line 207
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 209
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 210
                break // library marker kkossev.xiaomiLib, line 211
            case 0x69: // library marker kkossev.xiaomiLib, line 212
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 213
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                break // library marker kkossev.xiaomiLib, line 215
            case 0x6a: // library marker kkossev.xiaomiLib, line 216
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 217
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                break // library marker kkossev.xiaomiLib, line 219
            case 0x6b: // library marker kkossev.xiaomiLib, line 220
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x95: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x96: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x97: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x98: // library marker kkossev.xiaomiLib, line 233
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 234
                break // library marker kkossev.xiaomiLib, line 235
            case 0x9b: // library marker kkossev.xiaomiLib, line 236
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 237
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 238
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 239
                } // library marker kkossev.xiaomiLib, line 240
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 241
                break // library marker kkossev.xiaomiLib, line 242
            default: // library marker kkossev.xiaomiLib, line 243
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 244
        } // library marker kkossev.xiaomiLib, line 245
    } // library marker kkossev.xiaomiLib, line 246
} // library marker kkossev.xiaomiLib, line 247

/** // library marker kkossev.xiaomiLib, line 249
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 250
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 251
 */ // library marker kkossev.xiaomiLib, line 252
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 253
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 254
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 255
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 256
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 257
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 258
    } // library marker kkossev.xiaomiLib, line 259
    return bigInt // library marker kkossev.xiaomiLib, line 260
} // library marker kkossev.xiaomiLib, line 261

/** // library marker kkossev.xiaomiLib, line 263
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 264
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 265
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 266
 */ // library marker kkossev.xiaomiLib, line 267
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 268
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 269
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 270
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 271
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 272
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 273
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 274
            Object value // library marker kkossev.xiaomiLib, line 275
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 276
                int length = stream.read() // library marker kkossev.xiaomiLib, line 277
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 278
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 279
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 280
            } else { // library marker kkossev.xiaomiLib, line 281
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 282
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 283
            } // library marker kkossev.xiaomiLib, line 284
            results[tag] = value // library marker kkossev.xiaomiLib, line 285
        } // library marker kkossev.xiaomiLib, line 286
    } // library marker kkossev.xiaomiLib, line 287
    return results // library marker kkossev.xiaomiLib, line 288
} // library marker kkossev.xiaomiLib, line 289

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 291
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 292
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 293
    return cmds // library marker kkossev.xiaomiLib, line 294
} // library marker kkossev.xiaomiLib, line 295

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 297
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 298
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 299
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 300
    return cmds // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 306
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 307
    return cmds // library marker kkossev.xiaomiLib, line 308
} // library marker kkossev.xiaomiLib, line 309

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 311
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 312
} // library marker kkossev.xiaomiLib, line 313

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 315
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 316
} // library marker kkossev.xiaomiLib, line 317

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~
