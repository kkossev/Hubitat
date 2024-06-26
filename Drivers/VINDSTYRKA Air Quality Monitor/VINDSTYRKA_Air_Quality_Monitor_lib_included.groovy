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
 *
 *                                   TODO: 
 */

static String version() { "3.0.5" }
static String timeStamp() {"2024/04/05 9:42 PM"}

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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.5', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.4  2024-04-02 kkossev  - (dev.branch) removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 36
  * ver. 3.0.5  2024-04-05 kkossev  - (dev.branch) button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 37
  * // library marker kkossev.commonLib, line 38
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 39
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 42
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 43
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 44
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 45
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 46
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 47
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 48
 * // library marker kkossev.commonLib, line 49
*/ // library marker kkossev.commonLib, line 50

String commonLibVersion() { '3.0.5' } // library marker kkossev.commonLib, line 52
String commonLibStamp() { '2024/04/05 9:46 PM' } // library marker kkossev.commonLib, line 53

import groovy.transform.Field // library marker kkossev.commonLib, line 55
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 56
import hubitat.device.Protocol // library marker kkossev.commonLib, line 57
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 58
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 59
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 60
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 61
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 62
import java.math.BigDecimal // library marker kkossev.commonLib, line 63

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 65

metadata { // library marker kkossev.commonLib, line 67
        if (_DEBUG) { // library marker kkossev.commonLib, line 68
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 69
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 70
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 71
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 72
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 73
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 74
            ] // library marker kkossev.commonLib, line 75
        } // library marker kkossev.commonLib, line 76

        // common capabilities for all device types // library marker kkossev.commonLib, line 78
        capability 'Configuration' // library marker kkossev.commonLib, line 79
        capability 'Refresh' // library marker kkossev.commonLib, line 80
        capability 'Health Check' // library marker kkossev.commonLib, line 81

        // common attributes for all device types // library marker kkossev.commonLib, line 83
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 84
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 85
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 86

        // common commands for all device types // library marker kkossev.commonLib, line 88
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 89
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 90

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 92
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 93
            if (_DEBUG) { // library marker kkossev.commonLib, line 94
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 95
            } // library marker kkossev.commonLib, line 96
        } // library marker kkossev.commonLib, line 97
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 98
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 99
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 100
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 101
            ] // library marker kkossev.commonLib, line 102
        } // library marker kkossev.commonLib, line 103
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'AqaraCube']) { // library marker kkossev.commonLib, line 104
            capability 'Sensor' // library marker kkossev.commonLib, line 105
        } // library marker kkossev.commonLib, line 106
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 107
            capability 'MotionSensor' // library marker kkossev.commonLib, line 108
        } // library marker kkossev.commonLib, line 109
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 110
            capability 'Actuator' // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 113
            capability 'Battery' // library marker kkossev.commonLib, line 114
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 115
        } // library marker kkossev.commonLib, line 116
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 117
            capability 'Switch' // library marker kkossev.commonLib, line 118
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 119
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 120
            } // library marker kkossev.commonLib, line 121
        } // library marker kkossev.commonLib, line 122
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 123
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 124
        } // library marker kkossev.commonLib, line 125
        if (deviceType in  ['AqaraCube']) { // library marker kkossev.commonLib, line 126
            capability 'PushableButton' // library marker kkossev.commonLib, line 127
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 128
            capability 'HoldableButton' // library marker kkossev.commonLib, line 129
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 130
        } // library marker kkossev.commonLib, line 131
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 132
            capability 'Momentary' // library marker kkossev.commonLib, line 133
        } // library marker kkossev.commonLib, line 134
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 135
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 136
        } // library marker kkossev.commonLib, line 137
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 138
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 139
        } // library marker kkossev.commonLib, line 140
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 141
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 142
        } // library marker kkossev.commonLib, line 143

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 145
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 146

    preferences { // library marker kkossev.commonLib, line 148
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 149
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 150
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 151

        if (device) { // library marker kkossev.commonLib, line 153
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) && !isZigUSB()) { // library marker kkossev.commonLib, line 154
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 155
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 156
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 157
                } // library marker kkossev.commonLib, line 158
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
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 172
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
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 231
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 232
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 233
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 234
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 235
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 236
boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 237

/** // library marker kkossev.commonLib, line 239
 * Parse Zigbee message // library marker kkossev.commonLib, line 240
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 241
 */ // library marker kkossev.commonLib, line 242
void parse(final String description) { // library marker kkossev.commonLib, line 243
    checkDriverVersion() // library marker kkossev.commonLib, line 244
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 245
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 246
    setHealthStatusOnline() // library marker kkossev.commonLib, line 247

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 249
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 250
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 251
            parseIasMessage(description) // library marker kkossev.commonLib, line 252
        } // library marker kkossev.commonLib, line 253
        else { // library marker kkossev.commonLib, line 254
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 255
        } // library marker kkossev.commonLib, line 256
        return // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 259
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 260
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 261
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 262
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 263
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 264
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 265
        return // library marker kkossev.commonLib, line 266
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
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 281
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 282
    // // library marker kkossev.commonLib, line 283
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 284
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 285
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 286

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 288
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 289
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 290
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 291
            break // library marker kkossev.commonLib, line 292
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 293
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 294
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 295
            break // library marker kkossev.commonLib, line 296
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 297
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 298
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 299
            break // library marker kkossev.commonLib, line 300
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 301
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 302
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 305
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 306
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 307
            break // library marker kkossev.commonLib, line 308
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 309
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 310
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 313
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 314
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 317
            if (isZigUSB()) { // library marker kkossev.commonLib, line 318
                parseZigUSBAnlogInputCluster(description) // library marker kkossev.commonLib, line 319
            } // library marker kkossev.commonLib, line 320
            else { // library marker kkossev.commonLib, line 321
                parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 322
                descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 323
            } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 326
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 329
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 330
            break // library marker kkossev.commonLib, line 331
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 332
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 333
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 336
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 337
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 338
            break // library marker kkossev.commonLib, line 339
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 340
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 341
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 344
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 347
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 350
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 351
            break // library marker kkossev.commonLib, line 352
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 353
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 354
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 357
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 358
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0xE002 : // library marker kkossev.commonLib, line 361
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 362
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 363
            break // library marker kkossev.commonLib, line 364
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 365
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 366
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 367
            break // library marker kkossev.commonLib, line 368
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 369
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 370
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 373
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 374
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 375
            break // library marker kkossev.commonLib, line 376
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 377
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 378
            break // library marker kkossev.commonLib, line 379
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 380
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 381
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 382
            break // library marker kkossev.commonLib, line 383
        default: // library marker kkossev.commonLib, line 384
            if (settings.logEnable) { // library marker kkossev.commonLib, line 385
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 386
            } // library marker kkossev.commonLib, line 387
            break // library marker kkossev.commonLib, line 388
    } // library marker kkossev.commonLib, line 389
} // library marker kkossev.commonLib, line 390

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 392
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 393
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 394
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
    return false // library marker kkossev.commonLib, line 397
} // library marker kkossev.commonLib, line 398

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 400
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 401
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 402
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
    return false // library marker kkossev.commonLib, line 405
} // library marker kkossev.commonLib, line 406

/** // library marker kkossev.commonLib, line 408
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 409
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 410
 */ // library marker kkossev.commonLib, line 411
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 412
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 413
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 414
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 415
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 416
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 417
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 418
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 419
    } // library marker kkossev.commonLib, line 420
    else { // library marker kkossev.commonLib, line 421
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 422
    } // library marker kkossev.commonLib, line 423
} // library marker kkossev.commonLib, line 424

/** // library marker kkossev.commonLib, line 426
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 427
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 428
 */ // library marker kkossev.commonLib, line 429
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 430
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 431
    switch (commandId) { // library marker kkossev.commonLib, line 432
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 433
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 434
            break // library marker kkossev.commonLib, line 435
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 436
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 437
            break // library marker kkossev.commonLib, line 438
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 439
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 440
            break // library marker kkossev.commonLib, line 441
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 442
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 443
            break // library marker kkossev.commonLib, line 444
        case 0x0B: // default command response // library marker kkossev.commonLib, line 445
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 446
            break // library marker kkossev.commonLib, line 447
        default: // library marker kkossev.commonLib, line 448
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 449
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 450
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 451
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 452
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 453
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 454
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 455
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 456
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 457
            } // library marker kkossev.commonLib, line 458
            break // library marker kkossev.commonLib, line 459
    } // library marker kkossev.commonLib, line 460
} // library marker kkossev.commonLib, line 461

/** // library marker kkossev.commonLib, line 463
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 464
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 465
 */ // library marker kkossev.commonLib, line 466
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 467
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 468
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 469
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 470
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 471
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 472
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 473
    } // library marker kkossev.commonLib, line 474
    else { // library marker kkossev.commonLib, line 475
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 476
    } // library marker kkossev.commonLib, line 477
} // library marker kkossev.commonLib, line 478

/** // library marker kkossev.commonLib, line 480
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 481
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 482
 */ // library marker kkossev.commonLib, line 483
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 484
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 485
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 486
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 487
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 488
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 489
    } // library marker kkossev.commonLib, line 490
    else { // library marker kkossev.commonLib, line 491
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 492
    } // library marker kkossev.commonLib, line 493
} // library marker kkossev.commonLib, line 494

/** // library marker kkossev.commonLib, line 496
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 497
 */ // library marker kkossev.commonLib, line 498
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 499
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 500
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 501
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 502
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 503
        state.reportingEnabled = true // library marker kkossev.commonLib, line 504
    } // library marker kkossev.commonLib, line 505
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 506
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 507
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 508
    } else { // library marker kkossev.commonLib, line 509
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 510
    } // library marker kkossev.commonLib, line 511
} // library marker kkossev.commonLib, line 512

