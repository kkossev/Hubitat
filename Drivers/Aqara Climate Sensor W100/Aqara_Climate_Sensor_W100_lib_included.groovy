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
        attribute 'supportedButtonValues', 'JSON_OBJECT'
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
    sendNumberOfButtonsEvent(3)
    sendSupportedButtonValuesEvent(['pushed', 'held', 'doubleTapped', 'released'])
}

// Custom push implementation for buttonLib Momentary capability
void customPush() {
    logDebug "customPush: momentary push for W100"
    // For W100, we can simulate pushing the center button (button 2)
    sendButtonEvent(2, 'pushed', isDigital = true)
}

// Custom push implementation for buttonLib PushableButton capability
void customPush(Integer buttonNumber) {
    logDebug "customPush: simulating push for button ${buttonNumber}"
    if (buttonNumber >= 1 && buttonNumber <= 3) {
        sendButtonEvent(buttonNumber, 'pushed', isDigital = true)
    } else {
        logWarn "customPush: invalid button number ${buttonNumber}, valid range is 1-3"
    }
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
            
            // Use buttonLib's sendButtonEvent function
            sendButtonEvent(endpoint, action, isDigital = false)
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

ArrayList zigbeeWriteLongAttribute(Integer cluster, Integer attributeId, String value, Map additionalParams = [:], int delay = 2000) {
    String mfgCode = ""
    if (additionalParams.containsKey("mfgCode")) {
        Integer code = additionalParams.get("mfgCode") as Integer
        mfgCode = " {${HexUtils.integerToHexString(code, 2)}}"
    }
    String wattrArgs = "0x${device.deviceNetworkId} 0x01 0x${HexUtils.integerToHexString(cluster, 2)} " + 
                       "0x${HexUtils.integerToHexString(attributeId, 2)} " + 
                       "0x41 " + 
                       Integer.toHexString(payload.length() / 2).padLeft(2, '0')
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

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '3.4.0' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 24
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 25
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 26
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 27
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 28
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 29
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 30
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 31
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 32
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 33
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 34
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 35
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-06-05 kkossev  - 4 in 1 V3 compatibility; added IAS cluster; setDeviceNameAndProfile() fix; // library marker kkossev.commonLib, line 37
  * ver. 3.2.2  2024-06-12 kkossev  - removed isAqaraTRV_OLD() and isAqaraTVOC_OLD() dependencies from the lib; added timeToHMS(); metering and electricalMeasure clusters swapped bug fix; added cluster 0x0204; // library marker kkossev.commonLib, line 38
  * ver. 3.3.0  2024-06-25 kkossev  - fixed exception for unknown clusters; added cluster 0xE001; added powerSource - if 5 minutes after initialize() the powerSource is still unknown, query the device for the powerSource // library marker kkossev.commonLib, line 39
  * ver. 3.3.1  2024-07-06 kkossev  - removed isFingerbot() dependancy; added FC03 cluster (Frient); removed noDef from the linter; added customParseIasMessage and standardParseIasMessage; powerSource set to unknown on initialize(); add 0x0007 onOffConfiguration cluster'; // library marker kkossev.commonLib, line 40
  * ver. 3.3.2  2024-07-12 kkossev  - added PollControl (0x0020) cluster; ping for SONOFF // library marker kkossev.commonLib, line 41
  * ver. 3.3.3  2024-09-15 kkossev  - added queryAllTuyaDP(); 2 minutes healthCheck option; // library marker kkossev.commonLib, line 42
  * ver. 3.3.4  2025-01-29 kkossev  - 'LOAD ALL DEFAULTS' is the default Configure command. // library marker kkossev.commonLib, line 43
  * ver. 3.3.5  2025-03-05 kkossev  - getTuyaAttributeValue made public; fixed checkDriverVersion bug on hub reboot. // library marker kkossev.commonLib, line 44
  * ver. 3.4.0  2025-03-23 kkossev  - healthCheck by pinging the device; updateRxStats() replaced with inline code; added state.lastRx.timeStamp; added activeEndpoints() handler call; documentation improvements // library marker kkossev.commonLib, line 45
  * ver. 3.5.0  2025-04-08 kkossev  - urgent fix for java.lang.CloneNotSupportedException // library marker kkossev.commonLib, line 46
  * ver. 3.5.1  2025-07-23 kkossev  - Aqara W100 destEndpoint: 0x01 patch // library marker kkossev.commonLib, line 47
  * // library marker kkossev.commonLib, line 48
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 49
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 50
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 51
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 52
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 53
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 54
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 55
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 56
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 57
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 58
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 59
  * // library marker kkossev.commonLib, line 60
*/ // library marker kkossev.commonLib, line 61

String commonLibVersion() { '3.5.1' } // library marker kkossev.commonLib, line 63
String commonLibStamp() { '2025/07/23 12:47 PM' } // library marker kkossev.commonLib, line 64

import groovy.transform.Field // library marker kkossev.commonLib, line 66
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 67
import hubitat.device.Protocol // library marker kkossev.commonLib, line 68
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 69
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 70
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 71
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 72
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 73
import java.math.BigDecimal // library marker kkossev.commonLib, line 74

metadata { // library marker kkossev.commonLib, line 76
        if (_DEBUG) { // library marker kkossev.commonLib, line 77
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 78
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 79
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 80
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 81
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 82
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 83
            ] // library marker kkossev.commonLib, line 84
        } // library marker kkossev.commonLib, line 85

        // common capabilities for all device types // library marker kkossev.commonLib, line 87
        capability 'Configuration' // library marker kkossev.commonLib, line 88
        capability 'Refresh' // library marker kkossev.commonLib, line 89
        capability 'HealthCheck' // library marker kkossev.commonLib, line 90
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 91

        // common attributes for all device types // library marker kkossev.commonLib, line 93
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 94
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 95
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 96

        // common commands for all device types // library marker kkossev.commonLib, line 98
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 99

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 101
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 102

    preferences { // library marker kkossev.commonLib, line 104
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 105
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 106
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 107

        if (device) { // library marker kkossev.commonLib, line 109
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device...', defaultValue: false // library marker kkossev.commonLib, line 110
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 111
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 112
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 113
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 114
            } // library marker kkossev.commonLib, line 115
        } // library marker kkossev.commonLib, line 116
    } // library marker kkossev.commonLib, line 117
} // library marker kkossev.commonLib, line 118

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 120
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 121
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 122
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 123
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 124
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 125
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 126
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 127
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 128
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 129
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 130

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 132
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 135
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 139
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 140
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 141
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 142
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 143
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 144
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 145
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 146
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 147
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 148
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 149
] // library marker kkossev.commonLib, line 150

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 152

/** // library marker kkossev.commonLib, line 154
 * Parse Zigbee message // library marker kkossev.commonLib, line 155
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 156
 */ // library marker kkossev.commonLib, line 157
public void parse(final String description) { // library marker kkossev.commonLib, line 158
    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 159
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 160
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 161
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 162
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 163
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 164

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 166
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 167
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 168
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 169
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 170
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 171
        return // library marker kkossev.commonLib, line 172
    } // library marker kkossev.commonLib, line 173
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 174
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 175
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 176
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 177
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 178
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 179
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 180
        return // library marker kkossev.commonLib, line 181
    } // library marker kkossev.commonLib, line 182

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 184
        return // library marker kkossev.commonLib, line 185
    } // library marker kkossev.commonLib, line 186
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 187

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 189
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 190

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 192
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 193
        return // library marker kkossev.commonLib, line 194
    } // library marker kkossev.commonLib, line 195
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 196
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 197
        return // library marker kkossev.commonLib, line 198
    } // library marker kkossev.commonLib, line 199
    // // library marker kkossev.commonLib, line 200
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 201
    // // library marker kkossev.commonLib, line 202
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 203
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 204
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 205
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 206
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 207
            } // library marker kkossev.commonLib, line 208
            break // library marker kkossev.commonLib, line 209
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 210
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 211
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 212
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 213
            } // library marker kkossev.commonLib, line 214
            break // library marker kkossev.commonLib, line 215
        default: // library marker kkossev.commonLib, line 216
            if (settings.logEnable) { // library marker kkossev.commonLib, line 217
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 218
            } // library marker kkossev.commonLib, line 219
            break // library marker kkossev.commonLib, line 220
    } // library marker kkossev.commonLib, line 221
} // library marker kkossev.commonLib, line 222

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 224
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 225
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 226
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 227
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 228
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 229
] // library marker kkossev.commonLib, line 230

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 232
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 233
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 234
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 235
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 236
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 237
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 238
        return false // library marker kkossev.commonLib, line 239
    } // library marker kkossev.commonLib, line 240
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 241
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 242
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 243
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 244
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 245
        return true // library marker kkossev.commonLib, line 246
    } // library marker kkossev.commonLib, line 247
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 248
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 249
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 250
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 251
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 252
        return true // library marker kkossev.commonLib, line 253
    } // library marker kkossev.commonLib, line 254
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 255
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 256
    } // library marker kkossev.commonLib, line 257
    return false // library marker kkossev.commonLib, line 258
} // library marker kkossev.commonLib, line 259

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 261
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 262
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 263
} // library marker kkossev.commonLib, line 264

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 266
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 267
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 268
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 269
    } // library marker kkossev.commonLib, line 270
    return false // library marker kkossev.commonLib, line 271
} // library marker kkossev.commonLib, line 272

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 274
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 275
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 276
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 277
    } // library marker kkossev.commonLib, line 278
    return false // library marker kkossev.commonLib, line 279
} // library marker kkossev.commonLib, line 280

// not used? // library marker kkossev.commonLib, line 282
public boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 283
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 284
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 285
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 286
    } // library marker kkossev.commonLib, line 287
    return false // library marker kkossev.commonLib, line 288
} // library marker kkossev.commonLib, line 289

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 291
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 292
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 293
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 294
] // library marker kkossev.commonLib, line 295

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 297
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 298
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 299
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 300
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 301
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 302
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 303
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 304
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 305
    List<String> cmds = [] // library marker kkossev.commonLib, line 306
    switch (clusterId) { // library marker kkossev.commonLib, line 307
        case 0x0005 : // library marker kkossev.commonLib, line 308
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 309
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 310
            // send the active endpoint response // library marker kkossev.commonLib, line 311
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 312
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case 0x0006 : // library marker kkossev.commonLib, line 315
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 316
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 317
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 318
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 321
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 322
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 325
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 326
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 327
            break // library marker kkossev.commonLib, line 328
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 329
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 330
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 331
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 334
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 335
            break // library marker kkossev.commonLib, line 336
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 337
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 338
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 339
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 340
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        default : // library marker kkossev.commonLib, line 343
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
    } // library marker kkossev.commonLib, line 346
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 347
} // library marker kkossev.commonLib, line 348

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 350
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 351
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 352
    switch (commandId) { // library marker kkossev.commonLib, line 353
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 354
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 355
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 356
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 357
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 358
        default: // library marker kkossev.commonLib, line 359
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 360
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 361
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 362
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 363
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 364
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 365
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 366
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 367
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 368
            } // library marker kkossev.commonLib, line 369
            break // library marker kkossev.commonLib, line 370
    } // library marker kkossev.commonLib, line 371
} // library marker kkossev.commonLib, line 372

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 374
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 375
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 376
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 377
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 378
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 379
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 380
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 381
    } // library marker kkossev.commonLib, line 382
    else { // library marker kkossev.commonLib, line 383
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
} // library marker kkossev.commonLib, line 386

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 388
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 389
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 390
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 391
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 392
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 393
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 394
    } // library marker kkossev.commonLib, line 395
    else { // library marker kkossev.commonLib, line 396
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
} // library marker kkossev.commonLib, line 399

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 401
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 402
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 403
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 404
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 405
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 406
        state.reportingEnabled = true // library marker kkossev.commonLib, line 407
    } // library marker kkossev.commonLib, line 408
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 409
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 410
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 411
    } else { // library marker kkossev.commonLib, line 412
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 413
    } // library marker kkossev.commonLib, line 414
} // library marker kkossev.commonLib, line 415

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 417
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 418
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 419
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 420
    if (status == 0) { // library marker kkossev.commonLib, line 421
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 422
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 423
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 424
        int delta = 0 // library marker kkossev.commonLib, line 425
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 426
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 427
        } // library marker kkossev.commonLib, line 428
        else { // library marker kkossev.commonLib, line 429
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 430
        } // library marker kkossev.commonLib, line 431
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 432
    } // library marker kkossev.commonLib, line 433
    else { // library marker kkossev.commonLib, line 434
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
} // library marker kkossev.commonLib, line 437

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 439
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 440
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 441
        return false // library marker kkossev.commonLib, line 442
    } // library marker kkossev.commonLib, line 443
    // execute the customHandler function // library marker kkossev.commonLib, line 444
    Boolean result = false // library marker kkossev.commonLib, line 445
    try { // library marker kkossev.commonLib, line 446
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 447
    } // library marker kkossev.commonLib, line 448
    catch (e) { // library marker kkossev.commonLib, line 449
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 450
        return false // library marker kkossev.commonLib, line 451
    } // library marker kkossev.commonLib, line 452
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 453
    return result // library marker kkossev.commonLib, line 454
} // library marker kkossev.commonLib, line 455

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 457
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 458
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 459
    final String commandId = data[0] // library marker kkossev.commonLib, line 460
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 461
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 462
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 463
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 464
    } else { // library marker kkossev.commonLib, line 465
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 466
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 467
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 468
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 469
        } // library marker kkossev.commonLib, line 470
    } // library marker kkossev.commonLib, line 471
} // library marker kkossev.commonLib, line 472

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 474
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 475
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 476
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 477

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 479
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 480
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 481
] // library marker kkossev.commonLib, line 482

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 484
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 485
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 486
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 487
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 488
] // library marker kkossev.commonLib, line 489

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 491
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 492
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 493
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 494
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 495
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 496
    return avg // library marker kkossev.commonLib, line 497
} // library marker kkossev.commonLib, line 498

private void handlePingResponse() { // library marker kkossev.commonLib, line 500
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 501
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 502
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 503

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 505
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 506
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 507
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 508
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 509
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 510
        sendRttEvent() // library marker kkossev.commonLib, line 511
    } // library marker kkossev.commonLib, line 512
    else { // library marker kkossev.commonLib, line 513
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 514
    } // library marker kkossev.commonLib, line 515
    state.states['isPing'] = false // library marker kkossev.commonLib, line 516
} // library marker kkossev.commonLib, line 517

