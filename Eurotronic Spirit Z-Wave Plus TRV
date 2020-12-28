/**
 *                      Eurotronic Spirit Z-Wave Plus TRV         version 2.0.0.0
 *
 * ... just another Eurotronic Z-Wave TRV dirver for Hubutat...
 *  Based on the work of lolcutus, Patrick Wogan, rboy.. and many others.
 *  
 *	Author: 
 *      Krassimir Kossev (Trakker2)
 *
 *	Changelog:
 *
 *  2.0.0.0    (12/28/2020)
 *    - the initial version of the driver. It is fully based on lolcutus code of "Z-Wave - Eurotronics Spirit Z-Wave Plus Thermostat" version 1.0.4.0005
 *    - LCDTimeout range was extended to allow 0 value ( LCD will not timeout! )
 *    - temperature, "valve", thermostatOperatingState, heatingSetpoint, coolingSetpoint, thermostatFanState, thermostatMode, thermostatFanMode, supportedThermostatModes
 *           .. events were modified adding "isStateChange = true" in order to be stored in Hubitat and the data to be available for reporting in HubiGraphs...
 *    - 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ******************************************* KK mod *********************************************************************
 */
metadata {
	definition (name: "Eurotronic Spirit Z-Wave Plus TRV", namespace: "kkossev", author: "trakker2", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Eurotronic%20Spirit%20Z-Wave%20Plus%20TRV") {
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Configuration"
		capability "Polling"
		capability "Sensor"
		capability "Refresh"
		capability "Battery"
		
		command "resetBatteryReplacedDate"
		command "pollBattery"
        command "setTemperatureOffset"
		
		attribute "batteryLastReplaced", "Date"
		attribute "valve", "String"
		
		fingerprint deviceId: "0x01"
		fingerprint manufacturerId: "328"
		fingerprint inClusters: "0x5E,0x85,0x59,0x86,0x72,0x5A,0x75,0x31,0x26,0x40,0x43,0x80,0x70,0x71,0x73,0x98,0x9F,0x55,0x6C,0x7A"
	}
}