/** // library marker kkossev.commonLib, line 514
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 515
 */ // library marker kkossev.commonLib, line 516
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 517
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 518
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 519
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 520
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 521
    if (status == 0) { // library marker kkossev.commonLib, line 522
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 523
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 524
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 525
        int delta = 0 // library marker kkossev.commonLib, line 526
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 527
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 528
        } // library marker kkossev.commonLib, line 529
        else { // library marker kkossev.commonLib, line 530
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 531
        } // library marker kkossev.commonLib, line 532
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 533
    } // library marker kkossev.commonLib, line 534
    else { // library marker kkossev.commonLib, line 535
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 536
    } // library marker kkossev.commonLib, line 537
} // library marker kkossev.commonLib, line 538

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 540
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 541
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 542
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 543
        return false // library marker kkossev.commonLib, line 544
    } // library marker kkossev.commonLib, line 545
    // execute the customHandler function // library marker kkossev.commonLib, line 546
    boolean result = false // library marker kkossev.commonLib, line 547
    try { // library marker kkossev.commonLib, line 548
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 549
    } // library marker kkossev.commonLib, line 550
    catch (e) { // library marker kkossev.commonLib, line 551
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 552
        return false // library marker kkossev.commonLib, line 553
    } // library marker kkossev.commonLib, line 554
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 555
    return result // library marker kkossev.commonLib, line 556
} // library marker kkossev.commonLib, line 557

/** // library marker kkossev.commonLib, line 559
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 560
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 561
 */ // library marker kkossev.commonLib, line 562
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 563
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 564
    final String commandId = data[0] // library marker kkossev.commonLib, line 565
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 566
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 567
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 568
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 569
    } else { // library marker kkossev.commonLib, line 570
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 571
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 572
        if (isZigUSB()) { // library marker kkossev.commonLib, line 573
            executeCustomHandler('customParseDefaultCommandResponse', descMap) // library marker kkossev.commonLib, line 574
        } // library marker kkossev.commonLib, line 575
    } // library marker kkossev.commonLib, line 576
} // library marker kkossev.commonLib, line 577

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 579
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 580
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 581
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 582
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 583
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 584
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 585
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 586
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 587
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 588
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 589
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 590
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 591
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 592
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 593
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 594

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 596
    0x00: 'Success', // library marker kkossev.commonLib, line 597
    0x01: 'Failure', // library marker kkossev.commonLib, line 598
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 599
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 600
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 601
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 602
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 603
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 604
    0x88: 'Read Only', // library marker kkossev.commonLib, line 605
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 606
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 607
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 608
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 609
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 610
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 611
    0x94: 'Time out', // library marker kkossev.commonLib, line 612
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 613
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 614
] // library marker kkossev.commonLib, line 615

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 617
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 618
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 619
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 620
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 621
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 622
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 623
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 624
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 625
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 626
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 627
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 628
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 629
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 630
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 631
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 632
] // library marker kkossev.commonLib, line 633

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 635
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 636
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 637
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 638
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 639
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 640
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 641
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 642
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 643
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 644
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 645
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 646
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 647
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 648
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 649
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 650
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 651
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 652
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 653
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 654
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 655
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 656
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 657
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 658
] // library marker kkossev.commonLib, line 659

/* // library marker kkossev.commonLib, line 661
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 662
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 663
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 664
 */ // library marker kkossev.commonLib, line 665
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 666
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 667
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    else { // library marker kkossev.commonLib, line 670
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
} // library marker kkossev.commonLib, line 673

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 675
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 676
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 677
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 678
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 679
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 680
    return avg // library marker kkossev.commonLib, line 681
} // library marker kkossev.commonLib, line 682

/* // library marker kkossev.commonLib, line 684
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 685
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 686
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 687
*/ // library marker kkossev.commonLib, line 688
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 689

/** // library marker kkossev.commonLib, line 691
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 692
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 693
 */ // library marker kkossev.commonLib, line 694
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 695
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 696
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 697
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 698
        case 0x0000: // library marker kkossev.commonLib, line 699
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 700
            break // library marker kkossev.commonLib, line 701
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 702
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 703
            if (isPing) { // library marker kkossev.commonLib, line 704
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 705
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 706
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 707
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 708
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 709
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 710
                    sendRttEvent() // library marker kkossev.commonLib, line 711
                } // library marker kkossev.commonLib, line 712
                else { // library marker kkossev.commonLib, line 713
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 714
                } // library marker kkossev.commonLib, line 715
                state.states['isPing'] = false // library marker kkossev.commonLib, line 716
            } // library marker kkossev.commonLib, line 717
            else { // library marker kkossev.commonLib, line 718
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 719
            } // library marker kkossev.commonLib, line 720
            break // library marker kkossev.commonLib, line 721
        case 0x0004: // library marker kkossev.commonLib, line 722
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 723
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 724
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 725
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 726
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 727
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 728
            } // library marker kkossev.commonLib, line 729
            break // library marker kkossev.commonLib, line 730
        case 0x0005: // library marker kkossev.commonLib, line 731
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 732
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 733
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 734
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 735
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 736
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 737
            } // library marker kkossev.commonLib, line 738
            break // library marker kkossev.commonLib, line 739
        case 0x0007: // library marker kkossev.commonLib, line 740
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 741
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 742
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 743
            break // library marker kkossev.commonLib, line 744
        case 0xFFDF: // library marker kkossev.commonLib, line 745
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 746
            break // library marker kkossev.commonLib, line 747
        case 0xFFE2: // library marker kkossev.commonLib, line 748
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 749
            break // library marker kkossev.commonLib, line 750
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 751
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 752
            break // library marker kkossev.commonLib, line 753
        case 0xFFFE: // library marker kkossev.commonLib, line 754
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 755
            break // library marker kkossev.commonLib, line 756
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 757
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 758
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 759
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 760
            break // library marker kkossev.commonLib, line 761
        default: // library marker kkossev.commonLib, line 762
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 763
            break // library marker kkossev.commonLib, line 764
    } // library marker kkossev.commonLib, line 765
} // library marker kkossev.commonLib, line 766

/* // library marker kkossev.commonLib, line 768
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 769
 * power cluster            0x0001 // library marker kkossev.commonLib, line 770
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 771
*/ // library marker kkossev.commonLib, line 772
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 773
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 774
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 775
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 776
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 777
    } // library marker kkossev.commonLib, line 778

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 780
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 781
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 782
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 783
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 784
        } // library marker kkossev.commonLib, line 785
    } // library marker kkossev.commonLib, line 786
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 787
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    else { // library marker kkossev.commonLib, line 790
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
} // library marker kkossev.commonLib, line 793

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 795
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 796
    Map result = [:] // library marker kkossev.commonLib, line 797
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 798
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 799
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 800
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 801
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 802
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 803
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 804
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 805
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 806
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 807
            result.name = 'battery' // library marker kkossev.commonLib, line 808
            result.unit  = '%' // library marker kkossev.commonLib, line 809
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 810
        } // library marker kkossev.commonLib, line 811
        else { // library marker kkossev.commonLib, line 812
            result.value = volts // library marker kkossev.commonLib, line 813
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 814
            result.unit  = 'V' // library marker kkossev.commonLib, line 815
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        result.type = 'physical' // library marker kkossev.commonLib, line 818
        result.isStateChange = true // library marker kkossev.commonLib, line 819
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 820
        sendEvent(result) // library marker kkossev.commonLib, line 821
    } // library marker kkossev.commonLib, line 822
    else { // library marker kkossev.commonLib, line 823
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 824
    } // library marker kkossev.commonLib, line 825
} // library marker kkossev.commonLib, line 826

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 828
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 829
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 830
        return // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    Map map = [:] // library marker kkossev.commonLib, line 833
    map.name = 'battery' // library marker kkossev.commonLib, line 834
    map.timeStamp = now() // library marker kkossev.commonLib, line 835
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 836
    map.unit  = '%' // library marker kkossev.commonLib, line 837
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 838
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 839
    map.isStateChange = true // library marker kkossev.commonLib, line 840
    // // library marker kkossev.commonLib, line 841
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 842
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 843
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 844
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 845
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 846
        // send it now! // library marker kkossev.commonLib, line 847
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 848
    } // library marker kkossev.commonLib, line 849
    else { // library marker kkossev.commonLib, line 850
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 851
        map.delayed = delayedTime // library marker kkossev.commonLib, line 852
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 853
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 854
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 855
    } // library marker kkossev.commonLib, line 856
} // library marker kkossev.commonLib, line 857

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 859
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 860
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 861
    sendEvent(map) // library marker kkossev.commonLib, line 862
} // library marker kkossev.commonLib, line 863

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 865
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 866
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 867
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 868
    sendEvent(map) // library marker kkossev.commonLib, line 869
} // library marker kkossev.commonLib, line 870

/* // library marker kkossev.commonLib, line 872
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 873
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 874
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 875
*/ // library marker kkossev.commonLib, line 876
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 877
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 878
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 879
} // library marker kkossev.commonLib, line 880

/* // library marker kkossev.commonLib, line 882
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 883
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 884
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 885
*/ // library marker kkossev.commonLib, line 886
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 887
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 888
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 889
    } // library marker kkossev.commonLib, line 890
    else { // library marker kkossev.commonLib, line 891
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 892
    } // library marker kkossev.commonLib, line 893
} // library marker kkossev.commonLib, line 894

