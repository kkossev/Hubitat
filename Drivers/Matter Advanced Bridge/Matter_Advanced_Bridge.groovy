/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodParameterTypeRequired, MethodSize, NoDef, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter */
/**
 *  Matter Advanced Bridge - Device Driver for Hubitat Elevation
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
 * ver. 1.0.0  2023-12-29 kkossev  - Inital version;
 *
 *                                   TODO: Replace the scheduled jobs w/ StateMachine 
 *                                   TODO: GeneralDiagnostics (0x0033) endpoint 0x00 [0001] RebootCount = 06 [0002] UpTime = 0x000E22B4 (926388)  [0003] TotalOperationalHours = 0x0101 (257)
 */

static String version() { '1.0.0' }
static String timeStamp() { '2023/12/31 1:00 PM' }

@Field static final Boolean _DEBUG = true
@Field static final String  DEVICE_TYPE = 'MATTER_BRIDGE'
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
    definition(name: 'Matter Advanced Bridge', namespace: 'kkossev', author: 'Krassimir Kossev') {
        capability 'Actuator'
        capability 'Sensor'
        capability 'Initialize'
        capability 'Refresh'
        capability 'Health Check'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'Status', 'string'
        attribute 'productName', 'string'
        attribute 'bridgeSoftwareVersion', 'string'

        command 'getInfo', [
                [name:'infoType', type: 'ENUM', description: 'Bridge Info Type', constraints: ['Basic', 'Extended']],   // if the parameter name is 'type' - shows a drop-down list of the available drivers!
                [name:'endpoint', type: 'STRING', description: 'Endpoint', constraints: ['STRING']]
        ]
        command 'getPartsList'
        command 'identify'      // can't make it work ... :(
        command 'initialize', [[name: 'Invoked automatically during the hub reboot, do not click!']]
        command 'reSubscribe', [[name: 're-subscribe to the Matter controller events']]

        if (_DEBUG) {
            command 'readAttribute', [
                    [name:'endpoint',   type: 'STRING', description: 'Endpoint',  constraints: ['STRING']],
                    [name:'cluster',    type: 'STRING', description: 'Cluster',   constraints: ['STRING']],
                    [name:'attribute',  type: 'STRING', description: 'Attribute', constraints: ['STRING']]
            ]
            command 'subscribe', [
                    [name:'addOrRemove', type: 'ENUM', description: 'Select', constraints: ['Add', 'Remove', 'Show']],
                    [name:'endpoint',   type: 'STRING', description: 'Endpoint',  constraints: ['STRING']],
                    [name:'cluster',    type: 'STRING', description: 'Cluster',   constraints: ['STRING']],
                    [name:'attribute',  type: 'STRING', description: 'Attribute', constraints: ['STRING']]
            ]
            command 'unsubscribe'
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
        }
        // fingerprints are commented out, because are already included in the stock driver
        // fingerprint endpointId:"01", inClusters:"0003,001D", outClusters:"001E", model:"Aqara Hub E1", manufacturer:"Aqara", controllerType:"MAT"
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
    checkSubscriptionStatus()
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
        parseGlobalElement0xFFFB(descMap)
        return
    }
    switch (descMap.cluster) {
        case '0003' :   // Identify
            gatherAttributesValuesInfo(descMap, IdentifyClusterAttributes)
            break
        case '0006' :   // On/Off Cluster
            gatherAttributesValuesInfo(descMap, OnOffClusterAttributes)
            parseOnOffCluster(descMap)
            break
        case '001D' :  // Descriptor, ep:00
            gatherAttributesValuesInfo(descMap, DescriptorClusterAttributes)
            parseDescriptorCluster(descMap)
            break
        case '001E' :  // Binding, ep:00
            gatherAttributesValuesInfo(descMap, BindingClusterAttributes)
            break
        case '001F' :  // AccessControl, ep:00
            gatherAttributesValuesInfo(descMap, AccessControlClusterAttributes)
            break
        case '0028' :  // BasicInformation, ep:00
            gatherAttributesValuesInfo(descMap, BasicInformationClusterAttributes)
            parseBasicInformationCluster(descMap)
            break
        case '0029' :  // OTSA Software Update Provider, ep:00
            gatherAttributesValuesInfo(descMap, OTASoftwareUpdateProviderClusterAttributes)
            break
        case '002A' :  // OTA Software Update Requester, ep:00
            gatherAttributesValuesInfo(descMap, OTASoftwareUpdateRequestorClusterAttributes)
            break
        case '002B' :  // Localization Configuration, ep:00
            gatherAttributesValuesInfo(descMap, LocalizationConfigurationClusterAttributes)
            break
        case '002C' :  // TimeFormatLocalization, ep:00
            gatherAttributesValuesInfo(descMap, TimeFormatLocalizationClusterAttributes)
            break
        case '002E' :  // PowerSourceConfiguration, ep:00
            gatherAttributesValuesInfo(descMap, PowerSourceConfigurationClusterAttributes)
            break
        case '002F' :  // PowerSource, ep:02
            parseBatteryEvent(descMap)
            gatherAttributesValuesInfo(descMap, PowerSourceClusterAttributes)
            break
        case '0030' :  // GeneralCommissioning, ep:00
            gatherAttributesValuesInfo(descMap, GeneralCommissioningClusterAttributes)
            break
        case '0031' :  // NetworkCommissioning, ep:00
            gatherAttributesValuesInfo(descMap, NetworkCommissioningClusterAttributes)
            break
        case '0032' :  // DiagnosticLogs , ep:00
            gatherAttributesValuesInfo(descMap, DiagnosticLogsClusterAttributes)
            break
        case '0033' :  // GeneralDiagnostics, ep:00
            gatherAttributesValuesInfo(descMap, GeneralDiagnosticsClusterAttributes)
            break
        case '0034' :  // SoftwareDiagnostics, ep:00
            gatherAttributesValuesInfo(descMap, SoftwareDiagnosticsClusterAttributes)
            break
        case '0037' :  // EthernetNetworkDiagnostics, ep:00
            gatherAttributesValuesInfo(descMap, EthernetNetworkDiagnosticsClusterAttributes)
            break
        case '0039' :  // BridgedDeviceBasic
            parseBridgedDeviceBasic(descMap)
            gatherAttributesValuesInfo(descMap, BasicInformationClusterAttributes)
            break
        case '003C' :  // AdministratorCommissioning, ep:00
            gatherAttributesValuesInfo(descMap, AdministratorCommissioningClusterAttributes)
            break
        case '003E' :  // OperationalCredentials, ep:00
            gatherAttributesValuesInfo(descMap, OperationalCredentialsClusterAttributes)
            break
        case '003F' :  // GroupKeyManagement, ep:00
            gatherAttributesValuesInfo(descMap, GroupKeyManagementClusterAttributes)
            break
        case '0040' :  // FixedLabel, ep:00
            gatherAttributesValuesInfo(descMap, FixedLabelClusterAttributes)
            break
        case '0041' :  // UserLabel, ep:00
            gatherAttributesValuesInfo(descMap, UserLabelClusterAttributes)
            break
        case '0406' :  // OccupancySensing
            gatherAttributesValuesInfo(descMap, OccupancySensingClusterAttributes)
            parseOccupancySensing(descMap)
            break
        default :
            gatherAttributesValuesInfo(descMap, GlobalElementsAttributes)
            logWarn "parse: NOT PROCESSED: ${descMap}"
    }
}

void parseBatteryEvent(Map descMap) {
    logDebug "parseBatteryEvent: descMap:${descMap}"
    if (descMap.cluster != '002F') {
        logWarn "parseBatteryEvent: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    Integer value
    String descriptionText = ''
    Map eventMap = [:]
    switch (descMap.attrId) {
        case '0000' :   // Status
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "Battery status is: ${PowerSourceClusterStatus[value]} (raw:${descMap.value})"
            eventMap = [name: 'batteryStatus', value: PowerSourceClusterStatus[value], descriptionText: descriptionText]
            break
        case '000E' :   // BattChargeLevel
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "Battery charge level is: ${PowerSourceClusterBatteryChargeLevel[value]} (raw:${descMap.value})"
            eventMap = [name: 'batteryChargeLevel', value: PowerSourceClusterBatteryChargeLevel[value], descriptionText: descriptionText]
            break
        case '000B' :   // BatteryVoltage
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "Battery voltage is: ${value / 1000}V (raw:${descMap.value})"
            eventMap = [name: 'batteryVoltage', value: value / 1000, descriptionText: descriptionText]
            break
        case '000C' :   // BatteryPercentageRemaining
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "Battery percentage remaining is: ${value / 2}% (raw:${descMap.value})"
            eventMap = [name: 'battery', value: value / 2, descriptionText: descriptionText]
            break
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            if (logEnable) { logInfo "parseBatteryEvent: BasicInformation: ${attrName} = ${descMap.value}" }
            break
        default :
            logWarn "parseBatteryEvent: unexpected attrId:${descMap.attrId} (raw:${descMap.value})"
    }
    if (eventMap != [:]) {
        eventMap.type = 'physical'
        eventMap.isStateChange = true
        if (state.states['isRefresh'] == true) {
            eventMap.descriptionText += ' [refresh]'
        }
        /* groovylint-disable-next-line EmptyElseBlock */
        else {
            //log.debug "state.states['isRefresh'] = ${state.states['isRefresh']}"
        }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
}

String getClusterName(String cluster) {
    String clusterName = MatterClusters[HexUtils.hexStringToInt(cluster)]
    if (clusterName == null) { clusterName = UNKNOWN }
    return clusterName
}

String getAttributeName(String cluster, String attrId) {
    String attributeName = null
    String globalElementsAttrName = GlobalElementsAttributes[HexUtils.hexStringToInt(attrId)]
    String clusterName = getClusterName(cluster)
    // it is forbidden to use Eval.me() in Hubitat, so we have to use a workaround
    Map clusterAttributes = getAttributesMapByClusterId(cluster)
    attributeName = clusterAttributes ? clusterAttributes[HexUtils.hexStringToInt(attrId)] : null
    //logDebug "getAttributeName: cluster:${cluster} attrId:${attrId} clusterName:${clusterName} clusterAttributes:${clusterAttributes} attributeName:${attributeName} globalElementsAttrName:${globalElementsAttrName}"
    if (attributeName == null) { attributeName = globalElementsAttrName }
    return attributeName
}

void parseGlobalElement0xFFFB(final Map descMap) {
    String attributeName = getAttributeName(descMap.cluster, descMap.attrId)
    Integer cluster  = descMap.clusterInt  as Integer
    String stateName = '0x' + HexUtils.integerToHexString(cluster, 2)
    if (descMap.endpoint == '00') {
        if (state.bridgeDescriptor == null) { state.bridgeDescriptor = [:] }
        state.bridgeDescriptor[stateName] = descMap.value
        logDebug "parseGlobalElement0xFFFB: cluster: <b>${getClusterName(descMap.cluster)}</b> (0x${descMap.cluster}) attr: <b>${getAttributeName(descMap.cluster, descMap.attrId)}</b> (0x${descMap.attrId})  value:${descMap.value} stored in state.bridgeDescriptor[$stateName]"
    }
    else {
        String partName = "fingerprint${descMap.endpoint}"
        if (state[partName] == null) { state[partName] = [:] }
        state[partName][stateName] = descMap.value
        logDebug "parseGlobalElement0xFFFB: cluster: <b>${getClusterName(descMap.cluster)}</b> (0x${descMap.cluster}) attr: <b>${getAttributeName(descMap.cluster, descMap.attrId)}</b> (0x${descMap.attrId})  value:${descMap.value} stored in state[${partName}][${stateName}]"
    }
}

void gatherAttributesValuesInfo(final Map descMap, final Map knownClusterAttributes) {
    Integer attrInt = descMap.attrInt as Integer
    String  attrName = knownClusterAttributes[attrInt]
    Integer tempIntValue
    String  tmpStr
    if (attrName == null) {
        attrName = GlobalElementsAttributes[attrInt]
    }
    //logDebug "gatherAttributesValuesInfo: cluster:${descMap.cluster} attrInt:${attrInt} attrName:${attrName} value:${descMap.value}"
    if (attrName == null) {
        logWarn "gatherAttributesValuesInfo: unknown attribute # ${attrInt}"
        return
    }
    if (state.states['isInfo'] == true) {
        //logDebug "gatherAttributesValuesInfo: <b>isInfo:${state.states['isInfo']}</b> state.states['cluster'] = ${state.states['cluster']} "
        if (state.states['cluster'] == descMap.cluster) {
            if (descMap.value != null && descMap.value != '') {
                tmpStr = "[${descMap.attrId}] ${attrName}"
                if (tmpStr in state.tmp) {
                    logWarn "gatherAttributesValuesInfo: tmpStr:${tmpStr} is already in the state.tmp"
                    return
                }
                try {
                    tempIntValue = HexUtils.hexStringToInt(descMap.value)
                    if (tempIntValue >= 10) {
                        tmpStr += ' = 0x' + descMap.value + ' (' + tempIntValue + ')'
                    } else {
                        tmpStr += ' = ' + descMap.value
                    }
                } catch (e) {
                    tmpStr += ' = ' + descMap.value
                }
                if (logEnable) { logInfo "$tmpStr" }
                state.tmp = (state.tmp ?: '') + "${tmpStr} " + '<br>'
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
        } else {
            logWarn "unexpected ping timeRunning=${timeRunning} "
        }
        state.states['isPing'] = false
    }
    /* groovylint-disable-next-line EmptyElseBlock */
    else {
        //logDebug "gatherAttributesValuesInfo: isInfo:${state.states['isInfo']} descMap:${descMap}"
    }
}

void parseBasicInformationCluster(final Map descMap) {
    //logDebug "parseBasicInformationCluster: descMap:${descMap}"
    Map eventMap = [:]
    String attrName = BasicInformationClusterAttributes[descMap.attrInt as int] ?: GlobalElementsAttributes[descMap.attrInt as int] ?: UNKNOWN
    switch (descMap.attrId) {
        case '0003' : // productName
            eventMap = [name: 'productName', value:descMap.value, descriptionText: "productName is: ${descMap.value}"]
            break
        case '000A' : // softwareVersionString
            eventMap = [name: 'bridgeSoftwareVersion', value:descMap.value, descriptionText: "bridgeSoftwareVersion is: ${descMap.value}"]
            break
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            if (logEnable) { logInfo "parse: BasicInformation: ${attrName} = ${descMap.value}" }
            break
        default :
            if (descMap.attrId != '0000') { if (logEnable) { logInfo "parse: BasicInformation: ${attrName} = ${descMap.value}" } }
            break
    }

    if (eventMap != [:]) {
        eventMap.type = 'physical'; eventMap.isStateChange = true
        if (state.states['isRefresh'] == true) { eventMap.descriptionText += ' [refresh]' }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
}

void parseBridgedDeviceBasic(final Map descMap) {
    //logDebug "parseBridgedDeviceBasic: descMap:${descMap}"
    Map eventMap = [:]
    String attrName = BasicInformationClusterAttributes[descMap.attrInt as int] ?: GlobalElementsAttributes[descMap.attrInt as int] ?: UNKNOWN
    String endpointId = descMap.endpoint
    String fingerprintName = "fingerprint${endpointId}"
    if (endpointId == '00') { fingerprintName = 'bridgeDescriptor' }

    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }

    switch (descMap.attrId) {
        case '0001' :   // VendorName
        case '0003' :   // ProductName
        case '0011' :   // Reachable 
            state[fingerprintName][attrName] = descMap.value
            if (logEnable) { logInfo "parseBridgedDeviceBasic: ${attrName} = ${descMap.value}" }
            break
        default :
            if (descMap.attrId != '0000') { if (logEnable) { logInfo "parse: BasicInformation: ${attrName} = ${descMap.value}" } }
            break
    }

    if (eventMap != [:]) {
        eventMap.type = 'physical'; eventMap.isStateChange = true
        if (state.states['isRefresh'] == true) { eventMap.descriptionText += ' [refresh]' }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
}

void parseDescriptorCluster(final Map descMap) {
    logDebug "parseDescriptorCluster: descMap:${descMap}"
    String attrName = DescriptorClusterAttributes[descMap.attrInt as int] ?: GlobalElementsAttributes[descMap.attrInt as int] ?: UNKNOWN
    String endpointId = descMap.endpoint
    String fingerprintName = "fingerprint${endpointId}"
    if (endpointId == '00') { fingerprintName = 'bridgeDescriptor' }

    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }
/*
[0000] DeviceTypeList = [16, 1818]
[0001] ServerList = [03, 1D, 1F, 28, 29, 2A, 2B, 2C, 2E, 30, 31, 32, 33, 34, 37, 39, 3C, 3E, 3F, 40]
[0002] ClientList = [03, 1F, 29, 39]
[0003] PartsList = [01, 03, 04, 05, 06, 07, 08, 09, 0A, 0B, 0C, 0D, 0E, 0F, 10, 11]
*/
    switch (descMap.attrId) {
        case ['0000', '0001', '0002', '0003'] :
            state[fingerprintName][attrName] = descMap.value
            if (logEnable) { logInfo "parse: Descriptor: ${attrName} = ${descMap.value}" }
            break
        default :
            if (logEnable) { logInfo "parse: Descriptor: ${attrName} = ${descMap.value}" }
    }
}

void parseOnOffCluster(Map descMap) {
    logDebug "parseOnOffCluster: descMap:${descMap}"
    if (descMap.cluster != '0006') {
        logWarn "parseOnOffCluster: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    Integer attrInt = descMap.attrInt as Integer
    Integer value
    //String descriptionText = ''
    //Map eventMap = [:]
    String attrName = OnOffClusterAttributes[attrInt] ?: GlobalElementsAttributes[attrInt] ?: UNKNOWN

    switch (descMap.attrId) {
        case '0000' : // Switch
            sendSwitchEvent(descMap.value)
            break
        case '4000' : // GlobalSceneControl
            if (logEnable) { logInfo "parse: Switch: GlobalSceneControl = ${descMap.value}" }
            if (state.onOff  == null) { state.onOff =  [:] } ; state.onOff['GlobalSceneControl'] = descMap.value
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
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            if (logEnable) {
                logInfo "parse: Switch: ${attrName} = ${descMap.value}"
            }
            break
        default :
            logWarn "parseOnOffCluster: unexpected attrId:${descMap.attrId} (raw:${descMap.value})"
    }
    /*
    if (eventMap != [:]) {
        eventMap.type = 'physical'
        eventMap.isStateChange = true
        if (state.states['isRefresh'] == true) {
            eventMap.descriptionText += ' [refresh]'
        }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
    */
}

void parseOccupancySensing(Map descMap) {
    String attrName = OnOffClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN
    if (descMap.cluster != '0406') {
        logWarn "parseOccupancySensing: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    switch (descMap.attrId) {
        case '0000' : // Occupancy
            sendOccupancyEvent(descMap)
            break
        default :
            logDebug "parseOccupancySensing: ${(OnOffClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
            break
    }
}

//events
void sendOccupancyEvent(Map descMap) {
    String value = descMap.value == '01' ? 'active' : 'inactive'
    Map eventMap = [name: 'motion', value: value, descriptionText: "device #${descMap.endpoint} motion is ${value}", type: 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force the event to be sent
    }
    if (device.currentValue('motion') == value && state.states['isRefresh'] != true) {
        logDebug "ignored duplicated motion event, value:${value}"
        return
    }
    logInfo "${eventMap.descriptionText}"
    sendEvent(eventMap)
}

private void sendSwitchEvent(String rawValue, isDigital = false) {
    String value = rawValue == '01' ? 'on' : 'off'
    String descriptionText = "switch was turned ${value}"
    Map eventMap = [name: 'switch', value: value, descriptionText: descriptionText, type: isDigital ? 'digital' : 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText = "switch is ${value} [refresh]"
        eventMap.isStateChange = true   // force the event to be sent
    }
    if (device.currentValue('switch') == value && state.states['isRefresh'] != true) {
        logDebug "ignored duplicated switch event, value:${value}"
        return
    }
    eventMap.descriptionText += (isDigital == true || state.states['isDigital'] == true ) ? ' [digital]' : ' [physical]'
    logInfo "${eventMap.descriptionText}"
    sendEvent(eventMap)
}

//capability commands

void identify() {
    String cmd
    Integer time = 10
    //List<Map<String, String>> attributePaths = []
    //List<Map<String, String>> attributeWriteRequests = []
/*
    attributeWriteRequests.add(matter.attributeWriteRequest(device.endpointId, 0x0003, 0x0000, 0x05, zigbee.swapOctets(HexUtils.integerToHexString(time, 2))))
    cmd = matter.writeAttributes(attributeWriteRequests)
    sendToDevice(cmd)

    attributePaths.add(matter.attributePath(device.endpointId, 0x0003, 0x0000))     // IdentifyTime
    //attributePaths.add(matter.attributePath(device.endpointId, 0x0003, 0x0001))     // IdentifyType
    cmd = matter.readAttributes(attributePaths)
    sendToDevice(cmd)
*/

    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(0x05, 0x00, zigbee.swapOctets(HexUtils.integerToHexString(time, 2))))
    cmd = matter.invoke(device.endpointId, 0x0003, 0x0000, cmdFields)
    sendToDevice(cmd)
}

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true ; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) }                 // 3 seconds
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false }
void setDigitalRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isDigital'] = true ; runInMillis(DIGITAL_TIMER, clearDigitalRequest, [overwrite: true]) }                 // 3 seconds
void clearDigitalRequest() { if (state.states == null) { state.states = [:] } ; state.states['isDigital'] = false }

