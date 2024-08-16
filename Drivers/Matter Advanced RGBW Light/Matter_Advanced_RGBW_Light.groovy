/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, LineLength, MethodCount, MethodParameterTypeRequired, NoDef, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter */
/**
 *  Matter test - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * Thanks to Hubitat for publishing the sample Matter driver https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/thirdRealityMatterNightLight.groovy
 *
 * ver. 1.0.0  2023-12-19 kkossev  - Inital version: added healthCheck attribute; added refresh(); added stats; added RTT attribute; added periodicPolling healthCheck method;
 *
 *                                   TODO: isDigital isRefresh
 *                                   TODO: add toggle()
 *                                   TODO: add flashOnce()
 *                                   TODO: add powerOnBehavior
 *                                   TODO: add offlineCtr in stats
 *                                   TODO: supress repetative log events like 'Nanoleaf Bulb saturation was set to 96%'
 */

static String version() { '1.0.0' }
static String timeStamp() { '2023/12/21 11:38 PM' }

@Field static final Boolean _DEBUG = false
@Field static final String   DEVICE_TYPE = 'MATTER_BULB'
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final String  UNKNOWN = 'UNKNOWN'

import groovy.transform.Field

metadata {
    definition(name: 'Matter Advanced RGBW Light', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Actuator'
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'Configuration'
        capability 'Color Control'
        capability "ColorTemperature"
        capability 'Light'
        capability 'Initialize'
        capability 'Refresh'
        capability 'Health Check'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'

        if (_DEBUG) {
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']]
        }
        // fingerprints are commented out, because are already included in the stock driver
        // fingerprint endpointId:'01', inClusters:'0003,0004,0005,0006,0008,001D,001E,0300', outClusters:'', model:'NL67', manufacturer:'Nanoleaf', controllerType:'MAT'                      // Nanoleaf Essentials A19 Bulb
        // fingerprint endpointId:'01', inClusters:'0003,0004,0005,0006,0008,001D,001E,0300', outClusters:'', model:'NL68', manufacturer:'Nanoleaf', controllerType:'MAT'                      // Nanoleaf Strip 5E8
    }
    preferences {
        input(name:'txtEnable', type:'bool', title:'Enable descriptionText logging', defaultValue:true)
        input(name:'logEnable', type:'bool', title:'Enable debug logging', defaultValue:true)
        input(name:'transitionTime', type:'enum', title:"Level transition time (default:${ttOpts.defaultText})", options:ttOpts.options, defaultValue:ttOpts.defaultValue)
        input(name:'rgbTransitionTime', type:'enum', title:"RGB transition time (default:${ttOpts.defaultText})", options:ttOpts.options, defaultValue:ttOpts.defaultValue)
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false
        if (advancedOptions == true || advancedOptions == true) {
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
            input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
        }
    }
}

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1,
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240,
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
//transitionTime options
@Field static Map ttOpts = [
    defaultValue: '1',
    defaultText:  '1s',
    options:['0':'ASAP', '1':'1s', '2':'2s', '5':'5s']
]

@Field static Map colorRGBName = [
    4: 'Red',
    13:'Orange',
    21:'Yellow',
    29:'Chartreuse',
    38:'Green',
    46:'Spring',
    54:'Cyan',
    63:'Azure',
    71:'Blue',
    79:'Violet',
    88:'Magenta',
    96:'Rose',
    101:'Red'
]

