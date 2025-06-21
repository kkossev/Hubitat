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
        capability 'Initialize'

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
        attribute "boardTemperature", "number"
        attribute 'espTemperature', 'number'
        attribute "uptime", "number"
        attribute 'rgbLight', 'enum', ['on', 'off'] 

        command 'setRgbLight', [[name:'LED control', type: 'ENUM', constraints: ['off', 'on']]]
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

        input name: 'logWarnEnable',
              type: 'bool',
              title: 'Enable warning logging',
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
                        break
                    case 'temperature':
                        // This will populate the cover dropdown with all the entities
                        // discovered and the entity key which is required when sending commands
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        if (!settings.temperature) {
                            device.updateSetting('temperature', message.key)
                        }
                        break
                    case 'board_temperature':
                        // This is the temperature of the ESPHome device itself, not the sensor
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        break
                    case 'esp_temperature':
                        // This is the temperature of the ESPHome device itself, not the sensor
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        break
                    case 'duration':
                        // This is the uptime of the ESPHome device
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        break
                    case 'battery':
                        // This is the battery level of the ESPHome device
                        state.sensors = (state.sensors ?: [:]) + [ (message.key): message ]
                        break
                    default:
                        log.warn "Unsupported sensor device class: ${message.deviceClass}"
                        if (logWarnEnable) { log.warn "Unsupported sensor device class: ${message.deviceClass}" }
                        return
                }
                return
            }
            if (message.platform == 'light') {
                // This will populate the cover dropdown with all the entities
                // discovered and the entity key which is required when sending commands
                state.light = (state.light ?: [:]) + [ (message.key): message ]
                return
            }
            break

        case 'state':
            //log.trace "ESPHome state message: message.key = ${message.key}, message.hasState = ${message.hasState}, message.state = ${message.state}"
            // check sensors state map for the key
            def stateMapFound = state.sensors?.find { k, v -> k as Long == message.key as Long}
            if (stateMapFound != null && stateMapFound != [:]) {
                // Check if the entity key matches the message entity key received to update device state
                if (settings.temperature as Long == message.key && message.hasState) {
                    String value = String.format("%.1f", message.state as Float)
                    if (device.currentValue('temperature') != value) {
                        String descriptionText = "Temperature is ${value}"
                        if (logTextEnable) { log.info descriptionText }
                        sendEvent([
                            name: 'temperature',
                            value: value,
                            descriptionText: descriptionText,
                            unit: '°C'
                        ])
                    }
                    return
                }

                if (stateMapFound.value.objectId == 'board_humidity' && message.hasState) {
                // (settings.humidity as Long == message.key && message.hasState) {
                    String value = String.format("%.1f", message.state as Float)
                    if (device.currentValue('humidity') != value) {
                        String descriptionText = "Humidity is ${value}"
                        if (logTextEnable) { log.info descriptionText }
                        sendEvent([
                            name: 'humidity',
                            value: value,
                            unit: '%',
                            descriptionText: descriptionText
                        ])
                    }
                    return
                }
                
                if (stateMapFound.value.objectId == 'board_temperature' && message.hasState) {
                    def temp = String.format("%.1f", message.state as Float)
                    sendEvent(name: "boardTemperature", value: temp, unit: "°C", descriptionText: "Board Temperature is ${temp} °C")
                    if (logTextEnable) { log.info "Board Temperature is ${temp} °C" }
                    return  
                }
                
                if (stateMapFound.value.objectId == 'esp_temperature' && message.hasState) {
                    def temp = String.format("%.1f", message.state as Float)
                    sendEvent(name: "espTemperature", value: temp, unit: "°C", descriptionText: "ESP Temperature is ${temp} °C")
                    if (logTextEnable) { log.info "ESP Temperature is ${temp} °C" }
                    return  
                }

                if (stateMapFound.value.objectId == 'uptime' && message.hasState) {
                    Long uptime = message.state as Long
                    String unit = 's'
                    if (device.currentValue('uptime') != uptime) {
                        String descriptionText = "${device} uptime is ${uptime} ${unit}"
                        sendEvent(name: 'uptime', value: uptime, unit: unit, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    return
                }
                // the key was not found in the sensors state map
                if (logWarnEnable) { log.warn "No sensor entity found for key: ${message.key}" }
                return
            }

            // check signalStrength state map for the key
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
            // check rgbLight state map for the key
            if (state.light["$message.key"] != null) {
                if (message.state != null) {
                    String descriptionText = "${device} RGB light is ${message.state ? 'on' : 'off'}"
                    sendEvent(name: 'rgbLight', value: message.state ? 'on' : 'off',
                              descriptionText: descriptionText)
                } else {
                    log.warn "RGB light state message received without state: ${message}"
                }
                return
            }

            if (logWarnEnable) { log.warn "Unsupported state message: ${message}" }
            return
    }
}

void setRgbLight(String value) {
    def lightKey = state.light?.keySet()?.first() as Long
    if (value == 'on') {
        if (logTextEnable) { log.info "${device} RGB light on" }
        espHomeLightCommand(key: lightKey, state: true)
    } else if (value == 'off') {
        if (logTextEnable) { log.info "${device} RGB light off" }
        espHomeLightCommand(key: lightKey, state: false)
    } else {
        log.warn "Unsupported RGBlight value: ${value}"
    }
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelperKKmod
