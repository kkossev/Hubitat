/**
 *  Zigbee Reporting Configuration HE driver
 *
 *  This is a HE driver to configure the reporting settings for Zigbee devices.
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 * 
 *  Based on various other drivers including Hubitat crew examples, David McPaul, Markus Liljergren and many others
 * 
 *  ver. 1.0.0 2021-11-07 kkossev  - first version (temperature and humidity configuration when the device awakes)
 *  ver. 1.0.1 2021-11-07 kkossev  - added sendConfigurationToDeviceNow
 *  ver. 1.1.0 2021-11-08 kkossev  - TODO !!!!!!!!!!!!!!!!!!!!reporting configuration settings moved from Commands into Preferencies section
 *                                 - TODO !!!!!!!!!!!!  added reading of the device Reporting parameters; the actually read values are put as default preferencies...
 *
*/
public static String version()	  { return "v1.1.0" }


import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubMultiAction
import hubitat.zigbee.zcl.DataType
import hubitat.zigbee.clusters.iaszone.ZoneStatus
import java.util.concurrent.*

// Field annotation makes these variables global to the class
@Field static java.util.concurrent.Semaphore mutex = new java.util.concurrent.Semaphore(1)
@Field static def queueMap = [:]
@Field static def displayCounter
@Field static def timeoutCounter

    // Constants
@Field static final Integer CONST = 0
@Field static final String BATTERY = "Battery"
@Field static final String TEMPERATURE = "Temperature"


int staticTestNumber = 99

metadata {
    definition (name: "Zigbee Reporting Configuration", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Zigbee%20Reporting%20Configuration/Zigbee%20Reporting%20Configuration.groovy" ) {

    capability "TemperatureMeasurement"
	capability "RelativeHumidityMeasurement"
	capability "PressureMeasurement"            // pressure - NUMBER, unit: Pa || psi ???
	capability "Battery"
        
    //capability "Initialize"

    attribute   "_1", "string"        // when defined as attributes, will be shown on top of the 'Current States' list ...
    attribute   "_2", "string"
    attribute   "_3", "string"
  
    command "_0_getDeviceInfo"
    command "_1_readReportingConfiguration"
    command "_2_configureeReporting", [ 
        [name: "measurement*", type: "ENUM", constraints: ["*** Select ***", BATTERY, "Temperature", "Humidity"], description: "Select measurement to configure"],
        [name: "Minimum Reporting Interval (seconds)", type: "NUMBER", defaultValue: 2, value: 3, description: "Select Minimum reporting time (in seconds)"],
        [name: "Maximum Reporting Interval (seconds)", type: "NUMBER", constraints: ["3600", "120", "300", "600", "900", "1800", "7200", "43200"], description: "Select Maximim reporting time (in seconds)"],
        [name: "Minimum measurement change", type: "NUMBER", constraints: ["0.25", "0.01", "0.05", "0.10", "0.50", "1.00", "2.0", "5.0"], description: "Select Minimum measurement change to be reported"]
    ]
    command "_3_sendConfigurationToDevice"
    command "readAttribute", [ 
        [name: "attribute*", type: "ENUM", constraints: ["*** Select ***","Battery", "Temperature", "Humidity"], description: "Select attribute to read"]
    ]
        
    command "test"

      
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "ANY", model: "ANY", deviceJoinName: "Zigbee Reporting Configuration"
    }
    preferences {
            input (name: "_showAllPreferences",type: "bool", title: "<b>Show All Preferences?</b>", defaultValue: true )
            input (name: "traceEnable", type: "bool", title: "Enable trace logging", defaultValue: true)
            input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
            input (name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true)
            if( _showAllPreferences || _showAllPreferences == null ){ // Show the preferences options
                input (name: "repairAggressive", type: "bool", title: "Re-pair with aggressive reporting settings", defaultValue: false)
/*
                input name: "param1", title: "Selective reporting - Threshold Temperature", description: "°C", type: "enum", 
                    options:[[0:"Disabled (only time-based reports)"], [1:"0.1°C"], [2:"0.2°C"], [3:"0.3°C"], [4:"0.4°C"], [5:"0.5°C"], [10:"1°C"], [15:"1.5°C"], [20:"2°C"], [25:"2.5°C"], [30:"3°C"], [40:"4°C"], [50:"5°C"]],
                    defaultValue: 5, required: true
                input (name: "testNumber", type: "number", title: "number test", defaultValue: $staticTestNumber, value:$staticTestNumber)
*/
            }
    }
}



// Parse incoming device messages to generate events
//
//parsers
void parse(String description) {
    //
    deviceReported()
    //
    Map descMap = zigbee.parseDescriptionAsMap(description)
    if (logEnable) log.debug "descMap:${descMap}"
    String status

    if (descMap.clusterId != null && descMap.profileId == "0104") {
        if (descMap.isClusterSpecific == false) { //global commands
            processGlobalCommand(descMap)
        } else { //cluster specific
            switch (descMap.clusterId) {
                case "0004": //group
                    processGroupCommand(descMap)
                    break
                case "0006": 
                    log.info "cluster: ${descMap.clusterId} command: ${descMap.command}"
                    break
                default :
                    if (logEnable) log.warn "skipped cluster specific command cluster:${descMap.clusterId}, command:${descMap.command}, data:${descMap.data}"
            }
        }
        return
    } else if (descMap.profileId == "0000") { //zdo
        switch (descMap.clusterId) {
            case "8005" : //endpoint response
                def endpointCount = descMap.data[4]
                def endpointList = descMap.data[5]
                log.info "zdo command: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}"
                break
            case "8004" : //simple descriptor response
                log.info "zdo command: cluster: ${descMap.clusterId} (simple descriptor response)"
                break
            case "8034" : //leave response
                log.info "zdo command: cluster: ${descMap.clusterId} (leave response)"
                break
            case "8021" : //bind response
                log.info "zdo command: cluster: ${descMap.clusterId} (bind response)"
                break
            case "8022" : //unbind request
                log.info "zdo command: cluster: ${descMap.clusterId} (unbind request)"
                break
            case "0013" : //"device announce"
                log.info "zdo command: cluster: ${descMap.clusterId} (device announce)"
                if (logEnable) log.trace "device announce..."
                break
            default :
                if (logEnable) log.warn "skipped UNKNOWN zdo cluster: ${descMap.clusterId}"
        }
        return
    }

    List<Map> additionalAttributes = []
    additionalAttributes.add(["attrId":descMap.attrId, "value":descMap.value, "encoding":descMap.encoding])
    if (descMap.additionalAttrs) additionalAttributes.addAll(descMap.additionalAttrs)
    parseAttributes(descMap, descMap.cluster, descMap.endpoint, additionalAttributes, descMap.command)
}


private void parseAttributes(Map descMap, String cluster, String endPoint, List<Map> additionalAttributes, String command){
    //if (logEnable) log.warn "parseAttributes cluster:${cluster}"
    additionalAttributes.each{
        switch (cluster) {
            case "0000" :     // Basic cluster               
                switch (it.attrId) {
                    case "0000" :    // u8ZCLVersion
                        log.info "parseAttributes: ZLC version: ${it.value}"            // default 0x03
                        break
                    case "0001" :    // u8ApplicationVersion
                        def text = "received Applicaiton version: ${it.value}"        // For example, 0b 01 00 0001 = 1.0.1, where 0x41 is 1.0.1
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        break                                           
                    case "0002" :    // u8StackVersion
                        log.info "parseAttributes: Stack version: ${it.value}"             // default 0x02
                        break
                    case "0003" :   // u8HardwareVersion
                        log.info "parseAttributes: HW version: ${it.value}"                // default 0x01
                        break
                    case "0004" :    // au8ManufacturerName[32]
                        def text = "received Manufacturer name: ${it.value}"
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        break
                    case "0005" :    // au8ModelIdentifier[32]
                        def text = "received Model Identifier: ${it.value}"
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        break
                    case "0006" :  // au8DateCode[16]
                        def text = "received Date Code: ${it.value}"
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        break
                    case "0007" :    // ePowerSource (enum8)
                        def text = "received Power Source: ${it.value}"                // enum8-0x30 default 0x03
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        switch (it.value) {
                            case "00" : state.powerSource = "Battery"; break
                            case "01" : state.powerSource = "Mains"; break
                            default : state.powerSource = "Unknown"; break
                        }
                        break
                    case "4000" :    //software build
                        updateDataValue("softwareBuild",it.value ?: "unknown")
                        break
                    case "FFFD" :    // Cluster Revision (Tuya specific)
                        log.info "parseAttributes: Cluster Revision 0xFFFD: ${it.value}"    //uint16 -0x21 default 0x0001
                        break
                    case "FFFE" :    // Tuya specific
                        log.info "parseAttributes: Tuya specific 0xFFFE: ${it.value}"
                        break
                    default :
                        if (logEnable) log.warn "parseAttributes cluster:${cluster} UNKNOWN  attrId ${it.attrId} value:${it.value}"
                }
                break
            case "0001" :
                 switch (it.attrId) {
                    case "0020" :
                        batteryVoltageEvent(Integer.parseInt(descMap.value, 16))
                        def text = "received batteryVoltageEvent: ${it.value}"
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        stopDisplayCounter()
                        break
                    case "0021" :
                        batteryPercentageEvent(Integer.parseInt(descMap.value, 16))
                        def text = "received batteryPercentageEvent: ${it.value}"
                        log.info "${device.displayName} ${text}"
                        updateCurrentStates(" "," ",text)
                        stopDisplayCounter()
                        break
                    default :
                        if (logEnable) log.warn "parseAttributes cluster:${cluster} UNKNOWN  attrId ${it.attrId} value:${it.value}"
                }
                break               
            case "0006" :
                switch (it.attrId) {
                    /*
                        // https://github.com/zigpy/zha-device-handlers/pull/1105/commits/3af7d9776b90f275b068bb91e00e8e0633bef1ef
                            attributes = OnOff.attributes.copy()
                    attributes.update({0x8002: ("power_on_state", TZBPowerOnState)})
                    attributes.update({0x8001: ("backlight_mode", SwitchBackLight)})
                    attributes.update({0x8002: ("power_on_state", PowerOnState)})
                    attributes.update({0x8004: ("switch_mode", SwitchMode)})
                    */
                    case "8004" :        // Tuya TS004F
                        def mode = it.value=="00" ? "Dimmer" : it.value=="01" ? "Scene Switch" : "UNKNOWN " + it.value.ToString()
                        if (logEnable) log.info "parseAttributes cluster:${cluster} attrId ${it.attrId} TS004F mode: ${mode}"
                        break
                    default :
                        if (logEnable) log.warn "parseAttributes cluster:${cluster} UNKNOWN  attrId ${it.attrId} value:${it.value}"
                }
                break
            case "0008" :
                if (logEnable) log.warn "parseAttributes UNPROCESSED cluster:${cluster} attrId ${it.attrId} value:${it.value}"
                break
            case "0300" :
                if (logEnable) log.warn "parseAttributes UNPROCESSED cluster:${cluster} attrId ${it.attrId} value:${it.value}"
                break
            case "0402" : // temperature
                temperatureEvent(hexStrToSignedInt(descMap.value))
                break
            case "0403" : // pressure
                pressureEvent(Integer.parseInt(descMap.value, 16))
                break
            case "0405" : // humidity
                humidityEvent(Integer.parseInt(descMap.value, 16))
                break
            default :
                if (logEnable) {
                    String respType = (command == "0A") ? "reportResponse" : "readAttributeResponse"
                    log.warn "parseAttributes: UNPROCESSED :${cluster}:${it.attrId}, value:${it.value}, encoding:${it.encoding}, respType:${respType}"
                }
        } // cluster
    } // for each additionalAttributes
}


