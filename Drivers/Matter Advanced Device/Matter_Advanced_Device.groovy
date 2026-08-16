/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, MethodCount, MethodSize, NglParseError, NoDef, ParameterCount, UnnecessaryGetter, UnnecessaryObjectReferences, UnusedImport, VariableName */
/**
 *  Matter Advanced Device - Device Driver for Hubitat Elevation
 *
 *  A general purpose utility driver for a directly paired Matter device - the Matter
 *  equivalent of the Hubitat built-in 'Device' driver. Reads and writes any attribute,
 *  invokes any command, discovers endpoints and clusters, and reports the device
 *  firmware version and the Matter OTA software update state.
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
 * The Matter cluster and attribute reference data is derived from the Matter Advanced Bridge matterLib.
 *
 * ver. 1.0.0  2026-08-15 kkossev  - (dev. branch) first version: discovery, Get Info, read/write/invoke,
 *                                   plain English read menu, dynamic picker, housekeeping commands,
 *                                   identify/ping/health, firmware version and Matter OTA state monitoring.
 */

static String version() { '1.0.0' }
static String timeStamp() { '2026/08/16 12:22 PM' }

@Field static final Boolean _DEBUG = false
@Field static final Boolean DEFAULT_DEBUG_LOGGING = false

import groovy.transform.Field
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.matter.DataType

@Field static final String DRIVER_NAME = 'Matter Advanced Device'
@Field static final String IMPORT_URL  = 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Device/Matter_Advanced_Device.groovy'
@Field static final String COMM_LINK   = 'https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342'

// ---- constants referenced by the metadata block: they must be declared BEFORE it ----

@Field static final List<String> READ_MENU_ITEMS = [
    'Firmware version', 'Firmware update status', 'Device name and maker', 'Serial number and unique ID',
    'Battery level', 'On/Off state', 'Brightness', 'Color', 'Temperature', 'Humidity', 'Illuminance',
    'Motion or occupancy', 'Contact, water or boolean state', 'Door lock state', 'Window covering position',
    'Thermostat setpoints', 'Power and energy', 'Air quality', 'Thread network', 'Wi-Fi network',
    'Uptime and reboots', 'Endpoints and features', 'Everything about this device'
]

@Field static final List<String> WRITE_DATA_TYPE_NAMES = [
    'Whole number (UINT8)', 'Whole number (UINT16)', 'Whole number (UINT32)', 'Whole number (UINT64)',
    'Signed number (INT8)', 'Signed number (INT16)', 'Signed number (INT32)', 'Signed number (INT64)',
    'Text (UTF8)', 'True (BOOLEAN)', 'False (BOOLEAN)', 'Nothing / previous state (NULL)'
]

@Field static final List<String> OTA_PROBE_NAMES = [
    'ALL', 'P1 is the OTA cluster there', 'P2 read the OTA attributes', 'P3 subscribe to OTA reports and events',
    'P4 announce an OTA provider', 'P5 encode a 64 bit node id', 'P6 find the hub node id', 'CLEAR results'
]

metadata {
    definition(name: DRIVER_NAME, namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: IMPORT_URL, singleThreaded: true) {
        capability 'Actuator'
        capability 'Sensor'
        capability 'Refresh'
        capability 'Configuration'
        capability 'Health Check'

        // ---- status and health ----
        attribute '_status_',              'string'    // leading underscore keeps it at the top of the Current States list
        attribute 'healthStatus',          'enum', ['unknown', 'offline', 'online']
        attribute 'rtt',                   'number'
        attribute 'lastResult',            'string'
        // ---- identity (Basic Information cluster 0x0028) ----
        attribute 'vendorName',            'string'
        attribute 'productName',           'string'
        attribute 'nodeLabel',             'string'
        attribute 'serialNumber',          'string'
        attribute 'hardwareVersionString', 'string'
        attribute 'specificationVersion',  'string'
        attribute 'endpointsCount',        'number'
        // ---- firmware ----
        attribute 'firmwareVersion',       'string'    // SoftwareVersionString 0x000A - the authoritative one
        attribute 'firmwareVersionCode',   'number'    // SoftwareVersion 0x0009 - raw uint32, for OTA comparison only
        // ---- Matter OTA (OTA Software Update Requestor cluster 0x002A) ----
        attribute 'otaSupported',          'enum', ['yes', 'no', 'unknown']
        attribute 'otaState',              'enum', ['unknown', 'idle', 'querying', 'delayedOnQuery', 'downloading', 'applying', 'delayedOnApply', 'rollingBack', 'delayedOnUserConsent']
        attribute 'otaProgress',           'number'
        attribute 'otaProvider',           'string'
        attribute 'otaLastEvent',          'string'

        // =============== TIER 1 - the plain English menu ===============
        command 'readSomething', [[name: 'What do you want to read?*', type: 'ENUM',
            description: 'Pick a value - the answer appears in Current States and in the live logs',
            constraints: READ_MENU_ITEMS]]

        // =============== TIER 0 - housekeeping (same as the built-in Device driver) ===============
        command 'getInfo', [[name: 'Log the Matter fingerprint of every endpoint - same as the built-in Device driver']]
        command 'getInfoAdvanced', [[name: 'endpoint', type: 'STRING',
            description: 'Dump every cluster and attribute of one endpoint - decimal or 0x hex, blank = all endpoints', defaultValue: '']]
        command 'deleteAllChildDevices',   [[name: 'Remove every child device this driver created']]
        command 'deleteAllCurrentStates',  [[name: 'Clear the Current States list']]
        command 'deleteAllScheduledJobs',  [[name: 'Cancel every scheduled job']]
        command 'deleteAllStates',         [[name: 'Erase the State Variables']]

        // =============== firmware ===============
        command 'getFirmwareInfo',     [[name: 'Read the firmware version and the Matter update status']]
        command 'checkForUpdate',      [[name: 'Ask this device to check its update server for new firmware']]
        command 'watchFirmwareUpdate', [[name: 'minutes', type: 'STRING',
            description: 'Follow a firmware update in progress - works even when another app started it', defaultValue: '30']]

        // =============== safe actions ===============
        command 'identify', [[name: 'seconds', type: 'STRING',
            description: 'Make the device blink so you can find it', defaultValue: '10']]
        command 'discoverAll',       [[name: 'Find every endpoint, cluster and attribute this device has']]
        command 'reSubscribe',       [[name: 'Re-subscribe to the Matter reports of this device']]
        command 'unsubscribeMatter', [[name: 'Cancel the Matter subscription of this driver']]
        command 'ping'

        // =============== TIER 2 - the picker (uses the Preferences dropdowns) ===============
        command 'readSelected',      [[name: 'Read the endpoint / cluster / attribute selected in Preferences']]
        command 'subscribeSelected', [[name: 'Subscribe to the attribute selected in Preferences']]
        command 'writeSelected', [
            [name: 'value*',    type: 'STRING', description: 'Writes to the attribute selected in Preferences - advanced users only'],
            [name: 'dataType*', type: 'ENUM',   description: 'Matter data type of the value', constraints: WRITE_DATA_TYPE_NAMES]
        ]

        // =============== TIER 3 - expert free text ===============
        command 'readAttribute', [
            [name: 'endpoint',   type: 'STRING', description: 'Endpoint - a number; blank means 0, the device itself', defaultValue: '0'],
            [name: 'cluster*',   type: 'STRING', description: 'Cluster - a name or a number: OnOff, 0x0006, 0006 and 6 all work'],
            [name: 'attribute',  type: 'STRING', description: 'Attribute - a name or a number; leave blank to read them all']
        ]
        command 'writeAttribute', [
            [name: 'endpoint',   type: 'STRING', description: 'Endpoint', defaultValue: '0'],
            [name: 'cluster*',   type: 'STRING', description: 'Cluster - name or number'],
            [name: 'attribute*', type: 'STRING', description: 'Attribute - name or number'],
            [name: 'dataType*',  type: 'ENUM',   description: 'Matter data type of the value', constraints: WRITE_DATA_TYPE_NAMES],
            [name: 'value',      type: 'STRING', description: 'Value - a number, or text; leave blank for Boolean and Null']
        ]
        command 'invokeCommand', [
            [name: 'endpoint',   type: 'STRING', description: 'Endpoint', defaultValue: '0'],
            [name: 'cluster*',   type: 'STRING', description: 'Cluster - name or number'],
            [name: 'command*',   type: 'STRING', description: 'Command - name or number'],
            [name: 'fields',     type: 'STRING', description: 'Optional command fields, comma separated: dataType:tag:value  for example  UINT16:0:10']
        ]
        command 'subscribeAttribute', [
            [name: 'addOrRemove*', type: 'ENUM',   description: 'Add or remove this attribute from the subscription', constraints: ['add', 'remove', 'show']],
            [name: 'endpoint',     type: 'STRING', description: 'Endpoint', defaultValue: '0'],
            [name: 'cluster*',     type: 'STRING', description: 'Cluster - name or number'],
            [name: 'attribute*',   type: 'STRING', description: 'Attribute - name or number']
        ]
        command 'utilities', [[name: 'command', type: 'STRING',
            description: 'One line command box for advanced users - type a question mark for the list', constraints: ['STRING']]]

        if (_DEBUG) {
            command 'otaProbe', [[name: 'probe', type: 'ENUM',
                description: 'Matter OTA research probes - results land in State Variables, in state.otaProbe',
                constraints: OTA_PROBE_NAMES]]
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue: '']]
        }
    }

    preferences {
        input(name: 'helpInfo', type: 'hidden', title: fmtHelpInfo('Matter Advanced Device'))
        input(name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', description: 'Logs the results in plain text.', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', description: 'Turns on debug logging for 24 hours.', defaultValue: DEFAULT_DEBUG_LOGGING)
        input(name: 'advancedOptions', type: 'bool', title: '<b>Advanced options</b>', description: 'Shows the attribute picker and unlocks writing to the device.', defaultValue: false)
        if (settings?.advancedOptions == true) {
            input(name: 'pickEndpoint',  type: 'enum', title: '<b>Picker: endpoint</b>',  description: 'Run <i>Configure</i> or <i>Discover All</i> first to fill these in.', options: pickEndpointOptions(),  required: false)
            input(name: 'pickCluster',   type: 'enum', title: '<b>Picker: cluster</b>',   description: 'The clusters this endpoint supports.',   options: pickClusterOptions(),   required: false)
            input(name: 'pickAttribute', type: 'enum', title: '<b>Picker: attribute</b>', description: 'The attributes this cluster supports.',  options: pickAttributeOptions(), required: false)
            input(name: 'healthCheckMethod',   type: 'enum',   title: '<b>Health check method</b>',   description: 'How the driver decides that this device is offline.', defaultValue: 1,   options: HealthcheckMethodOpts.options)
            input(name: 'healthCheckInterval', type: 'enum',   title: '<b>Health check interval</b>', description: 'How often the health of the device is checked.',      defaultValue: 240, options: HealthcheckIntervalOpts.options)
            input(name: 'subscribeMinInterval', type: 'number', title: '<b>Subscription minimum interval, seconds</b>', description: 'Matter MinIntervalFloor - the fastest the device may report.', defaultValue: 1, range: '0..3600')
            input(name: 'subscribeMaxInterval', type: 'number', title: '<b>Subscription maximum interval, seconds</b>', description: 'Matter MaxIntervalCeiling - the slowest the device may report.', defaultValue: 600, range: '1..65535')
            input(name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', description: 'Very verbose - turns itself off after 30 minutes.', defaultValue: false)
        }
    }
}

// =============================================================================================
//  Reference data - Matter cluster and attribute names
//  Derived from the Matter Advanced Bridge matterLib 1.4.5. Used as a FALLBACK only: the live
//  Hubitat registry (matter.getClusterAttributeName) is queried first, because it covers about
//  1895 cluster/attribute pairs against roughly 1123 here. Cluster NAMES however must come from
//  this map - matter.getClusterName() is defective on 2.5.1.135 and returns the ID back as a
//  decimal String instead of a name.
// =============================================================================================

@Field static final Map<Integer, String> MatterClusters = [
    0x0003: 'Identify',                     0x0004: 'Groups',                       0x0005: 'Scenes',
    0x0006: 'OnOff',                        0x0008: 'LevelControl',                 0x001C: 'LevelControlDerived',
    0x001D: 'Descriptor',                   0x001E: 'Binding',                      0x001F: 'AccessControl',
    0x0025: 'Actions',                      0x0028: 'BasicInformation',             0x0029: 'OTASoftwareUpdateProvider',
    0x002A: 'OTASoftwareUpdateRequestor',   0x002B: 'LocalizationConfiguration',    0x002C: 'TimeFormatLocalization',
    0x002D: 'UnitLocalization',             0x002E: 'PowerSourceConfiguration',     0x002F: 'PowerSource',
    0x0030: 'GeneralCommissioning',         0x0031: 'NetworkCommissioning',         0x0032: 'DiagnosticLogs',
    0x0033: 'GeneralDiagnostics',           0x0034: 'SoftwareDiagnostics',          0x0035: 'ThreadNetworkDiagnostics',
    0x0036: 'WiFiNetworkDiagnostics',       0x0037: 'EthernetNetworkDiagnostics',   0x0038: 'TimeSync',
    0x0039: 'BridgedDeviceBasicInformation', 0x003B: 'Switch',                      0x003C: 'AdministratorCommissioning',
    0x003E: 'OperationalCredentials',       0x003F: 'GroupKeyManagement',           0x0040: 'FixedLabel',
    0x0041: 'UserLabel',                    0x0042: 'ProxyConfiguration',           0x0043: 'ProxyDiscovery',
    0x0044: 'ValidProxies',                 0x0045: 'BooleanState',                 0x0046: 'IcdManagement',
    0x0050: 'ModeSelect',                   0x0051: 'LaundryWasherMode',            0x0053: 'LaundryWasherControls',
    0x0054: 'RVCRunMode',                   0x0055: 'RVCCleanMode',                 0x0056: 'TemperatureControl',
    0x0057: 'RefrigeratorAlarm',            0x0059: 'DishwasherMode',               0x005B: 'AirQuality',
    0x005C: 'SmokeCOAlarm',                 0x005D: 'DishwasherAlarm',              0x0060: 'OperationalState',
    0x0061: 'RVCOperationalState',          0x0071: 'HEPAFilterMonitoring',         0x0072: 'ActivatedCarbonFilterMonitoring',
    0x0080: 'BooleanStateConfiguration',    0x0090: 'ElectricalPowerMeasurement',   0x0091: 'ElectricalEnergyMeasurement',
    0x0101: 'DoorLock',                     0x0102: 'WindowCovering',               0x0200: 'PumpConfigurationAndControl',
    0x0201: 'Thermostat',                   0x0202: 'FanControl',                   0x0204: 'ThermostatUserInterfaceConfiguration',
    0x0300: 'ColorControl',                 0x0301: 'BallastConfiguration',         0x0400: 'IlluminanceMeasurement',
    0x0402: 'TemperatureMeasurement',       0x0403: 'PressureMeasurement',          0x0404: 'FlowMeasurement',
    0x0405: 'RelativeHumidityMeasurement',  0x0406: 'OccupancySensing',             0x0407: 'LeafWetnessMeasurement',
    0x0408: 'SoilMoistureMeasurement',      0x040C: 'CarbonMonoxideConcentrationMeasurement',
    0x040D: 'CarbonDioxideConcentrationMeasurement',                                0x0413: 'NitrogenDioxideConcentrationMeasurement',
    0x0415: 'OzoneConcentrationMeasurement', 0x042A: 'PM25ConcentrationMeasurement', 0x042B: 'FormaldehydeConcentrationMeasurement',
    0x042C: 'PM1ConcentrationMeasurement',  0x042D: 'PM10ConcentrationMeasurement',
    0x042E: 'TotalVolatileOrganicCompoundsConcentrationMeasurement',                0x042F: 'RadonConcentrationMeasurement',
    0x0503: 'WakeOnLAN',                    0x0504: 'Channel',                      0x0505: 'TargetNavigator',
    0x0506: 'MediaPlayback',                0x0507: 'MediaInput',                   0x0508: 'LowPower',
    0x0509: 'KeypadInput',                  0x050A: 'ContentLauncher',              0x050B: 'AudioOutput',
    0x050C: 'ApplicationLauncher',          0x050D: 'ApplicationBasic',             0x050E: 'AccountLogin',
    // Matter 1.5 camera clusters. Seen on an Aqara Camera Hub G350 (Matter 1.5.0.0), endpoint 02,
    // ServerList [29, 3, 1361, 1363] - that is 0x0551 and 0x0553.
    0x0551: 'CameraAvStreamManagement',      0x0553: 'WebRTCTransportProvider'
]

@Field static final Map<Integer, String> GlobalElementsAttributes = [
    0x00FE: 'FabricIndex',      0xFFF8: 'GeneratedCommandList', 0xFFF9: 'AcceptedCommandList',
    0xFFFA: 'EventList',        0xFFFB: 'AttributeList',        0xFFFC: 'FeatureMap',
    0xFFFD: 'ClusterRevision'
]

@Field static final Map<Integer, String> DescriptorClusterAttributes = [
    0x0000: 'DeviceTypeList',   0x0001: 'ServerList',   0x0002: 'ClientList',
    0x0003: 'PartsList',        0x0004: 'TagList'
]

