/**
 *  MIT License
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 *  ver. 1.0.0  2022-06-29 kkossev  - first beta version
 * 
 *                         TODO: add driver version
*/

import groovy.transform.Field

@Field static final Boolean _DEBUG = true
@Field static final String DRIVER_VERSION =  '1.0.0'
@Field static final String DATE_TIME_STAMP = '06/29/2025 5:24 PM'

metadata {
    definition(
        name: 'ESPHome Apollo TEMP-1B',
        namespace: 'esphome',
        author: 'Krassimir Kossev',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/ESPHome/Drivers/ESPHome/Apollo%20TEMP-1B.groovy') {

        capability 'Sensor'
        capability 'Refresh'
        capability 'RelativeHumidityMeasurement'
        capability 'SignalStrength'
        capability 'TemperatureMeasurement'
        capability 'Battery'
        capability 'Initialize'

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
        attribute "boardTemperature", "number"
        attribute 'espTemperature', 'number'
        attribute 'temperatureProbe', 'number'  // Add this line
        attribute "uptime", "number"
        attribute 'rgbLight', 'enum', ['on', 'off'] 
        attribute 'batteryVoltage', 'number'
        attribute 'foodProbe', 'number'
        attribute 'alarmOutsideTempRange', 'enum', ['on', 'off']
        attribute 'tempProbeOffset', 'number'
        attribute 'foodProbeOffset', 'number'
        attribute 'boardTemperatureOffset', 'number'
        attribute 'boardHumidityOffset', 'number'
        attribute 'notifyOnlyOutsideTempDifference', 'enum', ['on', 'off']
        attribute 'preventSleep', 'enum', ['on', 'off']
        attribute 'selectedProbe', 'string'
        attribute 'sleepDuration', 'number'
        attribute 'probeTempDifferenceThreshold', 'number'
        attribute 'minProbeTemp', 'number'
        attribute 'maxProbeTemp', 'number'

        command 'setRgbLight', [[name:'LED control', type: 'ENUM', constraints: ['off', 'on']]]
    }

    preferences {
        input name: 'logEnable', type: 'bool', title: 'Enable Debug Logging', required: false, defaultValue: false    // if enabled the library will log debug details
        input name: 'txtEnable', type: 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
        input name: 'ipAddress', type: 'text', title: 'Device IP Address', required: true    // required setting for API library
        input name: 'selectedProbe', type: 'enum', title: 'Temperature Sensor Selection', required: false, options: ['Temperature', 'Food'], defaultValue: 'Temperature', description: 'Select which sensor to use for main temperature attribute'    // allows the user to select which sensor to use for temperature
        input name: 'boardHumidityOffset', type: 'decimal', title: 'Board Humidity Offset (%)', required: false, defaultValue: 0.0, range: '-50..50', description: 'Calibration offset for board humidity sensor'
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'Flip to see or hide the advanced options', defaultValue: false
        if (advancedOptions == true) {
            input name: 'password', type: 'text', title: 'Device Password <i>(if required)</i>', required: false     // optional setting for API library
            input name: 'diagnosticsReporting', type: 'bool', title: 'Enable Diagnostic Attributes', required: false, defaultValue: false, description: 'Enable reporting of technical diagnostic attributes (advanced users only)'
            input name: 'logWarnEnable', type: 'bool', title: 'Enable warning logging', required: false, defaultValue: true, description: '<i>Enables API Library warnings and info logging.</i>'
        }
    }
}