private void processGroupCommand(Map descMap) {
    String status = descMap.data[0]
    String group
    if (state.groups == null) state.groups = []

    switch (descMap.command){
        case "00" : //add group response
            if (status in ["00","8A"]) {
                group = descMap.data[1] + descMap.data[2]
                if (group in state.groups) {
                    if (txtEnable) log.info "group membership refreshed"
                } else {
                    state.groups.add(group)
                    if (txtEnable) log.info "group membership added"
                }
            } else {
                log.warn "${device.displayName}'s group table is full, unable to add group..."
            }
            break
        case "03" : //remove group response
            group = descMap.data[1] + descMap.data[2]
            state.groups.remove(group)
            if (txtEnable) log.info "group membership removed"
            break
        case "02" : //group membership response
            Integer groupCount = hexStrToUnsignedInt(descMap.data[1])
            if (groupCount == 0 && state.groups != []) {
                List<String> cmds = []
                state.groups.each {
                    cmds.addAll(zigbee.command(0x0004,0x00,[:],0,"${it} 00"))
                    if (txtEnable) log.warn "update group:${it} on device"
                }
                sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds,500), hubitat.device.Protocol.ZIGBEE))
            } else {
                //get groups and update state...
                Integer crntByte = 0
                for (int i = 0; i < groupCount; i++) {
                    crntByte = (i * 2) + 2
                    group = descMap.data[crntByte] + descMap.data[crntByte + 1]
                    if ( !(group in state.groups) ) {
                        state.groups.add(group)
                        if (txtEnable) log.info "group added to local list"
                    } else {
                        if (txtEnable) log.debug "group already exists in local list..."
                    }
                }
            }
            break
        default :
            if (txtEnable) log.warn "skipped group command:${descMap}"
    }
}