@Field static final Map<Integer, String> BasicInformationClusterAttributes = [
    0x0000: 'DataModelRevision',    0x0001: 'VendorName',           0x0002: 'VendorID',
    0x0003: 'ProductName',          0x0004: 'ProductID',            0x0005: 'NodeLabel',
    0x0006: 'Location',             0x0007: 'HardwareVersion',      0x0008: 'HardwareVersionString',
    0x0009: 'SoftwareVersion',      0x000A: 'SoftwareVersionString', 0x000B: 'ManufacturingDate',
    0x000C: 'PartNumber',           0x000D: 'ProductURL',           0x000E: 'ProductLabel',
    0x000F: 'SerialNumber',         0x0010: 'LocalConfigDisabled',  0x0011: 'Reachable',
    0x0012: 'UniqueID',             0x0013: 'CapabilityMinima',     0x0014: 'ProductAppearance',
    0x0015: 'SpecificationVersion', 0x0016: 'MaxPathsPerInvoke'
]

// 11.19.7.5 OTA Software Update Requestor Cluster 0x002A
@Field static final Map<Integer, String> OTASoftwareUpdateRequestorClusterAttributes = [
    0x0000: 'DefaultOTAProviders',  0x0001: 'UpdatePossible',
    0x0002: 'UpdateState',          0x0003: 'UpdateStateProgress'
]

// ---- the plain English menu: label -> what to read ----
// 'ep' fixed means the root node; when 'ep' is absent the first endpoint whose ServerList
// contains that cluster is used, so the user never has to know the endpoint number.
@Field static final Map<String, Map> READ_MENU = [
    'Firmware version'                : [ep: 0, cluster: 0x0028, attrs: [0x000A, 0x0009, 0x0008, 0x0007]],
    'Firmware update status'          : [ep: 0, cluster: 0x002A, attrs: [0x0000, 0x0001, 0x0002, 0x0003]],
    'Device name and maker'           : [ep: 0, cluster: 0x0028, attrs: [0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x000E]],
    'Serial number and unique ID'     : [ep: 0, cluster: 0x0028, attrs: [0x000F, 0x0012, 0x000C, 0x000B]],
    'Battery level'                   : [cluster: 0x002F, attrs: [0x0000, 0x000B, 0x000C, 0x000E, 0x000F]],
    'On/Off state'                    : [cluster: 0x0006, attrs: [0x0000, 0x4003]],
    'Brightness'                      : [cluster: 0x0008, attrs: [0x0000, 0x0002, 0x0003, 0x4000]],
    'Color'                           : [cluster: 0x0300, attrs: [0x0000, 0x0001, 0x0007, 0x0008]],
    'Temperature'                     : [cluster: 0x0402, attrs: [0x0000, 0x0001, 0x0002]],
    'Humidity'                        : [cluster: 0x0405, attrs: [0x0000, 0x0001, 0x0002]],
    'Illuminance'                     : [cluster: 0x0400, attrs: [0x0000, 0x0001, 0x0002]],
    'Motion or occupancy'             : [cluster: 0x0406, attrs: [0x0000, 0x0001, 0x0002]],
    'Contact, water or boolean state' : [cluster: 0x0045, attrs: [0x0000]],
    'Door lock state'                 : [cluster: 0x0101, attrs: [0x0000, 0x0001, 0x0002, 0x0003]],
    'Window covering position'        : [cluster: 0x0102, attrs: [0x0008, 0x000A, 0x000B, 0x000E]],
    'Thermostat setpoints'            : [cluster: 0x0201, attrs: [0x0000, 0x0011, 0x0012, 0x001C, 0x0029]],
    'Power and energy'                : [cluster: 0x0090, attrs: [0x0005, 0x0008, 0x0009, 0x000A]],
    'Air quality'                     : [cluster: 0x005B, attrs: [0x0000]],
    'Thread network'                  : [ep: 0, cluster: 0x0035, attrs: [0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0007, 0x0008]],
    'Wi-Fi network'                   : [ep: 0, cluster: 0x0036, attrs: [0x0000, 0x0001, 0x0002, 0x0003, 0x0004, 0x0005, 0x0006]],
    'Uptime and reboots'              : [ep: 0, cluster: 0x0033, attrs: [0x0001, 0x0002, 0x0003, 0x0000]],
    'Endpoints and features'          : [special: 'discoverAll'],
    'Everything about this device'    : [special: 'getInfoAll']
]

// ---- friendly write data type name -> Matter TLV element type ----
@Field static final Map<String, Integer> WRITE_DATA_TYPES = [
    'Whole number (UINT8)'            : 0x04,   'Whole number (UINT16)'  : 0x05,
    'Whole number (UINT32)'           : 0x06,   'Whole number (UINT64)'  : 0x07,
    'Signed number (INT8)'            : 0x00,   'Signed number (INT16)'  : 0x01,
    'Signed number (INT32)'           : 0x02,   'Signed number (INT64)'  : 0x03,
    'Text (UTF8)'                     : 0x0C,   'True (BOOLEAN)'         : 0x09,
    'False (BOOLEAN)'                 : 0x08,   'Nothing / previous state (NULL)' : 0x14
]

// the same table, keyed by the bare Matter type name, for the expert free text paths
@Field static final Map<String, Integer> TLV_TYPE_BY_NAME = [
    'INT8': 0x00, 'INT16': 0x01, 'INT32': 0x02, 'INT64': 0x03,
    'UINT8': 0x04, 'UINT16': 0x05, 'UINT32': 0x06, 'UINT64': 0x07,
    'BOOLEAN_FALSE': 0x08, 'BOOLEAN_TRUE': 0x09, 'FALSE': 0x08, 'TRUE': 0x09,
    'FLOAT4': 0x0A, 'FLOAT8': 0x0B,
    'UTF81': 0x0C, 'UTF8': 0x0C, 'STRING': 0x0C, 'UTF82': 0x0D, 'UTF84': 0x0E, 'UTF88': 0x0F,
    'STRING_OCTET1': 0x10, 'OCTET': 0x10, 'STRING_OCTET2': 0x11, 'STRING_OCTET4': 0x12, 'STRING_OCTET8': 0x13,
    'NULL': 0x14, 'STRUCTURE': 0x15, 'ARRAY': 0x16, 'LIST': 0x17
]

// how many hex characters each integer TLV type occupies, and whether it must be byte swapped
@Field static final Map<Integer, Integer> TLV_INT_WIDTH = [
    0x00: 2, 0x01: 4, 0x02: 8, 0x03: 16,
    0x04: 2, 0x05: 4, 0x06: 8, 0x07: 16
]

// ---- OTA Software Update Requestor cluster 0x002A: enumerations ----
@Field static final Integer OTA_REQUESTOR_CLUSTER = 0x002A
@Field static final Integer OTA_PROVIDER_CLUSTER  = 0x0029
@Field static final Integer BASIC_INFORMATION_CLUSTER = 0x0028
@Field static final Integer DESCRIPTOR_CLUSTER   = 0x001D
@Field static final Integer IDENTIFY_CLUSTER     = 0x0003

@Field static final Map<Integer, String> OTA_UPDATE_STATES = [
    0: 'unknown', 1: 'idle', 2: 'querying', 3: 'delayedOnQuery', 4: 'downloading',
    5: 'applying', 6: 'delayedOnApply', 7: 'rollingBack', 8: 'delayedOnUserConsent'
]

@Field static final Map<Integer, String> OTA_UPDATE_STATE_TEXT = [
    0: 'the device did not report a state',
    1: 'no update is running',
    2: 'checking the update server for new firmware',
    3: 'the update server asked to wait before checking again',
    4: 'downloading new firmware',
    5: 'installing the new firmware',
    6: 'the update server asked to wait before installing',
    7: 'rolling back to the previous firmware',
    8: 'waiting for someone to approve the update'
]

@Field static final Map<Integer, String> OTA_CHANGE_REASONS = [
    0: 'unknown', 1: 'success', 2: 'failure', 3: 'timeOut', 4: 'delayByProvider'
]

@Field static final Map<Integer, String> OTA_REQUESTOR_EVENTS = [
    0x00: 'StateTransition', 0x01: 'VersionApplied', 0x02: 'DownloadError'
]

// ---- health check ----
@Field static final Map HealthcheckMethodOpts = [defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']]
@Field static final Map HealthcheckIntervalOpts = [defaultValue: 240, options: [10: 'Every 10 minutes', 30: 'Every 30 minutes', 60: 'Every 1 hour', 240: 'Every 4 hours', 720: 'Every 12 hours']]

// ---- timing ----
@Field static final Integer COMMAND_TIMEOUT          = 10        // seconds - a Matter reply is expected within this
@Field static final Integer MAX_PING_MILISECONDS     = 10000
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3
@Field static final Integer INFO_AUTO_CLEAR_PERIOD   = 60        // seconds - the Status banner clears itself

// The hub's own Matter inventory, served on loopback. NEVER write a LAN address here - the driver
// runs on the hub, and a hub address in a repository file is a privacy leak (see AGENTS.md).
@Field static final String MATTER_DETAILS_URL = 'http://127.0.0.1:8080/hub/matterDetails/json'
@Field static final Integer READ_CHUNK_SIZE          = 20        // attribute paths per ReadRequest
@Field static final Integer SMALL_READ_CHUNK_SIZE    = 8         // fallback once a big read has gone unanswered
@Field static final Integer READ_CHUNK_DELAY_MS      = 500
@Field static final Integer INFO_COLLECT_PERIOD      = 300       // ms - the info collector tick
@Field static final Integer INFO_COLLECT_MAX_TICKS   = 34        // give up after this many quiet ticks
@Field static final Integer INFO_SETTLE_QUIET_TICKS  = 2
@Field static final Integer INFO_MAX_CONSECUTIVE_MISSES = 5   // that many silent clusters in a row means the device is gone
@Field static final Integer MAX_ATTRIBUTE_VALUE_LEN  = 900       // Hubitat truncates long attribute values
@Field static final Integer MAX_DUMP_VALUE_LEN       = 200       // one dump line's value; certificates run to 1000+ chars
@Field static final Integer INFO_LOG_BLOCK_LINES     = 40        // one huge log entry gets truncated by the platform
@Field static final String  UNKNOWN                  = 'UNKNOWN'

// ---- info collector state machine ----
// 1, 3 and 5 are unused: an earlier design had a separate 'sending' phase before each 'wait' one.
// The gaps are deliberate - the numbers are persisted in state.collect.phase, so renumbering would
// misread a collect that was in flight when the driver was updated.
@Field static final Integer INFO_STATE_NEXT           = 0
@Field static final Integer INFO_STATE_ATTR_LIST_WAIT = 2
@Field static final Integer INFO_STATE_VALUES_WAIT    = 4
@Field static final Integer INFO_STATE_END            = 99

// =============================================================================================
//  Lifecycle
// =============================================================================================

void installed() {
    log.info "${device.displayName} installed() driver ${version()}"
    device.updateDataValue('newParse', 'true')      // selects the decoded parse(Map) path
    initializeVars(fullInit = true)
    sendInfoEvent('installed', "driver ${version()} installed")
    runIn(3, 'configure')
}

void updated() {
    logInfo "updated() driver ${version()}"
    checkDriverVersion()
    device.updateDataValue('newParse', 'true')
    logInfo "debug logging is ${logEnable == true}, descriptionText logging is ${txtEnable != false}"
    if (logEnable == true) { runIn(86400, 'logsOff') } else { unschedule('logsOff') }
    if (traceEnable == true) { runIn(1800, 'traceOff') } else { unschedule('traceOff') }
    final int healthMethod = (settings?.healthCheckMethod ?: 1) as int
    if (healthMethod in [1, 2]) {
        scheduleDeviceHealthCheck((settings?.healthCheckInterval ?: 240) as int, healthMethod)
    } else {
        unScheduleDeviceHealthCheck()
        sendHealthStatusEvent('unknown')
        logWarn 'health check is disabled!'
    }
    sendInfoEvent('preferences updated')
}

void configure() {
    logInfo 'configure() - discovering the device and subscribing to its reports ...'
    sendInfoEvent('configure', 'discovery started')
    // installed() does not run when an existing device's Type is swapped to this driver, and the
    // documented flow is 'change Type -> Save Device -> press Configure'. Without this the decoded
    // map path is never armed and every message dies in the parse(String) dead end (B4)
    device.updateDataValue('newParse', 'true')
    initializeVars(fullInit = false)
    discoverAll()
}

void initialize() {
    logInfo 'initialize()'
    sendInfoEvent('initialize')
    configure()
}

void logsOff() {
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
    logWarn 'debug logging disabled'
}

void traceOff() {
    device.updateSetting('traceEnable', [value: 'false', type: 'bool'])
    logWarn 'trace logging disabled'
}

void refresh() {
    logInfo 'refresh() - re-reading the identity and firmware information'
    getFirmwareInfo()
}

// =============================================================================================
//  parse() - the single entry point for everything the Matter stack delivers
// =============================================================================================

// Safety net: only reached when the platform has not switched this device to the decoded map path.
// It deliberately does NOT forward to parse(Map) - being called at all is the symptom worth seeing,
// so it decodes the string for the log and stops there.
void parse(final String description) {
    Map descMap = null
    try {
        descMap = matter.parseDescriptionAsMap(description)
    } catch (e) {
        logWarn "parse(String) was called - NOT processed. description: '${description}' could not be decoded : ${e?.message}"
        return
    }
    logWarn "parse(String) was called - NOT processed. description: '${description}' decoded: ${descMap}"
}

/**
 * The platform's decoded map is used EXACTLY as it arrives - never rewritten. Read the *Int members
 * (endpointInt, clusterInt, attrInt, commandInt) and format hex only where something is displayed;
 * the hex String members (cluster, endpoint, attrId) are NOT always present, and 'cluster' can
 * arrive wider than four digits, so nothing here reads them. See HUBITAT_MATTER_API.md,
 * "parse(Map) callback shapes": "read only the *Int members".
 */
void parse(final Map descMap) {
    if (descMap == null) { logWarn 'parse(Map): null message' ; return }
    if (state.stats == null || state.states == null || state.lastRx == null || state.lastTx == null) { initializeVars(fullInit = false) }
    logTrace "parse(Map) ${descMap}"
    updateRxStats()
    setHealthStatusOnline()
    checkPendingCommandAnswered(descMap)

    String callbackType = descMap.callbackType
    if (callbackType == null) { callbackType = (descMap.evtId != null || descMap.evtInt != null) ? 'Event' : 'Report' }

    switch (callbackType) {
        case 'Report'            : processReport(descMap) ; break
        case 'Event'             : processMatterEvent(descMap) ; break
        case 'Invoke'            : processInvokeResponse(descMap) ; break
        case 'WriteAttributes'   : processWriteResponse(descMap) ; break
        case 'SubscriptionResult': processSubscriptionResult(descMap) ; break
        default                  : logWarn "parse(): unknown callbackType '${callbackType}' in ${descMap}"
    }
}

/**
 * The event id, as an Integer, without touching the message. Hubitat usually supplies a Number for
 * evtId but a hex String has been seen, and the older event shape carries evtInt instead. Returns
 * null when the message is not an event at all.
 */
Integer eventIdOf(final Map descMap) {
    if (descMap.evtId != null) {
        return (descMap.evtId instanceof Number) ? (descMap.evtId as Integer) : safeHexToInt(descMap.evtId as String)
    }
    if (descMap.evtInt != null) { return descMap.evtInt as Integer }
    return null
}

// =============================================================================================
//  Message handlers
// =============================================================================================

void processReport(final Map descMap) {
    Integer endpoint = descMap.endpointInt as Integer
    Integer cluster  = descMap.clusterInt as Integer
    Integer attrInt  = descMap.attrInt as Integer
    if (cluster == null || attrInt == null) { logDebug "processReport: incomplete report ${descMap}" ; return }

    String clusterName = getClusterName(cluster)
    String attrName    = getAttributeName(cluster, attrInt)
    logDebug "report ep:${hex2(endpoint)} ${clusterName}(${hex4(cluster)}) ${attrName}(${hex4(attrInt)}) = ${descMap.value}"

    storeDiscoveredData(endpoint, cluster, attrInt, attrName, descMap.value)
    collectInfoLine(endpoint, cluster, attrInt, attrName, descMap.value)

    switch (cluster) {
        case BASIC_INFORMATION_CLUSTER : processBasicInformation(descMap, attrName) ; break
        case OTA_REQUESTOR_CLUSTER     : processOtaRequestor(descMap, attrName) ; break
        default: break
    }
    announceManualReadResult(endpoint, cluster, attrInt, attrName, descMap.value)
}

void processMatterEvent(final Map descMap) {
    Integer cluster = descMap.clusterInt as Integer
    Integer evtId   = eventIdOf(descMap)
    String eventName = (cluster == OTA_REQUESTOR_CLUSTER) ? (OTA_REQUESTOR_EVENTS[evtId] ?: UNKNOWN) : UNKNOWN
    logInfo "Matter event: ep:${hex2(descMap.endpointInt as Integer)} ${getClusterName(cluster)}(${hex4(cluster)}) event ${eventName}(${hex2(evtId)}) data:${descMap.value ?: descMap.data}"
    if (cluster == OTA_REQUESTOR_CLUSTER) { processOtaEvent(evtId, descMap) }
}

void processInvokeResponse(final Map descMap) {
    Integer status  = safeToInt(descMap.status, -1)
    Integer cluster = descMap.clusterInt as Integer
    Integer command = descMap.commandInt as Integer
    String  text    = "invoke ${getClusterName(cluster)}(${hex4(cluster)}) command ${hex2(command)} on endpoint ${hex2(descMap.endpointInt as Integer)} -> status ${status}${status == 0 ? ' (success)' : ''}"
    if (status == 0) { logInfo text } else { logWarn text }
    sendLastResult(text)
    if (cluster == OTA_REQUESTOR_CLUSTER && command == 0x0000) {
        recordProbe('P4', "AnnounceOTAProvider ACCEPTED by the device, status=${status}")
        sendInfoEvent('update requested', 'the device accepted AnnounceOTAProvider')
    }
}

void processWriteResponse(final Map descMap) {
    // NOTE: Hubitat misspells the status key as 'sucess' - do not "fix" this to 'success'
    boolean ok = (descMap.sucess == true) || (descMap.success == true)
    Integer cluster = descMap.clusterInt as Integer
    Integer attrInt = descMap.attrInt as Integer
    String text = "write ${getClusterName(cluster)}(${hex4(cluster)}) ${getAttributeName(cluster, attrInt)}(${hex4(attrInt)}) on endpoint ${hex2(descMap.endpointInt as Integer)} -> ${ok ? 'accepted' : 'REJECTED'}"
    if (ok) { logInfo text } else { logWarn text }
    sendLastResult(text)
}

void processSubscriptionResult(final Map descMap) {
    logInfo "subscription established, id ${descMap.subscriptionId}"
    state.subscriptionId = descMap.subscriptionId?.toString()
    sendInfoEvent('subscribed', "Matter subscription id ${descMap.subscriptionId}")
    if (state.states['pendingOtaSubscribe'] == true) {
        state.states['pendingOtaSubscribe'] = false
        recordProbe('P3', 'SubscriptionResult received - OTA subscription established')
    }
}

// =============================================================================================
//  Name resolution - IDs to names and names to IDs
// =============================================================================================

String getClusterName(final Integer cluster) {
    if (cluster == null) { return UNKNOWN }
    return MatterClusters[cluster] ?: "Cluster_0x${hex4(cluster)}"
}

/** The curated per cluster attribute maps. Only a fallback - see getAttributeName(). */
Map getCuratedAttributesMap(final Integer cluster) {
    switch (cluster) {
        case DESCRIPTOR_CLUSTER        : return DescriptorClusterAttributes
        case BASIC_INFORMATION_CLUSTER : return BasicInformationClusterAttributes
        case 0x0039                    : return BasicInformationClusterAttributes   // BridgedDeviceBasicInformation mirrors 0x0028
        case OTA_REQUESTOR_CLUSTER     : return OTASoftwareUpdateRequestorClusterAttributes
        default                        : return null
    }
}

/**
 * Live Hubitat registry first (about 1895 cluster/attribute pairs), curated map second.
 * catch (Throwable) is mandatory: an unknown numeric ID throws NoSuchFieldError, which is an
 * Error and NOT caught by catch (Exception).
 */
String getAttributeName(final Integer cluster, final Integer attrInt) {
    if (cluster == null || attrInt == null) { return UNKNOWN }
    String live = null
    try {
        live = matter.getClusterAttributeName(cluster as Long, attrInt as Long)
    } catch (Throwable ignored) {
        live = null
    }
    if (live != null && live != '' && !(live ==~ /^-?\d+$/)) { return live }
    String curated = getCuratedAttributesMap(cluster)?.get(attrInt)
    if (curated != null) { return curated }
    return GlobalElementsAttributes[attrInt] ?: UNKNOWN
}

/** Accepts 'OnOff', '0x0006', '6' or '0006'. Returns null when nothing matches. */
Integer resolveCluster(final String par) {
    String s = par?.trim()
    if (s == null || s == '') { return null }
    Integer byNumber = parseNumber(s)
    if (byNumber != null) { return byNumber }
    Integer curated = MatterClusters.find { k, v -> v.equalsIgnoreCase(s) }?.key as Integer
    if (curated != null) { return curated }
    try {
        Long live = matter.getClusterIdByName(s)
        if (live != null) { return live as Integer }
    } catch (Throwable ignored) { }
    return null
}

/** Accepts 'OnOff', 'AttributeList', '0x0000', '0'. Returns null when nothing matches. */
Integer resolveAttribute(final Integer cluster, final String par) {
    String s = par?.trim()
    if (s == null || s == '') { return null }
    Integer byNumber = parseNumber(s)
    if (byNumber != null) { return byNumber }
    Integer curated = getCuratedAttributesMap(cluster)?.find { k, v -> v.equalsIgnoreCase(s) }?.key as Integer
    if (curated != null) { return curated }
    Integer global = GlobalElementsAttributes.find { k, v -> v.equalsIgnoreCase(s) }?.key as Integer
    if (global != null) { return global }
    try {
        Long live = matter.getClusterAttributeByName(cluster as Long, s)
        if (live != null) { return live as Integer }
    } catch (Throwable ignored) { }
    // last resort: walk the discovered attribute list of that cluster and match the resolved names
    List<Integer> known = discoveredAttributeList(currentPickEndpointInt(), cluster)
    Integer walked = known?.find { getAttributeName(cluster, it).equalsIgnoreCase(s) }
    return walked
}

Integer resolveCommand(final Integer cluster, final String par) {
    String s = par?.trim()
    if (s == null || s == '') { return null }
    Integer byNumber = parseNumber(s)
    if (byNumber != null) { return byNumber }
    try {
        Long live = matter.getClusterCommandByName(cluster as Long, s)
        if (live != null) { return live as Integer }
    } catch (Throwable ignored) { }
    return null
}

/**
 * Number parsing rules, in order:
 *   '0x1A' or '0X1A'  -> hexadecimal, always
 *   '001D' '0006'     -> hexadecimal: a leading zero means the fixed width form used all over Matter
 *   'FFFB' '1A'       -> hexadecimal: it contains a-f, so it cannot be decimal
 *   '26' '6'          -> decimal
 * Returns null for anything that is not a number at all, so the caller can try a name lookup.
 */
Integer parseNumber(final String par) {
    String s = par?.trim()
    if (s == null || s == '') { return null }
    try {
        if (s.toLowerCase().startsWith('0x')) { return Integer.parseInt(s.substring(2), 16) }
        if (s ==~ /^0[0-9a-fA-F]{1,3}$/) { return Integer.parseInt(s, 16) }  // 0006, 001D, 002A
        if (s ==~ /^\d+$/) { return Integer.parseInt(s, 10) }               // 6, 26, 300
        if (s ==~ /^[0-9a-fA-F]{2,4}$/) { return Integer.parseInt(s, 16) }   // FFFB, 1A
    } catch (NumberFormatException ignored) {
        return null
    }
    return null
}

// =============================================================================================
//  Discovery - what endpoints, clusters and attributes does this device have
// =============================================================================================

/** A Matter list attribute arrives either as a real List or as a String such as '[0006, 0008]'. */
List<Integer> toIntList(final Object value) {
    if (value == null) { return [] }
    if (value instanceof List) {
        return value.collect { safeHexToInt(it instanceof Number ? hex4(it as Integer) : it.toString()) }.findAll { it != null }
    }
    String s = value.toString().trim()
    if (s == '' || s == '[]') { return [] }
    if (s.startsWith('[') && s.endsWith(']')) { s = s.substring(1, s.length() - 1) }
    return s.split(',').collect { safeHexToInt(it.trim()) }.findAll { it != null }
}

Map endpointRecord(final Integer endpoint) {
    if (state.endpoints == null) { state.endpoints = [:] }
    String key = hex2(endpoint)
    if (state.endpoints[key] == null) { state.endpoints[key] = [serverList: [], attrs: [:]] }
    return state.endpoints[key]
}

void storeDiscoveredData(final Integer endpoint, final Integer cluster, final Integer attrInt, final String attrName, final Object value) {
    // AttributeList of any cluster - must come before the Descriptor branch, which returns early
    if (attrInt == 0xFFFB) {
        Map rec = endpointRecord(endpoint)
        if (rec.attrs == null) { rec.attrs = [:] }
        rec.attrs[hex4(cluster)] = toIntList(value)
        if (state.collect != null) { state.collect['gotAttrListFor'] = hex4(cluster) }
    }
    if (cluster == DESCRIPTOR_CLUSTER) {
        Map rec = endpointRecord(endpoint)
        switch (attrInt) {
            case 0x0000:    // DeviceTypeList
                rec.deviceTypes = describeDeviceTypes(value)
                break
            case 0x0001:    // ServerList
                rec.serverList = toIntList(value).collect { hex4(it) }
                break
            case 0x0002:    // ClientList
                rec.clientList = toIntList(value).collect { hex4(it) }
                break
            case 0x0003:    // PartsList
                List<Integer> parts = toIntList(value)
                rec.partsList = parts
                if (endpoint == 0) {
                    parts.each { endpointRecord(it) }
                    sendEvent(name: 'endpointsCount', value: parts.size(), descriptionText: "this device has ${parts.size()} endpoint(s) besides the root")
                }
                break
            case 0x0004:    // TagList
                rec.tagList = value?.toString()
                break
            default: break
        }
        return
    }
    if (cluster == OTA_REQUESTOR_CLUSTER && device.currentValue('otaSupported') != 'yes') {
        sendEvent(name: 'otaSupported', value: 'yes', descriptionText: 'this device has the Matter OTA Software Update Requestor cluster')
    }
}

String describeDeviceTypes(final Object value) {
    // real devices return three different shapes, e.g. [[0:22, 1:1]] or [[[tag:0, value:22]]] or plain ids
    List<Integer> ids = []
    try {
        if (value instanceof List) {
            value.each { entry ->
                if (entry instanceof Map) {
                    entry.each { k, v -> if (k?.toString() == '0' || k?.toString() == 'DeviceType') { ids << safeToInt(v) } }
                } else if (entry instanceof List) {
                    entry.each { sub -> if (sub instanceof Map && sub.tag?.toString() == '0') { ids << safeToInt(sub.value) } }
                } else {
                    ids << safeToInt(entry)
                }
            }
        }
    } catch (Exception e) {
        logDebug "describeDeviceTypes: ${e?.message}"
    }
    if (ids.isEmpty()) { return value?.toString() }
    return ids.collect { "${MATTER_DEVICE_TYPES[it] ?: 'DeviceType'} (0x${hex4(it)})" }.join(', ')
}

List<Integer> discoveredServerList(final Integer endpoint) {
    return (endpointRecord(endpoint).serverList ?: []).collect { safeHexToInt(it.toString()) }.findAll { it != null }
}

List<Integer> discoveredAttributeList(final Integer endpoint, final Integer cluster) {
    if (endpoint == null || cluster == null) { return [] }
    return (endpointRecord(endpoint).attrs?.get(hex4(cluster)) ?: []).collect { safeToInt(it) }
}

/** The first endpoint whose ServerList holds this cluster - so the user never has to know it. */
Integer findEndpointWithCluster(final Integer cluster) {
    if (state.endpoints == null) { return null }
    String hit = state.endpoints.find { epKey, rec -> (rec?.serverList ?: []).any { safeHexToInt(it.toString()) == cluster } }?.key
    return hit == null ? null : safeHexToInt(hit)
}

/**
 * Matter Device Library ids. The sensor block is NOT the Zigbee sequence, which is what an earlier
 * version of this map assumed: Pressure is 0x0305, Flow 0x0306 and Humidity 0x0307, and On/Off
 * Sensor sits far away at 0x0850. Hub verified on the DIRIGERA, 2026-08-16: endpoint 14 is 0x0302
 * with TemperatureMeasurement 0x0402 in its ServerList, endpoint 13 is 0x0307 with
 * RelativeHumidityMeasurement 0x0405 - so 0x0307, not 0x0305, is Humidity.
 */
@Field static final Map<Integer, String> MATTER_DEVICE_TYPES = [
    0x0016: 'Root Node',            0x0011: 'Power Source',        0x0012: 'OTA Requestor',
    0x0014: 'OTA Provider',         0x000E: 'Aggregator',          0x0013: 'Bridged Node',
    0x000F: 'Generic Switch',       0x0100: 'On/Off Light',        0x0101: 'Dimmable Light',
    0x0103: 'On/Off Light Switch',  0x0104: 'Dimmer Switch',       0x0105: 'Color Dimmer Switch',
    0x0106: 'Light Sensor',         0x0107: 'Occupancy Sensor',    0x010A: 'On/Off Plug-in Unit',
    0x010B: 'Dimmable Plug-in Unit', 0x010C: 'Color Temperature Light', 0x010D: 'Extended Color Light',
    0x0202: 'Window Covering',      0x0203: 'Window Covering Controller',
    0x000A: 'Door Lock',            0x000B: 'Door Lock Controller', 0x0301: 'Thermostat',
    0x002B: 'Fan',                  0x0302: 'Temperature Sensor',   0x0305: 'Pressure Sensor',
    0x0306: 'Flow Sensor',          0x0307: 'Humidity Sensor',      0x0850: 'On/Off Sensor',
    0x0015: 'Contact Sensor',       0x0043: 'Water Leak Detector',  0x0041: 'Smoke CO Alarm',
    0x0044: 'Rain Sensor',          0x002C: 'Air Quality Sensor',   0x002D: 'Air Purifier',
    0x0076: 'Robotic Vacuum Cleaner', 0x0090: 'Electrical Sensor',  0x0510: 'Electrical Sensor',
    0x0142: 'Camera'                // Matter 1.5; Aqara G350 endpoint 02, with 0x0551 + 0x0553
]

/**
 * The collector: a reply driven walk over a queue of (endpoint, cluster) pairs. Each entry either
 * reads a fixed attribute list, or first reads AttributeList (0xFFFB) and then every attribute it
 * names. It advances as soon as the replies go quiet, and gives up on an entry after a timeout -
 * a Matter read of an unsupported path is simply never answered, so there is nothing to wait for.
 */
void startCollect(final List<Map> queue, final String label, final String finishedText) {
    ensureStateMaps()
    if (queue == null || queue.isEmpty()) { logWarn "startCollect(${label}): nothing to collect" ; return }
    unschedule('collectTick')
    state.collect = [
        queue: queue, idx: 0, phase: INFO_STATE_NEXT, ticks: 0, quiet: 0, misses: 0,
        // .toString() - callers pass GStrings; a hub reboot mid collect would serialize them into state
        label: label?.toString(), finishedText: finishedText?.toString(),
        gotAttrListFor: null, lines: 0, replies: 0, repliesAtEntry: 0, notBefore: 0L
    ]
    state.infoBuffer = []
    sendInfoEvent(label, finishedText)
    logInfo "${label}: reading ${queue.size()} cluster(s) ..."
    runInMillis(INFO_COLLECT_PERIOD, 'collectTick', [overwrite: true])
}

boolean isCollecting() { return state.collect != null && state.collect.phase != INFO_STATE_END }

void collectTick() {
    Map c = state.collect
    if (c == null) { return }
    ensureStateMaps()                   // a stale schedule can outlive a Delete All States
    c.ticks = (c.ticks ?: 0) + 1

    switch (c.phase as Integer) {
        case INFO_STATE_NEXT:
            if ((c.idx as Integer) >= (c.queue as List).size()) { c.phase = INFO_STATE_END ; break }
            Map entry = (c.queue as List)[c.idx as Integer] as Map
            sendInfoEvent("${c.label} (${(c.idx as Integer) + 1}/${(c.queue as List).size()}) - please wait")
            c.gotAttrListFor = null
            c.quiet = 0
            c.notBefore = 0L                        // this entry's chunk pacing starts here (C2)
            if (entry.attrs != null) {
                sendAttributeReads(entry.ep as Integer, entry.cluster as Integer, entry.attrs as List)
                c.phase = INFO_STATE_VALUES_WAIT
            } else {
                readAttributePath(entry.ep as Integer, entry.cluster as Integer, 0xFFFB)
                c.phase = INFO_STATE_ATTR_LIST_WAIT
                c.ticks = 0
            }
            break

        case INFO_STATE_ATTR_LIST_WAIT:
            Map waitingFor = (c.queue as List)[c.idx as Integer] as Map
            if (c.gotAttrListFor == hex4(waitingFor.cluster as Integer)) {
                Map entry = waitingFor
                List<Integer> attrs = discoveredAttributeList(entry.ep as Integer, entry.cluster as Integer)
                attrs = attrs.findAll { it != 0xFFFB }      // do not re-read the list itself
                if (attrs.isEmpty()) {
                    flushCollectedEntry(c)
                    c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT
                } else {
                    sendAttributeReads(entry.ep as Integer, entry.cluster as Integer, attrs)
                    c.phase = INFO_STATE_VALUES_WAIT ; c.quiet = 0 ; c.ticks = 0
                }
            } else if ((c.ticks as Integer) > INFO_COLLECT_MAX_TICKS) {
                logDebug "collect: no AttributeList from endpoint ${hex2(waitingFor.ep as Integer)} cluster ${hex4(waitingFor.cluster as Integer)} - skipping"
                flushCollectedEntry(c)
                c.misses = (c.misses ?: 0) + 1
                c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT
            }
            break

        case INFO_STATE_VALUES_WAIT:
            c.quiet = (c.quiet ?: 0) + 1
            boolean allChunksSent = new Date().getTime() >= safeToLong(c.notBefore, 0L)
            boolean settled = allChunksSent && (c.quiet as Integer) >= INFO_SETTLE_QUIET_TICKS
            if (settled || (c.ticks as Integer) > INFO_COLLECT_MAX_TICKS) {
                // replies, NOT lines: 'did this entry answer' is not 'did the dump grow'. An
                // AttributeList reply adds no line, and when the dump was capped the line count
                // froze, which made healthy clusters look silent and aborted the walk (B7)
                c.misses = (c.replies as Integer) > (c.repliesAtEntry as Integer ?: 0) ? 0 : (c.misses ?: 0) + 1
                c.repliesAtEntry = c.replies
                flushCollectedEntry(c)                  // print this cluster before moving on
                c.idx = (c.idx as Integer) + 1 ; c.phase = INFO_STATE_NEXT ; c.ticks = 0 ; c.notBefore = 0L
            }
            break

        default:
            c.phase = INFO_STATE_END
            break
    }

    if ((c.misses ?: 0) >= INFO_MAX_CONSECUTIVE_MISSES) {
        logWarn "${c.label}: ${c.misses} clusters in a row gave no answer - the device looks unreachable, giving up"
        c.phase = INFO_STATE_END
        state.states['collectAborted'] = true
    }
    if ((c.phase as Integer) == INFO_STATE_END) {
        finishCollect()
        return
    }
    state.collect = c
    runInMillis(INFO_COLLECT_PERIOD, 'collectTick', [overwrite: true])
}

/**
 * Adds one answered attribute to the dump. Only paths that this collect actually asked for are
 * taken: a subscription report from an unrelated endpoint (a bridge keeps reporting throughout a
 * discovery) must not appear in the listing, and must not reset the quiet counter that decides when
 * the current entry has settled. Stragglers from an already advanced entry are still recorded -
 * they were asked for - but only the entry being waited on keeps the collector waiting.
 */
void collectInfoLine(final Integer endpoint, final Integer cluster, final Integer attrInt, final String attrName, final Object value) {
    Map c = state.collect
    if (c == null || (c.phase as Integer) == INFO_STATE_END) { return }
    List queue = (c.queue ?: []) as List
    if (!queue.any { Map e -> (e.ep as Integer) == endpoint && (e.cluster as Integer) == cluster }) { return }
    Map current = (c.idx as Integer) < queue.size() ? queue[c.idx as Integer] as Map : null
    if (current != null && (current.ep as Integer) == endpoint && (current.cluster as Integer) == cluster) {
        c.quiet = 0                                 // a reply to what we are waiting for - keep waiting for more
    }
    c.replies = (c.replies ?: 0) + 1                // this entry answered - independent of what gets printed
    if (attrInt == 0xFFFB) { state.collect = c ; return }   // the list itself is not an interesting line
    if (state.infoBuffer == null) { state.infoBuffer = [] }
    String prefix = "[${hex2(endpoint)}/${hex4(cluster)}_${hex4(attrInt)}] "
    String line = "${prefix}${attrName} = ${truncateForDump(formatValue(cluster, attrInt, value))}"
    // one line per path, latest value wins - a subscribed attribute that changes mid collect used to
    // add a new line per report, because the de-duplication compared the value too
    Integer at = (state.infoBuffer as List).findIndexOf { it.toString().startsWith(prefix) }
    if (at >= 0) {
        state.infoBuffer[at] = line
    } else {
        state.infoBuffer << line
        c.lines = (c.lines ?: 0) + 1                // running total for the closing summary only
    }
    state.collect = c
}

/**
 * Prints the cluster that just finished and empties the buffer. The dump used to accumulate every
 * value in state and print once at the end, which meant a hard line cap to keep state small - and a
 * 19-endpoint bridge lost half its output to it. Flushing per cluster means state never holds more
 * than one cluster's worth, there is no cap, and the output appears as the walk progresses.
 */
void flushCollectedEntry(final Map c) {
    List lines = ((state.infoBuffer ?: []) as List).sort()
    state.infoBuffer = []
    if (lines.isEmpty()) { return }
    List queue = (c?.queue ?: []) as List
    Map entry = (c?.idx as Integer) < queue.size() ? queue[c.idx as Integer] as Map : null
    String where = entry == null ? (c?.label ?: 'Collect')
                 : "endpoint ${hex2(entry.ep as Integer)} ${getClusterName(entry.cluster as Integer)} (${hex4(entry.cluster as Integer)})"
    List blocks = lines.collate(INFO_LOG_BLOCK_LINES)
    blocks.eachWithIndex { List block, Integer i ->
        String heading = blocks.size() == 1 ? "<b>${where}</b> - ${lines.size()} value(s)"
                                            : "<b>${where}</b> - part ${i + 1} of ${blocks.size()} (${lines.size()} value(s))"
        logInfo "${heading}:<br>${block.join('<br>')}"
    }
}

void finishCollect() {
    Map c = state.collect
    String label = c?.label ?: 'Collect'
    flushCollectedEntry(c)                  // the last cluster, and anything a miss path left behind
    Integer total = safeToInt(c?.lines)
    Integer clusters = ((c?.queue ?: []) as List).size()
    unschedule('collectTick')
    state.collect = null
    if (total == 0) {
        logWarn "${label}: the device did not answer - nothing collected"
        sendInfoEvent(label, 'no answer from the device')
    } else {
        logInfo "<b>${label}</b>: ${total} value(s) from ${clusters} cluster(s) - listed above"
        sendInfoEvent(label, "${total} value(s) collected")
        sendLastResult("${label}: ${total} values - see the live logs")
    }
    state.infoBuffer = null
    if (state.states['collectAborted'] == true) {
        state.states['collectAborted'] = false
        state.states['discoveryPending'] = false
        sendInfoEvent('gave up', 'the device stopped answering')
        return
    }
    if (state.states['discoveryPending'] == true) {
        state.states['discoveryPending'] = false
        afterDiscovery()
    }
}

void discoverAll() {
    ensureStateMaps()
    logInfo 'discoverAll() - step 1 of 2: reading the root endpoint descriptor ...'
    state.endpoints = [:]
    state.states['discoveryPending'] = true
    state.states['discoveryStage'] = 1
    startCollect([[ep: 0, cluster: DESCRIPTOR_CLUSTER, attrs: [0x0000, 0x0001, 0x0002, 0x0003, 0x0004]]],
                 'Discovery step 1 of 2', 'reading the root endpoint descriptor')
}

void afterDiscovery() {
    Integer stage = safeToInt(state.states['discoveryStage'], 1)
    if (stage == 1) {
        List<Integer> parts = (endpointRecord(0).partsList ?: []) as List<Integer>
        List<Map> queue = []
        parts.each { ep -> queue << [ep: ep, cluster: DESCRIPTOR_CLUSTER, attrs: [0x0000, 0x0001, 0x0003, 0x0004]] }
        queue << [ep: 0, cluster: BASIC_INFORMATION_CLUSTER, attrs: [0x0001, 0x0003, 0x0005, 0x0007, 0x0008, 0x0009, 0x000A, 0x000F, 0x0012, 0x0015]]
        // only ask for OTA when stage 1's ServerList says the cluster is there. A Matter read of an
        // unsupported path is never answered, so on a device without OTA (the DIRIGERA bridge, for
        // one) this entry used to burn its full miss timeout and then trip the command watchdog.
        // Fail open on an empty ServerList - a descriptor read that failed must not be read as 'no OTA'.
        List<Integer> rootClusters = discoveredServerList(0)
        if (rootClusters.isEmpty() || rootClusters.contains(OTA_REQUESTOR_CLUSTER)) {
            queue << [ep: 0, cluster: OTA_REQUESTOR_CLUSTER, attrs: [0x0000, 0x0001, 0x0002, 0x0003]]
        } else {
            logDebug "discoverAll(): endpoint 0 has no OTA cluster (0x${hex4(OTA_REQUESTOR_CLUSTER)}) - skipping the OTA read"
        }
        logInfo "discoverAll() - step 2 of 2: ${parts.size()} endpoint(s) found, reading their descriptors ..."
        state.states['discoveryPending'] = true
        state.states['discoveryStage'] = 2
        startCollect(queue, 'Discovery step 2 of 2', "reading ${parts.size()} endpoint descriptor(s)")
        return
    }
    // stage 2 finished
    state.states['discoveryStage'] = 0
    Integer epCount = (state.endpoints ?: [:]).size()
    logInfo "discovery finished - ${epCount} endpoint(s), ${summarizeEndpoints()}"
    if (device.currentValue('otaSupported') == null) {
        sendEvent(name: 'otaSupported', value: 'no', descriptionText: 'the OTA Software Update Requestor cluster was not found on this device')
    }
    sendInfoEvent('discovery finished', "${epCount} endpoint(s)")
    runIn(2, 'subscribeToDefaults')
}

String summarizeEndpoints() {
    return (state.endpoints ?: [:]).collect { epKey, rec ->
        "ep ${epKey}: ${rec?.deviceTypes ?: 'unknown type'} (${(rec?.serverList ?: []).size()} clusters)"
    }.join(' | ')
}

/**
 * The built-in Device driver's Get Info: one fingerprint line per endpoint, straight from the
 * platform. It generates no Matter traffic - the hub has held this since commissioning.
 */
void getInfo() {
    List fingerprints = null
    try {
        fingerprints = matter.getMatterFingerprints()
    } catch (Throwable t) {
        logWarn "getInfo(): matter.getMatterFingerprints() failed : ${t?.message}"
        return
    }
    if (fingerprints == null || fingerprints.isEmpty()) {
        logWarn 'getInfo(): the platform reported no fingerprints for this device'
        sendLastResult('getInfo: the platform reported no fingerprints')
        return
    }
    fingerprints.each { logInfo formatFingerprint(it) }
    sendLastResult("getInfo: ${fingerprints.size()} endpoint fingerprint(s) - see the live logs")
}

/**
 * Renders one com.hubitat.hub.domain.Fingerprint the way the built-in Device driver prints it -
 * its own toString() dumps every field positionally, most of them null on a Matter node.
 */
String formatFingerprint(final Object fp) {
    if (fp == null) { return 'fingerprint (null)' }
    try {
        return "fingerprint endpointId:\"${fp.endpointId}\", inClusters:\"${fp.inClusters}\", " +
               "outClusters:\"${fp.outClusters}\", model:\"${fp.model}\", " +
               "manufacturer:\"${fp.manufacturer}\", controllerType:\"${fp.controllerType}\""
    } catch (Throwable t) {          // a field the platform renamed must not abort the whole dump
        logDebug "formatFingerprint: ${t?.message}"
        return fp.toString()
    }
}

/** Dump every cluster and attribute of one endpoint, or of all of them when the parameter is blank. */
void getInfoAdvanced(String endpointPar = '') {
    if (state.endpoints == null || state.endpoints.isEmpty()) {
        logWarn 'getInfoAdvanced(): the device has not been discovered yet - running Discover All first, please repeat Get Info Advanced when it finishes'
        discoverAll()
        return
    }
    List<Map> queue = []
    String trimmed = endpointPar?.trim()
    if (trimmed == null || trimmed == '') {
        state.endpoints.each { epKey, rec ->
            Integer ep = safeHexToInt(epKey)
            discoveredServerList(ep).each { cl -> queue << [ep: ep, cluster: cl] }
        }
    } else {
        Integer ep = parseNumber(trimmed)
        if (ep == null) { logWarn "getInfoAdvanced(): '${endpointPar}' is not a valid endpoint number" ; return }
        List<Integer> clusters = discoveredServerList(ep)
        if (clusters.isEmpty()) { logWarn "getInfoAdvanced(): endpoint ${trimmed} has no known clusters - run Discover All first" ; return }
        clusters.each { cl -> queue << [ep: ep, cluster: cl] }
    }
    // reading FixedLabel and UserLabel on a non zero endpoint crashes some devices - skip them there
    queue = queue.findAll { !((it.ep as Integer) != 0 && ((it.cluster as Integer) in [0x0040, 0x0041])) }
    startCollect(queue, 'Get Info Advanced', "dumping ${queue.size()} cluster(s)")
}

void getInfoAll() { getInfoAdvanced('') }

// =============================================================================================
//  Matter I/O
// =============================================================================================

void sendToDevice(final String cmd) {
    if (cmd == null || cmd == '') { logWarn 'sendToDevice: empty command' ; return }
    updateTxStats()
    logTrace "sendToDevice: ${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void sendDeferredCommand(final Map data) { sendToDevice(data?.cmd as String) }

void sendToDevice(final List<String> cmds, final Integer delayMs = READ_CHUNK_DELAY_MS) {
    if (cmds == null || cmds.isEmpty()) { return }
    cmds.eachWithIndex { String c, Integer i ->
        if (i == 0) { sendToDevice(c) }
        else { runInMillis(delayMs * i, 'sendDeferredCommand', [overwrite: false, data: [cmd: c]]) }
    }
}

/** One attribute read. attrInt == -1 is the Matter wildcard: read every attribute of the cluster. */
void readAttributePath(final Integer endpoint, final Integer cluster, final Integer attrInt) {
    List<Map<String, String>> paths = [matter.attributePath(endpoint as Integer, cluster as Integer, attrInt as Integer)]
    scheduleCommandTimeoutCheck(cluster)
    sendToDevice(matter.readAttributes(paths))
}

/**
 * Chunked reads. A ReadRequest whose RESPONSE would be too large is never answered at all, so the
 * chunk size is capped and latched down once a big read has gone unanswered.
 */
void sendAttributeReads(final Integer endpoint, final Integer cluster, final List attrs) {
    if (attrs == null || attrs.isEmpty()) { return }
    Integer chunkSize = effectiveReadChunkSize()
    List<String> cmds = []
    attrs.collect { safeToInt(it) }.findAll { it != null }.collate(chunkSize).each { List chunk ->
        List<Map<String, String>> paths = chunk.collect { matter.attributePath(endpoint as Integer, cluster as Integer, it as Integer) }
        cmds << matter.readAttributes(paths)
    }
    if (state.collect != null) {
        // the last chunk goes out at (n-1) * delay; do not let the collector settle before then.
        // Math.max, never a plain assignment: a ping or a manual read fired while a collect is
        // running must not shorten the quiet window the collector is already waiting out (C2)
        Long computed = new Date().getTime() + ((cmds.size() - 1) * READ_CHUNK_DELAY_MS) + READ_CHUNK_DELAY_MS
        state.collect['notBefore'] = Math.max(safeToLong(state.collect['notBefore'], 0L), computed)
    }
    scheduleCommandTimeoutCheck(cluster)
    sendToDevice(cmds, READ_CHUNK_DELAY_MS)
}

Integer effectiveReadChunkSize() {
    return (state.states['smallReadChunks'] == true) ? SMALL_READ_CHUNK_SIZE : READ_CHUNK_SIZE
}

// =============================================================================================
//  TIER 3 - expert free text commands
// =============================================================================================

void readAttribute(String endpointPar = '0', String clusterPar = null, String attributePar = null) {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    Integer endpoint = parseNumber(endpointPar?.trim() ?: '0') ?: 0
    Integer cluster  = resolveCluster(clusterPar)
    if (cluster == null) { logWarn "readAttribute: '${clusterPar}' is not a known cluster name or number" ; return }
    String attrTrim = attributePar?.trim()
    if (attrTrim == null || attrTrim == '') {
        logInfo "reading ALL attributes of ${getClusterName(cluster)} (0x${hex4(cluster)}) on endpoint ${hex2(endpoint)} ..."
        startCollect([[ep: endpoint, cluster: cluster]], "Read ${getClusterName(cluster)}", "endpoint ${hex2(endpoint)}")
        return
    }
    Integer attrInt = resolveAttribute(cluster, attrTrim)
    if (attrInt == null) { logWarn "readAttribute: '${attributePar}' is not a known attribute of ${getClusterName(cluster)}" ; return }
    state.states['manualRead'] = [endpoint, cluster, attrInt]
    logInfo "reading endpoint ${hex2(endpoint)} ${getClusterName(cluster)}(0x${hex4(cluster)}) ${getAttributeName(cluster, attrInt)}(0x${hex4(attrInt)}) ..."
    readAttributePath(endpoint, cluster, attrInt)
}

void writeAttribute(String endpointPar = '0', String clusterPar = null, String attributePar = null, String dataTypePar = null, String valuePar = null) {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    if (settings?.advancedOptions != true) {
        logWarn 'writeAttribute: writing is locked - switch on "Advanced options" in the Preferences first'
        return
    }
    Integer endpoint = parseNumber(endpointPar?.trim() ?: '0') ?: 0
    Integer cluster  = resolveCluster(clusterPar)
    if (cluster == null) { logWarn "writeAttribute: '${clusterPar}' is not a known cluster name or number" ; return }
    Integer attrInt = resolveAttribute(cluster, attributePar)
    if (attrInt == null) { logWarn "writeAttribute: '${attributePar}' is not a known attribute of ${getClusterName(cluster)}" ; return }
    Integer tlvType = resolveDataType(dataTypePar)
    if (tlvType == null) { logWarn "writeAttribute: '${dataTypePar}' is not a known Matter data type" ; return }
    String dataHex = encodeWriteValue(tlvType, valuePar)
    if (dataHex == null) { logWarn "writeAttribute: cannot encode '${valuePar}' as ${dataTypePar}" ; return }

    logWarn "WRITING endpoint ${hex2(endpoint)} ${getClusterName(cluster)}(0x${hex4(cluster)}) ${getAttributeName(cluster, attrInt)}(0x${hex4(attrInt)}) = '${valuePar}' as type 0x${hex2(tlvType)}, data '${dataHex}' (as written - attributeWriteRequest does the byte swapping itself)"
    List<Map<String, String>> requests = [matter.attributeWriteRequest(endpoint as Integer, cluster as Integer, attrInt as Integer, tlvType as Integer, dataHex)]
    scheduleCommandTimeoutCheck(cluster)
    sendToDevice(matter.writeAttributes(requests))
    // read back, but DELAYED - a read sent immediately can be answered out of order with a stale value
    state.states['pendingReadBack'] = [endpoint, cluster, attrInt]
    runIn(4, 'readBackAfterWrite', [overwrite: true])
}

void readBackAfterWrite() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    List pending = state.states['pendingReadBack'] as List
    if (pending == null || pending.size() < 3) { return }
    state.states['pendingReadBack'] = null
    state.states['manualRead'] = pending
    logInfo 'reading the value back to confirm the write ...'
    readAttributePath(pending[0] as Integer, pending[1] as Integer, pending[2] as Integer)
}

void invokeCommand(String endpointPar = '0', String clusterPar = null, String commandPar = null, String fieldsPar = null) {
    if (settings?.advancedOptions != true) {
        logWarn 'invokeCommand: invoking is locked - switch on "Advanced options" in the Preferences first'
        return
    }
    Integer endpoint = parseNumber(endpointPar?.trim() ?: '0') ?: 0
    Integer cluster  = resolveCluster(clusterPar)
    if (cluster == null) { logWarn "invokeCommand: '${clusterPar}' is not a known cluster name or number" ; return }
    Integer command = resolveCommand(cluster, commandPar)
    if (command == null) { logWarn "invokeCommand: '${commandPar}' is not a known command of ${getClusterName(cluster)}" ; return }
    List<Map<String, String>> fields = parseCommandFields(fieldsPar)
    if (fields == null) { logWarn "invokeCommand: cannot parse the fields '${fieldsPar}' - expected  dataType:tag:value , comma separated" ; return }

    logWarn "INVOKING endpoint ${hex2(endpoint)} ${getClusterName(cluster)}(0x${hex4(cluster)}) command 0x${hex2(command)}${fields.isEmpty() ? ' (no fields)' : " fields ${fields}"}"
    scheduleCommandTimeoutCheck(cluster)
    String cmd = fields.isEmpty() ? matter.invoke(endpoint as Integer, cluster as Integer, command as Integer)
                                  : matter.invoke(endpoint as Integer, cluster as Integer, command as Integer, fields)
    sendToDevice(cmd)
}

// =============================================================================================
//  Value encoding
// =============================================================================================

Integer resolveDataType(final String par) {
    String s = par?.trim()
    if (s == null || s == '') { return null }
    if (WRITE_DATA_TYPES.containsKey(s)) { return WRITE_DATA_TYPES[s] }
    Integer byName = TLV_TYPE_BY_NAME[s.toUpperCase()]
    if (byName != null) { return byName }
    return parseNumber(s)
}

/** Reverse the octets of an even length hex String. zigbee.swapOctets() only handles 4 characters. */
String swapOctetsAny(final String hexPar) {
    String h = hexPar
    if (h == null || h.length() < 4 || h.length() % 2 != 0) { return h }
    StringBuilder sb = new StringBuilder()
    for (int i = h.length() - 2; i >= 0; i -= 2) { sb.append(h.substring(i, i + 2)) }
    return sb.toString()
}

String stringToHex(final String s) {
    if (s == null) { return '' }
    StringBuilder sb = new StringBuilder()
    s.getBytes('UTF-8').each { byte b -> sb.append(String.format('%02X', b & 0xFF)) }
    return sb.toString()
}

/**
 * Builds the bare value for attributeWriteRequest. The helper adds the TLV framing itself, so the
 * value must NOT be pre-framed, and it byte swaps the value itself - probe P5 confirmed this on
 * platform 2.5.1.156: attributeWriteRequest(0, 0x002A, 0x0000, UINT16, '1234') goes out as
 * data '053412', type byte 05 plus payload 3412. So the caller must NOT pre-swap here.
 * (cmdField is the opposite - it passes bytes through verbatim and the caller pre-swaps. See
 * HUBITAT_MATTER_API.md section 22, and OTA_PROBE_PLAN.md for both P5 captures.)
 * A 'swapWriteBytes' preference used to sit here to flip the convention while it was still an open
 * question. P5 settled it, and flipping it now would only double swap, so it was removed. The value
 * is always read back, so a future platform change of convention would still be visible.
 */
String encodeWriteValue(final Integer tlvType, final String valuePar) {
    String v = valuePar?.trim() ?: ''
    switch (tlvType) {
        case 0x08:      // BOOLEAN_FALSE
        case 0x09:      // BOOLEAN_TRUE
        case 0x14:      // NULL - the TLV element type itself carries the value
            return ''
        case 0x0C: case 0x0D: case 0x0E: case 0x0F:     // UTF8 strings
        case 0x10: case 0x11: case 0x12: case 0x13:     // octet strings
            return v.toLowerCase().startsWith('0x') ? v.substring(2).toUpperCase() : stringToHex(v)
        default:
            break
    }
    Integer width = TLV_INT_WIDTH[tlvType]
    if (width == null) { return v.toUpperCase() }       // an exotic type - pass the hex straight through
    Long number
    try {
        number = v.toLowerCase().startsWith('0x') ? Long.parseLong(v.substring(2), 16) : Long.parseLong(v)
    } catch (NumberFormatException ignored) {
        return null
    }
    String hex = String.format("%0${width}X", number)
    if (hex.length() > width) { hex = hex.substring(hex.length() - width) }
    return hex                                          // NOT pre-swapped - attributeWriteRequest swaps (P5)
}

/**
 * 'UINT16:0:10, UINT8:1:0x02' -> a List of cmdField maps, in field order.
 * Hubitat staff confirmed that raw multi-byte command field data must be byte swapped by the
 * caller, so cmdField values ARE swapped here (unlike attributeWriteRequest above).
 */
List<Map<String, String>> parseCommandFields(final String fieldsPar) {
    String s = fieldsPar?.trim()
    if (s == null || s == '') { return [] }
    List<Map<String, String>> out = []
    for (String part : s.split(',')) {
        String[] bits = part.trim().split(':')
        if (bits.length != 3) { return null }
        Integer tlvType = resolveDataType(bits[0])
        Integer tag     = parseNumber(bits[1])
        if (tlvType == null || tag == null) { return null }
        String hex = encodeCmdFieldValue(tlvType, bits[2])
        if (hex == null) { return null }
        out << matter.cmdField(tlvType as Integer, tag as Integer, hex)
    }
    return out
}

String encodeCmdFieldValue(final Integer tlvType, final String valuePar) {
    String v = valuePar?.trim() ?: ''
    if (tlvType in [0x08, 0x09, 0x14]) { return '' }
    if (tlvType in [0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13]) {
        return v.toLowerCase().startsWith('0x') ? v.substring(2).toUpperCase() : stringToHex(v)
    }
    Integer width = TLV_INT_WIDTH[tlvType]
    if (width == null) { return v.toUpperCase() }
    Long number
    try {
        number = v.toLowerCase().startsWith('0x') ? Long.parseUnsignedLong(v.substring(2), 16) : Long.parseLong(v)
    } catch (NumberFormatException ignored) {
        return null
    }
    String hex = String.format("%0${width}X", number)
    if (hex.length() > width) { hex = hex.substring(hex.length() - width) }
    return swapOctetsAny(hex)
}

// =============================================================================================
//  Subscriptions
// =============================================================================================

void subscribeAttribute(String addOrRemove = 'add', String endpointPar = '0', String clusterPar = null, String attributePar = null) {
    if (addOrRemove == 'show') { showSubscriptions() ; return }
    Integer endpoint = parseNumber(endpointPar?.trim() ?: '0') ?: 0
    Integer cluster  = resolveCluster(clusterPar)
    if (cluster == null) { logWarn "subscribeAttribute: '${clusterPar}' is not a known cluster" ; return }
    Integer attrInt = resolveAttribute(cluster, attributePar)
    if (attrInt == null) { logWarn "subscribeAttribute: '${attributePar}' is not a known attribute of ${getClusterName(cluster)}" ; return }
    if (cluster == DESCRIPTOR_CLUSTER) {
        logWarn 'subscribeAttribute: subscribing to the Descriptor cluster (0x001D) is deliberately not allowed - it destabilises the Matter stack'
        return
    }
    if (state.subscriptions == null) { state.subscriptions = [] }
    List entry = [endpoint, cluster, attrInt]
    if (addOrRemove == 'remove') {
        state.subscriptions.removeAll { it == entry }
        logInfo "removed endpoint ${hex2(endpoint)} ${getClusterName(cluster)} ${getAttributeName(cluster, attrInt)} from the subscription"
    } else {
        if (!(entry in state.subscriptions)) { state.subscriptions << entry }
        logInfo "added endpoint ${hex2(endpoint)} ${getClusterName(cluster)} ${getAttributeName(cluster, attrInt)} to the subscription"
    }
    sendSubscription()
}

void showSubscriptions() {
    List subs = (state.subscriptions ?: []) as List
    if (subs.isEmpty()) { logInfo 'there are no subscriptions' ; return }
    String text = subs.collect { List s ->
        "endpoint ${hex2(s[0] as Integer)} ${getClusterName(s[1] as Integer)}(0x${hex4(s[1] as Integer)}) ${getAttributeName(s[1] as Integer, s[2] as Integer)}(0x${hex4(s[2] as Integer)})"
    }.join('<br>')
    logInfo "<b>${subs.size()} subscription(s):</b><br>${text}"
    sendLastResult("${subs.size()} subscriptions - see the live logs")
}

/** Subscribe to the handful of things a diagnostic driver always wants to know about. */
void subscribeToDefaults() {
    List subs = []
    if (discoveredServerList(0).contains(BASIC_INFORMATION_CLUSTER)) {
        subs << [0, BASIC_INFORMATION_CLUSTER, 0x000A]      // SoftwareVersionString
        // NOT Reachable 0x0011 - it is a bridged-device attribute, absent on a directly paired node
    }
    if (discoveredServerList(0).contains(OTA_REQUESTOR_CLUSTER)) {
        subs << [0, OTA_REQUESTOR_CLUSTER, 0x0002]          // UpdateState
        subs << [0, OTA_REQUESTOR_CLUSTER, 0x0003]          // UpdateStateProgress
    }
    if (subs.isEmpty()) { logInfo 'subscribeToDefaults: nothing worth subscribing to on this device' ; return }
    state.subscriptions = subs
    sendSubscription()
}

void sendSubscription() {
    List subs = (state.subscriptions ?: []) as List
    if (subs.isEmpty()) { logWarn 'sendSubscription: the subscription list is empty' ; return }
    List<Map<String, String>> paths = subs.collect { List s -> matter.attributePath(s[0] as Integer, s[1] as Integer, s[2] as Integer) }
    // add the OTA Software Update Requestor events - StateTransition, VersionApplied, DownloadError
    if (subs.any { (it[1] as Integer) == OTA_REQUESTOR_CLUSTER }) {
        OTA_REQUESTOR_EVENTS.each { Integer evt, String name -> paths << matter.eventPath(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, evt as Integer) }
    }
    Integer minInterval = safeToInt(settings?.subscribeMinInterval, 1)
    Integer maxInterval = safeToInt(settings?.subscribeMaxInterval, 600)
    logInfo "subscribing to ${paths.size()} path(s), reporting between ${minInterval} and ${maxInterval} seconds ..."
    sendInfoEvent('subscribing', "${paths.size()} paths")
    sendToDevice(matter.cleanSubscribe(minInterval as Integer, maxInterval as Integer, paths))
}

void reSubscribe() {
    logInfo 'reSubscribe()'
    if (state.subscriptions == null || (state.subscriptions as List).isEmpty()) { subscribeToDefaults() ; return }
    sendSubscription()
}

void unsubscribeMatter() {
    logInfo 'unsubscribeMatter() - cancelling the Matter subscription of this driver'
    sendInfoEvent('unsubscribing')
    sendToDevice(matter.unsubscribe())
}

// =============================================================================================
//  TIER 1 - the plain English menu
// =============================================================================================

void readSomething(String what = null) {
    Map entry = READ_MENU[what]
    if (entry == null) { logWarn "readSomething: '${what}' is not on the menu" ; return }
    if (entry.special == 'discoverAll') { discoverAll() ; return }
    if (entry.special == 'getInfoAll')  { getInfoAdvanced('') ; return }

    Integer cluster = entry.cluster as Integer
    Integer endpoint = (entry.ep != null) ? (entry.ep as Integer) : findEndpointWithCluster(cluster)
    if (endpoint == null) {
        if (state.endpoints == null || state.endpoints.isEmpty()) {
            logWarn "'${what}': this device has not been discovered yet - press Configure (or Discover All) once, then try again"
            sendLastResult('press Configure first')
        } else {
            logWarn "'${what}': this device does not have the ${getClusterName(cluster)} (0x${hex4(cluster)}) function"
            sendLastResult("this device has no ${getClusterName(cluster)}")
        }
        return
    }
    logInfo "reading '${what}' from endpoint ${hex2(endpoint)} ${getClusterName(cluster)} (0x${hex4(cluster)}) ..."
    startCollect([[ep: endpoint, cluster: cluster, attrs: entry.attrs as List]], what, "endpoint ${hex2(endpoint)}")
}

// =============================================================================================
//  TIER 2 - the picker. Command ENUM constraints are frozen when the driver is saved, so the
//  discovery driven dropdowns have to live in the Preferences, which are rebuilt on every page load.
// =============================================================================================

Map pickEndpointOptions() {
    Map opts = [:]
    (state.endpoints ?: [:]).sort().each { String epKey, Map rec ->
        Integer ep = safeHexToInt(epKey)
        opts["0x${epKey}"] = "${ep} - ${rec?.deviceTypes ?: 'endpoint'}"
    }
    if (opts.isEmpty()) { opts['0x00'] = '0 - run Configure first' }
    return opts
}

Integer currentPickEndpointInt() { return parseNumber(settings?.pickEndpoint?.toString() ?: '0') ?: 0 }

Map pickClusterOptions() {
    Map opts = [:]
    discoveredServerList(currentPickEndpointInt()).sort().each { Integer cl ->
        opts["0x${hex4(cl)}"] = "${getClusterName(cl)} (0x${hex4(cl)})"
    }
    if (opts.isEmpty()) { opts['0x0028'] = 'BasicInformation (0x0028) - run Configure first' }
    return opts
}

Map pickAttributeOptions() {
    Map opts = [:]
    Integer cluster = parseNumber(settings?.pickCluster?.toString() ?: '')
    if (cluster != null) {
        discoveredAttributeList(currentPickEndpointInt(), cluster).sort().each { Integer at ->
            opts["0x${hex4(at)}"] = "${getAttributeName(cluster, at)} (0x${hex4(at)})"
        }
    }
    // keyed 'none', not '0x0000' - 0 is a real attribute id, so the placeholder would read
    // attribute 0 and present it as the user's choice. parseNumber('none') is null -> pickedTriple() warns.
    if (opts.isEmpty()) { opts['none'] = 'run Get Info Advanced on this cluster first' }
    return opts
}

private Map pickedTriple() {
    Integer endpoint = currentPickEndpointInt()
    Integer cluster  = parseNumber(settings?.pickCluster?.toString() ?: '')
    Integer attrInt  = parseNumber(settings?.pickAttribute?.toString() ?: '')
    if (cluster == null || attrInt == null) {
        logWarn 'the picker is not set - choose an endpoint, a cluster and an attribute in the Preferences, then press Save Preferences'
        return null
    }
    return [ep: endpoint, cluster: cluster, attr: attrInt]
}

void readSelected() {
    Map p = pickedTriple()
    if (p == null) { return }
    readAttribute(p.ep.toString(), "0x${hex4(p.cluster as Integer)}", "0x${hex4(p.attr as Integer)}")
}

void subscribeSelected() {
    Map p = pickedTriple()
    if (p == null) { return }
    subscribeAttribute('add', p.ep.toString(), "0x${hex4(p.cluster as Integer)}", "0x${hex4(p.attr as Integer)}")
}

void writeSelected(String value = null, String dataType = null) {
    Map p = pickedTriple()
    if (p == null) { return }
    writeAttribute(p.ep.toString(), "0x${hex4(p.cluster as Integer)}", "0x${hex4(p.attr as Integer)}", dataType, value)
}

// =============================================================================================
//  Basic Information (0x0028) - identity and firmware version
// =============================================================================================

void processBasicInformation(final Map descMap, final String attrName) {
    Object value = descMap.value
    switch (descMap.attrInt as Integer) {
        case 0x0001 : sendIdentityEvent('vendorName', value?.toString()) ; break
        case 0x0003 : sendIdentityEvent('productName', value?.toString()) ; break
        case 0x0005 : sendIdentityEvent('nodeLabel', value?.toString()) ; break
        case 0x000F : sendIdentityEvent('serialNumber', value?.toString()) ; break
        case 0x0008 : sendIdentityEvent('hardwareVersionString', value?.toString()) ; break
        case 0x0015 : sendIdentityEvent('specificationVersion', decodeSpecificationVersion(value)) ; break
        case 0x000A :
            sendEvent(name: 'firmwareVersion', value: value?.toString(), descriptionText: "firmware version is ${value}")
            logInfo "firmware version is <b>${value}</b>"
            device.updateDataValue('softwareVersionString', value?.toString())
            break
        case 0x0009 :
            // a vendor defined uint32 with no standard packing - keep it raw, it is what OTA compares
            // integer attribute values arrive as decimal strings - parse decimal, never hex
            Integer code = safeToInt(value, null)
            if (code != null) { sendEvent(name: 'firmwareVersionCode', value: code, descriptionText: "firmware version code is ${code}") }
            break
        default: break
    }
}

void sendIdentityEvent(final String name, final String value) {
    if (value == null || value == '') { return }
    if (device.currentValue(name)?.toString() == value) { return }
    sendEvent(name: name, value: value, descriptionText: "${name} is ${value}")
    logInfo "${name} is <b>${value}</b>"
}

String decodeSpecificationVersion(final Object value) {
    Integer raw = safeToInt(value, null)    // decimal string from the platform, not hex
    if (raw == null) { return value?.toString() }
    return "${(raw >> 24) & 0xFF}.${(raw >> 16) & 0xFF}.${(raw >> 8) & 0xFF}.${raw & 0xFF}"
}

// =============================================================================================
//  Matter OTA - OTA Software Update Requestor cluster 0x002A
//
//  A Hubitat driver can never SERVE firmware: Matter delivers the image over BDX from an OTA
//  Provider node commissioned on the same fabric, and the matter.* helper surface has no BDX
//  support at all. What a driver CAN do is read and watch the Requestor side, and ask the device
//  to go and talk to a Provider that already exists. See OTA_PROBE_PLAN.md in this folder.
// =============================================================================================

void processOtaRequestor(final Map descMap, final String attrName) {
    Object value = descMap.value
    if (state.ota == null) { state.ota = [:] }
    switch (descMap.attrInt as Integer) {
        case 0x0000 :       // DefaultOTAProviders
            state.ota['defaultProvidersRaw'] = value?.toString()
            // decode the node id here, while the structure is still decoded - a later regex over the
            // printed form cannot tell a node id from a tag number (B3)
            Long providerNode = firstProviderNodeId(value)
            if (providerNode != null) { state.ota['providerNodeId'] = providerNode } else { state.ota.remove('providerNodeId') }
            String summary = describeOtaProviders(value)
            state.ota['provider'] = summary
            sendEvent(name: 'otaProvider', value: summary, descriptionText: "OTA update server: ${summary}")
            logInfo "OTA update server (DefaultOTAProviders): <b>${summary}</b>   raw: ${value}"
            recordProbe('P2', "DefaultOTAProviders = ${value} -> ${summary}")
            break
        case 0x0001 :       // UpdatePossible
            Boolean possible = isTrueish(value)
            state.ota['updatePossible'] = possible
            logInfo "this device reports that a firmware update is ${possible ? '' : 'NOT '}possible (UpdatePossible=${value})"
            recordProbe('P2', "UpdatePossible = ${value}")
            break
        case 0x0002 :       // UpdateState
            Integer st = safeToInt(value, null)     // decimal string from the platform, not hex
            String stName = OTA_UPDATE_STATES[st] ?: 'unknown'
            sendEvent(name: 'otaState', value: stName, descriptionText: "firmware update state: ${OTA_UPDATE_STATE_TEXT[st] ?: stName}")
            logInfo "firmware update state: <b>${stName}</b> - ${OTA_UPDATE_STATE_TEXT[st] ?: ''}"
            state.ota['state'] = stName
            recordProbe('P2', "UpdateState = ${value} (${stName})")
            break
        case 0x0003 :       // UpdateStateProgress
            Integer pct = safeToInt(value, null)    // decimal string from the platform, not hex
            if (pct == null) {
                logInfo 'firmware update progress: not reported (null)'
            } else {
                sendEvent(name: 'otaProgress', value: pct, unit: '%', descriptionText: "firmware update progress ${pct}%")
                logInfo "firmware update progress: <b>${pct}%</b>"
            }
            state.ota['progress'] = pct
            recordProbe('P2', "UpdateStateProgress = ${value}")
            break
        default: break
    }
}

/**
 * One field of a decoded TLV struct, by its Matter tag number. Real devices return three shapes for
 * a list of structs - [1: 42], ['1': 42] and [[tag:1, value:42]] - all three were seen in
 * DeviceTypeList on this platform. NOT the same as fieldOf(), which indexes a List positionally:
 * that is right for event payloads and wrong for a struct that arrives as a list of tag/value pairs.
 */
Object tagValue(final Object item, final Integer tag) {
    if (item == null || tag == null) { return null }
    try {
        if (item instanceof Map) {
            Map m = (Map) item
            if (m.containsKey(tag))            { return m[tag] }
            if (m.containsKey(tag.toString())) { return m[tag.toString()] }
            if (m['tag']?.toString() == tag.toString()) { return m['value'] }
            return null
        }
        if (item instanceof List) {
            Map hit = (Map) ((List) item).find { it instanceof Map && ((Map) it)['tag']?.toString() == tag.toString() }
            if (hit != null) { return hit['value'] }
        }
    } catch (Exception e) {
        logDebug "tagValue: ${e?.message}"
    }
    return null
}

/** The first ProviderNodeID (tag 1) in a DefaultOTAProviders list, as a number. Node 1 is legal. */
Long firstProviderNodeId(final Object value) {
    if (!(value instanceof List)) { return null }
    for (Object item : (List) value) {
        Object id = tagValue(item, 1)
        if (id == null) { continue }
        Long n = safeToLong(id, -1L)
        if (n >= 0) { return n }
    }
    return null
}

/** DefaultOTAProviders is a fabric scoped list of ProviderLocation structs - report what we can see. */
String describeOtaProviders(final Object value) {
    String raw = value?.toString()
    if (raw == null || raw == '' || raw == '[]' || raw == 'null') { return 'none configured' }
    // ProviderLocation = { 1: ProviderNodeID (uint64), 2: Endpoint (uint16), 254: FabricIndex }
    try {
        if (value instanceof List && !((List)value).isEmpty()) {
            return ((List)value).collect { Object item ->
                Object nodeId = tagValue(item, 1)
                Object ep      = tagValue(item, 2)
                if (nodeId == null && item instanceof Map) { nodeId = ((Map) item)['ProviderNodeID'] }
                if (ep == null && item instanceof Map)     { ep     = ((Map) item)['Endpoint'] }
                if (nodeId != null) { return "node ${nodeId} endpoint ${ep}" }
                return item?.toString()
            }.join(', ')
        }
    } catch (Exception e) {
        logDebug "describeOtaProviders: ${e?.message}"
    }
    return raw
}

void processOtaEvent(final Integer evtId, final Map descMap) {
    Object data = descMap.value ?: descMap.data
    String text
    switch (evtId) {
        case 0x00:      // StateTransition { 0: PreviousState, 1: NewState, 2: Reason, 3: TargetSoftwareVersion }
            Integer newState = safeToInt(fieldOf(data, 1), -1)
            Integer reason   = safeToInt(fieldOf(data, 2), -1)
            String stateName = OTA_UPDATE_STATES[newState] ?: 'unknown'
            text = "update state changed to ${stateName}${reason >= 0 ? " (${OTA_CHANGE_REASONS[reason] ?: reason})" : ''}"
            if (newState >= 0) { sendEvent(name: 'otaState', value: stateName, descriptionText: text) }
            logInfo text
            break
        case 0x01:      // VersionApplied { 0: SoftwareVersion, 1: ProductID }
            text = "NEW FIRMWARE APPLIED - software version ${fieldOf(data, 0)}"
            logInfo text
            runIn(15, 'getFirmwareInfo')        // re-read the version strings once the device is back
            break
        case 0x02:      // DownloadError { 0: SoftwareVersion, 1: BytesDownloaded, 2: ProgressPercent, 3: PlatformCode }
            text = "download FAILED for software version ${fieldOf(data, 0)} after ${fieldOf(data, 1)} bytes"
            logWarn text
            break
        default:
            text = "OTA event 0x${hex2(evtId)} : ${data}"
            logInfo text
            break
    }
    sendEvent(name: 'otaLastEvent', value: text.take(MAX_ATTRIBUTE_VALUE_LEN), descriptionText: text)
    recordProbe('P3', "event ${OTA_REQUESTOR_EVENTS[evtId] ?: evtId}: ${data}")
}

/** Pull one context tagged field out of a decoded Matter event payload, whatever shape it arrived in. */
Object fieldOf(final Object data, final Integer tag) {
    if (data == null) { return null }
    try {
        if (data instanceof Map) {
            // containsKey, never Elvis: a field whose value is 0 is present, and 0 is falsy in Groovy
            Map m = (Map) data
            if (m.containsKey(tag))            { return m[tag] }
            if (m.containsKey(tag.toString())) { return m[tag.toString()] }
            Map nested = (Map) m.values().find { it instanceof Map }
            if (nested != null) {
                if (nested.containsKey(tag))            { return nested[tag] }
                if (nested.containsKey(tag.toString())) { return nested[tag.toString()] }
            }
            return null
        }
        if (data instanceof List && ((List)data).size() > tag) { return ((List)data)[tag] }
    } catch (Exception e) {
        logDebug "fieldOf: ${e?.message}"
    }
    return null
}

// =============================================================================================
//  Firmware commands
// =============================================================================================

void getFirmwareInfo() {
    logInfo 'reading the firmware version and the Matter update status ...'
    List<Map> queue = [[ep: 0, cluster: BASIC_INFORMATION_CLUSTER, attrs: [0x0007, 0x0008, 0x0009, 0x000A, 0x0015]]]
    boolean notDiscoveredYet = (state.endpoints == null || state.endpoints.isEmpty())
    boolean hasOta = notDiscoveredYet || discoveredServerList(0).contains(OTA_REQUESTOR_CLUSTER)
    if (hasOta) {
        queue << [ep: 0, cluster: OTA_REQUESTOR_CLUSTER, attrs: [0x0000, 0x0001, 0x0002, 0x0003]]
    } else {
        sendEvent(name: 'otaSupported', value: 'no', descriptionText: 'this device has no Matter OTA Software Update Requestor cluster')
        logInfo 'this device does not have the Matter OTA cluster - it cannot be updated over Matter by any controller'
    }
    startCollect(queue, 'Firmware information', 'reading the version and the update status')
}

/**
 * Ask the device to go and check its update server. This is the ONLY trigger a Hubitat driver has:
 * AnnounceOTAProvider tells the Requestor to query a Provider that is already commissioned on the
 * fabric. It cannot conjure a Provider into existence, and this driver cannot be one - Matter
 * delivers the image over BDX and the matter.* helper surface has no BDX support.
 */
void checkForUpdate() {
    if (device.currentValue('otaSupported') == 'no') {
        String msg = 'This device has no Matter OTA Requestor cluster (0x002A), so it cannot be updated over Matter ' +
                     'by any controller. Update it from the manufacturer app.'
        logWarn msg ; sendLastResult(msg) ; sendInfoEvent('no Matter OTA', msg)
        return
    }
    Long providerNodeId = configuredProviderNodeId()
    if (providerNodeId == null) {
        String summary = 'Hubitat provides no Matter update server - update via another controller or the vendor app'
        String msg = 'Hubitat does not provide Matter firmware updates: it implements no OTA Provider, and a Groovy ' +
                     'driver cannot be one - Matter sends the image over BDX, which the matter.* API does not expose.' +
                     '<br>Controllers that DO provide it - Apple Home, Google Home, SmartThings, Home Assistant - look ' +
                     'the firmware up in the Matter Distributed Compliance Ledger by vendor and product id, download it ' +
                     'from the manufacturer, and serve it to the device over the local fabric.' +
                     '<br>A Matter device accepts up to five fabrics at once, so you can add this one to such a ' +
                     'controller as an extra administrator without removing it from Hubitat - or simply use the ' +
                     'manufacturer app.' +
                     '<br><b>Either way this driver will follow the update</b>: run <b>Watch Firmware Update</b> and ' +
                     'the state and progress appear here as it happens.'
        logWarn msg
        sendLastResult(summary)
        sendInfoEvent('no update server', summary)
        getFirmwareInfo()
        return
    }
    logInfo "asking the device to check update server node ${providerNodeId} for new firmware ..."
    sendInfoEvent('checking for an update', "AnnounceOTAProvider to node ${providerNodeId}")
    announceOtaProvider(providerNodeId, 0L, 1, 0)       // reason 1 = UpdateAvailable
    watchFirmwareUpdate('15')
}

/** The provider node id read from DefaultOTAProviders, or the one supplied through the probe suite. */
Long configuredProviderNodeId() {
    Object override = state.ota?.get('providerNodeIdOverride')
    if (override != null) { return safeToLong(override) }
    Object stored = state.ota?.get('providerNodeId')     // decoded at report time - the reliable one
    if (stored != null) { return safeToLong(stored) }
    String raw = state.ota?.get('defaultProvidersRaw')?.toString()
    if (raw == null || raw == '' || raw == '[]' || raw == 'none configured' || raw == 'null') { return null }
    // last resort, for a shape the decoder did not recognize. No size floor - chip-tool's own OTA
    // example commissions the provider as node 1 - so prefer an explicit 'value:' over a bare number,
    // otherwise 'tag:1' would be read as node id 1.
    java.util.regex.Matcher m = (raw =~ /value:\s*(\d+)/)
    if (m.find()) { return safeToLong(m.group(1)) }
    m = (raw =~ /(\d+)/)
    if (m.find()) { return safeToLong(m.group(1)) }
    return null
}

/**
 * AnnounceOTAProvider, cluster 0x002A command 0x00. Fields, in order:
 *   0 ProviderNodeID (node-id / uint64)   1 VendorID (uint16)   2 AnnouncementReason (enum8)
 *   3 MetadataForNode (octstr, optional - omitted here)          4 Endpoint (endpoint-no / uint16)
 * Hubitat staff confirmed that multi byte cmdField data must be byte swapped by the caller.
 */
void announceOtaProvider(final Long providerNodeId, final Long vendorId, final Integer reason, final Integer providerEndpoint) {
    List<Map<String, String>> fields = []
    fields << matter.cmdField(DataType.UINT64 as Integer, 0x00 as Integer, swapOctetsAny(String.format('%016X', providerNodeId)))
    fields << matter.cmdField(DataType.UINT16 as Integer, 0x01 as Integer, swapOctetsAny(String.format('%04X', vendorId ?: 0L)))
    fields << matter.cmdField(DataType.UINT8  as Integer, 0x02 as Integer, String.format('%02X', reason ?: 0))
    fields << matter.cmdField(DataType.UINT16 as Integer, 0x04 as Integer, swapOctetsAny(String.format('%04X', providerEndpoint ?: 0)))
    logDebug "announceOtaProvider: cmdField list = ${fields}"
    recordProbe('P5', "AnnounceOTAProvider fields as built by cmdField: ${fields}")
    scheduleCommandTimeoutCheck(OTA_REQUESTOR_CLUSTER)
    sendToDevice(matter.invoke(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, 0x0000 as Integer, fields))
}

/** Follow an update that is already running - no matter which controller or app started it. */
void watchFirmwareUpdate(String minutesPar = '30') {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    Integer minutes = parseNumber(minutesPar?.trim() ?: '30') ?: 30
    if (device.currentValue('otaSupported') == 'no') {
        logWarn 'watchFirmwareUpdate: this device has no Matter OTA cluster to watch'
        return
    }
    if (state.subscriptions == null) { state.subscriptions = [] }
    [[0, OTA_REQUESTOR_CLUSTER, 0x0002], [0, OTA_REQUESTOR_CLUSTER, 0x0003]].each { List s ->
        if (!(s in state.subscriptions)) { state.subscriptions << s }
    }
    state.states['pendingOtaSubscribe'] = true
    sendSubscription()
    logInfo "watching the firmware update for the next ${minutes} minute(s) - progress shows up in otaState and otaProgress"
    sendInfoEvent('watching the update', "for ${minutes} minutes")
    runIn(minutes * 60, 'stopWatchingFirmwareUpdate', [overwrite: true])
    runIn(6, 'getFirmwareInfo')
}

void stopWatchingFirmwareUpdate() {
    logInfo 'no longer watching the firmware update'
    sendInfoEvent('stopped watching the update')
}

// =============================================================================================
//  Phase 0 - the Matter OTA probe suite
//
//  Matter OTA is undocumented on Hubitat: it appears nowhere in the platform documentation, in the
//  staff forum thread, or in HUBITAT_MATTER_API.md. These probes establish the facts on real
//  hardware before any of it is relied upon. Results are appended to state.otaProbe, which can be
//  read back from the device page or over /device/fullJson/<deviceId>.
// =============================================================================================

void recordProbe(final String key, final String text) {
    if (state.otaProbe == null) { return }      // no probe suite has been started - do not accumulate
    List entries = (state.otaProbe[key] ?: []) as List
    String line = "${new Date().format('HH:mm:ss')} ${text}"
    if (entries.size() > 20) { entries = entries.takeRight(20) }
    entries << line
    state.otaProbe[key] = entries
}

void otaProbe(String probe = 'ALL') {
    ensureStateMaps()
    String p = probe ?: 'ALL'
    logWarn "otaProbe('${p}') - results are appended to state.otaProbe"
    if (p.startsWith('CLEAR')) { state.remove('otaProbe') ; logInfo 'probe results cleared' ; return }
    state.otaProbe = (state.otaProbe ?: [:])
    if (p.startsWith('ALL')) {
        state.otaProbe = [:]
        otaProbeP1()
        runIn(6,  'otaProbeP2')
        runIn(14, 'otaProbeP3')
        runIn(24, 'otaProbeP5')
        runIn(26, 'otaProbeP6')
        runIn(30, 'otaProbeP4')
        runIn(45, 'otaProbeReport')
        return
    }
    if (p.startsWith('P1')) { otaProbeP1() ; runIn(8, 'otaProbeReport') ; return }
    if (p.startsWith('P2')) { otaProbeP2() ; runIn(8, 'otaProbeReport') ; return }
    if (p.startsWith('P3')) { otaProbeP3() ; runIn(15, 'otaProbeReport') ; return }
    if (p.startsWith('P4')) { otaProbeP4() ; runIn(15, 'otaProbeReport') ; return }
    if (p.startsWith('P5')) { otaProbeP5() ; runIn(2, 'otaProbeReport') ; return }
    if (p.startsWith('P6')) { otaProbeP6() ; runIn(2, 'otaProbeReport') ; return }
    logWarn "otaProbe: '${probe}' is not a known probe"
}

/** P1 - is the OTA Software Update Requestor cluster there, and what does it accept? */
void otaProbeP1() {
    logInfo 'PROBE P1: is the OTA Software Update Requestor cluster (0x002A) present on endpoint 0 ?'
    recordProbe('P1', 'started - reading the root ServerList and the 0x002A global elements')
    state.states['manualRead'] = null
    sendAttributeReads(0, DESCRIPTOR_CLUSTER, [0x0000, 0x0001, 0x0003])
    runInMillis(1500, 'otaProbeP1b')
}

void otaProbeP1b() {
    sendAttributeReads(0, OTA_REQUESTOR_CLUSTER, [0xFFFB, 0xFFF9, 0xFFF8, 0xFFFA, 0xFFFC, 0xFFFD])
    runInMillis(2500, 'otaProbeP1c')
}

void otaProbeP1c() {
    List<Integer> server = discoveredServerList(0)
    boolean present = server.contains(OTA_REQUESTOR_CLUSTER)
    List<Integer> accepted = discoveredAttributeList(0, OTA_REQUESTOR_CLUSTER)
    recordProbe('P1', "root ServerList = ${server.collect { hex4(it) }}")
    recordProbe('P1', "OTA Requestor 0x002A present = ${present}; its AttributeList = ${accepted.collect { hex4(it) }}")
    recordProbe('P1', "OTA Provider 0x0029 present on this node = ${server.contains(OTA_PROVIDER_CLUSTER)} (expected false - a device is a Requestor, not a Provider)")
    logInfo "PROBE P1 result: OTA Requestor cluster ${present ? 'IS' : 'is NOT'} present on endpoint 0"
}

/** P2 - the four Requestor attributes. DefaultOTAProviders is the single most valuable answer here. */
void otaProbeP2() {
    logInfo 'PROBE P2: reading DefaultOTAProviders, UpdatePossible, UpdateState and UpdateStateProgress'
    recordProbe('P2', 'started')
    sendAttributeReads(0, OTA_REQUESTOR_CLUSTER, [0x0000, 0x0001, 0x0002, 0x0003])
    runIn(6, 'otaProbeP2b')
}

void otaProbeP2b() {
    Long provider = configuredProviderNodeId()
    if (provider == null) {
        recordProbe('P2', 'VERDICT: no OTA Provider is named in DefaultOTAProviders - nothing on this fabric offers firmware to this device')
        logWarn 'PROBE P2 verdict: this device names no OTA Provider. AnnounceOTAProvider itself works - there is ' +
                'simply no Provider on this fabric to point it at, because Hubitat does not implement one and vendor ' +
                'bridges generally do not either. Ecosystem controllers (Apple, Google, SmartThings, Home Assistant) ' +
                'do, sourcing firmware from the Matter Distributed Compliance Ledger. Use "utilities nodes" to see ' +
                'what else is on this fabric.'
    } else {
        recordProbe('P2', "VERDICT: an OTA Provider IS configured - node ${provider}. AnnounceOTAProvider can address it.")
        logInfo "PROBE P2 verdict: OTA Provider node ${provider} is configured on this fabric"
    }
}

/** P3 - do OTA reports and events actually reach parse() ? */
void otaProbeP3() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    logInfo 'PROBE P3: subscribing to the OTA state, the progress and all three OTA events'
    recordProbe('P3', 'started - cleanSubscribe on 0x002A attributes 0x0002/0x0003 plus eventPath(0, 0x002A, -1)')
    List<Map<String, String>> paths = [
        matter.attributePath(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, 0x0002 as Integer),
        matter.attributePath(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, 0x0003 as Integer),
        matter.eventPath(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, -1 as Integer)      // -1 = every event of the cluster
    ]
    state.states['pendingOtaSubscribe'] = true
    sendToDevice(matter.cleanSubscribe(1 as Integer, 600 as Integer, paths))
    runIn(12, 'otaProbeP3b')
}