//parsers
void parse(String description) {
    checkDriverVersion()
    if (state.stats  != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats =  [:] }
    if (state.lastRx != null) { state.lastRx['checkInTime'] = new Date().getTime() }     else { state.lastRx = [:] }
    unschedule('deviceCommandTimeout')
    setHealthStatusOnline()

    Map descMap = matter.parseDescriptionAsMap(description)
    logDebug "parse: descMap:${descMap}  description:${description}"
    switch (descMap.cluster) {
        case '0000' :
            if (descMap.attrId == '4000') { //software build
                updateDataValue('softwareBuild', descMap.value ?: 'unknown')
            }
            else {
                logWarn "skipped softwareBuild, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0003' :   // Identify
            if (descMap.attrId == '0000') { // Identify
                logDebug "parse: Identify:${descMap.value}"
            }
            else {
                logWarn "parse: skipped Identify, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0004' :   // Groups
            if (descMap.attrId == '0000') { // Groups
                logDebug "parse: Groups:${descMap.value}"
            }
            else {
                logWarn "parse: skipped Groups, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0005' :   // Scenes
            if (descMap.attrId == '0000') { // Scenes
                logDebug "parse: Scenes:${descMap.value}"
            }
            else {
                logWarn "parse: skipped Scenes, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0006' :
            if (descMap.attrId == '0000') { // Switch
                sendSwitchEvent(descMap.value)
            }
            else if (descMap.attrId == '4000') { // GlobalSceneControl

                boolean isPing = state.states['isPing'] ?: false
                if (isPing) {
                    Long now = new Date().getTime()
                    Integer timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger()
                    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
                        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1
                        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning }
                        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning }
                        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int
                        sendRttEvent()
                    }
                    else {
                        logWarn "unexpected ping timeRunning=${timeRunning} "
                    }
                    state.states['isPing'] = false
                }
                else {
                    logDebug "parse: Switch: GlobalSceneControl = ${descMap.value}"
                }
            }
            else if (descMap.attrId == '4001') { // OnTime
                logDebug "parse: Switch: OnTime = ${descMap.value}"
            }
            else if (descMap.attrId == '4002') { // OffWaitTime
                logDebug "parse: Switch: OffWaitTime = ${descMap.value}"
            }
            else if (descMap.attrId == '4003') { // StartUpOnOff
                logDebug "parse: Switch: StartUpOnOff = ${descMap.value}"
            }
            else {
                logWarn "parse: skipped switch, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0008' :
            if (descMap.attrId == '0000') { //current level
                sendLevelEvent(descMap.value)
            }
            else {
                logWarn "skipped level, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0300' :
            if (descMap.attrId == '0000') { //hue
                sendHueEvent(descMap.value)
            } else if (descMap.attrId == '0001') { //saturation
                sendSaturationEvent(descMap.value)
            }
            else if (descMap.attrId == "0007") { //color temp
                sendCTEvent(descMap.value)
            } //logDebug "parse: skipped color temperature:${descMap}"
            else if (descMap.attrId == '0008') { //color mode
                logDebug "parse: skipped color mode:${descMap}"
            }
            else {
                logWarn "parse: skipped color, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        default :
                logDebug "parse: skipped:${descMap}"
    }
}

//events
private void sendSwitchEvent(String rawValue) {
    String value = rawValue == '01' ? 'on' : 'off'
    if (device.currentValue('switch') == value) { return }
    String descriptionText = " was turned ${value}"
    logInfo "${descriptionText}"
    sendEvent(name:'switch', value:value, descriptionText:descriptionText)
}

private void sendLevelEvent(String rawValue) {
    Integer value = Math.round(hexStrToUnsignedInt(rawValue) / 2.55)
    if (value == 0 || value == device.currentValue('level')) { return }
    String descriptionText = " level was set to ${value}%"
    logInfo "${descriptionText}"
    sendEvent(name:'level', value:value, descriptionText:descriptionText, unit: '%')
}

/* groovylint-disable-next-line UnusedPrivateMethodParameter */
private void sendHueEvent(String rawValue, Boolean presetColor = false) {
    Integer value = hex254ToInt100(rawValue)
    if (value == device.currentValue('hue')) { return }
    sendRGBNameEvent(value)
    String descriptionText = " hue was set to ${value}%"
    logInfo "${descriptionText}"
    sendEvent(name:'hue', value:value, descriptionText:descriptionText, unit: '%')
}

/* groovylint-disable-next-line UnusedPrivateMethodParameter */
private void sendSaturationEvent(String rawValue, Boolean presetColor = false) {
    Integer value = hex254ToInt100(rawValue)
    if (value == device.currentValue('saturation')) { return }
    sendRGBNameEvent(null, value)
    String descriptionText = " saturation was set to ${value}%"
    logInfo "${descriptionText}"
    sendEvent(name:'saturation', value:value, descriptionText:descriptionText, unit: '%')
}

/* groovylint-disable-next-line UnusedPrivateMethodParameter */
private void sendCTEvent(String rawValue, Boolean presetColor = false) {
    Integer value = (1000000/(hexStrToUnsignedInt(rawValue))).toInteger()
    String descriptionText = "${device.displayName} ColorTemp was set to ${value}K"
    if (txtEnable) log.info descriptionText
    sendEvent(name:"colorTemperature", value:value, descriptionText:descriptionText, unit: "K")

}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedPrivateMethodParameter */
private void sendRGBNameEvent(hue, sat = null) {
    String genericName
    if (device.currentValue('saturation') == 0) {
        genericName = 'White'
    } else if (hue == null) {
        return
    } else {
        genericName = colorRGBName.find { k , v -> hue < k }.value
    }
    if (genericName == device.currentValue('colorName')) { return }
    String descriptionText = " color is ${genericName}"
    logInfo "${descriptionText}"
    sendEvent(name: 'colorName', value: genericName ,descriptionText: descriptionText)
}

//capability commands
void on() {
    logDebug 'switching on()'
    sendToDevice(matter.on())
}

void off() {
    logDebug 'switching off()'
    sendToDevice(matter.off())
}

void setLevel(Object value) {
    logDebug "setLevel(${value})"
    setLevel(value, transitionTime ?: 1)
}

void setLevel(Object value, Object rate) {
    logDebug "setLevel(${value}, ${rate})"
    Integer level = value.toInteger()
    if (level == 0 && device.currentValue('switch') == 'off') { return }
    sendToDevice(matter.setLevel(level, rate.toInteger()))
}

void setHue(Object value) {
    logDebug "setHue(${value})"
    List<String> cmds = []
    Integer transitionTime = (rgbTransitionTime ?: 1).toInteger()
    if (device.currentValue('switch') == 'on') {
        cmds.add(matter.setHue(value.toInteger(), transitionTime))
    } else {
        cmds.add(matter.on())
        cmds.add(matter.setHue(value.toInteger(), transitionTime))
    }
    sendToDevice(cmds)
}

void setSaturation(Object value) {
    logDebug "setSaturation(${value})"
    List<String> cmds = []
    Integer transitionTime = (rgbTransitionTime ?: 1).toInteger()
    if (device.currentValue('switch') == 'on') {
        cmds.add(matter.setSaturation(value.toInteger(), transitionTime))
    } else {
        cmds.add(matter.on())
        cmds.add(matter.setSaturation(value.toInteger(), transitionTime))
    }
    sendToDevice(cmds)
}

void setHueSat(Object hue, Object sat) {
    logDebug "setHueSat(${hue}, ${sat})"
    List<String> cmds = []
    Integer transitionTime = (rgbTransitionTime ?: 1).toInteger()
    if (device.currentValue('switch') == 'on') {
        cmds.add(matter.setHue(hue.toInteger(), transitionTime))
        cmds.add(matter.setSaturation(sat.toInteger(), transitionTime))
    } else {
        cmds.add(matter.on())
        cmds.add(matter.setHue(hue.toInteger(), transitionTime))
        cmds.add(matter.setSaturation(sat.toInteger(), transitionTime))
    }
    sendToDevice(cmds)
}

void setColorTemperature(colortemperature, transitionTime=null) {
    if (logEnable) log.debug "setcolortemp(${colortemperature})"
    List<String> cmds = []
    if (device.currentValue("switch") == "on"){
        cmds.add(matter.setColorTemperature(colortemperature.toInteger(), transitionTime))
    } else {
        cmds.add(matter.on())
        cmds.add(matter.setColorTemperature(colortemperature.toInteger(), transitionTime))
    }
    sendToDevice(cmds)
}

void setColor(Map colorMap) {
    logDebug "setColor(${colorMap})"
    if (colorMap.level) {
        setLevel(colorMap.level)
    }
    if (colorMap.hue != null && colorMap.saturation != null) {
        setHueSat(colorMap.hue, colorMap.saturation)
    } else if (colorMap.hue != null) {
        setHue(colorMap.hue)
    } else if (colorMap.saturation != null) {
        setSaturation(colorMap.saturation)
    }
}

void configure() {
    log.warn 'configure...'
    sendToDevice(subscribeCmd())
}

//lifecycle commands
void updated() {
    log.info 'updated...'
    checkDriverVersion()
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) { runIn(86400, logsOff) }   // 24 hours

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        // schedule the periodic timer
        final int interval = (settings.healthCheckInterval as Integer) ?: 0
        if (interval > 0) {
            //log.trace "healthMethod=${healthMethod} interval=${interval}"
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method"
            scheduleDeviceHealthCheck(interval, healthMethod)
        }
    }
    else {
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod
        log.info 'Health Check is disabled!'
    }
}

void initialize() {
    if (state.deviceType == null) {
        log.warn 'initialize()...'
        initializeVars(fullInit = true)
    }
    sendToDevice(subscribeCmd())
}

void refresh() {
    logDebug 'refresh()'
    checkDriverVersion()
    sendToDevice(refreshCmd())
}

String refreshCmd() {
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x0000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0008, 0x0000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0300, 0x0000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0300, 0x0001))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0300, 0x0007))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0300, 0x0008))

    String cmd = matter.readAttributes(attributePaths)
    return cmd
}

