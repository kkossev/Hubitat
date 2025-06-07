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
        name: 'ESPHome Fan Control',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-FanControl.groovy') {

        capability 'Actuator'
        capability 'FanControl'
        capability 'Refresh'
        capability 'SignalStrength'
        capability 'Switch'
        capability 'Initialize'

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]

        attribute 'oscillating', 'enum', [ 'true', 'false' ]
        attribute 'direction', 'enum', [ 'forward', 'reverse' ]
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

        input name: 'fan',        // allows the user to select which fan entity to use
                type: 'enum',
                title: 'ESPHome Fan Entity',
                required: state.fans?.size() > 0,
                options: state.fans?.collectEntries { k, v -> [ k, v.name ] }

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
public void cycleSpeed() {
    switch (device.currentValue('speed')) {
        case 'low':
            setSpeed('medium-low')
            break
        case 'medium-low':
            setSpeed('medium')
            break
        case 'medium':
            setSpeed('medium-high')
            break
        case 'medium-high':
            setSpeed('high')
            break
        case 'high':
            setSpeed('low')
            break
    }
}

public void on() {
    setSpeed('on')
}

public void off() {
    setSpeed('off')
}

public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void setSpeed(String speed) {
    switch (speed) {
        case 'on':
        case 'auto':
            if (device.currentValue('speed') != 'on') {
                if (logTextEnable) { log.info "${device} on" }
                espHomeFanCommand(key: settings.fan as Long, state: true)
            }
            break
        case 'off':
            if (device.currentValue('speed') != 'off') {
                if (logTextEnable) { log.info "${device} off" }
                espHomeFanCommand(key: settings.fan as Long, state: false)
            }
            break
        case 'low':
        case 'medium-low':
        case 'medium':
        case 'medium-high':
        case 'high':
            if (device.currentValue('speed') != speed) {
                Integer index = ['low', 'medium-low', 'medium', 'medium-high', 'high'].indexOf(speed)
                Integer max = state.fans[settings.fan as String]?.supportedSpeedLevels ?: 100
                int speedLevel = remap(index, 0, 4, 1, max) as int
                if (logTextEnable) { log.info "${device} speed is ${speed}" }
                espHomeFanCommand(key: settings.fan as Long, state: true, speedLevel: speedLevel)
            }
            break
    }
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            // This will populate the fan dropdown with all the entities
            // discovered and the entity key which is required when sending commands
            if (message.platform == 'fan') {
                state.fans = (state.fans ?: [:]) + [ (message.key as String): message ]
                if (!settings.fan) {
                    device.updateSetting('fan', message.key as String)
                }
                return
            }

            if (message.platform == 'sensor') {
                switch (message.deviceClass) {
                    case 'signal_strength':
                        state['signalStrength'] = message.key
                        break
                }
                return
            }
            break

        case 'state':
            // Check if the entity key matches the message entity key received to update device state
            if (settings.fan as Long == message.key) {
                String type = message.isDigital ? 'digital' : 'physical'
                if (message.oscillating != null) {
                    String oscillating = message.oscillating ? 'true' : 'false'
                    if (device.currentValue('oscillating') != oscillating) {
                        descriptionText = "${device} oscillating is ${oscillating}"
                        sendEvent(name: 'oscillating', value: oscillating, type: type, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                }

                if (message.direction != null) {
                    String direction = message.direction == FAN_DIRECTION_FORWARD ? 'forward' : 'reverse'
                    if (device.currentValue('direction') != direction) {
                        descriptionText = "${device} direction is ${direction}"
                        sendEvent(name: 'direction', value: direction, type: type, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
                }

                if (message.speedLevel != null) {
                    String speed
                    if (message.state) {
                        Integer max = state.fans[settings.fan as String]?.supportedSpeedLevels ?: 100
                        Integer speedLevel = message.speedLevel as Integer
                        int value = remap(speedLevel, 1, max, 0, 4) as int
                        speed = ['low', 'medium-low', 'medium', 'medium-high', 'high'].get(value)
                    } else {
                        speed = 'off'
                    }
                    if (device.currentValue('speed') != speed) {
                        descriptionText = "${device} speed is ${speed}"
                        sendEvent(name: 'speed', value: speed, type: type, descriptionText: descriptionText)
                        if (logTextEnable) { log.info descriptionText }
                    }
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

private static BigDecimal remap(BigDecimal oldValue,
                                BigDecimal oldMin, BigDecimal oldMax,
                                BigDecimal newMin, BigDecimal newMax) {
    BigDecimal value = oldValue
    if (value < oldMin) { value = oldMin }
    if (value > oldMax) { value = oldMax }
    BigDecimal newValue = ((value - oldMin) / (oldMax - oldMin)) * (newMax - newMin) + newMin
    return newValue.setScale(1, BigDecimal.ROUND_HALF_UP)
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
