/* groovylint-disable DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, InsecureRandom, LineLength, MethodCount, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessarySetter, UnusedPrivateMethod */
/**
 *  Tuya Contact Sensor+ with healthStatus driver for Hubitat
 *
 *  https://community.hubitat.com/t/generic-tuya-contact-temp-zigbee-device/112357
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *   on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *   for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0  2023-02-12 kkossev  - Initial test version
 * ver. 1.0.1  2023-02-15 kkossev  - dynamic Preferences, depending on the device Profile; setDeviceName bug fixed; added BlitzWolf RH3001; _TZE200_nvups4nh fingerprint correction; healthStatus timer started; presenceCountDefaultThreshold bug fix;
 * ver. 1.0.2  2023-02-17 kkossev  - healthCheck is scheduled every 1 hour; added presenceCountThreshold option (default 12 hours); healthStatus is cleared when disabled or set to 'unknown' when enabled back; offlineThreshold bug fix; added Third Reality 3RDS17BZ
 * ver. 1.0.3  2023-02-25 kkossev  - added the missing illuminance event handler for _TZE200_pay2byax; open/close was reversed for _TZE200_pay2byax;
 * ver. 1.1.0  2023-04-24 kkossev  - added advancedOptions; added battery reporting configuration
 * ver. 1.1.1  2023-06-08 kkossev  - bug fix: batteryReporting configuration for Sonoff DS01
 * ver. 1.1.2  2023-10-20 kkossev  - added option 'Convert Battery Voltage to Percent'; added  pollContactStatus preference
 * ver. 1.2.0  2024-05-23 kkossev  - (dev. branch) Groovy linting; setDeviceName() bug fix; added lastBattery attribute; added ThirdReality 3RDS17BZ fingerprint; added Xfinity XHS2-UE fingerprint; 
 *                                   the configuration attempts are not repeated, if error code is returned; added setOpen and setClosed commands (for tests); added pollBatteryStatus option for devices that do not report the battery level automatically
 *
 *                                   TODO:
 *                                   TODO: filter duplicated open/close messages when 'Poll Contact Status' option is enabled
 *                                   TODO: Add clearStats command
 *                                   TODO: Add stat.stats for contact, battery, reJoin, ZDO
 *                                   TODO: Sonoff contact sensor is not reporting the battery - add an battery configuration option like in TS004F driver
 *                                   TODO: deviceProfile is not recognized?? ver 1.0.3 20223/02/25; TODO - remove 'lastRx'on Initialize
 *                                   TODO: on Initialize() - remove the prior values for Temperature, Humidity, Contact if not supported by the device profile
 *                                   TODO:  extend the model in the profile to a list
 *                                   TODO: refactor - use libraries !
 */

static String version() { '1.2.0' }
static String timeStamp() { '2024/05/23 2:55 PM' }

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus

@Field static final Boolean DEBUG = false
@Field static final Integer defaultMinReportingTime = 10

metadata {
    definition(name: 'Tuya Zigbee Contact Sensor++ w/ healthStatus', namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Contact%20Sensor/Tuya%20Contact%20Sensor.groovy', singleThreaded: true) {
        capability 'Refresh'
        capability 'Sensor'
        capability 'Battery'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'
        capability 'IlluminanceMeasurement'
        capability 'ContactSensor'
        capability 'Health Check'

        command 'initialize', [[name: 'Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****']]
        if (DEBUG == true) {
            command 'zTest', [
                    [name: 'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']],
                    [name: 'dpValue', type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']],
                    [name: 'dpType', type: 'ENUM', constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type']
            ]
            command 'test', [[name: 'test', type: 'STRING', description: 'test', constraints: ['STRING']]]
        }
        command 'setClosed', [[name: 'Set contact state to closed (for tests)']]
        command 'setOpen', [[name: 'Set contact state to open (for tests)']]

        attribute 'Info', 'string'
        // when defined as attributes, will be shown on top of the 'Current States' list ...
        attribute 'healthStatus', 'enum', ['offline', 'online', 'unknown']
        attribute 'batteryVoltage', 'number'
        attribute 'lastBattery', 'date'         // last battery event time - added in 1.2.0 05/22/2024

        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0004,0005,EF00', outClusters: '0019,000A', model: 'TS0601', manufacturer: '_TZE200_nvups4nh', deviceJoinName: 'Tuya Contact and T/H Sensor'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0500,0000', outClusters: '0019,000A', model: 'TS0601', manufacturer: '_TZE200_pay2byax', deviceJoinName: 'Tuya Contact and Illuminance Sensor'
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0500,0000', outClusters: '0019,000A', model: 'TS0601', manufacturer: '_TZE200_n8dljorx', deviceJoinName: 'Tuya Contact and Illuminance Sensor'                           // Model ZG-102ZL
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_26fmupbb', deviceJoinName: 'Tuya Contact Sensor'        // KK; https://community.hubitat.com/t/release-tuya-zigbee-multi-sensor-4-in-1-pir-motion-sensors-and-mmwave-presence-radars-w-healthstatus/92441/30?u=kkossev
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_n2egfsli', deviceJoinName: 'Tuya Contact Sensor'        // https://community.hubitat.com/t/tuya-zigbee-door-contact/95698/5?u=kkossev
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_oxslv1c9', deviceJoinName: 'Tuya Contact Sensor'        // Model iH-F001
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_2mbfxlzr', deviceJoinName: 'Tuya Contact Sensor'        // https://community.hubitat.com/t/tuya-zigbee-contact-sensor/82854/25?u=kkossev
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_402jjyro', deviceJoinName: 'Tuya Contact Sensor'        //
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_7d8yme6f', deviceJoinName: 'Tuya Contact Sensor'        // + tamper? check
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_psqjayrd', deviceJoinName: 'Tuya Contact Sensor'        // + tamper
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_ebar6ljy', deviceJoinName: 'Tuya Contact Sensor'        // + tamper
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TYZB01_xph99wvr', deviceJoinName: 'Tuya Contact Sensor'        // Model ZM-CG205
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TYZB01_ncdapbwy', deviceJoinName: 'Tuya Contact Sensor'        // Model ZM-CG205
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0001,0003,0500,0000', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'TS0203', manufacturer: '_TZ3000_fab7r7mc', deviceJoinName: 'Tuya Contact Sensor'        // +tamper Model GD-D-Z
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,000A,0001,0500', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'RH3001', manufacturer: 'TUYATEC-nznq0233', deviceJoinName: 'BlitzWolf Contact Sensor'   // Model SNTZ007
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,000A,0001,0500', outClusters: '0003,0004,0005,0006,0008,1000,0019,000A', model: 'RH3001', manufacturer: 'TUYATEC-trhrga6p', deviceJoinName: 'BlitzWolf Contact Sensor'   // Model BW-IS2
        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,000A,0001,0500', outClusters: '0019', model: 'RH3001', manufacturer: 'TUYATEC-0l6xaqmi', deviceJoinName: 'BlitzWolf Contact Sensor'   // KK

        fingerprint profileId: '0104', endpointId: '01', inClusters: '0000,0003,0500,0001', outClusters: '0003', model: 'DS01', manufacturer: 'eWeLink', deviceJoinName: 'Sonoff Contact Sensor'
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,FF01,FF00,0001,0500", outClusters: "0019", model: "3RDS17BZ", manufacturer: "Third Reality, Inc", deviceJoinName: "Third Reality Contact Sensor" 
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0001,0003,0020,0402,0500,0B05", outClusters: "0019", model: "URC4460BC0-X-R", manufacturer: "Universal Electronics Inc", deviceJoinName: 'Xfinity/Visonic MCT-350 Zigbee Contact Sensor'   
    }
    preferences {
        input(name: 'txtEnable', type: 'bool', title: '<b>Description text logging</b>', description: '<i>Display measured values in HE log page. Recommended value is <b>true</b></i>', defaultValue: true)
        input(name: 'logEnable', type: 'bool', title: '<b>Debug logging</b>', description: '<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>', defaultValue: true)
        if (isConfigurable()) {
            input(title: 'To configure a sleepy device, try any of the methods below :', description: '<b>* Change open/closed state<br> * Remove the battery for at least 1 minute<br> * Pair the device again to HE</b>', type: 'paragraph', element: 'paragraph')
            /*
            def model = getModelGroup()
            def modelProperties = deviceProfiles["$model"] as Map

            if (modelProperties != null) {
                def preferences =  modelProperties.find{it.key=="preferences"}
                if (preferences != null && preferences != []) {
                    preferences.value.each { key, value ->
                        def strMap = value as Map
                    //                        input ("${strMap.name}", "${strMap.type}", title: "<b>${strMap.title}</b>", description: "<i>${strMap.description}</i>", range: "${strMap.range}", defaultValue: "${strMap.defaultValue}")
                    }
                }
            }
            */
        }
        input(name: 'advancedOptions', type: 'bool', title: '<b>Advanced options</b>', defaultValue: false)
        if (advancedOptions == true) {
            input(name: 'offlineThreshold', type: 'number', title: '<b>HealthCheck Offline Threshold</b>', description: '<i>HealthCheck Offline Threshold, hours.<br> Zero value disables the Healtch Check</i>', range:'0..24', defaultValue: presenceCountDefaultThreshold)
            if (isBatteryConfigurable()) {
                input name: 'batteryReporting', type: 'enum', title: '<b>Battery Reporting Interval</b>', options: batteryReportingOptions.options, defaultValue: batteryReportingOptions.defaultValue, description: \
                    '<i>Keep the battery reporting interval to <b>Default</b>, except when battery level is not reported at all for a long period.</i>'
            }
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>'
            input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between non-contact reports</b>', description: '<i>Minimum time between non-contact reporting (humidity, illuminance), seconds</i>', defaultValue: 10, range: '1..3600'
            input name: 'pollContactStatus', type: 'bool', title: '<b>Poll Contact Status</b>', description: '<i>Poll the contact status when the device is awake</i>', defaultValue: false
            input name: 'pollBatteryStatus', type: 'bool', title: '<b>Poll Battery Status</b>', description: '<i>Poll the battery status when the device is awake</i>', defaultValue: false
        }
    }
}

@Field static final Map batteryReportingOptions = [
    defaultValue: 00,
    options     : [00: 'Default', 7200: 'Every 2 Hours', 14400: 'Every 4 Hours', 28800: 'Every 8 Hours', 43200: 'Every 12 Hours', 86400: 'Every 24 Hours']
]

@Field static final Map deviceProfiles = [
    'TS0203_CONTACT_BATT'          : [     // https://community.hubitat.com/t/i-need-help-with-tuya-contact-sensor-ts0203-white-label-ih-f001/110946/1
        model     : 'TS0203',      // default battery reporting period = 4 hours
        manufacturers : ['_TZ3000_26fmupbb', '_TZ3000_n2egfsli', '_TZ3000_oxslv1c9', '_TZ3000_2mbfxlzr', '_TZ3000_402jjyro', '_TZ3000_7d8yme6f', '_TZ3000_psqjayrd', '_TZ3000_ebar6ljy', '_TYZB01_xph99wvr',
                '_TYZB01_ncdapbwy', '_TZ3000_fab7r7mc', 'TUYATEC-nznq0233'],
        deviceJoinName: 'Tuya Zigbee Contact Sensor',
        inClusters    : '0001,0003,0500,0000',
        outClusters   : '0003,0004,0005,0006,0008,1000,0019,000A',
        capabilities  : ['contactSensor': true, 'battery': true],    // capabilities  : not used
        configuration : ['battery': true],                           // configuration : use in updated()
        attributes    : ['healthStatus'],                            // attributes    : not used
        preferences   : [],                                          // preferences   : used in the Preferences section
        batteries     : 'unknown'
    ],
    'TS0203_UNKNOWN'      : [
        model         : 'TS0203',
        manufacturers : [],
        deviceJoinName: 'Tuya TS0203 Sensor',
        capabilities  : ['contactSensor': true, 'battery': true],
        configuration : ['battery': true],
        attributes    : ['healthStatus'],
        batteries     : 'unknown'
    ],
    'TS0601_CONTACT_ILLUM_BATT'    : [
        model         : 'TS0601',
        manufacturers : ['_TZE200_pay2byax', '_TZE200_n8dljorx'],
        deviceJoinName: 'Tuya Zigbee Contact w/ Illuminance Sensor',
        capabilities  : ['contactSensor': true, 'IlluminanceMeasurement': true, 'battery': true],
        configuration : ['battery': false],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : 'unknown'
    ],
    'TS0601_CONTACT_TEMP_HUMI_BATT': [     // https://community.hubitat.com/t/generic-tuya-contact-temp-zigbee-device/112357        @Pr0z4k
        // https://www.aliexpress.com/item/1005004878609097.html
        model         : 'TS0601',
        manufacturers : ['_TZE200_nvups4nh'],
        deviceJoinName: 'Tuya Zigbee Contact Sensor w/ Temperature&Humidity',
        inClusters    : '0000,0001,0500,EF00',
        outClusters   : '0019,000A',
        capabilities  : ['contactSensor': true, 'temperatureMeasurement': true, 'RelativeHumidityMeasurement': true, 'battery': true],
        configuration : ['battery': false],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : '2xAAA'
    ],
    'TS0601_UNKNOWN'      : [
        model         : 'TS0601',
        manufacturers : [],
        deviceJoinName: 'Tuya TS0601 Sensor',
        capabilities  : ['contactSensor': true, 'battery': true],
        attributes    : ['healthStatus'],
        batteries     : 'unknown'
    ],
    'BLITZWOLF_CONTACT_BATT' : [
        model         : 'RH3001',
        manufacturers : ['TUYATEC-trhrga6p', 'TUYATEC-nznq0233', 'TUYATEC-0l6xaqmi'],
        deviceJoinName: 'BlitzWolf Contact Sensor',
        inClusters    : '0000,000A,0001,0500',
        outClusters   : '0019',
        capabilities  : ['contactSensor': true, 'battery': true],
        configuration : ['battery': true],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : 'CR2032'
    ],
    'SONOFF_CONTACT_BATT' : [
        model         : 'DS01',
        manufacturers : ['eWeLink'],
        deviceJoinName: 'Sonoff Contact Sensor',
        inClusters    : '0000,0003,0500,0001',
        outClusters   : '0003',
        capabilities  : ['contactSensor': true, 'battery': true],
        configuration : ['battery': true, 'minReportingTime': true],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : 'CR2032'
    ],
    '3RDREALITY_CONTACT_BATT' : [
        model         : '3RDS17BZ',
        manufacturers : ['Third Reality, Inc'],
        deviceJoinName: 'Third Reality Contact Sensor',
        inClusters    : '0000,FF01,FF00,0001,0500',
        outClusters   : '0019',
        capabilities  : ['contactSensor': true, 'battery': true],
        configuration : ['battery': true],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : '2xAAA'
    ],
    'XFINITY_VISONIC_CONTACT_BATT' : [
        model         : 'URC4460BC0-X-R',
        manufacturers : ['Universal Electronics Inc"'],
        deviceJoinName: 'Xfinity/Visonic MCT-350 Zigbee Contact Sensor',
        inClusters    : '0000,0001,0003,0020,0402,0500,0B05"',
        outClusters   : '0019',
        capabilities  : ['contactSensor': true, 'battery': true],
        configuration : ['battery': true],
        attributes    : ['healthStatus'],
        preferences   : ['minReportingTime': true],
        batteries     : '2xAAA'
    ],

    'UNKNOWN'             : [
        model         : '',
        manufacturers : [],
        deviceJoinName: 'Unknown Sensor',
        capabilities  : ['contactSensor': true, 'battery': true],
        attributes    : ['healthStatus'],
        batteries     : 'unknown'
    ]
]

def getModelGroup()         { return (state.deviceProfile as String) ?: 'UNKNOWN' }
def isConfigurable(model)   { return (deviceProfiles["$model"]?.preferences != null && deviceProfiles["$model"]?.preferences != []) }
def isConfigurable()        { def model = getModelGroup(); return isConfigurable(model) }
def isBatteryConfigurable() { deviceProfiles[getModelGroup()]?.configuration?.battery?.value == true }

@Field static final Integer MaxRetries = 3
@Field static final Integer ConfigTimer = 15
@Field static final Integer presenceCountDefaultThreshold = 12    // 12 hours

private static getCLUSTER_TUYA() { 0xEF00 }
private static getSETDATA() { 0x00 }
private static getSETTIME() { 0x24 }

// Tuya Commands
private static getTUYA_REQUEST() { 0x00 }
private static getTUYA_REPORTING() { 0x01 }
private static getTUYA_QUERY() { 0x02 }
private static getTUYA_STATUS_SEARCH() { 0x06 }
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private static getDP_TYPE_RAW() { '01' }    // [ bytes ]
private static getDP_TYPE_BOOL() { '01' }    // [ 0/1 ]
private static getDP_TYPE_VALUE() { '02' }    // [ 4 byte value ]
private static getDP_TYPE_STRING() { '03' }    // [ N byte string ]
private static getDP_TYPE_ENUM() { '04' }    // [ 0-255 ]
private static getDP_TYPE_BITMAP() { '05' }    // [ 1,2,4 bytes ] as bits

// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    setHealthStatusOnline()
    Map statsMap = stringToJsonMap(state.stats)
    Map descMap = [:]
    try { statsMap['rxCtr']++ } catch (e) { statsMap['rxCtr'] = 0 }; state.stats = mapToJsonString(statsMap)
    descMap = zigbee.parseDescriptionAsMap(description)
    logDebug "parse() description=$description "
//    /*try{*/ logDebug "parse() description=$description /*descMap = ${zigbee.parseDescriptionAsMap(description)}*/ " // } catch (e) {logWarn "exception catched when procesing description ${description}"}

    if (description?.startsWith('zone status') || description?.startsWith('zone report')) {
        logDebug "Zone status: $description"
        parseIasMessage(description)    // TS0203 contact sensors
    }
    else if (description?.startsWith('enroll request')) {
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device.
           It  must do this immediately it has joined the network  (during commissioning). */
        logInfo 'Sending IAS enroll response...'
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "sending enroll response: ${cmds}"
        sendZigbeeCommands(cmds)
    }
    else if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        try {
            descMap = zigbee.parseDescriptionAsMap(description)
        }
        catch (e) {
            logWarn "exception ${e} catched when procesing description ${description}"
        }
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                sendBatteryPercentageEvent(Integer.parseInt(descMap.value, 16))
            }
            else if (descMap.attrInt == 0x0020) {
                sendBatteryVoltageEvent(Integer.parseInt(descMap.value, 16))
                if ((settings.voltageToPercent ?: false) == true) {
                    sendBatteryVoltageEvent(Integer.parseInt(descMap.value, 16), convertToPercent=true)
                }
            }
            else {
                log.warn "unparesed attrint $descMap.attrInt"
            }
        } else if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response
            //if (settings?.logEnable) log.debug "${device.displayName} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
            if (descMap?.attrId == '0000') {
                if (settings?.logEnable) { log.debug "${device.displayName} Zone State repot ignored value= ${Integer.parseInt(descMap?.value, 16)}" }
            }
            else if (descMap?.attrId == '0002') {
                if (settings?.logEnable) { log.debug "${device.displayName} Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}" }
                sendContactEvent(Integer.parseInt(descMap?.value, 16))
            }
            else if (descMap?.attrId == '000B') {
                if (settings?.logEnable) { log.debug "${device.displayName} IAS Zone ID: ${descMap.value}" }
            }
            else if (descMap?.attrId == '0013') {
                // [raw:7CC50105000813002002, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:0013, encoding:20, command:0A, value:02, clusterInt:1280, attrInt:19]
                def value = Integer.parseInt(descMap?.value, 16)
                def str = getSensitivityString(value)
                if (settings?.txtEnable) { log.info "${device.displayName} Current Zone Sensitivity Level = ${str} (${value})" }
                device.updateSetting('sensitivity', [value: str, type: 'enum'])
            }
            else if (descMap?.attrId == 'F001') {
                // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441]
                def value = Integer.parseInt(descMap?.value, 16)
                def str = getKeepTimeString(value)
                if (settings?.txtEnable) { log.info "${device.displayName} Current Zone Keep-Time =  ${str} (${value})" }
                //log.trace "str = ${str}"
                device.updateSetting('keepTime', [value: str, type: 'enum'])
            }
            else {
                if (settings?.logEnable) { log.warn "${device.displayName} Zone status: NOT PROCESSED ${descMap}" }
            }
        } // if IAS read attribute response
        else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS)
            if (settings?.logEnable) { log.debug "${device.displayName} IAS enroll write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}" }
        }
        else if (descMap.cluster == '0400' && descMap.attrId == '0000') {
            def rawLux = Integer.parseInt(descMap.value, 16)
            illuminanceEventLux(rawLux)
        }
        else if (descMap.cluster == '0400' && descMap.attrId == 'F001') {
            def raw = Integer.parseInt(descMap.value, 16)
            if (settings?.txtEnable) { log.info "${device.displayName} illuminance sensitivity is ${raw} Lux" }
            device.updateSetting('illuminanceSensitivity', [value: raw, type: 'number'])
        }
        else if (descMap.cluster == '0402' && descMap.attrId == '0000') {
            def raw = Integer.parseInt(descMap.value, 16)
            if (raw > 32767) {
                //Here we deal with negative values
                raw = raw - 65536
            }
            temperatureEvent(raw / 100.0)
        }
        else if (descMap.cluster == '0405' && descMap.attrId == '0000') {
            def raw = Integer.parseInt(descMap.value, 16)
            humidityEvent(raw / 100.0)
        }
        else if (descMap.cluster == '0406' && descMap.attrId == '0000') {    // OWON, SiHAS
            def raw = Integer.parseInt(descMap.value, 16)
            motionEvent(raw & 0x01)
        }
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster(descMap)
        }
        else if (descMap?.clusterId == '0013') {    // device announcement, profileId:0000
            logInfo 'device announcement'
            statsMap['rejoins'] = (statsMap['rejoins'] ?: 0) + 1
            state.stats = mapToJsonString(statsMap)
        }
        else if (descMap.isClusterSpecific == false && descMap.command == '01') {
            //global commands read attribute response
            def status = descMap.data[2]
            if (status == '86') {
                if (settings?.logEnable) { log.warn "${device.displayName} Cluster ${descMap.clusterId} read attribute - NOT SUPPORTED!\r ${descMap}" }
            } else {
                if (settings?.logEnable) { log.warn "${device.displayName} <b>UNPROCESSED Global Command</b> :  ${descMap}" }
            }
        }
        else if (descMap.profileId == '0000') { //zdo
            parseZDOcommand(descMap)
        }
        else if (descMap.clusterId != null && descMap.profileId == '0104') { // ZHA global command
            parseZHAcommand(descMap)
        }
        else {
            if (descMap != [:]) {
                logDebug "<b> NOT PARSED </b> :  ${descMap}"
            }
        }
    } // if 'catchall:' or 'read attr -'
    else {
        if (settings?.logEnable) { log.debug "${device.displayName} <b> UNPROCESSED </b> parse() descMap = ${zigbee.parseDescriptionAsMap(description)}" }
    }
    //
    if (isPendingConfig()) {
        ConfigurationStateMachine()
    }
    if (settings?.pollContactStatus == true) {          // added 10/19/2023
        Map lastTxMap = stringToJsonMap(state.lastTx)
        //try {logDebug "now() - lastTxMap?.contactPoll = ${(now() - lastTxMap?.contactPoll)}"} catch (e) {logDebug "exception catched when procesing now() - lastTxMap?.contactPoll"}
        if (lastTxMap?.contactPoll == null || (lastTxMap?.contactPoll != null && (now() - lastTxMap?.contactPoll) > 60000)) {   // last poll was more than 60 seconds ago
            pollContactStatus()
        }
    }
    if (settings?.pollBatteryStatus == true) {          // added 5/23/2024
        String lastBatteryString = device.currentValue('lastBattery') as String
        long lastBatteryUnixTime = formattedDate2unix(lastBatteryString)
        int diff = (now() - lastBatteryUnixTime) / 1000
        //logDebug "lastBatteryString = ${lastBatteryString} lastBatteryUnixTime = ${lastBatteryUnixTime}  now() = ${now()} diff = ${diff} seconds"
        //logDebug "offlineThreshold = ${settings.offlineThreshold * 3600} seconds"
        if (lastBatteryString == null || (lastBatteryString != null && diff > settings.offlineThreshold * 3600)) {   // lastBattery was received more than offlineThreshold hours ago
            pollBatteryStatus()
        }
    }
}


