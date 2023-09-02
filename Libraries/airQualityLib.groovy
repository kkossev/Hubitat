library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Air Quality Library",
    name: "airQualityLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/airQualityLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 * airQualityLib -Air Quality Library
 *
 * ver. 1.0.0  2023-07-23 kkossev  - Libraries introduction for the VINDSTIRKA driver;
 * ver. 1.0.1  2023-09-02 kkossev  - (dev.branch) removed airQualityLevel for VINDSTYRKA; airQualityIndex is deleted if polling is disabled; added pm25Threshold; added airQualityIndexThreshold; airQualityIndex replaced by sensirionVOCindex
 *
 *                                   TODO:
 *
*/

def airQualityLibVersion()   {"1.0.1"}
def airQualityimeStamp() {"2023/09/02 9:18 PM"}

metadata {
    attribute "pm25", "number"
    attribute "sensirionVOCindex", "number"    // VINDSTYRKA used sensirionVOCindex instead of airQualityIndex
    attribute "airQualityLevel", "enum", ["Good","Moderate","Unhealthy for Sensitive Groups","Unhealthy","Very Unhealthy","Hazardous"]    // https://www.airnow.gov/aqi/aqi-basics/ **** for Aqara only! ***

    if (isAqaraTVOC()) {
            capability "Battery"
            attribute "batteryVoltage", "number"
    }
	 
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0402,0405,FC57,FC7C,042A,FC7E", outClusters:"0003,0019,0020,0202", model:"VINDSTYRKA", manufacturer:"IKEA of Sweden", deviceJoinName: "VINDSTYRKA Air Quality Monitor E2112" 
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0019", model:"lumi.airmonitor.acn01", manufacturer:"LUMI", deviceJoinName: "Aqara TVOC Air Quality Monitor" 

    preferences {
        input name: "pm25Threshold", type: "number", title: "<b>PM 2.5 Reporting Threshold</b>", description: "<i>PM 2.5 reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_PM25_THRESHOLD
        if (isVINDSTYRKA()) {
            //input name: 'airQualityIndexCheckInterval', type: 'enum', title: '<b>Air Quality Index check interval</b>', options: AirQualityIndexCheckIntervalOpts.options, defaultValue: AirQualityIndexCheckIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub retreives the Air Quality Index.</i>'
            input name: 'airQualityIndexCheckInterval', type: 'enum', title: '<b>Sensirion VOC index check interval</b>', options: AirQualityIndexCheckIntervalOpts.options, defaultValue: AirQualityIndexCheckIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub retreives the Sensirion VOC index.</i>'
            input name: "airQualityIndexThreshold", type: "number", title: "<b>Sensirion VOC index Reporting Threshold</b>", description: "<i>Sensirion VOC index reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD
        }
        else  if (isAqaraTVOC()) {
            input name: "airQualityIndexThreshold", type: "number", title: "<b>Air Quality Index Reporting Threshold</b>", description: "<i>Air quality index reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD
            input name: 'temperatureScale', type: 'enum', title: '<b>Temperaure Scale on the Screen</b>', options: TemperatureScaleOpts.options, defaultValue: TemperatureScaleOpts.defaultValue, required: true, description: '<i>Changes the temperature scale (Celsius, Fahrenheit) on the screen.</i>'
            input name: 'tVocUnut', type: 'enum', title: '<b>tVOC unit on the Screen</b>', options: TvocUnitOpts.options, defaultValue: TvocUnitOpts.defaultValue, required: true, description: '<i>Changes the tVOC unit (mg/m³, ppb) on the screen.</i>'
        }
    }
}

def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] }
def isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }

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

/*
 * -----------------------------------------------------------------------------
 * handlePm25Event
 * -----------------------------------------------------------------------------
*/
void handlePm25Event( Integer pm25, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:]
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0)
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) {
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})"
        return
    }
    eventMap.value = Math.round(pm25AsDouble)
    eventMap.name = "pm25"
    eventMap.unit = "\u03BCg/m3"    //"mg/m3"
    eventMap.type = isDigital == true ? "digital" : "physical"
    //eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000)
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastPm25 = device.currentValue("pm25") ?: 0
    Integer delta = Math.abs(lastPm25 - eventMap.value)
    if (delta < ((settings?.pm25Threshold ?: DEFAULT_PM25_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> pm25 report ${eventMap.value}, less than delta ${settings?.pm25Threshold} (lastPm25=${lastPm25})"
        return
    }    
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule("sendDelayedPm25Event")
        state.lastRx['pm25Time'] = now()
        sendEvent(eventMap)
    }
    else {
    	eventMap.type = "delayed"
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap])
    }
}

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
    def value = hexStrToUnsignedInt(descMap.value)
	Float floatValue = Float.intBitsToFloat(value.intValue())    
    handleAirQualityIndexEvent(floatValue as Integer)
}