void logRequestedClusterAttrResult(Map data) {
    String clusterAtttr = "Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint <b>0x${HexUtils.integerToHexString(data.endpoint as Integer, 1)}</b> attributes and values list"
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

/**
 * Requests the list of attributes for a Matter cluster (reads the 0xFFFB attribute)
 *
 * @param data A map containing the cluster and the endpoint.
 */
void requestMatterClusterAttributesList(Map data) {
    if (state.states == null) { state.states = [:] }
    state.states['isInfo'] = true
    state.states['cluster'] = HexUtils.integerToHexString(data.cluster, 2)  // which cluster we are collecting info for
    state.tmp = null
    Integer endpoint = data.endpoint as Integer
    Integer cluster  = data.cluster  as Integer
    logInfo "Requesting Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint <b>0x${HexUtils.integerToHexString(endpoint, 1)}</b> attributes list ..."
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, 0xFFFB as Integer))
    sendToDevice(matter.readAttributes(attributePaths))
}

/**
 * Requests the values of attributes for a Matter cluster.
 *
 * @param data A map containing the endpoint and cluster information.
 *             - endpoint: The endpoint of the cluster (as an Integer).
 *             - cluster: The cluster identifier (as an Integer).
 */
void requestMatterClusterAttributesValues(Map data) {
    Integer endpoint = data.endpoint as Integer
    Integer cluster  = data.cluster  as Integer
    String stateName
    String attrListName = 'ServerList'
    List<Map<String, String>> serverList = []

    if (endpoint == 0) {
        stateName = 'bridgeDescriptor'
    }
    else {
        stateName = 'fingerprint' + HexUtils.integerToHexString(endpoint, 1)
    }
    if (state[stateName] == null) {
        logWarn "requestMatterClusterAttributesValues: state.${stateName} is null !"
        return
    }
    logInfo "Requesting Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint <b>0x${HexUtils.integerToHexString(endpoint, 1)}</b> attributes values ..."
    String clusterMapName = '0x' + HexUtils.integerToHexString(cluster, 2)
    serverList = state[stateName][clusterMapName]
    if (serverList == null) {
        logWarn 'requestMatterClusterAttributesValues: attrListString is null'
        return
    }
    logDebug "requestMatterClusterAttributesValues: serverList:${serverList}"
    
    List<Map<String, String>> attributePaths = []
    Integer attrInt
    serverList.each { attr ->
        attrInt = HexUtils.hexStringToInt(attr)
        // skip some attributes that are known to be not supported by some devices
        if (attrInt == 0x0040 || attrInt == 0x0041) { 
            logDebug "requestMatterClusterAttributesValues: skipping attribute 0x${HexUtils.integerToHexString(attrInt, 4)} (${attrInt})"
            return 
        }
        /*
        if (endpoint == 0x03) { 
            logDebug "requestMatterClusterAttributesValues: skipping endpoint 03"
            return 
        } */
        /*
        if (cluster == 0x03) { 
            logDebug "requestMatterClusterAttributesValues: skipping cluster 03"
            return 
        }*/
        // DeviceTypeList
        attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrInt as Integer))
    }
    if (attributePaths != []) {
        sendToDevice(matter.readAttributes(attributePaths))
    }
}

