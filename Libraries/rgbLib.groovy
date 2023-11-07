library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "RGB Library",
    name: "rgbLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/rgbLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 *  Zigbee Button Dimmer -Library
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
 *  Credits: Ivar Holand for 'IKEA Tradfri RGBW Light HE v2' driver code
 *
 * ver. 1.0.0  2023-11-06 kkossev  - added rgbLib; musicMode;
 *
 *                                   TODO: 
*/

def thermostatLibVersion()   {"1.0.0"}
def thermostatLibStamp() {"2023/11/07 5:23 PM"}

import hubitat.helper.ColorUtils

metadata {
    capability "Actuator"
    capability "Color Control"
    capability "ColorMode"
    capability "Color Temperature"
    capability "Refresh"
    capability "Switch"
    capability "Switch Level"
    capability "Light"
    capability "ChangeLevel"

    attribute "deviceTemperature", "number"
    attribute "musicMode", "enum", MusicModeOpts.options.values() as List<String>

    command "musicMode", [[name:"Select Music Mode", type: "ENUM",   constraints: ["--- select ---"]+MusicModeOpts.options.values() as List<String>]]


    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  }
    
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0005,0004,0003,0000,0300,0008,0006,FCC0", outClusters:"0019,000A", model:"lumi.light.acn132", manufacturer:"Aqara"
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/7200
    //https://github.com/dresden-elektronik/deconz-rest-plugin/blob/50555f9350dc1872f266ebe9a5b3620b76e99af6/devices/xiaomi/lumi_light_acn132.json#L4
    preferences {
    }
}



private getMAX_WHITE_SATURATION() { 70 }
private getWHITE_HUE() { 8 }
private getMIN_COLOR_TEMP() { 2700 }
private getMAX_COLOR_TEMP() { 6500 }

@Field static final Map MusicModeOpts = [            // preset
    defaultValue: 0,
    options     : [0: 'off', 1: 'on']
]

/*
 * -----------------------------------------------------------------------------
 * Level Control Cluster            0x0008
 * -----------------------------------------------------------------------------
*/
void parseLevelControlClusterBulb(final Map descMap) {
    logDebug "parseLevelControlClusterBulb: 0x${descMap.value}"
    if (descMap.attrId == "0000") {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        // Aqara LED Strip T1 sends the level in the range 0..255
        def scaledValue = ((rawValue as double) / 2.55F + 0.5) as int
        sendLevelControlEvent(scaledValue)
    }
    else {
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}"
    }
}

