/**
 *                 Virtual Dimmer and Timer   version 1.0.0.2
 *    
 *     
 *
 * (https://github.com/kkossev/Hubitat/blob/main/Virtual%20Dimmer%20and%20Timer.groovy)
 *
 * Standard Capabilities:
 *      Switch, Switch Level, Refresh, Configuration, Actuator
 *   
 * Custom Commands:   
 *       "startTimer", ["number"] 
 *
 * Custom Attributes:
 *        "timer", "number"
 *        "timerStartedEvent", "number"
 *        "timerExpiredEvent", "enum", ["true", "false"]
 *
 *	Author: 
 *      Krassimir Kossev (Trakker2)
 *
 *	Changelog:
 *
 *  1.0.0.1    (12/25/2020)
 *    	- the initial version of the driver.
 *
 *  1.0.0.2    (12/26/2020)
 *    - "simulationEnable" preference - when set to "true", the timer decreases in 3 seconds interval (instead of 1 minute)!
*    - added standard capability "TimedSession" :
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *    in compliance with the License. You may obtain a copy of the License at:
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *    for the specific language governing permissions and limitations under the License.
*/


metadata {
	definition (name: "Virtual Dimmer and Timer", namespace: "Trakker2", author: "Krassimir Kosev") {
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
        capability "Configuration"
        capability "Actuator"
        capability "TimedSession"
        capability "Timed Session"      // setTimeRemaining(NUMBER); start(); stop(); pause(); cancel() 
                                        // attribute sessionStatus - ENUM ["stopped", "canceled", "running", "paused"]
                                        // attribute timeRemaining - NUMBER
	}
	preferences {
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
		input name: "simulationEnable", type: "bool", title: "Simulation mode 1 min = 3 seconds!", defaultValue: false
	}
}





def getVersion() {
	"Virtual Dimmer and Timer Version 1.0.0.2"
}

def configure() {
    //
    log.warn "wiping states"
	state.clear()
    log.warn "Clearing schedules"
	unschedule()
	setLevel(50)
    state.stateTimer = /*timer =*/ timeRemaining= 0
    state.version = getVersion()
	off()    
	log.warn "configured..."
}


def installed() {
    configure()
    log.warn "installed..."
}

def updated() {
    state.version = getVersion()
	log.warn "description logging is: ${txtEnable == true}"
	log.info "updated..."
}

def parse(String description) {
    log.warn "parse(String description) not implemented"
}


def refresh() {
    def descriptionText = "${device.displayName} refresh "
    //isStateChange: true : just to display the refreshed values .... 
	sendEvent(name: "switch", value: device.currentValue("switch"), descriptionText: descriptionText, isStateChange: true, type: "digital")
	sendEvent(name: "level", value: device.currentValue("level"), descriptionText: descriptionText, isStateChange: true, type: "digital")
	sendEvent(name: "timeRemaining", value: device.currentValue("timeRemaining"), descriptionText: descriptionText, isStateChange: true, type: "digital")
}

def on() {
	def descriptionText = "${device.displayName} was turned on"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "switch", value: "on", descriptionText: descriptionText)
    runTimer()
}

def off() {
	def descriptionText = "${device.displayName} was turned off"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "switch", value: "off", descriptionText: descriptionText)
    // zero the timer also!
    if ( state.stateTimer != 0 ) {
        state.stateTimer = timeRemaining = 0
        descriptionText = "${device.displayName} timer is zeroed when switching OFF"
	    if (txtEnable) log.info "${descriptionText}"
	    sendEvent(name: "timeRemaining", value: "${timeRemaining}", descriptionText: descriptionText, isStateChange: false, type: "digital")
    }
}

def setLevel(val){
	if (device.currentValue("switch") == "off" && val.toInteger() != 0) on()
	def descriptionText = "${device.displayName} level was set to ${val}%"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "level", value: "${val}", descriptionText: descriptionText)
}


def startTimer(val) {
    
    def timerTemp = val as int
    def descriptionText = "${device.displayName} startTimer: timer was set to ${val} "
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "timeRemaining", value: "${timerTemp}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    state.stateTimer = timerTemp
    runTimer()
    
    
}



def runTimer () {
    if ( simulationEnable == false ) {
        runIn( 60  , decreaseTimer )
    }
    else {
        runIn( 3 , decreaseTimer )    // sumulation mode :  3 seconds = minute ..
    }
}


def decreaseTimer( ) {
    
    if (state.stateTimer > 0) {
        state.stateTimer = state.stateTimer - 1
        timeRemaining = state.stateTimer
        //
        def descriptionText = "${device.displayName} timeRemaining is ${state.stateTimer} minutes "
    	if (txtEnable) log.info "${descriptionText}"
    	sendEvent(name: "timeRemaining", value: "${state.stateTimer}", descriptionText: descriptionText, isStateChange: true, type: "digital")
        log.info descriptionText

        if ( state.stateTimer == 0 ) {    // expired just now!
            descriptionText = "${device.displayName} timer Expired !"
            sessionStatus = "stopped"
            sendEvent(name: "sessionStatus", value: "stopped", descriptionText: descriptionText, isStateChange: true, type: "digital")
            log.info descriptionText
        }
        else {
            log.info "start timer again for ${state.stateTimer}"
            runTimer()        // one more minute
        }
    }
    else {
        log.info "timer WAS NOT DECREASED ${state.stateTimer} !!!!!!!!"
    }

}

//
// Timed Session commands 
//

def cancel() {                  //capability.timedSession
    sessionStatus = "canceled"
    log.warn "cancel() not implemented"
}

def pause() {                    //capability.timedSession
    sessionStatus = "paused"
    log.warn "pause() not implemented"
}

def start() {                  //capability.timedSession
    startTimer(state.stateTimer)
    sessionStatus = "running" 
    def descriptionText = "${device.displayName} start: sessionStatus is ${sessionStatus}"
    sendEvent(name: "sessionStatus", value: "${sessionStatus}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    log.info descriptionText
}

def stop() {                  //capability.timedSession
    sessionStatus = "stopped"
    def descriptionText = "${device.displayName} stop: sessionStatus is ${sessionStatus}"
    sendEvent(name: "sessionStatus", value: "${sessionStatus}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    log.info descriptionText
    log.warn "stop() not fully implemented!!!"
}

def setTimeRemaining( number ) {                  //capability.timedSession
    state.stateTimer = setTimeRemaining = number as int
    def descriptionText = "${device.displayName} setTimeRemaining: ${number as int}"
    sendEvent(name: "setTimeRemaining", value: "${number}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    log.info descriptionText
}



        



