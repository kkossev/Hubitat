/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */
library(
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Color Temperature Library', name: 'ctLib', namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/ctLib.groovy', documentationLink: '',
    version: '3.2.0'
)
/*
 *  Color Temperature Library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 3.2.0  2024-05-31 kkossev  - commonLib 3.2.0 allignment
 *
 *                                   TODO:
*/

static String ctLibVersion()   { '3.2.0' }
static String ctLibStamp() { '2024/05/31 4:35 PM' }

metadata {
    capability 'Color Temperature'  // Attributes: colorName - STRING, colorTemperature - NUMBER, unit:°K; Commands:setColorTemperature(colortemperature, level, transitionTime)
    capability 'ColorMode'          // Attributes:  colorMode - ENUM ["CT", "RGB", "EFFECTS"]
    // no attributes
    // no commands
    preferences {
        // no prefrences
    }
}

import groovy.transform.Field

private getMAX_WHITE_SATURATION() { 70 }
private getWHITE_HUE() { 8 }
private getMIN_COLOR_TEMP() { 2700 }
private getMAX_COLOR_TEMP() { 6500 }




/*
 * -----------------------------------------------------------------------------
 * ColorControl Cluster            0x0300
 * -----------------------------------------------------------------------------
*/
void standardParseColorControlCluster(final Map descMap, description) {
    logDebug "standardParseColorControlCluster: ${descMap}"
    if (descMap.attrId != null) {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "standardParseColorControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        processColorControlCluster(descMap, description)
    }
    else {
        logWarn "unprocessed ColorControl attribute ${descMap.attrId}"
    }
}

void processColorControlCluster(final Map descMap, description) {
    logDebug "processColorControlCluster : ${descMap}"
    def map = [:]
    def parsed

    if (description instanceof String)  {
        map = stringToMap(description)
    }

    logDebug "Map - $map"
    def raw = map['read attr - raw']

    if (raw) {
        def clusterId = map.cluster
        def attrList = raw.substring(12)

        parsed = parseAttributeList(clusterId, attrList)

        if (state.colorChanged || (state.colorXReported && state.colorYReported)) {
            state.colorChanged = false
            state.colorXReported = false
            state.colorYReported = false
            logTrace "Color Change: xy ($state.colorX, $state.colorY)"
            def rgb = colorXy2Rgb(state.colorX, state.colorY)
            logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)"
            updateColor(rgb)        // sends a bunch of events!
        }
    }
    else {
        logDebug 'Sending color event based on pending values'
        if (state.pendingColorUpdate) {
            parsed = true
            def rgb = colorXy2Rgb(state.colorX, state.colorY)
            updateColor(rgb)            // sends a bunch of events!
            state.pendingColorUpdate = false
        }
    }
}

def parseHex4le(hex) {
    Integer.parseInt(hex.substring(2, 4) + hex.substring(0, 2), 16)
}

def parseColorAttribute(id, value) {
    def parsed = false

    if (id == 0x03) {
        // currentColorX
        value = parseHex4le(value)
        logTrace "Parsed ColorX: $value"
        value /= 65536
        parsed = true
        state.colorXReported = true
        state.colorChanged |= value != colorX
        state.colorX = value
    }
    else if (id == 0x04) {
        // currentColorY
        value = parseHex4le(value)
        logTrace "Parsed ColorY: $value"
        value /= 65536
        parsed = true
        state.colorYReported = true
        state.colorChanged |= value != colorY
        state.colorY = value
    }
    else {  // TODO: parse atttribute 7 (color temperature in mireds)
        logDebug "Not parsing Color cluster attribute $id: $value"
    }

    parsed
}

def parseAttributeList(cluster, list) {
    logTrace "Cluster: $cluster, AttrList: $list"
    def parsed = true

    while (list.length()) {
        def attrId = parseHex4le(list.substring(0, 4))
        def attrType = Integer.parseInt(list.substring(4, 6), 16)
        def attrShift = 0

        if (!attrType) {
            attrType = Integer.parseInt(list.substring(6, 8), 16)
            attrShift = 1
        }

        def attrLen = DataType.getLength(attrType)
        def attrValue = list.substring(6 + 2 * attrShift, 6 + 2 * (attrShift+attrLen))

        logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue"

        if (cluster == 300) {
            parsed &= parseColorAttribute(attrId, attrValue)
        }
        else {
            log.info "Not parsing cluster $cluster attribute: $list"
            parsed = false
        }

        list = list.substring(6 + 2 * (attrShift+attrLen))
    }

    parsed
}


