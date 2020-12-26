// Copyright 2016, 2017, 2018 Hubitat Inc.  All Rights Reserved

metadata {
	definition (name: "Virtual Dimmer + Timer (KK)", namespace: "Trakker2", author: "Krassimir Kosev") {
		capability "Switch"
		capability "Switch Level"
		capability "Refresh"
        capability "Configuration"
        capability "Actuator"
        
        command "startTimer", ["number"]
        
        attribute "timer", "number"
        attribute "timerStartedEvent", "number"            //"enum", ["true", "false"]
        attribute "timerExpiredEvent", "enum", ["true", "false"]

	}
	preferences {
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input(name: "timerDelay99", type: "number", title:"Delay before OFF/ON", required: true, defaultValue: 99)	
	}
}


//def timerStarted


def installed() {
    configure()
}

def updated() {
	log.info "updated..."
	log.warn "description logging is: ${txtEnable == true}"
}

def parse(String description) {
}

def refresh() {
	sendEvent(name: "switch", value: device.currentValue("switch"))
	sendEvent(name: "level", value: device.currentValue("level"))
	sendEvent(name: "timer", value: device.currentValue("timer"))
}

def on() {
	def descriptionText = "${device.displayName} was turned on"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "switch", value: "on", descriptionText: descriptionText)
    runTimer();
}

def off() {
	def descriptionText = "${device.displayName} was turned off"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "switch", value: "off", descriptionText: descriptionText)
    // zero the timer also!
    if ( state.stateTimer != 0 ) {
        state.stateTimer = 0
        timer = 0
        descriptionText = "${device.displayName} timer is zeroed when switching OFF"
	    if (txtEnable) log.info "${descriptionText}"
	    sendEvent(name: "timer", value: "${state.stateTimer}", descriptionText: descriptionText, isStateChange: false, type: "digital")
    }
}

def setLevel(val){
	if (device.currentValue("switch") == "off" && val.toInteger() != 0) on()
	def descriptionText = "${device.displayName} level was set to ${val}%"
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "level", value: "${val}", descriptionText: descriptionText)
}


def setTimer(val){
    def timerTemp = val as int
    def descriptionText = "${device.displayName} setTimer: timer was set to ${val} "
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "timer", value: "${timerTemp}", descriptionText: descriptionText, isStateChange: true, type: "digital")
    state.stateTimer = timerTemp;
    runTimer();
}

//
//   command "startTimer", ["number"]
//   attribute "timerStarted"
//
def startTimer(val) {
    setTimer(val)
    state.stateTimer = val
    timer = state.stateTimer
    descriptionText = "${device.displayName} timerStartedEvent"
    sendEvent(name: "timerStartedEvent", value: "${val}", descriptionText: descriptionText, isStateChange: true, type: "digital")
}


def configure() {
    //
    log.warn "wiping states"
	state.clear()
    //
    log.warn "Clearing schedules"
	unschedule()
    //
	log.warn "installed..."
	setLevel(50)
    state.stateTimer = 0
    timer = 0;
    state.version = "Virtual Dimmer + Timer (KK) version 1.0.0.1"
	off()    
}

def runTimer () {
    runIn( 60 /*60*/ , decreaseTimer )
}


def decreaseTimer( ) {
    
    if (state.stateTimer > 0) {
        state.stateTimer = state.stateTimer - 1
        timer = state.stateTimer
        def descriptionText = "${device.displayName} decreaseTimer: timer was decreased to ${state.stateTimer}"
        log.info descriptionText
        //

        if ( state.stateTimer == 0 ) {    // expired just now!
            descriptionText = "${device.displayName} timer Expired !"
            sendEvent(name: "timerExpiredEvent", value: "true", descriptionText: descriptionText, isStateChange: true, type: "digital")
            log.info descriptionText;
        }
        else {
            log.info "start timer again for ${state.stateTimer}"
            runTimer();        // one more minute
        }
    }
    else {
        log.info "timer WAS NOT DECREASED ${state.stateTimer} !!!!!!!!"
    }
    // send timer event also!
    descriptionText = "${device.displayName} timer is ${state.stateTimer} minutes "
	if (txtEnable) log.info "${descriptionText}"
	sendEvent(name: "timer", value: "${state.stateTimer}", descriptionText: descriptionText, isStateChange: true, type: "digital")

}


def int getTimer ( ttt ) {
    ttt = state.timer;
}

private getVersion() {
	"Virtual Dimmer + TimedSesion Version 1.0"
}
