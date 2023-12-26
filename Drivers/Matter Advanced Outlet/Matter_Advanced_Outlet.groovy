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
 * ver. 1.0.2  2023-12-26 kkossev  - (dev. branch) added getInfo command; fixed the refresh() command for MATTER_OUTLET; added isDigital isRefresh; use the Basic cluster attr. 0 for ping()
 *
 *                                   TODO: add initializeCtr; disable sending commands for 30 seconds after initialize() call
 *                                   TODO: add power meter handling
 *                                   TODO: add flashRate preference; add flash() command
 *                                   TODO: add flashOnce()
 *                                   TODO: add powerOnBehavior
 *                                   TODO: add offlineCtr in stats
 */

static String version() { '1.0.2' }
static String timeStamp() { '2023/12/26 1:57 PM' }

@Field static final Boolean _DEBUG = false
@Field static final String  DEVICE_TYPE = 'MATTER_OUTLET'
@Field static final Integer DIGITAL_TIMER = 3000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final String  UNKNOWN = 'UNKNOWN'

import groovy.transform.Field
import hubitat.helper.HexUtils

metadata {
    definition(name: 'Matter Advanced Outlet', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Actuator'
        capability 'Sensor'
        capability 'Outlet'
        capability 'Switch'
        capability 'Power Meter'
        //capability 'ContactSensor'
        //capability 'Configuration'
        //capability 'Initialize'
        capability 'Refresh'
        capability 'Health Check'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'

        command 'toggle'
        command 'getInfo'
        //command 'identify'
        //command 'unsubscribe'

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

    Map descMap
    try {
        descMap = matter.parseDescriptionAsMap(description)
    } catch (e) {
        logWarn "parse: exception ${e} <br> Failed to parse description: ${description}"
        return
    }
    logDebug "parse: descMap:${descMap}  description:${description}"
    if (descMap == null) {
        logWarn "parse: descMap is null description:${description}"
        return
    }
    if (descMap.attrId == 'FFFB') { // parse the AttributeList first!
        pareseAttributeList(descMap)
        return
    }
    switch (descMap.cluster) {
        case '0000' :
            if (descMap.attrId == '4000') { //software build ?
                updateDataValue('softwareBuild', descMap.value ?: 'unknown')
            }
            else {
                logWarn "skipped softwareBuild, attribute:${descMap.attrId}, value:${descMap.value}"
            }
            break
        case '0003' :   // Identify
            gatherAttributesValuesInfo(descMap, IdentifyClusterAttributes)
            break
        case '0004' :   // Groups
            gatherAttributesValuesInfo(descMap, GroupsClusterAttributes)
            break
        case '0005' :   // Scenes
            gatherAttributesValuesInfo(descMap, ScenesClusterAttributes)
        case '0006' :   // On/Off Cluster
            parseOnOffCluster(descMap)
            break
        case '001D' :  // Descriptor, ep:00
            gatherAttributesValuesInfo(descMap, DescriptorClusterAttributes)
            break
        case '002F' :  // PowerConfiguration    //  parse: descMap:[endpoint:02, cluster:002F, attrId:000C, value:C8, clusterInt:47, attrInt:12] description:read attr - endpoint: 02, cluster: 002F, attrId: 000C, value: 04C8
            gatherAttributesValuesInfo(descMap, PowerConfigurationClusterAttributes)
            break
        case '0028' :  // BasicInformation, ep:00
            gatherAttributesValuesInfo(descMap, BasicInformationClusterAttributes)
            break
        case '0045' :  // BooleanState
            gatherAttributesValuesInfo(descMap, BoleanStateClusterAttributes)
            sendContactEvent(descMap.value)
            break
        default :
                logWarn "parse: skipped:${descMap}"
    }
}

// AttributeList 0xFFFB
void pareseAttributeList(Map descMap) {
    logDebug "pareseAttributeList: descMap:${descMap}"
    Integer cluster  = descMap.clusterInt  as Integer
    String stateName = '0x' + HexUtils.integerToHexString(cluster, 2)
    if (state.matter == null) { state.matter = [:] }
    state.matter[stateName] = descMap.value
    logDebug "pareseAttributeList: state.matter[$stateName] = ${descMap.value}"
}

void gatherAttributesValuesInfo(Map descMap, Map knownClusterAttributes) {
    Integer attrInt = descMap.attrInt as Integer
    String  attrName = knownClusterAttributes[attrInt]
    if (attrName == null) {
        attrName = GlobalElementsAttributes[attrInt]
    }
    logDebug "gatherAttributesValuesInfo: attrInt:${attrInt} attrName:${attrName} value:${descMap.value}"
    if (attrName == null) {
        logWarn "gatherAttributesValuesInfo: unknown attribute # ${attrInt}"
        return
    }
    if (state.states['isInfo'] == true) {
        logDebug "gatherAttributesValuesInfo: isInfo:${state.states['isInfo']} state.states['cluster'] = ${state.states['cluster']} "
        if (state.states['cluster'] == descMap.cluster) {
            if (descMap.value != null && descMap.value != "") {
                String tmp = "(${descMap.attrId}) ${attrName}"
                // check if tmp is not already in the state.tmp
                if (tmp in state.tmp) {
                    logWarn "gatherAttributesValuesInfo: tmp:${tmp} is already in the state.tmp"
                    return
                }
                if (logEnable) { logInfo "$tmp" }
                state.tmp = (state.tmp ?: '') + "${tmp} = ${descMap.value} " + '<br>'
            }
        }
    }
    else if ((state.states['isPing'] ?: false) == true && descMap.cluster == '0028' && descMap.attrId == '0000') {
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
        //logDebug "gatherAttributesValuesInfo: isInfo:${state.states['isInfo']} descMap:${descMap}"
    }
}


void parseOnOffCluster(Map descMap) {
    logDebug "parseOnOffCluster: descMap:${descMap}"
    Integer attrInt = Integer.parseInt(descMap.attrId, 16)
    String attrName = OnOffClusterAttributes[attrInt]
    String globalAttrName = GlobalElementsAttributes[attrInt]
    if (attrName == null && globalAttrName == null) {
        logWarn "parseOnOffCluster: unknown:${descMap}"
        return
    }
    if (state.states['isInfo'] == true) {
        if (descMap.value != null && descMap.value != "") {
            String tmp = "${attrName} = ${descMap.value} " 
            if (logEnable) { logInfo "$tmp" }
            state.tmp = (state.tmp ?: '') + tmp + '<br>'
        }
    }

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
            if (logEnable) { logWarn  "parse: Switch: should be already paresed! AttributeList = ${descMap.value}" }
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

}

//events
private void sendSwitchEvent(String rawValue, isDigital = false) {
    String value = rawValue == '01' ? 'on' : 'off'
    String descriptionText = "switch was turned ${value}"
    Map eventMap = [name: 'switch', value: value, descriptionText: descriptionText, type: isDigital ? "digital" : "physical"]
    if (state.states["isRefresh"] == true) {
        eventMap.descriptionText = "switch is ${value} [refresh]"
        eventMap.isStateChange = true   // force the event to be sent
    }
    if (device.currentValue('switch') == value && state.states["isRefresh"] != true) {
        logDebug "ignored duplicated switch event, value:${value}"
        return
    }
    eventMap.descriptionText += (isDigital == true || state.states["isDigital"] == true )? " [digital]" : " [physical]"
    logInfo "${eventMap.descriptionText}"
    sendEvent(eventMap)
}

private void sendContactEvent(String rawValue) {
    String value = rawValue == '00' ? 'open' : 'closed'
    if (device.currentValue('contact') == value) {
        logDebug "ignored duplicated contact event, value:${value}"
        return
    }
    String descriptionText = " is ${value}"
    logInfo "${descriptionText}"
    sendEvent(name:'contact', value:value, descriptionText:descriptionText)
}

//capability commands
void on() {
    logDebug 'switching on()'
    setDigitalRequest()    // 3 seconds
    sendToDevice(matter.on())
}

void off() {
    logDebug 'switching off()'
    setDigitalRequest()
    sendToDevice(matter.off())
}

void toggle() {
    logDebug 'toggling...'
    setDigitalRequest()
    String cmd = matter.invoke(device.endpointId, 0x0006, 0x0002)
    sendToDevice(cmd)
}

void identify() {
    List<Map<String, String>> attributeWriteRequests = []
    attributeWriteRequests.add(matter.attributeWriteRequest(device.endpointId, 0x0003, 0x0000, 0x05, "0101"))
    String cmd = matter.writeAttributes(attributeWriteRequests)
    sendToDevice(cmd)
    
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(0x05, 0x00, "0101"))
    cmd = matter.invoke(device.endpointId, 0x0003, 0x0000, cmdFields)
    sendToDevice(cmd)
    
}    

