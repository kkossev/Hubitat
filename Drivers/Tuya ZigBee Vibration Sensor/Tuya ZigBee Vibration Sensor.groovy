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
 * ver 1.2.0 2024-05-17 kkossev - (dev. branch) add healthStatus and ping(); bug fixes; added ThirdReality 3RVS01031Z ; added capability 'ThreeAxis'for testing; added Samsung multisensor;
 * 
 *                                TODO: 
 *                                TODO: add capability.tamperAlert
 *                                TODO: add sensitivity attribute
 *                                TODO: make sensitivity range dependant on the device model
 *                                TODO: Publish a new HE forum thread
 *                                TODO: minimum time filter : https://community.hubitat.com/t/tuya-vibration-sensor-better-laundry-monitor/113296/9?u=kkossev 
 *                                TODO: handle tamper: (zoneStatus & 1<<2); handle battery_low: (zoneStatus & 1<<3); TODO: check const sens = {'high': 0, 'medium': 2, 'low': 6}[value];
 */

static String version() { "1.2.0" }
static String timeStamp() { "2024/05/17 8:25 PM" }

import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import com.hubitat.zigbee.DataType
import groovy.transform.CompileStatic


metadata {
	definition (name: "Tuya ZigBee Vibration Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20ZigBee%20Vibration%20Sensor/Tuya%20ZigBee%20Vibration%20Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "AccelerationSensor"
        capability "TamperAlert"            // tamper - ENUM ["clear", "detected"]
		capability "Battery"
		capability "Configuration"
        capability "Refresh"
        capability 'Health Check'
        capability 'ThreeAxis'              // Attributes: threeAxis - VECTOR3
        
        attribute "batteryVoltage", "number"
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'rtt', 'number'
        attribute 'batteryStatus', 'enum', ["normal", "replace"]
        
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_3zv6oleo"     // KK
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_kulduhbj"     // not tested https://fr.aliexpress.com/item/1005002490419821.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_cc3jzhlj"     // not tested 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0500,0B05", outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_pbgpvhgx"     // Smart Vibration Sensor HS1VS
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_bmfw9ykl" // Moes https://community.hubitat.com/t/vibration-sensor/85203/14?u=kkossev       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TYZB01_j9xxahcl" // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_fkxmyics" // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,FFF1",           outClusters:"0019", model:"3RVS01031Z", manufacturer:"Third Reality, Inc"          // Third Reality vibration sensor   
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0402,0500,0B05,FC02", outClusters:"0003,0019", model:"multi", manufacturer:"Samjin" // Samsung Multisensor
	}

	preferences {
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
        input name: "sensitivity", type: "enum", title: "Vibration Sensitivity", description: "Select Vibration Sensitivity", defaultValue: "3", options:["0":"0 - Maximum", "1":"1", "2":"2", "3":"3 - Medium", "4":"4", "5":"5", "6":"6 - Minimum"]
		input "vibrationReset", "number", title: "After vibration is detected, wait ___ second(s) until resetting to inactive state. Default = 3 seconds (Hardware resets at 2 seconds)", description: "", range: "1..7200", defaultValue: 3
        if (device) {
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false
            if (advancedOptions == true) {
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>'
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
                input "batteryReportingHours", "number", title: "Report battery every ___ hours. Default = 12h (Minimum 2 h)", description: "", range: "2..12", defaultValue: 12
            }
        }
	}
}

