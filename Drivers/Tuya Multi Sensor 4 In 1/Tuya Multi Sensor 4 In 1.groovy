/**
 *  Tuya Multi Sensor 4 In 1 driver for Hubitat
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
 * ver. 1.0.0 2022-04-16 kkossev  - Inital test version
 * ver. 1.0.1 2022-04-18 kkossev  - IAS cluster multiple TS0202, TS0210 and RH3040 Motion Sensors fingerprints; 
 *
*/

def version() { "1.0.1" }
def timeStamp() {"2022/04/18 8:02 AM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.zigbee.clusters.iaszone.ZoneStatus

metadata {
    definition (name: "Tuya Multi Sensor 4 In 1", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://github.com/kkossev/Hubitat/blob/main/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya%20Multi%20Sensor%204%20In%201.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "Battery"
        capability "MotionSensor"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"
        capability "TamperAlert"
        capability "Refresh"
        
        command "configure", [[name: "Manually initialize the sensor after switching drivers" ]]
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00",      outClusters:"0019,000A", model:"TS0202", manufacturer:"_TZ3210_zmy9hjay", deviceJoinName: "Tuya Multi Sensor 4 In 1"          //
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00",      outClusters:"0019,000A", model:"5j6ifxj", manufacturer:"_TYST11_i5j6ifxj", deviceJoinName: "Tuya Multi Sensor 4 In 1"       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0500,EF00",      outClusters:"0019,000A", model:"hfcudw5", manufacturer:"_TYST11_7hfcudw5", deviceJoinName: "Tuya Multi Sensor 4 In 1"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya Multi Sensor 3 In 1"          // KK
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mrf6vtua", deviceJoinName: "Tuya Multi Sensor 3 In 1"          // not tested
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_7hfcudw5", deviceJoinName: "Tuya Multi Sensor 3 In 1"          // not tested

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_auin8mzr", deviceJoinName: "Tuya Multi Sensor 2 In 1"          // https://zigbee.blakadder.com/Tuya_LY-TAD-K616S-ZB.html // Model LY-TAD-K616S-ZB
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_vrfecyku", deviceJoinName: "Tuya Human presence sensor"        // fz.tuya_radar_sensor
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00",      outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lu01t0zl", deviceJoinName: "Tuya Human presence sensor with fall function"   // fz.tuya_radar_sensor_fall     // fz.tuya_radar_sensor
       
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_dl7cejts", deviceJoinName: "Tuya TS0202 Motion Sensor"  // 5 seconds (!) reset period for testing
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_mmtwjmaq", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_otvn3lne", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_jytabjkb", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_ef5xlc9q", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_vwqnz1sn", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_2b8f6cio", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZE200_bq5c8xfe", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_qjqgmqxr", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_kmh5qpmb", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_zwvaj5wy", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_bsvqrxru", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_tv3wxhcz", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TYZB01_hqbdru35", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_tiwq83wk", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_ykwcwxmz", deviceJoinName: "Tuya TS0202 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"WHD02",  manufacturer:"_TZ3000_hktqahrq", deviceJoinName: "Tuya TS0202 Motion Sensor"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_mcxw5ehu", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"    // TODO: PIR sensor sensitivity and PIR keep time in seconds
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0202", manufacturer:"_TZ3000_msl6wxk9", deviceJoinName: "Tuya TS0202 ZM-35H-Q Motion Sensor"    // TODO: fz.ZM35HQ_attr
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TS0210", manufacturer:"_TYZB01_3zv6oleo", deviceJoinName: "Tuya TS0210  Motion Sensor"

        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0500",      outClusters:"0000,0003,0001,0500", model:"TY0202", manufacturer:"_TZ1800_fcdjzz3s", deviceJoinName: "Lidl TY0202 Motion Sensor"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-53o41joc", deviceJoinName: "TUYATEC RH3040 Motion Sensor"       // 60 seconds reset period        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-b5g40alm", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-deetibst", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-bd5faf9p", deviceJoinName: "Nedis/Samotech RH3040 Motion Sensor"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-zn9wyqtr", deviceJoinName: "Samotech RH3040 Motion Sensor"        // vendor: 'Samotech', model: 'SM301Z'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-b3ov3nor", deviceJoinName: "Zemismart RH3040 Motion Sensor"       // vendor: 'Nedis', model: 'ZBSM10WT'
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500",                               model:"RH3040", manufacturer:"TUYATEC-2gn2zf9e", deviceJoinName: "TUYATEC RH3040 Motion Sensor"
    }
    preferences {
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display sensor states in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
		input "motionReset", "number", title: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 0 seconds (disabled)", description: "", range: "0..7200", defaultValue: 0
    }
}

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
    if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} power cluster not parsed attrint $descMap.attrInt"
            }
        }     
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            def rawLux = Integer.parseInt(descMap.value,16)
            illuminanceEvent( rawLux )
		}  
		else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            temperatureEvent( raw / 10.0 )
		}
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            humidityEvent( raw / 1.0 )
		}
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        } 
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            if (settings?.logEnable) log.info "${device.displayName} device announcement"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "0001") {
            if (settings?.logEnable) log.info "${device.displayName} application version is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFDF") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya check-in"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE2") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya AppVersion is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFE4") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya UNKNOWN attribute FFE4 value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0000" && descMap?.attrId == "FFFE") {
            if (settings?.logEnable) log.info "${device.displayName} Tuya UNKNOWN attribute FFFE value is ${descMap?.value}"
        } 
        else if (descMap?.cluster == "0500" && descMap?.command in ["01", "0A"] ) {    //read attribute response
            if (settings?.logEnable) log.debug "${device.displayName} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}"
            if (descMap?.attrId == "0002") {
                if (settings?.logEnable) log.debug "Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
                handleMotion(Integer.parseInt(descMap?.value, 16))
            } else if (descMap?.attrId == "000B") {
                if (settings?.logEnable) log.debug "IAS Zone ID: ${descMap.value}" 
            }
            else {
                if (settings?.logEnable) log.warn "Zone status: NOT PROCESSED ${descMap}" 
            }
        } 
        else if (descMap?.clusterId == "0500" && descMap?.command == "04") {    //write attribute response
            if (settings?.logEnable) log.debug "${device.displayName} IAS enroll write attribute response is ${descMap?.data[0] == "00" ? "success" : "failure"}"
        } 
        else {
            if (settings?.logEnable) log.debug "${device.displayName} <b> NOT PARSED </b> : descMap = ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {	
        if (settings?.logEnable) log.debug "Zone status: $description"
        parseIasMessage(description)    // TS0202 Motion sensor
    }
    else if (description?.startsWith('enroll request')) {
         /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) log.info "Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        if (settings?.logEnable) log.debug "enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    }    
    else {
        if (settings?.logEnable) log.debug "${device.displayName} <b> UNPROCESSED </b> description = ${description} descMap = ${zigbee.parseDescriptionAsMap(description)}"
    }
}


