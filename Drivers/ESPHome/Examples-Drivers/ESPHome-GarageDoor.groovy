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
        name: 'ESPHome Garage Door',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-GarageDoor.groovy') {

        capability 'Actuator'
        capability 'GarageDoorControl'
        capability 'Refresh'
        capability 'SignalStrength'
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

        input name: 'cover',        // allows the user to select which cover entity to use
            type: 'enum',
            title: 'ESPHome Cover Entity',
            required: state.covers?.size() > 0,
            options: state.covers?.collectEntries { k, v -> [ k, v.name ] }

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
public void open() {
    String doorState = device.currentValue('door')
    if (doorState != 'closed') {
        log.info "${device} ignoring open request (door is ${doorState})"
        return
    }
    // API library cover command, entity key for the cover is required
    if (logTextEnable) { log.info "${device} open" }
    espHomeCoverCommand(key: settings.cover as Long, position: 1.0)
}

public void close() {
    String doorState = device.currentValue('door')
    if (doorState != 'open') {
        log.info "${device} ignoring close request (door is ${doorState})"
        return
    }
    // API library cover command, entity key for the cover is required
    if (logTextEnable) { log.info "${device} close" }
    espHomeCoverCommand(key: settings.cover as Long, position: 0.0)
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
            // This will populate the cover dropdown with all the entities
            // discovered and the entity key which is required when sending commands
            if (message.platform == 'cover') {
                state.covers = (state.covers ?: [:]) + [ (message.key as String): message ]
                if (!settings.cover) {
                    device.updateSetting('cover', message.key as String)
                }
                return
            }

            if (message.platform == 'sensor' && message.deviceClass == 'signal_strength') {
                state['signalStrength'] = message.key
                return
            }
            break

        case 'state':
            String type = message.isDigital ? 'digital' : 'physical'
            // Check if the entity key matches the message entity key received to update device state
            if (settings.cover as Long == message.key) {
                String value
                switch (message.currentOperation) {
                    case COVER_OPERATION_IDLE:
                        value = message.position > 0 ? 'open' : 'closed'
                        break
                    case COVER_OPERATION_IS_OPENING:
                        value = 'opening'
                        break
                    case COVER_OPERATION_IS_CLOSING:
                        value = 'closing'
                        break
                }
                if (device.currentValue('door') != value) {
                    descriptionText = "${device} door is ${value}"
                    sendEvent(name: 'door', value: value, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
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
