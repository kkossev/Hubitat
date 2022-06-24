/**
 *  Aqara P1 Motion Sensor driver for Hubitat
 *
 *  https://community.hubitat.com/t/aqara-p1-motion-sensor/92987/46?u=kkossev
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
 * ver. 1.0.0 2022-06-19 kkossev  - project started
 *
*/

def version() { "1.0.0" }
def timeStamp() {"2022/06/24 8:26 PM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

@Field static final Boolean debug = true

metadata {
    definition (name: "Aqara P1 Motion Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20P1%20Motion%20Sensor/Aqara%20P1%20Motion%20Sensor.groovy", singleThreaded: true ) {
		capability "Motion Sensor"
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Battery"
        capability "PowerSource"
        
        attribute "batteryVoltage", "string"

        command "configure", [[name: "Initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
        
        if (debug) {
            command "setMotion", [[name: "Force motion active/inactive (when testing automations)", type: "ENUM", constraints: ["--- Select ---", "active", "inactive"], description: "Force motion active/inactive (for tests)"]]
            command "test", [[name: "Cluster", type: "STRING", description: "Zigbee Cluster (Hex)", defaultValue : "0001"]]
            command "refresh", [[name: "*** Press the motion sensor button at the same time! ***" ]]
            command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
            command "configLED", [[name:"cononfigLED", type: "ENUM", description: "Configure LED mode", constraints: ["--- Select ---", "Disabled", "Enabled"]]]
            command "configSensitivity", [[name:"cononfigSensitivity", type: "ENUM", description: "Configure PIR Sensitivity", constraints: ["--- Select ---", "Low", "Medium","High"]]]
            command "configDuration", [[name:"cononfigDuration", type: "NUMBER", description: "Configure PIR Duration"]]
        }
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0", outClusters:"0003,0019,FCC0", model:"lumi.motion.ac02", manufacturer:"LUMI", deviceJoinName: "Aqara P1 Motion Sensor RTCGQ14LM"         // "Aqara P1 presence sensor RTCGQ14LM" {manufacturerCode: 0x115f}
        if (debug) {
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,FFFF,0406,0400,0500,0001,0003", outClusters:"0000,0019", model:"lumi.sensor_motion.aq2", manufacturer:"LUMI" 
        }
    }

    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "Debug information, useful for troubleshooting. Recommended value is <b>false</b>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "Show motion activity in HE log page. Recommended value is <b>true</b>", defaultValue: true)
        input (name: "motionResetTimer", type: "number", title: "<b>Motion Reset Timer</b>", description: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 30 seconds", range: "1..7200", defaultValue: 30)
        input (name: "motionRetriggerInterval", type: "number", title: "<b>Motion Retrigger Interval</b>", description: "Motion Retrigger Interval, seconds (1..200)", range: "1..202", defaultValue: 30)
        input (name: "motionSensitivity", type: "enum", title: "<b>Motion Sensitivity</b>", description: "Sensor motion sensitivity", defaultValue: 0, options: [1:"Low", 2:"Medium", 3:"High" ])
        input (name: "motionLED",  type: "enum", title: "<b>Enable/Disable LED</b>",  description: "Enable/disable LED blinking on motion detection", defaultValue: -1, options: [0:"Disabled", 1:"Enabled" ])
    }
}

private P1_LED_MODE_VALUE(mode) { mode == "Disabled" ? 0 : mode == "Enabled" ? 1 : null }
private P1_LED_MODE_NAME(value) { value == 0 ? "Disabled" : value== 1 ? "Enabled" : null }


def parse(String description) {
    if (logEnable == true) log.debug "${device.displayName} parse: description is $description"
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()

    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
            log.warn "${device.displayName} parse: exception caught while parsing descMap:  ${descMap}"
            return null
    }
    if (logEnable) {log.debug "${device.displayName} parse: Desc Map: $descMap"}
    if (descMap.attrId != null ) {
        // attribute report received
        List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
        descMap.additionalAttrs.each {
            attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
            //log.trace "parse: filling in attrData: ${attrData}"
        }
        attrData.each {
            //log.trace "parse: attrData.each it=${it}"
            if (it.status == "86") {
                log.warn "unsupported cluster ${it.cluster} attribute ${it.attrId}"
            }
		    else if (it.cluster == "0400" && it.attrId == "0000") {
                // lumi.sensor_motion.aq2 parse: Desc Map: [raw:F5DE0104000A0000212200, dni:F5DE, endpoint:01, cluster:0400, size:0A, attrId:0000, encoding:21, command:0A, value:0022, clusterInt:1024, attrInt:0]
                def rawLux = Integer.parseInt(it.value,16)
                illuminanceEventLux( rawLux )

		    }                 
            else if (it.cluster == "0406" && it.attrId == "0000") {
                // lumi.sensor_motion.aq2 parse: Desc Map: [raw:F5DE0104060800001801, dni:F5DE, endpoint:01, cluster:0406, size:08, attrId:0000, encoding:18, command:0A, value:01, clusterInt:1030, attrInt:0]
                map = handleMotion( Integer.parseInt(it.value,16) as Boolean )
            }
            
            else if (it.cluster == "0000" && it.attrId == "0005") {    // value: value:lumi.sensor_motion.aq2 - sent when button is pressed
                //  attribute report: cluster=0000 attrId=0005 value=lumi.sensor_motion.aq2 status=null data=nul
                if (txtEnable) log.info "${device.displayName} device ${it.value} button was pressed "
            }

            else if (descMap.cluster == "FCC0") {    // Aqara P1
                //  attribute report: cluster=FCC0 attrId=0112 value=00010042 status=null data=null
                parseAqaraClusterFCC0( description, descMap, it )
            }
            else if (descMap.cluster == "0000" && it.attrId == "FF01") {
                // lumi.sensor_motion.aq2 parse: description is read attr - raw: F5DE0100004A01FF42210121F90B0328230421A81305211B00062401000000000A2188326410000B216402, dni: F5DE, endpoint: 01, cluster: 0000, size: 4A, attrId: FF01, encoding: 42, command: 0A, value: 210121F90B0328230421A81305211B00062401000000000A2188326410000B216402
                // lumi.sensor_motion.aq2 parse: Desc Map: [raw:F5DE0100004A01FF42210121F90B0328230421A81305211B00062401000000000A2188326410000B216402, dni:F5DE, endpoint:01, cluster:0000, size:4A, attrId:FF01, encoding:42, command:0A, value:!ù(#!¨!$!2d!d, clusterInt:0, attrInt:65281]
                // lumi.sensor_motion.aq2 attribute report: cluster=0000 attrId=FF01 value=!ù(#!¨!$
                parseAqaraAttributeFF01( description )
            }
            else {
                if (logEnable) log.warn "${device.displayName} Unprocessed attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
            }
                //if (logEnable) {log.debug "${device.displayName} Parse returned $map"}
        } // for each attribute
    } // if attribute report
    else if (descMap.profileId == "0000") { //zdo
        parseZDOcommand(descMap)
    } 
    else if (descMap.clusterId != null && descMap.profileId == "0104") { // ZHA global command
        parseZHAcommand(descMap)
    } 
    else {
        if (logEnable==true)  log.warn "${device.displayName} Unprocesed unknown command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }

}

def parseAqaraAttributeFF01 ( description ) {
    Map result = [:]
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    result = parseBatteryFF01( valueHex )    
    sendEvent( result )
}
                     
                     
def parseAqaraClusterFCC0 ( description, descMap, it  ) {
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    switch (it.attrId) {
        case "0005" :
            if (logEnable) log.info "${device.displayName} device ${it.value} button was pressed"
            break
        case "00F7" :
            // Battery : [6..7] LSB [8..9] MSB
            def rawVolts = Integer.parseInt((valueHex[8..9] + valueHex[6..7]),16) / 1000
            voltageAndBatteryEvents( rawVolts )
            // device temperature [14..15]
            def value = Integer.parseInt(valueHex[14..15],16)
            if (txtEnable) log.info "${device.displayName} temperature is ${value} deg.C"
            // Illuminance MSB-[78..79] LSB-[76..77]
            value = Integer.parseInt((valueHex[78..79] + valueHex[76..77]),16)
            illuminanceEventLux( value )
            // Motion retrigger interval [84..85]
            value = Integer.parseInt(valueHex[84..85],16)
            if (txtEnable) log.info "${device.displayName} retrigger interval is ${value} s."
            // Sensitivity
            value = Integer.parseInt(valueHex[90..91],16)
            if (txtEnable) log.info "${device.displayName} sensitivity is ${value}"
            // LED 
            value = Integer.parseInt(valueHex[96..97],16)
            //device.updateSetting("illuminanceSensitivity", [value:raw, type:"decimal"])
            device.updateSetting( "motionLED",  [value:value.toString(), type:"enum"] )
            if (txtEnable) log.info "${device.displayName} LED is ${P1_LED_MODE_NAME(value)} (${value})"
            break
        case "0112" : // Aqara P1 PIR motion Illuminance
            def rawValue = Integer.parseInt((valueHex[(2)..(3)] + valueHex[(0)..(1)]),16)
            illuminanceEventLux( rawValue )
            handleMotion( true )
            break
        case "0152" : // LED configuration
            def value = safeToInt(it.value)
            device.updateSetting( "motionLED",  [value:value.toString(), type:"enum"] )
            if (txtEnable) log.info "${device.displayName} <b>received LED configuration report: ${P1_LED_MODE_NAME(value)}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"    //P1_LED_MODE_VALUE
            break        
        case "010C" : // PIR sensitivity
            def value = safeToInt(it.value)
            if (txtEnable) log.info "${device.displayName} <b>received PIR sensitivity report: ${value==1?'Low':value==2?'Medium':value==3?'High':null}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0102" : // Duration
            def value = safeToInt(it.value)
            if (txtEnable) log.info "${device.displayName} <b>received PIR duration report: ${value} s</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        default :
            if (logEnable) log.warn "${device.displayName} Unprocessed <b>FCC0</b> attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
        break
    }
    
}
                     

// Convert raw 4 digit integer voltage value into percentage based on minVolts/maxVolts range
private parseBatteryFF01( valueHex ) {
	//if (logEnable) log.trace "${device.displayName} Battery parse string = ${valueHex}"
	def MsgLength = valueHex.size()
	//def rawValue = Integer.parseInt((valueHex[(2)..(3)] + valueHex[(4)..(5)]),16)
    	for (int i = 0; i < (MsgLength-3); i+=2) {
		if (valueHex[i..(i+1)] == "21") { // Search for byte preceeding battery voltage bytes
			rawValue = Integer.parseInt((valueHex[(i+2)..(i+3)] + valueHex[(i+4)..(i+5)]),16)
			break
		}
	}
    //log.warn "rawValue = ${rawValue}"
    if (rawValue == 0) {
        return
    }
	def rawVolts = rawValue / 100
	def minVolts = 2.5
	def maxVolts = 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
    if (txtEnable) log.info "${device.displayName} ${descText}"
	def result = [
		name: 'battery',
		value: roundedPct,
		unit: "%",
		isStateChange: true,
		descriptionText: descText
	]
	return result
}

def voltageAndBatteryEvents( rawVolts )
{
	def minVolts = 2.5
	def maxVolts = 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'batteryVoltage', value: rawVolts, unit: "V", isStateChange: true )
    sendEvent(name: 'battery', value: roundedPct, unit: "%", isStateChange: true )
}

def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : // device announcement
            if (logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
        case "8004" : // simple descriptor response
            if (logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            parseSimpleDescriptorResponse( descMap )
            break
        case "8005" : // endpoint response
            if (logEnable) log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}"
            break
        case "8021" : // bind response
            if (logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8038" : // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}"
            break
        default :
            if (logEnable) log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

def parseZHAcommand( Map descMap) {
    switch (descMap.command) {
        case "01" : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0] 
            if (status == "86") {
                if (logEnable==true) log.warn "${device.displayName} <b>UNSUPPORTED/b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
            }
            else {
                switch (descMap.clusterId) {
                    // "lumi.sensor_motion.aq2" inClusters: "0000,FFFF,0406,0400,0500,0001,0003"
                    case "0000" :
                    case "0001" :
                    case "0003" :
                    case "0400" :
                    case "0500" :
                    case "FFFF" :
                        if (logEnable==true) log.warn "${device.displayName} <b>NOT PROCESSED</b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
                        break                    
                    default :
                        if (logEnable==true) log.warn "${device.displayName} <b>UNHANDLED</b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
                        break
                } // switch (descMap.clusterId)
            }  //command is read attribute response
            break
        case "04" : //write attribute response
            if (logEnable==true) log.info "${device.displayName} Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "07" : // Configure Reporting Response
            if (txtEnable==true) log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "09" : // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7])
                if (logEnable==true) log.info "${device.displayName} Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max}"
            }
            else {
                if (logEnable==true) log.info "${device.displayName} <b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            }
            break
        case "0B" : // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                switch (descMap.clusterId) {
                    /// "lumi.sensor_motion.aq2" inClusters: "0000,FFFF,0406,0400,0500,0001,0003"
                    case "0000" :
                    case "0001" :
                    case "0003" :
                    case "0400" :
                    case "0500" :
                    case "FFFF" :
                    default :
                        if (logEnable==true) log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
                        break
                }
            }
            break
        default :
            if (logEnable==true) log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}



def illuminanceEvent( illuminance ) {
    //def rawLux = Integer.parseInt(descMap.value,16)
	def lux = illuminance > 0 ? Math.round(Math.pow(10,(illuminance/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

def illuminanceEventLux( Integer lux ) {
    if ( lux > 0xFFDC ) lux = 0    // maximum value is 0xFFDC !
    sendEvent("name": "illuminance", "value": lux, "unit": "lx")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

private handleMotion( Boolean motionActive ) {    
    if (motionActive) {
        def timeout = settings?.motionResetTimer ?: 30
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in the code
        if (timeout != 0) {
            runIn(timeout, resetToMotionInactive, [overwrite: true])
        }
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = now()
        }
    }
    else {
        if (device.currentState('motion')?.value == "inactive") {
            if (logEnable) log.debug "${device.displayName} ignored motion inactive event after ${getSecondsInactive()} s."
            return [:]   // do not process a second motion inactive event!
        }
    }
	return getMotionResult(motionActive)
}

def getMotionResult( Boolean motionActive ) {
	def descriptionText = "Detected motion"
    if (!motionActive) {
		descriptionText = "Motion reset to inactive after ${getSecondsInactive()} s."
    }
    else {
        descriptionText = device.currentValue("motion") == "active" ? "Motion is active ${getSecondsInactive()}s" : "Detected motion"
    }
    if (txtEnable) log.info "${device.displayName} ${descriptionText}"
	sendEvent (
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
			descriptionText : descriptionText
	)
}

def resetToMotionInactive() {
	if (device.currentState('motion')?.value == "active") {
		def descText = "Motion reset to inactive after ${getSecondsInactive()} s."
		sendEvent(
			name : "motion",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
        if (txtEnable) log.info "${device.displayName} ${descText}"
	}
    else {
        if (txtEnable) log.debug "${device.displayName} ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()} s."
    }
}

def getSecondsInactive() {
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
    } else {
        return motionResetTimer ?: 30
    }
}


// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    sendEvent(name : "powerSource",	value : "battery", isStateChange : false)
}


def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
        //log.trace "${device.displayName} driverVersion is the same ${driverVersionAndTimeStamp()}"
    }
    else {
        if (txtEnable==true) log.info "${device.displayName} updating the settings from driver version ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
        state.motionStarted = now()
    }
}

def logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


// called when preferences are saved
def updated() {
    checkDriverVersion()
    ArrayList<String> cmds = []
    
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getName()} model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    def value
    // LED
    if (settings?.motionLED != null ) {
        value = safeToInt( motionLED )
        if (settings?.logEnable) log.trace "${device.displayName} changing motionLED to ${motionLED}"
        cmds += zigbee.writeAttribute(0xFCC0, 0x0152, 0x20, value, [mfgCode: 0x115F], delay=200)
    }
    if ( cmds != null ) {
        sendZigbeeCommands( cmds )     
    }
}    

void initializeVars( boolean fullInit = false ) {
    if (logEnable==true) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || state.rxCounter == null) state.rxCounter = 0
    if (fullInit == true || state.txCounter == null) state.txCounter = 0
    
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings.motionResetTimer == null) device.updateSetting("motionResetTimer", 30)    
}