preferences {
	input(name: "debugLogging", type: "bool", title: "Enable debug logging", description: "" , defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
	input(name: "infoLogging", type: "bool", title: "Enable info logging", description: "", defaultValue: true, submitOnChange: true, displayDuringSetup: false, required: false)
	input(name: "showBatteryInfo", type: "bool", title: "Show battery messages in log", description: "", defaultValue: true, submitOnChange: true, displayDuringSetup: false, required: false)
	input "LCDinvert", "enum", title: "Invert LCD", options: ["No", "Yes"], defaultValue: "No", required: false, displayDuringSetup: true
	input "LCDtimeout", "number", title: "LCD Timeout (in secs)", description: "LCD will switch off after this time (5 - 30secs), 0 to disable", range: "0..30", displayDuringSetup: true
	input "LCDBackgroundLight", "enum", title: "LCD background light", options: ["No", "Yes"], defaultValue: "No", required: true, displayDuringSetup: true
	input "BatteryStatus", "enum", title: "Battery status", options: ["Only when low", "One time per day"], defaultValue: "Only when low", required: true, displayDuringSetup: true
	input "TemperatureReport", "decimal", title: "Temperature report", description: "0 not send range 0,1 to 5 ", range: "0.1..5", displayDuringSetup: true
	input "ValveReporting", "enum", title: "Report valve", options: ["No", "Yes"], defaultValue: "No", required: true, displayDuringSetup: true
	input "windowOpen", "enum", title: "Window Open Detection",description: "Sensitivity of Open Window Detection", options: ["Disabled", "Low", "Medium", "High" ], defaultValue: "Disabled", required: false, displayDuringSetup: false
	input(name: "tempOffset", type: "decimal", title: "Temperature Offset", description: "Adjust the temperature by this many degrees.", displayDuringSetup: true, required: false, range: "-5..5")
} 

 private setVersion(){
	def map = [:]
 	map.name = "driver"
	map.value = "v2.0.0.0000"
	updateDataValue(map.name,map.value)
 }

def configure() {
	setVersion()
 	if(device.currentValue("batteryLastReplaced") == null){
		 resetBatteryReplacedDate()
	}
	def cmds = []
	cmds << zwave.configurationV1.configurationSet(configurationValue: LCDinvert == "Yes" ? [0x01] : [0x00], parameterNumber:1, size:1, scaledConfigurationValue:  LCDinvert == "Yes" ? 0x01 : 0x00)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:1)
	cmds << zwave.configurationV1.configurationSet(configurationValue: LCDtimeout == null ? [0] : [LCDtimeout], parameterNumber:2, size:1, scaledConfigurationValue: LCDtimeout == null ? 0 :  LCDtimeout)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:2)
	cmds << zwave.configurationV1.configurationSet(configurationValue: LCDBackgroundLight == "Yes" ? [0x01] : [0x00], parameterNumber:3, size:1, scaledConfigurationValue:  LCDBackgroundLight == "Yes" ? 0x01 : 0x00)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:3)
	cmds << zwave.configurationV1.configurationSet(configurationValue: BatteryStatus == "One time per day" ? [0x01] : [0x00], parameterNumber:4, size:1, scaledConfigurationValue:  BatteryStatus == "One time per day" ? 0x01 : 0x00)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:4)
	cmds << zwave.configurationV1.configurationSet(configurationValue: TemperatureReport == null ? [0] : [TemperatureReport*10], parameterNumber:5, size:1, scaledConfigurationValue: TemperatureReport == null ? 0 :  TemperatureReport*10)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:5)
	cmds << zwave.configurationV1.configurationSet(configurationValue: ValveReporting == "Yes" ? [0x01] : [0x00], parameterNumber:6, size:1, scaledConfigurationValue:  ValveReporting == "Yes" ? 0x01 : 0x00)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:6)
	cmds << zwave.configurationV1.configurationSet(configurationValue: windowOpen == "Low" ? [0x01] : windowOpen == "Medium" ? [0x02] : windowOpen == "High" ? [0x03] : [0x00], parameterNumber:7, size:1, scaledConfigurationValue:  windowOpen == "Low" ? 0x01 : windowOpen == "Medium" ? 0x02 : windowOpen == "High" ? 0x03 : 0x00)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:7)
	cmds << zwave.configurationV1.configurationSet(configurationValue: tempOffset == null ? [0] : [tempOffset*10], parameterNumber:8, size:1, scaledConfigurationValue: tempOffset == null ? 0 : tempOffset*10)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:8)
	
	sendCommands(cmds,standardBigDelay)  
 }
 
def poll() {
	debugLog("Polling....")
	def cmds = []
	cmds << zwave.sensorMultilevelV1.sensorMultilevelGet() //temperature
	cmds << zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1)
	cmds << zwave.thermostatModeV2.thermostatModeGet()
	cmds << zwave.switchMultilevelV3.switchMultilevelGet() //valve
	cmds << zwave.batteryV1.batteryGet()
	sendCommands(cmds,standardBigDelay)
}

def pollBattery() {
	debugLog("Polling battery....")
	def cmds = []
	cmds << zwave.batteryV1.batteryGet()
	sendCommands(cmds,standardBigDelay)
}

def parse(String description)
{
	debugLog("Parsing '${description}'")
	def cmd = zwave.parse(description, [0x42:1, 0x43:2, 0x31: 3])
	if(!cmd){
		warnLog("Non-parsed event: ${description}")
		return null
	}
	debugLog("Command ${cmd}")
	def event =zwaveEvent(cmd)
	if(event){
		sendEvent(event)
		infoLog("Log event: ${event}")
	}
}

private resetBatteryReplacedDate() {
	sendEvent(name: "batteryLastReplaced", value: new Date())
}

