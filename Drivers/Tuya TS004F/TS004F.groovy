/**
 *	Copyright 2021 kkossev
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
 *  Hubitat Elevation TS004F driver. The inital version was based on ST DH "Zemismart Button", namespace: "SangBoy", author: "YooSangBeom"
 * 
 * ver. 1.0.0 2021-05-08 kkossev     - SmartThings version 
 * ver. 2.0.0 2021-10-03 kkossev     - First working version for Hubitat in 'Scene Control'mode
 *
 */

metadata {
    definition (name: "Tuya Scene Switch TS004F", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20TS004F/TS004F.groovy" ) {
      
	capability "Refresh"
    capability "PushableButton"
    capability "DoubleTapableButton"
    capability "HoldableButton"
	//capability "ReleasableButton"

    capability "Initialize"
    capability "Configuration"
      
    command "swicthIntoSceneMode"

 	fingerprint inClusters: "0000,0001,0003,0004,0006,1000", outClusters: "0019,000A,0003,0004,0005,0006,0008,1000", manufacturer: "_TZ3000_xabckq1v", model: "TS004F", deviceJoinName: "Tuya Scene Switch TS004F"
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
        input (name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true)
    }
}


// Parse incoming device messages to generate events
def parse(String description) {
    //if (logEnable) log.debug "description is $description"
	def event = zigbee.getEvent(description)
	def result = []
    def buttonNumber = 0
    final  DEBOUNCE_TIME = 900
    
	if (event) {
        result = event
        //if (logEnable) log.debug "sendEvent $event"
    }
    else if (description?.startsWith("catchall")) {
        def descMap = zigbee.parseDescriptionAsMap(description)            
        //if (logEnable) log.debug "catchall descMap: $descMap"
        def buttonState = "unknown"
        // TS004F in scene switch mode!
        if (descMap.clusterInt == 0x0006 && descMap.sourceEndpoint == "03" ) {
 	        buttonNumber = 1
        }
        else if (descMap.clusterInt == 0x0006 && descMap.sourceEndpoint == "04" ) {
  	        buttonNumber = 2
        }
        else if (descMap.clusterInt == 0x0006 && descMap.sourceEndpoint == "02" ) {
            buttonNumber = 3
        }
        else if (descMap.clusterInt == 0x0006 && descMap.sourceEndpoint == "01" ) {
   	        buttonNumber = 4
        }
        else {
             if (logEnable) log.warn "unprocessed event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint}"
        }
        //
        if (buttonNumber != 0 ) {
            if ( state.lastButtonNumber == buttonNumber ) {    // debouncing timer still active!
                //if (logEnable) log.warn "ignored event for button ${state.lastButtonNumber} - still in the debouncing time period!"
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
                 if (logEnable) log.warn "unkknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"
                 return null 
            }
        }
        if (buttonState != "unknown" && buttonNumber != 0) {
	        def descriptionText = "button $buttonNumber was $buttonState"
	        event = [name: "button", value: buttonState, data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, displayed: true]
            if (txtEnable) log.info "$descriptionText"
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
                    if (logEnable) log.debug "Application ID Received ${descMap?.value}"
                        //updateApplicationId(msgMap['value'])
                        break
                    case "0004":
                        if (logEnable) log.debug("Manufacturer Name Received ${descMap?.value}")
                        //updateManufacturer(msgMap['value'])
                        break
                    case "0005":
                        if (logEnable) log.debug("Model Name Received ${descMap?.value}")
                        //setCleanModelName(newModelToSet=msgMap["value"])
                        break
                    default:
                        break
                }
                break
            case "0001":    // battery reporting
                if (descMap.commandInt != 0x07) {
                    if (logEnable) log.debug("processing read attr: cluster 0x001 (Power Configuration)")
                    if (descMap.attrInt == 0x0021) {
                        getBatteryPercentageResult(Integer.parseInt(descMap?.value,16))
                    } else {
                        getBatteryResult(Integer.parseInt(descMap?.value, 16))
                    }                    
                }
                else {
                    if (logEnable) log.warn("UNPROCESSED battery reporting because escMap.commandInt == 0x07 ????")
                }
                break
            default:
                if (logEnable) {
                    log.warn "UNPROCESSED cluster ${descMap?.cluster} !!! descMap : ${descMap} ######## description = ${description}"
                             zigbee.enrollResponse()
                }
                break
        }
    } // if read attr
    else {
        log.warn "DID NOT PARSE MESSAGE for description : $description"
		log.debug zigbee.parseDescriptionAsMap(description)
	}
    return result
}


def refresh() {
}


def configure() {
	log.debug "Configuring device ${device.getDataValue("model")} in Scene Switch mode..."
    initialize()
}


def installed() 
{
  	initialize()
    def numberOfButtons = 4
    sendEvent(name: "supportedButtonValues", value: ["pushed", "held" /*,"double","up","up_hold","down_hold" */].encodeAsJSON(), displayed: false)
    sendEvent(name: "numberOfButtons", value: numberOfButtons , displayed: false)
    // Initialize default states
    numberOfButtons.times 
    {
        sendEvent(name: "button", value: "pushed", data: [buttonNumber: it+1], displayed: false)
    }
}

def initialize() {
    log.debug "Sending request to initialize TS004F in Scene Switch mode"
    runInMillis(5000, swicthIntoSceneMode)
    state.lastButtonNumber = 0
}

def updated() 
{
   log.debug "updated()"
}


def buttonDebounce(button) {
    //if (logEnable) log.warn "debouncing button ${state.lastButtonNumber}"
    state.lastButtonNumber = 0
}


def swicthIntoSceneMode ()
{
     zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01)        // magic
}
