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
	 ****************************** KK mods ***************************************************************************																														   
	 * Version 1.6.0.1 12/30/2020 
	 *		- when switchmultilevelv3.SwitchMultilevelReport is received, change the thermostatOperatingState depending on the valve fully closed or partially open..
	 *		- state.thermostatOperatingState is NOT changed when thermostatmodev2.ThermostatModeReport report is received ( only state.thermostaReportedtMode changes )
	 *		- Thermostat Mode 0x00 off() was changed from froston() to thermostatModeV2.thermostatModeSet(mode: 0)
	 *		- Thermostat Mode 0x0B eco() added 
	 *		- command setThermostatMode("off") now calls off() - was dvcon()
	 *		- once a hour batteryV1.batteryGet() is called
   	 *
   	 * Version 1.6.0.2 01/02/2021
   	 * 		- added "simulateCool" option (default is false) - Enable / Disable Cooling Capability simulation
   	 *		- fixed isStateChange: true in many events ...
	 *		- pollValve() added 
	 *		- when valve report is received, thermostatOperatingState is changed depending on the simulateCool option:
	 *			- if simulateCool is true : 
	 *				- when valve is closed : 
	 *					- if in cool or off mode ->cooling , otherwise -> idle
	 *					- if in cool or off mode ->cooling 
	 *				- when valve is open :  heating
	 *			- if simulateCool is false : off
	 *				- when valve is closed : 
	 *					- if in dvc mode -> off , otherwise -> idle
	 *					- if in cool or off mode ->cooling 
	 *				- when valve is open :  heating
	 *      - thermostatsetpointv2.ThermostatSetpointReport major changes ..
	 *		- basicv1.BasicReport : 
	 *			- when 'emergency heat' mode is reported, then if simulateCool is true -> thermostatOperatingState = "heating"; else '"boost"'
	 *			- when  'off' mode is reported, then if simulateCool is true -> thermostatMode = "cool" and thermostatOperatingState = "cooling"; else thermostatMode = "Switched off" and thermostatOperatingState = "frost"
	 * 			- when 'direct valve contol mode' mode is reported, then thermostatMode = "dvc"
	 *		- thermostatmodev2.ThermostatModeReport : 
	 *			- when 'MODE_OFF' mode is reported, then if simulateCool is true -> thermostatMode = "cool"; else thermostatMode = "off"
	 *			- when 'MODE_HEAT' mode is reported, then if lastEurotronicModeSet == "auto" -> thermostatMode = "auto"; else thermostatMode = "heat"
	 *			- when 'MODE_COOL' mode is reported, then ThermostatMode = "cool"
	 *			- when 'MODE_AUTO' mode is reported, then ThermostatMode = "auto"
	 *			- when 'MODE_ENERGY_SAVE_HEAT' mode is reported, then ThermostatMode = "eco"
	 *			- when '15' mode is reported, then ThermostatMode = "emergencyHeat"
	 *			- when '31' mode is reported, then ThermostatMode = "dvc"
	 *		- thermostatmodev2.ThermostatModeSupportedReport: "auto", "off", "heat", "emergency heat", "eco", "dvcon" modes are repored. If simulateCool -> also "cool"
	 *		- coolingSetpoint(temp) : if simulateCool -> send "coolingSetpoint" event; else -> send nextCoolingSetpoint, coolingSetpoint, thermostatSetpoint, thermostatTemperatureSetpoint events !!
	 *		- emergencyHeat() : sends "emergencyHeat" event and switches EUrotronic into (mode: 15 )
	 *		- heat() : sends "thermostatMode", value: "heat"; changes Eurotronic thermostatModeSet(mode: 1)        // EUROTRONIC_MODE_HEAT
	 *		- auto() : thermostatModeSet(mode: 1 )    // EUROTRONIC_MODE_HEAT
	 *		- off() :  sends "thermostatMode", value: "off'; sends "thermostatOperatingState", value: "off; changes Eurotronic thermostatModeSet(mode: 0)    //EUROTRONIC_MODE_OFF
	 *		- cool() - added new command that performs off() in both simulateCool == true  or == false ..
	 *		- eco() : thermostatModeSet(mode: 11 )    // EUROTRONIC_MODE_ECO_ENERGY_HEAT
	 *		- on() : auto(); 
	 *		- dvcon() ((31) EUROTRONIC_MODE_DIRECT_VALVE_CONTROL): 
	 *			- if simulateCool -> thermostatMode", value: "cool; thermostatOperatingState = "cooling" ; setLevel(0)     // close the valve!
	 *			- else -> thermostatMode = 'dvc'; thermostatOperatingState = "dvc" ;  setLevel(0)     // close the valve!
	 *		- ecoon() mode: 11)        //EUROTRONIC_MODE_ECO_ENERGY_HEAT; sendEvent(name: "ecoMode", value: "on",
	 *		- ecooff() -> auto(); 	sendEvent(name: "ecoMode", value: "off"
	 *		- setThermostatMode(String) :  
	 *			- "cool" now performs (off())  (was ecoOn()); 
	 *			- added "dvc": (dvcon()); added "emergency heat":  (emergencyHeat())
	 *		- added setThermostatFanMode(mode) simulation .. 
	 * 		- added updateSetPoints ( heatingSP, cooliningSP ) // used by Thermostat Controller app?
     *
     * Version 1.6.0.3 01/10/2021
     *      - when simulateCool is true, mode 'off' will return thermostatOperatingState 'cooling' ( expected by Thermostat Manager app ... )
     *    - added experimenta capabilities : ThermostatOperatingState
     *     - added "logEnable" parameter
     *     - added type: "physical" to z-wave events received
     *     - "lastCheckin" events commented out
     *     - report z-wave event reveived refactored
     *     - setHeatingSetpoint() - request and get the valve reading after 5 seconds!
     *     - Off() ->  sends "thermostatOperatingState" event "idle" or "cooling" when  simulateCool == true ( was sending óff'mode in the previous version
     *     - setThermostatMode() - added "eco" mode in the list
     *     - poll() - removed thermostatSetpointGet(setpointType: 11) every hour and batteryV1.batteryGet() 
     *
     *
     * Version 1.6.0.4  02/06/2021
     *       - reformatting 
     *        - removed 'Notification' capability
     *        - removed 'Switch' capability ( makes problems with Alexa switching off lights in a room ... )
     *
     * Version 1.6.0.5  02/13/2021
     *      - calibrate function added! Turns TRV first off, then emergencyHeat, then heat - each for 15 seconds. 
     *      - calibrate retries one more time if the state.thermostatReportedState was not changed as expected
     *      - removed "booston" and "boostoff" commands ( they are duplicating emergencyHeat and heat commands )
     *      - removed "froston" and "frostoff" commands ( they are duplicating off and heat commands )
     *      - removed "ecoon" and "ecooff" commands (ecoff was opening the valve!!! ). Recplaced by "eco" command
     *
     * TODO: option: filter out duplicate events?
     * TODO: do not send all the different setpoins every 15 minutes!
     * TODO: round nextHeatingSetpoint( 22.222222222222 )
     * TODO: for the 'Calibrate' command : OFF-> Emergency-> OFF -> old mode; buffer the received commands while calibrating and execute later!
     * TODO: default polling time to be 15 minutes ( now is 5? )
     * TODO: add statistics - do we have answer on each z-wave command sent to the TRV?
     * TODO: check why ecoMode is ON?
     * TODO: default ECO temperature to be 18 degrees
     *
	 */

	metadata {
		definition (name: "Aeotec eTRV (KK mod) v.1.6.0.5", namespace: "kkossev", author: "Patrick Wogan and Scruffy-SJB", cstHandler: true, ocfDeviceType: "oic.d.thermostat", vid: "generic-thermostat-1") {
			capability "Actuator"               //
			capability "Sensor"                 //
			capability "Battery"                // battery - NUMBER
			// capability "Lock"                // lock - ENUM ["locked", "unlocked with timeout", "unlocked", "unknown"]; lock() unlock()
			// capability "Notification"           // deviceNotification(text); text required (STRING) - Notification text
			//capability "Switch"                 // switch - ENUM ["on", "off"]; off()on()
			capability "Switch Level"           // level - NUMBER; setLevel(level, duration); level required (NUMBER) - Level to set (0 to 100); duration optional (NUMBER) - Transition duration in seconds
			capability "TemperatureMeasurement" // temperature - NUMBER
        	capability "Thermostat"             // coolingSetpoint - NUMBER; heatingSetpoint - NUMBER; schedule - JSON_OBJECT; supportedThermostatFanModes - ENUM ["on", "circulate", "auto"]
	                                            // supportedThermostatModes - ENUM ["auto", "off", "heat", "emergency heat", "cool"]; temperature - NUMBER
	                                            // thermostatFanMode - ENUM ["on", "circulate", "auto"]; thermostatMode - ENUM ["auto", "off", "heat", "emergency heat", "cool"]
	                                            // thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
	                                            // thermostatSetpoint - NUMBER
	                                            // auto(); cool(); emergencyHeat(); fanAuto(); fanCirculate(); fanOn(); heat(); off()
	                                            // setCoolingSetpoint(temperature); temperature required (NUMBER) - Cooling setpoint in degrees
	                                            // setHeatingSetpoint(temperature); temperature required (NUMBER) - Heating setpoint in degrees
	                                            // setSchedule(JSON_OBJECT); JSON_OBJECT (JSON_OBJECT) - JSON_OBJECT
	                                            // setThermostatFanMode(fanmode); fanmode required (ENUM) - Fan mode to set    
	                                            // setThermostatMode(thermostatmode); thermostatmode required (ENUM) - Thermostat mode to set
			capability "Configuration"        // configure()
			capability "Health Check"        // checkInterval - NUMBER; ping()
			capability "Refresh"                // refresh()
	        // capability "Initialize"        //initialize()
	        // capability "Polling"            //poll()
	        //
		    capability "ThermostatCoolingSetpoint"	// coolingSetpoint - NUMBER; setCoolingSetpoint(temperature); temperature required (NUMBER) - Cooling setpoint in degrees
	        //capability "ThermostatFanMode"        // thermostatFanMode - ENUM ["auto", "circulate", "on"]; fanAuto(), fanCirculate(), fanOn(), setThermostatFanMode(fanmode), fanmode required (ENUM) - Fan mode to set
	        // capability "ThermostatHeatingSetpoint"    // heatingSetpoint - NUMBER; setHeatingSetpoint(temperature); temperature required (NUMBER) - Heating setpoint in degrees
		    capability "ThermostatSetpoint"			// thermostatSetpoint - NUMBER
		    capability "TemperatureMeasurement"		// KK - check if needed
		    capability "ThermostatMode"             // thermostatMode - ENUM ["heat", "cool", "emergency heat", "auto", "off"]; auto(), cool(), emergencyHeat(), heat(), off(), setThermostatMode(thermostatmode), thermostatmode required (ENUM) - Thermostat mode to set
	        capability "ThermostatOperatingState"    // thermostatOperatingState - ENUM ["vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "idle"]
	        // capability "ThermostatSchedule"        // schedule - JSON_OBJECT; setSchedule(JSON_OBJECT); JSON_OBJECT (JSON_OBJECT) - JSON_OBJECT
	        //capability "Valve"                    // valve - ENUM ["open", "closed"]; close(); open()

			//command "booston"
			//command "boostoff"
			//command "ecoon"
			//command "ecooff"
            command "eco"
	        //command "froston"
			//command "frostoff"
			command "temperatureUp"
			command "temperatureDown"
			command "dvcon"
			command "dvcoff"
			command "poll"
			command "pollValve"
			command "resetBatteryReplacedDate"
	        command "setTemperature", [number]
            //command "backLightOn"
            //command "backLightOff"
            command "calibrate"
                
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
		    // 0x6C = Supervision V1
		    // 0x7A = Firmware Update Md V6
			}
		
			main "temperature"
			details(["temperature", "boostMode", "ecoMode", "lock", "frost", "dvcMode", "battery", "configure", "refresh", "trv", "setValve"])
    	}
			


        final EUROTRONIC_MODE_OFF = 0                       // 0x00  No heating. Only frost protection ?
        final EUROTRONIC_MODE_HEAT = 1                      // 0x01  The room temperature will be kept at the configured comfortable level.
        final EUROTRONIC_MODE_ECO_ENERGY_HEAT = 11          // 0x0B eco mode. The room temperature will be lowered to the configured setpoint in order to save energy.
        final EUROTRONIC_MODE_FULL_POWER = 15               // 0x0F Boost mode for 5 minutes 
        final EUROTRONIC_MODE_DIRECT_VALVE_CONTROL = 31     // 0x1F Switches into direct valve control mode. The valve opening percentage can be controlled using the Switch multilevel command class.

/*

 class hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport {
     Short mode
     static Short MODE_AUTO = 3
     static Short MODE_AUTO_CHANGEOVER = 10
     static Short MODE_AUXILIARY_HEAT = 4
     static Short MODE_AWAY = 13
     static Short MODE_COOL = 2
     static Short MODE_DRY_AIR = 8
     static Short MODE_ENERGY_SAVE_COOL = 12
     static Short MODE_ENERGY_SAVE_HEAT = 11
     static Short MODE_FAN_ONLY = 6
     static Short MODE_FURNACE = 7
     static Short MODE_HEAT = 1
     static Short MODE_MOIST_AIR = 9
     static Short MODE_OFF = 0
     static Short MODE_RESUME = 5

*/

/*      hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport  mapping vs Eurotronic modes : 

        Eurotronic                           hubitat.zwave.commands           Mode                    operatingState        commands
        =====================                ===================              ================        ===============       ==========
        EUROTRONIC_MODE_OFF = 0              MODE_OFF = 0                     "off"                   "cooling"             off()
        EUROTRONIC_MODE_HEAT = 1             MODE_HEAT = 1                    "heat"                  "heating"             auto() ???
        n/a                                  MODE_COOL = 2                    "cool"                  "ccoling"
        EUROTRONIC_MODE_HEAT = 1             MODE_AUTO = 3                    "auto"                                        auto()                            
        EUROTRONIC_MODE_FULL_POWER = 15      MODE_AUXILIARY_HEAT = 4          "emergencyHheat"
        EUROTRONIC_MODE_ECO_ENERGY_HEAT = 11 MODE_ENERGY_SAVE_HEAT = 11       "eco" 
                                             MODE_ENERGY_SAVE_COOL = 12
        EUROTRONIC_MODE_DIRECT_VALVE_CONTROL = 31  n/a                       "dvc"
*/



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
		// custom parameter KK
        input(name: "simulateCool", type: "bool", title: "Simulate cooling capabiities", description: "Enable / Disable Cooling Capability simulation" , defaultValue: false, submitOnChange: true, displayDuringSetup: false, required: false)
        // debug OFF
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
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
				log.warn "Non-parsed event: ${description}"
			}
		}
		return result
	}
			
	
	//Battery
	def zwaveEvent(hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
		def map = [ name: "battery", unit: "%", type: "physical" ]
      	if (cmd.batteryLevel == 0xFF) {  // Special value for low battery alert
			map.value = 1
			map.descriptionText = "${device.displayName} has a low battery"
			map.isStateChange = true
		} 
		else {
			map.value = cmd.batteryLevel
		}
        state.lastBatteryReport = new Date().time           // Store time of last battery report
        if ( logEnable ) { log.debug "Report Received : $cmd" }
		sendEvent(map)
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
			sendEvent(name: "lock", value: eventValue, type: "physical", isStateChange: true, dispayed: true)
		}

		if ( logEnable ) { log.info "Protection State - ${eventValue}" }
		//sendEvent(name: "lastCheckin", value: new Date())
	}


	//Valve report z-wave event reveived
	def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd){
		log.info "switchmultilevelv3 report received : Valve is open ${cmd.value}%"
		//def event = []
        sendEvent(name:"level", value: cmd.value, unit:"%", type: "physical", isStateChange: true, displayed: true)
        // KK: potentially change the thermostatOperatingState depending on whether the valve was reired to be fully closed or partially open..

		def currentState = device.currentValue("thermostatOperatingState")
       	if ( logEnable ) { log.trace "Thermostat thermostatOperatingState state WAS : $currentState" }
       	def isReportedValveOpen = cmd.value != 0 ? true : false 
       	if ( logEnable ) { log.trace "isReportedValveOpen : $isReportedValveOpen" }
       	def map2 = [:]
       	map2.type = "digital"
       	map2.name = "thermostatOperatingState"
       	map2.value = null
    	map2.isStateChange = true
            
       	//possible STANDARD thermostatOperatingState - ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]
       	switch ( device.currentValue("thermostatOperatingState") ) {
           	case "idle" :
           	case "off" :
           		if ( isReportedValveOpen ) {
                   	map2.value = "heating" 
                   	if ( logEnable ) {log.info "After Valve reprot was received: now changing the thermostatOperatingState to HEATING !"}
            	}
           		break
           	case "heating" :
           		if ( !isReportedValveOpen ) {
               		map2.value = "idle" 
               		if ( logEnable ) { log.info "After Valve reprot was received: now changing the thermostatOperatingState to IDLE !" }
           		}
           		break
           	case "cooling" :    // if COOL is simulated..
       			if ( isReportedValveOpen ) {
               		// should NOT HAPPEN
               		map2.value = "heating" 
               		log.error "After Valve reprot was received WHILE IN COOLIING STATE: now changing the thermostatOperatingState to HEATING !"
           		}
           		break
           	default : 
           		log.error "After Valve reprot was received: UNSUPPORTED thermostatOperatingState ${device.currentValue("thermostatOperatingState")}!"
		}
            
       	if ( map2.value != null ) {
            return map2
	    }
       	else {
           	if ( logEnable ) { log.debug "no change of thermostatOperatingState after Valve reprot was received" }
	    }
	}


	//Temperature
	def zwaveEvent(hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	    if ( logEnable ) { log.debug "Report Received : $cmd" }
		def map = [ value: cmd.scaledSensorValue.toString(), displayed: true ]
		def value = cmd.scaledSensorValue.toString()
		map.name = "temperature"
		map.unit = cmd.scale == 1 ? "F" : "C"
        map.type = "physical"
		map.isStateChange = true    // KK
		state.temperature = cmd.scaledSensorValue //.toString()
		createEvent(map)
        log.info "Temperature is $value"
        map
	}

	//Thermostat SetPoint
	def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) { //	Parsed ThermostatSetpointReport(precision: 2, reserved01: 0, scale: 0, scaledValue: 21.00, setpointType: 1, size: 2, value: [8, 52])
		def event = []
		def currentState = device.currentValue("thermostatOperatingState")
        if ( logEnable ) { log.info "# thermostatsetpointv2.ThermostatSetpointReport received while in thermostatOperatingState = ${currentState} " }
 		state.scale = cmd.scale	// So we can respond with same format later, see setHeatingSetpoint()
		state.precision = cmd.precision
		def radiatorSetPoint = cmd.scaledValue
        switch (cmd.setpointType) {
        	case 1:                  //  SETPOINT_TYPE_HEATING_1 - this is the standard heating setpoint
		        if ( logEnable ) { log.info "thermostatsetpointv2.ThermostatSetpointReport setpointType==3 Received, currentState ${currentState}" }
				event << createEvent(name: "nextHeatingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), /*isStateChange: true, */ displayed: true)
			   	event << createEvent(name: "heatingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), type: "physical", isStateChange: true, displayed: true)																																						  
			   	event << createEvent(name: "thermostatSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), /*isStateChange: true, */ displayed: false)
			   	event << createEvent(name: "thermostatTemperatureSetpoint", value: radiatorSetPoint.toString(), unit: "C", /*isStateChange: true,*/ displayed: false)
		        break
            case 11:                 // SETPOINT_TYPE_ENERGY_SAVE_HEATING - this is eco heat setting on this device
            	if ( logEnable ) { log.info "thermostatsetpointv2.ThermostatSetpointReport setpointType==3 Received, currentState ${currentState}" }
			   	event << createEvent(name: "ecoHeatingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), type: "physical", isStateChange: true, displayed: true)																																						  
		        break
            case 2: // SETPOINT_TYPE_COOLING_1
            	if ( logEnable ) { log.warn "thermostatsetpointv2.ThermostatSetpointReport setpointType==2 Received : ${cmd} !!!!!!!!!" }
			   	event << createEvent(name: "nextCoolingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), isStateChange: true, displayed: true)
			   	event << createEvent(name: "coolingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), type: "physical", isStateChange: true, displayed: true)																																						  
			   	event << createEvent(name: "thermostatSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), isStateChange: true, displayed: false)
			   	event << createEvent(name: "thermostatTemperatureSetpoint", value: radiatorSetPoint.toString(), unit: "C",isStateChange: true, displayed: false)
            	log.warn "!! set: nextCoolingSetpoint, coolingSetpoint, thermostatSetpoint, thermostatTemperatureSetpoint !!"
            	break
       		case 3:                  // SETPOINT_TYPE_NOT_SUPPORTED1, but Eurotronic is sending it? 
           		if ( cmd.setpointType == 3) {log.warn "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! thermostatsetpointv2.ThermostatSetpointReport setpointType==3 Received : ${cmd} !!!!!!!!!" }
           		break
       		default :
           		log.error "!!!!!!!!!!!!!!!!!!!!!!!!! thermostatsetpointv2.ThermostatSetpointReport UNKNOWN setpointType = ${cmd.setpointType} Received : ${cmd} !!!!!!!!!!!!!!!"
           		break
	        }
        	if (event)
		    return event
	}



	//Basic Operating State
	def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd){
        log.warn "basicv1.BasicReporT Received : ${cmd}"
        def event = [ ]
		if (cmd.value == 255) { //255 - 0xFF = normal mode
			state.thermostatReportedMode = "heat"
			state.thermostatOperatingState = "heating"
			state.switch = "on"
		}
		if (cmd.value == 240){ //240 - 0xF0 = Boost
			state.thermostatReportedMode = "emergencyHeat"
			if ( simulateCool == true ) {
				state.thermostatOperatingState = "heating"                // KK was "boost"
			}
			else {
				state.thermostatOperatingState = "boost"
			}
			state.switch = "on"
		}
		if (cmd.value == 0){ //0 - 0x00 = eco
			state.thermostatReportedMode = "eco"
			state.thermostatOperatingState = "eco"
			state.switch = "on"
		}
		if (cmd.value == 15){ //15 - 0x0F = off
			if ( simulateCool == true ) {
				state.thermostatReportedMode = "cool"              	              // KK was "Switched off" 
				state.thermostatOperatingState = "cooling"                   //KK was "frost"
			}
			else {
				state.thermostatReportedMode = "off"
				state.thermostatOperatingState = "off"
			}
			state.switch = "off"
		}
		if (cmd.value == 254){     //254 - 0xFE = direct valve control mode
			state.thermostatReportedMode = "dvc"
			state.thermostatOperatingState = "Direct Valve Control"
			state.switch = "on"
		}
	
		event << createEvent(name: "thermostatMode", value: state.thermostatReportedMode, isStateChange: true, displayed: true)
		event << createEvent(name: "thermostatOperatingState", value: state.thermostatOperatingState, isStateChange: true, displayed: true)
		event << createEvent(name: "switch", value: state.switch, isStateChange: true, displayed: true)
					
        return event
	}


	//Thermostat Mode
	def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd ) {
       	if ( logEnable ) { log.debug "* Received thermostatmodev2.ThermostatModeReport : ${cmd.mode}" }
        def mapThermostatMode = [:]
       	mapThermostatMode.type = "physical"
        switch (cmd.mode) {
			case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF:        // 0
    			state.thermostatReportedMode = "off"
           		if (state.lastEurotronicModeSet == "cool" ) {
                	mapThermostatMode.value = "cool"
                }
                else {
        			mapThermostatMode.value = "off"
	            }
				break
			case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT:           // 1
    			state.thermostatReportedMode = "heat"
           		if ( state.lastEurotronicModeSet == "auto" ) {
		    		mapThermostatMode.value = "auto"
                }
           		else {
					mapThermostatMode.value = "heat"
                }
				break
		 	case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_COOL:            // 2
    			state.thermostatReportedMode = "off"
				mapThermostatMode.value = "cool"
				break
			case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_AUTO:            // 3
    			state.thermostatReportedMode = "heat"
				mapThermostatMode.value = "auto"
				break
      		case hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_ENERGY_SAVE_HEAT:    // 11  EUROTRONIC_MODE_ECO_ENERGY_HEAT = 11  
    			state.thermostatReportedMode = "eco"
				mapThermostatMode.value = "eco"
                break
	    	case 15:                                                                               // non-standard ....
    			state.thermostatReportedMode = "emergencyHeat"
				mapThermostatMode.value = "emergencyHeat"
		    	break
	    	case 31:                                                                               // non-standard ....
    			state.thermostatReportedMode = "dvc"
				mapThermostatMode.value = "dvc"
		    	break
       		default :
    			state.thermostatReportedMode = "unknown"
				mapThermostatMode.value = "unknown"
           		log.error "!!!!!!!!!! thermostatmodev2.ThermostatModeReport unsupported mode ${cmd.mode} !!!!!!!!!!!!"
           		break
	        }
	        mapThermostatMode.name = "thermostatMode"
           	mapThermostatMode.isStateChange = true
	        mapThermostatMode
	}


	//Supported Modes
	def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
		// log.trace "$cmd"
		def supportedModes = []
       	//state.supportedModes = [auto, off, heat, emergencyHeat, eco, dvcon, cool] // basic modes prior to details from device

		supportedModes << "auto" 
		supportedModes << "off"
		supportedModes << "heat" 
		supportedModes << "emergency heat" 
		supportedModes << "eco" 
		supportedModes << "dvcon" 
		// KK - check !
		if ( simulateCool == true ){
			supportedModes << "cool" 
		}
        state.supportedModes = supportedModes 
		sendEvent(name: "supportedModes", value: supportedModes, isStateChange: true, type: "physical", displayed: false)
		sendEvent(name: "supportedThermostatModes", value: supportedModes, isStateChange: true, type: "digital", displayed: false)
   		if ( logEnable ) { log.debug "Report Received thermostatmodev2: $cmd, Thermostat supported modes : $supportedModes" }
	}


	def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
		log.info "Notification Report : $cmd"
		def events = []
		switch (cmd.notificationType) {
			case 8:
				events << processPowerMgtNot(cmd)
				break
			case 9:
				events << processSystemNot(cmd)
				break
		}
	}


	private processPowerMgtNot(cmd) {
		def descriptionText = "unknown"
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
		sendEvent(name: "NotificationCC_Power", value: descriptionText, isStateChange: true, displayed: false)			
		log.info "Power management event: Warning! $descriptionText"
	}
		
		private processSystemNot(cmd) {
			def descriptionText = "unknown"
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
			sendEvent(name: "NotificationCC_System", value: descriptionText, isStateChange: true, displayed: false)
			log.info "System event: $descriptionText"
		}


		def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
			log.warn "Report Received : $cmd"
			def result = []
			if (cmd.nodeId.any { it == zwaveHubNodeId }) {
				result << sendEvent(descriptionText: "$device.displayName is associated in group ${cmd.groupingIdentifier}")
			} else if (cmd.groupingIdentifier == 1) {
				// We're not associated properly to group 1, set association
				result << sendEvent(descriptionText: "Associating $device.displayName in group ${cmd.groupingIdentifier}")
				result << response(zwave.associationV1.associationSet(groupingIdentifier:cmd.groupingIdentifier, nodeId:zwaveHubNodeId))
			}
			result
		}

	//
	def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation	 cmd) { // Devices that support the Security command class can send messages in an encrypted form; they arrive wrapped in a SecurityMessageEncapsulation command and must be unencapsulated
        	if ( logEnable ) { log.debug "raw secEncap $cmd" }
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
		log.debug "Report Received : $cmd"
		if (cmd.manufacturerName) { updateDataValue("manufacturer", cmd.manufacturerName) }
		if (cmd.productTypeId) { updateDataValue("productTypeId", cmd.productTypeId.toString()) }
		if (cmd.productId) { updateDataValue("productId", cmd.productId.toString()) }
		if (cmd.manufacturerId){ updateDataValue("manufacturerId", cmd.manufacturerId.toString()) }
	}

	def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd ) {
		if ( logEnable ) { log.debug "Report Received : $cmd" }
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
	        if ( logEnable ) { log.debug "Version Report: $cmd" }
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
		runIn (5, "buffSetpoint",[data: [value: nextTemp], overwrite: true])
		sendEvent(name:"nextHeatingSetpoint", value: nextTemp, unit: getTemperatureScale(), displayed: false)	
	}

	def temperatureDown() {
        def nextTemp = currentDouble("nextHeatingSetpoint") - 0.5d
        if(nextTemp > 28) {		// It can't handle above 28, so don't allow it go above
			nextTemp = 28
		}
            
       	if(nextTemp < 8) {		// It can't go below 8, so don't allow it
			nextTemp = 8
		}
		runIn (5, "buffSetpoint",[data: [value: nextTemp], overwrite: true])
		sendEvent(name:"nextHeatingSetpoint", value: nextTemp, unit: getTemperatureScale(), displayed: false)	
	}


	def buffSetpoint(data) {
		def key = "value"
		def nextTemp = data[key]
       	if ( logEnable ) { log.debug " buff nextTemp is $nextTemp" }
		setHeatingSetpoint(nextTemp)
	}

	def setCoolingSetpoint(temp){
		if ( simulateCool == true ){
       		log.warn " setCoolingSetpoint $temp"
           	sendEvent(name: "coolingSetpoint", value: temp, unit: getTemperatureScale(), isStateChange: true, displayed: true)																																				   
		}
		else {
       		def event = []
			event << createEvent(name: "nextCoolingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), isStateChange: true, displayed: true)
			event << createEvent(name: "coolingSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), isStateChange: true, displayed: true)																																						  
			event << createEvent(name: "thermostatSetpoint", value: radiatorSetPoint.toString(), unit: getTemperatureScale(), isStateChange: true, displayed: false)
			event << createEvent(name: "thermostatTemperatureSetpoint", value: radiatorSetPoint.toString(), unit: "C",isStateChange: true, displayed: false)
		}
		if (event)
   			secureSequence(event)
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
            log.trace "###def setHeatingSetpoint(Double degrees) was called ### ->Setting Temp to ${degrees},  $cmds"
		}
		if (device.currentValue("thermostatMode") == "eco") {
            //ecoTemp=18
			cmds << zwave.thermostatSetpointV2.thermostatSetpointSet(precision: precision, scale: deviceScale, scaledValue: ecoTemp, setpointType: 11)
			cmds << zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)
   	        log.warn "Setting Eco Temp to ${ecoTemp},  $cmds"
		}
       	// added KK - get the valve reading after 5 seconds!
		runIn (05, pollValve)
		secureSequence(cmds)
	}

	//lock
	def lock() {
		log.trace "locking $cmds" 
		def cmds = []
		sendEvent(name: "lock", value: "locking", isStateChange: true, displayed: false)
		cmds << zwave.protectionV1.protectionSet(protectionState: 1)
		cmds << zwave.protectionV1.protectionGet()
		secureSequence(cmds)
	}

	//unlock
	def unlock() {
		log.trace "unlocking $cmds" 
		def cmds = []
		sendEvent(name: "lock", value: "unlocking", isStateChange: true, displayed: false)
		cmds << zwave.protectionV1.protectionSet(protectionState: 0)
		cmds << zwave.protectionV1.protectionGet()
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

/*

	def booston() {
		heat()
	}


	def boostoff(){
		auto()
	}

*/
    // Thermostat Mode 0x0F : Full Power. Switches into Boost mode (Quick heat). Spirit Z-Wave Plus heats the room up as fast as possible. 
    // The mode is left automatically after 5 minutes or earlier if requested by the user(via Z-Wave or locally on the device).
	//
	def emergencyHeat() { 
       	state.lastEurotronicModeSet = "emergencyHeat"
       	log.trace "... setting emergencyHeat() mode ..."
		def cmds = []
		sendEvent(name: "thermostatMode", value: "emergencyHeat", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 15)        // 0x0F
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		secureSequence(cmds)
	}


	def heat() {
       	state.lastEurotronicModeSet = "heat"
       	log.trace "... setting heat() mode ..."
		def cmds = []
		sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, displayed: true)
       	sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1)        // EUROTRONIC_MODE_HEAT
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		secureSequence (cmds)        
   }


    // Thermostat Mode 0x01 : Switches into comfort heating mode. The room temperature will be kept at the configured comfortable level. 
	def auto(){ //Comfort
        state.lastEurotronicModeSet = "auto"
		def cmds = []
		sendEvent(name: "thermostatMode", value: "auto", isStateChange: true, displayed: true)
       	sendEvent(name: "thermostatOperatingState", value: "heating", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 1 )    // EUROTRONIC_MODE_HEAT
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		// log.trace "auto $cmds" 
		secureSequence (cmds)
	}

	// Thermostat Mode 0x00 : off. No heating. Only frost protection.
	def off() {    //KK mode:0 )
		log.warn "... sending off()...  ${device.displayName} " 
       	def cmds = []
       	state.lastEurotronicModeSet = "off"
        
       	sendEvent(name: "thermostatMode", value: "off", isStateChange: true, displayed: true)
       	if ( simulateCool == true ) {
           	sendEvent(name: "thermostatOperatingState", value: "cooling", isStateChange: true, displayed: true)
       	}
       	else {
           	sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, displayed: true)
        }

		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0)    //EUROTRONIC_MODE_OFF
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		secureSequence (cmds)
	}

    // simulate cool() command !
    // Thermostat Mode 0x00 : off. No heating. Only frost protection. + operaringState "cooling"  ... :) 
	def cool() {    //KK mode:0 
		state.lastEurotronicModeSet = "cool"
       	if ( simulateCool == true ) {
           	off()
		}
		else {
           	off()    // for now - do the same as in simulateCool mode ...
		}
	}
    


    // Thermostat Mode 0x0B : Energy Heat. Switches into energy save heating mode. The room temperature will be lowered to the configured setpoint in order to save energy..
	def eco() {   
		log.trace "ecp $cmds" 
        state.lastEurotronicModeSet = "eco"
       	def cmds = []
		sendEvent(name: "thermostatMode", value: "eco", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 11 )    // EUROTRONIC_MODE_ECO_ENERGY_HEAT
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)
		secureSequence (cmds)
	}

	def on(){
        state.lastEurotronicModeSet = "on"
        auto()
	}