/* // library marker kkossev.commonLib, line 519
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 520
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 521
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 522
*/ // library marker kkossev.commonLib, line 523
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 524

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 526
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 527
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 528
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 529
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 530
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 531
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 532
        case 0x0000: // library marker kkossev.commonLib, line 533
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 534
            break // library marker kkossev.commonLib, line 535
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 536
            if (isPing) { // library marker kkossev.commonLib, line 537
                handlePingResponse() // library marker kkossev.commonLib, line 538
            } // library marker kkossev.commonLib, line 539
            else { // library marker kkossev.commonLib, line 540
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 541
            } // library marker kkossev.commonLib, line 542
            break // library marker kkossev.commonLib, line 543
        case 0x0004: // library marker kkossev.commonLib, line 544
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 545
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 546
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 547
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 548
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 549
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 550
            } // library marker kkossev.commonLib, line 551
            break // library marker kkossev.commonLib, line 552
        case 0x0005: // library marker kkossev.commonLib, line 553
            if (isPing) { // library marker kkossev.commonLib, line 554
                handlePingResponse() // library marker kkossev.commonLib, line 555
            } // library marker kkossev.commonLib, line 556
            else { // library marker kkossev.commonLib, line 557
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 558
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 559
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 560
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 561
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 562
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 563
                } // library marker kkossev.commonLib, line 564
            } // library marker kkossev.commonLib, line 565
            break // library marker kkossev.commonLib, line 566
        case 0x0007: // library marker kkossev.commonLib, line 567
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 568
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 569
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 570
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 571
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 572
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 573
            } // library marker kkossev.commonLib, line 574
            break // library marker kkossev.commonLib, line 575
        case 0xFFDF: // library marker kkossev.commonLib, line 576
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 577
            break // library marker kkossev.commonLib, line 578
        case 0xFFE2: // library marker kkossev.commonLib, line 579
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 580
            break // library marker kkossev.commonLib, line 581
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 582
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 583
            break // library marker kkossev.commonLib, line 584
        case 0xFFFE: // library marker kkossev.commonLib, line 585
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 586
            break // library marker kkossev.commonLib, line 587
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 588
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 589
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 590
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 591
            break // library marker kkossev.commonLib, line 592
        default: // library marker kkossev.commonLib, line 593
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 594
            break // library marker kkossev.commonLib, line 595
    } // library marker kkossev.commonLib, line 596
} // library marker kkossev.commonLib, line 597

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 599
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 600
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 601
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 604
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 605
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 606
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 607
        default: logWarn "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 608
    } // library marker kkossev.commonLib, line 609
} // library marker kkossev.commonLib, line 610

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 612
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 613
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 614

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 616
    Map descMap = [:] // library marker kkossev.commonLib, line 617
    try { // library marker kkossev.commonLib, line 618
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 619
    } // library marker kkossev.commonLib, line 620
    catch (e1) { // library marker kkossev.commonLib, line 621
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 622
        // try alternative custom parsing // library marker kkossev.commonLib, line 623
        descMap = [:] // library marker kkossev.commonLib, line 624
        try { // library marker kkossev.commonLib, line 625
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 626
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 627
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 628
            } // library marker kkossev.commonLib, line 629
        } // library marker kkossev.commonLib, line 630
        catch (e2) { // library marker kkossev.commonLib, line 631
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 632
            return [:] // library marker kkossev.commonLib, line 633
        } // library marker kkossev.commonLib, line 634
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 635
    } // library marker kkossev.commonLib, line 636
    return descMap // library marker kkossev.commonLib, line 637
} // library marker kkossev.commonLib, line 638

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 640
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 641
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 642
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 643
        return false // library marker kkossev.commonLib, line 644
    } // library marker kkossev.commonLib, line 645
    // try to parse ... // library marker kkossev.commonLib, line 646
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 647
    Map descMap = [:] // library marker kkossev.commonLib, line 648
    try { // library marker kkossev.commonLib, line 649
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 650
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 651
    } // library marker kkossev.commonLib, line 652
    catch (e) { // library marker kkossev.commonLib, line 653
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 654
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 655
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 656
        return true // library marker kkossev.commonLib, line 657
    } // library marker kkossev.commonLib, line 658

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 660
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 663
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 664
    } // library marker kkossev.commonLib, line 665
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 666
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
    else { // library marker kkossev.commonLib, line 669
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 670
        return false // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
    return true    // processed // library marker kkossev.commonLib, line 673
} // library marker kkossev.commonLib, line 674

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 676
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 677
  /* // library marker kkossev.commonLib, line 678
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 679
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 680
        return true // library marker kkossev.commonLib, line 681
    } // library marker kkossev.commonLib, line 682
*/ // library marker kkossev.commonLib, line 683
    Map descMap = [:] // library marker kkossev.commonLib, line 684
    try { // library marker kkossev.commonLib, line 685
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 686
    } // library marker kkossev.commonLib, line 687
    catch (e1) { // library marker kkossev.commonLib, line 688
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 689
        // try alternative custom parsing // library marker kkossev.commonLib, line 690
        descMap = [:] // library marker kkossev.commonLib, line 691
        try { // library marker kkossev.commonLib, line 692
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 693
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 694
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 695
            } // library marker kkossev.commonLib, line 696
        } // library marker kkossev.commonLib, line 697
        catch (e2) { // library marker kkossev.commonLib, line 698
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 699
            return true // library marker kkossev.commonLib, line 700
        } // library marker kkossev.commonLib, line 701
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 702
    } // library marker kkossev.commonLib, line 703
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 704
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 705
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 706
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 707
        return false // library marker kkossev.commonLib, line 708
    } // library marker kkossev.commonLib, line 709
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 710
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 711
    // attribute report received // library marker kkossev.commonLib, line 712
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 713
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 714
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 715
    } // library marker kkossev.commonLib, line 716
    attrData.each { // library marker kkossev.commonLib, line 717
        if (it.status == '86') { // library marker kkossev.commonLib, line 718
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 719
        // TODO - skip parsing? // library marker kkossev.commonLib, line 720
        } // library marker kkossev.commonLib, line 721
        switch (it.cluster) { // library marker kkossev.commonLib, line 722
            case '0000' : // library marker kkossev.commonLib, line 723
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 724
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 725
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 726
                } // library marker kkossev.commonLib, line 727
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 728
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 729
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 730
                } // library marker kkossev.commonLib, line 731
                else { // library marker kkossev.commonLib, line 732
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 733
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 734
                } // library marker kkossev.commonLib, line 735
                break // library marker kkossev.commonLib, line 736
            default : // library marker kkossev.commonLib, line 737
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 738
                break // library marker kkossev.commonLib, line 739
        } // switch // library marker kkossev.commonLib, line 740
    } // for each attribute // library marker kkossev.commonLib, line 741
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 742
} // library marker kkossev.commonLib, line 743

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 745
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 746
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 747
} // library marker kkossev.commonLib, line 748

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 750
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 751
} // library marker kkossev.commonLib, line 752

/* // library marker kkossev.commonLib, line 754
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 755
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 756
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 757
*/ // library marker kkossev.commonLib, line 758
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 759
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 760
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 761

// Tuya Commands // library marker kkossev.commonLib, line 763
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 764
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 765
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 766
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 767
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 768

// tuya DP type // library marker kkossev.commonLib, line 770
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 771
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 772
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 773
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 774
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 775
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 776

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 778
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 779
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 780
    long offset = 0 // library marker kkossev.commonLib, line 781
    int offsetHours = 0 // library marker kkossev.commonLib, line 782
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 783
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 784
    try { // library marker kkossev.commonLib, line 785
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 786
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 787
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 788
    } catch (e) { // library marker kkossev.commonLib, line 789
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 790
    } // library marker kkossev.commonLib, line 791
    // // library marker kkossev.commonLib, line 792
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 793
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 794
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 795
} // library marker kkossev.commonLib, line 796

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 798
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 799
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 800
        syncTuyaDateTime() // library marker kkossev.commonLib, line 801
    } // library marker kkossev.commonLib, line 802
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 803
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 804
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 805
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 806
        if (status != '00') { // library marker kkossev.commonLib, line 807
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 808
        } // library marker kkossev.commonLib, line 809
    } // library marker kkossev.commonLib, line 810
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 811
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 812
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 813
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 814
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 815
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 816
            return // library marker kkossev.commonLib, line 817
        } // library marker kkossev.commonLib, line 818
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 819
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 820
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 821
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 822
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 823
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 824
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 825
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 826
            } // library marker kkossev.commonLib, line 827
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 828
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 829
        } // library marker kkossev.commonLib, line 830
    } // library marker kkossev.commonLib, line 831
    else { // library marker kkossev.commonLib, line 832
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 833
    } // library marker kkossev.commonLib, line 834
} // library marker kkossev.commonLib, line 835

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 837
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 838
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 839
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 840
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 841
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 842
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 843
        } // library marker kkossev.commonLib, line 844
    } // library marker kkossev.commonLib, line 845
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 846
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 847
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 848
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 849
        } // library marker kkossev.commonLib, line 850
    } // library marker kkossev.commonLib, line 851
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 855
    int retValue = 0 // library marker kkossev.commonLib, line 856
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 857
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 858
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 859
        int power = 1 // library marker kkossev.commonLib, line 860
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 861
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 862
            power = power * 256 // library marker kkossev.commonLib, line 863
        } // library marker kkossev.commonLib, line 864
    } // library marker kkossev.commonLib, line 865
    return retValue // library marker kkossev.commonLib, line 866
} // library marker kkossev.commonLib, line 867

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 869

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 871
    List<String> cmds = [] // library marker kkossev.commonLib, line 872
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 873
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 874
    int tuyaCmd // library marker kkossev.commonLib, line 875
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 876
    if (this.respondsTo('getDEVICE') && DEVICE?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 877
        tuyaCmd = DEVICE?.device?.tuyaCmd // library marker kkossev.commonLib, line 878
    } // library marker kkossev.commonLib, line 879
    else { // library marker kkossev.commonLib, line 880
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 881
    } // library marker kkossev.commonLib, line 882
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 883
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 884
    return cmds // library marker kkossev.commonLib, line 885
} // library marker kkossev.commonLib, line 886

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 888

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 890
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 891
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 892
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 893
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 894
} // library marker kkossev.commonLib, line 895


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 898
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 899
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 900
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 901
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 902
} // library marker kkossev.commonLib, line 903

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 905
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 906
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 907
    return cmds // library marker kkossev.commonLib, line 908
} // library marker kkossev.commonLib, line 909

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 911
    List<String> cmds = [] // library marker kkossev.commonLib, line 912
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 913
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 914
    } // library marker kkossev.commonLib, line 915
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 916
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 917
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 918
        return // library marker kkossev.commonLib, line 919
    } // library marker kkossev.commonLib, line 920
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 921
} // library marker kkossev.commonLib, line 922

// Invoked from configure() // library marker kkossev.commonLib, line 924
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 925
    List<String> cmds = [] // library marker kkossev.commonLib, line 926
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 927
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 928
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 929
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 930
    } // library marker kkossev.commonLib, line 931
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 932
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 933
    return cmds // library marker kkossev.commonLib, line 934
} // library marker kkossev.commonLib, line 935

// Invoked from configure() // library marker kkossev.commonLib, line 937
public List<String> configureDevice() { // library marker kkossev.commonLib, line 938
    List<String> cmds = [] // library marker kkossev.commonLib, line 939
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 940
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 941
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 942
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 945
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 946
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 947
    return cmds // library marker kkossev.commonLib, line 948
} // library marker kkossev.commonLib, line 949

/* // library marker kkossev.commonLib, line 951
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 952
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 953
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 954
*/ // library marker kkossev.commonLib, line 955

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 957
    List<String> cmds = [] // library marker kkossev.commonLib, line 958
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 959
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 960
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 961
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 962
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 963
            } // library marker kkossev.commonLib, line 964
        } // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    return cmds // library marker kkossev.commonLib, line 967
} // library marker kkossev.commonLib, line 968

public void refresh() { // library marker kkossev.commonLib, line 970
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 971
    checkDriverVersion(state) // library marker kkossev.commonLib, line 972
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 973
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 974
        customCmds = customRefresh() // library marker kkossev.commonLib, line 975
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 976
    } // library marker kkossev.commonLib, line 977
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 978
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 979
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 982
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 983
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 984
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 985
    } // library marker kkossev.commonLib, line 986
    else { // library marker kkossev.commonLib, line 987
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
} // library marker kkossev.commonLib, line 990

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 992
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 993
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 994

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 996
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 997
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 998
        sendEvent(name: 'Status', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 999
    } // library marker kkossev.commonLib, line 1000
    else { // library marker kkossev.commonLib, line 1001
        logInfo "${info}" // library marker kkossev.commonLib, line 1002
        sendEvent(name: 'Status', value: info, type: 'digital') // library marker kkossev.commonLib, line 1003
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1004
    } // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

public void ping() { // library marker kkossev.commonLib, line 1008
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1009
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1010
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1011
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1012
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1013
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1014
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1015
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1016
    } // library marker kkossev.commonLib, line 1017
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1018
    logDebug 'ping...' // library marker kkossev.commonLib, line 1019
} // library marker kkossev.commonLib, line 1020

private void virtualPong() { // library marker kkossev.commonLib, line 1022
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1023
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1024
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1025
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1026
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1027
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1028
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1029
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1030
        sendRttEvent() // library marker kkossev.commonLib, line 1031
    } // library marker kkossev.commonLib, line 1032
    else { // library marker kkossev.commonLib, line 1033
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1034
    } // library marker kkossev.commonLib, line 1035
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1036
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1037
} // library marker kkossev.commonLib, line 1038

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1040
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1041
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1042
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1043
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1044
    if (value == null) { // library marker kkossev.commonLib, line 1045
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1046
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
    else { // library marker kkossev.commonLib, line 1049
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1050
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1051
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1052
    } // library marker kkossev.commonLib, line 1053
} // library marker kkossev.commonLib, line 1054

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1056
    if (cluster != null) { // library marker kkossev.commonLib, line 1057
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1058
    } // library marker kkossev.commonLib, line 1059
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1060
    return 'NULL' // library marker kkossev.commonLib, line 1061
} // library marker kkossev.commonLib, line 1062

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1064
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1065
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1066
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1067
} // library marker kkossev.commonLib, line 1068

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1070
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1071
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1072
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1073
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1074
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1075
    } // library marker kkossev.commonLib, line 1076
} // library marker kkossev.commonLib, line 1077

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1079
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1080
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1081
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1082
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1083
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1084
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1085
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1086
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1087
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1088
            } // library marker kkossev.commonLib, line 1089
        } // library marker kkossev.commonLib, line 1090
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1091
    } // library marker kkossev.commonLib, line 1092
} // library marker kkossev.commonLib, line 1093

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1095
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1096
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1097
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1098
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1099
    } // library marker kkossev.commonLib, line 1100
    else { // library marker kkossev.commonLib, line 1101
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1102
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
} // library marker kkossev.commonLib, line 1105

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1107
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1108
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1109
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1110
} // library marker kkossev.commonLib, line 1111

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1113
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1114
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1115
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1116
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1117
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1118
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1123
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1124
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1125
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1126
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1127
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1128
            logWarn 'not present!' // library marker kkossev.commonLib, line 1129
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1130
        } // library marker kkossev.commonLib, line 1131
    } // library marker kkossev.commonLib, line 1132
    else { // library marker kkossev.commonLib, line 1133
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1136
    // added 03/06/2025 // library marker kkossev.commonLib, line 1137
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1138
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1139
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1140
    } // library marker kkossev.commonLib, line 1141
} // library marker kkossev.commonLib, line 1142

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1144
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1145
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1146
    if (value == 'online') { // library marker kkossev.commonLib, line 1147
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1148
    } // library marker kkossev.commonLib, line 1149
    else { // library marker kkossev.commonLib, line 1150
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1151
    } // library marker kkossev.commonLib, line 1152
} // library marker kkossev.commonLib, line 1153

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1155
void updated() { // library marker kkossev.commonLib, line 1156
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1157
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1158
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1159
    unschedule() // library marker kkossev.commonLib, line 1160

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1162
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1163
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1166
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1167
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1168
    } // library marker kkossev.commonLib, line 1169

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1171
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1172
        // schedule the periodic timer // library marker kkossev.commonLib, line 1173
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1174
        if (interval > 0) { // library marker kkossev.commonLib, line 1175
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1176
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1177
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1178
        } // library marker kkossev.commonLib, line 1179
    } // library marker kkossev.commonLib, line 1180
    else { // library marker kkossev.commonLib, line 1181
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1182
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1185
        customUpdated() // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1189
} // library marker kkossev.commonLib, line 1190

