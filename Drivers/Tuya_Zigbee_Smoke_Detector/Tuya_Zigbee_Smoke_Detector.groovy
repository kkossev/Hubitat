/**
 *  Tuya Zigbee Smoke Detector driver for Hubitat Elevation
 * 
 *  https://community.hubitat.com/t/beta-tuya-zigbee-smoke-detector/104159
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  ver. 1.0.0 2022-10-29 kkossev - inital version for _TZE200_ntcy3xu1
 *  ver. 1.0.1 2022-10-31 kkossev - added _TZE200_uebojraa
 *  ver. 1.0.2 2022-11-17 kkossev - notPresentCounter set to 12 hours; states set to 'unknown' on device creation'; added Clear Detected Tested buttons; removed Configure button
 *
 *
 */
import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType

def version() { "1.0.2" }
def timeStamp() {"2022/11/17 1:13 PM"}

@Field static final Boolean debug = false

metadata {
    definition (name: "Tuya Zigbee Smoke Detector", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya_Zigbee_Smoke_Detector/Tuya_Zigbee_Smoke_Detector.groovy", singleThreaded: true ) {
		capability "Sensor"
		//capability "Configuration"
		capability "Smoke Detector"    // attributes: smoke ("detected","clear","tested")    ea.STATE, true, false).withDescription('Smoke alarm status'),  [dp=1] 
        capability "TamperAlert"       // attributes: tamper - ENUM ["clear", "detected"]    [dp=4 ]  values 1/0
		capability "TestCapability"
		capability "Battery"            //  ea.STATE, ['low', 'middle', 'high']).withDescription('Battery level state'),    dp14 0=25% 1=50% 2=90% [dp=14] battery low   value 2 (FULL)
        capability "PowerSource"        //powerSource - ENUM ["battery", "dc", "mains", "unknown"]
        capability "PresenceSensor"

        command "clear"
        command "detected"
        command "tested"
        
        //command "silenceSiren", [[name:"Silence Siren", type: "ENUM", description: "Silence the Siren", constraints: ["--- Select ---", "true", "false" ]]]        // 'Silence the siren' ea.STATE_SET, true, false)    HE->Tuya  dp=16, BOOL
        //command "enableAlarm",  [[name:"Enable Alarm",  type: "ENUM", description: "Enable the Alarm",  constraints: ["--- Select ---", "true", "false" ]]]          //'Enable the alarm' ea.STATE_SET, true, false     HE->Tuya  dp=20, ENUM, true: 0, false: 1
        
        if (debug == true) {        
            command "test", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
            ]
        }
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_ntcy3xu1"    // https:www.aliexpress.com/item/1005003951429372.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_uebojraa"    // https://community.hubitat.com/t/tuya-zigbee-smart-smoke-detector-support/102471
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_t5p1vj8r"    // not tested
        //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_yh7aoahi"    // https://github.com/Koenkk/zigbee2mqtt/issues/11119 silence = Code 16; smoke detection state = code 1; Fault Alarm = Code 11; battery level state = code 14; battery level = Code 15;
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_5d3vhjro"    // 'SA12IZL'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_aycxwiau"    // TuyaIasZone ?
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A",     model:"TS0601", manufacturer:"_TZE200_vzekyi4c"    // TuyaIasZone ?
  
    }
    
    preferences {
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
    }
}

// Constants
@Field static final Integer presenceCountTreshold = 12
@Field static final Integer defaultPollingInterval = 3600
@Field static String UNKNOWN = "UNKNOWN"

private getCLUSTER_TUYA()       { 0xEF00 }
private getTUYA_ELECTRICIAN_PRIVATE_CLUSTER() { 0xE001 }
private getSETDATA()            { 0x00 }
private getSETTIME()            { 0x24 }

// tuya DP type
private getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits

def isTS0601() { return device.getDataValue('model') in ['TS0001'] }