void otaProbeP3b() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    if (state.states['pendingOtaSubscribe'] == true) {
        state.states['pendingOtaSubscribe'] = false
        recordProbe('P3', 'VERDICT: no SubscriptionResult callback arrived within 12 s - the OTA subscription was NOT established')
        logWarn 'PROBE P3 verdict: the OTA subscription produced no SubscriptionResult'
    } else {
        recordProbe('P3', 'VERDICT: the OTA subscription was established')
    }
}

/**
 * P4 - is AnnounceOTAProvider reachable at all? A Matter invoke against an unsupported command
 * produces NO callback whatsoever, so silence and rejection look identical - hence the timeout arm.
 * When no provider is known this deliberately announces node 0. That is NOT passive: P4 on the
 * GRILLPLATS plug showed the device accepts the command and then sits in Querying until it times
 * out, looking for a provider that does not exist. Harmless there, but it does move the device's
 * OTA state machine. Any status at all still proves that the command exists and is reachable.
 */
void otaProbeP4() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    Long provider = configuredProviderNodeId()
    Long target = provider ?: 0L
    logWarn "PROBE P4: invoking AnnounceOTAProvider (0x002A / 0x00) with provider node ${target}${provider == null ? ' - node 0 does not exist, so the device will query it and time out; sent only to see whether the command is accepted' : ''}"
    recordProbe('P4', "started - AnnounceOTAProvider node=${target} vendorId=0 reason=0 (SimpleAnnouncement) endpoint=0")
    state.states['probeP4Pending'] = true
    announceOtaProvider(target, 0L, 0, 0)
    runIn(12, 'otaProbeP4b')
}

