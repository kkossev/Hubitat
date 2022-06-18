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
 *
*/

def version() { "1.0.0" }
def timeStamp() {"2022/06/18 2:08 PM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

@Field static final Boolean debug = true
@Field static final Integer pulseTimer  = 1000
@Field static final Integer doorTimeout = 5000


metadata {
    definition (name: "Tuya Zigbee Garage Door Opener", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Garage%20Door%20Opener/Tuya%20Zigbee%20Garage%20Door%20Opener.groovy", singleThreaded: true ) {
        capability "Actuator"
        capability "GarageDoorControl"    // door - ENUM ["unknown", "open", "closing", "closed", "opening"]; Commands: close() open()
        capability "ContactSensor"        // contact - ENUM ["closed", "open"]
        capability "Configuration"
        capability "Switch"

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
    }
}


private getCLUSTER_TUYA() { 0xEF00 }

// Parse incoming device messages to generate events
def parse(String description) {
    if (logEnable == true) log.debug "${device.displayName} parse: description is $description"
    checkDriverVersion()
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
                    case 0x03 : // Contact
                        def value = fncmd == 1 ? "closed" : "open"
                        sendContactEvent(value)
                        break
                    case 0x0C : // Door Status ?
                        if (logEnable) log.info "${device.displayName} Tuya report: Door Status is ${fncmd}"
                        break
                    default :
                        if (debug == true) {
                            if (dp==0x07) {
                                def value = fncmd == 1 ? "closed" : "open"
                                sendContactEvent(value)
                                return null
                            }
                        }
                        if (logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                        break
                }
			} // if command in ["00", "01", "02"]
            else {
                if (logEnable) log.warn "${device.displayName} <b>NOT PROCESSED COMMANDTuya cmd ${descMap?.command}</b> : dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
		} // if Tuya cluster
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
    log.debug "${device.displayName} open()"
	sendDoorEvent("opening")
    runInMillis( doorTimeout, confirmOpen, [overwrite: true])
    pulseOn()
}

def close() {
    log.debug "${device.displayName} close()"
	sendDoorEvent("closing")
    runInMillis( doorTimeout, confirmClosed, [overwrite: true])
    pulseOn()
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
    if (txtEnable) {log.info "${device.displayName} ${map.descriptionText} (${map.type})"}
    sendEvent(map)
}

def confirmClosed(){
	sendDoorEvent("closed")
    sendContactEvent("closed", isDigital=true)
}

def confirmOpen(){
    sendDoorEvent("open")
    sendContactEvent("open", isDigital=true)
}

void initializeVars( boolean fullInit = true ) {
    if (logEnable==true) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
}

def initialize() {
    if (txtEnable==true) log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
	sendEvent(name: "door", value: "unknown")
	sendEvent(name: "contact", value: "unknown")
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