def processTuyaCluster( descMap ) {
    if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        if (settings?.logEnable) log.debug "${device.displayName} time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch(e) {
            if (settings?.logEnable) log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        if (settings?.logEnable) log.trace "${device.displayName} now is: ${now()}"  // KK TODO - convert to Date/Time string!        
        if (settings?.logEnable) log.debug "${device.displayName} sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt==CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        if (settings?.logEnable) log.debug "${device.displayName} device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"|| descMap?.command == "06"))
    {
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        switch (dp) {
            case 0x65 : //  Tuya 3 in 1 (101) -> motion (ocupancy)
                if (settings?.logEnable) log.trace "motion event 0x65 fncmd = ${fncmd}"
                sendEvent(handleMotion(motionActive=fncmd))
                break            
            case 0x66 : 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // // case 102 //reporting time for 4 in 1 
                    if (settings?.txtEnable) log.info "${device.displayName} reporting time is ${fncmd}"
                }
                else {     // battery for 3 in 1;  
                 if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                    def rawValue = 0
                    if (fncmd == 0) rawValue = 100           // Battery Full
                    else if (fncmd == 1) rawValue = 75       // Battery High
                    else if (fncmd == 2) rawValue = 50       // Battery Medium
                    else if (fncmd == 3) rawValue = 25       // Battery Low
                    else if (fncmd == 4) rawValue = 100      // Tuya 3 in 1 -> USB powered ! -> PowerSource = USB     capability "PowerSource" Attributes powerSource - ENUM ["battery", "dc", "mains", "unknown"]
                    else {
                        rawValue = fncmd
                    }
                    getBatteryPercentageResult(rawValue*2)
                }
                break
            case 0x6E : // (110) Tuya 4 in 1
                if (settings?.logEnable) log.trace "${device.displayName} Tuya battery status report dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
                def rawValue = 0
                if (fncmd == 0) rawValue = 100           // Battery Full
                else if (fncmd == 1) rawValue = 75       // Battery High
                else if (fncmd == 2) rawValue = 50       // Battery Medium
                else if (fncmd == 3) rawValue = 25       // Battery Low
                else if (fncmd == 4) rawValue = 100      // Tuya 3 in 1 -> USB powered !
                else {
                    rawValue = fncmd
                }
                getBatteryPercentageResult(rawValue*2)
                break 
            case 0x67 : //  Tuya 3 in 1 (103) -> tamper
                def value = fncmd==0 ? 'clear' : 'detected'
                if (settings?.txtEnable) log.info "${device.displayName} tamper alarm is ${value}"
            	sendEvent(name : "tamper",	value : value, isStateChange : true)
                break            
            case 0x68 : 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 104: // 0x68 temperature calibration
                    def val = fncmd;
                    // for negative values produce complimentary hex (equivalent to negative values)
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} temperature calibration is ${val / 10.0}"
                }
                else {    //  Tuya 3 in 1 (104) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                break            
            case 0x69 : 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 105:// 0x69 humidity calibration
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} humidity calibration is ${val}"                
                }
                else {    //  Tuya 3 in 1 (105) -> humidity in %
                    humidityEvent (fncmd)
                }
                break
            case 0x6A : 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    // case 106: // 0x6a lux calibration
                    def val = fncmd;
                    if (val > 4294967295) val = val - 4294967295;                    
                    if (settings?.txtEnable) log.info "${device.displayName} lux calibration is ${val}"                
                }
                else {    //  Tuya 3 in 1 (105) -> humidity in %
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (lux calibration?) DP=0x6A fncmd = ${fncmd}"  
                }
                break
            case 0x6B : 
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {    //  Tuya 4 in 1 (107) -> temperature in °C
                    temperatureEvent( fncmd / 10.0 )
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (?) DP=0x6B fncmd = ${fncmd}"  
                }
                break            
            case 0x6C : //  Tuya 4 in 1 (108) -> humidity in %
                humidityEvent (fncmd)
                break
            case 0x6D :
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 109: 0x6d PIR enable
                    if (settings?.txtEnable) log.info "${device.displayName} PIR enable is ${fncmd}"                
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (PIR enable?) DP=0x6D fncmd = ${fncmd}"  
                }
                break
            case 0x6F :
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 111: // 0x6f led enable
                    if (settings?.txtEnable) log.info "${device.displayName} led enable is ${fncmd}"                
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName}  <b>UNKNOWN</b> (led enable?) DP=0x6F fncmd = ${fncmd}"  
                }
                break
            case 0x70 :
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 112: 0x70 reporting enable
                    if (settings?.txtEnable) log.info "${device.displayName} reporting enable is ${fncmd}"                
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x70 reporting enable?) DP=0x70 fncmd = ${fncmd}"  
                }
                break
            case 0x71 :
                if ( device.getDataValue('manufacturer') == '_TZ3210_zmy9hjay') {   // case 113: 0x71 unknown  ( ENUM)
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"  
                }
                else {
                    if (settings?.logEnable) log.info "${device.displayName} <b>UNKNOWN</b> (0x71 reporting enable?) DP=0x71 fncmd = ${fncmd}"  
                }
                break
            default :
                /*if (settings?.logEnable)*/ log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
    } // Tuya commands '01' and '02'
    else {
        if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya <b>descMap?.command = ${descMap?.command}</b> cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
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

def parseIasReport(Map descMap) {
    if (settings?.logEnable) log.debug "pareseIasReport: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}"
    def zs = new ZoneStatus(Integer.parseInt(descMap?.value, 16))
    log.trace "zs = ${zs}"
    if (settings?.logEnable) {
        log.debug "zs.alarm1 = $zs.alarm1"
        log.debug  "zs.alarm2 = $zs.alarm2"
        log.debug  "zs.tamper = $zs.tamper"
        log.debug  "zs.battery = $zs.battery"
        log.debug  "zs.supervisionReports = $zs.supervisionReports"
        log.debug  "zs.restoreReports = $zs.restoreReports"
        log.debug  "zs.trouble = $zs.trouble"
        log.debug  "zs.ac = $zs.ac"
        log.debug  "zs.test = $zs.test"
        log.debug  "zs.batteryDefect = $zs.batteryDefect"
    }    
    handleMotion(zs.alarm1)  
}
       

def parseIasMessage(String description) {
    // https://developer.tuya.com/en/docs/iot-device-dev/tuya-zigbee-water-sensor-access-standard?id=K9ik6zvon7orn 
    try {
        Map zs = zigbee.parseZoneStatusChange(description)
        if (settings?.logEnable) log.trace "zs = $zs"
        if (zs.alarm1Set == true) {
            handleMotion(motionActive=true)
        }
        else {
            handleMotion(motionActive=false)
        }
    }
    catch (e) {
        log.error "This driver requires HE version 2.2.7 (May 2021) or newer!"
        return null
    }
}

private handleMotion(motionActive) {    
    if (motionActive) {
        def timeout = motionReset ?: 0
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in code
        if (timeout != 0) {
            runIn(timeout, resetToMotionInactive)
        }
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = now()
        }
    }
    else {
        //log.warn "handleMotion: no motion active?"
    }
	return getMotionResult(motionActive)
}

