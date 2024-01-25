/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodParameterTypeRequired, MethodSize, NglParseError, NoDef, NoDouble, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter */
/**
 *  Matter Advanced Bridge - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *  https://community.hubitat.com/t/project-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009
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
 * ver. 1.0.1  2024-01-05 kkossev  - Linter; Discovery OK; published for alpha- testing.
 * ver. 1.0.2  2024-01-07 kkossev  - Refresh() reads the subscribed attributes; added command 'Device Label'; VendorName, ProductName, Reachable for child devices; show the device label in the event logs if set; added a test command 'setSwitch' on/off/toggle + device#;
 * ver. 1.0.3  2024-01-11 kkossev  - Child devices : deviceCount, mapTuyaCategory(d); added 'Matter_Generic_Component_Motion_Sensor.groovy', 'Matter_Generic_Component_Window_Shade.groovy' and 'matterLib.groovy'; Hubitat Bundle package;
 *                                   A1 Bridge Discovery now uses the short version; added logTrace() and logError() methods; setSwitch and setLabel commands visible in _DEBUG mode only
 * ver. 1.0.4  2024-01-14 kkossev  - added 'Matter Generic Component Switch' component driver; cluster 0x0102 (WindowCovering) attributes decoding - position, targetPosition, windowShade; add cluster 0x0102 commands processing; logTrace is  switched off after 30 minutes; filtered duplicated On/Off events in the Switch component driver;
 *                                   disabled devices are not processed to avoid spamming the debug logs; initializeCtr attribute; Default Bridge healthCheck method is set to periodic polling every 1 hour; added new removeAllSubscriptions() command; added 'Invert Motion' option to the Motion Sensor component driver @iEnam
 * ver. 1.0.5  2024-01-20 kkossev  - added endpointsCount; subscribe to [endpoint:00, cluster:001D, attrId:0003 - PartsList = the number of the parts list entries]; refactoring: parseGlobalElements(); discovery process bugs fixes; debug is false by default; changeed the steps sequence (first create devices, last subscribe to the attributes); temperature sensor omni component bug fix;
 * ver. 1.0.6  2024-01-25 kkossev  - (dev.branch) removed setLabel command; added readSingeAttrStateMachine; 
 *
 *                                   TODO: [== W.I.P.==] discoverAllStateMachine
 *                                   TODO: [== W.I.P.==] remove setSwitch command ?
 *
 *                                   TODO: [ RESEARCH  ] check on pauseExecution(1000) usage in the driver code -  will never work with singleThreaded: true ???
 *                                   TODO: [REFACTORING] Replace the scheduled jobs w/ StateMachine (store each discovery step in a list)
 *                                   TODO: [====MVP====] Publish version 1.0.6
 *
 *                                   TODO: [====MVP====] healhCheck schedued job is lost on resubscribe() - fix it!
 *                                   TODO: [====MVP====] When a bridged device is deleted - ReSubscribe() to first delete all subscriptions and then re-discover all the devices, capabilities and subscribe to the known attributes
 *                                   TODO: [====MVP====] refresh to be individual list in each fingerprint - needed for the refresh() command ! (add a deviceNumber parameter to the refresh() command command)
 *                                   TODO: [====MVP====] subscriptions to be individual list in each fingerprint
 *                                   TODO: [====MVP====] add Data.Refresh for each child device
 *                                   TODO: [====MVP====] componentRefresh(DeviceWrapper dw)
 *                                   TODO: [====MVP====] Publish version 1.0.7
 *
 *                                   TODO: [====MVP====] add cluster 08 processing
 *                                   TODO: [====MVP====] add cluster 0300 processing
 *                                   TODO: [====MVP====] Publish version 1.0.8
 *
 *                                   TODO: [====MVP====] add heathStatus to the child devices
 *                                   TODO: [====MVP====] Publish version 1.0.9
 *
 *                                   TODO: [REFACTORING] move the component drivers names into a table
 *                                   TODO: [REFACTORING] substitute the tmp state with a in-memory cache
 *                                   TODO: [REFACTORING] add a temporay state to store the attributes list of the currently interviewed cluster
 *                                   TODO: [REFACTORING] Convert SupportedMatterClusters to Map that include the known attributes to be subscribed to
 *
 *                                   TODO: [ENHANCEMENT] [0001] RebootCount = 06 [0002] UpTime = 0x000E22B4 (926388)  [0003] TotalOperationalHours = 0x0101 (257)
 *                                   TODO: [ENHANCEMENT] Device Extended Info - expose as a command?
 *                                   TODO: [ENHANCEMENT] option to automatically delete the child devices when missing from the PartsList
 *                                   TODO: [ENHANCEMENT] add initialized() method to the child devices (send 'unknown' events for all attributes)
 *                                   TODO: [ENHANCEMENT] deviceCount; endpointsCount
 *                                   TODO: [ENHANCEMENT] clearStatistics command/button
 *                                   TODO: [ENHANCEMENT] add support for cluster 0x003B  : 'Switch' (need to be able to subscribe to the 0x003B EVENTS !)
 *                                   TODO: [ENHANCEMENT] DeleteDevices() to take device# parameter to delete a single device (0=all)
 *                                   TODO: [ENHANCEMENT] store subscription lists in Hex format
 *                                   TODO: [ENHANCEMENT] Philips Hue Bridge discovery (subscription) depending on the ClusterAttributes list supported
 *                                   TODO: [ENHANCEMENT] add getInfo(Basic) for the child devices during the discovery !
 *                                   TODO: [ENHANCEMENT] add Cluster SoftwareDiagnostics (0x0034) endpoint 0x0 attribute [0001] CurrentHeapFree = 0x00056610 (353808)
 *                                   TODO: [ENHANCEMENT] implement ping() for the child devices (requires individual states for each child device...)
 *                                   TODO: [ENHANCEMENT] add [refresh] to the descriptionText and to the eventMap
 *                                   TODO: [ENHANCEMENT] implement healthStatus for the child devices
 *                                   TODO: [ENHANCEMENT] add to Device#xx the Bridge name! ( add the bridge LABEL ( 'Hue Matter Bridge')
 *                                   TODO: [ENHANCEMENT] add Configure() custom command - perform reSubscribe()
 *                                   TODO: [ENHANCEMENT] make Identify command work !
 *                                   TODO: [ENHANCEMENT] add GeneralDiagnostics (0x0033) endpoint 0x00 :  [0001] RebootCount = 06 [0002] UpTime = 0x000E22B4 (926388)  [0003] TotalOperationalHours = 0x0101 (257)
 *                                   TODO: [ENHANCEMENT] use the  BridgedDeviceBasicInformation (0x0039) attribute [0005] NodeLabel = Hue white lamp Livingroom 1
 *                                   TODO: [ENHANCEMENT] use the  BridgedDeviceBasicInformation (0x0039) attribute[000E] ProductLabel = Hue white lamp
 *                                   TODO: [ENHANCEMENT] use the  BridgedDeviceBasicInformation (0x0039) attribute [000A] SoftwareVersionString = 1.108.7
 *
 *                                   TODO: [ RESEARCH  ] check setSwitch() device# commandsList
 *                                   TODO: [ RESEARCH  ] add a Parent entry in the child devices fingerprints (PartsList)
 *                                   TODO: [ RESEARCH  ] how to  combine 2 endpoints in one device - 'Temperature and Humidity Sensor' - 2 clusters
 *                                   TODO: [ RESEARCH  ] why the child devices are automatically disabled when shared via Hub Mesh ?
 *                                   TODO: - template -  [====MVP====] [REFACTORING] [RESEARCH] [ENHANCEMENT]
 */

/* groovylint-disable-next-line NglParseError */
#include kkossev.matterLib
#include kkossev.matterStateMachinesLib

String version() { '1.0.6' }
String timeStamp() { '2023/01/25 11:29 PM' }

@Field static final Boolean _DEBUG = true
@Field static final Boolean DEFAULT_LOG_ENABLE = true
@Field static final Boolean DO_NOT_TRACE_FFFX = true         // don't trace the FFFx global attributes
@Field static final String  DEVICE_TYPE = 'MATTER_BRIDGE'
@Field static final Boolean STATE_CACHING = false            // enable/disable state caching
@Field static final Integer CACHING_TIMER = 60               // state caching time in seconds
@Field static final Integer DIGITAL_TIMER = 3000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final String  UNKNOWN = 'UNKNOWN'
@Field static final Integer SHORT_TIMEOUT  = 7
@Field static final Integer LONG_TIMEOUT   = 15
@Field static final Integer MAX_DEVICES_LIMIT = 20

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field

import hubitat.helper.HexUtils

import java.util.concurrent.ConcurrentHashMap

metadata {
    definition(name: 'Matter Advanced Bridge', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/Matter_Advanced_Bridge.groovy', 
                                singleThreaded: true ) {
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
        //[0001] RebootCount = 06 [0002] UpTime = 0x000E22B4 (926388)  [0003] TotalOperationalHours = 0x0101 (257)
        attribute 'rebootCount', 'number'           // TODO - Bridge specific
        attribute 'upTime', 'number'                // TODO - Bridge specific
        attribute 'totalOperationalHours', 'number' // TODO - Bridge specific
        attribute 'deviceCount', 'number'           // TODO - count the actual number of the child devices, not the fingerprint count!
        attribute 'endpointsCount', 'number'        // the nubler of the elements in the PartsList
        attribute 'initializeCtr', 'number'
        attribute 'reachable', 'string'
        attribute 'state', 'enum', [
            'not configured',
            'error',
            'authenticating',
            'authenticated',
            'connected',
            'disconnected',
            'ready'
        ]

        command 'a0DiscoverAll',  [[name:'Discover All', type: 'ENUM', description: 'Type', constraints: ['All', 'BasicInfo', 'PartsList', 'SupportedClusters']]]
        command 'a1BridgeDiscovery', [[name: 'First click here ...']]
        command 'a2DevicesDiscovery', [[name: 'Next click here ....']]
        command 'a3CapabilitiesDiscovery', [[name: 'Next click here ....']]
        command 'a4CreateChildDevices', [[name: 'Next click here ....']]
        command 'a5SubscribeKnownClustersAttributes', [[name: 'Last click here ....']]
        command 'initialize', [[name: 'Invoked automatically during the hub reboot, do not click!']]
        command 'reSubscribe', [[name: 're-subscribe to the Matter controller events']]
        command 'loadAllDefaults', [[name: 'panic button: Clear all States and start over']]
        command 'removeAllDevices', [[name: 'panic button: Remove all child devices']]
        command 'removeAllSubscriptions', [[name: 'panic button: remove all subscriptions']]

        if (_DEBUG) {
            command 'getInfo', [
                    [name:'infoType', type: 'ENUM', description: 'Bridge Info Type', constraints: ['Basic', 'Extended']],   // if the parameter name is 'type' - shows a drop-down list of the available drivers!
                    [name:'endpoint', type: 'STRING', description: 'Endpoint', constraints: ['STRING']]
            ]
            command 'testDiscoveryAll', [
                    [name:'endpoint', type: 'STRING', description: 'Endpoint', constraints: ['STRING']]
            ]

            command 'getPartsList'
            command 'identify'      // can't make it work ... :(
            command 'readAttributeSafe', [
                    [name:'endpoint',   type: 'STRING', description: 'Endpoint',  constraints: ['STRING']],
                    [name:'cluster',    type: 'STRING', description: 'Cluster',   constraints: ['STRING']],
                    [name:'attribute',  type: 'STRING', description: 'Attribute', constraints: ['STRING']]
            ]
            command 'readAttribute', [
                    [name:'endpoint',   type: 'STRING', description: 'Endpoint',  constraints: ['STRING']],
                    [name:'cluster',    type: 'STRING', description: 'Cluster',   constraints: ['STRING']],
                    [name:'attribute',  type: 'STRING', description: 'Attribute', constraints: ['STRING']]
            ]
            command 'subscribe', [
                    [name:'addOrRemove',  type: 'ENUM',   description: 'Select',    constraints: ['add', 'remove', 'show']],
                    [name:'endpointPar',  type: 'STRING', description: 'Endpoint',  constraints: ['STRING']],
                    [name:'clusterPar',   type: 'STRING', description: 'Cluster',   constraints: ['STRING']],
                    [name:'attributePar', type: 'STRING', description: 'Attribute', constraints: ['STRING']]
            ]
            command 'setSwitch', [
                    [name:'command',      type: 'ENUM', description: 'Select', constraints: ['Off', 'On', 'Toggle']],
                    [name:'deviceNumber', type: 'STRING', description: 'Device Number',   constraints: ['STRING']],
                    [name:'extraPar',     type: 'STRING', description: 'Extra Parameter', constraints: ['STRING']]
            ]
            command 'unsubscribe'
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
        }
        // do not expose the fingerprints for now ... Let the stock driver be assigned automatically.
        // fingerprint endpointId:"01", inClusters:"0003,001D", outClusters:"001E", model:"Aqara Hub E1", manufacturer:"Aqara", controllerType:"MAT"
    }
    preferences {
        input(name:'txtEnable', type:'bool', title:'Enable descriptionText logging', defaultValue:true)
        input(name:'logEnable', type:'bool', title:'Enable debug logging', defaultValue:DEFAULT_LOG_ENABLE)
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false
        if (advancedOptions == true || advancedOptions == true) {
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
            input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>'
        }
    }
}

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 2,
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 60,
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map StartUpOnOffEnumOpts = [0: 'Off', 1: 'On', 2: 'Toggle']

@Field static final Map<Integer, Map> SupportedMatterClusters = [
    //0x0039 : [parser: 'parseBridgedDeviceBasic', attributes: 'BridgedDeviceBasicAttributes', commands: 'BridgedDeviceBasicCommands'],   // BridgedDeviceBasic
    0x0006 : [parser: 'parseOnOffCluster', attributes: 'OnOffClusterAttributes', commands: 'OnOffClusterCommands'],   // On/Off Cluster
    0x0102 : [parser: 'parseWindowCovering', attributes: 'WindowCoveringClusterAttributes', commands: 'WindowCoveringClusterCommands'],   // WindowCovering
    0x0402 : [parser: 'parseTemperatureMeasurement', attributes: 'TemperatureMeasurementClusterAttributes', commands: 'TemperatureMeasurementClusterCommands'],   // TemperatureMeasurement
    0x0405 : [parser: 'parseHumidityMeasurement', attributes: 'RelativeHumidityMeasurementClusterAttributes', commands: 'RelativeHumidityMeasurementClusterCommands'],   // HumidityMeasurement
    0x0406 : [parser: 'parseOccupancySensing', attributes: 'OccupancySensingClusterAttributes', commands: 'OccupancySensingClusterCommands']   // OccupancySensing (motion)
]

// Json Parsing Cache
@Field static final Map<String, Map> jsonCache = new ConcurrentHashMap<>()

// Track for dimming operations
@Field static final Map<String, Integer> levelChanges = new ConcurrentHashMap<>()

// Json Parser
@Field static final JsonSlurper jsonParser = new JsonSlurper()

// Random number generator
@Field static final Random random = new Random()

//parsers
void parse(final String description) {
    checkDriverVersion()
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
    if (descMap == null) {
        logWarn "parse: descMap is null description:${description}"
        return
    }

    updateStateStats(descMap)
    checkStateMachineConfirmation(descMap)

    if (isDeviceDisabled(descMap)) {
        if (traceEnable) { logWarn "parse: device is disabled: ${descMap}" }
        return
    }

    if (!(descMap.attrId in ['FFF8', 'FFF9', 'FFFA', 'FFFC', 'FFFD', '00FE']) && !DO_NOT_TRACE_FFFX && !(state['states']['isDiscovery'] == true) ) {
        logDebug "parse: descMap:${descMap}  description:${description}"
    }

    parseGlobalElements(descMap)

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
            parseGeneralDiagnostics(descMap)
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
        case '0102' :  // WindowCovering
            parseWindowCovering(descMap)
            gatherAttributesValuesInfo(descMap, WindowCoveringClusterAttributes)
            break
        case '0400' :  // IlluminanceMeasurement
            gatherAttributesValuesInfo(descMap, IlluminanceMeasurementClusterAttributes)
            break
        case '0402' :  // TemperatureMeasurement
            parseTemperatureMeasurement(descMap)
            gatherAttributesValuesInfo(descMap, TemperatureMeasurementClusterAttributes)
            break
        case '0405' :  // HumidityMeasurement
            parseHumidityMeasurement(descMap)
            gatherAttributesValuesInfo(descMap, RelativeHumidityMeasurementClusterAttributes)
            break
        case '0406' :  // OccupancySensing (motion)
            gatherAttributesValuesInfo(descMap, OccupancySensingClusterAttributes)
            parseOccupancySensing(descMap)
            break
        default :
            gatherAttributesValuesInfo(descMap, GlobalElementsAttributes)
            logWarn "parse: NOT PROCESSED: ${descMap}"
    }
}

boolean isDeviceDisabled(final Map descMap) {
    if (descMap.endpoint == '00') {
        return false
    }
    // get device dni
    String dni = "${device.id}-${descMap.endpoint}"
    ChildDeviceWrapper dw = getChildDevice(dni)
    if (dw == null) {
        return false
    }
    if (dw?.disabled == true) {
        if (traceEnable) { logWarn "isDeviceDisabled: device:${dw} is disabled" }
        return true
    }
    return false
}

void checkStateMachineConfirmation(final Map descMap) {
    if (state['stateMachines'] == null || state['stateMachines']['toBeConfirmed'] == null) {
        return
    }
    List toBeConfirmedList = state['stateMachines']['toBeConfirmed']
    //logTrace "checkStateMachineConfirmation: toBeConfirmedList:${toBeConfirmedList} (endpoint:${descMap.endpoint} clusterInt:${descMap.clusterInt} attrInt:${descMap.attrInt})"
    if (toBeConfirmedList == null || toBeConfirmedList.size() == 0) {
        return
    }
    // toBeConfirmedList first element is endpoint, second is clusterInt, third is attrInt
    if (HexUtils.hexStringToInt(descMap.endpoint) == toBeConfirmedList[0] && descMap.clusterInt == toBeConfirmedList[1] && descMap.attrInt == toBeConfirmedList[2]) {
        logDebug "checkStateMachineConfirmation: endpoint:${descMap.endpoint} cluster:${descMap.cluster} attrId:${descMap.attrId} - <b>CONFIRMED!</b>"
        state['stateMachines']['Confirmation'] = true
    }
}

void parseBatteryEvent(final Map descMap) {
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
        else {
            logTrace "state.states['isRefresh'] = ${state.states['isRefresh']}"
        }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
}

String getClusterName(final String cluster) { return MatterClusters[HexUtils.hexStringToInt(cluster)] ?: UNKNOWN }
String getAttributeName(final Map descMap) { return getAttributeName(descMap.cluster, descMap.attrId) }
String getAttributeName(final String cluster, String attrId) { return getAttributesMapByClusterId(cluster)?.get(HexUtils.hexStringToInt(attrId)) ?: GlobalElementsAttributes[HexUtils.hexStringToInt(attrId)] }
String getFingerprintName(final Map descMap) { return descMap.endpoint == '00' ? 'bridgeDescriptor' : "fingerprint${descMap.endpoint}" }
String getFingerprintName(final Integer endpoint) { return getFingerprintName([endpoint: HexUtils.integerToHexString(endpoint, 1)]) }

String getStateClusterName(final Map descMap) {
    String clusterMapName = ''
    if (descMap.cluster == '001D') {
        clusterMapName = getAttributeName(descMap)
    }
    else {
        clusterMapName = descMap.cluster + '_' + descMap.attrId
    }
}

String getDeviceLabel(final Integer endpoint) { return getDeviceLabel(HexUtils.integerToHexString(endpoint, 1)) }
String getDeviceLabel(final String endpoint) {
    String label = "device#${endpoint} "
    String fingerprintName = getFingerprintName([endpoint: endpoint])
    String vendorName  = state[fingerprintName]?.VendorName ?: ''
    String productName = state[fingerprintName]?.ProductName ?: ''
    String customLabel = state[fingerprintName]?.Label ?: ''
    if (vendorName || productName) {
        label += "(${vendorName} ${productName}) "
    }
    label += customLabel
    return label
}