@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final int PING_ATTR_ID = 0x01

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
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
        if (debugLogging) log.warn "exception caught while parsing description:  ${description}"
        return null
    }
    //
    if (event) {
        if (event.name == 'battery') {
            event.unit = '%'
            event.isStateChange = true
            event.descriptionText = "battery is ${event.value} ${event.unit}"
        }
	    else if (event.name == "batteryVoltage")
	    {
    		event.unit = "V"
            event.isStateChange = true
    		event.descriptionText = "battery voltage is ${event.value} volts"
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
            map = parseBattery(descMap.value)
        }
        else if (descMap.command == "07") {
            // Process "Configure Reporting" response            
            if (descMap.data[0] == "00") {
                switch (descMap.clusterInt) {
                    case zigbee.POWER_CONFIGURATION_CLUSTER:
                        logInfo("Battery reporting configured");                        
                        break
                    default:                    
                        if (infoLogging) log.warn("Unknown reporting configured: ${descMap}");
                        break
                }
            } 
            else {
                if (infoLogging) log.warn "Reporting configuration failed: ${descMap}"
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
            logInfo("vibration sensitivity : ${descMap.value}")
            def iSens = descMap.value?.toInteger()
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
        else if (descMap.clusterInt == 0xFFF1 && descMap.command == '0A') {
            handleThreeAxisTR(descMap)
        }
        else if (descMap.clusterInt == 0xFC02 && descMap.command == '0A') {
            handleThreeAxisSamsung(descMap)
        }
        else {
            if (debugLogging) log.warn ("Description map not parsed: $descMap")            
        }
    }
    else {
        if (debugLogging) log.warn "Description not parsed: $description"
    }
    
    if (map != null && map != [:]) {
		logInfo(map?.descriptionText)
		return createEvent(map)
	} else {
		return [:]
    }
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
    if (debugLogging) log.debug "current acceleration = ${currentAccel}   new Zone status message zs = ${zsStr}"
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
        /*
        ac = 0,
        acSet = false,
        alarm1 = 0,
        alarm1Set = false,
        alarm2 = 0,
        alarm2Set = false,
        battery = 0,
        batteryDefect = 0,
        batteryDefectSet = false,
        batterySet = false,
        class = class hubitat.zigbee.clusters.iaszone.ZoneStatus,
        restoreReports = 1,
        restoreReportsSet = true,
        supervisionReports = 0,
        supervisionReportsSet = false,
        tamper = 0,
        tamperSet = false,
        test = 0,
        testSet = false,
        trouble = 0,
        troubleSet = false,
        */
        return [:]
    }
}

private handleVibration(boolean vibrationActive) {    
    if (vibrationActive) {
        int timeout = vibrationReset ?: 3
        // The sensor only sends a vibration detected message so reset to vibration inactive is performed in code
        runIn(timeout, resetToVibrationInactive)        
        if (device.currentState('acceleration')?.value != "active") {
            state.vibrationStarted = now()
        }
    }
	return getVibrationResult(vibrationActive)
}

Map getVibrationResult(vibrationActive) {
	def descriptionText = "Detected vibration"
    if (!vibrationActive) {
		descriptionText = "Vibration reset to inactive after ${getSecondsInactive()}s"
    }
	return [
			name			: 'acceleration',
			value			: vibrationActive ? 'active' : 'inactive',
			descriptionText : descriptionText
	]
}

void resetToVibrationInactive() {
	if (device.currentState('acceleration')?.value == "active") {
		String descText = "Vibration reset to inactive after ${getSecondsInactive()}s"
		sendEvent(
			name : "acceleration",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
		logInfo(descText)
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
private parseBattery(valueHex) {
	//logDebug("Battery parse string = ${valueHex}")
	def rawVolts = Integer.parseInt(valueHex, 16) / 10 // hexStrToSignedInt(valueHex)/10
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
	return result
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
    log.info "threeAxis : ${json}"
    sendEvent(name: 'threeAxis', value: json, isStateChange: true)
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

void refresh() {
	logInfo("Refreshing...")
    List<String> cmds = []
    cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, [:], delay=200) // battery voltage
    cmds += zigbee.readAttribute(0x0500, 0x0013, [:], delay=200)    // sensitivity
    sendZigbeeCommands(cmds)
}


// updated() runs every time user saves preferences
void updated() {
	logInfo("Updating preference settings")
    configureReporting()
}

boolean isTuya() {
    if (!device) { return true }
    String model = device.getDataValue('model')
    String manufacturer = device.getDataValue('manufacturer')
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false
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
        state.comment = 'Works with Tuya TS0210 Vibration Sensors'
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

    final String ep = device.getEndpointId()
    if ( ep  != null) {
        logDebug " destinationEP = ${ep}"
    }
    else {
        logWarn ' Destination End Point not found, please re-pair the device!'
    }
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
    if ( settings?.sensitivity != null ) {
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
	if (debugLogging) { log.debug "${device.displayName}: ${message}" }
}

private def logInfo(message) {
	if (infoLogging) { log.info "${device.displayName}: ${message}" }
}

private def logWarn(message) {
	if (debugLogging) { log.warn "${device.displayName}: ${message}" }
}
