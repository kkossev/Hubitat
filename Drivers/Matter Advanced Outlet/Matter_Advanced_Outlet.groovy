/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, LineLength, MethodCount, MethodParameterTypeRequired, MethodSize, NoDef, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter */
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
 * ver. 1.0.1  2023-12-23 kkossev  - Inital version; added onOff stats; added toggle(); commented out the initialize() and configure() capabilities because of duplicated subscriptions
 *
 *                                   TODO: add poweer meter
 *                                   TODO: add flashRate preference; add flash() command
 *                                   TODO: isDigital isRefresh
 *                                   TODO: add flashOnce()
 *                                   TODO: add powerOnBehavior
 *                                   TODO: add offlineCtr in stats
 */

static String version() { '1.0.1' }
static String timeStamp() { '2023/12/23 9:16 AM' }

@Field static final Boolean _DEBUG = false
@Field static final String   DEVICE_TYPE = 'MATTER_OUTLET'
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final String  UNKNOWN = 'UNKNOWN'

import groovy.transform.Field

metadata {
    definition(name: 'Matter Advanced Outlet', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Actuator'
        capability 'Sensor'
        capability 'Outlet'
        capability 'Switch'
        capability 'Power Meter'
        //capability 'Configuration'
        //capability 'Initialize'
        capability 'Refresh'
        capability 'Health Check'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'

        command 'toggle'

        if (_DEBUG) {
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
        }
        // fingerprints are commented out, because are already included in the stock driver
        // fingerprint endpointId:'01', inClusters:'001D,0003,0004,0005,0006', outClusters:'', model:'S4', manufacturer:'Onvis', controllerType:'MAT'      // Onvis plug                                          // Onvis Smart Plug SP120
    }
    preferences {
        input(name:'txtEnable', type:'bool', title:'Enable descriptionText logging', defaultValue:true)
        input(name:'logEnable', type:'bool', title:'Enable debug logging', defaultValue:true)
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
@Field static final Map StartUpOnOffEnumOpts = [0: 'Off', 1: 'On', 2: 'Toggle']

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
        case '0006' :   // EventList             on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
            int value
            switch (descMap.attrId) {
                case '0000' : // Switch
                    sendSwitchEvent(descMap.value)
                    break
                case '4000' : // GlobalSceneControl
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
                        if (logEnable) { logInfo "parse: Switch: GlobalSceneControl = ${descMap.value}" }
                        if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['GlobalSceneControl'] = descMap.value
                    }
                    break
                case '4001' : // OnTime
                    if (logEnable) { logInfo  "parse: Switch: OnTime = ${descMap.value}" }
                    if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['OnTime'] = descMap.value
                    break
                case '4002' : // OffWaitTime
                    if (logEnable) { logInfo  "parse: Switch: OffWaitTime = ${descMap.value}" }
                    if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['OffWaitTime'] = descMap.value
                    break
                case '4003' : // StartUpOnOff
                    value = descMap.value as int
                    String startUpOnOffText = "parse: Switch: StartUpOnOff = ${descMap.value} (${StartUpOnOffEnumOpts[value] ?: UNKNOWN})"
                    if (logEnable) { logInfo  "${startUpOnOffText}" }
                    if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['StartUpOnOff'] = descMap.value
                    break
                case 'FFF8' : // GeneratedCommandList
                    if (logEnable) { logInfo  "parse: Switch: GeneratedCommandList = ${descMap.value}" }
                    break
                case 'FFF9' : // AceptedCommandList
                    if (logEnable) { logInfo  "parse: Switch: AceptedCommandList = ${descMap.value}" }
                    break
                case 'FFFA' : // EventList
                    if (logEnable) { logInfo  "parse: Switch: EventList = ${descMap.value}" }
                    break
                case 'FFFB' : // AttributeList
                    if (logEnable) { logInfo  "parse: Switch: AttributeList = ${descMap.value}" }
                    break
                case 'FFFC' : // FeatureMap
                    value = descMap.value as int
                    String featureMapText = "parse: Switch: FeatureMap = ${descMap.value}"
                    /* groovylint-disable-next-line BitwiseOperatorInConditional */
                    if ((value & 0x01) != 0) { featureMapText += ' (Feature: Lighting)' }
                    /* groovylint-disable-next-line BitwiseOperatorInConditional */
                    if ((value & 0x02) != 0) { featureMapText += ' (Feature: DeadFrontBehaviour)' }
                    if (logEnable) { logInfo "$featureMapText" }
                    if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['featureMap'] = descMap.value
                    break
                case 'FFFD' : // ClusterRevision
                    if (logEnable) { logInfo  "parse: Switch: ClusterRevision = ${descMap.value}" }
                    break
                case 'FE' : // FabricIndex
                    if (logEnable) { logInfo  "parse: Switch: FabricIndex = ${descMap.value}" }
                    break
                default :
                    logWarn "parse: skipped switch, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        default :
                logWarn "parse: skipped:${descMap}"
    }
}