void requestAndCollectAttributesValues(endpoint, cluster, time, fast=false) {
    runIn(time ?: 1, requestMatterClusterAttributesList,    [overwrite: false, data: [endpoint:endpoint, cluster:cluster] ])
    runIn(time + (fast ? 2 : 3),  requestMatterClusterAttributesValues,  [overwrite: false, data: [endpoint:endpoint, cluster:cluster] ])
    runIn(time + (fast ? 6 : 12),  logRequestedClusterAttrResult,        [overwrite : false, data : [endpoint : endpoint, cluster : cluster] ])
}

void getInfo(infoType, endpoint=0) {
    logWarn "getInfo(${infoType}, ${endpoint})"
    unschedule('requestMatterClusterAttributesList')
    unschedule('requestMatterClusterAttributesValues')
    unschedule('logRequestedClusterAttrResult')
    ping()
    // ['Basic', 'Extended']
    if (infoType == 'Basic') {
        // first thing to do is to read the Bridge (ep=0) Descriptor Cluster (0x001D) attribute 0XFFFB and store the ServerList in state.bridgeDescriptor['ServerList']
        // also, the DeviceTypeList ClientList and PartsList are stored in state.bridgeDescriptor
        requestAndCollectAttributesValues(endpoint, cluster = 0x001D, time = 2)                    // Descriptor Cluster - DeviceTypeList, ServerList, ClientList, PartsList


        // collect the BasicInformation Cluster attributes
        requestAndCollectAttributesValues(endpoint, cluster = 0x0028, time = 16)                   // Basic Information Cluster
        logWarn "getInfo():'Basic' type"
        return
    }
    if (infoType == 'Extended') {
        requestAndCollectAttributesValues(endpoint, cluster = 0x001D, time = 1)                    // Descriptor Cluster
        requestAndCollectAttributesValues(endpoint, cluster = 0x0028, time = 16)                   // Basic Information Cluster

        // for each ServerList element in state.bridgeDescriptor['ServerList'] call requestAndCollectAttributesValues(endpoint = 0, cluster = element, time = 1)
        Integer clusterInt
        Integer endpointInt  = safeToInt(endpoint)
        String  endpointStr  = HexUtils.integerToHexString(endpointInt, 1)
        String  fingerprintName = "fingerprint${endpointStr}"
        if (endpointInt == 0) { fingerprintName = 'bridgeDescriptor' }

        List<Map<String, String>> serverList = []
        log.trace "getInfo(): endpointInt= ${endpointInt} fingerprintName:${fingerprintName}"
        if (state[fingerprintName] == null) {
            logWarn "getInfo(): state.${fingerprintName} is null !"
            return
        }
        serverList = state[fingerprintName]['ServerList']
        log.trace "getInfo(): serverList:${serverList}"
        if (serverList == null) {
            logWarn 'getInfo(): serverList is null !'
            return
        }
        //state.bridgeDescriptor['ServerList'].each { cluster ->
        Integer time = 30
        serverList.each { cluster ->
            clusterInt = HexUtils.hexStringToInt(cluster)
            //if (clusterInt == 0x0003) { endpoint = 1 } else { endpoint = 0 }
            logDebug "getInfo(): clusterInt:${clusterInt} (0x${cluster})  endpointInt:${endpointInt} (0x${endpointStr}) time:${time}"
            requestAndCollectAttributesValues(endpointInt, clusterInt, time, fast=true)
            time += 7
        }
        logDebug "getInfo(): jobs scheduled for total time:${time} seconds"
        return
    }
}

void getPartsList() {
    logDebug 'getPartsList()'
    if (state.bridgeDescriptor == null) {
        logWarn 'getPartsList(): state.bridgeDescriptor is null !'
        return
    }
    ping()
    Integer time = 2
    
    // for each bridged device endpoint in the state.bridgeDescriptor['PartsList'] we need to read the ServerList
    state.bridgeDescriptor['PartsList'].each { endpointId ->
        Integer endpoint = HexUtils.hexStringToInt(endpointId)
        if (endpoint == 1) { return }
        logDebug "getPartsList(): endpoint:${endpoint} time:${time}"
        // TODO - call cluster 0x001D only if the ServerList contains 0x001D !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        requestAndCollectAttributesValues(endpoint, 0x001D, time, fast=true)       // Descriptor Cluster -> fill in the ServerList for each endpoint
        time += 7
    }
    

    // We should have the ServerList for each child device now - state.fingerprintXX['ServerList']
    //
    // for each child device that has the BridgedDeviceBasicInformationCluster 0x0039 in the ServerList ->  read the BridgedDeviceBasicInformationCluster attributes
    String fingerprintName
    state.bridgeDescriptor['PartsList'].each { endpointId ->
        Integer endpoint = HexUtils.hexStringToInt(endpointId)
        fingerprintName = "fingerprint${endpointId}"
        //if (endpoint == 1) { return }
        //logDebug "getPartsList(): endpoint:${endpoint} time:${time}"
        if (state[fingerprintName] == null) {
            logWarn "getPartsList(): state.${fingerprintName} is null !"
            return
        }
        // check if there is a 0x0039 map entry in the fingerprint
        List<Map<String, String>> BridgedDeviceBasicInformationList = []
        BridgedDeviceBasicInformationList = state[fingerprintName]['0x0039']
        if (BridgedDeviceBasicInformationList == null) {
            logWarn "getPartsList(): state.${fingerprintName}['0x0039'] is null !"
            return
        }

        requestAndCollectAttributesValues(endpoint, 0x0039, time, fast=true)       // BridgedDeviceBasic Cluster
        time += 7
    }


    logDebug "getPartsList(): jobs scheduled for total time:${time} seconds"
}

void readAttribute(endpoint, cluster, attrId) {
    logDebug "readAttribute(endpoint:${endpoint}, cluster:${cluster}, attrId:${attrId})"
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer))
    sendToDevice(matter.readAttributes(attributePaths))
}

void configure() {
    log.warn 'configure...'
    sendToDevice(subscribeCmd())
    sendInfoEvent('configure()...', 'sent device subscribe command')
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
    log.warn 'initialize()...'
    unschedule()
    state.states['isInfo'] = false
    Integer timeSinceLastSubscribe   = (now() - (state.lastTx['subscribeTime']   ?: 0)) / 1000
    Integer timeSinceLastUnsubscribe = (now() - (state.lastTx['unsubscribeTime'] ?: 0)) / 1000
    logDebug "'isSubscribe'= ${state.states['isSubscribe']} timeSinceLastSubscribe= ${timeSinceLastSubscribe} 'isUnsubscribe' = ${state.states['isUnsubscribe']} timeSinceLastUnsubscribe= ${timeSinceLastUnsubscribe}"

    state.stats['initializeCtr'] = (state.stats['initializeCtr'] ?: 0) + 1
    if (state.deviceType == null || state.deviceType != DEVICE_TYPE) {
        log.warn 'initialize(fullInit = true))...'
        initializeVars(fullInit = true)
        sendInfoEvent('initialize()...', 'full initialization - all settings are reset to default')
    }
    log.warn "initialize(): calling subscribe()! (last unsubscribe was more than ${timeSinceLastSubscribe} seconds ago)"
    state.lastTx['subscribeTime'] = now()
    state.states['isUnsubscribe'] = false
    state.states['isSubscribe'] = true  // should be set to false in the parse() method
    scheduleCommandTimeoutCheck(delay = 30)
    subscribe()
}