void unsubscribe() {
    logWarn 'unsubscribe() ...'
    sendToDevice(matter.unsubscribe())
} 

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true ; runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }
void setDigitalRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isDigital'] = true ; runInMillis( DIGITAL_TIMER, clearDigitalRequest, [overwrite: true]) }                 // 3 seconds
void clearDigitalRequest() { if (state.states == null) { state.states = [:] } ; state.states['isDigital'] = false }

void logRequestedClusterAttrResult(Map data) {
    String clusterAtttr = "Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) attributes and values list"
    if (state.tmp != null) {
        logInfo "${clusterAtttr} : <br>${state.tmp}"
    } else {
        logInfo "${clusterAtttr} <b>timeout</b>! :("
    }
    state.tmp = null
    if (state.states == null) { state.states = [:] }
    state.states['isInfo'] = false
    state.states['cluster'] = null
}

void requestMatterClusterAttributesList(Map data) {
    if (state.states == null) { state.states = [:] }
    state.states['isInfo'] = true
    state.states['cluster'] = HexUtils.integerToHexString(data.cluster, 2)
    state.tmp = null
    Integer endpoint = data.endpoint as Integer
    Integer cluster  = data.cluster  as Integer
    logInfo "Requesting Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint ${HexUtils.integerToHexString(endpoint, 1)} attributes list ..."
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, 0xFFFB as Integer))
    sendToDevice(matter.readAttributes(attributePaths))
}