//events
private void sendSwitchEvent(String rawValue) {
    String value = rawValue == '01' ? 'on' : 'off'
    if (device.currentValue('switch') == value) { 
        logDebug "ignored duplicated switch event, value:${value}"
        return 
    }
    String descriptionText = " was turned ${value}"
    logInfo "${descriptionText}"
    sendEvent(name:'switch', value:value, descriptionText:descriptionText)
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

void toggle() {
    logDebug 'toggling...'
    String cmd = "he invoke 0x01 0x0006 0x0002 {1518}"
    sendToDevice(cmd)
}

void configure() {
    log.warn 'configure...'
    //sendToDevice(subscribeCmd())
    logWarn 'subscribeCmd creates duplicate events - skipped for now!'
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
    logDebug 'initialize()...'
    if (state.deviceType == null) {
        log.warn 'initialize(fullInit = true))...'
        initializeVars(fullInit = true)
    }
    runIn(1, sendSubscribeCmd)
    //runIn(20, sendUnsubscribeCmd)
    //logWarn 'subscribeCmd creates duplicate events - skipped for now!'
}

// creates duplicate events - do not use!
void sendSubscribeCmd() {
    logDebug 'sendSubscribeCmd()'
    sendToDevice(subscribeCmd())
}

// creates endledess loop - do not use!
void sendUnsubscribeCmd() {
    logDebug 'sendUnsubscribeCmd()'
    sendToDevice(unsubscribeCmd())
}

void refresh() {
    logDebug 'refresh()'
    checkDriverVersion()
    sendToDevice(refreshCmd())
}

String refreshCmd() {   //   on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x0000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4001))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4002))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4003))
    int cluster = 0x0006
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFD)) // ClusterRevision       on/off: value:04
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFC)) // FeatureMap            on/off: value:01
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFB)) // AttributeList         on/off: (no response)
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFA)) // EventList             on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF9)) // AceptedCommandList    on/off: value:[00, 01, 02, 40, 41, 42]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF8)) // GeneratedCommandList  on/off: value:1618
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFE))   // FabricIndex           on/off: (no response)
    String cmd = matter.readAttributes(attributePaths)
    return cmd
}

String unsubscribeCmd() {
    logDebug 'unsubscribe()'
    /*
    String cmd = matter.unsubscribe()
    return cmd
    */
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0x01, 0x0006, 0x00))
    //standard 0 reporting interval is way too busy for bulbs
    String cmd = matter.unsubscribe()
    return cmd    
}

String subscribeCmd() {
    logDebug 'subscribe()'
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0x01, 0x0006, 0x00))
    //standard 0 reporting interval is way too busy for bulbs
    String cmd = matter.subscribe(5, 0xFFFF, attributePaths)
    return cmd
}

void logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

void sendToDevice(List<String> cmds, Integer delay = 300) {
    logDebug "sendToDevice (List): (${cmds})"
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
    state.onOff  = [:]  // this driver specific
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
        state.comment = 'Works with Onvis Matter plug'
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
    List<Map<String, String>> attributePaths = []
    int cluster = 0x0006
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFD)) // ClusterRevision       on/off: value:04
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFC)) // FeatureMap            on/off: value:01
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFB)) // AttributeList         on/off: (no response)
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFA)) // EventList             on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF9)) // AceptedCommandList    on/off: value:[00, 01, 02, 40, 41, 42]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF8)) // GeneratedCommandList  on/off: value:1618
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFE))   // FabricIndex           on/off: (no response)

    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0x0000))   // IdentifyTime   value:00
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0x0001))   // IdentifyType   value:02

    String cmd = matter.readAttributes(attributePaths)
    sendToDevice(cmd)
*/
    String cmd = matter.unsubscribe()
    sendToDevice(cmd)
}
