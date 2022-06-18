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
        //if (state.driverVersion ) {
        capability "Switch"
        //}
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
				def switchFunc = (descMap?.data[2])
                def switchAttr = (descMap?.data[3])   
                def switchState = (descMap?.data[6]) == "01" ? "on" : "off"
                if (switchFunc in ["01", "02", "03", "04"] && switchAttr =="01") {
   	                def cd = getChildDevice("${device.id}-${switchFunc}")
				    if (cd == null) {
				        return createEvent(name: "switch", value: switchState)
				    }
                    if (descMap?.command == "00") {
			            // switch toggled
			            cd.parse([[name: "switch", value:switchState, descriptionText: "Child switch ${switchFunc} turned $switchState"]])
			        } 
			        else if (descMap?.command in ["01", "02"]) {
                        // report switch status
                        cd.parse([[name: "switch", value:switchState, descriptionText: "Child switch ${switchFunc} is $switchState"]])
			        }
                    if (switchState == "on") {
			            if (logEnable) log.debug "Parent Switch ON"
			            return createEvent(name: "switch", value: "on")
			        } 
			        else if (switchState == "off") {
			            def cdsOn = 0
			            // cound number of switches on
			            getChildDevices().each { child ->
                            if (getChildId(child) != switchFunc && child.currentValue('switch') == "on") {
                                cdsOn++
                            }
			            }
			            if (cdsOn == 0) {
                            if (logEnable) log.debug "Parent Switch OFF"
                            return createEvent(name: "switch", value: "off")
			            }    
			        }
                }
			} // if command in ["00", "01", "02"]
		} // if Tuya cluster
	}
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
    if (logEnable) runIn(1800, logsOff)

}

def installed() {
    log.info "Installing..."
    log.warn "Debug logging will be automatically disabled after 30 minutes!"
    device.updateSetting("logEnable",[type:"bool",value:"true"])
    device.updateSetting("txtEnable",[type:"bool",value:"true"])
    if (logEnable) runIn(1800,logsOff)
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