private void logsOff() { // library marker kkossev.commonLib, line 1192
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1193
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1194
} // library marker kkossev.commonLib, line 1195
private void traceOff() { // library marker kkossev.commonLib, line 1196
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1197
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1198
} // library marker kkossev.commonLib, line 1199

public void configure(String command) { // library marker kkossev.commonLib, line 1201
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1202
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1203
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1204
        return // library marker kkossev.commonLib, line 1205
    } // library marker kkossev.commonLib, line 1206
    // // library marker kkossev.commonLib, line 1207
    String func // library marker kkossev.commonLib, line 1208
    try { // library marker kkossev.commonLib, line 1209
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1210
        "$func"() // library marker kkossev.commonLib, line 1211
    } // library marker kkossev.commonLib, line 1212
    catch (e) { // library marker kkossev.commonLib, line 1213
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1214
        return // library marker kkossev.commonLib, line 1215
    } // library marker kkossev.commonLib, line 1216
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1220
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1221
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1222
} // library marker kkossev.commonLib, line 1223

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1225
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1226
    deleteAllSettings() // library marker kkossev.commonLib, line 1227
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1228
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1229
    deleteAllStates() // library marker kkossev.commonLib, line 1230
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1231

    initialize() // library marker kkossev.commonLib, line 1233
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1234
    updated() // library marker kkossev.commonLib, line 1235
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1236
} // library marker kkossev.commonLib, line 1237

private void configureNow() { // library marker kkossev.commonLib, line 1239
    configure() // library marker kkossev.commonLib, line 1240
} // library marker kkossev.commonLib, line 1241

/** // library marker kkossev.commonLib, line 1243
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1244
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1245
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1246
 */ // library marker kkossev.commonLib, line 1247
void configure() { // library marker kkossev.commonLib, line 1248
    List<String> cmds = [] // library marker kkossev.commonLib, line 1249
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1250
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1251
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1252
    if (isTuya()) { // library marker kkossev.commonLib, line 1253
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1254
    } // library marker kkossev.commonLib, line 1255
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1256
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1257
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1258
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1259
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1260
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1261
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1262
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1263
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
    else { // library marker kkossev.commonLib, line 1266
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1267
    } // library marker kkossev.commonLib, line 1268
} // library marker kkossev.commonLib, line 1269

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1271
void installed() { // library marker kkossev.commonLib, line 1272
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1273
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1274
    // populate some default values for attributes // library marker kkossev.commonLib, line 1275
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1276
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1277
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1278
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1279
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1280
} // library marker kkossev.commonLib, line 1281

private void queryPowerSource() { // library marker kkossev.commonLib, line 1283
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1284
} // library marker kkossev.commonLib, line 1285

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1287
private void initialize() { // library marker kkossev.commonLib, line 1288
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1289
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1290
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1291
        logInfo "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1292
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1293
    } // library marker kkossev.commonLib, line 1294
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1295
    updateTuyaVersion() // library marker kkossev.commonLib, line 1296
    updateAqaraVersion() // library marker kkossev.commonLib, line 1297
} // library marker kkossev.commonLib, line 1298

/* // library marker kkossev.commonLib, line 1300
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1301
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1302
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1303
*/ // library marker kkossev.commonLib, line 1304

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1306
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1307
} // library marker kkossev.commonLib, line 1308

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1310
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1314
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1315
} // library marker kkossev.commonLib, line 1316

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1318
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1319
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1320
        return // library marker kkossev.commonLib, line 1321
    } // library marker kkossev.commonLib, line 1322
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1323
    cmd.each { // library marker kkossev.commonLib, line 1324
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1325
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1326
            return // library marker kkossev.commonLib, line 1327
        } // library marker kkossev.commonLib, line 1328
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1329
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1330
    } // library marker kkossev.commonLib, line 1331
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1332
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1333
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1334
} // library marker kkossev.commonLib, line 1335

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1337

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1339
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1340
} // library marker kkossev.commonLib, line 1341

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1343
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1344
} // library marker kkossev.commonLib, line 1345

//@CompileStatic // library marker kkossev.commonLib, line 1347
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1348
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1349
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1350
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1351
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1352
        initializeVars(false) // library marker kkossev.commonLib, line 1353
        updateTuyaVersion() // library marker kkossev.commonLib, line 1354
        updateAqaraVersion() // library marker kkossev.commonLib, line 1355
    } // library marker kkossev.commonLib, line 1356
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1357
} // library marker kkossev.commonLib, line 1358

// credits @thebearmay // library marker kkossev.commonLib, line 1360
String getModel() { // library marker kkossev.commonLib, line 1361
    try { // library marker kkossev.commonLib, line 1362
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1363
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1364
    } catch (ignore) { // library marker kkossev.commonLib, line 1365
        try { // library marker kkossev.commonLib, line 1366
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1367
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1368
                return model // library marker kkossev.commonLib, line 1369
            } // library marker kkossev.commonLib, line 1370
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1371
            return '' // library marker kkossev.commonLib, line 1372
        } // library marker kkossev.commonLib, line 1373
    } // library marker kkossev.commonLib, line 1374
} // library marker kkossev.commonLib, line 1375

// credits @thebearmay // library marker kkossev.commonLib, line 1377
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1378
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1379
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1380
    String revision = tokens.last() // library marker kkossev.commonLib, line 1381
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1385
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1386
    unschedule() // library marker kkossev.commonLib, line 1387
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1388
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1389

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1391
} // library marker kkossev.commonLib, line 1392

void resetStatistics() { // library marker kkossev.commonLib, line 1394
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1395
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1396
} // library marker kkossev.commonLib, line 1397

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1399
void resetStats() { // library marker kkossev.commonLib, line 1400
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1401
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1402
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1403
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1404
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1405
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1406
} // library marker kkossev.commonLib, line 1407

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1409
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1410
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1411
        state.clear() // library marker kkossev.commonLib, line 1412
        unschedule() // library marker kkossev.commonLib, line 1413
        resetStats() // library marker kkossev.commonLib, line 1414
        if (deviceProfilesV3 != null && this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1415
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1416
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1417
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1418
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1419
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1420
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1421
    } // library marker kkossev.commonLib, line 1422

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1424
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1425
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1426
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1427
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1428

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1430
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1431
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1432
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1433
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1434
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1435
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1436

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1438

    // common libraries initialization // library marker kkossev.commonLib, line 1440
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1441
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1442
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1443
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1444
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1445
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1446
    // // library marker kkossev.commonLib, line 1447
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1448
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1449
    // // library marker kkossev.commonLib, line 1450
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1451
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1452
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1453
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1454

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1456
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1457
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1458
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1459
    if ( ep  != null) { // library marker kkossev.commonLib, line 1460
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1461
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1462
    } // library marker kkossev.commonLib, line 1463
    else { // library marker kkossev.commonLib, line 1464
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1465
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1466
    } // library marker kkossev.commonLib, line 1467
} // library marker kkossev.commonLib, line 1468

// not used!? // library marker kkossev.commonLib, line 1470
void setDestinationEP() { // library marker kkossev.commonLib, line 1471
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1472
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1473
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1474
} // library marker kkossev.commonLib, line 1475

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1477
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1478
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1479
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1480

// _DEBUG mode only // library marker kkossev.commonLib, line 1482
void getAllProperties() { // library marker kkossev.commonLib, line 1483
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1484
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1485
} // library marker kkossev.commonLib, line 1486

// delete all Preferences // library marker kkossev.commonLib, line 1488
void deleteAllSettings() { // library marker kkossev.commonLib, line 1489
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1490
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1491
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1492
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494

// delete all attributes // library marker kkossev.commonLib, line 1496
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1497
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1498
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1499
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1500
} // library marker kkossev.commonLib, line 1501

// delete all State Variables // library marker kkossev.commonLib, line 1503
void deleteAllStates() { // library marker kkossev.commonLib, line 1504
    String stateDeleted = '' // library marker kkossev.commonLib, line 1505
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1506
    state.clear() // library marker kkossev.commonLib, line 1507
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1508
} // library marker kkossev.commonLib, line 1509

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1511
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1512
} // library marker kkossev.commonLib, line 1513

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1515
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1516
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1517
} // library marker kkossev.commonLib, line 1518

void testParse(String par) { // library marker kkossev.commonLib, line 1520
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1521
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1522
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1523
    parse(par) // library marker kkossev.commonLib, line 1524
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1525
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1526
} // library marker kkossev.commonLib, line 1527

Object testJob() { // library marker kkossev.commonLib, line 1529
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1530
} // library marker kkossev.commonLib, line 1531

/** // library marker kkossev.commonLib, line 1533
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1534
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1535
 */ // library marker kkossev.commonLib, line 1536
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1537
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1538
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1539
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1540
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1541
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1542
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1543
    String cron // library marker kkossev.commonLib, line 1544
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1545
    else { // library marker kkossev.commonLib, line 1546
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1547
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1548
    } // library marker kkossev.commonLib, line 1549
    return cron // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

// credits @thebearmay // library marker kkossev.commonLib, line 1553
String formatUptime() { // library marker kkossev.commonLib, line 1554
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1558
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1559
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1560
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1561
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1562
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1563
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1564
} // library marker kkossev.commonLib, line 1565

boolean isTuya() { // library marker kkossev.commonLib, line 1567
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1568
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1569
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1570
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1571
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1572
} // library marker kkossev.commonLib, line 1573

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1575
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1576
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1577
    if (application != null) { // library marker kkossev.commonLib, line 1578
        Integer ver // library marker kkossev.commonLib, line 1579
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1580
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1581
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1582
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1583
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1584
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1585
        } // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
} // library marker kkossev.commonLib, line 1588

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1590

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1592
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1593
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1594
    if (application != null) { // library marker kkossev.commonLib, line 1595
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1596
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1597
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1598
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1599
        } // library marker kkossev.commonLib, line 1600
    } // library marker kkossev.commonLib, line 1601
} // library marker kkossev.commonLib, line 1602

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1604
    try { // library marker kkossev.commonLib, line 1605
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1606
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1607
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1608
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1609
    } catch (e) { // library marker kkossev.commonLib, line 1610
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1611
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1612
    } // library marker kkossev.commonLib, line 1613
} // library marker kkossev.commonLib, line 1614

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1616
    try { // library marker kkossev.commonLib, line 1617
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1618
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1619
        return date.getTime() // library marker kkossev.commonLib, line 1620
    } catch (e) { // library marker kkossev.commonLib, line 1621
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1622
        return now() // library marker kkossev.commonLib, line 1623
    } // library marker kkossev.commonLib, line 1624
} // library marker kkossev.commonLib, line 1625

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1627
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1628
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1629
    int seconds = time % 60 // library marker kkossev.commonLib, line 1630
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1631
} // library marker kkossev.commonLib, line 1632

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.3' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Battery Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent' // library marker kkossev.batteryLib, line 18
 * // library marker kkossev.batteryLib, line 19
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 20
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 21
*/ // library marker kkossev.batteryLib, line 22

static String batteryLibVersion()   { '3.2.3' } // library marker kkossev.batteryLib, line 24
static String batteryLibStamp() { '2025/07/13 7:45 PM' } // library marker kkossev.batteryLib, line 25

metadata { // library marker kkossev.batteryLib, line 27
    capability 'Battery' // library marker kkossev.batteryLib, line 28
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 29
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 30
    // no commands // library marker kkossev.batteryLib, line 31
    preferences { // library marker kkossev.batteryLib, line 32
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 33
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 34
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 35
            } // library marker kkossev.batteryLib, line 36
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 37
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 38
            } // library marker kkossev.batteryLib, line 39
        } // library marker kkossev.batteryLib, line 40
    } // library marker kkossev.batteryLib, line 41
} // library marker kkossev.batteryLib, line 42

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 44

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 47
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 48
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 49
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 50
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 51
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 52
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 53
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 54
        } // library marker kkossev.batteryLib, line 55
    } // library marker kkossev.batteryLib, line 56
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 57
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 58
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 59
        if (isTuya()) { // library marker kkossev.batteryLib, line 60
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 61
        } // library marker kkossev.batteryLib, line 62
        else { // library marker kkossev.batteryLib, line 63
            sendBatteryPercentageEvent((rawValue / 2) as int) // library marker kkossev.batteryLib, line 64
        } // library marker kkossev.batteryLib, line 65
    } // library marker kkossev.batteryLib, line 66
    else { // library marker kkossev.batteryLib, line 67
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 68
    } // library marker kkossev.batteryLib, line 69
} // library marker kkossev.batteryLib, line 70

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 72
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 73
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 74
    Map result = [:] // library marker kkossev.batteryLib, line 75
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 76
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 77
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 78
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 79
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 80
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 81
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 82
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 83
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 84
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 85
            result.name = 'battery' // library marker kkossev.batteryLib, line 86
            result.unit  = '%' // library marker kkossev.batteryLib, line 87
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 88
        } // library marker kkossev.batteryLib, line 89
        else { // library marker kkossev.batteryLib, line 90
            result.value = volts // library marker kkossev.batteryLib, line 91
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 92
            result.unit  = 'V' // library marker kkossev.batteryLib, line 93
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 94
        } // library marker kkossev.batteryLib, line 95
        result.type = 'physical' // library marker kkossev.batteryLib, line 96
        result.isStateChange = true // library marker kkossev.batteryLib, line 97
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 98
        sendEvent(result) // library marker kkossev.batteryLib, line 99
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 100
    } // library marker kkossev.batteryLib, line 101
    else { // library marker kkossev.batteryLib, line 102
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 103
    } // library marker kkossev.batteryLib, line 104
} // library marker kkossev.batteryLib, line 105

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 107
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 108
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 109
        return // library marker kkossev.batteryLib, line 110
    } // library marker kkossev.batteryLib, line 111
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 112
    Map map = [:] // library marker kkossev.batteryLib, line 113
    map.name = 'battery' // library marker kkossev.batteryLib, line 114
    map.timeStamp = now() // library marker kkossev.batteryLib, line 115
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 116
    map.unit  = '%' // library marker kkossev.batteryLib, line 117
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 118
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 119
    map.isStateChange = true // library marker kkossev.batteryLib, line 120
    // // library marker kkossev.batteryLib, line 121
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 122
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 123
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 124
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 125
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 126
        // send it now! // library marker kkossev.batteryLib, line 127
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 128
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 129
    } // library marker kkossev.batteryLib, line 130
    else { // library marker kkossev.batteryLib, line 131
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 132
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 133
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 134
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 135
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 136
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 137
    } // library marker kkossev.batteryLib, line 138
} // library marker kkossev.batteryLib, line 139

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 141
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 142
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 143
    sendEvent(map) // library marker kkossev.batteryLib, line 144
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 145
} // library marker kkossev.batteryLib, line 146

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 148
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 149
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 150
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 151
    sendEvent(map) // library marker kkossev.batteryLib, line 152
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 153
} // library marker kkossev.batteryLib, line 154

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 156
    int rawValue = fncmd // library marker kkossev.batteryLib, line 157
    switch (fncmd) { // library marker kkossev.batteryLib, line 158
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 159
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 160
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 161
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 162
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 163
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 164
    } // library marker kkossev.batteryLib, line 165
    return rawValue // library marker kkossev.batteryLib, line 166
} // library marker kkossev.batteryLib, line 167

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 169
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 170
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 171
} // library marker kkossev.batteryLib, line 172

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 174
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 175
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 176
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 178
    } // library marker kkossev.batteryLib, line 179
} // library marker kkossev.batteryLib, line 180

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 182
    List<String> cmds = [] // library marker kkossev.batteryLib, line 183
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 185
    return cmds // library marker kkossev.batteryLib, line 186
} // library marker kkossev.batteryLib, line 187

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.3' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.temperatureLib, line 10
 * // library marker kkossev.temperatureLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 12
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 13
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh() // library marker kkossev.temperatureLib, line 14
 * ver. 3.2.1  2024-06-07 kkossev  - excluded maxReportingTime for mmWaveSensor and Thermostat // library marker kkossev.temperatureLib, line 15
 * ver. 3.2.2  2024-07-06 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug // library marker kkossev.temperatureLib, line 16
 * ver. 3.2.3  2024-07-18 kkossev  - added 'ReportingConfiguration' capability check for minReportingTime and maxReportingTime // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 *                                   TODO: // library marker kkossev.temperatureLib, line 19
 *                                   TODO: add temperatureOffset // library marker kkossev.temperatureLib, line 20
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 21
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 22
*/ // library marker kkossev.temperatureLib, line 23

