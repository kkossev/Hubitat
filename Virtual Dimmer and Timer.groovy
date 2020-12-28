/**
 *                 Virtual Dimmer and Timer   version 1.0.0.3
 *    
 * Benefits when using a 'Timed Session' virtual driver:
 *    - Easy to make a fully Event driven WebCore piston or RM4 rule. No need to use any Wait/Delay commands inside the applications
 *    - Easy to display the "timeRemaining" on Hubitat Dashboards (it is a standard attribute!), as there are no wait/delay functions inside the application,
 *    - "simulationEnable" switch makes the debugging of the timed functions easy ( the timer decreases every 3 seconds, instead of 1 minute!)
 * 
 *
 * (https://github.com/kkossev/Hubitat/blob/main/Virtual%20Dimmer%20and%20Timer.groovy)
 *
 * Standard Capabilities:
 *      Switch, Switch Level, Refresh, Configuration, Actuator, Timed Session
 *   
 * "Timed Session" Standard Commands:   
 *       "setTimeRemaining", ["number"] - set the timeout of the virtual switch timer.
 *       "start"    - start the count down from the previously  "setTimeRemaining", number of minutes.
 *       "stop"    - stop (zero) the countdown timer.
 *
 * "Timed Session" Standard Attributes:
 *         "setTimeRemaining" - a number that is decreased every minute and can be accessed by RM4 / WebCore / Dashboards.
 * 
 * "Timed Session" Events:
 *        "timeRemaining", "number"    - fired every minute when changed; contains the countdown timer value
 *        "sessionStatus", "enum", ["running", "stopped"]    - fired on "start()" and "stop()" commands or when the "timeRemaining" expires!
 *
 * Other Standard Events:
 *     "switch", "enum", ["on", "off"] - fired when commands "on()" or "off()" are used.
 *     "level", ["number"] - fired when the virtual switch level changes from "SetLevel (number)" command
 * 
 *
 *	Author: 
 *      Krassimir Kossev (Trakker2)
 *
 *	Changelog:
 *
 *  1.0.0.1    (12/25/2020)
 *    	- the initial version of the driver. It waas based on Custom events and attributes..
 *
 *  1.0.0.2    (12/26/2020)
 *    - "simulationEnable" preference added - when set to "true", the timer decreases in 3 seconds interval (instead of 1 minute)!
 *    - "TimedSession" standard capability is added; the custom capabilities and attributes are replaces with standard 'Timed Session'.
 *
 *  1.0.0.3    (12/28/2020)
 *     - corrected and documented the "Timed Session" Standard Commands, Attributes and Events in the comments section inside the driver code.
 *     - changed the nameespace to 'kkossev' to match the github repository
 *     - added "logEnable"- enables/disables debug logging.
 *     - the switch is not forced to 'off' state on each update/configuration; the dimmer level is also not changed if within the 0..100 limit range.
 *     - logs and simulation modes (if enabled) are automaticallt scheduled to be turned off after 30 minutes!
 *
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
	definition (name: "Virtual Dimmer and Timer", namespace: "kkossev", author: "Krassimir Kosev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Virtual%20Dimmer%20and%20Timer.groovy") {
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
        capability "Configuration"
        capability "Actuator"
        capability "Timed Session"      // setTimeRemaining(NUMBER); start(); stop(); pause(); cancel() 
                                        // attribute sessionStatus - ENUM ["stopped", "canceled", "running", "paused"]
                                        // attribute timeRemaining - NUMBER
	}
	preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
		input name: "simulationEnable", type: "bool", title: "Simulation mode (1 min = 3 seconds!)", defaultValue: false
	}
}





def getVersion() {
	"Virtual Dimmer and Timer Version 1.0.0.3"
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def simulationOff(){
    log.warn "simulation disabled..."
    device.updateSetting("simulationEnable",[value:"false",type:"bool"])
}


def configure() {
    //
    log.warn "wiping states"
	state.clear()
    log.warn "Clearing schedules"
	unschedule()
    //
    if (device.currentValue("level") <0 || device.currentValue("level") > 100)
	    setLevel(50)
    state.stateTimer = timeRemaining= 0
    state.version = getVersion()
	//off()    
	log.warn "configured..."
}


def installed() {
    configure()
    log.warn "installed..."
    runIn(1800,logsOff)
}


def updated() {
    state.version = getVersion()
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    log.warn "simulation is: ${simulationEnable == true}"
    if (logEnable) runIn(1800,logsOff)
    if (simulationEnable) runIn(1800,simulationOff)
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

        if ( state.stateTimer == 0 ) {    // expired just now!
            descriptionText = "${device.displayName} timer Expired !"
            sessionStatus = "stopped"
            sendEvent(name: "sessionStatus", value: "stopped", descriptionText: descriptionText, isStateChange: true, type: "digital")
            if (txtEnable) log.info descriptionText
        }
        else {
            if (txtEnable) log.info "start timer again for ${state.stateTimer}"
            runTimer()        // one more minute
        }
    }
    else {
        if (txtEnable) log.info "timer WAS NOT DECREASED ${state.stateTimer} !!!!!!!!"
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
    if (txtEnable) log.info descriptionText
}

def stop() {                  //capability.timedSession
    sessionStatus = "stopped"
    def descriptionText = "${device.displayName} stop: sessionStatus is ${sessionStatus}"
    sendEvent(name: "sessionStatus", value: "${sessionStatus}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    if (txtEnable) log.info descriptionText
}

def setTimeRemaining( number ) {                  //capability.timedSession
    state.stateTimer = setTimeRemaining = number as int
    def descriptionText = "${device.displayName} setTimeRemaining: ${number as int}"
    sendEvent(name: "setTimeRemaining", value: "${number}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    if (txtEnable) log.info descriptionText
}



        