// 7.13. Global Elements - used for self-description of the server
void parseGlobalElements(final Map descMap) {
    //logTrace "parseGlobalElements: descMap:${descMap}"
    switch (descMap.attrId) {
        case '00FE' :   // FabricIndex          fabric-idx
        case 'FFF8' :   // GeneratedCommandList list[command-id]
        case 'FFF9' :   // AcceptedCommandList  list[command-id]
        case 'FFFA' :   // EventList            list[eventid]
        case 'FFFC' :   // FeatureMap           map32
        case 'FFFD' :   // ClusterRevision      uint16
        case 'FFFB' :   // AttributeList        list[attribid]
            String fingerprintName = getFingerprintName(descMap)
            //String attributeName = getAttributeName(descMap)
            String attributeName = getStateClusterName(descMap)
            String action = 'stored in'
            if (state[fingerprintName] == null) {
                state[fingerprintName] = [:]
            }
            if (state[fingerprintName][attributeName] == null) {
                state[fingerprintName][attributeName] = [:]
                action = 'created in'
            }
            //state[fingerprintName][attributeName] = descMap.value
            state[fingerprintName][attributeName] = descMap.value
            logTrace "parseGlobalElements: cluster: <b>${getClusterName(descMap.cluster)}</b> (0x${descMap.cluster}) attr: <b>${attributeName}</b> (0x${descMap.attrId})  value:${descMap.value} <b>-> ${action}</b> [$fingerprintName][$attributeName]"
            //logTrace "parseGlobalElements: state[${fingerprintName}][${attributeName}] = ${state[fingerprintName][attributeName]}"
            break
        default :
            break   // not a global element
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
    //logTrace"gatherAttributesValuesInfo: cluster:${descMap.cluster} attrInt:${attrInt} attrName:${attrName} value:${descMap.value}"
    if (attrName == null) {
        logWarn "gatherAttributesValuesInfo: unknown attribute # ${attrInt}"
        return
    }
    if (state.states['isInfo'] == true) {
        logTrace "gatherAttributesValuesInfo: <b>isInfo:${state.states['isInfo']}</b> state.states['cluster'] = ${state.states['cluster']} "
        if (state.states['cluster'] == descMap.cluster) {
            if (descMap.value != null && descMap.value != '') {
                tmpStr = "[${descMap.attrId}] ${attrName}"
                if (tmpStr in state.tmp) {  // TODO - seems to be not working ???
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
    else {
        logTrace "gatherAttributesValuesInfo: isInfo:${state.states['isInfo']} descMap:${descMap}"
    }
}

void parseGeneralDiagnostics(final Map descMap) {
    logTrace "parseGeneralDiagnostics: descMap:${descMap}"
    Integer value
    switch (descMap.attrId) {
        case '0001' :   // RebootCount -  a best-effort count of the number of times the Node has rebooted
            value = HexUtils.hexStringToInt(descMap.value)
            sendMatterEvent([attributeName: 'rebootCount', value: value,  description: "${getDeviceLabel(descMap.endpoint)} RebootCount is ${value}"])
            break
        case '0002' :   // UpTime -  a best-effort assessment of the length of time, in seconds,since the Node’s last reboot
            value = HexUtils.hexStringToInt(descMap.value)
            sendMatterEvent([attributeName: 'upTime', value:value,  description: "${getDeviceLabel(descMap.endpoint)} UpTime is ${value} seconds"])
            break
        case '0003' :   // TotalOperationalHours -  a best-effort attempt at tracking the length of time, in hours, that the Node has been operational
            value = HexUtils.hexStringToInt(descMap.value)
            sendMatterEvent([attributeName: 'totalOperationalHours', value: value,  description: "${getDeviceLabel(descMap.endpoint)} TotalOperationalHours is ${value} hours"])
            break
        default :
            if (descMap.attrId != '0000') { if (traceEnable) { logInfo "parse: parseGeneralDiagnostics: ${attrName} = ${descMap.value}" } }
            break
    }
}

void parseBasicInformationCluster(final Map descMap) {
    logTrace "parseBasicInformationCluster: descMap:${descMap}"
    Map eventMap = [:]
    String attrName = getAttributeName(descMap)
    String fingerprintName = getFingerprintName(descMap)
    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }

    switch (descMap.attrId) {
        case '0003' : // productName
            eventMap = [name: 'productName', value:descMap.value, descriptionText: "productName is: ${descMap.value}"]
            state[fingerprintName][attrName] = descMap.value
            break
        case '000A' : // softwareVersionString
            eventMap = [name: 'bridgeSoftwareVersion', value:descMap.value, descriptionText: "bridgeSoftwareVersion is: ${descMap.value}"]
            state[fingerprintName][attrName] = descMap.value
            break
        case '0011' : // reachable
            eventMap = [name: 'reachable', value:descMap.value, descriptionText: "reachable is: ${descMap.value}"]
            state[fingerprintName][attrName] = descMap.value
            break
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            // these are the global elements
            break
        default :
            //if (descMap.attrId != '0000') { if (traceEnable) { logDebug "parse: BasicInformation: ${attrName} = ${descMap.value}" } }
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
    Map eventMap = [:]
    String attrName = getAttributeName(descMap)
    String fingerprintName = getFingerprintName(descMap)
    logTrace "parseBridgedDeviceBasic: attrName:${attrName} fingerprintName:${fingerprintName} descMap:${descMap}"

    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }

    switch (descMap.attrId) {
        case '0001' :   // VendorName
        case '0003' :   // ProductName
        case '0011' :   // Reachable
            state[fingerprintName][attrName] = descMap.value
            if (logEnable) { logInfo "parseBridgedDeviceBasic: ${attrName} = ${descMap.value}" }
            break
        default :
            /*if (descMap.attrId != '0000') {*/ if (traceEnable) { logInfo "parse: BasicInformation: ${attrName} = ${descMap.value}" } //}
            break
    }

    if (eventMap != [:]) {
        eventMap.type = 'physical'; eventMap.isStateChange = true
        if (state.states['isRefresh'] == true) { eventMap.descriptionText += ' [refresh]' }
        sendEvent(eventMap)
        logInfo eventMap.descriptionText
    }
}

void parseDescriptorCluster(final Map descMap) {    // 0x001D Descriptor
    logTrace "parseDescriptorCluster: descMap:${descMap}"
    String attrName = getAttributeName(descMap)    //= DescriptorClusterAttributes[descMap.attrInt as int] ?: GlobalElementsAttributes[descMap.attrInt as int] ?: UNKNOWN
    String endpointId = descMap.endpoint
    String fingerprintName =  getFingerprintName(descMap)  /*"fingerprint${endpointId}"
    if (endpointId == '00') { fingerprintName = 'bridgeDescriptor' }*/

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
            logTrace "parse: Descriptor (${descMap.cluster}): ${attrName} = <b>-> updated state[$fingerprintName][$attrName]</b> to ${descMap.value}"
            if (endpointId == '00' && descMap.cluster == '001D') {
                if (attrName == 'PartsList') {
                    List partsList = descMap.value as List
                    int partsListCount = partsList.size()   // the number of the elements in the partsList
                    int oldCount = device.currentValue('endpointsCount') ?: 0 as int
                    String descriptionText = "Bridge partsListCount is: ${partsListCount}"
                    sendEvent(name: 'endpointsCount', value: partsListCount, descriptionText: descriptionText)
                    logInfo descriptionText
                    if (partsListCount != oldCount) {
                        logWarn "THE NUMBER OF THE BRIDGED DEVICES CHANGED FROM ${oldCount} TO ${partsListCount} !!!"
                    }
                }
            }
            else {
                //logWarn "parseDescriptorCluster: called for endpoint:${endpointId} (attrId:${descMap.attrId})"
            }
            break
        default :
            logTrace "parseDescriptorCluster: Descriptor: ${attrName} = ${descMap.value}"
            break
    }
}

void parseOnOffCluster(final Map descMap) {
    logTrace "parseOnOffCluster: descMap:${descMap}"
    if (descMap.cluster != '0006') {
        logWarn "parseOnOffCluster: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    Integer value

    switch (descMap.attrId) {
        case '0000' : // Switch
            String switchState = descMap.value == '01' ? 'on' : 'off'
            //sendSwitchEvent(descMap.value)
            sendMatterEvent([
                attributeName: 'switch',
                value: switchState,
                description: "${getDeviceLabel(descMap.endpoint)} switch is ${switchState}"
            ], descMap)
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
            logTrace "parse: Switch: ${attrName} = ${descMap.value}"
            break
        default :
            logWarn "parseOnOffCluster: unexpected attrId:${descMap.attrId} (raw:${descMap.value})"
    }
    //parseOtherGlobalElements(descMap)
}

// Method for parsing occupancy sensing
void parseOccupancySensing(final Map descMap) {
    if (descMap.cluster != '0406') {
        logWarn "parseOccupancySensing: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    String motionAttr = descMap.value == '01' ? 'active' : 'inactive'
    if (descMap.attrId == '0000') { // Occupancy
        sendMatterEvent([
            attributeName: 'motion',
            value: motionAttr,
            description: "${getDeviceLabel(descMap.endpoint)} motion is ${motionAttr}"
        ], descMap)
    } else {
        logTrace "parseOccupancySensing: ${(OccupancySensingClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
//    parseOtherGlobalElements(descMap)
}

// Method for parsing temperature measurement
void parseTemperatureMeasurement(final Map descMap) { // 0402
    if (descMap.cluster != '0402') {
        logWarn "parseTemperatureMeasurement: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    if (descMap.attrId == '0000') { // Temperature
//        parseOtherGlobalElements(descMap)
        Double valueInt = HexUtils.hexStringToInt(descMap.value) / 100.0
        sendMatterEvent([
            attributeName: 'temperature',
            value: valueInt.toString(),
            description: "device #${descMap.endpoint} temperature is ${valueInt} °C"
        ], descMap)
    } else {
        logTrace "parseTemperatureMeasurement: ${(TemperatureMeasurementClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
        logTrace "parseTemperatureMeasurement: ${getAttributeName(descMap)} = ${descMap.value}"
    }
//    parseOtherGlobalElements(descMap)
}

// Method for parsing humidity measurement
void parseHumidityMeasurement(final Map descMap) { // 0405
    if (descMap.cluster != '0405') {
        logWarn "parseHumidityMeasurement: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    if (descMap.attrId == '0000') { // Humidity
        Double valueInt = HexUtils.hexStringToInt(descMap.value) / 100.0
        sendMatterEvent([
            attributeName: 'humidity',
            value: valueInt.toString(),
            description: "device #${descMap.endpoint} humidity is ${valueInt} %"
        ], descMap)
    } else {
        logTrace "parseHumidityMeasurement: ${(RelativeHumidityMeasurementClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
//    parseOtherGlobalElements(descMap)
}

void parseWindowCovering(final Map descMap) { // 0102
    if (descMap.cluster != '0102') {
        logWarn "parseWindowCovering: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    if (descMap.attrId == '000B') { // TargetPositionLiftPercent100ths  - actually this is the current position !!!
        Integer valueInt = (100 - HexUtils.hexStringToInt(descMap.value) / 100.0) as int
        sendMatterEvent([
            attributeName: 'position',
            value: valueInt.toString(),
            description: "device #${descMap.endpoint} currentPosition  is ${valueInt} %"
        ], descMap)
    } else if (descMap.attrId == '000E') { // CurrentPositionLiftPercent100ths - actually this is the target position !!!
        Integer valueInt = (100 - HexUtils.hexStringToInt(descMap.value) / 100.0) as int
        sendMatterEvent([
            attributeName: 'targetPosition',
            value: valueInt.toString(),
            description: "device #${descMap.endpoint} targetPosition is ${valueInt} %"
        ], descMap)
    } else if (descMap.attrId == '000A') { // OperationalStatus
        sendMatterEvent([
            attributeName: 'operationalStatus',
            value: descMap.value,
            description: "device #${descMap.endpoint} operationalStatus is ${descMap.value}"
        ], descMap)
    }
    else {
        logTrace "parseWindowCovering: ${(WindowCoveringClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
//    parseOtherGlobalElements(descMap)
}

//events

// Common code method for sending events
void sendMatterEvent(final Map<String, String> eventParams, Map descMap = [:]) {
    String attributeName = eventParams['attributeName']
    String value = eventParams['value']
    String description = eventParams['description']

    String dni = ''
    // get the dni from the descMap eddpoint
    if (descMap != [:]) {
        dni = "${device.id}-${descMap.endpoint}"
    }
    ChildDeviceWrapper dw = getChildDevice(dni) // null if dni is null for the parent device
    Map eventMap = [name: attributeName, value: value, descriptionText: description, type: 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force the event to be sent
    }
    // TODO - use the child device wrapper to check the current value !!!!!!!!!!!!!!!!!!!!!
    /*
    if (device.currentValue(attributeName) == value && state.states['isRefresh'] != true) {
        logDebug "ignored duplicated ${attributeName} event, value:${value}"
        return
    }
    */
    if (dw != null) {
        // send events to child for parsing. Any filtering of duplicated events will be potentially done in the child device handler.
        logDebug "sendMatterEvent: sending for parsing to the child device: dw:${dw} dni:${dni} attributeName:${attributeName} value:${value} description:${description}"
        dw.parse([eventMap])
    } else {
        // send events to parent for parsing
        logDebug "sendMatterEvent: sending parent event: dw:${dw} dni:${dni} attributeName:${attributeName} value:${value} description:${description}"
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"       // logs are always sent to the parent device, when using system drivers :(
    }
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

void logRequestedClusterAttrResult(final Map data) {
    logDebug "logRequestedClusterAttrResult: data:${data}"
    String cluster = HexUtils.integerToHexString(data.cluster, 2)
    String endpoint = HexUtils.integerToHexString(data.endpoint as Integer, 1)
    String clusterAttr = "Cluster <b>${MatterClusters[data.cluster]}</b> (0x${cluster}) endpoint <b>0x${endpoint}</b> attributes and values list (0xFFFB)"
    String logMessage = state.tmp != null ? "${clusterAttr} : <br>${state.tmp}" : "${clusterAttr} <b>timeout</b>! :("

    state.tmp = null
    state.states = state.states ?: [:]
    state.states['isInfo'] = false
    state.states['cluster'] = null

    logInfo logMessage
}

/**
 * Requests the list of attributes for a Matter cluster (reads the 0xFFFB attribute of the cluster and endpoint in the data map)
 */
void requestMatterClusterAttributesList(final Map data) {
    state.states = state.states ?: [:]
    state.states['isInfo'] = true
    state.states['cluster'] = HexUtils.integerToHexString(data.cluster, 2)
    state.tmp = null

    Integer endpoint = data.endpoint as Integer
    Integer cluster = data.cluster as Integer

    logDebug "Requesting Attribute List for Cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) endpoint <b>0x${HexUtils.integerToHexString(endpoint, 1)}</b> attributes list ..."

    List<Map<String, String>> attributePaths = [matter.attributePath(endpoint, cluster, 0xFFFB)]
    sendToDevice(matter.readAttributes(attributePaths))
}

/**
 * Requests the values of ALL ATRIBUTES for a Matter cluster and endpoint
 */
void requestMatterClusterAttributesValues(final Map data) {
    Integer endpoint = data.endpoint as Integer
    Integer cluster  = data.cluster  as Integer
    List<Map<String, String>> serverList = []
    String fingerprintName = getFingerprintName(endpoint)
    if (state[fingerprintName] == null) {
        logWarn "requestMatterClusterAttributesValues: state.${fingerprintName} is null !"
        return
    }
    logDebug "Requesting Attributes Values for endpoint <b>0x${HexUtils.integerToHexString(endpoint, 1)}</b>  cluster <b>${MatterClusters[data.cluster]}</b> (0x${HexUtils.integerToHexString(data.cluster, 2)}) attributes values ..."

    String listMapName = ''
    if (cluster == 0x001D) {
        listMapName = 'AttributeList'
    }
    else {
        listMapName = HexUtils.integerToHexString(data.cluster, 2) + '_' + 'FFFB'
    }

    attributeList = state[fingerprintName][listMapName]

    logDebug "requestMatterClusterAttributesValues: (requesting cluster 0x${HexUtils.integerToHexString(data.cluster, 2)}) fingerprintName=${fingerprintName} listMapName=${listMapName} attributeList=${attributeList}"
    if (attributeList == null) {
        logWarn 'requestMatterClusterAttributesValues: attrListString is null'
        return
    }
    logDebug "requestMatterClusterAttributesValues: cluster ${MatterClusters[data.cluster]} (0x${HexUtils.integerToHexString(data.cluster, 2)}) attributeList:${attributeList}"
    List<Map<String, String>> attributePaths = attributeList.collect { attr ->
        Integer attrInt = HexUtils.hexStringToInt(attr)
        if (attrInt == 0x0040 || attrInt == 0x0041) {
            logDebug "requestMatterClusterAttributesValues: skipping attribute 0x${HexUtils.integerToHexString(attrInt, 2)} (${attrInt})"
            return
        }
        matter.attributePath(endpoint, cluster, attrInt)
    }.findAll()
    if (!attributePaths.isEmpty()) {
        sendToDevice(matter.readAttributes(attributePaths))
    }
}

/**
 * Requests, collects and logs all attribute values for a given endpoint and cluster.
 */
void requestAndCollectAttributesValues(Integer endpoint, Integer cluster, Integer time=1, boolean fast=false) {
    state.states['isPing'] = false
    runIn((time as int) ?: 1,              requestMatterClusterAttributesList,   [overwrite: false, data: [endpoint: endpoint, cluster: cluster] ])
    runIn((time as int) + (fast ? 2 : 3),  requestMatterClusterAttributesValues, [overwrite: false, data: [endpoint: endpoint, cluster: cluster] ])
    runIn((time as int) + (fast ? 6 : 12), logRequestedClusterAttrResult,        [overwrite: false, data: [endpoint: endpoint, cluster: cluster] ])
}

void scheduleRequestAndCollectServerListAttributesList(String endpointPar = '00', Integer time=1, boolean fast=false) {
    state.states['isPing'] = false
    runIn((time as int) ?: 1, requestAndCollectServerListAttributesList,   [overwrite: false, data: [endpointPar: endpointPar] ])
}

void requestAndCollectServerListAttributesList(Map data)
{
    Integer endpoint = safeNumberToInt(data.endpointPar)
    String fingerprintName = getFingerprintName(endpoint)
    List<String> serverList = state[fingerprintName]?.ServerList
    logDebug "requestAndCollectServerListAttributesList(): serverList:${serverList} endpoint=${endpoint} getFingerprintName = ${fingerprintName}"
    if (serverList == null) {
        logWarn 'requestAndCollectServerListAttributesList(): serverList is null!'
        return
    }
    serverList.each { cluster ->
        Integer clusterInt = HexUtils.hexStringToInt(cluster)
        //logTrace "requestAndCollectServerListAttributesList(): endpointInt:${endpoint} (0x${HexUtils.integerToHexString(safeToInt(endpoint), 1)}),  clusterInt:${clusterInt} (0x${cluster})"
        readAttribute(endpoint, clusterInt, 0xFFFB)
    }
}


void readAttributeSafe(String endpointPar, String clusterPar, String attrIdPar) {
    Integer endpointInt = safeNumberToInt(endpointPar)
    Integer clusterInt  = safeNumberToInt(clusterPar)
    Integer attrInt     = safeNumberToInt(attrIdPar)
    String  endpointId  = HexUtils.integerToHexString(endpointInt, 1)
    String  clusterId   = HexUtils.integerToHexString(clusterInt, 2)
    String  attrId      = HexUtils.integerToHexString(attrInt, 2)
    logDebug "readAttributeSafe(endpoint:${endpointId}, cluster:${clusterId}, attribute:${attrId}) -> starting readSingeAttrStateMachine!"

    readSingeAttrStateMachine([action: START, endpoint: endpointInt, cluster: clusterInt, attribute: attrInt])

}

/* 
 *  Discover all the endpoints and clusters for the Bridge and all the Bridged Devices
 */
void a0DiscoverAll(statePar = null) {
    logWarn "a0DiscoverAll()"
    Integer stateSt = DISCOVER_ALL_STATE_INIT
    state.stateMachines = [:]
    // ['All', 'BasicInfo', 'PartsList']]
    if (statePar == 'All') { stateSt = DISCOVER_ALL_STATE_INIT }
    else if (statePar == 'BasicInfo') { stateSt = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST }
    else if (statePar == 'PartsList') { stateSt = DISCOVER_ALL_STATE_GET_PARTS_LIST_START }
    else if (statePar == 'SupportedClusters') { stateSt = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START }
    else {
        logWarn "a0DiscoverAll(): unknown statePar:${statePar} !"
        return
    }

    discoverAllStateMachine([action: START, goToState: stateSt])
    logWarn "a0DiscoverAll(): started!"
}

void collectBasicInfo(Integer endpoint = 0, Integer timePar = 1, boolean fast = false) {
    Integer time = timePar
    // first thing to do is to read the Bridge (ep=0) Descriptor Cluster (0x001D) attribute 0XFFFB and store the ServerList in state.bridgeDescriptor['ServerList']
    // also, the DeviceTypeList ClientList and PartsList are stored in state.bridgeDescriptor
    requestAndCollectAttributesValues(endpoint, cluster = 0x001D, time, fast)  // Descriptor Cluster - DeviceTypeList, ServerList, ClientList, PartsList

    // next - fill in all the ServerList clusters attributes list in the fingerprint
    time += fast ? SHORT_TIMEOUT : LONG_TIMEOUT
    scheduleRequestAndCollectServerListAttributesList(endpoint.toString(), time, fast)

    // collect the BasicInformation Cluster attributes
    time += fast ? SHORT_TIMEOUT : LONG_TIMEOUT
    String fingerprintName = getFingerprintName(endpoint)
    if (state[fingerprintName] == null) {
        logWarn "collectBasicInfo(): state.${fingerprintName} is null !"
        state[fingerprintName]
        return
    }
    List<String> serverList = state[fingerprintName]['ServerList']
    logDebug "collectBasicInfo(): endpoint=${endpoint}, fingerprintName=${fingerprintName}, serverList=${serverList} "

    if (endpoint == 0) {
        /* groovylint-disable-next-line ConstantIfExpression */
        if ('28' in serverList) {
            requestAndCollectAttributesValues(endpoint, cluster = 0x0028, time, fast) // Basic Information Cluster
            time += fast ? SHORT_TIMEOUT : LONG_TIMEOUT
        }
        else {
            logWarn "collectBasicInfo(): BasicInformationCluster 0x0028 endpoint:${endpoint} is <b>not in the ServerList !</b>"
        }
    }
    else {
        if ('39' in serverList) {
            requestAndCollectAttributesValues(endpoint, cluster = 0x0039, time, fast) // Bridged Device Basic Information Cluster
            time += fast ? SHORT_TIMEOUT : LONG_TIMEOUT
        }
        else {
            logWarn "collectBasicInfo(): BridgedDeviceBasicInformationCluster 0x0039 endpoint:${endpoint} is <b>not in the ServerList !</b>"
        }
    }
    runIn(time as int, 'delayedInfoEvent', [overwrite: true, data: [info: 'Basic Bridge Discovery finished', descriptionText: '']])
}

void requestExtendedInfo(Integer endpoint = 0, Integer timePar = 15, boolean fast = false) {
    Integer time = timePar
    List<String> serverList = state[getFingerprintName(endpoint)]?.ServerList
    logWarn "requestExtendedInfo(): serverList:${serverList} endpoint=${endpoint} getFingerprintName = ${getFingerprintName(endpoint)}"
    if (serverList == null) {
        logWarn 'getInfo(): serverList is null!'
        return
    }
    serverList.each { cluster ->
        Integer clusterInt = HexUtils.hexStringToInt(cluster)
        if (endpoint != 0 && (clusterInt in [0x2E, 0x41])) {
            logWarn "requestExtendedInfo(): skipping endpoint ${endpoint}, cluster:${clusterInt} (0x${cluster}) - KNOWN TO CAUSE Zemismart M1 to crash !"
            return
        }
        logDebug "requestExtendedInfo(): endpointInt:${endpoint} (0x${HexUtils.integerToHexString(safeToInt(endpoint), 1)}),  clusterInt:${clusterInt} (0x${cluster}),  time:${time}"
        requestAndCollectAttributesValues(endpoint, clusterInt, time, fast = false)
        time += fast ? SHORT_TIMEOUT : LONG_TIMEOUT
    }

    runIn(time, 'delayedInfoEvent', [overwrite: true, data: [info: 'Extended Bridge Discovery finished', descriptionText: '']])
    logDebug "requestExtendedInfo(): jobs scheduled for total time: ${time} seconds"
}

void a1BridgeDiscovery() {
    //getInfo('Extended', endpointPar = '0')
    getInfo('Basic', endpointPar = '00')
}

void getInfo(String infoType, String endpointPar = '00') {
    Integer endpoint = safeNumberToInt(endpointPar)
    logDebug "getInfo(${infoType}, ${endpoint})"
    unschedule('requestMatterClusterAttributesList')
    unschedule('requestMatterClusterAttributesValues')
    unschedule('logRequestedClusterAttrResult')

    sendInfoEvent("starting ${endpoint == 0 ? 'Bridge' : 'Devices'} discovery ...")

    if (infoType == 'Basic') {
        logDebug "getInfo(): 'Basic' type"
        collectBasicInfo(endpoint, time = 1, fast = true)
        return
    }
    if (infoType == 'Extended') {
        logDebug "getInfo(): 'Extended' type"
        collectBasicInfo(endpoint, time = 1, fast = true)  // not needed, should already have it!
        requestExtendedInfo(endpoint, timePar = 8, fast = true)
        return
    }
}

void a2DevicesDiscovery() {
    logDebug 'a2DevicesDiscovery()'
    getPartsList()
}

void getPartsList() {
    logDebug 'getPartsList()'
    if (state.bridgeDescriptor == null) {
        logWarn 'getPartsList(): state.bridgeDescriptor is null !'
        return
    }
    sendInfoEvent('starting Devices discovery ...')
    Integer time = 2
    // for each bridged device endpoint in the state.bridgeDescriptor['PartsList'] we need to read the ServerList
    logDebug "getPartsList(): state.bridgeDescriptor['PartsList'] = ${state.bridgeDescriptor['PartsList']}"
    String fingerprintName
    Integer ctr = 0
    state.bridgeDescriptor['PartsList'].each { endpointId ->
        Integer endpoint = HexUtils.hexStringToInt(endpointId)
        //   PartsList=[01, 06, 07, 08, 09, 0A, 0B]
        fingerprintName = getFingerprintName([endpoint: endpointId])
        if (state[fingerprintName] == null) {
            state[fingerprintName] = [:]
        }
        if (ctr < MAX_DEVICES_LIMIT) {
            logDebug "getPartsList(): endpoint:${endpoint} time:${time}"
            requestAndCollectAttributesValues(endpoint, 0x001D, time, fast = true)       // Descriptor Cluster -> fill in the ServerList for each endpoint
            time += SHORT_TIMEOUT
            ctr ++
        }
        else {
            logWarn "getPartsList(): skipping endpoint:${endpoint} exceeded maxumum ctr${ctr}"
        }
    }

    // We should have the ServerList for each child device now - state.fingerprintXX['ServerList']
    //
    // for each child device that has the BridgedDeviceBasicInformationCluster '39' in the ServerList ->  read the BridgedDeviceBasicInformationCluster attributes
    ctr = 0
    state.bridgeDescriptor['PartsList'].each { endpointId ->
        Integer endpoint = HexUtils.hexStringToInt(endpointId)
        fingerprintName = getFingerprintName([endpoint: endpointId])
        if (state[fingerprintName] == null) {
            logDebug "getPartsList(): skipping -> fingerprintName for endpointId ${endpointId} is null !"
            return
        }
        // check if there is a 0039 map entry in the fingerprint
        List<Map<String, String>> bridgedDeviceBasicInformationList = []
        List<String> serverList = state[fingerprintName]['ServerList']
        bridgedDeviceBasicInformationList = state[fingerprintName]['0039']
        if (bridgedDeviceBasicInformationList == null) {
            logDebug "getPartsList(): state.${fingerprintName}['0039'] is null -> check if we can read it?"
            // check if cluster 39 is in the ServerList
            if (serverList == null) {
                logDebug "getPartsList(): state.${fingerprintName}['ServerList'] is null !"
                return
            }
            if (!serverList.contains('39')) {
                logDebug "getPartsList(): state.${fingerprintName}['ServerList']=${serverList} does not contain 39 !"
                return
            }
            // we can read the 0x0039 attributes
            if (ctr < MAX_DEVICES_LIMIT) {
                logDebug "getPartsList(): state.${fingerprintName}['ServerList'] contains 39 -> read the attributes"
                requestAndCollectAttributesValues(endpoint, 0x0039, time, fast = true)       // BridgedDeviceBasic Cluster
                time += SHORT_TIMEOUT
                ctr ++
            }
            else {
                logWarn "getPartsList(): skipping endpoint:${endpoint} exceeded maxumum ctr${ctr}"
            }
        }
        else {
            logDebug "getPartsList(): state.${fingerprintName}['0039'] = ${bridgedDeviceBasicInformationList}"
            //return  // we already have the 0x0039 map entry in the fingerprint - coninue with the clusters in the serverList
        }

        logInfo "getPartsList(): examine serverList ${serverList} for device #${endpoint}"
        // next step is for each child device that has the BridgedDeviceBasicInformationCluster 39 in the ServerList ->  check the ServerList for useful clusters ..
        ctr = 0
        serverList.each { cluster ->
            Integer clusterInt = HexUtils.hexStringToInt(cluster)
            if (cluster in ['0006', '0102', '0400', '0405', '0406']) {
                logDebug "getPartsList(): TODO : read device# ${endpoint} clusterInt ${clusterInt} (0x${cluster}) time:${time}"
                if (ctr < MAX_DEVICES_LIMIT) {
                    requestAndCollectAttributesValues(endpoint, clusterInt, time, fast = true)       // BridgedDeviceBasic Cluster
                    time += SHORT_TIMEOUT
                    ctr ++
                }  else {
                    logWarn "getPartsList(): skipping endpoint:${endpoint} exceeded maxumum ctr${ctr}"
                }
            }
            else {
                logDebug "getPartsList(): skipping cluster 0x${cluster} for device #${endpoint}"
            }
        }
    }
    runIn(time, 'delayedInfoEvent', [overwrite: true, data: [info: 'Devices Discovery finished', descriptionText: '']])
    logDebug "getPartsList(): jobs scheduled for total time:${time} seconds"
}

void a3CapabilitiesDiscovery() {
    logWarn 'a3CapabilitiesDiscovery()'
    sendInfoEvent('starting Capabilities discovery ...')
    Integer time = 1
    // for each finferprint in the state observe the ServerList for known clusters

    state.each { fingerprintName, fingerprintMap ->
        if (fingerprintName.startsWith('fingerprint')) {
            logTrace "a3CapabilitiesDiscovery(): fingerprintName:${fingerprintName} fingerprintMap:${fingerprintMap}"
            if (fingerprintMap['ServerList'] != null) {
                fingerprintMap['ServerList'].each { cluster ->
                    Integer clusterInt = HexUtils.hexStringToInt(cluster)
                    // the endpoint is the rightmost digit of the fingerprintName in hex
                    String endpointId = fingerprintName.substring(fingerprintName.length() - 2, fingerprintName.length())
                    Integer endpointInt = HexUtils.hexStringToInt(endpointId)
                    if (clusterInt in SupportedMatterClusters) {
                        logDebug "a3CapabilitiesDiscovery(): found fingerprintName:${fingerprintName} endpointInt:${endpointInt} (0x${endpointId})  clusterInt:${clusterInt} (0x${cluster}) time:${time}"
                        requestAndCollectAttributesValues(endpointInt, clusterInt, time, fast = true)
                        time += SHORT_TIMEOUT
                    }
                    else {
                        logTrace "a3CapabilitiesDiscovery(): skipping cluster 0x${cluster} for fingerprintName:${fingerprintName}"
                    }
                }
            } else {
                logDebug "a3CapabilitiesDiscovery(): fingerprintName:${fingerprintName} fingerprintMap:${fingerprintMap} does not have ServerList"
            }
        }
    }
    runIn(time, 'delayedInfoEvent', [overwrite: true, data: [info: 'Devices Capabilities discovery finished', descriptionText: '']])
    logDebug "a3CapabilitiesDiscovery(): jobs scheduled for total time:${time} seconds"
}

void a5SubscribeKnownClustersAttributes() {
    logDebug 'a5SubscribeKnownClustersAttributes()'
    sendInfoEvent('Subscribing for known clusters attributes reporting ...')
    // subscribe to the Descriptor cluster PartsList attribute
    subscribe('add', 0, 0x001D, 0x0003)

    // For each fingerprint in the state, check if the fingerprint has entries in the SupportedMatterClusters list. Then, add these entries to the state.subscriptions map
    Integer deviceCount = 0
    //Map stateCopy = state.clone()
    Map stateCopy = state
    state.each { fingerprintName, fingerprintMap ->
        logDebug "a3CapabilitiesDiscovery(): fingerprintName:${fingerprintName} fingerprintMap:${fingerprintMap}"
        if (fingerprintName.startsWith('fingerprint')) {
            boolean knownClusterFound = false
            List serverList = fingerprintMap['ServerList'] as List
            serverList.each { entry  ->
                if (safeHexToInt(entry) in SupportedMatterClusters.keySet()) {
                    // fingerprintName:fingerprint07 entry:0402 map:[FFF8:1618, FFF9:1618, 0002:2710, 0000:092A, 0001:EC78, FFFC:00, FFFD:04]
                    String endpointId = fingerprintName.substring(fingerprintName.length() - 2, fingerprintName.length())
                    logDebug "a3CapabilitiesDiscovery(): fingerprintName:${fingerprintName} endpointId:${endpointId} entry:${entry}"
                    // for now, we subscribe to attribute 0x0000 of the cluster, except for teh WindowCovering cluster, where we subscribe to attributes 0x000B and 0x000E
                    if (entry == '0102') {
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('000B'))
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('000E'))
                    }
                    else {
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('0000'))
                    }
                    knownClusterFound = true
                }
            }
            if (knownClusterFound) {
                deviceCount ++
            }
        }
    }
    //sendMatterEvent([attributeName: 'deviceCount', value: deviceCount.toString(), description: "number of devices exposing known clusters is ${deviceCount}"])
    int numberOfSubscriptions = state.subscriptions?.size() ?: 0
    sendInfoEvent("the number of subscriptions is ${numberOfSubscriptions}")
    runIn(1, 'delayedInfoEvent', [overwrite: true, data: [info: 'Subscribing finished', descriptionText: '']])
}

void a4CreateChildDevices() {
    logDebug 'a4CreateChildDevices()'
    sendInfoEvent('Creating child devices ...')
    Integer childDevicesCreated = 0
    childDevicesCreated = createChildDevices()
    String info = "Completed, created ${childDevicesCreated} child devices!"
    sendInfoEvent(info)
}

void readAttribute(Integer endpoint, Integer cluster, Integer attrId) {
    List<Map<String, String>> attributePaths = [matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer)]
    sendToDevice(matter.readAttributes(attributePaths))
}

void readAttribute(String endpointPar, String clusterPar, String attrIdPar) {
    Integer endpoint = safeNumberToInt(endpointPar)
    Integer cluster = safeNumberToInt(clusterPar)
    Integer attrId = safeNumberToInt(attrIdPar)
    logDebug "readAttribute(endpoint:${endpoint}, cluster:${cluster}, attrId:${attrId})"
    readAttribute(endpoint, cluster, attrId)
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
    if (settings.logEnable)   { runIn(86400, logsOff) }   // 24 hours
    if (settings.traceEnable) { logTrace settings; runIn(20000, traceOff) }   // 1800 = 30 minutes

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        // schedule the periodic timer
        final int interval = (settings.healthCheckInterval as Integer) ?: 0
        if (interval > 0) {
            logTrace "healthMethod=${healthMethod} interval=${interval}"
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method"
            scheduleDeviceHealthCheck(interval, healthMethod)
        }
    }
    else {
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod
        log.info 'Health Check is disabled!'
    }
}

// delete all Preferences
void deleteAllSettings() {
    settings.each { it ->
        logDebug "deleting ${it.key}"
        device.removeSetting("${it.key}")
    }
    logInfo  'All settings (preferences) DELETED'
}

// delete all attributes
void deleteAllCurrentStates() {
    device.properties.supportedAttributes.each { it ->
        logDebug "deleting $it"
        device.deleteCurrentState("$it")
    }
    logInfo 'All current states (attributes) DELETED'
}

// delete all State Variables
void deleteAllStates() {
    state.each { it ->
        logDebug "deleting state ${it.key}"
    }
    state.clear()
    logInfo 'All States DELETED'
}

void deleteAllScheduledJobs() {
    unschedule()
    logInfo 'All scheduled jobs DELETED'
}

void deleteAllChildDevices() {
    logWarn 'deleteAllChildDevices : not implemented!'
}

void loadAllDefaults() {
    logWarn 'loadAllDefaults() !!!'
    deleteAllSettings()
    deleteAllCurrentStates()
    deleteAllScheduledJobs()
    deleteAllStates()
    deleteAllChildDevices()
    initializeVars(fullInit = true)
    //initialize()
    //configure()
    updated()
    sendInfoEvent('All Defaults Loaded! F5 to refresh')
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
    sendMatterEvent([attributeName: 'initializeCtr', value: state.stats['initializeCtr'].toString(), description: "initializeCtr is ${state.stats['initializeCtr']}", type: 'digital'])
    scheduleCommandTimeoutCheck(delay = 30)
    subscribe()
}

void clearStates() {
    logWarn 'clearStates()...'
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

void removeAllSubscriptions() {
    logWarn 'removeAllSubscriptions() ...'
    state.subscriptions = []
    unsubscribe()
    sendInfoEvent('all subsciptions are removed!', 're-discover the deices again ...')
}

void subscribe(String addOrRemove, String endpointPar=null, String clusterPar=null, String attrIdPar=null) {
    Integer endpoint = safeNumberToInt(endpointPar)
    Integer cluster = safeNumberToInt(clusterPar)
    Integer attrId = safeNumberToInt(attrIdPar)
    subscribe(addOrRemove, endpoint, cluster, attrId)
}

void subscribe(String addOrRemove, Integer endpoint, Integer cluster, Integer attrId) {
    String cmd = ''
    logDebug "subscribe(action: ${addOrRemove} endpoint:${endpoint}, cluster:${cluster}, attrId:${attrId})"
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer))
    // format EP_CLUSTER_ATTRID
    List<String> newSub = [endpoint, cluster, attrId]
    List<List<String>> subscriptions = state.subscriptions ?: []
    if (addOrRemove == 'add') {
        if (subscriptions.contains(newSub)) {
            logDebug "subscribe(): subscription already exists: ${newSub}"
        } else {
            logDebug "subscribe(): adding subscription: ${newSub}"
            cmd = matter.subscribe(0, 0xFFFF, attributePaths)
            sendToDevice(cmd)
            subscriptions.add(newSub)
            state.subscriptions = subscriptions
        }
    }
    else if (addOrRemove == 'remove') {
        if (subscriptions.contains(newSub)) {
            subscriptions.remove(newSub)
            state.subscriptions = subscriptions
        } else {
            logWarn "subscribe(): subscription not found!: ${newSub}"
        }
    }
    else if (addOrRemove == 'show') {
        if (logEnable) { logInfo "subscribe(): state.subscriptions size is ${state.subscriptions?.size()}" }
    }
    else {
        logWarn "subscribe(): unknown action: ${addOrRemove}"
    }
    if (logEnable) { logInfo "subscribe(): state.subscriptions = ${state.subscriptions}" }
}

void subscribe() {
    sendInfoEvent('subscribe()...Please wait.', 'sent device subscribe command')
    String cmd = subscribeCmd()
    if (cmd != null && cmd != '') {
        logDebug "subscribe(): cmd = ${cmd}"
        sendToDevice(cmd)
    }
}

String subscribeCmd() {
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0, 0x001D, 0x03))   // Descriptor Cluster - PartsList
    attributePaths.addAll(state.subscriptions?.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    })
    if (attributePaths.isEmpty()) {
        logWarn 'subscribe(): attributePaths is empty!'
        return null
    }
    return matter.subscribe(0, 0xFFFF, attributePaths)
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

void setSwitch(String commandPar, String deviceNumberPar/*, extraPar = null*/) {
    String command = commandPar.strip()
    Integer deviceNumber
    logDebug "setSwitch() command: ${command}, deviceNumber:${deviceNumberPar}, extraPar:${extraPar}"
    deviceNumber = safeNumberToInt(deviceNumberPar)
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) {
        logWarn "setSwitch(): deviceNumber ${deviceNumberPar} is not valid!"
        return
    }
    String fingerprintName = getFingerprintName(deviceNumber)
    if (fingerprintName == null || state[fingerprintName] == null) {
        logWarn "setSwitch(): fingerprintName '${fingerprintName}' is not valid! (${getDeviceLabel(deviceNumber)})"
        return
    }
    String cluster = '0006'
    // list key is 0006_FFFB=[00, FFF8, FFF9, FFFB, FFFD, FFFC]
    String stateClusterName = getStateClusterName([cluster: cluster, attrId: 'FFFB'])
    logDebug "setSwitch(): fingerprintName = ${fingerprintName}, stateClusterName = ${stateClusterName}"

    List<String> onOffClusterAttributesList = state[fingerprintName][stateClusterName] as List
    logDebug "setSwitch(): onOffClusterAttributesList = ${onOffClusterAttributesList}"
    if (onOffClusterAttributesList == null) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceLabel(deviceNumber)} !"
        return
    }
    // check if '00' is in the onOffClusterAttributesList
    if (!onOffClusterAttributesList.contains('00')) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceLabel(deviceNumber)} !"
        return
    }
    if (onOffCommandsList == null) {
        logWarn "setSwitch(): OnOff commands not discovered for ${getDeviceLabel(deviceNumber)} !"
        return
    }
    // find the command in the OnOffClusterCommands map
    logDebug "setSwitch(): command = ${command}"
    Integer onOffcmd = OnOffClusterCommands.find { k, v -> v == command }?.key
    logDebug "setSwitch(): command = ${command}, onOffcmd = ${onOffcmd}, onOffCommandsList = ${onOffCommandsList}"
    if (onOffcmd == null) {
        logWarn "setSwitch(): command '${command}' is not valid for ${getDeviceLabel(deviceNumber)} !"
        return
    }

    String cmd = ''
    switch (command) {
        case ['Off', 'On', 'Toggle']:
            cmd = matter.invoke(deviceNumber, 0x0006, onOffcmd)
            break
        case 'OffWithEffect':
            cmd = matter.invoke(deviceNumber, 0x0006, 0x0040)
            break
        case 'OnWithRecallGlobalScene':
            cmd = matter.invoke(deviceNumber, 0x0006, 0x0041)
            break
        case 'OnWithTimedOff':
            cmd = matter.invoke(deviceNumber, 0x0006, 0x0042)
            break
        default:
            logWarn "setSwitch(): command '${command}' is not valid!"
            return
    }
    logInfo "setSwitch(): sending command '${cmd}'"
    sendToDevice(cmd)
}

