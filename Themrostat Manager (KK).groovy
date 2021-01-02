import java.text.SimpleDateFormat

definition(
    name: "Thermostat Manager",
    namespace: "elfege",
    author: "ELFEGE",

    description: "A Trully Smart Thermostat Manager That Can Save You Tons Of Money",

    category: "Green Living",
    iconUrl: "https://www.philonyc.com/assets/penrose.jpg",
    iconX2Url: "https://www.philonyc.com/assets/penrose.jpg",
    iconX3Url: "https://www.philonyc.com/assets/penrose.jpg", 
    image: "https://www.philonyc.com/assets/penrose.jpg"
)

preferences {

    page name: "settings"
    page name: "thermostats"
    page name: "methods"
    page name: "contactsensors"
    page name: "powersaving"

}
def settings() {

    pageNameUpdate()   

    def pageProperties = [
        name:       "settings",
        title:      "settings",
        nextPage:   null,
        install: true,
        uninstall: true
    ]

    dynamicPage(pageProperties) {

        section()
        {
            input "pause", "button", title: "$atomicState.button_name"
            input "buttonPause", "capability.doubleTapableButton", title: "Pause/resume this app when I double tap a button", multiple: true, required: false, submitOnChange:true

            if(buttonPause)
            {
                input "buttonTimer", "number", title: "optional: time limit in minutes", required:false
            }
            input "restricted", "mode", title: "Restricted modes", multiple: true
        }

        section("Main Settings") {
            href "thermostats", title: "Thermostats and other devices", description: ""
            href "methods", title: "Methods of evaluation", description: ""
            href "contactsensors", title: "Contact sensors", description: ""
            href "powersaving", title: "Power Saving Options", description: ""

        }

        section() {
            label title: "Assign a name", required: false
        }

        section("Actions")
        {
            input "run", "button", title: "RUN"
            input "update", "button", title: "UPDATE"
            input "poll", "button", title: "REFRESH SENSORS"
            input "polldevices", "bool", title: "Poll devices"
            input "enabledebug", "bool", title: "Debug", submitOnChange:true
            if(enabledebug)
            {
                log.warn "debug enabled"      
                atomicState.EnableDebugTime = now()
                runIn(1800,disablelogging)
                descriptiontext "debug will be disabled in 30 minutes"
            }
            else 
            {
                log.warn "debug disabled"
            }
            input "description", "bool", title: "Description Text", submitOnChange:true

        }
    }
}
def thermostats(){

    def title = formatText("Thermostats, sensors, heaters and coolers", "white", "grey")

    def pageProperties = [
        name:       "thermostats",
        title:      title,
        nextPage:   "settings",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {
        section("Select the thermostat you want to control")
        { 
            input "thermostat", "capability.thermostat", title: "select a thermostat", required: true, multiple: false, description: null, submitOnChange:true
            input "forceCmd", "bool", title:"Force commands (for old non-Zwave-plus devices that don't refresh their status properly under certain mesh conditions)", defaultValue:false
            input "pw", "capability.powerMeter", title:"verify status with a power meter", required:false
            input "heatpump", "bool", title: "$thermostat is a heat pump", submitOnChange:true
        }

        section("Sensors")
        {
            input "outsideTemp", "capability.temperatureMeasurement", title: "Required: select a weather sensor for outside temperature", required:true, submitOnChange:true
            if(thermostat != null && thermostat.hasCapability("RelativeHumidityMeasurement"))
            {
                paragraph formatText("""Your thermostat doesn't support humidity measurement, please select a separate humidity sensor so as to make sure 
that this app gives its best at keeping you comfortable!""", "white", "blue")
                input "optionalHumSensor", "capability.relativeHumidityMeasurement", title: "Select a humidity sensor", required:false, submitOnChange:true
            }
            input "sensor", "capability.temperatureMeasurement", title: "select a temperature sensor (optional)", submitOnChange:true, multiple:true
            if(sensor)
            {
                input "offrequiredbyuser", "bool", title: "turn off thermostat when desired temperature has been reached", defaultValue: false, submitOnChange:true
            }
        }

        section("Select alternate heater and/or cooler")
        {
            input "heater", "capability.switch", title: "Select a switch to control an alternate heater", required: false, submitOnChange:true, multiple: false 
            if(heater)
            {
                input "addLowTemp", "bool", title: "Turn on $heater only if OUTSIDE temperature goes below a certain threshold", submitOnChange:true   
            }
            if(heatpump || addLowTemp)
            {
                input "lowtemp", "number", title: "low temperature threshold", required: true, defaultValue: 0
            }

            input "cooler", "capability.switch", title: "Select a switch to control an alternate cooler", required: false, submitOnChange:true, multiple: false 

        }

        section("Central Thermostat")
        {
            if(thermostat){
                paragraph formatText("Make $thermostat a central thermostat for your home", "white", "blue")
                input "sync", "bool", title:"Synchronize ${thermostat} with states from a different thermostat", defaultValue:false, submitOnChange:true
                if(sync)
                {
                    input "thermostatB", "capability.thermostat", title: "select a second thermostat", required: true, multiple: true, description: null, submitOnChange:true
                    input "ignoreTherModes", "bool", title: "Ignore operating modes, synchronize set points only", defaultValue: false
                }
            }
        }
    }
}
def methods(){

    def title = formatText("METHODS OF EVALUTATION:", "white", "grey")

    def pageProperties = [
        name:       "methods",
        title:      title,
        nextPage:   "settings",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section(){
            input "autoOverride", "bool", title:"Pause this app when thermostat mode is 'auto'", submitOnChange: true, defaultValue: false
            if(autoOverride)
            {
                input "overrideDuration", "number", title: "Set a time limit", description: "number in hours, 0 means unlimitted time", submitOnChange:true
            }
            input "method", "enum", title:"select the method you want $app.name to use to adjust your thermostats cooling and heating set points", options:["dimmer","auto"],submitOnChange:true

            //used by both methods
            input "dimmer", "capability.switchLevel", title: "Use this dimmer as set point input source", required: false, submitOnChange:true

            if(method == "auto")
            {
                def reF =12 //KK outsideTemp ? outsideTemp.currentValue("temperature") : 25
                def des = 2
                def refTempTitle = outsideTemp ? "Set an outside temperature reference (You can leave the current outside temperature[KK]: $reF)": "Set an outside temperature reference for which you will set (below) a desired variation (for example: 77)"
                if(outsideTemp) {app.updateSetting("refTemp",[value:reF,type:"number"])}
                if(!refTemp){app.updateSetting("desiredVar",[value:des,type:"number"])}

                input "refTemp", "number", title: refTempTitle, required: false, range: "0..38", submitOnChange:true
                input "desiredVar", "number", title: "Set a desired variation: by how much do you want inside temperature to differ from outside temp when outside temp is $reF?", required:false, range: "0..38", submitOnChange:true
                if(refTemp && desiredVar)
                {
                    log.debug "refTemp = $refTemp desiredVar = $desiredVar"
                    paragraph """ 
In cooling mode, when outside temperature is ${refTemp}F your room will be cooled to ${refTemp - desiredVar}F. Now, based on these reference values, a linear function will apply hereafter (meaning this variation will change proportionally to how hot it gets outside)
In heating mode, the function will work with a different method to make sure to keep you warm """
                }
                input "maxAutoHeat", "number", title: "Highest heating set point", defaultValue: 28
                input "minAutoHeat", "number", title: "Lowest heating set point", defaultValue: 16

                input "minAutoCool", "number", title: "Lowest cooling set point", defaultValue: 16       
                input "maxAutoCool", "number", title: "Highest cooling set point", defaultValue: 28
            }

            input "antifreeze", "bool", title:"Optional: Customize Antifreeze",submitOnChange:true,defaultValue:true
            input "backupSensor", "capability.temperatureMeasurement", title: "Optional but highly recommended: pick a backup sensor (in case of network failure)", required:false

            if(antifreeze)
            {
                input "safeValue", "number", title: "safety temperature", required:true
            }
            input "sendAlert", "bool", title: "Send a sound and/or text notification when temperature goes below antifreeze safety", submitOnChange:true
            if(sendAlert)
            {
                input "speech", "capability.speechSynthesis", title: "Select your speech device", multiple:true, required:false, submitOnChange: true 
                input "speaker", "capability.audioNotification", title: "Select your speakers", multiple:true, required:false, submitOnChange: true 
                if(speaker)
                {
                    input "volumeLevel", "number", title: "Set the volume level", range: "0..100",required:true, submitOnChange: true  
                }
                input "initializeDevices", "bool", title:"Try to fix unresponsive speakers (such as Chrome's)", defaultValue:false
                input "notification", "capability.notification", title: "Select notification devices", multiple:true, required:false, submitOnChange: true 
            }
        }
    }
}
def contactsensors(){

    def title = formatText("CONTACTS AND DOORS", "white", "grey")

    def pageProperties = [
        name:       "contactsensors",
        title:      title,
        nextPage:   "settings",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section()
        {
            input "WindowsContact", "capability.contactSensor", title: "Turn off everything when any of these contacts is open", multiple: true, required: false, submitOnChange:true            
            if(WindowsContact)
            {
                input "openDelay", "number", title: "After how long?", description: "Time in seconds", required:true
            }

            input "useAbedSensor", "bool", title: "Use a bed sensor", submitOnChange:true
            if(useAbedSensor)
            {
                def message = ""
                def devivesStr = ""

                input "bedSensorType", "enum", title: "Which type ?", options: ["contact", "button", "both"], submitOnChange:true, required:true
                if(bedSensorType == "contact" || bedSensorType == "both")
                {
                    def s = bedSensorContact?.size() 
                    def i = 0
                    input "bedSensorContact", "capability.contactSensor", title: "When ${!bedSensorContact ? "this contact is" : (s > 1 ? "these contacts are" : "this contacts is")} closed, operate in limited mode", multiple: true, required: false, submitOnChange:true
                    if(bedSensorContact)
                    {
                        for(s!=0;i<s;i++){
                            devivesStr = devivesStr.length() > 0 ? devivesStr + ", " + bedSensorContact[i].toString() : bedSensorContact[i].toString()
                        } 
                        message = bedSensorContact ? "$app.label will work in limited mode when $devivesStr ${s > 1 ? "are" : "is"} closed and/or ${s > 1 ? "have" : "has"} been closed within 1 minute. Power saving options will not be active ${bedSensorType == "both" ? "Button supercedes this sensor":""}" : ""
                        paragraph formatText(message, "white", "grey")
                    }
                }
                if(bedSensorType == "button" || bedSensorType == "both")
                {
                    def s = bedSensorButton?.size() 
                    def i = 0
                    input "bedSensorButton", "capability.holdableButton", title: "When ${!bedSensorButton ? "this button is" : (s > 1 ? "these buttons are" : "this button is")} pushed, work in limited mode (hold or push again to cancel)", multiple: true, required: false, submitOnChange:true
                    input "bedSensorTimeLimit", "number", title: "Optional: return to normal operation after a certain amount of time", descripition: "Time in hours", submitOnChange:true
                    if(bedSensorButton)
                    {
                        for(s!=0;i<s;i++){
                            devivesStr = devivesStr.length() > 0 ? devivesStr + ", " + bedSensorButton[i].toString() : bedSensorButton[i].toString()
                        } 
                        if(bedSensorTimeLimit)
                        {
                            message = "Limited mode will be canceled after $bedSensorTimeLimit hours or after a new button event" //. Note that $devivesStr will not be able to cancel limited mode before time is out" 
                            paragraph formatText(message, "white", "grey") 
                        }
                        message = bedSensorButton ? "$app.label will operate in limited mode when $devivesStr ${s > 1 ? "have" : "has"} been pushed and canceled when held, double tapped or pushed again. Power saving options will not be active. ${bedSensorType == "both" ? "Note taht $bedSensorButton supercede $bedSensorContact":""}" : ""
                    }
                }
                paragraph formatText(message, "white", "grey")
                def bedDevice = bedSensorType == "contact" ? bedSensorContact : bedSensorButton
                input "setSpecialTemp", "bool", title: "Keep room at a preset temperature when in $bedDevice is ${bedSensorType == "contact" ? "closed" : "pushed"}", submitOnChange:true, defaultValue:false
                input "specialSubstraction", "bool", title: "Lower the current set point instead?", submitOnChange:true

                if(setSpecialTemp)
                {
                    app.updateSetting("specialSubstraction",[value:false,type:"bool"]) // foolproofing
                    input "specialTemp", "number", title: "Set the desired temperature", required: true
                }
                if(specialSubstraction)
                {
                    app.updateSetting("setSpecialTemp",[value:false,type:"bool"]) // foolproofing
                    input "substract", "number", title: "Substract this value to the current set point", required:true 
                }
            }
            input "doorsManagement", "bool", title: "When some doors are open, synchronise $thermostat with a thermostat from another room", defaultValue:false, submitOnChange:true
            if(doorsManagement)
            {
                input "doorsContacts", "capability.contactSensor", title: "select contact sensors", required:true, multiple:true, submitOnChange:true

                input "doorThermostat", "capability.thermostat", title: "select a thermostat from a different room", required:true, submitOnChange:true
                if(doorsContacts && doorThermostat)
                {
                    paragraph "when ${doorsContacts?.size()>1?"any of":""} ${doorsContacts} ${doorsContacts?.size()>1?"are":"is"} open, $thermostat will synchornise with $doorThermostat"
                }
                if(useAbedSensor)
                {
                    input "overrideSimpleMode", "bool", title: "This option overrides ${bedSensorContact && bedSensorButton ? "$bedSensorContact && $bedSensorButton" : bedSensorButton ? "$bedSensorButton" : "$bedSensorContact" }'s events"

                }
                if(doorsContacts)
                {
                    input "useDifferentSetOfSensors", "bool", title: "Use a different set of temperature sensors when ${doorsContacts} ${doorsContacts.size()>1?"are":"is"} open", submitOnChange:true
                    if(useDifferentSetOfSensors)
                    {
                        input "doorSetOfSensors", "capability.temperatureMeasurement", title: "Select your sensors", multiple:true, submitOnChange:true, required:true
                    }
                }
            }
        }
    }
}
def powersaving(){

    def title = formatText("POWER SAVING OPTIONS","white", "grey")

    def pageProperties = [
        name:       "powersaving",
        title:      title,
        nextPage:   "settings",
        install: false,
        uninstall: false
    ]

    dynamicPage(pageProperties) {

        section("Power saving modes")
        {
            input "powersavingmode", "mode", title: "Save power when in one of these modes", required: false, multiple: true, submitOnChange: true
        }
        section("Motion Management")
        {
            input "motionSensors", "capability.motionSensor", title: "Save power when there's no motion", required: false, multiple: true, submitOnChange:true

            if(motionSensors)
            {
                input "noMotionTime", "number", title: "after how long?", description: "Time in minutes"
                input "motionmodes", "mode", title: "Consider motion only in these modes", multiple: true, required: true 
            }  

            if(powersavingmode || motionSensors)
            {
                input "criticalcold", "number", title: "Set a critical low temperature", required: true
                input "criticalhot", "number", title: "Set a critical high temperature", required: true
            }
            input "fancirculate", "bool", title:"Run ${thermostat}'s fan circulation when contacts are open and temp is getting too high", defaultValue:true
        }

        section("Fans or Windows")
        {
            input "controlWindows", "bool", title: "Control some windows", submitOnChange:true
            if(controlWindows)
            {
                input "windows", "capability.switch", title: "Turn on those switches when home needs to cool down, wheather permitting", multiple:true, required: false, submitOnChange: true
                if(windows)
                {
                    input "windowsModes", "mode", title: "Select under which modes $windows can be operated", required:true, multiple:true

                    input "outsidetempwindowsH", "number", title: "Set a temperature below which it's ok to turn on $windows", required: true, submitOnChange: true
                    input "outsidetempwindowsL", "number", title: "Set a temperature below which it's NOT ok to turn on $windows", required: true, submitOnChange: true
                    if(outsidetempwindowsH && outsidetempwindowsL)
                    {
                        paragraph "If outside temperature is between ${outsidetempwindowsL}F & ${outsidetempwindowsH}F, $windows will be used to coold down your place instead of your AC"

                        input "operationTime", "bool", title: "${windows}' operation must stop after a certain time", defaultValue:false, submitOnChange:true
                        if(operationTime)
                        {
                            input "windowsDuration", "number", title: "Set minimum operation time", description: "time in seconds", required: false, submitOnChange:true
                            if(windowsDuration)
                            {
                                paragraph "<div style=\"width:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${app.name} will determine duration based on this value and outside temperature. The cooler it is outside, the shorter the duration (the closer the duration will be to the minimum you set here). Recommended value: 10 seconds</div>"
                            }
                            input "maxDuration", "number", title: "Set maximum operation time", description: "time in seconds", required: false, submitOnChange:true

                            input "customCommand", "text", title: "custom command to stop operation (default is 'off()')", required: false, submitOnChange:true

                            if(customCommand)
                            {
                                def cmd = customCommand.contains("()") ? customCommand.minus("()") : customCommand
                                def windowsCmds = windows.findAll{it.hasCommand("${cmd}")}
                                boolean cmdOk = windowsCmds.size() == windows.size()
                                if(!cmdOk)
                                {
                                    paragraph "<div style=\"width:102%;background-color:#1C2BB7;color:red;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">SORRY, THIS COMMAND $customCommand IS NOT SUPPORTED BY AT LEAST ONE OF YOUR DEVICES! Maybe a spelling error? In any case, make sure that each one of them support this command</div>"

                                }
                                else
                                {
                                    paragraph """<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">The command $customCommand is supported by all your devices!</div> """

                                }
                            }

                        }
                    }

                    if(doorsContacts && doorThermostat)
                    {
                        paragraph """In the 'contact sensors' settings you opted for for synchronizing your thermostat's operations 
with another thermostat's when some door contacts are open. Do you want to also control the windows from this other thermostat's room?"""
                        input "useOtherWindows", "bool", title: "Also control these windows when $doorsContacts are open", submitOnChange:true, defaultValue:false
                        if(useOtherWindows)
                        {
                            input "otherWindows", "capability.switch", title: "Select your windows", required:true, multiple:true
                        }

                    }
                }
            }
        }
    }
}
def pageNameUpdate(){
    if(atomicState.paused)
    {
        log.debug "new app label: ${app.label}"
        while(app.label.contains(" (Paused) "))
        {
            app.updateLabel(app.label.minus("(Paused)" ))
        }
        app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
    }
    else if(app.label.contains("(Paused)"))
    {
        app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
        while(app.label.contains(" (Paused) ")){app.updateLabel(app.label.minus("(Paused)" ))}
        log.debug "new app label: ${app.label}"
    }
    if(atomicState.paused == true)
    {
        atomicState.button_name = "resume"
        log.debug "button name is: $atomicState.button_name"
    }
    else 
    {
        atomicState.button_name = "pause"
        log.debug "button name is: $atomicState.button_name"
    }
}

/************************************************INITIALIZATION*************************************************/
def installed() {
    logging("Installed with settings: ${settings}")

    initialize()
}
def updated() {

    log.info "${app.name} updated with settings: $settings"

    unsubscribe()
    unschedule()
    initialize()
}
def initialize(){

    if(enabledebug)
    {
        log.warn "debug enabled"      
        atomicState.EnableDebugTime = now()
        runIn(1800,disablelogging)
        descriptiontext "debug will be disabled in 30 minutes"
    }
    else 
    {
        log.warn "debug disabled"
    }
    atomicState.paused = false
    atomicState.restricted = false
    atomicState.lastNeed = "cool"
    atomicState.antifreeze = false
    atomicState.buttonPushed = false
    atomicState.setpointSentByApp = false
    atomicState.openByApp = true
    atomicState.closedByApp = true
    atomicState.lastPlay = atomicState.lastPlay != null ? atomicState.lastPlay : now()
    atomicState.overrideTime = now() as long
        atomicState.resendAttempt = now() as long
        atomicState.offAttempt = now() as long

        atomicState.lastMotionEvent = now() as long
        atomicState.lastNotification = now() as long
        atomicState.motionEvents = 0
    atomicState.lastTimeBsTrue = now() as long

        logging("subscribing to events...")

    //subscribe(location, "mode", ChangedModeHandler) 
    subscribe(thermostat, "temperature", temperatureHandler)
    if(sensor)
    {
        int i = 0
        int s = sensor.size()
        for(s != 0; i<s;i++)
        {
            subscribe(sensor[i], "temperature", temperatureHandler)
        }
    }
    if(dimmer)
    {
        subscribe(dimmer, "level", dimmerHandler)
    }
    descriptiontext "subscribed $dimmer to dimmerHandler"
    subscribe(thermostat, "heatingSetpoint", setPointHandler)
    subscribe(thermostat, "coolingSetpoint", setPointHandler)
    subscribe(thermostat, "thermostatMode", thermostatModeHandler)

    descriptiontext "subscribed ${thermostat}'s coolingSetpoint to setPointHandler"
    descriptiontext "subscribed ${thermostat}'s heatingSetpoint to setPointHandler"
    descriptiontext "subscribed ${thermostat}'s thermostatMode to thermostatModeHandler"

    if(sync && thermostatB)
    {
        int i = 0
        int s = thermostatB.size()
        for(s!= 0; i<s; i++)
        {
            subscribe(thermostatB[i], "heatingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "coolingSetpoint", setPointHandler)
            subscribe(thermostatB[i], "thermostatMode", thermostatModeHandler)
            descriptiontext "subscribed ${thermostatB[i]}'s thermostatMode to thermostatModeHandler"
            descriptiontext "subscribed ${thermostatB[i]}'s heatingSetpoint to setPointHandler"
            descriptiontext "subscribed ${thermostatB[i]}'s coolingSetpoint to setPointHandler"
        }
    }    

    subscribe(location, "mode", modeChangeHandler)

    if(windows && controlWindows)
    {
        if(windows.every{element -> element.hasCapability("ContactSensor")})
        {
            subscribe(windows, "contact", contactHandler)
            subscribe(windows, "contact", windowsHandler)
            log.debug "$windows subscribed to contactHandler()"
        }
    }
    if(bedSensorContact)
    {        
        subscribe(bedSensorContact, "contact", bedSensorContactHandler)
    }
    if(bedSensorButton)
    {
        subscribe(bedSensorButton, "held", holdableButtonHandler)   
        subscribe(bedSensorButton, "pushed", holdableButtonHandler)   

    }
    if(buttonPause)
    {
        subscribe(buttonPause, "doubleTapped", doubleTapableButtonHandler) 
        log.info "$buttonPause subscribed to doubleTapableButtonHandler"
    }
    if(WindowsContact)
    {
        subscribe(WindowsContact, "contact", contactHandler)
    }
    if(motionSensors)
    {
        subscribe(motion, "motion", motionHandler)
    }

    if(polldevices)
    {
        schedule("0 0/5 * * * ?", Poll)
    }

    schedule("0 0/1 * * * ?", mainloop)


    descriptiontext "END OF INITIALIZATION"

}

/************************************************EVT HANDLERS***************************************************/
def modeChangeHandler(evt){
    log.debug "$evt.name is now $evt.value"

    //if(evt.value in powersavingmode || !Active())
    //{
    //atomicState.openByApp = true
    //atomicState.closedByApp = true  
    //}

    mainloop()
}
def appButtonHandler(btn) {
    if(location.mode in restricted){
        descriptiontext "location in restricted mode, doing nothing"
        return
    } 
    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused
        logging("atomicState.paused = $atomicState.paused")
        if(atomicState.paused)
        {
            log.debug "unsuscribing from events..."
            unsubscribe()  
            log.debug "unschedule()..."
            unschedule()
            break
        }
        else
        {
            updated()            
            break
        }
        case "update":
        atomicState.paused = false
        updated()
        break
        case "run":
        if(!atomicState.paused) mainloop()
        break
        case "poll":
        Poll()
        break

    }
}
def contactHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        log.info "$evt.device is $evt.value"
        atomicState.lastOpenEvt = now() 
        mainloop()
    }
}
def motionHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        descriptiontext "$evt.device is $evt.value"
        mainloop()
    }

}
def temperatureHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        logging("$evt.device returns ${evt.value}F")
        mainloop()
    }
}
def bedSensorContactHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        log.info "$evt.device is $evt.value"

        atomicState.lastBSeventStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        if(now() - atomicState.lastBSevent > 60000) // prevent false positives due to floating state of the bed sensor due to the mattress's weight (still working on this...)
        {
            atomicState.ButtonSupercedes = false // if there's a new contact event, this means it is working as expected, therefore no need for the button to supercede the sensor
        }

        // this boolean remains false until next button event
        atomicState.lastBSevent = now()
        mainloop()
    }
}
def holdableButtonHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        log.debug "BUTTON EVT $evt.device $evt.name $evt.value"

        if(evt.name in ["pushed", "held"])
        {
            atomicState.ButtonSupercedes = true // this condition becomes true as soon as the user pushed or held the button so it supercedes the sensor again
            // the sensor will retrieve its precedence as soon as it parses a new event beyond 60 seconds since its last event 
        } 

        if(evt.name == "pushed" && !atomicState.buttonPushed) // if pushed and not pushed a second time
        {
            atomicState.buttonPushed = true
        }
        else // if held or double tapped or pushed a second time
        {
            atomicState.buttonPushed = false
        }

        atomicState.lastButtonEvent = now()
        mainloop()
    }
    else
    {
        log.warn "App is paused, button event was ignored"
    }
}
def doubleTapableButtonHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        log.debug "BUTTON EVT $evt.device $evt.name $evt.value"

        if(evt.name == "doubleTapped")
        {
            atomicState.paused = !atomicState.paused 
            def message = atomicState.paused ? "APP PAUSED BY DOUBLE TAP" : "APP RESUMED BY DOUBLE TAP"
            log.warn message
            if(buttonTimer && atomicState.paused) {
                log.debug "App will resume in $buttonTimer minutes"
                runIn(buttonTimer, updated)
            }
        } 
    }
}
def thermostatModeHandler(evt){

    if(location.mode in restricted){
        descriptiontext "location in restricted mode, doing nothing"
        return
    } 
    log.debug "--------- $evt.device set to $evt.value"

    if(evt.value == "auto" && autoOverride)
    {
        atomicState.overrideTime = now()  
        atomicState.override = true
        return
    }
    else
    {
        atomicState.override = false
    }

    if(!atomicState.restricted && !atomicState.paused){
        logging """$evt.device $evt.name $evt.value
sync ? $sync
thermostatB: $thermostatB

"""
        if(sync && thermostatB)
        {
            int i = 0
            int s = thermostatB.size()

            if(!ignoreTherModes)
            {
                if("$evt.device" == "$thermostat")
                {
                    //log.warn "case AM"
                    def cmd = "set${evt.name.capitalize()}"
                    for(s!=0; i<s; i++)
                    {
                        thermostatB[i]."${cmd}"(evt.value)
                        descriptiontext "${thermostatB[i]} $cmd $evt.value"
                    }
                }
                else if(thermostatB.find{it.toString() == "$evt.device"})
                {
                    //log.warn "case BM"
                    def cmd = "set${evt.name.capitalize()}"
                    thermostat."${cmd}"(evt.value)
                    descriptiontext "$thermostat $cmd $evt.value"
                }
            }
            else
            {
                descriptiontext "ignoring operating mode sync at user request (syncing set points only)"
            }
        }
    }
}
def setPointHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        logging """$evt.device $evt.name $evt.value
sync ? $sync
thermostatB: $thermostatB

"""
        atomicState.lastSetPoint = atomicState.lastSetPoint != null ? atomicState.lastSetPoint : evt.value.toInteger()

        if(sync && thermostatB)
        {
            def cmd = "set${evt.name.capitalize()}"
            int i = 0
            int s = thermostatB.size()


            logging """
thermostat = $evt.device
evt.value = $evt.value
evt.name = $evt.name
${thermostat?.currentValue(evt.name) != "$evt.value"}

KEEP FOR FUTURE REFERENCE!
thermostatB current set point: ${thermostatB[0].currentValue(evt.name)} = $evt.value
true? ${thermostatB[0].currentValue(evt.name) == evt.value.toInteger()}
any found with same current value: ${thermostatB?.any{it -> it.currentValue(evt.name) == evt.value.toInteger()}} 


"""

            if("$evt.device" == "$thermostat")
            {
                //log.warn "case ASP"
                for(s!=0; i<s; i++)
                {
                    thermostatB[i]."${cmd}"(evt.value)
                    descriptiontext "${thermostatB[i]} $cmd $evt.value"
                }
            }
            if(thermostatB.find{it.toString() == "$evt.device"})
            {
                //log.warn "case BSP"
                thermostat."${cmd}"(evt.value)
                descriptiontext "$thermostat $cmd $evt.value"
                //atomicState.setpointSentByApp = true
            }
            //return // must not set atomicState.setpointSentByApp back to false in this case
        }
        if(!atomicState.setpointSentByApp)
        {
            descriptiontext "new $evt.name is $evt.value---------------------------------------"

            if(method == "auto" && !atomicState.setpointSentByApp)
            {
                log.debug "Updating automatic settings based on new thermostat set point input $evt.value"



                //updateValues((int)Integer.parseInt(evt.value.trim()))
                updateValues(evt.value.toInteger())


            }

            def currDim = dimmer?.currentValue("level")
            def thermMode = thermostat?.currentValue("thermostatMode")

            // this will be true only if thermostat is heating or cooling; therefore, dimmer won't be adjusted if off 
            // using atomicState.lastNeed == "heat" / "cool" seemed to allow exceptions... 
            boolean correspondingMode = (evt.name == "heatingSetpoint" && thermMode == "heat") || (evt.name == "coolingSetpoint" && thermMode == "cool")

            def message = """
atomicState.setpointSentByApp = $atomicState.setpointSentByApp
Current $dimmer value is $currDim
atomicState.lastNeed = $atomicState.lastNeed   
evt.value = $evt.value   
"""
            logging "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">$message</div>"

            boolean bedsensorclosed = bedSensorIsClosed()
            def desired = getDesired(bedsensorclosed)

            def inside = getInsideTemp()
            atomicState.inside = atomicState.inside != null ? atomicState.inside : inside
            def needData = getNeed(desired, bedsensorclosed, inside)
            def need = needData[1]
            def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter


            // make sure the therm event is same as current need
            // as to not apply a value from a differentiated thermostat mode (heat set to 75 will modify coolingSP and then trigger an event)

            if(correspondingMode && currDim != evt.value) 
            {
                if(method == "dimmer")
                {
                    //runIn(3, setDimmer, [data:evt.value.toInteger()]) 
                    log.debug "SETTING $dimmer to $evt.value"
                    setDimmer(evt.value.toInteger())
                }
            }
            if(!correspondingMode)
            {
                descriptiontext "not updating dimmer because this is $evt.name and current mode is $thermMode"
            }
            if(currDim == evt.value)
            {
                descriptiontext "dimmer level ok (${dimmer?.currentValue("level")} == ${evt.value}"
            }
        }
        else
        {
            log.warn "event generated by this app, doing nothing"
        }
        atomicState.setpointSentByApp = false // always reset this static/class variable after calling it
        atomicState.lastSetPoint = evt.value.toInteger()
        //mainloop() // prevent feedback loops so both dimmer and thermosta set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def dimmerHandler(evt){

    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        descriptiontext "new dimmer level is $evt.value"
        if(method == "auto" && !atomicState.setpointSentByApp)
        {
            log.debug "Updating automatic settings based on new dimmer level"

            def outside = outsideTemp.currentValue("temperature").toInteger()
            def outsideThreshold = getOutsideThershold()
            def needSituation = outside < outsideThreshold ? "heat" : "cool"
            def newRefTemp = outside


            updateValues(evt.value.toInteger())

        }
        else if(!atomicState.setpointSentByApp)
        {
            log.info "NOT AUTO METHOD"
        }
        else if(atomicState.setpointSentByApp)
        {
            log.info "command coming from this app, skipping"
        }

        atomicState.setpointSentByApp = false // always reset this static/class variable after calling it

        //mainloop() // prevent feedback loops so both dimmer and thermostat set points can be modified. Changes will be made on next scheduled loop or motion events
    }
}
def updateValues(evtVal){
    def outside = outsideTemp.currentValue("temperature").toInteger()
    def outsideThreshold = getOutsideThershold()
    def needSituation = outside < outsideThreshold ? "heat" : "cool"
    def newRefTemp = outside
    def newDesiredVar = Math.abs(evtVal - outside)
    app.updateSetting("desiredVar",[value:newDesiredVar,type:"number"]) // update this setting
    app.updateSetting("refTemp",[value:newRefTemp,type:"number"]) // we also need to update the new reference temperature in order to modify the linear equation 
    log.info """
newDesiredVar = $newDesiredVar
outside = $outside
needSituation = $needSituation
atomicState.lastSetPoint = $atomicState.lastSetPoint
new setpoint = $evtVal
"""          
    // calculate an absolute value of the difference between old and new value
    def absDifference = Math.abs(atomicState.lastSetPoint - evtVal)
    // update min and max auto cool values
    if(needSituation == "cool")
    {        
        def val = null
        //  if(evtVal < atomicState.lastSetPoint)  // if need cooler room, lower min
        //  {
        val = minAutoCool - absDifference
        log.debug "New val to add or substract to current thresholds = $absDifference"
        val = val < 16 ? 16 : val // don't go too low
        // so we lower this setting's value a bit
        app.updateSetting("minAutoCool",[value:val,type:"number"])
        log.debug "minAutoCool is now $minAutoCool"
        // }
        // else { // if need warmer room, raise max
        val = maxAutoCool + absDifference
        val = val > 27 ? 27 : val // don't go too high
        app.updateSetting("maxAutoCool",[value:val,type:"number"])
        log.debug "maxAutoCool is now $maxAutoCool"
        // }
    }
    // update min and max auto heat values
    if(needSituation == "heat")
    {
        def val = null
        //    if(evtVal < atomicState.lastSetPoint)  // if need cooler room, lower min
        //   {
        val = minAutoHeat - absDifference
        val = val < 16 ? 16 : val // don't go too low
        // so we lower this setting's value a bit
        app.updateSetting("minAutoHeat",[value:val,type:"number"])
        log.debug "minAutoHeat is now $minAutoHeat"
        //  }
        //  else { // if need warmer room, raise max
        val = maxAutoHeat + absDifference
        val = val > 27 ? 27 : val // don't go too high
        app.updateSetting("maxAutoHeat",[value:val,type:"number"])
        log.debug "maxAutoHeat is now $maxAutoHeat"
        // }

    }
}
def outsideThresDimmerHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        descriptiontext "*********** Outside threshold value is now: $evt.value ***********"
        //mainloop()
    }
}
def windowsHandler(evt){
    if(!atomicState.paused){
        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        } 
        log.debug "$evt.device is $evt.value"
        boolean doorContactsAreOpen = doorsContactsAreOpen()

        if(evt.value == "open")
        {
            /* 
if(doorsManagement && doorContactsAreOpen)
{
atomicState.otherWindowsOpenByApp = true
otherWindows?.on()
}
*/
            boolean openMore = !atomicState.widerOpeningDone && atomicState.insideTempHasIncreased
            if(!openMore){
                atomicState.lastOpeningTime = now()
            }
            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

        }
        else if(evt.value == "closed")
        {
            /*
if(doorsManagement && doorContactsAreOpen)
{
atomicState.otherWindowsClosedByApp = true
otherWindows?.off()
}
*/
            atomicState.lastClosingTime = now()
            atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
        }
    }
}