@Field static final Map<String, Map<String, Object>> ALL_ENTITIES = [
    'alarm_outside_temp_range':            [attr: 'alarmOutsideTempRange',          isDiag: true,  type: 'switch',      description: 'Temperature range alarm switch'],
    'battery_level':                       [attr: 'battery',                        isDiag: false, type: 'sensor',      description: 'Battery charge level percentage'],
    'battery_voltage':                     [attr: 'batteryVoltage',                 isDiag: false, type: 'sensor',      description: 'Battery voltage measurement'],
    'board_humidity':                      [attr: 'humidity',                       isDiag: false, type: 'sensor',      description: 'Humidity'],                                   // Internal board humidity sensor reading
    'board_humidity_offset':               [attr: 'boardHumidityOffset',            isDiag: true,  type: 'offset',      description: 'Board humidity sensor calibration offset'],
    'board_temperature':                   [attr: 'boardTemperature',               isDiag: true,  type: 'temperature', description: 'Internal board temperature sensor reading'],
    'board_temperature_offset':            [attr: 'boardTemperatureOffset',         isDiag: true,  type: 'offset',      description: 'Board temperature sensor calibration offset'],
    'esp_reboot':                          [attr: 'espReboot',                      isDiag: true,  type: 'button',      description: 'ESP device reboot button'],
    'esp_temperature':                     [attr: 'espTemperature',                 isDiag: true,  type: 'temperature', description: 'ESP32 chip internal temperature'],
    'factory_reset_esp':                   [attr: 'factoryResetEsp',                isDiag: true,  type: 'button',      description: 'Factory reset ESP device button'],
    'food_probe':                          [attr: 'foodProbe',                      isDiag: true,  type: 'temperature', description: 'External food probe temperature reading'],
    'food_probe_offset':                   [attr: 'foodProbeOffset',                isDiag: true,  type: 'offset',      description: 'Food probe calibration offset'],
    'max_probe_temp':                      [attr: 'maxProbeTemp',                   isDiag: true,  type: 'config',      description: 'Maximum valid probe temperature threshold'],
    'min_probe_temp':                      [attr: 'minProbeTemp',                   isDiag: true,  type: 'config',      description: 'Minimum valid probe temperature threshold'],
    'notify_only_outside_temp_difference': [attr: 'notifyOnlyOutsideTempDifference',isDiag: true,  type: 'switch',      description: 'Notify only when outside temperature difference threshold'],
    'online':                              [attr: 'networkStatus',                  isDiag: true,  type: 'status',      description: 'Network connection status'],
    'prevent_sleep':                       [attr: 'preventSleep',                   isDiag: true,  type: 'switch',      description: 'Prevent device sleep mode switch'],
    'probe_temp_difference_threshold':     [attr: 'probeTempDifferenceThreshold',   isDiag: true,  type: 'config',      description: 'Temperature difference threshold for notifications'],
    'rgb_light':                           [attr: 'rgbLight',                       isDiag: false, type: 'light',       description: 'RGB status light control'],
    'rssi':                                [attr: 'rssi',                           isDiag: true,  type: 'signal',      description: 'WiFi signal strength indicator'],
    'select_probe':                        [attr: 'selectedProbe',                  isDiag: true,  type: 'selector',    description: 'Active temperature probe selection'],
    'sleep_duration':                      [attr: 'sleepDuration',                  isDiag: true,  type: 'config',      description: 'Device sleep duration between measurements'],
    'temperature_probe':                   [attr: 'temperatureProbe',               isDiag: true,  type: 'temperature', description: 'Primary external temperature probe reading'],
    'temp_probe_offset':                   [attr: 'tempProbeOffset',                isDiag: true,  type: 'offset',      description: 'Temperature probe calibration offset'],
    'uptime':                              [attr: 'uptime',                         isDiag: true,  type: 'status',      description: 'Device uptime since last restart']
]

/**
 * Get entity information from the ALL_ENTITIES map
 * @param objectId ESPHome entity objectId
 * @return entity information map or null if not found
 */
private Map getEntityInfo(String objectId) {
    return ALL_ENTITIES[objectId]
}

/**
 * Get the unit for a specific entity from state.entities, with fallback to ALL_ENTITIES
 * @param objectId ESPHome entity objectId
 * @return unit string with temperature scale applied
 */
private String getEntityUnit(String objectId) {
    // First try to get unit from state.entities
    def entity = state.entities?.values()?.find { it.objectId == objectId }
    String unit = entity?.unitOfMeasurement as String
    
    // If no unit found in state.entities, use fallback from ALL_ENTITIES (if provided)
    if (!unit) {
        def entityInfo = ALL_ENTITIES[objectId]
        unit = entityInfo?.unit as String ?: ''
    }
    
    // Convert temperature units based on hub setting
    if (unit == '°C' && location.temperatureScale == 'F') {
        return '°F'
    }
    
    return unit
}

/**
 * Get entity type for classification
 * @param objectId ESPHome entity objectId
 * @return entity type string
 */