void reSubscribe() {
    logWarn 'reSubscribe() ...'
    unsubscribe()
}

void unsubscribe() {
    sendInfoEvent('unsubscribe()...Please wait.', 'sent device unsubscribe command')
    sendToDevice(unSubscribeCmd())
}

String  unSubscribeCmd() {
    return matter.unsubscribe()
}

void subscribe(addOrRemove, endpoint, cluster, attrId) {
    String cmd = ''
    logDebug "subscribe(action: ${addOrRemove} endpoint:${endpoint}, cluster:${cluster}, attrId:${attrId})"
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer))
    // format EP_CLUSTER_ATTRID
    List<String> newSub = [endpoint, cluster, attrId]
    List<String> subsciptions = state.subscriptions ?: []
    if (addOrRemove == 'Add') {
        if (subsciptions.contains(newSub)) {
            logWarn "subscribe(): subscription already exists: ${newSub}"
        } else {
            cmd = matter.subscribe(0, 0xFFFF, attributePaths)
            sendToDevice(cmd)
            subsciptions.add(newSub)
            state.subscriptions = subsciptions
        }
    }
    else if (addOrRemove == 'Remove') {
        if (subsciptions.contains(newSub)) {
            subsciptions.remove(newSub)
            state.subscriptions = subsciptions
        } else {
            logWarn "subscribe(): subscription not found!: ${newSub}"
        }
    }
    else if (addOrRemove == 'Show') {
        if (logEnable) { logInfo "subscribe(): state.subscriptions size is ${state.subscriptions?.size()}" }
    }
    else {
        logWarn "subscribe(): unknown action: ${addOrRemove}"
    }
    if (logEnable) { logInfo "subscribe(): state.subscriptions = ${state.subscriptions}" }
}

void subscribe() {
    sendInfoEvent('subscribe()...Please wait.', 'sent device subscribe command')
    sendToDevice(subscribeCmd())
}

String subscribeCmd() {
    List<Map<String, String>> attributePaths = []
    String cmd = ''
    //state.deviceType == 'MATTER_BRIDGE'
    attributePaths.add(matter.attributePath(0, 0x001D, 0x00))
    // get each element of state.subscriptions and add it to the attributePaths
    List<String> subsciptions = state.subscriptions ?: []
    subsciptions.each { sub ->
        attributePaths.add(matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer))
    }
    cmd = matter.subscribe(0, 0xFFFF, attributePaths)
    return cmd
}

void checkSubscriptionStatus() {
    if (state.states == null) { state.states = [:] }
    if (state.states['isUnsubscribe'] == true) {
        logInfo 'checkSubscription(): unsubscribe() is completed.'
        sendInfoEvent('unsubscribe() is completed', 'something was received in the parse() method')
        state.states['isUnsubscribe'] = false
    }
    if (state.states['isSubscribe'] == true) {
        logInfo 'checkSubscription(): subscribe() is completed.'
        sendInfoEvent('completed', 'something was received in the parse() method')
        state.states['isSubscribe'] = false
    }
}

void refresh() {
    logInfo'refresh() ...'
    checkDriverVersion()
    setRefreshRequest()    // 6 seconds
    sendToDevice(refreshCmd())
}

String refreshCmd() {
    List<Map<String, String>> attributePaths = []

// state.deviceType == 'MATTER_BRIDGE'
    attributePaths.add(matter.attributePath(device.endpointId, 0x0003, 0x0000))         // TODO !!
    String cmd = matter.readAttributes(attributePaths)
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

void sendInfoEvent(info = null, descriptionText = null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: 'Status', value: 'clear', descriptionText: 'last info messages auto cleared', isDigital: true)
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, descriptionText:descriptionText ?: '', isDigital: true)
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
            state.health['offlineCtr'] = (state.health['offlineCtr'] ?: 0) + 1
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
    if (state.states['isPing'] == true) {
        sendRttEvent('timeout')
        state.states['isPing'] = false
        if (state.stats != null) { state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 } else { state.stats = [:] }
    } else {
        sendInfoEvent('timeout!', 'no response received on the last matter command!')
    }
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
    state.stats  = state.states = [:]
    state.lastRx = state.lastTx = [:]
    state.lastTx['pingTime'] = state.lastTx['cmdTime'] = now()
    state.lastTx['subscribeTime'] = state.lastTx['unsubscribeTime'] = now()
    state.health = [:]
    state.bridgeDescriptor  = [:]   // driver specific
    state.subsciptions = []         // driver specific, format EP_CLUSTER_ATTR
    state.stats['rxCtr'] = state.stats['txCtr'] = 0
    state.stats['initializeCtr'] = state.stats['duplicatedCtr'] = 0
    state.states['isDigital'] = state.states['isRefresh'] = state.states['isPing'] =  state.states['isInfo']  = false
    state.states['isSubscribing'] =  state.states['isUnsubscribing']  = false
    state.health['offlineCtr'] = state.health['checkCtr3']  = 0
}

void initializeVars(boolean fullInit = false) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true) {
        state.clear()
        unschedule()
        resetStats()
        state.comment = 'Experimental Matter Bridge Driver'
        logInfo 'all states and scheduled jobs cleared!'
        state.driverVersion = driverVersionAndTimeStamp()
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}"
        state.deviceType = DEVICE_TYPE
        sendInfoEvent('Initialized (fullInit = true)', 'full initialization - loaded all defaults!')
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

/* groovylint-disable-next-line UnusedMethodParameter */
void test(par) {
    /*
    log.warn "test... ${par}"
    log.debug "Matter cluster names = ${matter.getClusterNames()}"    // OK
    log.debug "Matter getClusterIdByName ${matter.getClusterIdByName('Identify')}"  // OK
    log.debug "Matter getClusterName(3) :  ${matter.getClusterName(3)}"  // not OK - echoes back the cluster number?
    */
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0x01, 0x001D, 0x00))
    attributePaths.add(matter.attributePath(0x1F, 0x0003, 0x00))
    String cmd = matter.readAttributes(attributePaths)
    sendToDevice(cmd)

    //List<Map<String, String>> subscribePaths = []
    //String cmd = ''
    //state.deviceType == 'MATTER_BRIDGE'
    //attributePaths.add(matter.attributePath(device.endpointId, 0x0003, 0x00))
    //subscribePaths.add(matter.attributePath(0, 0x001D, 0x00))
    cmd = matter.subscribe(2, 0xFFFF, attributePaths)
    sendToDevice(cmd)
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

