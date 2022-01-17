/**
 *  Tuya Temperature Humidity Illuminance LCD Display with a Clock
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
 * ver. 1.0.0 2022-01-16 kkossev  - Inital version
 *
*/

def version() { "1.0.0" }
def timeStamp() {"2022/01/17 11:18 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition (name: "Tuya Temperature Humidity Illuminance LCD Display with a Clock", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock.groovy", singleThreaded: true ) {
        capability "Refresh"
        capability "Sensor"
        capability "Initialize"
        capability "Battery"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"

       
        command "zTest", [
            [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
            [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
            [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
        ]
             
        command "initialize"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lve3dvpy",  deviceJoinName: "Tuya Temperature Humidity Illuminance LCD Display with a Clock" 
        
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "modelGroupPreference", title: "Select a model group", description: "<i>Recommended value is <b>'Auto detect'</b></i>", type: "enum", options:["Auto detect", "TS0601", "TS0201", "TS0222", "TEST"], defaultValue: "Auto detect", required: false)        
    }
}

@Field static final Map<String, String> Models = [
    '_TZE200_lve3dvpy'  : 'TS0601',     
    '_TZ2000_a476raq2'  : 'TS0201',     
    '_TYZB01_kvwjujy9'  : 'TS0222',     
    ''                  : 'UNKNOWN'      // 
]

@Field static final Integer MaxRetries = 3
                                
// KK TODO !
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
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else {
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
        }     
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            handleIlluminanceEvent( descMap )
		}        
		else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            handleTemperatureEvent( descMap )
		}
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            handleHumidityEvent( descMap )
		}
        else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
            if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
            def offset = 0
            try {
                offset = location.getTimeZone().getOffset(new Date().getTime())
                //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
            } catch(e) {
                log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
            }
            def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
            log.trace "${device.displayName} now is: ${now()}"  // KK TODO - converto to Date/Time string!        
            if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
            cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
            state.old_dp = ""
            state.old_fncmd = ""
            
        } else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
            String clusterCmd = descMap?.data[0]
            def status = descMap?.data[1]            
            if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
            state.old_dp = ""
            state.old_fncmd = ""
            if (status != "00") {
                if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
            }
            
        } else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02")) {
            //if (descMap?.command == "02") { if (settings?.logEnable) log.warn "command == 02 !"  }   
            def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
            def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
            def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
            if (dp == state.old_dp && fncmd == state.old_fncmd) {
                /*if (settings?.logEnable)*/ log.warn "(duplicate) transid=${transid} dp_id=${dp_id} <b>dp=${dp}</b> fncmd=${fncmd} command=${descMap?.command} data = ${descMap?.data}"
                if ( state.duplicateCounter != null ) state.duplicateCounter = state.duplicateCounter +1
                return
            }
            if (settings?.logEnable) log.trace " dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
            state.old_dp = dp
            state.old_fncmd = fncmd
            // the switch cases below default to dp_id = "01"
            // TUYA / HUMIDITY/ILLUMINANCE/TEMPERATURE SENSOR
            //thitBatteryPercentage: 3,
            //thitIlluminanceLux: 7,
            //thitHumidity: 9,
            //thitTemperature: 8,            
            switch (dp) {
                case 0x01 : // temperature
                    temperatureEvent( fncmd / 10.0 )
                    break
                case 0x02 : // humidity
                    humidityEvent (fncmd)
                    break 
                case 0x03 : // illuminance - NOT TESTED! or battery?
                    illuminanceEvent (fncmd)
                    break 
                case 0x04 : // battery
                    getBatteryPercentageResult(fncmd * 2)
                    log.warn "battery is $fncmd %"
                    break
                case 0x13 : // temperature sensitivity
                    log.warn "temperature sensitivity is $fncmd "
                    break                
                default :
                    /*if (settings?.logEnable)*/ log.warn "${device.displayName} NOT PROCESSED Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                    break
            }
        } else {
            /*if (settings?.logEnable)*/ log.warn "<b>not parsed</b>: "+descMap
        }
    }
    else {
        /*if (settings?.logEnable)*/ log.debug "${device.displayName} <b>UNPROCESSED</b> parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
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

def getModelGroup() {
    def manufacturer = device.getDataValue("manufacturer")
    def modelGroup = 'Unknown'
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    if (modelGroupPreference == "Auto detect") {
        if (manufacturer in Models) {
            modelGroup = Models[manufacturer]
        }
        else {
             modelGroup = 'Unknown'
        }
    }
    else {
         modelGroup = modelGroupPreference 
    }
    //    log.trace "manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}

def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    map.value = temperature
    map.unit = "C"    // TODO!
    if (txtEnable) {log.info "${device.displayName} ${map.name} is ${map.value} ${map.unit}"}
    sendEvent(map)
}

def humidityEvent( humidity ) {
    def map = [:] 
    map.name = "humidity"
    map.value = humidity
    map.unit = "%"    // TODO!
    if (txtEnable) {log.info "${device.displayName} ${map.name} is ${map.value} ${map.unit}"}
    sendEvent(map)
}

def switchEvent( value ) {
    def map = [:] 
    map.name = "switch"
    map.value = value
    map.descriptionText = "${device.displayName} switch is ${value}"
    if (txtEnable) {log.info "${map.descriptionText}"}
    sendEvent(map)
}

def handleIlluminanceEvent( descMap ) {
    def rawEncoding = Integer.parseInt(descMap.encoding, 16)
    def rawLux = Integer.parseInt(descMap.value,16)
	def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lux", isStateChange: true)
	if (txtEnable) log.info "$device.displayName illuminance changed to $lux"
}

def handleTemperatureEvent( descMap ) {
    def rawValue = hexStrToSignedInt(descMap.value) / 100
    rawValue =  Math.round((rawValue - 0.05) * 10) / 10
	def Scale = location.temperatureScale
    if (Scale == "F") rawValue = (rawValue * 1.8) + 32
	if ((rawValue > 200 || rawValue < -200 || (Math.abs(rawValue)<0.1f)) ){
        log.warn "$device.displayName Ignored temperature value: $rawValue\u00B0"+Scale
	} else {
	    sendEvent("name": "temperature", "value": rawValue, "unit": "\u00B0"+Scale, isStateChange: true)
		if (txtEnable) log.info "$device.displayName temperature changed to $rawValue\u00B0"+Scale
    }
}

def handleHumidityEvent( descMap ) {
    def rawValue = Integer.parseInt(descMap.value,16) / 100
    rawValue =  ((float)rawValue).trunc(1)
	if (rawValue > 1000 || rawValue <= 0 ) {
    	log.warn "$device.displayName ignored humidity value: $rawValue"
	}
    else {
	    sendEvent("name": "humidity", "value": rawValue, "unit": "%", isStateChange: true)
		if (txtEnable) log.info "$device.displayName humidity changed to $rawValue"
	}
}



//  called from initialize()
def installed() {
    if (settings?.txtEnable) log.info "installed()"
    sendEvent(name: "temperature", value: 20, unit: "C", displayed: false)     
    //sendEvent(name: "switch", value: "on", displayed: true)
    unschedule()
}

def updated() {
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    log.info "Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(/*1800*/86400, logsOff)    // turn off debug logging after 30 minutes
        if (settings?.txtEnable) log.info "Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    log.info "Update finished"
}

def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    zigbee.readAttribute(0, 1)
}

def logInitializeRezults() {
    log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars() {
    if (settings?.logEnable) log.debug "${device.displayName} UnitializeVars()..."
    state.clear()
    //
    state.old_dp = ""
    state.old_fncmd = ""
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    state.duplicateCounter = 0
    //
    device.updateSetting("logEnable", false)    
    device.updateSetting("txtEnable", true)    
}



def configure() {
    initialize()
}

def initialize() {
    if (true) "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    installed()
    updated()
    runIn( 3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "sendTuyaCommand = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
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
    if (logEnable) log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery was ${result.value}%"
        result.isStateChange = true
        sendEvent(result)
    }

    return result
}

private Map getBatteryResult(rawValue) {
    if (logEnable) log.debug 'Battery'
    def linkText = getLinkText(device)

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
        result.descriptionText = "${linkText} battery was ${result.value}%"
        result.name = 'battery'
        result.isStateChange = true
        sendEvent(result)
    }
    return result
}


def zTest( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue

    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"

    switch ( getModelGroup() ) {
        case 'MOES' :
        case 'UNKNOWN' :
        default :
            break
    }     

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    
