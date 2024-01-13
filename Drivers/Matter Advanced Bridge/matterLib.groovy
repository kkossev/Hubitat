library(
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'matter',
    description: 'Matter Library',
    name: 'matterLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Bridge/MatterLib.groovy',
    version: '1.0.1',
    documentationLink: ''
)
/*
  *  Common Matter Library
  *
  *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
  *  https://community.hubitat.com/t/project-zemismart-m1-matter-bridge-for-tuya-zigbee-devices-matter/127009
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
  * ver. 1.0.0  2024-01-10 kkossev  - first version
  * ver. 1.0.1  2024-01-11 kkossev  - added WindowCovering cluster 0x0102
  *
  *                                   TODO:
  *
*/

import groovy.transform.Field

/* groovylint-disable-next-line ImplicitReturnStatement */
@Field static final String matterLibVersion = '1.0.1'
@Field static final String matterLibStamp   = '2024/01/11 11:59 PM'

// no metadata section for matterLib

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
    0x0505  : 'TargetNavigator',            // An interface for UX navigation within a set of targets on a Video Player device or Content App endpoint.
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
    /* groovylint-disable-next-line CouldBeSwitchStatement, ReturnsNullInsteadOfEmptyCollection */
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
    if (cluster == '0400') { return IlluminanceMeasurementClusterAttributes }   // TODO
    if (cluster == '0402') { return TemperatureMeasurementClusterAttributes }
    if (cluster == '0403') { return PressureMeasurementClusterAttributes }      // TODO
    if (cluster == '0405') { return RelativeHumidityMeasurementClusterAttributes }
    if (cluster == '0406') { return OccupancySensingClusterAttributes }
    /* groovylint-disable-next-line ReturnsNullInsteadOfEmptyCollection */
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