private setTemperatureOffset(){
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: tempOffset == null ? [0] : [tempOffset*10], parameterNumber:8, size:1, scaledConfigurationValue: tempOffset == null ? 0 : tempOffset*10)
	cmds << zwave.configurationV1.configurationGet(parameterNumber:8)
	
	sendCommands(cmds,standardBigDelay)  
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
	debugLog("Received switchmultilevelv3.SwitchMultilevelReport - ${cmd}")
	def map = [:]
	map.name = "valve"
	map.value = cmd.value
	map.unit = "%"
    map.isStateChange = true
	debugLog("Valve open '${cmd.value}'%")
	def map2 = [:]
	if(cmd.value == 0){
		map2.value = "idle" 
	}else{
		map2.value = "heating" 
	}
	map2.name = "thermostatOperatingState"
    map2.isStateChange = true
	infoLog(map2)
	sendEvent(map2)
    map.name = "level"
  map
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd ) {
	infoLog("Recived configuration ${cmd}")
}

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	debugLog("Received thermostatsetpointv2.ThermostatSetpointReport - ${cmd}")
	def cmdScale = cmd.scale == 1 ? "F" : "C"
	def map = [:]
	map.value = convertTemperatureIfNeeded(cmd.scaledValue, cmdScale, cmd.precision)
	map.unit = getTemperatureScale()
    map.isStateChange = true
	switch (cmd.setpointType) {
		case 1:
			map.name = "heatingSetpoint"
			break;
		case 2:
			map.name = "coolingSetpoint"
			break;
		default:
			return [:]
	}
	map
}

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	debugLog("Received sensormultilevelv2.SensorMultilevelReport - ${cmd}")
	def map = [:]
	if (cmd.sensorType == 1) {
		map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "F" : "C", cmd.precision)
		map.unit = getTemperatureScale()
		map.name = "temperature"
        map.isStateChange = true
	} else if (cmd.sensorType == 5) {
		map.value = cmd.scaledSensorValue
		map.unit = "%"
		map.name = "humidity"
        map.isStateChange = true
	}
	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
{
	debugLog("Received thermostatoperatingstatev1.ThermostatOperatingStateReport - ${cmd}")
	def map = [:]
	switch (cmd.operatingState) {
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
			map.value = "idle"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
			map.value = "heating"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_COOLING:
			map.value = "cooling"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_FAN_ONLY:
			map.value = "fan only"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT:
			map.value = "pending heat"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_COOL:
			map.value = "pending cool"
			break
		case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_VENT_ECONOMIZER:
			map.value = "vent economizer"
			break
	}
	map.name = "thermostatOperatingState"
    map.isStateChange = true
 	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanstatev1.ThermostatFanStateReport cmd) {
	debugLog("Received thermostatoperatingstatev1.ThermostatFanStateReport - ${cmd}")
	def map = [name: "thermostatFanState", unit: ""]
	switch (cmd.fanOperatingState) {
		case 0:
			map.value = "idle"
			break
		case 1:
			map.value = "running"
			break
		case 2:
			map.value = "running high"
			break
	}
    map.isStateChange = true
	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
	debugLog("Received thermostatmodev2.ThermostatModeReport - ${cmd}")
	def map = [:]
	switch (cmd.mode) {
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:
			map.value = "off"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:
			map.value = "heat"
			break
		case 15:
			map.value = "emergency heat"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:
			map.value = "cool"
			break
		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:
			map.value = "auto"
			break
	}
	map.name = "thermostatMode"
    map.isStateChange = true
	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport cmd) {
	debugLog("Received thermostatmodev2.ThermostatFanModeReport - ${cmd}")
	def map = [:]
	switch (cmd.fanMode) {
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_AUTO_LOW:
			map.value = "fanAuto"
			break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_LOW:
			map.value = "fanOn"
			break
		case hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeReport.FAN_MODE_CIRCULATION:
			map.value = "fanCirculate"
			break
	}
	map.name = "thermostatFanMode"
    map.isStateChange = true
 	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
	debugLog("Received thermostatmodev2.ThermostatModeSupportedReport - ${cmd}")
	def map = [:]
	def supportedModes = [ 	]
	if(cmd.off) { supportedModes << "off " }
	if(cmd.heat) { supportedModes << "heat " }
	if(cmd.auxiliaryemergencyHeat) { supportedModes << "emergency heat " }
	if(cmd.cool) { supportedModes << "cool " }
	if(cmd.auto) { supportedModes << "auto " }
	
	if(supportedModes.size() == 0){
		supportedModes= modes()
	}
	map.value = supportedModes
	map.name = "supportedThermostatModes"
    map.isStateChange = true
	map
}

def zwaveEvent(hubitat.zwave.commands.thermostatfanmodev3.ThermostatFanModeSupportedReport cmd) {
	debugLog("Received thermostatfanmodev3.ThermostatFanModeSupportedReport - ${cmd}")
	def supportedFanModes = ""
	if(cmd.auto) { supportedFanModes += "fanAuto " }
	if(cmd.low) { supportedFanModes += "fanOn " }
	if(cmd.circulation) { supportedFanModes += "fanCirculate " }
}

