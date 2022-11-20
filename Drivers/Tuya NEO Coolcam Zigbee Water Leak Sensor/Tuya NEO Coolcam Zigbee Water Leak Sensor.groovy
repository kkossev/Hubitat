/**
 *  Tuya / NEO Coolcam Zigbee Water Leak Sensor driver for Hubitat
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
 * ver. 1.0.0 2022-03-26 kkossev  - Inital test version
 * ver. 1.0.1 2022-04-12 kkossev  - added _TYST11_qq9mpfhw fingerprint
 * ver. 1.0.2 2022-04-14 kkossev  - Check-in info logs; model 'q9mpfhw' inClusters correction
 * ver. 1.0.3 2022-04-16 kkossev  - 'Last Updated' workaround for NEO sensors
 * ver. 1.0.4 2022-05-14 kkossev  - code cleanup; debug logging is off by default; fixed debug logging not turning off after 24 hours; added Configure button
 * ver. 1.0.5 2022-08-03 kkossev  - added batterySource, added watchDog, set battery 0% if OFFLINE
 * ver. 1.0.6 2022-11-15 kkossev  - fixed _TZ3000_qdmnmddg fingerprint; added _TZ3000_rurvxhcx ; added _TZ3000_kyb656no ;
 * ver. 1.0.7 2022-11-20 kkossev  - (dev. branch) offline timeout increased to 12 hours; Import button loads the dev. branch version; Configure will not reset power source to '?'; Save Preferences will update the driver version state; water is set to 'unknown' when offline
 *                                  added lastWaterWet time in human readable format; added device rejoinCounter state; water is set to 'unknown' when offline; added feibit FNB56-WTS05FB2.0; added 'tested' water state; pollPresence misfire after hub reboot bug fix
 *                                  added Momentary capability - push() button will generate a 'tested' event for 2 seconds; added Presence capability; 
 * 
 *                                  TODO: add batteryLastReplaced event; add 'Testing option'; add 'isTesting' state
 *
*/

def version() { "1.0.7" }
def timeStamp() {"2022/11/20 8:35 AM"}

@Field static final Boolean debug = false
@Field static final Boolean debugLogsDefault = true

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus
 