static String temperatureLibVersion()   { '3.2.3' } // library marker kkossev.temperatureLib, line 25
static String temperatureLibStamp() { '2024/07/18 3:08 PM' } // library marker kkossev.temperatureLib, line 26

metadata { // library marker kkossev.temperatureLib, line 28
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 29
    // no commands // library marker kkossev.temperatureLib, line 30
    preferences { // library marker kkossev.temperatureLib, line 31
        if (device && advancedOptions == true) { // library marker kkossev.temperatureLib, line 32
            if ('ReportingConfiguration' in DEVICE?.capabilities) { // library marker kkossev.temperatureLib, line 33
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: 'Minimum reporting interval, seconds <i>(1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 34
                if (!(deviceType in ['mmWaveSensor', 'Thermostat', 'TRV'])) { // library marker kkossev.temperatureLib, line 35
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: 'Maximum reporting interval, seconds <i>(120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 36
                } // library marker kkossev.temperatureLib, line 37
            } // library marker kkossev.temperatureLib, line 38
        } // library marker kkossev.temperatureLib, line 39
    } // library marker kkossev.temperatureLib, line 40
} // library marker kkossev.temperatureLib, line 41

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 43
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 44
    if (descMap.attrId == '0000') { // library marker kkossev.temperatureLib, line 45
        int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 46
        handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 47
    } // library marker kkossev.temperatureLib, line 48
    else { // library marker kkossev.temperatureLib, line 49
        logWarn "standardParseTemperatureCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.temperatureLib, line 50
    } // library marker kkossev.temperatureLib, line 51
} // library marker kkossev.temperatureLib, line 52

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 54
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 55
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 56
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 57
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 58
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 59
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 60
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 61
    } // library marker kkossev.temperatureLib, line 62
    else { // library marker kkossev.temperatureLib, line 63
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 64
    } // library marker kkossev.temperatureLib, line 65
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 66
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 67
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 68
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 69
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 70
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 71
        return // library marker kkossev.temperatureLib, line 72
    } // library marker kkossev.temperatureLib, line 73
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 74
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 75
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 76
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 77
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 78
    } // library marker kkossev.temperatureLib, line 79
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 80
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 81
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 82
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 83
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 84
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 85
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 86
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 87
    } // library marker kkossev.temperatureLib, line 88
    else {         // queue the event // library marker kkossev.temperatureLib, line 89
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 90
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 91
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 92
    } // library marker kkossev.temperatureLib, line 93
} // library marker kkossev.temperatureLib, line 94

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 96
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 97
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 98
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 99
} // library marker kkossev.temperatureLib, line 100

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 102
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 103
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 104
    logDebug "temperatureLibInitializeDevice() cmds=${cmds}" // library marker kkossev.temperatureLib, line 105
    return cmds // library marker kkossev.temperatureLib, line 106
} // library marker kkossev.temperatureLib, line 107

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 109
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 110
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 111
    return cmds // library marker kkossev.temperatureLib, line 112
} // library marker kkossev.temperatureLib, line 113

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (173) kkossev.humidityLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.humidityLib, line 1
library( // library marker kkossev.humidityLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Humidity Library', name: 'humidityLib', namespace: 'kkossev', // library marker kkossev.humidityLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/humidityLib.groovy', documentationLink: '', // library marker kkossev.humidityLib, line 4
    version: '3.2.3' // library marker kkossev.humidityLib, line 5
) // library marker kkossev.humidityLib, line 6
/* // library marker kkossev.humidityLib, line 7
 *  Zigbee Humidity Library // library marker kkossev.humidityLib, line 8
 * // library marker kkossev.humidityLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.humidityLib, line 10
 * // library marker kkossev.humidityLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added humidityLib.groovy // library marker kkossev.humidityLib, line 12
 * ver. 3.2.0  2024-05-29 kkossev  - commonLib 3.2.0 allignment; added humidityRefresh() // library marker kkossev.humidityLib, line 13
 * ver. 3.2.2  2024-07-02 kkossev  - fixed T/H clusters attribute different than 0 (temperature, humidity MeasuredValue) bug // library marker kkossev.humidityLib, line 14
 * ver. 3.2.3  2024-07-24 kkossev  - added humidity delta filtering to prevent duplicate events for unchanged values // library marker kkossev.humidityLib, line 15
 * // library marker kkossev.humidityLib, line 16
 *                                   TODO: add humidityOffset // library marker kkossev.humidityLib, line 17
*/ // library marker kkossev.humidityLib, line 18

static String humidityLibVersion()   { '3.2.3' } // library marker kkossev.humidityLib, line 20
static String humidityLibStamp() { '2024/07/24 7:45 PM' } // library marker kkossev.humidityLib, line 21

metadata { // library marker kkossev.humidityLib, line 23
    capability 'RelativeHumidityMeasurement' // library marker kkossev.humidityLib, line 24
    // no commands // library marker kkossev.humidityLib, line 25
    preferences { // library marker kkossev.humidityLib, line 26
        // the minReportingTime and maxReportingTime are already defined in the temperatureLib.groovy // library marker kkossev.humidityLib, line 27
    } // library marker kkossev.humidityLib, line 28
} // library marker kkossev.humidityLib, line 29

void standardParseHumidityCluster(final Map descMap) { // library marker kkossev.humidityLib, line 31
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.humidityLib, line 32
    if (descMap.attrId == '0000') { // library marker kkossev.humidityLib, line 33
        final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.humidityLib, line 34
        handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.humidityLib, line 35
    } // library marker kkossev.humidityLib, line 36
    else { // library marker kkossev.humidityLib, line 37
        logWarn "standardParseHumidityCluster() - unknown attribute ${descMap.attrId} value=${descMap.value}" // library marker kkossev.humidityLib, line 38
    } // library marker kkossev.humidityLib, line 39
} // library marker kkossev.humidityLib, line 40

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.humidityLib, line 42
    Map eventMap = [:] // library marker kkossev.humidityLib, line 43
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.humidityLib, line 44
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.humidityLib, line 45
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.humidityLib, line 46
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.humidityLib, line 47
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.humidityLib, line 48
        return // library marker kkossev.humidityLib, line 49
    } // library marker kkossev.humidityLib, line 50
    BigDecimal humidityRounded = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.humidityLib, line 51
    BigDecimal lastHumi = device.currentValue('humidity') ?: 0 // library marker kkossev.humidityLib, line 52
    logTrace "lastHumi=${lastHumi} humidityRounded=${humidityRounded} delta=${Math.abs(lastHumi - humidityRounded)}" // library marker kkossev.humidityLib, line 53
    if (Math.abs(lastHumi - humidityRounded) < 1.0) { // library marker kkossev.humidityLib, line 54
        logDebug "skipped humidity ${humidityRounded}, less than delta 1.0 (lastHumi=${lastHumi})" // library marker kkossev.humidityLib, line 55
        return // library marker kkossev.humidityLib, line 56
    } // library marker kkossev.humidityLib, line 57
    eventMap.value = humidityRounded // library marker kkossev.humidityLib, line 58
    eventMap.name = 'humidity' // library marker kkossev.humidityLib, line 59
    eventMap.unit = '% RH' // library marker kkossev.humidityLib, line 60
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.humidityLib, line 61
    //eventMap.isStateChange = true // library marker kkossev.humidityLib, line 62
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.humidityLib, line 63
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.humidityLib, line 64
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 65
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.humidityLib, line 66
    if (timeElapsed >= minTime) { // library marker kkossev.humidityLib, line 67
        logInfo "${eventMap.descriptionText}" // library marker kkossev.humidityLib, line 68
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.humidityLib, line 69
        state.lastRx['humiTime'] = now() // library marker kkossev.humidityLib, line 70
        sendEvent(eventMap) // library marker kkossev.humidityLib, line 71
    } // library marker kkossev.humidityLib, line 72
    else { // library marker kkossev.humidityLib, line 73
        eventMap.type = 'delayed' // library marker kkossev.humidityLib, line 74
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.humidityLib, line 75
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.humidityLib, line 76
    } // library marker kkossev.humidityLib, line 77
} // library marker kkossev.humidityLib, line 78

void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.humidityLib, line 80
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.humidityLib, line 81
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.humidityLib, line 82
    sendEvent(eventMap) // library marker kkossev.humidityLib, line 83
} // library marker kkossev.humidityLib, line 84

List<String> humidityLibInitializeDevice() { // library marker kkossev.humidityLib, line 86
    List<String> cmds = [] // library marker kkossev.humidityLib, line 87
    cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.humidityLib, line 88
    return cmds // library marker kkossev.humidityLib, line 89
} // library marker kkossev.humidityLib, line 90

List<String> humidityRefresh() { // library marker kkossev.humidityLib, line 92
    List<String> cmds = [] // library marker kkossev.humidityLib, line 93
    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.humidityLib, line 94
    return cmds // library marker kkossev.humidityLib, line 95
} // library marker kkossev.humidityLib, line 96

// ~~~~~ end include (173) kkossev.humidityLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Button Library', name: 'buttonLib', namespace: 'kkossev', // library marker kkossev.buttonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', documentationLink: '', // library marker kkossev.buttonLib, line 4
    version: '3.2.0' // library marker kkossev.buttonLib, line 5
) // library marker kkossev.buttonLib, line 6
/* // library marker kkossev.buttonLib, line 7
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 8
 * // library marker kkossev.buttonLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 11
 * // library marker kkossev.buttonLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 13
 * // library marker kkossev.buttonLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 19
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment; added capability 'PushableButton' and 'Momentary' // library marker kkossev.buttonLib, line 20
 * // library marker kkossev.buttonLib, line 21
 *                                   TODO: // library marker kkossev.buttonLib, line 22
*/ // library marker kkossev.buttonLib, line 23

static String buttonLibVersion()   { '3.2.0' } // library marker kkossev.buttonLib, line 25
static String buttonLibStamp() { '2024/05/24 12:48 PM' } // library marker kkossev.buttonLib, line 26

metadata { // library marker kkossev.buttonLib, line 28
    capability 'PushableButton' // library marker kkossev.buttonLib, line 29
    capability 'Momentary' // library marker kkossev.buttonLib, line 30
    // the other capabilities must be declared in the custom driver, if applicable for the particular device! // library marker kkossev.buttonLib, line 31
    // the custom driver must allso call sendNumberOfButtonsEvent() and sendSupportedButtonValuesEvent()! // library marker kkossev.buttonLib, line 32
    // capability 'DoubleTapableButton' // library marker kkossev.buttonLib, line 33
    // capability 'HoldableButton' // library marker kkossev.buttonLib, line 34
    // capability 'ReleasableButton' // library marker kkossev.buttonLib, line 35

    // no attributes // library marker kkossev.buttonLib, line 37
    // no commands // library marker kkossev.buttonLib, line 38
    preferences { // library marker kkossev.buttonLib, line 39
        // no prefrences // library marker kkossev.buttonLib, line 40
    } // library marker kkossev.buttonLib, line 41
} // library marker kkossev.buttonLib, line 42

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.buttonLib, line 44
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.buttonLib, line 45
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.buttonLib, line 46
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.buttonLib, line 47
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.buttonLib, line 48
        logInfo "$descriptionText" // library marker kkossev.buttonLib, line 49
        sendEvent(event) // library marker kkossev.buttonLib, line 50
    } // library marker kkossev.buttonLib, line 51
    else { // library marker kkossev.buttonLib, line 52
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.buttonLib, line 53
    } // library marker kkossev.buttonLib, line 54
} // library marker kkossev.buttonLib, line 55

void push() {                // Momentary capability // library marker kkossev.buttonLib, line 57
    logDebug 'push momentary' // library marker kkossev.buttonLib, line 58
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.buttonLib, line 59
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.buttonLib, line 60
} // library marker kkossev.buttonLib, line 61

/* // library marker kkossev.buttonLib, line 63
void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 64
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 65
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 66
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 67
} // library marker kkossev.buttonLib, line 68
*/ // library marker kkossev.buttonLib, line 69

void push(Object bn) {    //pushableButton capability // library marker kkossev.buttonLib, line 71
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 72
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 73
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 74
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 75
} // library marker kkossev.buttonLib, line 76

void doubleTap(Object bn) { // library marker kkossev.buttonLib, line 78
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void hold(Object bn) { // library marker kkossev.buttonLib, line 83
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 84
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 85
} // library marker kkossev.buttonLib, line 86

void release(Object bn) { // library marker kkossev.buttonLib, line 88
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 89
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 90
} // library marker kkossev.buttonLib, line 91

// must be called from the custom driver! // library marker kkossev.buttonLib, line 93
void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 94
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 95
} // library marker kkossev.buttonLib, line 96
// must be called from the custom driver! // library marker kkossev.buttonLib, line 97
void sendSupportedButtonValuesEvent(List<String> supportedValues) { // library marker kkossev.buttonLib, line 98
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 99
} // library marker kkossev.buttonLib, line 100


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessaryPublicModifier */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '', // library marker kkossev.xiaomiLib, line 3
    version: '3.3.0' // library marker kkossev.xiaomiLib, line 4
) // library marker kkossev.xiaomiLib, line 5
/* // library marker kkossev.xiaomiLib, line 6
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 7
 * // library marker kkossev.xiaomiLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 10
 * // library marker kkossev.xiaomiLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 12
 * // library marker kkossev.xiaomiLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 18
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 19
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 20
 * ver. 1.1.0  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.0 alignmment // library marker kkossev.xiaomiLib, line 21
 * ver. 3.2.2  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.2 alignmment // library marker kkossev.xiaomiLib, line 22
 * ver. 3.3.0  2024-06-23 kkossev  - comonLib 3.3.0 alignmment; added parseXiaomiClusterSingeTag() method // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD // library marker kkossev.xiaomiLib, line 25
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 26
*/ // library marker kkossev.xiaomiLib, line 27