private void processGlobalCommand(Map descMap) {
            switch (descMap.command) {
                case "01" : //read attribute response
                    readAttributeResponse(descMap)
                    break
                case "04" : //write attribute response
                    log.info "processGlobalCommand writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}"
                    break
                case "07" : // Configuration response
                    log.debug "processGlobalCommand Configuration response response descMap:${descMap}"
                    String status =  descMap.data[0]
                    switch (descMap.clusterId) {
                        case "0402" : // Temperature Configuration response
                            log.info "processGlobalCommand ${descMap.clusterId} (<b>Temperature Configuration</b> command response) clusterId: ${descMap.clusterId} status:${status}"
                            def text = "received Temperature Configuration command response status:${status}"
                            log.debug "${device.displayName} ${text}"
                            updateCurrentStates(" "," ",text)
                            stopDisplayCounter()
                            break
                        case "0405" : // Humidity Configuration response
                            log.info "processGlobalCommand ${descMap.clusterId} (<b>Humidity Configuration</b> command response) clusterId: ${descMap.clusterId} status:${status}"
                            def text = "received Humidity Configuration command response status:${status}"
                            log.debug "${device.displayName} ${text}"
                            updateCurrentStates(" "," ",text)
                            stopDisplayCounter()
                            break
                        default :
                            if (txtEnable) log.warn "skipped GlobalCommand Configuration response cluster: ${descMap.clusterId} : ${descMap}"
                    }
                
                case "0B" ://command response
                    String clusterCmd = descMap.data[0]
                    String status =  descMap.data[1]
                    switch (descMap.clusterId) {
                        case "0003" : // Identify response
                            log.info "processGlobalCommand ${descMap.clusterId} (<b>Identify</b> command response) clusterId: ${descMap.clusterId} status:${status}"
                            def text = "received Identify command response status:${status}"
                            log.debug "${device.displayName} ${text}"
                            updateCurrentStates(" "," ",text)
                            break
                        case "0300" :
                            log.info "processGlobalCommand ${descMap.clusterId} (command response) clusterId: ${descMap.clusterId} status:${status}"
                            break
                        case "0006" :
                            log.info "processGlobalCommand ${descMap.clusterId} (command response) clusterId: ${descMap.clusterId} clusterCmd: ${clusterCmd}"
                            break
                        case "0008" :
                            def cmd = clusterCmd=="01" ? "startLevelChange" : clusterCmd=="03" ? "stopLevelChange" : clusterCmd=="04" ? "move with on off" : clusterCmd=="00" ? "move" : "UNKNOWN"
                            log.info "processGlobalCommand ${descMap.clusterId} (command response) clusterId: ${descMap.clusterId} clusterCmd: ${clusterCmd} ${cmd}"
                            break
                        case "E001" :    // Tuya
                            log.info "processGlobalCommand ${descMap.clusterId} (command response) clusterId: ${descMap.clusterId} data:${descMap.data}"
                            break
                        default :
                            if (txtEnable) log.warn "skipped GlobalCommand response cluster: ${descMap.clusterId} : ${descMap}"
                    }
                    if (status == "82") {
                        if (logEnable) log.warn "unsupported general command cluster:${descMap.clusterId}, command:${clusterCmd}"
                    }
                    break
                default :
                    if (logEnable) log.warn "skipped global command cluster:${descMap.clusterId}, command:${descMap.command}, data:${descMap.data}"
            }

}