void otaProbeP4b() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    if (state.states['probeP4Pending'] == true) {
        state.states['probeP4Pending'] = false
        recordProbe('P4', 'VERDICT: NO Invoke callback within 12 s - AnnounceOTAProvider is unsupported, unreachable, or silently dropped')
        logWarn 'PROBE P4 verdict: no answer to AnnounceOTAProvider'
    }
}

/** P5 - can a 64 bit node id be encoded at all, and what does cmdField do with the bytes? Builder only, nothing is sent. */
void otaProbeP5() {
    logInfo 'PROBE P5: encoding a 64 bit node id with cmdField - nothing is sent to the device'
    Long sample = 0x1122334455667788L
    String plain   = String.format('%016X', sample)
    String swapped = swapOctetsAny(plain)
    recordProbe('P5', "sample node id 0x${plain} -> swapped ${swapped}")
    try {
        Map f = matter.cmdField(DataType.UINT64 as Integer, 0x00 as Integer, swapped)
        recordProbe('P5', "cmdField(UINT64=0x${hex2(DataType.UINT64)}, tag 0, '${swapped}') = ${f}")
        logInfo "PROBE P5: cmdField returned ${f}"
    } catch (Throwable t) {
        recordProbe('P5', "cmdField(UINT64) THREW: ${t?.message}")
        logWarn "PROBE P5: cmdField(UINT64) threw ${t?.message}"
    }
    try {
        Map w = matter.attributeWriteRequest(0 as Integer, OTA_REQUESTOR_CLUSTER as Integer, 0x0000 as Integer, DataType.UINT16 as Integer, '1234')
        recordProbe('P5', "attributeWriteRequest(UINT16, '1234') = ${w}  <- shows the TLV framing, and whether the helper swaps")
    } catch (Throwable t) {
        recordProbe('P5', "attributeWriteRequest THREW: ${t?.message}")
    }
    recordProbe('P5', "DataType constants: UINT8=0x${hex2(DataType.UINT8)} UINT16=0x${hex2(DataType.UINT16)} UINT32=0x${hex2(DataType.UINT32)} UINT64=0x${hex2(DataType.UINT64)} NULL=0x${hex2(DataType.NULL)}")
}