@Field static final Map<Integer, String> OnOffClusterCommands = [
    0x00    : 'Off',
    0x01    : 'On',
    0x02    : 'Toggle',
    0x40    : 'OffWithEffect',
    0x41    : 'OnWithRecallGlobalScene',
    0x42    : 'OnWithTimedOff'
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

// 5.3.3 Window Covering Cluster 0x0102 (258)
@Field static final Map<Integer, String> WindowCoveringClusterAttributes = [
    0x0000  : 'Type',                           // Tuya - 00
    0x0001  : 'PhysicalClosedLimitLift',
    0x0002  : 'PhysicalClosedLimitTilt',
    0x0003  : 'CurrentPositionLift',            // Tuya - 00
    0x0004  : 'CurrentPositionTilt',
    0x0005  : 'NumberOfActuationsLift',
    0x0006  : 'NumberOfActuationsTilt',
    0x0007  : 'ConfigStatus',                   // Tuya - 04
    0x0008  : 'CurrentPositionLiftPercentage',  // Tuya - 00
    0x0009  : 'CurrentPositionTiltPercentage',
    0x000A  : 'OperationalStatus',              // Tuya - 00
    0x000B  : 'TargetPositionLiftPercent100ths',    // Tuya - 1170 (must be subtracted from 100 ?)
    0x000C  : 'TargetPositionTiltPercent100ths',
    0x000D  : 'EndProductType',                 // Tuya - 00
    0x000E  : 'CurrentPositionLiftPercent100ths',   // Tuya - 1A2C (must be subtracted from 100 ?)
    0x000F  : 'CurrentPositionTiltPercent100ths',
    0x0010  : 'InstalledOpenLimitLift',         // Tuya - 00
    0x0011  : 'InstalledClosedLimitLift',       // Tuya - FFFF
    0x0012  : 'InstalledOpenLimitTilt',
    0x0013  : 'InstalledClosedLimitTilt',
    0x0014  : 'VelocityLift',
    0x0015  : 'AccelerationTimeLift',
    0x0016  : 'DecelerationTimeLift',
    0x0017  : 'Mode',                           // Tuya - 00
    0x0018  : 'IntermediateSetpointsLift',
    0x0019  : 'IntermediateSetpointsTilt',
    0x001A  : 'SafetyStatus'
]

// 5.3.7 Window Covering Controller Cluster Commands
@Field static final  Map<Integer, String> WindowCoveringClusterCommands = [
    0x00    : 'UpOrOpen',
    0x01    : 'DownOrClose',
    0x02    : 'StopMotion',
    0x04    : 'GoToLiftValue',
    0x05    : 'GoToLiftPercentage',
    0x07    : 'GoToTiltValue',
    0x08    : 'GoToTiltPercentage'
]

// 2.3.3. Temperature Measurement Cluster 0x0402 (1026)
@Field static final Map<Integer, String> TemperatureMeasurementClusterAttributes = [
    0x0000  : 'MeasuredValue',
    0x0001  : 'MinMeasuredValue',
    0x0002  : 'MaxMeasuredValue',
    0x0003  : 'Tolerance'
]

// 2.6.4. Relative Humidity Measurement Cluster 0x0405 (1029)
@Field static final Map<Integer, String> RelativeHumidityMeasurementClusterAttributes = [
    0x0000  : 'MeasuredValue',
    0x0001  : 'MinMeasuredValue',
    0x0002  : 'MaxMeasuredValue',
    0x0003  : 'Tolerance'
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

@Field static final Map<Integer, String> MatterDeviceTypes = [
    0x000A: 'Door Lock',
    0x000B: 'Door Lock Controller',
    0x000E: 'Aggregator',
    0x000F: 'Generic Switch (Button)',
    0x0015: 'Contact Sensor',
    0x0022: 'Speaker',
    0x0023: 'Casting Video Player',
    0x0024: 'Content App',
    0x0028: 'Basic Video Player',
    0x0029: 'Casting Video Client',
    0x002A: 'Video Remote Control',
    0x002B: 'Fan',
    0x002C: 'Air Quality Sensor',
    0x002D: 'Air Purifier',
    0x0070: 'Refrigerator',
    0x0071: 'Temperature Controlled Cabinet',
    0x0072: 'Room Air Conditioner',
    0x0073: 'Laundry Washer',
    0x0074: 'Robotic Vacuum Cleaner',
    0x0075: 'Dishwasher',
    0x0076: 'Smoke CO Alarm',
    0x0100: 'On/Off Light',
    0x0101: 'Dimmable Light',
    0x0103: 'On/Off Light Switch',
    0x0104: 'Dimmer Switch',
    0x0105: 'Color Dimmer Switch',
    0x0106: 'Light Sensor',
    0x0107: 'Occupancy Sensor',
    0x010A: 'On/Off Plug-in Unit',
    0x010B: 'Dimmable Plug-In Unit',
    0x010C: 'Color Temperature Light',
    0x010D: 'Extended Color Light',
    0x0202: 'Window Covering',
    0x0203: 'Window Covering Controller',
    0x0300: 'Heating/Cooling Unit',
    0x0301: 'Thermostat',
    0x0302: 'Temperature Sensor',
    0x0303: 'Pump',
    0x0304: 'Pump Controller',
    0x0305: 'Pressure Sensor',
    0x0306: 'Flow Sensor',
    0x0307: 'Humidity Sensor',
    0x0840: 'Control Bridge',
    0x0850: 'On/Off Sensor'
]

@Field static final Map<String, Map<String, String>> MatterDeviceTypeMappingsTuya = [
    'On/Off Light':             ['clusters': ['On/Off', 'Level Control'],   'tuyaType': 'Switch'],
    'Dimmable Light':           ['clusters': ['On/Off', 'Level Control'],   'tuyaType': 'Light'],
    'Color Temperature Light':  ['clusters': ['On/Off', 'Level Control', 'Color Control'], 'tuyaType': 'Light'],
    'Extended Color Light':     ['clusters': ['On/Off', 'Level Control', 'Color Control'], 'tuyaType': 'Light'],
    'On/Off Plug-in Unit':      ['clusters': ['On/Off', 'Level Control'],   'tuyaType': 'Socket'],
    'Dimmable Plug-In Unit':    ['clusters': ['On/Off', 'Level Control'],   'tuyaType': 'Socket'],
    'Contact Sensor':           ['clusters': ['Boolean State'],             'tuyaType': 'Door and Window Sensor'],
    'Occupancy Sensor':         ['clusters': ['Occupancy Sensing'],         'tuyaType': 'PIR'],
    'Window Covering':          ['clusters': ['Window Covering'],           'tuyaType': 'Curtain']
]

