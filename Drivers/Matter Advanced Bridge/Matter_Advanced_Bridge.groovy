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
 * The full revisions history is available at https://github.com/kkossev/Hubitat/wiki/Matter-Advanced-Bridge-%E2%80%90-revisions-history
 *
 * ver. 0.0.0  2023-12-29 kkossev  - Inital version;
 * ........
 * ver. 0.5.0  2024-03-09 kkossev  - WindowCovering driver refactoring; WindowCovering: added battery attributes; WindowCovering: added a bunch of new options; Minimize State Variables by default is true;
 *                                   documented the WindowCovering settings - https://github.com/kkossev/Hubitat/wiki/Matter-Advanced-Bridge-%E2%80%90-Window-Covering
 * ver. 0.5.1  2024-03-10 kkossev  - Help/Documentation button in the driver linked to GitHub Wiki page and HE Community thread;
 * ver. 0.5.2  2024-03-11 kkossev  - added parseTest(map as string) _DEBUg command in the 'Matter Generic Component Window Shade' driver; fixed an exception in the same driver; battery attributes name changes; removed the _DiscoverAll options;
 * ver. 0.5.3  2024-03-11 kkossev  - (dev.branch) Window Shade driver exception bug fixed
 *
 *
 *                                   TODO: [ENHANCEMENT] add WindowCovering presets (default, Zemismart 1)
 *                                   TODO: [ENHANCEMENT] check why the DeviceType is not populated to child device data ?
 *                                   TODO: [ENHANCEMENT] copy DeviceType list to the child device
 *                                   TODO: [ENHANCEMENT] product_name: Temperature Sensor to be added to the device name
 *                                   TODO: [ENHANCEMENT] use NodeLabel as device label when creating child devices (when available - Hue bridge) !
 *                                   TODO: [ENHANCEMENT] add showChildEvents advanced option
 *                                   TODO: [ENHANCEMENT] DeleteDevice # command (utilities) (0=all)
 *                                   TODO: [ENHANCEMENT] reSubscribe # command (utilities) (0=all)
 *                                   TODO: [ENHANCEMENT] hide Zemismart M1 getSubscribeCmdList(): cluster 0x001D is not in the SupportedMatterClusters list!
 *                                   TODO: [====MVP====] Publish version 0.5.3
 *
 *                                   TODO: [ENHANCEMENT] add an optoon to print the child device logs on the main driver logs (default disabled)
 *                                   TODO: [ENHANCEMENT] add to the device name the product type (e.g. 'Sontact Sensor', 'Battery') when creating devices (Aqara P2 contact sensor)
 *                                   TODO: [ENHANCEMENT] Ping the bridge at the start of the discovery process
 *                                   TODO: [ENHANCEMENT] check the 'healthStatus' attribute at the start of the Discovery process !
 *                                   TODO: [ENHANCEMENT] When deleting device, unsubscribe from all attributes (+Info logs)
 *                                   TODO: [ENHANCEMENT] When subscribing, remove from the subscribe list devices that are disabled ! (+Info logs)
 *                                   TODO: [====MVP====] Publish version 0.5.x
 *
 *                                   TODO: [ENHANCEMENT] distinguish between creating and checking an existing child device
 *                                   TODO: [ENHANCEMENT] When a bridged device is deleted - ReSubscribe() to first delete all subscriptions and then re-discover all the devices, capabilities and subscribe to the known attributes
 *                                   TODO: [====MVP====] Publish version 0.5.x
 *
 *                                   TODO: [====MVP====] Publish version 0.5.0
 *
 *                                   TODO: [====MVP====] **************************** Publish version 1.0.0 for public Beta testing - 16th of March 2024 ******************************
 *
 *                                   TODO: [====MVP====] add support for cluster 0x003B  : 'Switch' / Button? (need to be able to subscribe to the 0x003B EVENTS !)
 *                                   TODO: [====MVP====] add support for Lock cluster 0x0101
 *                                   TODO: [====MVP====] add Thermostat component driver
 *                                   TODO: [====MVP====] add heathStatus to the child devices custom component drivers
 *
 *                                   TODO: [REFACTORING] optimize State Machine variables and code
 *
 *                                   TODO: [ENHANCEMENT] add product_name: Temperature Sensor to the device name when creating devices
 *                                   TODO: [ENHANCEMENT] driverVersion to be stored in child devices states
 *                                   TODO: [ENHANCEMENT] Device Extended Info - expose as a command (needs state machine implementation) or remove the code?
 *                                   TODO: [ENHANCEMENT] option to automatically delete the child devices when missing from the PartsList
 *                                   TODO: [ENHANCEMENT] add initialized() method to the child devices (send 'unknown' events for all attributes)
 *                                   TODO: [ENHANCEMENT] store subscription lists in Hex format
 *                                   TODO: [ENHANCEMENT] add Cluster SoftwareDiagnostics (0x0034) endpoint 0x0 attribute [0001] CurrentHeapFree = 0x00056610 (353808)
 *                                   TODO: [ENHANCEMENT] implement ping() for the child devices (requires individual states for each child device...)
 *                                   TODO: [ENHANCEMENT] add Configure() custom command - perform reSubscribe()
 *                                   TODO: [ENHANCEMENT] make Identify command work !
 *
 *                                   TODO: [ RESEARCH  ] check setSwitch() device# commandsList
 *                                   TODO: [ RESEARCH  ] add a Parent entry in the child devices fingerprints (PartsList)
 *                                   TODO: [ RESEARCH  ] how to  combine 2 endpoints in one device - 'Temperature and Humidity Sensor' - 2 clusters
 *                                   TODO: - template -  [====MVP====] [REFACTORING] [RESEARCH] [ENHANCEMENT]
 */
/* groovylint-disable-next-line NglParseError */
#include kkossev.matterLib
#include kkossev.matterUtilitiesLib
#include kkossev.matterStateMachinesLib
//#include matterTools.parseDescriptionAsDecodedMap

static String version() { '0.5.3' }
static String timeStamp() { '2023/03/11 11:35 PM' }

@Field static final Boolean _DEBUG = false
@Field static final String  COMM_LINK =   "https://community.hubitat.com/t/project-nearing-beta-release-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009"
@Field static final String  GITHUB_LINK = "https://github.com/kkossev/Hubitat/wiki/Matter-Advanced-Bridge"
@Field static final Boolean DEFAULT_LOG_ENABLE = false
@Field static final Boolean DO_NOT_TRACE_FFFX = true         // don't trace the FFFx global attributes
@Field static final Boolean MINIMIZE_STATE_VARIABLES_DEFAULT = true  // minimize the state variables
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

        command '_DiscoverAll',  [[name:'Discover all bridged devices!' /*', type:ENUM', description: 'Type', constraints: ['All', 'BasicInfo', 'PartsList', 'ChildDevices', 'Subscribe']*/]]
        //command 'initialize', [[name: 'Invoked automatically during the hub reboot, do not click!']]
        command 'reSubscribe', [[name: 're-subscribe to the Matter controller events']]
        command 'loadAllDefaults', [[name: 'panic button: Clear all States and scheduled jobs']]
        if (_DEBUG) {
            command 'getInfo', [
                    [name:'infoType', type: 'ENUM', description: 'Bridge Info Type', constraints: ['Basic', 'Extended']],   // if the parameter name is 'type' - shows a drop-down list of the available drivers!
                    [name:'endpoint', type: 'STRING', description: 'Endpoint', constraints: ['STRING']]
            ]
            command 'identify'      // can't make it work ... :(
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']]
        }
        // do not expose the known Matter Bridges fingerprints for now ... Let the stock driver be assigned automatically.
        // fingerprint endpointId:"01", inClusters:"0003,001D", outClusters:"001E", model:"Aqara Hub E1", manufacturer:"Aqara", controllerType:"MAT"
    }
    preferences {
	    input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
        input name:'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true
        input name:'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: DEFAULT_LOG_ENABLE
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false
        if (device && advancedOptions == true) {
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
            input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>'
            input name: 'minimizeStateVariables', type: 'bool', title: '<b>Minimize State Variables</b>', defaultValue: MINIMIZE_STATE_VARIABLES_DEFAULT, description: '<i>Minimize the state variables size.</i>'
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
    // On/Off Cluster
    0x0006 : [attributes: 'OnOffClusterAttributes', commands: 'OnOffClusterCommands',  parser: 'parseOnOffCluster',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    // Level Control Cluster
    0x0008 : [attributes: 'LevelControlClusterAttributes', commands: 'LevelControlClusterCommands', parser: 'parseLevelControlCluster',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    0x002F : [parser: 'parsePowerSource', attributes: 'PowerSourceClusterAttributes',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]],   // Status
                               [0x0001: [min: 0, max: 0xFFFF, delta: 0]],   // Order
                               [0x0002: [min: 0, max: 0xFFFF, delta: 0]],   // Description
                               [0x000B: [min: 0, max: 0xFFFF, delta: 0]],   // BatVoltage
                               [0x000C: [min: 0, max: 0xFFFF, delta: 0]],   // BatPercentRemaining
                               [0x000E: [min: 0, max: 0xFFFF, delta: 0]],   // BatChargeLevel
                               [0x000F: [min: 0, max: 0xFFFF, delta: 0]]]   // BatReplacementNeeded
    ],
    /*
    0x0039 : [attributes: 'BridgedDeviceBasicAttributes', commands: 'BridgedDeviceBasicCommands', parser: 'parseBridgedDeviceBasic',            // BridgedDeviceBasic
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    */
    //0x003B : [parser: 'parseSwitch', attributes: 'SwitchClusterAttributes', events: 'SwitchClusterEvents'],       // Switch - DO NOT ENABLE -> CRASHES THE BRIDGE!?
    // Descriptor Cluster
    /*
    0x001D : [attributes: 'DescriptorClusterAttributes', parser: 'parseDescriptorCluster',      // decimal(29) manually subscribe to the Bridge device ep=0 0x001D 0x0003
              subscriptions : [[0x0003: [min: 0, max: 0xFFFF, delta: 0]]]   // PartsList
    ],
    */
    // Contact Sensor Cluster
    0x0045 : [attributes: 'BooleanStateClusterAttributes', parser: 'parseContactSensor',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    // DoorLock Cluster
    0x0101 : [attributes: 'DoorLockClusterAttributes', commands: 'DoorLockClusterCommands', parser: 'parseDoorLock',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]   // LockState
    ],
    // WindowCovering
    0x0102 : [attributes: 'WindowCoveringClusterAttributes', commands: 'WindowCoveringClusterCommands', parser: 'parseWindowCovering',
              subscriptions : [[0x000A: [min: 0, max: 0xFFFF, delta: 0]],   // OperationalStatus
                               [0x000B: [min: 0, max: 0xFFFF, delta: 0]],   // TargetPositionLiftPercent100ths
                               [0x000E: [min: 0, max: 0xFFFF, delta: 0]]]   // CurrentPositionLiftPercent100ths
    ],
    // Thermostat
    0x0201 : [attributes: 'ThermostatClusterAttributes', commands: 'ThermostatClusterCommands', parser: 'parseThermostat',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]],   // LocalTemperature
                               [0x0003: [min: 0, max: 0xFFFF, delta: 0]],   // OccupiedCoolingSetpoint  // TODO - not implemented!
                               [0x0004: [min: 0, max: 0xFFFF, delta: 0]],   // OccupiedHeatingSetpoint
                               [0x0007: [min: 0, max: 0xFFFF, delta: 0]],   // SystemMode
                               [0x0008: [min: 0, max: 0xFFFF, delta: 0]],   // AlarmMask
                               [0x0009: [min: 0, max: 0xFFFF, delta: 0]],   // RunningState
                               [0x0011: [min: 0, max: 0xFFFF, delta: 0]]]   // ControlSequenceOfOperation
    ],
    // ColorControl Cluster
    0x0300 : [attributes: 'ColorControlClusterAttributes', commands: 'ColorControlClusterCommands', parser: 'parseColorControl',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]],   // CurrentHue
                               [0x0001: [min: 0, max: 0xFFFF, delta: 0]],   // CurrentSaturation
                               [0x0007: [min: 0, max: 0xFFFF, delta: 0]],   // ColorTemperatureMireds
                               [0x0008: [min: 0, max: 0xFFFF, delta: 0]]]   // ColorMode
    ],
    // IlluminanceMeasurement Cluster
    0x0400 : [attributes: 'IlluminanceMeasurementClusterAttributes', parser: 'parseIlluminanceMeasurement',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    // TemperatureMeasurement Cluster
    0x0402 : [attributes: 'TemperatureMeasurementClusterAttributes', parser: 'parseTemperatureMeasurement',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    // HumidityMeasurement Cluster
    0x0405 : [attributes: 'RelativeHumidityMeasurementClusterAttributes', parser: 'parseHumidityMeasurement',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ],
    // OccupancySensing (motion) Cluster
    0x0406 : [attributes: 'OccupancySensingClusterAttributes', parser: 'parseOccupancySensing',
              subscriptions : [[0x0000: [min: 0, max: 0xFFFF, delta: 0]]]
    ]
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
    0x0400 : 'parseIlluminanceMeasurement',
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
    //String parserAttr = SupportedMatterClusters[HexUtils.hexStringToInt(descMap.cluster)]?.attributes

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
    //JvmDescMap = parseDescriptionAsDecodedMap(description)
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
    //descMap.value = JvmDescMap.decodedValue.toString()
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

String getClusterName(final String cluster) { return MatterClusters[HexUtils.hexStringToInt(cluster)] ?: UNKNOWN }
String getAttributeName(final Map descMap) { return getAttributeName(descMap.cluster, descMap.attrId) }
String getAttributeName(final String cluster, String attrId) { return getAttributesMapByClusterId(cluster)?.get(HexUtils.hexStringToInt(attrId)) ?: GlobalElementsAttributes[HexUtils.hexStringToInt(attrId)] ?: UNKNOWN }
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
@CompileStatic
private String byteReverseParameters(String oneString) { byteReverseParameters([] << oneString) }
@CompileStatic
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
//@CompileStatic
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

//@CompileStatic
void parseGeneralDiagnostics(final Map descMap) {
    //logTrace "parseGeneralDiagnostics: descMap:${descMap}"
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
        case ['BatTimeRemaining', 'BatChargeLevel', 'BatReplacementNeeded', 'BatReplaceability', 'BatReplacementDescription', 'BatQuantity'] :
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Power source ${attrName} is ${descMap.value}"
            eventMap = [name: eventName, value: descMap.value, descriptionText: descriptionText]
            break
        case 'BatPercentRemaining' :   // BatteryPercentageRemaining 0x000C
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Battery percentage remaining is ${value / 2}% (raw:${descMap.value})"
            eventMap = [name: 'battery', value: value / 2, descriptionText: descriptionText]
            break
        case 'BatVoltage' :   // BatteryVoltage 0x000B
            value = HexUtils.hexStringToInt(descMap.value)
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Battery voltage is ${value / 1000}V (raw:${descMap.value})"
            eventMap = [name: 'batteryVoltage', value: value / 1000, descriptionText: descriptionText]
            break
        case 'Status' :  // PowerSourceStatus 0x0000
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Power source status is ${descMap.value}"
            eventMap = [name: 'powerSourceStatus', value: descMap.value, descriptionText: descriptionText]
            break
        case 'Order' :   // PowerSourceOrder 0x0001
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Power source order is ${descMap.value}"
            eventMap = [name: 'powerSourceOrder', value: descMap.value, descriptionText: descriptionText]
            break
        case 'Description' :   // PowerSourceDescription 0x0002
            descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} Power source description is ${descMap.value}"
            eventMap = [name: 'powerSourceDescription', value: descMap.value, descriptionText: descriptionText]
            break
        default :
            logInfo "Power source ${attrName} is ${descMap.value} (unprocessed)"
            break
    }
    if (eventMap != [:]) {
        eventMap.type = 'physical'
        eventMap.isStateChange = true
        sendMatterEvent(eventMap, descMap, true) // bridge events
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
            if (logEnable) { logInfo "parseBasicInformationCluster: ${attrName} = ${descMap.value}" }
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
            ], descMap, true)
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
            ], descMap, true)
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
                logWarn "parseLevelControlCluster: unsupported LevelControl: attribute ${descMap.attrId} ${attrName} = ${descMap.value}"
            }
            if (eventMap != [:]) {
                eventMap.type = 'physical'; eventMap.isStateChange = true
                sendMatterEvent(eventMap, descMap, true) // child events
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
        ], descMap, true)
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
        ], descMap, true)
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
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} contact is ${contactAttr} (raw:${descMap.value})"
        ], descMap, true)
    } else {
        logTrace "parseContactSensor: ${(BooleanStateClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

// Method for parsing illuminance measurement
void parseIlluminanceMeasurement(final Map descMap) { // 0400
    if (descMap.cluster != '0400') { logWarn "parseIlluminanceMeasurement: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    if (descMap.attrId == '0000') { // Illuminance
        Integer valueInt = HexUtils.hexStringToInt(descMap.value)
        Integer valueLux = Math.pow( 10, (valueInt -1) / 10000)  as Integer
        if (valueLux < 0 || valueLux > 100000) {
            logWarn "parseIlluminanceMeasurement: valueInt:${valueInt} is out of range"
            return
        }
        sendMatterEvent([
            name: 'illuminance',
            value: valueLux as int,
            unit: 'lx',
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)}  illuminance is ${valueLux} lux"
        ], descMap, true)
    } else {
        logTrace "parseIlluminanceMeasurement: ${(IlluminanceMeasurementClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
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
            value: valueInt.round(1) as double,
            descriptionText: "${getDeviceDisplayName(descMap.endpoint)} temperature is ${valueInt.round(2)} ${unit}",
            unit: unit
        ], descMap, true)
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
            value: valueInt.round(0) as int,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)}  humidity is ${valueInt.round(1)} %"
        ], descMap, true)
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
    if (descMap.attrId == '000B') { // TargetPositionLiftPercent100ths
        Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 100) as int
        sendMatterEvent([
            name: 'targetPosition',
            value: valueInt,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} <b>targetPosition</b> is reported as ${valueInt} <i>(to be re-processed in the child driver!)</i>"
        ], descMap, ignoreDuplicates = false)
    } else if (descMap.attrId == '000E') { // CurrentPositionLiftPercent100ths
        Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 100) as int
        sendMatterEvent([
            name: 'position',
            value: valueInt,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} <b>position</b> is is reported as ${valueInt} <i>(to be re-processed in the child driver!)</i>"
        ], descMap, ignoreDuplicates = false)
    } else if (descMap.attrId == '000A') { // OperationalStatus
        sendMatterEvent([
            name: 'operationalStatus',
            value: descMap.value,
            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} operationalStatus is ${descMap.value}"
        ], descMap, ignoreDuplicates = false)
    }
    else {
        logTrace "parseWindowCovering: ${(WindowCoveringClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

void parseColorControl(final Map descMap) { // 0300
    if (descMap.cluster != '0300') { logWarn "parseColorControl: unexpected cluster:${descMap.cluster} (attrId:${descMap.attrId})"; return }
    ChildDeviceWrapper dw = getDw(descMap)
    switch (descMap.attrId) {
        case '0000' : // CurrentHue
            Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 2.54) as int
            logTrace "parseColorControl: hue = ${valueInt}"
            sendMatterEvent([
                name: 'hue',
                value: valueInt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} hue is ${valueInt}"
            ], descMap, true)
            if (dw?.currentValue('colorMode') != 'CT') {
                sendColorNameEvent(descMap, hue=valueInt, saturation=null)   // added 02/19/2024
            }
            break
        case '0001' : // CurrentSaturation
            Integer valueInt = (HexUtils.hexStringToInt(descMap.value) / 2.54) as int
            logTrace "parseColorControl: CurrentSaturation = ${valueInt} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'saturation',
                value: valueInt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} saturation is ${valueInt}"
            ], descMap, true)
            if (dw?.currentValue('colorMode') != 'CT') {
                sendColorNameEvent(descMap, hue=null, saturation=valueInt)   // added 02/19/2024
            }
            break
        case '0007' : // ColorTemperatureMireds
            Integer valueCt = miredHexToCt(descMap.value)
            logTrace "parseColorControl: ColorTemperatureCT = ${valueCt} (raw=0x${descMap.value})"
            sendMatterEvent([
                name: 'colorTemperature',
                value: valueCt,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} colorTemperature is ${valueCt}",
                unit: '°K'
            ], descMap, true)
            String colorMode = dw?.currentValue('colorMode') ?: UNKNOWN
            if (colorMode == 'CT') {
                String colorName = convertTemperatureToGenericColorName(valueCt)
                sendMatterEvent([
                    name: 'colorName',
                    value: colorName,
                    descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} color is ${colorName}"
                ], descMap, true)
            }
            break
        case '0008' : // ColorMode
            String colorMode = descMap.value == '00' ? 'RGB' : descMap.value == '01' ? 'XY' : descMap.value == '02' ? 'CT' : UNKNOWN
            logTrace "parseColorControl: ColorMode= ${colorMode} (raw=0x${descMap.value}) - sending <b>colorName</b>"
            if (dw != null) {
                Integer colorTemperature = dw.currentValue('colorTemperature') ?: -1
                Integer hue = dw.currentValue('hue') ?: -1
                Integer saturation = dw.currentValue('saturation') ?: -1
                if (colorMode == 'CT') {
                    logTrace "parseColorControl: CT colorTemperature = ${colorTemperature}"
                    if (colorTemperature != -1) {
                        String colorName = convertTemperatureToGenericColorName(colorTemperature)
                        sendMatterEvent([
                            name: 'colorName',
                            value: colorName,
                            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} color is ${colorName}"
                        ], descMap, true)
                    }
                }
                else if (colorMode == 'RGB' || colorMode == 'XY') {
                    if (hue != -1 && saturation != -1) {
                        String colorName = convertHueToGenericColorName(hue, saturation)
                        logTrace "parseColorControl: RGB colorName = ${colorName}"
                        sendMatterEvent([
                            name: 'colorName',
                            value: colorName,
                            descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} color is ${colorName}"
                        ], descMap, true)
                    }
                }
            }
            //
            sendMatterEvent([
                name: 'colorMode',
                value: colorMode,
                descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} colorMode is ${colorMode}"
            ], descMap, true)
            break
        case ['FFF8', 'FFF9', 'FFFA', 'FFFB', 'FFFC', 'FFFD', '00FE'] :
            //logTrace "parseColorControl: ${getAttributeName(descMap)} = ${descMap.value}"
            break
        default :
            Map eventMap = [:]
            String attrName = getAttributeName(descMap)
            String fingerprintName = getFingerprintName(descMap)
            //logDebug "parseColorControl: fingerprintName:${fingerprintName} attrName:${attrName}"
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
                sendMatterEvent(eventMap, descMap, true) // child events
            }
            break
    }
}

ChildDeviceWrapper getDw(descMap) {
    String id = descMap?.endpoint ?: '00'
    return getChildDevice("${device.id}-${id}")
}

void sendColorNameEvent(final Map descMap, final Integer huePar=null, final Integer saturationPar=null) {
    Integer hue = huePar == null ? safeToInt(getDw(descMap)?.currentValue('hue')) : huePar
    Integer saturation = saturationPar == null ? safeToInt(getDw(descMap)?.currentValue('saturation')) : saturationPar
    logTrace "sendColorNameEvent -> huePar:${huePar}  saturationPar=${saturationPar} hue:${hue} saturation:${saturation}"
    if (hue == null || saturation == null) { logWarn "sendColorNameEvent: hue:${hue} <b>or</b> saturation:${saturation} is null"; return }
    String colorName = convertHueToGenericColorName(hue, saturation)    //  (Since 2.3.2) - for RGB bulbs only
    sendMatterEvent([
        name: 'colorName',
        value: colorName,
        descriptionText: "${getDeviceDisplayName(descMap?.endpoint)} color is ${colorName}"
    ], descMap, true)
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
        ], descMap, true)
    }
    else {
        logDebug "parseThermostat: ${(ThermostatClusterAttributes[descMap.attrInt] ?: GlobalElementsAttributes[descMap.attrInt] ?: UNKNOWN)} = ${descMap.value}"
    }
}

//events

// Common code method for sending events
void sendMatterEvent(final Map<String, String> eventParams, DeviceWrapper dw, ignoreDuplicates = false) {
    String id = dw?.getDataValue('id') ?: '00'
    sendMatterEvent(eventParams, [endpoint: id], ignoreDuplicates)
}

void sendMatterEvent(final Map<String, String> eventParams, Map descMap = [:], ignoreDuplicates = false) {
    String name = eventParams['name']
    String value = eventParams['value']
    String descriptionText = eventParams['descriptionText']
    String unit = eventParams['unit']
    logTrace "sendMatterEvent: name:${name} value:${value} descriptionText:${descriptionText} unit:${unit}"

    String dni = ''
    // get the dni from the descMap eddpoint
    if (descMap != [:]) {
        dni = "${device.id}-${descMap.endpoint}"
    }
    if (descriptionText == null) {
        descriptionText = "${getDeviceDisplayName(descMap?.endpoint)} ${name} is ${value}"
    }
    ChildDeviceWrapper dw = getChildDevice(dni) // null if dni is null for the parent device
    Map eventMap = [name: name, value: value, descriptionText: descriptionText, unit: unit, type: 'physical']
    if (state.states['isRefresh'] == true) {
        eventMap.descriptionText += ' [refresh]'
        eventMap.isStateChange = true   // force the event to be sent
        eventMap.isRefresh = true
    }
    if (state.states['isDiscovery'] == true) {
        eventMap.descriptionText += ' [discovery]'
        eventMap.isStateChange = true   // force the event to be sent
        eventMap.isDiscovery = true
    }
    // TODO - use the child device wrapper to check the current value !!!!!!!!!!!!!!!!!!!!!

    if (ignoreDuplicates == true && state.states['isRefresh'] == false) {
        boolean isDuplicate = false
        Object latestEvent = dw?.device?.currentState(name)
        //latestEvent.properties.each { k, v -> logWarn ("$k: $v") }
        try {
            if (latestEvent != null) {
                if (latestEvent.value != null) {
                    if (latestEvent.dataType in ['NUMBER', 'DOUBLE', 'FLOAT'] ) {
                        isDuplicate = Math.abs(latestEvent.doubleValue - safeToDouble(value)) < 0.00001
                    }
                    else if (latestEvent.dataType == 'STRING' || latestEvent.dataType == 'ENUM' || latestEvent.dataType == 'DATE') {
                        isDuplicate = (latestEvent.stringValue == value.toString())
                    }
                    else if (latestEvent.dataType == 'JSON_OBJECT') {
                        isDuplicate = (latestEvent.jsonValue == value.toString())   // TODO - check this
                    }
                    else {
                        isDuplicate = false
                        logWarn "sendMatterEvent: unsupported dataType:${latestEvent.dataType}"
                    }
                }
                else {
                    logTrace "sendMatterEvent: latestEvent.value is null"
                }
            }
            else {
                logTrace "sendMatterEvent: latestEvent is null"
            }
        } catch (Exception e) {
            logWarn "sendMatterEvent: error checking for duplicates: ${e}"
        }
        if (isDuplicate) {
            logTrace "sendMatterEvent: <b>IGNORED</b> duplicate event: ${eventMap.descriptionText} (value:${value} dataType:${latestEvent?.dataType})"
            return
        }
        else {
            logTrace "sendMatterEvent: <b>NOT IGNORED</b> event: ${eventMap.descriptionText} (value:${value} latestEvent.value = ${latestEvent?.value} dataType:${latestEvent?.dataType})"
        }
    }
    else {
        logTrace "sendMatterEvent: <b>ignoreDuplicates=false</b> or isRefresh=${state.states['isRefresh'] } for event: ${eventMap.descriptionText} (value:${value})"
    }
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

/*
 *  Discover all the endpoints and clusters for the Bridge and all the Bridged Devices
 */
void _DiscoverAll(statePar = null) {
    logWarn "_DiscoverAll()"
    Integer stateSt = DISCOVER_ALL_STATE_INIT
    state.stateMachines = [:]
    // ['All', 'BasicInfo', 'PartsList']]
    if (statePar == null || statePar == 'All') { stateSt = DISCOVER_ALL_STATE_INIT }
    else if (statePar == 'BasicInfo') { stateSt = DISCOVER_ALL_STATE_BRIDGE_BASIC_INFO_ATTR_LIST }
    else if (statePar == 'PartsList') { stateSt = DISCOVER_ALL_STATE_GET_PARTS_LIST_START }
    else if (statePar == 'ChildDevices') { stateSt = DISCOVER_ALL_STATE_SUPPORTED_CLUSTERS_START }
    else if (statePar == 'Subscribe') { stateSt = DISCOVER_ALL_STATE_SUBSCRIBE_KNOWN_CLUSTERS }
    else {
        logWarn "_DiscoverAll(): unknown statePar:${statePar} !"
        return
    }

    discoverAllStateMachine([action: START, goToState: stateSt])
}

void readAttribute(Integer endpoint, Integer cluster, Integer attrId) {
    List<Map<String, String>> attributePaths = [matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer)]
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
    logInfo "debug logging is: ${logEnable == true} description logging is: ${txtEnable == true}"
    if (settings.logEnable)   { runIn(86400, logsOff) }   // 24 hours
    if (settings.traceEnable) { logTrace settings; runIn(1800, traceOff) }   // 1800 = 30 minutes

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
    // compare state.preferences.minimizeStateVariables with settings.minimizeStateVariables was changed and call the minimizeStateVariables()
    if (state.preferences == null) { state.preferences = [:] }
    if ((state.preferences['minimizeStateVariables'] ?: false) != settings?.minimizeStateVariables && settings?.minimizeStateVariables == true) {
        minimizeStateVariables(['true'])
    }
    state.preferences['minimizeStateVariables'] = settings.minimizeStateVariables
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
    //deleteAllChildDevices()
    initializeVars(fullInit = true)
    //initialize()
    //configure()
    updated()
    sendInfoEvent('All Defaults Loaded! F5 to refresh')
}

void initialize() {
    log.warn 'initialize()...'
    unschedule()
    if (state.states == null) { state.states = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.stats == null)  { state.stats = [:] }
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
    log.warn "initialize(): calling sendSubsribeList()! (last unsubscribe was more than ${timeSinceLastSubscribe} seconds ago)"
    state.lastTx['subscribeTime'] = now()
    state.states['isUnsubscribe'] = false
    state.states['isSubscribe'] = true  // should be set to false in the parse() method
    sendEvent([name: 'initializeCtr', value: state.stats['initializeCtr'], descriptionText: "${device.displayName} initializeCtr is ${state.stats['initializeCtr']}", type: 'digital'])
    scheduleCommandTimeoutCheck(delay = 55)
    sendSubsribeList()
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

void clearSubscriptionsState() {
    state.subscriptions = []
}

String updateStateSubscriptionsList(String addOrRemove, Integer endpoint, Integer cluster, Integer attrId) {
    String cmd = ''
    logTrace "updateStateSubscriptionsList(action: ${addOrRemove} endpoint:${endpoint}, cluster:${cluster}, attrId:${attrId})"
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(endpoint as Integer, cluster as Integer, attrId as Integer))
    // format EP_CLUSTER_ATTRID
    List<String> newSub = [endpoint, cluster, attrId]
    List<List<String>> stateSubscriptionsList = state.subscriptions ?: []
    if (addOrRemove == 'add') {
        if (stateSubscriptionsList.contains(newSub)) {
            logTrace "updateStateSubscriptionsList(): subscription already exists: ${newSub}"
        } else {
            logTrace "updateStateSubscriptionsList(): adding subscription: ${newSub}"
            cmd = matter.subscribe(0, 0xFFFF, attributePaths)
            //sendToDevice(cmd)     // commented out 2024-02-17
            stateSubscriptionsList.add(newSub)
            state.subscriptions = stateSubscriptionsList
        }
    }
    else if (addOrRemove == 'remove') {
        if (stateSubscriptionsList.contains(newSub)) {
            stateSubscriptionsList.remove(newSub)
            state.subscriptions = stateSubscriptionsList
        } else {
            logWarn "updateStateSubscriptionsList(): subscription not found!: ${newSub}"
        }
    }
    else if (addOrRemove == 'show') {
        if (logEnable) {
            logInfo "updateStateSubscriptionsList(): state.subscriptions size is ${state.subscriptions?.size()}"
            logInfo "updateStateSubscriptionsList(): state.subscriptions = ${state.subscriptions}"
        }
    }
    else {
        logWarn "updateStateSubscriptionsList(): unknown action: ${addOrRemove}"
    }
    return cmd
}