def parse(String description) {
    if (logEnable==true) {log.debug "${device.displayName} description is $description"}
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()    // powerSource event
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {
        return null
    }
    def event = [:]
    try {
        event = zigbee.getEvent(description)
    }
    catch ( e ) {
        log.warn "exception caught while parsing description:  ${description}"
        //return null
    }
    if (event) {
        if (event.name ==  "switch" ) {
            if (logEnable==true) log.debug "${device.displayName} event ${event}"
            switchEvent( event.value )
        }
        else {
            if (txtEnable) {log.warn "${device.displayName} received <b>unhandled event</b> ${event.name} = $event.value"} 
        }
        //return null //event
    }
    else {
        //List result = []
        def descMap = [:]
        try {
            descMap = zigbee.parseDescriptionAsMap(description)
        }
        catch ( e ) {
            log.warn "${device.displayName} exception caught while parsing descMap:  ${descMap}"
            //return null
        }
        if (logEnable==true) {log.debug "${device.displayName} Desc Map: $descMap"}
        if (descMap.attrId != null ) {
            // attribute report received
            List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
            descMap.additionalAttrs.each {
                attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
            }
            attrData.each {
                def map = [:]
                if (it.status == "86") {
                    if (logEnable==true) log.warn "${device.displayName} Read attribute response: unsupported Attributte ${it.attrId} cluster ${descMap.cluster}"
                }
                else if ( it.cluster == "0000" && it.attrId in ["0001", "FFE0", "FFE1", "FFE2", "FFE4", "FFFE", "FFDF"]) {
                    if (it.attrId == "0001") {
                        if (logEnable) log.debug "${device.displayName} Tuya check-in message (attribute ${it.attrId} reported: ${it.value})"
                    }
                    else {
                        if (logEnable) log.debug "${device.displayName} Tuya specific attribute ${it.attrId} reported: ${it.value}"    // not tested
                    }
                }
                else if ( it.cluster == "0000" ) {
                    if (it.attrId == "0000") {
                        if (logEnable) log.debug "${device.displayName} zclVersion is :  ${it.value}"
                    }
                    else if (it.attrId == "0004") {
                        if (logEnable) log.debug "${device.displayName} Manufacturer is :  ${it.value}"
                    }
                    else if (it.attrId == "0005") {
                        if (logEnable) log.debug "${device.displayName} Model is :  ${it.value}"
                    }
                    else {
                        if (logEnable) log.debug "${device.displayName} Cluster 0000 attribute ${it.attrId} reported: ${it.value}"
                    }
                }
                else {
                    if (logEnable==true) log.warn "${device.displayName} Unprocessed attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
                }
            } // for each attribute
        } // if attribute report
        else if (descMap.profileId == "0000") { //zdo
            parseZDOcommand(descMap)
        } 
        else if (descMap.clusterId != null && descMap.profileId == "0104") { // ZHA global command
            parseZHAcommand(descMap)
        } 
        else {
            if (logEnable==true)  log.warn "${device.displayName} Unprocesed unknown command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
        }
        //return null //result
    } // descMap
}




def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : // device announcement
            if (logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
        case "8001" :  // Device and Service Discovery - IEEE_addr_rsp
            if (logEnable) log.info "${device.displayName} Received Device and Service Discovery - IEEE_addr_rsp, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            break
            break
        case "8004" : // simple descriptor response
            if (logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            parseSimpleDescriptorResponse( descMap )
            break
        case "8005" : // endpoint response
            if (logEnable) log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}"
            break
        case "8021" : // bind response
            if (logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8038" : // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}"
            break
        default :
            if (logEnable) log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

def parseSimpleDescriptorResponse(Map descMap) {
    //log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
    if (logEnable==true) log.info "${device.displayName} Endpoint: ${descMap.data[5]} Application Device:${descMap.data[9]}${descMap.data[8]}, Application Version:${descMap.data[10]}"
    def inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    def inputClusterList = ""
    for (int i in 1..inputClusterCount) {
        inputClusterList += descMap.data[13+(i-1)*2] + descMap.data[12+(i-1)*2] + ","
    }
    inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
    if (logEnable==true) log.info "${device.displayName} Input Cluster Count: ${inputClusterCount} Input Cluster List : ${inputClusterList}"
    if (getDataValue("inClusters") != inputClusterList)  {
        if (logEnable==true) log.warn "${device.displayName} inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!"
        updateDataValue("inClusters", inputClusterList)
    }
    
    def outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12+inputClusterCount*2])
    def outputClusterList = ""
    for (int i in 1..outputClusterCount) {
        outputClusterList += descMap.data[14+inputClusterCount*2+(i-1)*2] + descMap.data[13+inputClusterCount*2+(i-1)*2] + ","
    }
    outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
    if (logEnable==true) log.info "${device.displayName} Output Cluster Count: ${outputClusterCount} Output Cluster List : ${outputClusterList}"
    if (getDataValue("outClusters") != outputClusterList)  {
        if (logEnable==true) log.warn "${device.displayName} outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} -  will be updated!"
        updateDataValue("outClusters", outputClusterList)
    }
}