void refresh() {
    logInfo'refresh() ...'
    checkDriverVersion()
    setRefreshRequest()    // 6 seconds
    sendToDevice(refreshCmd())
}

String refreshCmd() {
    List<Map<String, String>> attributePaths = state.subscriptions?.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    } ?: []

    //if ('33' in state.bridgeDescriptor?.ServerList) {
    attributePaths.add(matter.attributePath(0, 0x0033, 0x01))   // rebootCount
        // TODO - check if the attribute is within the 0033 attribute list !!
        //attributePaths.add(matter.attributePath(0, 0x0033, 0x02))   // upTime
        //attributePaths.add(matter.attributePath(0, 0x0033, 0x03))   // totalOperationalHours
    //}
    return matter.readAttributes(attributePaths)
}

void logsOff() {
    log.warn 'debug logging disabled...'
    device.updateSetting('logEnable', [value:'false', type:'bool'])
}

void traceOff() {
    logInfo 'trace logging disabled...'
    device.updateSetting('traceEnable', [value: 'false', type: 'bool'])
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

/* ============================= Child Devices code ================================== */
/* code segments 'borrowed' from Jonathan's 'Tuya IoT Platform (Cloud)' driver importUrl: 'https://raw.githubusercontent.com/bradsjm/hubitat-drivers/main/Tuya/TuyaOpenCloudAPI.groovy' */

// Json Parsing Cache

// Tuya Function Categories  TODO - refactor or remove !!!!
@Field static final Map<String, List<String>> tuyaFunctions = [
    'battery'        : [ 'battery_percentage', 'va_battery' ],
    'brightness'     : [ 'bright_value', 'bright_value_v2', 'bright_value_1', 'bright_value_2' ],
    'co'             : [ 'co_state' ],
    'co2'            : [ 'co2_value' ],
    'colour'         : [ 'colour_data', 'colour_data_v2' ],
    'contact'        : [ 'doorcontact_state' ],
    'ct'             : [ 'temp_value', 'temp_value_v2' ],
    'control'        : [ 'control', 'mach_operate' ],
    'fanSpeed'       : [ 'fan_speed_enum', 'fan_speed' ],
    'fanSwitch'      : [ 'switch_fan', 'switch' ],
    'light'          : [ 'switch_led', 'switch_led_1', 'switch_led_2', 'light' ],
    'humiditySet'    : [ 'dehumidify_set_value' ],                                                                                       /* Inserted by SJB */
    'humiditySpeed'  : [ 'fan_speed_enum' ],
    'humidity'       : [ 'temp_indoor', 'swing', 'shake', 'child_lock', 'lock', 'fan_speed_enum', 'dehumidify_set_value', 'humidity_indoor', 'humidity', 'envhumid', 'switch', 'mode', 'anion', 'pump', 'dry', 'windspeed', 'countdown', 'countdown_left', 'fault' ],
    'meteringSwitch' : [ 'countdown_1' , 'add_ele' , 'cur_current', 'cur_power', 'cur_voltage' , 'relay_status', 'light_mode' ],
    'omniSensor'     : [ 'bright_value', 'humidity_value', 'va_humidity', 'bright_sensitivity', 'shock_state', 'inactive_state', 'sensitivity' ],
    'pir'            : [ 'pir' ],
    'power'          : [ 'Power', 'power', 'power_go', 'switch', 'switch_1', 'switch_2', 'switch_3', 'switch_4', 'switch_5', 'switch_6', 'switch_usb1', 'switch_usb2', 'switch_usb3', 'switch_usb4', 'switch_usb5', 'switch_usb6', 'alarm_switch', 'start' ],
    'percentControl' : [ 'percent_control', 'fan_speed_percent', 'position' ],
    'push'           : [ 'manual_feed' ],
    'sceneSwitch'    : [ 'switch1_value', 'switch2_value', 'switch3_value', 'switch4_value', 'switch_mode2', 'switch_mode3', 'switch_mode4' ],
    'smoke'          : [ 'smoke_sensor_status' ],
    'temperatureSet' : [ 'temp_set' ],
    'temperature'    : [ 'temp_current', 'va_temperature' ],
    'water'          : [ 'watersensor_state' ],
    'workMode'       : [ 'work_mode' ],
    'workState'      : [ 'work_state' ],
    'situationSet'   : [ 'situation_set' ]
].asImmutable()

/**
  *  Tuya Standard Instruction Set Category Mapping to Hubitat Drivers
  *  https://developer.tuya.com/en/docs/iot/standarddescription?id=K9i5ql6waswzq
  *  MATTER : https://developer.tuya.com/en/docs/iot-device-dev/Matter_Product_Feature_List?id=Kd2wjfpuhgmrw
  */
private static Map mapTuyaCategory(Map d) {
    if ('06' in d.ServerList) {   // OnOff
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Switch', product_name: 'Switch' ]
    }
    if ('0402' in d.ServerList) {   // TemperatureMeasurement
        return [ driver: 'Generic Component Omni Sensor', product_name: 'Temperature Sensor' ]
    }
    if ('0405' in d.ServerList) {   // HumidityMeasurement
        return [ driver: 'Generic Component Omni Sensor', product_name: 'Humidity Sensor' ]
    }
    if ('0406' in d.ServerList) {   // OccupancySensing (motion)
        //return [ driver: 'Generic Component Motion Sensor', product_name: 'Motion Sensor' ]
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Motion Sensor', product_name: 'Motion Sensor' ]
    }
    /* groovylint-disable-next-line IfStatementCouldBeTernary */
    if ('0102' in d.ServerList) {   // Curtain Motor (uses custom driver)
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Window Shade', product_name: 'Curtain Motor' ]
    }

/*
    switch (d.category) {
        // Lighting
        case 'dc':    // String Lights
        case 'dd':    // Strip Lights
        case 'dj':    // Light
        case 'tgq':   // Dimmer Light
        case 'tyndj': // Solar Light
        case 'qjdcz': // Night Light
        case 'xdd':   // Ceiling Light
        case 'ykq':   // Remote Control
            if (getFunctionCode(d.statusSet, tuyaFunctions.colour)) {
                return [ driver: 'Generic Component RGBW', devices: switches ]
            } else if (getFunctionCode(d.statusSet, tuyaFunctions.ct)) {
                return [ driver: 'Generic Component CT', devices: switches ]
            } else if (getFunctionCode(d.statusSet, tuyaFunctions.brightness)) {
                return [ driver: 'Generic Component Dimmer', devices: switches ]
            }
            break
        case 'fsd':  // Ceiling Fan (with Light)
            return [
                driver: 'Generic Component Fan Control',
                devices: [
                    'light': [ suffix: 'Light', driver: 'Generic Component Switch' ]
                ]
            ]

        // Electrical
        case 'tgkg':  // Dimmer Switch
            return [ driver: 'Generic Component Dimmer' ]
        case 'wxkg':  // Scene Switch (TS004F in 'Device trigger' mode only; TS0044)
            return [ driver: 'Generic Component Central Scene Switch' ]
        case 'cl':    // Curtain Motor (uses custom driver)
        case 'clkg':
            return [ namespace: 'component', driver: 'Generic Component Window Shade' ]
        case 'bh':    // Kettle
            return [ driver: 'Generic Component Switch' ]
        case 'cwwsq': // Pet Feeder (https://developer.tuya.com/en/docs/iot/f?id=K9gf468bl11rj)
            return [ driver: 'Generic Component Button Controller' ]
        case 'cz':    // Socket (https://developer.tuya.com/en/docs/iot/s?id=K9gf7o5prgf7s)
        case 'kg':    // Switch
        case 'pc':    // Power Strip (https://developer.tuya.com/en/docs/iot/s?id=K9gf7o5prgf7s)
            if (getFunctionCode(d.statusSet, tuyaFunctions.colour)) {
                return [ driver: 'Generic Component RGBW', devices: switches ]
            } else if (getFunctionCode(d.statusSet, tuyaFunctions.brightness)) {
                return [ driver: 'Generic Component Dimmer', devices: switches ]
            }
            return [ devices: switches ]

        // Security & Sensors
        case 'ms':    // Lock
            return [ driver: 'Generic Component Lock' ]
        case 'ldcg':  // Brightness, temperature, humidity, CO2 sensors
        case 'wsdcg':
        case 'zd':    // Vibration sensor as motion
            return [ driver: 'Generic Component Omni Sensor' ]
        case 'mcs':   // Contact Sensor
            return [ driver: 'Generic Component Contact Sensor' ]
        case 'sj':    // Water Sensor
            return [ driver: 'Generic Component Water Sensor' ]
        case 'ywbj':  // Smoke Detector
            return [ driver: 'Generic Component Smoke Detector' ]
        case 'cobj':  // CO Detector
            return [ driver: 'Generic Component Carbon Monoxide Detector' ]
        case 'co2bj': // CO2 Sensor
            return [ driver: 'Generic Component Carbon Dioxide Detector' ]
        case 'pir':   // Motion Sensor
            return [ driver: 'Generic Component Motion Sensor' ]

        // Large Home Appliances
        case 'rs':    // Heater
            return [ namespace: 'component', driver: 'Generic Component Heating Device' ]

        // Small Home Appliances
        case 'qn':    // Heater
            return [ namespace: 'component', driver: 'Generic Component Heating Device' ]
        case 'cs':    // DeHumidifer
            return [ namespace: 'component', driver: 'Generic Component DeHumidifer Device' ]
        case 'fs':    // Fan
            Map devices = [:]
            if (getFunctionCode(d.statusSet, tuyaFunctions.colour)) {
                devices['light'] = [ suffix: 'Light', driver: 'Generic Component RGBW' ]
            } else if (getFunctionCode(d.statusSet, tuyaFunctions.brightness)) {
                devices['bright_value'] = [ suffix: 'Dimmer', driver: 'Generic Component Dimmer' ]
            } else {
                devices['light'] = [ suffix: 'Light', driver: 'Generic Component Switch' ]
            }

            return [
                driver: 'Generic Component Fan Control',
                devices: devices
            ]
        case 'fskg':  // Switch Fan
            return [ driver: 'Generic Component Fan Control' ]
        // Kitchen Appliances
    }
*/
   // logWarn "mapTuyaCategory(): Unknown category"   //  '${d.category}' for device '${d.name}'"
    return [ driver: 'Generic Component Switch', product_name: 'Unknown' ]
}

/* --------------------------------------------------------------------------------------------------------------
 * Implementation of component commands from child devices
 */

// Component command to refresh device      TODO: implement this
void componentRefresh(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentRefresh(${dw}) id=${id} (TODO: not implemented!)"
}

// Component command to ping the device
void componentPing(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentPing(${dw}) id=${id} (TODO: not implemented!)"
}

// Component command to turn on device
void componentOn(DeviceWrapper dw) {
    if (!dw.hasCommand('on')) {
        logError "componentOn(${dw}) driver '${dw.typeName}' does not have command 'on' in ${dw.supportedCommands}"
        return
    }
    // TODO: check if the device has cluster 0x0006 in the ServerList=[06, ....
    // TODO: check if the device has command 'on' in the {0006={FFF8=1618, FFF9=[00, 01, 02],
    code = 'Go agead!'
    if (code != null) {
        logInfo "Turning ${dw} on"
        setSwitch('On', '0x' + dw.getDataValue('id'))
    } else {
        logError "Unable to determine on function code in ${functions}"
    }
}

// Component command to turn off device
void componentOff(DeviceWrapper dw) {
    if (!dw.hasCommand('off')) {
        logError "componentOff(${dw}) driver '${dw.typeName}' does not have command 'off' in ${dw.supportedCommands}"
        return
    }
    // TODO: check if the device has cluster 0x0006 in the ServerList=[06, ....
    // TODO: check if the device has command 'off' in the {0006={FFF8=1618, FFF9=[00, 01, 02],
    code = 'Go agead!'
    if (code != null) {
        logInfo "Turning ${dw} off"
        setSwitch('Off', '0x' + dw.getDataValue('id'))
    } else {
        logError "Unable to determine off function code in ${functions}"
    }
}

//  '[close, open, refresh, setPosition, startPositionChange, stopPositionChange]'
// Component command to open device
void componentOpen(DeviceWrapper dw) {
    if (!dw.hasCommand('open')) {
        logError "componentOpen(${dw}) driver '${dw.typeName}' does not have command 'open' in ${dw.supportedCommands}"
        return
    }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Open command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) {
        logWarn "componentOpen(): deviceNumber ${deviceNumberPar} is not valid!"
        return
    }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x00) // 0x0102 = Window Covering Cluster, 0x00 = UpOrOpen
    logDebug "componentOpen(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// Component command to close device