static String xiaomiLibVersion()   { '3.3.0' } // library marker kkossev.xiaomiLib, line 29
static String xiaomiLibStamp() { '2024/06/23 9:36 AM' } // library marker kkossev.xiaomiLib, line 30

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 32
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 33
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 34
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.xiaomiLib, line 35
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.xiaomiLib, line 36

// no metadata for this library! // library marker kkossev.xiaomiLib, line 38

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 40

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 42
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 43
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 45
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 46
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 47
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 48
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 53
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 55
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 56
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 57

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 59
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 60
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 61
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 62
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 64
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 65

// called from parseXiaomiCluster() in the main code, if no customParse is defined // library marker kkossev.xiaomiLib, line 67
// TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 68
// TODO - refactor for Thermostat and Bulb specific code // library marker kkossev.xiaomiLib, line 69
void standardParseXiaomiFCC0Cluster(final Map descMap) { // library marker kkossev.xiaomiLib, line 70
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 71
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 72
    } // library marker kkossev.xiaomiLib, line 73
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 74
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 75
        return // library marker kkossev.xiaomiLib, line 76
    } // library marker kkossev.xiaomiLib, line 77
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 78
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 79
        return // library marker kkossev.xiaomiLib, line 80
    } // library marker kkossev.xiaomiLib, line 81
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 83
    final String funcName = 'standardParseXiaomiFCC0Cluster' // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            logWarn "${funcName}: unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 91
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
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
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
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
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
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

// cluster 0xFCC0 attribute  0x00F7 is sent as a keep-alive beakon every 55 minutes // library marker kkossev.xiaomiLib, line 160
public void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 161
    final String funcName = 'parseXiaomiClusterTags' // library marker kkossev.xiaomiLib, line 162
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 163
        parseXiaomiClusterSingeTag(tag, value) // library marker kkossev.xiaomiLib, line 164
    } // library marker kkossev.xiaomiLib, line 165
} // library marker kkossev.xiaomiLib, line 166

public void parseXiaomiClusterSingeTag(final Integer tag, final Object value) { // library marker kkossev.xiaomiLib, line 168
    final String funcName = 'parseXiaomiClusterSingeTag' // library marker kkossev.xiaomiLib, line 169
    switch (tag) { // library marker kkossev.xiaomiLib, line 170
        case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 171
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 172
            break // library marker kkossev.xiaomiLib, line 173
        case 0x03: // library marker kkossev.xiaomiLib, line 174
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 175
            break // library marker kkossev.xiaomiLib, line 176
        case 0x05: // library marker kkossev.xiaomiLib, line 177
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 178
            break // library marker kkossev.xiaomiLib, line 179
        case 0x06: // library marker kkossev.xiaomiLib, line 180
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 181
            break // library marker kkossev.xiaomiLib, line 182
        case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 183
            final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 184
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 185
            device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 186
            break // library marker kkossev.xiaomiLib, line 187
        case 0x0a: // library marker kkossev.xiaomiLib, line 188
            String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 189
            if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 190
            String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 191
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 192
            if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 193
                logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 194
                state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 195
                state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 196
            } // library marker kkossev.xiaomiLib, line 197
            break // library marker kkossev.xiaomiLib, line 198
        case 0x0b: // library marker kkossev.xiaomiLib, line 199
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 200
            break // library marker kkossev.xiaomiLib, line 201
        case 0x64: // library marker kkossev.xiaomiLib, line 202
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 203
            // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 204
            break // library marker kkossev.xiaomiLib, line 205
        case 0x65: // library marker kkossev.xiaomiLib, line 206
            if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
            else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 208
            break // library marker kkossev.xiaomiLib, line 209
        case 0x66: // library marker kkossev.xiaomiLib, line 210
            if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
            else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 212
            else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 213
            break // library marker kkossev.xiaomiLib, line 214
        case 0x67: // library marker kkossev.xiaomiLib, line 215
            if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 217
            // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 218
            break // library marker kkossev.xiaomiLib, line 219
        case 0x69: // library marker kkossev.xiaomiLib, line 220
            if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
            break // library marker kkossev.xiaomiLib, line 223
        case 0x6a: // library marker kkossev.xiaomiLib, line 224
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 225
            else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 226
            break // library marker kkossev.xiaomiLib, line 227
        case 0x6b: // library marker kkossev.xiaomiLib, line 228
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 229
            else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 230
            break // library marker kkossev.xiaomiLib, line 231
        case 0x95: // library marker kkossev.xiaomiLib, line 232
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 233
            break // library marker kkossev.xiaomiLib, line 234
        case 0x96: // library marker kkossev.xiaomiLib, line 235
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 236
            break // library marker kkossev.xiaomiLib, line 237
        case 0x97: // library marker kkossev.xiaomiLib, line 238
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 239
            break // library marker kkossev.xiaomiLib, line 240
        case 0x98: // library marker kkossev.xiaomiLib, line 241
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 242
            break // library marker kkossev.xiaomiLib, line 243
        case 0x9b: // library marker kkossev.xiaomiLib, line 244
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 245
                logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 246
                sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 247
            } // library marker kkossev.xiaomiLib, line 248
            else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 249
            break // library marker kkossev.xiaomiLib, line 250
        default: // library marker kkossev.xiaomiLib, line 251
            logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 252
    } // library marker kkossev.xiaomiLib, line 253
} // library marker kkossev.xiaomiLib, line 254

/** // library marker kkossev.xiaomiLib, line 256
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 257
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 258
 */ // library marker kkossev.xiaomiLib, line 259
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 260
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 261
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 262
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 263
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 264
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 265
    } // library marker kkossev.xiaomiLib, line 266
    return bigInt // library marker kkossev.xiaomiLib, line 267
} // library marker kkossev.xiaomiLib, line 268

/** // library marker kkossev.xiaomiLib, line 270
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 271
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 272
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 273
 */ // library marker kkossev.xiaomiLib, line 274
private Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 275
    try { // library marker kkossev.xiaomiLib, line 276
        final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 277
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 278
        new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 279
            while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 280
                int tag = stream.read() // library marker kkossev.xiaomiLib, line 281
                int dataType = stream.read() // library marker kkossev.xiaomiLib, line 282
                Object value // library marker kkossev.xiaomiLib, line 283
                if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 284
                    int length = stream.read() // library marker kkossev.xiaomiLib, line 285
                    byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 286
                    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 287
                    value = new String(byteArr) // library marker kkossev.xiaomiLib, line 288
                } else { // library marker kkossev.xiaomiLib, line 289
                    int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 290
                    value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 291
                } // library marker kkossev.xiaomiLib, line 292
                results[tag] = value // library marker kkossev.xiaomiLib, line 293
            } // library marker kkossev.xiaomiLib, line 294
        } // library marker kkossev.xiaomiLib, line 295
        return results // library marker kkossev.xiaomiLib, line 296
    } // library marker kkossev.xiaomiLib, line 297
    catch (e) { // library marker kkossev.xiaomiLib, line 298
        if (settings.logEnable) { "${device.displayName} decodeXiaomiTags: ${e}" } // library marker kkossev.xiaomiLib, line 299
        return [:] // library marker kkossev.xiaomiLib, line 300
    } // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 306
    return cmds // library marker kkossev.xiaomiLib, line 307
} // library marker kkossev.xiaomiLib, line 308

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 310
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 311
    logDebug "configureXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 312
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 313
    return cmds // library marker kkossev.xiaomiLib, line 314
} // library marker kkossev.xiaomiLib, line 315

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 317
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 318
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 319
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 320
    return cmds // library marker kkossev.xiaomiLib, line 321
} // library marker kkossev.xiaomiLib, line 322

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 324
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 325
} // library marker kkossev.xiaomiLib, line 326

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 328
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 329
} // library marker kkossev.xiaomiLib, line 330

List<String> standardAqaraBlackMagic() { // library marker kkossev.xiaomiLib, line 332
    return [] // library marker kkossev.xiaomiLib, line 333
    ///////////////////////////////////////// // library marker kkossev.xiaomiLib, line 334
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 335
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.xiaomiLib, line 336
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.xiaomiLib, line 337
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 339
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.xiaomiLib, line 340
        if (isAqaraTVOC_OLD()) { // library marker kkossev.xiaomiLib, line 341
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.xiaomiLib, line 342
        } // library marker kkossev.xiaomiLib, line 343
        logDebug 'standardAqaraBlackMagic()' // library marker kkossev.xiaomiLib, line 344
    } // library marker kkossev.xiaomiLib, line 345
    return cmds // library marker kkossev.xiaomiLib, line 346
} // library marker kkossev.xiaomiLib, line 347

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/deviceProfileLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-deviceProfileLib', // library marker kkossev.deviceProfileLib, line 4
    version: '3.4.2' // library marker kkossev.deviceProfileLib, line 5
) // library marker kkossev.deviceProfileLib, line 6
/* // library marker kkossev.deviceProfileLib, line 7
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 8
 * // library marker kkossev.deviceProfileLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 11
 * // library marker kkossev.deviceProfileLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 17
 * // library marker kkossev.deviceProfileLib, line 18
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.0  2023-11-27 kkossev  - fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-06-06 kkossev  - Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent); getDeviceProfilesMap bug fix; forcedProfile is always shown in preferences; // library marker kkossev.deviceProfileLib, line 29
 * ver. 3.3.0  2024-06-29 kkossev  - empty preferences bug fix; zclWriteAttribute delay 50 ms; added advanced check in inputIt(); fixed 'Cannot get property 'rw' on null object' bug; fixed enum attributes first event numeric value bug; // library marker kkossev.deviceProfileLib, line 30
 * ver. 3.3.1  2024-07-06 kkossev  - added powerSource event in the initEventsDeviceProfile // library marker kkossev.deviceProfileLib, line 31
 * ver. 3.3.2  2024-08-18 kkossev  - release 3.3.2 // library marker kkossev.deviceProfileLib, line 32
 * ver. 3.3.3  2024-08-18 kkossev  - sendCommand and setPar commands commented out; must be declared in the main driver where really needed // library marker kkossev.deviceProfileLib, line 33
 * ver. 3.3.4  2024-09-28 kkossev  - fixed exceptions in resetPreferencesToDefaults() and initEventsDeviceProfile() // library marker kkossev.deviceProfileLib, line 34
 * ver. 3.4.0  2025-02-02 kkossev  - deviceProfilesV3 optimizations (defaultFingerprint); is2in1() mod // library marker kkossev.deviceProfileLib, line 35
 * ver. 3.4.1  2025-02-02 kkossev  - setPar help improvements; // library marker kkossev.deviceProfileLib, line 36
 * ver. 3.4.2  2025-03-24 kkossev  - added refreshFromConfigureReadList() method; documentation update; getDeviceNameAndProfile uses DEVICE.description instead of deviceJoinName // library marker kkossev.deviceProfileLib, line 37
 * ver. 3.4.3  2025-04-25 kkossev  - HE platfrom version 2.4.1.x decimal preferences patch/workaround. // library marker kkossev.deviceProfileLib, line 38
 * ver. 3.5.0  2025-07-23 kkossev  - zclWriteAttribute() support for forced destinationEndpoint in the attributes map // library marker kkossev.deviceProfileLib, line 39
 * // library marker kkossev.deviceProfileLib, line 40
 *                                   TODO - remove the 2-in-1 patch ! // library marker kkossev.deviceProfileLib, line 41
 *                                   TODO - add updateStateUnknownDPs (from the 4-in-1 driver) // library marker kkossev.deviceProfileLib, line 42
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 43
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 44
 *                                   TODO: add _DEBUG command (for temporary switching the debug logs on/off) // library marker kkossev.deviceProfileLib, line 45
 *                                   TODO: allow NULL parameters default values in the device profiles // library marker kkossev.deviceProfileLib, line 46
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 47
 * // library marker kkossev.deviceProfileLib, line 48
*/ // library marker kkossev.deviceProfileLib, line 49

static String deviceProfileLibVersion()   { '3.5.0' } // library marker kkossev.deviceProfileLib, line 51
static String deviceProfileLibStamp() { '2025/07/23 2:21 PM' } // library marker kkossev.deviceProfileLib, line 52
import groovy.json.* // library marker kkossev.deviceProfileLib, line 53
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 54
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 55
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 56
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 57

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 59

metadata { // library marker kkossev.deviceProfileLib, line 61
    // no capabilities // library marker kkossev.deviceProfileLib, line 62
    // no attributes // library marker kkossev.deviceProfileLib, line 63
    /* // library marker kkossev.deviceProfileLib, line 64
    // copy the following commands to the main driver, if needed // library marker kkossev.deviceProfileLib, line 65
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 66
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 67
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 68
    ] // library marker kkossev.deviceProfileLib, line 69
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 70
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 71
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 72
    ] // library marker kkossev.deviceProfileLib, line 73
    */ // library marker kkossev.deviceProfileLib, line 74
    preferences { // library marker kkossev.deviceProfileLib, line 75
        if (device) { // library marker kkossev.deviceProfileLib, line 76
            input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: 'Manually change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 77
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 78
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 79
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 80
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 81
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 82
                        input inputMap // library marker kkossev.deviceProfileLib, line 83
                    } // library marker kkossev.deviceProfileLib, line 84
                } // library marker kkossev.deviceProfileLib, line 85
            } // library marker kkossev.deviceProfileLib, line 86
        } // library marker kkossev.deviceProfileLib, line 87
    } // library marker kkossev.deviceProfileLib, line 88
} // library marker kkossev.deviceProfileLib, line 89

private boolean is2in1() { return getDeviceProfile().startsWith('TS0601_2IN1')  }   // patch! // library marker kkossev.deviceProfileLib, line 91

public String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 93
public Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 94
public Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 95

public List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 97
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 98
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 99
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 100
    } // library marker kkossev.deviceProfileLib, line 101
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 102
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 103
        if ((profileMap.device?.isDepricated ?: false) != true) { // library marker kkossev.deviceProfileLib, line 104
            activeProfiles.add(profileMap.description ?: '---') // library marker kkossev.deviceProfileLib, line 105
        } // library marker kkossev.deviceProfileLib, line 106
    } // library marker kkossev.deviceProfileLib, line 107
    return activeProfiles // library marker kkossev.deviceProfileLib, line 108
} // library marker kkossev.deviceProfileLib, line 109

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 111

/** // library marker kkossev.deviceProfileLib, line 113
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 114
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 115
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 116
 */ // library marker kkossev.deviceProfileLib, line 117
public String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 118
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 119
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 120
    else { return null } // library marker kkossev.deviceProfileLib, line 121
} // library marker kkossev.deviceProfileLib, line 122

/** // library marker kkossev.deviceProfileLib, line 124
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 125
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 126
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 127
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 128
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 129
 */ // library marker kkossev.deviceProfileLib, line 130
private Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 131
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 132
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 133
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 134
    def preference // library marker kkossev.deviceProfileLib, line 135
    try { // library marker kkossev.deviceProfileLib, line 136
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 137
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 138
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 139
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 140
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 141
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 142
        } // library marker kkossev.deviceProfileLib, line 143
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 144
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 145
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 146
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 147
        } // library marker kkossev.deviceProfileLib, line 148
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 149
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 150
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 151
        } // library marker kkossev.deviceProfileLib, line 152
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 153
    } catch (e) { // library marker kkossev.deviceProfileLib, line 154
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 155
        return [:] // library marker kkossev.deviceProfileLib, line 156
    } // library marker kkossev.deviceProfileLib, line 157
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 158
    return foundMap // library marker kkossev.deviceProfileLib, line 159
} // library marker kkossev.deviceProfileLib, line 160

public Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 162
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 163
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 164
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 165
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 166
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 167
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 168
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 169
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 170
            return foundMap // library marker kkossev.deviceProfileLib, line 171
        } // library marker kkossev.deviceProfileLib, line 172
    } // library marker kkossev.deviceProfileLib, line 173
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 174
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 175
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 176
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 177
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 178
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 179
            return foundMap // library marker kkossev.deviceProfileLib, line 180
        } // library marker kkossev.deviceProfileLib, line 181
    } // library marker kkossev.deviceProfileLib, line 182
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 183
    return [:] // library marker kkossev.deviceProfileLib, line 184
} // library marker kkossev.deviceProfileLib, line 185

/** // library marker kkossev.deviceProfileLib, line 187
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 188
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 189
 */ // library marker kkossev.deviceProfileLib, line 190
public void resetPreferencesToDefaults(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 191
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 192
    if (DEVICE == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 193
    Map preferences = DEVICE?.preferences ?: [:] // library marker kkossev.deviceProfileLib, line 194
    if (preferences == null || preferences == [:]) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 195
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 196
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 197
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 198
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 199
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 200
            return // continue // library marker kkossev.deviceProfileLib, line 201
        } // library marker kkossev.deviceProfileLib, line 202
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 203
        if (parMap == null || parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 204
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 205
        if (parMap?.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 206
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 207
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 208
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 209
    } // library marker kkossev.deviceProfileLib, line 210
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 211
} // library marker kkossev.deviceProfileLib, line 212

/** // library marker kkossev.deviceProfileLib, line 214
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 215
 * // library marker kkossev.deviceProfileLib, line 216
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 217
 */ // library marker kkossev.deviceProfileLib, line 218
private List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 219
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 220
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 221
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 222
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 223
    } // library marker kkossev.deviceProfileLib, line 224
    return validPars // library marker kkossev.deviceProfileLib, line 225
} // library marker kkossev.deviceProfileLib, line 226

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 228
private def getScaledPreferenceValue(String preference, Map dpMap) {        // TODO - not used ??? // library marker kkossev.deviceProfileLib, line 229
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 230
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 231
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 232
    def scaledValue // library marker kkossev.deviceProfileLib, line 233
    if (value == null) { // library marker kkossev.deviceProfileLib, line 234
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 235
        return null // library marker kkossev.deviceProfileLib, line 236
    } // library marker kkossev.deviceProfileLib, line 237
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 238
        case 'number' : // library marker kkossev.deviceProfileLib, line 239
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 240
            break // library marker kkossev.deviceProfileLib, line 241
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 242
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 243
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 244
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 245
            } // library marker kkossev.deviceProfileLib, line 246
            break // library marker kkossev.deviceProfileLib, line 247
        case 'bool' : // library marker kkossev.deviceProfileLib, line 248
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 249
            break // library marker kkossev.deviceProfileLib, line 250
        case 'enum' : // library marker kkossev.deviceProfileLib, line 251
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 252
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 253
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 254
                return null // library marker kkossev.deviceProfileLib, line 255
            } // library marker kkossev.deviceProfileLib, line 256
            scaledValue = value // library marker kkossev.deviceProfileLib, line 257
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 258
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 259
            } // library marker kkossev.deviceProfileLib, line 260
            break // library marker kkossev.deviceProfileLib, line 261
        default : // library marker kkossev.deviceProfileLib, line 262
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 263
            return null // library marker kkossev.deviceProfileLib, line 264
    } // library marker kkossev.deviceProfileLib, line 265
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 266
    return scaledValue // library marker kkossev.deviceProfileLib, line 267
} // library marker kkossev.deviceProfileLib, line 268

// called from customUpdated() method in the custom driver // library marker kkossev.deviceProfileLib, line 270
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 271
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 272
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 273
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 274
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 275
        return // library marker kkossev.deviceProfileLib, line 276
    } // library marker kkossev.deviceProfileLib, line 277
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 278
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 279
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 280
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 281
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 282
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 283
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 284
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 285
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 286
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 287
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 288
                // scale the value // library marker kkossev.deviceProfileLib, line 289
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 290
            } // library marker kkossev.deviceProfileLib, line 291
            if (preferenceValue != null) { // library marker kkossev.deviceProfileLib, line 292
                setPar(name, preferenceValue.toString()) // library marker kkossev.deviceProfileLib, line 293
            } // library marker kkossev.deviceProfileLib, line 294
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 295
        } // library marker kkossev.deviceProfileLib, line 296
        else { logDebug "warning: couldn't find map for preference ${name}" ; return }  // TODO - supress the warning if the preference was boolean true/false // library marker kkossev.deviceProfileLib, line 297
    } // library marker kkossev.deviceProfileLib, line 298
    return // library marker kkossev.deviceProfileLib, line 299
} // library marker kkossev.deviceProfileLib, line 300

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 302
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 303
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 304
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 305
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 306
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 307
} // library marker kkossev.deviceProfileLib, line 308
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 309
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 310
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 311
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 312
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 313
} // library marker kkossev.deviceProfileLib, line 314
int invert(int val) { // library marker kkossev.deviceProfileLib, line 315
    if (settings.invertMotion == true) { return val == 0 ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 316
    else { return val } // library marker kkossev.deviceProfileLib, line 317
} // library marker kkossev.deviceProfileLib, line 318

// called from setPar and sendAttribite methods for non-Tuya DPs // library marker kkossev.deviceProfileLib, line 320
private List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 321
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 322
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 323
    Map map = [:] // library marker kkossev.deviceProfileLib, line 324
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 325
    try { // library marker kkossev.deviceProfileLib, line 326
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 327
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 328
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 329
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 330
        map['ep'] = (attributesMap.ep != null && attributesMap.ep != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.ep) as Integer : null // library marker kkossev.deviceProfileLib, line 331
    } // library marker kkossev.deviceProfileLib, line 332
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 333
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 334
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 335
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.ENUM8 // library marker kkossev.deviceProfileLib, line 336
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 337
    } // library marker kkossev.deviceProfileLib, line 338
    if ((map.mfgCode != null && map.mfgCode != '') || (map.ep != null && map.ep != '')) { // library marker kkossev.deviceProfileLib, line 339
        Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 340
        Map ep = map.ep != null ? ['destEndpoint':map.ep] : [:] // library marker kkossev.deviceProfileLib, line 341
        Map mapOptions = [:] // library marker kkossev.deviceProfileLib, line 342
        if (mfgCode) mapOptions.putAll(mfgCode) // library marker kkossev.deviceProfileLib, line 343
        if (ep) mapOptions.putAll(ep) // library marker kkossev.deviceProfileLib, line 344
        log.trace "$mapOptions" // library marker kkossev.deviceProfileLib, line 345
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, mapOptions, delay = 50) // library marker kkossev.deviceProfileLib, line 346
    } // library marker kkossev.deviceProfileLib, line 347
    else { // library marker kkossev.deviceProfileLib, line 348
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 50) // library marker kkossev.deviceProfileLib, line 349
    } // library marker kkossev.deviceProfileLib, line 350
    return cmds // library marker kkossev.deviceProfileLib, line 351
} // library marker kkossev.deviceProfileLib, line 352

/** // library marker kkossev.deviceProfileLib, line 354
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 355
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 356
 * // library marker kkossev.deviceProfileLib, line 357
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 358
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 359
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 360
 */ // library marker kkossev.deviceProfileLib, line 361
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 362
private def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 363
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 364
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 365
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 366
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 367
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 368
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 369
        case 'number' : // library marker kkossev.deviceProfileLib, line 370
            // TODO - negative values ! // library marker kkossev.deviceProfileLib, line 371
            // TODO - better conversion to integer! // library marker kkossev.deviceProfileLib, line 372
            value = safeToInt(val, 0) // library marker kkossev.deviceProfileLib, line 373
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 374
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 375
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 376
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 377
            } // library marker kkossev.deviceProfileLib, line 378
            else { // library marker kkossev.deviceProfileLib, line 379
                scaledValue = value // library marker kkossev.deviceProfileLib, line 380
            } // library marker kkossev.deviceProfileLib, line 381
            break // library marker kkossev.deviceProfileLib, line 382

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 384
            value = safeToDouble(val, 0.0) // library marker kkossev.deviceProfileLib, line 385
            // scale the value // library marker kkossev.deviceProfileLib, line 386
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 387
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 388
            } // library marker kkossev.deviceProfileLib, line 389
            else { // library marker kkossev.deviceProfileLib, line 390
                scaledValue = value // library marker kkossev.deviceProfileLib, line 391
            } // library marker kkossev.deviceProfileLib, line 392
            break // library marker kkossev.deviceProfileLib, line 393

        case 'bool' : // library marker kkossev.deviceProfileLib, line 395
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 396
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 397
            else { // library marker kkossev.deviceProfileLib, line 398
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 399
                return null // library marker kkossev.deviceProfileLib, line 400
            } // library marker kkossev.deviceProfileLib, line 401
            break // library marker kkossev.deviceProfileLib, line 402
        case 'enum' : // library marker kkossev.deviceProfileLib, line 403
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 404
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 405
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 406
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 407
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 408
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 409
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 410
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 411
            } // library marker kkossev.deviceProfileLib, line 412
            else { // library marker kkossev.deviceProfileLib, line 413
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 414
            } // library marker kkossev.deviceProfileLib, line 415
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 416
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 417
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 418
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 419
                // find the key for the value // library marker kkossev.deviceProfileLib, line 420
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 421
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 422
                if (key == null) { // library marker kkossev.deviceProfileLib, line 423
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 424
                    return null // library marker kkossev.deviceProfileLib, line 425
                } // library marker kkossev.deviceProfileLib, line 426
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 427
            //return null // library marker kkossev.deviceProfileLib, line 428
            } // library marker kkossev.deviceProfileLib, line 429
            break // library marker kkossev.deviceProfileLib, line 430
        default : // library marker kkossev.deviceProfileLib, line 431
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 432
            return null // library marker kkossev.deviceProfileLib, line 433
    } // library marker kkossev.deviceProfileLib, line 434
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 435
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 436
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 437
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 438
        return null // library marker kkossev.deviceProfileLib, line 439
    } // library marker kkossev.deviceProfileLib, line 440
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 441
    return scaledValue // library marker kkossev.deviceProfileLib, line 442
} // library marker kkossev.deviceProfileLib, line 443

/** // library marker kkossev.deviceProfileLib, line 445
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 446
 * // library marker kkossev.deviceProfileLib, line 447
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 448
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 449
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 450
 */ // library marker kkossev.deviceProfileLib, line 451
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 452
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 453
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 454
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 455
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 456
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 457
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 458
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 459
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 460
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 461
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 462
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 463
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 464
        log.trace "$dpMap  ${dpMap.map}" // library marker kkossev.deviceProfileLib, line 465
        String helpTxt = "setPar: invalid parameter ${par} value <b>${val}</b>." // library marker kkossev.deviceProfileLib, line 466
        if (dpMap.min != null && dpMap.max != null) { helpTxt += " Must be in the range ${dpMap.min} to ${dpMap.max}" } // library marker kkossev.deviceProfileLib, line 467
        if (dpMap.map != null) { helpTxt += " Must be one of ${dpMap.map}" } // library marker kkossev.deviceProfileLib, line 468
        logInfo helpTxt // library marker kkossev.deviceProfileLib, line 469
        return false // library marker kkossev.deviceProfileLib, line 470
    } // library marker kkossev.deviceProfileLib, line 471

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 473
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 474
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 475
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 476
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 477
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 478
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 479
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 480
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 481
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 482
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 483
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 484
            return true // library marker kkossev.deviceProfileLib, line 485
        } // library marker kkossev.deviceProfileLib, line 486
        else { // library marker kkossev.deviceProfileLib, line 487
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 488
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 489
        } // library marker kkossev.deviceProfileLib, line 490
    } // library marker kkossev.deviceProfileLib, line 491
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 492
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 493
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 494
        def valMiscType // library marker kkossev.deviceProfileLib, line 495
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 496
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 497
            // find the key for the value // library marker kkossev.deviceProfileLib, line 498
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 499
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 500
            if (key == null) { // library marker kkossev.deviceProfileLib, line 501
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 502
                return false // library marker kkossev.deviceProfileLib, line 503
            } // library marker kkossev.deviceProfileLib, line 504
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 505
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 506
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 507
        } // library marker kkossev.deviceProfileLib, line 508
        else { // library marker kkossev.deviceProfileLib, line 509
            valMiscType = val // library marker kkossev.deviceProfileLib, line 510
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 511
        } // library marker kkossev.deviceProfileLib, line 512
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 513
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 514
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 515
        return true // library marker kkossev.deviceProfileLib, line 516
    } // library marker kkossev.deviceProfileLib, line 517

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 519
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 520

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 522
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 523
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 524
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 525
        // Tuya DP // library marker kkossev.deviceProfileLib, line 526
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 527
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 528
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 529
            return false // library marker kkossev.deviceProfileLib, line 530
        } // library marker kkossev.deviceProfileLib, line 531
        else { // library marker kkossev.deviceProfileLib, line 532
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 533
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 534
            return false // library marker kkossev.deviceProfileLib, line 535
        } // library marker kkossev.deviceProfileLib, line 536
    } // library marker kkossev.deviceProfileLib, line 537
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 538
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 539
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mfgCode=${dpMap.mfgCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 540
        int signedIntScaled = convertSignedInts(scaledValue, dpMap) // library marker kkossev.deviceProfileLib, line 541
        cmds = zclWriteAttribute(dpMap, signedIntScaled) // library marker kkossev.deviceProfileLib, line 542
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 543
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 544
            return false // library marker kkossev.deviceProfileLib, line 545
        } // library marker kkossev.deviceProfileLib, line 546
    } // library marker kkossev.deviceProfileLib, line 547
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 548
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 549
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 550
    return true // library marker kkossev.deviceProfileLib, line 551
} // library marker kkossev.deviceProfileLib, line 552

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 554
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 555
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 556
public List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 557
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 558
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 559
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 560
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 561
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 562
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 563
        return [] // library marker kkossev.deviceProfileLib, line 564
    } // library marker kkossev.deviceProfileLib, line 565
    String dpType // library marker kkossev.deviceProfileLib, line 566
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 567
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 568
    } // library marker kkossev.deviceProfileLib, line 569
    else { // library marker kkossev.deviceProfileLib, line 570
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 571
    } // library marker kkossev.deviceProfileLib, line 572
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 573
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 574
        return [] // library marker kkossev.deviceProfileLib, line 575
    } // library marker kkossev.deviceProfileLib, line 576
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 577
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 578
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 579
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 580
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 581
    } // library marker kkossev.deviceProfileLib, line 582
    else { // library marker kkossev.deviceProfileLib, line 583
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 584
    } // library marker kkossev.deviceProfileLib, line 585
    return cmds // library marker kkossev.deviceProfileLib, line 586
} // library marker kkossev.deviceProfileLib, line 587

