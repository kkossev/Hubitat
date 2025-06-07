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
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
metadata {
    definition(
        name: 'ESPHome SmartDry Sensor',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-SmartDry.groovy') {

        capability 'Battery'
        capability 'Refresh'
        capability 'ShockSensor'
        capability 'Initialize'
        capability 'RelativeHumidityMeasurement'
        capability 'Sensor'
        capability 'TemperatureMeasurement'

        attribute 'shake', 'number'
        attribute 'awake', 'number'

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

        input name: 'shockThreshold',
                type: 'number',
                title: 'Shock Threshold',
                description: 'level',
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

import groovy.transform.Field

@Field static final long AWAKE_KEY       = 2946701995
@Field static final long BATTERY_KEY     = 243219225
@Field static final long HUMIDITY_KEY    = 4092638869
@Field static final long SHAKE_KEY       = 3122363332
@Field static final long TEMPERATURE_KEY = 532231000

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
            // Entity information
            break

        case 'state':
            // Lookup entity in state
            switch (message.key) {
                case TEMPERATURE_KEY:
                    String unit = '°C'
                    Float value = round(message.state as Float, 1)
                    if (message.hasState && device.currentValue('temperature') != value) {
                        descriptionText = "${device} temperature is ${value}${unit}"
                        sendEvent(name: 'temperature', value: value, unit: unit, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    break
                case HUMIDITY_KEY:
                    String unit = '%'
                    Float value = round(message.state as Float, 1)
                    if (message.hasState && device.currentValue('humidity') != value) {
                        descriptionText = "${device} humidity is ${value}${unit}"
                        sendEvent(name: 'humidity', value: value, unit: unit, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    break
                case SHAKE_KEY:
                    Float value = round(message.state as Float, 1)
                    if (message.hasState && device.currentValue('shake') != value) {
                        descriptionText = "${device} shake is ${value}"
                        sendEvent(name: 'shake', value: value, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    if (settings.shockThreshold) {
                        int minValue = settings.shockThreshold
                        String shock = value >= minValue ? 'detected' : 'clear'
                        if (message.hasState && device.currentValue('shock') != shock) {
                            descriptionText = "${device} shock is ${shock}"
                            sendEvent(name: 'shock', value: shock, descriptionText: descriptionText)
                            if (logTextEnable) { log.info descriptionText }
                        }
                    }
                    break
                case BATTERY_KEY:
                    String unit = '%'
                    Float value = round(message.state as Float, 1)
                    if (message.hasState && device.currentValue('battery') != value) {
                        descriptionText = "${device} battery is ${value}${unit}"
                        sendEvent(name: 'battery', value: value, unit: unit, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    break
                case AWAKE_KEY:
                    Float value = round(message.state as Float, 1)
                    if (message.hasState && device.currentValue('awake') != value) {
                        descriptionText = "${device} awake is ${value}"
                        sendEvent(name: 'awake', value: value, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                    break
                default:
                    if (logEnable) { log.debug "Unknown entity: ${message}" }
                    break
            }
            break
    }
}

private static float round(float f, int decimals = 0) {
    return new BigDecimal(f).setScale(decimals, java.math.RoundingMode.HALF_UP).floatValue()
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