/* // library marker kkossev.commonLib, line 896
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 897
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 898
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 899
*/ // library marker kkossev.commonLib, line 900
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 901
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 902
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 903
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 904
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 905
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 906
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 907
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 908
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 909
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 910
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 911
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 912
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 913
            } // library marker kkossev.commonLib, line 914
            else { // library marker kkossev.commonLib, line 915
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 916
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 917
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 918
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 919
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 920
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 921
                        return // library marker kkossev.commonLib, line 922
                    } // library marker kkossev.commonLib, line 923
                } // library marker kkossev.commonLib, line 924
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 925
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 926
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 927
            } // library marker kkossev.commonLib, line 928
            break // library marker kkossev.commonLib, line 929
        case 0x01: // View group // library marker kkossev.commonLib, line 930
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 931
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 932
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 933
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 934
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 935
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 936
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 937
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 938
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 939
            } // library marker kkossev.commonLib, line 940
            else { // library marker kkossev.commonLib, line 941
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 942
            } // library marker kkossev.commonLib, line 943
            break // library marker kkossev.commonLib, line 944
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 945
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 946
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 947
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 948
            final Set<String> groups = [] // library marker kkossev.commonLib, line 949
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 950
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 951
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 952
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 953
            } // library marker kkossev.commonLib, line 954
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 955
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 956
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 957
            break // library marker kkossev.commonLib, line 958
        case 0x03: // Remove group // library marker kkossev.commonLib, line 959
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 960
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 961
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 962
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 963
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 964
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 965
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 966
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 967
            } // library marker kkossev.commonLib, line 968
            else { // library marker kkossev.commonLib, line 969
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 970
            } // library marker kkossev.commonLib, line 971
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 972
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 973
            if (index >= 0) { // library marker kkossev.commonLib, line 974
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 975
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 976
            } // library marker kkossev.commonLib, line 977
            break // library marker kkossev.commonLib, line 978
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 979
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 980
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 981
            break // library marker kkossev.commonLib, line 982
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 983
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 984
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 985
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 986
            break // library marker kkossev.commonLib, line 987
        default: // library marker kkossev.commonLib, line 988
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 989
            break // library marker kkossev.commonLib, line 990
    } // library marker kkossev.commonLib, line 991
} // library marker kkossev.commonLib, line 992

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 994
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 995
    List<String> cmds = [] // library marker kkossev.commonLib, line 996
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 997
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 998
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 999
        return [] // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1002
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1003
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1004
    return cmds // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1008
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1009
    List<String> cmds = [] // library marker kkossev.commonLib, line 1010
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1011
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1012
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1013
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1014
    return cmds // library marker kkossev.commonLib, line 1015
} // library marker kkossev.commonLib, line 1016

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1018
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1019
    List<String> cmds = [] // library marker kkossev.commonLib, line 1020
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1021
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1022
    return cmds // library marker kkossev.commonLib, line 1023
} // library marker kkossev.commonLib, line 1024

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1026
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1027
    List<String> cmds = [] // library marker kkossev.commonLib, line 1028
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1029
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1030
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1031
        return [] // library marker kkossev.commonLib, line 1032
    } // library marker kkossev.commonLib, line 1033
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1034
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1035
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1036
    return cmds // library marker kkossev.commonLib, line 1037
} // library marker kkossev.commonLib, line 1038

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1040
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1041
    List<String> cmds = [] // library marker kkossev.commonLib, line 1042
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1043
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1044
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1045
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1046
    return cmds // library marker kkossev.commonLib, line 1047
} // library marker kkossev.commonLib, line 1048

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1050
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1051
    List<String> cmds = [] // library marker kkossev.commonLib, line 1052
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1053
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1054
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1055
    return cmds // library marker kkossev.commonLib, line 1056
} // library marker kkossev.commonLib, line 1057

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1059
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1060
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1061
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1062
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1063
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1064
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1065
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1066
] // library marker kkossev.commonLib, line 1067

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1069
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1070
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1071
    List<String> cmds = [] // library marker kkossev.commonLib, line 1072
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1073
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1074
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1075
    def value // library marker kkossev.commonLib, line 1076
    Boolean validated = false // library marker kkossev.commonLib, line 1077
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1078
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1079
        return // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1082
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1083
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1084
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1085
        return // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    // // library marker kkossev.commonLib, line 1088
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1089
    def func // library marker kkossev.commonLib, line 1090
    try { // library marker kkossev.commonLib, line 1091
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1092
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1093
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1094
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1095
    } // library marker kkossev.commonLib, line 1096
    catch (e) { // library marker kkossev.commonLib, line 1097
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1098
        return // library marker kkossev.commonLib, line 1099
    } // library marker kkossev.commonLib, line 1100

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1102
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1103
} // library marker kkossev.commonLib, line 1104

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1106
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1107
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1108
} // library marker kkossev.commonLib, line 1109

/* // library marker kkossev.commonLib, line 1111
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1112
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1113
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1114
*/ // library marker kkossev.commonLib, line 1115

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1117
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1118
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1121
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1122
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1123
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1126
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
    else { // library marker kkossev.commonLib, line 1129
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
} // library marker kkossev.commonLib, line 1132

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1134
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1135
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1136

void toggle() { // library marker kkossev.commonLib, line 1138
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1139
    String state = '' // library marker kkossev.commonLib, line 1140
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1141
        state = 'on' // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    else { // library marker kkossev.commonLib, line 1144
        state = 'off' // library marker kkossev.commonLib, line 1145
    } // library marker kkossev.commonLib, line 1146
    descriptionText += state // library marker kkossev.commonLib, line 1147
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1148
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1149
} // library marker kkossev.commonLib, line 1150

void off() { // library marker kkossev.commonLib, line 1152
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1153
        customOff() // library marker kkossev.commonLib, line 1154
        return // library marker kkossev.commonLib, line 1155
    } // library marker kkossev.commonLib, line 1156
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1157
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1158
        return // library marker kkossev.commonLib, line 1159
    } // library marker kkossev.commonLib, line 1160
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1161
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1162
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1163
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1164
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1165
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1166
        } // library marker kkossev.commonLib, line 1167
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1168
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1169
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1170
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1171
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1172
    } // library marker kkossev.commonLib, line 1173
    /* // library marker kkossev.commonLib, line 1174
    else { // library marker kkossev.commonLib, line 1175
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1176
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1177
        } // library marker kkossev.commonLib, line 1178
        else { // library marker kkossev.commonLib, line 1179
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1180
            return // library marker kkossev.commonLib, line 1181
        } // library marker kkossev.commonLib, line 1182
    } // library marker kkossev.commonLib, line 1183
    */ // library marker kkossev.commonLib, line 1184

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1186
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1187
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1188
} // library marker kkossev.commonLib, line 1189