void requestMatterClusterAttributesValues(Map data) {
    Integer endpoint = data.endpoint as Integer
    Integer cluster  = data.cluster  as Integer
    String stateName = '0x' + HexUtils.integerToHexString(cluster, 2)
    if (state.matter == null) { state.matter = [:] }
    String attrListString = state.matter["$stateName"] as String
    logInfo "Requesting Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint ${HexUtils.integerToHexString(endpoint, 1)} attributes values ..."
    if (attrListString == null) {
        logWarn "requestMatterClusterAttributesValues: attrListString is null"
        return
    }   
    attrListString = attrListString.substring(1, attrListString.length() - 1)                   // remove the [] brackets
    List<Integer> attrList = attrListString.split(',').collect { HexUtils.hexStringToInt(it) }  // convert the string to a list of integers
    logDebug "requestMatterClusterAttributesValues: attrList:${attrList}"
    List<Map<String, String>> attributePaths = []
    attrList.each { attrInt ->
        attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrInt))
    }
    sendToDevice(matter.readAttributes(attributePaths))
}

void requestAndCollectAttributesValues(endpoint, cluster, time) {
    runIn(time ?: 1, requestMatterClusterAttributesList,    [overwrite: false, data: [endpoint:endpoint, cluster:cluster] ])
    runIn(time + 3,  requestMatterClusterAttributesValues,  [overwrite: false, data: [endpoint:endpoint, cluster:cluster] ])
    runIn(time + 12,  logRequestedClusterAttrResult,        [overwrite: false, data: [endpoint:endpoint, cluster:cluster] ])

}

void getInfo() {
    logDebug 'getInfo()'
    requestAndCollectAttributesValues(endpoint = 0, cluster = 0x001D, time = 1)    // Descriptor Cluster
    requestAndCollectAttributesValues(endpoint = 0, cluster = 0x0028, time = 15)   // Basic Information Cluster
    requestAndCollectAttributesValues(endpoint = device.endpointId, cluster = 0x0003, time = 30)   // Identify Cluster (results in initialize call)
    //requestAndCollectAttributesValues(endpoint=device.endpointId, cluster=0x0045, time=20)   // BooleanState Cluster
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
    logWarn 'initialize()...'
    if (state.deviceType == null) {
        log.warn 'initialize(fullInit = true))...'
        initializeVars(fullInit = true)
    }
    sendSubscribeCmd()
    //runIn(1, sendSubscribeCmd)
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
    logInfo'refresh() ...'
    checkDriverVersion()
    setRefreshRequest()    // 6 seconds
    sendToDevice(refreshCmd())
}