def parseZHAcommand( Map descMap) {
    switch (descMap.command) {
        case "01" : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
        case "02" :
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0] 
            if (status == "86") {
                if (logEnable==true) log.warn "${device.displayName} Read attribute response: unsupported Attributte ${attrId} cluster ${descMap.clusterId}  descMap = ${descMap}"
            }
            else {
                switch (descMap.clusterId) {
                    case "EF00" :
                        if (logEnable==true) log.debug "${device.displayName} Tuya cluster read attribute response: code ${status} Attributte ${attrId} cluster ${descMap.clusterId} data ${descMap.data}"
                        def cmd = descMap.data[2]
                        def value = getAttributeValue(descMap.data)
                        //if (logEnable==true) log.trace "${device.displayName} Tuya cluster cmd=${cmd} value=${value} ()"
                        def map = [:]
                        switch (cmd) {
                            case "01" : // smoke alarm for all models
                                if (txtEnable==true) log.info "${device.displayName} smoke alarm (dp=${cmd}) is: ${value}"
                                sendSmokeAlarmEvent( value )
                                break
                            case "04" : // "TamperAlert" for all models
                                if (txtEnable==true) log.info "${device.displayName} tamper alert (dp=${cmd}) is: ${value}"
                                sendTamperAlertEvent( value )
                                break
                            case "0B" : // (11) "Fault Alarm" for _TZE200_yh7aoahi
                                if (txtEnable==true) log.info "${device.displayName} 'silence' state (dp=${cmd}) is: ${value}"
                                sendBatteryStateEvent( value )
                                break
                            case "0E" : // (14) "battery level state" ['low', 'middle', 'high'] dp14 0=25% 1=50% 2=90% also for _TZE200_yh7aoahi 
                                if (txtEnable==true) log.info "${device.displayName} Battery level state (dp=${cmd}) is: ${value}"
                                sendBatteryStateEvent( value )
                                break
                            case "0F" : // (15) "battery level % for _TZE200_yh7aoahi 
                                if (txtEnable==true) log.info "${device.displayName} Battery level % (dp=${cmd}) is: ${value}%"
                                // TODO - send batteryLevel event!
                                break
                            case "10" : // (16) "silence" for _TZE200_yh7aoahi
                                if (txtEnable==true) log.info "${device.displayName} 'silence' state (dp=${cmd}) is: ${value}"
                                sendBatteryStateEvent( value )
                                break
                            default :
                                if (logEnable==true) log.warn "Tuya unknown attribute: ${descMap.data[0]}${descMap.data[1]}=${descMap.data[2]}=${descMap.data[3]}${descMap.data[4]} data.size() = ${descMap.data.size()} value: ${value}}"
                                if (logEnable==true) log.warn "map= ${descMap}"
                                break
                        }
                        break
                    default :
                        if (logEnable==true) log.warn "${device.displayName} Read attribute response: unknown status code ${status} Attributte ${attrId} cluster ${descMap.clusterId}"
                        break
                } // switch (descMap.clusterId)
            }  //command is read attribute response 01 or 02 (Tuya)
            break
        case "07" : // Configure Reporting Response
            if (logEnable==true) log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "0B" : // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                switch (descMap.clusterId) {
                    case "0006" : // Switch state
                        if (logEnable==true) log.warn "${device.displayName} Switch state is not supported -> Switch polling will be disabled."
                        state.switchPollingSupported = false
                        break
                    default :
                        if (logEnable==true) log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
                        break
                }
            }
            break
        case "11" :    // Tuya specific
            if (logEnable==true) log.info "${device.displayName} Tuya specific command: cluster=${descMap.clusterId} command=${descMap.command} data=${descMap.data}"
            break
        case "24" :    // Tuya time sync
            //log.trace "Tuya time sync"
            if (descMap?.clusterInt==0xEF00 && descMap?.command == "24") {        //getSETTIME
                if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
                def offset = 0
                try {
                    offset = location.getTimeZone().getOffset(new Date().getTime())
                    //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
                }
                catch(e) {
                    if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
                }
                def cmds = zigbee.command(0xEF00, 0x24, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
                if (settings?.logEnable) log.trace "${device.displayName} now is: ${now()}"  // KK TODO - convert to Date/Time string!        
                if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
                cmds.each { 
                    sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) 
                }
                return
            }
            break
        default :
            if (logEnable==true) log.warn "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