void sendSubsribeList() {
    sendInfoEvent('sendSubsribeList()...Please wait.', 'sent device subscribe command')
    List<String> cmds = getSubscribeOrRefreshCmdList('SUBSCRIBE_ALL')
    if (cmds != null && cmds != []) {
        logTrace "sendSubsribeList(): cmds = ${cmds}"
        sendToDevice(cmds)
    }
}

List<String> getSubscribeOrRefreshCmdList(action='REFRESH') {
    // the state.subscriptions list is: subscriptions : [[0, 29, 3], [36, 6, 0], [36, 8, 0], [36, 768, 0], [36, 768, 1], [36, 768, 7], [54, 6, 0], [8, 1029, 0], [7, 1026, 0], [55, 6, 0], [15, 6, 0], [13, 513, 0]]
    List<List<Integer>>  stateSubscriptionsList = new ArrayList<List<Integer>>(state.subscriptions ) ?: []
    List<String> cmdsList = []
    logDebug "getSubscribeCmdList(): stateSubscriptionsList = ${stateSubscriptionsList}"

    LinkedHashMap<Integer, List<List<Integer>>>  groupedSubscriptionsByCluster = stateSubscriptionsList.groupBy { it[1] }
    logTrace "groupedSubscriptionsByCluster=${groupedSubscriptionsByCluster}"
    // sample groupedSubscriptionsByCluster:  768 : [[36, 768, 0], [36, 768, 1], [36, 768, 7]]
    for (Map.Entry<Integer, List<List<Integer>>> entry : groupedSubscriptionsByCluster.entrySet()) {
        Integer cluster = entry.getKey()
        Integer attribute = null
        List<List<Integer>> value = entry.getValue()
        logTrace "Cluster:${cluster}, value:${value}"
        // check if the cluster is in the supported clusters list
        if (!SupportedMatterClusters.containsKey(cluster)) {
            logWarn "getSubscribeCmdList(): cluster 0x${HexUtils.integerToHexString(cluster, 2)} is not in the SupportedMatterClusters list!"
            continue  // do not subscribe to this cluster, continue with the next cluster
        }
        // Sample groupedSubscriptionsByAttribute Attribute 0 : [[36, 768, 0]]
        Map<Integer, List<List<Integer>>> groupedSubscriptionsByAttribute = value.groupBy { it[2] }
        logTrace "groupedSubscriptionsByAttribute=${groupedSubscriptionsByAttribute}"
        for (Map.Entry<Integer, List<List<Integer>>> entry2 : groupedSubscriptionsByAttribute.entrySet()) {
            List<Map<String, String>> attributePaths = []       // individual attributePaths for each attribute
            attribute = entry2.getKey()
            List<List<Integer>> endpointsList = entry2.getValue()
            logTrace "Cluster:${cluster}, Attribute:${attribute}, endpointsList:${endpointsList}"

            List<List<Map<Integer, Map<String, Integer>>>> supportedSubscriptions = SupportedMatterClusters[cluster]['subscriptions']
            //def supportedSubscriptions = SupportedMatterClusters[cluster]['subscriptions']
            // sample supportedSubscriptions=[[0:[min:0, max:65535, delta:0]], [1:[min:0, max:65535, delta:0]]]
            if (supportedSubscriptions == null || supportedSubscriptions == []) {
                logWarn "<b>getSubscribeCmdList(): supportedSubscriptions is null or empty for cluster:${cluster} attribute:${attribute}!</b>"
                continue  // do not subscribe to this attribute, continue with the next
            }
            // make a list of integer keys from  the supportedSubscriptions list
            List<Integer> supportedSubscriptionsKeys = supportedSubscriptions*.keySet().flatten()
            logTrace "supportedSubscriptionsKeys=${supportedSubscriptionsKeys}"
            // check if the attribute is in the supportedSubscriptionsKeys list
            if (!supportedSubscriptionsKeys.contains(attribute)) {
                logWarn "getSubscribeCmdList(): attribute 0x${HexUtils.integerToHexString(attribute, 2)} is not in the supportedSubscriptionsKeys:${supportedSubscriptionsKeys} list! "
                continue  // do not subscribe to this attribute, continue with the next
            }
            // here we have a list of same cluster, same attribute, different endpoints
            endpointsList.each { endpointList ->
                Integer endpoint = endpointList[0]
                //logDebug "endpoint:${endpoint}, Cluster:${cluster}, Attribute:${attribute}"
                attributePaths.add(matter.attributePath(endpoint, cluster, attribute))
            }
            logTrace "attribute: ${attribute} attributePaths:${attributePaths} supportedSubscriptions[attribute]:${supportedSubscriptions[attribute]}"
            // assume the min, max and delta values are the same for all endpoints
            def firstSupportedSubscription = supportedSubscriptions[attribute]?.get(0)
            logTrace "firstSupportedSubscription = ${firstSupportedSubscription}"
            Integer min = firstSupportedSubscription?.get('min') ?: 0
            Integer max = firstSupportedSubscription?.get('max') ?: 0xFFFF
            logTrace "min=${min}, max=${max}, delta=${delta}"
            if (action == 'REFRESH_ALL') {
                cmdsList.add(matter.readAttributes(attributePaths))
            }
            else if (action == 'SUBSCRIBE_ALL') {
                cmdsList.add(matter.subscribe(min, max, attributePaths))
            }
        } // for each attribute
        logTrace "attribute:${attribute} cmdsList=${cmdsList}"
        //return cmdsList
    }   // for each cluster
    return cmdsList
}

String subscribeCmd() {
    List<Map<String, String>> attributePaths = []
    attributePaths.add(matter.attributePath(0, 0x001D, 0x03))   // Descriptor Cluster - PartsList
    attributePaths.addAll(state.subscriptions?.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    })
    if (attributePaths.isEmpty()) {
        logWarn 'subscribeCmd(): attributePaths is empty!'
        return null
    }
    return matter.subscribe(0, 0xFFFF, attributePaths)
}

// TODO - check if this is still needed ?
void checkSubscriptionStatus() {
    if (state.states == null) { state.states = [:] }
    if (state.states['isUnsubscribe'] == true) {
        logInfo 'checkSubscription(): unsubscribe() is completed.'
        sendInfoEvent('unsubscribe() is completed', 'something was received in the parse() method')
        state.states['isUnsubscribe'] = false
    }
    if (state.states['isSubscribe'] == true) {
        logInfo 'checkSubscription(): completed.'
        sendInfoEvent('completed', 'something was received in the parse() method')
        state.states['isSubscribe'] = false
    }
}

/**
 * This method is called at the end of the discovery process to update the state.subscriptions list of lists.
 * It collects the known clusters and attributes based on the state.fingerprintXX.Subscribe individual devoces lists.
 * It iterates through each fingerprint in the state and checks if the fingerprint has entries in the SupportedMatterClusters list.
 * If a match is found, it adds the corresponding entries to the state.subscriptions list of lists.
 * The number of the found subscriptions and the device count are logged and sent as info events.
 */
