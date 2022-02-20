/**
 *  Tuya Temperature Humidity Illuminance LCD Display with a Clock
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
 * ver. 1.0.0 2022-01-02 kkossev  - Inital test version
 * ver. 1.0.1 2022-02-05 kkossev  - Added Zemismart ZXZTH fingerprint; added _TZE200_locansqn; Fahrenheit scale + rounding; temperatureScaleParameter; temperatureSensitivity; minTempAlarm; maxTempAlarm
 * ver. 1.0.2 2022-02-06 kkossev  - Tuya commands refactoring; TS0222 T/H poll on illuminance change (EP2); modelGroupPreference bug fix; dyncamic parameters
 * ver. 1.0.3 2022-02-13 kkossev  - _TZE200_c7emyjom fingerprint added; 
 * ver. 1.0.4 2022-02-20 kkossev  - Celsius/Fahrenheit correction for TS0601_Tuya devices
 *                                   TODO: force reading Temp and Humidity in Refresh() for TS0201 Neo CoolcaM ! temperature and humidity are on endpoint 2, not 1!
*/

def version() { "1.0.3" }
def timeStamp() {"2022/02/20 9:58 PM"}

import groovy.json.*
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.device.HubAction
import hubitat.device.Protocol

 
metadata {
    definition (name: "Tuya Temperature Humidity Illuminance LCD Display with a Clock", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock/Tuya%20Temperature%20Humidity%20Illuminance%20LCD%20Display%20with%20a%20Clock.groovy", singleThreaded: true ) {
        capability "Refresh"
        capability "Sensor"
        capability "Initialize"
        capability "Battery"
        capability "TemperatureMeasurement"        
        capability "RelativeHumidityMeasurement"
        capability "IlluminanceMeasurement"

        attribute "minTempAlarm", "enum", ["inactive","active"]        // (TS0601_Tuya and TS0601_Haozee only)
        attribute "maxTempAlarm", "enum", ["inactive","active"]        // (TS0601_Haozee only)
        attribute "minHumidityAlarm", "enum", ["inactive","active"]    // (TS0601_Haozee only)
        attribute "maxHumidityAlarm", "enum", ["inactive","active"]    // (TS0601_Haozee only)

/*        
        command "zTest", [
            [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
            [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
            [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] 
        ]
        command "test"
*/        
        
        command "initialize"
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_lve3dvpy", deviceJoinName: "Tuya Temperature Humidity Illuminance LCD Display with a Clock" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_c7emyjom", deviceJoinName: "Tuya Temperature Humidity Illuminance LCD Display with a Clock" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_locansqn", deviceJoinName: "Haozee Temperature Humidity Illuminance LCD Display with a Clock" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,EF00", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_bq5c8xfe", deviceJoinName: "Haozee Temperature Humidity Illuminance LCD Display with a Clock" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0402,0405", outClusters:"0019", model:"TS0201", manufacturer:"_TZ2000_hjsgdkfl", deviceJoinName: "AVATTO S-H02" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0400,E002", outClusters:"0019,000A", model:"TS0201", manufacturer:"_TZ3000_qaaysllp", deviceJoinName: "NAS-TH02B Temperature Humidity Illuminance LCD Display"  // Neo Coolcam ?  // NOT TESTED!
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0400", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_kvwjujy9", deviceJoinName: "MOES ZSS-ZK-THL" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", deviceJoinName: "Tuya Illuminance Sensor TS0222_2"  
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TZ3000_lfa05ajd", deviceJoinName: "Zemismart ZXZTH"  
    }
    preferences {

       
        input (name: "logEnable", type: "bool", title: "Debug logging", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: "txtEnable", type: "bool", title: "Description text logging", description: "<i>Display measured values in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        //input (name: "modelGroupPreference", type: "enum", title: "Model Group", description:"Recommended value is <b>Auto detect</b></i>", defaultValue: 0, options: [0:"Auto detect", 1:"TS0601_Tuya", 2:"TS0601_Haozee", 3:"TS0201", 4:"TS0222", 5:"TS0222_2", 6:"Zigbee NON-Tuya"])
        input (name: "modelGroupPreference", type: "enum", title: "Model Group", description:"Recommended value is <b>Auto detect</b></i>", defaultValue: 0, options: 
               ["Auto detect":"Auto detect", "TS0601_Tuya":"TS0601_Tuya", "TS0601_Haozee":"TS0601_Haozee", "TS0201":"TS0201", "TS0222":"TS0222", "TS0222_2":"TS0222_2", "Zigbee NON-Tuya":"Zigbee NON-Tuya"])
        input (name: "advancedOptions", type: "bool", title: "Advanced options", description: "May not be supported by all devices!", defaultValue: false)
        if (advancedOptions == true) {
            configParams.each { 
                if (it.value.input.limit == null || getModelGroup() in it.value.input.limit) {
                    //log.trace "it = ${it.value.input.limit}"
                    input it.value.input 
                }
            }
        }
    }
}

@Field static final Integer numberOfconfigParams = 8
@Field static Map configParams = [
        1: [input: [name: "temperatureScaleParameter", type: "enum", title: "Temperature Scale", description:"Auto detect or force Celsius/Fahrenheit", defaultValue: 0, options: [0:"Auto detect", 1:"Celsius", 2:"Fahrenheit"]]],
                   // no limit
        2: [input: [name: "temperatureSensitivity", type: "number", title: "Temperature Sensitivity", description: "Temperature change for reporting, °C", defaultValue: 0.5, range: "0.1..5.0",
                   limit:['TS0601_Tuya', 'TS0601_Haozee', "Zigbee NON-Tuya"]]],
    
        3: [input: [name: "humiditySensitivity", type: "decimal", title: "Humidity Sensitivity", description: "Humidity change for reporting, %", defaultValue: 5, range: "1..50",
                   limit:['TS0601_Tuya', 'TS0601_Haozee', "Zigbee NON-Tuya"]]],
    
        4: [input: [name: "illuminanceSensitivity", type: "decimal", title: "Illuminance Sensitivity", description: "Illuminance change for reporting, %", defaultValue: 12, range: "10..100",                // TS0222 "MOES ZSS-ZK-THL"
                   limit:['TS0222','TS0201']]],
    
        5: [input: [name: "minTempAlarmPar", type: "number", title: "Minimum Temperature Alarm", description: "Minimum Temperature Alarm, °C", defaultValue: 18.0, range: "-20.0..60.0",
                   limit:['TS0601_Tuya', 'TS0601_Haozee']]],
    
        6: [input: [name: "maxTempAlarmPar", type: "number", title: "Maximum Temperature Alarm", description: "Maximum Temperature Alarm, °C", defaultValue: 28.0, range: "-20.0..60.0",
                   limit:['TS0601_Tuya', 'TS0601_Haozee']]],
    
        7: [input: [name: "minHumidityAlarmPar", type: "decimal", title: "Minimal Humidity Alarm", description: "Minimum Humidity Alarm, % (TS0601_Haozee only)", defaultValue: 10, range: "0..100",           // 'TS0601_Haozee' only!
                   limit:['TS0601_Haozee']]], 
    
        8: [input: [name: "maxHumidityAlarmPar", type: "decimal", title: "Maximum Humidity Alarm", description: "Maximum Humidity Alarm, % (TS0601_Haozee only)", defaultValue: 90, range: "0..100",            // 'TS0601_Haozee' only!
                   limit:['TS0601_Haozee']]], 
    
        9: [input: [name: "minReportingTimeTemp", type: "number", title: "Minimum time between temperature reports", description: "Minimum time between temperature reporting, seconds", defaultValue: 60, range: "10..3600",
                   limit:["Zigbee NON-Tuya"]]],
    
       10: [input: [name: "maxReportingTimeTemp", type: "number", title: "Maximum time between temperature reports", description: "Maximum time between temperature reporting, seconds", defaultValue: 3600, range: "10..43200",
                   limit:['TS0601_Haozee', "Zigbee NON-Tuya"]]],
    
       11: [input: [name: "minReportingTimeHumidity", type: "number", title: "Minimum time between humidity reports", description: "Minimum time between humidity reporting, seconds", defaultValue: 60, range: "10..3600",
                   limit:["Zigbee NON-Tuya"]]],
    
       12: [input: [name: "maxReportingTimeHumidity", type: "number", title: "Maximum time between humidity reports", description: "Maximum time between humidity reporting, seconds", defaultValue: 3600, range: "10..43200",
                   limit:['TS0601_Haozee', "Zigbee NON-Tuya"]]]
    
]

@Field static final Map<String, String> Models = [
    '_TZE200_lve3dvpy'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_c7emyjom'  : 'TS0601_Tuya',         // Tuya Temperature Humidity LCD Display with a Clock
    '_TZE200_locansqn'  : 'TS0601_Haozee',       // Haozee Temperature Humidity Illuminance LCD Display with a Clock
    '_TZE200_bq5c8xfe'  : 'TS0601_Haozee',       // 
    
    '_TZ2000_a476raq2'  : 'TS0201',     
    '_TZ3000_lfa05ajd'  : 'TS0201',              // Zemismart ZXZTH
    '_TZ2000_xogb73am'  : 'TS0201',     
    '_TZ2000_avdnvykf'  : 'TS0201',     
    '_TYZB01_a476raq2'  : 'TS0201',     
    '_TYZB01_hjsgdkfl'  : 'TS0201',     
    '_TZ2000_hjsgdkfl'  : 'TS0201',             // "AVATTO S-H02"   
    '_TZ3000_qaaysllp'  : 'TS0201',             // NAS-TH02B  / NEO Coolcam ?  - testing! 

    '_TYZB01_kvwjujy9'  : 'TS0222',              // "MOES ZSS-ZK-THL" e-Ink display 
    '_TYZB01_4mdqxxnn'  : 'TS0222_2',            // illuminance only sensor
    
    ''                  : 'UNKNOWN'              // 
]




@Field static final Integer MaxRetries = 3
                                
// KK TODO !
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
    //if (settings?.logEnable) log.debug "${device.displayName} parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
    if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap.clusterInt == 0x0001 && descMap.commandInt != 0x07 && descMap?.value) {
            if (descMap.attrInt == 0x0021) {
                getBatteryPercentageResult(Integer.parseInt(descMap.value,16))
            } else if (descMap.attrInt == 0x0020){
                log.trace "descMap.attrInt == 0x0020"
                getBatteryResult(Integer.parseInt(descMap.value, 16))
            }
            else {
                log.warn "unparesed attrint $descMap.attrInt"
            }
        }     
		else if (descMap.cluster == "0400" && descMap.attrId == "0000") {
            def rawLux = Integer.parseInt(descMap.value,16)
            illuminanceEvent( rawLux )
            if (getModelGroup() == 'TS0222') {
                pollTS0222()
            }
		}        
		else if (descMap.cluster == "0400" && descMap.attrId == "F001") {        //MOES ZSS-ZK-THL, also TS0201 Neo Coolcam!
            def raw = Integer.parseInt(descMap.value,16)
            if (settings?.txtEnable) log.info "${device.displayName} illuminance sensitivity is ${raw} Lux"
            device.updateSetting("illuminanceSensitivity", [value:raw, type:"decimal"])		
        }        
		else if (descMap.cluster == "0402" && descMap.attrId == "0000") {
            if (getModelGroup() != 'TS0222_2') {
                def raw = Integer.parseInt(descMap.value,16)
                temperatureEvent( raw / 100.0 )
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} Ignoring ${getModelGroup()} temperature event"
            }
		}
        else if (descMap.cluster == "0405" && descMap.attrId == "0000") {
            def raw = Integer.parseInt(descMap.value,16)
            humidityEvent( raw / 100.0 )
		}
        else if (descMap?.clusterInt == CLUSTER_TUYA) {
            processTuyaCluster( descMap )
        } 
        else if (descMap?.clusterId == "0013") {    // device announcement, profileId:0000
            if (getModelGroup() == 'TS0222') {
                log.warn "TS0222 device announcement"
                configure()
            }
        } 
        else if (descMap.isClusterSpecific == false && descMap.command == "01" ) { //global commands read attribute response
            def status = descMap.data[2]
            if (status == "86") {
                if (settings?.logEnable) log.warn "${device.displayName} Cluster ${descMap.clusterId} read attribute - NOT SUPPORTED!\r ${descMap}"
            }
            else {
                if (settings?.logEnable) log.warn "${device.displayName} <b>UNPROCESSED Global Command</b> :  ${descMap}"
            }
        }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} <b> NOT PARSED </b> :  ${descMap}"
        }
    } // if 'catchall:' or 'read attr -'
    else {
        if (settings?.logEnable) log.debug "${device.displayName} <b> UNPROCESSED </b> parse() descMap = ${zigbee.parseDescriptionAsMap(description)}"
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
            if (settings?.logEnable) log.warn "${device.displayName} ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} group = ${getModelGroup()} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt==CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02"))
    {
        //if (descMap?.command == "02") { if (settings?.logEnable) log.warn "${device.displayName} command == 02 !"  }   
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        def dp = zigbee.convertHexToInt(descMap?.data[2])                // "dp" field describes the action/message of a command frame
        def dp_id = zigbee.convertHexToInt(descMap?.data[3])             // "dp_identifier" is device dependant
        def fncmd = getTuyaAttributeValue(descMap?.data)                 // 
        if (settings?.logEnable) log.trace "${device.displayName}  dp_id=${dp_id} dp=${dp} fncmd=${fncmd}"
        // the switch cases below default to dp_id = "01"
        switch (dp) {
            case 0x01 : // temperature in °C
                temperatureEvent( fncmd / 10.0 )
                break
            case 0x02 : // humidity in %
                humidityEvent (fncmd)
                break 
            case 0x03 : // illuminance - NOT TESTED!
                illuminanceEvent (fncmd)
                break 
            case 0x04 : // battery
                getBatteryPercentageResult(fncmd * 2)
                if (settings?.txtEnable) log.info "${device.displayName} battery is $fncmd %"
                break
            case 0x09: // temp. scale  1=Fahrenheit 0=Celsius (TS0601 Tuya and Haoze) TS0601_Tuya does not change the symbol on the LCD !
                if (settings?.txtEnable) log.info "${device.displayName} Temperature scale reported by device is: ${fncmd == 1 ? 'Fahrenheit' :'Celsius' }"
                break
            case 0x0A: // Max. Temp Alarm, Value / 10  (both TS0601_Tuya and TS0601_Haozee)
                if (settings?.txtEnable) log.info "${device.displayName} temperature alarm upper limit reported by device is: ${fncmd/10.0 as double} °C"
                device.updateSetting("maxTempAlarmPar", [value:fncmd/10.0 as double, type:"number"])
                break
            case 0x0B: // Min. Temp Alarm, Value / 10 (both TS0601_Tuya and TS0601_Haozee)
                if (settings?.txtEnable) log.info "${device.displayName} temperature alarm lower limit reported by device is: ${fncmd/10.0 as double} °C "
                device.updateSetting("minTempAlarmPar", [value:fncmd/10.0 as double, type:"number"])
                break
            case 0x0C: // Max?. Humidity Alarm    (Haozee only?)
                if (settings?.txtEnable) log.info "${device.displayName} humidity alarm upper limit is ${fncmd} "
                device.updateSetting("maxHumidityAlarmPar", [value:fncmd, type:"number"])
                break
            case 0x0D: // Min?. Humidity Alarm    (Haozee only?)
                if (settings?.txtEnable) log.info "${device.displayName} humidity alarm lower limit is ${fncmd} "
                device.updateSetting("minHumidityAlarmPar", [value:fncmd, type:"number"])
                break
            case 0x0E: // Temperature Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared
                if (fncmd == 1) {
                    sendEvent("name": "minTempAlarm", "value": "active")
                    if (settings?.txtEnable) log.info "${device.displayName} Minimal Temperature Alarm (0x0E=${fncmd}) is active"
                }
                else if (fncmd == 0) {    // TS0601_Haozee only?
                    sendEvent("name": "maxTempAlarm", "value": "active")
                    if (settings?.txtEnable) log.info "${device.displayName} Maximal Temperature Alarm (0x0E=${fncmd}) is active"
                }
                else if (fncmd == 2 ) {
                    sendEvent("name": "minTempAlarm", "value": "inactive")
                    if (getModelGroup() in ['TS0601_Haozee']) {
                        sendEvent("name": "maxTempAlarm", "value": "inactive")
                    }
                    if (settings?.txtEnable) log.info "${device.displayName} Temperature Alarms (0x0E=${fncmd}) are inactive"
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" // 1 if alarm (lower alarm) ? 2 if lower alam is cleared
                }
                break
            case 0x0F: // humidity Alarm 0 = low alarm? 1 = high alarm? 2 = alarm cleared    (Haozee only?) 
                if (fncmd == 1) {
                    sendEvent("name": "minHumidityAlarm", "value": "active")
                    if (settings?.txtEnable) log.info "${device.displayName} Minimal Temperature Alarm (0x0F=${fncmd}) is active"
                }
                else if (fncmd == 0) {
                    sendEvent("name": "maxHumidityAlarm", "value": "active")
                    if (settings?.txtEnable) log.info "${device.displayName} Maximal Temperature Alarm (0x0F=${fncmd}) is active"
                }
                else if (fncmd == 2 ) {
                    sendEvent("name": "minHumidityAlarm", "value": "inactive")
                    sendEvent("name": "maxHumidityAlarm", "value": "inactive")
                    if (settings?.txtEnable) log.info "${device.displayName} Humidity Alarm (0x0F=${fncmd}) is inactive"
                }
                else {
                    if (settings?.txtEnable) log.warn "${device.displayName} Temperature Alarm (0x0E) UNKNOWN value ${fncmd}" // 1 if alarm (lower alarm) ? 2 if lower alam is cleared
                }                
                break
            case 0x11 : // temperature max reporting interval, default 120 min (Haozee only?) 
                if (settings?.txtEnable) log.info "${device.displayName} temperature max reporting interval is ${fncmd} min"
                break                
            case 0x12 : // humidity max reporting interval, default 120 min (Haozee only?) 
                if (settings?.txtEnable) log.info "${device.displayName} humidity max reporting interval is ${fncmd} min"
                break                
            case 0x13 : // temperature sensitivity(value/2/10) default 0.3C ( divide / 2 for Haozee only?) 
                if (settings?.txtEnable) log.info "${device.displayName} temperature sensitivity reported by device is: ${fncmd/10.0} °C"
                device.updateSetting("temperatureSensitivity", [value:fncmd/10.0, type:"number"])
                break                
            case 0x14 : // humidity sensitivity default 3%  (Haozee only?)
                if (settings?.txtEnable) log.info "${device.displayName} humidity sensitivity is ${fncmd} %"
                device.updateSetting("humiditySensitivity", [value:fncmd, type:"decimal"])
                 break 
            //
            default :
                if (settings?.logEnable) log.warn "${device.displayName} <b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
                break
        }
    } // if (descMap?.command == "01" || descMap?.command == "02")
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

