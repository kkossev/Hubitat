/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateStringLiteral, ImplicitClosureParameter, MethodCount, MethodSize, NglParseError, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessarySetter, UnusedImport */
/**
 *  Aqara Climate Sensor W100 - Device Driver for Hubitat Elevation
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
 * ver. 1.0.0  2025-07-23 kkossev  - Initial version
 *
 *                        TODO: 0x0168 and 0x016F attributes (alarms)
 *                        TODO: add support for external temperature and humidity sensors
 *                        TODO: add support for battery level reporting
 *                        TODO: foundMap.advanced == true && settings.advancedOptions != true
 */

static String version() { '1.0.0' }
static String timeStamp() { '2025/07/24 11:14 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import java.math.RoundingMode

#include kkossev.commonLib
#include kkossev.batteryLib
#include kkossev.temperatureLib
#include kkossev.humidityLib
#include kkossev.xiaomiLib
#include kkossev.deviceProfileLib
//#include kkossev.thermostatLib

deviceType = 'Sensor' // Aqara Climate Sensor W100 is not a Thermostat, but a Temperature and Humidity Sensor
@Field static final String DEVICE_TYPE = 'Sensor'

metadata {
    definition(
        name: 'Aqara Climate Sensor W100',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Aqara%20Climate%20Sensor%20W100/Aqara_Climate_Sensor_W100_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        attribute 'displayOff', 'enum', ['disabled', 'enabled']   // 0xFCC0:0x0173
        attribute 'highTemperature', 'decimal'                    // 0xFCC0:0x0167
        attribute 'lowTemperature', 'decimal'                     // 0xFCC0:0x0166
        attribute 'highHumidity', 'decimal'                       // 0xFCC0:0x0169
        attribute 'lowHumidity', 'decimal'                        // 0xFCC0:0x016A
        attribute 'sampling', 'enum', ['low', 'standard', 'high', 'custom'] // 0xFCC0:0x0170
        attribute 'period', 'decimal'                             // 0xFCC0:0x016D
        attribute 'tempReportMode', 'enum', ['no', 'threshold', 'period', 'threshold_period'] // 0xFCC0:0x0165
        attribute 'tempPeriod', 'decimal'                         // 0xFCC0:0x0163
        attribute 'tempThreshold', 'decimal'                      // 0xFCC0:0x0164
        attribute 'humiReportMode', 'enum', ['no', 'threshold', 'period', 'threshold_period'] // 0xFCC0:0x016C
        attribute 'humiPeriod', 'decimal'                         // 0xFCC0:0x016A
        attribute 'humiThreshold', 'decimal'                      // 0xFCC0:0x016B
        attribute 'sensor', 'enum', ['internal', 'external']       // 0xFCC0:0x0172
        attribute 'externalTemperature', 'decimal'                // Virtual attribute for external temperature
        attribute 'externalHumidity', 'decimal'                   // Virtual attribute for external humidity
        attribute 'powerOutageCount', 'number'                     // Power outage counter from 0x00F7 reports
        
        // Button capabilities
        capability 'PushableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'
        capability 'DoubleTapableButton'
        
        attribute 'numberOfButtons', 'number'
        attribute 'pushed', 'number'
        attribute 'held', 'number'
        attribute 'released', 'number'
        attribute 'doubleTapped', 'number'
        
        //command 'setExternalTemperature', [[name: 'temperature', type: 'NUMBER', description: 'External temperature value (-100 to 100°C)', range: '-100..100', required: true]]
        //command 'setExternalHumidity', [[name: 'humidity', type: 'NUMBER', description: 'External humidity value (0 to 100%)', range: '0..100', required: true]]
        //command 'setSensorMode', [[name: 'mode', type: 'ENUM', constraints: ['internal', 'external'], description: 'Set sensor mode: internal or external']]
        
        if (_DEBUG) { command 'testT', [[name: 'testT', type: 'STRING', description: 'testT', defaultValue : '']]  }

        // itterate through all the figerprints and add them on the fly
        deviceProfilesV3.each { profileName, profileMap ->
            if (profileMap.fingerprints != null) {
                profileMap.fingerprints.each {
                    fingerprint it
                }
            }
        }
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: 'Enables command logging.'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.'
        // the rest of the preferences are inputed from the deviceProfile maps in the deviceProfileLib
    }
}

@Field static final Map deviceProfilesV3 = [
    // https://www.aqara.com/en/product/climate-sensor-w100/
    // https://www.zigbee2mqtt.io/devices/TH-S04D.html
    // https://github.com/Koenkk/zigbee2mqtt/issues/27262
    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/master/src/devices/lumi.ts#L4571 
    // https://github.com/rohankapoorcom/zigbee-herdsman-converters/blob/753c114f428d36e8164837922ea0ac89039f0bf6/src/devices/lumi.ts#L4569 
    'AQARA_CLIMATE_SENSOR_W100'   : [
            description   : 'Aqara Climate Sensor W100',
            device        : [manufacturers: ['Aqara'], type: 'Sensor', powerSource: 'battery', isSleepy:false],
            //capabilities  : ['ThermostatHeatingSetpoint': true, 'ThermostatOperatingState': true, 'ThermostatSetpoint':true, 'ThermostatMode': true],
            capabilities  : ['ReportingConfiguration': false, 'TemperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'Battery': true, 'BatteryVoltage': true, 'Configuration': true, 'Refresh': true, 'HealthCheck': true],
            preferences   : ['displayOff':'0xFCC0:0x0173', 'highTemperature':'0xFCC0:0x0167', 'lowTemperature':'0xFCC0:0x0166', 'highHumidity':'0xFCC0:0x016E', 'lowHumidity':'0xFCC0:0x016D', 'sampling':'0xFCC0:0x0170', 'period':'0xFCC0:0x0162', 'tempReportMode':'0xFCC0:0x0165', 'tempPeriod':'0xFCC0:0x0163', 'tempThreshold':'0xFCC0:0x0164', 'humiReportMode':'0xFCC0:0x016C', 'humiPeriod':'0xFCC0:0x016A', 'humiThreshold':'0xFCC0:0x016B', 'sensor':'0xFCC0:0x0172'],
            fingerprints  : [
                [profileId:'0104', endpointId:'01', inClusters:'0012,0405,0402,00001,0003,0x0000,FD20', outClusters:'0019', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100'],      //  "TH-S04D" - main endpoint
                [profileId:'0104', endpointId:'02', inClusters:'0012', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100'],      //  center button endpoint
                [profileId:'0104', endpointId:'03', inClusters:'0012', model:'lumi.sensor_ht.agl001', manufacturer:'Aqara', deviceJoinName: 'Aqara Climate Sensor W100']      //  minus button endpoint

            ],
            commands      : ['sendSupportedThermostatModes':'sendSupportedThermostatModes', 'autoPollThermostat':'autoPollThermostat', 'resetStats':'resetStats', 'refresh':'refresh', 'initialize':'initialize', 'updateAllPreferences': 'updateAllPreferences', 'resetPreferencesToDefaults':'resetPreferencesToDefaults', 'validateAndFixPreferences':'validateAndFixPreferences'],
            tuyaDPs       : [:],
            attributes    : [
                [at:'0xFCC0:0x0173',  name:'displayOff',       ep:'0x01', type:'enum',    dt:'0x10', mfgCode:'0x115f',  rw: 'rw', min:0,     max:1,     step:1,   scale:1,    map:[0: 'disabled', 1: 'enabled'], unit:'',     title: '<b>Display Off</b>',      description:'Enables/disables auto display off'],
                [at:'0xFCC0:0x0167',  name:'highTemperature',  ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:26.0,  max:60.0,  step:0.5, scale:100,  unit:'°C', title: '<b>High Temperature</b>', description:'High temperature alert'],
                [at:'0xFCC0:0x0166',  name:'lowTemperature',   ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:-20.0, max:20.0,  step:0.5, scale:100,  unit:'°C', title: '<b>Low Temperature</b>', description:'Low temperature alert'],
                [at:'0xFCC0:0x016E',  name:'highHumidity',     ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:65.0,  max:100.0, step:1.0, scale:100,  unit:'%',   title: '<b>High Humidity</b>',   description:'High humidity alert'],
                [at:'0xFCC0:0x016D',  name:'lowHumidity',      ep:'0x01', type:'decimal', dt:'0x29', mfgCode:'0x115f',  rw: 'rw', min:0.0,   max:30.0,  step:1.0, scale:100,  unit:'%',   title: '<b>Low Humidity</b>',    description:'Low humidity alert'],
                [at:'0xFCC0:0x0170',  name:'sampling',          ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:1,     max:4,     step:1,   scale:1,    map:[1: 'low', 2: 'standard', 3: 'high', 4: 'custom'], unit:'', title: '<b>Sampling</b>', description:'Temperature and Humidity sampling settings'],
                [at:'0xFCC0:0x0162',  name:'period',            ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:0.5,   max:600.0, step:0.5, scale:1000, unit:'sec', title: '<b>Sampling Period</b>', description:'Sampling period'], // result['period'] = (value / 1000).toFixed(1); - rw
                [at:'0xFCC0:0x0165',  name:'tempReportMode',  ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'no', 1: 'threshold', 2: 'period', 3: 'threshold_period'], unit:'', title: '<b>Temperature Report Mode</b>', description:'Temperature reporting mode'],
                [at:'0xFCC0:0x0163',  name:'tempPeriod',       ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:1.0,   max:10.0,  step:1.0, scale:1000, unit:'sec', title: '<b>Temperature Period</b>', description:'Temperature reporting period'],
                [at:'0xFCC0:0x0164',  name:'tempThreshold',    ep:'0x01', type:'decimal', dt:'0x21', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:0.1, scale:100,  unit:'°C', title: '<b>Temperature Threshold</b>', description:'Temperature reporting threshold'],
                [at:'0xFCC0:0x016C',  name:'humiReportMode',  ep:'0x01', type:'enum',    dt:'0x20', mfgCode:'0x115f',  rw: 'rw', min:0,     max:3,     step:1,   scale:1,    map:[0: 'no', 1: 'threshold', 2: 'period', 3: 'threshold_period'], unit:'', title: '<b>Humidity Report Mode</b>', description:'Humidity reporting mode'],
                [at:'0xFCC0:0x016A',  name:'humiPeriod',       ep:'0x01', type:'decimal', dt:'0x23', mfgCode:'0x115f',  rw: 'rw', min:1.0,   max:10.0,  step:1.0, scale:1000, unit:'sec', title: '<b>Humidity Period</b>', description:'Humidity reporting period'],
                [at:'0xFCC0:0x016B',  name:'humiThreshold',    ep:'0x01', type:'decimal', dt:'0x21', mfgCode:'0x115f',  rw: 'rw', min:2.0,   max:10.0,  step:0.5, scale:100,  unit:'%', title: '<b>Humidity Threshold</b>', description:'Humidity reporting threshold'],
                [at:'0xFCC0:0x0172',  name:'sensor',            ep:'0x01', type:'enum',    dt:'0x23', mfgCode:'0x115f',  rw: 'ro', min:0,     max:255,   step:1,   scale:1,    map:[0: 'internal', 1: 'internal', 2: 'external', 3: 'external', 255: 'unknown'], unit:'', title: '<b>Sensor Mode</b>', description:'Select sensor mode: internal or external'],
            ],
            //supportedThermostatModes: ['off', 'auto', 'heat', 'away'/*, "emergency heat"*/],
            //refresh: ['refreshAqaraE1'],
            deviceJoinName: 'Aqara Climate Sensor W100',
            configuration : [:]
    ]
]

// called from commonLib (Xiaomi cluster 0xFCC0 )
//
void customParseXiaomiFCC0Cluster(final Map descMap) {
    logDebug "customParseXiaomiFCC0Cluster: zigbee received 0xFCC0 attribute 0x${descMap.attrId} (raw value = ${descMap.value})"
    if ((descMap.attrInt as Integer) == 0x00F7 ) {      // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
        final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
        customParseXiaomiClusterTags(tags)
        return
    }
    
    // Handle external sensor response attribute 0xFFF2
    if ((descMap.attrInt as Integer) == 0xFFF2 ) {
        logDebug "customParseXiaomiFCC0Cluster: received external sensor response attribute 0xFFF2"
        parseExternalSensorResponse(descMap.value)
        return
    }
    
    Boolean result = processClusterAttributeFromDeviceProfile(descMap)
    if ( result == false ) {
        logWarn "customParseXiaomiFCC0Cluster: received unknown Thermostat cluster (0xFCC0) attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
// called from customParseXiaomiFCC0Cluster
//
void customParseXiaomiClusterTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        log.trace "xiaomi decode tag: 0x${intToHexStr(tag, 1)} value=${value}"
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})"
                // Send battery voltage event using the battery library
                sendBatteryVoltageEvent(value as Integer)
                if ((settings.voltageToPercent ?: false) == true) {
                    sendBatteryVoltageEvent(value as Integer, convertToPercent = true)
                }
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
            case 0x0d:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} sensor mode/state is ${value}"
                // May indicate sensor mode or operational state
                break
            case 0x11:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} trigger count is ${value}"
                // Button press or sensor trigger count
                if (state.health == null) { state.health = [:] }
                state.health['triggerCount'] = value
                break
            case 0x64:  // (100)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100}°C (raw ${value})"
                // W100 main temperature sensor reading
                //handleTemperatureEvent(value / 100.0)
                break
            case 0x65:  // (101)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100}% (raw ${value})"
                // W100 humidity sensor reading
                //handleHumidityEvent(value / 100.0)
                break
            case 0x66:  // (102)    TODO - check if this is an alternative battery percentage reading ?
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature (alternate) is ${value / 100}°C (raw ${value})"
                // Alternative temperature reading - may be external sensor
                if (device.currentValue('sensor') == 'external') {
                    sendEvent(name: 'externalTemperature', value: value / 100.0, unit: '°C', 
                             descriptionText: "External temperature: ${value / 100.0}°C", type: 'physical')
                    logInfo "External temperature updated: ${value / 100.0}°C"
                }
                break
            case 0x67:  // (103)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity (alternate) is ${value / 100}% (raw ${value})"
                // Alternative humidity reading - may be external sensor
                if (device.currentValue('sensor') == 'external') {
                    sendEvent(name: 'externalHumidity', value: value / 100.0, unit: '%', 
                             descriptionText: "External humidity: ${value / 100.0}%", type: 'physical')
                    logInfo "External humidity updated: ${value / 100.0}%"
                }
                break
            case 0x68:  // (104)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power outage count is ${value}"
                // Power outage counter - useful for device health monitoring
                if (state.health == null) { state.health = [:] }
                state.health['powerOutageCount'] = value
                sendEvent(name: 'powerOutageCount', value: value, descriptionText: "Power outage count: ${value}", type: 'physical')
                break
            case 0x69:  // (105)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery percentage is ${value}% (raw ${value})"
                // Battery percentage from device (if available)
                if (value > 0 && value <= 100) {
                    sendBatteryPercentageEvent(value as Integer)
                }
                break
            case 0x6a:  // (106)
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device uptime is ${value} (raw ${value})"
                // Device uptime information
                if (state.health == null) { state.health = [:] }
                state.health['uptime'] = value
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

// Initialize button functionality
void initializeButtons() {
    logDebug "initializeButtons: setting up 3 buttons (plus, center, minus)"
    sendEvent(name: 'numberOfButtons', value: 3, descriptionText: 'Number of buttons set to 3', type: 'digital')
}

// Button parsing for W100 - based on GitHub zigbee2mqtt implementation
void parseButtonAction(final Map descMap) {
    // From GitHub: actionLookup: {hold: 0, single: 1, double: 2, release: 255}
    // endpointNames: ["plus", "center", "minus"] - endpoints 1, 2, 3
    
    logDebug "parseButtonAction: endpoint ${descMap.endpoint}, cluster ${descMap.cluster ?: descMap.clusterId}, attr ${descMap.attrId}, value ${descMap.value}"
    
    Integer endpoint = Integer.parseInt(descMap.endpoint ?: "01", 16)
    String buttonName = getButtonName(endpoint)
    
    if (buttonName == 'unknown') {
        logWarn "parseButtonAction: unknown button endpoint ${endpoint}"
        return
    }
    
    // Parse the action value - looking for cluster 0x0012 multistate input reports
    String cluster = descMap.cluster ?: descMap.clusterId
    if (cluster == '0012' && descMap.attrId == '0055') {
        Integer actionValue = Integer.parseInt(descMap.value, 16)
        String action = getButtonAction(actionValue)
        
        if (action != 'unknown') {
            logInfo "Button ${buttonName} (${endpoint}) ${action}"
            
            Map buttonEvent = [
                name: action,
                value: endpoint,
                descriptionText: "Button ${buttonName} (${endpoint}) ${action}",
                isStateChange: true,
                type: 'physical'
            ]
            
            sendEvent(buttonEvent)
        } else {
            logWarn "parseButtonAction: unknown action value ${actionValue} for button ${buttonName}"
        }
    } else {
        logDebug "parseButtonAction: skipping - cluster=${cluster}, attrId=${descMap.attrId}"
    }
}

// Get button name from endpoint number
String getButtonName(Integer endpoint) {
    switch (endpoint) {
        case 1: return 'plus'
        case 2: return 'center' 
        case 3: return 'minus'
        default: return 'unknown'
    }
}

// Get button action from value
String getButtonAction(Integer value) {
    // From GitHub actionLookup: {hold: 0, single: 1, double: 2, release: 255}
    switch (value) {
        case 0: return 'held'
        case 1: return 'pushed'
        case 2: return 'doubleTapped'
        case 255: return 'released'
        default: return 'unknown'
    }
}

// Custom parsing for multistate input cluster (0x0012) - button events
void customParseMultistateInputCluster(final Map descMap) {
    logDebug "customParseMultistateInputCluster: cluster 0x0012 endpoint ${descMap.endpoint} attribute 0x${descMap.attrId} value ${descMap.value}"
    
    // W100 buttons send multistate input events on cluster 0x0012, attribute 0x0055
    if (descMap.attrId == '0055') {
        parseButtonAction(descMap)
    } else {
        logDebug "customParseMultistateInputCluster: unhandled attribute 0x${descMap.attrId}"
    }
}

// Parse external sensor response from attribute 0xFFF2
void parseExternalSensorResponse(String value) {
    logDebug "parseExternalSensorResponse: parsing response value: ${value}"
    
    try {
        // The response should contain sensor mode information
        // Based on the GitHub implementation, we need to decode the response
        if (value?.length() >= 2) {
            // Convert hex string to integer for basic parsing
            Integer responseValue = Integer.parseInt(value.substring(0, 2), 16)
            
            // Parse sensor mode from response (based on GitHub lookup: {2: "external", 0: "internal", 1: "internal", 3: "external"})
            String sensorMode = null
            switch (responseValue) {
                case 0:
                case 1:
                    sensorMode = 'internal'
                    break
                case 2:
                case 3:
                    sensorMode = 'external'
                    break
                default:
                    logWarn "parseExternalSensorResponse: unknown sensor mode value: ${responseValue}"
                    return
            }
            
            // Update the sensor attribute if it changed
            if (device.currentValue('sensor') != sensorMode) {
                sendEvent(name: 'sensor', value: sensorMode, descriptionText: "Sensor mode changed to ${sensorMode}", type: 'physical')
                logInfo "Sensor mode updated to: ${sensorMode}"
            }
        }
    } catch (Exception e) {
        logWarn "parseExternalSensorResponse: error parsing response: ${e.message}"
    }
}


//
// called from updated() in the main code
void customUpdated() {
    //ArrayList<String> cmds = []
    logDebug 'customUpdated: ...'
    //
    if (settings?.forcedProfile != null) {
        //logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false)
            customInitializeVars(fullInit = false)
            resetPreferencesToDefaults(debug = true)
            logInfo 'press F5 to refresh the page'
        }
    }
    else {
        logDebug 'forcedProfile is not set'
    }
    /*
    final int pollingInterval = (settings.temperaturePollingInterval as Integer) ?: 0
    if (pollingInterval > 0) {
        logInfo "updatedThermostat: scheduling temperature polling every ${pollingInterval} seconds"
        scheduleThermostatPolling(pollingInterval)
    }
    else {
        unScheduleThermostatPolling()
        logInfo 'updatedThermostat: thermostat polling is disabled!'
    }
    */
    // Itterates through all settings
    logDebug 'customUpdated: updateAllPreferences()...'
    updateAllPreferences()
}


