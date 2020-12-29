/**
		 *  Aeotec and Eurotronic Spirit TRV + DVC
		 *
		 *  Copyright 2020 Patrick Wogan
         *      Modified for Hubitat and for Aeotec eTRV by Scruffy-Sjb 
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
		 ***************************Version 1.5 - Based on original ST Version 17******************************************
		 *
         * Version 1.6 - Commented out Lock capability - simply uncomment line 27 if you want it back....
         *               Removed autooff command as was not referenced in the code
         *
		 */

		metadata {
			definition (name: "Aeotec eTRV", namespace: "scruffy-sjb", author: "Patrick Wogan and Scruffy-SJB", cstHandler: true, ocfDeviceType: "oic.d.thermostat", vid: "generic-thermostat-1") {
				capability "Actuator"
				capability "Sensor"
				capability "Battery"
				// capability "Lock"
				capability "Notification"
				capability "Switch"
				capability "Switch Level"
				capability "Temperature Measurement"
                capability "Thermostat"
				capability "Configuration"
				capability "Health Check"
				capability "Refresh"

				command "booston"
				command "boostoff"
				command "ecoon"
				command "ecooff"
                command "froston"
				command "frostoff"
				command "temperatureUp"
				command "temperatureDown"
				command "dvcon"
				command "dvcoff"
				command "poll"
				command "resetBatteryReplacedDate"
                
				attribute "minHeatingSetpoint", "number" // google / amazon
				attribute "maxHeatingSetpoint", "number" // google / amazon
				attribute "thermostatTemperatureSetpoint", "String"	//need for google
				attribute "applicationVersion", "String"
				attribute "zWaveLibraryType", "String"
                attribute "batteryLastReplaced", "String"                
                attribute "nextHeatingSetpoint", "number"
                attribute "heatingSetpoint", "number"
				
			fingerprint manufacturerId: "371"
			fingerprint deviceId : 0x15, mfr: 0x0371, deviceType: 0002, deviceTypeID: 0x17, cc: "0x5E,0x55,0x98", inClusters : "0x5E,0x85,0x59,0x86,0x72,0x5A,0x75,0x31,0x26,0x40,0x43,0x80,0x70,0x71,0x73,0x98,0x9F,0x55,0x6C,0x7A", deviceJoinName: "Aeotech eTRV"
			fingerprint manufacturerId: "148"
			fingerprint mfr: "0148", prod: "0003", model: "0001", cc: "5E,55,98", sec: "86,85,59,72,5A,73,75,31,26,40,43,80,70,71,6C,7A", role: "07", ff: "9200", ui: "9200", deviceJoinName: "Eurotronic Spirit TRV"
			
				// 0x80 = Battery v1
				// 0x70 = Configuration v1
				// 0x72 = Manufacturer Specific v1
				// 0x31 = Multilevel Sensor v5
				// 0x26 = MultiLevel Switch v1
				// 0x71 = Notification v8
				// 0x75 = Protection v2
				// 0x98 = Security v2
				// 0x40 = Thermostat Mode
				// 0x43 = Thermostat Setpoint v3
				// 0x86 = Version v1
			}
		
				main "temperature"
				details(["temperature", "boostMode", "ecoMode", "lock", "frost", "dvcMode", "battery", "configure", "refresh", "trv", "setValve"])
				}
			
			
			//options for InvertLCD
			def LCDinvertOptions = [:]
				LCDinvertOptions << ["0" : "No"] // 0x00
				LCDinvertOptions << ["1" : "Yes"] // 0x01

			//options for ExternalTemp
			def ExternalTempOptions = [:]
				ExternalTempOptions << ["0" : "No"] // 0x00
				ExternalTempOptions << ["1" : "Yes"] // 0x01

			//options for LCDtimeout
			def LCDtimeoutOptions = [:]
				LCDtimeoutOptions << ["0" : "Always on"] // 0x00
				LCDtimeoutOptions << ["5" : "5 seconds"] // 0x05
				LCDtimeoutOptions << ["10" : "10 seconds"] // 0x0A
				LCDtimeoutOptions << ["15" : "15 seconds"] // 0x0F
				LCDtimeoutOptions << ["20" : "20 seconds"] // 0x14
				LCDtimeoutOptions << ["30" : "30 seconds"] // 0x1E
				
				
			//options for backlight
			def backlightOptions = [:]
				backlightOptions << ["0" : "Disabled"] // 0x00
				backlightOptions << ["1" : "Enabled"] // 0x01
				
			//options for battery notification
			def batteryNotOptions = [:]
				batteryNotOptions << ["0" : "Event only"] // 0x00
				batteryNotOptions << ["1" : "Once a day"] // 0x01
				
			//options for window detection
			def windowDetectOptions = [:]
				windowDetectOptions << ["0" : "Disabled"] // 0x00
				windowDetectOptions << ["1" : "Low"] // 0x01
				windowDetectOptions << ["2" : "Medium"] // 0x02
				windowDetectOptions << ["3" : "High"] // 0x03
				
					
			//Thresholds for TRV Temp report
			def tempReportRates = [:] // // 0x00 Unsolicited Temperature reporting disabled 0x01 – 0x32 report if temperature changed by delta = 0,1°C … 5,0 °C default 0x00)
				tempReportRates << ["0" : "Disabled"] // 0x00
				tempReportRates << ["1" : "Report 0.1 degree temperature change"] // 0x01
				tempReportRates << ["2" : "Report 0.2 degree temperature change"] // 0x02
				tempReportRates << ["5" : "Report 0.5 degree temperature change"] // 0x05
				tempReportRates << ["8" : "Report 0.8 degree temperature change"] // 0x08
				tempReportRates << ["10" : "Report 1.0 degree temperature change"] // 0x0A
				tempReportRates << ["15" : "Report 1.5 degree temperature change"] // 0x0F
				tempReportRates << ["20" : "Report 2.0 degree temperature change"] // 0x14
				tempReportRates << ["30" : "Report 3.0 degree temperature change"] // 0x1E
				tempReportRates << ["50" : "Report 5.0 degree temperature change"] // 0x32
			
			//Thresholds for TRV valve report
			def valveReportRates = [:] // 0x00 Unsolicited valve opening percentage reporting disabled 0x01-0x64 report if valve opening changed by delta = 1% … 100%  default 0x00
				valveReportRates << ["0" : "Disabled"] // 0x00
				valveReportRates << ["1" : "Report 1% valve movement"] // 0x01
				valveReportRates << ["2" : "Report 2% valve movement"] // 0x02
				valveReportRates << ["5" : "Report 5% valve movement"] // 0x32
				valveReportRates << ["10" : "Report 10% valve movement"] // 0x0A
				valveReportRates << ["20" : "Report 20% valve movement"] // 0x14
				valveReportRates << ["30" : "Report 30% valve movement"] // 0x1E
				valveReportRates << ["50" : "Report 50% valve movement"] // 0x32
				
			//Rates for Poll
			def rates = [:]
				rates << ["0" : "Disabled - Set temperature, valve & battery reports, if required"]
				rates << ["1" : "Refresh every minute (Not recommended)"]
				rates << ["5" : "Refresh every 5 minutes"]
				rates << ["10" : "Refresh every 10 minutes"]
				rates << ["15" : "Refresh every 15 minutes"]
				
			//options for Push
			def pushOptions = [:]
				pushOptions << ["0" : "Disabled"] // 0x00
				pushOptions << ["1" : "Enabled"] // 0x01
				
			//Settings Page
			preferences {
				//parameter 1
				input "LCDinvert", "enum", title: "Invert LCD Display", options: LCDinvertOptions, description: "Default: No", required: false, displayDuringSetup: true
				//parameter 2
				input "LCDtimeout", "enum", title: "LCD timeout (in secs)", options: LCDtimeoutOptions, description: "Default: Always on", displayDuringSetup: true
				//parameter 3
				input "backlight", "enum", title: "Backlight", options: backlightOptions, description: "Default: Disabled", required: false, displayDuringSetup: true
				//parameter 4 
				input "battNotification", "enum", title: "Battery notification", options: batteryNotOptions, description: "Default: Once a day", required: false, displayDuringSetup: true // 0x00 Battery status is only reported as a system notification (Notification CC)  0x01 Send battery status unsolicited once a day default: 0x01
				//parameter 5
				input "tempReport", "enum", title: "Temperature report threshold", options: tempReportRates, description: "Default: Disabled", required: false, displayDuringSetup: false
				//parameter 6
				input "valveReport", "enum", title: "Valve report threshold", description: "Default: Disabled", options: valveReportRates, required: false, displayDuringSetup: false
				//parameter 7
				input "windowOpen", "enum", title: "Window open detection sensitivity",description: "Default: Medium", options: windowDetectOptions, required: false, displayDuringSetup: false
				//parameter 8
				input "tempOffset", "number", title: "Temperature offset", description: "Set temperature offset : (-5 to +5°C)", range: "-5..5", displayDuringSetup: false
				//custom parameter
 				input "ExternalTemp", "enum", title: "Use External Temperature", options: ExternalTempOptions, description: "Default: No", required: false, displayDuringSetup: true               
				//custom parameter                
				input "ecoTemp", "number", title: "Eco heating setpoint", description: "18 - Default. Range: (8 - 28°C)", range: "8..28", defaultValue: "18", displayDuringSetup: false
				// custom paramater
				input "tempMin", "number", title: "Min temperature recorded", description: "default 8 : (range 8 to 10°C)", range: "8..10", displayDuringSetup: false
				// custom parameter
				input "tempMax", "number", title: "Max temperature recorded", description: "default 28 : (range 25 to 28°C)", range: "25..28", displayDuringSetup: false
				// custom parameter
				input name: "refreshRate", type: "enum", title: "Refresh rate", options: rates, description: "Select refresh rate", defaultValue: "5", required: false
				// custom parameter
				input name: "pushNot", type: "enum", title: "Push notifications (system Events)", options: pushOptions, description: "Enable / Disable push", required: false
			}
			
		// parse events into attributes
		def parse(String description) {
		    // log.debug "Parsing '${description}'"
			def result = []
			if (description.startsWith("Err 106")) {
				state.sec = 0
				result = createEvent(descriptionText: description, isStateChange: true)
			}
			else {
				def cmd = zwave.parse(description,[0x75:1])
				if (cmd) {
					result += zwaveEvent(cmd)
	//				log.debug "Parsed ${cmd} to ${result.inspect()}"
				} else {
					log.debug "Non-parsed event: ${description}"
				}
			}
			return result
		}
			
		//Battery
	def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
		def map = [ name: "battery", unit: "%" ]
		
        if (cmd.batteryLevel == 0xFF) {  // Special value for low battery alert
			map.value = 1
			map.descriptionText = "${device.displayName} has a low battery"
			map.isStateChange = true
		} 
		else {
			map.value = cmd.batteryLevel
		}
        
        state.lastBatteryReport = new Date().time           // Store time of last battery report
		log.info "Report Received : $cmd"
		createEvent(map)
	}

		//Lock
		def zwaveEvent(hubitat.zwave.commands.protectionv1.ProtectionReport cmd) {
			def event = [ ]
			def eventValue
			//log.debug "$cmd.protectionState"
			
            if (cmd.protectionState == 0) { //00 - unlocked
				eventValue = "unlocked"
			}
            
			if (cmd.protectionState == 1) { //01 - locked
				eventValue = "locked"
			}
            
			if (device.currentValue("lock") != eventValue) {
			    sendEvent(name: "lock", value: eventValue, displayed: true)
			}

			log.info "Protection State - ${eventValue}"
			sendEvent(name: "lastCheckin", value: new Date())
            return
		}

		//Valve
		def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
			def event = []
			event << createEvent(name:"level", value: cmd.value, unit:"%", displayed: true)
			
			log.info "Report Received : Valve open ${cmd.value}%"
            sendEvent(name: "lastCheckin", value: new Date())
			return event
		}

		//Temperature
		def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
			def map = [ value: cmd.scaledSensorValue.toString(), displayed: true ]
			def value = cmd.scaledSensorValue.toString()
					map.name = "temperature"
					map.unit = cmd.scale == 1 ? "F" : "C"
					state.temperature = cmd.scaledSensorValue //.toString()

			log.info "Report Received : $cmd"
            sendEvent(name: "lastCheckin", value: new Date())
			createEvent(map)
		}

	//Thermostat SetPoint
	def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) { //	Parsed ThermostatSetpointReport(precision: 2, reserved01: 0, scale: 0, scaledValue: 21.00, setpointType: 1, size: 2, value: [8, 52])
		def event = []
		def currentState = device.currentValue("thermostatOperatingState")
        // log.debug "setpointType = " + cmd.setpointType + " currentState = " + currentState
 		state.scale = cmd.scale	// So we can respond with same format later, see setHeatingSetpoint()
		state.precision = cmd.precision
		def radiatorSetPoint = cmd.scaledValue
        
        if (cmd.setpointType == 1) { //this is the standard heating setpoint
            if  (currentState != "eco") {
			    event << createEvent(name: "nextHeatingSetpoint", value: radiatorSetPoint, unit: getTemperatureScale(), displayed: true)
			    event << createEvent(name: "heatingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), displayed: true)
			    event << createEvent(name: "thermostatSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), displayed: false)
			    event << createEvent(name: "thermostatTemperatureSetpoint", value: radiatorSetPoint.toString(), unit: "C", displayed: false)
                log.info "Regular Setpoint Report Received : ${cmd}"              
            }
		}
		
		if (cmd.setpointType == 11) { // this is eco heat setting on this device
            if (currentState == "eco") {
			    event << createEvent(name: "nextHeatingSetpoint", value: radiatorSetPoint, unit: getTemperatureScale(), displayed: true)
			    event << createEvent(name: "heatingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), displayed: true)
			    event << createEvent(name: "thermostatSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), displayed: false)
			    event << createEvent(name: "thermostatTemperatureSetpoint", value: radiatorSetPoint.toString(), unit: "C", displayed: false)
                log.info "Eco Setpoint Report Received : ${cmd}"    
            }
		}
        
        if (cmd.setpointType == 3)  {            // Ignore Type 3 reports which are unused
		    // log.info "Setpoint Report Received : ${cmd}"
        }
        
        if (event)
		    return event
	}

		//Basic Operating State
		def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd){
			def event = [ ]
			if (cmd.value == 255) { //255 - 0xFF = normal mode
				state.thermostatMode = "auto"
				state.thermostatOperatingState = "heating"
				state.switch = "on"
			}
			if (cmd.value == 240){ //240 - 0xF0 = Boost
				state.thermostatMode = "boost"
				state.thermostatOperatingState = "boost"
				state.switch = "on"
			}
			if (cmd.value == 0){ //0 - 0x00 = eco
				state.thermostatMode = "eco"
				state.thermostatOperatingState = "eco"
				state.switch = "on"
			}
			if (cmd.value == 15){ //15 - 0x0F = off
				state.thermostatMode = "Switched off"
				state.thermostatOperatingState = "frost"
				state.switch = "off"
			}
			if (cmd.value == 254){     //254 - 0xFE = direct valve contol mode
				state.thermostatMode = "off"
				state.thermostatOperatingState = "Direct Valve Control"
				state.switch = "on"
			}

			event << createEvent(name: "thermostatMode", value: state.thermostatMode, displayed: true)
			event << createEvent(name: "thermostatOperatingState", value: state.thermostatOperatingState, displayed: true)
			event << createEvent(name: "switch", value: state.switch, displayed: true)
				
			log.info "Report Received : ${cmd}, ${state.thermostatMode}, ${state.thermostatOperatingState}"
			sendEvent(name: "lastCheckin", value: new Date())
            return event
		}

		//Thermostat Mode / Operating State
		def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd ) {
			def event = []
			if (cmd.mode == 1){ //1 normal heat 0x01
				state.thermostatMode = "auto"
				state.thermostatOperatingState = "heating"
				state.switch = "on"
			}
			if (cmd.mode == 15){ //15 Boost 0x0F
				state.thermostatMode = "heat"
				state.thermostatOperatingState = "boost"
				state.switch = "on"
			}
			if (cmd.mode == 11){ //11 eco 11 0x0B
				state.thermostatMode = "eco"
				state.thermostatOperatingState = "eco"
				state.switch = "on"
			}
			if (cmd.mode == 0){ // 0 off 0x00
				state.thermostatMode = "switched off"
				state.thermostatOperatingState = "frost"
				state.switch = "off"
			}
			if (cmd.mode == 31){ // 31 dvc 0xFE
				state.thermostatMode = "off"
				state.thermostatOperatingState = "Direct Valve Control"
				state.switch = "on"
			}
			event << createEvent(name: "thermostatMode", value: state.thermostatMode, displayed: true)
			event << createEvent(name: "thermostatOperatingState", value: state.thermostatOperatingState, displayed: true)
			event << createEvent(name: "switch", value: state.switch, displayed: true)
				
			log.info "Report Received : ${cmd}, ${state.thermostatMode}, ${state.thermostatOperatingState}"
			sendEvent(name: "lastCheckin", value: new Date())
            return event
		}

		//Supported Modes
		def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
			// log.trace "$cmd"
			def supportedModes = []
			supportedModes << "off"
			supportedModes << "heat" 
			supportedModes << "cool" 
            supportedModes << "eco"
			supportedModes << "auto" 
			state.supportedModes = supportedModes 
			sendEvent(name: "supportedModes", value: supportedModes, displayed: false)
			log.info "Report Received : $cmd, Thermostat supported modes : $supportedModes"
            sendEvent(name: "lastCheckin", value: new Date())
		}

		def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
			def events = []
			switch (cmd.notificationType) {
				case 8:
				events << processPowerMgtNot(cmd)
				break

				case 9:
				events << processSystemNot(cmd)
				break
			}

			log.info "Notification Report : $cmd"
            sendEvent(name: "lastCheckin", value: new Date())
		}

		private processPowerMgtNot(cmd) {
			def descriptionText
			if (cmd.eventParameter == []) {
				descriptionText = "New batteries"
			} else {
				if (cmd.eventParameter == [10]) {
					descriptionText = "replace battery soon"
				}
				if (cmd.eventParameter == [11]) {
				descriptionText = "replace battery now"
				}
			}
			if ($pushNot == 1) {
			sendPush("${device.displayName}: Warning! $descriptionText")
			}
			sendEvent(name: "NotificationCC_Power", value: descriptionText, displayed: false)			
			log.info "Power management event: Warning! $descriptionText"
		}
		
		private processSystemNot(cmd) {
			def descriptionText
			if (cmd.eventParameter == []) {
				descriptionText = "Cleared"
			} else {
				if (cmd.eventParameter == [1]) {
				descriptionText = "Warning! Motor movement not possible"
				}
				if (cmd.eventParameter == [2]) {
				descriptionText = "Warning! Not mounted on a valve"
				}
				if (cmd.eventParameter == [3]) {
				descriptionText = "Warning! Valve closing point could not be detected"
				}
				if (cmd.eventParameter == [4]) {
				descriptionText = "Warning! Piston positioning failed"	
				}
			}
			if ($pushNot == 1) {
				sendPush("${device.displayName}: $descriptionText")

			}
			sendEvent(name: "NotificationCC_System", value: descriptionText, displayed: false)
			log.info "System event: $descriptionText"
		}


		def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
			def result = []
			if (cmd.nodeId.any { it == zwaveHubNodeId }) {
				result << sendEvent(descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier}")
			} else if (cmd.groupingIdentifier == 1) {
				// We're not associated properly to group 1, set association
				result << sendEvent(descriptionText: "Associating $device.displayName in group ${cmd.groupingIdentifier}")
				result << response(zwave.associationV1.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
			}
			log.info "Report Received : $cmd"
			result
		}

		//
		def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation	 cmd) { // Devices that support the Security command class can send messages in an encrypted form; they arrive wrapped in a SecurityMessageEncapsulation command and must be unencapsulated
			log.debug "raw secEncap $cmd"
			state.sec = 1
			def encapsulatedCommand = cmd.encapsulatedCommand ([0x20: 1, 0x80: 1, 0x70: 1, 0x72: 1, 0x31: 5, 0x26: 3, 0x75: 1, 0x40: 2, 0x43: 2, 0x86: 1, 0x71: 3, 0x98: 2, 0x7A: 1 ]) 

			if (encapsulatedCommand) {
				return zwaveEvent(encapsulatedCommand)
			} else {
			log.warn "Unable to extract encapsulated cmd from $cmd"
			createEvent(descriptionText: cmd.toString())
			}
		}

		def zwaveEvent(hubitat.zwave.Command cmd) {
			def map = [ descriptionText: "${device.displayName}: ${cmd}" ]
			log.warn "mics zwave.Command - ${device.displayName} - $cmd"
			sendEvent(map)
		}


		def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
			if (cmd.manufacturerName) { updateDataValue("manufacturer", cmd.manufacturerName) }
			if (cmd.productTypeId) { updateDataValue("productTypeId", cmd.productTypeId.toString()) }
			if (cmd.productId) { updateDataValue("productId", cmd.productId.toString()) }
			if (cmd.manufacturerId){ updateDataValue("manufacturerId", cmd.manufacturerId.toString()) }
			log.info "Report Received : $cmd"
		}

		def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd ) {
			log.info "Report Received : $cmd"
			def events = []

			switch (cmd.parameterNumber) {

				case 1:
				events << processParam1(cmd)
				break
				
				case 2:
				events << processParam2(cmd)
				break
				
				case 3:
				events << processParam3(cmd)
				break
				
				case 4:
				events << processParam4(cmd)
				break
				
				case 5:
				events << processParam5(cmd)
				break
				
				case 6:
				events << processParam6(cmd)
				break
				
				case 7:
				events << processParam7(cmd)
				break
				
				case 8:
				events << processParam8(cmd)
				break
			}
		}
		
		def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
			log.debug "Version Report: $cmd"
			def zWaveLibraryTypeDisp  = String.format("%02X",cmd.zWaveLibraryType)
			def zWaveLibraryTypeDesc  = ""
			switch(cmd.zWaveLibraryType) {
				case 1:
				zWaveLibraryTypeDesc = "Static Controller"
				break

				case 2:
				zWaveLibraryTypeDesc = "Controller"
				break

				case 3:
				zWaveLibraryTypeDesc = "Enhanced Slave"
				break

				case 4:
				zWaveLibraryTypeDesc = "Slave"
				break

				case 5:
				zWaveLibraryTypeDesc = "Installer"
				break

				case 6:
				zWaveLibraryTypeDesc = "Routing Slave"
				break

				case 7:
				zWaveLibraryTypeDesc = "Bridge Controller"
				break

				case 8:
				zWaveLibraryTypeDesc = "Device Under Test (DUT)"
				break

				case 0x0A:
				zWaveLibraryTypeDesc = "AV Remote"
				break

				case 0x0B:
				zWaveLibraryTypeDesc = "AV Device"
				break

				default:
				zWaveLibraryTypeDesc = "N/A"
			}

			def zWaveVersion = String.format("%d.%02d",cmd.zWaveProtocolVersion,cmd.zWaveProtocolSubVersion)
            def firmwareLevel = String.format("%d.%02d",cmd.firmware0Version,cmd.firmware0SubVersion)
			sendEvent([name: "FirmwareLevel", value:  firmwareLevel])            
			sendEvent([name: "ZWaveVersion", value:  zWaveVersion])
			sendEvent([name: "ZWaveLibraryType", value:  zWaveLibraryTypeDesc])
		}   

	//LCDinvert	
	private processParam1(cmd) {
		def setValue
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "No"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Yes"
			}
			
		log.info "LCDinvert: ${setValue}"
		//settings.$LCDinvert = setValue
	}

	//LCDtimeout
	private processParam2(cmd) {
		def setValue
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Always on"
			}
			if (cmd.scaledConfigurationValue == 5) {
			setValue = "5 Seconds"
			}
			if (cmd.scaledConfigurationValue == 10) {
			setValue = "10 Seconds"
			}
			if (cmd.scaledConfigurationValue == 15) {
			setValue = "15 Seconds"
			}
			if (cmd.scaledConfigurationValue == 20) {
			setValue = "20 Seconds"
			}
			if (cmd.scaledConfigurationValue == 30) {
			setValue = "30 Seconds"
			}
			
		log.info "LCDtimeout: ${setValue}"
		//settings.$LCDtimeout = setValue
	}

	//backlight
	private processParam3(cmd) {
		def setValue
			
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Disabled"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Enabled"
			}
			
		log.info "backlight: ${setValue}"
		//settings.$backlight = setValue
	}

	//battery
	private processParam4(cmd) {
		def setValue
			
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Event only"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Once a day"
			}
		log.info "battery notification: ${setValue}"
		//settings.$battNotification = setValue
	}

	//temp report threshold
	private processParam5(cmd) {
		def setValue
			
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Disabled"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Report 0.1 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 2) {
			setValue = "Report 0.2 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 3) {
			setValue = "Report 0.3 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 5) {
			setValue = "Report 0.5 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 8) {
			setValue = "Report 0.8 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 15) {
			setValue = "Report 1.0 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 15) {
			setValue = "Report 1.5 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 20) {
			setValue = "Report 2.0 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 30) {
			setValue = "Report 3.0 degree temperature change"
			}
			if (cmd.scaledConfigurationValue == 50) {
			setValue = "Report 5.0 degree temperature change"
			}

		log.info "Temp report: ${setValue}"
		//settings.$tempReport = setValue
	}

	//valve report
	private processParam6(cmd) {
		def setValue
			
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Disabled"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Report 1% valve movement"
			}
			if (cmd.scaledConfigurationValue == 2) {
			setValue = "Report 2% valve movement"
			}
			if (cmd.scaledConfigurationValue == 5) {
			setValue = "Report 5% valve movement"
			}
			if (cmd.scaledConfigurationValue == 10) {
			setValue = "Report 10% valve movement"
			}
			if (cmd.scaledConfigurationValue == 20) {
			setValue = "Report 20% valve movement"
			}
			if (cmd.scaledConfigurationValue == 30) {
			setValue = "Report 30% valve movement"
			}
			if (cmd.scaledConfigurationValue == 50) {
			setValue = "Report 50% valve movement"
			}
		log.info "Valve report: ${setValue}"
		//settings.$valveReport = setValue
	}
		
	//window open	
	private processParam7(cmd) {
		def setValue
			
			if (cmd.scaledConfigurationValue == 0) {
			setValue = "Disabled"
			}
			if (cmd.scaledConfigurationValue == 1) {
			setValue = "Low"
			}
			if (cmd.scaledConfigurationValue == 2) {
			setValue = "Medium"
			}
			if (cmd.scaledConfigurationValue == 3) {
			setValue = "High"
			}
		log.info "Window open detection: ${setValue}"
		//settings.windowOpen = setValue
	}

	//temp offset
	private processParam8(cmd) {
		def setValue
			
		setValue = cmd.scaledConfigurationValue
		log.info "Temp offset: ${setValue}"
		//settings.$tempOffset = setValue
	}

		def temperatureUp() {			
	        def nextTemp = currentDouble("nextHeatingSetpoint") + 0.5d

            if(nextTemp > 28) {		// It can't handle above 28, so don't allow it go above
				nextTemp = 28
			}
            
            if(nextTemp < 8) {		// It can't go below 8, so don't allow it
				nextTemp = 8
			}
			sendEvent(name:"nextHeatingSetpoint", value: nextTemp, unit: getTemperatureScale(), displayed: false)	
			runIn (5, "buffSetpoint",[data: [value: nextTemp], overwrite: true])
		}

		def temperatureDown() {
	        def nextTemp = currentDouble("nextHeatingSetpoint") - 0.5d

            if(nextTemp > 28) {		// It can't handle above 28, so don't allow it go above
				nextTemp = 28
			}
            
            if(nextTemp < 8) {		// It can't go below 8, so don't allow it
				nextTemp = 8
			}
			sendEvent(name:"nextHeatingSetpoint", value: nextTemp, unit: getTemperatureScale(), displayed: false)	
			runIn (5, "buffSetpoint",[data: [value: nextTemp], overwrite: true])
		}

		def buffSetpoint(data) {
			def key = "value"
			def nextTemp = data[key]
			//log.debug " buff nextTemp is $nextTemp"
			setHeatingSetpoint(nextTemp)
		}

		def setCoolingSetpoint(temp){
		}

		def setHeatingSetpoint(Double degrees) { //Double added
			def cmds = []
			def precision = state.precision ?: 2
			def deviceScale = state.scale ?: 0
			
			sendEvent(name:"nextHeatingSetpoint", value: degrees, unit: getTemperatureScale(), descriptionText: "Next heating setpoint is ${degrees}", displayed: true, isStateChange:true)
			log.debug "nextHeatingSetpoint set to ${degrees}"
			// log.debug device.currentValue("nextHeatingSetpoint")
			// log.debug device.currentValue("HeatingSetpoint")	
			// log.debug device.nextHeatingSetpoint
			// log.debug device.HeatingSetpoint    
            
			if (device.currentValue("thermostatMode") != "eco") {
				cmds << zwave.thermostatSetpointV2.thermostatSetpointSet(precision: precision, scale: deviceScale, scaledValue: degrees, setpointType: 1)
				cmds << zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
                log.trace "Setting Temp to ${degrees},  $cmds"
			}
			if (device.currentValue("thermostatMode") == "eco") {
				cmds << zwave.thermostatSetpointV2.thermostatSetpointSet(precision: precision, scale: deviceScale, scaledValue: ecoTemp, setpointType: 11)
				cmds << zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)
                log.trace "Setting Eco Temp to ${ecoTemp},  $cmds"
			}
					
			secureSequence(cmds)
		}

		//lock
		def lock() {
			def cmds = []
			sendEvent(name: "lock", value: "locking", displayed: false)
			cmds << zwave.protectionV1.protectionSet(protectionState: 1)
			cmds << zwave.protectionV1.protectionGet()
			log.trace "locking $cmds" 
			secureSequence(cmds)
		}

		//unlock
		def unlock() {
			def cmds = []
			sendEvent(name: "lock", value: "unlocking", displayed: false)
			cmds << zwave.protectionV1.protectionSet(protectionState: 0)
			cmds << zwave.protectionV1.protectionGet()
			log.trace "unlocking $cmds" 
			secureSequence (cmds)
		}

		/*
		* switch on/off turns frost on/off
		****Mode Commands**
		* Boost --> heat
		* auto --> This is the normal mode (Comfort)
		* cool --> ecoon
		* off --> dvcon
		*/

		def booston() {
			heat()
		}

		def boostoff(){
			auto()
		}

		def heat() { //Boost
			def cmds = []
			sendEvent(name: "thermostatMode", value: "heat", displayed: true)
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0x0F)
			cmds << zwave.thermostatModeV2.thermostatModeGet()
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
			// log.trace "heat On $cmds"
			secureSequence(cmds)
		}

		def auto(){ //Comfort
			def cmds = []
			sendEvent(name: "thermostatMode", value: "auto", displayed: true)
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1)
			cmds << zwave.thermostatModeV2.thermostatModeGet()
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
			// log.trace "auto $cmds" 
			secureSequence (cmds)
		}

		def off() {
			froston()
		}

		def on(){
			frostoff()
		}

		def froston(){ //taken from switchoff new app / frost tile classic
			def cmds = []
			sendEvent(name: "thermostatMode", value: "switched off", displayed: true)
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0)
			cmds << zwave.thermostatModeV2.thermostatModeGet()
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
			// log.trace "froston $cmds" 
			secureSequence(cmds)
		}
		 
		def frostoff(){
			auto()
		}
			
		def dvcon() { //taken from Mode off new app
			def cmds = []

			sendEvent(name: "thermostatMode", value: "off", displayed: true)
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 31)
			cmds << zwave.thermostatModeV2.thermostatModeGet()
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)

			// log.trace "DVC On : $cmds"
			secureSequence(cmds)
			
		}
		  
		def dvcoff(){
			auto()
		}
		  
		def ecoon() { 
			def cmds = []
			sendEvent(name: "thermostatMode", value: "eco", displayed: true)
			cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 11)
			cmds << zwave.thermostatModeV2.thermostatModeGet()
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)
			// log.trace "Eco/Cool $cmds"
			secureSequence(cmds)
		}
		 
		def ecooff(){
			auto()
		}
		 	 
		def setThermostatMode(String) {
			def nextMode = String

			switch ("$nextMode") {
				case "heat":
					(booston())
					break
				case "auto":
					(auto())
					break
				case "cool":
					(ecoon())
					break
				case "off":
					(dvcon())
					break
				}
		}
			
		//SetValve during DVC
			def setLevel(nextLevel) {
				def cmds = []

				sendEvent(name:"level", value: nextLevel, displayed: false, isStateChange:true)
				
				cmds << zwave.switchMultilevelV3.switchMultilevelSet(value: nextLevel)
				cmds << zwave.switchMultilevelV3.switchMultilevelGet()
						
				// log.trace "Executing 'setLevel' : $cmds"
				secureSequence(cmds)
		}

		//Refresh (Momentary)
		def refresh() {
			log.trace "refresh"
			poll()
		}

        def daysToTime(days) {
				days*24*60*60*1000
		}
			
		def configure() {
			state.supportedModes = [off, heat, eco, Boost, dvc] // basic modes prior to details from device
			setDeviceLimits()

            if (!device.currentState('batteryLastReplaced')?.value)
		        resetBatteryReplacedDate(true)
            
            if (ExternalTemp == null) 
                ExternalTemp = 0
            
            if (ExternalTemp == 0) {
                if (tempOffset) {
                    if (tempOffset.toBigDecimal() > 0) {
                        iTempOffset=(tempOffset.toBigDecimal()*10+1).toInteger()        // Multiply by 10 - Positive Offset is 0 to 50
                    } else {
                        iTempOffset=((tempOffset.toBigDecimal()*10)*-1).toInteger()     // Multiply by 10 and make the result positive
                        iTempOffset = 256-iTempOffset                                   // Negative offset is 205 to 255
                    }
                } else {
                    iTempOffset=0
                }
            } else { 
                log.info "Using External Temperatures"
                iTempOffset = 128                                                         // External temperatures are being used
                tempOffset  = 128
            }
            
			def cmds = []
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  LCDinvert == "1" ? [0x01] : [0x00], parameterNumber:1, size:1, scaledConfigurationValue:  LCDinvert == "1" ? 0x01  : 0x00)		
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  LCDtimeout == "5" ? [0x05] : LCDtimeout == "10" ? [0x0A] : LCDtimeout == "15" ? [0x0F] : LCDtimeout == "20" ? [0x14] : LCDtimeout == "30" ? [0x1E] : [0x00], parameterNumber:2, size:1, scaledConfigurationValue:  LCDtimeout == "5" ? 0x05 : LCDtimeout == "10" ? 0x0A : LCDtimeout == "15" ? 0x0F : LCDtimeout == "20" ? 0x14 : LCDtimeout == "30" ? 0x1E : 0x00)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  backlight == "1" ? [0x01] : [0x00], parameterNumber:3, size:1, scaledConfigurationValue:  backlight == "1" ? 0x01 : 0x00)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  battNotification == "Event only" ? [0x00] : [0x01], parameterNumber:4, size:1, scaledConfigurationValue:  battNotification == "Event only" ? 0x00 : 0x01)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  tempReport == "1" ? [0x01] : tempReport == "2" ? [0x02] : tempReport == "5" ? [0x05] : tempReport == "8" ? [0x08] : tempReport == "10" ? [0x0A] : tempReport == "15" ? [0x0F] : tempReport == "20" ? [0x14] : tempReport == "30" ? [0x1E] : tempReport == "50" ? [0x32] : [0x00], parameterNumber:5, size:1, scaledConfigurationValue:  tempReport == "1" ? 0x01 : tempReport == "2" ? 0x02 : tempReport == "5" ? 0x05 : tempReport == "8" ? 0x08 : tempReport == "10" ? 0x0A : tempReport == "15" ? 0x0F : tempReport == "20" ? 0x14 : tempReport == "30" ? 0x1E : tempReport == "50" ? 0x32 : 0x00)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  valveReport == "1" ? [0x01] : valveReport == "2" ? [0x02] : valveReport == "5" ? [0x05] : valveReport == "10" ? [0x0A] : valveReport == "20" ? [0x14] : valveReport == "30" ? [0x1E] : valveReport == "50" ? [0x32] : [0x00], parameterNumber:6, size:1, scaledConfigurationValue:  valveReport == "1" ? 0x01 : valveReport == "2" ? 0x02 : valveReport == "5" ? 0x05 : valveReport == "10" ? 0x0A : valveReport == "20" ? 0x14 : valveReport == "30" ? 0x1E : valveReport == "50" ? 0x32 : 0x00)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  windowOpen == "0" ? [0x00] : windowOpen == "1" ? [0x01] : windowOpen == "3" ? [0x03] : [0x02], parameterNumber:7, size:1, scaledConfigurationValue: windowOpen == "0" ? 0x00 : windowOpen == "1" ? 0x01 : windowOpen == "3" ? 0x03 : 0x02)
			    cmds << zwave.configurationV1.configurationSet(configurationValue:  tempOffset == null ? [0] : [iTempOffset], parameterNumber:8, size:1, scaledConfigurationValue: tempOffset == null ? 0 : iTempOffset)
			    cmds << zwave.thermostatSetpointV1.thermostatSetpointSet(precision: 1, scale: 0, scaledValue: ecoTemp == null ? 18.toBigDecimal() : ecoTemp, setpointType: 11, size: 2, value: ecoTemp == null ? [0, 180] : [0, ecoTemp*10])
			    cmds << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:1) 
			    cmds << zwave.thermostatModeV2.thermostatModeGet()
			    cmds << zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 0x01)
			    cmds << zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 0x0B)
			    cmds << zwave.switchMultilevelV3.switchMultilevelGet()
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:1)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:2)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:3)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:4)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:5)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:6)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:7)
			    cmds << zwave.configurationV1.configurationGet(parameterNumber:8)
			    cmds << zwave.batteryV1.batteryGet()
			    cmds << zwave.protectionV1.protectionGet()
			    cmds << zwave.thermostatModeV2.thermostatModeSupportedGet()
			    cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet()
			    cmds << zwave.versionV1.versionGet()
			
            sendEvent(name: "configuration", value: "sent", displayed: true)
			sendEvent(name: "configure", displayed: false)
			log.debug "Configuration sent"
			secureSequence(cmds)            
 			}

		def updated() {
		sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
			if (!state.updatedLastRanAt || new Date().time >= state.updatedLastRanAt + 2000) {
				state.updatedLastRanAt = new Date().time
				unschedule(refresh)
				unschedule(poll)
				log.trace "Configuring settings"
				runIn (05, configure)
				sendEvent(name: "configure", value: "configdue", displayed: false)
				switch(refreshRate) {
				case "1":
					runEvery1Minute(poll)
					log.info "Refresh Scheduled for every minute"
					break
				case "15":
					runEvery15Minutes(poll)
					log.info "Refresh Scheduled for every 15 minutes"
					break
				case "10":
					runEvery10Minutes(poll)
					log.info "Refresh Scheduled for every 10 minutes"
					break
				case "5":
					runEvery5Minutes(poll)
					log.info "Refresh Scheduled for every 5 minutes"
					break
				case "0":
					log.info "Refresh off"}
		 
			}
			else {
				log.warn "update ran within the last 2 seconds"
			}
		}

		// PING is used by Device-Watch in attempt to reach the Device
		def ping() {
			refresh()
		}
		
		def poll() { // If you add the Polling capability to your device type, this command will be called approximately every 5 minutes to check the device's state
			// log.debug "polling"
			def cmds = []
			
				if (!state.lastBatteryReport) {
                    log.trace "POLL - Asking for battery report as never got one before"
					cmds << zwave.batteryV1.batteryGet()
				} else {
					if (new Date().time - state.lastBatteryReport > daysToTime(1)) {
						log.trace "POLL - Asking for battery report as over 1 days since last one"
						cmds << zwave.batteryV1.batteryGet()
                    }
				}
				
				//once an hour ask for everything
				if (!state.extra || (new Date().time) - state.extra > (60*60000)) {			// minutes * millseconds these settings shouldnt be needs as device should send response at time of update
					//cmds <<	zwave.thermostatModeV2.thermostatModeGet()	// get mode
					cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11) 	// get eco/cool setpoint
					cmds <<	zwave.basicV1.basicGet()											// get mode (basic)	
					cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)	// get heating setpoint
					state.extra = new Date().time
				}
				cmds <<	zwave.sensorMultilevelV1.sensorMultilevelGet()	// get temp
				cmds << zwave.switchMultilevelV3.switchMultilevelGet()	// valve position
				cmds <<	zwave.thermostatModeV1.thermostatModeGet()		// get mode
				cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)	// get heating setpoint
                cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)	// get heating setpoint
				// log.trace "POLL $cmds"
				secureSequence (cmds)
			}

		def setDeviceLimits() { // for google and amazon compatability
			sendEvent(name:"minHeatingSetpoint", value: settings.tempMin ?: 8, unit: "°C", displayed: false)
			sendEvent(name:"maxHeatingSetpoint", value: settings.tempMax ?: 28, unit: "°C", displayed: false)
			log.trace "setDeviceLimits - device max/min set"
		}	


		def secure(hubitat.zwave.Command cmd) {
			if (state.sec) {
				//log.debug "Seq secure - $cmd"
				zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
			} 
			else {
				//log.debug "Seq unsecure- $cmd"
				cmd.format()
			}
		}

		def secureSequence(cmds, delay=1500) {
			//log.debug "SeSeq $commands"
            delayBetween(cmds.collect{ secure(it) }, delay)
		}	

        def resetBatteryReplacedDate(paired) {
	        def newlyPaired = paired ? " for newly paired sensor" : ""
	        sendEvent(name: "batteryLastReplaced", value: new Date())
	        log.debug "Setting Battery Last Replaced to current date${newlyPaired}"
        }

        private currentDouble(attributeName) {
	        if(device.currentValue(attributeName)) {
		        return device.currentValue(attributeName).doubleValue()
	        }
	        else {
		        return 0d
	        }
        }																								