def getMotionResult(motionActive) {
	def descriptionText = "Detected motion"
    if (!motionActive) {
		descriptionText = "Motion reset to inactive after ${getSecondsInactive()}s"
    }
    if (settings?.txtEnable) log.info " ${descriptionText}"
	return [
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            //isStateChange   : true,
			descriptionText : descriptionText
	]
}

def resetToMotionInactive() {
	if (device.currentState('motion')?.value == "active") {
		def descText = "Motion reset to inactive after ${getSecondsInactive()}s"
		sendEvent(
			name : "motion",
			value : "inactive",
			isStateChange : true,
			descriptionText : descText
		)
        if (settings?.txtEnable) log.info " ${descText}"
	}
}

def getSecondsInactive() {
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
    } else {
        return motionReset ?: 0
    }
}



def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    map.unit = "\u00B0"+"C"
    map.value  =  Math.round((temperature - 0.05) * 10) / 10
    map.isStateChange = true
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.name} is ${map.value} ${map.unit}"}
    sendEvent(map)
}

def humidityEvent( humidity ) {
    def map = [:] 
    map.name = "humidity"
    map.value = humidity as int
    map.unit = "% RH"
    map.isStateChange = true
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.name} is ${Math.round((humidity) * 10) / 10} ${map.unit}"}
    sendEvent(map)
}