void handleAirQualityIndexEvent( Integer tVoc, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['tVocCtr'] = (state.stats['tVocCtr'] ?: 0) + 1 else state.stats=[:]
    Integer tVocCorrected= safeToDouble(tVoc) + safeToDouble(settings?.tVocOffset ?: 0)
    if (tVocCorrected < 0 || tVocCorrected > 999) {
        logWarn "ignored invalid tVoc ${tVoc} (${tVocCorrected})"
        return
    }
    if (safeToInt((device.currentState("airQualityIndex")?.value ?: -1)) == tVocCorrected) {
        logDebug "ignored duplicated tVoc ${tVoc} (${tVocCorrected})"
        return
    }
    eventMap.value = tVocCorrected as Integer
    Integer lastAIQ
    if (isVINDSTYRKA()) {
        eventMap.name = "sensirionVOCindex"
        lastAIQ = device.currentValue("sensirionVOCindex") ?: 0    
    }
    else {
        eventMap.name = "airQualityIndex"
        lastAIQ = device.currentValue("airQualityIndex") ?: 0    
    }
    eventMap.unit = ""
    eventMap.type = isDigital == true ? "digital" : "physical"
    eventMap.descriptionText = "${eventMap.name} is ${tVocCorrected} ${eventMap.unit}"
    Integer timeElapsed = ((now() - (state.lastRx['tVocTime'] ?: now() -10000 ))/1000) as Integer
    Integer minTime = settings?.minReportingTimetVoc ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer delta = Math.abs(lastAIQ - eventMap.value)
    if (delta < ((settings?.airQualityIndexThreshold ?: DEFAULT_AIR_QUALITY_INDEX_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> airQualityIndex ${eventMap.value}, less than delta ${delta} (lastAIQ=${lastAIQ})"
        return
    }
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule("sendDelayedtVocEvent")
        state.lastRx['tVocTime'] = now()
        sendEvent(eventMap)
        if (isAqaraTVOC()) {
            sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value)))
        }
    }
    else {
    	eventMap.type = "delayed"
        //logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedtVocEvent',  [overwrite: true, data: eventMap])
    }
}

private void sendDelayedtVocEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['tVocTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
	sendEvent(eventMap)
    if (isAqaraTVOC()) {
        sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value)))
    }
}

// https://github.com/zigpy/zigpy/discussions/691 
// 09/02/2023 - used by Aqara only !
String airQualityIndexToLevel(final Integer index)
{
    String level
    if (index <0 )        { level = 'unknown' }
    else if (index < 50)  { level = 'Good' }
    else if (index < 100) { level = 'Moderate' }
    else if (index < 150) { level = 'Unhealthy for Sensitive Groups' }
    else if (index < 200) { level = 'Unhealthy' }
    else if (index < 300) { level = 'Very Unhealthy' }
    else if (index < 501) { level = 'Hazardous' }
    else                  { level = 'Hazardous Out of Range' }
    
    return level
}