String subscribeCmd() {
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0x01, 0x0006, 0x00))
    attributePaths.add(matter.attributePath(0x01, 0x0008, 0x00))
    attributePaths.add(matter.attributePath(0x01, 0x0300, 0x00))
    attributePaths.add(matter.attributePath(0x01, 0x0300, 0x01))
    attributePaths.add(matter.attributePath(0x01, 0x0300, 0x07))
    attributePaths.add(matter.attributePath(0x01, 0x0300, 0x08))

    //standard 0 reporting interval is way too busy for bulbs
    String cmd = matter.subscribe(5, 0xFFFF, attributePaths)
    return cmd
}

void logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

Integer hex254ToInt100(String value) {
    return Math.round(hexStrToUnsignedInt(value) / 2.54)
}

String int100ToHex254(value) {
    return intToHexStr(Math.round(value * 2.54))
}

Integer getLuxValue(rawValue) {
    return Math.max((Math.pow(10, (rawValue / 10000)) - 1).toInteger(), 1)
}

void sendToDevice(List<String> cmds, Integer delay = 300) {
    logDebug "sendToDevice (List): (${cmd})"
    if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.MATTER))
}

/* groovylint-disable-next-line UnusedMethodParameter */
void sendToDevice(String cmd, Integer delay = 300) {
    logDebug "sendToDevice (String): (${cmd})"
    if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.MATTER))
}