void on() { // library marker kkossev.commonLib, line 1191
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1192
        customOn() // library marker kkossev.commonLib, line 1193
        return // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1196
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1197
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1198
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1199
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1200
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1201
        } // library marker kkossev.commonLib, line 1202
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1203
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1204
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1205
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1206
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1207
    } // library marker kkossev.commonLib, line 1208
    /* // library marker kkossev.commonLib, line 1209
    else { // library marker kkossev.commonLib, line 1210
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1211
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1212
        } // library marker kkossev.commonLib, line 1213
        else { // library marker kkossev.commonLib, line 1214
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1215
            return // library marker kkossev.commonLib, line 1216
        } // library marker kkossev.commonLib, line 1217
    } // library marker kkossev.commonLib, line 1218
    */ // library marker kkossev.commonLib, line 1219
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1220
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1221
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1222
} // library marker kkossev.commonLib, line 1223

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1225
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1226
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1227
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1228
    } // library marker kkossev.commonLib, line 1229
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1230
    Map map = [:] // library marker kkossev.commonLib, line 1231
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1232
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1233
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1234
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1235
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1236
        return // library marker kkossev.commonLib, line 1237
    } // library marker kkossev.commonLib, line 1238
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1239
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1240
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1241
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1242
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1243
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1244
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1245
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1246
    } else { // library marker kkossev.commonLib, line 1247
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1248
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1249
    } // library marker kkossev.commonLib, line 1250
    map.name = 'switch' // library marker kkossev.commonLib, line 1251
    map.value = value // library marker kkossev.commonLib, line 1252
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1253
    if (isRefresh) { // library marker kkossev.commonLib, line 1254
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1255
        map.isStateChange = true // library marker kkossev.commonLib, line 1256
    } else { // library marker kkossev.commonLib, line 1257
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1258
    } // library marker kkossev.commonLib, line 1259
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1260
    sendEvent(map) // library marker kkossev.commonLib, line 1261
    clearIsDigital() // library marker kkossev.commonLib, line 1262
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1263
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
} // library marker kkossev.commonLib, line 1266

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1268
    '0': 'switch off', // library marker kkossev.commonLib, line 1269
    '1': 'switch on', // library marker kkossev.commonLib, line 1270
    '2': 'switch last state' // library marker kkossev.commonLib, line 1271
] // library marker kkossev.commonLib, line 1272

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1274
    '0': 'toggle', // library marker kkossev.commonLib, line 1275
    '1': 'state', // library marker kkossev.commonLib, line 1276
    '2': 'momentary' // library marker kkossev.commonLib, line 1277
] // library marker kkossev.commonLib, line 1278

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1280
    Map descMap = [:] // library marker kkossev.commonLib, line 1281
    try { // library marker kkossev.commonLib, line 1282
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1283
    } // library marker kkossev.commonLib, line 1284
    catch (e1) { // library marker kkossev.commonLib, line 1285
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1286
        // try alternative custom parsing // library marker kkossev.commonLib, line 1287
        descMap = [:] // library marker kkossev.commonLib, line 1288
        try { // library marker kkossev.commonLib, line 1289
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1290
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1291
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1292
            } // library marker kkossev.commonLib, line 1293
        } // library marker kkossev.commonLib, line 1294
        catch (e2) { // library marker kkossev.commonLib, line 1295
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1296
            return [:] // library marker kkossev.commonLib, line 1297
        } // library marker kkossev.commonLib, line 1298
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1299
    } // library marker kkossev.commonLib, line 1300
    return descMap // library marker kkossev.commonLib, line 1301
} // library marker kkossev.commonLib, line 1302

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1304
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1305
        return false // library marker kkossev.commonLib, line 1306
    } // library marker kkossev.commonLib, line 1307
    // try to parse ... // library marker kkossev.commonLib, line 1308
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1309
    Map descMap = [:] // library marker kkossev.commonLib, line 1310
    try { // library marker kkossev.commonLib, line 1311
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1312
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1313
    } // library marker kkossev.commonLib, line 1314
    catch (e) { // library marker kkossev.commonLib, line 1315
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1316
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1317
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1318
        return true // library marker kkossev.commonLib, line 1319
    } // library marker kkossev.commonLib, line 1320

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1322
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1323
    } // library marker kkossev.commonLib, line 1324
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1325
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1326
    } // library marker kkossev.commonLib, line 1327
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1328
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
    else { // library marker kkossev.commonLib, line 1331
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1332
        return false // library marker kkossev.commonLib, line 1333
    } // library marker kkossev.commonLib, line 1334
    return true    // processed // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1338
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1339
  /* // library marker kkossev.commonLib, line 1340
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1341
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1342
        return true // library marker kkossev.commonLib, line 1343
    } // library marker kkossev.commonLib, line 1344
*/ // library marker kkossev.commonLib, line 1345
    Map descMap = [:] // library marker kkossev.commonLib, line 1346
    try { // library marker kkossev.commonLib, line 1347
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1348
    } // library marker kkossev.commonLib, line 1349
    catch (e1) { // library marker kkossev.commonLib, line 1350
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1351
        // try alternative custom parsing // library marker kkossev.commonLib, line 1352
        descMap = [:] // library marker kkossev.commonLib, line 1353
        try { // library marker kkossev.commonLib, line 1354
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1355
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1356
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1357
            } // library marker kkossev.commonLib, line 1358
        } // library marker kkossev.commonLib, line 1359
        catch (e2) { // library marker kkossev.commonLib, line 1360
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1361
            return true // library marker kkossev.commonLib, line 1362
        } // library marker kkossev.commonLib, line 1363
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1366
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1367
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1368
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1369
        return false // library marker kkossev.commonLib, line 1370
    } // library marker kkossev.commonLib, line 1371
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1372
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1373
    // attribute report received // library marker kkossev.commonLib, line 1374
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1375
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1376
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1377
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1378
    } // library marker kkossev.commonLib, line 1379
    attrData.each { // library marker kkossev.commonLib, line 1380
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1381
        //def map = [:] // library marker kkossev.commonLib, line 1382
        if (it.status == '86') { // library marker kkossev.commonLib, line 1383
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1384
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1385
        } // library marker kkossev.commonLib, line 1386
        switch (it.cluster) { // library marker kkossev.commonLib, line 1387
            case '0000' : // library marker kkossev.commonLib, line 1388
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1389
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1390
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1391
                } // library marker kkossev.commonLib, line 1392
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1393
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1394
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1395
                } // library marker kkossev.commonLib, line 1396
                else { // library marker kkossev.commonLib, line 1397
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1398
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1399
                } // library marker kkossev.commonLib, line 1400
                break // library marker kkossev.commonLib, line 1401
            default : // library marker kkossev.commonLib, line 1402
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1403
                break // library marker kkossev.commonLib, line 1404
        } // switch // library marker kkossev.commonLib, line 1405
    } // for each attribute // library marker kkossev.commonLib, line 1406
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1407
} // library marker kkossev.commonLib, line 1408

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1410

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1412
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1413
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1414
    def mode // library marker kkossev.commonLib, line 1415
    String attrName // library marker kkossev.commonLib, line 1416
    if (it.value == null) { // library marker kkossev.commonLib, line 1417
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1418
        return // library marker kkossev.commonLib, line 1419
    } // library marker kkossev.commonLib, line 1420
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1421
    switch (it.attrId) { // library marker kkossev.commonLib, line 1422
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1423
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1424
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1425
            break // library marker kkossev.commonLib, line 1426
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1427
            attrName = 'On Time' // library marker kkossev.commonLib, line 1428
            mode = value // library marker kkossev.commonLib, line 1429
            break // library marker kkossev.commonLib, line 1430
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1431
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1432
            mode = value // library marker kkossev.commonLib, line 1433
            break // library marker kkossev.commonLib, line 1434
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1435
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1436
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1437
            break // library marker kkossev.commonLib, line 1438
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1439
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1440
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1441
            break // library marker kkossev.commonLib, line 1442
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1443
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1444
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1445
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1446
            } // library marker kkossev.commonLib, line 1447
            else { // library marker kkossev.commonLib, line 1448
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1449
            } // library marker kkossev.commonLib, line 1450
            break // library marker kkossev.commonLib, line 1451
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1452
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1453
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1454
            break // library marker kkossev.commonLib, line 1455
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1456
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1457
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1458
            break // library marker kkossev.commonLib, line 1459
        default : // library marker kkossev.commonLib, line 1460
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1461
            return // library marker kkossev.commonLib, line 1462
    } // library marker kkossev.commonLib, line 1463
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1464
} // library marker kkossev.commonLib, line 1465

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1467
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.commonLib, line 1468
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.commonLib, line 1469
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.commonLib, line 1470
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1471
        logInfo "$descriptionText" // library marker kkossev.commonLib, line 1472
        sendEvent(event) // library marker kkossev.commonLib, line 1473
    } // library marker kkossev.commonLib, line 1474
    else { // library marker kkossev.commonLib, line 1475
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.commonLib, line 1476
    } // library marker kkossev.commonLib, line 1477

} // library marker kkossev.commonLib, line 1479

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1481
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1482
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.commonLib, line 1483
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1487
    logDebug "push button $buttonNumber" // library marker kkossev.commonLib, line 1488
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.commonLib, line 1489
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1490
} // library marker kkossev.commonLib, line 1491

void doubleTap(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1493
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1494
} // library marker kkossev.commonLib, line 1495

void hold(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1497
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.commonLib, line 1498
} // library marker kkossev.commonLib, line 1499

void release(BigDecimal buttonNumber) { // library marker kkossev.commonLib, line 1501
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.commonLib, line 1502
} // library marker kkossev.commonLib, line 1503

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1505
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1506
} // library marker kkossev.commonLib, line 1507

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1509
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1510
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

/* // library marker kkossev.commonLib, line 1514
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1515
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1516
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1517
*/ // library marker kkossev.commonLib, line 1518
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1519
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1520
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1521
    } // library marker kkossev.commonLib, line 1522
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1523
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1524
    } // library marker kkossev.commonLib, line 1525
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1526
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1527
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1528
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1529
    } // library marker kkossev.commonLib, line 1530
    else { // library marker kkossev.commonLib, line 1531
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1532
    } // library marker kkossev.commonLib, line 1533
} // library marker kkossev.commonLib, line 1534

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1536
    int value = rawValue as int // library marker kkossev.commonLib, line 1537
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1538
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1539
    Map map = [:] // library marker kkossev.commonLib, line 1540

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1542
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1543

    map.name = 'level' // library marker kkossev.commonLib, line 1545
    map.value = value // library marker kkossev.commonLib, line 1546
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1547
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1548
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1549
        map.isStateChange = true // library marker kkossev.commonLib, line 1550
    } // library marker kkossev.commonLib, line 1551
    else { // library marker kkossev.commonLib, line 1552
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1553
    } // library marker kkossev.commonLib, line 1554
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1555
    sendEvent(map) // library marker kkossev.commonLib, line 1556
    clearIsDigital() // library marker kkossev.commonLib, line 1557
} // library marker kkossev.commonLib, line 1558

/** // library marker kkossev.commonLib, line 1560
 * Get the level transition rate // library marker kkossev.commonLib, line 1561
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1562
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1563
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1564
 */ // library marker kkossev.commonLib, line 1565
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1566
    int rate = 0 // library marker kkossev.commonLib, line 1567
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1568
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1569
    if (!isOn) { // library marker kkossev.commonLib, line 1570
        currentLevel = 0 // library marker kkossev.commonLib, line 1571
    } // library marker kkossev.commonLib, line 1572
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1573
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1574
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1575
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1576
    } else { // library marker kkossev.commonLib, line 1577
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1578
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1579
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1580
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1581
        } // library marker kkossev.commonLib, line 1582
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1583
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1584
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1585
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1586
        } // library marker kkossev.commonLib, line 1587
    } // library marker kkossev.commonLib, line 1588
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1589
    return rate // library marker kkossev.commonLib, line 1590
} // library marker kkossev.commonLib, line 1591

// Command option that enable changes when off // library marker kkossev.commonLib, line 1593
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1594

/** // library marker kkossev.commonLib, line 1596
 * Constrain a value to a range // library marker kkossev.commonLib, line 1597
 * @param value value to constrain // library marker kkossev.commonLib, line 1598
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1599
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1600
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1601
 */ // library marker kkossev.commonLib, line 1602
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1603
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1604
        return value // library marker kkossev.commonLib, line 1605
    } // library marker kkossev.commonLib, line 1606
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1607
} // library marker kkossev.commonLib, line 1608

/** // library marker kkossev.commonLib, line 1610
 * Constrain a value to a range // library marker kkossev.commonLib, line 1611
 * @param value value to constrain // library marker kkossev.commonLib, line 1612
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1613
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1614
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1615
 */ // library marker kkossev.commonLib, line 1616
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1617
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1618
        return value as Integer // library marker kkossev.commonLib, line 1619
    } // library marker kkossev.commonLib, line 1620
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1621
} // library marker kkossev.commonLib, line 1622

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1624
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1625

/** // library marker kkossev.commonLib, line 1627
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1628
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1629
 * @param commands commands to execute // library marker kkossev.commonLib, line 1630
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1631
 */ // library marker kkossev.commonLib, line 1632
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1633
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1634
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1635
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1636
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1637
    } // library marker kkossev.commonLib, line 1638
    return [] // library marker kkossev.commonLib, line 1639
} // library marker kkossev.commonLib, line 1640

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1642
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1643
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1644
} // library marker kkossev.commonLib, line 1645

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1647
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1648
} // library marker kkossev.commonLib, line 1649