private int getAttributeValue(ArrayList _data) {
    int retValue = 0
    try {    
        if (_data.size() >= 6) {
            int dataLength = zigbee.convertHexToInt(_data[5]) as Integer
            int power = 1;
            for (i in dataLength..1) {
                retValue = retValue + power * zigbee.convertHexToInt(_data[i+5])
                power = power * 256
            }
        }
    }
    catch(e) {
        log.error "${device.displayName} Exception caught : data = ${_data}"
    }
    return retValue
}

def sendSmokeAlarmEvent( value, isDigital=false ) {    // attributes: smoke ("detected","clear","tested")    ea.STATE, true, false).withDescription('Smoke alarm status'),  [dp=1] 
    def map = [:]
    map.value = value==0 ? "detected" : value==1 ? "clear" : value==2 ? "tested" : null
    map.name = "smoke"
    map.unit = ""
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${map.value}"
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}

def sendTamperAlertEvent( value, isDigital=false ) {    // attributes: tamper - ENUM ["clear", "detected"]    [dp=4 ]  values 1/0
    def map = [:]
    map.value = value==0 ? "clear" : value==1 ? "detected" : null
    map.name = "tamper"
    map.unit = ""
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${map.value}"
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}

def sendBatteryStateEvent( value, isDigital=false ) {    // ea.STATE, ['low', 'middle', 'high']).withDescription('Battery level state'),    dp14 0=25% 1=50% 2=90% [dp=14] battery low   value 2 (FULL)
    def map = [:]
    map.value = value==0 ? 25 : value==1 ? "50" : value==2 ? "100" : null
    map.name = "battery"
    map.unit = "%"
    map.type = isDigital == true ? "digital" : "physical"
    map.isStateChange = true
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.descriptionText}"}
    sendEvent(map)
}


def silenceSiren( state ) {    //  command "silenceSiren"  'Silence the siren' ea.STATE_SET, true, false)    HE->Tuya  dp=16, BOOL
    if (logEnable) {log.debug "${device.displayName} silenceSiren ${state}"}
    ArrayList<String> cmds = []
    def dpVal = state=="true" ? 1 : state=="false" ? 0 : null
    if (dpVal != null) {
        def dpValHex = zigbee.convertToHexString(dpVal, 2)
        cmds = sendTuyaCommand("10", DP_TYPE_BOOL, dpValHex)
        sendZigbeeCommands( cmds )
    }
    else {
        if (txtEnable) {log.warn "${device.displayName} silenceSiren : please select true or false"}
    }
}

def enableAlarm( state ) {    //  command "enableAlarm"         //'Enable the alarm' ea.STATE_SET, true, false       HE->Tuya  dp=20, ENUM, true: 0, false: 1
    if (logEnable) {log.debug "${device.displayName} silenceSiren ${state}"}
    ArrayList<String> cmds = []
    def dpVal = state=="true" ? 1 : state=="false" ? 0 : null
    if (dpVal != null) {
        def dpValHex = zigbee.convertToHexString(dpVal, 2)
        cmds = sendTuyaCommand("14", DP_TYPE_ENUM, dpValHex)
        sendZigbeeCommands( cmds )
    }
    else {
        if (txtEnable) {log.warn "${device.displayName} enableAlarm : please select true or false"}
    }
}

def sendBatteryEvent( roundedPct, isDigital=false ) {
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type:  isDigital == true ? "digital" : "physical", isStateChange: true )    
}

def clear() {
    sendSmokeAlarmEvent( 1, isDigital=true )
}

def detected() {
    sendSmokeAlarmEvent( 0, isDigital=true )
}

def tested() {
    sendSmokeAlarmEvent( 2, isDigital=true )
}


def tuyaBlackMagic() {
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
}

/*
    configure() method is called: 
       *  unconditionally during the initial pairing, immediately after Installed() method
       *  when Initialize button is pressed
       *  from updated() when preferencies are saved
*/
def configure() {
    if (txtEnable==true) log.info "${device.displayName} configure().."
    runIn( defaultPollingInterval, pollPresence, [overwrite: true])
    List<String> cmds = []
    cmds += tuyaBlackMagic()
    sendZigbeeCommands(cmds)
}