List<String> commands(List<String> cmds, Integer delay = 300) {
    return delayBetween(cmds.collect { it }, delay)
}

/* =================================================================================== */

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }
void clearInfoEvent()      { sendInfoEvent('clear') }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(fullInit = false)
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
    // no driver version change
    }
}

// credits @thebearmay
String getModel() {
    try {
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */
        String model = getHubVersion() // requires >=2.2.8.141
    } catch (ignore) {
        try {
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res ->
                model = res.data.device.modelName
                return model
            }
        } catch (ignore_again) {
            return ''
        }
    }
}
void sendInfoEvent(String info=null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: 'Status', value: 'clear', isDigital: true)
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, isDigital: true)
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute
    }
}

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2)  {
        String cron = getCron(intervalMins * 60)
        schedule(cron, 'deviceHealthCheck')
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes"
    }
    else {
        logWarn 'deviceHealthCheck is not scheduled!'
        unschedule('deviceHealthCheck')
    }
}

private void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    device.deleteCurrentState('healthStatus')
    logWarn 'device health check is disabled!'
}

// called when any event was received from the device in the parse() method.
void setHealthStatusOnline() {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (((device.currentValue('healthStatus') ?: 'unknown') != 'online')) {
        sendHealthStatusEvent('online')
        logInfo 'is now online!'
    }
}

