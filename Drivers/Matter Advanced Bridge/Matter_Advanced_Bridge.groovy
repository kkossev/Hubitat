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
 * ver. 0.0.0  2023-12-29 kkossev  - Inital version;
 * ver. 0.0.1  2024-01-05 kkossev  - Linter; Discovery OK; published for alpha- testing.
 * ver. 0.0.2  2024-01-07 kkossev  - Refresh() reads the subscribed attributes; added command 'Device Label'; VendorName, ProductName, Reachable for child devices; show the device label in the event logs if set; added a test command 'setSwitch' on/off/toggle + device#;
 * ver. 0.0.3  2024-01-11 kkossev  - Child devices : deviceCount, mapTuyaCategory(d); added 'Matter_Generic_Component_Motion_Sensor.groovy', 'Matter_Generic_Component_Window_Shade.groovy' and 'matterLib.groovy'; Hubitat Bundle package;
 *                                   A1 Bridge Discovery now uses the short version; added logTrace() and logError() methods; setSwitch and setLabel commands visible in _DEBUG mode only
 * ver. 0.0.4  2024-01-14 kkossev  - added 'Matter Generic Component Switch' component driver; cluster 0x0102 (WindowCovering) attributes decoding - position, targetPosition, windowShade; add cluster 0x0102 commands processing; logTrace is  switched off after 30 minutes; filtered duplicated On/Off events in the Switch component driver;
 *                                   disabled devices are not processed to avoid spamming the debug logs; initializeCtr attribute; Default Bridge healthCheck method is set to periodic polling every 1 hour; added new removeAllSubscriptions() command; added 'Invert Motion' option to the Motion Sensor component driver @iEnam
 * ver. 0.0.5  2024-01-20 kkossev  - added endpointsCount; subscribe to [endpoint:00, cluster:001D, attrId:0003 - PartsList = the number of the parts list entries]; refactoring: parseGlobalElements(); discovery process bugs fixes; debug is false by default; changeed the steps sequence (first create devices, last subscribe to the attributes); temperature sensor omni component bug fix;
 * ver. 0.0.6  2024-01-27 kkossev  - DiscoverAll() button (state machine) replaces all old manual discovery buttons; removed setLabel and setSwitch test command;
 * ver. 0.0.7  2024-01-28 kkossev  - code cleanup; bug fix -do not send events to the bridge device if the child device does not exist; discoverAll debug options bug fix; deviceCount, endpointsCount, nodeLabelbug fixes; refresh() button on the Bridge device fixed; multiple subscribe entries bug fix;
 *                                   Bulbs are assigned 'Generic Component Dimmer'; cluster 08 partial processing - componentSetLevel() implementation; added subscribing to more than attribute per endpoint; Celsius to Fahrenheit conversion for temperature sensors
 * ver. 0.1.0  2024-02-03 kkossev  - added Contact Sensor processing; added Thermostat cluster 0x0201 attributes decoding (only); nodeLabel null checks; rounded the humidity to the neares integer value;
 *                                   versions renamed from major ver. 0.x.x; added a compatibility table matrix for Aqara devices on the top post; vibration sensors are processed as motion sensors; added Generic Component Battery
 * ver. 0.1.1  2024-02-03 kkossev  - softwareVersionString bug fix; disabled the processing of the PowerSource cluster and creating child devices for it; state['subscriptions'] list is cleared at the beginning of DiscoverAll();
 * ver. 0.2.0  2024-02-04 kkossev  -  refactored the matter messages parsing method using a lookup map; bug fix: duplicated attrList entries; bug fix: deviceCount and initializeCtr nit updated; bug fix : healhCheck schedued job was lost on resubscribe()
 *                                   added cluster 0x0101 DoorLock decoding; lock and unlock commands (not tested!)
 * ver. 0.2.1  2024-02-07 kkossev  - added temperature and humidity valid values checking; change: When creating new child devices, the Device Name is set to 'Bridge #4407 Device#08 (Humidity Sensor)' as exaple, Device Label is left empty; bugfix: device labels in logs @fanmanrules;
 *                                   implemented componentStartLevelChange(), componentStopLevelChange(), componentSetColorTemperature; use 'Generic Component CT' driver instead of dimmer for bulbs; added colorTemperature and colorName for CT bulbs; @CompileStatic experiments...
 * ver. 0.2.2  2024-02-10 kkossev  - bugfix: null pointers checks exceptions; increased the discovery timeouts (the number of the retries); all states are cleared at the start of teh discovery process;  bugfix: CT/RGB bulbs reinitialization;
 * ver. 0.2.3  2024-02-11 kkossev  - (dev.branch) lock/unlock commands disabled (not working for now); RGBW bulbs: hue, saturation; setColor
 *
 *                                   TODO: [====MVP====] refresh CT temperature (returns 0 after power off/on)
 *                                   TODO: [====MVP====] add physical to ping; healthStatus offline not working !!??
 *                                   TODO: [====MVP====] process the rest of cluster 0300 attributes : hue   TODO - check setLevel duration is not working!
 *                                   TODO: [====MVP====] RGBW bulbs to be assigned 'Generic Component RGBW' driver;
 *                                   TODO: [====MVP====] during the discovery, read 0xFFFB attribute of the supported clusters for each child device!
 *                                   TODO: [====MVP====] when subscribing to the attributes, check if the attribute is in the 0300_FFFB=[00, 01, 02, 03, 04, 07, 08, 0F, 10, 4001, 400A, 400B, 400C, 400D, 4010, FFF8, FFF9, FFFB, FFFC, FFFD]
 *                                   TODO: [====MVP====] copy DeviceType list to the child device
 *                                   TODO: [====MVP====] keep a list of known issues and limitations in the top post
 *                                   TODO: [====MVP====] keep a list of DeviceTypes that need to be tested in the top post
 *                                   TODO: [====MVP====] add a compatibility table matrix for Tuya devices on the top post
 *                                   TODO: [====MVP====] add a compatibility table matrix for SwitchBot Hub2 devices on the top post
 *                                   TODO: [====MVP====] copy the Matter specificatgion PDSs too Google Drive; Lock cluster specifications page numbers;
 *                                   TODO: [====MVP====] SwitchBot WindowCovering - close command issues @Steve9123456789
 *                                   TODO: [====MVP====] if error discovering the device name or label - still try to continue processing the attributes in the state machine
 *                                   TODO: [====MVP====] Publish version 0.2.3
 *
 *                                   TODO: [====MVP====] add heathStatus to the child devices
 *                                   TODO: [====MVP====] distinguish between creating and checking an existing child device
 *                                   TODO: [====MVP====] ignore duplicated switch and level events on main driver level
 *                                   TODO: [====MVP====] refresh to be individual list in each fingerprint - needed for the device individual refresh() command ! (add a deviceNumber parameter to the refresh() command command)
 *                                   TODO: [====MVP====] subscriptions to be individual list in each fingerprint, minReportTime to be different for each attribute
 *                                   TODO: [====MVP====] add Data.Refresh for each child device
 *                                   TODO: [====MVP====] componentRefresh(DeviceWrapper dw)
 *                                   TODO: [====MVP====] When a bridged device is deleted - ReSubscribe() to first delete all subscriptions and then re-discover all the devices, capabilities and subscribe to the known attributes
 *                                   TODO: [====MVP====] When deleting device, unsubscribe from all attributes
 *                                   TODO: [====MVP====] Publish version 0.3.0
 *
 *                                   TODO: [====MVP====] continue testing the Philips Hue Dimmer Switch
 *                                   TODO: [====MVP====] continue testing the Battery / PowerSource cluster (0x002F)
 *                                   TODO: [====MVP====] add support for cluster 0x003B  : 'Switch' / Button? (need to be able to subscribe to the 0x003B EVENTS !)
 *                                   TODO: [====MVP====] add Thermostat component driver
 *                                   TODO: [====MVP====] Publish version 0.4.0

 *                                   TODO: [====MVP====] add support for Lock cluster 0x0101
 *                                   TODO: [====MVP====] add illuminance processing
 *                                   TODO: [====MVP====] Publish version 0.5.0
 *
 *                                   TODO: [REFACTORING] optimize State Machine variables and code
 *                                   TODO: [REFACTORING] move the component drivers names into a table
 *                                   TODO: [REFACTORING] substitute the tmp state with a in-memory cache
 *                                   TODO: [REFACTORING] add a temporary state to store the attributes list of the currently interviewed cluster
 *                                   TODO: [REFACTORING] Convert SupportedMatterClusters to Map that include the known attributes to be subscribed to
 *
 *                                   TODO: [ENHANCEMENT] driverVersion to be stored in child devices states
 *                                   TODO: [ENHANCEMENT] check water sensors
 *                                   TODO: [ENHANCEMENT] change attributes and values list Info log to be shown in Debug mode only
 *                                   TODO: [ENHANCEMENT] disable the debug logs in discovery mode
 *                                   TODO: [ENHANCEMENT] Device Extended Info - expose as a command (needs state machine implementation) or remove the code?
 *                                   TODO: [ENHANCEMENT] option to automatically delete the child devices when missing from the PartsList
 *                                   TODO: [ENHANCEMENT] add initialized() method to the child devices (send 'unknown' events for all attributes)
 *                                   TODO: [ENHANCEMENT] clearStatistics command/button
 *                                   TODO: [ENHANCEMENT] DeleteDevices() to take device# parameter to delete a single device (0=all)
 *                                   TODO: [ENHANCEMENT] store subscription lists in Hex format
 *                                   TODO: [ENHANCEMENT] add Cluster SoftwareDiagnostics (0x0034) endpoint 0x0 attribute [0001] CurrentHeapFree = 0x00056610 (353808)
 *                                   TODO: [ENHANCEMENT] implement ping() for the child devices (requires individual states for each child device...)
 *                                   TODO: [ENHANCEMENT] add Configure() custom command - perform reSubscribe()
 *                                   TODO: [ENHANCEMENT] make Identify command work !
 *
 *                                   TODO: [ RESEARCH  ] check setSwitch() device# commandsList
 *                                   TODO: [ RESEARCH  ] add a Parent entry in the child devices fingerprints (PartsList)
 *                                   TODO: [ RESEARCH  ] how to  combine 2 endpoints in one device - 'Temperature and Humidity Sensor' - 2 clusters
 *                                   TODO: [ RESEARCH  ] why are the child devices  automatically disabled when shared via Hub Mesh ?
 *                                   TODO: - template -  [====MVP====] [REFACTORING] [RESEARCH] [ENHANCEMENT]
 */