private String getEntityType(String objectId) {
    def entityInfo = getEntityInfo(objectId)
    return entityInfo?.type as String ?: 'unknown'
}

/**
 * Get entity description for logging
 * @param objectId ESPHome entity objectId
 * @return entity description string
 */
private String getEntityDescription(String objectId) {
    def entityInfo = getEntityInfo(objectId)
    return entityInfo?.description as String ?: objectId
}

/**
 * Check if diagnostic reporting is enabled for the given entity
 * @param objectId ESPHome entity objectId
 * @return true if events should be sent, false if diagnostic reporting is disabled
 */
private boolean shouldReportDiagnostic(String objectId) {
    // If diagnosticsReporting is enabled, always report
    if (settings.diagnosticsReporting == true) {
        return true
    }
    
    // If the entity is not in the map, always report
    if (!ALL_ENTITIES.containsKey(objectId)) {
        return true
    }
    
    // Check if the entity is marked as diagnostic
    def entityInfo = ALL_ENTITIES[objectId]
    if (entityInfo?.isDiag != true) {
        return true
    }
    
    // Entity is diagnostic and reporting is disabled
    return false
}

public void initialize() {
    // API library command to open socket to device, it will automatically reconnect if needed
    openSocket()

    if (logEnable) {
        runIn(1800, 'logsOff')
    }
}

public void installed() {
    log.info "${device} driver installed"
}

public void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false) // disable device logging
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