Map getAttributesMapByClusterId(String cluster) {
    if (cluster == null) { return null }
    if (cluster == '001D') { return DescriptorClusterAttributes }
    if (cluster == '001E') { return BindingClusterAttributes }
    if (cluster == '001F') { return AccessControlClusterAttributes }
    if (cluster == '0028') { return BasicInformationClusterAttributes }
    if (cluster == '0029') { return OTASoftwareUpdateProviderClusterAttributes }
    if (cluster == '002A') { return OTASoftwareUpdateRequestorClusterAttributes }
    if (cluster == '002B') { return LocalizationConfigurationClusterAttributes }
    if (cluster == '002C') { return TimeFormatLocalizationClusterAttributes }
    if (cluster == '002E') { return PowerSourceConfigurationClusterAttributes }
    if (cluster == '002F') { return PowerSourceClusterAttributes }
    if (cluster == '0030') { return GeneralCommissioningClusterAttributes }
    if (cluster == '0031') { return NetworkCommissioningClusterAttributes }
    if (cluster == '0032') { return DiagnosticLogsClusterAttributes }
    if (cluster == '0033') { return GeneralDiagnosticsClusterAttributes }
    if (cluster == '0034') { return SoftwareDiagnosticsClusterAttributes }
    if (cluster == '0037') { return EthernetNetworkDiagnosticsClusterAttributes }
    if (cluster == '0039') { return BridgedDeviceBasicClusterAttributes }
    if (cluster == '003C') { return AdministratorCommissioningClusterAttributes }
    if (cluster == '003E') { return OperationalCredentialsClusterAttributes }
    if (cluster == '003F') { return GroupKeyManagementClusterAttributes }
    if (cluster == '0040') { return FixedLabelClusterAttributes }
    if (cluster == '0041') { return UserLabelClusterAttributes }
    if (cluster == '0406') { return OccupancySensingClusterAttributes }
    return null
}

// 7.13. Global Elements
@Field static final Map<Integer, String> GlobalElementsAttributes = [
    0x00FE  : 'FabricIndex',
    0xFFF8  : 'GeneratedCommandList',
    0xFFF9  : 'AcceptedCommandList',
    0xFFFA  : 'EventList',
    0xFFFB  : 'AttributeList',
    0xFFFC  : 'FeatureMap',
    0xFFFD  : 'ClusterRevision'
]

// 9.5. Descriptor Cluser 0x001D ep=0
@Field static final Map<Integer, String> DescriptorClusterAttributes = [
    0x0000  : 'DeviceTypeList',
    0x0001  : 'ServerList',
    0x0002  : 'ClientList',
    0x0003  : 'PartsList'
]

// 9.6. Binding Cluster 0x001E
@Field static final Map<Integer, String> BindingClusterAttributes = [
    0x0000  : 'Binding'
]