private void readAttributeResponse(Map descMap) {
    //log.debug "processGlobalCommand read attribute response descMap:${descMap}"
    def status = descMap.data[2]
    def hexValue = descMap.data[1] + descMap.data[0] 
    switch (descMap.clusterId) {
        case "0001" : // Power?
            // attributes 0x20 and 0x21
            if (status != "86") {
                log.warn "UNPROCESSED processGlobalCommand ${descMap.clusterId} (read attribute response) clusterId: ${descMap.clusterId} data:${descMap.data}"
            }
            else {
                log.info "Power/Battery is not supported!"
            }
            break    // TODO
        case "0402" : // temperature
            if (status != "86") {
                temperatureEvent(hexStrToSignedInt(descMap.value))
            }
            else {
                log.info "Temperature is not supported!"
            }
            break
        case "0403" : // pressure
            if (status != "86") {
                pressureEvent(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.info "Pressure is not supported!"
            }
            break
        case "0405" : // humidity
            if (status != "86") {
                humidityEvent(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.info "Humidity is not supported!"
            }
            break
        case "E001" : /// tuya specific
            log.warn "processGlobalCommand ${descMap.clusterId} (read attribute response) TUYA SPECIFIC clusterId: ${descMap.clusterId} data:${descMap.data}"
            break
        default :
            log.warn "processGlobalCommand ${descMap.clusterId} (read attribute response) UNKNOWN clusterId: ${descMap.clusterId} data:${descMap.data}"
            if (status == "86") {
                log.warn "Unsupported Attributte ${hexValue}"
            }
    } // clusterID 
}



// Events generated

def temperatureEvent(rawValue) {
	// rawValue represents the temperature in degrees Celsius as follows: 
	// Value = 100 x temperature in degrees Celsius. Where -273.15°C <= temperature <= 327.67 ºC, corresponding to a Value in the range 0x954d to 0x7fff. 
	// The maximum resolution this format allows is 0.01 ºC. 
	// A Value of 0x8000 indicates that the temperature measurement is invalid
	
	if (rawValue != 32768) {
		BigDecimal offset = temperatureOffset ? new BigDecimal(temperatureOffset).setScale(2, BigDecimal.ROUND_HALF_UP) : 0
		BigDecimal temp = new BigDecimal(rawValue).setScale(2, BigDecimal.ROUND_HALF_UP) / 100

		// Apply offset and convert to F if location scale set to F
		temp = (location.temperatureScale == "F") ? ((temp * 1.8) + 32) + offset : temp + offset
	
		sendEvent("name": "temperature", "value": temp, "unit": "\u00B0" + location.temperatureScale)
		log.info "${device.displayName} temperature changed to ${temp}\u00B0 ${location.temperatureScale} "
	} else {
		log.error "${device.displayName} temperature read failed"
	}
}

def humidityEvent(rawValue) {
	// Value represents the relative humidity in % as follows: 
	// Value = 100 x Relative humidity Where 0% <= Relative humidity <= 100%, corresponding to a value in the range 0 to 0x2710.
	// The maximum resolution this format allows is 0.01%.
	// A value of 0xffff indicates that the measurement is invalid.
	
	if (rawValue != 65535 && rawValue <= 10000) {
		BigDecimal offset = humidityOffset ? new BigDecimal(humidityOffset).setScale(2, BigDecimal.ROUND_HALF_UP) : 0
		BigDecimal humidity = new BigDecimal(rawValue).setScale(2, BigDecimal.ROUND_HALF_UP) / 100 + offset
		sendEvent("name": "humidity", "value": humidity, "unit": "%")
		log.info "${device.displayName} humidity changed to ${humidity}% "
	} else {
		log.error "${device.displayName} humidity read failed"
	}
}

def pressureEvent(rawValue) {
	// Value represents the pressure in kPa as follows: 
	// Value = 10 x Pressure where -3276.7 kPa <= Pressure <= 3276.7 kPa, corresponding to a value in the range 0x8001 to 0x7fff.
	// A Valueof 0x8000 indicates that the pressure measurement is invalid.
	if (rawValue != 32768) {
		Integer pressure = rawValue	// Divide by 10 for kPa or leave for hPa
		sendEvent("name": "pressure", "value": pressure, "unit": "hPa")        // pressure - NUMBER, unit: Pa || psi ???????????
		log.info "${device.displayName} pressure changed to ${pressure} hPa"
	} else {
		log.error "${device.displayName} pressure read failed"
	}
}

def batteryVoltageEvent(rawValue) {
	// The BatteryVoltage attribute is 8 bits in length and specifies the current actual (measured) battery voltage, in units of 100mV
	BigDecimal batteryVolts = new BigDecimal(rawValue).setScale(2, BigDecimal.ROUND_HALF_UP) / 10

	if (batteryVolts > 0){
		sendEvent("name": "voltage", "value": batteryVolts, "unit": "volts")
		log.info "${device.displayName} voltage changed to ${batteryVolts}V"
	
		if (getDataValue("calcBattery") == null || getDataValue("calcBattery") == "true") {
			updateDataValue("calcBattery", "true")	// We will calculate until a battery perc event occurs
			// Guess at percentage remaining
			// Battery percantage is not a linear relationship to voltage
			// Should try to do this as a table with more ranges
			def batteryValue = 100.0
			if (rawValue < 20.01) {
				batteryValue = 0.0
			} else if (rawValue < 24.01) {
				batteryValue = 10.0
			} else if (rawValue < 25.01) {
				batteryValue = 20.0
			} else if (rawValue < 26.01) {
				batteryValue = 30.0
			} else if (rawValue < 27.01) {
				batteryValue = 40.0
			} else if (rawValue < 27.51) {
				batteryValue = 50.0
			} else if (rawValue < 28.01) {
				batteryValue = 60.0
			} else if (rawValue < 28.51) {
				batteryValue = 70.0
			} else if (rawValue < 29.01) {
				batteryValue = 80.0
			} else if (rawValue < 29.51) {
				batteryValue = 90.0
			} else if (rawValue < 30.01) {
				batteryValue = 92.0
			} else if (rawValue < 30.51) {
				batteryValue = 95.0
			} else if (rawValue < 31.01) {
				batteryValue = 97.0
			} else if (rawValue < 31.51) {
				batteryValue = 99.0
			}
			sendEvent("name": "battery", "value": batteryValue, "unit": "%")
			log.info "${device.displayName} battery % remaining changed to ${batteryValue}% calculated from voltage ${batteryVolts}"
		}
	}

}

def batteryPercentageEvent(rawValue) {
	// The BatteryPercentageRemaining attribute specifies the remaining battery life as a half integer percentage of the full battery capacity
	// (e.g., 34.5%, 45%, 68.5%, 90%) with a range between zero and 100%, with 0x00 = 0%, 0x64 = 50%, and 0xC8 = 100%
	// A value of 0xff indicates that the measurement is invalid.
	if (rawValue != 255) {
		Float pct = rawValue / 2
		def batteryValue = Math.min(100, pct)
	
		sendEvent("name": "battery", "value": batteryValue, "unit": "%")
		log.info "${device.displayName} battery % remaining changed to ${batteryValue}% calculated from raw value ${rawValue}"
		updateDataValue("calcBattery", "false")	// Battery events are generated so no need to calc
	} else {
		log.error "${device.displayName} battery % remaining read failed"
	}
}



def configureTemperatureReporting (min, max, delta)
{
    int intDelta = (Float.parseFloat(delta)+0.005) * 100.0
    def tempConfig = min + ", " + max + ", " + intDelta.toString()
    state.tempConfig = tempConfig
    
    log.debug "min=${min} max=${max} delta=${delta} "
    log.debug "state.tempConfig = ${state.tempConfig} "
    updateCurrentStates("Temperature configuration stored (${state.tempConfig}) ", " ", "Press 'Send Configuration to device' button when everything is configured")
    sendEvent("name": "tmpConfig", "value": tempConfig )
}

def configureHumidityReporting(min, max, delta) {
    int intDelta = (Float.parseFloat(delta)+0.005) * 100.0
    def humConfig = min + ", " + max + ", " + intDelta.toString()
    state.humConfig = humConfig
    log.debug "state.humConfig = ${state.humConfig} "
    updateCurrentStates("Humidity configuration stored (${state.humConfig}) ", " ", "Press 'Send Configuration to device' button when everything is configured")
    sendEvent("name": "humConfig", "value": humConfig )
}



List<String> getResetToDefaultsCmds() {
    return    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	List<String> cmds = []

	cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 0xFFFF, null, [:], 200)    // Reset Battery Voltage reporting to default
	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 0xFFFF, null, [:], 200)	// Reset Battery % reporting to default
	cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 0, 0xFFFF, null, [:], 200)	// Reset Temperature reporting to default (looks to be 1/2 hr reporting)
	cmds += zigbee.configureReporting(0x0403, 0x0000, DataType.INT16, 0, 0xFFFF, null, [:], 200)	// Reset Pressure reporting to default (looks to be 1/2 hr reporting)
	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 0, 0xFFFF, null, [:], 200)   // Reset Humidity reporting to default (looks to be 1/2 hr reporting)

	return cmds
}



/*
private def parseBindingTableMessage(description) {
	Integer groupAddr = getGroupAddrFromBindingTable(description)
	if (groupAddr) {
		List cmds = addHubToGroup(groupAddr)
		cmds?.collect { new hubitat.device.HubAction(it) }
	}
}

private Integer getGroupAddrFromBindingTable(description) {
	//log.info "Parsing binding table - '$description'"
	def btr = zigbee.parseBindingTableResponse(description)
	def groupEntry = btr?.table_entries?.find { it.dstAddrMode == 1 }

	//log.info "Found ${groupEntry}"

	!groupEntry?.dstAddr ?: Integer.parseInt(groupEntry.dstAddr, 16)
}

private List addHubToGroup(Integer groupAddr) {
	["st cmd 0x0000 0x01 ${CLUSTER_GROUPS} 0x00 {${zigbee.swapEndianHex(zigbee.convertToHexString(groupAddr,4))} 00}", "delay 200"]
}

private List readDeviceBindingTable() {
	["zdo mgmt-bind 0x${device.deviceNetworkId} 0", "delay 200"]
}
*/



def test() {
    
    //log.debug "${param1.value"
    log.debug "${testNumber}"
    staticTestNumber = 4
    testNumber = 4
    log.debug "${testNumber}"
    
    //log.trace "${param1.name}"
    //log.trace "${param1.unit}"
    
    /*

    command "configureTemperatureReporting"


    def comment = "Zigbee Reporting Configuration"
    state.comment = comment + " " + version()

    List repConf
    repConf = zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)
   // repConf = zigbee.reportingConfiguration(0x0001, 0x0020, additionalParams, 250)
    log.trace "repConf = ${repConf}"

   // List reportingConfiguration(Integer clusterId, Integer attributeId, Map additionalParams=[:], int delay = STANDARD_DELAY_INT) {
    sendZigbeeCommands(repConf)
*/
}


def updateCurrentStates(_1=" ", _2=" ", _3= " ") {
    if (_1 != "") {sendEvent(name: "_1", value: _1, isStateChange: false)}
    if (_2 != "") {sendEvent(name: "_2", value: _2, isStateChange: false)}
    if (_3 != "") {sendEvent(name: "_3", value: _3, isStateChange: false)}       
}

void resetToDefaults() {
	clearStateVariables()
	//updateDataValue("calcBattery", "true")	// Calculate Battery Perc until an Battery Perc event is sent
	addToQueue("resetToDefaults")
}


void refreshAll() {
	addToQueue("refreshAll")
}

void clearStateVariables() {
	state.clear()
    state.powerSource = "unknown"
    state.temperatureSupported = false
    state.tempConfig = ""
    state.humiditySupported = false
    state.humConfig = ""
}


def _0_getDeviceInfo() {
    if (logEnable) {log.info "${device.displayName} getDeviceInfo() requested"}
	clearStateVariables()
	refreshAll()
	return getRefreshCmds()
}


def configure() {
	if (logEnable) log.debug "Configuring device ${device.getDataValue("model")} ..."
    initialize()
}


def installed() 
{
    if (logEnable) {log.debug "Zigbee Reporting Configuration installed()"}
  	initialize()
}

def initialize() {
    if (logEnable) {log.debug "Zigbee Reporting Configuration initialize()"}
    clearStateVariables()
    updateCurrentStates()
    displayCounter = timeoutCounter = 0
    if (repairAggressive==true) {
    	List<String> cmds = []

    	//List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)
    	cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure temperature - Report every minute, 1 second if any change
    	cmds += zigbee.configureReporting(0x0403, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure Pressure - Report every minute, 1 second if any change
    	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure Humidity - Report every minute, 1 second if any change
   		cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
   		cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected

	    sendZigbeeCommands(cmds)
        log.warn "Zigbee Reporting Configuration repair Aggressive (Report every minute, 1 second if any change) !!!"
    }
    else {
         log.info "Zigbee Reporting Configuration - no automatic configuration on re-pair..."
    }
}

def updated() 
{
    if (logEnable) {log.debug "Zigbee Reporting Configuration updated()"}
}

def startDisplayCounter() {
    displayCounter = 1
    runInMillis(1000, updateDisplayCounter)
}

def stopDisplayCounter() {
    displayCounter = 0
    timeoutCounter = 0
}

def updateDisplayCounter() {
    if (displayCounter != 0) {
        def sCounter = "Waiting... ${displayCounter}"
        sendEvent(name: "_2", value: sCounter)    
        displayCounter = displayCounter + 1
        runInMillis(1000, updateDisplayCounter)
    }
}


def startTimeoutCounter(int timeout) {
    timeoutCounter = timeout
    runInMillis(1000, updateTimeoutCounter)
}

def stopTimeoutCounter() {
    timeoutCounter = 0
}

def updateTimeoutCounter() {
    if (timeoutCounter > 0) {
        if (traceEnable==true) {log.trace "timeoutCounter = ${timeoutCounter}"}
        timeoutCounter = timeoutCounter - 1
        if (timeoutCounter > 0) {
            runInMillis(1000, updateTimeoutCounter)
        }
        else {
            if (traceEnable==true) {log.warn "TIMEOUT!"}
            updateCurrentStates("", " ", "TIMEOUT!")
            stopDisplayCounter()
        }
    }
}



def intTo16bitUnsignedHex(value) {
	def hexStr = zigbee.convertToHexString(value.toInteger(),4)
	return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
	return zigbee.convertToHexString(value.toInteger(), 2)
}

void addToQueue(String command) {
	queueMap.put(device.displayName, command)
    def text = "queued command " + queueMap.get(device.displayName)
    log.info "${device.displayName} ${text}"
    updateCurrentStates(text, " ", "please, wake up the device... ")
    startDisplayCounter()
}

String removeFromQueue() {
	String command = queueMap.get(device.displayName)
	if (command != null) {
		log.debug "${device.displayName} reading command " + command
		queueMap.put(device.displayName, null)
	}
	return command
}


private getIDENTIFY_CMD_IDENTIFY() { 0x00 }
private getIDENTIFY_CMD_QUERY() { 0x01 }
private getIDENTIFY_CMD_TRIGGER() { 0x40 }

List<String> getIdentifyCmds() {

	List<String> cmds = []
	// Identify for 60 seconds
	cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0003 ${IDENTIFY_CMD_IDENTIFY} { 0x${intTo16bitUnsignedHex(60)} }"
	// Trigger Effect
	//cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0003 ${IDENTIFY_CMD_TRIGGER} { 0x${intTo8bitUnsignedHex(EFFECT_BREATHE)} 0x${intTo8bitUnsignedHex(0)} }"

	return cmds;
}

List<String> getRefreshCmds() {
	List<String> cmds = []

	cmds += zigbee.readAttribute(0x0000, 0x0000)  // ZCLVersion;
	cmds += zigbee.readAttribute(0x0000, 0x0001)  // App Version
	//cmds += zigbee.readAttribute(0x0000, 0x0002)  // StackVersion
	cmds += zigbee.readAttribute(0x0000, 0x0003)  // HardwareVersion;
	cmds += zigbee.readAttribute(0x0000, 0x0004)  // Manufacturer Name
	cmds += zigbee.readAttribute(0x0000, 0x0005)  // Model ID
	cmds += zigbee.readAttribute(0x0000, 0x0006)  // Date Code
	cmds += zigbee.readAttribute(0x0000, 0x0007)  // PowerSource;
    
	cmds += zigbee.readAttribute(0x0001, 0x0020)  // Battery Voltage
	cmds += zigbee.readAttribute(0x0001, 0x0021)  // Battery % remaining
	cmds += zigbee.readAttribute(0x0402, 0x0000)  // Temperature
	cmds += zigbee.readAttribute(0x0403, 0x0000)  // Pressure
	cmds += zigbee.readAttribute(0x0405, 0x0000)  // Humidity
	
	return cmds
}

List<String> getConfigureCmds() {
	List<String> cmds = []

    int tMin, tMax, tDelta
    int hMin, hMax, hDelta
    
    if (state.tempConfig != null) {
        def ta = state.tempConfig.tokenize(" ,")
        def size = ta.size()
        tMin = Integer.parseInt(ta[0]); 
        tMax = Integer.parseInt(ta[1]); 
        tDelta = Integer.parseInt(ta[2]); 
        if (traceEnable==true) {log.trace "tMin = ${tMin};  tMax = ${tMax};  tDelta = ${tDelta}"}
        cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, tMin, tMax, tDelta, [:], 200)  // Configure temperature
        //List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)
    }
    else {
        log.warn "state.tempConfig is NULL, skipping temperature reporting configuration"
    }

    if (state.humConfig != null) {
        def ta = state.humConfig.tokenize(" ,")
        def size = ta.size()
        hMin = Integer.parseInt(ta[0]); 
        hMax = Integer.parseInt(ta[1]); 
        hDelta = Integer.parseInt(ta[2]); 
        if (traceEnable==true) {log.trace "hMin = ${hMin};  hMax = ${hMax};  hDelta = ${hDelta}"}
        cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.INT16, hMin, hMax, hDelta, [:], 200)  // Configure humidity
        //List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)
    }
    else {
        log.warn "state.humConfig is NULL, skipping humidity reporting configuration"
    }
	return cmds
}



void sendDelayedCmds() {
	String command = removeFromQueue()
	if (command != null) {
        def text = "sending delayed command ${command}"
		log.debug "${device.displayName} ${text}"
        updateCurrentStates(" ", text, " ")
		if (command == "resetToDefaults") {
			sendZigbeeCommands(getResetToDefaultsCmds())
		} else if (command == "refreshAll") {
			sendZigbeeCommands(getRefreshCmds(), 100)
		} else if (command == "reconfigure") {
			sendZigbeeCommands(getConfigureCmds())
		} else if (command == "identify") {
			sendZigbeeCommands(getIdentifyCmds(), 500)
		} else if (command == "readReporting") {
			sendZigbeeCommands(getReadReportingCmds(), 200)
		}
        stopDisplayCounter()    // ??????
	}
}


void logConfigureResponse(cluster, attribute, code) {
	if (code == "00") {
		log.info "${device.displayName} cluster ${cluster} successful configure reporting response for ${attribute}"
	} else if (code == "86") {
		log.error "${device.displayName} cluster ${cluster} UNSUPPORTED_ATTRIBUTE passed to configure ${attribute}"
	} else if (code == "8D") {
		log.error "${device.displayName} cluster ${cluster} INVALID_DATA_TYPE passed to configure ${attribute}"
	}
}

void deviceReported() {
	try {
		// synchronize this method
		mutex.acquire()
		sendDelayedCmds()
	} catch (InterruptedException e) {
		e.printStackTrace();
	} finally {
		mutex.release()
	}
}


void sendZigbeeCommands(List<String> cmds) {
	log.debug "${device.displayName} sendZigbeeCommands received : ${cmds}"
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

void sendZigbeeCommands(List<String> cmds, Long delay) {
	sendZigbeeCommands(delayBetween(cmds, delay))
}

/* The Identify cluster allows the host device to be put into identification mode in which the node highlights itself in some way to an observer (in order to distinguish itself from other nodes in the network). 
It is recommended that identification mode should involve flashing a light with a period of 0.5 seconds.
*/
void identify() {
	// Try sending immediately then queue it
	sendZigbeeCommands(getIdentifyCmds(), 500)
	addToQueue("identify")
	//log.info "${device.displayName} queued command identify"
}

void sendConfigurationToDeviceWhenAwake() {
    addToQueue("reconfigure")
}

void sendConfigurationToDeviceNow() {
    def text = "trying to send reconfigure command NOW"
    log.debug "${device.displayName} ${text}"
    updateCurrentStates(text, " ",  " ")    
    sendZigbeeCommands(getConfigureCmds())
    startDisplayCounter()
    startTimeoutCounter(30)
}


List<String> getReadReportingCmds() {
	List<String> cmds = []
    
    cmds += zigbee.reportingConfiguration(0x0402, 0x0000, [:], 250)    // Temperature
    cmds += zigbee.reportingConfiguration(0x0403, 0x0000, [:], 250)    // Pressure
    cmds += zigbee.reportingConfiguration(0x0405, 0x0000, [:], 250)    // Humidity
    cmds += zigbee.reportingConfiguration(0x0001, 0x0020, [:], 250)    // Battery Voltage
    cmds += zigbee.reportingConfiguration(0x0001, 0x0021, [:], 250)    // Battery % remaining
    
	return cmds
}


void __readReportingConfiguration() {
    addToQueue("readReporting")
}

void readAttribute( attribute ) {
}