/**
 * P6 - is the Matter node id of the hub itself reachable from a driver? If it is, and if the hub
 * turns out to be an OTA Provider, AnnounceOTAProvider can point a device straight at it.
 * The Hubitat sandbox blocks reflection, so only known property names can be read - never .class.
 */
void otaProbeP6() {
    logInfo 'PROBE P6: looking for the Matter node id of the hub'
    probeProperty('P6', 'location.hub.id')        { location?.hub?.id }
    probeProperty('P6', 'location.hub.name')      { location?.hub?.name }
    probeProperty('P6', 'location.hub.hardwareID'){ location?.hub?.hardwareID }
    probeProperty('P6', 'location.hub.zigbeeEui') { location?.hub?.zigbeeEui }
    probeProperty('P6', 'location.hub.zigbeeId')  { location?.hub?.zigbeeId }
    probeProperty('P6', 'location.hub.firmwareVersionString') { location?.hub?.firmwareVersionString }
    probeProperty('P6', 'device.deviceNetworkId') { device?.deviceNetworkId }
    probeProperty('P6', 'device.getData()')       { device?.getData() }
    probeProperty('P6', 'matter.getMatterEndpoints()')    { matter.getMatterEndpoints() }
    probeProperty('P6', 'matter.getMatterFingerprints()') { matter.getMatterFingerprints() }
    logInfo 'PROBE P6 finished - see state.otaProbe.P6'
}