String refreshCmd() {   //   on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
    List<Map<String, String>> attributePaths = []
    if (state.deviceType == 'MATTER_OUTLET') {
        if (state.matter != null && state.matter['0x0006'] !=null) {
            String attrListString = state.matter['0x0006'] as String
            attrListString = attrListString.substring(1, attrListString.length() - 1)                   // remove the [] brackets
            List<Integer> attrList = attrListString.split(',').collect { HexUtils.hexStringToInt(it) }  // convert the string to a list of integers
            logDebug "refreshCmd: attrList:${attrList}"
            attrList.each { attrInt ->
                attributePaths.add(matter.attributePath(device.endpointId, 0x0006, attrInt))
            }
        }
        else {
            logWarn "refreshCmd: state.matter['0x0006'] is null"
        }

    }
/*

    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x0000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4000))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4001))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4002))
    attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x4003))
    int cluster = 0x001D
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFD)) // ClusterRevision       on/off: value:04
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFC)) // FeatureMap            on/off: value:01
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFB)) // AttributeList         on/off: (no response)
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFFA)) // EventList             on/off: :[FFF8, FFF9, FFFB, FFFC, FFFD, 00, 4000, 4001, 4002, 4003]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF9)) // AceptedCommandList    on/off: value:[00, 01, 02, 40, 41, 42]
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFFF8)) // GeneratedCommandList  on/off: value:1618
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 0xFE))   // FabricIndex           on/off: (no response)

    cluster = 0x001D
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 00)) // DeviceTypeList
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 01)) // ServerList
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 02)) // ClientList
    attributePaths.add(matter.attributePath(device.endpointId, cluster, 03)) // PartsList
*/

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
    List<String> inClusters = device.getDataValue('inClusters').split(',').collect { it.trim() }
    List<Map<String, String>> attributePaths = []
    String cmd = null
    if ('0006' in inClusters) {
        attributePaths.add(matter.attributePath(device.endpointId, 0x0006, 0x0000))
    }
    if ('0045' in inClusters) {
        attributePaths.add(matter.attributePath(device.endpointId, 0x0045, 0x0000))
    }
    if (attributePaths.size() > 0) {
        //standard 0 reporting interval is way too busy for bulbs
        cmd = matter.subscribe(5, 0xFFFF, attributePaths)
    }
    else {
        logWarn 'subscribeCmd: no attributes to subscribe!'
    }
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
    sendToDevice(pingCmd())
    logDebug 'ping...'
}

String pingCmd() {
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0, 0x0028, 0x00))   // Basic Information Cluster : DataModelRevision
    String cmd = matter.readAttributes(attributePaths)
    return cmd
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
    state.matter  = [:]
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
    log.debug "Matter cluster names = ${matter.getClusterNames()}"    // OK
    log.debug "Matter getClusterIdByName ${matter.getClusterIdByName('Identify')}"  // OK
    log.debug "Matter getClusterName(3) = ${matter.getClusterName(3)}"  // not OK - echoes back the cluster number?

}