/************************************************MAIN functions*************************************************/
def mainloop(){

    if(!atomicState.paused)
    {

        if(location.mode in restricted){
            descriptiontext "location in restricted mode, doing nothing"
            return
        }    

        boolean bedsensorclosed = bedSensorIsClosed()
        boolean motionActive = Active() || bedSensorContactClosed
        boolean contactClosed = !contactsAreOpen()
        boolean doorContactsAreOpen = doorsContactsAreOpen()
        int desired = getDesired(bedsensorclosed)
        def inside = getInsideTemp()
        def outside = outsideTemp.currentValue("temperature").toInteger()
        descriptiontext "outside temperature is $outside"
        def needData = getNeed(desired, bedsensorclosed, inside)
        def need = needData[1]

        def currSP = thermostat?.currentValue("thermostatSetpoint").toInteger()
        //log.warn "--- $currSP"
        def thermMode = thermostat?.currentValue("thermostatMode")
        logging("need is needData[1] = $need")
        def cmd = "set"+"${needData[0]}"+"ingSetpoint" // "Cool" or "Heat" with a capital letter

        if(atomicState.antifreeze)
        {
            log.warn "ANTI FREEZE HAS BEEN TRIGGERED"
        }
        // antifreeze precaution (runs after calling atomicState.antifreeze on purpose here)
        def backupSensorTemp = backupSensor ? backupSensor.currentValue("temperature").toInteger() : inside
        if(antifreeze){
            def safeVal = safeValue != null ? safeValue : criticalcold != null ? criticalcold : 19

            if(inside <= safeVal || backupSensorTemp <= safeVal){

                atomicState.antifreeze = true

                log.warn """$thermostat setpoint set to 72 as ANTI FREEZE VALUE
inside = $inside
safeValue = $safeVal
"""
                thermostat.setThermostatMode("heat")
                thermostat.setHeatingSetpoint(22)
                windows?.off() // make sure all windows linked to this instance are closed
                sendNotification()
                return
            }
            else if(atomicState.antifreeze)
            {
                atomicState.antifreeze = false
                log.trace "END OF ANTI FREEZE"
            }

        }
        else // mandatory anti freeze
        {
            def defaultSafeTemp = criticalcold == null ? 14 : criticalcold <= 14 ? criticalcold : 14 
            if(inside <= defaultSafeTemp || backupSensorTemp <= defaultSafeTemp){
                log.warn """ANTIFREEZE (DEFAULT) IS TRIGGERED: 
inside = $inside
backupSensorTemp = $backupSensorTemp
defaultSafeTemp = $defaultSafeTemp (is this user's criticalcold set temp ? ${criticalcold == null ? false : true}
"""
                windows?.off() // make sure all windows linked to this instance are closed
                thermostat.setThermostatMode("heat")
                thermostat.setHeatingSetpoint(22)
                atomicState.antifreeze = true
                sendNotification()
            }
            else
            {
                atomicState.antifreeze = false
            }
        }
        if(autoOverride && thermMode == "auto"){
            atomicState.override = true // wanted redundancy for the rare cases when evt handler failed
            def overrideDur = overrideDuration != null ? overrideDuration : 0
            def timeLimit = overrideDur * 60 * 60 * 1000
            def timeStamp = atomicState.overrideTime

            if(overrideDur != 0 && overrideDur != null)
            {
                if(now() - timeStamp > timeLimit)
                {
                    log.warn "END OF OVERRIDE, turning off $thermostat"
                    atomicState.override = false
                    thermostat.setThermostatMode("off")
                }
                else 
                {
                    log.warn "OVERRIDE - AUTO MODE - remaining time: ${getRemainTime(overrideDur, atomicState.overrideTime)}"
                }
            }
            else 
            {
                log.warn "OVERRIDE - APP PAUSED DUE TO AUTO MODE (no time limit)"
                return
            }
        }

        if(thermostat.currentValue("thermostatFanMode") == "on" && contactClosed && fancirculate && atomicState.fanOn){
            descriptiontext "Setting fan back to auto"
            thermostat.setThermostatFanMode("auto")
            atomicState.fanOn = false 
        }
        if(enabledebug && now() - atomicState.EnableDebugTime > 1800000){
            descriptiontext "Debug has been up for too long..."
            disablelogging() 
        }

        if(pw){
            logging("$pw power meter returns ${pw?.currentValue("power")}Watts")
        }
        if(!atomicState.override){

            logging"""
thermMode = $thermMode currSP = $currSP"
currSP != desired -> ${currSP != desired} -> ${currSP} != ${desired} 
"""

            virtualThermostat(need)

            def currentOperatingNeed = need == "cool" ? "cooling" : need == "heat" ? "heating" : need == "off" ? "idle" : "ERROR" 
            if(currentOperatingNeed == "ERROR"){log.error "currentOperatingNeed = $currentOperatingNeed"}
            logging """currentOperatingNeed = $currentOperatingNeed && need = $need
thermostat.currentValue("thermostatOperatingState") = ${thermostat.currentValue("thermostatOperatingState")}
${thermostat.currentValue("thermostatOperatingState") == currentOperatingNeed}
"""
            // control discrepancy when thermostat measures a temp equal to desired temp while it's not the case on the alternate sensor
            boolean thermTempDiscrepancy = need in ["cool", "heat"] ? thermostat.currentValue("temperature") == desired : false
            thermTempDiscrepancy = contactClosed && !doorContactsAreOpen ? thermTempDiscrepancy : false
            boolean OperatingStateOk = contactClosed && !doorContactsAreOpen ? thermostat.currentValue("thermostatOperatingState") in [currentOperatingNeed, "fanCirculate"] : true

            logging "thermTempDiscrepancy = $thermTempDiscrepancy currentOperatingNeed = $currentOperatingNeed | current state = ${thermostat.currentValue("thermostatOperatingState")}"
            descriptiontext "Operating State is Consistent: $OperatingStateOk"

            atomicState.lastSetTime = atomicState.lastSetTime != null ? atomicState.lastSetTime : now() + 31 * 60 * 1000

            atomicState.forceLimit = Math.abs(inside-desired) > 5 ? 20 : 5 // higher amount of attempts if bigger discrepancy         
            atomicState.forceAttempts = atomicState.forceAttempts != null ? atomicState.forceAttempts : 0
            boolean forceCommand = forceCommand ? (atomicState.forceAttempts < atomicState.forceLimit ? true : false) : false //
            forceCommand = forceCommand ? (need in ["cool", "heat"] && Math.abs(inside-desired) > 3 ? true : false) : false // 
            forceCommand = !forceCommand && forceCommand && Math.abs(inside-desired) >= 5 ? true : (forceCommand ? true : false) // counter ignored if forceCmd user decision is true and temp discrepancy too high: continue trying until temp is ok
            forceCommand = !forceCommand && !OperatingStateOk ? true : forceCommand // OperatingStateOk supercedes all other conditions
            forceCommand = contactClosed && !doorContactsAreOpen ? forceCommand : false // don't use this method when contacts are open, even door contacts


            if(!OperatingStateOk && thermTempDiscrepancy)
            {
                descriptiontext "$thermostat IS IDLE BECAUSE SETPOINT IS INCONSISTENT DUE TO ITS CURRENT TEMPERATURE"
            }

            logging "forceCommand ? $forceCommand atomicState.forceAttempts = $atomicState.forceAttempts | abs(inside-desired) = ${Math.abs(inside-desired).round(2)}"

            if(thermMode != need || forceCommand)
            {
                if(forceCommand && OperatingStateOk) {log.warn "FORCING CMD TO DEVICE BECAUSE temperature difference is TOO HIGH"}
                if(forceCommand && !OperatingStateOk && !thermTempDiscrepancy) {logging "FORCING CMD TO DEVICE BECAUSE current operating state is INCONSISTENT"}

                atomicState.forceAttempts += 1
                if(atomicState.forceAttempts >= forceLimit) { runIn(1800, resetCmdForce)} // after 5 attempts, stop and retry in half an hour to prevent z-wave cmds overflow onto the device

                //atomicState.lastSetTime =  5 * 60 * 1000 + 1 // for TESTS ONLY

                if(need != "off" || forceCommand || (need == "off" && (sensor || offrequiredbyuser)))
                {                
                    if((!OperatingStateOk || now() - atomicState.lastSetTime > 5 * 60 * 1000) || need == "off" || forceCommand)
                    {
                        thermostat.setThermostatMode(need) // set desired mode

                        // readjust setpoint if there's inconsistency
                        if(thermTempDiscrepancy)
                        {
                            if(need == "cool")
                            {
                                def temporarySetpoint = desired - 1
                                thermostat.setCoolingSetpoint(temporarySetpoint) 
                                log.warn "CHANGING COOLING SETPOINT TO $desired - 1 = ${temporarySetpoint} due to operation discrepancy"
                                atomicState.setpointSentByApp = true // prevent this from modifying the dimmer's and the desired value
                            }
                            else if(need == "heat")
                            {    
                                def temporarySetpoint = desired + 1
                                thermostat.setHeatingSetpoint(temporarySetpoint)
                                log.warn "CHANGING HEATING SETPOINT TO $desired + 1 = ${temporarySetpoint} due to operation discrepancy"
                                atomicState.setpointSentByApp = true // prevent this from modifying the dimmer's and the desired value
                            }

                        }


                        atomicState.lastSetTime = now()

                        if(need in ["cool", "heat"])
                        {
                            atomicState.lastSetTime = now() // prevent switching from heat to cool too frequently
                        }

                        logging("THERMOSTAT SET TO $need mode (587gf)")
                    }
                    else if(now() - atomicState.lastSetTime < 30 * 60 * 1000)
                    {
                        logging "THERMOSTAT CMD NOT SENT due to the fact that a cmd was already sent less than 5 minutes ago"
                    }

                    if(need == "off")
                    {
                        atomicState.offAttempt = now() as long

                            }
                }
                else 
                {
                    logging("THERMOSTAT stays in $thermMode mode")
                }

            }
            else if(need != "off")
            {
                logging("Thermostat already set to $need mode")
            }

            if(need != "off" && currSP.toInteger() != desired && !thermTempDiscrepancy)
            {
                atomicState.setpointSentByApp = true
                thermostat."${cmd}"(desired)   // set desired temp
                logging("THERMOSTAT SET TO $desired (564fdevrt)")
            }
            else if(need != "off" && !thermTempDiscrepancy)
            {
                logging("Thermostat already set to $desired")
            }
            else if(thermTempDiscrepancy)
            {
                log.warn "Skipping normal setpoint and thermostatMode management due to thermTempDiscrepancy = $thermTempDiscrepancy"   
            }

            if(pw)
            {
                atomicState.resendAttempt = atomicState.resendAttempt ? atomicState.resendAttempt : now()
                atomicState.offAttempt = atomicState.offAttempt ? atomicState.offAttempt : now()
                // here we manage possible failure for a thermostat to have received the z-wave/zigbee or http command
                long timeElapsedSinceLastResend = now() - atomicState.resendAttempt
                long timeElapsedSinceLastOff = now() - atomicState.offAttempt // when device driver returns state off while in fact signal didn't go through
                long threshold = 4 * 60 * 1000 // give it 4 minutes to kick in before attempting new request 
                boolean timeIsUp = timeElapsedSinceLastResend > threshold
                boolean timeIsUpOff = timeElapsedSinceLastOff > 30000
                boolean pwLow = pw?.currentValue("power") < 100 // below 100 watts we assume there's no AC compression nor resistor heat running
                logging("time since last Resend Attempt = ${timeElapsedSinceLastResend/1000} seconds & threshold = ${threshold/1000}sec")
                logging("time since last OFF Attempt = ${timeElapsedSinceLastOff/1000} seconds & threshold = ${30}sec")

                if(timeIsUp && pwLow && need != "off")
                {
                    descriptiontext "$app.label is resending ${cmd}(${desired}) due to inconsistency in power value"
                    atomicState.resendAttempt = now() as long
                        atomicState.setpointSentByApp = true
                    thermostat."${cmd}"(desired) // resend cmd
                }
                else if(timeIsUpOff && need == "off" && !pwLow && !doorsContactsAreOpen())
                {
                    log.warn("$thermostat should be off but still draining power, resending cmd")
                    atomicState.offAttempt = now() as long
                        thermostat.setThermostatMode("off")
                    thermostat.off()
                    Poll()
                }
                else if((!pwLow &&  need in ["heat", "cool"]) || (need == "off" && pwLow))
                {
                    logging("EVERYTHING OK")
                }
                else 
                {
                    logging("Auto Fix Should Kick in within time threshold")
                }
            }
        }
        else{
            descriptiontext("OVERRIDE MODE--------------")   
        }
    }
    else if(atomicState.restricted)
    {
        log.info "app in restricted mode, doing nothing"
    }
}
def sendNotification(){
    def message = "Temperature is too low at $thermostat, antifreeze is now active. Please make sure everything is ok"

    atomicState.lastNotification = atomicState.lastNotification != null ? atomicState.lastNotification : now()

    def dTime = 5*60*1000 // every 5 minutes

    if(now() - atomicState.lastNotification >= dTime || 1==1)
    {
        atomicState.lastNotification = now()

        def speakers = speaker ? buildDebugString(speaker) : ""
        def speeches = speech ? buildDebugString(speech) : ""
        def notifDevices = notification ? buildDebugString(notification) : ""

        def notifLogs = "${notification && speaker && speech ? "to ${notifDevices}, ${speakers}, ${speeches}" : notification && speaker ? "to ${notifDevices}, ${speakers}" : notification && speech ? "to ${notifDevices}, ${speechesspeeches}" : speaker && speech ? "to ${speakers}, ${speeches}" : speaker ? "to ${speakers}" : speech ? "to ${speeches}" : ""}" 

        def debugMessage = "message to be sent: '${message} ${notifLogs}" 

        descriptiontext formatText(debugMessage, "white", "red")

        if(notification)
        {
            notification.deviceNotification(message)
        }
        else
        {
            log.info "User did not select any text notification device"
        }
        if(speaker || speech)
        {
            if(speaker)
            {
                if(initializeDevices)
                {
                    int i = 0
                    int s = speaker.size()
                    def device 
                    for(s!=0;i!=s;i++)
                    {
                        device = speaker[i]
                        if(device.hasCommand("initialize"))
                        {
                            log.debug "Initializing $device (speaker)"
                            device.initialize()
                            log.debug "wainting for 1 second"
                            pauseExecution(1000)
                        }
                    }
                }
                speaker.playTextAndRestore(message, volumeLevel.toInteger())
            }
            if(speech)
            {
                if(initializeDevices)
                {
                    int i = 0
                    int s = speech.size()
                    def device 
                    for(s!=0;i!=s;i++)
                    {
                        device = speech[i]
                        if(device.hasCommand("initialize"))
                        {
                            log.debug "Initializing $device (speech)"
                            device.initialize()
                            log.debug "wainting for 1 second"
                            pauseExecution(1000)
                        }
                    }
                }
                speech.speak(message)    
            }
        }
    }
}
def buildDebugString(deviceList){
    def devices = ""
    int i = 0 
    int s = deviceList.size()
    if(s != 0) { 

        for(s!=0; i!=s; i++)
        {
            devices += "${deviceList[i]}, "   
        }

    }
    return devices
}
def resetCmdForce(){
    log.warn "Resetting forceCommand counter"
    atomicState.forceAttempts = 0   
}
def setDimmer(int val){

    if(dimmer)
    {
        atomicState.setpointSentByApp = true
        dimmer.setLevel(val) 
        descriptiontext "$dimmer set to $val BY THIS APP"
    }
}
def virtualThermostat(need){
    if(heater || heatpump || addLowTemp)
    {
        def outsideTemperature = outsideTemp?.currentValue("temperature") // only needed if electric heater here
        def lowTemperature = lowtemp ? lowtemp : 4
        boolean heaterAsAdditional = addLowTemp && outsideTemperature < lowTemperature.toInteger()
        logging("heaterAsAdditional = $heaterAsAdditional")
        if(need == "heat" || (need == "heat" && heaterAsAdditional))
        {
            boolean powercap = heater?.hasAttribute("power")
            logging("is heater power meter capable? $powercap")
            boolean powerok = powercap ? (heater?.currentValue("power") > 100) : true
            logging "$heater power consumption is ${powerok ? "ok" : "not as expected"} ${powercap ? "${heater?.currentValue("power")}watts" : ''}"
            if(heater?.currentValue("switch") != "on" || !powerok)
            {
                logging "Turning $heater on"
                heater?.on()   
            }
            else if(heater?.currentValue("switch") == "on")
            {
                logging("$heater already off")
            }                        
        }
        else 
        {
            if(heater?.currentValue("switch") != "off")
            {
                logging("Turning $heater off")
                heater?.off()
            }
            else 
            {
                logging("$heater already off")
            }
        }
    }
    if(cooler)
    {
        if(need == "cool"){
            logging "turning $cooler on"
            cooler?.on()
        }
        else {
            logging "turning $cooler off"
            cooler?.off()
        }
    }
}
def windowsManagement(desired, bedSensorContactClosed, inside, outsideTemperature, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh){

    if(controlWindows && windows && !bedSensorContactClosed && !atomicState.override)
    {
        def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp
        boolean tooHumid = humidity >= 90 ? true : humidity >= humThres 
        boolean contactCapable = windows.any{it -> it.hasCapability("ContactSensor")}//?.size() == windows.size() 
        boolean someAreOff =  contactCapable ? (windows.findAll{it?.currentValue("contact") == "closed"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "off"}?.size() > 0)
        boolean someAreOpen = contactCapable ? (windows.findAll{it?.currentValue("contact") == "open"}?.size() > 0) : (windows.findAll{it?.currentValue("switch") == "on"}?.size() > 0)
        boolean withinRange = outsideTemperature < outsidetempwindowsH && outsideTemperature > outsidetempwindowsL // stric temp value

        boolean outsideWithinRange = withinRange && !tooHumid // same as withinRange but with humidity

        atomicState.lastOpeningTime = atomicState.lastOpeningTime != null ? atomicState.lastOpeningTime : now() // make sure value is not null
        atomicState.outsideTempAtTimeOfOpening = atomicState.outsideTempAtTimeOfOpening  != null ? atomicState.outsideTempAtTimeOfOpening : outsideTemperature // make sure value is not null
        boolean outsideTempHasDecreased = outsideTemperature < atomicState.outsideTempAtTimeOfOpening - swing // serves mostly to reset opening time stamp
        atomicState.outsideTempAtTimeOfOpening = outsideTempHasDecreased ? outsideTemperature : atomicState.outsideTempAtTimeOfOpening // if outsideTempHasDecreased true, reset outsidetemAtTimeOfOpening stamp so to use outsideTempHasDecreased only once 
        atomicState.lastOpeningTime = outsideTempHasDecreased ? now() : atomicState.lastOpeningTime // reset opening time stamp if it got cooler outside, allowing more time to cool the room

        atomicState.insideTempAtTimeOfOpening = atomicState.insideTempAtTimeOfOpening ? atomicState.insideTempAtTimeOfOpening : inside // make sure value is not null
        boolean insideTempHasIncreased = inside > atomicState.insideTempAtTimeOfOpening + swing // serves for windows wider opening ONLY
        atomicState.insideTempHasIncreased = insideTempHasIncreased
        atomicState.widerOpeningDone = (atomicState.widerOpeningDone != null) ? atomicState.widerOpeningDone : (atomicState.widerOpeningDone = false) // make sure value is not null
        boolean openMore = !atomicState.widerOpeningDone && insideTempHasIncreased && someAreOpen

        boolean insideTempIsHopeLess = inside > atomicState.insideTempAtTimeOfOpening + 2 && atomicState.widerOpeningDone

        double lastOpeningTime = (now() - atomicState.lastOpeningTime) / 1000 / 60 
        lastOpeningTime = lastOpeningTime.round(2)
        boolean openSinceLong = lastOpeningTime > 15.0 && someAreOpen // been open for more than 15 minutes

        atomicState.lastClosingTime = atomicState.lastClosingTime ? atomicState.lastClosingTime : (atomicState.lastClosingTime = now()) // make sure value is not null
        double lastClosingTime = (now() - atomicState.lastClosingTime) / 1000 / 60 
        lastClosingTime = lastClosingTime.round(2)
        boolean closedSinceLong = lastClosingTime > 10.0 && someAreClosed // been open for more than 30 minutes

        boolean tooColdInside = inside <= desired - 4
        //closing error management for safety, if cmd didn't go through for whatever reason and temp went too low, force close the windows
        boolean exception = someAreOpen && ((atomicState.closedByApp && now() - lastClosingTime > 30 && tooColdInside) || (!outsideWithinRange && tooColdInside))
        long elapsed = now() - lastClosingTime
        def elapsedseconds = elapsed/1000
        def elapsedminutes = elapsed/1000/60
        if(exception) {log.warn "$windows still open! EMERGENCY CLOSING WILL BE ATTEMPTED"}


        //(inside > desired + (swing * 2) && openSinceLong) -> give it a chance to cool down the place
        boolean enoughTimeBetweenOpenAndClose = ((now() - atomicState.lastOpeningTime) / 1000 / 60) > 10.0 || inside < desired - swing //-> give it a chance to cool down the place
        boolean enoughTimeBetweenCloseAndOpen = ((now() - atomicState.lastClosingTime) / 1000 / 60) > 60.0 //-> don't reopen too soon after closing

        boolean needToClose = (enoughTimeBetweenOpenAndClose && ((inside > desired + (swing * 3) && openSinceLong) || inside < desired - swing || insideTempIsHopeLess)) || !outsideWithinRange
        boolean needToOpen = (enoughTimeBetweenCloseAndOpen && (inside > desired + swing && !needToClose)) && outsideWithinRange //|| amplitudeTooHigh) // timer ok, too hot inside + within range (acounting for humidity) and no discrepency

        //if need = cool while it's clearly cold outside, rather open windows than use AC
        boolean needToOpenForcedToTrue = false
        //needToOpen = !needToOpen && !tooHumid && needCool && outsideTemperature <= desired - 4 && enoughTimeBetweenCloseAndOpen ? true : needToOpen
        //needToOpenForcedToTrue = needToOpen 
        /*logging """
${!needToOpen}
$needCool
${outsideTemperature <= desired - 4}
$enoughTimeBetweenCloseAndOpen
${!needToOpen && needCool && outsideTemperature <= desired - 4 && enoughTimeBetweenCloseAndOpen}"""
*/

        if(INpwSavingMode)
        {
            needToOpen = outsideWithinRange && inside > criticalcold && inside < criticalhot  // in power saving mode open windows if within range and inside not too hot nor too cold
        }



        logging """<div style=\"width:102%;background-color:green;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">
**********************WINDOWS************************
inWindowsModes = $inWindowsModes
$windows ${!contactCapable ? "${(windows.size() > 1) ? "have":"has"} contact capability" : "${(windows.size() > 1) ? "don't have":"doesn't have"} contact capability"}
closed: ${windows.findAll{it?.currentValue("contact") == "closed"}}
Open: ${windows.findAll{it?.currentValue("contact") == "open"}}
atomicState.openByApp = $atomicState.openByApp
atomicState.closedByApp = $atomicState.closedByApp
withinRange (stritcly): $withinRange
humidity >= humThres  : ${humidity >= humThres}
outsideWithinRange = $outsideWithinRange [range: $outsidetempwindowsL <> $outsidetempwindowsH] ${tooHumid ? "Too humid" : ""}
insideTempHasIncreased = $insideTempHasIncreased
atomicState.outsideTempAtTimeOfOpening = $atomicState.outsideTempAtTimeOfOpening
atomicState.insideTempAtTimeOfOpening = $atomicState.insideTempAtTimeOfOpening
insideTempIsHopeLess = $insideTempIsHopeLess ${insideTempIsHopeLess ? "temp went from: $atomicState.outsideTempAtTimeOfOpening to $inside" : ""}
amplThreshold = $amplThreshold
someAreOff = $someAreOff
someAreOpen = $someAreOpen
last time windows were OPEN = at $atomicState.lastOpeningTimeStamp ${lastOpeningTime < 2 ? "less than 1 minute ago" : (lastOpeningTime < 60 ? "${lastOpeningTime} minutes ago" : (lastOpeningTime < 60*2 ? "${(lastOpeningTime/60).round(2)} hour ago" : "${(lastOpeningTime/60).round(2)} hours ago"))}
last time windows were CLOSED = $atomicState.lastClosingTimeStamp ${lastClosingTime < 2 ? "less than 1 minute ago" : (lastClosingTime < 60 ? "${lastClosingTime} minutes ago" : (lastClosingTime < 60*2 ? "${(lastClosingTime/60).round(2)} hour ago" : "${(lastClosingTime/60).round(2)} hours ago"))}
humThres = ${humThres}
humidity = ${humidity}%
tooHumid = $tooHumid
openMore = $openMore
inside > desired + (swing * 2) : ${inside > desired + (swing * 2)}
inside > desired + swing : ${inside > desired + swing}
inside < desired - swing       : ${inside < desired - swing}
enoughTimeBetweenOpenAndClose : $enoughTimeBetweenOpenAndClose
enoughTimeBetweenCloseAndOpen : $enoughTimeBetweenCloseAndOpen
lastOpeningTime = $lastOpeningTime minutes ago ${outsideTempHasDecreased ? "value was reset to 0 because outsideTempHasDecreased = true (outsideTempHasDecreased = $outsideTempHasDecreased" : ""}
lastClosingTime = $lastClosingTime minutes ago
openSinceLong = $openSinceLong
temperature at last window opening = $atomicState.outsideTempAtTimeOfOpening
now() = ${now()}
atomicState.lastOpeningTime = $atomicState.lastOpeningTime 
atomicState.outsideTempAtTimeOfOpening = $atomicState.outsideTempAtTimeOfOpening  
atomicState.widerOpeningDone = $atomicState.widerOpeningDone
atomicState.lastNeed = $atomicState.lastNeed

needToOpen = $needToOpen
needToClose = $needToClose


*****************************************************
</div>
"""
        def causeClosing = "${needToClose ? "WINDOWS CLOSED OR CLOSING BECAUSE: ${enoughTimeBetweenOpenAndClose && inside > desired + (swing * 2) && openSinceLong ? "enoughTimeBetweenOpenAndClose && inside > desired + (swing * 2) && openSinceLong" : inside < desired - swing ? "inside < desired - $swing" : !outsideWithinRange ? "!outsideWithinRange" : insideTempIsHopeLess ? "insideTempIsHopeLess" : !someAreOpen ? "Already closed" : atomicState.lastNeed == "heat" ? "atomicState.lastNeed = heat" : "FIRE THE DEVELOPER IF THIS MESSAGE SHOWS UP"}":""}"

        if(needToClose && !needToOpenForcedToTrue){logging "${formatText(causeClosing, white, grey)}"}
        if(needToOpenForcedToTrue){logging "needToOpenForcedToTrue set to $needToOpenForcedToTrue to prevent useless use of AC"}

        if(inWindowsModes || exception){

            def time = getWindowsTimeOfOperation(outsideTemperature)

            if(needToOpen) // outsideWithinRange and humidity level are accounted for in needToOpen boolean, unless in power saving mode
            {
                descriptiontext "using $windows INSTEAD OF AC"

                if(someAreOff || openMore)
                {
                    if(openMore) {
                        atomicState.widerOpeningDone = true
                        unschedule(stop)
                    }
                    if(atomicState.closedByApp || (openMore && atomicState.openByApp))
                    {
                        def message = "opening $windows"
                        windows.on()
                        if(doorsManagement && doorContactsAreOpen)
                        {
                            atomicState.otherWindowsOpenByApp = true
                            otherWindows?.on()
                        }
                        need0 = "off"
                        need1 = "off"
                        thermostat.setThermostatMode("off")
                        if(!openMore)
                        {
                            atomicState.lastOpeningTime = now()
                            atomicState.lastOpeningTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                            atomicState.outsideTempAtTimeOfOpening = outsideTemperature
                            atomicState.insideTempAtTimeOfOpening = inside
                        }
                        atomicState.openByApp = true
                        atomicState.closedByApp = false

                        if(operationTime && !openMore && !INpwSavingMode) // if openMore or INpwSavingMode ignore stop() and open in full
                        {
                            runIn(time, stop)
                            message += " for a duration of $time seconds"
                        }
                        log.warn message

                    }
                    else
                    {
                        descriptiontext "$windows were not closed by this app"
                    }
                }
                else
                {
                    descriptiontext "$windows already open"
                }
            }
            else if(someAreOpen && needToClose)
            {
                if((atomicState.openByApp) || exception)
                {
                    if(exception) { log.warn "EXCEPTION CLOSING" }
                    log.warn "closing $windows"
                    unschedule(stop)
                    atomicState.lastClosingTime = now() 
                    atomicState.lastClosingTimeStamp = new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose
                    atomicState.widerOpeningDone = false // simple value reset
                    windows.off()
                    if(exception) {
                        if(windows.any{it.hasCapability("Switch Level")}){ windows.setLevel(100) }
                    }
                    if(doorsManagement && doorContactsAreOpen && otherWindows?.currentValue("switch") == "on" && atomicState.otherWindowsOpenByApp)
                    {
                        atomicState.otherWindowsOpenByApp = false
                        log.warn "closing $otherWindows"
                        otherWindows?.off()
                    }
                    atomicState.openByApp = false
                    atomicState.closedByApp = true
                }
                else if(!atomicState.openByApp)
                {
                    descriptiontext "$windows were not open by this app"
                }
                else if(needToClose)
                {
                    descriptiontext "$windows may close soon"
                }
                else 
                {
                    log.error "WINDOWS MANAGEMENT ERROR - fire the developper"
                }
            }
        }
        else if(windows && !inWindowsModes){
            descriptiontext "outside of windows modes"
            if(someAreOpen && atomicState.openByApp) // && (inside > desired + 2 || inside < desired - 2 ))
            {
                windows.off()
                if(windows.any{it.hasCapability("Switch Level")}){ 
                    windows.setLevel(50) 
                }
                atomicState.openByApp = false
                atomicState.closedByApp = true
                if(doorsManagement && doorContactsAreOpen && otherWindows?.currentValue("switch") == "on")
                {
                    otherWindows?.off()
                    atomicState.otherWindowsOpenByApp = false
                }
            }
        }
        else if(windows && bedSensorContactClosed){
            logging "no windows management due to $bedSensorContact"
        }

    }
    else if(!windows) {
        logging "user did not select any window switch"
    }
    else if(bedSensorContactClosed)
    {
        descriptiontext "skipping windows management due to bed sensor mode"
    }
    else if(atomicState.override)
    {
        descriptiontext "Override mode because $thermostat is set to 'auto'"
    }


}


