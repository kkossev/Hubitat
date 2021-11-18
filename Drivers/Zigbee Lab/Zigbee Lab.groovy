/**
 *  Zigbee Lab HE driver
 *
 *  This is a HE driver to consolidate all my experimental Zigbee protocol bits and pieces from my production drivers into a single test driver code.
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
 * 
 * ver. 1.0.0 2021-10-24 kkossev  - first dummy version
 * ver. 1.0.1 2021-11-18 kkossev  - even more messy stuff
 *
*/
public static String version()	  { return "v1.0.1" }


import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubMultiAction
import hubitat.zigbee.zcl.DataType
import hubitat.zigbee.clusters.iaszone.ZoneStatus



metadata {
    definition (name: "Zigbee Lab", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Zigbee%20Lab/Zigbee%20Lab.groovy" ) {
      
		capability "Refresh"
    	capability "Initialize"
    	capability "Configuration"
        capability "EnergyMeter"          // energy - NUMBER, unit:kWh
        capability "PowerMeter"           // power - NUMBER, unit:W
        capability "CurrentMeter"         // amperage - NUMBER, unit:A
        capability "VoltageMeasurement"   // voltage - NUMBER, unit:V; frequency - NUMBER, unit:Hz
        capability "Actuator"    
        capability "Switch"               // switch - ENUM ["on", "off"]; off(); on()
        capability "Outlet"               // switch - ENUM ["on", "off"]; off(); on()
        capability "Health Check"         // checkInterval - NUMBER; ping()
        capability "Sensor"
        capability "Configuration"        // configure()
        capability "PresenceSensor"       // ENUM ["present", "not present"]
        capability "Polling"              // poll()
        //capability "SignalStrength"       // lqi - NUMBER; rssi - NUMBER
        //capability "PowerSource"          // powerSource - ENUM ["battery", "dc", "mains", "unknown"]

    attribute   "driver", "string"
  
    command "activeEndpoints"
    command "zdoBind"
    command "zdoUnbind"
    command "raw"
    command "zigbeeCommand"
    command "heCr"
    command "configureReporting"
    command "resetReportingToFactoryDefaults"
// command "playSoundByName", [[name: "Sound Name", type: "STRING", description: "Sound object name"], [name: "Set Volume", type: "NUMBER", description: "Sets the volume before playing the message"],[name: "Restore Volume", type: "NUMBER", description: "Restores the volume after playing"]]
//        command "playTellStory", [[name: "Set Volume", type: "NUMBER", description: "Sets the volume before playing the message"],[name: "Restore Volume", type: "NUMBER", description: "Restores the vo        
    command "readAttribute", [[name: "Cluster", type: "STRING", description: "Zigbee Cluster (Hex)", defaultValue : "0001"], [name: "Attribute", type: "STRING", description: "Attribute (Hex)", defaultValue : "0002"]]
    command "readAttributesTS004F"
    command "heGrp"
    command "test"
    command "test2"
    command "heCmd"
    command "leaveAndRejoin"
    command "enrollResponse"
    command "getClusters"
    command "zclGlobal"

      
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_ANY_", model: "ANY", deviceJoinName: "Hubitat Zigbee Lab"
    }
    preferences {
        input (name: "traceEnable", type: "bool", title: "Enable trace logging", defaultValue: true)
        input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true)
        input (name: "rawCommands", type: "bool", title: "Send raw commands where applicable", defaultValue: false)
        if (autoPollingEnabled?.value==true) {
            input (name: "pollingInterval", type: "number", title: "<b>Polling interval</b>, seconds", description: "<i>The time period when the smart plug will be polled for power, voltage and amperage readings. Recommended value is <b>60 seconds</b></i>", 
                   range: "10..3600", defaultValue: 60)
        }
    }
}


// Constants
@Field static final Integer DIMMER_MODE = 0
@Field static final Integer SCENE_MODE  = 1
@Field static final Integer DEBOUNCE_TIME = 900
@Field static final Integer powerDiv = 1
@Field static final Integer energyDiv = 100
@Field static final Integer currentDiv = 1000


// Parse incoming device messages to generate events
// example : https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/advancedZigbeeCTbulb.groovy 
//
//parsers
void parse(String description) {
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
    ////////////////////////////////////////////////////////   zdo commands ////////////////////////////////////////////
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
            /*
                String currentState = device.currentValue("switch") ?: "on"
                String opt = powerRestore ?: pwrRstOpts.defaultValue
                List<String> cmds = configAttributeReporting()
                if ((opt == "last" && currentState == "off") || (opt == "off")) {
                    cmds.addAll(zigbee.off(0))
                }
                sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
                String currentAddress = descMap.data[2] + descMap.data[1]
                if (currentAddress != state.lastAddress) {
                    if (txtEnable) log.info "address changed! new:${currentAddress}, old:${state.lastAddress}, checking group membership status..."
                    state.lastAddress = currentAddress
                    runIn(4,getGroups)
                }
*/
                break
            default :
                if (logEnable) log.warn "skipped UNKNOWN zdo cluster: ${descMap.clusterId}"
        }
        return
    }

    List<Map> additionalAttributes = []
    additionalAttributes.add(["attrId":descMap.attrId, "value":descMap.value, "encoding":descMap.encoding])
    if (descMap.additionalAttrs) additionalAttributes.addAll(descMap.additionalAttrs)
    parseAttributes(descMap.cluster, descMap.endpoint, additionalAttributes, descMap.command)
}

