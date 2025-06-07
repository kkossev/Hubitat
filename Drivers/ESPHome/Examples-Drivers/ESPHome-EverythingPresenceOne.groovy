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
 */
metadata {
    definition(
        name: 'ESPHome Everything Presence One',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-EverythingPresenceOnce.groovy') {

        capability 'Configuration'
        capability 'IlluminanceMeasurement'
        capability 'MotionSensor'
        capability 'Sensor'
        capability 'Refresh'
        capability 'RelativeHumidityMeasurement'
        capability 'TemperatureMeasurement'
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

        input name: 'mmwaveSensitivity',
            type: 'number',
            title: '<b>mmWave Sensitivity</b>',
            description: '<i>How much motion is required to trigger the sensor (0 is most, 9 is least sensitive)</i>',
            required: false,
            range: '0..9'

        input name: 'mmwaveDistance',
            type: 'number',
            title: '<b>mmWave Distance</b>',
            description: '<i>Set to the distance of the room you have the EP1 in (0 to 800cm)</i>',
            required: false,
            range: '0..800'

        input name: 'mmwaveOnLatency (Advanced)',
            type: 'number',
            title: '<b>mmWave On Latency</b>',
            description: '<i>How long motion must be detected for before triggering (0 to 60 seconds)</i>',
            required: false,
            range: '0..60'

        input name: 'mmwaveOffLatency (Advanced)',
            type: 'number',
            title: '<b>mmWave Off Latency</b>',
            description: '<i>How long after motion is no longer detected to wait (1 to 60 seconds)</i>',
            required: false,
            range: '1..60'

        input name: 'statusLedEnable',
            type: 'bool',
            title: '<b>Enable Status LED</b>',
            required: false,
            defaultValue: true

        input name: 'mmwaveLedEnable',
            type: 'bool',
            title: '<b>Enable mmWave LED</b>',
            required: false,
            defaultValue: true

        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: \
             '<i>Enables command logging.</i>'

        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: \
             '<i>Turns on debug logging for 30 minutes.</i>'
    }
}

void configure() {
    if (isNullOrEmpty(settings['mmwaveOnLatency'])) {
        final Long key = state.entities['mmwave_on_latency'] as Long
        if (key) {
            final BigDecimal value = settings['mmwaveOnLatency'] as BigDecimal
            if (value < 0 || value > 60) {
                log.warn "mmWave On Latency must be between 0 and 60 seconds"
            } else {
                log.info "Setting mmWave On Latency to ${value}"
                espHomeNumberCommand([key: key, state: value])
            }
        }
    }

    if (isNullOrEmpty(settings['mmwaveOffLatency'])) {
        final Long key = state.entities['mmwave_off_latency'] as Long
        if (key) {
            final BigDecimal value = settings['mmwaveOffLatency'] as BigDecimal
            if (value < 1 || value > 60) {
                log.warn "mmWave Off Latency must be between 1 and 60 seconds"
            } else {
                log.info "Setting mmWave Off Latency to ${value}"
                espHomeNumberCommand([key: key, state: value])
            }
        }
    }

    if (isNullOrEmpty(settings['mmwaveDistance'])) {
        final Long key = state.entities['mmwave_distance'] as Long
        if (key) {
            final BigDecimal value = settings['mmwaveDistance'] as Integer
            if (value < 0 || value > 800) {
                log.warn "mmWave Distance must be between 0 and 800 cm"
            } else {
                log.info "Setting mmWave Distance to ${value}"
                espHomeNumberCommand([key: key, state: value])
            }
        }
    }

    if (isNullOrEmpty(settings['mmwaveSensitivity'])) {
        final Long key = state.entities['mmwave_sensitivity'] as Long
        if (key) {
            final BigDecimal value = settings['mmwaveSensitivity'] as Integer
            if (value < 0 || value > 9) {
                log.warn "mmWave Sensitivity must be between 0 and 9"
            } else {
                log.info "Setting mmWave Sensitivity to ${value}"
                espHomeNumberCommand([key: key, state: value])
            }
        }
    }
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
        case ~/_illuminance$/:
            // Illuminance Sensor
            state.entities['illuminance'] = key
            break
        case ~/_mmwave$/:
            // Millimeter wave radar sensor
            state.entities['mmwave'] = key
            break
        case ~/_occupancy$/:
            // Occupancy Sensor
            state.entities['occupancy'] = key
            break
        case ~/_pir$/:
            // Passive Infrared Sensor
            state.entities['pir'] = key
            break
        case ~/_temperature$/:
            // Temperature Sensor
            state.entities['temperature'] = key
            break
        case ~/_humidity$/:
            // Humidity Sensor
            state.entities['humidity'] = key
            break
        case 'esp32_status_led':
            // ESP32 Status LED
            state.entities['status_led'] = key
            break
        case 'everything-presence-one_safe_mode':
            // Safe mode switch
            state.entities['safe_mode'] = key
            break
        case 'mmwave_distance':
            // Millimeter wave radar sensor distance
            state.entities['mmwave_distance'] = key
            break
        case 'mmwave_led':
            // Millimeter wave radar sensor LED
            state.entities['mmwave_led'] = key
            break
        case 'mmwave_off_latency':
            // Millimeter wave radar sensor off latency
            state.entities['mmwave_off_latency'] = key
            break
        case 'mmwave_on_latency':
            // Millimeter wave radar sensor on latency
            state.entities['mmwave_on_latency'] = key
            break
        case 'mmwave_sensitivity':
            // Millimeter wave radar sensor sensitivity
            state.entities['mmwave_sensitivity'] = key
            break
        case 'mmwave_sensor':
            // Millimeter wave radar sensor switch
            state.entities['mmwave_switch'] = key
            break
        case 'restart_everything-presence-one':
            // Restart everything switch
            state.entities['restart'] = key
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

    switch (key) {
        case state.entities['illuminance']:
            // Illuminance Sensor
            if (message.hasState) {
                updateAttribute('illuminance', message.state as Integer, 'lx')
            }
            break
        case state.entities['mmwave']:
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
        case state.entities['temperature']:
            // Temperature Sensor
            if (message.hasState) {
                final String value = convertTemperatureIfNeeded(message.state as BigDecimal, 'C', 0)
                updateAttribute('temperature', value)
            }
            break
        case state.entities['humidity']:
            // Humidity Sensor
            if (message.hasState) {
                updateAttribute('humidity', message.state as Integer, '%rh')
            }
            break
        case state.entities['esp32_status_led']:
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
        case state.entities['mmwave_led']:
            // Millimeter wave radar sensor LED
            if (message.hasState) {
                log.info "Millimeter wave radar sensor LED: ${message.state}"
                device.updateSetting('mmwaveLedEnable', message.state)
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