/*
 * -----------------------------------------------------------------------------
 * ColorControl Cluster            0x0300
 * -----------------------------------------------------------------------------
*/
void parseColorControlClusterBulb(final Map descMap, description) {
    if (descMap.attrId != null) {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseColorControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
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
    def raw = map["read attr - raw"]

    if(raw) {
        def clusterId = map.cluster
        def attrList = raw.substring(12)

        parsed = parseAttributeList(clusterId, attrList)

        if(state.colorChanged || (state.colorXReported && state.colorYReported)) {
            state.colorChanged = false;
            state.colorXReported = false;
            state.colorYReported = false;
            logTrace "Color Change: xy ($state.colorX, $state.colorY)"
            def rgb = colorXy2Rgb(state.colorX, state.colorY)
            logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)"
            updateColor(rgb)        // sends a bunch of events!
        }
    }
    else {
        logDebug "Sending color event based on pending values"
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

    if(id == 0x03) {
        // currentColorX
        value = parseHex4le(value)
        logTrace "Parsed ColorX: $value"
        value /= 65536
        parsed = true
        state.colorXReported = true;
        state.colorChanged |= value != colorX
        state.colorX = value
    }
    else if(id == 0x04) {
        // currentColorY
        value = parseHex4le(value)
        logTrace "Parsed ColorY: $value"
        value /= 65536
        parsed = true
        state.colorYReported = true;
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

    while(list.length()) {
        def attrId = parseHex4le(list.substring(0, 4))
        def attrType = Integer.parseInt(list.substring(4, 6), 16)
        def attrShift = 0

        if(!attrType) {
            attrType = Integer.parseInt(list.substring(6, 8), 16)
            attrShift = 1
        }

        def attrLen = DataType.getLength(attrType)
        def attrValue = list.substring(6 + 2*attrShift, 6 + 2*(attrShift+attrLen))

        logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue"

        if(cluster == 300) {
            parsed &= parseColorAttribute(attrId, attrValue)
        }
        else {
            log.info "Not parsing cluster $cluster attribute: $list"
            parsed = false;
        }

        list = list.substring(6 + 2*(attrShift+attrLen))
    }

    parsed
}



/*
def sendColorControlEvent( rawValue ) {
    logWarn "TODO: sendColorControlEvent ($rawValue)"
    return
    
    def value = rawValue as int
    if (value <0) value = 0
    if (value >100) value = 100
    def map = [:] 
    
    def isDigital = state.states["isDigital"]
    map.type = isDigital == true ? "digital" : "physical"
        
    map.name = "level"
    map.value = value
    boolean isRefresh = state.states["isRefresh"] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    }
    else {
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
}
*/

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterRgbLib(final Map descMap) {
    //logWarn "parseXiaomiClusterRgbLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    final Integer raw
    final String  value
    switch (descMap.attrInt as Integer) {
        case 0x00EE:    // attr/swversion"
            raw = hexStrToUnsignedInt(descMap.value)        // val = '0.0.0_' + ('0000' + ((Attr.val & 0xFF00) >> 8).toString() + (Attr.val & 0xFF).toString()).slice(-4)"
            logInfo "Aqara Version is ${raw}"
            break
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterRgbTags(tags)
            break
        case 0x0515:    // config/bri/min                   // r/w "dt": "0x20" 
            raw = hexStrToUnsignedInt(descMap.value)        // .val = Math.round(Attr.val * 2.54)
            logInfo "Aqara min brightness is ${raw}"
            break
        case 0x0516:    // config/bri/max                   // r/w "dt": "0x20" 
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara max brightness is ${raw}"
            break
        case 0x0517:    // config/on/startup               // r/w "dt": "0x20" 
            raw = hexStrToUnsignedInt(descMap.value)       // val = [1, 255, 0][Attr.val]       // val === 1 ? 0 : Item.val === 0 ? 2 : 1" 
            logInfo "Aqara on startup is ${raw}"
            break
        case 0x051B:    // config/color/gradient/pixel_count                  // r/w "dt": "0x20" , Math.max(5, Math.min(Item.val, 50))
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara pixel count is ${raw}"
            break
        case 0x051C:    // state/music_sync                 // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0 
            raw = hexStrToUnsignedInt(descMap.value)
            value = MusicModeOpts.options[raw as int]
            aqaraEvent("musicMode", value, raw)
            break
        case 0x0509:    // state/gradient                   // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0 
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient is ${raw}"
            break
        case 0x051F:    // state/gradient/flow              // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0 
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient flow is ${raw}"
            break
        case 0x051D:    // state/gradient/flow/speed        // r/w "dt": "0x20" , val = Math.max(1, Math.min(Item.val, 10))
            raw = hexStrToUnsignedInt(descMap.value)
            logInfo "Aqara gradient flow speed is ${raw}"
            break
        default:
            logWarn "parseXiaomiClusterRgbLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

void aqaraEvent(eventName, value, raw) {
    sendEvent(name: eventName, value: value, type: "physical")
    logInfo "${eventName} is ${value} (raw ${raw})"
}

//
// called from parseXiaomiClusterRgbLib 
//
void parseXiaomiClusterRgbTags(final Map<Integer, Object> tags) {       // TODO: check https://github.com/sprut/Hub/issues/2420 
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg;"
                sendEvent(name: "deviceTemperature", value: value, unit: "C")
                break
            case 0x05:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
                break
            case 0x06:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}"
                break
            case 0x08:            // SWBUILD_TAG_ID:
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
                device.updateDataValue("aqaraVersion", swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer,2)
                if (state.health == null) { state.health = [:] }
                String oldNWK = state.health['parentNWK'] ?: 'n/a'
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
                if (oldNWK != nwk ) {
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                    state.health['parentNWK']  = nwk
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                }
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
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

    sendColorEvent([name: "color", value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false])
    sendHueEvent([name: "hue", value: hsv.hue, displayed: false])
    sendSaturationEvent([name: "saturation", value: hsv.saturation, displayed: false])
    if (hsv.hue == WHITE_HUE) {
        def percent = (1 - ((hsv.saturation / 100) * (100 / MAX_WHITE_SATURATION)))
        def amount = (MAX_COLOR_TEMP - MIN_COLOR_TEMP) * percent
        def val = Math.round(MIN_COLOR_TEMP + amount)
        sendColorTemperatureEvent([name: "colorTemperature", value: val])
        sendColorModeEvent([name: "colorMode", value: "CT"])
        sendColorNameEvent([setGenericTempName(val)])
    } 
    else {
        sendColorModeEvent([name: "colorMode", value: "RGB"])
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
        runInMillis(500, "sendDelayedColorEvent",  [overwrite: true, data: map])
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
    runInMillis(500, "sendDelayedHueEvent",  [overwrite: true, data: map])
}
private void sendDelayedHueEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendSaturationEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, "sendDelayedSaturationEvent",  [overwrite: true, data: map])
}
private void sendDelayedSaturationEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorModeEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, "sendDelayedColorModeEvent",  [overwrite: true, data: map])
}
private void sendDelayedColorModeEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorNameEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, "sendDelayedColorNameEvent",  [overwrite: true, data: map])
}
private void sendDelayedColorNameEvent(Map map) {
    sendEvent(map)
    logInfo "${map.name} is now ${map.value}"
}