def illuminanceEvent( illuminance ) {
    //def rawLux = Integer.parseInt(descMap.value,16)
	def lux = illuminance > 0 ? Math.round(Math.pow(10,(illuminance/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lux"/*, isStateChange: true*/)
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}


// called on initial install of device during discovery
// also called from initialize() in this driver!
def installed() {
    log.info "${device.displayName} installed()"
    unschedule()
}

// called when preferences are saved
// runs when save is clicked in the preferences section
def updated() {
    //ArrayList<String> cmds = []
    
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b>"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 24 hours
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    /*
    cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
   	cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected    
    if (settings?.txtEnable) log.info "${device.displayName} Update finished"
    sendZigbeeCommands( cmds )  
  */
}


def refresh() {
    ArrayList<String> cmds = []
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    cmds += zigbee.readAttribute(0, 1)
    sendZigbeeCommands( cmds ) 
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
    }
    else {
        if (txtEnable==true) log.debug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def logInitializeRezults() {
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars(boolean fullInit = true ) {
    if (settings?.txtEnable) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
        state.motionStarted = now()
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0

    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || device.getDataValue("motionReset") == null) device.updateSetting("motionReset", 0)
    
}

def tuyaBlackMagic() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)
    return  cmds
}

// called when used with capability "Configuration" is called when the configure button is pressed on the device page. 
// Runs when driver is installed, after installed() is run. if capability Configuration exists, a Configure command is added to the ui
// It is also called on initial install after discovery.
def configure() {
    if (settings?.txtEnable) log.info "${device.displayName} configure().."
    state.motionStarted = now()
    List<String> cmds = []
    cmds += tuyaBlackMagic()    

    cmds += "delay 200"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0402 {${device.zigbeeId}} {}"
    cmds += "delay 200"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x02 0x01 0x0405 {${device.zigbeeId}} {}"
    cmds += "delay 200"
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x03 0x01 0x0400 {${device.zigbeeId}} {}"

    sendZigbeeCommands(cmds)    
}

// called when used with capability "Initialize" it will call this method every time the hub boots up. So for things that need refreshing or re-connecting (LAN integrations come to mind here) ..
// runs first time driver loads, ie system startup 
// when capability Initialize exists, a Initialize command is added to the ui.
def initialize() {
    log.info "${device.displayName} Initialize()..."
    unschedule()
    initializeVars()
    installed()
    updated()
    configure()
    runIn( 3, logInitializeRezults)
}

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    if (settings?.logEnable) log.trace "${device.displayName} sendTuyaCommand = ${cmds}"
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
    return cmds
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    if (settings?.logEnable) {log.trace "${device.displayName} sendZigbeeCommands(cmd=$cmd)"}
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    sendHubCommand(allActions)
}

private getPACKET_ID() {
    state.packetID = ((state.packetID ?: 0) + 1 ) % 65536
    return zigbee.convertToHexString(state.packetID, 4)
}

private getDescriptionText(msg) {
	def descriptionText = "${device.displayName} ${msg}"
	if (settings?.txtEnable) log.info "${descriptionText}"
	return descriptionText
}

def logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def getBatteryPercentageResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
    def result = [:]

    if (0 <= rawValue && rawValue <= 200) {
        result.name = 'battery'
        result.translatable = true
        result.value = Math.round(rawValue / 2)
        result.descriptionText = "${device.displayName} battery is ${result.value}%"
        result.isStateChange = true
        result.unit  = '%'
        sendEvent(result)
        if (settings?.txtEnable) log.info "${result.descriptionText}"
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

private Map getBatteryResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} batteryVoltage = ${(double)rawValue / 10.0} V"
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
        result.unit  = '%'
        result.isStateChange = true
        if (settings?.txtEnable) log.info "${result.descriptionText}"
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }    
}