// 9.10.5. Access Control Cluster 0x001F
@Field static final Map<Integer, String> AccessControlClusterAttributes = [
    0x0000  : 'ACL',
    0x0001  : 'Extension',
    0x0002  : 'SubjectsPerAccessControlEntry',
    0x0003  : 'TargetsPerAccessControlEntry',
    0x0004  : 'AccessControlEntriesPerFabric'
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

// 11.19.6.5. OTA Software Update Provider Cluster 0x0029
@Field static final Map<Integer, String> OTASoftwareUpdateProviderClusterAttributes = [
    0x0000  : 'Dummy'
]

// 11.19.7.5 OTA Software Update Requestor Cluster 0x002A
@Field static final Map<Integer, String> OTASoftwareUpdateRequestorClusterAttributes = [
    0x0000  : 'DefaultOTAProviders',
    0x0001  : 'UpdatePossible',
    0x0002  : 'UpdateState',
    0x0003  : 'UpdateStateProgress'
]

// 11.3.1.3 Localization Configuration Cluster 0x002B
@Field static final Map<Integer, String> LocalizationConfigurationClusterAttributes = [
    0x0000  : 'ActiveLocale',
    0x0001  : 'SupportedLocales'
]

// 11.4.1.3. Time Format Localization Cluster 0x002C
@Field static final Map<Integer, String> TimeFormatLocalizationClusterAttributes = [
    0x0000  : 'HourFormat',
    0x0001  : 'ActiveCalendarType',
    0x0002  : 'SupportedCalendarTypes'
]

// 11.6.6.1 Poweer Source Configuration Cluster 0x002E
@Field static final Map<Integer, String> PowerSourceConfigurationClusterAttributes = [
    0x0000  : 'dummy'
]

// 11.9.6. General Commissioning Cluster 0x0030
@Field static final Map<Integer, String> GeneralCommissioningClusterAttributes = [
    0x0000  : 'Breadcrumb',
    0x0001  : 'BasicCommissioningInfo',
    0x0002  : 'RegulatoryConfig',
    0x0003  : 'LocationCapability',
    0x0004  : 'SupportsConcurrentConnection'
]

// 11.8. Network Commissioning Cluster 0x0031
@Field static final Map<Integer, String> NetworkCommissioningClusterAttributes = [
    0x0000  : 'MaxNetworks',
    0x0001  : 'Networks',
    0x0002  : 'ScanMaxTimeSeconds',
    0x0003  : 'ConnectMaxTimeSeconds',
    0x0004  : 'InterfaceEnabled',
    0x0005  : 'LastNetworkingStatus',
    0x0006  : 'LastNetworkID',
    0x0007  : 'LastConnectErrorValue'
]

// 11.10.4. Diagnostic Logs Cluster 0x0032
@Field static final Map<Integer, String> DiagnosticLogsClusterAttributes = [
    0x0000  : 'dummy'
]

// 11.11.7. General Diagnostics Cluster 0x0033
@Field static final Map<Integer, String> GeneralDiagnosticsClusterAttributes = [
    0x0000  : 'NetworkInterfaces',
    0x0001  : 'RebootCount',
    0x0002  : 'UpTime',
    0x0003  : 'TotalOperationalHours',
    0x0004  : 'BootReasons',
    0x0005  : 'ActiveHardwareFault',
    0x0006  : 'ActiveRadioFault',
    0x0007  : 'ActiveNetworkFaults',
    0x0008  : 'TestEventTriggersEnabled'
]

// 11.12.4. Software Diagnostics Cluster 0x0034
@Field static final Map<Integer, String> SoftwareDiagnosticsClusterAttributes = [
    0x0000  : 'ThreadMetrics',
    0x0001  : 'CurrentHeapFree',
    0x0002  : 'CurrentHeapUsed',
    0x0003  : 'CurrentHeapHighWatermark'
]

// 11.15.4. Ethernet Network Diagnostics Cluster 0x0037
@Field static final Map<Integer, String> EthernetNetworkDiagnosticsClusterAttributes = [
    0x0000  : 'PHYRate',
    0x0001  : 'FullDuplex',
    0x0002  : 'PacketRxCount',
    0x0003  : 'PacketTxCount',
    0x0004  : 'TxErrCount',
    0x0005  : 'CollisionCount',
    0x0006  : 'OverrunCount',
    0x0007  : 'CarrierDetect',
    0x0008  : 'TimeSinceReset'
]

/*
// 9.13.4. Bridged Device Basic Information Cluster 0x0039  // TODO - check the IDs !!  - probably the same as Basic Information Cluster 0x0028
@Field static final Map<Integer, String> BridgedDeviceBasicInformationClusterAttributes = [
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
    0x0012  : 'UniqueID',
    0x0013  : 'CapabilityMinima'
]
*/

// 11.18.4. Administrator Commissioning Cluster 0x003C
@Field static final Map<Integer, String> AdministratorCommissioningClusterAttributes = [
    0x0000  : 'WindowStatus',
    0x0001  : 'AdminFabricIndex',
    0x0002  : 'AdminVendorId'
]

// 11.17.6. Operational Credentials Cluster 0x003E
@Field static final Map<Integer, String> OperationalCredentialsClusterAttributes = [
    0x0000  : 'NOCs',
    0x0001  : 'Fabrics',
    0x0002  : 'SupportedFabrics',
    0x0003  : 'CommissionedFabrics',
    0x0004  : 'TrustedRootCertificates',
    0x0005  : 'CurrentFabricIndex'
]

// 11.2.7.1. Group Key Management Cluster 0x003F
@Field static final Map<Integer, String> GroupKeyManagementClusterAttributes = [
    0x0000  : 'GroupKeyMap',
    0x0001  : 'GroupTable',
    0x0002  : 'MaxGroupsPerFabric',
    0x0003  : 'MaxGroupKeysPerFabric'
]

// 9.8.3. Fixed Label Cluster 0x0040
@Field static final Map<Integer, String> FixedLabelClusterAttributes = [
    0x0000  : 'LabelList'
]

// 9.9.3. User Label Cluster 0x0041
@Field static final Map<Integer, String> UserLabelClusterAttributes = [
    0x0000  : 'LabelList'
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

// 1.6. Level Control Cluster 0x0008
@Field static final Map<Integer, String> LevelControlClusterAttributes = [
    0x0000  : 'CurrentLevel',
    0x0001  : 'RemainingTime',
    0x0002  : 'MinLevel',
    0x0003  : 'MaxLevel',
    0x0004  : 'CurrentFrequency',
    0x0005  : 'MinFrequency',
    0x0010  : 'OnOffTransitionTime',
    0x0011  : 'OnLevel',
    0x0012  : 'OnTransitionTime',
    0x0013  : 'OffTransitionTime',
    0x000F  : 'Options',
    0x4000  : 'StartUpCurrentLevel'
]

@Field static final Map<Integer, String> LevelControlClusterCommands = [
    0x00    : 'MoveToLevel',
    0x01    : 'Move',
    0x02    : 'Step',
    0x03    : 'Stop',
    0x04    : 'MoveToLevelWithOnOff',
    0x05    : 'MoveWithOnOff',
    0x06    : 'StepWithOnOff',
    0x07    : 'StopWithOnOff',
    0x08    : 'MoveToClosestFrequency'
]

// 11.7. Power Source Cluster 0x002F    // attrList:[0, 1, 2, 11, 12, 14, 15, 16, 19, 25, 65528, 65529, 65531, 65532, 65533]
@Field static final Map<Integer, String> PowerSourceClusterAttributes = [
    0x0000  : 'Status',
    0x0001  : 'Order',
    0x0002  : 'Description',
    0x000B  : 'BatVoltage',
    0x000C  : 'BatPercentRemaining',
    0x000D  : 'BatTimeRemaining',
    0x000E  : 'BatChargeLevel',
    0x000F  : 'BatReplacementNeeded',
    0x0010  : 'BatReplaceability',
    0x0013  : 'BatReplacementDescription',
    0x0019  : 'BatQuantity'
]
@Field static final Map<Integer, String> PowerSourceClusterStatus = [
    0x00    : 'Unspecified',    // SHALL indicate the source status is not specified
    0x01    : 'Active',         // SHALL indicate the source is available and currently supplying power
    0x02    : 'Standby',        // SHALL indicate the source is available, but is not currently supplying power
    0x03    : 'Unavailable'     // SHALL indicate the source is not currently available to supply power
]
@Field static final Map<Integer, String> PowerSourceClusterBatteryChargeLevel = [
    0x00    : 'OK',             // Charge level is nominal
    0x01    : 'Warning',        // Charge level is low, intervention may soon be required.
    0x02    : 'Critical'        // Charge level is critical, immediate intervention is required.
]

// 1.7 Bolean State Cluster 0x0045
@Field static final Map<Integer, String> BoleanStateClusterAttributes = [
    0x0000  : 'StateValue'
]

// 2.7.5. Occupancy Sensing Cluster 0x0406
@Field static final Map<Integer, String> OccupancySensingClusterAttributes = [
    0x0000  : 'Occupancy',
    0x0001  : 'OccupancySensorType',
    0x0002  : 'OccupancySensorTypeBitmap',
    0x0010  : 'PIROccupiedToUnoccupiedDelay',
    0x0011  : 'PIRUnoccupiedToOccupiedDelay',
    0x0012  : 'PIRUnoccupiedToOccupiedThreshold',
    0x0020  : 'UltrasonicOccupiedToUnoccupiedDelay',
    0x0021  : 'UltrasonicUnoccupiedToOccupiedDelay',
    0x0022  : 'UltrasonicUnoccupiedToOccupiedThreshold',
    0x0030  : 'PhysicalContactOccupiedToUnoccupiedDelay',
    0x0031  : 'PhysicalContactUnoccupiedToOccupiedDelay',
    0x0032  : 'PhysicalContactUnoccupiedToOccupiedThreshold'
]