private void sendAirQualityLevelEvent(String level) {
    if (level == null || level == '') { return }
    def descriptionText = "Air Quality Level is ${level}"
    logInfo "${descriptionText}"
    sendEvent(name: "airQualityLevel", value: level, descriptionText: descriptionText, unit: "", isDigital: true)        
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

def configureDeviceAirQuality() {
    ArrayList<String> cmds = []
    if (isAqaraTVOC()) {
        logDebug 'configureDeviceAirQuality() AqaraTVOC'
        // https://forum.phoscon.de/t/aqara-tvoc-zhaairquality-data/1160/21
        final int tScale = (settings.temperatureScale as Integer) ?: TemperatureScaleOpts.defaultValue
        final int tUnit =  (settings.tVocUnut as Integer) ?: TvocUnitOpts.defaultValue
        logDebug "setting temperatureScale to ${TemperatureScaleOpts.options[tScale]} (${tScale})"
        int cfg = tUnit
        cfg |= (tScale << 4)
        cmds += zigbee.writeAttribute(0xFCC0, 0x0114, DataType.UINT8, cfg, [mfgCode: 0x115F], delay=200)
        cmds += zigbee.readAttribute(0xFCC0, 0x0114, [mfgCode: 0x115F], delay=200)    
    }
    else if (isVINDSTYRKA()) {
        logDebug 'configureDeviceAirQuality() VINDSTYRKA (nothig to configure)'
    }
    else {
        logWarn "configureDeviceAirQuality: unsupported device?"
    }
    return cmds
}


def initializeDeviceAirQuality() {
    ArrayList<String> cmds = []
    if (isAqaraTVOC()) {
        logDebug 'initializeDeviceAirQuality() AqaraTVOC'
	    return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)+
			zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000)+
			zigbee.readAttribute(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000) +
			zigbee.readAttribute(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE) +
			zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000, DataType.UINT16, 30, 300, 1*100) +
			zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.INT16, 30, 300, 0x1) +
			zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, 30, 21600, 0x1) + 
			zigbee.configureReporting(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE, DataType.FLOAT4, 10, 3600, 5)    
    } 
    else if (isVINDSTYRKA()) {
        logDebug 'initializeDeviceAirQuality() VINDSTYRKA'
        // Ikea VINDSTYRKA : bind clusters 402, 405, 42A (PM2.5)
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                 // 402 - temperature
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)    // 405 - humidity
        cmds += zigbee.configureReporting(0x042a, 0, 0x39, 30, 60, 1)    // 405 - pm2.5
        //cmds += zigbee.configureReporting(0xfc7e, 0, 0x39, 10, 60, 50)     // provides a measurement in the range of 0-500 that correlates with the tVOC trend display on the unit itself.
        cmds += ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0xfc7e {${device.zigbeeId}} {}", "delay 251", ]
        
    }
    else {
        logWarn "initializeDeviceAirQuality: unsupported device?"
    }
    return cmds
}

void updatedAirQuality() {
    if (isVINDSTYRKA()) {
        final int intervalAirQuality = (settings.airQualityIndexCheckInterval as Integer) ?: 0
        if (intervalAirQuality > 0) {
            logInfo "updatedAirQuality: scheduling Air Quality Index check every ${intervalAirQuality} seconds"
            scheduleAirQualityIndexCheck(intervalAirQuality)
        }
        else {
            unScheduleAirQualityIndexCheck()
            logInfo "updatedAirQuality: Air Quality Index polling is disabled!"
            // 09/02/2023
            device.deleteCurrentState("airQualityIndex")
        }
            
    }
    else {
        logDebug "updatedAirQuality: skipping airQuality polling "
    }
}

def refreshAirQuality() {
    List<String> cmds = []
    if (isAqaraTVOC()) {
            // TODO - check what is available for VINDSTYRKA
	        cmds += zigbee.readAttribute(0x042a, 0x0000, [:], delay=200)                    // pm2.5    attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; 3:Tolerance
	        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }
        else if (false) {
            // TODO - check what is available for Aqara 
        }
        else {
            // TODO - unknown AirQuaility sensor - try all ??
        }
    
    logDebug "refreshAirQuality() : ${cmds}"
    return cmds
}

def initVarsAirQuality(boolean fullInit=false) {
    logDebug "initVarsAirQuality(${fullInit})"
    if (fullInit || settings?.airQualityIndexCheckInterval == null) device.updateSetting('airQualityIndexCheckInterval', [value: AirQualityIndexCheckIntervalOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.TemperatureScaleOpts == null) device.updateSetting('temperatureScale', [value: TemperatureScaleOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.tVocUnut == null) device.updateSetting('tVocUnut', [value: TvocUnitOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.pm25Threshold == null) device.updateSetting("pm25Threshold", [value:DEFAULT_PM25_THRESHOLD, type:"number"])
    if (fullInit || settings?.airQualityIndexThreshold == null) device.updateSetting("airQualityIndexThreshold", [value:DEFAULT_AIR_QUALITY_INDEX_THRESHOLD, type:"number"])

    if (isVINDSTYRKA()) {     // 09/02/2023 removed airQualityLevel, replaced airQualityIndex w/ sensirionVOCindex
        device.deleteCurrentState("airQualityLevel") 
        device.deleteCurrentState("airQualityIndex") 
    }     
    
}

void initEventsAirQuality(boolean fullInit=false) {
    // nothing to do
}