/** // library marker kkossev.commonLib, line 1651
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1652
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1653
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1654
 */ // library marker kkossev.commonLib, line 1655
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1656
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1657
    List<String> cmds = [] // library marker kkossev.commonLib, line 1658
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1659
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1660
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1661
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1662
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1663
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1664
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1665
    } // library marker kkossev.commonLib, line 1666
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1667
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1668
    /* // library marker kkossev.commonLib, line 1669
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1670
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1671
    */ // library marker kkossev.commonLib, line 1672
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1673
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1674
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1675

    return cmds // library marker kkossev.commonLib, line 1677
} // library marker kkossev.commonLib, line 1678

/** // library marker kkossev.commonLib, line 1680
 * Set Level Command // library marker kkossev.commonLib, line 1681
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1682
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1683
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1684
 */ // library marker kkossev.commonLib, line 1685
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1686
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1687
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1688
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1689
        return // library marker kkossev.commonLib, line 1690
    } // library marker kkossev.commonLib, line 1691
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1692
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1693
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1694
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1695
} // library marker kkossev.commonLib, line 1696

/* // library marker kkossev.commonLib, line 1698
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1699
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1700
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1701
*/ // library marker kkossev.commonLib, line 1702
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1703
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1704
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1705
    } // library marker kkossev.commonLib, line 1706
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1707
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1708
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1709
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1710
    } // library marker kkossev.commonLib, line 1711
    else { // library marker kkossev.commonLib, line 1712
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1713
    } // library marker kkossev.commonLib, line 1714
} // library marker kkossev.commonLib, line 1715

/* // library marker kkossev.commonLib, line 1717
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1718
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1719
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1720
*/ // library marker kkossev.commonLib, line 1721
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1722
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1723
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1724
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1725
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1726
} // library marker kkossev.commonLib, line 1727

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1729
    Map eventMap = [:] // library marker kkossev.commonLib, line 1730
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1731
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1732
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1733
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1734
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1735
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1736
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1737
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1738
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1739
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1740
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1741
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1742
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1743
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1744
        return // library marker kkossev.commonLib, line 1745
    } // library marker kkossev.commonLib, line 1746
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1747
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1748
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1749
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1750
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1751
    } // library marker kkossev.commonLib, line 1752
    else {         // queue the event // library marker kkossev.commonLib, line 1753
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1754
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1755
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1756
    } // library marker kkossev.commonLib, line 1757
} // library marker kkossev.commonLib, line 1758

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1760
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1761
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1762
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1763
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1764
} // library marker kkossev.commonLib, line 1765

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1767

/* // library marker kkossev.commonLib, line 1769
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1770
 * temperature // library marker kkossev.commonLib, line 1771
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1772
*/ // library marker kkossev.commonLib, line 1773
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1774
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1775
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1776
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1777
} // library marker kkossev.commonLib, line 1778

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1780
    Map eventMap = [:] // library marker kkossev.commonLib, line 1781
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1782
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1783
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1784
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1785
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1786
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1787
    } // library marker kkossev.commonLib, line 1788
    else { // library marker kkossev.commonLib, line 1789
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1790
    } // library marker kkossev.commonLib, line 1791
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1792
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1793
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1794
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1795
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1796
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1797
        return // library marker kkossev.commonLib, line 1798
    } // library marker kkossev.commonLib, line 1799
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1800
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1801
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1802
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1803
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1804
    } // library marker kkossev.commonLib, line 1805
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1806
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1807
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1808
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1809
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1810
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1811
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1812
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1813
    } // library marker kkossev.commonLib, line 1814
    else {         // queue the event // library marker kkossev.commonLib, line 1815
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1816
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1817
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1818
    } // library marker kkossev.commonLib, line 1819
} // library marker kkossev.commonLib, line 1820

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1822
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1823
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1824
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1825
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1826
} // library marker kkossev.commonLib, line 1827

/* // library marker kkossev.commonLib, line 1829
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1830
 * humidity // library marker kkossev.commonLib, line 1831
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1832
*/ // library marker kkossev.commonLib, line 1833
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1834
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1835
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1836
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1837
} // library marker kkossev.commonLib, line 1838

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1840
    Map eventMap = [:] // library marker kkossev.commonLib, line 1841
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1842
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1843
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1844
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1845
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1846
        return // library marker kkossev.commonLib, line 1847
    } // library marker kkossev.commonLib, line 1848
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1849
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1850
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1851
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1852
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1853
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1854
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1855
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1856
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1857
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1858
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1859
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1860
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1861
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1862
    } // library marker kkossev.commonLib, line 1863
    else { // library marker kkossev.commonLib, line 1864
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1865
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1866
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1867
    } // library marker kkossev.commonLib, line 1868
} // library marker kkossev.commonLib, line 1869

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1871
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1872
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1873
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1874
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1875
} // library marker kkossev.commonLib, line 1876

/* // library marker kkossev.commonLib, line 1878
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1879
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1880
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1881
*/ // library marker kkossev.commonLib, line 1882

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1884
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1885
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1886
    } // library marker kkossev.commonLib, line 1887
} // library marker kkossev.commonLib, line 1888

/* // library marker kkossev.commonLib, line 1890
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1891
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1892
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1893
*/ // library marker kkossev.commonLib, line 1894

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1896
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1897
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1898
    } // library marker kkossev.commonLib, line 1899
} // library marker kkossev.commonLib, line 1900

/* // library marker kkossev.commonLib, line 1902
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1903
 * pm2.5 // library marker kkossev.commonLib, line 1904
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1905
*/ // library marker kkossev.commonLib, line 1906
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1907
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1908
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1909
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1910
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1911
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1912
    } // library marker kkossev.commonLib, line 1913
    else { // library marker kkossev.commonLib, line 1914
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1915
    } // library marker kkossev.commonLib, line 1916
} // library marker kkossev.commonLib, line 1917


/* // library marker kkossev.commonLib, line 1920
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1921
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1922
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1923
*/ // library marker kkossev.commonLib, line 1924
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1925
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1926
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1927
    } // library marker kkossev.commonLib, line 1928
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1929
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1930
    } // library marker kkossev.commonLib, line 1931
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1932
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1933
    } // library marker kkossev.commonLib, line 1934
    else { // library marker kkossev.commonLib, line 1935
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1936
    } // library marker kkossev.commonLib, line 1937
} // library marker kkossev.commonLib, line 1938

/* // library marker kkossev.commonLib, line 1940
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1941
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1942
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1943
*/ // library marker kkossev.commonLib, line 1944

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1946
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1947
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1948
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1949
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1950
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1951
    } // library marker kkossev.commonLib, line 1952
    else { // library marker kkossev.commonLib, line 1953
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1954
    } // library marker kkossev.commonLib, line 1955
} // library marker kkossev.commonLib, line 1956

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1958
    Map eventMap = [:] // library marker kkossev.commonLib, line 1959
    eventMap.value = value // library marker kkossev.commonLib, line 1960
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1961
    eventMap.unit = '' // library marker kkossev.commonLib, line 1962
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1963
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1964
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1965
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1966
} // library marker kkossev.commonLib, line 1967

/* // library marker kkossev.commonLib, line 1969
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1970
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1971
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1972
*/ // library marker kkossev.commonLib, line 1973

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1975
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1976
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1977
    } // library marker kkossev.commonLib, line 1978
    else { // library marker kkossev.commonLib, line 1979
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1980
    } // library marker kkossev.commonLib, line 1981
} // library marker kkossev.commonLib, line 1982

/* // library marker kkossev.commonLib, line 1984
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1985
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1986
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1987
*/ // library marker kkossev.commonLib, line 1988
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1989
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1990
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1991
    } // library marker kkossev.commonLib, line 1992
    else { // library marker kkossev.commonLib, line 1993
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1994
    } // library marker kkossev.commonLib, line 1995
} // library marker kkossev.commonLib, line 1996

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1998

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2000
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 2001
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 2002
    } // library marker kkossev.commonLib, line 2003
    else { // library marker kkossev.commonLib, line 2004
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2005
    } // library marker kkossev.commonLib, line 2006
} // library marker kkossev.commonLib, line 2007

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2009
    if (this.respondsTo('customParseE002Cluster')) { // library marker kkossev.commonLib, line 2010
        customParseE002Cluster(descMap) // library marker kkossev.commonLib, line 2011
    } // library marker kkossev.commonLib, line 2012
    else { // library marker kkossev.commonLib, line 2013
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2014
    } // library marker kkossev.commonLib, line 2015
} // library marker kkossev.commonLib, line 2016

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2018
    if (this.respondsTo('customParseEC03Cluster')) { // library marker kkossev.commonLib, line 2019
        customParseEC03Cluster(descMap) // library marker kkossev.commonLib, line 2020
    } // library marker kkossev.commonLib, line 2021
    else { // library marker kkossev.commonLib, line 2022
        logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2023
    } // library marker kkossev.commonLib, line 2024
} // library marker kkossev.commonLib, line 2025

/* // library marker kkossev.commonLib, line 2027
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2028
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2029
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2030
*/ // library marker kkossev.commonLib, line 2031
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2032
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2033
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2034

// Tuya Commands // library marker kkossev.commonLib, line 2036
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2037
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2038
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2039
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2040
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2041

