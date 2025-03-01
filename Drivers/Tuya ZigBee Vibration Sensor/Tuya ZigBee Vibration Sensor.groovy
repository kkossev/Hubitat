/* groovylint-disable CompileStatic */
/**
 *  Tuya ZigBee Vibration Sensor
 *  Device Driver for Hubitat Elevation hub
 *
 *  https://community.hubitat.com/t/tuya-vibration-sensor/75269
 *  
 *  Based on Mikhail Diatchenko (muxa) 'Konke ZigBee Motion Sensor' Version 1.0.2, based on code from Robert Morris and ssalahi.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * ver 1.0.3 2022-02-28 kkossev - inital version
 * ver 1.0.4 2022-03-02 kkossev - 'acceleration' misspelled bug fix
 * ver 1.0.5 2022-03-03 kkossev - Battery reporting
 * ver 1.0.6 2022-03-03 kkossev - Vibration Sensitivity
 * ver 1.0.7 2022-05-12 kkossev - TS0210 _TYZB01_pbgpvhgx Smart Vibration Sensor HS1VS 
 * ver 1.0.8 2022-11-08 kkossev - TS0210 _TZ3000_bmfw9ykl
 * ver 1.1.0 2023-03-07 kkossev - added Import URL; IAS enroll response is sent w/ 1 second delay; added _TYZB01_cc3jzhlj ; IAS is initialized on configure();
 * ver 1.2.0 2024-05-20 kkossev - add healthStatus and ping(); bug fixes; added ThirdReality 3RVS01031Z ; added capability and preference 'ThreeAxis'; added Samsung multisensor; logsOff scheduler; added sensitivity attribute,
 * ver 1.2.1 2024-05-22 kkossev - delete scheduled jobs on Save Preferences; added lastBattery attribute; added setAccelarationInactive command;
 * ver 1.2.2 2024-06-03 kkossev - sensitivity preference is hidden for non-Tuya models; threeAxis preference is hidden for Tuya models;
 * ver 1.3.0 2025-01-28 kkossev - added Tuya Cluster parser; added TS0601 _TZE200_kzm5w4iz (contact&vibration); added TS0601 _TZE200_iba1ckek (Tilt Xyz Axis Sensor) (ZG-103Z); added queryAllTuyaDP(); missing [overwrite: true] bug fix;
 * ver 1.3.1 2025-02-19 kkossev - added TS0210 _TZ3000_lqpt3mvr _TZ3000_lzdjjfss _TYZB01_geigpsy4
 * ver 1.4.0 2025-03-01 kkossev - added ShockSensor capability; added shockSensor option (default:enabled)
 * 
 *                                TODO: this driver does not process ZCL battery percentage reports, only voltage reports!
 *                                TODO: bugFix: healthCheck is not started on installed()
 *                                TODO: add powerSource attribute
 *                                TODO: make sensitivity range dependant on the device model
 *                                TODO: minimum time filter : https://community.hubitat.com/t/tuya-vibration-sensor-better-laundry-monitor/113296/9?u=kkossev 
 *                                TODO: add capability.tamperAlert
 *                                TODO: handle tamper: (zoneStatus & 1<<2); handle battery_low: (zoneStatus & 1<<3); TODO: check const sens = {'high': 0, 'medium': 2, 'low': 6}[value];
 */

static String version() { "1.4.0" }
static String timeStamp() { "2025/03/01 4:21 PM" }

import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import com.hubitat.zigbee.DataType
import groovy.transform.CompileStatic


