/**
 *  MIT License
 *  Copyright 2024 Joshua Glemza
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
 */
metadata {
    definition(
        name: 'Athom Human Presence Sensor',
        namespace: 'esphome',
        author: 'Joshua Glemza',
        singleThreaded: true,
        importUrl: 'https://github.com/bradsjm/hubitat-public/raw/main/ESPHome/ESPHome-AthomHumanPresenceSensor.groovy') {

        capability 'Configuration'
        capability 'IlluminanceMeasurement'
        capability 'MotionSensor'
        capability 'Sensor'
        capability 'Refresh'
        capability 'Initialize'
        
        attribute 'distance', 'number'
        attribute 'mmwave', 'enum', [ 'active', 'not active' ]
        attribute 'pir', 'enum', [ 'active', 'not active' ]

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]

        command 'restart'
    }

    preferences {
        input name: 'ipAddress',    // required setting for API library
            type: 'text',
            title: '<b>Device IP Address</b>',
            required: true

        input name: 'password',     // optional setting for API library
            type: 'text',
            title: '<b>Device Password</b>',
            description: '<i>(if required)</i>',
            required: false

        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: \
             '<i>Enables command logging.</i>'

        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: \
             '<i>Turns on debug logging for 30 minutes.</i>'
    }
}

void configure() {

}

void initialize() {
    // API library command to open socket to device, it will automatically reconnect if needed
    openSocket()

    if (logEnable) {
        runIn(1800, 'logsOff')
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
    if (logEnable) { log.debug "ESPHome entity: ${message}" }
    if (state.entities == null) { state.entities = [:] }
    final long key = message.key as long

    switch (message.objectId) {
        case 'light_sensor':
            // Illuminance Sensor
            state.entities['illuminance'] = key
            break
        case 'occupancy':
            // Occupancy Sensor
            state.entities['occupancy'] = key
            break
        case 'pir_sensor':
            // Passive Infrared Sensor
            state.entities['pir'] = key
            break
        case 'status_led':
            // ESP32 Status LED
            state.entities['status_led'] = key
            break
        case 'farthest_detection':
            // Millimeter wave radar sensor distance
            state.entities['mmwave_distance'] = key
            break
        case 'fading_time':
            // Millimeter wave radar sensor off latency
            state.entities['mmwave_off_latency'] = key
            break
        case 'detection_delay':
            // Millimeter wave radar sensor on latency
            state.entities['mmwave_on_latency'] = key
            break
        case 'trigger_sensitivity':
            // Millimeter wave radar sensor sensitivity
            state.entities['mmwave_sensitivity'] = key
            break
        case 'mmwave_sensor':
            // Millimeter wave radar sensor switch
            state.entities['mmwave_switch'] = key
            break
        case 'uart_presence_output':
        case 'uart_target_output':
            // ignore
            break
        default:
            log.warn "ESPHome entity not supported: ${message}"
            break
    }
}

void parseState(final Map message) {
    if (logEnable) { log.debug "ESPHome state: ${message}" }
    if (message.key == null) { return }
    final long key = message.key as long

    log.trace "ESPHome state key: ${key} (${message.objectId})"
    ///////
    return
    ////////

    switch (key) {
        case state.entities['illuminance']:
            // Illuminance Sensor
            if (message.hasState) {
                updateAttribute('illuminance', message.state as Integer, 'lx')
            }
            break
        case state.entities['mmwave_switch']:
            // Millimeter wave radar sensor
            if (message.hasState) {
                updateAttribute('mmwave', message.state ? 'active' : 'inactive')
            }
            break
        case state.entities['occupancy']:
            // Combined Millimeter wave radar and PIR
            if (message.hasState) {
                updateAttribute('motion', message.state ? 'active' : 'inactive')
            }
            break
        case state.entities['pir']:
            // PIR sensor
            if (message.hasState) {
                updateAttribute('pir', message.state ? 'active' : 'inactive')
            }
            break
        case state.entities['status_led']:
            // ESP32 Status LED
            if (message.hasState) {
                log.info "ESP32 Status LED: ${message.state}"
                device.updateSetting('statusLedEnable', message.state)
            }
            break
        case state.entities['mmwave_distance']:
            // Millimeter wave radar sensor distance
            if (message.hasState) {
                log.info "Millimeter wave radar sensor distance: ${message.state}"
                device.updateSetting('mmwaveDistance', message.state)
            }
            break
        case state.entities['mmwave_off_latency']:
            // Millimeter wave radar sensor off latency
            if (message.hasState) {
                log.info "Millimeter wave radar sensor off latency: ${message.state}"
                device.updateSetting('mmwaveOffLatency', message.state)
            }
            break
        case state.entities['mmwave_on_latency']:
            // Millimeter wave radar sensor on latency
            if (message.hasState) {
                log.info "Millimeter wave radar sensor on latency: ${message.state}"
                device.updateSetting('mmwaveOnLatency', message.state)
            }
            break
        case state.entities['mmwave_sensitivity']:
            // Millimeter wave radar sensor sensitivity
            if (message.hasState) {
                log.info "Millimeter wave radar sensor sensitivity: ${message.state}"
                device.updateSetting('mmwaveSensitivity', message.state)
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
        log.info descriptionText
    }
    sendEvent(name: attribute, value: value, unit: unit, type: type, descriptionText: descriptionText)
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