// tuya DP type // library marker kkossev.commonLib, line 2043
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2044
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2045
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2046
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2047
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2048
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2049

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2051
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2052
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2053
        Long offset = 0 // library marker kkossev.commonLib, line 2054
        try { // library marker kkossev.commonLib, line 2055
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2056
        } // library marker kkossev.commonLib, line 2057
        catch (e) { // library marker kkossev.commonLib, line 2058
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2059
        } // library marker kkossev.commonLib, line 2060
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2061
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2062
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2063
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2064
    } // library marker kkossev.commonLib, line 2065
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2066
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2067
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2068
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2069
        if (status != '00') { // library marker kkossev.commonLib, line 2070
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2071
        } // library marker kkossev.commonLib, line 2072
    } // library marker kkossev.commonLib, line 2073
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2074
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2075
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2076
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2077
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2078
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2079
            return // library marker kkossev.commonLib, line 2080
        } // library marker kkossev.commonLib, line 2081
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2082
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2083
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2084
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2085
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2086
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2087
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2088
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2089
        } // library marker kkossev.commonLib, line 2090
    } // library marker kkossev.commonLib, line 2091
    else { // library marker kkossev.commonLib, line 2092
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2093
    } // library marker kkossev.commonLib, line 2094
} // library marker kkossev.commonLib, line 2095

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2097
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 2098
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2099
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 2100
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2101
            return // library marker kkossev.commonLib, line 2102
        } // library marker kkossev.commonLib, line 2103
    } // library marker kkossev.commonLib, line 2104
    // check if the method  method exists // library marker kkossev.commonLib, line 2105
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2106
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2107
            return // library marker kkossev.commonLib, line 2108
        } // library marker kkossev.commonLib, line 2109
    } // library marker kkossev.commonLib, line 2110
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2111
} // library marker kkossev.commonLib, line 2112

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2114
    int retValue = 0 // library marker kkossev.commonLib, line 2115
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2116
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2117
        int power = 1 // library marker kkossev.commonLib, line 2118
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2119
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2120
            power = power * 256 // library marker kkossev.commonLib, line 2121
        } // library marker kkossev.commonLib, line 2122
    } // library marker kkossev.commonLib, line 2123
    return retValue // library marker kkossev.commonLib, line 2124
} // library marker kkossev.commonLib, line 2125

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2127
    List<String> cmds = [] // library marker kkossev.commonLib, line 2128
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2129
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2130
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2131
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2132
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2133
    return cmds // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

private getPACKET_ID() { // library marker kkossev.commonLib, line 2137
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2138
} // library marker kkossev.commonLib, line 2139

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2141
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2142
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2143
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2144
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2145
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2146
} // library marker kkossev.commonLib, line 2147

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2149
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2150

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2152
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2153
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2154
    logInfo "tuyaBlackMagic()..." // library marker kkossev.commonLib, line 2155
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2156
} // library marker kkossev.commonLib, line 2157

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2159
    List<String> cmds = [] // library marker kkossev.commonLib, line 2160
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2161
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2162
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2163
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2164
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2165
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2166
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2167
        } // library marker kkossev.commonLib, line 2168
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2169
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2170
    } // library marker kkossev.commonLib, line 2171
    else { // library marker kkossev.commonLib, line 2172
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2173
    } // library marker kkossev.commonLib, line 2174
} // library marker kkossev.commonLib, line 2175

/** // library marker kkossev.commonLib, line 2177
 * initializes the device // library marker kkossev.commonLib, line 2178
 * Invoked from configure() // library marker kkossev.commonLib, line 2179
 * @return zigbee commands // library marker kkossev.commonLib, line 2180
 */ // library marker kkossev.commonLib, line 2181
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2182
    List<String> cmds = [] // library marker kkossev.commonLib, line 2183
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2184

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2186
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2187
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 2188
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2189
    } // library marker kkossev.commonLib, line 2190
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2191
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2192
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2193
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2194
    } // library marker kkossev.commonLib, line 2195
    // // library marker kkossev.commonLib, line 2196
    return cmds // library marker kkossev.commonLib, line 2197
} // library marker kkossev.commonLib, line 2198

/** // library marker kkossev.commonLib, line 2200
 * configures the device // library marker kkossev.commonLib, line 2201
 * Invoked from configure() // library marker kkossev.commonLib, line 2202
 * @return zigbee commands // library marker kkossev.commonLib, line 2203
 */ // library marker kkossev.commonLib, line 2204
List<String> configureDevice() { // library marker kkossev.commonLib, line 2205
    List<String> cmds = [] // library marker kkossev.commonLib, line 2206
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2207

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2209
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 2210
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2211
    } // library marker kkossev.commonLib, line 2212
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2213
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2214
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2215
    return cmds // library marker kkossev.commonLib, line 2216
} // library marker kkossev.commonLib, line 2217

/* // library marker kkossev.commonLib, line 2219
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2220
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2221
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2222
*/ // library marker kkossev.commonLib, line 2223

void refresh() { // library marker kkossev.commonLib, line 2225
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2226
    checkDriverVersion() // library marker kkossev.commonLib, line 2227
    List<String> cmds = [] // library marker kkossev.commonLib, line 2228
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2229

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2231
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2232
        cmds += customRefresh() // library marker kkossev.commonLib, line 2233
    } // library marker kkossev.commonLib, line 2234
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2235
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
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2250
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

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2264
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2265
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2266
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2267

void clearInfoEvent() { // library marker kkossev.commonLib, line 2269
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2270
} // library marker kkossev.commonLib, line 2271

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2273
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2274
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2275
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2276
    } // library marker kkossev.commonLib, line 2277
    else { // library marker kkossev.commonLib, line 2278
        logInfo "${info}" // library marker kkossev.commonLib, line 2279
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2280
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2281
    } // library marker kkossev.commonLib, line 2282
} // library marker kkossev.commonLib, line 2283

void ping() { // library marker kkossev.commonLib, line 2285
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2286
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2287
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2288
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2289
    } // library marker kkossev.commonLib, line 2290
    else { // library marker kkossev.commonLib, line 2291
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2292
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2293
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2294
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2295
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2296
        if (isVirtual()) { // library marker kkossev.commonLib, line 2297
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2298
        } // library marker kkossev.commonLib, line 2299
        else { // library marker kkossev.commonLib, line 2300
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2301
        } // library marker kkossev.commonLib, line 2302
        logDebug 'ping...' // library marker kkossev.commonLib, line 2303
    } // library marker kkossev.commonLib, line 2304
} // library marker kkossev.commonLib, line 2305

def virtualPong() { // library marker kkossev.commonLib, line 2307
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2308
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2309
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2310
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2311
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2312
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2313
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2314
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2315
        sendRttEvent() // library marker kkossev.commonLib, line 2316
    } // library marker kkossev.commonLib, line 2317
    else { // library marker kkossev.commonLib, line 2318
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2319
    } // library marker kkossev.commonLib, line 2320
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2321
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2322
} // library marker kkossev.commonLib, line 2323

/** // library marker kkossev.commonLib, line 2325
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2326
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2327
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2328
 * @return none // library marker kkossev.commonLib, line 2329
 */ // library marker kkossev.commonLib, line 2330
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2331
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2332
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2333
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2334
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2335
    if (value == null) { // library marker kkossev.commonLib, line 2336
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2337
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2338
    } // library marker kkossev.commonLib, line 2339
    else { // library marker kkossev.commonLib, line 2340
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2341
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2342
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2343
    } // library marker kkossev.commonLib, line 2344
} // library marker kkossev.commonLib, line 2345

/** // library marker kkossev.commonLib, line 2347
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2348
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2349
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2350
 */ // library marker kkossev.commonLib, line 2351
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2352
    if (cluster != null) { // library marker kkossev.commonLib, line 2353
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2354
    } // library marker kkossev.commonLib, line 2355
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2356
    return 'NULL' // library marker kkossev.commonLib, line 2357
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

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2402
    checkDriverVersion() // library marker kkossev.commonLib, line 2403
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2404
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2405
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

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2418
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2419
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
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2436
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2437
    } // library marker kkossev.commonLib, line 2438

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2440
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2441
    } // library marker kkossev.commonLib, line 2442
} // library marker kkossev.commonLib, line 2443

/** // library marker kkossev.commonLib, line 2445
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2446
 */ // library marker kkossev.commonLib, line 2447
void updated() { // library marker kkossev.commonLib, line 2448
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2449
    checkDriverVersion() // library marker kkossev.commonLib, line 2450
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2451
    unschedule() // library marker kkossev.commonLib, line 2452

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2454
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2455
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2456
    } // library marker kkossev.commonLib, line 2457
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2458
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2459
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2460
    } // library marker kkossev.commonLib, line 2461

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2463
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2464
        // schedule the periodic timer // library marker kkossev.commonLib, line 2465
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2466
        if (interval > 0) { // library marker kkossev.commonLib, line 2467
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2468
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2469
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2470
        } // library marker kkossev.commonLib, line 2471
    } // library marker kkossev.commonLib, line 2472
    else { // library marker kkossev.commonLib, line 2473
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2474
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2475
    } // library marker kkossev.commonLib, line 2476
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2477
        customUpdated() // library marker kkossev.commonLib, line 2478
    } // library marker kkossev.commonLib, line 2479

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2481
} // library marker kkossev.commonLib, line 2482

/** // library marker kkossev.commonLib, line 2484
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2485
 */ // library marker kkossev.commonLib, line 2486
void logsOff() { // library marker kkossev.commonLib, line 2487
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2488
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2489
} // library marker kkossev.commonLib, line 2490
void traceOff() { // library marker kkossev.commonLib, line 2491
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2492
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2493
} // library marker kkossev.commonLib, line 2494

void configure(String command) { // library marker kkossev.commonLib, line 2496
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2497
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2498
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2499
        return // library marker kkossev.commonLib, line 2500
    } // library marker kkossev.commonLib, line 2501
    // // library marker kkossev.commonLib, line 2502
    String func // library marker kkossev.commonLib, line 2503
    try { // library marker kkossev.commonLib, line 2504
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2505
        "$func"() // library marker kkossev.commonLib, line 2506
    } // library marker kkossev.commonLib, line 2507
    catch (e) { // library marker kkossev.commonLib, line 2508
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2509
        return // library marker kkossev.commonLib, line 2510
    } // library marker kkossev.commonLib, line 2511
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2512
} // library marker kkossev.commonLib, line 2513

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2515
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2516
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2517
} // library marker kkossev.commonLib, line 2518