void componentClose(DeviceWrapper dw) {
    if (!dw.hasCommand('close')) {
        logError "componentClose(${dw}) driver '${dw.typeName}' does not have command 'close' in ${dw.supportedCommands}"
        return
    }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Close command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) {
        logWarn "componentClose(): deviceNumber ${deviceNumberPar} is not valid!"
        return
    }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x01) // 0x0102 = Window Covering Cluster, 0x01 = DownOrClose
    logDebug "componentClose(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// Component command to start level change (up or down)
void componentStartLevelChange(DeviceWrapper dw, String direction) {
    levelChanges[dw.deviceNetworkId] = (direction == 'down') ? -10 : 10
    if (txtEnable) { LOG.info "Starting level change ${direction} for ${dw}" }
    runInMillis(1000, 'doLevelChange')
}

// Component command to stop level change
void componentStopLevelChange(DeviceWrapper dw) {
    logInfo "Stopping level change for ${dw}"
    levelChanges.remove(dw.deviceNetworkId)
}

// Utility function to handle multiple level changes
void doLevelChange() {
    List active = levelChanges.collect() // copy list locally
    active.each { kv ->
        ChildDeviceWrapper dw = getChildDevice(kv.key)
        if (dw != null) {
            int newLevel = (int)dw.currentValue('level') + kv.value
            if (newLevel < 0) { newLevel = 0 }
            if (newLevel > 100) { newLevel = 100 }
            componentSetLevel(dw, newLevel)
            if (newLevel <= 0 && newLevel >= 100) {
                componentStopLevelChange(device)
            }
        } else {
            levelChanges.remove(kv.key)
        }
    }

    if (!levelChanges.isEmpty()) {
        runInMillis(1000, 'doLevelChange')
    }
}