/*
Matter cluster names = [$FaultInjection, $UnitTesting, $ElectricalMeasurement, $AccountLogin, $ApplicationBasic, $ApplicationLauncher, $AudioOutput, $ContentLauncher, $KeypadInput, $LowPower, $MediaInput, $MediaPlayback, $TargetNavigator, $Channel, $WakeOnLan, $RadonConcentrationMeasurement, $TotalVolatileOrganicCompoundsConcentrationMeasurement, $Pm10ConcentrationMeasurement, $Pm1ConcentrationMeasurement, $FormaldehydeConcentrationMeasurement, $Pm25ConcentrationMeasurement, $SodiumConcentrationMeasurement, $ChloroformConcentrationMeasurement, $ChlorodibromomethaneConcentrationMeasurement, $BromoformConcentrationMeasurement, $BromodichloromethaneConcentrationMeasurement, $SulfateConcentrationMeasurement, $ManganeseConcentrationMeasurement, $LeadConcentrationMeasurement, $CopperConcentrationMeasurement, $TurbidityConcentrationMeasurement, $TotalColiformBacteriaConcentrationMeasurement, $TotalTrihalomethanesConcentrationMeasurement, $HaloaceticAcidsConcentrationMeasurement, $FluorideConcentrationMeasurement, $FecalColiformEColiConcentrationMeasurement, $ChlorineConcentrationMeasurement, $ChloraminesConcentrationMeasurement, $BromateConcentrationMeasurement, $DissolvedOxygenConcentrationMeasurement, $SulfurDioxideConcentrationMeasurement, $OzoneConcentrationMeasurement, $OxygenConcentrationMeasurement, $NitrogenDioxideConcentrationMeasurement, $NitricOxideConcentrationMeasurement, $HydrogenSulfideConcentrationMeasurement, $HydrogenConcentrationMeasurement, $EthyleneOxideConcentrationMeasurement, $EthyleneConcentrationMeasurement, $CarbonDioxideConcentrationMeasurement, $CarbonMonoxideConcentrationMeasurement, $OccupancySensing, $RelativeHumidityMeasurement, $FlowMeasurement, $PressureMeasurement, $TemperatureMeasurement, $IlluminanceMeasurement, $BallastConfiguration, $ColorControl, $ThermostatUserInterfaceConfiguration, $FanControl, $Thermostat, $PumpConfigurationAndControl, $BarrierControl, $WindowCovering, $DoorLock, $TonerCartridgeMonitoring, $InkCartridgeMonitoring, $FuelTankMonitoring, $WaterTankMonitoring, $OzoneFilterMonitoring, $ZeoliteFilterMonitoring, $IonizingFilterMonitoring, $UvFilterMonitoring, $ElectrostaticFilterMonitoring, $CeramicFilterMonitoring, $ActivatedCarbonFilterMonitoring, $HepaFilterMonitoring, $RvcOperationalState, $OperationalState, $DishwasherAlarm, $SmokeCoAlarm, $AirQuality, $DishwasherMode, $RefrigeratorAlarm, $TemperatureControl, $RvcCleanMode, $RvcRunMode, $LaundryWasherControls, $RefrigeratorAndTemperatureControlledCabinetMode, $LaundryWasherMode, $ModeSelect, $IcdManagement, $BooleanState, $ProxyValid, $ProxyDiscovery, $ProxyConfiguration, $UserLabel, $FixedLabel, $GroupKeyManagement, $OperationalCredentials, $AdministratorCommissioning, $Switch, $BridgedDeviceBasicInformation, $TimeSynchronization, $EthernetNetworkDiagnostics, $WiFiNetworkDiagnostics, $ThreadNetworkDiagnostics, $SoftwareDiagnostics, $GeneralDiagnostics, $DiagnosticLogs, $NetworkCommissioning, $GeneralCommissioning, $PowerSource, $PowerSourceConfiguration, $UnitLocalization, $TimeFormatLocalization, $LocalizationConfiguration, $OtaSoftwareUpdateRequestor, $OtaSoftwareUpdateProvider, $BasicInformation, $Actions, $AccessControl, $Binding, $Descriptor, $PulseWidthModulation, $BinaryInputBasic, $LevelControl, $OnOffSwitchConfiguration, $OnOff, $Scenes, $Groups, $Identify]
*/

