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
 * 
 *                                TODO: healthStatus
 *                                TODO: Publish a new HE forum thread
 *                                TODO: minimum time filter : https://community.hubitat.com/t/tuya-vibration-sensor-better-laundry-monitor/113296/9?u=kkossev 
 *                                TODO: handle tamper: (zoneStatus & 1<<2); handle battery_low: (zoneStatus & 1<<3); TODO: check const sens = {'high': 0, 'medium': 2, 'low': 6}[value];
 */

def version() { "1.1.0" }
def timeStamp() {"2023/03/07 2:10 PM"}

import groovy.transform.Field
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import com.hubitat.zigbee.DataType

metadata {
	definition (name: "Tuya ZigBee Vibration Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20ZigBee%20Vibration%20Sensor/Tuya%20ZigBee%20Vibration%20Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
        capability "Refresh"
        
        attribute "batteryVoltage", "number"
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        
		fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_3zv6oleo"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_kulduhbj"     // not tested https://fr.aliexpress.com/item/1005002490419821.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0500",           outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_cc3jzhlj"     // not tested 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,0500,0B05", outClusters:"0019", model:"TS0210", manufacturer:"_TYZB01_pbgpvhgx"     // Smart Vibration Sensor HS1VS
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_bmfw9ykl" // Moes https://community.hubitat.com/t/vibration-sensor/85203/14?u=kkossev       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TYZB01_j9xxahcl" // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0500,0000",                outClusters:"0019,000A", model:"TS0210", manufacturer:"_TZ3000_fkxmyics" // not tested
	}

	preferences {
		input "vibrationReset", "number", title: "After vibration is detected, wait ___ second(s) until resetting to inactive state. Default = 3 seconds (Hardware resets at 2 seconds)", description: "", range: "1..7200", defaultValue: 3
        input "batteryReportingHours", "number", title: "Report battery every ___ hours. Default = 12h (Minimum 2 h)", description: "", range: "2..12", defaultValue: 12
		
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
        input name: "sensitivity", type: "enum", title: "Vibration Sensitivity", description: "Select Vibration Sensitivity", defaultValue: "3", options:["0":"0 - Maximum", "1":"1", "2":"2", "3":"3 - Medium", "4":"4", "5":"5", "6":"6 - Minimum"]
	}
}

@Field static final Map<Integer, String> ZONE_STATE = [
    0x00: 'Not Enrolled',
    0x01: 'Enrolled'
]

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

@Field static final Map<Integer, String> ZONE_STATUS = [
    0x0001: 'Alarm 1',                    // 0 - closed or not alarmed; 1 - opened or alarmed
    0x0002: 'Alarm 2',                    // 0 - closed or not alarmed; 1 - opened or alarmed
    0x0004: 'Tamper',                     // 0 - not tampeted; 1 - tampered
    0x0008: 'Battery',                    // 0 - battery OK; 1 - Low battery
    0x0010: 'Supervision reports',        // 0 - does not notify; 1 - notify
    0x0020: 'Restore reports',            // 0 - does not notify on restore; 1 - notify restore
    0x0040: 'Trouble',                    // 0 - OK; 1 - Trouble/Failure
    0x0080: 'AC mains',                   // 0 - AC/Mains OK; 1 - AX/Mains Fault
    0x0100: 'Test',                       // 0 - Sensor is in operation mode; 1 - Sensor is in test mode
    0x0200: 'Battery Defect'              // 0 - Sensor battery is functioning normally; 1 - Sensor detects a defective battery
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
    Map map = [:]
    logDebug("Parsing: $description")
    def event = [:]
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
            // ignore routing table messages
        } 
        else {
            if (debugLogging) log.warn ("Description map not parsed: $descMap")            
        }
    }
    else {
        if (debugLogging) log.warn "Description not parsed: $description"
    }
    
    if (map != [:]) {
		logInfo(map?.descriptionText)
		return createEvent(map)
	} else
		return [:]
}

