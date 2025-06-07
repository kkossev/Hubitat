/**
 *  MIT License
 *  Copyright 2023 Jonathan Bradshaw (jb@nrgup.net)
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
        name: 'ESPHome Upsy Desky',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-UpsyDesky.groovy') {

        capability 'Actuator'
        capability 'Sensor'
        capability 'Refresh'
        capability 'Initialize'

        command 'ledOn'
        command 'ledOff'
        command 'reDetectDecoder'
        command 'restart'
        command 'setPosition', [ [ name: 'Height*', type: 'NUMBER' ] ]
        command 'preset', [ [ name: 'Number*', type: 'NUMBER', description: 'Preset Number (1-4)' ] ]
        command 'setPreset', [ [ name: 'Number*', type: 'NUMBER', description: 'Preset Number (1-4)' ] ]

        attribute 'position', 'number'
        attribute 'led', 'enum', [ 'on', 'off' ]

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
    }

    preferences {
        input name: 'ipAddress',    // required setting for API library
                type: 'text',
                title: 'Device IP Address',
                required: true

        input name: 'password',     // optional setting for API library
                type: 'text',
                title: 'Device Password <i>(if required)</i>',
                required: false

        input name: 'logEnable',    // if enabled the library will log debug details
                type: 'bool',
                title: 'Enable Debug Logging',
                required: false,
                defaultValue: false

        input name: 'logTextEnable',
              type: 'bool',
              title: 'Enable descriptionText logging',
              required: false,
              defaultValue: true
    }
}

import java.math.RoundingMode

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

void updated() {
    log.info "${device} driver configuration updated"
    initialize()
}

void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// driver commands
void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

void reDetectDecoder() {
    log.info "${device} redetectEncoder"
    final Long key = state.entities['re-detect_decoder'] as Long
    if (!key) {
        log.warn "${device} redetectEncoder not found"
        return
    }
    espHomeButtonCommand([key: key])
}

void restart() {
    log.info "${device} restart"
    final Long key = state.entities['restart'] as Long
    if (!key) {
        log.warn "${device} restart not found"
        return
    }
    espHomeButtonCommand([key: key])
}

// the parse method is invoked by the API library when messages are received
void parse(final Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            log.info "device info: ${message}"
            break

        case 'entity':
            // Persist Entity objectId mapping to keys
            if (state.entities == null) {
                state.entities = [:]
            }
            state.entities[message.objectId as String] = message.key
            break

        case 'state':
            if (message.key) {
                parseState(message)
            }
            break
    }
}

// parse state messages
void parseState(final Map message) {
    final String objectId = state.entities.find { final Map.Entry entity ->
        entity.value as Long == message.key as Long
    }?.key
    if (!objectId) {
        log.warn "ESPHome: Unknown entity key: ${message}"
        return
    }
    switch (objectId) {
        case 'desk_height':
            if (message.hasState) {
                final BigDecimal value = (message.state as BigDecimal).setScale(1, RoundingMode.HALF_UP)
                runInMillis(750, "updateAttributeDebounce", [ data: [ attribute: 'position', value: value ] ])
            }
            break
        case 'status_led':
            updateAttribute('led', message.state ? 'on' : 'off')
            break
    }
}

void preset(final BigDecimal number) {
    log.info "${device} preset: ${number}"
    final Long key = state.entities["preset_${number}"] as Long
    if (!key) {
        log.warn "${device} invalid preset number: ${number}"
        return
    }
    espHomeButtonCommand([key: key])
}

void setPreset(final BigDecimal number) {
    log.info "${device} hold: ${number}"
    final Long key = state.entities["set_preset_${number}"] as Long
    if (!key) {
        log.warn "${device} invalid preset number: ${number}"
        return
    }
    espHomeButtonCommand([key: key])
}

void setPosition(final BigDecimal position) {
    log.info "${device} setPosition: ${position}"
    final Long key = state.entities['target_desk_height'] as Long
    if (!key) {
        log.warn "${device} target_desk_height key not found"
        return
    }
    espHomeNumberCommand([key: key, state: position])
}

void ledOn() {
    log.info "${device} LED on"
    final Long key = state.entities['status_led'] as Long
    if (!key) {
        log.warn "${device} status_led key not found"
        return
    }
    espHomeLightCommand([key: key, state: true])
}

void ledOff() {
    log.info "${device} LED off"
    final Long key = state.entities['status_led'] as Long
    if (!key) {
        log.warn "${device} status_led key not found"
        return
    }
    espHomeLightCommand([key: key, state: false])
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

/**
 * Used to update an attribute with a delay from the runIn method
 * @param data map of data to pass to the updateAttribute method
 */
private void updateAttributeDebounce(final Map data) {
    updateAttribute(data.attribute as String, data.value, data.unit as String, data.type as String)
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