void sendColorTemperatureEvent(map) {
    if (map.value == device.currentValue(map.name)) { return }
    runInMillis(500, "sendDelayedColorTemperatureEvent",  [overwrite: true, data: map])
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

def setLevelBulb(value, rate=null) {
    logDebug "setLevelBulb: $value, $rate"

    state.pendingLevelChange = value

    if (rate == null) {
        state.cmds += zigbee.setLevel(value)
    } else {
        state.cmds += zigbee.setLevel(value, rate)
    }

    unschedule(sendZigbeeCommandsDelayed)
    runInMillis(100, sendZigbeeCommandsDelayed)
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
    def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue);
    logTrace "setColor: xy ($xy.x, $xy.y)"

    def intX = Math.round(xy.x*65536).intValue() // 0..65279
    def intY = Math.round(xy.y*65536).intValue() // 0..65279

    logTrace "setColor: xy ($intX, $intY)"

    state.colorX = xy.x
    state.colorY = xy.y

    def strX = DataType.pack(intX, DataType.UINT16, true);
    def strY = DataType.pack(intY, DataType.UINT16, true);

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

    cmds += zigbee.command(0x0300, 0x07, strX, strY, "0a00")
    if (state.cmds == null) { state.cmds = [] }   
    state.cmds += cmds

    logTrace "zigbee command: $cmds"

    unschedule(sendZigbeeCommandsDelayed)
    runInMillis(100, sendZigbeeCommandsDelayed)
}


def setHue(hue) {
    logDebug "setHue: $hue"
    setColor([ hue: hue, saturation: device.currentValue("saturation") ])
}

def setSaturation(saturation) {
    logDebug "setSaturation: $saturation"
    setColor([ hue: device.currentValue("hue"), saturation: saturation ])
}

def setGenericTempName(temp){
    if (!temp) return
    String genericName
    int value = temp.toInteger()
    if (value <= 2000) genericName = "Sodium"
    else if (value <= 2100) genericName = "Starlight"
    else if (value < 2400) genericName = "Sunrise"
    else if (value < 2800) genericName = "Incandescent"
    else if (value < 3300) genericName = "Soft White"
    else if (value < 3500) genericName = "Warm White"
    else if (value < 4150) genericName = "Moonlight"
    else if (value <= 5000) genericName = "Horizon"
    else if (value < 5500) genericName = "Daylight"
    else if (value < 6000) genericName = "Electronic"
    else if (value <= 6500) genericName = "Skylight"
    else if (value < 20000) genericName = "Polar"
    String descriptionText = "${device.getDisplayName()} color is ${genericName}"
    return createEvent(name: "colorName", value: genericName ,descriptionText: descriptionText)
}

def setGenericName(hue){
    String colorName
    hue = hue.toInteger()
    hue = (hue * 3.6)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
    }
    String descriptionText = "${device.getDisplayName()} color is ${colorName}"
    return createEvent(name: "colorName", value: colorName ,descriptionText: descriptionText)
}


def startLevelChange(direction) {
    def dir = direction == "up"? 0 : 1
    def rate = 100

    if (levelChangeRate != null) {
        rate = levelChangeRate
    }

    return zigbee.command(0x0008, 0x01, "0x${iTo8bitHex(dir)} 0x${iTo8bitHex(rate)}")
}

def stopLevelChange() {
    return zigbee.command(0x0008, 0x03, "") + zigbee.levelRefresh()
}


// Color Management functions

def min(first, ... rest) {
    def min = first;
    for(next in rest) {
        if(next < min) min = next
    }

    min
}

def max(first, ... rest) {
    def max = first;
    for(next in rest) {
        if(next > max) max = next
    }

    max
}