def zwaveEvent(hubitat.zwave.commands.multiinstancev1.MultiInstanceCmdEncap cmd) {   
	traceLog("multiinstancev1.MultiInstanceCmdEncap: command: ${cmd}")
	
	def encapsulatedCommand = cmd.encapsulatedCommand([0x31: 2])
	debugLog( ("multiinstancev1.MultiInstanceCmdEncap: command from instance ${cmd.instance}: ${encapsulatedCommand}"))
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}
def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	def nowTime = new Date().time
	def map = [ name: "battery", unit: "%" ]
	isStateChanged = true
	map.displayed = true
	if (cmd.batteryLevel == 0xFF || cmd.batteryLevel == 0) {
		map.value = 1
		map.descriptionText = "battery is low!"
	} else {
		map.value = cmd.batteryLevel
	}
	map.isStateChanged = true
	infoLog(map,showBatteryInfo)
	map
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	warnLog("Zwave event received: $cmd")
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	warnLog( "Unexpected zwave command $cmd")
}

def setHeatingSetpoint(degrees, delay = standardBigDelay) {
	setHeatingSetpoint(degrees.toDouble(), delay)
}

def setHeatingSetpoint(Double degrees, Integer delay = standardBigDelay) {
	def deviceScale =  1
	def deviceScaleString = deviceScale == 2 ? "C" : "F"
	def locationScale = getTemperatureScale()
	def p = 1

	def convertedDegrees
	if (locationScale == "C" && deviceScaleString == "F") {
		convertedDegrees = celsiusToFahrenheit(degrees)
	} else if (locationScale == "F" && deviceScaleString == "C") {
		convertedDegrees = fahrenheitToCelsius(degrees)
	} else {
		convertedDegrees = degrees
	}
	
	def cmds = []
	cmds << zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: deviceScale, precision: p, scaledValue: convertedDegrees)
	cmds << zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1)
	
	sendCommands(cmds,delay)
}

def modes() {
	["off", "heat", "emergency heat"]
}

def getModeMap() { [
	"off": 0,
	"heat": 1,
	"emergency heat": 15
]}

def setThermostatMode(String value) {
	if(modeMap.containsKey(value)){
		def cmds = []
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: modeMap[value])
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		sendCommands(cmds)
	}else{
		warnLog("Mode '${value}' not supported!")
	}
}


def off() {
	def cmds = []
	cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0)
	cmds << zwave.thermostatModeV2.thermostatModeGet()
	sendCommands(cmds)
}

def heat() {
	def cmds = []
	cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1)
	cmds << zwave.thermostatModeV2.thermostatModeGet()
	sendCommands(cmds)
}

def emergencyHeat() {
	def cmds = []
	cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 15)
	cmds << zwave.thermostatModeV2.thermostatModeGet()
	sendCommands(cmds)
}

def auto() {
	warnLog("Auto not supported")
}

def pollDevice(){
    poll()
}



private getStandardDelay() {
	1000
}

private getStandardBigDelay() {
	3000
}


def refresh() {
  poll()
}


def debugLog(msg){
	if(debugLogging == true){
		   log.debug "["+device.getLabel() + "] " + msg
	}
}

def infoLog(msg,forced = false){
	if(infoLogging == true || forced){
		   log.info "[" + device.getLabel() + "] " + msg
	}
}
def warnLog(msg){
	log.warn "[" + device.getLabel() + "] " + msg
}

def traceLog(msg){
	log.trace "[" + device.getLabel() + "] " + msg
}

def fanAuto(){
	warnLog("Fan not supported")
}

def fanCirculate(){
	warnLog("Fan not supported")
}

def fanOn(){
	warnLog("Fan not supported")
}

def setThermostatFanMode(value){
	warnLog("Fan not supported")
}

def cool(){
	warnLog("Cool not supported")
}

def setCoolingSetpoint(value){
	warnLog("Cool not supported")
}

private sendCommands(cmds,delay = standardDelay) {
	debugLog(cmds)
	delayBetween(cmds.collect{ secure(it) }, delay)
}

private secure(hubitat.zwave.Command cmd) {
	if (state.sec) {
		debugLog("Secured: " + cmd.format())
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		debugLog("Not secured: " + cmd.format())
		return cmd.format()
	}
}

	