def setColorTemperature(value, level=null, rate=null) {
    logDebug "Set color temperature $value"

    def sat = MAX_WHITE_SATURATION - (((value - MIN_COLOR_TEMP) / (MAX_COLOR_TEMP - MIN_COLOR_TEMP)) * MAX_WHITE_SATURATION)
    setColor([
            hue: WHITE_HUE,
            saturation: sat,
            level: level,
            rate: rate
    ])
}

def setColor(value) {
    logDebug "setColor($value)"
    def rgb = colorHsv2Rgb(value.hue / 100, value.saturation / 100)

    logTrace "setColor: RGB ($rgb.red, $rgb.green, $rgb.blue)"
    def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue)
    logTrace "setColor: xy ($xy.x, $xy.y)"

    def intX = Math.round(xy.x * 65536).intValue() // 0..65279
    def intY = Math.round(xy.y * 65536).intValue() // 0..65279

    logTrace "setColor: xy ($intX, $intY)"

    state.colorX = xy.x
    state.colorY = xy.y

    def strX = DataType.pack(intX, DataType.UINT16, true)
    def strY = DataType.pack(intY, DataType.UINT16, true)

    List cmds = []

    def level = value.level
    def rate = value.rate

    if (level != null && rate != null) {
        state.pendingLevelChange = level
        cmds += zigbee.setLevel(level, rate)
    } else if (level != null) {
        state.pendingLevelChange = level
        cmds += zigbee.setLevel(level)
    }

    state.pendingColorUpdate = true

    cmds += zigbee.command(0x0300, 0x07, strX, strY, '0a00')
    if (state.cmds == null) { state.cmds = [] }
    state.cmds += cmds

    logTrace "zigbee command: $cmds"

    unschedule(sendZigbeeCommandsDelayed)
    runInMillis(100, sendZigbeeCommandsDelayed)
}

// all the code below is borrowed from Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver
// -----------------------------------------------------------------------------------------

def updateColor(rgb) {
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)"
    def hsv = colorRgb2Hsv(rgb.red, rgb.green, rgb.blue)
    hsv.hue = Math.round(hsv.hue * 100).intValue()
    hsv.saturation = Math.round(hsv.saturation * 100).intValue()
    hsv.level = Math.round(hsv.level * 100).intValue()
    logTrace "updateColor: HSV ($hsv.hue, $hsv.saturation, $hsv.level)"

    rgb.red = Math.round(rgb.red * 255).intValue()
    rgb.green = Math.round(rgb.green * 255).intValue()
    rgb.blue = Math.round(rgb.blue * 255).intValue()
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)"

    def color = ColorUtils.rgbToHEX([rgb.red, rgb.green, rgb.blue])
    logTrace "updateColor: $color"

    sendColorEvent([name: 'color', value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false])
    sendHueEvent([name: 'hue', value: hsv.hue, displayed: false])
    sendSaturationEvent([name: 'saturation', value: hsv.saturation, displayed: false])
    if (hsv.hue == WHITE_HUE) {
        def percent = (1 - ((hsv.saturation / 100) * (100 / MAX_WHITE_SATURATION)))
        def amount = (MAX_COLOR_TEMP - MIN_COLOR_TEMP) * percent
        def val = Math.round(MIN_COLOR_TEMP + amount)
        sendColorTemperatureEvent([name: 'colorTemperature', value: val])
        sendColorModeEvent([name: 'colorMode', value: 'CT'])
        sendColorNameEvent([setGenericTempName(val)])
    }
    else {
        sendColorModeEvent([name: 'colorMode', value: 'RGB'])
        sendColorNameEvent(setGenericName(hsv.hue))
    }
}