// https://github.com/project-chip/connectedhomeip/tree/master/src/app/clusters
@Field static final Map<Integer, String> MatterClusters = [
    0x001D  : 'Descriptor',                 // The Descriptor cluster is meant to replace the support from the Zigbee Device Object (ZDO) for describing a node, its endpoints and clusters
    0x001E  : 'Binding',                    // Meant to replace the support from the Zigbee Device Object (ZDO) for supportiprefriginatng the binding table.
    0x001F  : 'AccessControl',              // Exposes a data model view of a Node’s Access Control List (ACL), which codifies the rules used to manage and enforce Access Control for the Node’s endpoints and their associated cluster instances.
    0x0025  : 'Actions',                    // Provides a standardized way for a Node (typically a Bridge, but could be any Node) to expose information, commands, events ... 
    0x0028  : 'BasicInformation',           // Provides attributes and events for determining basic information about Nodes, which supports both Commissioning and operational determination of Node characteristics, such as Vendor ID, Product ID and serial number, which apply to the whole Node.
    0x0029  : 'OTASoftwareUpdateProvider',
    0x002A  : 'OTASoftwareUpdateRequestor',
    0x002B  : 'LocalizationConfiguration',  // Provides attributes for determining and configuring localization information
    0x002C  : 'TimeFormatLocalization',     // Provides attributes for determining and configuring time and date formatting information
    0x002D  : 'UnitLocalization',           // Provides attributes for determining and configuring the units
    0x002E  : 'PowerSourceConfiguration',   // Used to describe the configuration and capabilities of a Device’s power system
    0x002F  : 'PowerSource',                // Used to describe the configuration and capabilities of a physical power source that provides power to the Node
    0x0030  : 'GeneralCommissioning',       // Used to manage basic commissioning lifecycle
    0x0031  : 'NetworkCommissioning',       // Associates a Node with or manage a Node’s one or more network interfaces
    0x0032  : 'DiagnosticLogs',             // Provides commands for retrieving unstructured diagnostic logs from a Node that may be used to aid in diagnostics.
    0x0033  : 'GeneralDiagnostics',         // Provides a means to acquire standardized diagnostics metrics
    0x0034  : 'SoftwareDiagnostics',        // Provides a means to acquire standardized diagnostics metrics that MAY be used by a Node to assist a user or Administrator in diagnosing potential problems
    0x0035  : 'ThreadNetworkDiagnostics',   // Provides a means to acquire standardized diagnostics metrics that MAY be used by a Node to assist a user or Administrator in diagnosing potential problems
    0x0036  : 'WiFiNetworkDiagnostics',     // Provides a means to acquire standardized diagnostics metrics that MAY be used by a Node to assist a user or Administrator in diagnosing potential
    0x0037  : 'EthernetNetworkDiagnostics', // Provides a means to acquire standardized diagnostics metrics that MAY be used by a Node to assist a user or Administrator in diagnosing potential
    0x0038  : 'TimeSync',                   // Provides Attributes for reading a Node’s current time
    0x0039  : 'BridgedDeviceBasicInformation',  // Serves two purposes towards a Node communicating with a Bridge
    0x003C  : 'AdministratorCommissioning', // Used to trigger a Node to allow a new Administrator to commission it. It defines Attributes, Commands and Responses needed for this purpose.
    0x003E  : 'OperationalCredentials',     // Used to add or remove Node Operational credentials on a Commissionee or Node, as well as manage the associated Fabrics.
    0x003F  : 'GroupKeyManagement',         // Manages group keys for the node
    0x0040  : 'FixedLabel',                 // Provides a feature for the device to tag an endpoint with zero or more read only labels
    0x0041  : 'UserLabel',                  // Provides a feature to tag an endpoint with zero or more labels.
    0x0042  : 'ProxyConfiguration',         // Provides a means for a proxy-capable device to be told the set of Nodes it SHALL proxy
    0x0043  : 'ProxyDiscovery',             // Contains commands needed to do proxy discovery
    0x0044  : 'ValidProxies',               // Provides a means for a device to be told of the valid set of possible proxies that can proxy subscriptions on its behalf

    0x0003  : 'Identify',                   // Supports an endpoint identification state (e.g., flashing a light), that indicates to an observer (e.g., an installer) which of several nodes and/or endpoints it is.
    0x0004  : 'Groups',                     // Manages, per endpoint, the content of the node-wide Group Table that is part of the underlying interaction layer.
    0x0005  : 'Scenes',                     // Provides attributes and commands for setting up and recalling scenes.
    0x0006  : 'OnOff',                      // Attributes and commands for turning devices on and off.
    0x0008  : 'LevelControl',               // Provides an interface for controlling a characteristic of a device that can be set to a level, for example the brightness of a light, the degree of closure of a door, or the power output of a heater.
    0x001C  : 'LevelControlDerived',        // Derived cluster specifications are defined elsewhere.
    0x003B  : 'Switch',                     // Exposes interactions with a switch device, for the purpose of using those interactions by other devices
    0x0045  : 'BooleanState',               // Provides an interface to a boolean state.
    0x0050  : 'ModeSelect',                 // Provides an interface for controlling a characteristic of a device that can be set to one of several predefined values.
    0x0051  : 'LaundryWasherMode',          // Commands and attributes for controlling a laundry washer
    0x0052  : 'RefrigeratorAndTemperatureControlledCabinetMode',          // Commands and attributes for controlling a refrigerator or a temperature controlled cabinet
    0x0053  : 'LaundryWasherControls',      // Commands and attributes for the control of options on a device that does laundry washing
    0x0054  : 'RVCRunMode',                 // Commands and attributes for controlling the running mode of an RVC device.
    0x0055  : 'RVCCleanMode',               // Commands and attributes for controlling the cleaning mode of an RVC device.
    0x0056  : 'TemperatureControl',         // Commands and attributes for control of a temperature set point
    0x0057  : 'RefrigeratorAlarm',          // Alarm definitions for Refrigerator devices
    0x0059  : 'DishwasherMode',             // Commands and attributes for controlling a dishwasher
    0x005B  : 'AirQuality',                 // Provides an interface to air quality classification using distinct levels with human-readable labels.
    0x005C  : 'SmokeCOAlarm',               // Provides an interface for observing and managing the state of smoke and CO alarms
    0x005D  : 'DishwasherAlarm',            // Alarm definitions for Dishwasher devices
    0x0060  : 'OperationalState',           // Supports remotely monitoring and, where supported, changing the operational state of any device where a state machine is a part of the operation.
    0x0061  : 'RVCOperationalState',        // Commands and attributes for monitoring and controlling the operational state of an RVC device.
    0x0071  : 'HEPAFilterMonitoring',       // HEPA Filter
    0x0072  : 'ActivatedCarbonFilterMonitoring', // Activated Carbon Filter
    0x0101  : 'DoorLock',                   // An interface to a generic way to secure a door
    0x0102  : 'WindowCovering',             // Commands and attributes for controlling a window covering
    0x0200  : 'PumpConfigurationAndControl',// An interface for configuring and controlling pumps.
    0x0201  : 'Thermostat',                 // An interface for configuring and controlling the functionalty of a thermostat
    0x0202  : 'FanControl',                 // An interface for controlling a fan in a heating / cooling system
    0x0204  : 'ThermostatUserInterfaceConfiguration',                 // An interface for configuring the user interface of a thermostat (which MAY be remote from the thermostat)
    0x0300  : 'ColorControl',               // Attributes and commands for controlling the color of a color capable light.
    0x0301  : 'BallastConfiguration',       // Attributes and commands for configuring a lighting ballast
    0x0400  : 'IlluminanceMeasurement',     // Attributes and commands for configuring the measurement of illuminance, and reporting illuminance measurements
    0x0402  : 'TemperatureMeasurement',     // Attributes and commands for configuring the measurement of temperature, and reporting temperature measurements
    0x0403  : 'PressureMeasurement',        // Attributes and commands for configuring the measurement of pressure, and reporting pressure measurements
    0x0404  : 'FlowMeasurement',            // Attributes and commands for configuring the measurement of flow, and reporting flow rates
    0x0405  : 'RelativeHumidityMeasurement',// Supports configuring the measurement of relative humidity, and reporting relative humidity measurements of water in the air
    0x0406  : 'OccupancySensing',           // Occupancy sensing functionality, including configuration and provision of notifications of occupancy status
    0x0407  : 'LeafWetnessMeasurement',     // Percentage of water in the leaves of plants
    0x0408  : 'SoilMoistureMeasurement',    // Percentage of water in the soil
    0x040C  : 'CarbonMonoxideConcentrationMeasurement',
    0x040D  : 'CarbonDioxideConcentrationMeasurement',
    0x0413  : 'NitrogenDioxideConcentrationMeasurement',
    0x0415  : 'OzoneConcentrationMeasurement',
    0x042A  : 'PM2.5ConcentrationMeasurement',
    0x042B  : 'FormaldehydeConcentrationMeasurement',
    0x042C  : 'PM1ConcentrationMeasurement',
    0x042D  : 'PM10ConcentrationMeasurement',
    0x042E  : 'TotalVolatileOrganicCompoundsConcentrationMeasurement',
    0x042F  : 'RadonConcentrationMeasurement',
    0x0503  : 'WakeOnLAN',                  // interface for managing low power mode on a device that supports the Wake On LAN or Wake On Wireless LAN (WLAN) protocol
    0x0504  : 'Channel',                    // interface for controlling the current Channel on an endpoint.
    0x0505  : 'TargetNavigator',            // n interface for UX navigation within a set of targets on a Video Player device or Content App endpoint.
    0x0506  : 'MediaPlayback',              // interface for controlling Media Playback (PLAY, PAUSE, etc) on a Video Player device
    0x0507  : 'MediaInput',                 // interface for controlling the Input Selector on a Video Player device.
    0x0508  : 'LowPower',                   // interface for managing low power mode on a device.
    0x0509  : 'KeypadInput',                // interface for controlling a Video Player or a Content App using action commands such as UP, DOWN, and SELECT.
    0x050A  : 'ContentLauncher',            // interface for launching content on a Video Player device or a Content App.
    0x050B  : 'AudioOutput',                // interface for controlling the Output on a Video Player device.
    0x050E  : 'AccountLogin',               // interface for facilitating user account login on an application or a node.
    0x050C  : 'ApplicationLauncher',        // interface for launching content on a Video Player device.
    0x050D  : 'ApplicationBasic'            // information about a Content App running on a Video Player device which is represented as an endpoint
]

