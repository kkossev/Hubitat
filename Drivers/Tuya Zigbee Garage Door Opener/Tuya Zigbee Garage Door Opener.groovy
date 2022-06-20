/**
 *  Tuya Zigbee Garage Door Opener driver for Hubitat
 *
 *  https://community.hubitat.com/t/tuya-zigbee-garage-door-opener/95579
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
 * ver. 1.0.0 2022-06-18 kkossev  - Inital test version
 * ver. 1.0.1 2022-06-19 kkossev  - fixed Contact status open/close; added doorTimeout preference, default 15s; improved debug loging; PowerSource capability'; contact open/close status determines door state!
 * ver. 1.0.2 2022-06-20 kkossev  - ignore Open command if the sensor is open; ignore Close command if the sensor is closed.
 *
*/

def version() { "1.0.2" }
def timeStamp() {"2022/06/20 7:35 AM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

@Field static final Boolean debug = false
@Field static final Integer pulseTimer  = 1000


metadata {
    definition (name: "Tuya Zigbee Garage Door Opener", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Garage%20Door%20Opener/Tuya%20Zigbee%20Garage%20Door%20Opener.groovy", singleThreaded: true ) {
        capability "Actuator"
        capability "GarageDoorControl"    // door - ENUM ["unknown", "open", "closing", "closed", "opening"]; Commands: close() open()
        capability "ContactSensor"        // contact - ENUM ["closed", "open"]
        capability "Configuration"
        capability "Switch"
        capability "PowerSource"

        if (debug) {
            command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
            command "setContact", [[name:"Set Contact", type: "ENUM", description: "Select Contact State", constraints: ["--- Select ---", "open", "closed" ]]]
        }
        
        fingerprint profileId:"0104", model:"TS0601", manufacturer:"_TZE200_wfxuhoea", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", application:"42", deviceJoinName: "LoraTap Garage Door Opener"        // LoraTap GDC311ZBQ1
        fingerprint profileId:"0104", model:"TS0601", manufacturer:"_TZE200_nklqjk62", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", application:"42", deviceJoinName: "MatSee Garage Door Opener"         // MatSee PJ-ZGD01
    }

    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "doorTimeout", type: "number", title: "<b>Door timeout</b>", description: "<i>The time needed for the door to open, seconds</i>", range: "1..100", defaultValue: 15)
    }
}


private getCLUSTER_TUYA() { 0xEF00 }

// Parse incoming device messages to generate events
def parse(String description) {
    if (logEnable == true) log.debug "${device.displayName} parse: description is $description"
    checkDriverVersion()
    setPresent()
	if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        def descMap = [:]
        try {
            descMap = zigbee.parseDescriptionAsMap(description)
        }
        catch (e) {
            log.warn "${device.displayName} parse: exception caught while parsing descMap:  ${descMap}"
            return null
        }
		if (descMap?.clusterInt == CLUSTER_TUYA) {
        	if (logEnable) log.debug "${device.displayName} parse Tuya Cluster: descMap = $descMap"
			if ( descMap?.command in ["00", "01", "02"] ) {
                def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
                def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
                def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
                def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
                if (logEnable) log.trace "${device.displayName} Tuya cluster dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                switch (dp) {
                    case 0x01 : // Relay / trigger switch
                        def value = fncmd == 1 ? "on" : "off"
                        sendSwitchEvent(value)
                        break
                    case 0x02 : // unknown, received as a confirmation of the relay on/off commands? Payload is always 0
                        if (logEnable) log.debug "${device.displayName} received confirmation report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                        break
                    case 0x03 : // Contact
                    case 0x07 : // debug/testing only!
                        def contactState = fncmd == 0 ? "closed" : "open"    // reversed in ver 1.0.1
                        def doorState = device.currentState('door').value
                        sendContactEvent(contactState)
                        switch (doorState) {
                            case 'open' : // contact state was changed while the door was open
                                if (contactState == "open") {
                                    // do nothing - contact state confirms the door open state
                                }
                                else { // if the contact is now closed, the door state should be considered 'closed' as well !
                                    runInMillis( 100, confirmClosed, [overwrite: true])
                                }
                                break
                            case 'opening' : // contact state was changed while the door was in opening motion state
                                if (contactState == "open") {
                                    // do nothing - open contact state confirms the door opening state
                                }
                                else { // it is unusual if the contact changes to 'closed' during 'opening' door motion... just issue a warning!
                                    if (txtEnable) log.warn "${device.displayName} Contact changed to 'closed' during door 'open' command?"
                                }
                                break
                            case 'closing' : // contact state was changed while the door was in closing motion state
                                if (contactState == "closed") {
                                    // contact sensor closed confirmation -> force door status 'closed' as well
                                    runInMillis( 100, confirmClosed, [overwrite: true])
                                }
                                else { // it is unusual if the contact changes to 'open' during 'closing' door motion... just issue a warning!
                                    if (txtEnable) log.warn "${device.displayName} Contact changed to 'open' during door 'close' command?"
                                }
                                break
                            case 'closed' : // contact state was changed while the door was closed
                                if (contactState == "closed") {
                                    // do nothing - contact state confirms the door closed state
                                }
                                else { // if the contact is now open, the door state should be considered 'open' as well !
                                    runInMillis( 100, confirmOpen, [overwrite: true])
                                }
                                break;
                            default :    // unknown
                                if (logEnable) log.warn "${device.displayName} unknown door state ${doorState} while the contact was ${contactState}" 
                                break
                        }
                        break
                    case 0x0C : // Door Status ?
                        if (logEnable) log.info "${device.displayName} Tuya report: Door status is ${fncmd==2?'CLOSED':fncmd.toString()}"
                        break
                    default :
                        if (logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                        break
                }
			} // if command in ["00", "01", "02"]
            else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
                if (logEnable) log.debug "${device.displayName} device received Tuya cluster ZCL command 0x${descMap?.command} response: 0x${descMap?.data[1]} status: ${descMap?.data[1]=='00'?'success':'FAILURE'} data: ${descMap?.data}"
            } 
            else {
                if (logEnable) log.warn "${device.displayName} <b>NOT PROCESSED COMMAND Tuya cmd ${descMap?.command}</b> : dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
		} // if Tuya cluster
        else {
            if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
                if (logEnable) log.debug "${device.displayName} Tuya check-in: ${descMap}"
            }
            else {
                if (logEnable) log.debug "${device.displayName} parsed non-Tuya cluster: descMap = $descMap"
            }
        }
        
	} // if catchall or read attr
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


def on() { 
    if (logEnable) log.debug "${device.displayName} Turning the relay ON"
     sendZigbeeCommands(zigbee.command(0xEF00, 0x0, "00010101000101"))
}

def off() {
    if (logEnable) log.debug "${device.displayName} Turning the relay OFF"	
    sendZigbeeCommands(zigbee.command(0xEF00, 0x0, "00010101000100"))
}


def pulseOn() {
    if (logEnable) log.debug "${device.displayName} pulseOn()"
    runInMillis( pulseTimer, pulseOff, [overwrite: true])
    on()
}

def pulseOff() {
    if (logEnable) log.debug "${device.displayName} pulseOff()"
    off()
}


def open() {
    if (device.currentState('contact').value != "open") {
        if (logEnable) log.debug "${device.displayName} opening (door was ${device.currentState('door').value} , contact was ${device.currentState('contact').value})"
    	sendDoorEvent("opening")
        unschedule(confirmClosed)
        Integer timeout = settings?.doorTimeout * 1000
        runInMillis( timeout, confirmOpen, [overwrite: true])
        pulseOn()
    }
    else {
        if (txtEnable) log.warn "${device.displayName} ignoring Open command (door was ${device.currentState('door').value} , contact was ${device.currentState('contact').value})"
    }
}

def close() {
    if (device.currentState('contact').value != "closed") {
        if (logEnable) log.debug "${device.displayName} closing (door was ${device.currentState('door').value} , contact was ${device.currentState('contact').value})"
    	sendDoorEvent("closing")
        unschedule(confirmOpen)
        Integer timeout = settings?.doorTimeout * 1200  // add 20% tolerance when closing
        runInMillis( timeout , confirmClosed, [overwrite: true])
        pulseOn()
    }
    else {
        if (txtEnable) log.warn "${device.displayName} ignoring Close command (door was ${device.currentState('door').value} , contact was ${device.currentState('contact').value})"
    }
}

def sendDoorEvent(state) {
    def map = [:]
    map.name = "door"
    map.value = state    //  ["unknown", "open", "closing", "closed", "opening"]
    map.descriptionText = "${device.displayName} door is ${map.value}"
    if (txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}

def sendContactEvent(state, isDigital=false) {
    def map = [:]
    map.name = "contact"
    map.value = state    // open or closed
    map.type = isDigital == true ? "digital" : "physical"
    map.descriptionText = "${device.displayName} contact is ${map.value}"
    if (txtEnable) {log.info "${device.displayName} ${map.descriptionText} (${map.type})"}
    sendEvent(map)
}

def sendSwitchEvent(state, isDigital=false) {
    def map = [:]
    map.name = "switch"
    map.value = state    // on or off
    map.type = isDigital == true ? "digital" : "physical"
    map.descriptionText = "${device.displayName} switch is ${map.value}"
    if (logEnable) {log.info "${device.displayName} ${map.descriptionText} (${map.type})"}
    sendEvent(map)
}

def confirmClosed() {
    if (device.currentState('contact').value == 'closed') {
	    sendDoorEvent("closed")
    }
    else {
        sendDoorEvent("open")
        if (txtEnable) {log.warn "${device.displayName} closing failed, contact sensor is still open!"}
    }
}

def confirmOpen() {
    if (device.currentState('contact').value == 'open') {
        sendDoorEvent("open")
    }
    else {
        sendDoorEvent("closed")
        if (txtEnable) {log.warn "${device.displayName} open failed, contact sensor is still closed!"}
    }
}

void initializeVars( boolean fullInit = true ) {
    if (logEnable==true) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.doorTimeout == null) device.updateSetting("doorTimeout", 15)   
}

def initialize() {
    if (txtEnable==true) log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
	sendEvent(name: "door", value: "closed")
	sendEvent(name: "contact", value: "closed")
    sendEvent(name : "powerSource",	value : "mains")
    updated()            // calls also configure()
}

void logsOff(){
    log.warn "${device.displayName} Debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def tuyaBlackMagic() {
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)
}

def configure() {
    if (txtEnable==true) log.info "${device.displayName} configure().."
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    sendZigbeeCommands(cmds)
}

def updated() {
    log.info "${device.displayName} debug logging is: ${logEnable == true}"
    log.info "${device.displayName} description logging is: ${txtEnable == true}"
    if (txtEnable) log.info "${device.displayName} Updated..."
    if (logEnable) runIn(86400, logsOff, [overwrite: true])

}

def installed() {
    log.info "Installing..."
    log.info "Debug logging will be automatically disabled after 24 hours"
    device.updateSetting("logEnable",[type:"bool",value:"true"])
    device.updateSetting("txtEnable",[type:"bool",value:"true"])
    if (logEnable) runIn(86400, logsOff, [overwrite: true])
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
    }
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    sendEvent(name : "powerSource",	value : "mains", isStateChange : false)
}

void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) {log.trace "${device.displayName} sendZigbeeCommands received : ${cmds}"}
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
}

def setContact( mode ) {
    if (mode in ['open', 'closed']) {
        sendContactEvent(mode, isDigital=true)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} please select the Contact state"
    }
    
}