List<String> customRefresh() {
    // TODO - use the refreshFromDeviceProfileList() !
    /*
    List<String> cmds = refreshFromDeviceProfileList()
    */
    List<String> cmds = []
    
    // Standard Zigbee clusters
    cmds += zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x01], 200)    // temperature
    cmds += zigbee.readAttribute(0x0405, 0x0000, [destEndpoint: 0x01], 200)    // humidity
    
    // Aqara proprietary cluster attributes - grouped reads
    cmds += zigbee.readAttribute(0xFCC0, [0x0172, 0x0173], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // sensor, displayOff
    cmds += zigbee.readAttribute(0xFCC0, [0x0167, 0x0166], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // highTemperature, lowTemperature
    cmds += zigbee.readAttribute(0xFCC0, [0x016E, 0x016D], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // highHumidity, lowHumidity
    cmds += zigbee.readAttribute(0xFCC0, [0x0170, 0x0162], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // sampling, period
    cmds += zigbee.readAttribute(0xFCC0, [0x0165, 0x0163, 0x0164], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // tempReportMode, tempPeriod, tempThreshold
    cmds += zigbee.readAttribute(0xFCC0, [0x016C, 0x016A, 0x016B], [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // humiReportMode, humiPeriod, humiThreshold
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [destEndpoint: 0x01, mfgCode: 0x115F], 200)    // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
    logDebug "customRefresh: ${cmds} "
    return cmds
}

List<String> customConfigure() {
    List<String> cmds = []
    logDebug "customConfigure() : ${cmds} (not implemented!)"
    return cmds
}

List<String> initializeAqara() {
    List<String> cmds = []
    logDebug 'initializeAqara() ...'
    return cmds
}

// called from initializeDevice in the commonLib code
List<String> customInitializeDevice() {
    List<String> cmds = []
    logDebug 'customInitializeDevice() ...'
    cmds = initializeAqara()
    
    // Initialize button functionality
    initializeButtons()
    
    logDebug "initializeThermostat() : ${cmds}"
    return cmds
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }
    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
}

// called from initializeVars() in the main code ...
void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
}

List<String> customAqaraBlackMagic() {
    logDebug 'customAqaraBlackMagic() - not needed...'
    List<String> cmds = []
    return cmds
}

// called from processFoundItem  (processTuyaDPfromDeviceProfile and ) processClusterAttributeFromDeviceProfile in deviceProfileLib when a Zigbee message was found defined in the device profile map
//
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */
void customProcessDeviceProfileEvent(final Map descMap, final String name, final valueScaled, final String unitText, final String descText) {
    logTrace "customProcessDeviceProfileEvent(${name}, ${valueScaled}) called"
    Map eventMap = [name: name, value: valueScaled, unit: unitText, descriptionText: descText, type: 'physical', isStateChange: true]
    switch (name) {
        case 'temperature' :
            handleTemperatureEvent(valueScaled as Float)
            break
        case 'sensor' :    // Sensor mode selection
            sendEvent(eventMap)
            logInfo "${descText}"
            
            // When switching to internal mode, we might want to trigger a refresh of internal sensor readings
            if (valueScaled == 'internal') {
                logDebug "Sensor mode switched to internal - refreshing internal sensor readings"
                runInMillis(1000, customRefresh)
            }
            break
        default :
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event !
                //if (!doNotTrace) {
            logDebug "event ${name} sent w/ value ${valueScaled}"
            logInfo "${descText}"                                 // send an Info log also (because value changed )  // TODO - check whether Info log will be sent also for spammy DPs ?
            //}
            break
    }
}

// Command to set external temperature using GitHub-compliant protocol
// https://github.com/13717033460/zigbee-herdsman-converters/blob/4ac6b66c7dd37e7b90a7487f3aa796f491dd24af/src/lib/lumi.ts#L2534
void setExternalTemperature(BigDecimal temperature) {
    logDebug "setExternalTemperature(${temperature}) - START"
    
    String currentSensorMode = device.currentValue('sensor')
    logDebug "setExternalTemperature: current sensor mode = '${currentSensorMode}'"
    if (currentSensorMode != 'external') {
        logWarn "setExternalTemperature: sensor mode is '${currentSensorMode}', must be 'external' to set external temperature"
        //return
    }
    
    if (temperature < -100 || temperature > 100) {
        logWarn "setExternalTemperature: temperature ${temperature} is out of range (-100 to 100°C)"
        //return
    }
    
    List<String> cmds = []
    
    // Fixed fictive sensor IEEE address from GitHub implementation
    byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
    logDebug "setExternalTemperature: fictiveSensor = ${fictiveSensor.collect { String.format('%02X', it & 0xFF) }.join('')}"
    
    // Build temperature buffer using writeFloatBE like GitHub implementation
    byte[] temperatureBuf = new byte[4]
    Float tempValue = Math.round(temperature * 100) as Float
    logDebug "setExternalTemperature: tempValue (Float) = ${tempValue}"
    
    // Convert to IEEE 754 32-bit float and write as big-endian (GitHub uses writeFloatBE)
    int floatBits = Float.floatToIntBits(tempValue)
    logDebug "setExternalTemperature: IEEE 754 floatBits = 0x${String.format('%08X', floatBits)} (${floatBits})"
    temperatureBuf[0] = (byte)((floatBits >> 24) & 0xFF)
    temperatureBuf[1] = (byte)((floatBits >> 16) & 0xFF)
    temperatureBuf[2] = (byte)((floatBits >> 8) & 0xFF)
    temperatureBuf[3] = (byte)(floatBits & 0xFF)
    logDebug "setExternalTemperature: temperatureBuf bytes = ${temperatureBuf.collect { String.format('%02X', it & 0xFF) }.join('')}"
    
    // Build params array exactly like GitHub: [...fictiveSensor, 0x00, 0x01, 0x00, 0x55, ...temperatureBuf]
    List<Integer> params = []
    params.addAll(fictiveSensor.collect { it & 0xFF })
    logDebug "setExternalTemperature: params after fictiveSensor = ${params.collect { String.format('%02X', it) }.join('')}"
    params.addAll([0x00, 0x01, 0x00, 0x55])
    logDebug "setExternalTemperature: params after temp identifier = ${params.collect { String.format('%02X', it) }.join('')}"
    params.addAll(temperatureBuf.collect { it & 0xFF })
    logDebug "setExternalTemperature: final params (${params.size()} bytes) = ${params.collect { String.format('%02X', it) }.join('')}"
    
    // Build complete message using GitHub's lumiHeader function
    List<Integer> data = buildLumiHeader(0x12, params.size(), 0x05)
    logDebug "setExternalTemperature: lumiHeader = ${data.collect { String.format('%02X', it) }.join('')}"
    data.addAll(params)
    logDebug "setExternalTemperature: complete data (${data.size()} bytes) = ${data.collect { String.format('%02X', it) }.join('')}"
    
    String hexString = data.collect { String.format('%02X', it) }.join('')
    logDebug "setExternalTemperature: final hexString length = ${hexString.length()} chars"
    
    //String command = "he wattr 0x${device.deviceNetworkId} 0x01 0xFCC0 0xFFF2 0x41 {${hexString}} {0x115F}"
    String command = zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, 0x41, hexString, [mfgCode: 0x115F])
    cmds += [command]
    logDebug "setExternalTemperature: Zigbee command = ${command}"
    
    logDebug "setExternalTemperature: sending GitHub-compliant command: ${hexString}"
    sendZigbeeCommands(cmds)
    
    // Update the state
    //sendEvent(name: 'external_temperature', value: temperature, unit: '°C', descriptionText: "External temperature set to ${temperature}°C", type: 'digital')
    logDebug "setExternalTemperature(${temperature}) - COMPLETED"
}

// Command to set external humidity using GitHub-compliant protocol
void setExternalHumidity(BigDecimal humidity) {
    logDebug "setExternalHumidity(${humidity})"
    
    String currentSensorMode = device.currentValue('sensor')
    if (currentSensorMode != 'external') {
        logWarn "setExternalHumidity: sensor mode is '${currentSensorMode}', must be 'external' to set external humidity"
        return
    }
    
    if (humidity < 0 || humidity > 100) {
        logWarn "setExternalHumidity: humidity ${humidity} is out of range (0 to 100%)"
        return
    }
    
    List<String> cmds = []
    
    // Fixed fictive sensor IEEE address from GitHub implementation
    byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
    
    // Build humidity buffer using writeFloatBE like GitHub implementation
    byte[] humidityBuf = new byte[4]
    Float humiValue = humidity as Float
    
    // Convert to IEEE 754 32-bit float and write as big-endian (GitHub uses writeFloatBE)
    int floatBits = Float.floatToIntBits(humiValue)
    humidityBuf[0] = (byte)((floatBits >> 24) & 0xFF)
    humidityBuf[1] = (byte)((floatBits >> 16) & 0xFF)
    humidityBuf[2] = (byte)((floatBits >> 8) & 0xFF)
    humidityBuf[3] = (byte)(floatBits & 0xFF)
    
    // Build params array exactly like GitHub: [...fictiveSensor, 0x00, 0x02, 0x00, 0x55, ...humidityBuf]
    List<Integer> params = []
    params.addAll(fictiveSensor.collect { it & 0xFF })
    params.addAll([0x00, 0x02, 0x00, 0x55])  // 0x02 indicates humidity
    params.addAll(humidityBuf.collect { it & 0xFF })
    
    // Build complete message using GitHub's lumiHeader function
    List<Integer> data = buildLumiHeader(0x12, params.size(), 0x05)
    data.addAll(params)
    
    String hexString = data.collect { String.format('%02X', it) }.join('')
    
    cmds += ["he wattr 0x${device.deviceNetworkId} 0x01 0xFCC0 0xFFF2 0x41 {${hexString}} {0x115F}"]
    
    logDebug "setExternalHumidity: sending GitHub-compliant command: ${hexString}"
    sendZigbeeCommands(cmds)
    
    // Update the state
    sendEvent(name: 'externalHumidity', value: humidity, unit: '%', descriptionText: "External humidity set to ${humidity}%", type: 'digital')
}

// Build Lumi header based on GitHub implementation
// https://github.com/13717033460/zigbee-herdsman-converters/blob/4ac6b66c7dd37e7b90a7487f3aa796f491dd24af/src/lib/lumi.ts#L2403
private List<Integer> buildLumiHeader(Integer counter, Integer paramsLength, Integer action) {
    // const lumiHeader = (counter: number, length: number, action: number) => {
    //     const header = [0xaa, 0x71, length + 3, 0x44, counter];
    //     const integrity = 512 - header.reduce((sum, elem) => sum + elem, 0);
    //     return [...header, integrity, action, 0x41, length];
    // };
    
    List<Integer> header = [0xaa, 0x71, paramsLength + 3, 0x44, counter]
    Integer integrity = 512 - header.sum()
    List<Integer> result = []
    result.addAll(header)
    result.addAll([integrity, action, 0x41, paramsLength])
    return result
}

ArrayList zigbeeWriteLongAttribute(Integer cluster, Integer attributeId, Integer dataType, String value, Map additionalParams = [:], int delay = 2000) {
    String mfgCode = ""
    if (additionalParams.containsKey("mfgCode")) {
        Integer code = additionalParams.get("mfgCode") as Integer
        mfgCode = " {${HexUtils.integerToHexString(code, 2)}}"
    }
    String wattrArgs = "0x${device.deviceNetworkId} 0x01 0x${HexUtils.integerToHexString(cluster, 2)} " + 
                       "0x${HexUtils.integerToHexString(attributeId, 2)} " + 
                       "0x${HexUtils.integerToHexString(dataType, 1)} " + 
                       "{${value}}" + 
                       "$mfgCode"
    ArrayList cmdList = ["he wattr $wattrArgs", "delay $delay"]
    return cmdList
}

// Command to set sensor mode (internal/external) using GitHub implementation
void setSensorMode(String mode) {
    logDebug "setSensorMode(${mode})"
    
    if (mode != 'internal' && mode != 'external') {
        logWarn "setSensorMode: invalid mode '${mode}', must be 'internal' or 'external'"
        return
    }
    
    List<String> cmds = []
    
    if (mode == 'external') {
        // GitHub implementation for external mode - complex multi-step process
        String deviceIeee = device.zigbeeId
        log.trace "${deviceIeee} - deviceIeee"
        // Remove 0x prefix if present and ensure proper format
        String hexString = deviceIeee.startsWith('0x') ? deviceIeee.substring(2) : deviceIeee
        hexString = hexString.padLeft(16, '0')
        byte[] deviceBytes = hubitat.helper.HexUtils.hexStringToByteArray(hexString)
        log.trace "deviceBytes: ${deviceBytes.collect { String.format('%02X', it & 0xFF) }.join('')}"
        byte[] fictiveSensor = hubitat.helper.HexUtils.hexStringToByteArray("00158d00019d1b98")
        
        // Create timestamp
        byte[] timestamp = new byte[4]
        Long currentTime = (now() / 1000) as Long
        timestamp[0] = (byte)((currentTime >> 24) & 0xFF)
        timestamp[1] = (byte)((currentTime >> 16) & 0xFF)
        timestamp[2] = (byte)((currentTime >> 8) & 0xFF)
        timestamp[3] = (byte)(currentTime & 0xFF)
        
        // First command - params1 from GitHub
        List<Integer> params1 = []
        params1.addAll(timestamp.collect { it & 0xFF })
        params1.addAll([0x15])
        params1.addAll(deviceBytes.collect { it & 0xFF })
        params1.addAll(fictiveSensor.collect { it & 0xFF })
        params1.addAll([0x00, 0x02, 0x00, 0x55, 0x15, 0x0a, 0x01, 0x00, 0x00, 0x01, 0x06, 0xe6, 0xb9, 0xbf, 0xe5, 0xba, 0xa6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x08, 0x65])
        
        List<Integer> val1 = buildLumiHeader(0x12, params1.size(), 0x04)
        val1.addAll(params1)
        
        // Second command - params2 from GitHub  
        List<Integer> params2 = []
        params2.addAll(timestamp.collect { it & 0xFF })
        params2.addAll([0x14])
        params2.addAll(deviceBytes.collect { it & 0xFF })
        params2.addAll(fictiveSensor.collect { it & 0xFF })
        params2.addAll([0x00, 0x01, 0x00, 0x55, 0x15, 0x0a, 0x01, 0x00, 0x00, 0x01, 0x06, 0xe6, 0xb8, 0xa9, 0xe5, 0xba, 0xa6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x07, 0x63])
        
        List<Integer> val2 = buildLumiHeader(0x13, params2.size(), 0x04)
        val2.addAll(params2)
        
        String hexString1 = val1.collect { String.format('%02X', it) }.join('')
        String hexString2 = val2.collect { String.format('%02X', it) }.join('')
        
        //cmds += ["he wattr 0x${device.deviceNetworkId} 0x01 0xFCC0 0xFFF2 0x41 {${hexString1}} {0x115F}"]
        //cmds += ["he wattr 0x${device.deviceNetworkId} 0x01 0xFCC0 0xFFF2 0x41 {${hexString2}} {0x115F}"]
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, 0x41, hexString1, [mfgCode: 0x115F])
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, 0x41, hexString2, [mfgCode: 0x115F])
        
        logDebug "setSensorMode: sending external mode setup commands"
        sendZigbeeCommands(cmds)
        
        // Read sensor mode after setup
       //runInMillis(3000, 'readSensorModeAfterSetup')
        
    } else {
        // Internal mode - GitHub implementation for internal mode
        String deviceIeee = device.zigbeeId
        log.trace "${deviceIeee} - deviceIeee for internal mode"
        // Remove 0x prefix if present and ensure proper format
        String hexString = deviceIeee.startsWith('0x') ? deviceIeee.substring(2) : deviceIeee
        hexString = hexString.padLeft(16, '0')
        byte[] deviceBytes = hubitat.helper.HexUtils.hexStringToByteArray(hexString)
        log.trace "deviceBytes: ${deviceBytes.collect { String.format('%02X', it & 0xFF) }.join('')}"
        
        // Create timestamp
        byte[] timestamp = new byte[4]
        Long currentTime = (now() / 1000) as Long
        timestamp[0] = (byte)((currentTime >> 24) & 0xFF)
        timestamp[1] = (byte)((currentTime >> 16) & 0xFF)
        timestamp[2] = (byte)((currentTime >> 8) & 0xFF)
        timestamp[3] = (byte)(currentTime & 0xFF)
        
        // First command - params1 for internal mode (based on GitHub lines 2488-2533)
        List<Integer> params1 = []
        params1.addAll(timestamp.collect { it & 0xFF })
        params1.addAll([0x3d])
        params1.addAll([0x05])
        params1.addAll(deviceBytes.collect { it & 0xFF })
        params1.addAll([0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        List<Integer> val1 = buildLumiHeader(0x12, params1.size(), 0x04)
        val1.addAll(params1)
        
        // Second command - params2 for internal mode
        List<Integer> params2 = []
        params2.addAll(timestamp.collect { it & 0xFF })
        params2.addAll([0x3d])
        params2.addAll([0x04])
        params2.addAll(deviceBytes.collect { it & 0xFF })
        params2.addAll([0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        List<Integer> val2 = buildLumiHeader(0x13, params2.size(), 0x04)
        val2.addAll(params2)
        
        String hexString1 = val1.collect { String.format('%02X', it) }.join('')
        String hexString2 = val2.collect { String.format('%02X', it) }.join('')
        
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, 0x41, hexString1, [mfgCode: 0x115F])
        cmds += zigbeeWriteLongAttribute(0xFCC0, 0xFFF2, 0x41, hexString2, [mfgCode: 0x115F])
        
        logDebug "setSensorMode: sending internal mode setup commands"
        sendZigbeeCommands(cmds)
        
        // Read sensor mode after switching to internal mode
        runInMillis(5000, 'readSensorModeAfterSetup')
    }
    
    // Update the state immediately for UI responsiveness
    sendEvent(name: 'sensor', value: mode, descriptionText: "Sensor mode set to ${mode}", type: 'digital')
}

// Helper to read sensor mode after external setup
void readSensorModeAfterSetup() {
    logDebug "readSensorModeAfterSetup: reading sensor mode attribute"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, 0x0172, [destEndpoint: 0x01, mfgCode: 0x115F])
    sendZigbeeCommands(cmds)
}

// Helper to read local temperature after switching to internal mode
void readLocalTemperature() {
    logDebug "readLocalTemperature: reading local temperature from hvacThermostat cluster"
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0201, 0x0000, [destEndpoint: 0x01])  // localTemp from hvacThermostat
    sendZigbeeCommands(cmds)
}

void testT(String par) {

    logInfo "Test function called with parameter: ${par}"
    String xx = "read attr - raw: 80E901FCC05AF700412903281E04210000052105000A213BE00C200A0D23250E00001320006429B80B6521110C662064672000, dni: 80E9, endpoint: 01, cluster: FCC0, size: 5A, attrId: 00F7, encoding: 41, command: 0A, value: 2903281E04210000052105000A213BE00C200A0D23250E00001320006429B80B6521110C662064672000"
    
    parse(xx)
    
    logDebug "testT(${par}) - COMPLETED"   
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