metadata {
	definition (name: "Tuya ZigBee Vibration Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20ZigBee%20Vibration%20Sensor/Tuya%20ZigBee%20Vibration%20Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "AccelerationSensor"
        capability "ShockSensor"              // shock - ENUM ["clear", "detected"] attribute
        //capability "TamperAlert"            // tamper - ENUM ["clear", "detected"]
		capability "Battery"
		capability "Configuration"
        capability "Refresh"
        capability 'Health Check'
        capability 'ThreeAxis'              // Attributes: threeAxis - VECTOR3

        command 'setAccelarationInactive', [[name: 'Reset the accelaration to inactive state']]
        
        attribute "batteryVoltage", "number"
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'batteryStatus', 'enum', ["normal", "replace"]
        attribute 'sensitivity', 'number'
        attribute 'lastBattery', 'date'         // last battery event time - added in 1.2.1 05/21/2024
        attribute 'tilt', 'enum', ["clear", "detected"]
        
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_3zv6oleo"              // KK
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_kulduhbj"              // not tested https://fr.aliexpress.com/item/1005002490419821.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_cc3jzhlj"              // not tested 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_geigpsy4"              // not tested 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TZ3000_lqpt3mvr"              // https://community.hubitat.com/t/release-tuya-zigbee-vibration-sensor/138208/37?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0500,0B05", outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_pbgpvhgx"              // Smart Vibration Sensor HS1VS
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_bmfw9ykl"         // Moes https://community.hubitat.com/t/vibration-sensor/85203/14?u=kkossev       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_lzdjjfss"         // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TYZB01_j9xxahcl"         // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_fkxmyics"         // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,FFF1",           outClusters:"0019", model:"3RVS01031Z", manufacturer:"Third Reality, Inc"        // Third Reality vibration sensor   
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0500,0B05,FC02", outClusters:"0003,0019", model:"multi", manufacturer:"Samjin"          // Samsung Multisensor
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000",           outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_kzm5w4iz"         // https://github.com/flatsiedatsie/zigbee-herdsman-converters/blob/ef4d559ccba0a39cd6957d2270352e29fb1d0296/converters/fromZigbee.js#L7449-L7467
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001",           outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_iba1ckek"         // https://nl.aliexpress.com/item/1005007520278259.html Tilt Xyz Axis Sensor (ZG-103Z)
	}

	preferences {
		input name: "txtEnable", type: "bool", title: "<b>Enable info message logging</b>", description: ""
		input name: "logEnable", type: "bool", title: "<b>Enable debug message logging</b>", description: ""
        if (device && isTuya()) {
            input name: "sensitivity", type: "enum", title: "<b>Vibration Sensitivity</b>", description: "Select Vibration Sensitivity", defaultValue: "3", options:["0":"0 - Maximum", "1":"1", "2":"2", "3":"3 - Medium", "4":"4", "5":"5", "6":"6 - Minimum"]
        }
		input "vibrationReset", "number", title: "After vibration is detected, wait $vibrationReset second(s) until <b>resetting to inactive state</b>. Default = $VIBRATION_RESET seconds.", description: "", range: "1..7200", defaultValue: VIBRATION_RESET
        if (device && (!isTuya() || isTuyaTiltXyzAxisSensor )) {
            input name: 'threeAxis', type: 'enum', title: '<b>Three Axis</b>', description: 'Enable or disable the Three Axis reporting<br>(ThirdReality and Samsung)', defaultValue: ThreeAxisOpts.defaultValue, options: ThreeAxisOpts.options
        }
        if (device) {
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'These advanced options should be already automatically set in an optimal way for your device.', defaultValue: false
            if (advancedOptions == true) {
                input (name: "shockSensor", type: "bool",   title: "<b>Shock Sensor</b>", description: "Simulate a Shock Sensor", defaultValue: true)
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
                input "batteryReportingHours", "number", title: "Report battery every $batteryReportingHours hours. Default = 12h (Minimum 2 h)", description: "", range: "2..12", defaultValue: 12
            }
        }
	}
}

boolean isTuyaVibrationDoorSensor() {
    return device.getDataValue("manufacturer") == "_TZE200_kzm5w4iz"    // Tuya TS0601 Vibration and Door Sensor
}

boolean isTuyaTiltXyzAxisSensor() {
    return device.getDataValue("manufacturer") == "_TZE200_iba1ckek"    // Tuya TS0601 Tilt Xyz Axis Sensor
}

@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer VIBRATION_RESET = 3            // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final int PING_ATTR_ID = 0x01

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map ThreeAxisOpts = [
    defaultValue: 1, options: [0: 'Disabled', 1: 'Enabled - Events only', 2: 'Enabled - Events and Logs']
]

// e8ZoneState is a mandatory attribute which indicates the membership status of the device in an IAS system (enrolled or not enrolled) - one of:
@Field static final Map<Integer, String> ZONE_STATE = [
    0x00: 'Not Enrolled',
    0x01: 'Enrolled'
]
// ‘Enrolled’ means that the cluster client will react to Zone State Change Notification commands from the cluster server. 

// e16ZoneType is a mandatory attribute which indicates the zone type and the types of security detectors that can trigger the alarms, Alarm1 and Alarm2: 
@Field static final Map<Integer, String> ZONE_TYPE = [
    0x0000: 'Standard CIE',
    0x000D: 'Motion Sensor',
    0x0015: 'Contact Switch',
    0x0028: 'Fire Sensor',
    0x002A: 'Water Sensor',
    0x002B: 'Carbon Monoxide Sensor',
    0x002C: 'Personal Emergency Device',
    0x002D: 'Vibration Movement Sensor',
    0x010F: 'Remote Control',
    0x0115: 'Key Fob',
    0x021D: 'Key Pad',
    0x0225: 'Standard Warning Device',
    0x0226: 'Glass Break Sensor',
    0x0229: 'Security Repeater',
    0xFFFF: 'Invalid Zone Type'
]