metadata {
    definition (name: "Tuya NEO Coolcam Zigbee Water Leak Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20NEO%20Coolcam%20Zigbee%20Water%20Leak%20Sensor/Tuya%20NEO%20Coolcam%20Zigbee%20Water%20Leak%20Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "Battery"
        capability "WaterSensor"        
        capability "PowerSource"
        capability "TestCapability"
        capability "Momentary"
        capability "PresenceSensor"
        //capability "TamperAlert"    // tamper - ENUM ["clear", "detected"]

        
        command "configure", [[name: "Manually initialize the sensor after switching drivers.  \n\r   ***** Will load the device default values! *****" ]]
        command "wet", [[name: "Manually switch the Water Leak Sensor to WET state" ]]
        command "dry", [[name: "Manually switch the Water Leak Sensor to DRY state" ]]
        command "push", [[name: "Manually switch the Water Leak Sensor to TESTED state" ]]
        //command "test"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_qq9mpfhw", deviceJoinName: "NEO Coolcam Leak Sensor"          // vendor: 'Neo', model: 'NAS-WS02B0', 'NAS-DS07'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003",                outClusters:"0003,0019", model:"q9mpfhw",manufacturer:"_TYST11_qq9mpfhw", deviceJoinName: "NEO Coolcam Leak Sensor SNTZ009" // SNTZ009
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_jthf7vb6", deviceJoinName: "Tuya Leak Sensor TS0601"          // vendor: 'TuYa', model: 'WLS-100z'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,EF01", outClusters:"0003,0019", model:"TS0207", manufacturer:"_TYZB01_sqmd19i1", deviceJoinName: "Tuya Leak Sensor TS0207 Type I"   // round cabinet, sensors on the bottom
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,EF01", outClusters:"0003,0019", model:"TS0207", manufacturer:"_TYZB01_o63ssaah", deviceJoinName: "Blitzwolf Leak Sensor BW-IS5" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0500,0000",      outClusters:"0019,000A", model:"TS0207", manufacturer:"_TZ3000_upgcbody", deviceJoinName: "Tuya Leak Sensor TS0207 Type II"  // rerctangular cabinet, external sensor; +BatteryLowAlarm!?
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0500,0000",      outClusters:"0019,000A", model:"TS0207", manufacturer:"_TZ3000_t6jriawg", deviceJoinName: "Moes Leak Sensor TS0207"          // Moes
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0500,0001",      outClusters:"0019",      model:"TS0207", manufacturer:"_TZ3000_qdmnmddg", deviceJoinName: "Tuya Leak Sensor TS0207 Type II"  // https://community.hubitat.com/t/aliexpress-has-flash-sale-on-tuya-zigbee-leak-sensor-9-28/93727/3?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0500,0000",      outClusters:"0019,000A", model:"TS0207", manufacturer:"_TZ3000_rurvxhcx", deviceJoinName: "Tuya Leak Sensor TS0207 Type III" // https://community.hubitat.com/t/aliexpress-has-flash-sale-on-tuya-zigbee-leak-sensor-9-28/93727/13?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0500,0000",      outClusters:"0019,000A", model:"TS0207", manufacturer:"_TZ3000_kyb656no", deviceJoinName: "MEIAN Water Leak Sensor"          // https://community.hubitat.com/t/release-tuya-neo-coolcam-zigbee-water-leak-sensor/91370/22?u=kkossev
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,000A,0019,0001,0500,0501,1000", outClusters:"0004,0003,0001,0500,0501", model:"FNB56-WTS05FB2.0", manufacturer:"feibit", deviceJoinName: "Feibit SWA01ZB Water Leakage  Sensor"         // https://community.hubitat.com/t/release-tuya-neo-coolcam-zigbee-water-leak-sensor/91370/41?u=kkossev 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,000A,0019,0001,0500,0501,1000", outClusters:"0004,0003,0001,0500,0501", model:"FNB56-WTS05FB2.4", manufacturer:"feibit", deviceJoinName: "Feibit SWA01ZB Water Leakage  Sensor"         // not tested
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: debugLogsDefault)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        /*
        input (name: "testingDelay", type: "bool", title: "Enable Delayed Testing", description: "<i>If state stays 'wet' for less than NN seconds, assume this was a test</i>", defaultValue: false)
        if (testingDelay?.value == true) {
            input (name: "minimumWetTime", type: "number", title: "<b>Minimum 'wet' time</b>", description: "<i>The minimum time the leak sensor must report 'wet' state to be considered as a real alarm. Default = 3 seconds</i>", range: "0..7200", defaultValue: 3)
        }
        */
        
    }
}

@Field static final Integer presenceCountTreshold = 12
@Field static final Integer defaultPollingInterval = 3600

private getCLUSTER_TUYA()       { 0xEF00 }
private getSETDATA()            { 0x00 }
private getSETTIME()            { 0x24 }

// Tuya Commands
private getTUYA_REQUEST()       { 0x00 }
private getTUYA_REPORTING()     { 0x01 }
private getTUYA_QUERY()         { 0x02 }
private getTUYA_STATUS_SEARCH() { 0x06 }
private getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits

// Parse incoming device messages to generate events
def parse(String description) {
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()
    //if (settings?.logEnable == true) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                logWarn "unparsed power cluster attrint ${descMap.attrInt}"
            }
        }     
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        } 
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            state.rejoinCounter = (state.rejoinCounter ?: 0) + 1
            logDebug "device announcement"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            logDebug "Tuya check-in (0001) app version ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId in ["FFDF", "FFE2", "FFE4","FFFE"]) {
            logDebug "Tuya check-in (${descMap?.attrId}) value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0500" && descMap?.command == "01") {    //read attribute response
            logDebug "IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
        } 
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response
            logDebug "IAS enroll write attribute response is ${descMap?.data[0] == "00" ? "success" : "failure"}"
        } 
        else {
            logDebug "<b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {	
        logDebug "Zone status: $description"
        parseIasMessage(description)
    }
    else if (description?.startsWith('enroll request')) {
         /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        logDebug "Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else {
        logDebug "<b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
}

def parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn 
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        //if (settings?.logEnable == true) log.trace "zs = $zs"
        processWaterEvent( zs.alarm1Set == true ? 'wet' : 'dry')
    }
    catch (e) {
        log.error "This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

def processWaterEvent( String value, boolean isDigital=false, boolean wasChecked=false ) {
    def valueToBeSent = value
    switch (value) {
        case 'checking' :
            valueToBeSent = 'checking'
            state.isTesting = true
            break
        case 'tested' :
            valueToBeSent = 'tested'
            state.isTesting = true
            runIn( 2, setDryDelayed, [overwrite: true])
            break
        case 'wet' :
            if (settings?.testingDelay == false ) {
                // send 'wet' without delays and additional checks
                valueToBeSent = "wet"
                state.isTesting = false
            }
            else {
                // check whether this is the initial 'wet' event, or a confirmation after few seconds?
                if (wasChecked == true) {
                    // scheduled call, confirmed from "checkIfStillWet()"
                    valueToBeSent = "wet"
                }
                else {
                    // this is the inital 'wet' event - to be verified!
                    valueToBeSent = "checking"
                    state.isTesting = true
                    runIn( settings?.minimumWetTime ?: 1, "checkIfStillWet")
                }
            }
            if (isDigital==false ) {
                state.lastWaterWet = FormattedDateTimeFromUnix( now() )
            }
            break
        case 'dry' :
            // 'dry' may come after a test or after a real alarm
            valueToBeSent = "dry"
            descriptionText = "${device.displayName} is ${valueToBeSent}"
            state.isTesting = false
            break
        default :
            log.warn "unprocessed water event ${value}"
            return
    }
    sendWaterEvent( valueToBeSent, isDigital )
}

def sendWaterEvent( String value, boolean isDigital=false) {
    def type = isDigital == true ? "digital" : "physical"
    String descriptionText
    switch (value) {
        case 'checking' :
            descriptionText = "${device.displayName} checking"
            break
        case 'tested' :
            descriptionText = "${device.displayName} is tested"
            break
        case 'wet' :
            if (settings?.testingDelay == false ) {
                // send 'wet' without delays and additional checks
                descriptionText = "<b>${device.displayName} is wet</b>"
            }
            else {
                // check whether this is the initial 'wet' event, or a confirmation after few seconds?
                if (wasChecked == true) {
                    // scheduled call, confirmed from "checkIfStillWet()"
                    descriptionText = "<b>${device.displayName} is wet</b> (checked)"
                }
                else {
                    // this is the inital 'wet' event - to be verified!
                    descriptionText = "${device.displayName} received wet status - checking again in ${settings?.minimumWetTime ?: 1} seconds"
                }
            }
            break
        case 'dry' :
            // 'dry' may come after a test or after a real alarm
            if (state.isTesting  == true) {
                descriptionText = "${device.displayName} is dry (test finished)"
            }
            else {
                descriptionText = "${device.displayName} is dry"
            }
            break
        default :
            log.warn "unprocessed water event ${value}"
            return
    }
    if (isDigital == true) descriptionText += " (digital)"
    if (settings?.txtEnable==true) log.info "$descriptionText"    // includes deviceName
    sendEvent(name: "water", value: value, descriptionText: descriptionText, type: type , isStateChange: true)    
}

def setDryDelayed() {
    processWaterEvent( 'dry', isDigital=true, wasChecked=true  )
}

def checkIfStillWet() {
    // called when the first 'wet' event is received
    if (device.currentValue('water', true) == 'checking') {
        logDebug "sensor still reprots 'checking' after ${settings?.minimumWetTime ?: 1} seconds - this is a real alarm!"
        state.isTesting = false
        processWaterEvent( 'wet', isDigital=false, wasChecked=true  )
    }
    else {
        // the leak sensor status is back to 'tested' or 'dry', before the check timer expired - it was a test!
        logDebug "it was a test (${device.currentValue('water', true)})"
        state.isTesting = true
        processWaterEvent( 'tested', isDigital=false, wasChecked=true )
    }
    unschedule("checkIfStillWet")
}


def wet() {
    processWaterEvent( "wet", isDigital=true  )
}

def dry() {
    processWaterEvent( "dry", isDigital=true  )
}

def push() {
    processWaterEvent( "tested", isDigital=true )
}

def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        // Tuya time sync request is sent by NEO Coolcam sensors every 1 hour
        logDebug "${device.displayName} Tuya time synchronization request"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
        }
        catch(e) {
            if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        logDebug "time now is: ${FormattedDateTimeFromUnix( now() )}"
        logDebug "sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
        getBatteryPercentageResult((device.currentState('battery').value as int)* 2, isDigital=true)         // added 04/06/2022 : send latest known battery level event to update the 'Last Activity At' timestamp
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"))
    {
        def transid = zigbee.convertHexToInt(descMap?.data[1])
        def dp = zigbee.convertHexToInt(descMap?.data[2])
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        logDebug "Tuya Cluster command: dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case 0x65 : // dry/wet
                processWaterEvent( fncmd == 0 ? "dry" : "wet" )
                break
            case 0x66 : // battery
                logDebug "Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                def rawValue = 0
                if (fncmd == 0) rawValue = 100           // Battery Full
                else if (fncmd == 1) rawValue = 75       // Battery High
                else if (fncmd == 2) rawValue = 50       // Battery Medium
                else if (fncmd == 3) rawValue = 25       // Battery Low
                getBatteryPercentageResult(rawValue*2)
                break 
            default :
                logWarn "<b>NOT PROCESSED TUYA COMMAND</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
    }
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


// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    logInfo "${device.displayName} installed()... driver version ${driverVersionAndTimeStamp()}"
}