def colorGammaAdjust(component) {
    return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92)
}

def colorGammaRevert(component) {
    return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055;
}

def colorXy2Rgb(x = 255, y = 255) {

    logTrace "< Color xy: ($x, $y)"

    def Y = 1;
    def X = (Y / y) * x;
    def Z = (Y / y) * (1.0 - x - y);

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

        if(region == 0) {
            r = 1
            g = t
            b = p
        }
        else if(region == 1) {
            r = q
            g = 1
            b = p
        }
        else if(region == 2) {
            r = p
            g = 1
            b = t
        }
        else if(region == 3) {
            r = p
            g = q
            b = 1
        }
        else if(region == 4) {
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


def colorRgb2Hsv(r, g, b)
{
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
        else if(g == max) h = 2 + ( b - r ) / delta    // between cyan & yellow
        else h = 4 + ( r - g ) / delta                // between magenta & cyan
        h /= 6

        if(h < 0) h += 1
    }

    logTrace "> Color HSV: ($h, $s, $v)"

    return [ hue: h, saturation: s, level: v ]
}

def iTo8bitHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}


// ----------- end of Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver code ------------

def musicMode(mode) {
    List<String> cmds = []
    if (mode in MusicModeOpts.options.values()) {
        logDebug "sending musicMode: ${mode}"
        if (mode == "on") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x01, [mfgCode: 0x115F], delay=200)
        }
        else if (mode == "off") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x00, [mfgCode: 0x115F], delay=200)
        }
    }
    else {
        logWarn "musicMode: invalid mode ${mode}"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)

}


//
// called from updated() in the main code ...
void updatedBulb() {
    logDebug "updatedBulb()..."
}

def colorControlRefresh() {
    def commands = []
    commands += zigbee.readAttribute(0x0300, 0x03,[:],200) // currentColorX
    commands += zigbee.readAttribute(0x0300, 0x04,[:],201) // currentColorY
    commands
}

def colorControlConfig(min, max, step) {
    def commands = []
    commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX
    commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY
    commands
}

def refreshBulb() {
    List<String> cmds = []
    state.colorChanged = false
    state.colorXReported = false
    state.colorYReported = false    
    state.cmds = []
    cmds =  zigbee.onOffRefresh(200) + zigbee.levelRefresh(201) + colorControlRefresh()
    cmds += zigbee.readAttribute(0x0300,[0x4001,0x400a,0x400b,0x400c,0x000f],[:],204)    // colormode and color/capabilities
    cmds += zigbee.readAttribute(0x0008,[0x000f,0x0010,0x0011],[:],204)                  // config/bri/execute_if_off
    cmds += zigbee.readAttribute(0xFCC0,[0x0515,0x0516,0x517],[mfgCode:0x115F],204)      // config/bri/min & max * startup
    cmds += zigbee.readAttribute(0xFCC0,[0x051B,0x051c],[mfgCode:0x115F],204)            // pixel count & musicMode
    if (cmds == []) { cmds = ["delay 299"] }
    logDebug "refreshBulb: ${cmds} "
    return cmds
}

def configureBulb() {
    List<String> cmds = []
    logDebug "configureBulb() : ${cmds}"
    cmds = refreshBulb() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + colorControlConfig(0, 300, 1)
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}

def initializeBulb()
{
    List<String> cmds = []
    logDebug "initializeBulb() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299",] }
    return cmds        
}


void initVarsBulb(boolean fullInit=false) {
    state.colorChanged = false
    state.colorXReported = false
    state.colorYReported = false
    state.colorX = 0.9999
    state.colorY = 0.9999
    state.cmds = []
    //if (fullInit || settings?.temperaturePollingInterval == null) device.updateSetting('temperaturePollingInterval', [value: TemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum'])

    logDebug "initVarsBulb(${fullInit})"
}


