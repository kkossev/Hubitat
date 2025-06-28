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
 *  FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 * 
 *  ver. 1.0.0  2022-06-18 kkossev  - first beta version
 * 
 *                         TODO: check the restart() command - state.entities['restart'] 
 *                         TODO : LED control! 
 *                         TODO: add driver version
 */
metadata {
    definition(
        name: 'ESPHome Apollo MSR-2',
        namespace: 'esphome',
        author: 'Krassimir Kossev',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/ESPHome/Drivers/ESPHome/Apollo%20MSR-2.groovy') {

        capability 'Configuration'
        capability 'IlluminanceMeasurement'
        capability 'MotionSensor'
        capability 'Sensor'
        capability 'Refresh'
        //capability 'TemperatureMeasurement'
        capability 'Initialize'
        
        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
        attribute 'radarMovingDistance', 'number'
        attribute 'radarStillDistance', 'number'
        attribute 'mmwave', 'enum', [ 'active', 'not active' ]
        attribute 'pir', 'enum', [ 'active', 'not active' ]
        attribute 'rgbLight', 'enum', ['on', 'off'] 
        attribute 'uptime', 'string'
        attribute 'boardTemperature', 'number'
        attribute 'espTemperature', 'number'
        attribute 'pressure', 'number'
        attribute 'rssi', 'number'
        attribute 'radarTarget', 'string'
        attribute 'radarStillTarget', 'string'
        attribute 'radarZone1Occupanncy', 'enum', [ 'active', 'inactive' ]
        attribute 'radarZone2Occupanncy', 'enum', [ 'active', 'inactive' ]
        attribute 'radarZone3Occupanncy', 'enum', [ 'active', 'inactive' ]  

        command 'restart'
        command 'setRgbLight', [[name:'LED control', type: 'ENUM', constraints: ['off', 'on']]]
        command 'test'
    }

    preferences {
        input name: 'ipAddress', type: 'text', title: '<b>Device IP Address</b>', required: true
        input name: 'password',  type: 'text', title: '<b>Device Password</b>', description: '<i>(if required)</i>', required: false
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: '<i>Turns on debug logging for 30 minutes.</i>'
        input name: 'distanceReporting', type: 'bool', title: '<b>Distance Reporting</b>', defaultValue: false, description: '<i>Enables distance reporting from the radar sensor.<br>Keep it <b>disabled</b> if not really used in automations!</i>'
        input name: 'diagnosticsReporting', type: 'bool', title: '<b>Diagnostics Reporting</b>', defaultValue: false, description: '<i>Enables diagnostics reporting from the device.</i>'
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'Flip to see or hide the advanced options', defaultValue: false
        if (advancedOptions == true) {
            input name: 'logWarnEnable', type: 'bool', title: 'Enable warning logging', required: false, defaultValue: true, description: '<i>Enables API Library warnings and info logging.</i>'
        }
    }
}

// called from updated() method after 5 seconds
void configure() {
    if (logEnable) { log.debug "${device} configure()" }
}

void initialize() {
    // API library command to open socket to device, it will automatically reconnect if needed.  initialize() is called automatically when the hub reboots!
    openSocket()
    if (logEnable) {
        runIn(1800, 'logsOff')  // keep debug logging enabled for 30 minutes
    }
}

void installed() {
    log.info "${device} driver installed"
}

void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false) // disable device logging
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.entities = [:]
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

void restart() {
    log.info "${device} restart"
    espHomeButtonCommand([ key: state.entities['restart'] as Long ])
}

void updated() {
    log.info "${device} driver configuration updated"
    if (settings.distanceReporting == false) {
        device.deleteCurrentState('radarMovingDistance')
        device.deleteCurrentState('radarStillDistance')
    }
    if (settings.diagnosticsReporting == false) {
        device.deleteCurrentState('uptime')
        device.deleteCurrentState('rssi')
        device.deleteCurrentState('boardTemperature')
        device.deleteCurrentState('espTemperature')
        device.deleteCurrentState('pressure')
    }
    initialize()
    runIn(5, 'configure')
}

void uninstalled() {
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
    Long key = message.key.toLong() // Convert to Long if necessary

    // Check if the message contains the required keys
    if (message.objectId && message.key) {
        // Store the entity in the state map using message.key as the key
        def entity = state.entities["$key"]
        state.entities[message.key] = message
        if (logEnable) { log.debug "entity registered: ${message.objectId} (key=${message.key})" }
    } else {
        if (logEnable) { log.warn "Message does not contain required keys: ${message}" }
    }
}