long formattedDate2unix(String formattedDate) {
    try {
        if (formattedDate == null) { return null }
        //Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate)
        Date date = Date.parse('EEE MMM dd HH:mm:ss zzz yyyy', formattedDate)
        return date.getTime()
    } catch (e) {
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead."
        return now()
    }
}


def parseZHAcommand(Map descMap) {
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map lastTxMap = stringToJsonMap(state.lastTx)
    switch (descMap.command) {
        case '01': //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0]
            if (status == '86') {
                if (logEnable == true) { log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${clusterId}" }
            }
            else {
                if (logEnable == true) { log.debug "${device.displayName} Read attribute response: status code ${status} Attributte ${attrId} cluster ${descMap.clusterId}" }
            }
            break
        case '04': //write attribute response
            if (logEnable == true) { log.info "${device.displayName} Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '07': // Configure Reporting Response
            if (logEnable == true) { log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
            // Status: Unreportable Attribute (0x8c)
            break
        case '09': // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            def attr = zigbee.convertHexToInt(descMap.data[3]) * 256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7])
                def delta = 0
                if (descMap.data.size() == 11) {
                    delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9])
                }
                else if (descMap.data.size() == 10) {
                    delta = zigbee.convertHexToInt(descMap.data[9])
                }
                else {
                    if (logEnable == true) { log.debug "${device.displayName} descMap.data.size = ${descMap.data.size()}" }
                }
                logDebug "Received Read Reporting Configuration response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}"
                String attributeName
                if (descMap.clusterId == '0405') {
                    attributeName = 'humidity'
                    lastRxMap.humiCfg = min.toString() + ',' + max.toString() + ',' + delta.toString()
                    if (lastRxMap.humiCfg == lastTxMap.humiCfg) {
                        lastTxMap.humiCfgOK = true
                    }
                }
                else if (descMap.clusterId == '0402') {
                    attributeName = 'temperature'
                    lastRxMap.tempCfg = min.toString() + ',' + max.toString() + ',' + delta.toString()
                    if (lastRxMap.tempCfg == lastTxMap.tempCfg) {
                        lastTxMap.tempCfgOK = true
                    }
                }
                else if (descMap.clusterId == '0001') {
                    attributeName = 'battery %'
                    lastRxMap.battCfg = min.toString() + ',' + max.toString() + ',' + delta.toString()
                    if (lastRxMap.battCfg == lastTxMap.battCfg) {
                        lastTxMap.battCfgOK = true
                    }
                }
                else {
                    attributeName = descMap.clusterId
                }
                if ((lastTxMap.humiCfgOK != null ? lastTxMap.humiCfgOK : true) && (lastTxMap.tempCfgOK != null ? lastTxMap.tempCfgOK : true) && (lastTxMap.battCfgOK != null ? lastTxMap.battCfgOK : true)) {
                    logDebug 'all parameters configured!'
                }
                if (txtEnable == true) {
                    log.info "${device.displayName} Reporting Configuration Response for ${attributeName}  (status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) is: min=${min} max=${max} delta=${delta}"
                }
            } else {    // failure
                if (logEnable == true) { log.info "${device.displayName} <b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" }
                // changed 05/22/2024 - in case of configuration failure, do NOT try to configure it again!
                lastTxMap.battCfgOK = true
            }
            break
        case '0B': // ZCL Default Response
            def status = descMap.data[1]
            if (status != '00') {
                if (logEnable == true) { log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            }
            break
        default:
            if (logEnable == true) { log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break
    }
    state.lastRx = mapToJsonString(lastRxMap)
    state.lastTx = mapToJsonString(lastTxMap)
}

def parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        case '0006':
            if (logEnable) { log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" }
            break
        case '0013': // device announcement
            if (logEnable) { log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" }
            break
        case '8004': // simple descriptor response
            if (logEnable) { log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" }
            //parseSimpleDescriptorResponse( descMap )
            break
        case '8005': // endpoint response
            if (logEnable) { log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${descMap.data[4]}  endpointList = ${descMap.data[5]}" }
            break
        case '8021': // bind response
            if (logEnable) { log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8022': // unbind response
            if (logEnable) { log.info "${device.displayName} Received unbind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" }
            break
        case '8034': // leave response
            if (logEnable) { log.info "${device.displayName} Received leave response, data=${descMap.data}" }
            break
        case '8038': // Management Network Update Notify
            if (logEnable) { log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}" }
            break
        default:
            if (logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" }
            break    // 2022/09/16
    }
}

def processTuyaCluster(descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME
        if (settings?.logEnable) { log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}" }
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
        //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch (e) {
            if (settings?.logEnable) { log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" }
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int) (now() / 1000), 8) + zigbee.convertToHexString((int) ((now() + offset) / 1000), 8))
        // TODO : send raw command without 'need confirmation' frame control !
        if (settings?.logEnable) { log.trace "${device.displayName} now is: ${now()}" }
        // KK TODO - convert to Date/Time string!
        if (settings?.logEnable) { log.debug "${device.displayName} sending time data : ${cmds}" }
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]
        logDebug "Tuya cluster confirmation for command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != '00') {
            if (settings?.logEnable) { log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" }
        }
    }
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02')) {
        def dataLen = descMap?.data.size()
        def transid = zigbee.convertHexToInt(descMap?.data[1])                 // "transid" is just a "counter", a response will have the same transid as the command
        for (int i = 0; i < (dataLen - 4);) {
            def dp = zigbee.convertHexToInt(descMap?.data[2 + i])              // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])           // "dp_identifier" is device dependant
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i])
            def fncmd = getTuyaAttributeValue(descMap?.data, i)                //
            //if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})"
            processTuyaDP(descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4;
        }
    } // if (descMap?.command == "01" || descMap?.command == "02")
}