void sendColorEvent(map) {
    if (map.value == device.currentValue(map.name)) {
        logDebug "sendColorEvent: ${map.name} is already ${map.value}"
        return
    }
    // get the time of the last event named "color" and compare it to the current time
 //   def lastColorEvent = device.currentState("color",true).date.time
 //   if ((now() - lastColorEvent) < 1000) {
       // logDebug "sendColorEvent: delaying ${map.name} event because the last color event was less than 1 second ago ${(now() - lastColorEvent)}"
    runInMillis(500, 'sendDelayedColorEvent',  [overwrite: true, data: map])
    return
//    }
    //unschedule("sendDelayedColorEvent") // cancel any pending delayed events
    //logDebug "sendColorEvent: lastColorEvent = ${lastColorEvent}, now = ${now()}, diff = ${(now() - lastColorEvent)}"
    //sendEvent(map)
}
private void sendDelayedColorEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendHueEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, 'sendDelayedHueEvent',  [overwrite: true, data: map])
}
private void sendDelayedHueEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendSaturationEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, 'sendDelayedSaturationEvent',  [overwrite: true, data: map])
}
private void sendDelayedSaturationEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorModeEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, 'sendDelayedColorModeEvent',  [overwrite: true, data: map])
}
private void sendDelayedColorModeEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorNameEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, 'sendDelayedColorNameEvent',  [overwrite: true, data: map])
}
private void sendDelayedColorNameEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorTemperatureEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, 'sendDelayedColorTemperatureEvent',  [overwrite: true, data: map])
}
private void sendDelayedColorTemperatureEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

def sendZigbeeCommandsDelayed() {
    List cmds = state.cmds
    if (cmds != null) {
        state.cmds = []
        sendZigbeeCommands(cmds)
    }
}


def setHue(hue) {
    logDebug "setHue: $hue"
    setColor([ hue: hue, saturation: device.currentValue('saturation') ])
}

def setSaturation(saturation) {
    logDebug "setSaturation: $saturation"
    setColor([ hue: device.currentValue('hue'), saturation: saturation ])
}

def setGenericTempName(temp) {
    if (!temp) return
    String genericName
    int value = temp.toInteger()
    if (value <= 2000) genericName = 'Sodium'
    else if (value <= 2100) genericName = 'Starlight'
    else if (value < 2400) genericName = 'Sunrise'
    else if (value < 2800) genericName = 'Incandescent'
    else if (value < 3300) genericName = 'Soft White'
    else if (value < 3500) genericName = 'Warm White'
    else if (value < 4150) genericName = 'Moonlight'
    else if (value <= 5000) genericName = 'Horizon'
    else if (value < 5500) genericName = 'Daylight'
    else if (value < 6000) genericName = 'Electronic'
    else if (value <= 6500) genericName = 'Skylight'
    else if (value < 20000) genericName = 'Polar'
    String descriptionText = "${device.getDisplayName()} color is ${genericName}"
    return createEvent(name: 'colorName', value: genericName ,descriptionText: descriptionText)
}

def setGenericName(hue) {
    String colorName
    hue = hue.toInteger()
    hue = (hue * 3.6)
    switch (hue.toInteger()) {
        case 0..15: colorName = 'Red'
            break
        case 16..45: colorName = 'Orange'
            break
        case 46..75: colorName = 'Yellow'
            break
        case 76..105: colorName = 'Chartreuse'
            break
        case 106..135: colorName = 'Green'
            break
        case 136..165: colorName = 'Spring'
            break
        case 166..195: colorName = 'Cyan'
            break
        case 196..225: colorName = 'Azure'
            break
        case 226..255: colorName = 'Blue'
            break
        case 256..285: colorName = 'Violet'
            break
        case 286..315: colorName = 'Magenta'
            break
        case 316..345: colorName = 'Rose'
            break
        case 346..360: colorName = 'Red'
            break
    }
    String descriptionText = "${device.getDisplayName()} color is ${colorName}"
    return createEvent(name: 'colorName', value: colorName ,descriptionText: descriptionText)
}

/*
def startLevelChange(direction) {
    def dir = direction == 'up'? 0 : 1
    def rate = 100

    if (levelChangeRate != null) {
        rate = levelChangeRate
    }

    return zigbee.command(0x0008, 0x01, "0x${iTo8bitHex(dir)} 0x${iTo8bitHex(rate)}")
}
*/
/*
def stopLevelChange() {
    return zigbee.command(0x0008, 0x03, '') + zigbee.levelRefresh()
}
*/

// Color Management functions

def min(first, ... rest) {
    def min = first
    for (next in rest) {
        if (next < min) min = next
    }

    min
}