// b16ZoneStatus is a mandatory attribute which is a 16-bit bitmap indicating the status of each of the possible notification triggers from the device: 
@Field static final Map<Integer, String> ZONE_STATUS = [
    0x0001: 'Alarm 1',                    // 0 - closed or not alarmed; 1 - opened or alarmed
    0x0002: 'Alarm 2',                    // 0 - closed or not alarmed; 1 - opened or alarmed
    0x0004: 'Tamper',                     // 0 - not tampered with; 1 - tampered with
    0x0008: 'Battery',                    // 0 - battery OK; 1 - Low battery
    // Bit 4 indicates whether the Zone device issues periodic Zone Status Change Notification commands that may be used by the CIE device as evidence that the Zone device is operational    
    0x0010: 'Supervision reports',        // 0 - does not notify; 1 - notify; 
    // 2 Bit 5 indicates whether the Zone device issues a Zone Status Change Notification command to notify when an alarm is no longer present (some Zone devices do not have the ability to detect when the alarm condition has disappeared).    
    0x0020: 'Restore reports',            // 0 - does not notify on restore; 1 - notify restore
    0x0040: 'Trouble',                    // 0 - OK; 1 - Trouble/Failure
    0x0080: 'AC mains',                   // 0 - AC/Mains OK; 1 - AX/Mains Fault
    0x0100: 'Test',                       // 0 - Sensor is in operation mode; 1 - Sensor is in test mode
    0x0200: 'Battery Defect'              // 0 - Sensor battery is functioning normally; 1 - Sensor detects a defective battery
    // bits 10..15 are reserved
]

@Field static final Map<Integer, String> ENROLL_RESPOSNE_CODE = [
    0x00: 'Success',
    0x01: 'Not supported',
    0x02: 'No enroll permit',
    0x03: 'Too many zones'
]

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [
//  Zone Information
    0x0000: 'zone state',
    0x0001: 'zone type',
    0x0002: 'zone status',
//  Zone Settings
    0x0010: 'CIE addr',    // EUI64
    0x0011: 'Zone Id',     // uint8
    0x0012: 'Num zone sensitivity levels supported',     // uint8
    0x0013: 'Current zone sensitivity level'             // uint8
]

@Field static final Map<Integer, String> IAS_SERVER_COMMANDS = [
    0x0000: 'enroll response',                           // uint8
    0x0001: 'init normal op mode',                       //
    0x0002: 'init test mode'                             // uint8, uint8
]

@Field static final Map<Integer, String> IAS_CLIENT_COMMANDS = [
    0x0000: 'status change notification',                // ZoneStatus, bitmap8, uint8, uint16
    0x0001: 'enroll'                                     // ZoneType, uint16
]


// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion(state)
    updateRxStats(state)
    unscheduleCommandTimeoutCheck(state)
    setHealthStatusOnline(state)

    Map map = [:]
    logDebug("Parsing: $description")
    Map event = [:]
    try {
        event = zigbee.getEvent(description)
    }
    catch ( e ) {
        if (logEnable) log.warn "exception caught while decoding event description:  ${description}"
        // return null // ignore and continue, changed 05/19/2024
    }
    //
    if (event) {
        if (event.name == 'battery') {
            event.unit = '%'
            event.isStateChange = true
            event.descriptionText = "battery is ${event.value} ${event.unit}"
            sendLastBatteryEvent()
        }
	    else if (event.name == "batteryVoltage")
	    {
    		event.unit = "V"
            event.isStateChange = true
    		event.descriptionText = "battery voltage is ${event.value} volts"
            sendLastBatteryEvent()
    	}
        else {
             logDebug("event: $event")    
        }
        logInfo(event.descriptionText)
        return createEvent(event)
    }
	else if (description?.startsWith('enroll request')) {
        //------IAS Zone Enroll request------//
		logDebug "Scheduling IAS enroll response after 1 second..."
        runIn(1, "sendEnrollResponse")  
	}
	else if (description?.startsWith('zone status')) {	
        logDebug("Zone status: $description")    
        def zs = zigbee.parseZoneStatus(description)
        map = parseIasMessage(zs)
    }
    else if (description?.startsWith("catchall") || description?.startsWith("read attr")) {
        Map descMap = zigbee.parseDescriptionAsMap(description)        
        if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0020) {
            map = parseBatteryVoltage(descMap.value)
        }
        else if (descMap.command == "07") {
            // Process "Configure Reporting" response            
            if (descMap.data[0] == "00") {
                switch (descMap.clusterInt) {
                    case zigbee.POWER_CONFIGURATION_CLUSTER:
                        logInfo("Battery reporting configured");                        
                        break
                    default:                    
                        if (txtEnable) { log.warn("Unknown reporting configured: ${descMap}") }
                        break
                }
            } 
            else {
                if (logEnable) { log.warn "Reporting configuration failed: ${descMap}" }
            }
        } 
        else if (descMap.clusterInt == 0x0500 && descMap.attrInt == 0x0002) {
            logDebug("Zone status repoted: $descMap")
            def zs = new ZoneStatus(Integer.parseInt(descMap.value, 16))
            map = parseIasMessage(zs)        
        } 
        else if (descMap.clusterInt == 0x0500 && descMap.attrInt == 0x0011) {
            logInfo("IAS Zone ID: ${descMap.value}")
        } 
        else if (descMap.clusterInt == 0x0500 && descMap.attrInt == 0x0013) {
            String descText = "IAS Zone Sensitivity: ${descMap.value}"
            int iSens = descMap.value?.toInteger()
            logInfo "vibration sensitivity : ${iSens}"
            sendEvent(name: "sensitivity", value: iSens, descText: descText)
            if (iSens>=0 && iSens<7)  {
                device.updateSetting("sensitivity",[value:iSens.toString(), type:"enum"])
            }
            else {
                logDebug "unsupported sensitivity value ${iSens} !"
            }
        } 
        else if (descMap.profileId == "0000") {
            logDebug "ignored ZDO messages "
        } 
        else if (descMap.clusterInt == zigbee.BASIC_CLUSTER && descMap.attrInt == PING_ATTR_ID) {
            handlePingResponse(descMap)
        }
        else if (descMap.clusterInt == 0xFFF1 && descMap.command in ['01', '0A']) {
            handleThreeAxisTR(descMap)
        }
        else if (descMap.clusterInt == 0xFC02 && descMap.command in ['01', '0A']) {
            handleThreeAxisSamsung(descMap)
        }
        else if ((descMap?.clusterInt == 0xEF00) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '06')) {
            int dataLen = descMap?.data.size()
            //log.warn "dataLen=${dataLen}"
            //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            if (dataLen <= 5) {
                logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})"
                return
            }
            boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024
            for (int i = 0; i < (dataLen - 4); ) {
                int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame
                int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant
                int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i])
                int fncmd = getTuyaAttributeValue(descMap?.data, i)          //
                processTuyaDP(descMap, dp, dp_id, fncmd)
                i = i + fncmd_len + 4
            }
        } // if (descMap?.command == "01" || descMap?.command == "02")        
        else {
            if (logEnable) log.warn ("Description map not parsed: $descMap")            
        }
    }
    else {
        if (logEnable) log.warn "Description not parsed: $description"
    }
    
    if (map != null && map != [:]) {
		logInfo(map?.descriptionText)
		return createEvent(map)
	} else {
		return [:]
    }
}

private int getTuyaAttributeValue(final List<String> _data, final int index) {
    int retValue = 0
    if (_data.size() >= 6) {
        int dataLength = zigbee.convertHexToInt(_data[5 + index])
        if (dataLength == 0) { return 0 }
        int power = 1
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5])
            power = power * 256
        }
    }
    return retValue
}


void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd) {
    switch (dp) {
        case 0x01:
            if (isTuyaVibrationDoorSensor()) {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
                logInfo "TuyaVibrationDoorSensor: contact is ${fncmd == 1 ? 'open' : 'closed'}"
            // TODO - create a child device?
            }
            else {
                // isTuyaTiltXyzAxisSensor() - Vibration State 
                logDebug "Tuya Vibration State cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
                sendVibrationEvent(fncmd != 0)
            }
            break
        case 0x02:  // ?
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
        case 0x03:  // thitBatteryPercentage  isTuyaVibrationDoorSensor() TS0601 _TZE200_kzm5w4iz
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendBatteryPercentageEvent(fncmd)
            sendLastBatteryEvent()
            break
        case 0x07 :// tilt
            logDebug "Tuya tilt cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendTiltEvent(fncmd != 0)
            break
        case 0x0A:  // (10) tuyaVibration isTuyaVibrationDoorSensor() TS0601 _TZE200_kzm5w4iz
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendVibrationEvent(fncmd != 0)
            break
        case 0x65:  // (101) X-axis acceleration
            logDebug "Tuya X-axis acceleration cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            state.lastAcceleration['x'] = fncmd
            break
        case 0x66:  // (102) Y-axis acceleration
            logDebug "Tuya 102) Y-axis acceleration cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            state.lastAcceleration['y'] = fncmd
            break
        case 0x67:  // (103) Z-axis acceleration
            logDebug "Tuya Z-axis acceleration cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            state.lastAcceleration['z'] = fncmd
            sendTuyaThreeAxisEvent(state.lastAcceleration.x, state.lastAcceleration.y, state.lastAcceleration.z)
            break
        case 0x68:  // (104) Sensitivity Setting
            logDebug "Tuya Sensitivity Setting cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
        case 0x69:  // (105) Battery Percentage
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendBatteryPercentageEvent(fncmd)
            sendLastBatteryEvent()
            break
        default :
            logDebug "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            break
    }
}

void sendTuyaThreeAxisEvent(Integer x, Integer y, Integer z) {
    convertXYZtoPsiPhiTheta(x ?: 0, y ?: 0, z ?: 0)
}

void sendTiltEvent(boolean tiltActive) {
    logDebug "Tilt : $tiltActive"
    Map result = handleTilt(tiltActive)
    if (result != [:]) {
        sendEvent(result)
        logInfo (result.descriptionText)
    }
    else {
        logDebug "Tilt event not sent"
    }
}

private handleTilt(boolean tiltActive) {    
    if (tiltActive) {
        if (device.currentState('tilt')?.value != "detected") {
            // Tilt detected
            return getTiltResult(true)
        }
        else {
            logDebug "Tilt already detected"
            return [:]
        }
    }
    else {
        if (device.currentState('tilt')?.value == "detected") {
            // Tilt reset to inactive
            return getTiltResult(false)
        }
        else {
            logDebug "Tilt was already cleared"
            return [:]
        }
    }
}

Map getTiltResult(tiltActive) {
    String descriptionText = "Tilt detected"
    if (!tiltActive) {
        descriptionText = "Tilt reset to clear"
    }
    return [
            name            : 'tilt',
            value           : tiltActive ? 'detected' : 'clear',
            type            : 'physical',
            descriptionText : descriptionText
    ]
}



def sendBatteryPercentageEvent(rawValuePar) {
    def rawValue = rawValuePar as int
    logDebug "sendBatteryPercentageEvent: rawValue = ${rawValue}"
    def result = [:]
    if (rawValue < 0) { rawValue = 0; logWarn "batteryPercentage rawValue corrected to ${rawValue}" }
    if (rawValue > 100 ) { rawValue = 100; logWarn "batteryPercentage rawValue corrected to ${rawValue}" }
    result.name = 'battery'
    result.translatable = true
    result.value = Math.round(rawValue)
    result.descriptionText = "${device.displayName} battery is ${result.value}%"
    result.isStateChange = true
    result.unit = '%'
    result.type = 'physical'
    sendEvent(result)
}

def sendEnrollResponse() {
	logDebug "Sending a scheduled IAS enroll response..."
    List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
    sendZigbeeCommands(cmds)    
}

// helpers -------------------

Map parseIasMessage(ZoneStatus zs) {
    String currentAccel = device.currentState('acceleration')?.value
    String zsStr = ''
    zs.properties.sort().each { key, value ->  zsStr += "$key = $value, "}
    if (logEnable) log.debug "current acceleration = ${currentAccel}   new Zone status message zs = ${zsStr}"
    // check for vibration active
    if (zs.alarm1Set == true || zs.alarm2Set == true) {
        if (currentAccel != "active") {
            // Vibration detected
            return handleVibration(true)
        }
        else {
            logDebug "Vibration already active"
            return [:]
        }
    }
    else if (zs.alarm1Set == false && zs.alarm2Set == false) {
        if (currentAccel == "active") {
            // Vibration reset to inactive
            return handleVibration(false)
        }
        else {
            logDebug "Vibration already inactive"
            return [:]
        }
    }
    else {
        logWarn "Unsupported IAS Zone status: ${zsStr}"
        return [:]
    }
}

// called when processing Tuya TS0601 model EF00 cluster commands
void sendVibrationEvent(boolean vibrationActive) {
    log.trace "Vibration : $vibrationActive"
    Map result = handleVibration(vibrationActive)
    if (result != [:]) {
        sendEvent(result)
        logInfo (result.descriptionText)
        if (settings.shockSensor == true) {
            sendEvent(getShockResult(vibrationActive))
        }
    }
    else {
        logDebug "Vibration event not sent"
    }
}

Map handleVibration(boolean vibrationActive) {    
    if (vibrationActive) {
        int timeout = vibrationReset ?: 3
        // Some sensors will send only a vibration detected message, so reset to vibration inactive is performed in code
        runIn(timeout, resetToVibrationInactive, [overwrite: true])        
        if (device.currentState('acceleration')?.value != "active") {
            state.vibrationStarted = now()
        }
        sendEvent(getShockResult(vibrationActive))
    	return getVibrationResult(vibrationActive)
    }
    else { // vibration inactive event
        unschedule('resetToVibrationInactive')
        if (device.currentState('acceleration')?.value != "inactive") {
            sendEvent(getShockResult(vibrationActive))
        	return getVibrationResult(vibrationActive)
        }
        else {
            logDebug "Vibration already inactive"
            return [:]
        }
    }
}

Map getVibrationResult(vibrationActive) {
	String descriptionText = "Vibration detected"
    if (!vibrationActive) {
		descriptionText = "Vibration reset to inactive after ${getSecondsInactive()}s"
    }
	return [
			name			: 'acceleration',
			value			: vibrationActive ? 'active' : 'inactive',
            type            : 'physical',
			descriptionText : descriptionText
	]
}

Map getShockResult(shockActive) {
    String descriptionText = "Shock detected"
    if (!shockActive) {
        descriptionText = "Shock reset to inactive"
    }
    return [
            name            : 'shock',
            value           : shockActive ? 'detected' : 'clear',
            type            : 'physical',
            descriptionText : descriptionText
    ]
}

void setAccelarationInactive() {
    resetToVibrationInactive(true)
}

void resetToVibrationInactive(boolean isDigital = false) {
	if (device.currentState('acceleration')?.value == "active") {
        String type = isDigital ? "digital" : "physical"
		String descText = "Vibration reset to inactive after ${getSecondsInactive()}s [$type]"
		sendEvent(
			name : "acceleration",
			value : "inactive",
			isStateChange : true,
            type : type,
			descriptionText : descText
		)
		logInfo(descText)
        if (settings.shockSensor == true) {
            sendEvent(getShockResult(false))
        }
	}
}

int getSecondsInactive() {
    if (state.vibrationStarted) {
        return Math.round((now() - state.vibrationStarted)/1000)
    } else {
        return vibrationReset ?: 3
    }
}

// Convert 2-byte hex string to voltage
// 0x0020 BatteryVoltage -  The BatteryVoltage attribute is 8 bits in length and specifies the current actual (measured) battery voltage, in units of 100mV.
private parseBatteryVoltage(valueHex) {
	//logDebug("Battery parse string = ${valueHex}")
	def rawVolts = Integer.parseInt(valueHex, 16) / 10
	def minVolts = voltsmin ? voltsmin : 2.5
	def maxVolts = voltsmax ? voltsmax : 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	//logInfo(descText)
    // sendEvent(name: "batteryLevelLastReceived", value: new Date())    
	def result = [
		name: 'battery',
		value: roundedPct,
		unit: "%",
		//isStateChange: true,
		descriptionText: descText
	]
    sendLastBatteryEvent()
	return result
}

void sendLastBatteryEvent() {
    final Date lastBattery = new Date()
    sendEvent(name: 'lastBattery', value: lastBattery, descriptionText: "Last battery event at ${lastBattery}")
}

String getDEGREE() { return String.valueOf((char)(176)) }
import groovy.json.JsonOutput

/* Some parts borrowed from veeceeoh in this method */
void convertXYZtoPsiPhiTheta(int x, int y, int z) {
    BigDecimal psi = new BigDecimal(Math.atan(x.div(Math.sqrt(z * z + y * y))) * 180 / Math.PI).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal phi = new BigDecimal(Math.atan(y.div(Math.sqrt(x * x + z * z))) * 180 / Math.PI).setScale(1, BigDecimal.ROUND_HALF_UP)
    BigDecimal theta = new BigDecimal(Math.atan(z.div(Math.sqrt(x * x + y * y))) * 180 / Math.PI).setScale(1, BigDecimal.ROUND_HALF_UP)
    logDebug "Calculated angles are Psi = ${psi}$DEGREE, Phi = ${phi}$DEGREE, Theta = ${theta}$DEGREE   Raw accelerometer XYZ axis values = $x, $y, $z" 
    String json  = JsonOutput.toJson([x:x, y:y, z:z, psi:psi, phi:phi, theta:theta])
    if ((settings.threeAxis as int) == 2) { // 2 - Enabled - Events and Logs
        log.info "threeAxis : ${json}"
    }
    if ((settings.threeAxis as int) > 0) { // 1 - Enabled - Events only
        sendEvent(name: 'threeAxis', value: json, isStateChange: true)
    }
}

void handleThreeAxisTR(final Map descMap) {
    logDebug "handleThreeAxisTR: descMap = ${descMap}"
    boolean isValid = descMap.value == "0001"
    int x, y, z
    descMap.additionalAttrs.each { attr ->
        int axis = zigbee.convertHexToInt(attr.value)
        if (axis > 0x7FFF) { axis = axis - 0x10000 }
        if (attr.attrInt == 1) { x = axis } else if (attr.attrInt == 2) { y = axis } else if (attr.attrInt == 3) { z = axis }
    }
    if (isValid) {
        convertXYZtoPsiPhiTheta(x, y, z)
    }
}

void handleThreeAxisSamsung(final Map descMap) {
    logDebug "handleThreeAxisSamsung: descMap = ${descMap}"
    if (descMap.attrInt == 0x0010) {
        //  read attr - raw: DC8401FC020810001801, dni: DC84, endpoint: 01, cluster: FC02, size: 08, attrId: 0010, encoding: 18, command: 0A, value: 01
        Map event = handleVibration(descMap.value == "01")
        if (event) {
            sendEvent(event)
            logInfo event.descriptionText
        }
        return
    }
    else if (descMap.attrInt == 0x0012) {
        int x, y, z
        x = zigbee.convertHexToInt(descMap.value)
        if (x > 0x7FFF) { x = x - 0x10000 }
        descMap.additionalAttrs.each { attr ->
            int axis = zigbee.convertHexToInt(attr.value)
            if (axis > 0x7FFF) { axis = axis - 0x10000 }
            if (attr.attrInt == 19) { y = axis } else if (attr.attrInt == 20) { z = axis }
        }
        if ((x != null) && (y != null) && (z != null)) {
            convertXYZtoPsiPhiTheta(x, y, z)
        }        
    }
    else {
        logWarn "handleThreeAxisSamsung: unsupported attrInt=${descMap.attrInt}"
    }
    return
}

// lifecycle methods -------------

// installed() runs just after a sensor is paired
def installed() {
	logInfo "Installing..."
    sendEvent(name: 'healthStatus', value: 'unknown')
    initializeVars(fullInit = true)
    updateTuyaVersion()
    refresh()
}

// configure() runs after installed() when a sensor is paired or reconnected
void configure() {
	logInfo("Configuring")
    configureReporting()
}

List<String> queryAllTuyaDP() {
    logDebug 'queryAllTuyaDP()'
    List<String> cmds = zigbee.command(0xEF00, 0x03)
    return cmds
}

void refresh() {
	logInfo("Refreshing...")
    List<String> cmds = []
    cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, [:], delay=200) // battery voltage
    cmds += zigbee.readAttribute(0x0500, 0x0013, [:], delay=200)    // sensitivity
    if (device?.getDataValue('manufacturer') == 'Samjin') {
        cmds += zigbee.readAttribute(0xFC02, [0x0010, 0x0012], [:], delay=200) // vibration and three axis
    }
    else if (device?.getDataValue('manufacturer') == 'Third Reality, Inc') {
        cmds += zigbee.readAttribute(0xFFF1, [0x0000, 0x0001, 0x0002, 0x0003], [:], delay=200) // vibration and three axis
    }
    if (isTuya()) {
        cmds += queryAllTuyaDP()
    }
    sendZigbeeCommands(cmds)
}


// updated() runs every time user saves preferences
void updated() {
    unschedule()        // added 05/21/2024
    if (logEnable == true) {
        runIn(86400, 'logsOff', [overwrite: true, misfire: 'ignore'])    // turn off debug logging after 30 minutes
        if (settings?.txtEnable) { log.info "${device.displayName} Debug logging will be turned off after 24 hours" }
    }
    else {
        unschedule('logsOff')
    }
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
        logInfo 'Health Check is disabled!'
    }
    if (settings.shockSensor == true) {
        logInfo "Shock Sensor is enabled"
        if (device.currentState('shock') == null) {
            sendEvent(getShockResult(false))
        }
    }
    else {
        logInfo "Shock Sensor is disabled"
        if (device.currentState('shock') != null) {
            device.deleteCurrentState('shock')
        }
    }

    String currentTreeAxis = device.currentState('threeAxis')?.value
	logInfo("Updating preference settings, sensitivity = ${settings.sensitivity}, threeAxisOpt = ${settings.threeAxis}, currentTreeAxis = $currentTreeAxis}")
    if (settings.threeAxis as int == 0 && currentTreeAxis != null) {
        logInfo "Three Axis reporting is now disabled"
        device.deleteCurrentState('threeAxis')
    }
    else if (settings.threeAxis as int != 0 && currentTreeAxis == null) {
        logInfo "Three Axis reporting is now enabled with option ${settings.threeAxis}"
    }
    configureReporting()
}

boolean isTuya() {
    if (!device) { return true }
    String model = device.getDataValue('model')
    String manufacturer = device.getDataValue('manufacturer')
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */
    return (model?.startsWith('T') && manufacturer?.startsWith('_T')) ? true : false
}

void updateTuyaVersion() {
    if (!isTuya()) { logDebug 'not Tuya' ; return }
    final String application = device.getDataValue('application')
    if (application != null) {
        Integer ver
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString()
        if (device.getDataValue('tuyaVersion') != str) {
            device.updateDataValue('tuyaVersion', str)
            logInfo "tuyaVersion set to $str"
        }
    }
}

public void ping() {
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime()
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true
    scheduleCommandTimeoutCheck()
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
    logDebug 'ping...'
}

void handlePingResponse(final Map descMap) {
    boolean isPing = state.states['isPing'] ?: false
    Long now = new Date().getTime()
    if (state.lastRx == null) { state.lastRx = [:] }
    state.lastRx['checkInTime'] = now
    if (isPing) {
        int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger()
        if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
            state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1
            if (timeRunning < (state.stats['pingsMin'] ?: 999)) { state.stats['pingsMin'] = timeRunning }
            if (timeRunning > (state.stats['pingsMax'] ?: 0))   { state.stats['pingsMax'] = timeRunning }
            state.stats['pingsAvg'] = approxRollingAverage(state.stats['pingsAvg'], timeRunning) as int
            sendRttEvent()
        }
        else {
            logWarn "unexpected ping timeRunning=${timeRunning} "
        }
        state.states['isPing'] = false
    }
    else {
        logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
    }
}

@Field static final int ROLLING_AVERAGE_N = 10
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) {
    BigDecimal avg = avgPar
    if (avg == null || avg == 0) { avg = newSample }
    avg -= avg / ROLLING_AVERAGE_N
    avg += newSample / ROLLING_AVERAGE_N
    return avg
}

// helpers -------------

static void updateRxStats(final Map state) {
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    if (state.states == null) { state.states = [:] }
    state.states['isTimeoutCheck'] = true
    runIn(delay, 'deviceCommandTimeout')
}

void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( 
    if (state.states == null) { state.states = [:] }
    if (state.states['isTimeoutCheck'] == true) {
        state.states['isTimeoutCheck'] = false
        unschedule('deviceCommandTimeout')
    }
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent('timeout')
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1
}

void sendRttEvent( String value=null) {
    Long now = new Date().getTime()
    if (state.lastTx == null ) { state.lastTx = [:] }
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger()
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

String getCron(int timeInSeconds) {
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping')
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours
    final Random rnd = new Random()
    int minutes = (timeInSeconds / 60 ) as int
    int  hours = (minutes / 60 ) as int
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

/**
 * Schedule a device health check
 * @param intervalMins interval in minutes
 */
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2)  {
        String cron = getCron( intervalMins * 60 )
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

// called when any event was received from the Zigbee device in the parse() method.

void setHealthStatusOnline(Map state) {
    if (state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {
        sendHealthStatusEvent('online')
        logInfo 'is now online!'
    }
}

void deviceHealthCheck() {
    checkDriverVersion(state)
    if (state.health == null) { state.health = [:] }
    int ctr = state.health['checkCtr3'] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) {
            logWarn 'not present!'
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    state.health['checkCtr3'] = ctr + 1
}

void sendHealthStatusEvent(final String value) {
    String descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" }

String getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
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

@CompileStatic
void checkDriverVersion(final Map state) {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(false)
        updateTuyaVersion()
    }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.stats  == null) { state.stats =  [:] }
    if (state.lastAcceleration == null) { state.lastAcceleration = [:] }
}

void resetStats() {
    logDebug 'resetStats...'
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:]
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0
}

void initializeVars( boolean fullInit = false ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        state.comment = 'Works with Tuya TS0210 and TR Vibration Sensors'
        logInfo 'all states and scheduled jobs cleared!'
        state.driverVersion = driverVersionAndTimeStamp()
    }

    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) }
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) }
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) }
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) }
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') }
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) }
    if (fullInit || settings?.threeAxis == null) { device.updateSetting('threeAxis', [value: ThreeAxisOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.shockSensor == null) { device.updateSetting('shockSensor', true) }
    

    final String ep = device.getEndpointId()
    if ( ep  != null) {
        logDebug " destinationEP = ${ep}"
    }
    else {
        logWarn ' Destination End Point not found, please re-pair the device!'
    }
}

void logsOff() {
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

void configureReporting() {
    int seconds = Math.round((settings?.batteryReportingHours ?: 12)*3600)
    logInfo("Battery reporting frequency: ${seconds/3600}h")    
    
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200) 
    cmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, seconds-1, seconds, 0x00, [:], delay=200)
    cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x20, [:], delay=200)
    // added 03/07/2023
    cmds += zigbee.enrollResponse(200) + zigbee.readAttribute(0x0500, 0x0000, [:], delay=200)
    //
    if (settings?.sensitivity != null && isTuya()) {
    logDebug("Configuring vibration sensitivity to : ${settings?.sensitivity}")
            int iSens = settings.sensitivity?.toInteger()
            if (iSens>=0 && iSens<7)  {
                cmds += zigbee.writeAttribute(0x0500, 0x0013,  DataType.UINT8, iSens, [:], delay=200)
            }    
    }
    sendZigbeeCommands(cmds)
}

void sendZigbeeCommands(List<String> cmd) {
    if (cmd == null || cmd.isEmpty()) {
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}"
        return
    }
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        if (it == null || it.isEmpty() || it == 'null') {
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})"
            return
        }
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] }
    }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(allActions)
    logDebug "sendZigbeeCommands: sent cmd=${cmd}"
}

private def logDebug(message) {
	if (logEnable) { log.debug "${device.displayName}: ${message}" }
}

private def logInfo(message) {
	if (txtEnable) { log.info "${device.displayName}: ${message}" }
}

private def logWarn(message) {
	if (logEnable) { log.warn "${device.displayName}: ${message}" }
}