def processTuyaDP(descMap, dp, dp_id, fncmd) {
    switch (dp) {
        case 0x01: // contact 1=open 0=closed
            logDebug "(dp=$dp) contact event fncmd = ${fncmd}"
            sendContactEvent(contactActive = fncmd)
            break
        case 0x02: // 'TS0601_Contact' battery %
            logDebug "(dp=$dp) battery event fncmd = ${fncmd}"
            sendBatteryPercentageEvent(fncmd * 2)
            break
        case 0x07: // Temperature
            logDebug "(dp=$dp) temperature event fncmd = ${fncmd}"
            if (fncmd > 32767) {
                fncmd = fncmd - 65536
            }
            temperatureEvent(fncmd / 10.0)
            break
        case 0x08: // humidity
            logDebug "(dp=$dp) humidity event fncmd = ${fncmd}"
            humidityEvent(fncmd)
            break
        case 0x0C : // (12)
            logDebug "(dp=$dp) illuminance event fncmd = ${fncmd}"
            illuminanceEventLux( fncmd )
            break
        case 0x65 :    // (101)
            logDebug "(dp=$dp) illuminance event fncmd = ${fncmd}"
            illuminanceEventLux(fncmd) // illuminance for TS0601 ContactSensor with LUX
            break
        case 0x66 :     // (102)
            logDebug "(dp=$dp) battery event fncmd = ${fncmd}"
            handleTuyaBatteryLevel( fncmd )
            break
        default:
            if (settings?.logEnable) { log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" }
            break
    }
}

private int getTuyaAttributeValue(ArrayList _data, index) {
    int retValue = 0
    if (_data.size() >= 6) {
        int dataLength = _data[5 + index] as Integer
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5])
            power = power * 256
        }
    }
    return retValue
}

void parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        //if (settings?.logEnable) log.trace "zs = $zs"
        if (zs.alarm1Set == true) {
            sendContactEvent(contactActive = true)
        } else {
            sendContactEvent(contactActive = false)
        }
    }
    catch (e) {
        log.error "${device.displayName} This driver requires HE version 2.2.7 (May 2021) or newer!"
        return
    }
}

void setOpen() {
    checkDriverVersion()
    sendContactEvent(contactActive = true, isDigital = true)
}

void setClosed() {
    checkDriverVersion()
    sendContactEvent(contactActive = false, isDigital = true)
}

void sendContactEvent(contactActive, isDigital = false) {
    String descriptionText = 'contact is ' + (contactActive  ? 'open' : 'closed')
    if (txtEnable) { log.info "${device.displayName} ${descriptionText}" }
    Map statsMap = stringToJsonMap(state.stats);
    // if contact is changed and contactPoll time is less than 10 seconds ago, increment the stats.outOfSync counter
    if ((contactActive ? 'open' : 'closed') != device.currentValue('contact')) {
        if (now() - (statsMap['contactPoll'] ?: now() ) < 10000) {
            try {statsMap['outOfSync']++} catch (e) {statsMap['outOfSync'] = 1; }
        }
    }
    sendEvent(
            name: 'contact',
            value: contactActive ? 'open' : 'closed',
            //isStateChange   : true,
            type: isDigital == true ? 'digital' : 'physical',
            descriptionText: descriptionText
    )
    state.stats = mapToJsonString(statsMap)
}