private void parseAttributes(String cluster, String endPoint, List<Map> additionalAttributes, String command){
    //if (logEnable) log.warn "parseAttributes cluster:${cluster}"
    additionalAttributes.each{
        switch (cluster) {
            case "0000" :
                switch (it.attrId) {
                    case "0000" :
                        log.info "parseAttributes: ZLC version: ${it.value}"        // default 0x03
                        break
                    case "0001" :
                        log.info "parseAttributes: Applicaiton version: ${it.value}"    // For example, 0b 01 00 0001 = 1.0.1, where 0x41 is 1.0.1
                        break                                                            // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-lighting-dimmer-swith-access-standard?id=K9ik6zvlvbqyw 
                    case "0002" : 
                        log.info "parseAttributes: Stack version: ${it.value}"        // default 0x02
                    case "0003" : 
                        log.info "parseAttributes: HW version: ${it.value}"        // default 0x01
                    case "0004" :
                        log.info "parseAttributes: Manufacturer name: ${it.value}"
                        break
                    case "0005" :
                        log.info "parseAttributes: Model Identifier: ${it.value}"
                        break
                    case "0007" :
                        log.info "parseAttributes: Power Source: ${it.value}"        // enum8-0x30 default 0x03
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
                log.warn "parseAttributes: cluster ${cluster}"
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
                if (logEnable) log.warn "parseAttributes cluster:${cluster} attrId ${it.attrId} value:${it.value}"
                break
            case "0300" :
                if (logEnable) log.warn "parseAttributes cluster:${cluster} attrId ${it.attrId} value:${it.value}"
/*            
                if (it.attrId == "0007") { //color temperature
                    if (state.checkPhase == 4) {
                        state.ct.min = myredHexToCt(zigbee.swapOctets(it.value))
                        runOptionTest(5)
                    } else if (state.checkPhase == 5) {
                        state.ct.max = myredHexToCt(zigbee.swapOctets(it.value))
                        runOptionTest(10)
                    }
                    sendColorTempEvent(zigbee.swapOctets(it.value))
                } //else log.trace "skipped color, attribute:${it.attrId}, value:${it.value}"
*/
                break
            default :
                if (logEnable) {
                    String respType = (command == "0A") ? "reportResponse" : "readAttributeResponse"
                    log.warn "parseAttributes: skipped:${cluster}:${it.attrId}, value:${it.value}, encoding:${it.encoding}, respType:${respType}"
                }
        }
    }
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
                    log.warn "processGlobalCommand read attribute response descMap:${descMap}"
                    switch (descMap.clusterId) {
                        case "E001" : /// tuya specific
                            log.info "processGlobalCommand ${descMap.clusterId} (read attribute response) clusterId: ${descMap.clusterId} data:${descMap.data}"
                            break
                        default :
                            log.warn "processGlobalCommand ${descMap.clusterId} (read attribute response) UNKNOWN clusterId: ${descMap.clusterId} data:${descMap.data}"
                            def status = descMap.data[2]
                            def hexValue = descMap.data[1] + descMap.data[0] 
                            if (status == "86") {
                                log.warn "Unsupported Attributte ${hexValue}"
                            }
                    }
                    break
                case "04" : //write attribute response
                    log.info "processGlobalCommand writeAttributeResponse cluster: ${descMap.clusterId} status:${descMap.data[0]}"
                    break
                case "0B" ://command response
                    String clusterCmd = descMap.data[0]
                    status =  descMap.data[1]
                    switch (descMap.clusterId) {
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



/*
def parse(String description) {
    //if (logEnable) log.debug "description is $description"
	def event = null
    try {
        event = zigbee.getEvent(description)
    }
    catch (e) {
        if (logEnable) {log.error "exception caught while procesing event $description"}
    }
	def result = []
    def buttonNumber = 0
    
	if (event) {
        result = event
        if (logEnable) log.debug "sendEvent $event"
    }
    else if (description?.startsWith("catchall")) {
        def descMap = zigbee.parseDescriptionAsMap(description)            
        if (logEnable) log.debug "catchall descMap: $descMap"
        //log.debug "catchall data: ${descMap.data[0]} ${descMap.data[1]} ${descMap.data[7]}"
        //log.debug "device = ${device.deviceNetworkId}"
        //def zbc = zigbee.command()
        def zbc = EMBER_AF_DOXYGEN_CLI_COMMAND_ZDO_ZDO_BIND
        log.debug "zbc = ${zbc}"
        //
        def buttonState = "unknown"
        // when TS004F initialized in Scene switch mode!
        if (descMap.clusterInt == 0x0006 && descMap.command == "FD") {
            if (descMap.sourceEndpoint == "03") {
     	        buttonNumber = reverseButton==true ? 3 : 1
            }
            else if (descMap.sourceEndpoint == "04") {
      	        buttonNumber = reverseButton==true  ? 4 : 2
            }
            else if (descMap.sourceEndpoint == "02") {
                buttonNumber = reverseButton==true  ? 2 : 3
            }
            else if (descMap.sourceEndpoint == "01") {
       	        buttonNumber = reverseButton==true  ? 1 : 4
            }
        }
        else if (descMap.clusterInt == 0x8021 && descMap.sourceEndpoint == "00") {
            if (descMap.data[1]=="00") {
                if (logEnable) {log.debug "binding confirmation received"}
            }
            else {
                if (logEnable) {log.warn "binding confirmation ERROR ${descMap.data[1]}"}
            }
            if (logEnable) {log.debug "catchall descMap: $descMap"}
        }
        else {
            if (logEnable) {log.warn "unprocessed catchall from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint}"}
            if (logEnable) {log.debug "catchall descMap: $descMap"}
        }
        //
        if (buttonNumber != 0 ) {
            if ( state.lastButtonNumber == buttonNumber ) {    // debouncing timer still active!
                if (logEnable) {log.warn "ignored event for button ${state.lastButtonNumber} - still in the debouncing time period!"}
                runInMillis(DEBOUNCE_TIME, buttonDebounce)    // restart the debouncing timer again
                return null 
            }
            state.lastButtonNumber = buttonNumber
            if (descMap.data[0] == "00")
                buttonState = "pushed"
            else if (descMap.data[0] == "01")
                buttonState = "doubleTapped"
            else if (descMap.data[0] == "02")
                buttonState = "held"
            else {
                 if (logEnable) {log.warn "unkknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"}
                 return null 
            }
        }
        if (buttonState != "unknown" && buttonNumber != 0) {
	        def descriptionText = "button $buttonNumber was $buttonState"
	        event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true]
            if (txtEnable) {log.info "$descriptionText"}
        }
        
        if (event) {
            //if (logEnable) {log.debug "Creating event: ${event}"}
		    result = createEvent(event)
            runInMillis(DEBOUNCE_TIME, buttonDebounce)
	    } 
	} // if catchall
    
    else if (description?.startsWith("read attr -")) {
        //if (logEnable) log.debug "processing cluster ${descMap?.cluster}"
        switch(descMap?.cluster) {
            case "0000":
                switch (descMap?.attrId) {
                    case "0001":
                    if (logEnable) {log.debug "Application ID Received ${descMap?.value}"}
                        //updateApplicationId(msgMap['value'])
                        break
                    case "0004":
                        if (logEnable) {log.debug("Manufacturer Name Received ${descMap?.value}")}
                        //updateManufacturer(msgMap['value'])
                        break
                    case "0005":
                        if (logEnable) {log.debug("Model Name Received ${descMap?.value}")}
                        //setCleanModelName(newModelToSet=msgMap["value"])
                        break
                    default:
                        break
                }
                break
            case "0001":    // battery reporting
                if (descMap.commandInt != 0x07) {
                    if (logEnable) {log.debug("processing read attr: cluster 0x001 (Power Configuration)")}
                    if (descMap.attrInt == 0x0021) {
                        getBatteryPercentageResult(Integer.parseInt(descMap?.value,16))
                    } else {
                        getBatteryResult(Integer.parseInt(descMap?.value, 16))
                    }                    
                }
                else {
                    if (logEnable) {log.warn("UNPROCESSED battery reporting because escMap.commandInt == 0x07 ????")}
                }
                break
            default:
                if (logEnable) {
                    log.warn "UNPROCESSED cluster ${descMap?.cluster} !!! descMap : ${descMap} ######## description = ${description}"
                }
                break
        }
    } // if read attr
    else {
        if (logEnable) {log.warn "DID NOT PARSE MESSAGE for description : $description"}
	}
    return result
}

*/



def refresh() {
    def comment = "Hubitat Zigbee Lab"
    state.comment = comment
    def attr = "test attribure"
    sendEvent(name: "attribute1", value: attr)
    updateDataValue('attribute1', attr)
}


def configure() {
	if (logEnable) log.debug "Configuring device ${device.getDataValue("model")} in Scene Switch mode..."
    initialize()
}


def installed() 
{
  	initialize()
}

def initialize() {
   // readAttributesTS004F()
    def numberOfButtons = 4
    sendEvent(name: "numberOfButtons", value: numberOfButtons , displayed: false)
    state.lastButtonNumber = 0
    configureReporting()
}

def updated() 
{
    if (logEnable) {log.debug "updated()"}
}


def buttonDebounce(button) {
    if (logEnable) log.warn "debouncing button ${state.lastButtonNumber}"
    state.lastButtonNumber = 0
}


def switchToSceneMode()
{
    zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01)
}

def switchToDimmerMode()
{
     zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00)   
}

def configureReporting(  ) {
    Integer endpointId = 1
    ArrayList<String> cmd = []
    
    // List configureReporting(Integer clusterId, Integer attributeId, Integer dataType, Integer minReportTime, Integer maxReportTime, Integer reportableChange = null, Map additionalParams=[:], int delay = STANDARD_DELAY_INT)
    // was (                                0x0402,             0x0000,             0x29,                 60,              3600,             20,                         [:],                     52)
    cmd += zigbee.configureReporting(0x0402, 0x0000, 0x29, 1, 120, 1, [:], 52)        // was (0x0402, 0x0000, 0x29, 60, 3600, 20, [:], 52)
    cmd += zigbee.configureReporting(0x0405, 0x0000, 0x29, 1, 120, 1, [:], 53)        // was (0x0405, 0x0000, 0x29, 60, 3600, 200, [:], 53)   

    cmd += zigbee.readAttribute(0x0001, [0x0020, 0x0021])
    cmd += zigbee.readAttribute(0x0402, 0x0000)
    cmd += zigbee.readAttribute(0x0405, 0x0000)
    
    log.trace "configureReporting : ${cmd}"
    sendZigbeeCommands(cmd)
}

def resetReportingToFactoryDefaults() {
    return getResetToDefaultsCmds()
}

List<String> getResetToDefaultsCmds() {
	List<String> cmds = []

	cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 0xFFFF, null, [:], 500)	// Reset Battery Voltage reporting to default
	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 0xFFFF, null, [:], 500)	// Reset Battery % reporting to default
	cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 0, 0xFFFF, null, [:], 500)	// Reset Temperature reporting to default (looks to be 1/2 hr reporting)
	cmds += zigbee.configureReporting(0x0403, 0x0000, DataType.INT16, 0, 0xFFFF, null, [:], 500)	// Reset Pressure reporting to default (looks to be 1/2 hr reporting)
	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.UINT16, 0, 0xFFFF, null, [:], 500)   // Reset Humidity reporting to default (looks to be 1/2 hr reporting)

	//cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}"
	//cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0001 0x0020 0x20 0xFFFF 0x0000 {0000}"
	//cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}"
	//cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0001 0x0021 0x20 0xFFFF 0x0000 {0000}"
	//cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0402 {${device.zigbeeId}} {}"
	//cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0402 0x0000 0x29 0xFFFF 0x0000 {0000}"
	//cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0405 {${device.zigbeeId}} {}"
	//cmds += "he cr 0x${device.deviceNetworkId} 0x01 0x0405 0x0000 0x21 0xFFFF 0x0000 {0000}"

	return cmds
}