// options: [0:"Auto detect", 1:"TS0601_Tuya", 2:"TS0601_Haozee", 3:"TS0201", 4:"TS0222", 5:"TS0222_2", 6:"Zigbee NON-Tuya"]

def getModelGroup() {
    def manufacturer = device.getDataValue("manufacturer")
    def modelGroup = 'UNKNOWN'
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    if (modelGroupPreference == "Auto detect") {
        if (manufacturer in Models) {
            modelGroup = Models[manufacturer]
        }
        else {
             modelGroup = 'UNKNOWN'
        }
    }
    else {
         modelGroup = modelGroupPreference 
    }
    //    if (settings?.logEnable) log.trace "${device.displayName} manufacturer ${manufacturer} group is ${modelGroup}"
    return modelGroup
}

def temperatureEvent( temperature ) {
    def map = [:] 
    map.name = "temperature"
    def Scale = location.temperatureScale
    
    if (temperatureScaleParameter == "2" || (temperatureScaleParameter == "0" && Scale == "F")) {
        temperature = (temperature * 1.8) + 32
        map.unit = "\u00B0"+"F"
    }
    else {
        map.unit = "\u00B0"+"C"
    }
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

def switchEvent( value ) {
    def map = [:] 
    map.name = "switch"
    map.value = value
    map.descriptionText = "${device.displayName} switch is ${value}"
    if (settings?.txtEnable) {log.info "${map.descriptionText}"}
    sendEvent(map)
}

def illuminanceEvent( illuminance ) {
    //def rawLux = Integer.parseInt(descMap.value,16)
	def lux = illuminance > 0 ? Math.round(Math.pow(10,(illuminance/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lux", isStateChange: true)
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}


//  called from initialize()
def installed() {
    if (settings?.txtEnable) log.info "${device.displayName} installed()"
    unschedule()
}


def updated() {
    ArrayList<String> cmds = []
    
    if (modelGroupPreference == null) {
        device.updateSetting("modelGroupPreference", "Auto detect")
    }
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getLabel()} (${device.getName()}) model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> modelGroupPreference = <b>${modelGroupPreference}</b> (${getModelGroup()})"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, logsOff)    // turn off debug logging after 30 minutes
        if (settings?.txtEnable) log.info "${device.displayName} Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    Integer fncmd
    if (getModelGroup() in ['TS0601_Tuya','TS0601_Haozee']) {
        if (settings?.logEnable) log.trace "${device.displayName} temperatureScaleParameter = ${temperatureScaleParameter}"
        if (temperatureScaleParameter == "1" || (temperatureScaleParameter == "0" && location.temperatureScale== "C")) {    // Celsius
            cmds += sendTuyaCommand("09", DP_TYPE_ENUM, "00")
            if (settings?.logEnable) log.warn "${device.displayName} changing to Celsius: ${cmds}"
        }
        else if (temperatureScaleParameter == "2" || (temperatureScaleParameter == "0" && location.temperatureScale== "F")) {    // Fahrenheit
            cmds += sendTuyaCommand("09", DP_TYPE_ENUM, "01")
            if (settings?.logEnable) log.warn "${device.displayName} changing to Fahrenheit: ${cmds}"
        }
        else {
            if (settings?.logEnable) log.warn "${device.displayName} temperatureScaleParameter NOT MATCH!"
        }
        fncmd = (safeToDouble( temperatureSensitivity ) * 10) as int
        if (settings?.logEnable) log.trace "${device.displayName} changing temperatureSensitivity to= ${fncmd/10.0}"
        cmds += sendTuyaCommand("13", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        
        fncmd = (safeToDouble( maxTempAlarmPar ) * 10) as int
        if (settings?.logEnable) log.trace "${device.displayName} changing maxTempAlarm to= ${fncmd/10.0 as double}"
        cmds += sendTuyaCommand("0A", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        
        fncmd = (safeToDouble( minTempAlarmPar ) * 10) as int
        if (settings?.logEnable) log.trace "${device.displayName} changing minTempAlarm to= ${fncmd/10.0 as double}"
        cmds += sendTuyaCommand("0B", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        
    }
    if (getModelGroup() in ['TS0601_Haozee']) {
        fncmd = safeToInt( humiditySensitivity )
        if (settings?.logEnable) log.trace "${device.displayName} changing humiditySensitivity to= ${fncmd}"
        cmds += sendTuyaCommand("14", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        fncmd = safeToInt( maxHumidityAlarmPar )
        if (settings?.logEnable) log.trace "${device.displayName} changing maxHumidityAlarm to= ${fncmd}"
        cmds += sendTuyaCommand("0C", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
        fncmd = safeToInt( minHumidityAlarmPar )
        if (settings?.logEnable) log.trace "${device.displayName} changing minHumidityAlarm to= ${fncmd}"
        cmds += sendTuyaCommand("0D", DP_TYPE_VALUE, zigbee.convertToHexString(fncmd as int, 8))
    }
    if (getModelGroup() in ['TS0601_Haozee']) {
        // TODO - write attribute 0xF001, cluster 0x400 
    }
    if (getModelGroup() in ["Zigbee NON-Tuya"]) {
    	cmds += zigbee.configureReporting(0x0402, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure temperature - Report every minute, 1 second if any change
    	cmds += zigbee.configureReporting(0x0403, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure Pressure - Report every minute, 1 second if any change
    	cmds += zigbee.configureReporting(0x0405, 0x0000, DataType.INT16, 1, 60, 1, [:], 200)  // Configure Humidity - Report every minute, 1 second if any change
   		cmds += zigbee.configureReporting(0x0001, 0x0020, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Voltage - Report once per 6hrs or if a change of 100mV detected
   		cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8, 0, 21600, 1, [:], 200)   // Configure Battery % - Report once per 6hrs or if a change of 1% detected    
    }    
    
    //illuminanceSensitivity - descMap.cluster == "0400" && descMap.attrId == "F001" 
    // TODO !!!!! ( for TS0201 Neo Coolcam and TS0222  MOES ZSS-ZK-THL
    //
    if (settings?.txtEnable) log.info "${device.displayName} Update finished"
    sendZigbeeCommands( cmds )  
}

def pollTS0222() {
    List<String> cmds = []
	cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)  // Battery Percent
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0402 0x0000 {}" //, "delay 200",
    cmds += "he rattr 0x${device.deviceNetworkId} 0x02 0x0405 0x0000 {}" //, "delay 200",
	sendZigbeeCommands(cmds)
}

def refresh() {
    if (settings?.logEnable)  {log.debug "${device.displayName} refresh()..."}
    if (getModelGroup() == 'TS0222') {
        pollTS0222()
    }
    else {
        zigbee.readAttribute(0, 1)
    }
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
    if (settings?.txtEnable) log.info "${device.displayName} manufacturer  = ${device.getDataValue("manufacturer")} ModelGroup = ${getModelGroup()}"
    if (settings?.txtEnable) log.info "${device.displayName} Initialization finished\r                          version=${version()} (Timestamp: ${timeStamp()})"
}

// called by initialize() button
void initializeVars(boolean fullInit = true ) {
    if (settings?.txtEnable) log.info "${device.displayName} InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    //
    state.packetID = 0
    state.rxCounter = 0
    state.txCounter = 0

    if (fullInit == true || device.getDataValue("modelGroupPreference") == null) device.updateSetting("modelGroupPreference", "Auto detect")
    if (fullInit == true || device.getDataValue("logEnable") == null) device.updateSetting("logEnable", true)
    if (fullInit == true || device.getDataValue("txtEnable") == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || device.getDataValue("advancedOptions") == null) device.updateSetting("advancedOptions", false)
    if (fullInit == true || device.getDataValue("temperatureScaleParameter") == null) device.updateSetting("temperatureScaleParameter",  [value:"Auto detect", type:"enum"])
    if (fullInit == true || device.getDataValue("temperatureSensitivity") == null)     device.updateSetting("temperatureSensitivity", [value:0.5, type:"number"])
    if (fullInit == true || device.getDataValue("humiditySensitivity") == null)     device.updateSetting("temperatureSensitivity", [value:5, type:"decimal"])
    if (fullInit == true || device.getDataValue("illuminanceSensitivity") == null)     device.updateSetting("illuminanceSensitivity", [value:12, type:"decimal"])
    if (fullInit == true || device.getDataValue("minTempAlarmPar") == null) device.updateSetting("minTempAlarmPar",  [value:18.0, type:"number"])
    if (fullInit == true || device.getDataValue("maxTempAlarmPar") == null) device.updateSetting("maxTempAlarmPar",  [value:28.0, type:"number"])
    if (fullInit == true || device.getDataValue("minHumidityAlarmPar") == null) device.updateSetting("minHumidityAlarmPar",  [value:10, type:"decimal"])
    if (fullInit == true || device.getDataValue("maxHumidityAlarmPar") == null) device.updateSetting("maxHumidityAlarmPar",  [value:90, type:"decimal"])
    if (fullInit == true || device.getDataValue("minReportingTimeTemp") == null) device.updateSetting("minReportingTimeTemp",  [value:60, type:"decimal"])
    if (fullInit == true || device.getDataValue("maxReportingTimeTemp") == null) device.updateSetting("maxReportingTimeTemp",  [value:3600, type:"decimal"])
    if (fullInit == true || device.getDataValue("minReportingTimeHumidity") == null) device.updateSetting("minReportingTimeHumidity",  [value:60, type:"decimal"])
    if (fullInit == true || device.getDataValue("maxReportingTimeHumidity") == null) device.updateSetting("maxReportingTimeHumidity",  [value:3600, type:"decimal"])
    
}

def tuyaBlackMagic() {
     List<String> cmds = []
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200)    // Cluster: Basic, attributes: Man.name, ZLC ver, App ver, Model Id, Power Source, attributeReportingStatus
    cmds += zigbee.writeAttribute(0x0000, 0xffde, 0x20, 0x13, [:], delay=200)
    return  cmds
}

def configure() {
    if (settings?.txtEnable) log.info "${device.displayName} configure().."
    List<String> cmds = []
    cmds += tuyaBlackMagic()    
    sendZigbeeCommands(cmds)    
}

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
    log.warn "${device.displayName} debug logging disabled..."
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
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryPercentageResult(${rawValue})"
    }
}

private Map getBatteryResult(rawValue) {
    if (settings?.logEnable) log.debug "${device.displayName} getBatteryResult volts = ${(double)rawValue / 10.0}"
    def linkText = getLinkText(device)

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
        result.descriptionText = "${linkText} battery is ${result.value}%"
        result.name = 'battery'
        result.isStateChange = true
        sendEvent(result)
    }
    else {
        if (settings?.logEnable) log.warn "${device.displayName} ignoring BatteryResult(${rawValue})"
    }    
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}