void temperatureEvent(temperature, isDigital = false) {
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map map = [:]
    map.name = 'temperature'
    if (location.temperatureScale == 'F') {
        temperature = (temperature * 1.8) + 32
        map.unit = '\u00B0F'
    }
    else {
        map.unit = '\u00B0C'
    }
    def tempCorrected = temperature + safeToDouble(settings?.temperatureOffset)
    map.value = Math.round((tempCorrected - 0.05) * 10) / 10
    map.type = isDigital == true ? 'digital' : 'physical'
    //map.isStateChange = true
    map.descriptionText = "${map.name} is ${tempCorrected} ${map.unit}"
    def timeElapsed = Math.round((now() - (lastRxMap['tempTime'] ?: now() - (minReportingTime * 2000))) / 1000)
    Integer timeRamaining = (minReportingTime - timeElapsed) as Integer
    if (timeElapsed >= minReportingTime) {
        if (settings?.txtEnable) {
            log.info "${device.displayName} ${map.descriptionText}"
        }
        unschedule('sendDelayedEventTemp')        //get rid of stale queued reports
        lastRxMap['tempTime'] = now()
        sendEvent(map)
    }
    else {         // queue the event
        map.type = 'delayed'
        if (settings?.logEnable) { log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}" }
        runIn(timeRamaining, 'sendDelayedEventTemp', [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventTemp(Map map) {
    logInfo "${map.descriptionText} (${map.type})"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    lastRxMap['tempTime'] = now()
    state.lastRx = mapToJsonString(lastRxMap)
    sendEvent(map)
}

void humidityEvent(humidity, boolean isDigital = false) {
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map map = [:]
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset)
    humidityAsDouble = humidityAsDouble < 0.0 ? 0.0 : humidityAsDouble > 100.0 ? 100.0 : humidityAsDouble
    map.value = Math.round(humidityAsDouble)
    map.name = 'humidity'
    map.unit = '% RH'
    map.type = isDigital == true ? 'digital' : 'physical'
    //map.isStateChange = true
    map.descriptionText = "${map.name} is ${humidityAsDouble.round(1)} ${map.unit}"
    int timeElapsed = Math.round((now() - (lastRxMap['humiTime'] ?: now() - (minReportingTime * 2000))) / 1000)
    Integer timeRamaining = (minReportingTime - timeElapsed) as Integer
    if (timeElapsed >= minReportingTime) {
        if (settings?.txtEnable) {
            log.info "${device.displayName} ${map.descriptionText}"
        }
        unschedule('sendDelayedEventHumi')
        lastRxMap['humiTime'] = now()
        sendEvent(map)
    }
    else {         // queue the event
        map.type = 'delayed'
        if (settings?.logEnable) { log.debug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${map}" }
        runIn(timeRamaining, 'sendDelayedEventHumi', [overwrite: true, data: map])
    }
    state.lastRx = mapToJsonString(lastRxMap)
}

private void sendDelayedEventHumi(Map map) {
    logInfo "${map.descriptionText} (${map.type})"
    Map lastRxMap = stringToJsonMap(state.lastRx)
    lastRxMap['humiTime'] = now()
    state.lastRx = mapToJsonString(lastRxMap)
    sendEvent(map)
}

void switchEvent(value) {
    Map map = [:]
    map.name = 'switch'
    map.value = value
    map.descriptionText = "${device.displayName} switch is ${value}"
    if (settings?.txtEnable) {
        log.info "${map.descriptionText}"
    }
    sendEvent(map)
}

void motionEvent(value) {
    Map map = [:]
    map.name = 'motion'
    map.value = value ? 'active' : 'inactive'
    map.descriptionText = "${device.displayName} motion is ${map.value}"
    if (settings?.txtEnable) {
        log.info "${map.descriptionText}"
    }
    sendEvent(map)
}

void illuminanceEvent(int illuminance, boolean isDigital = false) {
    Integer lux = illuminance > 0 ? Math.round(Math.pow(10, (illuminance / 10000))) : 0
    sendEvent('name': 'illuminance', 'value': lux, 'type': isDigital == true ? 'digital' : 'physical', 'unit': 'lx')
    logInfo "illuminance is ${lux} Lux"
}

void illuminanceEventLux(Integer lux, boolean isDigital = false) {
    sendEvent('name': 'illuminance', 'value': lux, 'type': isDigital == true ? 'digital' : 'physical', 'unit': 'lx')
    logInfo "illuminance is ${lux} Lux"
}

//  called from initialize() and when installed as a new device
void installed() {
    sendEvent(name: 'Info', value: 'installed', isStateChange: true)
    if (settings?.txtEnable) { log.info "${device.displayName} installed()..." }
    unschedule()
    initializeVars(fullInit = true)
}

//
void updated() {
    ArrayList<String> cmds = []
    Map lastRxMap = stringToJsonMap(state.lastRx)
    Map lastTxMap = stringToJsonMap(state.lastTx)
    checkDriverVersion()
    if (settings?.txtEnable) { log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>, deviceProfile = ${getModelGroup()}" }
    if (settings?.txtEnable) { log.info "${device.displayName} Debug logging is ${logEnable}; Description text logging is ${txtEnable}" }
    if (logEnable == true) {
        runIn(86400, 'logsOff', [overwrite: true, misfire: 'ignore'])    // turn off debug logging after 30 minutes
        if (settings?.txtEnable) { log.info "${device.displayName} Debug logging will be turned off after 24 hours" }
    }
    else {
        unschedule('logsOff')
    }
    scheduleDeviceHealthCheck()

    if (isBatteryConfigurable()) {
        int batteryReportinginterval = (settings.batteryReporting as Integer) ?: 0
        //logDebug "settings?.batteryReporting = ${settings?.batteryReporting as int} batteryReportinginterval=${batteryReportinginterval}"
        if (batteryReportinginterval > 0) {
            String newBattCfg = '3600' + ',' + batteryReportinginterval.toString() + ',' + '1'
            if (lastTxMap.battCfg == null || (lastTxMap.battCfg != lastRxMap.battCfg) || (lastTxMap.battCfg != newBattCfg ) ) {
                lastTxMap.battCfg = newBattCfg
                logDebug "lastTxMap.battCfg = ${lastTxMap.battCfg}"
                cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 3600, batteryReportinginterval as int, 1  /*0*/, [:], 101)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
                cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 3600, batteryReportinginterval as int, 1  /*0*/, [:], 102)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected
                cmds += zigbee.reportingConfiguration(0x0001, 0x0020, [:], 103)
                cmds += zigbee.reportingConfiguration(0x0001, 0x0021, [:], 104)
                log.info "configure battery reporting (${lastTxMap.battCfg}) pending ..."
                lastTxMap.battCfgOK = false
            } else {
                logDebug "Battery reporting already configured (${lastRxMap.battCfg} == ${lastTxMap.battCfg}), skipping ..."
                lastTxMap.battCfgOK = true
            }
        }
        else {
            logDebug 'no battery reporting configuration'
        }
    } // SONOFF

    state.lastTx = mapToJsonString(lastTxMap)

    int pendingConfig = 0
    pendingConfig += lastTxMap.tempCfgOK != null ? (lastTxMap.tempCfgOK == true ?  0 : 1) : 0
    pendingConfig += lastTxMap.humiCfgOK != null ? (lastTxMap.humiCfgOK == true ?  0 : 1) : 0
    pendingConfig += lastTxMap.battCfgOK != null ? (lastTxMap.battCfgOK == true ?  0 : 1) : 0
    if (isConfigurable()) {
        logInfo "pending ${pendingConfig} reporting configurations"
        if (pendingConfig != 0) {
            updateInfo("Pending ${pendingConfig} configuration(s). Wake up the device!")
        }
    }

    if (cmds != []) {
        sendZigbeeCommands(cmds)
    } else {
        logDebug "nothing to send to the device (${getModelGroup()})"
    }
}

boolean isPendingConfig() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    if ((lastTxMap.tempCfgOK != null && lastTxMap.tempCfgOK == false) || (lastTxMap.humiCfgOK != null && lastTxMap.humiCfgOK == false) || (lastTxMap.battCfgOK != null && lastTxMap.battCfgOK == false)) {
        return true
    } else {
        return false
    }
}

// called from parse() when any packet is received from the awaken device ...
void ConfigurationStateMachine() {
    if (!isConfigurable()) {
        return
    }
    Map lastTxMap = stringToJsonMap(state.lastTx)
    def configState = state.configState
    logDebug "ConfigurationStateMachine configState = ${configState}"
    switch (configState) {
        case 0: // idle
            if (isPendingConfig()) {
                logDebug 'configuration pending ...'
                updateInfo('sending the reporting configuration...')
                lastTxMap.cfgTimer = ConfigTimer
                updated()
                runIn(1, 'configTimer', [overwrite: true, misfire: 'ignore'])
                configState = 1
            } else {
                logWarn 'ConfigurationStateMachine called without isPendingConfig?'
                unschedule('configTimer')
            }
            break
        case 1: // waiting 10 seconds for acknowledge from the device // TODO - process config ERRORS !!!
            if (!isPendingConfig()) {
                updateInfo('configured')
                lastTxMap.cfgTimer = 0
                configState = 0
                unschedule('configTimer')
            } else if (lastTxMap.cfgTimer == null || lastTxMap.cfgTimer == 0) {    // timeout
                updateInfo('Timeout when waiting for configuration result confirmation!')
                lastTxMap.cfgTimer = 0
                unschedule('configTimer')
                configState = 0    // try again next time a packet is received from the device..
            } else {
                logDebug "config confirmation still pending ... lastTxMap.cfgTimer is ${lastTxMap.cfgTimer}"
            }
            break
        default:
            logWarn "ConfigurationStateMachine() unknown state ${configState}"
            unschedule('configTimer')
            configState = 0
            break
    }
    state.configState = configState
    state.lastTx = mapToJsonString(lastTxMap)
}

// started from ConfigurationStateMachine
void configTimer() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    logDebug 'configTimer() callled'
    if (lastTxMap.cfgTimer != null) {
        if (!isPendingConfig()) {
            logDebug 'configuration is successful! '
            ConfigurationStateMachine()
        } else {
            lastTxMap.cfgTimer = lastTxMap.cfgTimer - 1
            if (lastTxMap.cfgTimer >= 0) {
                state.lastTx = mapToJsonString(lastTxMap)    // flush the timer!
                ConfigurationStateMachine()
                runIn(1, 'configTimer' /*, [overwrite: true, misfire: "ignore"]*/)
                logDebug "scheduling again configTimer = ${lastTxMap.cfgTimer}"
            } else {
                logDebug 'configTimer expired! Do not restart it.'
            }
        }
    } else {
        lastTxMap.cfgTimer = 0
    }
    state.lastTx = mapToJsonString(lastTxMap)
}

void pollBatteryStatus() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x001, 0x0021, [:], delay = 200)
    cmds += zigbee.readAttribute(0x001, 0x0020, [:], delay = 100)
    sendZigbeeCommands(cmds)
    logDebug 'pollBatteryStatus() called'
    lastTxMap.batteryPoll = now()
    state.lastTx = mapToJsonString(lastTxMap)
}

void refresh() {
    checkDriverVersion()
    pollContactStatus()
    if (deviceProfiles[getModelGroup()]?.capabilities?.battery?.value == true) {
        pollBatteryStatus()
    } else {
        logInfo 'refresh() is not implemented for this sleepy Zigbee device'
    }
}

void pollContactStatus() {
    Map lastTxMap = stringToJsonMap(state.lastTx)
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0500, 0x0000, [:], delay = 200)
    sendZigbeeCommands(cmds)
    logDebug 'pollContactStatus() called'
    lastTxMap.contactPoll = now()
    state.lastTx = mapToJsonString(lastTxMap)
}

static String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars(fullInit = false)
        if (state.lastRx == null || state.stats == null || state.lastTx == null) {
            resetStats()
        }
        scheduleDeviceHealthCheck()
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void resetStats() {
    Map stats = [
            rxCtr  : 0,
            txCtr  : 0,
            rejoins: 0,
            outOfSync: 0
    ]
    Map lastRx = [
            battCfg : '-1,-1,-1'
    ]
    Map lastTx = [
            battCfgOK : true,
            cfgTimer : 0
    ]
    state.stats = mapToJsonString(stats)
    state.lastRx = mapToJsonString(lastRx)
    state.lastTx = mapToJsonString(lastTx)
    logInfo 'Statistics were reset. Press F5 to refresh the device page'
}

void logInitializeRezults() {
    if (settings?.txtEnable) { log.info "${device.displayName} manufacturer  = ${device.getDataValue('manufacturer')} ModelGroup = ${getModelGroup()}" }
    if (settings?.txtEnable) { log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})" }
}

// called from  initializeVars( fullInit = true)
void setDeviceName() {
    String deviceName = ''
    Map currentModelMap = [:]
    String deviceModel = device.getDataValue('model')
    String deviceManufacturer = device.getDataValue('manufacturer')
    deviceProfiles.each { profileName, profileMap ->
        if (profileMap.model == deviceModel) {
            if (deviceManufacturer in profileMap.manufacturers) {
                currentModelMap = profileMap
                state.deviceProfile = profileName
                deviceName = profileMap.deviceJoinName
                log.debug "FOUND! currentModelMap=${currentModelMap}, deviceName =${deviceName}"
            }
        }
    }

    if (currentModelMap == null || currentModelMap == [:]) {
        logWarn "unknown model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}"
        // don't change the device name when unknown
        state.deviceProfile = 'UNKNOWN'
    }
    if (deviceName != NULL && deviceName != '') {
        device.setName(deviceName)
        logInfo { "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} deviceName was set to ${deviceName}" }
    } else {
        logWarn { "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} was not found!" }
    }
}

