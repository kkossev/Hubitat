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
        name: 'ESPHome Temperature Sensor',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-TemperatureSensor.groovy') {

        capability 'Sensor'
        capability 'Refresh'
        capability 'RelativeHumidityMeasurement'
        capability 'SignalStrength'
        capability 'TemperatureMeasurement'
        capability 'Initialize'

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

        input name: 'temperature', // allows the user to select which sensor entity to use
            type: 'enum',
            title: 'ESPHome Temperature Entity',
            required: state.sensors?.size() > 0,
            options: state.sensors?.collectEntries { k, v -> [ k, v.name ] }

        input name: 'humidity', // allows the user to select which sensor entity to use
            type: 'enum',
            title: 'ESPHome Humidity Entity',
            required: state.sensors?.size() > 0,
            options: state.sensors?.collectEntries { k, v -> [ k, v.name ] }

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
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            if (message.platform == 'sensor') {
                switch (message.deviceClass) {
                    case 'signal_strength':
                        state['signalStrength'] = message.key
                        break
                    case 'humidity':
                        // This will populate the cover dropdown with all the entities
                        // discovered and the entity key which is required when sending commands
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        if (!settings.humidity) {
                            device.updateSetting('humidity', message.key)
                        }
                        break
                    case 'temperature':
                        // This will populate the cover dropdown with all the entities
                        // discovered and the entity key which is required when sending commands
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        if (!settings.temperature) {
                            device.updateSetting('temperature', message.key)
                        }
                        break
                }
                return
            }
            break

        case 'state':
            // Check if the entity key matches the message entity key received to update device state
            if (settings.temperature as Long == message.key && message.hasState) {
                String value = message.state
                if (device.currentValue('temperature') != value) {
                    sendEvent([
                        name: 'temperature',
                        value: value,
                        descriptionText: "Temperature is ${value}"
                    ])
                }
                return
            }

            if (settings.humidity as Long == message.key && message.hasState) {
                String value = message.state
                if (device.currentValue('humidity') != value) {
                    sendEvent([
                        name: 'humidity',
                        value: value,
                        unit: '%',
                        descriptionText: "Humidity is ${value}"
                    ])
                }
                return
            }

            // Signal Strength
            if (state.signalStrength as Long == message.key && message.hasState) {
                Integer rssi = Math.round(message.state as Float)
                String unit = 'dBm'
                if (device.currentValue('rssi') != rssi) {
                    descriptionText = "${device} rssi is ${rssi}"
                    sendEvent(name: 'rssi', value: rssi, unit: unit, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }
            break
    }
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