void probeProperty(final String key, final String label, final Closure supplier) {
    try {
        Object v = supplier.call()
        recordProbe(key, "${label} = ${v}")
    } catch (Throwable t) {
        recordProbe(key, "${label} THREW ${t?.message}")
    }
}

void otaProbeReport() {
    Map probes = (state.otaProbe ?: [:]) as Map
    if (probes.isEmpty()) { logWarn 'no probe results yet' ; return }
    String text = probes.sort().collect { String k, List v -> "<b>${k}</b><br>${v.join('<br>')}" }.join('<br>')
    logInfo "<b>Matter OTA probe results</b><br>${text}"
    sendLastResult('OTA probe results are in state.otaProbe - see the live logs')
    sendInfoEvent('OTA probes finished', "${probes.size()} probe group(s)")
}

// =============================================================================================
//  TIER 0 - housekeeping. In the built-in Device driver these are platform behaviors; a custom
//  driver has to do them by hand.
// =============================================================================================

void deleteAllChildDevices() {
    List children = getChildDevices() ?: []
    if (children.isEmpty()) { logInfo 'there are no child devices to delete' ; return }
    logWarn "deleting ${children.size()} child device(s) ..."
    children.each { child ->
        try {
            deleteChildDevice(child.deviceNetworkId)
            logInfo "deleted child ${child.displayName}"
        } catch (Exception e) {
            logWarn "could not delete child ${child.displayName}: ${e?.message}"
        }
    }
    sendInfoEvent('child devices deleted')
}

void deleteAllCurrentStates() {
    logWarn 'clearing the Current States ...'
    // always pass the attribute name - the no argument form does not do what it looks like
    device.currentStates?.each { st ->
        try { device.deleteCurrentState(st.name) } catch (Exception e) { logWarn "could not delete state ${st.name}: ${e?.message}" }
    }
    sendInfoEvent('current states cleared')
}

void deleteAllScheduledJobs() {
    logWarn 'cancelling every scheduled job ...'
    unschedule()
    state.collect = null
    sendInfoEvent('scheduled jobs canceled')
}

/**
 * Erases state and leaves it erased, the way the built-in Device driver's namesake command does -
 * it does NOT re-seed. Every entry point rebuilds what it needs through ensureStateMaps(), parse()
 * self-heals its four maps, and endpointRecord() creates state.endpoints on demand, so an empty
 * state is a valid state to be in.
 */
void deleteAllStates() {
    logWarn 'erasing the State Variables ...'
    state.clear()
    sendInfoEvent('state variables erased')
}

// =============================================================================================
//  Safe actions
// =============================================================================================

/**
 * Identify - cluster 0x0003, command 0x00, field 0 is a UINT16 IdentifyTime in seconds.
 * The existing Matter Advanced drivers send an unswapped '0101' here, which is 257 seconds, not 1.
 */
void identify(String secondsPar = '10') {
    Integer seconds = parseNumber(secondsPar?.trim() ?: '10') ?: 10
    if (seconds < 1) { seconds = 1 }
    if (seconds > 300) { seconds = 300 }
    Integer endpoint = findEndpointWithCluster(IDENTIFY_CLUSTER)
    if (endpoint == null) { endpoint = safeHexToInt(device.endpointId, 1) }
    List<Map<String, String>> fields = [matter.cmdField(DataType.UINT16 as Integer, 0x00 as Integer, swapOctetsAny(String.format('%04X', seconds)))]
    logInfo "identify: asking endpoint ${hex2(endpoint)} to blink for ${seconds} second(s)"
    scheduleCommandTimeoutCheck(IDENTIFY_CLUSTER)
    sendToDevice(matter.invoke(endpoint as Integer, IDENTIFY_CLUSTER as Integer, 0x0000 as Integer, fields))
}

void ping() {
    ensureStateMaps()
    logInfo 'ping ...'
    state.states['isPing'] = true
    state.lastTx['pingTime'] = new Date().getTime()
    readAttributePath(0, BASIC_INFORMATION_CLUSTER, 0x0000)     // arms the watchdog on 0x0028 itself      // DataModelRevision - present on every Matter node
}

// =============================================================================================
//  The one line command box
// =============================================================================================

@Field static final Map<String, String> UtilitiesMap = [
    'read'            : 'utilRead',
    'readall'         : 'utilReadAll',
    'write'           : 'utilWrite',
    'invoke'          : 'utilInvoke',
    'subscribe'       : 'utilSubscribe',
    'unsubscribe'     : 'unsubscribeMatter',
    'subscriptions'   : 'showSubscriptions',
    'discover'        : 'discoverAll',
    'info'            : 'utilInfo',
    'firmware'        : 'getFirmwareInfo',
    'checkupdate'     : 'checkForUpdate',
    'setprovider'     : 'utilSetProvider',
    'probe'           : 'utilProbe',
    'endpoints'       : 'utilEndpoints',
    'nodes'           : 'utilNodes',
    'stats'           : 'utilStats',
    'resetstats'      : 'resetStats',
    'help'            : 'utilitiesHelp'
]

void utilities(String commandLine = null) {
    String line = commandLine?.trim()
    if (line == null || line == '' || line == '?' || line == 'help') { utilitiesHelp() ; return }
    List<String> words = line.split(/\s+/) as List
    String verb = words[0].toLowerCase()
    List<String> args = words.size() > 1 ? words[1..-1] : []
    String func = UtilitiesMap[verb]
    if (func == null) { logWarn "utilities: '${verb}' is not a known command - type  ?  for the list" ; return }
    try {
        if (args.isEmpty()) { "${func}"() } else { "${func}"(args) }
    } catch (MissingMethodException mme) {
        try { "${func}"() } catch (Exception e2) { logWarn "utilities: ${verb} failed: ${e2?.message}" }
    } catch (Exception e) {
        logWarn "utilities: ${verb} failed: ${e?.message}"
    }
}

void utilitiesHelp() {
    String help = [
        '<b>One line commands</b> - type them into the utilities box:',
        'read &lt;endpoint&gt; &lt;cluster&gt; &lt;attribute&gt;   for example:  read 1 OnOff OnOff',
        'readall &lt;endpoint&gt; &lt;cluster&gt;               reads every attribute of that cluster',
        'write &lt;endpoint&gt; &lt;cluster&gt; &lt;attribute&gt; &lt;dataType&gt; &lt;value&gt;',
        'invoke &lt;endpoint&gt; &lt;cluster&gt; &lt;command&gt; [dataType:tag:value,...]',
        'subscribe add|remove &lt;endpoint&gt; &lt;cluster&gt; &lt;attribute&gt;',
        'unsubscribe | subscriptions | discover | endpoints',
        'info [endpoint]        firmware        checkupdate',
        'setprovider &lt;nodeId&gt;   tells checkupdate which OTA Provider node to announce; 0 clears it',
        'nodes                  every Matter node on this fabric, with its node id, from the hub itself',
        'probe [ALL|P1..P6]     stats    resetstats',
        'Names or numbers both work: OnOff, 0x0006 and 6 are the same cluster.'
    ].join('<br>')
    logInfo help
    sendLastResult('utilities help printed to the live logs')
}

void utilRead(List args)  { readAttribute(args[0], args.size() > 1 ? args[1] : null, args.size() > 2 ? args[2] : null) }
void utilReadAll(List args) { readAttribute(args[0], args.size() > 1 ? args[1] : null, null) }
void utilWrite(List args) { writeAttribute(args[0], args[1], args[2], args.size() > 3 ? args[3] : null, args.size() > 4 ? args[4] : null) }
void utilInvoke(List args) { invokeCommand(args[0], args[1], args[2], args.size() > 3 ? args[3] : null) }
void utilSubscribe(List args) { subscribeAttribute(args[0], args[1], args[2], args.size() > 3 ? args[3] : null) }
void utilInfo(List args)  { getInfoAdvanced(args.isEmpty() ? '' : args[0]) }
void utilInfo()           { getInfoAdvanced('') }
void utilProbe(List args) { otaProbe(args.isEmpty() ? 'ALL' : args[0]) }
void utilProbe()          { otaProbe('ALL') }
void utilEndpoints()      { logInfo "<b>endpoints</b><br>${summarizeEndpoints().replace(' | ', '<br>')}" }

/**
 * Every Matter node on this fabric, read from the hub's own inventory endpoint - the same JSON
 * behind Settings / Matter details. Loopback only: the driver runs ON the hub, and a LAN address
 * must never be written into a driver. Blocking httpGet, so this is a manual command, never scheduled.
 * The node ids are what 'setprovider' wants; a hub from the same vendor as this device is the only
 * plausible OTA Provider for it.
 */