void loadAllDefaults() { // library marker kkossev.commonLib, line 2520
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2521
    deleteAllSettings() // library marker kkossev.commonLib, line 2522
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2523
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2524
    deleteAllStates() // library marker kkossev.commonLib, line 2525
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2526
    initialize() // library marker kkossev.commonLib, line 2527
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 2528
    updated() // library marker kkossev.commonLib, line 2529
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2530
} // library marker kkossev.commonLib, line 2531

void configureNow() { // library marker kkossev.commonLib, line 2533
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2534
} // library marker kkossev.commonLib, line 2535

/** // library marker kkossev.commonLib, line 2537
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2538
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2539
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2540
 */ // library marker kkossev.commonLib, line 2541
List<String> configure() { // library marker kkossev.commonLib, line 2542
    List<String> cmds = [] // library marker kkossev.commonLib, line 2543
    logInfo 'configure...' // library marker kkossev.commonLib, line 2544
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2545
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2546
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2547
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 2548
    } // library marker kkossev.commonLib, line 2549
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2550
    cmds += configureDevice() // library marker kkossev.commonLib, line 2551
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2552
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2553
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 2554
    //return cmds // library marker kkossev.commonLib, line 2555
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2556
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2557
    } // library marker kkossev.commonLib, line 2558
    else { // library marker kkossev.commonLib, line 2559
        logDebug "no configure() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2560
    } // library marker kkossev.commonLib, line 2561
} // library marker kkossev.commonLib, line 2562

/** // library marker kkossev.commonLib, line 2564
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2565
 */ // library marker kkossev.commonLib, line 2566
void installed() { // library marker kkossev.commonLib, line 2567
    logInfo 'installed...' // library marker kkossev.commonLib, line 2568
    // populate some default values for attributes // library marker kkossev.commonLib, line 2569
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2570
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2571
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2572
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2573
} // library marker kkossev.commonLib, line 2574

/** // library marker kkossev.commonLib, line 2576
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2577
 */ // library marker kkossev.commonLib, line 2578
void initialize() { // library marker kkossev.commonLib, line 2579
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2580
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2581
    updateTuyaVersion() // library marker kkossev.commonLib, line 2582
    updateAqaraVersion() // library marker kkossev.commonLib, line 2583
} // library marker kkossev.commonLib, line 2584

/* // library marker kkossev.commonLib, line 2586
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2587
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2588
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2589
*/ // library marker kkossev.commonLib, line 2590

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2592
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2593
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2594
} // library marker kkossev.commonLib, line 2595

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2597
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2598
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2599
} // library marker kkossev.commonLib, line 2600

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2602
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2603
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2604
} // library marker kkossev.commonLib, line 2605

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2607
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 2608
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 2609
        return // library marker kkossev.commonLib, line 2610
    } // library marker kkossev.commonLib, line 2611
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2612
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2613
    cmd.each { // library marker kkossev.commonLib, line 2614
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2615
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2616
    } // library marker kkossev.commonLib, line 2617
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2618
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2619
} // library marker kkossev.commonLib, line 2620

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2622

String getDeviceInfo() { // library marker kkossev.commonLib, line 2624
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2625
} // library marker kkossev.commonLib, line 2626

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2628
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2629
} // library marker kkossev.commonLib, line 2630

void checkDriverVersion() { // library marker kkossev.commonLib, line 2632
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2633
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2634
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2635
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2636
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2637
        updateTuyaVersion() // library marker kkossev.commonLib, line 2638
        updateAqaraVersion() // library marker kkossev.commonLib, line 2639
    } // library marker kkossev.commonLib, line 2640
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2641
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2642
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2643
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 2644
} // library marker kkossev.commonLib, line 2645

// credits @thebearmay // library marker kkossev.commonLib, line 2647
String getModel() { // library marker kkossev.commonLib, line 2648
    try { // library marker kkossev.commonLib, line 2649
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2650
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2651
    } catch (ignore) { // library marker kkossev.commonLib, line 2652
        try { // library marker kkossev.commonLib, line 2653
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2654
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2655
                return model // library marker kkossev.commonLib, line 2656
            } // library marker kkossev.commonLib, line 2657
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2658
            return '' // library marker kkossev.commonLib, line 2659
        } // library marker kkossev.commonLib, line 2660
    } // library marker kkossev.commonLib, line 2661
} // library marker kkossev.commonLib, line 2662

// credits @thebearmay // library marker kkossev.commonLib, line 2664
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2665
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2666
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2667
    String revision = tokens.last() // library marker kkossev.commonLib, line 2668
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2669
} // library marker kkossev.commonLib, line 2670

/** // library marker kkossev.commonLib, line 2672
 * called from TODO // library marker kkossev.commonLib, line 2673
 */ // library marker kkossev.commonLib, line 2674

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2676
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2677
    unschedule() // library marker kkossev.commonLib, line 2678
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2679
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2680

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2682
} // library marker kkossev.commonLib, line 2683

void resetStatistics() { // library marker kkossev.commonLib, line 2685
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2686
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2687
} // library marker kkossev.commonLib, line 2688

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2690
void resetStats() { // library marker kkossev.commonLib, line 2691
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2692
    state.stats = [:] // library marker kkossev.commonLib, line 2693
    state.states = [:] // library marker kkossev.commonLib, line 2694
    state.lastRx = [:] // library marker kkossev.commonLib, line 2695
    state.lastTx = [:] // library marker kkossev.commonLib, line 2696
    state.health = [:] // library marker kkossev.commonLib, line 2697
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2698
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2699
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2700
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2701
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2702
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2703
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2704
} // library marker kkossev.commonLib, line 2705

/** // library marker kkossev.commonLib, line 2707
 * called from TODO // library marker kkossev.commonLib, line 2708
 */ // library marker kkossev.commonLib, line 2709
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2710
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2711
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2712
        state.clear() // library marker kkossev.commonLib, line 2713
        unschedule() // library marker kkossev.commonLib, line 2714
        resetStats() // library marker kkossev.commonLib, line 2715
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2716
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2717
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2718
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2719
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2720
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2721
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2722
    } // library marker kkossev.commonLib, line 2723

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2725
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2726
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2727
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2728
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2729
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2730

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2732
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2733
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2734
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2735
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2736
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2737
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2738
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2739
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2740
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2741

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2743
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2744
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2745
    } // library marker kkossev.commonLib, line 2746
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2747
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2748
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2749
    } // library marker kkossev.commonLib, line 2750
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2751
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2752
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2753
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2754
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2755

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2757
    if ( mm != null) { // library marker kkossev.commonLib, line 2758
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2759
    } // library marker kkossev.commonLib, line 2760
    else { // library marker kkossev.commonLib, line 2761
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2762
    } // library marker kkossev.commonLib, line 2763
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2764
    if ( ep  != null) { // library marker kkossev.commonLib, line 2765
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2766
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2767
    } // library marker kkossev.commonLib, line 2768
    else { // library marker kkossev.commonLib, line 2769
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2770
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2771
    } // library marker kkossev.commonLib, line 2772
} // library marker kkossev.commonLib, line 2773

/** // library marker kkossev.commonLib, line 2775
 * called from TODO // library marker kkossev.commonLib, line 2776
 */ // library marker kkossev.commonLib, line 2777
void setDestinationEP() { // library marker kkossev.commonLib, line 2778
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2779
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2780
        state.destinationEP = ep // library marker kkossev.commonLib, line 2781
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2782
    } // library marker kkossev.commonLib, line 2783
    else { // library marker kkossev.commonLib, line 2784
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2785
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2786
    } // library marker kkossev.commonLib, line 2787
} // library marker kkossev.commonLib, line 2788

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2790
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2791
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2792
    } // library marker kkossev.commonLib, line 2793
} // library marker kkossev.commonLib, line 2794

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2796
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2797
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2798
    } // library marker kkossev.commonLib, line 2799
} // library marker kkossev.commonLib, line 2800

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2802
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2803
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2804
    } // library marker kkossev.commonLib, line 2805
} // library marker kkossev.commonLib, line 2806

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2808
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2809
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2810
    } // library marker kkossev.commonLib, line 2811
} // library marker kkossev.commonLib, line 2812

// _DEBUG mode only // library marker kkossev.commonLib, line 2814
void getAllProperties() { // library marker kkossev.commonLib, line 2815
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2816
    device.properties.each { it -> // library marker kkossev.commonLib, line 2817
        log.debug it // library marker kkossev.commonLib, line 2818
    } // library marker kkossev.commonLib, line 2819
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2820
    settings.each { it -> // library marker kkossev.commonLib, line 2821
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2822
    } // library marker kkossev.commonLib, line 2823
    log.trace 'Done' // library marker kkossev.commonLib, line 2824
} // library marker kkossev.commonLib, line 2825

// delete all Preferences // library marker kkossev.commonLib, line 2827
void deleteAllSettings() { // library marker kkossev.commonLib, line 2828
    settings.each { it -> // library marker kkossev.commonLib, line 2829
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2830
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2831
    } // library marker kkossev.commonLib, line 2832
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2833
} // library marker kkossev.commonLib, line 2834

// delete all attributes // library marker kkossev.commonLib, line 2836
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2837
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2838
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2839
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2840
    } // library marker kkossev.commonLib, line 2841
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2842
} // library marker kkossev.commonLib, line 2843

// delete all State Variables // library marker kkossev.commonLib, line 2845
void deleteAllStates() { // library marker kkossev.commonLib, line 2846
    state.each { it -> // library marker kkossev.commonLib, line 2847
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2848
    } // library marker kkossev.commonLib, line 2849
    state.clear() // library marker kkossev.commonLib, line 2850
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2851
} // library marker kkossev.commonLib, line 2852

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2854
    unschedule() // library marker kkossev.commonLib, line 2855
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2856
} // library marker kkossev.commonLib, line 2857

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2859
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2860
} // library marker kkossev.commonLib, line 2861

void parseTest(String par) { // library marker kkossev.commonLib, line 2863
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2864
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2865
    parse(par) // library marker kkossev.commonLib, line 2866
} // library marker kkossev.commonLib, line 2867