/* groovylint-disable-next-line NglParseError */
#include kkossev.matterLib
#include kkossev.matterStateMachinesLib

String version() { '0.2.3' }
String timeStamp() { '2023/02/11 10:59 AM' }

@Field static final Boolean _DEBUG = true
@Field static final Boolean DEFAULT_LOG_ENABLE = false
@Field static final Boolean DO_NOT_TRACE_FFFX = true         // don't trace the FFFx global attributes
@Field static final String  DEVICE_TYPE = 'MATTER_BRIDGE'
@Field static final Boolean STATE_CACHING = false            // enable/disable state caching
@Field static final Integer CACHING_TIMER = 60               // state caching time in seconds
@Field static final Integer DIGITAL_TIMER = 3000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 2     // missing 3 checks will set the device healthStatus to offline
@Field static final String  UNKNOWN = 'UNKNOWN'
@Field static final Integer SHORT_TIMEOUT  = 7
@Field static final Integer LONG_TIMEOUT   = 15

import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper
import com.hubitat.app.exception.UnknownDeviceTypeException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import groovy.transform.CompileStatic
import hubitat.helper.HexUtils
import java.util.concurrent.ConcurrentHashMap
import hubitat.matter.DataType

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
        attribute 'nodeLabel', 'string'
        attribute 'softwareVersionString', 'string'
        attribute 'rebootCount', 'number'
        attribute 'upTime', 'number'
        attribute 'totalOperationalHours', 'number'
        attribute 'deviceCount', 'number'
        attribute 'endpointsCount', 'number'
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

        command '_DiscoverAll',  [[name:'Discover All', type: 'ENUM', description: 'Type', constraints: ['All', 'BasicInfo', 'PartsList', 'ChildDevices', 'Subscribe']]]
        command 'initialize', [[name: 'Invoked automatically during the hub reboot, do not click!']]
        command 'reSubscribe', [[name: 're-subscribe to the Matter controller events']]
        command 'loadAllDefaults', [[name: 'panic button: Clear all States and scheduled jobs']]
        command 'removeAllDevices', [[name: 'panic button: Remove all child devices']]
        command 'removeAllSubscriptions', [[name: 'panic button: remove all subscriptions']]

        if (_DEBUG) {
            command 'getInfo', [
                    [name:'infoType', type: 'ENUM', description: 'Bridge Info Type', constraints: ['Basic', 'Extended']],   // if the parameter name is 'type' - shows a drop-down list of the available drivers!
                    [name:'endpoint', type: 'STRING', description: 'Endpoint', constraints: ['STRING']]
            ]
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
    defaultValue: 15,
    options     : [1: 'Every minute (not recommended!)', 15: 'Every 15 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map StartUpOnOffEnumOpts = [0: 'Off', 1: 'On', 2: 'Toggle']

@Field static final Map<Integer, Map> SupportedMatterClusters = [
    //0x0039 : [parser: 'parseBridgedDeviceBasic', attributes: 'BridgedDeviceBasicAttributes', commands: 'BridgedDeviceBasicCommands'],   // BridgedDeviceBasic
    0x0006 : [attributes: 'OnOffClusterAttributes', commands: 'OnOffClusterCommands'],                  // On/Off Cluster
    0x0008 : [attributes: 'LevelControlClusterAttributes', commands: 'LevelControlClusterCommands'],    // Level Control
    //0x002F : [parser: 'parsePowerSource', attributes: 'PowerSourceClusterAttributes'],                // PowerSource - DO NOT ENABLE -> CRASHES THE BRIDGE!?
    //0x003B : [parser: 'parseSwitch', attributes: 'SwitchClusterAttributes', events: 'SwitchClusterEvents'],       // Switch - DO NOT ENABLE -> CRASHES THE BRIDGE!?
    0x0045 : [attributes: 'BooleanStateClusterAttributes'],                                             // Contact Sensor
    0x0101 : [attributes: 'DoorLockClusterAttributes', commands: 'DoorLockClusterCommands'],            // DoorLock
    0x0102 : [attributes: 'WindowCoveringClusterAttributes', commands: 'WindowCoveringClusterCommands'],// WindowCovering
    0x0201 : [attributes: 'ThermostatClusterAttributes', commands: 'ThermostatClusterCommands'],        // Thermostat
    0x0300 : [attributes: 'ColorControlClusterAttributes', commands: 'ColorControlClusterCommands'],    // ColorControl
    0x0402 : [attributes: 'TemperatureMeasurementClusterAttributes'],                                   // TemperatureMeasurement
    0x0405 : [attributes: 'RelativeHumidityMeasurementClusterAttributes'],                              // HumidityMeasurement
    0x0406 : [attributes: 'OccupancySensingClusterAttributes']                                          // OccupancySensing (motion)
]

@Field static final Map<Integer, String> ParsedMatterClusters = [
    0x0006 : 'parseOnOffCluster',
    0x0008 : 'parseLevelControlCluster',
    0x001D : 'parseDescriptorCluster',
    0x0028 : 'parseBasicInformationCluster',
    0x002F : 'parsePowerSource',
    0x0033 : 'parseGeneralDiagnostics',
    0x0039 : 'parseBridgedDeviceBasic',
    0x003B : 'parseSwitch',
    0x0045 : 'parseContactSensor',
    0x0101 : 'parseDoorLock',
    0x0102 : 'parseWindowCovering',
    0x0201 : 'parseThermostat',
    0x0300 : 'parseColorControl',
    0x0402 : 'parseTemperatureMeasurement',
    0x0405 : 'parseHumidityMeasurement',
    0x0406 : 'parseOccupancySensing'
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
        descMap = myParseDescriptionAsMap(description)
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

    if (!(((descMap.attrId in ['FFF8', 'FFF9', 'FFFA', 'FFFC', 'FFFD', '00FE']) && DO_NOT_TRACE_FFFX) || state['states']['isDiscovery'] == true)) {
        logDebug "parse: descMap:${descMap}  description:${description}"
    }

    parseGlobalElements(descMap)
    gatherAttributesValuesInfo(descMap)

    String parserFunc = ParsedMatterClusters[HexUtils.hexStringToInt(descMap.cluster)]
    String parserAttr = SupportedMatterClusters[HexUtils.hexStringToInt(descMap.cluster)]?.attributes

    if (parserFunc) {
        if (_DEBUG) {
            this."${parserFunc}"(descMap)
        }
        else {
            try {
                this."${parserFunc}"(descMap)
            } catch (e) {
                logWarn "parserFunc: exception ${e} <br> Failed to parse description: ${description}"
            }
        }
    } else {
        logWarn "parserFunc: NOT PROCESSED: ${descMap} description:${description}"
    }
}

Map myParseDescriptionAsMap(description) {
    Map descMap
    try {
        descMap = matter.parseDescriptionAsMap(description)
    } catch (e) {
        logWarn "parse: exception ${e} <br> Failed to parse description: ${description}"
        return null
    }
    if (descMap == null) {
        logWarn "parse: descMap is null description:${description}"
        return null
    }
    // parse: descMap:[endpoint:00, cluster:0028, attrId:0000, value:01, clusterInt:40, attrInt:0] description:read attr - endpoint: 00, cluster: 0028, attrId: 0000, value: 0401
    if (descMap.value != null && descMap.value in ['1518', '1618', '1818']) {
        descMap.value = []
    }
    return descMap
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

@CompileStatic
String getClusterName(final String cluster) { return MatterClusters[HexUtils.hexStringToInt(cluster)] ?: UNKNOWN }
//@CompileStatic
String getAttributeName(final Map descMap) { return getAttributeName(descMap.cluster, descMap.attrId) }
@CompileStatic
String getAttributeName(final String cluster, String attrId) { return getAttributesMapByClusterId(cluster)?.get(HexUtils.hexStringToInt(attrId)) ?: GlobalElementsAttributes[HexUtils.hexStringToInt(attrId)] ?: UNKNOWN }
@CompileStatic
String getFingerprintName(final Map descMap) { return descMap.endpoint == '00' ? 'bridgeDescriptor' : "fingerprint${descMap.endpoint}" }
@CompileStatic
String getFingerprintName(final Integer endpoint) { return getFingerprintName([endpoint: HexUtils.integerToHexString(endpoint, 1)]) }

//@CompileStatic
String getStateClusterName(final Map descMap) {
    String clusterMapName = ''
    if (descMap.cluster == '001D') {
        clusterMapName = getAttributeName(descMap)
    }
    else {
        clusterMapName = descMap.cluster + '_' + descMap.attrId
    }
}

@CompileStatic
String getDeviceDisplayName(final Integer endpoint) { return getDeviceDisplayName(HexUtils.integerToHexString(endpoint, 1)) }
/**
 * Returns the device label based on the provided endpoint.
 * If a child device exists, the label is retrieved from the child device display name.
 * If no child device exists yet, the label is constructed by combining the endpoint with the vendor name, product name, and custom label.
 * If the vendor name or product name is available, they are included in parentheses.
 *
 * @param endpoint The endpoint of the device.
 * @return The device display label.
 */
String getDeviceDisplayName(final String endpoint) {
    // if a child device exists, use its endpoint to get the ${device.displayName}
    if (getChildDevice("${device.id}-${endpoint}") != null) {
        return getChildDevice("${device.id}-${endpoint}")?.displayName
    }
    String label = "Bridge#${device.id} Device#${endpoint} "
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

// credits: @jvm33
// Matter payloads need hex parameters of greater than 2 characters to be pair-reversed.
// This function takes a list of parameters and pair-reverses those longer than 2 characters.
// Alternatively, it can take a string and pair-revers that.
// Thus, e.g., ["0123", "456789", "10"] becomes "230189674510" and "123456" becomes "563412"
private String byteReverseParameters(String oneString) { byteReverseParameters([] << oneString) }
private String byteReverseParameters(List<String> parameters) {
    StringBuilder rStr = new StringBuilder(64)

    for (hexString in parameters) {
        if (hexString.length() % 2) throw new Exception("In method byteReverseParameters, trying to reverse a hex string that is not an even number of characters in length. Error in Hex String: ${hexString}, All method parameters were ${parameters}.")

        for(Integer i = hexString.length() -1 ; i > 0 ; i -= 2) {
            rStr << hexString[i-1..i]
        }
    }
    return rStr
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

void gatherAttributesValuesInfo(final Map descMap) {
    Integer attrInt = descMap.attrInt as Integer
    String  attrName = getAttributeName(descMap)
    Integer tempIntValue
    String  tmpStr
    if (state.states['isInfo'] == true) {
        logTrace "gatherAttributesValuesInfo: <b>isInfo:${state.states['isInfo']}</b> state.states['cluster'] = ${state.states['cluster']} "
        if (state.states['cluster'] == descMap.cluster) {
            if (descMap.value != null && descMap.value != '') {
                tmpStr = "[${descMap.attrId}] ${attrName}"
                if (state.tmp?.contains(tmpStr)) {
                    logDebug "gatherAttributesValuesInfo: tmpStr:${tmpStr} is already in the state.tmp"
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
            sendMatterEvent([name: 'rebootCount', value: value,  descriptionText: "${getDeviceDisplayName(descMap.endpoint)} RebootCount is ${value}"])
            break
        case '0002' :   // UpTime -  a best-effort assessment of the length of time, in seconds,since the Node’s last reboot
            value = HexUtils.hexStringToInt(descMap.value)
            sendMatterEvent([name: 'upTime', value:value,  descriptionText: "${getDeviceDisplayName(descMap.endpoint)} UpTime is ${value} seconds"])
            break
        case '0003' :   // TotalOperationalHours -  a best-effort attempt at tracking the length of time, in hours, that the Node has been operational
            value = HexUtils.hexStringToInt(descMap.value)
            sendMatterEvent([name: 'totalOperationalHours', value: value,  descriptionText: "${getDeviceDisplayName(descMap.endpoint)} TotalOperationalHours is ${value} hours"])
            break
        default :
            if (descMap.attrId != '0000') { if (traceEnable) { logInfo "parse: parseGeneralDiagnostics: ${attrName} = ${descMap.value}" } }
            break
    }
}

void parsePowerSource(final Map descMap) {
    logTrace "parsePowerSource: descMap:${descMap}"
    String attrName = getAttributeName(descMap)
    Integer value
    String descriptionText = ''
    Map eventMap = [:]
    String eventName = attrName[0].toLowerCase() + attrName[1..-1]  // change the attribute name first letter to lower case
    switch (attrName) {
        case ['Status', 'Order', 'Description', 'BatTimeRemaining', 'BatChargeLevel', 'BatReplacementNeeded', 'BatReplaceability', 'BatReplacementDescription', 'BatQuantity'] :
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Power source ${attrName} is: ${descMap.value}"
            eventMap = [name: eventName, value: descMap.value, descriptionText: descriptionText]
            break
        case 'BatPercentRemaining' :   // BatteryPercentageRemaining
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Battery percentage remaining is: ${value / 2}% (raw:${descMap.value})"
            eventMap = [name: 'battery', value: value / 2, descriptionText: descriptionText]
            break
        case 'BatVoltage' :   // BatteryVoltage
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Battery voltage is: ${value / 1000}V (raw:${descMap.value})"
            eventMap = [name: 'batteryVoltage', value: value / 1000, descriptionText: descriptionText]
            break
        default :
            logInfo "Power source ${attrName} is: ${descMap.value} (unprocessed)"
            break
    }
    if (eventMap != [:]) {
        eventMap.type = 'physical'
        eventMap.isStateChange = true
        sendMatterEvent(eventMap, descMap) // bridge events
    }
}

void parseBasicInformationCluster(final Map descMap) {  // 0x0028 BasicInformation (the Bridge)
    Map eventMap = [:]
    String attrName = getAttributeName(descMap)
    String fingerprintName = getFingerprintName(descMap)
    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }
    String eventName = attrName[0].toLowerCase() + attrName[1..-1]  // change the attribute name first letter to lower case
    if (attrName in ['ProductName', 'NodeLabel', 'SoftwareVersionString', 'Reachable']) {
        if (descMap.value != null && descMap.value != '') {
            state[fingerprintName][attrName] = descMap.value
            eventMap = [name: eventName, value:descMap.value, descriptionText: "${getDeviceDisplayName(descMap?.endpoint)}  ${eventName} is: ${descMap.value}"]
            if (logEnable) { logInfo "parseBridgedDeviceBasic: ${attrName} = ${descMap.value}" }
        }
    }
    if (eventMap != [:]) {
        eventMap.type = 'physical'; eventMap.isStateChange = true
        sendMatterEvent(eventMap, descMap) // bridge events
    }
}

void parseBridgedDeviceBasic(final Map descMap) {       // 0x0039 BridgedDeviceBasic (the child devices)
    Map eventMap = [:]
    String attrName = getAttributeName(descMap)
    String fingerprintName = getFingerprintName(descMap)
    if (state[fingerprintName] == null) { state[fingerprintName] = [:] }
    String eventName = attrName[0].toLowerCase() + attrName[1..-1]  // change the attribute name first letter to lower case
    if (attrName in ['VendorName', 'ProductName', 'NodeLabel', 'SoftwareVersionString', 'Reachable']) {
        if (descMap.value != null && descMap.value != '') {
            state[fingerprintName][attrName] = descMap.value
            eventMap = [name: eventName, value:descMap.value, descriptionText: "${getDeviceDisplayName(descMap?.endpoint)}  ${eventName} is: ${descMap.value}"]
            if (logEnable) { logInfo "parseBridgedDeviceBasic: ${attrName} = ${descMap.value}" }
        }
    }
    if (eventMap != [:]) {
        eventMap.type = 'physical'; eventMap.isStateChange = true
        sendMatterEvent(eventMap, descMap) // child events
    }
}

void parseDescriptorCluster(final Map descMap) {    // 0x001D Descriptor
    logTrace "parseDescriptorCluster: descMap:${descMap}"
    String attrName = getAttributeName(descMap)    //= DescriptorClusterAttributes[descMap.attrInt as int] ?: GlobalElementsAttributes[descMap.attrInt as int] ?: UNKNOWN
    String endpointId = descMap.endpoint
    String fingerprintName =  getFingerprintName(descMap)  /*"fingerprint${endpointId}"
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
                    String descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Bridge partsListCount is: ${partsListCount}"
                    sendMatterEvent([name: 'endpointsCount', value: partsListCount, descriptionText: descriptionText], descMap)
                    if (partsListCount != oldCount) {
                        logWarn "THE NUMBER OF THE BRIDGED DEVICES CHANGED FROM ${oldCount} TO ${partsListCount} !!!"
                    }
                }
            }
            break
        default :
            logTrace "parseDescriptorCluster: Descriptor: ${attrName} = ${descMap.value}"
            break
    }
}

void parseOnOffCluster(final Map descMap) {
    logTrace "parseOnOffCluster: descMap:${descMap}"
    if (descMap.cluster != '0006') { logWarn "parseOnOffCluster: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return  }
    Integer value

    switch (descMap.attrId) {
        case '0000' : // Switch
            String switchState = descMap.value == '01' ? 'on' : 'off'
            sendMatterEvent([
                name: 'switch',
                value: switchState,
                descriptionText: "${getDeviceDisplayName(descMap.endpoint)} switch is ${switchState}"
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
}

void parseLevelControlCluster(final Map descMap) {
    logTrace "parseLevelControlCluster: descMap:${descMap}"
    if (descMap.cluster != '0008') { logWarn "parseLevelControlCluster: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    Integer value
    switch (descMap.attrId) {
        case '0000' : // CurrentLevel
            value = hex254ToInt100(descMap.value)
            sendMatterEvent([
                name: 'level',
                value: value, //.toString(),
                descriptionText: "${getDeviceDisplayName(descMap.endpoint)} level is ${value}"
            ], descMap)
            break
        default :
            Map eventMap = [:]
            String attrName = getAttributeName(descMap)
            String fingerprintName = getFingerprintName(descMap)
            if (state[fingerprintName] == null) { state[fingerprintName] = [:] }
            String eventName = attrName[0].toLowerCase() + attrName[1..-1]  // change the attribute name first letter to lower case
            if (attrName in ['CurrentLevel', 'RemainingTime', 'MinLevel', 'MaxLevel', 'OnOffTransitionTime', 'OnLevel', 'OnTransitionTime', 'OffTransitionTime', 'Options', 'StartUpCurrentLevel', 'Reachable']) {
                eventMap = [name: eventName, value:descMap.value, descriptionText: "${eventName} is: ${descMap.value}"]
                if (logEnable) { logInfo "parseLevelControlCluster: ${attrName} = ${descMap.value}" }
            }
            else {
                logWarn "parseLevelControlCluster: unsupported LevelControl: ${attrName} = ${descMap.value}"
            }
            if (eventMap != [:]) {
                eventMap.type = 'physical'; eventMap.isStateChange = true
                sendMatterEvent(eventMap, descMap) // child events
            }
    }
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
            name: 'motion',
            value: motionAttr,
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} motion is ${motionAttr}"
        ], descMap)
    } else {
        logTrace "parseOccupancySensing: ${(OccupancySensingClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

// Method for parsing switch
void parseSwitch(final Map descMap) {
    if (descMap.cluster != '003B') { logWarn "parseSwitch: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '0000') { // Switch
        String switchState = descMap.value == '01' ? 'on' : 'off'
        sendMatterEvent([
            name: 'switch',
            value: switchState,
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} switch is ${switchState}"
        ], descMap)
    } else {
        logTrace "parseSwitch: ${(SwitchClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

// Method for parsing contact sensor
void parseContactSensor(final Map descMap) {
    if (descMap.cluster != '0045') { logWarn "parseContactSensor: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    String contactAttr = descMap.value == '01' ? 'closed' : 'open'
    if (descMap.attrId == '0000') { // Contact
        sendMatterEvent([
            name: 'contact',
            value: contactAttr,
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} contact is ${contactAttr}"
        ], descMap)
    } else {
        logTrace "parseContactSensor: ${(BooleanStateClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

// Method for parsing temperature measurement
void parseTemperatureMeasurement(final Map descMap) { // 0402
    if (descMap.cluster != '0402') { logWarn "parseTemperatureMeasurement: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '0000') { // Temperature
        Double valueInt = HexUtils.hexStringToInt(descMap.value) / 100.0
        String unit
        //log.debug "parseTemperatureMeasurement: location.temperatureScale:${location.temperatureScale}"
        if (valueInt < -100 || valueInt > 300) {
            logWarn "parseTemperatureMeasurement: valueInt:${valueInt} is out of range"
            return
        }
        if (location.temperatureScale == 'F') {
            valueInt = (valueInt * 1.8) + 32
            unit = "\u00B0" + 'F'
        }
        else {
            unit = "\u00B0" + 'C'
        }
        sendMatterEvent([
            name: 'temperature',
            value: valueInt.round(1),
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} temperature is ${valueInt.round(2)} ${unit}",
            unit: unit
        ], descMap)
    } else {
        logTrace "parseTemperatureMeasurement: ${(TemperatureMeasurementClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
        logTrace "parseTemperatureMeasurement: ${getAttributeName(descMap)} = ${descMap.value}"
    }
}

// Method for parsing humidity measurement
void parseHumidityMeasurement(final Map descMap) { // 0405
    if (descMap.cluster != '0405') {
        logWarn "parseHumidityMeasurement: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"
        return
    }
    if (descMap.attrId == '0000') { // Humidity
        Double valueInt = HexUtils.hexStringToInt(descMap.value) / 100.0
        if (valueInt <= 0 || valueInt > 100) {
            logWarn "parseHumidityMeasurement: valueInt:${valueInt} is out of range"
            return
        }
        sendMatterEvent([
            name: 'humidity',
            value: valueInt.round(0),
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)}  humidity is ${valueInt.round(1)} %"
        ], descMap)
    } else {
        logTrace "parseHumidityMeasurement: ${(RelativeHumidityMeasurementClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

void parseDoorLock(final Map descMap) { // 0101
    if (descMap.cluster != '0101') { logWarn "parseDoorLock: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '0000') { // LockState
        String lockState = descMap.value == '01' ? 'locked' : 'unlocked'
        sendMatterEvent([
            name: 'lock',
            value: lockState,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} lock is ${lockState}"
        ], descMap)
    } else {
        logTrace "parseDoorLock: ${(DoorLockClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

void parseWindowCovering(final Map descMap) { // 0102
    if (descMap.cluster != '0102') { logWarn "parseWindowCovering: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '000B') { // TargetPositionLiftPercent100ths  - actually this is the current position !!!
        Integer valueInt = (100 - HexUtils.hexStringToInt(descMap.value) / 100.0) as int
        sendMatterEvent([
            name: 'position',
            value: valueInt.toString(),
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} currentPosition  is ${valueInt} %"
        ], descMap)
    } else if (descMap.attrId == '000E') { // CurrentPositionLiftPercent100ths - actually this is the target position !!!
        Integer valueInt = (100 - HexUtils.hexStringToInt(descMap.value) / 100.0) as int
        sendMatterEvent([
            name: 'targetPosition',
            value: valueInt.toString(),
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} targetPosition is ${valueInt} %"
        ], descMap)
    } else if (descMap.attrId == '000A') { // OperationalStatus
        sendMatterEvent([
            name: 'operationalStatus',
            value: descMap.value,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} operationalStatus is ${descMap.value}"
        ], descMap)
    }
    else {
        logTrace "parseWindowCovering: ${(WindowCoveringClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

void parseColorControl(final Map descMap) { // 0300
    if (descMap.cluster != '0300') { logWarn "parseColorControl: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    switch (descMap.attrId) {
        case '0000' : // CurrentHue
            Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 2.54) as int
            logDebug "parseColorControl: CurrentHue= ${valueInt} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'hue',
                value: valueInt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} hue is ${valueInt}"
            ], descMap)
            break
        case '0001' : // CurrentSaturation
            Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 2.54) as int
            logDebug "parseColorControl: CurrentSaturation= ${valueInt} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'saturation',
                value: valueInt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} saturation is ${valueInt}"
            ], descMap)
            break
        case '0007' : // ColorTemperatureMireds
            Integer valueCt = miredHexToCt(descMap.value)
            logDebug "parseColorControl: ColorTemperatureCT= ${valueCt} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'colorTemperature',
                value: valueCt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} colorTemperature is ${valueCt}",
                unit: '°K'
            ], descMap)
            String colorName = getGenericTempName(valueCt)
            sendMatterEvent([
                name: 'colorName',
                value: colorName,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} color is ${valueCt}"
            ], descMap)
            break
        case '0008' : // ColorMode
            String colorMode = descMap.value == '00' ? 'RGB' : descMap.value == '01' ? 'XY' : descMap.value == '02' ? 'CT' : UNKNOWN
            logDebug "parseColorControl: ColorMode= ${colorMode} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'colorMode',
                value: colorMode,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} colorMode is ${colorMode}"
            ], descMap)
            break
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            logTrace "parseColorControl: ${getAttributeName(descMap)} = ${descMap.value}"
            break
        default :
            Map eventMap = [:]
            String attrName = getAttributeName(descMap)
            String fingerprintName = getFingerprintName(descMap)
            logDebug "parseColorControl: fingerprintName:${fingerprintName} attrName:${attrName}"
            if (state[fingerprintName] == null) { state[fingerprintName] = [:] }
            String eventName = attrName[0].toLowerCase() + attrName[1..-1]  // change the attribute name first letter to lower case
            if (attrName in ColorControlClusterAttributes.values().toList()) {
                eventMap = [name: eventName, value:descMap.value, descriptionText: "${eventName} is: ${descMap.value}"]
                if (logEnable) { logInfo "parseLevelControlCluster: ${attrName} = ${descMap.value}" }
            }
            else {
                logWarn "parseLevelControlCluster: unsupported LevelControl: ${attrName} = ${descMap.value}"
            }
            if (eventMap != [:]) {
                eventMap.type = 'physical'; eventMap.isStateChange = true
                sendMatterEvent(eventMap, descMap) // child events
            }
            break
    }
}

void parseThermostat(final Map descMap) {
    if (descMap.cluster != '0201') { logWarn "parseThermostat: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '0000') { // LocalTemperature
        Double valueInt = HexUtils.hexStringToInt(descMap.value) / 100.0
        String unit
        //log.debug "parseThermostat: location.temperatureScale:${location.temperatureScale}"
        if (location.temperatureScale == 'F') {
            valueInt = (valueInt * 1.8) + 32
            unit = "\u00B0" + 'F'
        }
        else {
            unit = "\u00B0" + 'C'
        }
        Double valueIntCorrected = valueInt.round(1)

        sendMatterEvent([
            name: 'temperature',
            value: valueIntCorrected,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} temperature is ${valueInt} ${unit}",
            unit: unit
        ], descMap)
    }
    else {
        logDebug "parseThermostat: ${(ThermostatClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

//events

// Common code method for sending events
void sendMatterEvent(final Map<String, String> eventParams, DeviceWrapper dw) {
    String id = dw?.getDataValue('id') ?: '00'
    sendMatterEvent(eventParams, [endpoint: id])
}

void sendMatterEvent(final Map<String, String> eventParams, Map descMap = [:]) {
    String name = eventParams['name']
    String value = eventParams['value']
    String descriptionText = eventParams['descriptionText']
    String unut = eventParams['unit']

    String dni = ''
    // get the dni from the descMap eddpoint
    if (descMap != [:]) {
        dni = "${device.id}-${descMap.endpoint}"
    }
    if (descriptionText == null) {
        descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} ${name} is ${value}"
    }
    ChildDeviceWrapper dw = getChildDevice(dni) // null if dni is null for the parent device
    Map eventMap = [name: name, value: value, descriptionText: descriptionText, unit:unit, type: 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force the event to be sent
    }
    if (state.states['isDiscovery'] == true) {
        eventMap.descriptionText += ' [discovery]'
        eventMap.isStateChange = true   // force the event to be sent
    }
    // TODO - use the child device wrapper to check the current value !!!!!!!!!!!!!!!!!!!!!
    if (dw != null && dw?.disabled != true) {
        // send events to child for parsing. Any filtering of duplicated events will be potentially done in the child device handler.
        logDebug "sendMatterEvent: sending for parsing to the child device: dw:${dw} dni:${dni} name:${name} value:${value} descriptionText:${descriptionText}"
        dw.parse([eventMap])
    } else if (descMap?.endpoint == null || descMap?.endpoint == '00') {
        // Bridge event
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"
    }
    else {
        // event intended to be sent to the parent device, but the dni is null ..
        if (state['states']['isDiscovery'] != true) { // do not log this event if the discovery is in progress
            logWarn "sendMatterEvent: <b>cannot send </b> for parsing to the child device: dw:${dw} dni:${dni} name:${name} value:${value} descriptionText:${descriptionText}"
        }
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
void _DiscoverAll(statePar = null) {
    logWarn "_DiscoverAll()"
    Integer stateSt = DISCOVER_ALL_STATE_INIT
    state.stateMachines = [:]
    // ['All', 'BasicInfo', 'PartsList']]
    if (statePar == 'All') { stateSt = DISCOVER_ALL_STATE_INIT }
    else if (statePar == 'BasicInfo') { stateSt = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST }
    else if (statePar == 'PartsList') { stateSt = DISCOVER_ALL_STATE_GET_PARTS_LIST_START }
    else if (statePar == 'ChildDevices') { stateSt = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START }
    else if (statePar == 'Subscribe') { stateSt = DISCOVER_ALL_STATE_SUBSCRIBE_KNOWN_CLUSTERS }
    else {
        logWarn "_DiscoverAll(): unknown statePar:${statePar} !"
        return
    }

    discoverAllStateMachine([action: START, goToState: stateSt])
    logInfo "_DiscoverAll(): started!"
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

void a5SubscribeKnownClustersAttributes() {
    logDebug 'a5SubscribeKnownClustersAttributes:'
    sendInfoEvent('Subscribing for known clusters attributes reporting ...')
    // subscribe to the Descriptor cluster PartsList attribute
    subscribe('add', 0, 0x001D, 0x0003)

    // For each fingerprint in the state, check if the fingerprint has entries in the SupportedMatterClusters list. Then, add these entries to the state.subscriptions map
    Integer deviceCount = 0
    //Map stateCopy = state.clone()
    Map stateCopy = state
    state.each { fingerprintName, fingerprintMap ->
        logDebug "a5SubscribeKnownClustersAttributes: fingerprintName:${fingerprintName} fingerprintMap:${fingerprintMap}"
        if (fingerprintName.startsWith('fingerprint')) {
            boolean knownClusterFound = false
            List serverList = fingerprintMap['ServerList'] as List
            serverList.each { entry  ->
                if (safeHexToInt(entry) in SupportedMatterClusters.keySet()) {
                    // fingerprintName:fingerprint07 entry:0402 map:[FFF8:1618, FFF9:1618, 0002:2710, 0000:092A, 0001:EC78, FFFC:00, FFFD:04]
                    String endpointId = fingerprintName.substring(fingerprintName.length() - 2, fingerprintName.length())
                    logDebug "a5SubscribeKnownClustersAttributes: (deviceCount=${deviceCount}) fingerprintName:${fingerprintName} endpointId:${endpointId} entry:${entry}"
                    // we subscribe to attribute 0x0000 of the cluster by default
                    if (entry == '0102') {
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('000B'))
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('000E'))
                    }
                    else if (entry == '0300') {
                        List<String> attributeList = fingerprintMap['0300_FFFB']
                        List<Integer> attributeListInt = attributeList.collect { it -> safeHexToInt(it) }
                        // TODO - check whether 0000 is in 0300_FFFB list  // 300_FFFB=[00, 01, 02, 03, 04, 07, 08, 0F, 10, 4001, 400A, 400B, 400C, 400D, 4010, FFF8, FFF9, FFFB, FFFC, FFFD],
                        if (0x00 in attributeListInt || 0x01 in attributeListInt) {
                            subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('0000'))
                        }
                        else {
                            logWarn "a5SubscribeKnownClustersAttributes: attributeListInt ${attributeListInt} does not contain 0x00 !"
                        }
                        //subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('0000'))
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('0007'))
                    }
                    else {
                        subscribe(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = safeHexToInt(entry), attrId = safeHexToInt('0000'))
                    }
                    knownClusterFound = true
                }
            }
            if (knownClusterFound) { deviceCount ++ }
        }
    }
    int numberOfSubscriptions = state.subscriptions?.size() ?: 0
    sendInfoEvent("the number of subscriptions is ${numberOfSubscriptions}")
    sendMatterEvent([name: 'deviceCount', value: deviceCount, descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} subscribed for events from ${deviceCount} devices"])
    //runIn(1, 'delayedInfoEvent', [overwrite: true, data: [info: 'Subscribing finished', descriptionText: '']])
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
    sendMatterEvent([name: 'initializeCtr', value: state.stats['initializeCtr'], descriptionText: "${device.displayName} initializeCtr is ${state.stats['initializeCtr']}", type: 'digital'])
    scheduleCommandTimeoutCheck(delay = 30)
    subscribe()
    updated()   // added 02/03/2024
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
    clearSubscriptionsState()
    unsubscribe()
    sendInfoEvent('all subsciptions are removed!', 're-discover the deices again ...')
}

void clearSubscriptionsState() {
    state.subscriptions = []
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
        logWarn "setSwitch(): fingerprintName '${fingerprintName}' is not valid! (${getDeviceDisplayName(deviceNumber)})"
        return
    }
    String cluster = '0006'
    // list key is 0006_FFFB=[00, FFF8, FFF9, FFFB, FFFD, FFFC]
    String stateClusterName = getStateClusterName([cluster: cluster, attrId: 'FFFB'])
    logDebug "setSwitch(): fingerprintName = ${fingerprintName}, stateClusterName = ${stateClusterName}"

    List<String> onOffClusterAttributesList = state[fingerprintName][stateClusterName] as List
    logDebug "setSwitch(): onOffClusterAttributesList = ${onOffClusterAttributesList}"
    if (onOffClusterAttributesList == null) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceDisplayName(deviceNumber)} !"
        return
    }
    // check if '00' is in the onOffClusterAttributesList
    if (!onOffClusterAttributesList.contains('00')) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceDisplayName(deviceNumber)} !"
        return
    }
    // find the command in the OnOffClusterCommands map
    logDebug "setSwitch(): command = ${command}"
    Integer onOffcmd = OnOffClusterCommands.find { k, v -> v == command }?.key
    logDebug "setSwitch(): command = ${command}, onOffcmd = ${onOffcmd}, onOffCommandsList = ${onOffCommandsList}"
    if (onOffcmd == null) {
        logWarn "setSwitch(): command '${command}' is not valid for ${getDeviceDisplayName(deviceNumber)} !"
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
    logInfo 'refreshCmd() ...'
    List<Map<String, String>> attributePaths = state.subscriptions?.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    } ?: []
    if (state['bridgeDescriptor'] == null) { logWarn 'refreshCmd(): state.bridgeDescriptor is null!'; return null  }
    List<String> serverList = (state['bridgeDescriptor']['0033_FFFB'] as List)?.clone()  // new ArrayList<>(originalList)
    serverList?.removeAll(['FFF8', 'FFF9', 'FFFB', 'FFFC', 'FFFD', '00'])                // 0x0000  : 'NetworkInterfaces' - not supported
    attributePaths.addAll(serverList?.collect { attr ->
        matter.attributePath(0, 0x0033, HexUtils.hexStringToInt(attr))
    })
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
    if ('08' in d.ServerList) {   // Dimmer
        return [ driver: 'Generic Component CT', product_name: 'Dimmer/Bulb' ]      // was 'Generic Component Dimmer'
    }
    if ('45' in d.ServerList) {   // Contact Sensor
        return [ driver: 'Generic Component Contact Sensor', product_name: 'Contact Sensor' ]
    }
    if ('0402' in d.ServerList) {   // TemperatureMeasurement
        return [ driver: 'Generic Component Omni Sensor', product_name: 'Temperature Sensor' ]
    }
    if ('0405' in d.ServerList) {   // HumidityMeasurement
        return [ driver: 'Generic Component Omni Sensor', product_name: 'Humidity Sensor' ]
    }
    if ('0406' in d.ServerList) {   // OccupancySensing (motion)
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Motion Sensor', product_name: 'Motion Sensor' ]
    }
    if ('0101' in d.ServerList) {   // Door Lock
        return [driver: 'Generic Component Lock', product_name: 'Door Lock' ]
    }
    if ('0102' in d.ServerList) {   // Curtain Motor (uses custom driver)
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Window Shade', product_name: 'Curtain Motor' ]
    }
    if ('0201' in d.ServerList) {   // Thermostat
        return [ driver: 'Generic Component Thermostat', product_name: 'Thermostat' ]
    }
    if ('06' in d.ServerList) {   // OnOff
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Switch', product_name: 'Switch' ]
    }
    if ('3B' in d.ServerList) {   // Switch / Button - TODO !
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Switch', product_name: 'Button' ]
    }
    /*
    if ('2F' in d.ServerList) {   // Power Source
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Battery', product_name: 'Switch' ]
    }
    */

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
    if (!dw.hasCommand('on')) { logError "componentOn(${dw}) driver '${dw.typeName}' does not have command 'on' in ${dw.supportedCommands}"; return }
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
    if (!dw.hasCommand('off')) { logError "componentOff(${dw}) driver '${dw.typeName}' does not have command 'off' in ${dw.supportedCommands}"; return }
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
    if (!dw.hasCommand('open')) { logError "componentOpen(${dw}) driver '${dw.typeName}' does not have command 'open' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Open command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentOpen(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x00) // 0x0102 = Window Covering Cluster, 0x00 = UpOrOpen
    logDebug "componentOpen(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// Component command to close device
void componentClose(DeviceWrapper dw) {
    if (!dw.hasCommand('close')) { logError "componentClose(${dw}) driver '${dw.typeName}' does not have command 'close' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Close command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentClose(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x01) // 0x0102 = Window Covering Cluster, 0x01 = DownOrClose
    logDebug "componentClose(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// prestage level : https://community.hubitat.com/t/sengled-element-color-plus-driver/21811/2

// Component command to set level
void componentSetLevel(DeviceWrapper dw, BigDecimal levelPar, BigDecimal durationlPar=0) {
    if (!dw.hasCommand('setLevel')) { logError "componentSetLevel(${dw}) driver '${dw.typeName}' does not have command 'setLevel' in ${dw.supportedCommands}" ; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    int level = levelPar as int
    int duration = durationPar ?: 0 as int
    if (level < 0) { level = 0 }
    if (level > 100) { level = 100 }
    logInfo "Setting level ${level} durtion ${duration} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    Integer levelHex = Math.round(level * 2.54)
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(0x04, 0x00, HexUtils.integerToHexString(levelHex, 1)))
    cmdFields.add(matter.cmdField(0x05, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(duration, 2))))
    cmd = matter.invoke(deviceNumber, 0x0008, 0x04, cmdFields)  // 0x0008 = Level Control Cluster, 0x04 = MoveToLevelWithOnOff
    def stock = matter.setLevel(level, duration)                             //    {152400 0C2501 0A0018}'
    sendToDevice(cmd)
}

// Component command to start level change (up or down)
void componentStartLevelChange(DeviceWrapper dw, String direction) {
    if (!dw.hasCommand('startLevelChange')) { logError "componentStartLevelChange(${dw}) driver '${dw.typeName}' does not have command 'startLevelChange' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    String moveMode = direction == 'up' ? '00' : '01'
    Integer rateInt = 5  // seconds
    //String moveRate = zigbee.swapOctets(HexUtils.integerToHexString(rateInt as int, 1))   // TODO - errorjava.lang.StringIndexOutOfBoundsException: begin 2, end 4, length 2 on line 1684 (method componentStartLevelChange)
    String moveRate = '50'
    List<Map<String, String>> cmdFields = []
    logInfo "Starting level change UP for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    cmdFields.add(matter.cmdField(DataType.UINT8, 0x00, moveMode))   // MoveMode
    cmdFields.add(matter.cmdField(DataType.UINT8, 0x01, moveRate))   // MoveRate    // TODO - configurable ??
    String cmd = matter.invoke(deviceNumber, 0x0008, 0x01, cmdFields)       // 0x01 = Move
    sendToDevice(cmd)
}

// Component command to stop level change
void componentStopLevelChange(DeviceWrapper dw) {
    if (!dw.hasCommand('stopLevelChange')) { logError "componentStopLevelChange(${dw}) driver '${dw.typeName}' does not have command 'stopLevelChange' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT8, 0x00, '00'))      // OptionsMask - map8 = 0x18
    cmdFields.add(matter.cmdField(DataType.UINT8, 0x01, '00'))      // OptionsOverride
    String cmd = matter.invoke(deviceNumber, 0x0008, 0x03, cmdFields)      // 0x03 = Stop
    sendToDevice(cmd)
}

void componentSetColorTemperature(DeviceWrapper dw, BigDecimal colorTemperature, BigDecimal level, BigDecimal duration=0) {
    if (!dw.hasCommand('setColorTemperature')) { logError "componentSetColorTemperature(${dw}) driver '${dw.typeName}' does not have command 'setColorTemperature' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "Setting color temperature ${colorTemperature} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    String colorTemperatureMireds = byteReverseParameters(HexUtils.integerToHexString(ctToMired(colorTemperature as int), 2))
    String transitionTime = zigbee.swapOctets(HexUtils.integerToHexString((duration ?: 0) as int, 2))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT16, 0, colorTemperatureMireds))
    cmdFields.add(matter.cmdField(DataType.UINT16, 1, transitionTime))
    String cmd = matter.invoke(deviceNumber, 0x0300, 0x0A, cmdFields)  // 0x0300 = Color Control Cluster, 0x0A = MoveToColorTemperature
    sendToDevice(cmd)
}

void componentSetHue(DeviceWrapper dw, BigDecimal hue) {
    if (!dw.hasCommand('setHue')) { logError "componentSetHue(${dw}) driver '${dw.typeName}' does not have command 'setHue' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "Setting hue ${hue} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    Integer hueScaled = Math.round(hue * 2.54)
    Integer transitionTime = 1
    hueScaled = hueScaled < 0 ? 0 : hueScaled > 254 ? 254 : hueScaled
    String hueHex = byteReverseParameters(HexUtils.integerToHexString(hueScaled as int, 1))
    String transitionTimeHex = zigbee.swapOctets(HexUtils.integerToHexString(transitionTime as int, 2))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT8, 0, hueHex))
    cmdFields.add(matter.cmdField(DataType.UINT8,  1, "00"))               // Direction 00 = Shortest
    cmdFields.add(matter.cmdField(DataType.UINT16, 2, transitionTimeHex))  // TransitionTime in 0.1 seconds, uint16 0-65534, byte swapped
    String cmd = matter.invoke(deviceNumber, 0x0300, 0x00, cmdFields)  // 0x0300 = Color Control Cluster, 0x00 = MoveToHue
    sendToDevice(cmd)
}

void componentSetSaturation(DeviceWrapper dw, BigDecimal saturation) {
    if (!dw.hasCommand('setSaturation')) { logError "componentSetSaturation(${dw}) driver '${dw.typeName}' does not have command 'setSaturation' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "Setting saturation ${saturation} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    Integer saturationScaled = Math.round(saturation * 2.54)
    Integer transitionTime = 1
    saturationScaled = saturationScaled < 0 ? 0 : saturationScaled > 254 ? 254 : saturationScaled
    String saturationHex = byteReverseParameters(HexUtils.integerToHexString(saturationScaled as int, 1))
    String transitionTimeHex = zigbee.swapOctets(HexUtils.integerToHexString(transitionTime as int, 2))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT8, 0, saturationHex))
    cmdFields.add(matter.cmdField(DataType.UINT16, 1, transitionTimeHex))  // TransitionTime in 0.1 seconds, uint16 0-65534, byte swapped
    String cmd = matter.invoke(deviceNumber, 0x0300, 0x03, cmdFields)  // 0x0300 = Color Control Cluster, 0x03 = MoveToSaturation
    sendToDevice(cmd)
}

void componentSetColor(DeviceWrapper dw, Map colormap) {
    if (!dw.hasCommand('setColor')) { logError "componentSetColor(${dw}) driver '${dw.typeName}' does not have command 'setColor' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "Setting color hue ${colormap.hue} saturation ${colormap.saturation} level ${colormap.level} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    Integer hueScaled = Math.round(colormap.hue * 2.54)
    Integer saturationScaled = Math.round(colormap.saturation * 2.54)
    Integer levelScaled = Math.round(colormap.level * 2.54)
    Integer transitionTime = 1
    hueScaled = hueScaled < 0 ? 0 : hueScaled > 254 ? 254 : hueScaled
    saturationScaled = saturationScaled < 0 ? 0 : saturationScaled > 254 ? 254 : saturationScaled
    levelScaled = levelScaled < 0 ? 0 : levelScaled > 254 ? 254 : levelScaled
    String hueHex = byteReverseParameters(HexUtils.integerToHexString(hueScaled as int, 1))
    String saturationHex = byteReverseParameters(HexUtils.integerToHexString(saturationScaled as int, 1))
    String levelHex = byteReverseParameters(HexUtils.integerToHexString(levelScaled as int, 1))
    String transitionTimeHex = zigbee.swapOctets(HexUtils.integerToHexString(transitionTime as int, 2))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT8, 0, hueHex))
    cmdFields.add(matter.cmdField(DataType.UINT8, 1, saturationHex))
    cmdFields.add(matter.cmdField(DataType.UINT16, 2, transitionTimeHex))  // TransitionTime in 0.1 seconds, uint16 0-65534, byte swapped
    String cmd = matter.invoke(deviceNumber, 0x0300, 0x06, cmdFields)  // 0x0300 = Color Control Cluster, 0x06 = MoveToHueAndSaturation ;0x07 = MoveToColor
    sendToDevice(cmd)
}

void componentSetEffect(DeviceWrapper dw, BigDecimal effect) {
    String id = dw.getDataValue('id')
    logWarn "componentSetEffect(${dw}) id=${id} (TODO: not implemented!)"
}

void componentSetNextEffect(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentSetNextEffect(${dw}) id=${id} (TODO: not implemented!)"
}

void componentSetPreviousEffect(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentSetPreviousEffect(${dw}) id=${id} (TODO: not implemented!)"
}

// Color Names
String getGenericTempName(temp){
    if (!temp) return UNKNOWN
    String genericName = UNKNOWN
    Integer value = temp.toInteger()
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
    return genericName
}

/**
 * Convert a color temperature in Kelvin to a mired value
 * @param kelvin color temperature in Kelvin
 * @return mired value
 */
 @CompileStatic
private static Integer ctToMired(final int kelvin) {
    return (1000000 / kelvin).toInteger()
}

/**
 * Mired to Kelvin conversion
 * @param mired mired value in hex
 * @return color temperature in Kelvin
 */
private int miredHexToCt(final String mired) {
    Integer miredInt = hexStrToUnsignedInt(mired)
    return miredInt > 0 ? (1000000 / miredInt) as int : 0
}

// Component command to set position
void componentSetPosition(DeviceWrapper dw, BigDecimal positionPar) {
    if (!dw.hasCommand('setPosition')) { logError "componentSetPosition(${dw}) driver '${dw.typeName}' does not have command 'setPosition' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    int position = positionPar as int
    if (position < 0) { position = 0 }
    if (position > 100) { position = 100 }
    logInfo "Setting position ${position} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
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
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "setSwitch(): deviceNumber ${deviceNumberPar} is not valid!"; return; }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x02) // 0x0102 = Window Covering Cluster, 0x02 = StopMotion
    logInfo "componentStopPositionChange(): sending command '${cmd}'"
    sendToDevice(cmd)
}

void componentSetThermostatMode(DeviceWrapper dw, String mode) {
    if (dw.currentValue('supportedThermostatModes') == null) { initializeThermostat(dw) }
    logWarn "componentSetThermostatMode(${dw}, ${mode}) is not implemented!"
    return
}

void componentSetThermostatFanMode(DeviceWrapper dw, String mode) {
    if (dw.currentValue('supportedThermostatModes') == null) { initializeThermostat(dw) }
    logWarn "componentSetThermostatFanMode(${dw}, ${mode}) is not implemented!"
    return
}

void componentSetHeatingSetpoint(DeviceWrapper dw, BigDecimal temperature) {
    if (dw.currentValue('supportedThermostatModes') == null) { initializeThermostat(dw) }
    logWarn "componentSetHeatingSetpoint(${dw}, ${temperature}) is not implemented!"
    return
}

void componentSetCoolingSetpoint(DeviceWrapper dw, BigDecimal temperature) {
    if (dw.currentValue('supportedThermostatModes') == null) { initializeThermostat(dw) }
    logWarn "componentSetCoolingSetpoint(${dw}, ${temperature}) is not implemented!"
    return
}

void componentLock(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentLock(${dw}) id=${id} TODO: not implemented!<br>Use virtual switch to control the lock via Apple Home..."
    return

    if (!dw.hasCommand('lock')) { logError "componentLock(${dw}) driver '${dw.typeName}' does not have command 'lock' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Lock command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentLock(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(0x12, 0x00, "0000"))
    String cmd = matter.invoke(deviceNumber, 0x0101, 0x00, cmdFields) // 0x0101 = DoorLock Cluster, 0x00 = LockDoor
    logDebug "componentLock(): sending command '${cmd}'"
    sendToDevice(cmd)
}

void componentUnlock(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentLock(${dw}) id=${id} TODO: not implemented!<br>Use virtual switch to control the lock via Apple Home..."
    return

    if (!dw.hasCommand('unlock')) { logError "componentUnlock(${dw}) driver '${dw.typeName}' does not have command 'unlock' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Unlock command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentUnlock(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    List<Map<String, String>> cmdFields = []
    Integer duration = 1
    cmdFields.add(matter.cmdField(0x05, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(duration, 2))))
    String cmd = matter.invoke(deviceNumber, 0x0101, 0x07, cmdFields) // 0x0101 = DoorLock Cluster, 0x01 = UnlockDoor; 04 - GetLockRecord 08-clear all PIN codes; Clear PIN Code 
    logDebug "componentUnlock(): sending command '${cmd}'"
    sendToDevice(cmd)
}

void componentDeleteCode(DeviceWrapper dw, BigDecimal codePosition) {
    String id = dw.getDataValue('id')
    logWarn "componentDeleteCode(${dw}) id=${id} (TODO: not implemented!)"
}

void componentGetCodes(DeviceWrapper dw) {
    String id = dw.getDataValue('id')
    logWarn "componentGetCodes(${dw}) id=${id} (TODO: not implemented!)"
}

void componentSetCode(DeviceWrapper dw, BigDecimal codePosition, String code, String name = null) {
    String id = dw.getDataValue('id')
    logWarn "componentSetCode(${dw}) id=${id} (TODO: not implemented!)"
}

void componentSetCodeLength(DeviceWrapper dw, BigDecimal codeLength) {
    String id = dw.getDataValue('id')
    logWarn "componentSetCodeLength(${dw}) id=${id} (TODO: not implemented!)"
}

void initializeThermostat(DeviceWrapper dw) {
    logWarn "initializeThermostat(${dw}) is not implemented!"
    def supportedThermostatModes = []
    supportedThermostatModes = ["off", "heat", "auto"]
    logInfo "supportedThermostatModes: ${supportedThermostatModes}"
    sendMatterEvent([name: "supportedThermostatModes", value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true], dw)
    sendMatterEvent([name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]), isStateChange: true], dw)
    sendMatterEvent([name: "thermostatMode", value: "heat", isStateChange: true, description: "inital attribute setting"], dw)
    sendMatterEvent([name: "thermostatFanMode", value: "auto", isStateChange: true, description: "inital attribute setting"], dw)
    sendMatterEvent([name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "inital attribute setting"], dw)
    sendMatterEvent([name: "thermostatSetpoint", value:  12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting"], dw)        // Google Home compatibility
    sendMatterEvent([name: "heatingSetpoint", value: 12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting"], dw)
    sendMatterEvent([name: "coolingSetpoint", value: 34.5, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting"], dw)
    sendMatterEvent([name: "temperature", value: 23.4, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting"], dw)
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
    sendEvent(name: 'deviceCount', value: '0', descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} All child devices removed")
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
        //data['name'] = fingerprintMap['Label'] ?: "Device#${data['id']}"          // Device Label
        data['name'] = getDeviceDisplayName(data['id'])
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
    sendMatterEvent([name: 'deviceCount', value: deviceCount, descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} number of devices exposing known clusters is ${deviceCount}"])
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
                    name: d.name    // was  d.product_name
                    //label: null     // do not set the label here, it will be set by the user!
                ]
            )
        } catch (UnknownDeviceTypeException e) {
            if (mapping.namespace == 'kkossev') {
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
        //label = label ?: d.name
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
        sendEvent(name: 'Status', value: 'clear', descriptionText: 'last info messages auto cleared', type: 'digital')
    }
    else {
        logInfo "${info}"
        sendEvent(name: 'Status', value: info, descriptionText:descriptionText ?: '',  type: 'digital')
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

// a periodic cron job, increasing the checkCtr3 each time called.
// checkCtr3 is cleared when some event is received from the device.
void deviceHealthCheck() {
    checkDriverVersion()
    if (state.health == null) { state.health = [:] }
    Integer ctr = state.health['checkCtr3'] ?: 0
    String healthStatus = device.currentValue('healthStatus') ?: 'unknown'
    logDebug "deviceHealthCheck: healthStstus = ${healthStatus} checkCtr3=${ctr}"
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        state.health['offlineCtr'] = (state.health['offlineCtr'] ?: 0) + 1      // increase the offline counter even if the device is already not present - changed 02/1/2024
        if (healthStatus != 'offline') {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck: ${healthStatus} (checkCtr3=${ctr}) offlineCtr=${state.health['offlineCtr']}"
    }
    if (((settings.healthCheckMethod as Integer) ?: 0) == 2) { //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        ping()          // TODO - ping() results in initialize() call if the device is switched off !
    }
    state.health['checkCtr3'] = ctr + 1
}

void sendHealthStatusEvent(String value) {
    String descriptionText = "${device.displayName} healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true,  type: 'digital')
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

// scheduled job to check if the device responded to the last command
// increases the checkCtr3 each time called.
void deviceCommandTimeout() {
    checkDriverVersion()
    if (state.health == null) { state.health = [:] }
    logWarn "no response received (sleepy device or offline?) checkCtr3 = ${state.health['checkCtr3']} offlineCtr = ${state.health['offlineCtr']} "
    if (state.states['isPing'] == true) {
        sendRttEvent('timeout')
        state.states['isPing'] = false
        if (state.stats != null) { state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 } else { state.stats = [:] }
    } else {
        sendInfoEvent('timeout!', "no response received on the last matter command! (checkCtr3 = ${state.health['checkCtr3']} offlineCtr = ${state.health['offlineCtr']})")
    }
    // added 02/11/2024 - deviceHealthCheck() will increase the check3Ctr and will send the healthStatus event if the device is offline
    deviceHealthCheck()
}

void sendRttEvent(String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null) { state.lastTx = [:] }
    Integer timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "${device.displayName} Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical')
    }
    else {
        descriptionText = "${device.displayName} Round-trip time : ${value} (healthStatus=<b>${device.currentValue('healthStatus')}</b> offlineCtr=${state.health['offlineCtr']} checkCtr3=${state.health['checkCtr3']})"
        logInfo "${descriptionText}"
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'digital')
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
    if (fullInit == true || state.deviceType == null) {
        logWarn 'forcing fullInit = true'
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
        sendEvent([ name: 'initializeCtr', value: 0 ])
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
    int endpoint = 0x14
    int cluster = 0x003B
    int attrId = 0x0000
    int event = 0x0001
    int time = 0x0010

   // String cmd = ''

    //List<Map<String, String>> attributePaths = []
    //List<Map<String, String>> eventPaths = []
    //attributePaths.add(matter.attributePath(endpoint, cluster, attrId))
    //eventPaths.add(matter.eventPath(endpoint, cluster, event))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x00))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x01))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x02))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x03))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x04))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x05))
//    eventPaths.add(matter.eventPath(endpoint, cluster, 0x06))
    //cmd = matter.subscribe(0, 0xFFFF, eventPaths)
    /*
    cmd = matter.subscribe(1, 5, eventPaths)
    logWarn "test(): sending command '${cmd}'"
    sendToDevice(cmd)
    */
    //log.debug "test(): this.TTSVoices=${this.TTSVoices}"
    // print all TTSVoices properties
    //this.TTSVoices.each { k, v -> log.debug "TTSVoices.${k}=${v}" }
   // TTSVoices.each { log.debug "$it" }
   // this.properties.each { log.debug "$it" }

    /*
    //cmd = matter.setColorTemperature(3000, 1)
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT8, 0x00, zigbee.swapOctets(HexUtils.integerToHexString(time, 2))))
    cmd = matter.invoke(device.endpointId, 0x0003, 0x0000, cmdFields)       //  'he invoke 0x01 0x0003 0x0000 {152500100018}'
    logWarn "test(): sending command '${cmd}'  DataType.UINT8=${DataType.UINT8}"
    */
    /*
    Map descMap = [:]
    descMap.endpoint = '01'
    logWarn "displayName(${par}) = ${getDeviceDisplayName(par)}"
    String dni = "${device.id}-${descMap.endpoint}"
    ChildDeviceWrapper dw = getChildDevice(dni)
    logDebug "test(): dw=${dw}"
    dw.setState('testState', 'testValue')
    logWarn "test(): dw.getState('testState')=${dw.getState('testState')}"
    */
        Integer intpar = safeNumberToInt(par)
        String hexEP = HexUtils.integerToHexString(intpar, 2)
        /*
        String cmd = 'he rattrs [{"ep":"0x' + hexEP + '","cluster":"0x0300","attr":"0xFFFFFFFF"}]'
        sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.MATTER))
        */

    List<Map<String, String>> attributePaths = []
    //List<Map<String, String>> attributeWriteRequests = []

    attributePaths.add(matter.attributePath(hexEP, -1, -1))     // IdentifyTime
    String cmd = matter.readAttributes(attributePaths)
    //sendToDevice(cmd)

    List eventPaths = []
    eventPaths.add(matter.eventPath("29", 0x3B, 0x00))
    eventPaths.add(matter.eventPath("2A", 0x3B, 0x01))
    eventPaths.add(matter.eventPath("2A", 0x3B, 0x01))
    eventPaths.add(matter.eventPath("29", 0x3B, 0xFFFF))
    eventPaths.add(matter.eventPath("29", 0xFFFF, 0xFFFF))
    cmd = matter.subscribe(0, 0xFFFF, eventPaths)
    logWarn "test(): sending command '${cmd}'"
    sendToDevice(cmd)

}