// called by initialize() button
void initializeVars(boolean fullInit = true) {
    log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true) {
        state.clear()
        unschedule()
        resetStats()
        setDeviceName()
        state.comment = 'works with Tuya TS0601, TS0203, BlitzWolf, Sonoff, ThirdReality'
        log.info "${device.displayName} all states and scheduled jobs cleared!"
        state.driverVersion = driverVersionAndTimeStamp()
    }
    state.configState = 0    // reset the configuration state machine

    if (fullInit == true || settings?.logEnable == null) { device.updateSetting('logEnable', true) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit == true || settings?.temperatureOffset == null) { device.updateSetting('temperatureOffset', [value: 0.0, type: 'decimal']) }
    if (fullInit == true || settings?.humidityOffset == null) { device.updateSetting('humidityOffset', [value: 0.0, type: 'decimal']) }
    if (fullInit == true || settings?.temperatureSensitivity == null) { device.updateSetting('temperatureSensitivity', [value: 0.5, type: 'decimal']) }
    if (fullInit == true || settings?.humiditySensitivity == null) { device.updateSetting('humiditySensitivity', [value: 5, type: 'number']) }
    if (fullInit == true || settings?.illuminanceSensitivity == null) { device.updateSetting('illuminanceSensitivity', [value: 12, type: 'number']) }
    if (fullInit == true || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value: 10, type: 'number']) }
    if (fullInit == true || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value: 3600, type: 'number']) }
    if (fullInit == true || state.notPresentCounter == null) { state.notPresentCounter = 0 }
    if (fullInit == true || settings?.offlineThreshold == null) { device.updateSetting('offlineThreshold', [value: presenceCountDefaultThreshold, type: 'number']) }
    if (fullInit == true || settings?.batteryReporting == null) { device.updateSetting('batteryReporting', [value: batteryReportingOptions.defaultValue.toString(), type: 'enum']) }
    if (fullInit == true || settings?.pollContactStatus == null) { device.updateSetting('pollContactStatus', false) }
    if (fullInit == true || settings?.pollBatteryStatus == null) { device.updateSetting('pollBatteryStatus', false) }
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay = 200)
    return cmds
}

def configure() {
    if (settings?.txtEnable) { log.info "${device.displayName} configure().." }
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    sendZigbeeCommands(cmds)
    runIn(1, updated)
// send the default or previously configured preference parameters during the Zigbee pairing process..
}

// NOT called when the driver is initialized as a new device, because the Initialize capability is NOT declared!
def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars(fullInit = true)
    installed()
    configure()
    runIn(3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay = 200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int) (fncmd.length() / 2), 4) + fncmd)
    if (settings?.logEnable) { log.trace "${device.displayName} sendTuyaCommand = ${cmds}" }
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {
        log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"
    }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    Map statsMap = stringToJsonMap(state.stats); try {statsMap['txCtr']++} catch (e) {statsMap['txCtr'] = 0}; state.stats = mapToJsonString(statsMap)
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)
}

private getDescriptionText(msg) {
    String descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) { log.info "${descriptionText}" }
    return descriptionText
}

void logsOff() {
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

void sendBatteryPercentageEvent(rawValue) {
    //if (settings?.logEnable) log.debug "${device.displayName} Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    Map result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery percentage is ${result.value}%"
        result.descriptionText += " (${device.currentValue('contact')})"
        result.isStateChange = true    // enabled 10/22/2023
        result.unit = '%'
        result.type = 'physical'
        sendEvent(result)
        sendLastBatteryEvent()
    } else {
        if (settings?.logEnable) { log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})" }
    }
}

void handleTuyaBatteryLevel( fncmd ) {
    def rawValue = 0
    if (fncmd == 0) { rawValue = 100 }          // Battery Full
    else if (fncmd == 1) { rawValue = 75 }      // Battery High
    else if (fncmd == 2) { rawValue = 50 }      // Battery Medium
    else if (fncmd == 3) { rawValue = 25 }      // Battery Low
    else if (fncmd == 4) { rawValue = 100 }     // Tuya 3 in 1 -> USB powered
    else { rawValue = fncmd }
    sendBatteryPercentageEvent(rawValue * 2)
}

void sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) {
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V"
    Map result = [:]
    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.2
        def maxVolts = 3.2
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) roundedPct = 1
        if (roundedPct >100) roundedPct = 100
        if (convertToPercent == true) {
            result.value = Math.min(100, roundedPct)
            result.name = 'battery'
            result.unit  = '%'
            result.descriptionText = "battery is ${roundedPct} %"
        }
        else {
            result.value = volts
            result.name = 'batteryVoltage'
            result.unit  = 'V'
            result.descriptionText = "battery is ${volts} Volts"
        }
        result.descriptionText += " (${device.currentValue('contact')})"
        result.type = 'physical'
        result.isStateChange = true
        logInfo "${result.descriptionText}"
        sendEvent(result)
        sendLastBatteryEvent()        
    }
    else {
        logWarn "ignoring BatteryResult(${rawValue})"
    }
}

void sendLastBatteryEvent() {
    final Date lastBattery = new Date()
    sendEvent(name: 'lastBattery', value: lastBattery, descriptionText: "Last battery event at ${lastBattery}")
}

// called when any event was received from the Zigbee device in parse() method..
void setHealthStatusOnline() {
    if ((device.currentValue('healthStatus', true) ?: 'unknown') != 'online') {
        sendHealthStatusEvent('online')
        if (settings?.txtEnable) log.info "${device.displayName} is present"
    }
    state.notPresentCounter = 0
}

void deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > safeToInt(settings?.offlineThreshold, presenceCountDefaultThreshold)) {
        if ((device.currentValue('healthStatus', true) ?: 'unknown') != 'offline') {
            sendHealthStatusEvent('offline')
            if (settings?.txtEnable) { log.warn "${device.displayName} is not present!" }
        }
    } else {
        if (logEnable) { log.debug "${device.displayName} deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})" }
    }
}

void sendHealthStatusEvent(value) {
    sendEvent(name: 'healthStatus', value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    if (safeToInt(settings?.offlineThreshold, presenceCountDefaultThreshold) != 0) {
        schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/1 * * ? *", 'deviceHealthCheck')
        if (device.currentValue('healthStatus') == null) {
            sendHealthStatusEvent('unknown')
        }
    } else {
        logDebug 'unscheduling the healthCheck...'
        unschedule('deviceHealthCheck')
        if (device.currentValue('healthStatus') != null) {
            device.deleteCurrentState('healthStatus')
        }
    }
}

void ping() {
    logInfo 'ping() is not implemented'
}

String mapToJsonString(Map map) {
    if (map == null || map == [:]) { return '' }
    String str = JsonOutput.toJson(map)
    return str
}

Map stringToJsonMap(String str) {
    if (str == null || str == '') { return [:] }
    JsonSlurper jsonSlurper = new JsonSlurper()
    Map map = jsonSlurper.parseText(str)
    return map
}

Integer safeToInt(val, Integer defaultVal = 0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal = 0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void updateInfo(msg = ' ') {
    logInfo "$msg"
    sendEvent(name: 'Info', value: msg, isStateChange: false)
}

void zTest(dpCommand, dpValue, dpTypeString) {
    ArrayList<String> cmds = []
    def dpType = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" }
    sendZigbeeCommands(sendTuyaCommand(dpCommand, dpType, dpValHex))
}

void test(String description) {
    log.warn "test parsing : ${description}"
    parse( description)
}