def readAttributesTS004F() {
return
    Map dummy = [:]
    ArrayList<String> cmd = []
    
    cmd += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], dummy, delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, Unknown 0xfffe
    cmd += zigbee.readAttribute(0x0006, 0x8004, dummy, delay=50)    // success / 0x00
    cmd += zigbee.readAttribute(0xE001, 0xD011, dummy, delay=50)    // Unsupported attribute (0x86)
    cmd += zigbee.readAttribute(0x0001, [0x0020, 0x0021], dummy, delay=50)    // Battery voltage + Battery Percentage Remaining
    cmd += zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01, dummy, delay=50)        // switch into Scene Mode !
    cmd += zigbee.readAttribute(0x0006, 0x8004, dummy, delay=50)
    //
    // or  // "zdo bind 0x${device.deviceNetworkId} 1 1 0x0001 {${device.zigbeeId}} {}", "delay 50" ??????????
    /*
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x04 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
*/
        for (endpoint = 1; endpoint <= 4; endpoint++)
    {
        cmd += ["zdo bind ${device.deviceNetworkId} ${endpoint} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 50", 
                "delay 1000", 
                "he cr 0x${device.deviceNetworkId} ${endpoint} 6 0 16 0 900 {}", "delay 50", 
                "delay 1000"]
    }
    //cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}"    // Bind the outgoing level cluster from remote to hub, so the hub receives messages when Dim Up/Down buttons pushed
    //cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}"    // Bind the incoming battery info cluster from remote to hub, so the hub receives battery updates
    //
    sendZigbeeCommands(cmd)
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (logEnable) {log.debug "sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}


def parsePowerEnergy(String description) {
    if (logEnable) {log.debug "description is $description"}
    def event = zigbee.getEvent(description)

    if (event) {
        //log.info "event enter:$event"
        setPresent()
        if (event.name== "power") {
            event.value = event.value/powerDiv
            event.unit = "W"
        } else if (event.name== "energy") {
            event.value = event.value/energyDiv
            event.unit = "kWh"
        }
        if (txtEnable) {log.info "${event.name} = $event.value"} 
        sendEvent(event)
        if (event.name== "switch") {
            //TODO: if switch changes previous state - refresh!
            if (state.lastSwitchState != event.value ) {
                if (logEnable) {log.trace "switch state changed from <b>${state.lastSwitchState}</b> to <b>${event.value}</b>"}
                state.lastSwitchState = event.value
            }
        }
    } else {
        List result = []
        def descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) {log.debug "Desc Map: $descMap"}

        List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value]]
        descMap.additionalAttrs.each {
            attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value]
        }

        attrData.each {
                def map = [:]
                if (it.value && it.cluster == "0B04" && it.attrId == "050B") {
                        map.name = "power"
                        map.value = zigbee.convertHexToInt(it.value)/powerDiv
                        map.unit = "W"
                        if (state.lastPower != map.value ) {
                            if (logEnable) {log.trace "power changed from <b>${state.lastPower}</b> to <b>${map.value}</b>"}
                            state.lastPower = map.value
                        }
                }
                else if (it.value && it.cluster == "0B04" && it.attrId == "0505") {
                        map.name = "voltage"
                        map.value = zigbee.convertHexToInt(it.value)/powerDiv
                        map.unit = "V"
                }
                else if (it.value && it.cluster == "0B04" && it.attrId == "0508") {
                        map.name = "amperage"
                        map.value = zigbee.convertHexToInt(it.value)/currentDiv
                        map.unit = "A"
                }
                else if (it.value && it.cluster == "0702" && it.attrId == "0000") {
                        map.name = "energy"
                        map.value = zigbee.convertHexToInt(it.value)/energyDiv
                        map.unit = "kWh"
                }
                else {
                    //log.warn "^^^^ UNRPOCESSED! ^^^^"
                }

                if (map) {
                    if (txtEnable) {log.info "${map.name} = ${map.value} ${map.unit}"}
                    result << createEvent(map)
                }
                if (logEnable) {log.debug "Parse returned $map"}
        }
        return result
    }
}


def off() {
    def cmds = zigbee.off()
    if (device.getDataValue("model") == "HY0105") {
        cmds += zigbee.command(zigbee.ONOFF_CLUSTER, 0x00, "", [destEndpoint: 0x02])
    }
    return cmds
}

def on() {
    def cmds = zigbee.on()
    if (device.getDataValue("model") == "HY0105") {
        cmds += zigbee.command(zigbee.ONOFF_CLUSTER, 0x01, "", [destEndpoint: 0x02])
    }
    return cmds
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    return refresh()
}

// Sends refresh / readAttribute commands to the plug
def poll() {
    zigbee.onOffRefresh() +
    zigbee.electricMeasurementPowerRefresh() +
    zigbee.readAttribute(0x0702, 0x0000) + 
    zigbee.readAttribute(0x0B04, 0x0505) +      // voltage
    zigbee.readAttribute(0x0B04, 0x0508)        // current
}

// not used !
def powerRefresh() {
    def cmds = zigbee.electricMeasurementPowerRefresh()
    cmds.each{
        sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds,200), hubitat.device.Protocol.ZIGBEE))
    }
}

def refresh() {
    if (logEnable) {log.debug "refresh"}
    poll()

}

def autoPoll() {
    if (logEnable) {log.debug "autoPoll()"}
    checkIfNotPresent()
    if (autoPollingEnabled?.value==true) {
        runIn( pollingInterval.value, autoPoll)
    }
    poll()    
}

// Called when preferences are saved
def updated(){
    if (logEnable) {log.debug "updated() : Saved preferences"}
    configure()
}

void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) {log.debug "${device.displayName} sendZigbeeCommands received : ${cmds}"}
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}



/* =================================================== test commands ================================================= */

def zdoBind() {
    // example: "zdo bind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 200",
    
    

    // from https://github.com/RichardLaxton/Hubitat/blob/main/Ikuu%20Mercator%20GPO%20with%20Power%20Reporting.groovy
     def cmd = []
    for (endpoint = 1; endpoint <= 4; endpoint++)
    {
        cmd += ["zdo bind ${device.deviceNetworkId} ${endpoint} 0x01 0x0006 {${device.zigbeeId}} {}", "delay 50", 
                "delay 1000", 
                "he cr 0x${device.deviceNetworkId} ${endpoint} 6 0 16 0 900 {}", "delay 50", 
                "delay 1000"]
    }
    //cmd += powerConfig()
    //cmd += refresh()
    
    return cmd

    
}

def zdoUnbind() {
}

def raw() {
    // example : https://community.hubitat.com/t/iris-v2-keypad-w-2-0-7-2-0-8-2-0-9-or-2-1-0-no-tones-after-arming/12555/33?u=kkossev 
    // https://community.hubitat.com/t/iris-v2-keypad-w-2-0-7-2-0-8-2-0-9-or-2-1-0-no-tones-after-arming/12555/34?u=kkossev : return ["he raw 0x${device.deviceNetworkId} 1 1 0xFC04 {15 4E 10 00 00 00}"]
    // https://community.hubitat.com/t/need-help-converting-st-zigbee-dth-to-he/13658
/*
HE raw zigbee frame (for the same command)
List cmds = ["he raw 0x${device.deviceNetworkId} 1 1 0x0501 {09 01 00 04}"]
	
he raw 
0x${device.deviceNetworkId} 16 bit hex address 
1							source endpoint, always one				 
1 							destination endpoint, device dependent
0x0501 						zigbee cluster id
{09 						frame control
	01 						sequence, always 01
		00 					command
			04}				command parameter(s)    
*/    
    
}


def zigbeeCommand() {
    if (rawCommands==true) {
        heCmd()
    }
    else {
        // zigbee.command( .... )
         heCmd()
    }
}

def heCmd() {
    // example : https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/GenericZigbeeRGBWBulb.groovy
    // example : "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0300 0x06 {${hexHue} ${hexSat} ${intTo16bitUnsignedHex(rate / 100)}}", "delay ${rate + 400}",
    // example :  "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}","delay 200",
    
    Map dummy = [:]
    ArrayList<String> cmd = []
    // example : https://community.hubitat.com/t/discover-zigbee-active-endpoints/44950/3?u=kkossev
    // ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get all the endpoints...
    //cmd += "he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    
    cmd += "he raw  0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 4 {}"
    
    sendZigbeeCommands(cmd)    
    
    
    //"he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}","delay 200"
}

def heCr() {
    // example : "he cr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0300 0 0x0008 1 0xFFFE {}", "delay 200",
}

def heWattr() {
    // example : https://community.hubitat.com/t/zigbee-writing-hex-string-with-he-wattr/38051/7?u=kkossev
    // write Long attributes:
/*
ArrayList zigbeeWriteLongAttribute(Integer cluster, Integer attributeId, Integer dataType, Long value, Map additionalParams = [:], int delay = 2000) {
    String mfgCode = ""
    if(additionalParams.containsKey("mfgCode")) {
        mfgCode = " {${HexUtils.integerToHexString(HexUtils.hexStringToInt(additionalParams.get("mfgCode")), 2)}}"
    }
    String wattrArgs = "0x${device.deviceNetworkId} 0x01 0x${HexUtils.integerToHexString(cluster, 2)} " + 
                       "0x${HexUtils.integerToHexString(attributeId, 2)} " + 
                       "0x${HexUtils.integerToHexString(dataType, 1)} " + 
                       "{${Long.toHexString(value)}}" + 
                       "$mfgCode"
    ArrayList cmdList = ["he wattr $wattrArgs", "delay $delay"]
    return cmdList
}


*/
    
    
}

def heGrp() {
    // example : https://community.hubitat.com/t/need-help-converting-st-zigbee-dth-to-he/13658/14?u=kkossev
    // "he grp cmd 0x${groupAddress} 0x01 0x${cluster} 0x${command} { ${hexData} }"
}

def writeAttribute() {
    // example : https://community.hubitat.com/t/sending-raw-zigbee-commands/81459/10
    // zigbee.writeAttribute(cluster, attributeId, dataType, [destEndpoint :0x02])
}


def readAttribute (String cluster, String attrId) {
    // example : https://community.hubitat.com/t/zigbee-writing-hex-string-with-he-wattr/38051/2?u=kkossev
    // example : zigbee.readAttribute(0x0000,0x0401,[mfgCode: "0x115F"])
    
    log.trace "cluster=${cluster} attrId=${attrId}"
    def test
    if (rawCommands==true) {
        heRattr()
    }
    else {
        test = zigbee.readAttribute(hubitat.helper.HexUtils.hexStringToInt(attrId), hubitat.helper.HexUtils.hexStringToInt(cluster))
        log.trace "test=${test}"
    }
    return test
}

def heRattr() {
    // example : https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/GenericZigbeeRGBWBulb.groovy
    // example : "he rattr 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0300 0x0000 {}", "delay 200",
    // example : def commandtosend = "HE wattr 0x${device.deviceNetworkId} 8 0x000 0x010 0x42 {10"+packed+"}" // SAMPLELIGHT_ENDPOINT is defined as 8 in device code // the 10 on the end means 16 bytes length
    //    ^^^^^^^^^^^check if working? 
}





def activeEndpoints() {
    Map dummy = [:]
    ArrayList<String> cmd = []
    // example : https://community.hubitat.com/t/discover-zigbee-active-endpoints/44950/3?u=kkossev
    // ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get all the endpoints...
    cmd += "he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    sendZigbeeCommands(cmd)
}


def test()
{
    Map dummy = [:]
    ArrayList<String> cmd = []
    
    //cmd += "he raw leave 0x${device.deviceNetworkId} 0x01 0x01"
    
    //  send  cmd 20 data 3 to cluster 00C0
    // "he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00C0 {11 00 20 03 00} {0xC216}
    // he raw [16 bit address] [source endpoint] [destination endpoint] [cluster id] {[payload]} {[profile id]}
    
    
    
    // touchlink - reset to factory defaults
    cmd += "he raw ${device.deviceNetworkId} 1 1 0x1000 {11 00 07 01 00 00 00} {0x0104}"
    
    
    
    
    
    //cmd += "he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0x1000 {09 01 40 00 00 01 01}  {0x0104}"
    //        11 -> Frame Control Field
    //        12 -> ???
    //        13 -> Command
    //        14 -> ?
    //        15 -> ?
/*
    WireShark result:
    ZigBee Application Support Layer Data, Dst Endpt: 1, Src Endpt: 0
        Frame Control Field: Data (0x40)
        Destination Endpoint: 1
        Cluster: On/Off (0x0006)
        Profile: Home Automation (0x0104)
        Source Endpoint: 0
        Counter: 166

    ZigBee Cluster Library Frame
        Frame Control Field: Cluster-specific (0x11)
        Sequence Number: 155
        Command: Unknown (0x13)
        Payload
            40 01 06 00 04 01 00 a6    11 9b 13 14 15
                                          ^^ Sequential number?



*/
    sendZigbeeCommands(cmd)
}


def test2() {

    log.debug "${device} : Refresh. Sending Hello to Device"

    
	def cmds = new ArrayList<String>()
    /*
	cmds.add("he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00F6 {11 00 FC 01} {0xC216}")    // version information request
	cmds.add("he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00EE {11 00 01 01} {0xC216}")    // power control operating mode nudge ( I dont think we need this)
*/
    
    		//zcl global write [cluster:2] [attributeId:2] [type:4] [data:-1]
    // https://community.hubitat.com/t/went-back-to-smarthings-but-came-back/10629/144
    String hubZigbeeId = swapEndianHex(device.hub.zigbeeEui)
	cmds.add("zcl global write 0x500 0x10 0xf0 {${hubZigbeeId}}")
	cmds.add("send 0x${device.deviceNetworkId} 0x08 1")
    // {0xC216} = Profile: AlertMe (0xc216)
    
	sendZigbeeCommands(cmds)

}

def leaveAndRejoin() {
    // https://github.com/zigpy/zigpy/issues/831
    //
    //   Mgmt_Leave_req = 0x0034
    //
    // ZDOCmd.Mgmt_Leave_req: (("DeviceAddress", t.EUI64), ("Options", t.bitmap8)),
    //    ZDOCmd.Mgmt_Permit_Joining_req: (
    //    ("PermitDuration", t.uint8_t),
    //    ("TC_Significant", t.Bool),
    //),
    //Defined in zigpy/types/named.py
    //class EUI64(basic.FixedList, item_type=basic.uint8_t, length=8):
    //
    /*
        ZDOCmd.Mgmt_Leave_req: (
        (
            lambda addr, DeviceAddress, Options: c.ZDO.MgmtLeaveReq.Req(
                DstAddr=addr.address,
                IEEE=DeviceAddress,
                RemoveChildren_Rejoin=c.zdo.LeaveOptions(Options),
            )
        )
    */
    //
    // check also https://github.com/zigpy/zigpy/blob/a19a2a063d5edacb46722aeb56d538badd37fcc0/zigpy/zdo/types.py
    //
    /*

        const payload: Events.DeviceLeavePayload = {
            networkAddress: nwk,
            ieeeAddr: `0x${ieee.toString('hex')}`,
        };

*/
    
    def cmds = new ArrayList<String>()
    log.debug "${device} : Sending leaveAndRejoin to Device zigbeeId= ${device.zigbeeId} endpointId =${device.endpointId}"
    
/*
Frame Control Field: 0x1209, Frame Type: Command, Discover Route: Suppress, Security, Extended Source Command
    Destination: 0xbaaf
    Source: 0x0000
    Radius: 1
    Sequence Number: 97
    Extended Source: SiliconL_ff:fe:85:ee:07 (84:71:27:ff:fe:85:ee:07)
    ZigBee Security Header
    Command Frame: Leave
       Command Identifier: Leave (0x04)
        ..1. .... = Rejoin: True
        .1.. .... = Request: True
        0... .... = Remove Children: False


*/
    
    //   "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed

    
   //  send  cmd 20 data 3 to cluster 00C0
    // "he raw ${device.deviceNetworkId} 0 ${device.endpointId} 0x00C0 {11 00 20 03 00} {0xC216}
   // cmds += "he raw 0x${device.deviceNetworkId} 0x01 0x01 0x0006  ${device.zigbeeId} 0x0000 {FF 12 04 14 15}"
    
    cmds += "he raw 0x${device.deviceNetworkId} 1 1 0x0034 ${device.zigbeeId} 0 {19 01 04 60}"


    
    // cmds += "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0000 4 {60}"
    //cmds += "he raw 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    //cmds +=     "send 0x${device.deviceNetworkId} 1 1", "delay 1500"
    //        11 -> Frame Control Field
    //        12 -> ???
    //        13 -> Command
    //        14 -> ?
    //        15 -> ?
/*
    WireShark result:
    ZigBee Application Support Layer Data, Dst Endpt: 1, Src Endpt: 0
        Frame Control Field: Data (0x40)
        Destination Endpoint: 1
        Cluster: On/Off (0x0006)
        Profile: Home Automation (0x0104)
        Source Endpoint: 0
        Counter: 166

    ZigBee Cluster Library Frame
        Frame Control Field: Cluster-specific (0x11)
        Sequence Number: 155
        Command: Unknown (0x13)
        Payload
            40 01 06 00 04 01 00 a6    11 9b 13 14 15
                                          ^^ Sequential number?



*/
/*
        "zcl global send-me-a-report 0x402 0 0x29 300 3600 {6400}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",

*/
    
    
	sendZigbeeCommands(cmds)


}


def enrollResponse() {
    // example : https://community.smartthings.com/t/new-zigbee-device-securifi-key-fob/15453/16 
    // example : https://github.com/tierneykev/SmartThings_New/blob/master/devicetypes/tierneykev/securifi-key-fob.src/securifi-key-fob.groovy 
    
}


def getClusters() { 
    // example : https://community.smartthings.com/t/new-zigbee-device-securifi-key-fob/15453/19
     "zdo active 0x${device.deviceNetworkId}" 
       log.debug "Get Clusters Called";
}


def zclGlobal() {
    // example : https://github.com/tierneykev/hubitat/blob/master/Securifi.Groovy

    log.debug "zclGlobal Called"
	String hubZigbeeId = swapEndianHex(device.hub.zigbeeId)
	def configCmds = [
		//------IAS Zone/CIE setup------//
		//zcl global write [cluster:2] [attributeId:2] [type:4] [data:-1]
		"zcl global write 0x500 0x10 0xf0 {${hubZigbeeId}}", "delay 200",
		"send 0x${device.deviceNetworkId} 0x08 1", "delay 1500",

		//------Set up binding------//
		//zdo bind Dev_Nework_ID Src_Endpoint Dest_Endpoint Cluster Zigbee_ID
		"zdo bind 0x${device.deviceNetworkId} 0x08 0x01 0x0501 {${device.zigbeeId}} {}", "delay 500",
		//**Do we need this
		"zdo bind 0x${device.deviceNetworkId} 0x08 1 1 {${device.zigbeeId}} {}"
	] 
	return configCmds            
            
            
}


/*
Tuya ManufacturerSpecificCluster
================================
    // https://github.com/zigpy/zigpy/discussions/823#discussioncomment-1539469

tuya is using 0xEF00 for there DP commands to TS0601 devices (tunneling MQTT commands to there cloud).

"Normal" tuya Zigbee devices:
Manufactur cluster:

0xE000 Not known
0xE001 Not known
0xE002 is being used in some sensors with attribute:
0xD00A: ("alarm_temperature_man", t.uint16_t),
0xD00B: ("alarm_temperature_min", t.uint16_t),
0xD00C: ("alarm_humidity_max", t.uint16_t),
0xD00E: ("alarm_humidity_min", t.uint16_t),
0xD00F: ("alarm_humidity", ValueAlarm),
0x00 Min alarm
0x01 Max alarm
0x02 Amarm off
0xD006: ("temperature_humidity", ValueAlarm),
0x00 Min alarm
0x01 Max alarm
0x02 Amarm off
0xD010: {"unknown", t.uint8_t},
Extra "custom attribute added to ZCL cluster:

0x0006 OnOff Cluster
0x8001 Back Light Mode (t.enum8)
0x00 BL Mode 0
0x01 BL Mode 1
0x02 BL Mode 2
0x8002 Power On State (t.enum8)
0x00 = Off
0x01 = On
0x02 = Last state
0x8004 Switch Operation Mode (t.enum8)
0x00 = Command Mode (Light OnOff / Dimmer commands)
0x01 = Event Mode (tuya "scene" commands)

*/


/*  other Tuya specifics 
=========================================================

https://github.com/MattWestb/zha-device-handlers/blob/678de0ca19ebae9f6de170adf2bffb1ece16d4f3/zhaquirks/tuya/ts130f.py#L29-L38



*/



/*
from https://docs.silabs.com/zigbee/6.5/af_v2/group-zdo

#define EMBER_AF_DOXYGEN_CLI_COMMAND_ZDO_ZDO_LEAVE
zdo leave [target:2] [removeChildren:1] [rejoin:1]

Send a ZDO Management Leave command to the target device.
    target - INT16U - Target node ID
    removeChildren - BOOLEAN - Remove children
    rejoin - BOOLEAN - Rejoin after leave
*/


/*
GPL libraries used in Hubitat:
    ...
    Zsmartsystems ZigBee                 ver 2.1.10
    Zsmartsystems ZigBee Dongle Ember    ver 2.1.10
    ...


*/


/*    ====== ZIgbee Cluster Librabry Frame =================== Frame Control Field

https://zigbeealliance.org/wp-content/uploads/2019/11/docs-05-3474-21-0csg-zigbee-specification.pdf

The frame control field is 8-bits in length and contains information defining the frame type, addressing fields, and other control flags.
    Bits: 0-1  : Frame type
        | b1 b0 | - Frame type name
        |   00  | - Data
        |   01  | - Command
        |   10  | - Acknowledgement
        |   11  | - Inter-PAN APS

    Bits: 2-3  : Delivery mode
        | b3 b2 | : Delivery Mode Name
        |   00  | - Normal unicast delivery
        |   01  | - Reserved
        |   10  | - Broadcast
        |   11  | - Group addressing

If the value is 0b00, the frame will be delivered to a given endpoint on the receiving device.
If the value is 0b10, the message is a broadcast. In this case, the message will go to all devices defined for the selected broadcast address in use as defined in section 3.6.5. The destination endpoint field shall be set
     to a value between 0x01-0xfe (for broadcasts to specific endpoints) or to 0xff (for broadcasts to all active endpoints).
If the value is 0b11, then group addressing is in use and that frame will only be delivered to device end points that express group membership in the group identified by the group address field in the APS header.
Note that other endpoints on the source device may be members of the group addressed by the outgoing frame. The frame shall be delivered to any member of the group, including other endpoints on the source device that are members of the specified group.
Devices where nwkUseMulticast is set to TRUE, shall never set the delivery mode of an outgoing frame to 0b11. In this case, the delivery mode of the outgoing frame shall be set to 0b10 (broadcast) and the frame shall be sent using an NLDE-DATA.request with the destination address mode set to group addressing.


    Bits: 4    : Ack. format 
This bit indicates if the destination endpoint, cluster identifier, profile identifier and source endpoint fields shall be present in the acknowledgement frame. 
This is set to 0 for data frame acknowledgement and 1 for APS command frame acknowledgement.

    Bits: 5    : Security
The Security Services Provider (see Chapter 4) manages the security sub-field.

    Bits: 6    : Ack. request
The acknowledgement request sub-field is one bit in length and specifies whether the current transmission requires an acknowledgement frame to be sent to the originator on receipt of the frame. 
If this sub-field is set to 1, the recipient shall construct and send an acknowledgement frame back to the originator after determining that the frame is valid.
If this sub-field is set to 0, the recipient shall not send an acknowledge1995 ment frame back to the originator.
This sub-field shall be set to 0 for all frames that are broadcast or multicast.

    Bits: 7    : Extended header present
The extended header present sub-field is one bit in length and specifies whether the extended header shall be included in the frame.
If this sub-field is set to 1, then the extended header shall be included in the frame. 
Otherwise, it shall not be included in the frame.


*/





def intTo16bitUnsignedHex(value) {
    def hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}


/* ================================= another TS004F model ????

        fingerprint: [{modelID: 'TS004F', manufacturerName: '_TZ3000_pcqjmcud'}],
        model: 'YSR-MINI-Z',
        vendor: 'TuYa',
        description: '2 in 1 Dimming remote control and scene control',

*/


/* ********************************** bookmarks  ************************

https://community.smartthings.com/t/faq-zigbee-application-profiles-or-why-not-all-zigbee-devices-work-with-smartthings/76219
^^^a ^big list of useful posts ^^

https://www.nxp.com/docs/en/user-guide/JN-UG-3076.pdf
^^^^ ZigBee Home Automation User Guide ^^ 353 pp
https://www.nxp.com/docs/en/user-guide/JN-UG-3101.pdf
^^^^ ZigBee PRO Stack User Guide ^^ 444 pp

https://www.drdsnell.com/projects/hubitat/drivers/AlmondClick.groovy
^^^^^^^^^^^^^ big list of Zigbee clusters ^^^^^^^^^^^^^^^^

https://github.com/hubitat/HubitatPublic/blob/master/examples/drivers/environmentSensor.groovy
^^^^^^^^^^^^^ temp/humidity/illuminance/pressure parsing sample

https://github.com/iharyadi/hubitat/blob/master/Environment%20SensorEx/Environment%20SensorEX.groovy
https://github.com/iharyadi/hubitat/blob/master/Environment%20SensorEx/Environment%20SensorEX.groovy
^^^^^^^^ getLQITable() ^^^^^^^^^^^^^^^^^^^^^^

https://community.hubitat.com/t/is-zigbee-a-con-overpriced/21887/121?u=kkossev
The second tuple in the raw description is the application profile, 0104 is zha, C05E is zll





*/



/* 

=================== Tuya ====================
Basic cluster 

Commands:
ID	    Name	                    Direction	Description
0x00	Reset to factory defaults	C->S	    Recieve command




Touchlink commisioning cluster
================================
Attributes (server):
ID	    Name	        Data type	Range	        Default value
0xFFFD	ClusterRevision	uint16-0x21	0x0000-0xffff	0x0001

Attributes (client):
ID	    Name	        Data type	Range	        Default value
0xFFFD	ClusterRevision	uint16-0x21	0x0000-0xffff	0x0001

Commands (server)
ID	    Name	                    Direction	    Description
0x00	Scan request	            C->S	        Recieve command
0x02	Device information request	C->S	        Recieve command
0x06	Identify request	        C->S	        Recieve command
0x07	Reset to factory new request	C->S	    Recieve command
0x14	Network join end device request	C->S	    Recieve command
0x01	Scan response	            S->C	        Send command
0x03	Device information response	S->C	        Send command
0x40	Endpoint information	    S->C	        Send command
0x41	Get group identifiers response	S->C	    Send command
0x42	Get endpoint list response	S->C	        Send command

Commands (client):
ID	    Name	                    Direction	Description
0x00	Scan request	            C->S	Send command
0x02	Device information request	C->S	Send command
0x06	Identify request	        C->S	Send command
0x07	Reset to factory new request	C->S	Send command
0x10	Network start request	    C->S	Send command
0x12	Network join router request	C->S	Send command
0x14	Network join end device	    C->S	Send command
0x01	Scan response	            S->C	Recieve command
0x03	Device information response	S->C	Recieve command
0x11	Network start response	    S->C	Recieve command
0x13	Network join router response	S->C	Recieve command
0x15	Network join end device response	S->C	Recieve command
0x40	Endpoint information	    S->C	Recieve command
0x41	Get group identifiers response	S->C	Recieve command
0x42	Get endpoint list response	S->C	Recieve command

*/