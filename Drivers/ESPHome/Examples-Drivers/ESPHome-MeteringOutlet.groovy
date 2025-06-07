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
        name: 'ESPHome Metering Outlet',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-MeteringOutlet.groovy') {

        capability 'Actuator'
        capability 'CurrentMeter'
        capability 'EnergyMeter'
        capability 'PowerMeter'
        capability 'Refresh'
        capability 'Switch'
        capability 'Initialize'
        capability 'Outlet'
        capability 'Sensor'
        capability 'SignalStrength'
        capability 'VoltageMeasurement'

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

        input name: 'switch',       // allows the user to select which entity to use
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
            // This will populate the cover dropdown with all the entities
            // discovered and the entity key which is required when sending commands
            if (message.platform == 'switch') {
                state.switches = (state.switches ?: [:]) + [ (message.key as String): message ]
                if (!settings.switch) {
                    device.updateSetting('switch', message.key as String)
                }
                return
            }

            if (message.platform == 'sensor') {
                switch (message.deviceClass) {
                    case 'current':
                        state['amperage'] = message.key
                        break
                    case 'energy':
                        state['energy'] = message.key
                        break
                    case 'power':
                        state['power'] = message.key
                        break
                    case 'signal_strength':
                        state['signalStrength'] = message.key
                        break
                    case 'voltage':
                        state['voltage'] = message.key
                        break
                }
                return
            }
            break

        case 'state':
            String type = message.isDigital ? 'digital' : 'physical'
            // Check if the entity key matches the message entity key received to update device state
            if (state.amperage as Long == message.key && message.hasState) {
                Float amperage = round(message.state as Float, 1)
                String unit = 'A'
                if (device.currentValue('amperage') != amperage) {
                    descriptionText = "${device} amperage is ${amperage}"
                    sendEvent(name: 'amperage', value: amperage, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.energy as Long == message.key && message.hasState) {
                Float energy = round(message.state as Float, 1)
                String unit = 'kWh'
                if (device.currentValue('energy') != energy) {
                    descriptionText = "${device} energy is ${energy}"
                    sendEvent(name: 'energy', value: energy, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.power as Long == message.key && message.hasState) {
                Float power = round(message.state as Float, 1)
                String unit = 'W'
                if (device.currentValue('power') != power) {
                    descriptionText = "${device} power is ${power}"
                    sendEvent(name: 'power', value: power, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.signalStrength as Long == message.key && message.hasState) {
                Integer rssi = round(message.state as Float)
                String unit = 'dBm'
                if (device.currentValue('rssi') != rssi) {
                    descriptionText = "${device} rssi is ${rssi}"
                    sendEvent(name: 'rssi', value: rssi, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (settings.switch as Long == message.key) {
                String value = message.state ? 'on' : 'off'
                if (device.currentValue('switch') != value) {
                    sendEvent(name: 'switch', value: value, type: type, descriptionText: "Switch is ${value} (${type})")
                }
                return
            }

            if (state.voltage as Long == message.key && message.hasState) {
                Integer voltage = round(message.state as Float)
                String unit = 'V'
                if (device.currentValue('voltage') != voltage) {
                    descriptionText = "${device} voltage is ${voltage}"
                    sendEvent(name: 'voltage', value: voltage, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }
            break
    }
}

private static float round(float f, int decimals = 0) {
    return new BigDecimal(f).setScale(decimals, java.math.RoundingMode.HALF_UP).floatValue();
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