public void refresh() {
    checkDriverVersion()
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void updated() {
    checkDriverVersion()
    log.info "${device} driver configuration updated"
    
    // Delete diagnostic attribute states if diagnostics reporting is disabled
    if (settings.diagnosticsReporting == false) {
        ALL_ENTITIES.each { entityId, entityInfo ->
            def attributeName = entityInfo.attr
            // Skip networkStatus as it's important for connection status
            if (attributeName != 'networkStatus' && entityInfo.isDiag == true) {
                device.deleteCurrentState(attributeName)
            }
        }
    }
    
    // Sync temperature preference with ESPHome select_probe entity
    if (settings.selectedProbe) {
        syncTemperatureSelection()
    }
    
    // Sync board humidity offset preference with ESPHome
    if (settings.boardHumidityOffset != null) {
        syncBoardHumidityOffset()
    }
    
    initialize()
}

private void syncTemperatureSelection() {
    // Find the select_probe entity key
    def selectKey = null
    state.entities?.each { key, entity ->
        if (entity.objectId == 'select_probe') {
            selectKey = key as Long
        }
    }
    
    if (selectKey == null) {
        if (logEnable) { 
            log.warn "Select probe entity not found - available entities: ${state.entities?.values()?.collect { it.objectId }}" 
        }
        return
    }
    
    String selectedProbe = settings.selectedProbe
    if (txtEnable) { log.info "${device} syncing selected probe to ${selectedProbe} (key: ${selectKey})" }
    
    // Send the selection to ESPHome
    espHomeSelectCommand(key: selectKey, state: selectedProbe)
}

private void syncBoardHumidityOffset() {
    // Find the board_humidity_offset entity key
    def offsetKey = null
    state.entities?.each { key, entity ->
        if (entity.objectId == 'board_humidity_offset') {
            offsetKey = key as Long
        }
    }
    
    if (offsetKey == null) {
        if (logEnable) { 
            log.warn "Board humidity offset entity not found - available entities: ${state.entities?.values()?.collect { it.objectId }}" 
        }
        return
    }
    
    Float offset = settings.boardHumidityOffset as Float
    if (txtEnable) { log.info "${device} syncing board humidity offset to ${offset}% (key: ${offsetKey})" }
    
    // Send the offset to ESPHome
    espHomeNumberCommand(key: offsetKey, state: offset)
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// the parse method is invoked by the API library when messages are received
void parse(final Map message) {
    checkDriverVersion()
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            parseKeys(message)
            break

        case 'state':
            parseState(message)
    }
}

void parseKeys(final Map message) {
    if (state.entities == null) { state.entities = [:] }
    
    // Convert key to Long for consistency
    Long key = message.key as Long
    
    // Check if the message contains the required keys
    if (message.objectId && message.key) {
        // Store the entity using string representation of key for consistent map access
        state.entities["$key"] = message
        
        // Store specific entity keys for quick access
        if (message.objectId == 'rgb_light') {
            state.rgbLightKey = key
        }
        
        if (logEnable) { 
            log.debug "entity registered: ${message.objectId} (key=${key}, platform=${message.platform})" 
        }
    } else {
        if (logEnable) { 
            log.warn "Message does not contain required keys: ${message}" 
        }
    }
}


void parseState(final Map message) {
    if (message.key == null) { return }
    
    final Long key = message.key as Long        
    def entity = state.entities["$key"]
    if (logEnable) {
        log.debug "parseState: key=${key}, objectId=${entity?.objectId}, state=${message.state}"
    }
    
    if (entity == null) { 
        log.warn "Entity for key ${key} not found" 
        return 
    }
    
    if (isNullOrEmpty(message.state)) { 
        if (logEnable) { log.warn "Message state is null or empty for key ${key}" }
        return 
    }
    
    def objectId = entity.objectId
    if (isNullOrEmpty(objectId)) { 
        if (logEnable) { log.warn "ObjectId is null or empty for key ${key}" }
        return 
    }

    // Handle special cases that need custom logic
    switch (objectId) {
        case 'rgb_light':
            handleRgbLightState(message)
            break
        case 'select_probe':
            handleSelectProbeState(message)
            break
        case 'food_probe':
        case 'temperature_probe':
            handleTemperatureState(message, entity)
            break
        case 'board_humidity':
            handleHumidityState(message, entity)
            break
        default:
            // Use common handler for most entities
            handleGenericEntityState(message, entity)
            break
    }
}


/**
 * Common handler for most entity state updates
 * @param message state message from ESPHome
 * @param entity entity information from state.entities
 */
private void handleGenericEntityState(Map message, Map entity) {
    if (!message.hasState) {
        return
    }
    
    String objectId = entity.objectId
    def entityInfo = getEntityInfo(objectId)
    if (!entityInfo) {
        if (logEnable) { log.warn "No entity info found for objectId: ${objectId}" }
        return
    }
    
    String attributeName = entityInfo.attr
    String description = entityInfo.description
    String unit = getEntityUnit(objectId)
    def rawValue = message.state
    def processedValue = rawValue
    String formattedValue = ""
    
    // Process value based on entity type
    switch (entityInfo.type) {
        case 'temperature':
            Float tempC = rawValue as Float
            Float temp = convertTemperature(tempC)
            processedValue = temp
            formattedValue = String.format("%.1f", temp)
            break
            
        case 'offset':
            if (unit.contains('°')) {  // Temperature offset
                Float offsetC = rawValue as Float
                Float offset = convertTemperature(offsetC) - convertTemperature(0)
                processedValue = offset
                formattedValue = String.format("%.1f", offset)
            } else {  // Other offsets (humidity, etc.)
                processedValue = rawValue as Float
                formattedValue = String.format("%.1f", processedValue)
                
                // Special case: Sync board humidity offset preference
                if (objectId == 'board_humidity_offset') {
                    Float currentPref = settings.boardHumidityOffset as Float
                    if (currentPref != processedValue) {
                        device.updateSetting('boardHumidityOffset', processedValue)
                        if (txtEnable && shouldReportDiagnostic(objectId)) {
                            log.info "Board humidity offset preference synced from ESPHome to ${processedValue}%"
                        }
                    }
                }
            }
            break
            
        case 'switch':
            boolean switchState = rawValue as Boolean
            processedValue = switchState ? "on" : "off"
            formattedValue = processedValue
            break
            
        case 'sensor':
            if (rawValue instanceof Float) {
                processedValue = rawValue as Float
                formattedValue = String.format("%.1f", processedValue)
            } else {
                processedValue = rawValue as Integer
                formattedValue = processedValue.toString()
            }
            break
            
        case 'config':
            if (unit.contains('°')) {  // Temperature config
                Float tempC = rawValue as Float
                Float temp = convertTemperature(tempC)
                processedValue = temp
                formattedValue = String.format("%.1f", temp)
            } else if (rawValue instanceof Float) {
                processedValue = rawValue as Float
                formattedValue = String.format("%.1f", processedValue)
            } else {
                processedValue = rawValue as Integer
                formattedValue = processedValue.toString()
            }
            break
            
        case 'signal':
            processedValue = rawValue as Integer
            formattedValue = processedValue.toString()
            break
            
        case 'status':
            if (objectId == 'uptime') {
                Long uptime = rawValue as Long
                int days = uptime / 86400
                int hours = (uptime % 86400) / 3600
                int minutes = (uptime % 3600) / 60
                int seconds = uptime % 60
                processedValue = "${days}d ${hours}h ${minutes}m ${seconds}s"
                formattedValue = processedValue
            } else {
                processedValue = rawValue
                formattedValue = processedValue.toString()
            }
            break
            
        default:
            processedValue = rawValue
            formattedValue = processedValue.toString()
            break
    }
    
    // Send event if diagnostic reporting allows it
    if (shouldReportDiagnostic(objectId)) {
        Map eventData = [
            name: attributeName,
            value: (formattedValue ?: processedValue),
            descriptionText: "${description} is ${formattedValue} ${unit}".trim()
        ]
        
        if (unit) {
            eventData.unit = unit
        }
        
        sendEvent(eventData)
    }
    
    // Only log if text logging is enabled AND diagnostic reporting allows it
    if (txtEnable && shouldReportDiagnostic(objectId)) {
        log.info "${description}: ${formattedValue} ${unit}".trim()
    }
}

/**
 * Check if the specified value is null or empty
 * @param value value to check
 * @return true if the value is null or empty, false otherwise
 */
private static boolean isNullOrEmpty(final Object value) {
    return value == null || (value as String).trim().isEmpty()
}


void setRgbLight(String value) {
    def lightKey = state.rgbLightKey
    
    if (lightKey == null) {
        log.warn "RGB light entity not found"
        return
    }
    
    if (value == 'on') {
        if (txtEnable) { log.info "${device} RGB light on" }
        espHomeLightCommand(key: lightKey, state: true)
    } else if (value == 'off') {
        if (txtEnable) { log.info "${device} RGB light off" }
        espHomeLightCommand(key: lightKey, state: false)
    } else {
        log.warn "Unsupported RGBlight value: ${value}"
    }
}



private void handleRgbLightState(Map message) {
    // For light entities, check for 'state' directly since they don't use 'hasState'
    if (message.state != null) {
        def rgbLightState = message.state as Boolean
        sendEvent(name: "rgbLight", value: rgbLightState ? 'on' : 'off', descriptionText: "RGB Light is ${rgbLightState ? 'on' : 'off'}")
        if (txtEnable) { log.info "RGB Light is ${rgbLightState ? 'on' : 'off'}" }
    } else {
        if (logEnable) { log.warn "RGB light message does not contain state: ${message}" }
    }
}

/**
 * Handle temperature probe entities (food_probe and temperature_probe)
 * @param message state message from ESPHome
 * @param entity entity information from state.entities
 */
private void handleTemperatureState(Map message, Map entity) {
    if (!message.hasState) {
        return
    }
    
    String objectId = entity.objectId
    def entityInfo = getEntityInfo(objectId)
    if (!entityInfo) {
        if (logEnable) { log.warn "No entity info found for objectId: ${objectId}" }
        return
    }
    
    Float tempC = message.state as Float
    Float temp = convertTemperature(tempC)
    String unit = getTemperatureUnit()
    String tempStr = String.format("%.1f", temp)
    String attributeName = entityInfo.attr
    String description = entityInfo.description
    
    // Get the previous individual probe temperature value
    def currentProbeState = device.currentState(attributeName)
    String previousProbeValue = currentProbeState?.value
    
    // Send individual probe events only when Debug logging is enabled AND value has changed
    if (settings.logEnable && previousProbeValue != tempStr) {
        sendEvent(name: attributeName, value: tempStr, unit: unit, descriptionText: "${description} is ${tempStr} ${unit}")
        log.info "${description} is ${tempStr} ${unit}"
    }
    
    // Update main temperature attribute based on selected probe
    String selectedProbeType = (objectId == 'food_probe') ? 'Food' : 'Temperature'
    if (settings.selectedProbe == selectedProbeType) {
        // Get the previous main temperature value
        def currentMainTempState = device.currentState("temperature")
        String previousMainValue = currentMainTempState?.value
        
        // Only update main temperature if the value has changed
        if (previousMainValue != tempStr) {
            String mainDescription = "Temperature is ${tempStr} ${unit}"  // Create new variable instead of reassigning
            sendEvent(name: "temperature", value: tempStr, unit: unit, descriptionText: mainDescription)
            if (txtEnable) { 
                log.info "${mainDescription}" 
            }
        }
        else {
            if (logEnable) { 
                log.debug "Main temperature already at ${tempStr} ${unit}, no update needed" 
            }
        }
    }
}

private void handleSelectProbeState(Map message) {
    if (message.hasState) {
        def selectedProbe = message.state as String
        
        if (shouldReportDiagnostic('select_probe')) {
            sendEvent(name: "selectedProbe", value: selectedProbe, descriptionText: "Selected probe is ${selectedProbe}")
        }
        
        // Only log if diagnostic reporting allows it
        if (txtEnable && shouldReportDiagnostic('select_probe')) { 
            log.info "ESPHome selected probe changed to: ${selectedProbe}" 
        }
        
        // Sync the preference setting with ESPHome selection (avoid loops)
        if (settings.selectedProbe != selectedProbe) {
            device.updateSetting('selectedProbe', selectedProbe)
            if (txtEnable && shouldReportDiagnostic('select_probe')) { 
                log.info "Selected probe preference synced from ESPHome to ${selectedProbe}" 
            }
        }
        
        if (txtEnable && shouldReportDiagnostic('select_probe')) { 
            log.info "Selected probe is ${selectedProbe}" 
        }
    } else {
        if (logEnable) { log.warn "Select probe message does not have state: ${message}" }
    }
}

/**
 * Handle humidity sensor entity (board_humidity)
 * @param message state message from ESPHome
 * @param entity entity information from state.entities
 */
private void handleHumidityState(Map message, Map entity) {
    if (!message.hasState) {
        return
    }
    
    String objectId = entity.objectId
    def entityInfo = getEntityInfo(objectId)
    if (!entityInfo) {
        if (logEnable) { log.warn "No entity info found for objectId: ${objectId}" }
        return
    }
    
    Float humidity = message.state as Float
    String humidityStr = String.format("%.1f", humidity)
    String attributeName = entityInfo.attr
    String description = entityInfo.description
    String unit = "%rh"  // Hubitat standard unit for relative humidity
    
    // Get the previous humidity value from current state
    def currentHumidityState = device.currentState(attributeName)
    String previousValue = currentHumidityState?.value
    
    // Only send event and log if the value has changed
    if (previousValue != humidityStr) {
        // Always send humidity event (board_humidity is isDiag: false)
        sendEvent(name: attributeName, value: humidityStr, unit: unit, descriptionText: "${description} is ${humidityStr} ${unit}")
        
        // Always log humidity (it's not a diagnostic attribute)
        if (txtEnable) { 
            log.info "${description} is ${humidityStr} ${unit}" 
        }
    }
}

/**
 * Convert temperature based on hub's temperature scale setting
 * @param tempC temperature in Celsius
 * @return temperature in the hub's preferred scale
 */
private def convertTemperature(Float tempC) {
    if (location.temperatureScale == 'F') {
        return (tempC * 9/5) + 32
    }
    return tempC
}

/**
 * Get temperature unit based on hub's temperature scale setting
 * @return temperature unit string
 */
private String getTemperatureUnit() {
    return location.temperatureScale == 'F' ? '°F' : '°C'
}

private String driverVersionAndTimeStamp() { 
    String debugSuffix = _DEBUG ? ' (debug version!)' : ''
    return "${DRIVER_VERSION} ${DATE_TIME_STAMP} ${debugSuffix} (${getHubVersion()} ${location.hub.firmwareVersionString})"
}

//@CompileStatic
public void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable) { log.info "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" }
        //sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        //logInfo("Updated to version ${driverVersionAndTimeStamp()}")
        state.driverVersion = driverVersionAndTimeStamp()
    }
}


// Put this line at the end of the driver to include the ESPHome API library helper

#include esphome.espHomeApiHelperKKmod