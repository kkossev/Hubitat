/**
 *  MIT License
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the 'Software'), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
metadata {
    definition(
        name: 'ESPHome RGBCT Light',
        namespace: 'esphome',
        author: 'Jonathan Bradshaw',
        singleThreaded: true,
        importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/ESPHome/ESPHome-Light-RGBCT.groovy') {

        capability 'Actuator'
        capability 'Bulb'
        capability 'ChangeLevel'
        capability 'ColorControl'
        capability 'ColorMode'
        capability 'ColorTemperature'
        capability 'Light'
        capability 'LightEffects'
        capability 'Refresh'
        capability 'SignalStrength'
        capability 'Switch'
        capability 'SwitchLevel'
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

        if (state.lights?.size()) {
            input name: 'light',       // allows the user to select which entity to use
                type: 'enum',
                title: 'ESPHome Light Entity',
                required: true,
                options: state.lights?.collectEntries { k, v -> [ k, v.name ] }
        }

        input name: 'changeLevelStep',
            type: 'decimal',
            title: 'Change level step size',
            required: false,
            range: 1..50,
            defaultValue: 5

        input name: 'changeLevelEvery',
            type: 'number',
            title: 'Change Level every x milliseconds',
            required: false,
            range: 100..60000,
            defaultValue: 100

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

import groovy.json.JsonOutput
import groovy.transform.Field
import hubitat.helper.ColorUtils

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

// Driver Commands
void on() {
    if (device.currentValue('switch') != 'on') {
        if (logTextEnable) { log.info "${device} on" }
        espHomeLightCommand(key: settings.light as Long, state: true)
    }
}

void off() {
    if (device.currentValue('switch') != 'off') {
        if (logTextEnable) { log.info "${device} off" }
        espHomeLightCommand(key: settings.light as Long, state: false)
    }
}

void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

void setColor(Map colorMap) {
    setColorInternal(colorMap)
}

void setColorTemperature(BigDecimal colorTemperature, BigDecimal level = null, BigDecimal duration = null) {
    setColorTemperatureInternal(colorTemperature, level, duration)
}

void setHue(BigDecimal hue) {
    BigDecimal saturation = device.currentValue('saturation')
    BigDecimal level = device.currentValue('level')
    if (hue != null && saturation != null && level != null) {
        setColor([ hue: hue, saturation: saturation, level: level ])
    }
}

void setLevel(BigDecimal level, BigDecimal duration = null) {
    setLevelInternal(level, duration)
}

void setSaturation(BigDecimal saturation) {
    BigDecimal hue = device.currentValue('hue')
    BigDecimal level = device.currentValue('level')
    if (hue != null && saturation != null && level != null) {
        setColor([ hue: hue, saturation: saturation, level: level ])
    }
}

void setEffect(BigDecimal number) {
    if (state.lights && settings.light) {
        List<String> effects = getEntity().effects
        if (effects) {
            int index = number
            if (index < 1) { index = effects.size() }
            if (index > effects.size()) { index = 1 }
            String effectName = effects[index - 1]
            if (device.currentValue('effectName') != effectName) {
                if (logTextEnable) { log.info "${device} set effect ${effectName}" }
                espHomeLightCommand(key: settings.light as Long, state: true, effect: effectName)
            }
        }
    }
}

void setNextEffect() {
    if (state.lights && settings.light) {
        String current = device.currentValue('effectName')
        int index = getEntity().effects.indexOf(current) + 1
        setEffect(index + 1)
    }
}

void setPreviousEffect() {
    if (state.lights && settings.light) {
        String current = device.currentValue('effectName')
        int index = getEntity().effects.indexOf(current) + 1
        setEffect(index - 1)
    }
}

void startLevelChange(String direction) {
    if (settings.changeLevelStep && settings.changeLevelEvery) {
        state.levelChange = true
        doLevelChange(direction == 'up' ? 1 : -1)
    }
}

void stopLevelChange() {
    state.remove('levelChange')
}

// the parse method is invoked by the ESPHome API library when messages are received
void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            // This will populate the cover dropdown with all the entities
            // discovered and the entity key which is required when sending commands
            if (message.platform == 'light') {
                state.lights = (state.lights ?: [:]) + [ (message.key as String): message ]
                if (!settings.light) {
                    device.updateSetting('light', message.key as String)
                }
            }

            // Find a signal strength sensor and put the key in our state
            if (message.platform == 'sensor' && message.deviceClass == 'signal_strength') {
                state['signalStrength'] = message.key
            }

            // Populate the light effects json
            if (message.platform == 'light' && (!settings.light || settings.light as Long == message.key)) {
                String effects = JsonOutput.toJson(message.effects ?: [])
                if (device.currentValue('lightEffects') != effects) {
                    sendEvent(name: 'lightEffects', value: effects)
                }
            }
            break

        case 'complete':
            // Called upon completion of all entities loaded, state updates will follow
            break

        case 'state':
            String type = message.isDigital ? 'digital' : 'physical'

            // Check if the entity key matches the message entity key received to update device state
            if (settings.light && settings.light as Long == message.key) {
                boolean isRgb = (message.colorMode ?: 0) & COLOR_CAP_RGB
                String descriptionText

                String state = message.state ? 'on' : 'off'
                if (device.currentValue('switch') != state) {
                    descriptionText = "${device} was turned ${state}"
                    sendEvent(name: 'switch', value: state, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }

                int level = Math.round(message.masterBrightness * 100f)
                if (device.currentValue('level') != level) {
                    descriptionText = "${device} level was set to ${level}%"
                    sendEvent(name: 'level', value: level, unit: '%', type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }

                def (int h, int s, int b) = ColorUtils.rgbToHSV([message.red * 255f, message.green * 255f, message.blue * 255f])
                String colorName = colorNameMap.find { k, v -> h * 3.6 <= k }.value
                if (isRgb && device.currentValue('colorName') != colorName) {
                    descriptionText = "${device} color name was set to ${colorName}"
                    sendEvent name: 'colorName', value: colorName, type: type, descriptionText: descriptionText
                    if (logTextEnable) { log.info descriptionText }

                    String color = "{hue=${h}, saturation=${s}, level=${b}}"
                    descriptionText = "${device} color set to ${color}"
                    sendEvent name: 'color', value: color, type: type, descriptionText: descriptionText
                    if (logTextEnable) { log.info descriptionText }
                }

                if (device.currentValue('hue') != h) {
                    descriptionText = "${device} hue was set to ${h}"
                    sendEvent name: 'hue', value: h, type: type, descriptionText: descriptionText
                    if (logTextEnable) { log.info descriptionText }
                }

                if (device.currentValue('saturation') != s) {
                    descriptionText = "${device} saturation was set to ${s}"
                    sendEvent name: 'saturation', value: s, type: type, descriptionText: descriptionText, unit: '%'
                    if (logTextEnable) { log.info descriptionText }
                }

                int colorTemperature = Math.round(1000000f / message.colorTemperature)
                if (device.currentValue('colorTemperature') != colorTemperature) {
                    descriptionText = "${device} color temperature was set to ${colorTemperature}"
                    sendEvent(name: 'colorTemperature', value: colorTemperature, unit: '°K', type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }

                colorName = colorTempNameMap.find { k, v -> colorTemperature < k }.value
                if (!isRgb && device.currentValue('colorName') != colorName) {
                    descriptionText = "${device} color name is ${colorName}"
                    sendEvent(name: 'colorName', value: colorName, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }

                String effectName = message.effect
                if (device.currentValue('effectName') != effectName) {
                    descriptionText = "${device} effect name is ${effectName}"
                    sendEvent(name: 'effectName', value: effectName, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }

                String colorMode = isRgb ? 'RGB' : 'CT'
                if (message.effect && message.effect != 'None') { colorMode = 'EFFECTS' }
                if (device.currentValue('colorMode') != colorMode) {
                    descriptionText = "${device} color mode is ${colorMode}"
                    sendEvent(name: 'colorMode', value: colorMode, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.signalStrength && state.signalStrength as Long == message.key && message.hasState) {
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

// Private helper methods
private void doLevelChange(int direction) {
    int level = device.currentValue('level') ?: 1
    int step = settings.changeLevelStep ?: 100
    int newLevel = level + (direction * step)
    if (newLevel <= 1) {
        setLevel(1)
        stopLevelChange()
        return
    } else if (newLevel >= 100) {
        setLevel(100)
        stopLevelChange()
        return
    }

    if (state.levelChange && newLevel != level) {
        setLevel(newLevel)
        runInMillis(settings.changeLevelEvery as int, 'doLevelChange', [data: direction])
    } else {
        log.info "${device} level change cancelled"
        stopLevelChange()
    }
}

private Integer getColorMode(int capability) {
    Map entity = getEntity()
    if (entity?.supportedColorModes) {
        Integer result = entity.supportedColorModes.keySet().find {
            colorMode -> (colorMode as int) & capability
        } as Integer
        if (logEnable) { log.debug "Using color mode ${result}" }
        return result
    }
    return null
}

private Map getEntity() {
    return state.lights.get(settings.light as String) ?: [:]
}

private void setColorInternal(Map colorMap) {
    if (colorMap.hue < 0) { colorMap.hue = 0 }
    if (colorMap.hue > 100) { colorMap.hue = 100 }
    if (colorMap.saturation < 0) { colorMap.saturation = 0 }
    if (colorMap.saturation > 100) { colorMap.saturation = 100 }
    if (colorMap.level < 1) { colorMap.level = 1 }
    if (colorMap.level > 100) { colorMap.level = 100 }
    if (device.currentValue('hue') != colorMap.hue ||
        device.currentValue('saturation') != colorMap.saturation ||
        device.currentValue('level') != colorMap.level ||
        device.currentValue('switch') == 'off') {
        if (logTextEnable) { log.info "${device} ${state ? 'set' : 'preset'} color ${colorMap}" }
        def (int r, int g, int b) = ColorUtils.hsvToRGB([colorMap.hue, colorMap.saturation, colorMap.level])
        Integer transitionLength = colorMap.rate == null ? null : colorMap.rate * 1000
        espHomeLightCommand(
            key: settings.light as Long,
            red: r / 255f,
            green: g / 255f,
            blue: b / 255f,
            state: true,
            masterBrightness: colorMap.level / 100f,
            colorBrightness: 1f, // use the master brightness
            colorMode: getColorMode(COLOR_CAP_RGB),
            transitionLength: device.currentValue('colorMode') == 'RGB' ? transitionLength : 0
        )
    }
}

private void setColorTemperatureInternal(BigDecimal colorTemperature, BigDecimal level = null, BigDecimal duration = null) {
    Integer transitionLength = duration
    Integer newLevel = level
    BigDecimal mireds = 1000000f / colorTemperature
    Map entity = getEntity()
    int maxMireds = entity.maxMireds ?: 370
    int minMireds = entity.minMireds ?: 153
    if (mireds > maxMireds) { mireds = maxMireds }
    if (mireds < minMireds) { mireds = minMireds }
    if (transitionLength != null && transitionLength < 0) { transitionLength = 0 }
    if (newLevel != null && newLevel < 1) { newLevel = 1 }
    if (newLevel != null && newLevel > 100) { newLevel = 100 }
    int kelvin = 1000000f / mireds
    if (device.currentValue('colorMode') != 'CT') {
        transitionLength = 0 // when switching from RGB to CT make it fast
    }
    if (device.currentValue('colorTemperature') != kelvin || device.currentValue('switch') == 'off') {
        if (logTextEnable) { log.info "${device} ${state ? 'set' : 'preset'} color temperature ${kelvin}" }
        espHomeLightCommand(
            key: settings.light as Long,
            colorTemperature: mireds,
            masterBrightness: newLevel != null ? newLevel / 100f : null,
            colorMode: getColorMode(COLOR_CAP_COLOR_TEMPERATURE | COLOR_CAP_COLD_WARM_WHITE),
            state: true,
            transitionLength: transitionLength != null ? transitionLength * 1000 : null
        )
    } else if (level != null) {
        setLevelInternal(level, duration, state)
    }
}

private void setLevelInternal(BigDecimal level, BigDecimal duration = null) {
    Integer newLevel = level
    Integer transitionLength = duration
    if (newLevel < 1) { newLevel = 1 }
    if (newLevel > 100) { newLevel = 100 }
    if (transitionLength != null && transitionLength < 0) { transitionLength = 0 }
    if (device.currentValue('level') != level || device.currentValue('switch') == 'off') {
        if (logTextEnable) { log.info "${device} ${state ? 'set' : 'preset'} level to ${level}%" }
        espHomeLightCommand(
            key: settings.light as Long,
            state: true,
            masterBrightness: newLevel / 100f,
            transitionLength: transitionLength != null ? transitionLength * 1000 : null
        )
    }
}

@Field private static Map colorNameMap = [
    15: 'Red',
    45: 'Orange',
    75: 'Yellow',
    105: 'Chartreuse',
    135: 'Green',
    165: 'Spring',
    195: 'Cyan',
    225: 'Azure',
    255: 'Blue',
    285: 'Violet',
    315: 'Magenta',
    345: 'Rose',
    360: 'Red'
]

@Field private static Map colorTempNameMap = [
    2001: 'Sodium',
    2101: 'Starlight',
    2400: 'Sunrise',
    2800: 'Incandescent',
    3300: 'Soft White',
    3500: 'Warm White',
    4150: 'Moonlight',
    5001: 'Horizon',
    5500: 'Daylight',
    6000: 'Electronic',
    6501: 'Skylight',
    20000: 'Polar'
]

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper
