/**
 *  Virtual Thermostat w/ Battery and HealthStatus - Device Driver for Hubitat Elevation
 *
 *  Based on Virtual Thermostat by Kevin L. (Kevin.L.), Mike M. (mike.maxwell) and Bruce R. from Hubitat Inc.
 *  Copyright 2016 -> 2023 Hubitat Inc.  All Rights Reserved
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
 *
 *  ver. 2.0.0  2025/10/31 kkossev  - first version : added forceEvents option; added refresh() method; 
 *  ver. 2.1.0  2025/11/01 kkossev  - removed automatic logic (manageCycle); all setter methods now work directly
 *                                    added battery capability and setBattery method; added healthStatus capability and setHealthStatus method
 *  ver. 2.1.1  2025/11/12 kkossev  - forceEvents preference hidden and isStateChange() forcibly set to true (all events should be sent!)
 *  ver. 2.1.2  2025/12/06 kkossev  - (dev. branch) added digital/physical event type to all setter methods
 * 
 *              TODO: 
 */

import groovy.transform.Field
import groovy.json.JsonOutput

static String version() { '2.1.2' }
static String timeStamp() { '2025/12/06 11:35 PM' }

@Field static final Boolean _DEBUG = true
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

metadata {
	definition (
			name: "Virtual Thermostat w/ Battery and HealthStatus",
			namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Drivers/Zigbee%20TRV/Virtual%20Thermostat%20(Battery%20and%20HealthStatus).groovy", singleThreaded: true
	) {
		capability "Actuator"
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Refresh"
		capability "Battery"
		capability "HealthCheck"

		attribute "supportedThermostatFanModes", "JSON_OBJECT"
		attribute "supportedThermostatModes", "JSON_OBJECT"
		attribute "hysteresis", "NUMBER"
		attribute "healthStatus", "ENUM", ["offline", "online"]

		// Commands needed to change internal attributes of virtual device.
		command "setTemperature", [[name: "Temperature", type: "NUMBER", description: "Temperature value"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setThermostatOperatingState", [[name: "Operating State", type: "ENUM", description: "Thermostat operating state"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setThermostatSetpoint", [[name: "Setpoint", type: "NUMBER", description: "Thermostat setpoint temperature"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setThermostatMode", [[name: "Mode", type: "ENUM", constraints: ["auto", "cool", "emergency heat", "heat", "off"], description: "Thermostat mode"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setThermostatFanMode", [[name: "Fan Mode", type: "ENUM", constraints: ["auto", "circulate", "on"], description: "Thermostat fan mode"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setHeatingSetpoint", [[name: "Heating Setpoint", type: "NUMBER", description: "Heating setpoint temperature"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setCoolingSetpoint", [[name: "Cooling Setpoint", type: "NUMBER", description: "Cooling setpoint temperature"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setSupportedThermostatFanModes", ["JSON_OBJECT"]
		command "setSupportedThermostatModes", ["JSON_OBJECT"]
		command "setBattery", [[name: "Battery Level", type: "NUMBER", description: "Battery percentage (0-100)"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
		command "setHealthStatus", [[name: "Health Status", type: "ENUM", constraints: ["offline", "online"], description: "Set device health status"], [name: "Event Type", type: "ENUM", constraints: ["digital", "physical"], description: "Event type (optional, defaults to digital)"]]
	}

	preferences {
		input( name: "hysteresis",type:"enum",title: "Thermostat hysteresis degrees", options:["0.1","0.25","0.5","1","2"], description:"", defaultValue: 0.5)
		//input( name: "forceEvents", type:"bool", title: "Force events even when values don't change", description: "Send events even when attribute values haven't changed", defaultValue: true)
		input( name: "logEnable", type:"bool", title: "Enable debug logging",defaultValue: false)
		input( name: "txtEnable", type:"bool", title: "Enable descriptionText logging", defaultValue: true)
	}
}

def installed() {
	log.warn "installed..."
	initialize()
}

def updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${txtEnable == true}"
	if (logEnable) runIn(1800,logsOff)
	initialize()
}

def initialize() {
	if (state?.lastRunningMode == null) {
		// Set initial values without forcing relationships
		sendEvent(name: "temperature", value: convertTemperatureIfNeeded(68.0,"F",1))
		sendEvent(name: "thermostatSetpoint", value: convertTemperatureIfNeeded(68.0,"F",1))
		sendEvent(name: "heatingSetpoint", value: convertTemperatureIfNeeded(68.0,"F",1))
		sendEvent(name: "coolingSetpoint", value: convertTemperatureIfNeeded(75.0,"F",1))
		sendEvent(name: "thermostatMode", value: "heat")
		sendEvent(name: "thermostatOperatingState", value: "idle")
		sendEvent(name: "thermostatFanMode", value: "auto")
		sendEvent(name: "battery", value: 100, unit: "%")
		sendEvent(name: "healthStatus", value: "online")
		
		// Set supported modes
		setSupportedThermostatFanModes(JsonOutput.toJson(["auto","circulate","on"]))
		setSupportedThermostatModes(JsonOutput.toJson(["auto", "cool", "emergency heat", "heat", "off"]))
		
		// Initialize state for first run detection
		state.lastRunningMode = "initialized"
		updateDataValue("lastRunningMode", "initialized")
	}
	sendEvent(name: "hysteresis", value: (hysteresis ?: 0.5).toBigDecimal())
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

// Commands needed to change internal attributes of virtual device.
def setTemperature(temperature, eventType = 'digital') {
	logDebug "setTemperature(${temperature}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendTemperatureEvent("temperature", temperature, validatedEventType)
}

def setHumidity(humidity, eventType = 'digital') {
	logDebug "setHumidity(${humidity}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "humidity", value: humidity, unit: "%", type: validatedEventType, descriptionText: getDescriptionText("humidity set to ${humidity}%", validatedEventType), isStateChange: getIsStateChange())
}

def setBattery(batteryLevel, eventType = 'digital') {
	logDebug "setBattery(${batteryLevel}) was called"
	
	// Validate battery level range
	def validatedLevel = batteryLevel as int
	if (validatedLevel < 0) validatedLevel = 0
	if (validatedLevel > 100) validatedLevel = 100
	
	// Validate event type
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	
	sendEvent(name: "battery", value: validatedLevel, unit: "%", type: validatedEventType, descriptionText: getDescriptionText("battery level set to ${validatedLevel}%", validatedEventType), isStateChange: getIsStateChange())
}

def setHealthStatus(healthStatus, eventType = 'digital') {
	logDebug "setHealthStatus(${healthStatus}) was called"
	
	// Validate health status
	def validStatus = healthStatus?.toString()?.toLowerCase()
	if (!(validStatus in ["online", "offline"])) {
		logWarn "Invalid health status '${healthStatus}', defaulting to 'online'"
		validStatus = "online"
	}
	
	// Validate event type
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	
	sendEvent(name: "healthStatus", value: validStatus, type: validatedEventType, descriptionText: getDescriptionText("health status set to ${validStatus}", validatedEventType), isStateChange: getIsStateChange())
}

def setThermostatOperatingState(operatingState, eventType = 'digital') {
	logDebug "setThermostatOperatingState(${operatingState}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "thermostatOperatingState", value: operatingState, type: validatedEventType, descriptionText: getDescriptionText("thermostatOperatingState set to ${operatingState}", validatedEventType), isStateChange: getIsStateChange())
}

def setSupportedThermostatFanModes(fanModes) {
	logDebug "setSupportedThermostatFanModes(${fanModes}) was called"
	// (auto, circulate, on)
	sendEvent(name: "supportedThermostatFanModes", value: fanModes, descriptionText: getDescriptionText("supportedThermostatFanModes set to ${fanModes}"), isStateChange: getIsStateChange())
}

def setSupportedThermostatModes(modes) {
	logDebug "setSupportedThermostatModes(${modes}) was called"
	// (auto, cool, emergency heat, heat, off)
	sendEvent(name: "supportedThermostatModes", value: modes, descriptionText: getDescriptionText("supportedThermostatModes set to ${modes}"), isStateChange: getIsStateChange())
}


def auto() { setThermostatMode("auto") }

def cool() { setThermostatMode("cool") }

def emergencyHeat() { setThermostatMode("heat") }

def heat() { setThermostatMode("heat") }
def off() { setThermostatMode("off") }

def setThermostatMode(mode, eventType = 'digital') {
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "thermostatMode", value: "${mode}", type: validatedEventType, descriptionText: getDescriptionText("thermostatMode is ${mode}", validatedEventType), isStateChange: getIsStateChange())
}

def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }

def setThermostatFanMode(fanMode, eventType = 'digital') {
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "thermostatFanMode", value: "${fanMode}", type: validatedEventType, descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}", validatedEventType), isStateChange: getIsStateChange())
}

def setThermostatSetpoint(setpoint, eventType = 'digital') {
	logDebug "setThermostatSetpoint(${setpoint}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "thermostatSetpoint", value: setpoint, unit: "°${getTemperatureScale()}", type: validatedEventType, descriptionText: getDescriptionText("thermostatSetpoint set to ${setpoint}°${getTemperatureScale()}", validatedEventType), isStateChange: getIsStateChange())
}

def setCoolingSetpoint(setpoint, eventType = 'digital') {
	logDebug "setCoolingSetpoint(${setpoint}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "coolingSetpoint", value: setpoint, unit: "°${getTemperatureScale()}", type: validatedEventType, descriptionText: getDescriptionText("coolingSetpoint set to ${setpoint}°${getTemperatureScale()}", validatedEventType), isStateChange: getIsStateChange())
}

def setHeatingSetpoint(setpoint, eventType = 'digital') {
	logDebug "setHeatingSetpoint(${setpoint}) was called"
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "heatingSetpoint", value: setpoint, unit: "°${getTemperatureScale()}", type: validatedEventType, descriptionText: getDescriptionText("heatingSetpoint set to ${setpoint}°${getTemperatureScale()}", validatedEventType), isStateChange: getIsStateChange())
}

def setSchedule(schedule) {
	sendEvent(name: "schedule", value: "${schedule}", descriptionText: getDescriptionText("schedule is ${schedule}"), isStateChange: getIsStateChange())
}

def ping() {
	logDebug "ping() was called"
	sendEvent(name: "healthStatus", value: "online", descriptionText: getDescriptionText("ping received - device is online"), isStateChange: getIsStateChange())
	return "pong"
}

def refresh() {
	logDebug "refresh() was called"
	
	// Send all current attribute values as refresh events
	def currentTemp = device.currentValue("temperature")
	if (currentTemp != null) {
		sendEvent(name: "temperature", value: currentTemp, unit: "°${getTemperatureScale()}", 
				  descriptionText: getRefreshDescriptionText("temperature is ${currentTemp} °${getTemperatureScale()}"), isStateChange: true)
	}
	
	def currentThermostatSetpoint = device.currentValue("thermostatSetpoint")
	if (currentThermostatSetpoint != null) {
		sendEvent(name: "thermostatSetpoint", value: currentThermostatSetpoint, unit: "°${getTemperatureScale()}", 
				  descriptionText: getRefreshDescriptionText("thermostatSetpoint is ${currentThermostatSetpoint} °${getTemperatureScale()}"), isStateChange: true)
	}
	
	def currentHeatingSetpoint = device.currentValue("heatingSetpoint")
	if (currentHeatingSetpoint != null) {
		sendEvent(name: "heatingSetpoint", value: currentHeatingSetpoint, unit: "°${getTemperatureScale()}", 
				  descriptionText: getRefreshDescriptionText("heatingSetpoint is ${currentHeatingSetpoint} °${getTemperatureScale()}"), isStateChange: true)
	}
	
	def currentCoolingSetpoint = device.currentValue("coolingSetpoint")
	if (currentCoolingSetpoint != null) {
		sendEvent(name: "coolingSetpoint", value: currentCoolingSetpoint, unit: "°${getTemperatureScale()}", 
				  descriptionText: getRefreshDescriptionText("coolingSetpoint is ${currentCoolingSetpoint} °${getTemperatureScale()}"), isStateChange: true)
	}
	
	def currentThermostatMode = device.currentValue("thermostatMode")
	if (currentThermostatMode != null) {
		sendEvent(name: "thermostatMode", value: currentThermostatMode, 
				  descriptionText: getRefreshDescriptionText("thermostatMode is ${currentThermostatMode}"), isStateChange: true)
	}
	
	def currentThermostatOperatingState = device.currentValue("thermostatOperatingState")
	if (currentThermostatOperatingState != null) {
		sendEvent(name: "thermostatOperatingState", value: currentThermostatOperatingState, 
				  descriptionText: getRefreshDescriptionText("thermostatOperatingState is ${currentThermostatOperatingState}"), isStateChange: true)
	}
	
	def currentThermostatFanMode = device.currentValue("thermostatFanMode")
	if (currentThermostatFanMode != null) {
		sendEvent(name: "thermostatFanMode", value: currentThermostatFanMode, 
				  descriptionText: getRefreshDescriptionText("thermostatFanMode is ${currentThermostatFanMode}"), isStateChange: true)
	}
	
	def currentSupportedThermostatFanModes = device.currentValue("supportedThermostatFanModes")
	if (currentSupportedThermostatFanModes != null) {
		sendEvent(name: "supportedThermostatFanModes", value: currentSupportedThermostatFanModes, 
				  descriptionText: getRefreshDescriptionText("supportedThermostatFanModes is ${currentSupportedThermostatFanModes}"), isStateChange: true)
	}
	
	def currentSupportedThermostatModes = device.currentValue("supportedThermostatModes")
	if (currentSupportedThermostatModes != null) {
		sendEvent(name: "supportedThermostatModes", value: currentSupportedThermostatModes, 
				  descriptionText: getRefreshDescriptionText("supportedThermostatModes is ${currentSupportedThermostatModes}"), isStateChange: true)
	}
	
	def currentHysteresis = device.currentValue("hysteresis")
	if (currentHysteresis != null) {
		sendEvent(name: "hysteresis", value: currentHysteresis, 
				  descriptionText: getRefreshDescriptionText("hysteresis is ${currentHysteresis}"), isStateChange: true)
	}
	
	def currentSchedule = device.currentValue("schedule")
	if (currentSchedule != null) {
		sendEvent(name: "schedule", value: currentSchedule, 
				  descriptionText: getRefreshDescriptionText("schedule is ${currentSchedule}"), isStateChange: true)
	}
	
	// Also refresh humidity if it exists
	def currentHumidity = device.currentValue("humidity")
	if (currentHumidity != null) {
		sendEvent(name: "humidity", value: currentHumidity, unit: "%", 
				  descriptionText: getRefreshDescriptionText("humidity is ${currentHumidity}%"), isStateChange: true)
	}
	
	// Also refresh battery level
	def currentBattery = device.currentValue("battery")
	if (currentBattery != null) {
		sendEvent(name: "battery", value: currentBattery, unit: "%", 
				  descriptionText: getRefreshDescriptionText("battery is ${currentBattery}%"), isStateChange: true)
	}
	
	// Also refresh health status
	def currentHealthStatus = device.currentValue("healthStatus")
	if (currentHealthStatus != null) {
		sendEvent(name: "healthStatus", value: currentHealthStatus, 
				  descriptionText: getRefreshDescriptionText("healthStatus is ${currentHealthStatus}"), isStateChange: true)
	}
	
	logInfo "All attributes refreshed"
}

private sendTemperatureEvent(name, val, eventType = 'digital') {
	def validatedEventType = (eventType in ['digital', 'physical']) ? eventType : 'digital'
	sendEvent(name: "${name}", value: val, unit: "°${getTemperatureScale()}", type: validatedEventType, descriptionText: getDescriptionText("${name} is ${val} °${getTemperatureScale()}", validatedEventType), isStateChange: getIsStateChange())
}

private getIsStateChange() {
	return true
//	return settings?.forceEvents == true ? true : null
}

def parse(String description) {
	logDebug "$description"
}

private logDebug(msg) {
	if (settings?.logEnable) log.debug "${msg}"
}

private logInfo(msg) {
	if (settings?.txtEnable) log.info "${msg}"
}

private logWarn(msg) {
	log.warn "${msg}"
}

private getDescriptionText(msg, eventType = null) {
	def descriptionText = "${device.displayName} ${msg}"
	if (eventType != null && eventType in ['digital', 'physical']) {
		descriptionText += " [${eventType}]"
	}
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}

private getRefreshDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg} [Refresh]"
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}