// called when preferences are saved
// runs when save is clicked in the preferences section
def updated() {
    checkDriverVersion()
    if (settings?.txtEnable == true) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable == true) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
}


def refresh() {
    logDebug "refresh()..."
    zigbee.readAttribute(0, 1)
}


def powerSourceEvent( state = null) {
    if (state != null && state == 'unknown' ) {
        sendEvent(name : "powerSource",	value : "unknown", descriptionText: "device is OFFLINE", type: "digital")
    }
    else {
        sendEvent(name : "powerSource",	value : "battery", descriptionText: "device is back online", type: "digital")
    }
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    /*
    if ((state.rxCounter != null) && state.rxCounter <= 2)
        return                    // do not count the first device announcement or binding ack packet as an online presence!
    */
    powerSourceEvent()
    if (device.currentValue('powerSource', true) in ['unknown', '?'] || device.currentValue('presence', true) != "present") {
        logInfo "is now present"
        if (device.currentValue('battery', true) == 0 ) {
            if (state.lastBattery != null &&  safeToInt(state.lastBattery) != 0) {
                sendBatteryEvent(safeToInt(state.lastBattery), isDigital=true)
            }
        }
        sendEvent(name: "presence", value: "present", descriptionText: "device is now online", type:  'digital' , isStateChange: true )
    }    
    state.notPresentCounter = 0    
}

// called every 60 minutes from pollPresence()
def checkIfNotPresent() {
    state.notPresentCounter = (state.notPresentCounter?: 0) + 1
    if (state.notPresentCounter >= presenceCountTreshold) {
        if (!(device.currentValue('powerSource', true) in ['unknown'])) {
    	    powerSourceEvent("unknown")
            logWarn "<b>is not present!</b>"
        }
        if (safeToInt(device.currentValue('battery', true)) != 0) {
            logWarn "forced battery to '<b>0 %</b>"
            sendBatteryEvent( 0, isDigital=true )
        }
        if (device.currentValue('water', true) != 'unknown') {
            sendWaterEvent( 'unknown',  isDigital=true )
        }
        if (device.currentValue('presence', true) != "not present") {
            sendEvent(name: "presence", value: "not present", descriptionText: "device is <b>not present</b>", type:  'digital' , isStateChange: true )
        }
    }
}

def configurePollPresence() {
    runIn( defaultPollingInterval, pollPresence, [overwrite: true, misfire: "ignore"])
}

def configureLogsOff() {
    if (settings?.logEnable == true) {
        runIn(86400, logsOff, [overwrite: true, misfire: "ignore"])    // turn off debug logging after 24 hours
        logInfo "Debug logging will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
}

// check for device offline every 60 minutes
def pollPresence() {
    logDebug "pollPresence()"
    checkIfNotPresent()
    configurePollPresence()
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

def logDebug(msg) {
    if (settings?.logEnable == null || settings?.logEnable == true) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable == null || settings?.txtEnable == true) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.logEnable == null || settings?.logEnable == true) {
        log.warn "${device.displayName} " + msg
    }
}

@Field static final String dateFormat = 'yyyy-MM-dd HH:mm:ss.SSS'

def unixFromFormattedDateTime( formattedDateTime ) {
    def unixDateTime = Date.parse(dateFormat, formattedDateTime).time
    return unixDateTime
}