def sendEnrollResponse() {
	logDebug "Sending a scheduled IAS enroll response..."
    List<String> cmds = []
    cmds += zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
    sendZigbeeCommands(cmds)    
}

// helpers -------------------

def parseIasMessage(ZoneStatus zs) {
        if ((zs.alarm1 || zs.alarm2) && zs.battery == 0 && zs.trouble == 0) {
            // Vibration detected
	        return handleVibration(true)
        }
        else if (zs.tamper == 1 && zs.battery == 1 && zs.trouble == 1 && zs.ac == 1) {
            logDebug "Device button pressed"
            map = [
		        name: 'pushed',
		        value: 1,
                isStateChange: true,
                descriptionText: "Device button pushed"
	        ]
        }
        else {
            if (infoLogging) log.warn "Zone status message not parsed"
            if (debugLogging) {
                logDebug "zs.alarm1 = $zs.alarm1"
                logDebug "zs.alarm2 = $zs.alarm2"
                logDebug "zs.tamper = $zs.tamper"
                logDebug "zs.battery = $zs.battery"
                logDebug "zs.supervisionReports = $zs.supervisionReports"
                logDebug "zs.restoreReports = $zs.restoreReports"
                logDebug "zs.trouble = $zs.trouble"
                logDebug "zs.ac = $zs.ac"
                logDebug "zs.test = $zs.test"
                logDebug "zs.batteryDefect = $zs.batteryDefect"
            }
        }
}

private handleVibration(vibrationActive) {    
    if (vibrationActive) {
        def timeout = vibrationReset ?: 3
        // The sensor only sends a vibration detected message so reset to vibration inactive is performed in code
        runIn(timeout, resetToVibrationInactive)        
        if (device.currentState('acceleration')?.value != "active") {
            state.vibrationStarted = now()
        }
    }
	return getVibrationResult(vibrationActive)
}

def getVibrationResult(vibrationActive) {
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

def resetToVibrationInactive() {
	if (device.currentState('acceleration')?.value == "active") {
		def descText = "Vibration reset to inactive after ${getSecondsInactive()}s"
		sendEvent(
			name : "acceleration",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
		logInfo(descText)
	}
}

def getSecondsInactive() {
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

// lifecycle methods -------------

// installed() runs just after a sensor is paired
def installed() {
	logInfo "Installing..."
    sendEvent(name: 'healthStatus', value: 'unknown')
    return refresh()
}

// configure() runs after installed() when a sensor is paired or reconnected
def configure() {
	logInfo("Configuring")
    return configureReporting()
}

def refresh() {
	logInfo("Refreshing...")
    List<String> cmds = []
    cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, [:], delay=200) // battery voltage
    cmds += zigbee.readAttribute(0x0500, 0x0013, [:], delay=200)    // sensitivity
    sendZigbeeCommands(cmds)
}


// updated() runs every time user saves preferences
def updated() {
	logInfo("Updating preference settings")
    return configureReporting()
}

// helpers -------------

private def configureReporting() {
    def seconds = Math.round((settings?.batteryReportingHours ?: 12)*3600)
    logInfo("Battery reporting frequency: ${seconds/3600}h")    
    
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200) 
    cmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, seconds-1, seconds, 0x00)
    cmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x20)
    // added 03/07/2023
    cmds += zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
    //
    if ( settings?.sensitivity != null ) {
    logDebug("Configuring vibration sensitivity to : ${settings?.sensitivity}")
            def iSens = settings.sensitivity?.toInteger()
            if (iSens>=0 && iSens<7)  {
                cmds += sendZigbeeCommands(zigbee.writeAttribute(0x0500, 0x0013,  DataType.UINT8, iSens))
            }    
    }
    sendZigbeeCommands(cmds)
}

void sendZigbeeCommands(List<String> cmds) {
    if (debugLogging) {log.trace "${device.displayName} sendZigbeeCommands received : ${cmds}"}
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

private def logDebug(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def logInfo(message) {
	if (infoLogging) log.info "${device.displayName}: ${message}"
}