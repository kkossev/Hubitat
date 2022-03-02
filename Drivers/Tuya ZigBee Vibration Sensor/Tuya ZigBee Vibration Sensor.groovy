/**
 *  Tuya ZigBee Vibration Sensor
 *  Device Driver for Hubitat Elevation hub
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
 * ver 1.0.4 2022-03-03 kkossev - (development branch)
 */

def version() { "1.0.4" }
def timeStamp() {"2022/03/02 10:24 PM"}

// https://community.hubitat.com/t/tuya-vibration-sensor/75269

import hubitat.zigbee.clusters.iaszone.ZoneStatus
import com.hubitat.zigbee.DataType

metadata {
	definition (name: "Tuya ZigBee Vibration Sensor", namespace: "kkossev", author: "Krassimir Kossev") {
        capability "Sensor"
        capability "AccelerationSensor"
		capability "Battery"
		capability "Configuration"
        capability "Refresh"
        
		fingerprint endpointId: "01", profileId: "0104", inClusters: "0000,000A,0001,0500", outClusters: "0019", manufacturer: "_TYZB01_3zv6oleo", model: "TS0210"      
	}

	preferences {
		input "vibrationReset", "number", title: "After vibration is detected, wait ___ second(s) until resetting to inactive state. Default = 3 seconds (Hardware resets at 2 seconds)", description: "", range: "1..7200", defaultValue: 3
        input "batteryReportingHours", "number", title: "Report battery every ___ hours. Default = 12h (Minimum 2 h)", description: "", range: "2..12", defaultValue: 12
		
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
	}
}

// Parse incoming device messages to generate events
def parse(String description) {

    Map map = [:]
    logDebug("Parsing: $description")
	if (description?.startsWith('zone status')) {	
        logDebug("Zone status: $description")    
        def zs = zigbee.parseZoneStatus(description)
        map = parseIasMessage(zs)
    }
    else if (description?.startsWith("catchall") || description?.startsWith("read attr"))
    {
        Map descMap = zigbee.parseDescriptionAsMap(description)        
        if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0020) {
            map = parseBattery(descMap.value)
        } else if (descMap.command == "07") {
            // Process "Configure Reporting" response            
            if (descMap.data[0] == "00") {
                switch (descMap.clusterInt) {
                    case zigbee.POWER_CONFIGURATION_CLUSTER:
                        logInfo("Battery reporting configured");                        
                        break
                    default:                    
                        log.warn("Unknown reporting configured: ${descMap}");
                        break
                }
            } else {
                log.warn "Reporting configuration failed: ${descMap}"
            }
        } else if (descMap.clusterInt == 0x0500 && descMap.attrInt == 0x0002) {
            logDebug("Zone status repoted: $descMap")
            def zs = new ZoneStatus(Integer.parseInt(descMap.value, 16))
            map = parseIasMessage(zs)        
        } else if (descMap.clusterInt == 0x0500 && descMap.attrInt == 0x0011) {
            logInfo("IAS Zone ID: ${descMap.value}")
        } else if (descMap.profileId == "0000") {
            // ignore routing table messages
        } else {
            if (debugLogging) log.warn ("Description map not parsed: $descMap")            
        }
    }
    //------IAS Zone Enroll request------//
	else if (description?.startsWith('enroll request')) {
		logInfo "Sending IAS enroll response..."
		return zigbee.enrollResponse()
	}
    else {
        if (debugLogging) log.warn "Description not parsed: $description"
    }
    
    if (map != [:]) {
		logInfo(map.descriptionText)
		return createEvent(map)
	} else
		return [:]
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
            log.warn "Zone status message not parsed"
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
            isStateChange   : true,
			descriptionText : descriptionText
	]
   // } else return [:]
}

def resetToVibrationInactive() {
    //log.trace "resetToVibrationInactive device.currentState('acceleration')?.value == ${device.currentState('acceleration')?.value}"
	if (device.currentState('acceleration')?.value == "active") {
		def descText = "Vibration reset to inactive after ${getSecondsInactive()}s"
		sendEvent(
			name : 'accelaration',
			value : 'inactive',
			isStateChange : true,
			descriptionText : descText
		)
		logInfo(descText)
	}
}
/*
def setActive() {
    accelerationActive()
}
def setInactive() {
    accelerationInactive()
}

def accelerationActive() {
    def descriptionText = "${device.displayName} acceleration is active"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "acceleration", value: "active", descriptionText: descriptionText)
}

def accelerationInactive() {
    def descriptionText = "${device.displayName} acceleration is inactive"
    if (txtEnable) log.info "${descriptionText}"
    sendEvent(name: "acceleration", value: "inactive", descriptionText: descriptionText)
}
*/

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
	logInfo("Installing")    
	sendEvent(name: "numberOfButtons", value: 1, descriptionText: "Device installed")
    return refresh()
}

// configure() runs after installed() when a sensor is paired or reconnected
def configure() {
	logInfo("Configuring")
    
    return configureReporting()
}

def refresh() {
	logInfo("Refreshing")

    return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) // battery voltage
}

// updated() runs every time user saves preferences
def updated() {
	logInfo("Updating preference settings")
    
    return configureReporting()
}

// helpers -------------

private def configureReporting() {
    def seconds = Math.round((batteryReportingHours ?: 12)*3600)
    
    logInfo("Battery reporting frequency: ${seconds/3600}h")    
    
    return zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, seconds, seconds, 0x01)
        + zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x20)
}

private def logDebug(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def logInfo(message) {
	if (infoLogging)
		log.info "${device.displayName}: ${message}"
}