void fingerprintsToSubscriptionsList() {
    logDebug 'fingerprintsToSubscriptionsList:'
    sendInfoEvent('Subscribing for known clusters and attributes reporting ...')
    state.subscriptions = []
    // subscribe to the Descriptor cluster PartsList attribute
    updateStateSubscriptionsList('add', 0, 0x001D, 0x0003)

    // For each fingerprint in the state, check if the fingerprint has entries in the SupportedMatterClusters list. Then, add these entries to the state.subscriptions map
    Integer deviceCount = 0
    //Map stateCopy = state.clone()
    Map stateCopy = state
    state.each { fingerprintName, fingerprintMap ->
        if (fingerprintName.startsWith('fingerprint')) {
            boolean knownClusterFound = false
            List subscribeList = fingerprintMap['Subscribe'] as List
            logTrace "fingerprintsToSubscriptionsList: fingerprintName:${fingerprintName} subscribeList:${subscribeList}"
            // Subscribe=[6, 8, 768]
            subscribeList.each { cluster  ->
                Integer clusterInt = safeToInt(cluster)
                List supportedClustersKeys = SupportedMatterClusters.keySet().collect { it as Integer }
                if (!supportedClustersKeys.contains(clusterInt)) {
                    logWarn "fingerprintsToSubscriptionsList: clusterInt:${clusterInt} is not in the SupportedMatterClusters list!"
                    return  // continue with the next cluster
                }
                def supportedSubscriptions = SupportedMatterClusters[clusterInt]['subscriptions']
                def supportedSubscriptionsKeys = supportedSubscriptions*.keySet().flatten()
                logTrace "fingerprintsToSubscriptionsList: clusterInt:${clusterInt} subscribeList=${subscribeList} supportedSubscriptions=${supportedSubscriptions} supportedSubscriptionsKeys=${supportedSubscriptionsKeys}"
                String endpointId = fingerprintName.substring(fingerprintName.length() - 2, fingerprintName.length())
                // Add the supported subscriptions to the state.subscriptions list
                supportedSubscriptionsKeys.each { attribute ->
                    // check whether the attribute_0xFFFB entry is in the fingerprintMap
                    String clusterListName = HexUtils.integerToHexString(clusterInt, 2) + '_FFFB'
                    List clusterAttrList = fingerprintMap[clusterListName]
                    // convert clusterAttrList from list of hex to list of integers
                    clusterAttrList = clusterAttrList.collect { safeHexToInt(it) }
                    logTrace "fingerprintsToSubscriptionsList: clusterInt:${clusterInt} attribute:${attribute} clusterListName=${clusterListName} clusterAttrList=${clusterAttrList}"
                    if (clusterAttrList != null && clusterAttrList != []) {
                        // 0006_FFFB=[00, 4000, 4001, 4002, 4003, FFF8, FFF9, FFFB, FFFC, FFFD]
                        // check if the attribute is in the clusterAttrList
                        if (!clusterAttrList.contains(attribute)) {
                            logWarn "fingerprintsToSubscriptionsList: clusterInt:${clusterInt} attribute:${attribute} is not in the clusterAttrList ${clusterAttrList}!"
                            return  // continue with the next attribute
                        }
                        logDebug "fingerprintsToSubscriptionsList: updateStateSubscriptionsList: adding endpointId=${endpointId} clusterInt:${clusterInt} attribute:${attribute} clusterListName=${clusterListName} to the state.subscriptions list!"
                        updateStateSubscriptionsList(addOrRemove = 'add', endpoint = safeHexToInt(endpointId), cluster = clusterInt, attrId = safeToInt(attribute))
                    }
                    else {
                        logWarn "fingerprintsToSubscriptionsList: clusterInt:${clusterInt} attribute:${attribute} clusterListName ${clusterListName} is not in the fingerprintMap!"
                    }
                }
                // done!
                knownClusterFound = true
            }
            if (knownClusterFound) { deviceCount ++ }
        }
    }
    int numberOfSubscriptions = state.subscriptions?.size() ?: 0
    sendInfoEvent("the number of subscriptions is ${numberOfSubscriptions}")
    sendEvent([name: 'deviceCount', value: deviceCount, descriptionText: "${device.displayName} subscribed for events from ${deviceCount} devices"])
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
    /*
    String fingerprintName = getFingerprintName(deviceNumber)
    if (fingerprintName == null || state[fingerprintName] == null) {
        logWarn "setSwitch(): fingerprintName '${fingerprintName}' is not valid! (${getDeviceDisplayName(deviceNumber)})"
        return
    }
    String cluster = '0006'
    // list key is 0006_FFFB=[00, FFF8, FFF9, FFFB, FFFD, FFFC]
    String stateClusterName = getStateClusterName([cluster: cluster, attrId: 'FFFB'])
    List<String> onOffClusterAttributesList = state[fingerprintName][stateClusterName] as List
    if (onOffClusterAttributesList == null) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceDisplayName(deviceNumber)} !"
        return
    }
    // check if '00' is in the onOffClusterAttributesList
    if (!onOffClusterAttributesList.contains('00')) {
        logWarn "setSwitch(): OnOff capability is not present for ${getDeviceDisplayName(deviceNumber)} !"
        return
    }
    */ // commented out 2024-02-26

    // find the command in the OnOffClusterCommands map
    Integer onOffcmd = OnOffClusterCommands.find { k, v -> v == command }?.key
    logTrace "setSwitch(): command = ${command}, onOffcmd = ${onOffcmd}, onOffCommandsList = ${onOffCommandsList}"
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
    logTrace "setSwitch(): sending command '${cmd}'"
    sendToDevice(cmd)
}

void refresh() {
    logInfo'refresh() ...'
    checkDriverVersion()
    setRefreshRequest()    // 6 seconds
    List<String> cmdsList = getSubscribeOrRefreshCmdList('REFRESH_ALL')
    if (cmdsList != null && cmdsList != []) {
        logDebug "refresh(): cmdsList = ${cmdsList}"
        sendToDevice(cmdsList)
    }
    else {
        logWarn 'refresh(): cmdsList is null or empty!'
    }
}

String refreshCmd() {
    logInfo 'refreshCmd() ...'
    List<Map<String, String>> attributePaths = state.subscriptions?.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    } ?: []
    if (state['bridgeDescriptor'] == null) { logWarn 'refreshCmd(): state.bridgeDescriptor is null!'; return null  }
    List<String> serverList = (state['bridgeDescriptor']['0033_FFFB'] as List)?.clone()  // new ArrayList<>(originalList)
    serverList?.removeAll(['FFF8', 'FFF9', 'FFFB', 'FFFC', 'FFFD', '00'])                // 0x0000  : 'NetworkInterfaces' - not supported
    if (serverList == null) { logWarn 'refreshCmd(): serverList is null!'; return null  }
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

/**
  *  Tuya Standard Instruction Set Category Mapping to Hubitat Drivers
  *  https://developer.tuya.com/en/docs/iot/standarddescription?id=K9i5ql6waswzq
  *  MATTER : https://developer.tuya.com/en/docs/iot-device-dev/Matter_Product_Feature_List?id=Kd2wjfpuhgmrw
  */
