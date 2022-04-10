/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
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
 * ver. 1.0.0 2022-04-10 kkossev  - Inital test version
 *
*/

def version() { "1.0.0" }
def timeStamp() {"2022/04/10 6:02 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus
 
metadata {
    definition (name: "Tuya Multi Sensor 4 In 1", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://github.com/kkossev/Hubitat/blob/main/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "Battery"
        capability "MotionSensor"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        
        
        command "configure", [[name: "Manually initialize the sensor after switching drivers" ]]
        //command "test"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00",      outClusters:"0019,000A", model:"TS0202", manufacturer:"_TZ3210_zmy9hjay", deviceJoinName: "Tuya Multi Sensor 4 In 1"          //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya Multi Sensor 3 In 1"          // KK
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
    }
}

private getCLUSTER_TUYA()       { 0xEF00 }
private getSETDATA()            { 0x00 }
private getSETTIME()            { 0x24 }

// Tuya Commands
private getTUYA_REQUEST()       { 0x00 }
private getTUYA_REPORTING()     { 0x01 }
private getTUYA_QUERY()         { 0x02 }
private getTUYA_STATUS_SEARCH() { 0x06 }
private getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits

// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    /*if (settings?.logEnable)*/ log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.warn "unparesed attrint $descMap.attrInt"
            }
        }     
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        } 
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            if (settings?.logEnable) log.info "${device.displayName} device announcement"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.info "${device.displayName} application version is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFDF") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE2") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya AppVersion is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE4") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya UNKNOWN attribute FFE4 value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFFE") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya UNKNOWN attribute FFFE value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0500" && descMap?.command in ["01", "0A"] ) {    //read attribute response
            if (settings?.logEnable) log.info "${device.displayName} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
        } 
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response
            if (settings?.logEnable) log.info "${device.displayName} IAS enroll write attribute response is ${descMap?.data[0] == "00" ? "success" : "failure"}"
        } 
        else {
            if (settings?.logEnable) log.debug "${device.displayName} <b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {	
        if (settings?.logEnable) log.debug "Zone status: $description"
        parseIasMessage(description)
    }
    else if (description?.startsWith('enroll request')) {
         /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        if (settings?.logEnable) log.debug "enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else {
        if (settings?.logEnable) log.debug "${device.displayName} <b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
}


def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch(e) {
            if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        if (settings?.logEnable) log.trace "${device.displayName} now is: ${now()}"  // KK TODO - convert to Date/Time string!        
        if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"))
    {
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case 0x65 : //  Tuya 3 in 1 (101) -> motion
                log.warn "motion event 0x65 fncmd = ${fncmd}"
                sendEvent(getMotionResult(motionActive=fncmd))
                break            
            case 0x67 : //  Tuya 3 in 1 (103) -> tamper
                if (settings?.txtEnable) log.info "${device.displayName} tamper alarm is ${fncmd==0 ? 'inactive' : 'active'}"
                break            
            case 0x66 : // battery
            case 0x6E : // Tuya 4 in 1
                if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                def rawValue = 0
                if (fncmd == 0) rawValue = 100           // Battery Full
                else if (fncmd == 1) rawValue = 75       // Battery High
                else if (fncmd == 2) rawValue = 50       // Battery Medium
                else if (fncmd == 3) rawValue = 25       // Battery Low
                else if (fncmd == 4) rawValue = 100      // Tuya 3 in 1 -> USB powered !
                else {
                    rawValue = fncmd
                }
                getBatteryPercentageResult(rawValue*2)
                break 
            case 0x68 : //  Tuya 3 in 1 (104) -> temperature in °C
                temperatureEvent( fncmd / 10.0 )
                break            
            case 0x69 : //  Tuya 3 in 1 (105) -> humidity in %
                humidityEvent (fncmd)
                break
            case 0x6B : //  Tuya 4 in 1 (107) -> temperature in °C
                temperatureEvent( fncmd / 10.0 )
                break            
            case 0x6C : //  Tuya 4 in 1 (108) -> humidity in %
                humidityEvent (fncmd)
                break
            default :
                /*if (settings?.logEnable)*/ log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
    }
}


private int getTuyaAttributeValue(ArrayList _data) {
    int retValue = 0
    
    if (_data.size() >= 6) {
        int dataLength = _data[5] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[i+5])
            power = power * 256
        }
    }
    return retValue
}


def parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn 
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        if (settings?.logEnable) log.trace "zs = $zs"
        if (zs.alarm1Set == true) {
            handleMotion(motionActive=true)
        }
        else {
            log.warn "no motion active?"
        }
    }
    catch (e) {
        log.error "This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

private handleMotion(motionActive) {    
    if (motionActive) {
        def timeout = motionReset ?: 3
        // If the sensor only sends a motion detected message. the reset to motion inactive must be  performed in code
        runIn(timeout, resetToMotionInactive)        
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = now()
        }
    }
	return getMotionResult(motionActive)
}

def getMotionResult(motionActive) {
	def descriptionText = "Detected motion"
    if (!motionActive) {
		descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    log.info " ${descriptionText}"
	return [
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
			descriptionText : descriptionText
	]
}

def resetToMotionInactive() {
	if (device.currentState('motion')?.value == "active") {
		def descText = "Motion reset to inactive after ${getSecondsInactive()}s"
		sendEvent(
			name : "motion",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
        log.info " ${descText}"
	}
}

def getSecondsInactive() {
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
    } else {
        return motionReset ?: 3
    }
}



def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    map.unit = "\u00B0"+"C"
    map.value  =  Math.round((temperature - 0.05) * 10) / 10
    map.isStateChange = true
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.name} is ${map.value} ${map.unit}"}
    sendEvent(map)
}

def humidityEvent( humidity ) {
    def map = [:] 
    map.name = "humidity"
    map.value = humidity as int
    map.unit = "% RH"
    map.isStateChange = true
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.name} is ${Math.round((humidity) * 10) / 10} ${map.unit}"}
    sendEvent(map)
}

// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()"
    unschedule()
}

// called when preferences are saved
// runs when save is clicked in the preferences section
def updated() {
    //ArrayList<String> cmds = []
    
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    /*
    cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
   	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected    
    if (settings?.txtEnable) log.info "${device.displayName} Update finished"
    sendZigbeeCommands( cmds )  
  */
}


def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    zigbee.readAttribute(0, 1)
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
    }
    else {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def logInitializeRezults() {
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars(boolean fullInit = true ) {
    if (settings?.txtEnable) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0

    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)
    return  cmds
}

// called when used with capability "Configuration" is called when the configure button is pressed on the device page. 
// Runs when driver is installed, after installed() is run. if capability Configuration exists, a Configure command is added to the ui
// It is also called on initial install after discovery.
def configure() {
    if (settings?.txtEnable) log.info "${device.displayName} configure().."
    List<String> cmds = []
    cmds += tuyaBlackMagic()    
    sendZigbeeCommands(cmds)    
}

// called when used with capability "Initialize" it will call this method every time the hub boots up. So for things that need refreshing or re-connecting (LAN integrations come to mind here) ..
// runs first time driver loads, ie system startup 
// when capability Initialize exists, a Initialize command is added to the ui.
def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    installed()
    updated()
    configure()
    runIn( 3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}

def logsOff(){
    log.warn "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def getBatteryPercentageResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery is ${result.value}%"
        result.isStateChange = true
        result.unit  = '%'
        sendEvent(result)
        if (settings?.txtEnable) log.info "${result.descriptionText}"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

private Map getBatteryResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} batteryVoltage = ${(double)rawValue / 10.0} V"
    def result = [:]
    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0)
        roundedPct = 1
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${device.displayName} battery is ${result.value}% (${volts} V)"
        result.name = 'battery'
        result.unit  = '%'
        result.isStateChange = true
        if (settings?.txtEnable) log.info "${result.descriptionText}"
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }    
}