// Component command to set position
void componentSetPosition(DeviceWrapper dw, BigDecimal positionPar) {
    if (!dw.hasCommand('setPosition')) {
        logError "componentSetPosition(${dw}) driver '${dw.typeName}' does not have command 'setPosition' in ${dw.supportedCommands}"
        return
    }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    int position = positionPar as int
    if (position < 0) { position = 0 }
    if (position > 100) { position = 100 }
    logInfo "Setting position ${position} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) {
        logWarn "componentSetPosition(): deviceNumber ${deviceNumber} is not valid!"
        return
    }
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(0x05, 0x00, zigbee.swapOctets(HexUtils.integerToHexString((100 - position) * 100, 2))))
    cmd = matter.invoke(deviceNumber, 0x0102, 0x05, cmdFields)  // 0x0102 = Window Covering Cluster, 0x05 = GoToLiftPercentage
    sendToDevice(cmd)
}

// Component command to set position direction
void componentStartPositionChange(DeviceWrapper dw, String direction) {
    logDebug "componentStartPositionChange(${dw}, ${direction})"
    switch (direction) {
        case 'open': componentOpen(dw); break
        case 'close': componentClose(dw); break
        default:
            logWarn "componentStartPositionChange not implemented! direction ${direction} for ${dw}"
            break
    }
}