void utilNodes() {
    String myDni  = device.deviceNetworkId?.toString()
    String vendor = device.currentValue('vendorName')?.toString()
    List<String> rows = []
    try {
        httpGet([uri: MATTER_DETAILS_URL, timeout: 5]) { resp ->
            // none of these keys is platform documented - if a firmware update renames them, say
            // exactly what came back instead of reporting an empty fabric (C7)
            if (!(resp?.data instanceof Map)) {
                logWarn 'utilities nodes: the hub inventory did not come back as a Map - the JSON schema has changed'
                return
            }
            List nodes = (resp.data['devices'] as List)
            if (nodes == null) {
                logWarn "utilities nodes: no 'devices' list in the hub inventory - top level keys are ${((Map) resp.data).keySet()}"
                return
            }
            if (!nodes.isEmpty() && nodes[0] instanceof Map) { logDebug "utilities nodes: node keys = ${((Map) nodes[0]).keySet()}" }
            nodes.sort { it?.nodeId }?.each { node ->
                boolean online = node?.online == true
                String mark = ''
                // 'same vendor' is as far as this JSON allows - it carries no device type, so the
                // driver cannot tell a vendor's hub from that vendor's battery button
                if (node?.dni?.toString() == myDni)                                            { mark = ' &lt;- this device' }
                else if (online && vendor != null && node?.manufacturer?.toString() == vendor) { mark = ' &lt;- same vendor' }
                rows << "${node?.nodeId}${online ? '' : ' (offline)'} - ${node?.manufacturer} ${node?.name}${mark}"
            }
        }
    } catch (Exception e) {
        logWarn "utilities nodes: could not read the hub Matter inventory: ${e?.message}"
        return
    }
    if (rows.isEmpty()) { logWarn 'utilities nodes: the hub returned no Matter nodes' ; return }
    String footer = 'An OTA Provider, if this fabric has one, is a hub or a bridge made by that vendor - ' +
                    'never a sensor or a button. The only way to know is to ask: <b>setprovider &lt;nodeId&gt;</b> ' +
                    'then <b>Check For Update</b>, and read the StateTransition reason - <i>success</i> means ' +
                    'that node answered, <i>failure</i> means it did not.'
    logInfo "<b>${rows.size()} Matter node(s) on this fabric</b><br>${rows.join('<br>')}<br>${footer}"
    sendLastResult("${rows.size()} Matter nodes - see the live logs")
}
void utilStats()          { logInfo "stats: ${state.stats}  lastRx: ${state.lastRx}  lastTx: ${state.lastTx}" }

void utilSetProvider(List args) {
    if (state.ota == null) { state.ota = [:] }
    Long nodeId = safeToLong(args[0])
    if (nodeId == 0L) {         // 'setprovider 0' clears the override - node 0 is not a real node id
        state.ota.remove('providerNodeIdOverride')
        logInfo "the OTA Provider override is cleared - 'Check for firmware update' goes back to DefaultOTAProviders"
        return
    }
    state.ota['providerNodeIdOverride'] = nodeId
    logInfo "the OTA Provider node id is now ${nodeId} - 'Check for firmware update' will announce that node"
}

// =============================================================================================
//  Presentation
// =============================================================================================

/** The transient banner in the _status_ attribute, cleared automatically after a minute. */
void sendInfoEvent(final String info = null, final String descriptionText = null) {
    if (info == null || info == 'clear') {
        sendEvent(name: '_status_', value: 'clear', descriptionText: 'last info messages auto cleared', type: 'digital')
        return
    }
    sendEvent(name: '_status_', value: info, descriptionText: descriptionText ?: info, type: 'digital')
    runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent', [overwrite: true])
}

void clearInfoEvent() { sendInfoEvent('clear') }

/** Puts the answer where a non technical user will actually see it - in the Current States list. */
void sendLastResult(final String text) {
    if (text == null) { return }
    String value = text.size() > MAX_ATTRIBUTE_VALUE_LEN ? "${text.take(MAX_ATTRIBUTE_VALUE_LEN - 20)} ... see the logs" : text
    sendEvent(name: 'lastResult', value: value, descriptionText: value, type: 'digital')
}

/** Pretty print one attribute value for the dump lines. */
/**
 * Shortens one value for the dump listing. Operational credentials are the reason this exists:
 * NOCs, TrustedRootCertificates and Fabrics each arrive as a single base64 blob well over a
 * thousand characters, and the whole dump is held in state until finishCollect() prints it.
 * A manual read of one attribute is NOT shortened - if you asked for that certificate by name, you
 * want all of it.
 */
String truncateForDump(final String text) {
    if (text == null || text.length() <= MAX_DUMP_VALUE_LEN) { return text }
    return "${text.take(MAX_DUMP_VALUE_LEN)}... (${text.length()} chars - read this attribute on its own to see it all)"
}

String formatValue(final Integer cluster, final Integer attrInt, final Object value) {
    if (value == null) { return 'null' }
    String raw = value.toString()
    if (cluster == OTA_REQUESTOR_CLUSTER && attrInt == 0x0002) {
        Integer st = safeToInt(raw)     // decimal string from the platform, not hex
        return "${raw} (${OTA_UPDATE_STATES[st] ?: 'unknown'})"
    }
    if (cluster == BASIC_INFORMATION_CLUSTER && attrInt == 0x0015) { return "${raw} (Matter ${decodeSpecificationVersion(value)})" }
    if (attrInt in [0xFFF8, 0xFFF9, 0xFFFA, 0xFFFB]) { return toIntList(value).collect { '0x' + hex4(it) }.toString() }
    if (attrInt == 0xFFFC) {
        Integer fm = safeToInt(raw, null)    // decimal string from the platform, not hex
        return (fm == null) ? raw : "0x${hex4(fm)} (${fm})"
    }
    // no hex decoration: parse(Map) delivers integers as decimal strings and octet strings as
    // base64, so anything that looks hexadecimal here is a text attribute such as UniqueID
    return raw
}

/** A single attribute read asked for by hand deserves a headline, not just a dump line. */
void announceManualReadResult(final Integer endpoint, final Integer cluster, final Integer attrInt, final String attrName, final Object value) {
    List pending = state.states['manualRead'] as List
    if (pending == null || pending.size() < 3) { return }
    if ((pending[0] as Integer) != endpoint || (pending[1] as Integer) != cluster || (pending[2] as Integer) != attrInt) { return }
    state.states['manualRead'] = null
    String text = "endpoint ${hex2(endpoint)} ${getClusterName(cluster)} ${attrName} = ${formatValue(cluster, attrInt, value)}"
    logInfo "<b>${text}</b>"
    sendLastResult(text)
}

String fmtHelpInfo(final String str) {
    String info = "${DRIVER_NAME} v${version()}"
    String prefLink = "<a href='${COMM_LINK}' target='_blank'><div style='color:#1A77C9;font-weight:bold'>${str}</div><div style='font-size:small'><i>${info}</i></div></a>"
    return prefLink
}

// =============================================================================================
//  Command timeout, statistics and health
// =============================================================================================

void ensureStateMaps() {
    if (state.stats == null || state.states == null || state.lastRx == null ||
        state.lastTx == null || state.health == null || state.endpoints == null) {
        initializeVars(fullInit = false)
    }
}

/**
 * Arms the silent-failure watchdog. 'cluster' is what was sent, so that an unrelated subscription
 * report cannot pass for the answer - a chatty device (the GRILLPLATS emitted OTA reports every few
 * seconds) would otherwise cancel every timeout and no unanswered read would ever be reported.
 * Pass null only where the answering cluster genuinely cannot be predicted.
 */
void scheduleCommandTimeoutCheck(final Integer cluster = null, final int delay = COMMAND_TIMEOUT) {
    ensureStateMaps()
    state.states['pendingAnswer'] = true
    state.states['pendingCluster'] = cluster
    runIn(delay, 'deviceCommandTimeout', [overwrite: true])
}

/**
 * Is this message the answer to what was sent, or unrelated traffic? Subscription reports look
 * exactly like read answers apart from the cluster, so the cluster is the whole test. Anything the
 * platform leaves unsaid is accepted rather than assumed wrong - a missed timeout warning is a
 * smaller sin than a false one.
 */
boolean isAnswerToPending(final Map descMap) {
    if (!(descMap.callbackType in ['Report', 'WriteAttributes', 'Invoke'])) { return false }
    Object want = state.states['pendingCluster']
    if (want == null || descMap.clusterInt == null) { return true }
    return (descMap.clusterInt as Integer) == safeToInt(want, -1)
}

void checkPendingCommandAnswered(final Map descMap) {
    if (state.states['pendingAnswer'] == true && isAnswerToPending(descMap)) {
        state.states['pendingAnswer'] = false
        state.states['pendingCluster'] = null
        unschedule('deviceCommandTimeout')
    }
    // the cluster test matters - without it an unrelated Invoke (identify, for one) inside P4's
    // 12 s window would be recorded as the AnnounceOTAProvider answer
    if (state.states['probeP4Pending'] == true && descMap.callbackType == 'Invoke' &&
        descMap.clusterInt == OTA_REQUESTOR_CLUSTER) {
        state.states['probeP4Pending'] = false
        recordProbe('P4', "VERDICT: an Invoke callback DID arrive - status ${descMap.status}. AnnounceOTAProvider is reachable on this platform.")
    }
    // the attribute and callbackType tests matter: a subscribed SoftwareVersionString report, or the
    // WriteAttributes ack of a NodeLabel write, would otherwise complete the ping and time an unrelated message
    if (state.states['isPing'] == true && descMap.clusterInt == BASIC_INFORMATION_CLUSTER &&
        descMap.attrInt == 0x0000 && descMap.callbackType == 'Report') {
        state.states['isPing'] = false
        Long now = new Date().getTime()
        Integer timeRunning = (now - safeToLong(state.lastTx['pingTime'], now)) as Integer
        if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
            state.stats['pingsOK'] = safeToInt(state.stats['pingsOK']) + 1
            if (timeRunning < safeToInt(state.stats['pingsMin'], 99999)) { state.stats['pingsMin'] = timeRunning }
            if (timeRunning > safeToInt(state.stats['pingsMax'])) { state.stats['pingsMax'] = timeRunning }
            state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), timeRunning as Double) as int
            sendEvent(name: 'rtt', value: timeRunning, unit: 'ms', descriptionText: "round trip time is ${timeRunning} ms")
            logInfo "ping answered in ${timeRunning} ms"
        }
    }
}

void deviceCommandTimeout() {
    ensureStateMaps()               // a command or a scheduled job can arrive with state bare
    state.states['pendingAnswer'] = false
    state.stats['pingsFail'] = safeToInt(state.stats['pingsFail']) + 1
    if (state.states['isPing'] == true) {
        state.states['isPing'] = false
        sendEvent(name: 'rtt', value: 0, unit: 'ms', descriptionText: 'no answer to the ping')
    }
    // a ReadRequest whose response would be too large is never answered - drop to small chunks
    if (state.states['smallReadChunks'] != true && isCollecting()) {
        state.states['smallReadChunks'] = true
        logWarn 'no answer from the device - switching to smaller read chunks from now on'
    }
    logWarn 'no answer from the device within the timeout'
    sendInfoEvent('no answer', 'the device did not answer within the timeout')
}

void updateRxStats() {
    state.stats['rxCtr'] = safeToInt(state.stats['rxCtr']) + 1
    state.lastRx['checkInTime'] = new Date().getTime()
}

void updateTxStats() {
    state.stats['txCtr'] = safeToInt(state.stats['txCtr']) + 1
    state.lastTx['cmdTime'] = new Date().getTime()
}

void resetStats() {
    state.stats = [rxCtr: 0, txCtr: 0, pingsOK: 0, pingsFail: 0, pingsMin: 0, pingsMax: 0, pingsAvg: 0]
    state.lastRx = [:]
    state.lastTx = [:]
    logInfo 'statistics reset'
}

Double approxRollingAverage(final Double avgPar, final Double newSample) {
    Double avg = (avgPar == null || avgPar == 0) ? newSample : avgPar
    avg -= avg / 10
    avg += newSample / 10
    return avg
}

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    Map healthProperty = [warning: 'Health check', scheduled: true]
    if (healthMethod == 1) {
        state.health['checkMethod'] = 'activity'
        schedule(getCron(intervalMins * 60), 'deviceHealthCheck')
    } else {
        state.health['checkMethod'] = 'polling'
        schedule(getCron(intervalMins * 60), 'ping')
    }
    logDebug "scheduleDeviceHealthCheck: ${healthProperty.warning} every ${intervalMins} minutes, method ${healthMethod}"
}

private void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    state.health['checkMethod'] = 'disabled'
}

void deviceHealthCheck() {
    state.health['checkCtr'] = safeToInt(state.health['checkCtr']) + 1
    if (safeToInt(state.health['checkCtr']) >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline') {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    } else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${state.health['checkCtr']})"
    }
}

void setHealthStatusOnline() {
    state.health['checkCtr'] = 0
    if ((device.currentValue('healthStatus') ?: 'unknown') != 'online') { sendHealthStatusEvent('online') }
}

void sendHealthStatusEvent(final String value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "healthStatus set to ${value}")
    if (value == 'online') { logInfo 'is online' } else { logWarn 'is offline' }
}

String getCron(final int timeInSeconds) {
    int minutes = (timeInSeconds / 60) as int
    int hours = (minutes / 60) as int
    if (hours > 23) { hours = 23 }
    if (timeInSeconds < 60) { return "*/${timeInSeconds} * * * * ? *" }
    if (minutes < 60) { return "${new Random().nextInt(60)} */${minutes} * ? * *" }
    return "${new Random().nextInt(60)} ${new Random().nextInt(60)} */${hours} ? * *"
}

// =============================================================================================
//  Version and variables
// =============================================================================================

String driverVersionAndTimeStamp() {
    return "${version()} ${timeStamp()}${_DEBUG ? ' (debug version!) ' : ' '}(${device.getDataValue('model')} ${location.hub.firmwareVersionString})"
}

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the driver version from ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        sendInfoEvent("upgraded to driver version ${version()}")
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(fullInit = false)
        unschedule('deviceCommandTimeout')
    }
}

void initializeVars(boolean fullInit = false) {
    logDebug "initializeVars(fullInit=${fullInit})"
    if (fullInit == true) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
        state.deviceType = 'MATTER_DEVICE'
    }
    if (state.stats == null)  { state.stats = [rxCtr: 0, txCtr: 0, pingsOK: 0, pingsFail: 0, pingsMin: 0, pingsMax: 0, pingsAvg: 0] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }
    if (state.endpoints == null) { state.endpoints = [:] }
    if (state.subscriptions == null) { state.subscriptions = [] }
    if (state.ota == null) { state.ota = [:] }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting('txtEnable', [value: true, type: 'bool']) }
    if (fullInit == true || settings?.logEnable == null) { device.updateSetting('logEnable', [value: DEFAULT_DEBUG_LOGGING, type: 'bool']) }
    if (fullInit == true || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value: false, type: 'bool']) }
    if (fullInit == true) {
        sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'initialized')
        sendEvent(name: 'otaSupported', value: 'unknown', descriptionText: 'not checked yet')
    }
}

// =============================================================================================
//  Small helpers
// =============================================================================================

String hex2(final Number value) { return formatMatterHex(value, 2) }
String hex4(final Number value) { return formatMatterHex(value, 4) }

String formatMatterHex(final Number value, final Integer minDigits) {
    if (value == null) { return '?' * minDigits }
    Long l = value.longValue()
    String hex = (l < 0) ? Long.toHexString(l & 0xFFFFFFFFL).toUpperCase() : Long.toHexString(l).toUpperCase()
    while (hex.length() < minDigits) { hex = "0${hex}" }
    return hex
}

/** Parses hexadecimal. Accepts an optional 0x prefix and a Number passed straight through. */
Integer safeHexToInt(final Object val, final Integer defaultValue = null) {
    if (val == null) { return defaultValue }
    if (val instanceof Number) { return ((Number) val).intValue() }
    String s = val.toString().trim()
    if (s == '' || s.equalsIgnoreCase('null')) { return defaultValue }
    if (s.toLowerCase().startsWith('0x')) { s = s.substring(2) }
    try { return (int) Long.parseLong(s, 16) } catch (NumberFormatException ignored) { }
    try { return Integer.parseInt(s, 10) } catch (NumberFormatException ignored) { return defaultValue }
}

/** Parses DECIMAL. Never feed it hexadecimal. */
Integer safeToInt(final Object val, final Integer defaultValue = 0) {
    if (val == null) { return defaultValue }
    if (val instanceof Number) { return ((Number) val).intValue() }
    try { return val.toString().trim().toInteger() } catch (Exception ignored) { return defaultValue }
}

Long safeToLong(final Object val, final Long defaultValue = 0L) {
    if (val == null) { return defaultValue }
    if (val instanceof Number) { return ((Number) val).longValue() }
    try { return Long.parseLong(val.toString().trim()) } catch (Exception ignored) { return defaultValue }
}

Double safeToDouble(final Object val, final Double defaultValue = 0.0) {
    if (val == null) { return defaultValue }
    if (val instanceof Number) { return ((Number) val).doubleValue() }
    try { return val.toString().trim().toDouble() } catch (Exception ignored) { return defaultValue }
}

Boolean isTrueish(final Object val) {
    if (val == null) { return false }
    if (val instanceof Boolean) { return (Boolean) val }
    String s = val.toString().trim().toLowerCase()
    return s in ['true', '1', '01', 'yes', 'on']
}

// =============================================================================================
//  Logging
// =============================================================================================

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} ${msg}" } }
void logInfo(final String msg)  { if (settings?.txtEnable != false) { log.info  "${device.displayName} ${msg}" } }
void logWarn(final String msg)  { if (settings?.txtEnable != false) { log.warn  "${device.displayName} ${msg}" } }    // deliberately NOT gated on logEnable - see AGENTS.md
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} ${msg}" } }
void logError(final String msg) { if (settings?.txtEnable != false) { log.error "${device.displayName} ${msg}" } }

void test(String par = '') {
    log.warn "test(${par})"
    log.warn "endpoints: ${state.endpoints}"
    log.warn "subscriptions: ${state.subscriptions}"
    log.warn "ota: ${state.ota}"
}