// This method is called when the preferences of a device are updated.
def updated(){
    if (txtEnable==true) log.info "Updating ${device.getLabel()} (${device.getName()}) model ${state.model} "
    if (txtEnable==true) log.info "${device.displayName} Debug logging is <b>${logEnable}</b> Description text logging is  <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff, [overwrite: true])
        if (txtEnable==true) log.info "${device.displayName} Debug logging will be automatically switched off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }

    if (txtEnable==true) log.info "updated()..."
    configure()
}



void initializeVars( boolean fullInit = true ) {
    if (txtEnable==true) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0
    
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    if (fullInit == true || state.isDigital == null) state.isDigital = true
    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)


    def mm = device.getDataValue("model")
    if ( mm != null) {
        state.model = mm
        if (logEnable==true) log.trace " model = ${state.model}"
    }
    else {
        if (txtEnable==true) log.warn " Model not found, please re-pair the device!"
        state.model = UNKNOWN
    }
    def ep = device.getEndpointId()
    if ( ep  != null) {
        //state.destinationEP = ep
        if (logEnable==true) log.trace " destinationEP = ${ep}"
    }
    else {
        if (txtEnable==true) log.warn " Destination End Point not found, please re-pair the device!"
        //state.destinationEP = "01"    // fallback
    }    
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
        //log.trace "driverVersion is the same ${driverVersionAndTimeStamp()}"
    }
    else {
        if (txtEnable==true) log.debug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def logInitializeRezults() {
    if (logEnable==true) log.info "${device.displayName} Initialization finished"
}

def initialize() {
    if (txtEnable==true) log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars(fullInit = false)
    updated()            // calls also configure()
    runIn( 5, logInitializeRezults, [overwrite: true])
}

// This method is called when the device is first created.
def installed() {
    if (txtEnable==true) log.info "${device.displayName} Installed()..."
    initializeVars()
    def descText = 'driver just installed'
    sendEvent(name: 'smoke', value: 'unknown', descriptionText: descText, type:  'digital' , isStateChange: true )    
    sendEvent(name: "presence", value: "unknown", descriptionText: descText, type:  'digital' , isStateChange: true )
    sendEvent(name: "powerSource", value: "unknown", descriptionText: descText, type:  'digital' , isStateChange: true )    
    runIn( 5, initialize, [overwrite: true])
    if (logEnable==true) log.debug "calling initialize() after 5 seconds..."
    // HE will autoomaticall call configure() method here
}

void uninstalled() {
    if (logEnable==true) log.info "${device.displayName} Uninstalled()..."
    unschedule()     //Unschedule any existing schedules
}


// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if (device.currentValue("presence", true) != "present") {
    	sendEvent(name: "presence", value: "present") 
    	sendEvent(name: "powerSource", value: "battery") 
    }
    state.notPresentCounter = 0
}

// called from pollPresence()
def checkIfNotPresent() {
    if (state.notPresentCounter != null) {
        state.notPresentCounter = state.notPresentCounter + 1
        if (state.notPresentCounter >= presenceCountTreshold) {
            if (device.currentValue("presence", true) != "not present") {
    	        sendEvent(name: "presence", value: "not present")
    	        sendEvent(name: "powerSource", value: "unknown")
                if (logEnable==true) log.warn "${device.displayName} not present!"
            }
        }
    }
    else {
        state.notPresentCounter = 1
    }
}

// check for device offline every 60 minutes
def pollPresence() {
    if (logEnable) log.debug "${device.displayName} pollPresence()"
    checkIfNotPresent()
    runIn( defaultPollingInterval, pollPresence, [overwrite: true])
}



private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [:], delay=200, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.debug "${device.displayName} <b>sendZigbeeCommands</b> (cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    sendHubCommand(allActions)
}


def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value:"false",type:"bool"])
}

boolean isTuyaE00xCluster( String description )
{
    if(!(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) {
        return false 
    }
    // try to parse ...
    if (logEnable) log.debug "${device.displayName}  Tuya cluster: E000 or E001 - try to parse it..."
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
        log.warn "${device.displayName} <b>exception</b> caught while parsing description:  ${description}"
        if (logEnable==true) log.debug "${device.displayName} TuyaE00xCluster Desc Map: ${descMap}"
        // cluster E001 is the one that is generating exceptions...
        return true
    }
    if (logEnable==true) {log.debug "${device.displayName} TuyaE00xCluster Desc Map: $descMap"}
    
    //
    return true
}

boolean otherTuyaOddities( String description )
{
    return false
}

def test( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue
    log.warn " sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}    
 