// 7.13. Global Elements
@Field static final Map<Integer, String> GlobalElementsAttributes = [
    0x00FE  : 'FabricIndex',
    0xFFF8  : 'GeneratedCommandList',
    0xFFF9  : 'AceptedCommandList',
    0xFFFA  : 'EventList',
    0xFFFB  : 'AttributeList',
    0xFFFC  : 'FeatureMap',
    0xFFFD  : 'ClusterRevision'
]

// 9.5. Descriptor Cluser 0x001D
@Field static final Map<Integer, String> DescriptorClusterAttributes = [
    0x0000  : 'DeviceTypeList',
    0x0001  : 'ServerList',
    0x0002  : 'ClientList',
    0x0003  : 'PartsList'
]

// 11.1.6.3. Attributes of the Basic Information Cluster 0x0028 ep=0
@Field static final Map<Integer, String> BasicInformationClusterAttributes = [
    0x0000  : 'DataModelRevision',
    0x0001  : 'VendorName',
    0x0002  : 'VendorID',
    0x0003  : 'ProductName',
    0x0004  : 'ProductID',
    0x0005  : 'NodeLabel',
    0x0006  : 'Location',
    0x0007  : 'HardwareVersion',
    0x0008  : 'HardwareVersionString',
    0x0009  : 'SoftwareVersion',
    0x000A  : 'SoftwareVersionString',
    0x000B  : 'ManufacturingDate',
    0x000C  : 'PartNumber',
    0x000D  : 'ProductURL',
    0x000E  : 'ProductLabel',
    0x000F  : 'SerialNumber',
    0x0010  : 'LocalConfigDisabled',
    0x0011  : 'Reachable',
    0x0012  : 'UniquieID',
    0x0013  : 'CapabilityMinima'
]

// Identify Cluster 0x0003
@Field static final Map<Integer, String> IdentifyClusterAttributes = [
    0x0000  : 'IdentifyTime',
    0x0001  : 'IdentifyType'
]

// Groups Cluster 0x0004
@Field static final Map<Integer, String> GroupsClusterAttributes = [
    0x0000  : 'NameSupport'
]

// Scenes Cluster 0x0005
@Field static final Map<Integer, String> ScenesClusterAttributes = [
    0x0000  : 'SceneCount',
    0x0001  : 'CurrentScene',
    0x0002  : 'CurrentGroup',
    0x0003  : 'SceneValid',
    0x0004  : 'RemainingCapacity'
]

// On/Off Cluser 0x0006
@Field static final Map<Integer, String> OnOffClusterAttributes = [
    0x0000  : 'Switch',
    0x4000  : 'GlobalSceneControl',
    0x4001  : 'OnTime',
    0x4002  : 'OffWaitTime',
    0x4003  : 'StartUpOnOff'
]

// 1.7 Bolean State Cluster 0x0045
@Field static final Map<Integer, String> BoleanStateClusterAttributes = [
    0x0000  : 'StateValue'
]