private int convertSignedInts(int val, Map dpMap) { // library marker kkossev.deviceProfileLib, line 589
    if (dpMap.dt == '0x28') { // library marker kkossev.deviceProfileLib, line 590
        if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 591
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    else if (dpMap.dt == '0x29') { // library marker kkossev.deviceProfileLib, line 594
        if (val > 32767) { return (val as int) - 65536 } // library marker kkossev.deviceProfileLib, line 595
        else { return (val as int) } // library marker kkossev.deviceProfileLib, line 596
    } // library marker kkossev.deviceProfileLib, line 597
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 598
} // library marker kkossev.deviceProfileLib, line 599

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 601
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 602
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 603
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 604
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 605
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug 'DEVICE.preferences is empty!' ; return false } // library marker kkossev.deviceProfileLib, line 606

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 608
    l//log.trace "sendAttribute: dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 609
    if (dpMap == null || dpMap?.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 610
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 611
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 612
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 613
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 614
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 615
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 616
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 617
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 618
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 619
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 620
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 621
        try { // library marker kkossev.deviceProfileLib, line 622
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 623
        } // library marker kkossev.deviceProfileLib, line 624
        catch (e) { // library marker kkossev.deviceProfileLib, line 625
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 626
            return false // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 629
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 630
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 631
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 632
            return true // library marker kkossev.deviceProfileLib, line 633
        } // library marker kkossev.deviceProfileLib, line 634
        else { // library marker kkossev.deviceProfileLib, line 635
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 636
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 637
        } // library marker kkossev.deviceProfileLib, line 638
    } // library marker kkossev.deviceProfileLib, line 639
    else { // library marker kkossev.deviceProfileLib, line 640
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 641
    } // library marker kkossev.deviceProfileLib, line 642
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 643
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 644
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 645
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 646
        // patch !! // library marker kkossev.deviceProfileLib, line 647
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 648
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 649
        } // library marker kkossev.deviceProfileLib, line 650
        else { // library marker kkossev.deviceProfileLib, line 651
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 652
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 653
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 654
        } // library marker kkossev.deviceProfileLib, line 655
        return true // library marker kkossev.deviceProfileLib, line 656
    } // library marker kkossev.deviceProfileLib, line 657
    else { // library marker kkossev.deviceProfileLib, line 658
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 659
    } // library marker kkossev.deviceProfileLib, line 660
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 661
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 662
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 663
    try { // library marker kkossev.deviceProfileLib, line 664
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 665
    } // library marker kkossev.deviceProfileLib, line 666
    catch (e) { // library marker kkossev.deviceProfileLib, line 667
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 668
        return false // library marker kkossev.deviceProfileLib, line 669
    } // library marker kkossev.deviceProfileLib, line 670
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 671
        // Tuya DP // library marker kkossev.deviceProfileLib, line 672
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 673
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 674
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 675
            return false // library marker kkossev.deviceProfileLib, line 676
        } // library marker kkossev.deviceProfileLib, line 677
        else { // library marker kkossev.deviceProfileLib, line 678
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 679
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 680
            return true // library marker kkossev.deviceProfileLib, line 681
        } // library marker kkossev.deviceProfileLib, line 682
    } // library marker kkossev.deviceProfileLib, line 683
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 684
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 685
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 686
    } // library marker kkossev.deviceProfileLib, line 687
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 688
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 689
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 690
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 691
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 692
            return false // library marker kkossev.deviceProfileLib, line 693
        } // library marker kkossev.deviceProfileLib, line 694
    } // library marker kkossev.deviceProfileLib, line 695
    else { // library marker kkossev.deviceProfileLib, line 696
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 697
        return false // library marker kkossev.deviceProfileLib, line 698
    } // library marker kkossev.deviceProfileLib, line 699
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 700
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 701
    return true // library marker kkossev.deviceProfileLib, line 702
} // library marker kkossev.deviceProfileLib, line 703

/** // library marker kkossev.deviceProfileLib, line 705
 * SENDS a list of Zigbee commands to be sent to the device. // library marker kkossev.deviceProfileLib, line 706
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 707
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 708
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 709
 */ // library marker kkossev.deviceProfileLib, line 710
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 711
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 712
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 713
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 714
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 715
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 716
    if (supportedCommandsMap == null || supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 717
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 718
        return false // library marker kkossev.deviceProfileLib, line 719
    } // library marker kkossev.deviceProfileLib, line 720
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 721
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 722
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 723
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 724
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 725
        return false // library marker kkossev.deviceProfileLib, line 726
    } // library marker kkossev.deviceProfileLib, line 727
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 728
    def func, funcResult // library marker kkossev.deviceProfileLib, line 729
    try { // library marker kkossev.deviceProfileLib, line 730
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 731
        // added 01/25/2025 : the commands now can be shorted : instead of a map kay and value 'printFingerprints':'printFingerprints' we can skip the value when it is the same:  'printFingerprints:'  - the value is the same as the key // library marker kkossev.deviceProfileLib, line 732
        if (func == null || func == '') { // library marker kkossev.deviceProfileLib, line 733
            func = command // library marker kkossev.deviceProfileLib, line 734
        } // library marker kkossev.deviceProfileLib, line 735
        if (val != null && val != '') { // library marker kkossev.deviceProfileLib, line 736
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 737
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 738
        } // library marker kkossev.deviceProfileLib, line 739
        else { // library marker kkossev.deviceProfileLib, line 740
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 741
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 742
        } // library marker kkossev.deviceProfileLib, line 743
    } // library marker kkossev.deviceProfileLib, line 744
    catch (e) { // library marker kkossev.deviceProfileLib, line 745
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 746
        return false // library marker kkossev.deviceProfileLib, line 747
    } // library marker kkossev.deviceProfileLib, line 748
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 749
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 750
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 751
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 752
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 753
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 754
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 755
        } // library marker kkossev.deviceProfileLib, line 756
    } // library marker kkossev.deviceProfileLib, line 757
    else if (funcResult == null) { // library marker kkossev.deviceProfileLib, line 758
        return false // library marker kkossev.deviceProfileLib, line 759
    } // library marker kkossev.deviceProfileLib, line 760
     else { // library marker kkossev.deviceProfileLib, line 761
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 762
        return false // library marker kkossev.deviceProfileLib, line 763
    } // library marker kkossev.deviceProfileLib, line 764
    return true // library marker kkossev.deviceProfileLib, line 765
} // library marker kkossev.deviceProfileLib, line 766

/** // library marker kkossev.deviceProfileLib, line 768
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 769
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 770
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 771
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 772
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 773
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 774
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 775
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 776
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 777
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 778
 */ // library marker kkossev.deviceProfileLib, line 779
public Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 780
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 781
    Map input = [:] // library marker kkossev.deviceProfileLib, line 782
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 783
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 784
    Object preference // library marker kkossev.deviceProfileLib, line 785
    try { preference = DEVICE?.preferences["$param"] } // library marker kkossev.deviceProfileLib, line 786
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 787
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 788
    try { if (preference in [true, false]) { if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } ; return [:] } } // library marker kkossev.deviceProfileLib, line 789
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } ; return [:] } // library marker kkossev.deviceProfileLib, line 790
    /* // library marker kkossev.deviceProfileLib, line 791
    // TODO - check if this is neccessary? isTuyaDP is not defined! // library marker kkossev.deviceProfileLib, line 792
    try { isTuyaDP = preference.isNumber() } // library marker kkossev.deviceProfileLib, line 793
    catch (e) { if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 794
    */ // library marker kkossev.deviceProfileLib, line 795
    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 796
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 797
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 798
    if (foundMap == null || foundMap?.isEmpty()) { if (debug) { log.warn "inputIt: map not found for param '${param}'!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 799
    if (foundMap.rw != 'rw') { if (debug) { log.warn "inputIt: param '${param}' is read only!" } ; return [:]  } // library marker kkossev.deviceProfileLib, line 800
    if (foundMap.advanced != null && foundMap.advanced == true && settings.advancedOptions != true) { // library marker kkossev.deviceProfileLib, line 801
        if (debug) { log.debug "inputIt: param '${param}' is advanced!" } // library marker kkossev.deviceProfileLib, line 802
        return [:] // library marker kkossev.deviceProfileLib, line 803
    } // library marker kkossev.deviceProfileLib, line 804
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 805
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 806
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 807
    //input.description = (foundMap.description ?: foundMap.title)?.replaceAll(/<\/?b>/, '')  // if description is not defined, use the title // library marker kkossev.deviceProfileLib, line 808
    input.description = foundMap.description ?: ''   // if description is not defined, skip it // library marker kkossev.deviceProfileLib, line 809
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 810
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 811
            //input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 812
            input.range = "${Math.ceil(foundMap.min) as int}..${Math.ceil(foundMap.max) as int}" // library marker kkossev.deviceProfileLib, line 813
        } // library marker kkossev.deviceProfileLib, line 814
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 815
            if (input.description != '') { input.description += '<br>' } // library marker kkossev.deviceProfileLib, line 816
            input.description += "<i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 817
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 818
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 819
            } // library marker kkossev.deviceProfileLib, line 820
        } // library marker kkossev.deviceProfileLib, line 821
    } // library marker kkossev.deviceProfileLib, line 822
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 823
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 824
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 825
    }/* // library marker kkossev.deviceProfileLib, line 826
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 827
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 828
    }*/ // library marker kkossev.deviceProfileLib, line 829
    else { // library marker kkossev.deviceProfileLib, line 830
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 831
        return [:] // library marker kkossev.deviceProfileLib, line 832
    } // library marker kkossev.deviceProfileLib, line 833
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 834
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 835
    } // library marker kkossev.deviceProfileLib, line 836
    return input // library marker kkossev.deviceProfileLib, line 837
} // library marker kkossev.deviceProfileLib, line 838

/** // library marker kkossev.deviceProfileLib, line 840
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 841
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 842
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 843
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 844
 */ // library marker kkossev.deviceProfileLib, line 845
public List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 846
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 847
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 848
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 849
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 850
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 851
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 852
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 853
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].description ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 854
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 855
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 856
            } // library marker kkossev.deviceProfileLib, line 857
        } // library marker kkossev.deviceProfileLib, line 858
    } // library marker kkossev.deviceProfileLib, line 859
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 860
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 861
    } // library marker kkossev.deviceProfileLib, line 862
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 863
} // library marker kkossev.deviceProfileLib, line 864

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 866
public void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 867
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 868
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 869
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 870
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 871
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 872
    } // library marker kkossev.deviceProfileLib, line 873
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 874
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 875
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 876
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 877
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 878
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 879
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 880
    } else { // library marker kkossev.deviceProfileLib, line 881
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 882
    } // library marker kkossev.deviceProfileLib, line 883
} // library marker kkossev.deviceProfileLib, line 884

public List<String> refreshFromConfigureReadList(List<String> refreshList) { // library marker kkossev.deviceProfileLib, line 886
    logDebug "refreshFromConfigureReadList(${refreshList})" // library marker kkossev.deviceProfileLib, line 887
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 888
    if (refreshList != null && !refreshList.isEmpty()) { // library marker kkossev.deviceProfileLib, line 889
        //List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 890
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 891
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 892
            if (k != null) { // library marker kkossev.deviceProfileLib, line 893
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 894
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 895
                if (map != null) { // library marker kkossev.deviceProfileLib, line 896
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 897
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 898
                } // library marker kkossev.deviceProfileLib, line 899
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 900
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 901
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 902
                } // library marker kkossev.deviceProfileLib, line 903
            } // library marker kkossev.deviceProfileLib, line 904
        } // library marker kkossev.deviceProfileLib, line 905
    } // library marker kkossev.deviceProfileLib, line 906
    return cmds // library marker kkossev.deviceProfileLib, line 907
} // library marker kkossev.deviceProfileLib, line 908

// called from customRefresh() in the device drivers // library marker kkossev.deviceProfileLib, line 910
public List<String> refreshFromDeviceProfileList() { // library marker kkossev.deviceProfileLib, line 911
    logDebug 'refreshFromDeviceProfileList()' // library marker kkossev.deviceProfileLib, line 912
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 913
    if (DEVICE?.refresh != null) { // library marker kkossev.deviceProfileLib, line 914
        List<String> refreshList = DEVICE.refresh // library marker kkossev.deviceProfileLib, line 915
        for (String k : refreshList) { // library marker kkossev.deviceProfileLib, line 916
            k = k.replaceAll('\\[|\\]', '') // library marker kkossev.deviceProfileLib, line 917
            if (k != null) { // library marker kkossev.deviceProfileLib, line 918
                // check whether the string in the refreshList matches an attribute name in the DEVICE.attributes list // library marker kkossev.deviceProfileLib, line 919
                Map map = DEVICE.attributes.find { it.name == k } // library marker kkossev.deviceProfileLib, line 920
                if (map != null) { // library marker kkossev.deviceProfileLib, line 921
                    Map mfgCode = map.mfgCode != null ? ['mfgCode':map.mfgCode] : [:] // library marker kkossev.deviceProfileLib, line 922
                    cmds += zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[0]), hubitat.helper.HexUtils.hexStringToInt((map.at).split(':')[1]), mfgCode, delay = 100) // library marker kkossev.deviceProfileLib, line 923
                } // library marker kkossev.deviceProfileLib, line 924
                // check whether the string in the refreshList matches a method defined somewhere in the code // library marker kkossev.deviceProfileLib, line 925
                if (this.respondsTo(k)) { // library marker kkossev.deviceProfileLib, line 926
                    cmds += this."${k}"() // library marker kkossev.deviceProfileLib, line 927
                } // library marker kkossev.deviceProfileLib, line 928
            } // library marker kkossev.deviceProfileLib, line 929
        } // library marker kkossev.deviceProfileLib, line 930
    } // library marker kkossev.deviceProfileLib, line 931
    return cmds // library marker kkossev.deviceProfileLib, line 932
} // library marker kkossev.deviceProfileLib, line 933

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 935
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 936
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 937
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 938
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 939
    return cmds // library marker kkossev.deviceProfileLib, line 940
} // library marker kkossev.deviceProfileLib, line 941

// TODO ! - remove? // library marker kkossev.deviceProfileLib, line 943
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 944
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 945
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 946
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 947
    return cmds // library marker kkossev.deviceProfileLib, line 948
} // library marker kkossev.deviceProfileLib, line 949

// TODO! - remove? // library marker kkossev.deviceProfileLib, line 951
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 952
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 953
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 954
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 955
    return cmds // library marker kkossev.deviceProfileLib, line 956
} // library marker kkossev.deviceProfileLib, line 957

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 959
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 960
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 961
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 962
    } // library marker kkossev.deviceProfileLib, line 963
} // library marker kkossev.deviceProfileLib, line 964

public void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 966
    String ps = DEVICE?.device?.powerSource // library marker kkossev.deviceProfileLib, line 967
    logDebug "initEventsDeviceProfile(${fullInit}) for deviceProfile=${state.deviceProfile} DEVICE?.device?.powerSource=${ps} ps.isEmpty()=${ps?.isEmpty()}" // library marker kkossev.deviceProfileLib, line 968
    if (ps != null && !ps.isEmpty()) { // library marker kkossev.deviceProfileLib, line 969
        sendEvent(name: 'powerSource', value: ps, descriptionText: "Power Source set to '${ps}'", type: 'digital') // library marker kkossev.deviceProfileLib, line 970
    } // library marker kkossev.deviceProfileLib, line 971
} // library marker kkossev.deviceProfileLib, line 972

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 974