// Component command to stop position change
void componentStopPositionChange(DeviceWrapper dw) {
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "Stopping position change for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) {
        logWarn "setSwitch(): deviceNumber ${deviceNumberPar} is not valid!"
        return
    }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x02) // 0x0102 = Window Covering Cluster, 0x02 = StopMotion
    logInfo "componentStopPositionChange(): sending command '${cmd}'"
    sendToDevice(cmd)
}

/*
* Retrieves a map of functions from a cache.
* If the map is not already in the cache, it computes the map by parsing a JSON string and adds it to the cache.
* The JSON string is obtained from the DeviceWrapper instance.
* If the DeviceWrapper instance is null or does not contain a 'functions' data value, the method uses an empty JSON string.
*/
private static Map<String, Map> getFunctions(DeviceWrapper dw) {
    return jsonCache.computeIfAbsent(dw?.getDataValue('functions') ?: '{}') {
        k -> jsonParser.parseText(k)
    }
}

/*
* Returns the value of the first key-value pair in the functions map where the key is also an element in the codes list.
* If no such key-value pair is found, or if functions is null, the method returns null.
*/
/* groovylint-disable-next-line UnusedPrivateMethod */
private static Map getFunction(Map functions, List codes) {
    return functions?.find { f -> f.key in codes }?.value
}