/*
	def froston(){ //taken from switchoff new app / frost tile classic
		// log.trace "froston $cmds" 
        state.lastEurotronicModeSet = "froston"
		def cmds = []
		sendEvent(name: "thermostatMode", value: "switched off", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 0)    //EUROTRONIC_MODE_OFF
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		secureSequence(cmds)
	}
		 
	def frostoff(){
        state.lastEurotronicModeSet = "frostoff"
		auto()
	}
*/
			
	def dvcon() { //taken from Mode off new app
        state.lastEurotronicModeSet = "dvcon"
		def cmds = []
        if ( simulateCool == true ){
			sendEvent(name: "thermostatMode", value: "cool", isStateChange: true, displayed: true) 
		}
		else {
			sendEvent(name: "thermostatMode", value: "dvc", isStateChange: true, displayed: true)
		}
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 31 )        //  0x1F (31) EUROTRONIC_MODE_DIRECT_VALVE_CONTROL
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)
		log.warn "DVC On : $cmds"
		secureSequence(cmds)
		if ( simulateCool == true ){ 
	       	def map2 = [:]
			map2.value = "cooling" 
			map2.name = "thermostatOperatingState"
	    	map2.isStateChange = true
			log.info (map2)
			sendEvent(map2)
	        setLevel(0)     // close the valve!
       	}
		else {
			def map2 = [:]
			map2.value = "dvc" 
			map2.name = "thermostatOperatingState"
	   		map2.isStateChange = true
			log.info (map2)
			sendEvent(map2)
	        setLevel(0)     // close the valve!
		}
       	secureSequence(cmds)
	}
		  
	def dvcoff(){
		auto()
	    state.lastEurotronicModeSet = "auto"
	}
		  
/*
	def ecoon() { 
       	state.lastEurotronicModeSet = "eco"
		def cmds = []
		sendEvent(name: "thermostatMode", value: "eco", isStateChange: true, displayed: true)
		cmds << zwave.thermostatModeV2.thermostatModeSet(mode: 11)        //EUROTRONIC_MODE_ECO_ENERGY_HEAT
		cmds << zwave.thermostatModeV2.thermostatModeGet()
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)
		// log.trace "Eco/Cool $cmds"
		sendEvent(name: "ecoMode", value: "on", isStateChange: true, displayed: true)
		secureSequence(cmds)
	}

		 
	def ecooff(){
       	state.lastEurotronicModeSet = "auto"
		sendEvent(name: "ecoMode", value: "off", isStateChange: true, displayed: true)
		auto()
	}
		 	 
*/

	def setThermostatMode(String) {
		def nextMode = String
		switch ("$nextMode") {
			case "heat":
				(heat())        // was (booston())
				break
			case "auto":
				(auto())
				break
			case "cool":
				(off())     
				break
			case "off":
				(off()) 
				break
            case "dvc":
   	            (dvcon())
                 break
            case "emergency heat":
                 (emergencyHeat())
                 break
            case "eco":
                 (eco())
                 break
            default :
                 log.error "!!!!setThermostatMode ${String} ERROR !!!!!!!"
                 break
		}
	}
			
	//SetValve during DVC
	def setLevel(nextLevel) {
		log.warn "Executing 'setLevel' : $cmds"
		def cmds = []
		sendEvent(name:"level", value: nextLevel, displayed: false, isStateChange:true)
		cmds << zwave.switchMultilevelV3.switchMultilevelSet(value: nextLevel)
		cmds << zwave.switchMultilevelV3.switchMultilevelGet()
		secureSequence(cmds)
	}


    // simulate Fan Control ...
    def setThermostatFanMode(mode) {
        log.trace "setThermostatFanMode $mode"
        sendEvent(name:"thermostatFanMode", value: mode, displayed: false, isStateChange:true)
    }


    //
    //  TODO  updateSetPoints(heatingSetpoint, coolingSetpoint)  // used by Thermostat Controller app !!!!!!!!!!!
    // 
    def updateSetPoints ( heatingSP, cooliningSP ) {
        log.warn "...updateSetPoints ${heatingSP} ${cooliningSP} was called!"
        if ( heatingSP ) {
            updateHeatingSetpoint( heatingSP )
        }
        if ( cooliningSP ) {
            updateCoolingSetpoint( coolingSP )
        }
    }

    def setTemperature ( double tmp ) {
            log.warn "...setTemperature ${tmp} was called!"
    }
                
                
	//Refresh (Momentary)
	def refresh() {
		log.trace "refresh() command called"
		poll()
	}

    def daysToTime(days) {
		days*24*60*60*1000
	}
			

	def configure() {
		//state.supportedModes = [off, heat, eco, Boost, dvc] // basic modes prior to details from device
		state.supportedModes = [auto, off, heat, "emergency heat", eco, dvc, cool] // basic modes prior to details from device
		state.lastEurotronicModeSet = "unknown"
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
		//cmds << zwave.thermostatSetpointV1.thermostatSetpointSet(precision: 1, scale: 0, scaledValue: ecoTemp == null ? 18.toBigDecimal() : ecoTemp, setpointType: 11, size: 2, value: ecoTemp == null ? [0, 180] : [0, ecoTemp*10])
        def precision = state.precision ?: 2
		def deviceScale = state.scale ?: 0
    	cmds << zwave.thermostatSetpointV2.thermostatSetpointSet(precision: precision, scale: deviceScale, scaledValue: ecoTemp, setpointType: 11)    //  !! java.lang.NullPointerException: Cannot invoke method and() on null object on line 1634 (configure)
		//cmds << zwave.thermostatSetpointV1.thermostatSetpointSet(precision: 1, scale: 0, scaledValue: 18, setpointType: 11, size: 2, value:  [0, 180])
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
			
	    sendEvent(name: "configuration", value: "sent", isStateChange: true, displayed: true)
		sendEvent(name: "configure", isStateChange: true, displayed: false)
		log.debug "Configuration sent"
		secureSequence(cmds)            
 	}

    def backLightOn() {
        def cmds = []
		cmds << zwave.configurationV1.configurationSet( configurationValue: [0x01] , parameterNumber:3, size:1, scaledConfigurationValue:  0x01 )
		log.debug "backLightOn"
		secureSequence(cmds)            
    }

    def backLightOff() {
        //		cmds << zwave.configurationV1.configurationSet(configurationValue:  backlight == "1" ? [0x01] : [0x00], parameterNumber:3, size:1, scaledConfigurationValue:  backlight == "1" ? 0x01 : 0x00)
        def cmds = []
		cmds << zwave.configurationV1.configurationSet( configurationValue: [0x00] , parameterNumber:3, size:1, scaledConfigurationValue:  0x00 )
		log.debug "backLightOff"
		secureSequence(cmds)            
    }

	def updated() {
	    log.warn "updated()..."
		sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
		if (!state.updatedLastRanAt || new Date().time >= state.updatedLastRanAt + 2000) {
			state.updatedLastRanAt = new Date().time
			unschedule(refresh)
			unschedule(poll)
			log.trace "Configuring settings"
			runIn (05, configure)
			sendEvent(name: "configure", value: "configdue", isStateChange: true, displayed: false)
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
		 
		} else {
			log.warn "update ran within the last 2 seconds"
		}
	}

	// PING is used by Device-Watch in attempt to reach the Device
	def ping() {
		refresh()
	}


    def calibrate() {
        log.debug "starting calibrate() for ${device.displayName} ....."
        state.calibrateState = 0
        runIn (01, 'calibrateStateMachine')    
    }


    def calibrateStateMachine() {
        def ss = state.calibrateState
        switch ( ss ) {
            case 0:    // start
                log.info "state.calibrateState 0 ->  start"
                state.calibrateState = 1
                state.calibrateRetry = 0
                runIn (01, 'calibrateStateMachine')
                break
            
            case 1:    // turn off
                log.debug "state.calibrateState 1 -> now turning OFF"
                state.calibrateState = 2
                state.calibrateRetry = 0
                runIn (15, calibrateStateMachine)
                off()
                break
            
            case 2:    // turn emergencyHeat
                if (state.thermostatReportedMode == "off" || state.calibrateRetry > 1 ) {    // TRV has been successfuly turned off
                    log.debug "state.calibrateState 2 -> now turning ON"
                    state.calibrateState = 3
                    state.calibrateRetry = 0
                    runIn (15, calibrateStateMachine)
                    emergencyHeat()
                }
                else {    // retry
                    state.calibrateRetry = state.calibrateRetry +1
                    state.calibrateState = 2
                    runIn (15, calibrateStateMachine)
                    log.error "ERROR turning OFF - retrying..."
                    off()    // try again
                }
                break
            
            case 3:    // turn heat (auto)
                if (state.thermostatReportedMode == "emergencyHeat" || state.calibrateRetry > 1 ) {    // TRV has been successfuly turned emergencyHeat
                    log.debug "state.calibrateState 3 -> now turning heat/auto"
                    state.calibrateState = 4    // verify if back to normal..
                    state.calibrateRetry = 0
                    runIn (15, calibrateStateMachine)
                    heat()
                }
                else {    // retry
                    state.calibrateRetry = state.calibrateRetry +1
                    state.calibrateState = 3
                    runIn (15, calibrateStateMachine)
                    log.error "ERROR turning emergencyHeta - retrying..."
                    emergencyHeat()    // try again
                }
                break
            
            case 4:    // verify if back to heat (auto)
                if (state.thermostatReportedMode == "heat" || state.calibrateRetry > 1 ) {    // TRV has been successfuly turned to heat/auto
                    log.debug "state.calibrateState 4 ->  finished successfuly"
                    state.calibrateState = 0
                    state.calibrateRetry = 0
                }
                else {    // retry
                    state.calibrateRetry = state.calibrateRetry +1
                    state.calibrateState = 3
                    runIn (15, calibrateStateMachine)
                    log.error "ERROR turning heat/auto - retrying..."
                    heat()    // try again
                }
                break

            default :
                log.error "state.calibrate UNKNOWN= ${state.calibrate}"
                state.calibrateState = 0
                state.calibrateRetry = 0
                break
        }
        
        // off() emergencyHeat() heat() MUST be the LAST executed commands in the function !!!!!!!!!!!!!!!!!!!!!
        /*
            If you want to send a Z-Wave command without it being the return value of a (driver) command, then you can do so manually with the sendHubCommand() 1 method. 
            Something like this, for example: sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.meterV4.meterGet(scale: 0)), hubitat.device.Protocol.ZWAVE))

        */
    }

    def pollValve() {
            log.debug "polling Valve only!"
			def cmds = []
      		cmds << zwave.switchMultilevelV3.switchMultilevelGet()	// valve position
    		secureSequence (cmds)
    }

	def poll() { // If you add the Polling capability to your device type, this command will be called approximately every 5 minutes to check the device's state
		log.debug "polling..."
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

         /*
		//once an hour ask for everything
		if (!state.extra || (new Date().time) - state.extra > (60*60000)) {			// minutes * millseconds these settings shouldnt be needs as device should send response at time of update
			//cmds <<	zwave.thermostatModeV2.thermostatModeGet()	// get mode
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11) 	// get eco/cool setpoint
			cmds <<	zwave.basicV1.basicGet()											// get mode (basic)	
			cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)	// get heating setpoint
	            	cmds << zwave.batteryV1.batteryGet()
			state.extra = new Date().time
		}
	*/
		cmds <<	zwave.sensorMultilevelV1.sensorMultilevelGet()	// get temp
		cmds << zwave.switchMultilevelV3.switchMultilevelGet()	// valve position
		cmds <<	zwave.thermostatModeV1.thermostatModeGet()		// get mode
		cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1)	// get heating setpoint
                //cmds <<	zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 11)	// get heating setpoint            // KK - dupicates the reports !!
		// log.debug "POLL $cmds"
        if (cmds) {
			secureSequence (cmds)
        }
	}


	def setDeviceLimits() { // for google and amazon compatability
		sendEvent(name:"minHeatingSetpoint", value: settings.tempMin ?: 8, unit: "°C", isStateChange: true, displayed: false)
		sendEvent(name:"maxHeatingSetpoint", value: settings.tempMax ?: 28, unit: "°C", isStateChange: true, displayed: false)
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
	    sendEvent(name: "batteryLastReplaced", value: new Date(), isStateChange: true)
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


	def installed() {
	    log.warn "installed()..."
	    //configure()
	    //runIn(1800,logsOff)
	}