def installed() {
    log.info "${device.displayName} installed()"
}

def configure(boolean fullInit = true ) {
    log.info "${device.displayName} configure()..."
    unschedule()
    initializeVars( fullInit )
    updated()    
}
def initialize() {
    log.info "${device.displayName} Initialize()..."
    configure(fullInit = true)
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) {log.trace "${device.displayName} sending ZigbeeCommands : ${cmds}"}
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
}


def setMotion( mode ) {
    switch (mode) {
        case "active" : 
            handleMotion(true)
            break
        case "inactive" :
            handleMotion(false)
            break
        default :
            if (settings?.logEnable) log.warn "${device.displayName} please select motion action)"
            break
    }
}


def configLED( mode ) {
    ArrayList<String> cmds = []
 
    def value = P1_LED_MODE_VALUE(mode)        /*mode == "disabled" ? 0 : mode == "enabled" ? 1 : null*/
    if (value != null) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x0152, 0x20, value.toInteger(), [mfgCode: 0x115F], delay=200)
        if (settings?.txtEnable) log.info "${device.displayName} sending LED mode : ${mode}" 
        cmds += zigbee.readAttribute(0xFCC0, 0x0152, [mfgCode: 0x115F], delay=200)    // read LED config back!
        sendZigbeeCommands( cmds )    
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} please select a LED mode"
    }
}


def configSensitivity( mode ) {
    ArrayList<String> cmds = []
    def value = mode == "Low" ? 1 : mode == "Medium" ? 2 : mode == "High" ? 3 : null
    if (value != null) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x010C, 0x20, value.toInteger(), [mfgCode: 0x115F], delay=200)
        if (settings?.txtEnable) log.info "${device.displayName} sending PIR sensitivity : ${mode}" 
        cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: 0x115F], delay=200)    // read sensitivity config back!
        sendZigbeeCommands( cmds )    
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} please select a sensitivity mode"
    }
}


def configDuration( duration ) {
    ArrayList<String> cmds = []
    def value = safeToInt( duration )
    if (value != 0) {
        cmds += zigbee.writeAttribute(0xFCC0, 0x0102, 0x20, value.toInteger(), [mfgCode: 0x115F], delay=200)
        if (settings?.txtEnable) log.info "${device.displayName} sending duration : ${value}" 
        cmds += zigbee.readAttribute(0xFCC0, 0x0102, [mfgCode: 0x115F], delay=200)    // read duration config back!
        sendZigbeeCommands( cmds )    
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} please select a duration"
    }
}

def refresh() {
    if (txtEnable) log.info "${device.displayName} shortly press the motion sensor button at the same time!"
    ArrayList<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, 0x0152, [mfgCode: 0x115F], delay=200)    // read LED config back!
    cmds += zigbee.readAttribute(0xFCC0, 0x0102, [mfgCode: 0x115F], delay=200)    // read duration config back!
    cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: 0x115F], delay=200)    // read sensitivity config back!
    sendZigbeeCommands( cmds )    
}

def test( description ) {
    /*
    log.warn "test $description"
    parse(description)
*/
	List<String> cmds = []
/*
	cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 0xFFFF, null, [:], 200)	// Reset Battery Voltage reporting to default
	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 0xFFFF, null, [:], 200)	// Reset Battery % reporting to default
*/
   // cmds += zigbee.batteryConfig()
    cmds += zigbee.readAttribute(0xFCC0, 0x00F7, [:] /*[mfgCode: 0x115F]*/, delay=200)  
        sendZigbeeCommands( cmds )  
}