// // library marker kkossev.deviceProfileLib, line 976
// called from parse() // library marker kkossev.deviceProfileLib, line 977
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profile // library marker kkossev.deviceProfileLib, line 978
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 979
// // library marker kkossev.deviceProfileLib, line 980
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 981
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 982
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 983
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 984
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 985
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 986
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 987
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 988
} // library marker kkossev.deviceProfileLib, line 989

// // library marker kkossev.deviceProfileLib, line 991
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 992
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profile // library marker kkossev.deviceProfileLib, line 993
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 994
// // library marker kkossev.deviceProfileLib, line 995
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 996
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 997
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 998
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 999
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 1000
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 1001
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 1002
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 1003
} // library marker kkossev.deviceProfileLib, line 1004

// all DPs are spammy - sent periodically! (this function is not used?) // library marker kkossev.deviceProfileLib, line 1006
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 1007
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 1008
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 1009
    return isSpammy // library marker kkossev.deviceProfileLib, line 1010
} // library marker kkossev.deviceProfileLib, line 1011

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1013
private List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 1014
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 1015
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 1016
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 1017
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1018
    } // library marker kkossev.deviceProfileLib, line 1019
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1020
} // library marker kkossev.deviceProfileLib, line 1021

private List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 1023
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 1024
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1025
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 1026
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1027
    } // library marker kkossev.deviceProfileLib, line 1028
    else { // library marker kkossev.deviceProfileLib, line 1029
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 1030
    } // library marker kkossev.deviceProfileLib, line 1031
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 1032
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1033
} // library marker kkossev.deviceProfileLib, line 1034

private List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 1036
    Double convertedValue // library marker kkossev.deviceProfileLib, line 1037
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1038
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 1039
    } // library marker kkossev.deviceProfileLib, line 1040
    else { // library marker kkossev.deviceProfileLib, line 1041
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1042
    } // library marker kkossev.deviceProfileLib, line 1043
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1044
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1045
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1046
} // library marker kkossev.deviceProfileLib, line 1047

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1049
private List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 1050
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 1051
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1052
    def convertedValue // library marker kkossev.deviceProfileLib, line 1053
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 1054
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 1055
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 1056
    } // library marker kkossev.deviceProfileLib, line 1057
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 1058
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 1059
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 1060
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 1061
            isEqual = false // library marker kkossev.deviceProfileLib, line 1062
        } // library marker kkossev.deviceProfileLib, line 1063
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 1064
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 1065
        } // library marker kkossev.deviceProfileLib, line 1066
    } // library marker kkossev.deviceProfileLib, line 1067
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1068
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 1069
} // library marker kkossev.deviceProfileLib, line 1070

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 1072
private List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 1073
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1074
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1075
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1076
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1077
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 1078
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1079
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 1080
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1081
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1082
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1083
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1084
            break // library marker kkossev.deviceProfileLib, line 1085
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1086
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1087
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1088
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1089
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1090
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1091
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1092
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1093
            } // library marker kkossev.deviceProfileLib, line 1094
            else { // library marker kkossev.deviceProfileLib, line 1095
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1096
            } // library marker kkossev.deviceProfileLib, line 1097
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1098
            break // library marker kkossev.deviceProfileLib, line 1099
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1100
        case 'number' : // library marker kkossev.deviceProfileLib, line 1101
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1102
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1103
            break // library marker kkossev.deviceProfileLib, line 1104
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1105
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1106
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1107
            break // library marker kkossev.deviceProfileLib, line 1108
        default : // library marker kkossev.deviceProfileLib, line 1109
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1110
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1111
    } // library marker kkossev.deviceProfileLib, line 1112
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1113
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1114
    } // library marker kkossev.deviceProfileLib, line 1115
    // // library marker kkossev.deviceProfileLib, line 1116
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1117
} // library marker kkossev.deviceProfileLib, line 1118

// // library marker kkossev.deviceProfileLib, line 1120
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1121
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1122
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1123
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1124
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1125
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1126
// // library marker kkossev.deviceProfileLib, line 1127
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1128
// // library marker kkossev.deviceProfileLib, line 1129
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1130
// // library marker kkossev.deviceProfileLib, line 1131
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1132
private List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1133
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1134
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1135
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1136
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1137
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1138
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1139
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1140
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1141
            break // library marker kkossev.deviceProfileLib, line 1142
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1143
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1144
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1145
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1146
            logTrace "latestEvent is ${latestEvent} dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1147
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1148
            if (dataType == null || dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1149
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1150
            } // library marker kkossev.deviceProfileLib, line 1151
            else { // library marker kkossev.deviceProfileLib, line 1152
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1153
            } // library marker kkossev.deviceProfileLib, line 1154
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1155
            break // library marker kkossev.deviceProfileLib, line 1156
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1157
        case 'number' : // library marker kkossev.deviceProfileLib, line 1158
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1159
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1160
            break // library marker kkossev.deviceProfileLib, line 1161
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1162
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1163
            break // library marker kkossev.deviceProfileLib, line 1164
        default : // library marker kkossev.deviceProfileLib, line 1165
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1166
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1167
    } // library marker kkossev.deviceProfileLib, line 1168
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1169
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1170
} // library marker kkossev.deviceProfileLib, line 1171

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1173
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1174
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1175
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1176
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1177
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1178
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1179
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1180
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1181
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1182
    } // library marker kkossev.deviceProfileLib, line 1183
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1184
    try { // library marker kkossev.deviceProfileLib, line 1185
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1186
    } // library marker kkossev.deviceProfileLib, line 1187
    catch (e) { // library marker kkossev.deviceProfileLib, line 1188
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1189
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1190
    } // library marker kkossev.deviceProfileLib, line 1191
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1192
    return fncmd // library marker kkossev.deviceProfileLib, line 1193
} // library marker kkossev.deviceProfileLib, line 1194

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1196
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1197
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1198
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1199
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1200
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1201
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1202

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1204
    if (attribMap == null || attribMap?.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1205

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1207
    int value // library marker kkossev.deviceProfileLib, line 1208
    try { // library marker kkossev.deviceProfileLib, line 1209
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1210
    } // library marker kkossev.deviceProfileLib, line 1211
    catch (e) { // library marker kkossev.deviceProfileLib, line 1212
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1213
        return false // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1216
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1217
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1218
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1219
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1220
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1221
        return false // library marker kkossev.deviceProfileLib, line 1222
    } // library marker kkossev.deviceProfileLib, line 1223
    value = convertSignedInts(value, foundItem) // library marker kkossev.deviceProfileLib, line 1224
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1225
} // library marker kkossev.deviceProfileLib, line 1226

/** // library marker kkossev.deviceProfileLib, line 1228
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1229
 * // library marker kkossev.deviceProfileLib, line 1230
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1231
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1232
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1233
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1234
 * // library marker kkossev.deviceProfileLib, line 1235
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1236
 */ // library marker kkossev.deviceProfileLib, line 1237
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1238
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1239
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1240
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1241
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1242

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1244
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1245

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1247
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1248
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1249
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1250
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1251
        return false // library marker kkossev.deviceProfileLib, line 1252
    } // library marker kkossev.deviceProfileLib, line 1253
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1254
} // library marker kkossev.deviceProfileLib, line 1255

/* // library marker kkossev.deviceProfileLib, line 1257
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1258
 */ // library marker kkossev.deviceProfileLib, line 1259
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1260
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1261
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1262
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1263
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1264
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1265
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1266
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1267
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1268
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1269
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1270
        } // library marker kkossev.deviceProfileLib, line 1271
    } // library marker kkossev.deviceProfileLib, line 1272
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1273

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1275
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1276
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1277
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1278
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1279
    boolean preferenceExists = (DEVICE?.preferences != null &&  !DEVICE?.preferences?.isEmpty()) ? DEVICE?.preferences?.containsKey(foundItem.name) : false         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1280
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1281
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1282
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1283
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1284
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1285
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1286
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1287
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1288
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1289
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1290
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1291

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1293
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1294
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1295
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1296
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1297
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1298
            if (settings.logEnable) { logInfo "${descText} (Debug logging is enabled)" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1299
        } // library marker kkossev.deviceProfileLib, line 1300
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1301
    } // library marker kkossev.deviceProfileLib, line 1302

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1304
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1305
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1306
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1307
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1308
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1309
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1310
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1311
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1312
            } // library marker kkossev.deviceProfileLib, line 1313
        } // library marker kkossev.deviceProfileLib, line 1314
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1315
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1316
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1317
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1320
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1321
            try { // library marker kkossev.deviceProfileLib, line 1322
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1323
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1324
            } // library marker kkossev.deviceProfileLib, line 1325
            catch (e) { // library marker kkossev.deviceProfileLib, line 1326
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1327
            } // library marker kkossev.deviceProfileLib, line 1328
        } // library marker kkossev.deviceProfileLib, line 1329
    } // library marker kkossev.deviceProfileLib, line 1330
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1331
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1332
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1333
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1334
    } // library marker kkossev.deviceProfileLib, line 1335

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1337
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1338
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1339
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1340
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1341
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1342
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1343
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1344
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1345
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1346
            } // library marker kkossev.deviceProfileLib, line 1347
            if (foundItem.processDuplicated == true) { // library marker kkossev.deviceProfileLib, line 1348
                logDebug 'processDuplicated=true -> continue' // library marker kkossev.deviceProfileLib, line 1349
            } // library marker kkossev.deviceProfileLib, line 1350

            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1352
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1353
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1354
            // continue ... // library marker kkossev.deviceProfileLib, line 1355
            } // library marker kkossev.deviceProfileLib, line 1356

            else { // library marker kkossev.deviceProfileLib, line 1358
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1359
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1360
                } // library marker kkossev.deviceProfileLib, line 1361
                else { // library marker kkossev.deviceProfileLib, line 1362
                    //log.trace "should not be here !!!!!!!!!!" // library marker kkossev.deviceProfileLib, line 1363
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1364
                } // library marker kkossev.deviceProfileLib, line 1365
            } // library marker kkossev.deviceProfileLib, line 1366
        } // library marker kkossev.deviceProfileLib, line 1367

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1369
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1370
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1371
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1372
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1373
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1374
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1375
        } // library marker kkossev.deviceProfileLib, line 1376
        else { // library marker kkossev.deviceProfileLib, line 1377
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1378
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1379
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1380
                logTrace "event ${name} sent w/ valueScaled ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1381
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1382
            } // library marker kkossev.deviceProfileLib, line 1383
        } // library marker kkossev.deviceProfileLib, line 1384
    } // library marker kkossev.deviceProfileLib, line 1385
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1386
} // library marker kkossev.deviceProfileLib, line 1387

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1389
public boolean validateAndFixPreferences(String debugStr) { return validateAndFixPreferences(debugStr.toBoolean() as boolean) } // library marker kkossev.deviceProfileLib, line 1390
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1391
    //debug = true // library marker kkossev.deviceProfileLib, line 1392
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1393
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1394
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1395
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1396
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1397
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1398
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1399
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1400
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1401
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1402
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1403
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1404
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1405
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1406
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1407
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1408
            try { // library marker kkossev.deviceProfileLib, line 1409
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1410
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1411
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1412
            } // library marker kkossev.deviceProfileLib, line 1413
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1414
            try { // library marker kkossev.deviceProfileLib, line 1415
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1416
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1417
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1418
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1419
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1420
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1421
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1422
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1423
                    } // library marker kkossev.deviceProfileLib, line 1424
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1425
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1426
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1427
                    } else { // library marker kkossev.deviceProfileLib, line 1428
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1429
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1430
                    } // library marker kkossev.deviceProfileLib, line 1431
                } // library marker kkossev.deviceProfileLib, line 1432
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1433
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1434
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1435
            } // library marker kkossev.deviceProfileLib, line 1436
            catch (e) { // library marker kkossev.deviceProfileLib, line 1437
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1438
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1439
                try { // library marker kkossev.deviceProfileLib, line 1440
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1441
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1442
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1443
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1444
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1445
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1446
                } // library marker kkossev.deviceProfileLib, line 1447
            } // library marker kkossev.deviceProfileLib, line 1448
        } // library marker kkossev.deviceProfileLib, line 1449
        total ++ // library marker kkossev.deviceProfileLib, line 1450
    } // library marker kkossev.deviceProfileLib, line 1451
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1452
    return true // library marker kkossev.deviceProfileLib, line 1453
} // library marker kkossev.deviceProfileLib, line 1454

public String fingerprintIt(Map profileMap, Map fingerprint) { // library marker kkossev.deviceProfileLib, line 1456
    if (profileMap == null) { return 'profileMap is null' } // library marker kkossev.deviceProfileLib, line 1457
    if (fingerprint == null) { return 'fingerprint is null' } // library marker kkossev.deviceProfileLib, line 1458
    Map defaultFingerprint = profileMap.defaultFingerprint ?: [:] // library marker kkossev.deviceProfileLib, line 1459
    // if there is no defaultFingerprint, use the fingerprint as is // library marker kkossev.deviceProfileLib, line 1460
    if (defaultFingerprint == [:]) { // library marker kkossev.deviceProfileLib, line 1461
        return fingerprint.toString() // library marker kkossev.deviceProfileLib, line 1462
    } // library marker kkossev.deviceProfileLib, line 1463
    // for the missing keys, use the default values // library marker kkossev.deviceProfileLib, line 1464
    String fingerprintStr = '' // library marker kkossev.deviceProfileLib, line 1465
    defaultFingerprint.each { key, value -> // library marker kkossev.deviceProfileLib, line 1466
        String keyValue = fingerprint[key] ?: value // library marker kkossev.deviceProfileLib, line 1467
        fingerprintStr += "${key}:'${keyValue}', " // library marker kkossev.deviceProfileLib, line 1468
    } // library marker kkossev.deviceProfileLib, line 1469
    // remove the last comma and space // library marker kkossev.deviceProfileLib, line 1470
    fingerprintStr = fingerprintStr[0..-3] // library marker kkossev.deviceProfileLib, line 1471
    return fingerprintStr // library marker kkossev.deviceProfileLib, line 1472
} // library marker kkossev.deviceProfileLib, line 1473

public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1475
    int count = 0 // library marker kkossev.deviceProfileLib, line 1476
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1477
        logInfo "Device Profile: ${profileName}" // library marker kkossev.deviceProfileLib, line 1478
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1479
            log.info "${fingerprintIt(profileMap, fingerprint)}" // library marker kkossev.deviceProfileLib, line 1480
            count++ // library marker kkossev.deviceProfileLib, line 1481
        } // library marker kkossev.deviceProfileLib, line 1482
    } // library marker kkossev.deviceProfileLib, line 1483
    logInfo "Total fingerprints: ${count}" // library marker kkossev.deviceProfileLib, line 1484
} // library marker kkossev.deviceProfileLib, line 1485

public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1487
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1488
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1489
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1490
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1491
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1492
                log.info inputMap // library marker kkossev.deviceProfileLib, line 1493
            } // library marker kkossev.deviceProfileLib, line 1494
        } // library marker kkossev.deviceProfileLib, line 1495
    } // library marker kkossev.deviceProfileLib, line 1496
} // library marker kkossev.deviceProfileLib, line 1497

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
