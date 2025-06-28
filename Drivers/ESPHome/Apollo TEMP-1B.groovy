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
 */
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
        //command 'setSelectedProbe', [[name:'Probe Selection', type: 'ENUM', constraints: ['Temperature', 'Food']]]
    }

    preferences {
        input name: 'ipAddress', type: 'text', title: 'Device IP Address', required: true    // required setting for API library
        input name: 'password', type: 'text', title: 'Device Password <i>(if required)</i>', required: false     // optional setting for API library
        input name: 'selectedProbe', type: 'enum', title: 'Temperature Sensor Selection', required: false, options: ['Temperature', 'Food'], defaultValue: 'Temperature', description: 'Select which sensor to use for main temperature attribute'    // allows the user to select which sensor to use for temperature
        input name: 'logEnable', type: 'bool', title: 'Enable Debug Logging', required: false, defaultValue: false    // if enabled the library will log debug details
        input name: 'txtEnable', type: 'bool', title: 'Enable descriptionText logging', required: false, defaultValue: true
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'Flip to see or hide the advanced options', defaultValue: false
        if (advancedOptions == true) {
            input name: 'logWarnEnable', type: 'bool', title: 'Enable warning logging', required: false, defaultValue: true, description: '<i>Enables API Library warnings and info logging.</i>'
        }
    }
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
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void updated() {
    log.info "${device} driver configuration updated"
    
    // Sync temperature preference with ESPHome select_probe entity
    if (settings.selectedProbe) {
        syncTemperatureSelection()
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

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// the parse method is invoked by the API library when messages are received
void parse(final Map message) {
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

    switch (objectId) {
        case 'uptime':
            handleUptimeState(message)
            break
        case 'online':
            handleOnlineState(message)
            break
        case 'rssi':
            handleRssiState(message)
            break
        case 'rgb_light':
            handleRgbLightState(message)
            break
        case 'select_probe':
            handleSelectProbeState(message)
            break
        case 'sleep_duration':
            handleSleepDurationState(message)
            break
        case 'probe_temp_difference_threshold':
            handleProbeTempDifferenceThresholdState(message)
            break
        case 'min_probe_temp':
            handleMinProbeTempState(message)
            break
        case 'max_probe_temp':
            handleMaxProbeTempState(message)
            break
        case 'dps310_temperature':
        case 'board_temperature':
            handleBoardTemperatureState(message)
            break
        case 'esp_temperature':
            handleEspTemperatureState(message)
            break
        case 'food_probe':
            handleFoodProbeState(message)
            break
        case 'board_humidity':
            handleBoardHumidityState(message)
            break
        case 'temperature_probe':
            handleTemperatureProbeState(message)
            break
        case 'temp_probe_offset':
            handleTempProbeOffsetState(message)
            break
        case 'food_probe_offset':
            handleFoodProbeOffsetState(message)
            break
        case 'board_temperature_offset':
            handleBoardTemperatureOffsetState(message)
            break
        case 'board_humidity_offset':
            handleBoardHumidityOffsetState(message)
            break
        case 'battery_voltage':
            handleBatteryVoltageState(message)
            break
        case 'battery_level':
            handleBatteryLevelState(message)
            break
        case 'alarm_outside_temp_range':
            handleAlarmOutsideTempRangeState(message)
            break
        case 'notify_only_outside_temp_difference':
            handleNotifyOnlyOutsideTempDifferenceState(message)
            break
        case 'prevent_sleep':
            handlePreventSleepState(message)
            break
        default:
            if (logEnable) {
                log.warn "Unhandled objectId: ${objectId} (key=${key}, state=${message.state})"
            }
            break
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

private void handleUptimeState(Map message) {
    if (message.hasState) {
        Long uptime = message.state as Long
        int days = uptime / 86400
        int hours = (uptime % 86400) / 3600
        int minutes = (uptime % 3600) / 60
        int seconds = uptime % 60
        String uptimeString = "${days}d ${hours}h ${minutes}m ${seconds}s"
        sendEvent(name: "uptime", value: uptimeString, descriptionText: "Uptime is ${uptimeString}")
        if (txtEnable) { log.info "Uptime is ${uptimeString}" }
    }
}

private void handleOnlineState(Map message) {
    if (message.hasState) {
        boolean online = message.state as Boolean
        String status = online ? 'online' : 'offline'
        if (txtEnable) { log.info "Network status is ${status}" }
    }
}

private void handleRssiState(Map message) {
    if (message.hasState) {
        def rssi = message.state as Integer
        sendEvent(name: "rssi", value: rssi, unit: "dBm", descriptionText: "Signal Strength is ${rssi} dBm")
        if (txtEnable) { log.info "Signal Strength is ${rssi} dBm" }
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

private void handleBoardTemperatureState(Map message) {
    if (message.hasState) {
        Float tempC = message.state as Float
        Float temp = convertTemperature(tempC)
        String unit = getTemperatureUnit()
        String tempStr = String.format("%.1f", temp)
        sendEvent(name: "boardTemperature", value: tempStr, unit: unit, descriptionText: "Board Temperature is ${tempStr} ${unit}")
        
        // Board temperature is always just an attribute, not selectable for main temperature
        
        if (txtEnable) { log.info "Board Temperature is ${tempStr} ${unit}" }
    }
}

private void handleEspTemperatureState(Map message) {
    if (message.hasState) {
        Float tempC = message.state as Float
        Float temp = convertTemperature(tempC)
        String unit = getTemperatureUnit()
        String tempStr = String.format("%.1f", temp)
        sendEvent(name: "espTemperature", value: tempStr, unit: unit, descriptionText: "ESP Temperature is ${tempStr} ${unit}")
        
        // ESP temperature is always just an attribute, not selectable for main temperature
        
        if (txtEnable) { log.info "ESP Temperature is ${tempStr} ${unit}" }
    }
}

private void handleFoodProbeState(Map message) {
    if (message.hasState) {
        Float tempC = message.state as Float
        Float temp = convertTemperature(tempC)
        String unit = getTemperatureUnit()
        String tempStr = String.format("%.1f", temp)
        sendEvent(name: "foodProbe", value: tempStr, unit: unit, descriptionText: "Food Probe Temperature is ${tempStr} ${unit}")
        
        // Update main temperature if this sensor is selected
        if (settings.selectedProbe == 'Food') {
            sendEvent(name: "temperature", value: tempStr, unit: unit, descriptionText: "Temperature is ${tempStr} ${unit}")
        }
        
        if (txtEnable) { log.info "Food Probe Temperature is ${tempStr} ${unit}" }
    }
}

private void handleTemperatureProbeState(Map message) {
    if (message.hasState) {
        Float tempC = message.state as Float
        Float temp = convertTemperature(tempC)
        String unit = getTemperatureUnit()
        String tempStr = String.format("%.1f", temp)
        sendEvent(name: "temperatureProbe", value: tempStr, unit: unit, descriptionText: "Temperature Probe is ${tempStr} ${unit}")
        
        // Update main temperature if this sensor is selected
        if (settings.selectedProbe == 'Temperature') {
            sendEvent(name: "temperature", value: tempStr, unit: unit, descriptionText: "Temperature is ${tempStr} ${unit}")
        }
        
        if (txtEnable) { log.info "Temperature Probe is ${tempStr} ${unit}" }
    }
}

private void handleTempProbeOffsetState(Map message) {
    if (message.hasState) {
        Float offsetC = message.state as Float
        Float offset = convertTemperature(offsetC) - convertTemperature(0) // Convert offset correctly
        String unit = getTemperatureUnit()
        sendEvent(name: "tempProbeOffset", value: offset, unit: unit, descriptionText: "Temperature probe offset is ${offset} ${unit}")
        if (txtEnable) { log.info "Temperature probe offset is ${offset} ${unit}" }
    }
}

private void handleFoodProbeOffsetState(Map message) {
    if (message.hasState) {
        Float offsetC = message.state as Float
        Float offset = convertTemperature(offsetC) - convertTemperature(0) // Convert offset correctly
        String unit = getTemperatureUnit()
        sendEvent(name: "foodProbeOffset", value: offset, unit: unit, descriptionText: "Food probe offset is ${offset} ${unit}")
        if (txtEnable) { log.info "Food probe offset is ${offset} ${unit}" }
    }
}

private void handleBoardTemperatureOffsetState(Map message) {
    if (message.hasState) {
        Float offsetC = message.state as Float
        Float offset = convertTemperature(offsetC) - convertTemperature(0) // Convert offset correctly
        String unit = getTemperatureUnit()
        sendEvent(name: "boardTemperatureOffset", value: offset, unit: unit, descriptionText: "Board temperature offset is ${offset} ${unit}")
        if (txtEnable) { log.info "Board temperature offset is ${offset} ${unit}" }
    }
}

private void handleBoardHumidityOffsetState(Map message) {
    if (message.hasState) {
        def offset = message.state as Float
        sendEvent(name: "boardHumidityOffset", value: offset, unit: "%", descriptionText: "Board humidity offset is ${offset} %")
        if (txtEnable) { log.info "Board humidity offset is ${offset} %" }
    }
}

private void handleProbeTempDifferenceThresholdState(Map message) {
    if (message.hasState) {
        Float thresholdC = message.state as Float
        Float threshold = convertTemperature(thresholdC) - convertTemperature(0) // Convert threshold correctly
        String unit = getTemperatureUnit()
        sendEvent(name: "probeTempDifferenceThreshold", value: threshold, unit: unit, descriptionText: "Probe temperature difference threshold is ${threshold} ${unit}")
        if (txtEnable) { log.info "Probe temperature difference threshold is ${threshold} ${unit}" }
    }
}

private void handleMinProbeTempState(Map message) {
    if (message.hasState) {
        Float minTempC = message.state as Float
        Float minTemp = convertTemperature(minTempC)
        String unit = getTemperatureUnit()
        sendEvent(name: "minProbeTemp", value: minTemp, unit: unit, descriptionText: "Minimum probe temperature is ${minTemp} ${unit}")
        if (txtEnable) { log.info "Minimum probe temperature is ${minProbeTemp} ${unit}" }
    }
}

private void handleMaxProbeTempState(Map message) {
    if (message.hasState) {
        Float maxTempC = message.state as Float
        Float maxTemp = convertTemperature(maxTempC)
        String unit = getTemperatureUnit()
        sendEvent(name: "maxProbeTemp", value: maxTemp, unit: unit, descriptionText: "Maximum probe temperature is ${maxTemp} ${unit}")
        if (txtEnable) { log.info "Maximum probe temperature is ${maxTemp} ${unit}" }
    }
}

private void handleSelectProbeState(Map message) {
    log.trace "handleSelectProbeState: ${message}"
    if (message.hasState) {
        def selectedProbe = message.state as String
        sendEvent(name: "selectedProbe", value: selectedProbe, descriptionText: "Selected probe is ${selectedProbe}")
        
        if (txtEnable) { log.info "ESPHome selected probe changed to: ${selectedProbe}" }
        
        // Sync the preference setting with ESPHome selection (avoid loops)
        if (settings.selectedProbe != selectedProbe) {
            device.updateSetting('selectedProbe', selectedProbe)
            if (txtEnable) { log.info "Selected probe preference synced from ESPHome to ${selectedProbe}" }
        }
        
        if (txtEnable) { log.info "Selected probe is ${selectedProbe}" }
    } else {
        if (logEnable) { log.warn "Select probe message does not have state: ${message}" }
    }
}

private void handleSleepDurationState(Map message) {
    if (message.hasState) {
        def duration = message.state as Integer
        sendEvent(name: "sleepDuration", value: duration, unit: "s", descriptionText: "Sleep duration is ${duration} seconds")
        if (txtEnable) { log.info "Sleep duration is ${duration} seconds" }
    }
}

private void handleBoardHumidityState(Map message) {
    if (message.hasState) {
        def humidity = message.state as Float
        String humidityStr = String.format("%.1f", humidity)
        sendEvent(name: "humidity", value: humidityStr, unit: "%", descriptionText: "Board Humidity is ${humidityStr} %")
        if (txtEnable) { log.info "Board Humidity is ${humidityStr} %" }
    }
}

private void handleBatteryVoltageState(Map message) {
    if (message.hasState) {
        def voltage = message.state as Float
        sendEvent(name: "batteryVoltage", value: voltage, unit: "V", descriptionText: "Battery voltage is ${voltage} V")
        if (txtEnable) { log.info "Battery voltage is ${voltage} V" }
    }
}

private void handleBatteryLevelState(Map message) {
    if (message.hasState) {
        def level = message.state as Integer
        sendEvent(name: "battery", value: level, unit: "%", descriptionText: "Battery level is ${level} %")
        if (txtEnable) { log.info "Battery level is ${level} %" }
    }
}

private void handleAlarmOutsideTempRangeState(Map message) {
    if (message.hasState) {
        def alarmState = message.state as Boolean
        sendEvent(name: "alarmOutsideTempRange", value: alarmState ? "on" : "off", descriptionText: "Outside temperature alarm is ${alarmState ? 'on' : 'off'}")
        if (txtEnable) { log.info "Outside temperature alarm is ${alarmState ? 'on' : 'off'}" }
    }
}

private void handleNotifyOnlyOutsideTempDifferenceState(Map message) {
    if (message.hasState) {
        def notifyState = message.state as Boolean
        sendEvent(name: "notifyOnlyOutsideTempDifference", value: notifyState ? "on" : "off", descriptionText: "Notify only outside temp difference is ${notifyState ? 'on' : 'off'}")
        if (txtEnable) { log.info "Notify only outside temp difference is ${notifyState ? 'on' : 'off'}" }
    }
}

private void handlePreventSleepState(Map message) {
    if (message.hasState) {
        def preventState = message.state as Boolean
        sendEvent(name: "preventSleep", value: preventState ? "on" : "off", descriptionText: "Prevent sleep is ${preventState ? 'on' : 'off'}")
        if (txtEnable) { log.info "Prevent sleep is ${preventState ? 'on' : 'off'}" }
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

/*
void setSelectedProbe(String probe) {
    // Find the select_probe entity key
    def selectKey = null
    state.entities?.each { key, entity ->
        if (entity.objectId == 'select_probe') {
            selectKey = key as Long
        }
    }
    
    if (selectKey == null) {
        log.warn "Select probe entity not found - available entities: ${state.entities?.values()?.collect { it.objectId }}"
        return
    }
    
    if (txtEnable) { log.info "${device} setting selected probe to ${probe}" }
    
    // Send the selection to ESPHome
    espHomeSelectCommand(key: selectKey, state: probe)
    
    // Also update the preference setting to keep them in sync
    device.updateSetting('selectedProbe', probe)
}
*/

// Put this line at the end of the driver to include the ESPHome API library helper

#include esphome.espHomeApiHelperKKmod