/*
* Returns the first code in the codes list that is also a key in the functions map.
* If no such code is found, or if codes or functions is null, the method returns null.
*/
/* groovylint-disable-next-line UnusedPrivateMethod */
private static String getFunctionCode(Map functions, List codes) {
    return codes?.find { c -> functions?.containsKey(c) }
}

/*
* Retrieves a map of status sets from a cache. If the map is not already in the cache, it computes the map by parsing a JSON string and adds it to the cache.
* The JSON string is obtained from the DeviceWrapper instance.
* If the DeviceWrapper instance is null or does not contain a 'statusSet' data value, the method uses an empty JSON string.
*/
/* groovylint-disable-next-line UnusedPrivateMethod */
private static Map<String, Map> getStatusSet(DeviceWrapper dw) {
    return jsonCache.computeIfAbsent(dw?.getDataValue('statusSet') ?: '{}') {
        k -> jsonParser.parseText(k)
    }
}

// Command to remove all the child devices
void removeAllDevices() {
    logInfo 'Removing all child devices'
    childDevices.each { device -> deleteChildDevice(device.deviceNetworkId) }
    sendEvent(name: 'deviceCount', value: '0', descriptionText: 'All child devices removed')
    sendInfoEvent('All child devices removed')
}

/**
 *  Driver Capabilities Implementation
 */

Map fingerprintToData(String fingerprint) {
    Map data = [:]
    Map fingerprintMap = state[fingerprint]
    if (fingerprintMap != null) {
        data['id'] = fingerprint.substring(fingerprint.length() - 2, fingerprint.length())  // Device Number
        data['fingerprintName'] = fingerprint
        Map productName = mapTuyaCategory([ServerList: fingerprintMap['ServerList']])
        data['product_name'] = fingerprintMap['ProductName'] ?: productName['product_name']           // Device Name
        data['name'] = fingerprintMap['Label'] ?: "Device#${data['id']}"          // Device Label
        data['ServerList'] = fingerprintMap['ServerList']
        // TODO !!
        //data['local_key'] = fingerprintMap['local_key']
        //data['product_id'] = fingerprintMap['product_id']
        //data['category'] = fingerprintMap['category']
        //data['functions'] = fingerprintMap['functions']
        //data['statusSet'] = fingerprintMap['statusSet']
        //data['online'] = fingerprintMap['online']
    }
    return data
 }

private Integer createChildDevices() {
    logDebug 'createChildDevices(): '
    boolean result = false
    Integer deviceCount = 0
    List<Integer> supportedClusters = SupportedMatterClusters.collect { it.key }
    logDebug "createChildDevices(): supportedClusters=${supportedClusters}"
    state.each { fingerprintName, fingerprintMap ->
        if (fingerprintName.startsWith('fingerprint')) {
            List<String> serverListStr = fingerprintMap['ServerList']
            List<Integer> serverListInt = serverListStr.collect { Integer.parseInt(it, 16) }
            if (supportedClusters.any { it in serverListInt }) {
                logDebug "createChildDevices(): creating child device for fingerprintName: ${fingerprintName} ProductName: ${fingerprintMap['ProductName']}"
                result = createChildDevices(fingerprintToData(fingerprintName))
                if (result) { deviceCount++ }
            }
            else {
                logWarn "createChildDevices(): fingerprintName: ${fingerprintName} ProductName: ${fingerprintMap['ProductName']} <b>ServerList: ${fingerprintMap['ServerList']}</b> is not supported yet!"
            }
        }
        else {
            logTrace "createChildDevices(): fingerprintName: ${fingerprintName} SKIPPED"
        }
    }
    sendMatterEvent([attributeName: 'deviceCount', value: deviceCount.toString(), description: "number of devices exposing known clusters is ${deviceCount}"])
    return deviceCount
}

private boolean createChildDevices(Map d) {
    logDebug "createChildDevices(Map d): d=${d}"
    Map mapping = mapTuyaCategory(d)
    logDebug "createChildDevices(Map d): Tuya category ${d.category} driver ${mapping}"

    if (mapping.driver != null) {
        logDebug "createChildDevices(Map d): mapping.driver is ${mapping.driver}, <b>device.id is ${device.id}</b> "
        logDebug "createChildDevices(Map d): createChildDevice '${device.id}-${d.id}' ${mapping} ${d}   "
        createChildDevice("${device.id}-${d.id}", mapping, d)
    } else {
        logWarn "createChildDevices(Map d): mapping.driver is ${mapping.driver} !"
    }
/*

    if (mapping.devices == null) {
        logWarn "mapping.devices is ${mapping.devices} !"
        return false
    }

    // Tuya Device to Multiple Hubitat Devices
    String baseName = d.name
    Map baseFunctions = d.functions
    Map baseStatusSet = d.statusSet
    Map subdevices = mapping.devices.findAll { entry -> entry.key in baseFunctions.keySet() }

    logDebug "createChildDevices(Map d): baseName:${baseName} baseFunctions:${baseFunctions} baseStatusSet:${baseStatusSet} subdevices:${subdevices}"
    return

    subdevices.each { code, submap ->
        d.name = "${baseName} ${submap.suffix ?: code}"
        d.functions = [ (code): baseFunctions[(code)] ]
        d.statusSet = [ (code): baseStatusSet[(code)] ]
        createChildDevice("${device.id}-${d.id}-${code}", [
            namespace: submap.namespace ?: mapping.namespace,
            driver: submap.driver ?: mapping.driver
        ], d)
    }
*/
    return true
}

private ChildDeviceWrapper createChildDevice(String dni, Map mapping, Map d) {
    logDebug "createChildDevice(String dni, Map mapping, Map d): dni:${dni} mapping:${mapping} d:${d}"
    ChildDeviceWrapper dw = getChildDevice(dni)
    logDebug "createChildDevice(String dni, Map mapping, Map d): dw:${dw}"

    if (dw == null) {
        logInfo "Creating device ${d.name} using ${mapping.driver} driver (name: ${d.product_name}, label: ${d.name})"
        try {
            dw = addChildDevice(mapping.namespace ?: 'hubitat', mapping.driver, dni,
                [
                    name: d.product_name,
                    label: d.name,
                ]
            )
        } catch (UnknownDeviceTypeException e) {
            if (mapping.namespace == 'component') {
                logError "${d.name} driver not found, try downloading from " +
                          "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/${mapping.driver}"
            } else {
                logError("${d.name} device creation failed", e)
            }
        }
    }
    /*
    String functionJson = JsonOutput.toJson(d.functions)
    jsonCache.put(functionJson, d.functions)
    */
    dw?.with {
        label = label ?: d.name
        updateDataValue 'id', d.id
        updateDataValue 'fingerprintName', d.fingerprintName
        updateDataValue 'product_name', d.product_name
        updateDataValue 'ServerList', JsonOutput.toJson(d.ServerList)
        // TODO !!1

        /*
        updateDataValue 'local_key', d.local_key
        updateDataValue 'product_id', d.product_id
        updateDataValue 'category', d.category
        updateDataValue 'functions', functionJson
        updateDataValue 'statusSet', JsonOutput.toJson(d.statusSet)
        updateDataValue 'online', d.online as String
        */
    }

    return dw
}

/* ================================================================================================================================================================================ */

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

void delayedInfoEvent(Map data) {
    sendInfoEvent(data.info, data.descriptionText)
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

void sendHealthStatusEvent(String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

String getCron(int timeInSeconds) {
    final Random rnd = new Random()
    int minutes = (timeInSeconds / 60) as int
    int hours = (minutes / 60) as int
    hours = Math.min(hours, 23)
    String cron
    if (timeInSeconds < 60) {
        cron = "*/$timeInSeconds * * * * ? *"
    } else if (minutes < 60) {
        cron = "${rnd.nextInt(59)} */$minutes * ? * *"
    } else {
        cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"
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
    return matter.readAttributes([
        matter.attributePath(0, 0x0028, 0x00) // Basic Information Cluster : DataModelRevision
    ])
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
    state.stats  = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.lastTx['pingTime'] = state.lastTx['cmdTime'] = now()
    state.lastTx['subscribeTime'] = state.lastTx['unsubscribeTime'] = now()
    state.health = [:]
    state.bridgeDescriptor  = [:]   // driver specific
    state.subscriptions = []        // driver specific, format EP_CLUSTER_ATTR
    state.stateMachines = [:]     // driver specific
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
        sendEvent([ name: 'endpointsCount', value: 0 ])
        sendEvent([ name: 'deviceCount', value: 0 ])
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_LOG_ENABLE) }
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }
}

static Integer safeNumberToInt(val, Integer defaultVal=0) {
    try {
        return val?.startsWith('0x') ? safeHexToInt(val.substring(2)) : safeToInt(val)
    } catch (NumberFormatException e) {
        return defaultVal
    }
}

static Integer safeHexToInt(val, Integer defaultVal=0) {
    try {
        return HexUtils.hexStringToInt(val)
    } catch (NumberFormatException e) {
        return defaultVal
    }
}

static Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

static Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

@Field static final int ROLLING_AVERAGE_N = 10

double approxRollingAverage(double avg, double newSample) {
    if (avg == null || avg == 0) { return newSample }
    return (avg * (ROLLING_AVERAGE_N - 1) + newSample) / ROLLING_AVERAGE_N
}

void logInfo(msg)  { if (settings.txtEnable)   { log.info  "${device.displayName} " + msg } }
void logError(msg) { if (settings.txtEnable)   { log.error "${device.displayName} " + msg } }
void logDebug(msg) { if (settings.logEnable)   { log.debug "${device.displayName} " + msg } }
void logWarn(msg)  { if (settings.logEnable)   { log.warn  "${device.displayName} " + msg } }
void logTrace(msg) { if (settings.traceEnable) { log.trace "${device.displayName} " + msg } }

void parseTest(par) {
    log.warn "parseTest(${par})"
    parse(par)
}

void updateStateStats(Map descMap) {
    if (state.stats  != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats =  [:] }
    if (state.lastRx != null) { state.lastRx['checkInTime'] = new Date().getTime() }     else { state.lastRx = [:] }
    if (STATE_CACHING == true) {
        String dni = device.getId() + '_' + descMap['endpoint'] ?: 'XX'
        if (stateCache[dni] == null) { stateCache[dni] = [:] }
        if (stateCache[dni][stats] == null) { stateCache[dni][stats]  = [:] }
        if (stateCache[dni][lastRx] == null) { stateCache[dni][lastRx]  = [:] }
        stateCache[dni][lastRx]['checkInTime'] = new Date().getTime()
        stateCache[dni][lastRx]['rxCtr'] = (stateCache[dni]['rxCtr'] ?: 0) + 1
    }
}

@Field static final Map<String, Map> stateCache = new ConcurrentHashMap<>()

@Field volatile static Map<String,Long> TimeStamps = [:]

/* groovylint-disable-next-line UnusedMethodParameter */
void test(par) {
    log.warn "test(${par})"
    //log.warn "test(${par}) stateCache=${stateCache}"
    int endpoint = 0x1F
    int cluster = 0x003B
    int attrId = 0x0000
    int event = 0x0001

    String cmd = ''
    List<Map<String, String>> attributePaths = []
    List<Map<String, String>> eventPaths = []
    attributePaths.add(matter.attributePath(endpoint, cluster, attrId))
    eventPaths.add(matter.eventPath(endpoint, cluster, event))
    cmd = matter.subscribe(0, 0xFFFF, eventPaths)
    sendToDevice(cmd)

}