void parseState(final Map message) {
    if (message.key == null) { return }
    final long key = message.key as long
    def entity = state.entities["$key"]
    if (logEnable) {
        log.debug "ESPHome parseState() : key: ${key}, entity: ${entity}"
    }
    if (entity == null) { log.warn "ESPHome parseState() : Entity for key: ${key} is null" ; return }
 
    if (message.hasState != true) { if (logEnable) { log.warn "ESPHome parseState() : Message does not have 'hasState' key: ${message}" } ; return }
    if (isNullOrEmpty(message.state)) { if (logEnable) { log.warn "ESPHome parseState() : Message state is null or empty: ${message}" } ; return }
    def objectId = entity["objectId"]
    if (isNullOrEmpty(objectId)) { if (logEnable) { log.warn "ESPHome parseState() : Message objectId is null or empty: ${message}" } ; return }

    switch (objectId) {
        case 'uptime':
            // Uptime in seconds
            if (!settings.diagnosticsReporting) {
                if (logEnable) { log.warn "Diagnostics reporting is disabled, ignoring uptime." }
                return
            }
            Long uptime = message.state as Long
            int days = uptime / 86400
            int hours = (uptime % 86400) / 3600
            int minutes = (uptime % 3600) / 60
            int seconds = uptime % 60
            String uptimeString = "${days}d ${hours}h ${minutes}m ${seconds}s"
            sendEvent(name: "uptime", value: uptimeString, descriptionText: "Uptime is ${uptimeString}")
            if (txtEnable) { log.info "Uptime is ${uptimeString}" }
            break
        case 'rssi':
            // Signal strength in dBm
            if (!settings.diagnosticsReporting) {
                if (logEnable) { log.warn "Diagnostics reporting is disabled, ignoring RSSI." }
                return
            }
            def rssi = message.state as Integer
            sendEvent(name: "rssi", value: rssi, unit: "dBm", descriptionText: "Signal Strength is ${rssi} dBm")
            if (txtEnable) { log.info "Signal Strength is ${rssi} dBm" }
            break
        case 'rgb_light':
            // RGB light state
            def rgbLightState = message.state as Boolean
            sendEvent(name: "rgbLight", value: rgbLightState ? 'on' : 'off', descriptionText: "RGB Light is ${rgbLightState ? 'on' : 'off'}")
            if (txtEnable) { log.info "RGB Light is ${rgbLightState ? 'on' : 'off'}" }
            break
        case 'dps310_temperature':
            // Board temperature in Celsius
            if (!settings.diagnosticsReporting) {
                if (logEnable) { log.warn "Diagnostics reporting is disabled, ignoring board temperature." }
                return
            }
            def temp = String.format("%.1f", message.state as Float)
            sendEvent(name: "boardTemperature", value: temp, unit: "°C", descriptionText: "Board Temperature is ${temp} °C")
            if (txtEnable) { log.info "Board Temperature is ${temp} °C" }
            break
        case 'esp_temperature':
            // ESP temperature in Celsius
            if (!settings.diagnosticsReporting) {
                if (logEnable) { log.warn "Diagnostics reporting is disabled, ignoring ESP temperature." }
                return
            }
            def temp = String.format("%.1f", message.state as Float)
            sendEvent(name: "espTemperature", value: temp, unit: "°C", descriptionText: "ESP Temperature is ${temp} °C")
            if (txtEnable) { log.info "ESP Temperature is ${temp} °C" }
            break
        case 'dps310_pressure':
            // Pressure in hPa
            def pressure = String.format("%.1f", message.state as Float)
            sendEvent(name: "pressure", value: pressure, unit: "hPa", descriptionText: "Pressure is ${pressure} hPa")
            if (txtEnable) { log.info "Pressure is ${pressure} hPa" }
            break
        case 'ltr390_light':
            // Illuminance in lux
            def illuminance = message.state as Integer
            sendEvent(name: "illuminance", value: illuminance, unit: "lx", descriptionText: "Illuminance is ${illuminance} lx")
            if (txtEnable) { log.info "Illuminance is ${illuminance} lx" }
            break
        case 'radar_detection_distance':
            // Millimeter wave radar sensor distance
            if (!distanceReporting) {
                if (logEnable) { log.warn "Distance reporting is disabled, ignoring radar detection distance." }
                return
            }
            Integer distance = message.state as Integer
            sendEvent(name: "radarMovingDistance", value: distance, unit: "cm", descriptionText: "Millimeter wave radar sensor distance is ${distance} cm")
            if (logEnable) { log.info "Millimeter wave radar sensor distance is ${distance} cm" }
            break
        case 'radar_still_distance':
            if (!distanceReporting) {
                if (logEnable) { log.warn "Distance reporting is disabled, ignoring radar still distance." }
                return
            }
            // Millimeter wave radar sensor still distance
            Integer stillDistance = message.state as Integer
            sendEvent(name: "radarStillDistance", value: stillDistance, unit: "cm", descriptionText: "Millimeter wave radar sensor still distance is ${stillDistance} cm")
            if (logEnable) { log.info "Millimeter wave radar sensor still distance is ${stillDistance} cm" }
            break
        case 'radar_target':
            // Millimeter wave radar sensor target
            String target = message.state as String
            sendEvent(name: "radarTarget", value: target, descriptionText: "Millimeter wave radar sensor target is ${target}")
            if (txtEnable) { log.info "Millimeter wave radar sensor target is ${target}" }
            String motionValue = (target == 'true') ? 'active' : 'inactive'
            sendEvent(name: "motion", value: motionValue, descriptionText: "motion is ${motionValue}")
            break
        case 'radar_still_target':
            // Millimeter wave radar sensor still target
            String stillTarget = message.state as String
            sendEvent(name: "radarStillTarget", value: stillTarget, descriptionText: "Millimeter wave radar sensor still target is ${stillTarget}")
            if (txtEnable) { log.info "Millimeter wave radar sensor still target is ${stillTarget}" }
            break
        case 'radar_zone_1_occupancy':
            // Millimeter wave radar sensor occupancy
            boolean occupancy = message.state as Boolean
            sendEvent(name: "radarZone1Occupanncy", value: occupancy ? 'active' : 'inactive', descriptionText: "Zone 1 Occupanncy is ${occupancy ? 'active' : 'inactive'}")
            if (txtEnable) { log.info "Zone 1 Occupanncy is ${occupancy ? 'active' : 'inactive'}" }
            break
        case 'radar_zone_2_occupancy':
            // Millimeter wave radar sensor occupancy
            boolean occupancy = message.state as Boolean
            sendEvent(name: "radarZone2Occupanncy", value: occupancy ? 'active' : 'inactive', descriptionText: "Zone 2 Occupanncy is ${occupancy ? 'active' : 'inactive'}")
            if (txtEnable) { log.info "Zone 2 Occupanncy is ${occupancy ? 'active' : 'inactive'}" }
            break
        case 'radar_zone_3_occupancy':
            // Millimeter wave radar sensor occupancy
            boolean occupancy = message.state as Boolean
            sendEvent(name: "radarZone3Occupanncy", value: occupancy ? 'active' : 'inactive', descriptionText: "Zone 3 Occupanncy is ${occupancy ? 'active' : 'inactive'}")
            if (txtEnable) { log.info "Zone 3 Occupanncy is ${occupancy ? 'active' : 'inactive'}" }
            break

        default:
            // Handle other objectIds if needed
            if (logEnable) {
                log.debug "ESPHome parseState() : Unhandled objectId: ${objectId} for key: ${key} objectId: ${objectId}, state: ${message.state}"
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

/**
 * Update the specified device attribute with the specified value and log if changed
 * @param attribute name of the attribute
 * @param value value of the attribute
 * @param unit unit of the attribute
 * @param type type of the attribute
 */
private void updateAttribute(final String attribute, final Object value, final String unit = null, final String type = null) {
    final String descriptionText = "${attribute} was set to ${value}${unit ?: ''}"
    if (device.currentValue(attribute) != value && settings.txtEnable) {
        if (txtEnable) { log.info descriptionText }
    }
    sendEvent(name: attribute, value: value, unit: unit, type: type, descriptionText: descriptionText)
}

void setRgbLight(String value) {
    def lightKey = state.light?.keySet()?.first() as Long
    if (value == 'on') {
        if (txtEnable) { log.info "${device} RGB light on" }
        espHomeLightCommand(key: lightKey, state: true)
    } else if (value == 'off') {
        if (txtEnable) { log.info "${device} RGB light off" }
        espHomeLightCommand(key: lightKey, state: false)
    } else {
        if (logEnable) { log.warn "Unsupported RGBlight value: ${value}" }
    }
}

void test() {
    log.info "${device} test command executed"
    def ssstate = state.parseEntities
    //log.trace "Test command: state.entities = ${state.entities}"
    Long key = 68806113 // Example key, replace with actual key if needed
    log.trace "Test command: key = ${key}"
    def entity = state.entities["$key"]
    if (entity) {
        log.info "Entity with key ${key} found: ${entity}"

    } else {
        log.warn "Entity with key ${key} not found"
    }

   

    if (!isNullOrEmpty(state.entities["$key"])) {
        log.info "Entity with key ${key} found: ${state.entities["$key"]}"
    } else {
        log.warn "Entity with key ${key} not found"
    }
}


// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelperKKmod
