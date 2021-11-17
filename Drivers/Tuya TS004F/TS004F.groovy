/**
 *  Scene switch TS004F driver for Hubitat Elevation hub.
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
 *  The inital version was based on ST DH "Zemismart Button", namespace: SangBoy, author: YooSangBeom
 * 
 * ver. 1.0.0 2021-05-08 kkossev     - SmartThings version 
 * ver. 2.0.0 2021-10-03 kkossev     - First version for Hubitat in 'Scene Control'mode - AFTER PAIRING FIRST to Tuya Zigbee gateway!
 * ver. 2.1.0 2021-10-20 kkossev     - typos fixed; button wrong event names bug fixed; extended debug logging; added experimental switchToDimmerMode command
 * ver. 2.1.1 2021-10-20 kkossev     - numberOfButtons event bug fix; 
 * ver. 2.2.0 2021-10-20 kkossev     - First succesfuly working version with HE!
 * ver. 2.2.1 2021-10-23 kkossev     - added "Reverse button order" preference option
 * ver. 2.2.2 2021-11-17 kkossev     - added battery reporting capability; added buttons handlers for use in Hubutat Dashboards; code cleanup
 *
 */

import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubMultiAction

metadata {
    definition (name: "Tuya Scene Switch TS004F", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20TS004F/TS004F.groovy" ) {
      
	capability "Refresh"
    capability "PushableButton"
    capability "DoubleTapableButton"
    capability "HoldableButton"
    capability "Battery"

    capability "Initialize"
    capability "Configuration"
      
 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_xabckq1v", model: "TS004F", deviceJoinName: "Tuya Scene Switch TS004F"
    }
    preferences {
        input (name: "reverseButton", type: "bool", title: "Reverse button order", defaultValue: false)
        input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true)
    }
}

// Constants
@Field static final Integer DIMMER_MODE = 0
@Field static final Integer SCENE_MODE  = 1
@Field static final Integer DEBOUNCE_TIME = 900


// Parse incoming device messages to generate events
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
    else {
        if (logEnable) {log.warn "DID NOT PARSE MESSAGE for description : $description"}
	}
    return result
}

def refresh() {
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
    readAttributes()
    def numberOfButtons = 4
    sendEvent(name: "numberOfButtons", value: numberOfButtons , displayed: false)
    state.lastButtonNumber = 0
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

def buttonEvent(buttonNumber, buttonState) {

    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true]
    if (txtEnable) {log.info "$event.descriptionText"}
    sendEvent(event)
}

def push(buttonNumber) {
    buttonEvent(buttonNumber, "pushed")
}

def doubleTap(buttonNumber) {
    buttonEvent(buttonNumber, "doubleTapped")
}

def hold(buttonNumber) {
    buttonEvent(buttonNumber, "held")
}

def readAttributes() {
    ArrayList<String> cmd = []
    cmd += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, Unknown 0xfffe
    cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay=50)                      // success / 0x00
    cmd += zigbee.readAttribute(0xE001, 0xD011, [:], delay=50)                      // Unsupported attribute (0x86)
    cmd += zigbee.readAttribute(0x0001, [0x0020, 0x0021], [:], delay=50)            // Battery voltage + Battery Percentage Remaining
    cmd += zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01, [:], delay=50)         // switch into Scene Mode !
    cmd += zigbee.readAttribute(0x0006, 0x8004, [:], delay=50)
    //
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    cmd +=  "zdo bind 0x${device.deviceNetworkId} 0x04 0x01 0x0006 {${device.zigbeeId}} {}, delay 50"    // Bind the outgoing on/off cluster from remote to hub, so the hub receives messages when On/Off buttons pushed
    //
    sendZigbeeCommands(cmd)
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (logEnable) {log.trace "sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
    }
    sendHubCommand(allActions)
}