void deviceHealthCheck() {
    checkDriverVersion()
    if (state.health == null) { state.health = [:] }
    Integer ctr = state.health['checkCtr3'] ?: 0
    logDebug "deviceHealthCheck: checkCtr3=${ctr}"
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline') {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck: online (notPresentCounter=${ctr})"
    }
    if (((settings.healthCheckMethod as Integer) ?: 0) == 2) { //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        ping()
    }
    state.health['checkCtr3'] = ctr + 1
}

void sendHealthStatusEvent(value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

String getCron(timeInSeconds) {
    final Random rnd = new Random()
    int minutes = (timeInSeconds / 60) as int
    int hours = (minutes / 60) as int
    if (hours > 23) { hours = 23 }
    String cron
    if (timeInSeconds < 60) {
        cron = "*/$timeInSeconds * * * * ? *"
    }
    else {
        if (minutes < 60) {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"
        }
        else {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"
        }
    }
    return cron
}

void ping() {
    if (state.lastTx != null) { state.lastTx['pingTime'] = new Date().getTime() } else { state.lastTx = [:] }
    if (state.states != null) { state.states['isPing'] = true } else { state.states = [:] }
    scheduleCommandTimeoutCheck()

    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4000))
    String cmd = matter.readAttributes(attributePaths)
    sendToDevice(cmd)
    logDebug 'ping...'
}

void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent('timeout')
    if (state.stats != null) { state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 } else { state.stats = [:] }
}

void sendRttEvent(String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null) { state.lastTx = [:] }
    Integer timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true)
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true)
    }
}

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model') } ${device.getDataValue('manufacturer') }) (${getModel()} ${location.hub.firmwareVersionString}) " }

String getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
}

void resetStats() {
    logDebug 'resetStats...'
    state.stats = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.health = [:]
    state.stats['rxCtr'] = 0
    state.stats['txCtr'] = 0
    state.states['isDigital'] = false
    state.states['isRefresh'] = false
    state.health['offlineCtr'] = 0
    state.health['checkCtr3'] = 0
}

void initializeVars(boolean fullInit = false) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true) {
        state.clear()
        unschedule()
        resetStats()
        state.comment = 'Works with Nanoleaf Matter bulb'
        logInfo 'all states and scheduled jobs cleared!'
        state.driverVersion = driverVersionAndTimeStamp()
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}"
        state.deviceType = DEVICE_TYPE
        sendInfoEvent('Initialized')
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', true) }
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }
}

static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

static Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

@Field static final int ROLLING_AVERAGE_N = 10
double approxRollingAverage(double avg, double newSample) {
    Double tempAvg = avg
    if (tempAvg == null || tempAvg == 0) { tempAvg = newSample }
    tempAvg -= tempAvg / ROLLING_AVERAGE_N
    tempAvg += newSample / ROLLING_AVERAGE_N
    // TODO: try Method II : New average = old average * (n-1)/n + new value /n
    return tempAvg
}

void logDebug(msg) {
    if (settings.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(msg) {
    if (settings.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(msg) {
    if (settings.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void logTrace(msg) {
    if (settings.traceEnable) {
        log.trace "${device.displayName} " + msg
    }
}

void parseTest(par) {
    log.warn "parseTest(${par})"
    parse(par)
}

void test(par) {
    log.warn "test... ${par}"
/*
    def cmd = ["he invoke 0x01 0x0006 0x0001 {1518}"]
    logDebug "sending cmd: ${cmd}"
    sendToDevice(cmd)
    */

    log.warn "version = ${driverVersionAndTimeStamp()} getDeviceInfo = ${getDeviceInfo()}"
}