/************************************************DECISIONS******************************************************/
def getDesired(bedsensorclosed){
    int desired = 22 // default value
    def inside = getInsideTemp()



    if(method == "auto")
    {
        desired = getAutoVal()
    }
    else
    {
        desired = dimmer?.currentValue("level")
    }
    if(bedsensorclosed)
    {
        if(doorsContactsAreOpen() && overrideSimpleMode)
        {
            descriptiontext "some doors are open: bed sensor mode ignored at user's request"
        }
        else if(setSpecialTemp || specialSubstraction)
        {       
            desired = specialSubstraction ? desired.toInteger() - substract.toInteger() : specialTemp

            descriptiontext "desired temperature ${substract ? "(specialSubstraction)":"(specialTemp)"} is: $desired and last recorded temperature is ${inside}"
            return desired // END due to bed sensor mode
        }
        else
        {
            descriptiontext "desired temperature is: $desired and last recorded temperature is ${inside}"
            return desired // return the default value
        }
    } 
    descriptiontext "desired temperature is: $desired and current temperature is ${inside}"
    return desired
}
def getInsideHumidity(){

    def result 

    if(!optionalHumSensor)
    {
        // if  we tested with hasCapability() it could return true due to generic thermostat drivers, so we test null value instead
        result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity") 

        if(result == null) // if still null, force the user to review their settings
        {
            log.error formatText("NOR YOUR THERMOSTAT NOR YOUR OUTSIDE SENSOR SUPPORT HUMIDITY MEASUREMENT - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
        }
    }
    else
    {
        result = optionalHumSensor.currentValue("humidity")   
        if(result == null) // if still null, force the user to review their settings
        {
            log.warn formatText("$optionalHumSensor does not support humidity (beware of generic drivers!). - PICK A DIFFERENT SENSOR IN YOUR SETTINGS", "black", "red")
            result = thermostat?.currentValue("humidity") != null ? thermostat?.currentValue("humidity") : outsideTemp?.currentValue("humidity") 
            if(result != null)
            {
                log.warn formatText("This app is using ${thermostat?.currentValue("humidity") != null ? "$thermostat" : "$outsideTemp"} as a default humidity sensor in the mean time", "black", "red")
            }
            result = result == null ? 50 : result // temporary value as last resort
        }
    }
    descriptiontext "Inside humidity is ${result}%"
}
def getNeed(desired, bedSensorContactClosed, inside){

    def humidity = outsideTemp?.currentValue("humidity") 
    def insideHum = getInsideHumidity() // backup for windows and value used for negative swing variation when cooling   
    //def insideHum = 50 //KK
    humidity = humidity != null ? humidity : (insideHum != null ? insideHum : 50)
    boolean INpwSavingMode = powersavingmode && location.mode in powersavingmode && !bedSensorContactClosed 
    boolean inWindowsModes = windows && location.mode in windowsModes
    boolean contactClosed = !contactsAreOpen()  
    boolean doorContactsAreOpen = doorsContactsAreOpen()
    def outsideThres = getOutsideThershold()
    def outsideTemperature = outsideTemp.currentValue("temperature").toInteger()
    def need0 = ""
    def need1 = ""
    def need = []
    def amplThreshold = 2
    def amplitude = Math.abs(inside - desired)
    def swing = outsideTemperature < 10 || outsideTemperature > 24 ? 0.5 : 1 // lower swing when hot or cold outside
    def coolswing = insideHum < 15 ? desired + swing : desired - swing // if too humid, swing down the threshold when cooling
    boolean amplitudeTooHigh = amplitude >= amplThreshold // amplitude between inside temp and desired / preventing amplitude paradox during mid-season

    boolean needCool = !bedSensorContactClosed ? (inWindowsModes ? outsideTemperature >= outsideThres && inside >= coolswing : outsideTemperature >= outsideThres && inside >= desired + swing) : outsideTemperature >= outsideThres + 5 && inside >= desired + swing

    logging"""
outsideTemperature >= outsideThres + 5 = ${outsideTemperature >= outsideThres + 5}
outsideTemperature = $outsideTemperature
outsideThres + 5 = ${outsideThres + 5}
needCool = $needCool
bedSensorContactClosed = $bedSensorContactClosed

"""
    boolean needHeat = !bedSensorContactClosed ? (outsideTemperature < outsideThres /* makes heat run during summer... || amplitudeTooHigh*/) && inside <= desired - swing : inside <= desired - swing && outsideTemperature < outsideThres

    //log.warn "inside = $inside inside >= desired + swing : ${inside >= desired + swing} |||needCool=$needCool"

    boolean motionActive = Active() || bedSensorContactClosed

    // shoulder season management: bedsensor forces ac to run despite cold outside if it gets too hot inside
    boolean norHeatNorCool = !needCool && !needHeat && inside > desired + swing && bedSensorContactClosed && outsideTemperature >= 12 ? true : false
    // the other room could be in an inadequate mode, which would be noticed by an undesirable temperature amplitude
    boolean unacceptable = doorContactsAreOpen && !atomicState.override && (inside < desired - 2 || inside > desired + 2) // if it gets too cold or too hot, ignore doorsManagement
    logging """inside = $inside 
desired = $desired 
$inside < ${desired - 2} : ${inside < desired - 2} 
$inside > ${desired + 2} : ${inside > desired + 2}
"""
    if(unacceptable) // when doors are open, other room's thermostat manager might be in power saving mode
    {
        log.info formatText("UNACCEPTABLE TEMP - ignoring doors management sync", "red", "white")   
    }

    if(!unacceptable && doorsManagement && doorContactsAreOpen && contactClosed)
    {
        def n = doorThermostat?.currentValue("thermostatMode")
        need0 = n.capitalize() // capital letter for later construction of the setCoolingSetpoint cmd String
        need1 = n
        def message = "$doorsContacts ${doorsContacts.size() > 1 ? "are":"is"} open. $thermostat set to ${doorThermostat}'s mode ($n)"     
        descriptiontext "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">$message</div>"         

    }
    else if(!INpwSavingMode && contactClosed && motionActive)
    {
        if(needCool || needHeat || norHeatNorCool)
        {
            if(needCool || norHeatNorCool)
            {
                descriptiontext "needCool true"
                need0 = "Cool"// capital letter for later construction of the setCoolingSetpoint cmd
                need1 = "cool"
                atomicState.lastNeed = need1
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
            if(needHeat) // heating need supercedes cooling need in order to prevent amplitude paradox
            {
                descriptiontext "needHeat true"
                need0 = "Heat" // capital letter for later construction of the setHeatingSetpoint cmd
                need1 = "heat"
                atomicState.lastNeed = need1
                logging("need and atomicState.lastNeed respectively set to ${[need0,need1]}")
            }
        }
        else if(offrequiredbyuser)
        {
            need0 = "off"
            need1 = "off"
            logging("need set to OFF")
        }
        else if(!offrequiredbyuser)
        {
            need0 = atomicState.lastNeed.capitalize()
            need1 = atomicState.lastNeed
            descriptiontext """Not turning off $thermostat at user's request (offrequiredbyuser = $offrequiredbyuser)
Temperature managed by unit's inner thermostat
need0 = $need0
need1 = $need1
atomicState.lastNeed = $atomicState.lastNeed

"""
        }
    }
    else   // POWER SAVING MODE OR NO MOTION OR CONTACTS OPEN     
    { 

        def cause = !motionActive ? "no motion" : (INpwSavingMode ? "power saving mode" : (!contactClosed ? "Contacts Open" : "UNKNOWN CAUSE - SPANK DEVELOPPER"))
        cause = cause == "Contacts Open" ? "${cause}: ${atomicState.listOfOpenContacts}" : cause
        def message = ""

        logging """
inside < criticalhot :  ${inside < criticalhot}
inside > criticalcold :  ${inside > criticalcold}

"""        
        need0 = "off"
        need1 = "off"

        if(inside > criticalhot)
        {
            if(!contactClosed) // if contacts open then just fan circulate
            {
                message = "<div style=\"width:102%;background-color:red;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">FAN CIRCULATE DUE TO EXCESSIVE HEAT AND CONTACTS OPEN: $atomicState.listOfOpenContacts</div>"
                if(fancirculate)
                {
                    thermostat.setThermostatFanMode("on")
                    atomicState.fanOn = true // this global is to ensure user's override
                }
                need0 = "off"
                need1 = "off"
            }
            else 
            {
                message = """<div style=\"width:102%;background-color:red;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">POWER SAVING MODE EXPCETION: TOO HOT! ($cause)</div>"""
                need0 = "Cool"
                need1 = "cool"
            }
        }
        else
        {
            thermostat.setThermostatFanMode("auto")
            atomicState.fanOn = false
        }

        if(inside < criticalcold)
        {
            message = """<div style=\"width:102%;background-color:blue;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">POWER SAVING MODE EXPCETION: TOO COLD! ($cause)</div>"""
            need0 = "Heat"
            need1 = "heat"
        }
        else 
        {
            message = """<div style=\"width:102%;background-color:blue;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">POWER SAVING MODE ($cause)</div>"""
        }
        log.warn message

    }

    windowsManagement(desired, bedSensorContactClosed, inside, outsideTemperature, humidity, swing, needCool, inWindowsModes, amplitudeTooHigh)

    logging"""
bedSensorContactClosed = $bedSensorContactClosed
doorContactsAreOpen = $doorContactsAreOpen
!overrideSimpleMode = ${!overrideSimpleMode}
bedSensorIsClosed() = ${bedSensorIsClosed()}
"""

    if((bedSensorContactClosed && !doorContactsAreOpen) || (!doorContactsAreOpen && !overrideSimpleMode && bedSensorContactClosed))
    {
        log.info "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">Simple Mode Enabled</div>"
    }
    else if(useAbedSensor && bedSensorContactClosed && overrideSimpleMode && doorsOpen)
    {
        log.info "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">Simple Mode Called but NOT active due to doors</div>"
    }
    else if(useAbedSensor)
    {
        log.info "<div style=\"width:102%;background-color:grey;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">Simple Mode Disabled</div>"
    }

    need = [need0, need1]

    log.trace "current need: ${need1 != "off" ? "${need1}ing" : need1}"

    logging"""<div style=\"width:102%;background-color:#1C2BB7;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">
--------------NEED---------------------
inWindowsModes = $inWindowsModes
power saving management= ${powersavingmode ? "$powersavingmode INpwSavingMode = $INpwSavingMode":"option not selected by user"}
amplitude = $amplitude
amplitudeTooHigh = $amplitudeTooHigh

humidity = ${humidity}%
insideHum = ${insideHum}%

outside = $outsideTemperature
inside = $inside
desired = $desired

swing = $swing
coolswing = $coolswing

inside > coolswing = ${inside > coolswing}
inside > desired = ${inside > desired}
inside < desired = ${inside < desired}

bedSensorContactClosed = $bedSensorContactClosed (bedSensorType = $bedSensorType)
contactClosed = $contactClosed (open = $atomicState.listOfOpenContacts)
outsideThres = $outsideThres
outsideTemperature > desired = ${outsideTemperature > desired}
outsideTemperature < desired = ${outsideTemperature < desired}
outsideTemperature >= outsideThres = ${outsideTemperature >= outsideThres}
outsideTemperature < outsideThres = ${outsideTemperature < outsideThres}

needCool = $needCool
needHeat = $needHeat (needHeat supercedes needCool) 

final NEED value = $need
---------------------------------------
</div>
"""

    return need

}
def getAutoVal(){

    def need = outside >= getOutsideThershold() ? "cool" : "heat"
    def result = 23 // just a temporary default value
    def outside = outsideTemp?.currentValue("temperature")    
    //def humidity = outsideTemp?.currentValue("humidity") // outside humidity
    def humidity = getInsideHumidity() // in auto mode we evaluate based only on inside humidity
    humidity = humidity != null ? humidity : 50 // assume 50 as a temporary value to prevent errors when a has just been installed by user and humidity value has yet to be parsed
    def humThres = getHumidityThreshold() // linear equation: hum thres varies with outside temp

    def variation = getVariationAmplitude(outside, need)
    log.debug "variation amplitude = $variation | need (auto, not from getNeed()) is $need "

    result = need == "cool" ? humidity >= humThres ? outside - variation + 1 : outside - variation : need == "heat" ? humidity >= humThres ? outside + variation + 1 : outside + variation : "ERROR"

    if(result == "ERROR") { 
        log.error """ERROR at getAutoVal()
need = $need
atomicState.lastNeed = $atomicState.lastNeed
humidity = $humidity
insideHum = $insideHum
humThres = $humThres
outside = $outside
""" 
        return 23
    }

    def maxAH = maxAutoHeat != null ? maxAutoHeat : 28.0
    def minAC = minAutoCool != null ? minAutoCool : 18.0
    def minAH = minAutoHeat != null ? minAutoHeat : 18.0
    def maxAC = maxAutoCool != null ? maxAutoCool : 29.0

    logging """
maxAH = $maxAH
minAC = $minAC
minAH = $minAH
maxAC = $maxAC
"""

    result = result > maxAH && need == "heat" ? maxAH : result // in this scope need is always either "cool" or "heat", never "off" so these conditions won't be ignored
    result = result < minAC && need == "cool" ? minAC : result
    result = result < minAH && need == "heat" ? minAH : result
    result = result > maxAC && need == "cool" ? maxAC : result

    descriptiontext "desired temperature (auto) in this room is: $result (${humidity > humThres ? "humid condition true" : "humid condition false"}(${humidity}%) | outside temp: $outside) "
    return result
}
def getVariationAmplitude(outside, need){

    //https://www.desmos.com/calculator/uc9391tw1f

    //def max = 10 // max variation // deprecated: need more than 10 when heating... 
    //log.warn "desiredVar = $desiredVar"

    def y = null // value to find
    def x = outside // current temperature outside
    def ya = desiredVar != null ? desiredVar : 1 // coressponding difference required when outside temperature = xa
    def xa = refTemp != null ? refTemp : 24 // 
    def slope = 0.8
    def m = need == "cool" ? slope : (slope + 0.1)*-1  // slope 
    //def a = -1 // offset

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //y = y < max ? y : max // deprecated
    y = y < 1 ? 1 : y

    log.trace "linear result for amplitude variation for auto temp = ${y.toInteger()}"
    return y.toInteger()

}
def getWindowsTimeOfOperation(outsideTemperature){

    def max = maxDuration ? maxDuration : 2000

    def y = null // value to find
    def x = outsideTemperature // current temperature outside
    def ya = windowsDuration ? windowsDuration : 10 // minimal duration // coressponding duration for when outside temperature = xa
    def xa = outsidetempwindowsL // minimal operation temperature
    def m = 0.9 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    y = y < max ? y : max

    logging "linear result for windows duration = ${y.toInteger()} seconds"
    return y.toInteger()
}
def getInsideTemp(){

    def inside = thermostat?.currentValue("temperature") 

    if(sensor)
    {
        def sum = 0
        int i = 0
        int s = sensor.size()
        for(s != 0; i<s;i++)
        {
            def val = sensor[i]?.currentValue("temperature")
            descriptiontext "--${sensor[i]} temperature is: $val"
            sum += val
        }

        inside = sum/s
    }
    else if(doorsManagement && doorsContactsAreOpen() && doorSetOfSensors && useDifferentSetOfSensors)
    {
        def sum = 0
        int i = 0
        int s = doorSetOfSensors.size()
        for(s != 0; i<s;i++)
        {
            def val = doorSetOfSensors[i]?.currentValue("temperature")
            descriptiontext "**${doorSetOfSensors[i]} temperature is: $val"
            sum += val
        }

        inside = sum/s
    }

    descriptiontext "average temperature in this room is: $inside"
    inside = inside.toDouble()
    inside = inside.round(2)
    atomicState.inside = inside
    return inside
}
def getOutsideThershold(){

    // define the outside temperature as of which heating or cooling are respectively required 
    // modulated with outside humidity 

    def humidity = outsideTemp?.currentValue("humidity") 
    humidity = humidity != null ? humidity : 50 // prevents error from recently installed thermostats
    if(humidity == null){
        def message = """$outsideTemp is not returning any humdity value - it may be because it was just included; if so, this will resolve ont its own.
If this message still shows within an hour, check your thermostat configuration..."""
        log.warn """<div style=\"width:102%;background-color:red;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">$message</div>"""
    }
    def outsideTemperature = outsideTemp?.currentValue("temperature")

    // the higher the humidity, the lower the threshold so cooling can happen 
    def y = null // value to find
    def x = humidity 
    def ya = 15 // coressponding outside temperature value for when humidity = xa 
    def xa = 15 // humidity level
    def m = -0.1 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //log.warn "y = $y"
    def result = y > 20 ? 20 : (y < 15 ? 15 : y) // max and min

    descriptiontext "cool/heat decision result = ${y != result ? "$result (corrected from y=$y)" : "$result"} (humidity being ${humidity < 40 ? "low at ${humidity}%" : "high at ${humidity}%"})"
    return result


}
def getHumidityThreshold(){ // must be called only upon windows opening decision
    def humidity = outsideTemp?.currentValue("humidity") 
    humidity = humidity != null ? humidity : 50
    def outsideTemperature = outsideTemp?.currentValue("temperature")

    // we want to set a humidity threshold depending on outside temperature
    // humidity of 98, even an outside temp of 70 will feel too warm so we don't open the windows
    // but humidity of 98 at 60F, it's ok to use outside air to cool down the house

    def y = null // value to find
    def x = outsideTemperature 
    def ya = 70 // coressponding humidity threshold for when humidity = xa
    def xa = 70
    def m = -3 // slope / coef

    y = m*(x-xa)+ya // solving y-ya = m*(x-xa)
    //y = y >= 78 ? 

    return y

}
def getLastMotionEvents(Dtime, testType){
    int s = motionSensors.size() 
    int i = 0
    def thisDeviceEvents = []
    int events = 0
    for(s != 0; i < s; i++) // collect active events
    { 
        def device = motionSensors[i]                
        thisDeviceEvents = device.eventsSince(new Date(now() - Dtime)).findAll{it.value == "active"} // collect motion events for each sensor separately
        logging "Collected ${thisDeviceEvents.size()} evts for $device"
        events += thisDeviceEvents.size() 
    }
    descriptiontext "$events active events in the last ${noMotionTime} minutes ($testType)"
    return events
}
def getRemainTime(timeLimit, timeStamp){

    timeLimit = timeLimit.toInteger() * 60 * 60 * 1000
    long elapsedTime = now() - timeStamp // total elapsed time since last true event and now

    if(elapsedTime > timeLimit)
    {
        return 0
    }

    // get the remaining time given the time limit
    float minutes = (timeLimit - elapsedTime)/1000/60 // remaining minutes
    float hours = (timeLimit - elapsedTime)/1000/60/60 // remaining hours
    float remain = minutes >= 60 ? hours : minutes // decision hours/minutes
    def unit = minutes >= 60 ? "hours" : "minutes"

    logging """
timeLlimit = $timeLimit
timeStamp = $timeStamp
(now() - timeStamp)/1000/60 = ${(now() - timeStamp)/1000/60} minutes
elapsedTime = $elapsedTime
//REMAINING TIME in minutes, hours
minutes = $minutes
hours = $hours
remain = $remain
unit = $unit 
"""

    return "${Math.round(remain)} $unit"
}
/************************************************BOOLEANS******************************************************/
boolean contactsAreOpen(){
    boolean Open = false
    def listOpen = []

    if(WindowsContact)
    {
        atomicState.lastOpenEvt = atomicState.lastOpenEvt != null ? atomicState.lastOpenEvt : now()
        def deltaTime = openDelay != null ? openDelay : 30 
        listOpen = WindowsContact?.findAll{it?.currentValue("contact") == "open"}     
        atomicState.listOfOpenContacts = "$listOpen"
        def messageText = "${listOpen.size() > 0 ? "Some contacts are open : $atomicState.listOfOpenContacts" : "all contacts closed"}"
        if(listOpen.size() > 0)
        {
            log.warn messageText
        }
        else 
        {
            log.trace messageText
        }
        Open = listOpen.size() > 0 && now() - atomicState.lastOpenEvt > deltaTime * 1000
    }
    else
    {
        logging "NO CONTACTS"
    }

    logging """contacts: $contact open ?: ${listOpen}"""
    return Open
}
boolean bedSensorIsClosed(){
    boolean result =  atomicState.lastResultWasTrue 
    //boolean doorOpen = doorsContactsAreOpen() // FEEDBACK LOOP since doorsContactsAreOpen() function calls bedSensorIsClosed()
    boolean currentlyClosed = false 

    if(useAbedSensor)
    {
        if(bedSensorType == "contact" || bedSensorType == "both" )
        {
            def Dtime = 60000
            int s = bedSensorContact.size() 
            int i = 0
            def thisDeviceEvents = []
            int events = 0
            def findClosed = bedSensorContact.findAll{it?.currentValue("contact") == "closed"}
            currentlyClosed = findClosed.size() != 0

            atomicState.ButtonSupercedes = atomicState.ButtonSupercedes != null ? atomicState.ButtonSupercedes : true
            atomicState.lastBSeventStamp = atomicState.lastBSeventStamp != null ? atomicState.lastBSeventStamp : new Date().format("h:mm:ss a", location.timeZone) // formated time stamp for debug purpose

            def loggingMessage = """
BED SENSOR DATA
currentlyClosed = $currentlyClosed
atomicState.ButtonSupercedes = $atomicState.ButtonSupercedes
last bed sensor contact event = ${atomicState.lastBSeventStamp}

"""
            log.debug (formatText(loggingMessage, "white", "#39CC37"))

            if(bedSensorType == "both")
            {                

                if(atomicState.buttonPushed)
                {
                    descriptiontext "atomicState.buttonPushed = $atomicState.buttonPushed"
                    result = true
                    atomicState.lastResultWasTrue = true
                }
                else 
                {
                    descriptiontext "atomicState.buttonPushed = $atomicState.buttonPushed"
                    result = false
                    atomicState.lastResultWasTrue = false
                }
                if(atomicState.ButtonSupercedes) 
                {
                    atomicState.lastResultWasTrue = atomicState.lastResultWasTrue ? true : false
                    return result
                }
            }

            if(currentlyClosed && atomicState.buttonPushed) { // if contact is closed AND button pushed, return true
                result = true 
            }
            else // if not, then evaluate recent events and if there are recent contact events, someone is indeed on the bed
            {
                for(s != 0; i < s; i++) // collect active events
                { 
                    thisDeviceEvents = bedSensorContact[i].eventsSince(new Date(now() - Dtime)).findAll{it.value in ["closed", "open"]} // collect ALL motion events for each sensor separately
                    events += thisDeviceEvents.size() 
                }
                descriptiontext "$bedSensorContact ${s>1? "were":"was"} closed $events times in the last ${Dtime/1000} seconds"
                result = events > 0
                logging("$bedSensorContact closed ?: $result")
            }
            atomicState.lastResultWasTrue = atomicState.lastResultWasTrue ? true : false
        }
        else if(bedSensorType == "button")
        {
            descriptiontext "-- atomicState.buttonPushed = $atomicState.buttonPushed"
            if(atomicState.buttonPushed)
            {
                result = true
                atomicState.lastResultWasTrue = true
            }
            else 
            {
                result = false
                atomicState.lastResultWasTrue = false
            }
        }
    }
    atomicState.lastButtonEvent = atomicState.lastButtonEvent != null ? atomicState.lastButtonEvent : now()
    boolean newEvent = now() - atomicState.lastButtonEvent < 5000 // check that this is not a new request
    if(bedSensorTimeLimit != null) // if user set a time limit
    {
        // forward declaration in updated() would generate false positive of atomicState.lastTimeBsTrue, 
        // so we need to test that it has been initialized first by an actual event
        if(atomicState.lastTimeBsTrue != null && atomicState.buttonPushed) 
        {
            def remainTime = getRemainTime(bedSensorTimeLimit, atomicState.lastTimeBsTrue)
            def message = "SIMPLE MODE - remaining time: ${remainTime}"
            descriptiontext formatText(message, "white", "grey")

            if(remainTime <= 0 && !newEvent) // time is up
            {
                result = false 
                atomicState.lastResultWasTrue = false
            }
            else if(result || atomicState.buttonPushed) // time isn't up and either contact still returns true or button still pushed
            {
                result = true // return true as long as time limit hasn't been reached and atomicState.buttonPushed = true
                atomicState.lastResultWasTrue = true
            }
            else
            {
                log.error "ERROR 5244"
            }
        }
    }

    logging"bed sensor boolean returns $result"   
    log.warn "atomicState.lastResultWasTrue = $atomicState.lastResultWasTrue"

    if(newEvent && result == true)
    {
        // time stamp this new value
        atomicState.lastTimeBsTrue = now()
    }

    return result
}
boolean doorsContactsAreOpen(){
    boolean Open = false
    def listOpen = []

    if(doorsContacts)
    {
        listOpen = doorsContacts?.findAll{it?.currentValue("contact") == "open"}
        Open = listOpen.size() > 0
    }
    if(Open && !overrideSimpleMode && bedSensorIsClosed())
    {
        descriptiontext "$doorsContacts open but $bedSensorContact is closed and user doesn't wish to override"
        return false
    }

    logging """doors: $doorsContacts open ?: ${listOpen}"""
    return Open
}
boolean Active(){
    boolean result = true // default is true  always return Active = true when no sensor is selected by the user


    if(motionSensors)
    {
        long Dtime = noMotionTime * 1000 * 60
        boolean inMotionMode = location.mode in motionmodes
        logging "inMotionMode = $inMotionMode"

        if(inMotionMode)
        {
            result = getLastMotionEvents(Dtime, "motionTest") > 0
        }
        else 
        {
            logging("motion returns true because outside of motion modes")
        }
        Dtime = 60 * 60 * 1000 
        if(getLastMotionEvents(Dtime, "overrideTest")) // if no motion for over one hour then save power by resetting windows override
        {
            //atomicState.openByApp = true
            atomicState.closedByApp = true
        }
    }
    else 
    {
        logging("user did not select any motion sensor")
    }
    return result
}
/************************************************MISCELANEOUS*********************************************************/
def stop(){

    if(customCommand)
    {

        def cmd = customCommand.minus("()")
        int s = windows.size()
        int i = 0
        for(s!=0;i<s;i++)
        {
            windows[i]."${cmd}"()
            log.warn "${windows[i]} $customCommand"
        }
        if(doorsManagement && doorContactsAreOpen && atomicState.otherWindowsOpenByApp)
        {
            s = otherWindows.size()
            i = 0
            for(s!=0;i<s;i++)
            {
                otherWindows[i]."${cmd}"()
                log.warn "${otherWindows[i]} $customCommand"
            }
        }
    }

}
def Poll(){
    if(location.mode in restricted){
        descriptiontext "location in restricted mode, doing nothing"
        return
    } 
    if(atomicState.paused == true)
    {
        return
    }
    
    boolean override = atomicState.override   
    boolean thermPoll = thermostat.hasCommand("poll")
    boolean thermRefresh = thermostat.hasCommand("refresh") 
    boolean outsidePoll = outsideTemp.hasCommand("poll")
    boolean outsideRefresh = outsideTemp.hasCommand("refresh") 
    boolean heaterPoll = heater?.hasCommand("poll")
    boolean heaterRefresh = heater?.hasCommand("refresh") 
    boolean coolerPoll = cooler?.hasCommand("poll")
    boolean coolerRefresh = cooler?.hasCommand("refresh") 
    boolean pwPoll = pw?.hasCommand("poll")
    boolean pwRefresh = pw?.hasCommand("refresh") 

    if(thermRefresh){
        thermostat.refresh()
        descriptiontext("refreshing $thermostat")
    }
    if(thermPoll){
        thermostat.poll()
        descriptiontext("polling $thermostat")
    }
    if(outsideRefresh){
        outsideTemp.refresh()
        descriptiontext("refreshing $outsideTemp")
    }
    if(outsidePoll){
        outsideTemp.poll()
        descriptiontext("polling $outsideTemp")
    }

    if(coolerRefresh){
        cooler?.refresh()
        descriptiontext("refreshing $cooler")
    }
    if(coolerPoll){
        cooler?.poll()
        descriptiontext("polling $cooler")
    }

    if(heaterRefresh){
        heater?.refresh()
        descriptiontext("refreshing $heater")
    }
    if(heaterPoll){
        heater?.poll()
        descriptiontext("polling $heater")
    }

    if(sensor)
    {
        boolean sensorPoll = sensor.findAll{it.hasCommand("poll")}.size() == sensor.size()
        boolean sensorRefresh = sensor.findAll{it.hasCommand("refresh")}.size() == sensor.size()

        if(sensorRefresh){
            int i = 0
            int s = sensor.size()
            for(s!=0;i<s;i++)
            {
                sensor[i].refresh()
                descriptiontext("refreshing ${sensor[i]}")
            }
        }
        if(sensorPoll){
            int i = 0
            int s = sensor.size()
            for(s!=0;i<s;i++)
            {
                sensor[i].poll()
                descriptiontext("polling ${sensor[i]}")
            }
        }
    }

    if(pwRefresh){
        pw.refresh()
        descriptiontext("refreshing $pw")
    }
    if(pwPoll){
        sensor.poll()
        descriptiontext("polling $pw")
    }

    if(windows)
    {
        boolean windowsPoll = windows.findAll{it.hasCommand("poll")}.size() == windows.size()
        boolean windowsRefresh = windows.findAll{it.hasCommand("refresh")}.size() == windows.size()

        if(windowsRefresh){
            int i = 0
            int s = windows.size()
            for(s!=0;i<s;i++)
            {
                def dev = windows[i]
                dev.refresh()
                descriptiontext("refreshing $dev")
            }
        }
        if(windowsPoll){
            int i = 0
            int s = windows.size()
            for(s!=0;i<s;i++)
            {
                def dev = windows[i]
                dev.refresh()
                descriptiontext("refreshing $dev")
            }
        }
    }

}
def logging(message){
    if(enabledebug)
    {
        log.debug message
    }
    if(atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptiontext(message){
    if(description)
    {
        log.info message
    }
}
def disablelogging(){
    log.warn "debug logging disabled..."
    app.updateSetting("enabledebug",[value:"false",type:"bool"])
}
def formatText(title, textColor, bckgColor){
    return  "<div style=\"width:102%;background-color:${bckgColor};color:${textColor};padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${title}</div>"
}