def max(first, ... rest) {
    def max = first
    for (next in rest) {
        if (next > max) max = next
    }

    max
}

def colorGammaAdjust(component) {
    return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92)
}

def colorGammaRevert(component) {
    return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055
}

def colorXy2Rgb(x = 255, y = 255) {
    logTrace "< Color xy: ($x, $y)"
    if (y == 0) return [red: 0, green: 0, blue: 0]  // patch! Added KK 05/31/2024
    def Y = 1
    def X = (Y / y) * x
    def Z = (Y / y) * (1.0 - x - y)

    logTrace "< Color XYZ: ($X, $Y, $Z)"

    // sRGB, Reference White D65
    def M = [
            [  3.2410032, -1.5373990, -0.4986159 ],
            [ -0.9692243,  1.8759300,  0.0415542 ],
            [  0.0556394, -0.2040112,  1.0571490 ]
    ]

    def r = X * M[0][0] + Y * M[0][1] + Z * M[0][2]
    def g = X * M[1][0] + Y * M[1][1] + Z * M[1][2]
    def b = X * M[2][0] + Y * M[2][1] + Z * M[2][2]

    def max = max(r, g, b)
    r = colorGammaRevert(r / max)
    g = colorGammaRevert(g / max)
    b = colorGammaRevert(b / max)

    logTrace "< Color RGB: ($r, $g, $b)"

    [red: r, green: g, blue: b]
}

def colorRgb2Xy(r, g, b) {
    logTrace "> Color RGB: ($r, $g, $b)"

    r = colorGammaAdjust(r)
    g = colorGammaAdjust(g)
    b = colorGammaAdjust(b)

    // sRGB, Reference White D65
    // D65    0.31271    0.32902
    //  R  0.64000 0.33000
    //  G  0.30000 0.60000
    //  B  0.15000 0.06000
    def M = [
            [  0.4123866,  0.3575915,  0.1804505 ],
            [  0.2126368,  0.7151830,  0.0721802 ],
            [  0.0193306,  0.1191972,  0.9503726 ]
    ]

    def X = r * M[0][0] + g * M[0][1] + b * M[0][2]
    def Y = r * M[1][0] + g * M[1][1] + b * M[1][2]
    def Z = r * M[2][0] + g * M[2][1] + b * M[2][2]

    logTrace "> Color XYZ: ($X, $Y, $Z)"

    def x = X / (X + Y + Z)
    def y = Y / (X + Y + Z)

    logTrace "> Color xy: ($x, $y)"

    [x: x, y: y]
}

def colorHsv2Rgb(h, s) {
    logTrace "< Color HSV: ($h, $s, 1)"

    def r
    def g
    def b

    if (s == 0) {
        r = 1
        g = 1
        b = 1
    }
    else {
        def region = (6 * h).intValue()
        def remainder = 6 * h - region

        def p = 1 - s
        def q = 1 - s * remainder
        def t = 1 - s * (1 - remainder)

        if (region == 0) {
            r = 1
            g = t
            b = p
        }
        else if (region == 1) {
            r = q
            g = 1
            b = p
        }
        else if (region == 2) {
            r = p
            g = 1
            b = t
        }
        else if (region == 3) {
            r = p
            g = q
            b = 1
        }
        else if (region == 4) {
            r = t
            g = p
            b = 1
        }
        else {
            r = 1
            g = p
            b = q
        }
    }

    logTrace "< Color RGB: ($r, $g, $b)"

    [red: r, green: g, blue: b]
}

def colorRgb2Hsv(r, g, b) {
    logTrace "> Color RGB: ($r, $g, $b)"

    def min = min(r, g, b)
    def max = max(r, g, b)
    def delta = max - min

    def h
    def s
    def v = max

    if (delta == 0) {
        h = 0
        s = 0
    }
    else {
        s = delta / max
        if (r == max) h = ( g - b ) / delta            // between yellow & magenta
        else if (g == max) h = 2 + ( b - r ) / delta    // between cyan & yellow
        else h = 4 + ( r - g ) / delta                // between magenta & cyan
        h /= 6

        if (h < 0) h += 1
    }

    logTrace "> Color HSV: ($h, $s, $v)"

    return [ hue: h, saturation: s, level: v ]
}

def iTo8bitHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

// ----------- end of Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver code ------------