//private static Map mapTuyaCategory(Map d) {
Map mapTuyaCategory(Map d) {
    // check order is important!
    logDebug "mapTuyaCategory: ServerList=${d.ServerList} DeviceType=${d.DeviceType}"

    if ('0300' in d.ServerList) {
        if ('0D' in d.DeviceType || '13' in d.DeviceType) {
            return [ driver: 'Generic Component RGBW', product_name: 'RGB Extended Color Light' ]
        }
        else {
            return [ driver: 'Generic Component CT', product_name: 'Color Temperature Light' ]
        }
    }
    if ('08' in d.ServerList) {   // Dimmer
        return [ driver: 'Generic Component Dimmer', product_name: 'Dimmer/Bulb' ]
    }
    if ('45' in d.ServerList) {   // Contact Sensor
        return [ driver: 'Generic Component Contact Sensor', product_name: 'Contact Sensor' ]
    }
    if ('0400' in d.ServerList) {   // Illuminance Sensor
        return [ driver: 'Generic Component Omni Sensor', product_name: 'Illuminance Sensor' ]
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
    if ('2F' in d.ServerList) {   // Power Source
        return [ namespace: 'kkossev', driver: 'Matter Generic Component Battery', product_name: 'Battery' ]
    }

    return [ driver: 'Generic Component Switch', product_name: 'Unknown' ]
}

/* --------------------------------------------------------------------------------------------------------------
 * Implementation of component commands from child devices
 */

// Component command to refresh device
void componentRefresh(DeviceWrapper dw) {
    String id = dw.getDataValue('id')       // in hex
    // find the id in the state.subscriptions list of lists - this is the first element of the lists
    List<List<Integer>> stateSubscriptionsList = state.subscriptions ?: []
    List<List<Integer>> deviceSubscriptionsList = stateSubscriptionsList.findAll { it[0] == HexUtils.hexStringToInt(id) }
    logDebug "componentRefresh(${dw}) id=${id} deviceSubscriptionsList=${deviceSubscriptionsList}"
    if (deviceSubscriptionsList == null || deviceSubscriptionsList == []) {
        logWarn "componentRefresh(${dw}) id=${id} deviceSubscriptionsList is empty!"
        return
    }
    // for deviceSubscriptionsList, readAttributes
    List<Map<String, String>> attributePaths = deviceSubscriptionsList.collect { sub ->
        matter.attributePath(sub[0] as Integer, sub[1] as Integer, sub[2] as Integer)
    }
    if (!attributePaths.isEmpty()) {
        setRefreshRequest()    // 6 seconds
        sendToDevice(matter.readAttributes(attributePaths))
        logDebug "componentRefresh(${dw}) id=${id} : refreshing attributePaths=${attributePaths}"
    }
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
        logDebug "Turning ${dw} on"
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
        logDebug "Turning ${dw} off"
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
    logDebug "sending Open command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentOpen(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x00) // 0x0102 = Window Covering Cluster, 0x00 = UpOrOpen
    logTrace "componentOpen(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// Component command to close device
void componentClose(DeviceWrapper dw) {
    if (!dw.hasCommand('close')) { logError "componentClose(${dw}) driver '${dw.typeName}' does not have command 'close' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logDebug "sending Close command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentClose(): deviceNumber ${deviceNumberPar} is not valid!"; return }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x01) // 0x0102 = Window Covering Cluster, 0x01 = DownOrClose
    logTrace "componentClose(): sending command '${cmd}'"
    sendToDevice(cmd)
}

// prestage level : https://community.hubitat.com/t/sengled-element-color-plus-driver/21811/2

// Component command to set level
void componentSetLevel(DeviceWrapper dw, BigDecimal levelPar, BigDecimal durationPar=null) {
    if (!dw.hasCommand('setLevel')) { logError "componentSetLevel(${dw}) driver '${dw.typeName}' does not have command 'setLevel' in ${dw.supportedCommands}" ; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    if (levelPar == null) { logWarn "componentSetLevel(): levelPar is null!"; return }
    int level = levelPar as int
    level = level < 0 ? 0 : level > 100 ? 100 : level
    int duration = (durationPar ?: 0) * 10
    logDebug "Setting level ${level} durtion ${duration} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
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
    if (direction == null) { logWarn "componentStartLevelChange(): direction is null!"; return }
    String moveMode = direction == 'up' ? '00' : '01'
    Integer rateInt = 5  // seconds
    //String moveRate = zigbee.swapOctets(HexUtils.integerToHexString(rateInt as int, 1))   // TODO - errorjava.lang.StringIndexOutOfBoundsException: begin 2, end 4, length 2 on line 1684 (method componentStartLevelChange)
    String moveRate = '50'
    List<Map<String, String>> cmdFields = []
    logDebug "Starting level change UP for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
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

void componentSetColorTemperature(DeviceWrapper dw, BigDecimal colorTemperature, BigDecimal level=null, BigDecimal duration=null) {
    if (!dw.hasCommand('setColorTemperature')) { logError "componentSetColorTemperature(${dw}) driver '${dw.typeName}' does not have command 'setColorTemperature' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logDebug "Setting color temperature ${colorTemperature} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (dw.currentValue('switch') == 'off') {
        logDebug "componentSetColorTemperature(): device is off, turning it on"
        componentOn(dw)
    }
    if (dw.currentValue('colorMode') != 'CT') {
        logDebug "componentSetColor(): setting color mode to CT"
        sendMatterEvent([name: 'colorMode', value: 'CT', isStateChange: true, displayed: false], dw, true)
    }
    String colorTemperatureMireds = byteReverseParameters(HexUtils.integerToHexString(ctToMired(colorTemperature as int), 2))
    String transitionTime = zigbee.swapOctets(HexUtils.integerToHexString((duration ?: 0) as int, 2))
    List<Map<String, String>> cmdFields = []
    cmdFields.add(matter.cmdField(DataType.UINT16, 0, colorTemperatureMireds))
    cmdFields.add(matter.cmdField(DataType.UINT16, 1, transitionTime))
    String cmd = matter.invoke(deviceNumber, 0x0300, 0x0A, cmdFields)  // 0x0300 = Color Control Cluster, 0x0A = MoveToColorTemperature
    sendToDevice(cmd)
    if (level != null || duration != null) {
        componentSetLevel(dw, level, duration)
    }
}

void componentSetHue(DeviceWrapper dw, BigDecimal hue) {
    if (!dw.hasCommand('setHue')) { logError "componentSetHue(${dw}) driver '${dw.typeName}' does not have command 'setHue' in ${dw.supportedCommands}"; return }
    Integer deviceNumber =
    logDebug "Setting hue ${hue} for device ${dw.getDataValue('id')} ${dw}"
    Integer hueScaled = Math.min(Math.max(Math.round(hue * 2.54), 0), 254)
    String hueHex = byteReverseParameters(HexUtils.integerToHexString(hueScaled, 1))
    String transitionTimeHex = zigbee.swapOctets(HexUtils.integerToHexString(1, 2))
    List<Map<String, String>> cmdFields = [
        matter.cmdField(DataType.UINT8, 0, hueHex),
        matter.cmdField(DataType.UINT8, 1, "00"), // Direction 00 = Shortest
        matter.cmdField(DataType.UINT16, 2, transitionTimeHex) // TransitionTime in 0.1 seconds, uint16 0-65534, byte swapped
    ]
    String cmd = matter.invoke(HexUtils.hexStringToInt(dw.getDataValue('id')), 0x0300, 0x00, cmdFields) // 0x0300 = Color Control Cluster, 0x00 = MoveToHue
    sendToDevice(cmd)
}

void componentSetSaturation(DeviceWrapper dw, BigDecimal saturation) {
    if (!dw.hasCommand('setSaturation')) { logError "componentSetSaturation(${dw}) driver '${dw.typeName}' does not have command 'setSaturation' in ${dw.supportedCommands}"; return }
    Integer saturationScaled = Math.min(Math.max(Math.round(saturation * 2.54), 0), 254)
    String saturationHex = byteReverseParameters(HexUtils.integerToHexString(saturationScaled as int, 1))
    String transitionTimeHex = zigbee.swapOctets(HexUtils.integerToHexString(1, 2))
    List<Map<String, String>> cmdFields = [
        matter.cmdField(DataType.UINT8, 0, saturationHex),
        matter.cmdField(DataType.UINT16, 1, transitionTimeHex)
    ]
    String cmd = matter.invoke(HexUtils.hexStringToInt(dw.getDataValue('id')), 0x0300, 0x03, cmdFields)
    sendToDevice(cmd)
}

void componentSetColor(DeviceWrapper dw, Map colormap) {
    if (!dw.hasCommand('setColor')) { logError "componentSetColor(${dw}) driver '${dw.typeName}' does not have command 'setColor' in ${dw.supportedCommands}"; return }
    logDebug "Setting color hue ${colormap.hue} saturation ${colormap.saturation} level ${colormap.level} for device# ${dw.getDataValue('id')} ${dw}"
    if (dw.currentValue('switch') == 'off') {
        logDebug "componentSetColor(): device is off, turning it on"
        componentOn(dw)
    }
    if (dw.currentValue('colorMode') != 'RGB') {
        logDebug "componentSetColor(): setting color mode to RGB"
        sendMatterEvent([name: 'colorMode', value: 'RGB', isStateChange: true, displayed: false], dw, true)
    }
    Integer hueScaled = Math.round(Math.max(0, Math.min((double)(colormap.hue * 2.54), 254.0)))
    Integer saturationScaled = Math.round(Math.max(0, Math.min((colormap.saturation * 2.54).toInteger(), 254)))
    Integer levelScaled = Math.round(Math.max(0, Math.min((colormap.level * 2.54).toInteger(), 254)))
    Integer transitionTime = 1
    logDebug    "Setting color hue ${hueScaled} saturation ${saturationScaled} level ${levelScaled} for device# ${dw.getDataValue('id')} ${dw}"
    List<Map<String, String>> cmdFields = [
        matter.cmdField(DataType.UINT8, 0, byteReverseParameters(HexUtils.integerToHexString(hueScaled as int, 1))),
        matter.cmdField(DataType.UINT8, 1, byteReverseParameters(HexUtils.integerToHexString(saturationScaled as int, 1))),
        matter.cmdField(DataType.UINT16, 2, zigbee.swapOctets(HexUtils.integerToHexString(transitionTime as int, 2)))
    ]
    String cmd = matter.invoke(HexUtils.hexStringToInt(dw.getDataValue('id')), 0x0300, 0x06, cmdFields)  // 0x0300 = Color Control Cluster, 0x06 = MoveToHueAndSaturation ;0x07 = MoveToColor
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

// Component command to set position  (used by Window Shade)
void componentSetPosition(DeviceWrapper dw, BigDecimal positionPar) {
    if (!dw.hasCommand('setPosition')) { logError "componentSetPosition(${dw}) driver '${dw.typeName}' does not have command 'setPosition' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    int position = positionPar as int
    if (position < 0) { position = 0 }
    if (position > 100) { position = 100 }
    logDebug "Setting position ${position} for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    List<Map<String, String>> cmdFields = []
    //cmdFields.add(matter.cmdField(0x05, 0x00, zigbee.swapOctets(HexUtils.integerToHexString((100 - position) * 100, 2))))
    cmdFields.add(matter.cmdField(0x05, 0x00, zigbee.swapOctets(HexUtils.integerToHexString(position * 100, 2))))
    cmd = matter.invoke(deviceNumber, 0x0102, 0x05, cmdFields)  // 0x0102 = Window Covering Cluster, 0x05 = GoToLiftPercentage
    sendToDevice(cmd)
}

// Component command to set position direction (not used by Window Shade !)
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
    logDebug "Stopping position change for device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "setSwitch(): deviceNumber ${deviceNumberPar} is not valid!"; return; }
    String cmd = matter.invoke(deviceNumber, 0x0102, 0x02) // 0x0102 = Window Covering Cluster, 0x02 = StopMotion
    logTrace "componentStopPositionChange(): sending command '${cmd}'"
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
    //return

    if (!dw.hasCommand('lock')) { logError "componentLock(${dw}) driver '${dw.typeName}' does not have command 'lock' in ${dw.supportedCommands}"; return }
    Integer deviceNumber = HexUtils.hexStringToInt(dw.getDataValue('id'))
    logInfo "sending Lock command to device# ${deviceNumber} (${dw.getDataValue('id')}) ${dw}"
    if (deviceNumber == null || deviceNumber <= 0 || deviceNumber > 255) { logWarn "componentLock(): deviceNumber ${deviceNumberPar} is not valid!"; return }

    List<Map<String, String>> cmdFields = []
    //cmdFields.add(matter.cmdField(DataType.STRING_OCTET8, 0x00, ""))
    cmdFields.add(matter.cmdField(DataType.STRING_OCTET8, 0x00))
    //String cmd = matter.invoke(deviceNumber, 0x0101, 0x00, cmdFields) // 0x0101 = DoorLock Cluster, 0x00 = LockDoor
    String cmd = matter.invoke(deviceNumber, 0x0101, 0x00) // 0x0101 = DoorLock Cluster, 0x00 = LockDoor

    /*
    List<Map<String, String>> attrWriteRequests = [matter.attributeWriteRequest(deviceNumber, 0x0101, 0x0000, DataType.UINT8, '10')]
    String cmd = matter.writeAttributes(attrWriteRequests)
    */

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
        data['name'] = getDeviceDisplayName(data['id'])
        data['fingerprintName'] = fingerprint
        data['ServerList'] = fingerprintMap['ServerList']
        List deviceTypeList = fingerprintMap['DeviceTypeList'] as List ?: []
        data['DeviceType'] = deviceTypeList
        logWarn "fingerprintToData(): fingerprintMap=${fingerprintMap} data=${data}"
        Map productName = mapTuyaCategory(data)

        data['product_name'] = fingerprintMap['ProductName'] ?: productName['product_name']           // Device Name
        //data['name'] = fingerprintMap['Label'] ?: "Device#${data['id']}"          // Device Label

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
    logDebug "createChildDevices(Map d): product_name ${d.product_name} driver ${mapping}"

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

String getStateDriverVersion() { return state.driverVersion }
void setStateDriverVersion(String version) { state.driverVersion = version }

@CompileStatic
void checkDriverVersion() {
    if (getStateDriverVersion() == null || driverVersionAndTimeStamp() != getStateDriverVersion()) {
        logDebug "updating the settings from the current driver version ${getStateDriverVersion()} to the new version ${driverVersionAndTimeStamp()}"
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        setStateDriverVersion(driverVersionAndTimeStamp())
        final boolean fullInit = false
        initializeVars(fullInit)
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

void checkHealthStatusForOffline() {
    if (state.health == null) { state.health = [:] }
    Integer ctr = state.health['checkCtr3'] ?: 0
    String healthStatus = device.currentValue('healthStatus') ?: 'unknown'
    logDebug "checkHealthStatusForOffline: healthStstus = ${healthStatus} checkCtr3=${ctr}"
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        state.health['offlineCtr'] = (state.health['offlineCtr'] ?: 0) + 1      // increase the offline counter even if the device is already not present - changed 02/1/2024
        if (healthStatus != 'offline') {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "checkHealthStatusForOffline: ${healthStatus} (checkCtr3=${ctr}) offlineCtr=${state.health['offlineCtr']}"
    }
    state.health['checkCtr3'] = ctr + 1
}

// a periodic cron job, increasing the checkCtr3 each time called.
// checkCtr3 is cleared when some event is received from the device.
void deviceHealthCheck() {
    checkDriverVersion()
    checkHealthStatusForOffline()
    if (((settings.healthCheckMethod as Integer) ?: 0) == 2) { //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        ping()          // TODO - ping() results in initialize() call if the device is switched off !
    }
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
    // added 02/11/2024 - checkHealthStatusForOffline() will increase the check3Ctr and will send the healthStatus event if the device is offline
    checkHealthStatusForOffline()
}

void sendRttEvent(String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null) { state.lastTx = [:] }
    Integer timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
    String descriptionText = "${device.displayName} Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']} (HE uptime: ${formatUptime()})"
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

// credits @thebearmay
String formatUptime() {
    try {
        Long ut = location.hub.uptime.toLong()
        Integer days = Math.floor(ut/(3600*24)).toInteger()
        Integer hrs = Math.floor((ut - (days * (3600*24))) /3600).toInteger()
        Integer min = Math.floor( (ut -  ((days * (3600*24)) + (hrs * 3600))) /60).toInteger()
        Integer sec = Math.floor(ut -  ((days * (3600*24)) + (hrs * 3600) + (min * 60))).toInteger()
        String attrval = "${days.toString()}d, ${hrs.toString()}h, ${min.toString()}m, ${sec.toString()}s"
        return attrval
    } catch(ignore) {
        return UNKNOWN
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
    state.stateMachines = [:]       // driver specific
    state.preferences = [:]         // driver specific
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
        sendEvent([ name: 'endpointsCount', value: 0, type: 'digital'])
        sendEvent([ name: 'deviceCount', value: 0, type: 'digital'])
        sendEvent([ name: 'initializeCtr', value: 0, type: 'digital'])
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_LOG_ENABLE) }
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) }
    if (settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (settings?.minimizeStateVariables == null) { device.updateSetting('minimizeStateVariables', [value: MINIMIZE_STATE_VARIABLES_DEFAULT, type: 'bool']) }
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

@Field static final String DRIVER = 'Matter Advanced Bridge'
@Field static final String WIKI   = 'Wiki page:'

// credits @jtp10181
String fmtHelpInfo(String str) {
	String info = "${DRIVER} v${version()}"
	String prefLink = "<a href='${GITHUB_LINK}' target='_blank'>${WIKI}<br><div style='font-size: 70%;'>${info}</div></a>"
    String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid green; border-radius: 6px; color: green;'"
    String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}


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
    /*
    List<String> list = []
    s = getSubscribeOrRefreshCmdList('SUBSCRIBE_ALL')
    log.warn "getSubscribeOrRefreshCmdList=${s}"
    */
    //fingerprintsToSubscriptionsList()
}