def FormattedDateTimeFromUnix( unixDateTime ) {
    def formattedDateTime = new Date(unixDateTime).format(dateFormat, location.timeZone) 
    return formattedDateTime
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
        configurePollPresence()
        configureLogsOff()
    }
}

def logInitializeRezults() {
    logInfo "manufacturer  = ${device.getDataValue("manufacturer")}"
    logInfo "Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by configure(fullInit) button 
void initializeVars( boolean fullInit = true ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || state.packetID == null) state.notPresentCounter = 0
    if (fullInit == true || state.rxCounter == null) state.rxCounter = 0
    if (fullInit == true || state.txCounter == null) state.txCounter = 0
    if (fullInit == true || state.rejoinCounter == null) state.rejoinCounter = 0
    if (fullInit == true || state.isTesting == null) state.isTesting = false
    if (state.lastBattery == null) state.lastBattery = "0"
    if (state.lastWaterWet == null) state.lastWaterWet = "unknown"    //FormattedDateTimeFromUnix( now() )
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    if (device.currentValue('powerSource', true) == null) sendEvent(name : "powerSource", descriptionText: "device just installed",	value : "?", isStateChange : true)
    if (device.currentValue('water', true) == null) sendEvent(name : "water",	value : "unknown", descriptionText: "device just installed", isStateChange : true)
    if (device.currentValue('presence', true) == null) sendEvent(name: "presence", value: "unknown", descriptionText: "device just installed", type:  'digital' , isStateChange: true )
    
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", [value:debugLogsDefault, type:"bool"])
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", [value: true, type:"bool"])
    if (fullInit == true || settings?.testingDelay == null) device.updateSetting("testingDelay", [value: false, type:"bool"])
    if (fullInit == true || settings?.minimumWetTime == null) device.updateSetting("minimumWetTime", [value:3, type:"number"])
    
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)
    return  cmds
}

// called when used with capability "Configuration" is called when the configure button is pressed on the device page. 
// Runs when driver is installed, after installed() is run. if capability Configuration exists, a Configure command is added to the ui
// It is also called on initial install after discovery.
def configure() {
    List<String> cmds = []
    logInfo "configure().."
    unschedule()
    initializeVars(fullInit = true)
    configurePollPresence()
    configureLogsOff()
    cmds += tuyaBlackMagic()    
    sendZigbeeCommands(cmds)    
}

// called when used with capability "Initialize" it will call this method every time the hub boots up. So for things that need refreshing or re-connecting (LAN integrations come to mind here) ..
// runs first time driver loads, ie system startup 
// when capability Initialize exists, a Initialize command is added to the ui.
def initialize() {
    logInfo "Initialize()..."
    installed()
    updated()
    configure()
    runIn( 3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    logDebug "sendTuyaCommand = ${cmds}"
    state.txCounter = (state.txCounter ?: 0) + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    logDebug "sendZigbeeCommands (cmd=${cmd})"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
        state.txCounter = (state.txCounter ?: 0) + 1
    }
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	logInfo "${descriptionText}"
	return descriptionText
}

def logsOff(){
    log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value: false, type:"bool"])
}

def getBatteryPercentageResult(rawValue, isDigital=false) {
    logDebug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.isStateChange = true
        result.unit  = '%'
        result.type = isDigital == true ? "digital" : "physical"
        result.descriptionText = "${device.displayName} battery is ${result.value}% ($result.type)"
        state.lastBattery = (result.value).toString()
        sendEvent(result)
        logInfo "${result.descriptionText}, water:${device.currentState('water').value}"
    }
    else {
        logWarn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

private Map getBatteryResult(rawValue) {
    logDebug "${device.displayName} batteryVoltage = ${(double)rawValue / 10.0} V"
    def result = [:]
    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0)
        roundedPct = 1
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${device.displayName} battery is ${result.value}% (${volts} V)"
        result.name = 'battery'
        result.unit = '%'
        result.type = "physical"
        result.isStateChange = true
        logInfo "${result.descriptionText}, water:${device.currentState('water').value}"
        state.lastBattery = roundedPct.toString()
        sendEvent(result)
    }
    else {
        logWarn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }    
}

def sendBatteryEvent( roundedPct, isDigital=false ) {
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type:  isDigital == true ? "digital" : "physical", isStateChange: true )    
}
