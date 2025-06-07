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
        name: 'ESPHome Button Switch',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-ButtonSwitch.groovy') {

        capability 'DoubleTapableButton'
        capability 'Sensor'
        capability 'PushableButton'
        capability 'Refresh'
        capability 'SignalStrength'
        capability 'Switch'
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

        input name: 'buttonNumber', // allows the user to select which sensor entity to use
            type: 'enum',
            title: 'ESPHome Button Number',
            required: state.sensors?.size() > 0,
            options: state.sensors?.collectEntries { k, v -> [ k, v.name ] }

        input name: 'switch', // allows the user to select which switch entity to use
            type: 'enum',
            title: 'ESPHome Switch Entity',
            required: state.switches?.size() > 0,
            options: state.switches?.collectEntries { k, v -> [ k, v.name ] }

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

public void updated() {
    log.info "${device} driver configuration updated"
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// driver commands
public void on() {
    if (device.currentValue('switch') != 'on') {
        if (logTextEnable) { log.info "${device} on" }
        espHomeSwitchCommand(key: settings.switch as Long, state: true)
    }
}

public void off() {
    if (device.currentValue('switch') != 'off') {
        if (logTextEnable) { log.info "${device} off" }
        espHomeSwitchCommand(key: settings.switch as Long, state: false)
    }
}

public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            if (message.platform == 'switch') {
                state.switches = (state.switches ?: [:]) + [ (message.key as String): message ]
                if (!settings.switch) {
                    device.updateSetting('switch', message.key as String)
                }
                return
            }

            if (message.platform == 'sensor') {
                switch (message.deviceClass) {
                    case 'signal_strength':
                        state['signalStrength'] = message.key
                        break
                    default:
                        state.sensors = (state.sensors ?: [:]) + [ (message.key as String): message ]
                }
                return
            }
            break

        case 'state':
            // Check if the entity key matches the message entity key received to update device state
            if (settings.switch as Long == message.key) {
                String value = message.state ? 'on' : 'off'
                String type = message.isDigital ? 'digital' : 'physical'
                if (device.currentValue('switch') != value) {
                    sendEvent(name: 'switch', value: value, type: type, descriptionText: "Switch is ${value} (${type})")
                }
                return
            }

            if (settings.buttonNumber as Long == message.key && message.hasState) {
                String type = message.isDigital ? 'digital' : 'physical'
                String eventName
                int button
                switch (message.state) {
                    case 11:
                        button = 1
                        eventName = 'pushed'
                        break
                    case 12:
                        button = 1
                        eventName = 'doubleTapped'
                        break
                    case 21:
                        button = 2
                        eventName = 'pushed'
                        break
                    case 22:
                        button = 2
                        eventName = 'doubleTapped'
                        break
                    default:
                        log.warn "Unknown button value ${message.state}"
                        return
                }
                descriptionText = "Button ${button} ${eventName} (${type})"
                sendEvent(name: eventName, value: button, type: type, descriptionText: descriptionText, isStateChange: true)
                if (logTextEnable) { log.info descriptionText }
                return
            }

            if (state.signalStrength as Long == message.key && message.hasState) {
                Integer rssi = Math.round(message.state as Float)
                String unit = 'dBm'
                if (device.currentValue('rssi') != rssi) {
                    descriptionText = "${device} rssi is ${rssi}"
                    sendEvent(name: 'rssi', value: rssi, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }
            break
    }
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