def testJob() { // library marker kkossev.commonLib, line 2869
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2870
} // library marker kkossev.commonLib, line 2871

/** // library marker kkossev.commonLib, line 2873
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2874
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2875
 */ // library marker kkossev.commonLib, line 2876
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2877
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2878
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2879
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2880
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2881
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2882
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2883
    String cron // library marker kkossev.commonLib, line 2884
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2885
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2886
    } // library marker kkossev.commonLib, line 2887
    else { // library marker kkossev.commonLib, line 2888
        if (minutes < 60) { // library marker kkossev.commonLib, line 2889
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2890
        } // library marker kkossev.commonLib, line 2891
        else { // library marker kkossev.commonLib, line 2892
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2893
        } // library marker kkossev.commonLib, line 2894
    } // library marker kkossev.commonLib, line 2895
    return cron // library marker kkossev.commonLib, line 2896
} // library marker kkossev.commonLib, line 2897

// credits @thebearmay // library marker kkossev.commonLib, line 2899
String formatUptime() { // library marker kkossev.commonLib, line 2900
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2901
} // library marker kkossev.commonLib, line 2902

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2904
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2905
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2906
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2907
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2908
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2909
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2910
} // library marker kkossev.commonLib, line 2911

boolean isTuya() { // library marker kkossev.commonLib, line 2913
    if (!device) { return true }    // fallback - added 04/03/2024  // library marker kkossev.commonLib, line 2914
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2915
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2916
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2917
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2918
} // library marker kkossev.commonLib, line 2919

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2921
    if (!isTuya()) { // library marker kkossev.commonLib, line 2922
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2923
        return // library marker kkossev.commonLib, line 2924
    } // library marker kkossev.commonLib, line 2925
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2926
    if (application != null) { // library marker kkossev.commonLib, line 2927
        Integer ver // library marker kkossev.commonLib, line 2928
        try { // library marker kkossev.commonLib, line 2929
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2930
        } // library marker kkossev.commonLib, line 2931
        catch (e) { // library marker kkossev.commonLib, line 2932
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2933
            return // library marker kkossev.commonLib, line 2934
        } // library marker kkossev.commonLib, line 2935
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2936
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2937
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2938
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2939
        } // library marker kkossev.commonLib, line 2940
    } // library marker kkossev.commonLib, line 2941
} // library marker kkossev.commonLib, line 2942

boolean isAqara() { // library marker kkossev.commonLib, line 2944
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2945
} // library marker kkossev.commonLib, line 2946

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2948
    if (!isAqara()) { // library marker kkossev.commonLib, line 2949
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2950
        return // library marker kkossev.commonLib, line 2951
    } // library marker kkossev.commonLib, line 2952
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2953
    if (application != null) { // library marker kkossev.commonLib, line 2954
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2955
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2956
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2957
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2958
        } // library marker kkossev.commonLib, line 2959
    } // library marker kkossev.commonLib, line 2960
} // library marker kkossev.commonLib, line 2961

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2963
    try { // library marker kkossev.commonLib, line 2964
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2965
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2966
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2967
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2968
    } catch (e) { // library marker kkossev.commonLib, line 2969
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2970
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2971
    } // library marker kkossev.commonLib, line 2972
} // library marker kkossev.commonLib, line 2973

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2975
    try { // library marker kkossev.commonLib, line 2976
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2977
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2978
        return date.getTime() // library marker kkossev.commonLib, line 2979
    } catch (e) { // library marker kkossev.commonLib, line 2980
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2981
        return now() // library marker kkossev.commonLib, line 2982
    } // library marker kkossev.commonLib, line 2983
} // library marker kkossev.commonLib, line 2984

void test(String par) { // library marker kkossev.commonLib, line 2986
    List<String> cmds = [] // library marker kkossev.commonLib, line 2987
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2988

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2990
    //parse(par) // library marker kkossev.commonLib, line 2991

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2993
} // library marker kkossev.commonLib, line 2994

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
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
 * ver. 1.0.2  2024-04-01 kkossev  - (dev. branch) Groovy linting  // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/04/01 8:51 AM' } // library marker kkossev.xiaomiLib, line 35

// no metadata for this library! // library marker kkossev.xiaomiLib, line 37

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 39

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 41
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 42
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 43
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 45
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 46
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 47
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 48
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 52
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 53
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 55
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 56

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 58
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 59
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 60
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 61
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 62
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 64

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 66
// // library marker kkossev.xiaomiLib, line 67
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 68
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 69
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 70
    } // library marker kkossev.xiaomiLib, line 71
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 72
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 73
        return // library marker kkossev.xiaomiLib, line 74
    } // library marker kkossev.xiaomiLib, line 75
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 76
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 77
        return // library marker kkossev.xiaomiLib, line 78
    } // library marker kkossev.xiaomiLib, line 79
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 80
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 81
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 82
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 83
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 84
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 87
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 90
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 91
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 92
            break // library marker kkossev.xiaomiLib, line 93
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 94
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 95
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 96
            break // library marker kkossev.xiaomiLib, line 97
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 98
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 99
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 100
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 101
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 102
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 103
            } // library marker kkossev.xiaomiLib, line 104
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 105
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 106
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 107
            } // library marker kkossev.xiaomiLib, line 108
            break // library marker kkossev.xiaomiLib, line 109
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 110
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 111
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 112
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 113
            break // library marker kkossev.xiaomiLib, line 114
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 115
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 116
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 117
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 118
            break // library marker kkossev.xiaomiLib, line 119
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 120
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 121
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 122
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 123
            break // library marker kkossev.xiaomiLib, line 124
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 125
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 126
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 127
            break // library marker kkossev.xiaomiLib, line 128
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 129
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 130
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 131
            break // library marker kkossev.xiaomiLib, line 132
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 133
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 134
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 135
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 136
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 137
            } // library marker kkossev.xiaomiLib, line 138
            break // library marker kkossev.xiaomiLib, line 139
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 140
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 141
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 142
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 143
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 144
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 145
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 146
                } // library marker kkossev.xiaomiLib, line 147
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 148
            } // library marker kkossev.xiaomiLib, line 149
            break // library marker kkossev.xiaomiLib, line 150
        default: // library marker kkossev.xiaomiLib, line 151
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
    } // library marker kkossev.xiaomiLib, line 154
} // library marker kkossev.xiaomiLib, line 155

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 157
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 158
        switch (tag) { // library marker kkossev.xiaomiLib, line 159
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 160
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 161
                break // library marker kkossev.xiaomiLib, line 162
            case 0x03: // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x05: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x06: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 172
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 173
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 174
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 175
                break // library marker kkossev.xiaomiLib, line 176
            case 0x0a: // library marker kkossev.xiaomiLib, line 177
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 178
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 179
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 180
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 181
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 182
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 183
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 184
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 185
                } // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x0b: // library marker kkossev.xiaomiLib, line 188
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x64: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 192
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 193
                break // library marker kkossev.xiaomiLib, line 194
            case 0x65: // library marker kkossev.xiaomiLib, line 195
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 196
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 197
                break // library marker kkossev.xiaomiLib, line 198
            case 0x66: // library marker kkossev.xiaomiLib, line 199
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 200
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 201
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 202
                break // library marker kkossev.xiaomiLib, line 203
            case 0x67: // library marker kkossev.xiaomiLib, line 204
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 205
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 206
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 207
                break // library marker kkossev.xiaomiLib, line 208
            case 0x69: // library marker kkossev.xiaomiLib, line 209
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 210
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                break // library marker kkossev.xiaomiLib, line 212
            case 0x6a: // library marker kkossev.xiaomiLib, line 213
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 215
                break // library marker kkossev.xiaomiLib, line 216
            case 0x6b: // library marker kkossev.xiaomiLib, line 217
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 219
                break // library marker kkossev.xiaomiLib, line 220
            case 0x95: // library marker kkossev.xiaomiLib, line 221
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x96: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x97: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x98: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x9b: // library marker kkossev.xiaomiLib, line 233
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 234
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 235
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 236
                } // library marker kkossev.xiaomiLib, line 237
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 238
                break // library marker kkossev.xiaomiLib, line 239
            default: // library marker kkossev.xiaomiLib, line 240
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 241
        } // library marker kkossev.xiaomiLib, line 242
    } // library marker kkossev.xiaomiLib, line 243
} // library marker kkossev.xiaomiLib, line 244

/** // library marker kkossev.xiaomiLib, line 246
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 247
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 248
 */ // library marker kkossev.xiaomiLib, line 249
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 250
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 251
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 252
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 253
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 254
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 255
    } // library marker kkossev.xiaomiLib, line 256
    return bigInt // library marker kkossev.xiaomiLib, line 257
} // library marker kkossev.xiaomiLib, line 258

/** // library marker kkossev.xiaomiLib, line 260
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 261
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 262
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 263
 */ // library marker kkossev.xiaomiLib, line 264
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 265
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 266
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 267
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 268
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 269
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 270
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 271
            Object value // library marker kkossev.xiaomiLib, line 272
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 273
                int length = stream.read() // library marker kkossev.xiaomiLib, line 274
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 275
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 276
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 277
            } else { // library marker kkossev.xiaomiLib, line 278
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 279
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 280
            } // library marker kkossev.xiaomiLib, line 281
            results[tag] = value // library marker kkossev.xiaomiLib, line 282
        } // library marker kkossev.xiaomiLib, line 283
    } // library marker kkossev.xiaomiLib, line 284
    return results // library marker kkossev.xiaomiLib, line 285
} // library marker kkossev.xiaomiLib, line 286

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 288
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 289
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 290
    return cmds // library marker kkossev.xiaomiLib, line 291
} // library marker kkossev.xiaomiLib, line 292

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 294
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 295
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 296
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 297
    return cmds // library marker kkossev.xiaomiLib, line 298
} // library marker kkossev.xiaomiLib, line 299

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 301
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 302
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 303
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 304
    return cmds // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 312
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~