void initEventsBulb(boolean fullInit=false) {
    logDebug "initEventsBulb(${fullInit})"
    if((device.currentState("saturation")?.value == null)) {
        sendEvent(name: "saturation", value: 0);
    }
    if((device.currentState("hue")?.value == null)) {
        sendEvent(name: "hue", value: 0);
    }
    if ((device.currentState("level")?.value == null) || (device.currentState("level")?.value == 0)) {
        sendEvent(name: "level", value: 100)
    }    
}
/*
================================================================================================
Node Descriptor
================================================================================================
▸ Logical Type                              = Zigbee Router
▸ Complex Descriptor Available              = No
▸ User Descriptor Available                 = No
▸ Frequency Band                            = 2400 - 2483.5 MHz
▸ Alternate PAN Coordinator                 = No
▸ Device Type                               = Full Function Device (FFD)
▸ Mains Power Source                        = Yes
▸ Receiver On When Idle                     = Yes (always on)
▸ Security Capability                       = No
▸ Allocate Address                          = Yes
▸ Manufacturer Code                         = 0x115F = XIAOMI
▸ Maximum Buffer Size                       = 82 bytes
▸ Maximum Incoming Transfer Size            = 82 bytes
▸ Primary Trust Center                      = No
▸ Backup Trust Center                       = No
▸ Primary Binding Table Cache               = Yes
▸ Backup Binding Table Cache                = No
▸ Primary Discovery Cache                   = Yes
▸ Backup Discovery Cache                    = Yes
▸ Network Manager                           = Yes
▸ Maximum Outgoing Transfer Size            = 82 bytes
▸ Extended Active Endpoint List Available   = No
▸ Extended Simple Descriptor List Available = No
================================================================================================
Power Descriptor
================================================================================================
▸ Current Power Mode         = Same as "Receiver On When Idle" from "Node Descriptor" section above
▸ Available Power Sources    = [Constant (mains) power]
▸ Current Power Sources      = [Constant (mains) power]
▸ Current Power Source Level = 100%
================================================================================================
Endpoint 0x01 | Out Clusters: 0x000A (Time Cluster), 0x0019 (OTA Upgrade Cluster)
================================================================================================
Endpoint 0x01 | In Cluster: 0x0000 (Basic Cluster)
================================================================================================
▸ 0x0000 | ZCL Version          | req | r-- | uint8  | 03                | --
▸ 0x0001 | Application Version  | opt | r-- | uint8  | 1B                | --
▸ 0x0002 | Stack Version        | opt | r-- | uint8  | 1B                | --
▸ 0x0003 | HW Version           | opt | r-- | uint8  | 01                | --
▸ 0x0004 | Manufacturer Name    | opt | r-- | string | Aqara             | --
▸ 0x0005 | Model Identifier     | opt | r-- | string | lumi.light.acn132 | --
▸ 0x0006 | Date Code            | req | r-- | string | 20230606          | --
▸ 0x0007 | Power Source         | opt | r-- | enum8  | 04 = DC source    | --
▸ 0x000A | Product Code         | opt | r-- | octstr | --                | --
▸ 0x000D | Serial Number        | opt | r-- | string | --                | --
▸ 0x0010 | Location Description | opt | rw- | string | é»è®¤æ¿é´     | --
▸ 0xF000 | --                   | --  | r-- | uint16 | 0000              | --
▸ 0xFFFD | Cluster Revision     | req | r-- | uint16 | 0002              | --
------------------------------------------------------------------------------------------------
▸ No commands found
================================================================================================
Endpoint 0x01 | In Cluster: 0x0003 (Identify Cluster)
================================================================================================
▸ 0x0000 | Identify Time    | req | rw- | uint16 | 0000 = 0 seconds | --
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0001             | --
------------------------------------------------------------------------------------------------
▸ 0x00 | Identify       | req
▸ 0x01 | Identify Query | req
================================================================================================
Endpoint 0x01 | In Cluster: 0x0004 (Groups Cluster)
================================================================================================
▸ 0x0000 | Name Support     | req | r-- | map8   | 00   | --
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002 | --
------------------------------------------------------------------------------------------------
▸ 0x00 | Add Group                | req
▸ 0x01 | View Group               | req
▸ 0x02 | Get Group Membership     | req
▸ 0x03 | Remove Group             | req
▸ 0x04 | Remove All Groups        | req
▸ 0x05 | Add Group If Identifying | req
================================================================================================
Endpoint 0x01 | In Cluster: 0x0005 (Scenes Cluster)
================================================================================================
▸ 0x0000 | Scene Count      | req | r-- | uint8  | 00         | --
▸ 0x0001 | Current Scene    | req | r-- | uint8  | 00         | --
▸ 0x0002 | Current Group    | req | r-- | uint16 | 0000       | --
▸ 0x0003 | Scene Valid      | req | r-- | bool   | 00 = False | --
▸ 0x0004 | Name Support     | req | r-- | map8   | 00         | --
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002       | --
------------------------------------------------------------------------------------------------
▸ 0x00 | Add Scene            | req
▸ 0x01 | View Scene           | req
▸ 0x02 | Remove Scene         | req
▸ 0x03 | Remove All Scenes    | req
▸ 0x04 | Store Scene          | req
▸ 0x05 | Recall Scene         | req
▸ 0x06 | Get Scene Membership | req
================================================================================================
Endpoint 0x01 | In Cluster: 0x0006 (On/Off Cluster)
================================================================================================
▸ 0x0000 | On Off           | req | r-p | bool   | 01 = On  | 0..300
▸ 0x00F5 | --               | --  | r-- | uint32 | 00D8A053 | --    
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002     | --    
------------------------------------------------------------------------------------------------
▸ 0x00 | Off    | req
▸ 0x01 | On     | req
▸ 0x02 | Toggle | req
================================================================================================
Endpoint 0x01 | In Cluster: 0x0008 (Level Control Cluster)
================================================================================================
▸ 0x0000 | Current Level          | req | r-p | uint8  | 0C = 4%          | 1..3600
▸ 0x0001 | Remaining Time         | opt | r-- | uint16 | 0000 = 0 seconds | --     
▸ 0x0002 | --                     | --  | r-- | uint8  | 01               | --     
▸ 0x0003 | --                     | --  | r-- | uint8  | FE               | --     
▸ 0x000F | --                     | --  | rw- | map8   | 00               | --     
▸ 0x0010 | On Off Transition Time | opt | rw- | uint16 | 000F = 1 seconds | --     
▸ 0x0011 | On Level               | opt | rw- | uint8  | 0C = 4%          | --     
▸ 0x0012 | On Transition Time     | opt | rw- | uint16 | 000F = 1 seconds | --     
▸ 0x0013 | Off Transition Time    | opt | rw- | uint16 | 000F = 1 seconds | --     
▸ 0x00F5 | --                     | --  | r-- | uint32 | 00D8A074         | --     
▸ 0xFFFD | Cluster Revision       | req | r-- | uint16 | 0002             | --     
------------------------------------------------------------------------------------------------
▸ 0x00 | Move To Level             | req
▸ 0x01 | Move                      | req
▸ 0x02 | Step                      | req
▸ 0x03 | Stop                      | req
▸ 0x04 | Move To Level With On/Off | req
▸ 0x05 | Move With On/Off          | req
▸ 0x06 | Step With On/Off          | req
▸ 0x07 | Stop                      | req
================================================================================================
Endpoint 0x01 | In Cluster: 0x0300 (Color Control Cluster)
================================================================================================
▸ 0x0002 | Remaining Time                   | opt | r-- | uint16 | 0000     | --    
▸ 0x0003 | CurrentX                         | req | r-p | uint16 | 4A3C     | 0..300
▸ 0x0004 | CurrentY                         | req | r-p | uint16 | 8FEB     | 0..300
▸ 0x0007 | Color Temperature Mireds         | req | r-p | uint16 | 0099     | --    
▸ 0x0008 | Color Mode                       | req | r-- | enum8  | 01       | --    
▸ 0x000F | --                               | --  | rw- | map8   | 00       | --    
▸ 0x0010 | Number Of Primaries              | req | r-- | uint8  | 00       | --    
▸ 0x00F5 | --                               | --  | r-- | uint32 | 00D8A06A | --    
▸ 0x4001 | Enhanced Color Mode              | req | r-- | enum8  | 01       | --    
▸ 0x400A | Color Capabilities               | req | r-- | map16  | 0018     | --    
▸ 0x400B | Color Temp Physical Min Mireds   | req | r-- | uint16 | 0099     | --    
▸ 0x400C | Color Temp Physical Max Mireds   | req | r-- | uint16 | 0172     | --    
▸ 0x400D | --                               | --  | r-- | uint16 | 0099     | --    
▸ 0x4010 | StartUp Color Temperature Mireds | opt | rw- | uint16 | 00FA     | --    
▸ 0xFFFD | Cluster Revision                 | req | r-- | uint16 | 0002     | --    
------------------------------------------------------------------------------------------------
▸ 0x07 | Move to Color             | req
▸ 0x08 | Move Color                | req
▸ 0x09 | Step Color                | req
▸ 0x0A | Move to Color Temperature | req
▸ 0x47 | Stop Move Step            | req
▸ 0x4B | Move Color Temperature    | req
▸ 0x4C | Step Color Temperature    | req
================================================================================================
Endpoint 0x01 | In Cluster: 0xFCC0 (Unknown Cluster)
================================================================================================
▸ No attributes found
------------------------------------------------------------------------------------------------
▸ No commands found
================================================================================================

*/

def testT(par) {
    logWarn "testT(${par})"
}

